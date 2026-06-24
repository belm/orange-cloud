package jiamin.chen.orangecloud.ui.workers

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import jiamin.chen.orangecloud.core.util.copyToClipboard
import androidx.compose.ui.platform.LocalContext

private val ConsoleBg = Color(0xFF1A1410)

@Composable
fun WorkerTailScreen(
    onBack: () -> Unit,
    viewModel: WorkerTailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf("all") }
    val visibleLines = remember(lines, query, levelFilter) {
        lines.filter { line ->
            (levelFilter == "all" || line.level == levelFilter) &&
                (query.isBlank() || line.text.contains(query, ignoreCase = true))
        }
    }

    LaunchedEffect(visibleLines.size, paused) {
        if (!paused && visibleLines.isNotEmpty()) listState.animateScrollToItem(visibleLines.lastIndex)
    }

    SkyBackground(phase = phase) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SkyHeader(
                title = viewModel.scriptName,
                onSky = onSky,
                isLoading = false,
                onRefresh = {},
                onBack = onBack,
                titleSize = 20,
                backDescription = stringResource(R.string.common_back),
            )

            if (viewModel.missingScope) {
                SkyEmptyState(Icons.Outlined.Lock, stringResource(R.string.scope_missing), onSky, stringResource(R.string.common_refresh)) {}
                return@Column
            }

            StatusBar(state, paused, onSky, viewModel::togglePause, viewModel::clear, viewModel::start)
            TailFilters(
                query = query,
                onQuery = { query = it },
                levelFilter = levelFilter,
                onLevel = { levelFilter = it },
                onSky = onSky,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ConsoleBg),
            ) {
                if (visibleLines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tail_waiting),
                        color = Color(0xFF8A8178),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(visibleLines, key = { it.id }) { line ->
                            LogRow(line, onCopy = { copyToClipboard(context, line.text) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TailFilters(
    query: String,
    onQuery: (String) -> Unit,
    levelFilter: String,
    onLevel: (String) -> Unit,
    onSky: Color,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            label = { Text(stringResource(R.string.tail_search)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all", "event", "log", "warn", "error", "exception").forEach { level ->
                Surface(
                    color = if (levelFilter == level) OcOrange else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable { onLevel(level) },
                ) {
                    Text(
                        text = level,
                        color = if (levelFilter == level) Color.White else onSky,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBar(
    state: TailConnState,
    paused: Boolean,
    onSky: Color,
    onPause: () -> Unit,
    onClear: () -> Unit,
    onReconnect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(state)
        Spacer(Modifier.width(8.dp))
        Text(statusText(state), color = onSky, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (state is TailConnState.Disconnected) {
            TextButton(onClick = onReconnect) { Text(stringResource(R.string.tail_reconnect)) }
        } else {
            IconButton(onClick = onPause) {
                Icon(
                    if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = stringResource(if (paused) R.string.tail_resume else R.string.tail_pause),
                    tint = onSky,
                )
            }
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.tail_clear), tint = onSky)
        }
    }
}

@Composable
private fun StatusDot(state: TailConnState) {
    when (state) {
        is TailConnState.Connecting -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = OcOrange)
        else -> Box(
            Modifier.size(10.dp).clip(CircleShape).background(
                when (state) {
                    TailConnState.Connected -> Color(0xFF2FBF71)
                    is TailConnState.Disconnected -> Color(0xFFE5484D)
                    else -> Color(0xFF9AA0A6)
                },
            ),
        )
    }
}

@Composable
private fun LogRow(line: TailLogLine, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = line.text,
            color = levelColor(line.level),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.common_copy), tint = Color(0xFF8A8178), modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun statusText(state: TailConnState): String = stringResource(
    when (state) {
        TailConnState.Idle -> R.string.tail_connecting
        TailConnState.Connecting -> R.string.tail_connecting
        TailConnState.Connected -> R.string.tail_connected
        is TailConnState.Disconnected -> R.string.tail_disconnected
    },
)

private fun levelColor(level: String): Color = when (level) {
    "event" -> OcOrange
    "error", "exception" -> Color(0xFFFF6B6B)
    "warn" -> Color(0xFFF5C451)
    else -> Color(0xFFD8CFC4)
}
