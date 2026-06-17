package jiamin.chen.orangecloud.data.repository

import jiamin.chen.orangecloud.core.network.ApiError
import jiamin.chen.orangecloud.core.network.CfApiClient
import jiamin.chen.orangecloud.data.model.Tunnel
import jiamin.chen.orangecloud.data.model.WafRule
import jiamin.chen.orangecloud.data.model.WafRuleToggle
import jiamin.chen.orangecloud.data.model.WafRuleset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WAF 自定义规则 + Cloudflare Tunnel（对应 iOS WAFService / TunnelService）。
 */
@Singleton
class SecurityRepository @Inject constructor(
    private val api: CfApiClient,
) {
    /** 自定义防火墙规则 entrypoint ruleset；Zone 未建过自定义规则时返回 null（404/业务错误均接住）。 */
    suspend fun customRuleset(zoneId: String): WafRuleset? = try {
        api.get<WafRuleset>("zones/$zoneId/rulesets/phases/http_request_firewall_custom/entrypoint")
    } catch (e: ApiError.Http) {
        if (e.status == 404) null else throw e
    } catch (e: ApiError.Cloudflare) {
        if (e.errors.any { it.message.contains("could not find entrypoint", ignoreCase = true) }) null else throw e
    }

    /** 启停单条规则，返回更新后的整个 ruleset。 */
    suspend fun setRuleEnabled(zoneId: String, rulesetId: String, rule: WafRule, enabled: Boolean): WafRuleset =
        api.patch("zones/$zoneId/rulesets/$rulesetId/rules/${rule.id}", WafRuleToggle(enabled))

    /** 账号下全部 Tunnel（排除已删除，页码分页）。 */
    suspend fun listTunnels(accountId: String): List<Tunnel> {
        val all = mutableListOf<Tunnel>()
        var page = 1
        while (true) {
            val paged = api.getList<Tunnel>(
                "accounts/$accountId/cfd_tunnel",
                listOf("is_deleted" to "false", "page" to page.toString(), "per_page" to "100"),
            )
            all += paged.items
            if (page >= (paged.info?.totalPages ?: 1)) break
            page++
        }
        return all
    }
}
