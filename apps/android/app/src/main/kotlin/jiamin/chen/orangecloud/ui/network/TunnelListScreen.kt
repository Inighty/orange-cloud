package jiamin.chen.orangecloud.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jiamin.chen.orangecloud.R
import jiamin.chen.orangecloud.core.design.SkyBackground
import jiamin.chen.orangecloud.core.design.SkyHeader
import jiamin.chen.orangecloud.core.design.onSky
import jiamin.chen.orangecloud.core.design.rememberSkyPhase
import jiamin.chen.orangecloud.data.model.Tunnel
import jiamin.chen.orangecloud.ui.storage.StorageListBody

@Composable
fun TunnelListScreen(
    onBack: () -> Unit,
    viewModel: TunnelListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky

    SkyBackground(phase = phase) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SkyHeader(
                title = stringResource(R.string.tunnel_title),
                onSky = onSky,
                isLoading = state.isLoading,
                onRefresh = { viewModel.load() },
                onBack = onBack,
                titleSize = 22,
                backDescription = stringResource(R.string.common_back),
                refreshDescription = stringResource(R.string.common_refresh),
            )
            StorageListBody(state, onSky, Icons.Outlined.Hub, stringResource(R.string.tunnel_empty), { viewModel.load() }) { tunnel ->
                TunnelRow(tunnel)
            }
        }
    }
}

@Composable
private fun TunnelRow(tunnel: Tunnel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(tunnel.status)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tunnel.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${stringResource(statusLabel(tunnel.status))} · ${stringResource(R.string.tunnel_connections, tunnel.activeConnections)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun statusColor(status: String?): Color = when (status) {
    "healthy" -> Color(0xFF2FBF71)
    "degraded" -> Color(0xFFF5A623)
    "down" -> Color(0xFFE5484D)
    else -> Color(0xFF9AA0A6)
}

private fun statusLabel(status: String?): Int = when (status) {
    "healthy" -> R.string.tunnel_status_healthy
    "degraded" -> R.string.tunnel_status_degraded
    "down" -> R.string.tunnel_status_down
    "inactive" -> R.string.tunnel_status_inactive
    else -> R.string.tunnel_status_unknown
}
