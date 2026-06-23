package jiamin.chen.orangecloud.ui.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import jiamin.chen.orangecloud.data.model.R2Bucket
import jiamin.chen.orangecloud.data.model.R2BucketUsage
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
    val usageState by viewModel.usageState.collectAsStateWithLifecycle()
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
                R2BucketRow(
                    bucket = bucket,
                    usage = usageState.usageByBucket[bucket.name],
                    isUsageLoading = usageState.isLoading,
                    onClick = { onOpenBucket(bucket.name) },
                )
            }
        }
    }
}

@Composable
private fun R2BucketRow(
    bucket: R2Bucket,
    usage: R2BucketUsage?,
    isUsageLoading: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(OcOrange.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Cloud, contentDescription = null, tint = OcOrange, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        bucket.name,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    bucket.location?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (usage != null) {
                    Text(
                        "${stringResource(R.string.r2_usage_storage)} ${formatBytes(usage.storageBytes)} · ${stringResource(R.string.r2_usage_objects)} ${formatCompactCount(usage.objectCount)}",
                        fontSize = 12.5.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UsagePill("A ${formatCompactCount(usage.classARequests)}")
                        UsagePill("B ${formatCompactCount(usage.classBRequests)}")
                        UsagePill("${stringResource(R.string.r2_usage_month)} ${formatCompactCount(usage.totalRequests)}")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.QueryStats, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isUsageLoading) stringResource(R.string.r2_usage_loading) else stringResource(R.string.r2_usage_unavailable),
                            fontSize = 12.5.sp,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun UsagePill(text: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = text,
        color = cs.primary,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(cs.primaryContainer.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000.0)
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
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
                        SkyEmptyState(Icons.AutoMirrored.Outlined.InsertDriveFile, stringResource(R.string.error_generic), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                    state.objects.isEmpty() ->
                        SkyEmptyState(Icons.AutoMirrored.Outlined.InsertDriveFile, stringResource(R.string.r2_objects_empty), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.objects, key = { it.key }) { obj ->
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
                obj = obj,
                canWrite = state.canWrite,
                isDownloading = state.isDownloading,
                onOpen = { openObject(obj) },
                onDelete = { viewModel.delete(obj.key) },
            )
        }
    }
}

@Composable
private fun ObjectDetail(
    obj: R2Object,
    canWrite: Boolean,
    isDownloading: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            obj.key,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        obj.size?.let { MetaRow(stringResource(R.string.r2_meta_size), formatBytes(it)) }
        obj.httpMetadata?.contentType?.let { MetaRow(stringResource(R.string.r2_meta_type), it) }
        obj.storageClass?.let { MetaRow(stringResource(R.string.r2_meta_class), it) }
        obj.etag?.let { MetaRow(stringResource(R.string.r2_meta_etag), it, mono = true) }
        obj.lastModified?.let { MetaRow(stringResource(R.string.r2_meta_modified), it, mono = true) }

        Spacer(Modifier.height(4.dp))

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

        if (canWrite) {
            if (confirmDelete) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmDelete = false }, modifier = Modifier.weight(1f)) {
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
            } else {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFE5484D), modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.r2_delete), color = Color(0xFFE5484D))
                }
            }
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
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** content:// 文档显示名（上传时保留原文件名）。 */
private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
