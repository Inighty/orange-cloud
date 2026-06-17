package jiamin.chen.orangecloud.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 所有 Cloudflare API 调用的统一入口（对应 iOS Core/Network/CFAPIClient.swift）。
 * 自动注入 Bearer、临期主动刷新、401 刷新后重试一次、统一错误解析。
 *
 * path 视为已编码：KV key 等特殊字符由调用方预先百分号编码，直接拼到 baseURL 后。
 */
@Singleton
class CfApiClient @Inject constructor(
    private val httpClient: OkHttpClient,
    @PublishedApi internal val json: Json,
    private val tokenProvider: AccessTokenProvider,
) {

    suspend inline fun <reified T> get(path: String, query: List<Pair<String, String>> = emptyList()): T {
        val bytes = executeRaw("GET", path, query, null, JSON_MEDIA_TYPE)
        return decodeResult(bytes, serializer<T>())
    }

    /** 列表端点：返回数据 + 分页信息（result_info） */
    suspend inline fun <reified T> getList(path: String, query: List<Pair<String, String>> = emptyList()): Paged<T> {
        val bytes = executeRaw("GET", path, query, null, JSON_MEDIA_TYPE)
        val env = json.decodeFromString(CfEnvelope.serializer(ListSerializer(serializer<T>())), bytes.decodeToString())
        if (!env.success) throw ApiError.Cloudflare(env.errors.map { ApiError.CfError(it.code, it.message) })
        return Paged(env.result ?: emptyList(), env.resultInfo)
    }

    suspend inline fun <reified T, reified B> post(path: String, body: B): T {
        val payload = json.encodeToString(serializer<B>(), body).encodeToByteArray()
        return decodeResult(executeRaw("POST", path, emptyList(), payload, JSON_MEDIA_TYPE), serializer<T>())
    }

    suspend inline fun <reified T, reified B> put(path: String, body: B): T {
        val payload = json.encodeToString(serializer<B>(), body).encodeToByteArray()
        return decodeResult(executeRaw("PUT", path, emptyList(), payload, JSON_MEDIA_TYPE), serializer<T>())
    }

    suspend inline fun <reified T, reified B> patch(path: String, body: B): T {
        val payload = json.encodeToString(serializer<B>(), body).encodeToByteArray()
        return decodeResult(executeRaw("PATCH", path, emptyList(), payload, JSON_MEDIA_TYPE), serializer<T>())
    }

    /** 只关心 success 的请求（DELETE 等）。非 2xx 由 executeRaw 抛错。 */
    suspend fun delete(path: String) {
        executeRaw("DELETE", path, emptyList(), null, JSON_MEDIA_TYPE)
    }

    /** KV value 等非 JSON 信封端点：返回原始字节 */
    suspend fun getRaw(path: String, query: List<Pair<String, String>> = emptyList()): ByteArray =
        executeRaw("GET", path, query, null, null)

    /** 原始字节 PUT（R2 对象上传等），自带 Content-Type */
    suspend inline fun <reified T> putRaw(path: String, body: ByteArray, contentType: String): T =
        decodeResult(executeRaw("PUT", path, emptyList(), body, contentType), serializer<T>())

    /** multipart/form-data 写入（KV 写值要求 value + metadata 两个 part） */
    suspend inline fun <reified T> putMultipart(path: String, fields: Map<String, String>): T {
        val boundary = "OrangeCloud-${UUID.randomUUID()}"
        val bytes = executeRaw("PUT", path, emptyList(), buildMultipartBody(boundary, fields), "multipart/form-data; boundary=$boundary")
        return decodeResult(bytes, serializer<T>())
    }

    /** multipart 写入，只校验 success（KV 写值返回 result=null，不强制解码 result）。 */
    suspend fun putMultipartVoid(path: String, fields: Map<String, String>) {
        val boundary = "OrangeCloud-${UUID.randomUUID()}"
        executeRaw("PUT", path, emptyList(), buildMultipartBody(boundary, fields), "multipart/form-data; boundary=$boundary")
    }

    /** 原始字节 PUT，只校验 success（R2 上传等 result 可能为 null）。 */
    suspend fun putRawVoid(path: String, body: ByteArray, contentType: String) {
        executeRaw("PUT", path, emptyList(), body, contentType)
    }

    /** Snippets 创建/更新：multipart 带 metadata part + JS 模块文件 part（application/javascript+module）。 */
    suspend inline fun <reified T> putMultipartFile(
        path: String,
        metadataJson: String,
        fileName: String,
        fileText: String,
        fileContentType: String,
    ): T {
        val boundary = "OrangeCloud-${UUID.randomUUID()}"
        val body = buildMultipartFileBody(boundary, metadataJson, fileName, fileText, fileContentType)
        val bytes = executeRaw("PUT", path, emptyList(), body, "multipart/form-data; boundary=$boundary")
        return decodeResult(bytes, serializer<T>())
    }

    @PublishedApi
    internal fun buildMultipartFileBody(
        boundary: String,
        metadataJson: String,
        fileName: String,
        fileText: String,
        fileContentType: String,
    ): ByteArray {
        val sb = StringBuilder()
        sb.append("--").append(boundary).append("\r\n")
        sb.append("Content-Disposition: form-data; name=\"metadata\"\r\n\r\n")
        sb.append(metadataJson).append("\r\n")
        sb.append("--").append(boundary).append("\r\n")
        sb.append("Content-Disposition: form-data; name=\"").append(fileName).append("\"; filename=\"").append(fileName).append("\"\r\n")
        sb.append("Content-Type: ").append(fileContentType).append("\r\n\r\n")
        sb.append(fileText).append("\r\n")
        sb.append("--").append(boundary).append("--\r\n")
        return sb.toString().encodeToByteArray()
    }

    @PublishedApi
    internal fun buildMultipartBody(boundary: String, fields: Map<String, String>): ByteArray {
        val sb = StringBuilder()
        for ((name, value) in fields) {
            sb.append("--").append(boundary).append("\r\n")
            sb.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
            sb.append(value).append("\r\n")
        }
        sb.append("--").append(boundary).append("--\r\n")
        return sb.toString().encodeToByteArray()
    }

    /** GraphQL Analytics：信封 {data, errors}，GraphQL 错误时 HTTP 仍 200 */
    suspend inline fun <reified D, reified V> graphQL(query: String, variables: V): D {
        val payload = json.encodeToString(GraphQLRequest.serializer(serializer<V>()), GraphQLRequest(query, variables)).encodeToByteArray()
        val bytes = executeRaw("POST", "graphql", emptyList(), payload, JSON_MEDIA_TYPE)
        val env = json.decodeFromString(GraphQLResponse.serializer(serializer<D>()), bytes.decodeToString())
        env.errors.firstOrNull()?.let { throw ApiError.Cloudflare(listOf(ApiError.CfError(0, it.message))) }
        return env.data ?: throw ApiError.Decoding(IllegalStateException("GraphQL data missing"))
    }

    // MARK: - 内部实现（@PublishedApi internal 供上方 inline 函数引用）

    @PublishedApi
    internal fun <T> decodeResult(bytes: ByteArray, elementSerializer: KSerializer<T>): T {
        val env = try {
            json.decodeFromString(CfEnvelope.serializer(elementSerializer), bytes.decodeToString())
        } catch (e: Exception) {
            throw ApiError.Decoding(e)
        }
        if (!env.success) throw ApiError.Cloudflare(env.errors.map { ApiError.CfError(it.code, it.message) })
        return env.result ?: throw ApiError.Decoding(IllegalStateException("result missing"))
    }

    @PublishedApi
    internal suspend fun executeRaw(
        method: String,
        path: String,
        query: List<Pair<String, String>>,
        body: ByteArray?,
        contentType: String?,
        isRetry: Boolean = false,
    ): ByteArray {
        val token = tokenProvider.validAccessToken()

        val url = "$BASE_URL/$path".toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        val requestBody = if (method == "GET") null
        else (body ?: ByteArray(0)).toRequestBody(contentType?.toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method(method, requestBody)
            .build()

        // execute + 读 body 都在 IO 上完成；状态判断与 401 重试留在调用方协程
        val (code, bytes) = try {
            withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { resp ->
                    resp.code to (resp.body?.bytes() ?: ByteArray(0))
                }
            }
        } catch (e: IOException) {
            throw ApiError.Network(e)
        }

        // 401：刷新后重试一次
        if (code == 401 && !isRetry) {
            tokenProvider.refreshAccessToken()
            return executeRaw(method, path, query, body, contentType, isRetry = true)
        }

        return when (code) {
            in 200..299 -> bytes
            401 -> throw ApiError.Unauthorized
            else -> {
                // 优先透出 CF 业务错误
                val cfErrors = runCatching {
                    json.decodeFromString(
                        CfEnvelope.serializer(JsonElement.serializer()),
                        bytes.decodeToString(),
                    ).errors
                }.getOrNull().orEmpty()
                if (cfErrors.isNotEmpty()) {
                    throw ApiError.Cloudflare(cfErrors.map { ApiError.CfError(it.code, it.message) })
                }
                throw ApiError.Http(code)
            }
        }
    }

    companion object {
        const val BASE_URL = "https://api.cloudflare.com/client/v4"
        const val JSON_MEDIA_TYPE = "application/json"
    }
}
