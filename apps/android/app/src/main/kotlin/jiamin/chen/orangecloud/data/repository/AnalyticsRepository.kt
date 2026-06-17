package jiamin.chen.orangecloud.data.repository

import jiamin.chen.orangecloud.core.network.CfApiClient
import jiamin.chen.orangecloud.data.model.AnalyticsGroup
import jiamin.chen.orangecloud.data.model.AnalyticsQueries
import jiamin.chen.orangecloud.data.model.AnalyticsTimeRange
import jiamin.chen.orangecloud.data.model.TrafficDataPoint
import jiamin.chen.orangecloud.data.model.ZoneAnalyticsData
import jiamin.chen.orangecloud.data.model.ZoneAnalyticsVariables
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zone 流量分析：GraphQL Analytics API → 归一化数据点（对应 iOS AnalyticsService.zoneTraffic）。
 * 分析为只读派生数据，不入 Room；按时间范围在 ViewModel 会话级缓存。
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val api: CfApiClient,
) {
    /** 单个 Worker 的 24h 指标（请求/错误/子请求），对应 iOS workerMetrics 摘要。 */
    suspend fun workerMetrics(accountId: String, scriptName: String): jiamin.chen.orangecloud.data.model.WorkerMetrics {
        val (since, until) = AnalyticsTimeRange.LAST_24H.sinceUntil()
        val data = api.graphQL<jiamin.chen.orangecloud.data.model.WorkerMetricsData, jiamin.chen.orangecloud.data.model.WorkerMetricsVariables>(
            AnalyticsQueries.workerSummary(),
            jiamin.chen.orangecloud.data.model.WorkerMetricsVariables(accountId, scriptName, since, until),
        )
        val sums = data.viewer.accounts.firstOrNull()?.summary.orEmpty()
        return jiamin.chen.orangecloud.data.model.WorkerMetrics(
            requests = sums.sumOf { it.sum?.requests ?: 0L },
            errors = sums.sumOf { it.sum?.errors ?: 0L },
            subrequests = sums.sumOf { it.sum?.subrequests ?: 0L },
        )
    }

    suspend fun zoneTraffic(zoneId: String, range: AnalyticsTimeRange): List<TrafficDataPoint> {
        val (since, until) = range.sinceUntil()
        val query = if (range.usesHourlyGroups) {
            AnalyticsQueries.zoneHourly(range.limit)
        } else {
            AnalyticsQueries.zoneDaily(range.limit)
        }
        val data = api.graphQL<ZoneAnalyticsData, ZoneAnalyticsVariables>(
            query,
            ZoneAnalyticsVariables(zoneTag = zoneId, since = since, until = until),
        )
        val zone = data.viewer.zones.firstOrNull() ?: return emptyList()
        return zone.groups.mapNotNull { it.toDataPoint() }
    }
}

private fun AnalyticsGroup.toDataPoint(): TrafficDataPoint? {
    val date = AnalyticsTimeRange.parseDimension(dimensions?.datetime, dimensions?.date) ?: return null
    return TrafficDataPoint(
        date = date,
        requests = sum?.requests ?: 0,
        bytes = sum?.bytes ?: 0,
        threats = sum?.threats ?: 0,
        pageViews = sum?.pageViews ?: 0,
        uniques = uniq?.uniques ?: 0,
        cachedRequests = sum?.cachedRequests ?: 0,
    )
}
