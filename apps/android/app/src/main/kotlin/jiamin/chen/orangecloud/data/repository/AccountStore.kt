package jiamin.chen.orangecloud.data.repository

import jiamin.chen.orangecloud.data.model.Account
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当前身份下的账号列表与选中账号（对应 iOS SessionStore 的账号选择职责）。
 * 所有账号级模块（Zones / Workers / 存储 / 分析）统一从这里取 selectedAccountId 作用域，
 * 切账号时各 @flatMapLatest 自动重查（规避多账号资源错配）。
 */
@Singleton
class AccountStore @Inject constructor(
    private val accountRepository: AccountRepository,
) {
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()

    val selectedAccount: Account?
        get() = _accounts.value.firstOrNull { it.id == _selectedAccountId.value }

    private val mutex = Mutex()
    private var loaded = false

    /** 幂等加载账号列表，首个账号设为当前账号。 */
    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            applyAccounts(accountRepository.listAccounts())
        }
    }

    /** 强制刷新账号列表（添加账号后等）。 */
    suspend fun refresh() {
        mutex.withLock {
            applyAccounts(accountRepository.listAccounts())
        }
    }

    fun select(accountId: String) {
        if (_accounts.value.any { it.id == accountId }) {
            _selectedAccountId.value = accountId
        }
    }

    private fun applyAccounts(list: List<Account>) {
        _accounts.value = list
        val current = _selectedAccountId.value
        if (current == null || list.none { it.id == current }) {
            _selectedAccountId.value = list.firstOrNull()?.id
        }
        loaded = list.isNotEmpty()
    }
}
