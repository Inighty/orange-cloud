package jiamin.chen.orangecloud.ui.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jiamin.chen.orangecloud.R
import jiamin.chen.orangecloud.core.design.SkyBackground
import jiamin.chen.orangecloud.core.design.SkyEmptyState
import jiamin.chen.orangecloud.core.design.SkyHeader
import jiamin.chen.orangecloud.core.design.onSky
import jiamin.chen.orangecloud.core.design.rememberSkyPhase

@Composable
fun R2BucketListScreen(
    onBack: () -> Unit,
    onOpenBucket: (String) -> Unit,
    viewModel: R2BucketListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky

    SkyBackground(phase = phase) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SkyHeader(
                title = stringResource(R.string.storage_r2),
                onSky = onSky,
                isLoading = state.isLoading,
                onRefresh = { viewModel.load() },
                onBack = onBack,
                titleSize = 22,
                backDescription = stringResource(R.string.common_back),
                refreshDescription = stringResource(R.string.common_refresh),
            )
            StorageListBody(state, onSky, Icons.Outlined.Cloud, stringResource(R.string.r2_empty), { viewModel.load() }) { bucket ->
                StorageRow(Icons.Outlined.Cloud, bucket.name, bucket.location, onClick = { onOpenBucket(bucket.name) })
            }
        }
    }
}

@Composable
fun R2ObjectListScreen(
    onBack: () -> Unit,
    viewModel: R2ObjectListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky

    SkyBackground(phase = phase) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SkyHeader(
                title = viewModel.bucket,
                onSky = onSky,
                isLoading = state.isLoading,
                onRefresh = { viewModel.loadFirst() },
                onBack = onBack,
                titleSize = 22,
                backDescription = stringResource(R.string.common_back),
                refreshDescription = stringResource(R.string.common_refresh),
            )
            when {
                state.missingScope ->
                    SkyEmptyState(Icons.Outlined.Lock, stringResource(R.string.scope_missing), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                state.objects.isEmpty() && state.isLoading ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = onSky) }

                state.objects.isEmpty() && state.hasError ->
                    SkyEmptyState(Icons.Outlined.InsertDriveFile, stringResource(R.string.error_generic), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                state.objects.isEmpty() ->
                    SkyEmptyState(Icons.Outlined.InsertDriveFile, stringResource(R.string.r2_objects_empty), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.objects, key = { it.key }) { obj ->
                        StorageRow(
                            Icons.Outlined.InsertDriveFile,
                            obj.key,
                            obj.size?.let { formatBytes(it) },
                            showChevron = false,
                        )
                    }
                    if (state.hasMore) {
                        item {
                            OutlinedButton(
                                onClick = { viewModel.loadMore() },
                                enabled = !state.isLoadingMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(if (state.isLoadingMore) R.string.common_loading else R.string.common_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}
