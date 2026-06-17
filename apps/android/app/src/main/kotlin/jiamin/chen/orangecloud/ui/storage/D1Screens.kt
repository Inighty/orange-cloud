package jiamin.chen.orangecloud.ui.storage

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import jiamin.chen.orangecloud.data.model.tailDisplayText

@Composable
fun D1DatabaseListScreen(
    onBack: () -> Unit,
    onOpenDatabase: (id: String, name: String) -> Unit,
    viewModel: D1DatabaseListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky

    SkyBackground(phase = phase) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SkyHeader(
                title = stringResource(R.string.storage_d1),
                onSky = onSky,
                isLoading = state.isLoading,
                onRefresh = { viewModel.load() },
                onBack = onBack,
                titleSize = 22,
                backDescription = stringResource(R.string.common_back),
                refreshDescription = stringResource(R.string.common_refresh),
            )
            StorageListBody(state, onSky, Icons.Outlined.Storage, stringResource(R.string.d1_empty), { viewModel.load() }) { db ->
                StorageRow(
                    Icons.Outlined.Storage,
                    db.name,
                    db.numTables?.let { stringResource(R.string.d1_tables, it) },
                    onClick = { onOpenDatabase(db.uuid, db.name) },
                )
            }
        }
    }
}

@Composable
fun D1QueryScreen(
    onBack: () -> Unit,
    viewModel: D1QueryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky
    var sql by rememberSaveable { mutableStateOf("SELECT name FROM sqlite_master WHERE type='table';") }

    SkyBackground(phase = phase) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SkyHeader(
                title = viewModel.databaseName.ifBlank { stringResource(R.string.storage_d1) },
                onSky = onSky,
                isLoading = state.isRunning,
                onRefresh = { viewModel.run(sql) },
                onBack = onBack,
                titleSize = 22,
                backDescription = stringResource(R.string.common_back),
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.missingScope) {
                    Text(stringResource(R.string.scope_missing), color = onSky.copy(alpha = 0.8f), fontSize = 14.sp)
                    return@Column
                }
                OutlinedTextField(
                    value = sql,
                    onValueChange = { sql = it },
                    label = { Text(stringResource(R.string.d1_sql)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
                Button(
                    onClick = { viewModel.run(sql) },
                    enabled = !state.isRunning && sql.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = OcOrange, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isRunning) {
                        CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.d1_run))
                }
                state.error?.let {
                    Text(it, color = Color(0xFFE5484D), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                if (state.columns.isNotEmpty()) {
                    ResultsTable(state.columns, state.results.firstOrNull()?.results.orEmpty())
                } else if (state.results.isNotEmpty() && state.error == null) {
                    val meta = state.results.first().meta
                    Text(
                        stringResource(R.string.d1_ok, meta?.changes ?: 0),
                        color = onSky.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsTable(
    columns: List<String>,
    rows: List<Map<String, kotlinx.serialization.json.JsonElement>>,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Row {
                columns.forEach { col ->
                    Text(
                        col,
                        modifier = Modifier.width(140.dp).padding(end = 8.dp, bottom = 6.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            rows.take(200).forEach { row ->
                Row {
                    columns.forEach { col ->
                        Text(
                            row[col]?.tailDisplayText() ?: "",
                            modifier = Modifier.width(140.dp).padding(end = 8.dp, bottom = 4.dp),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
