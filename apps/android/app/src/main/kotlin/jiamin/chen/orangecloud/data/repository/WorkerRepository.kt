package jiamin.chen.orangecloud.data.repository

import jiamin.chen.orangecloud.core.network.CfApiClient
import jiamin.chen.orangecloud.data.local.WorkerDao
import jiamin.chen.orangecloud.data.local.toEntity
import jiamin.chen.orangecloud.data.local.toWorker
import jiamin.chen.orangecloud.data.model.WorkerScript
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Workers 仓库：Room 为单一可信源，网络刷新后整账号替换缓存（对应 iOS WorkerService + CacheSync）。
 * 该端点不分页。
 */
@Singleton
class WorkerRepository @Inject constructor(
    private val api: CfApiClient,
    private val workerDao: WorkerDao,
) {
    fun observeWorkers(accountId: String): Flow<List<WorkerScript>> =
        workerDao.observeByAccount(accountId).map { rows -> rows.map { it.toWorker() } }

    suspend fun refreshWorkers(accountId: String) {
        val scripts = api.getList<WorkerScript>("accounts/$accountId/workers/scripts").items
        workerDao.replaceForAccount(accountId, scripts.map { it.toEntity(accountId) })
    }
}
