package jiamin.chen.orangecloud.data.repository

import jiamin.chen.orangecloud.core.network.CfApiClient
import jiamin.chen.orangecloud.data.model.D1CreateRequest
import jiamin.chen.orangecloud.data.model.D1Database
import jiamin.chen.orangecloud.data.model.D1QueryRequest
import jiamin.chen.orangecloud.data.model.D1QueryResult
import jiamin.chen.orangecloud.data.model.KVKey
import jiamin.chen.orangecloud.data.model.KVNamespace
import jiamin.chen.orangecloud.data.model.R2Bucket
import jiamin.chen.orangecloud.data.model.R2BucketList
import jiamin.chen.orangecloud.data.model.R2Object
import jiamin.chen.orangecloud.data.model.R2ObjectListRequest
import jiamin.chen.orangecloud.data.model.R2ObjectPage
import jiamin.chen.orangecloud.data.model.encodeStorageKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 存储仓库：R2 / D1 / KV（对应 iOS R2Service / D1Service / KVService）。
 * 派生/会话级数据不入 Room；游标分页一次一页，key 显式百分号编码。
 */
@Singleton
class StorageRepository @Inject constructor(
    private val api: CfApiClient,
) {
    // MARK: - R2

    suspend fun listBuckets(accountId: String): List<R2Bucket> =
        api.get<R2BucketList>("accounts/$accountId/r2/buckets", listOf("per_page" to "100")).buckets

    /** 对象列表（游标分页，一次一页）→ 对象、同级文件夹前缀、下一页游标。 */
    suspend fun listObjects(request: R2ObjectListRequest): R2ObjectPage {
        val query = buildList {
            add("per_page" to "100")
            add("delimiter" to "/")
            if (request.prefix.isNotEmpty()) add("prefix" to request.prefix)
            request.cursor?.let { add("cursor" to it) }
        }
        val paged = api.getList<R2Object>(
            "accounts/${request.accountId}/r2/buckets/${request.bucket}/objects",
            query,
        )
        val next = if (paged.info?.isTruncated == true) paged.info?.cursor else null
        return R2ObjectPage(paged.items, paged.info?.delimited.orEmpty(), next)
    }

    suspend fun getObjectBytes(accountId: String, bucket: String, key: String): ByteArray =
        api.getRaw("accounts/$accountId/r2/buckets/$bucket/objects/${encodeStorageKey(key)}")

    /** 上传对象（原始字节 PUT，自带 Content-Type；result 可能为 null 故只校验 success）。 */
    suspend fun putObject(accountId: String, bucket: String, key: String, bytes: ByteArray, contentType: String) =
        api.putRawVoid("accounts/$accountId/r2/buckets/$bucket/objects/${encodeStorageKey(key)}", bytes, contentType)

    suspend fun deleteObject(accountId: String, bucket: String, key: String) =
        api.delete("accounts/$accountId/r2/buckets/$bucket/objects/${encodeStorageKey(key)}")

    // MARK: - D1

    suspend fun listDatabases(accountId: String): List<D1Database> {
        val all = mutableListOf<D1Database>()
        var page = 1
        while (true) {
            val paged = api.getList<D1Database>(
                "accounts/$accountId/d1/database",
                listOf("page" to page.toString(), "per_page" to "100"),
            )
            all += paged.items
            if (page >= (paged.info?.totalPages ?: 1)) break
            page++
        }
        return all
    }

    /**
     * 数据库详情。列表端点不返回 file_size / num_tables 的真实值（常年 0），
     * 这两个字段以详情端点为准（对齐 iOS D1Service.getDatabase）。
     */
    suspend fun getDatabase(accountId: String, databaseId: String): D1Database =
        api.get("accounts/$accountId/d1/database/$databaseId")

    /** 创建数据库。locationHint 为空走自动放置。 */
    suspend fun createDatabase(accountId: String, name: String, locationHint: String?): D1Database =
        api.post("accounts/$accountId/d1/database", D1CreateRequest(name, locationHint))

    /** 删除数据库（连同全部表与数据，不可恢复）。 */
    suspend fun deleteDatabase(accountId: String, databaseId: String) =
        api.delete("accounts/$accountId/d1/database/$databaseId")

    /** 执行 SQL（每条语句一个结果）。 */
    suspend fun query(accountId: String, databaseId: String, sql: String, params: List<String>? = null): List<D1QueryResult> =
        api.post("accounts/$accountId/d1/database/$databaseId/query", D1QueryRequest(sql, params))

    // MARK: - KV

    suspend fun listNamespaces(accountId: String): List<KVNamespace> {
        val all = mutableListOf<KVNamespace>()
        var page = 1
        while (true) {
            val paged = api.getList<KVNamespace>(
                "accounts/$accountId/storage/kv/namespaces",
                listOf("page" to page.toString(), "per_page" to "100"),
            )
            all += paged.items
            if (page >= (paged.info?.totalPages ?: 1)) break
            page++
        }
        return all
    }

    /** 键列表（游标分页，一次一页）。cursor 为空串表示已到末尾。 */
    suspend fun listKeys(accountId: String, namespaceId: String, cursor: String?): Pair<List<KVKey>, String?> {
        val query = buildList {
            add("limit" to "100")
            cursor?.let { add("cursor" to it) }
        }
        val paged = api.getList<KVKey>("accounts/$accountId/storage/kv/namespaces/$namespaceId/keys", query)
        val next = paged.info?.cursor?.takeIf { it.isNotEmpty() }
        return paged.items to next
    }

    suspend fun getValue(accountId: String, namespaceId: String, key: String): ByteArray =
        api.getRaw("accounts/$accountId/storage/kv/namespaces/$namespaceId/values/${encodeStorageKey(key)}")

    /** 写文本值（multipart：value + metadata 两个 part 必填）。 */
    suspend fun putValue(accountId: String, namespaceId: String, key: String, value: String) =
        api.putMultipartVoid(
            "accounts/$accountId/storage/kv/namespaces/$namespaceId/values/${encodeStorageKey(key)}",
            mapOf("value" to value, "metadata" to "{}"),
        )

    suspend fun deleteKey(accountId: String, namespaceId: String, key: String) =
        api.delete("accounts/$accountId/storage/kv/namespaces/$namespaceId/values/${encodeStorageKey(key)}")
}
