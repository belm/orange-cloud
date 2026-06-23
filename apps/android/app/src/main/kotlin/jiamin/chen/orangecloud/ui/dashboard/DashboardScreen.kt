package jiamin.chen.orangecloud.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import jiamin.chen.orangecloud.core.design.StatTile
import jiamin.chen.orangecloud.core.design.StatusDot
import jiamin.chen.orangecloud.core.design.ZoneAvatar
import jiamin.chen.orangecloud.core.design.onSky
import jiamin.chen.orangecloud.core.design.rememberSkyPhase
import jiamin.chen.orangecloud.core.design.theme.OcSuccess
import jiamin.chen.orangecloud.data.model.Account
import jiamin.chen.orangecloud.data.model.Zone
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    onOpenTunnels: () -> Unit,
    onOpenZones: () -> Unit,
    onOpenWorkers: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenZone: (Zone) -> Unit,
    onAddAccount: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = rememberSkyPhase()
    val onSky = phase.onSky
    val cs = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val todayLabel = remember {
        val locale = Locale.getDefault()
        val pattern = if (locale.language == Locale.CHINESE.language) "M月d日 EEEE" else "MMM d, EEEE"
        LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    val greeting = timeGreeting()

    SkyBackground(phase = phase) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 0.dp),
            ) {
                // 顶栏：账号头像（点开切换菜单）
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 18.dp, top = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(todayLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = onSky.copy(alpha = 0.66f))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(greeting),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSky,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(cs.surfaceContainerHigh.copy(alpha = 0.62f))
                            .border(1.dp, cs.outlineVariant.copy(alpha = 0.42f), RoundedCornerShape(15.dp))
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.accountName.isNotBlank()) {
                            ZoneAvatar(state.accountName, size = 40.dp)
                        } else {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = onSky)
                        }
                    }
                }

                // 问候
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 18.dp)) {
                    Text(
                        state.accountName.ifBlank { stringResource(R.string.app_name) },
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSky,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.dash_account_summary, state.zoneCount, state.bucketCount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = onSky.copy(alpha = 0.62f),
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    if (state.accountEmail.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Icon(Icons.Outlined.Cloud, contentDescription = null, tint = onSky.copy(alpha = 0.52f), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(state.accountEmail, fontSize = 12.sp, color = onSky.copy(alpha = 0.52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // 统计磁贴 2×2
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            Icons.Outlined.Language,
                            state.zoneCount,
                            stringResource(R.string.nav_zones),
                            stringResource(R.string.dash_sub_zones),
                            primary = true,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenZones,
                        )
                        StatTile(
                            Icons.Outlined.Bolt,
                            state.workerCount,
                            stringResource(R.string.nav_workers),
                            stringResource(R.string.dash_sub_workers),
                            modifier = Modifier.weight(1f),
                            onClick = onOpenWorkers,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            Icons.Outlined.Cloud,
                            state.bucketCount,
                            stringResource(R.string.dash_buckets),
                            stringResource(R.string.dash_sub_buckets),
                            modifier = Modifier.weight(1f),
                            onClick = onOpenStorage,
                        )
                        StatTile(
                            Icons.Outlined.BarChart,
                            state.requestsToday,
                            stringResource(R.string.dash_requests),
                            stringResource(R.string.dash_sub_requests),
                            modifier = Modifier.weight(1f),
                            onClick = onOpenZones,
                        )
                    }
                }

                // 最近访问
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 26.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.dash_recent), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = onSky, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.dash_view_all),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
                        modifier = Modifier.clickable(onClick = onOpenZones).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (state.recentZones.isNotEmpty()) {
                    Surface(
                        color = cs.surfaceContainerLow.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Column {
                            state.recentZones.forEachIndexed { i, zone ->
                                RecentZoneRow(zone, onClick = { onOpenZone(zone) }, divider = i < state.recentZones.lastIndex)
                            }
                        }
                    }
                }

                // 快捷操作
                Text(
                    stringResource(R.string.dash_quick),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSky,
                    modifier = Modifier.padding(start = 24.dp, top = 26.dp, bottom = 10.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickAction(Icons.Outlined.Refresh, stringResource(R.string.dash_refresh)) { viewModel.refresh() }
                    QuickAction(Icons.Outlined.Hub, stringResource(R.string.tunnel_title), onOpenTunnels)
                }
            }

            if (menuOpen) {
                AccountMenu(
                    accounts = state.accounts,
                    currentId = state.selectedAccountId,
                    onPick = { viewModel.selectAccount(it); menuOpen = false },
                    onAddAccount = { menuOpen = false; onAddAccount() },
                    onDismiss = { menuOpen = false },
                )
            }
        }
    }
}

private fun timeGreeting(): Int {
    val hour = LocalTime.now().hour
    return when (hour) {
        in 5..10 -> R.string.dash_greeting_morning
        in 11..13 -> R.string.dash_greeting_noon
        in 14..17 -> R.string.dash_greeting_afternoon
        else -> R.string.dash_greeting_evening
    }
}

@Composable
private fun RecentZoneRow(zone: Zone, onClick: () -> Unit, divider: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZoneAvatar(zone.name, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(zone.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                zone.plan?.name?.let { Text(it, fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            StatusDot(if (zone.isActive) OcSuccess else cs.error)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
        }
        if (divider) {
            Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(cs.outlineVariant.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clickable(onClick = onClick)
            .background(cs.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
    }
}

@Composable
private fun AccountMenu(
    accounts: List<Account>,
    currentId: String?,
    onPick: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
        Surface(
            color = cs.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 96.dp, end = 12.dp)
                .width(264.dp),
        ) {
            Column(Modifier.padding(8.dp)) {
                Text(
                    stringResource(R.string.dash_switch_account),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp),
                )
                accounts.forEach { account ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(account.id) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ZoneAvatar(account.name, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(account.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (account.id == currentId) Icon(Icons.Outlined.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).height(1.dp).background(cs.outlineVariant))
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onAddAccount).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).background(cs.primaryContainer, RoundedCornerShape(percent = 50)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.dash_add_account), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = cs.primary)
                }
            }
        }
    }
}
