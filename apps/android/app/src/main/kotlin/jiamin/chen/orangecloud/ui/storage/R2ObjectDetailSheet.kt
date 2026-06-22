package jiamin.chen.orangecloud.ui.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jiamin.chen.orangecloud.R
import jiamin.chen.orangecloud.core.design.theme.OcOrange
import jiamin.chen.orangecloud.data.model.R2Object

data class R2ObjectDetailState(
    val obj: R2Object,
    val canWrite: Boolean,
    val isDownloading: Boolean,
)

data class R2ObjectDetailActions(
    val onOpen: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
fun ObjectDetail(state: R2ObjectDetailState, actions: R2ObjectDetailActions) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ObjectTitle(state.obj.key)
        ObjectMetadata(state.obj)
        Spacer(Modifier.height(4.dp))
        DownloadButton(state.isDownloading, actions.onOpen)
        if (state.canWrite) {
            DeleteControl(confirmDelete, { confirmDelete = it }, actions.onDelete)
        }
    }
}

@Composable
private fun ObjectTitle(key: String) {
    Text(
        key,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ObjectMetadata(obj: R2Object) {
    obj.size?.let { MetaRow(stringResource(R.string.r2_meta_size), formatBytes(it)) }
    obj.httpMetadata?.contentType?.let { MetaRow(stringResource(R.string.r2_meta_type), it) }
    obj.storageClass?.let { MetaRow(stringResource(R.string.r2_meta_class), it) }
    obj.etag?.let { MetaRow(stringResource(R.string.r2_meta_etag), it, mono = true) }
    obj.lastModified?.let { MetaRow(stringResource(R.string.r2_meta_modified), it, mono = true) }
}

@Composable
private fun DownloadButton(isDownloading: Boolean, onOpen: () -> Unit) {
    Button(
        onClick = onOpen,
        enabled = !isDownloading,
        colors = ButtonDefaults.buttonColors(containerColor = OcOrange, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isDownloading) {
            CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.height(18.dp).width(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.r2_download_open))
    }
}

@Composable
private fun DeleteControl(
    confirmDelete: Boolean,
    onConfirmChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    if (confirmDelete) {
        DeleteConfirmRow(onConfirmChange, onDelete)
        return
    }
    TextButton(onClick = { onConfirmChange(true) }, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = null,
            tint = Color(0xFFE5484D),
            modifier = Modifier.height(18.dp).width(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.r2_delete), color = Color(0xFFE5484D))
    }
}

@Composable
private fun DeleteConfirmRow(onConfirmChange: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onConfirmChange(false) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.common_cancel))
        }
        Button(
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D), contentColor = Color.White),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.r2_delete))
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = if (mono) 12.sp else 13.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End,
        )
    }
}
