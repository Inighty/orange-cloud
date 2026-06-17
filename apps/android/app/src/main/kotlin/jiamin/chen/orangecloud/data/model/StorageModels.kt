package jiamin.chen.orangecloud.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// MARK: - R2

/** GET /accounts/{id}/r2/buckets 的 result 是 { buckets: [...] }（不是数组）。 */
@Serializable
data class R2BucketList(val buckets: List<R2Bucket> = emptyList())

@Serializable
data class R2Bucket(
    val name: String,
    @SerialName("creation_date") val creationDate: String? = null,
    val location: String? = null,
    @SerialName("storage_class") val storageClass: String? = null,
)

@Serializable
data class R2Object(
    val key: String,
    val etag: String? = null,
    @SerialName("last_modified") val lastModified: String? = null,
    val size: Long? = null,
    @SerialName("http_metadata") val httpMetadata: R2HttpMetadata? = null,
    @SerialName("storage_class") val storageClass: String? = null,
)

@Serializable
data class R2HttpMetadata(val contentType: String? = null)  // R2 对象元数据是 camelCase

// MARK: - D1

@Serializable
data class D1Database(
    val uuid: String,
    val name: String,
    val version: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("num_tables") val numTables: Int? = null,
)

@Serializable
data class D1QueryRequest(
    val sql: String,
    val params: List<String>? = null,
)

/** POST /query 的 result 是 [D1QueryResult]（每条语句一个结果）。 */
@Serializable
data class D1QueryResult(
    val results: List<Map<String, JsonElement>>? = null,
    val success: Boolean = false,
    val meta: D1QueryMeta? = null,
)

@Serializable
data class D1QueryMeta(
    val duration: Double? = null,
    val changes: Int? = null,
    @SerialName("last_row_id") val lastRowId: Long? = null,
    @SerialName("rows_read") val rowsRead: Int? = null,
    @SerialName("rows_written") val rowsWritten: Int? = null,
)

// MARK: - KV

@Serializable
data class KVNamespace(
    val id: String,
    val title: String,
)

@Serializable
data class KVKey(
    val name: String,
    val expiration: Long? = null,   // Unix 秒
)

// MARK: - 存储路径编码

/**
 * R2 / KV key 可含任意字符（/ 空格等），按 iOS `.alphanumerics` 口径百分号编码后拼路径；
 * CfApiClient 把 path 视为已编码，OkHttp toHttpUrl 保留 %XX 不二次编码。
 */
fun encodeStorageKey(key: String): String = buildString {
    for (byte in key.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        if (c in 0x30..0x39 || c in 0x41..0x5A || c in 0x61..0x7A) {
            append(c.toChar())
        } else {
            append('%').append("%02X".format(c))
        }
    }
}
