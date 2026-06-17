package jiamin.chen.orangecloud.ui.waf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jiamin.chen.orangecloud.R
import jiamin.chen.orangecloud.core.design.SkyBackground
import jiamin.chen.orangecloud.core.design.SkyEmptyState
import jiamin.chen.orangecloud.core.design.SkyHeader
import jiamin.chen.orangecloud.core.design.onSky
import jiamin.chen.orangecloud.core.design.rememberSkyPhase
import jiamin.chen.orangecloud.core.design.theme.OcOrange
import jiamin.chen.orangecloud.data.model.WafRule

@Composable
fun WafRulesScreen(
    onBack: () -> Unit,
    viewModel: WafRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky

    SkyBackground(phase = phase) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SkyHeader(
                title = stringResource(R.string.waf_title),
                onSky = onSky,
                isLoading = state.isLoading,
                onRefresh = { viewModel.load() },
                onBack = onBack,
                titleSize = 22,
                backDescription = stringResource(R.string.common_back),
                refreshDescription = stringResource(R.string.common_refresh),
            )
            if (!state.canWrite && !state.missingScope) {
                Text(
                    stringResource(R.string.waf_readonly),
                    color = onSky.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            when {
                state.missingScope ->
                    SkyEmptyState(Icons.Outlined.Lock, stringResource(R.string.scope_missing), onSky, stringResource(R.string.common_refresh)) { viewModel.load() }

                state.rules.isEmpty() && state.isLoading ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = onSky) }

                state.rules.isEmpty() && state.hasError ->
                    SkyEmptyState(Icons.Outlined.Shield, stringResource(R.string.error_generic), onSky, stringResource(R.string.common_refresh)) { viewModel.load() }

                state.rules.isEmpty() ->
                    SkyEmptyState(Icons.Outlined.Shield, stringResource(R.string.waf_empty), onSky, stringResource(R.string.common_refresh)) { viewModel.load() }

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.rules, key = { it.id }) { rule ->
                        WafRuleRow(rule, enabled = state.canWrite) { viewModel.toggle(rule, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WafRuleRow(rule: WafRule, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .background(OcOrange.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(stringResource(actionLabel(rule.action)), color = OcOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    rule.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                rule.expression?.let {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        it,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = rule.enabled ?: false, onCheckedChange = onToggle, enabled = enabled)
        }
    }
}

private fun actionLabel(action: String?): Int = when (action) {
    "block" -> R.string.waf_action_block
    "challenge" -> R.string.waf_action_challenge
    "managed_challenge" -> R.string.waf_action_managed_challenge
    "js_challenge" -> R.string.waf_action_js_challenge
    "log" -> R.string.waf_action_log
    "skip", "allow" -> R.string.waf_action_allow
    else -> R.string.waf_action_other
}
