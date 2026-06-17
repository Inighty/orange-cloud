package jiamin.chen.orangecloud.ui.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import jiamin.chen.orangecloud.R
import jiamin.chen.orangecloud.core.design.SkyBackground
import jiamin.chen.orangecloud.core.design.onSky
import jiamin.chen.orangecloud.core.design.rememberSkyPhase
import jiamin.chen.orangecloud.core.design.theme.OcOrange
import jiamin.chen.orangecloud.core.purchase.EntitlementStore
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ProGateViewModel @Inject constructor(
    entitlementStore: EntitlementStore,
    private val billingGateway: jiamin.chen.orangecloud.core.purchase.BillingGateway,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = entitlementStore.isPro
    fun purchase(activity: android.app.Activity, planId: String) = billingGateway.launchPurchase(activity, planId)
}

/** Pro 闸门：非 Pro 时以 Paywall 取代受限内容（六处闸门统一用）。 */
@Composable
fun ProGate(
    gateViewModel: ProGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val isPro by gateViewModel.isPro.collectAsStateWithLifecycle()
    if (isPro) content() else PaywallScreen()
}

@Composable
fun PaywallScreen(gateViewModel: ProGateViewModel = hiltViewModel()) {
    val phase = rememberSkyPhase()
    val onSky = phase.onSky
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    SkyBackground(phase = phase) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.size(24.dp))
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = OcOrange, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.paywall_title), color = onSky, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.paywall_subtitle), color = onSky.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.size(8.dp))
            listOf(
                R.string.paywall_feat_multi,
                R.string.paywall_feat_storage,
                R.string.paywall_feat_tail,
                R.string.paywall_feat_security,
                R.string.paywall_feat_analytics,
            ).forEach { FeatureLine(stringResource(it), onSky) }
            Spacer(Modifier.size(12.dp))
            // 价格从 Play ProductDetails 动态取；oss 风味网关为空操作（isPro 恒真，本屏不展示）。
            Button(
                onClick = { activity?.let { gateViewModel.purchase(it, jiamin.chen.orangecloud.core.purchase.BillingGateway.PLAN_YEARLY) } },
                colors = ButtonDefaults.buttonColors(containerColor = OcOrange, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.paywall_yearly))
            }
            Button(
                onClick = { activity?.let { gateViewModel.purchase(it, jiamin.chen.orangecloud.core.purchase.BillingGateway.PLAN_MONTHLY) } },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.paywall_monthly))
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { activity?.let { gateViewModel.purchase(it, jiamin.chen.orangecloud.core.purchase.BillingGateway.PLAN_LIFETIME) } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.paywall_lifetime))
            }
            Text(stringResource(R.string.paywall_note), color = onSky.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun FeatureLine(text: String, onSky: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = OcOrange, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = onSky.copy(alpha = 0.9f), fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}
