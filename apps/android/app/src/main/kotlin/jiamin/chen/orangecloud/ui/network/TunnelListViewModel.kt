package jiamin.chen.orangecloud.ui.network

import dagger.hilt.android.lifecycle.HiltViewModel
import jiamin.chen.orangecloud.core.auth.AuthRepository
import jiamin.chen.orangecloud.core.auth.Scopes
import jiamin.chen.orangecloud.data.model.Tunnel
import jiamin.chen.orangecloud.data.repository.AccountStore
import jiamin.chen.orangecloud.data.repository.SecurityRepository
import jiamin.chen.orangecloud.ui.storage.StorageListViewModel
import javax.inject.Inject

@HiltViewModel
class TunnelListViewModel @Inject constructor(
    accountStore: AccountStore,
    private val securityRepository: SecurityRepository,
    authRepository: AuthRepository,
) : StorageListViewModel<Tunnel>(accountStore, authRepository.hasScope(Scopes.TUNNEL_READ)) {
    override suspend fun fetch(accountId: String) = securityRepository.listTunnels(accountId)
    init { load() }
}
