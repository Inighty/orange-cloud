package jiamin.chen.orangecloud.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Cloudflare 账号（与 iOS Models/Account.swift 对应）。 */
@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: String? = null,
)

/** 域名 Zone（与 iOS Models/Zone.swift 对应）。 */
@Serializable
data class Zone(
    val id: String,
    val name: String,
    val status: String, // "active" | "pending" | "paused" 等
    val plan: ZonePlan? = null,
    @SerialName("name_servers") val nameServers: List<String>? = null,
) {
    val isActive: Boolean get() = status == "active"
}

@Serializable
data class ZonePlan(val name: String)

/** DNS 记录（与 iOS Models/DNSRecord.swift 对应）。 */
@Serializable
data class DnsRecord(
    val id: String,
    val type: String,              // A / AAAA / CNAME / TXT / MX / NS 等
    val name: String,
    val content: String,
    val proxied: Boolean? = null,  // 仅 A/AAAA/CNAME 可代理
    val ttl: Int = 1,              // 1 = 自动
    val priority: Int? = null,     // MX / SRV 需要
    val comment: String? = null,
    @SerialName("created_on") val createdOn: String? = null,
) {
    val isProxied: Boolean get() = proxied ?: false
}

/**
 * 新建 / 更新 DNS 记录的请求体（与 iOS CreateDNSRecord 对应）。
 * priority/comment 为 null 时不编码进 JSON（Json explicitNulls=false），
 * 避免给非 MX 记录传 priority:null 被 Cloudflare 拒绝。
 */
@Serializable
data class CreateDnsRecord(
    val type: String,
    val name: String,
    val content: String,
    val proxied: Boolean,
    val ttl: Int,
    val priority: Int? = null,
    val comment: String? = null,
)

/** Workers 脚本（与 iOS Models/WorkerScript.swift 对应）。GET /accounts/{id}/workers/scripts */
@Serializable
data class WorkerScript(
    val id: String,                                    // 即脚本名，账号内唯一
    val etag: String? = null,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    @SerialName("usage_model") val usageModel: String? = null,
    val handlers: List<String>? = null,                // ["fetch", "scheduled"] 等
    val logpush: Boolean? = null,
)
