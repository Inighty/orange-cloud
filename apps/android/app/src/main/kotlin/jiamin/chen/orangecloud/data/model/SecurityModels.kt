package jiamin.chen.orangecloud.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - WAF 自定义规则（Rulesets，phase = http_request_firewall_custom）

@Serializable
data class WafRuleset(
    val id: String,
    val name: String? = null,
    val phase: String? = null,
    val rules: List<WafRule>? = null,
)

@Serializable
data class WafRule(
    val id: String,
    val action: String? = null,          // block | challenge | managed_challenge | js_challenge | log | skip
    val expression: String? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
)

/** PATCH 规则只更新 enabled。 */
@Serializable
data class WafRuleToggle(val enabled: Boolean)

// MARK: - Cloudflare Tunnel（cfd_tunnel）

@Serializable
data class Tunnel(
    val id: String,
    val name: String,
    val status: String? = null,          // inactive | degraded | healthy | down
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("conns_active_at") val connsActiveAt: String? = null,
    @SerialName("tun_type") val tunType: String? = null,
    @SerialName("remote_config") val remoteConfig: Boolean? = null,
    val connections: List<TunnelConnection>? = null,
) {
    val activeConnections: Int get() = connections?.size ?: 0
}

@Serializable
data class TunnelConnection(
    val id: String? = null,
    @SerialName("colo_name") val coloName: String? = null,
    @SerialName("origin_ip") val originIp: String? = null,
    @SerialName("opened_at") val openedAt: String? = null,
    @SerialName("client_version") val clientVersion: String? = null,
)
