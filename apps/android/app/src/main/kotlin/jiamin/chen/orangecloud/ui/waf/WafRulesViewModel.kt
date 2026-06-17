package jiamin.chen.orangecloud.ui.waf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jiamin.chen.orangecloud.core.auth.AuthRepository
import jiamin.chen.orangecloud.core.auth.Scopes
import jiamin.chen.orangecloud.data.model.WafRule
import jiamin.chen.orangecloud.data.repository.SecurityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WafUiState(
    val zoneName: String = "",
    val rules: List<WafRule> = emptyList(),
    val rulesetId: String? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val missingScope: Boolean = false,
    val canWrite: Boolean = false,
)

@HiltViewModel
class WafRulesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val securityRepository: SecurityRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val zoneId: String = checkNotNull(savedStateHandle["zoneId"])
    private val hasRead = authRepository.hasScope(Scopes.WAF_READ)
    private val canWrite = authRepository.hasScope(Scopes.WAF_WRITE)

    private val _uiState = MutableStateFlow(
        WafUiState(
            zoneName = savedStateHandle.get<String>("zoneName").orEmpty(),
            isLoading = hasRead,
            missingScope = !hasRead,
            canWrite = canWrite,
        ),
    )
    val uiState: StateFlow<WafUiState> = _uiState.asStateFlow()

    init {
        if (hasRead) load()
    }

    fun load() {
        if (!hasRead) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            try {
                val ruleset = securityRepository.customRuleset(zoneId)
                _uiState.update { it.copy(rules = ruleset?.rules.orEmpty(), rulesetId = ruleset?.id) }
            } catch (e: Exception) {
                _uiState.update { it.copy(hasError = true) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggle(rule: WafRule, enabled: Boolean) {
        val rulesetId = _uiState.value.rulesetId ?: return
        if (!canWrite) return
        viewModelScope.launch {
            try {
                val updated = securityRepository.setRuleEnabled(zoneId, rulesetId, rule, enabled)
                _uiState.update { it.copy(rules = updated.rules.orEmpty(), rulesetId = updated.id) }
            } catch (e: Exception) {
                _uiState.update { it.copy(hasError = true) }
                load()
            }
        }
    }
}
