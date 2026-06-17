package jiamin.chen.orangecloud.ui.workers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jiamin.chen.orangecloud.R
import jiamin.chen.orangecloud.core.design.SkyBackground
import jiamin.chen.orangecloud.core.design.SkyHeader
import jiamin.chen.orangecloud.core.design.onSky
import jiamin.chen.orangecloud.core.design.rememberSkyPhase
import jiamin.chen.orangecloud.core.design.theme.OcOrange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun WorkerDetailScreen(
    onBack: () -> Unit,
    onOpenTail: () -> Unit,
    viewModel: WorkerDetailViewModel = hiltViewModel(),
) {
    val worker by viewModel.worker.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky

    SkyBackground(phase = phase) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SkyHeader(
                title = viewModel.scriptName,
                onSky = onSky,
                isLoading = false,
                onRefresh = {},
                onBack = onBack,
                titleSize = 22,
                backDescription = stringResource(R.string.common_back),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onOpenTail,
                    colors = ButtonDefaults.buttonColors(containerColor = OcOrange, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tail_title))
                }

                val metrics by viewModel.metrics.collectAsStateWithLifecycle()
                metrics?.let { m ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.worker_metrics_24h), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                MetricCell(stringResource(R.string.worker_metric_requests), m.requests.toString())
                                MetricCell(stringResource(R.string.worker_metric_errors), m.errors.toString())
                                MetricCell(stringResource(R.string.worker_metric_error_rate), "%.1f%%".format(m.errorRate))
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InfoRow(stringResource(R.string.worker_usage_model), worker?.usageModel ?: "—")
                        InfoRow(
                            stringResource(R.string.worker_logpush),
                            stringResource(if (worker?.logpush == true) R.string.common_on else R.string.common_off),
                        )
                        InfoRow(stringResource(R.string.worker_created), formatIso(worker?.createdOn) ?: "—")
                        InfoRow(stringResource(R.string.worker_modified), formatIso(worker?.modifiedOn) ?: "—")

                        val handlers = worker?.handlers?.takeIf { it.isNotEmpty() }
                        if (handlers != null) {
                            Text(
                                stringResource(R.string.worker_handlers),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Handlers(handlers)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Handlers(handlers: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        handlers.forEach { handler ->
            Box(
                modifier = Modifier
                    .background(OcOrange.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(handler, color = OcOrange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private val isoFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

private fun formatIso(iso: String?): String? {
    if (iso.isNullOrEmpty()) return null
    return runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(isoFormatter)
    }.getOrNull() ?: iso
}
