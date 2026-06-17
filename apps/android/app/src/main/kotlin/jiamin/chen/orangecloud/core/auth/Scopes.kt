package jiamin.chen.orangecloud.core.auth

/**
 * OAuth scope ID（来自 GET /client/v4/oauth/scopes 的 id 字段，与 iOS PermissionModels 一致）。
 * 注意：Cloudflare 的 authorization_code 授权默认返回 refresh_token，**不需要** offline_access。
 */
object Scopes {
    const val ACCOUNT_READ = "account-settings.read"
    const val ZONE_READ = "zone.read"
    const val ZONE_WRITE = "zone.write"
    const val DNS_READ = "dns.read"
    const val DNS_WRITE = "dns.write"
    const val WORKERS_READ = "workers-scripts.read"
    const val WORKERS_WRITE = "workers-scripts.write"
    const val WORKERS_TAIL_READ = "workers-tail.read"
    const val SNIPPETS_READ = "snippets.read"
    const val SNIPPETS_WRITE = "snippets.write"
    const val R2_READ = "workers-r2.read"
    const val R2_WRITE = "workers-r2.write"
    const val D1_READ = "d1.read"
    const val D1_WRITE = "d1.write"
    const val KV_READ = "workers-kv-storage.read"
    const val KV_WRITE = "workers-kv-storage.write"
    const val TUNNEL_READ = "argotunnel.read"
    const val WAF_READ = "zone-waf.read"
    const val WAF_WRITE = "zone-waf.write"
    const val ZONE_SETTINGS_READ = "zone-settings.read"
    const val ZONE_SETTINGS_WRITE = "zone-settings.write"
    const val CACHE_PURGE = "cache.purge"
    const val ACCOUNT_ANALYTICS_READ = "account-analytics.read"
    const val ANALYTICS_READ = "analytics.read"

    /**
     * 默认申请的权限集，覆盖全部已对表 iOS 的功能（账号/域名/DNS/Workers/tail/Snippets/
     * 存储/Tunnel/WAF/Zone 设置/分析）。对应 iOS PermissionModels.allFeatures 的全选默认。
     * 完整权限选择 UI（让用户按功能裁剪）见后续切片；裁剪前默认全量请求。
     */
    val defaultP0: List<String> = listOf(
        ACCOUNT_READ,
        ZONE_READ, ZONE_WRITE,
        DNS_READ, DNS_WRITE,
        WORKERS_READ, WORKERS_WRITE,
        WORKERS_TAIL_READ,
        SNIPPETS_READ, SNIPPETS_WRITE,
        R2_READ, R2_WRITE,
        D1_READ, D1_WRITE,
        KV_READ, KV_WRITE,
        TUNNEL_READ,
        WAF_READ, WAF_WRITE,
        ZONE_SETTINGS_READ, ZONE_SETTINGS_WRITE, CACHE_PURGE,
        ACCOUNT_ANALYTICS_READ, ANALYTICS_READ,
    )

    /** 空格分隔、排序去重的 scope 字符串，直接用于 OAuth scope 参数。 */
    fun scopeString(scopes: List<String> = defaultP0): String =
        scopes.toSortedSet().joinToString(" ")
}
