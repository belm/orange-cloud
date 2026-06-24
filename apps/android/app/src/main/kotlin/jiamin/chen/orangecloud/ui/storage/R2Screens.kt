package jiamin.chen.orangecloud.ui.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import jiamin.chen.orangecloud.core.util.copyToClipboard
import jiamin.chen.orangecloud.data.model.R2Bucket
import jiamin.chen.orangecloud.data.model.R2CorsPolicy
import jiamin.chen.orangecloud.data.model.R2BucketUsage
import jiamin.chen.orangecloud.data.model.R2Object
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
                    canLoadUsage = usageState.canLoadUsage,
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
    canLoadUsage: Boolean,
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
                } else if (canLoadUsage) {
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
    var showNewFolder by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val folderRows = remember(state.objects, state.currentPrefix, state.query) {
        state.objects
            .asSequence()
            .mapNotNull { obj ->
                val relative = obj.key.removePrefix(state.currentPrefix).trimStart('/')
                val folder = relative.substringBefore('/', missingDelimiterValue = "")
                folder.takeIf { relative.contains('/') && it.isNotBlank() && it.contains(state.query, ignoreCase = true) }
            }
            .distinct()
            .sorted()
            .toList()
    }
    val objectRows = remember(state.objects, state.currentPrefix, state.query) {
        state.objects.filter { obj ->
            val relative = obj.key.removePrefix(state.currentPrefix).trimStart('/')
            relative.isNotBlank() &&
                !relative.trimEnd('/').contains('/') &&
                !obj.key.endsWith("/") &&
                obj.key.contains(state.query, ignoreCase = true)
        }
    }
    val hasRows = folderRows.isNotEmpty() || objectRows.isNotEmpty()

    val uploadedMsg = stringResource(R.string.r2_uploaded)
    val deletedMsg = stringResource(R.string.r2_deleted)
    val noAppMsg = stringResource(R.string.r2_no_app)
    val folderCreatedMsg = stringResource(R.string.r2_folder_created)
    val settingsSavedMsg = stringResource(R.string.r2_settings_saved)
    val copiedMsg = stringResource(R.string.r2_object_copied)
    val movedMsg = stringResource(R.string.r2_object_moved)
    val copiedUrlMsg = stringResource(R.string.r2_url_copied)
    val previewTooLargeMsg = stringResource(R.string.r2_preview_too_large)
    val previewUnavailableMsg = stringResource(R.string.r2_preview_unavailable)
    var preview by remember { mutableStateOf<R2Preview?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                R2Event.Uploaded -> snackbarHostState.showSnackbar(uploadedMsg)
                R2Event.Deleted -> { detailObject = null; snackbarHostState.showSnackbar(deletedMsg) }
                R2Event.FolderCreated -> snackbarHostState.showSnackbar(folderCreatedMsg)
                R2Event.SettingsSaved -> snackbarHostState.showSnackbar(settingsSavedMsg)
                R2Event.ObjectCopied -> { detailObject = null; snackbarHostState.showSnackbar(copiedMsg) }
                R2Event.ObjectMoved -> { detailObject = null; snackbarHostState.showSnackbar(movedMsg) }
                is R2Event.Error -> snackbarHostState.showSnackbar(event.message ?: noAppMsg)
            }
        }
    }

    // SAF 多选文件上传
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val docs = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                    val name = queryDisplayName(context, uri) ?: "upload"
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    Triple(name, mime, bytes)
                    }.getOrNull()
                }
            }
            if (docs.isNotEmpty()) viewModel.uploadMany(docs)
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

    fun previewObject(obj: R2Object) {
        scope.launch {
            val size = obj.size ?: 0L
            val contentType = obj.httpMetadata?.contentType.orEmpty()
            val isImage = contentType.startsWith("image/") || obj.key.isImageKey()
            if (!isImage && size > 1_048_576L) {
                snackbarHostState.showSnackbar(previewTooLargeMsg)
                return@launch
            }
            val bytes = viewModel.objectBytes(obj.key) ?: return@launch
            val next = withContext(Dispatchers.IO) {
                when {
                    isImage -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { R2Preview.Image(obj.key, it) }
                    contentType.contains("json", ignoreCase = true) || obj.key.endsWith(".json", ignoreCase = true) ->
                        runCatching { R2Preview.Text(obj.key, formatJsonPreview(bytes.decodeToString()), isJson = true) }.getOrNull()
                    contentType.startsWith("text/") || obj.key.isTextKey() ->
                        runCatching { R2Preview.Text(obj.key, bytes.decodeToString(), isJson = false) }.getOrNull()
                    else -> runCatching { bytes.decodeToString().takeIf { it.isNotBlank() }?.let { R2Preview.Text(obj.key, it, isJson = false) } }.getOrNull()
                }
            }
            if (next == null) snackbarHostState.showSnackbar(previewUnavailableMsg)
            else preview = next
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
                R2ObjectToolbar(
                    state = state,
                    onSearch = viewModel::updateQuery,
                    onUp = viewModel::goUp,
                    onNewFolder = { showNewFolder = true },
                    onSettings = {
                        showSettings = true
                        viewModel.loadSettings()
                    },
                    onClearSelection = viewModel::clearSelection,
                    onDeleteSelected = viewModel::deleteSelected,
                )
                when {
                    state.missingScope ->
                        SkyEmptyState(Icons.Outlined.Lock, stringResource(R.string.scope_missing), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                    state.isLoading ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = onSky) }

                    state.hasError ->
                        SkyEmptyState(Icons.AutoMirrored.Outlined.InsertDriveFile, stringResource(R.string.error_generic), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                    !hasRows ->
                        SkyEmptyState(Icons.AutoMirrored.Outlined.InsertDriveFile, stringResource(R.string.r2_objects_empty), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(folderRows, key = { "folder:$it" }) { folder ->
                            StorageRow(
                                Icons.Outlined.Folder,
                                folder,
                                stringResource(R.string.r2_folder),
                                onClick = { viewModel.openFolder(folder) },
                            )
                        }
                        items(objectRows, key = { it.key }) { obj ->
                            R2ObjectRow(
                                obj = obj,
                                prefix = state.currentPrefix,
                                selected = obj.key in state.selectedKeys,
                                selectionMode = state.selectedKeys.isNotEmpty(),
                                onClick = {
                                    if (state.selectedKeys.isNotEmpty()) viewModel.toggleSelection(obj.key)
                                    else detailObject = obj
                                },
                                onLongClick = { viewModel.toggleSelection(obj.key) },
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

            if (state.isUploading) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 92.dp)
                        .systemBarsPadding()
                        .fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            state.uploadName ?: stringResource(R.string.r2_uploading),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LinearProgressIndicator(progress = { state.uploadProgress ?: 0.2f }, modifier = Modifier.fillMaxWidth())
                        state.uploadQueue.take(4).forEach { item ->
                            Text(
                                "${item.status} · ${item.name}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

    if (showNewFolder) {
        NewFolderDialog(
            isCreating = state.isCreatingFolder,
            onDismiss = { showNewFolder = false },
            onCreate = {
                viewModel.createFolder(it)
                showNewFolder = false
            },
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            R2BucketSettingsSheet(
                bucket = viewModel.bucket,
                state = state.settings,
                canWrite = state.canWrite,
                onRefresh = viewModel::loadSettings,
                onToggleManaged = viewModel::setManagedDomainEnabled,
                onSaveCors = viewModel::saveCorsPolicy,
                onDeleteCors = viewModel::deleteCorsPolicy,
                onRemoveCustomDomain = viewModel::removeCustomDomain,
            )
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
                isMutating = state.isObjectMutating,
                publicHost = state.settings.customDomains.firstOrNull { it.enabled }?.domain
                    ?: state.settings.managedDomain?.takeIf { it.enabled }?.domain,
                onOpen = { openObject(obj) },
                onPreview = { previewObject(obj) },
                onCopyUrl = { host ->
                    copyToClipboard(context, "https://$host/${obj.key}")
                    scope.launch { snackbarHostState.showSnackbar(copiedUrlMsg) }
                },
                onCopyObject = { destination -> viewModel.copyObject(obj.key, destination, obj.httpMetadata?.contentType) },
                onMoveObject = { destination -> viewModel.moveObject(obj.key, destination, obj.httpMetadata?.contentType) },
                onDelete = { viewModel.delete(obj.key) },
            )
        }
    }

    preview?.let { current ->
        ModalBottomSheet(
            onDismissRequest = { preview = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ObjectPreviewSheet(preview = current)
        }
    }
}

@Composable
private fun R2ObjectToolbar(
    state: R2ObjectUiState,
    onSearch: (String) -> Unit,
    onUp: () -> Unit,
    onNewFolder: () -> Unit,
    onSettings: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUp, enabled = state.currentPrefix.isNotBlank(), modifier = Modifier.weight(1f)) {
                Text(state.currentPrefix.ifBlank { stringResource(R.string.r2_root) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onNewFolder, enabled = state.canWrite) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = stringResource(R.string.r2_new_folder))
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.r2_bucket_settings))
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearch,
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.r2_search_objects)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.selectedKeys.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.r2_selected_count, state.selectedKeys.size),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearSelection) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = onDeleteSelected,
                        enabled = !state.isBatchDeleting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D), contentColor = Color.White),
                    ) {
                        Text(stringResource(R.string.r2_delete_selected))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun R2ObjectRow(
    obj: R2Object,
    prefix: String,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (selected) cs.primaryContainer.copy(alpha = 0.72f) else cs.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.AutoMirrored.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = if (selected) cs.primary else OcOrange,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    obj.key.removePrefix(prefix).ifBlank { obj.key },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = cs.onSurface,
                )
                Text(
                    listOfNotNull(obj.size?.let { formatBytes(it) }, obj.lastModified).joinToString(" · "),
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NewFolderDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.r2_new_folder)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.r2_folder_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name) }, enabled = name.isNotBlank() && !isCreating) {
                Text(stringResource(R.string.common_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun R2BucketSettingsSheet(
    bucket: String,
    state: R2BucketSettingsUiState,
    canWrite: Boolean,
    onRefresh: () -> Unit,
    onToggleManaged: (Boolean) -> Unit,
    onSaveCors: (R2CorsPolicy) -> Unit,
    onDeleteCors: () -> Unit,
    onRemoveCustomDomain: (String) -> Unit,
) {
    val context = LocalContext.current
    val json = remember { Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true } }
    var corsText by remember { mutableStateOf("") }
    var corsError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.corsPolicy) {
        corsText = state.corsPolicy?.let { json.encodeToString(R2CorsPolicy.serializer(), it) } ?: """{"rules":[]}"""
        corsError = null
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bucket, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.QueryStats, contentDescription = stringResource(R.string.common_refresh))
            }
        }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.hasError) Text(stringResource(R.string.error_generic), color = MaterialTheme.colorScheme.error)

        SettingsBlock(title = stringResource(R.string.r2_managed_domain)) {
            val managed = state.managedDomain
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(managed?.domain?.takeIf { it.isNotBlank() } ?: stringResource(R.string.r2_domain_unavailable), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (managed?.enabled == true) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                if (!managed?.domain.isNullOrBlank()) {
                    IconButton(onClick = { copyToClipboard(context, "https://${managed?.domain}") }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.common_copy))
                    }
                }
                Switch(
                    checked = managed?.enabled == true,
                    onCheckedChange = onToggleManaged,
                    enabled = canWrite && !state.isSaving,
                )
            }
        }

        SettingsBlock(title = stringResource(R.string.r2_custom_domains)) {
            if (state.customDomains.isEmpty()) {
                Text(stringResource(R.string.r2_custom_domains_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                state.customDomains.forEach { domain ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(domain.domain, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    if (domain.enabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                                    domain.status?.ownership,
                                    domain.status?.ssl,
                                ).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { copyToClipboard(context, "https://${domain.domain}") }) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.common_copy))
                        }
                        if (canWrite) {
                            TextButton(onClick = { onRemoveCustomDomain(domain.domain) }, enabled = !state.isSaving) {
                                Text(stringResource(R.string.common_remove), color = Color(0xFFE5484D))
                            }
                        }
                    }
                }
            }
        }

        SettingsBlock(title = stringResource(R.string.r2_cors_policy)) {
            Text(stringResource(R.string.r2_cors_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            OutlinedTextField(
                value = corsText,
                onValueChange = { corsText = it; corsError = null },
                minLines = 7,
                maxLines = 12,
                isError = corsError != null,
                supportingText = corsError?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 180.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        val policy = runCatching { json.decodeFromString(R2CorsPolicy.serializer(), corsText) }
                            .onFailure { corsError = it.message ?: "Invalid JSON" }
                            .getOrNull()
                        if (policy != null) onSaveCors(policy)
                    },
                    enabled = canWrite && !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_save)) }
                OutlinedButton(
                    onClick = onDeleteCors,
                    enabled = canWrite && !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.r2_cors_clear)) }
            }
        }
    }
}

@Composable
private fun SettingsBlock(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun ObjectDetail(
    obj: R2Object,
    canWrite: Boolean,
    isDownloading: Boolean,
    isMutating: Boolean,
    publicHost: String?,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onCopyObject: (String) -> Unit,
    onMoveObject: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<R2ObjectAction?>(null) }
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPreview, enabled = !isDownloading, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Preview, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.r2_preview))
            }
            OutlinedButton(onClick = { publicHost?.let(onCopyUrl) }, enabled = !publicHost.isNullOrBlank(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.r2_copy_url))
            }
        }

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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { action = R2ObjectAction.Copy }, enabled = !isMutating, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.r2_copy_object))
                }
                OutlinedButton(onClick = { action = R2ObjectAction.Move }, enabled = !isMutating, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.r2_move_object))
                }
            }
            OutlinedButton(onClick = { action = R2ObjectAction.Rename }, enabled = !isMutating, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.r2_rename_object))
            }
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

    action?.let { current ->
        ObjectMutationDialog(
            action = current,
            currentKey = obj.key,
            isSaving = isMutating,
            onDismiss = { action = null },
            onConfirm = { destination ->
                when (current) {
                    R2ObjectAction.Copy -> onCopyObject(destination)
                    R2ObjectAction.Move, R2ObjectAction.Rename -> onMoveObject(destination)
                }
                action = null
            },
        )
    }
}

private enum class R2ObjectAction { Copy, Move, Rename }

@Composable
private fun ObjectMutationDialog(
    action: R2ObjectAction,
    currentKey: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var key by remember(action, currentKey) {
        mutableStateOf(
            when (action) {
                R2ObjectAction.Copy -> currentKey.substringBeforeLast('/', missingDelimiterValue = "").let { prefix ->
                    val name = currentKey.substringAfterLast('/')
                    if (prefix.isBlank()) "copy-$name" else "$prefix/copy-$name"
                }
                R2ObjectAction.Move, R2ObjectAction.Rename -> currentKey
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (action) {
                        R2ObjectAction.Copy -> R.string.r2_copy_object
                        R2ObjectAction.Move -> R.string.r2_move_object
                        R2ObjectAction.Rename -> R.string.r2_rename_object
                    },
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.r2_destination_key)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(key.trim()) }, enabled = key.isNotBlank() && key != currentKey && !isSaving) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private sealed interface R2Preview {
    val title: String

    data class Image(override val title: String, val bitmap: Bitmap) : R2Preview
    data class Text(override val title: String, val text: String, val isJson: Boolean) : R2Preview
}

@Composable
private fun ObjectPreviewSheet(preview: R2Preview) {
    var query by remember(preview.title) { mutableStateOf("") }
    val displayText = (preview as? R2Preview.Text)?.text.orEmpty()
    val matchCount = remember(displayText, query) {
        if (query.isBlank()) 0 else Regex.escape(query).toRegex(RegexOption.IGNORE_CASE).findAll(displayText).count()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(preview.title, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
        when (preview) {
            is R2Preview.Image -> {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
                    Image(
                        bitmap = preview.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(460.dp).padding(10.dp),
                    )
                }
            }
            is R2Preview.Text -> {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.r2_preview_search)) },
                    supportingText = {
                        if (query.isNotBlank()) Text(stringResource(R.string.r2_preview_matches, matchCount))
                        else Text(if (preview.isJson) stringResource(R.string.r2_preview_json) else stringResource(R.string.r2_preview_text))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
                    Text(
                        preview.text.take(80_000),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun String.isImageKey(): Boolean =
    endsWith(".png", true) || endsWith(".jpg", true) || endsWith(".jpeg", true) || endsWith(".webp", true) || endsWith(".gif", true)

private fun String.isTextKey(): Boolean =
    listOf(".txt", ".log", ".md", ".csv", ".xml", ".html", ".css", ".js", ".ts", ".yaml", ".yml").any { endsWith(it, true) }

private fun formatJsonPreview(raw: String): String {
    val parser = Json { ignoreUnknownKeys = true }
    val pretty = Json { prettyPrint = true }
    val element = parser.decodeFromString(JsonElement.serializer(), raw)
    return pretty.encodeToString(JsonElement.serializer(), element)
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
