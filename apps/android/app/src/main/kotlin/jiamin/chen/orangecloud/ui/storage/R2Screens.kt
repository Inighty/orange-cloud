package jiamin.chen.orangecloud.ui.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jiamin.chen.orangecloud.R
import jiamin.chen.orangecloud.core.design.SkyBackground
import jiamin.chen.orangecloud.core.design.SkyEmptyState
import jiamin.chen.orangecloud.core.design.SkyHeader
import jiamin.chen.orangecloud.core.design.onSky
import jiamin.chen.orangecloud.core.design.rememberSkyPhase
import jiamin.chen.orangecloud.core.design.theme.OcOrange
import jiamin.chen.orangecloud.data.model.R2Object
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun R2ObjectListScreen(
    onBack: () -> Unit,
    viewModel: R2ObjectListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var detailObject by remember { mutableStateOf<R2Object?>(null) }

    val uploadedMsg = stringResource(R.string.r2_uploaded)
    val deletedMsg = stringResource(R.string.r2_deleted)
    val noAppMsg = stringResource(R.string.r2_no_app)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                R2Event.Uploaded -> snackbarHostState.showSnackbar(uploadedMsg)
                R2Event.Deleted -> { detailObject = null; snackbarHostState.showSnackbar(deletedMsg) }
                is R2Event.Error -> snackbarHostState.showSnackbar(event.message ?: noAppMsg)
            }
        }
    }

    // SAF 选取任意文件上传
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val doc = withContext(Dispatchers.IO) {
                runCatching {
                    val name = queryDisplayName(context, uri) ?: "upload"
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    Triple(name, mime, bytes)
                }.getOrNull()
            }
            if (doc != null) viewModel.upload(doc.first, doc.second, doc.third)
            else snackbarHostState.showSnackbar(noAppMsg)
        }
    }

    fun openObject(obj: R2Object) {
        scope.launch {
            val bytes = viewModel.objectBytes(obj.key) ?: return@launch
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "r2").apply { mkdirs() }
                    val file = File(dir, obj.key.substringAfterLast('/').ifBlank { "file" })
                    file.writeBytes(bytes)
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrNull()
            }
            if (uri == null) {
                snackbarHostState.showSnackbar(noAppMsg)
                return@launch
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, obj.httpMetadata?.contentType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }.onFailure {
                snackbarHostState.showSnackbar(noAppMsg)
            }
        }
    }

    SkyBackground(phase = phase) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                SkyHeader(
                    title = viewModel.title(state.currentPrefix),
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

                    state.isContentEmpty && state.isLoading ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = onSky) }

                    state.isContentEmpty && state.hasError ->
                        SkyEmptyState(Icons.AutoMirrored.Outlined.InsertDriveFile, stringResource(R.string.error_generic), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                    state.isContentEmpty && state.currentPrefix.isEmpty() ->
                        SkyEmptyState(Icons.AutoMirrored.Outlined.InsertDriveFile, stringResource(R.string.r2_objects_empty), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.currentPrefix.isNotEmpty()) {
                            item {
                                StorageRow(
                                    Icons.Outlined.KeyboardArrowUp,
                                    "..",
                                    state.currentPrefix,
                                    onClick = { viewModel.openParentFolder() },
                                )
                            }
                        }
                        items(state.folders, key = { "folder:${it.prefix}" }) { folder ->
                            StorageRow(
                                Icons.Outlined.Folder,
                                folder.name,
                                folder.prefix,
                                onClick = { viewModel.openFolder(folder) },
                            )
                        }
                        items(state.objects, key = { "object:${it.key}" }) { obj ->
                            StorageRow(
                                Icons.AutoMirrored.Outlined.InsertDriveFile,
                                obj.key,
                                obj.size?.let { formatBytes(it) },
                                onClick = { detailObject = obj },
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

            if (state.canWrite) {
                FloatingActionButton(
                    onClick = { if (!state.isUploading) picker.launch(arrayOf("*/*")) },
                    containerColor = OcOrange,
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).systemBarsPadding(),
                ) {
                    if (state.isUploading) {
                        CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Outlined.Upload, contentDescription = stringResource(R.string.r2_upload))
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).systemBarsPadding())
        }
    }

    detailObject?.let { obj ->
        ModalBottomSheet(
            onDismissRequest = { detailObject = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ObjectDetail(
                state = R2ObjectDetailState(
                    obj = obj,
                    canWrite = state.canWrite,
                    isDownloading = state.isDownloading,
                ),
                actions = R2ObjectDetailActions(
                    onOpen = { openObject(obj) },
                    onDelete = { viewModel.delete(obj.key) },
                ),
            )
        }
    }
}

/** content:// 文档显示名（上传时保留原文件名）。 */
private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
