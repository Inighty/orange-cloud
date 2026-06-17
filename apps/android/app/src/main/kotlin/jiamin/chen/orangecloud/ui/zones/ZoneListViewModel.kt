package jiamin.chen.orangecloud.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jiamin.chen.orangecloud.data.model.Zone
import jiamin.chen.orangecloud.data.repository.AccountStore
import jiamin.chen.orangecloud.data.repository.ZoneRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ZoneListUiState(
    val zones: List<Zone> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ZoneListViewModel @Inject constructor(
    private val accountStore: AccountStore,
    private val zoneRepository: ZoneRepository,
) : ViewModel() {

    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow(false)

    // 切账号自动重查当前账号的域名缓存
    private val zones: Flow<List<Zone>> = accountStore.selectedAccountId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else zoneRepository.observeZones(id)
    }

    val uiState: StateFlow<ZoneListUiState> =
        combine(zones, loading, error) { zoneList, isLoading, hasError ->
            ZoneListUiState(zones = zoneList, isLoading = isLoading, hasError = hasError)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZoneListUiState(isLoading = true))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            error.value = false
            try {
                accountStore.ensureLoaded()
                val id = accountStore.selectedAccountId.value
                if (id == null) {
                    error.value = true
                } else {
                    zoneRepository.refreshZones(id)
                }
            } catch (e: Exception) {
                error.value = true
            } finally {
                loading.value = false
            }
        }
    }
}
