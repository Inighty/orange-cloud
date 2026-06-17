package jiamin.chen.orangecloud.data.repository

import jiamin.chen.orangecloud.core.network.CfApiClient
import jiamin.chen.orangecloud.data.local.ZoneDao
import jiamin.chen.orangecloud.data.local.toEntity
import jiamin.chen.orangecloud.data.local.toZone
import jiamin.chen.orangecloud.data.model.Zone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 域名仓库：Room 为单一可信源，网络刷新后写回缓存（官方 App Architecture）。
 * 对应 iOS ZoneService + SwiftData @Query。
 */
@Singleton
class ZoneRepository @Inject constructor(
    private val api: CfApiClient,
    private val zoneDao: ZoneDao,
    private val json: Json,
) {
    /** 观察缓存中的域名（UI 始终读这里）。 */
    fun observeZones(accountId: String): Flow<List<Zone>> =
        zoneDao.observeByAccount(accountId).map { rows -> rows.map { it.toZone(json) } }

    /** 从网络拉全量（自动翻页）并整账号替换缓存。 */
    suspend fun refreshZones(accountId: String) {
        val zones = fetchAllZones(accountId)
        zoneDao.replaceForAccount(accountId, zones.map { it.toEntity(accountId, json) })
    }

    private suspend fun fetchAllZones(accountId: String): List<Zone> {
        val all = mutableListOf<Zone>()
        var page = 1
        while (true) {
            val paged = api.getList<Zone>(
                "zones",
                listOf("account.id" to accountId, "page" to page.toString(), "per_page" to "50"),
            )
            all += paged.items
            val totalPages = paged.info?.totalPages ?: 1
            if (page >= totalPages) break
            page++
        }
        return all
    }
}
