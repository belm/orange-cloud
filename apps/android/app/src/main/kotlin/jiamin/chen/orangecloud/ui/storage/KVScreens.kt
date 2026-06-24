package jiamin.chen.orangecloud.ui.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import jiamin.chen.orangecloud.core.design.SkyEmptyState
import jiamin.chen.orangecloud.core.design.SkyHeader
import jiamin.chen.orangecloud.core.design.onSky
import jiamin.chen.orangecloud.core.design.rememberSkyPhase
import jiamin.chen.orangecloud.core.design.theme.OcOrange
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Composable
fun KVNamespaceListScreen(
    onBack: () -> Unit,
    onOpenNamespace: (id: String, title: String) -> Unit,
    viewModel: KVNamespaceListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky

    SkyBackground(phase = phase) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SkyHeader(
                title = stringResource(R.string.storage_kv),
                onSky = onSky,
                isLoading = state.isLoading,
                onRefresh = { viewModel.load() },
                onBack = onBack,
                titleSize = 22,
                backDescription = stringResource(R.string.common_back),
                refreshDescription = stringResource(R.string.common_refresh),
            )
            StorageListBody(state, onSky, Icons.Outlined.Key, stringResource(R.string.kv_empty), { viewModel.load() }) { ns ->
                StorageRow(Icons.Outlined.Key, ns.title, ns.id, onClick = { onOpenNamespace(ns.id, ns.title) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KVKeyListScreen(
    onBack: () -> Unit,
    viewModel: KVKeyListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky
    val snackbarHostState = remember { SnackbarHostState() }
    var editKey by remember { mutableStateOf<String?>(null) }   // 现有键
    var creating by remember { mutableStateOf(false) }
    var prefix by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visibleKeys = remember(state.keys, state.query, prefix) {
        state.keys.filter {
            it.name.contains(state.query, ignoreCase = true) &&
                (prefix.isBlank() || it.name.startsWith(prefix))
        }
    }
    val prefixes = remember(state.keys) {
        state.keys.mapNotNull { key ->
            key.name.substringBefore('/', missingDelimiterValue = "").takeIf { it.isNotBlank() && key.name.contains('/') }?.plus("/")
        }.distinct().sorted()
    }

    val savedMsg = stringResource(R.string.kv_saved)
    val deletedMsg = stringResource(R.string.dns_deleted)
    val genericErr = stringResource(R.string.error_generic)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                KVEvent.Saved -> { editKey = null; creating = false; snackbarHostState.showSnackbar(savedMsg) }
                KVEvent.Deleted -> { editKey = null; creating = false; snackbarHostState.showSnackbar(deletedMsg) }
                is KVEvent.Error -> snackbarHostState.showSnackbar(event.cfMessage ?: genericErr)
            }
        }
    }

    SkyBackground(phase = phase) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                SkyHeader(
                    title = viewModel.namespaceTitle.ifBlank { stringResource(R.string.storage_kv) },
                    onSky = onSky,
                    isLoading = state.isLoading,
                    onRefresh = { viewModel.loadFirst() },
                    onBack = onBack,
                    titleSize = 22,
                    backDescription = stringResource(R.string.common_back),
                    refreshDescription = stringResource(R.string.common_refresh),
                )
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::updateQuery,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        label = { Text(stringResource(R.string.kv_search)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrefixChip(stringResource(R.string.r2_root), prefix.isBlank()) { prefix = "" }
                        prefixes.forEach { item -> PrefixChip(item, prefix == item) { prefix = item } }
                    }
                    if (state.selectedKeys.isNotEmpty()) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.kv_selected_count, state.selectedKeys.size), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSecondaryContainer)
                                TextButton(onClick = viewModel::clearSelection) { Text(stringResource(R.string.common_cancel)) }
                                Button(onClick = viewModel::deleteSelected, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D), contentColor = Color.White)) {
                                    Icon(Icons.Outlined.Delete, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.kv_delete_selected))
                                }
                            }
                        }
                    }
                }
                when {
                    state.missingScope ->
                        SkyEmptyState(Icons.Outlined.Lock, stringResource(R.string.scope_missing), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                    state.keys.isEmpty() && state.isLoading ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = onSky) }

                    state.keys.isEmpty() && state.hasError ->
                        SkyEmptyState(Icons.Outlined.Key, stringResource(R.string.error_generic), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                    visibleKeys.isEmpty() ->
                        SkyEmptyState(Icons.Outlined.Key, stringResource(R.string.kv_keys_empty), onSky, stringResource(R.string.common_refresh)) { viewModel.loadFirst() }

                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visibleKeys, key = { it.name }) { kvKey ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.selectedKeys.isNotEmpty()) {
                                    Checkbox(checked = kvKey.name in state.selectedKeys, onCheckedChange = { viewModel.toggleSelection(kvKey.name) })
                                }
                                Box(Modifier.weight(1f)) {
                                    StorageRow(
                                        Icons.Outlined.Key,
                                        kvKey.name,
                                        showChevron = state.canWrite,
                                        onClick = {
                                            if (state.selectedKeys.isNotEmpty()) viewModel.toggleSelection(kvKey.name) else editKey = kvKey.name
                                        },
                                        onLongClick = { viewModel.toggleSelection(kvKey.name) },
                                    )
                                }
                            }
                        }
                        if (state.hasMore) {
                            item {
                                OutlinedButton(onClick = { viewModel.loadMore() }, enabled = !state.isLoadingMore, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(if (state.isLoadingMore) R.string.common_loading else R.string.common_load_more))
                                }
                            }
                        }
                    }
                }
            }

            if (state.canWrite) {
                FloatingActionButton(
                    onClick = { creating = true },
                    containerColor = OcOrange,
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.kv_add))
                }
            }

            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (editKey != null || creating) {
        KVValueSheet(
            existingKey = editKey,
            sheetState = sheetState,
            canWrite = state.canWrite,
            loadValue = { viewModel.loadValue(it) },
            onSave = { key, value -> viewModel.saveValue(key, value) },
            onDelete = { viewModel.deleteKey(it) },
            onDismiss = { editKey = null; creating = false },
        )
    }
}

@Composable
private fun PrefixChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) OcOrange else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KVValueSheet(
    existingKey: String?,
    sheetState: androidx.compose.material3.SheetState,
    canWrite: Boolean,
    loadValue: suspend (String) -> String?,
    onSave: (key: String, value: String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyText by remember(existingKey) { mutableStateOf(existingKey.orEmpty()) }
    var valueText by remember(existingKey) { mutableStateOf("") }
    var loading by remember(existingKey) { mutableStateOf(existingKey != null) }

    LaunchedEffect(existingKey) {
        if (existingKey != null) {
            valueText = loadValue(existingKey) ?: ""
            loading = false
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(if (existingKey == null) R.string.kv_new_key else R.string.kv_edit_key),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                label = { Text(stringResource(R.string.kv_key)) },
                singleLine = true,
                enabled = existingKey == null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (loading) {
                Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) { CircularProgressIndicator() }
            } else {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text(stringResource(R.string.kv_value)) },
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
                OutlinedButton(
                    onClick = { valueText = formatKvJson(valueText) },
                    enabled = valueText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.kv_format_json))
                }
            }
            if (canWrite) {
                Button(
                    onClick = { onSave(keyText.trim(), valueText) },
                    enabled = keyText.isNotBlank() && !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = OcOrange, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dns_save))
                }
                if (existingKey != null) {
                    OutlinedButton(onClick = { onDelete(existingKey) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.dns_delete))
                    }
                }
            } else {
                Text(stringResource(R.string.kv_readonly), color = Color(0xFF8A8178), fontSize = 13.sp)
            }
        }
    }
}

private fun formatKvJson(raw: String): String = runCatching {
    val parser = Json { ignoreUnknownKeys = true }
    val pretty = Json { prettyPrint = true }
    pretty.encodeToString(JsonElement.serializer(), parser.decodeFromString(JsonElement.serializer(), raw))
}.getOrDefault(raw)
