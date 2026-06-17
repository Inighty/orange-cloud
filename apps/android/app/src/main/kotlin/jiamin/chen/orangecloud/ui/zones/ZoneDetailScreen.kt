package jiamin.chen.orangecloud.ui.zones

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jiamin.chen.orangecloud.R
import jiamin.chen.orangecloud.core.design.SkyBackground
import jiamin.chen.orangecloud.core.design.SkyHeader
import jiamin.chen.orangecloud.core.design.onSky
import jiamin.chen.orangecloud.core.design.rememberSkyPhase
import jiamin.chen.orangecloud.core.design.theme.OcOrange

/** 单个域名的工具中枢：分发到 DNS / 分析 /（后续 Snippets / WAF / 设置）。对应 iOS Zone 详情。 */
@Composable
fun ZoneDetailScreen(
    zoneName: String,
    onBack: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenWaf: () -> Unit,
    onOpenSnippets: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val phase = rememberSkyPhase()
    val onSky = phase.onSky

    SkyBackground(phase = phase) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SkyHeader(
                title = zoneName.ifBlank { stringResource(R.string.nav_zones) },
                onSky = onSky,
                isLoading = false,
                onRefresh = {},
                onBack = onBack,
                titleSize = 24,
                backDescription = stringResource(R.string.common_back),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ToolRow(Icons.Outlined.Dns, stringResource(R.string.zone_tool_dns), onOpenDns)
                ToolRow(Icons.Outlined.ShowChart, stringResource(R.string.zone_tool_analytics), onOpenAnalytics)
                ToolRow(Icons.Outlined.Shield, stringResource(R.string.zone_tool_waf), onOpenWaf)
                ToolRow(Icons.Outlined.Code, stringResource(R.string.zone_tool_snippets), onOpenSnippets)
                ToolRow(Icons.Outlined.Tune, stringResource(R.string.zone_tool_settings), onOpenSettings)
            }
        }
    }
}

@Composable
private fun ToolRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = OcOrange, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
