package jiamin.chen.orangecloud.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jiamin.chen.orangecloud.core.auth.AuthRepository
import jiamin.chen.orangecloud.core.auth.Scopes
import jiamin.chen.orangecloud.core.system.AppPrefs
import jiamin.chen.orangecloud.data.model.Account
import jiamin.chen.orangecloud.data.model.AnalyticsTimeRange
import jiamin.chen.orangecloud.data.model.Zone
import jiamin.chen.orangecloud.data.repository.AccountStore
import jiamin.chen.orangecloud.data.repository.AnalyticsRepository
import jiamin.chen.orangecloud.data.repository.SecurityRepository
import jiamin.chen.orangecloud.data.repository.StorageRepository
import jiamin.chen.orangecloud.data.repository.WorkerRepository
import jiamin.chen.orangecloud.data.repository.ZoneRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DashboardResourceType { Zone, Worker, R2Bucket, D1Database, KVNamespace, Tunnel }

data class DashboardResource(
    val type: DashboardResourceType,
    val id: String,
    val title: String,
    val subtitle: String,
) {
    val encoded: String get() = listOf(type.name, id, title, subtitle).joinToString("|") { it.replace("|", "%7C") }
}

data class AlertItem(
    val severity: String,
    val title: String,
    val detail: String,
)

data class DashboardUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String? = null,
    val accountName: String = "",
    val accountEmail: String = "",
    val zoneCount: String = "—",
    val workerCount: String = "—",
    val bucketCount: String = "—",
    val requestsToday: String = "—",
    val r2Storage: String = "—",
    val r2RequestsMonth: String = "—",
    val tunnelCount: String = "—",
    val tunnelHealthy: String = "—",
    val attentionItems: List<String> = emptyList(),
    val alerts: List<AlertItem> = emptyList(),
    val resources: List<DashboardResource> = emptyList(),
    val pinnedResources: List<DashboardResource> = emptyList(),
    val pinnedIds: Set<String> = emptySet(),
    val recentZones: List<Zone> = emptyList(),
    val isLoading: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountStore: AccountStore,
    private val authRepository: AuthRepository,
    private val zoneRepository: ZoneRepository,
    private val workerRepository: WorkerRepository,
    private val storageRepository: StorageRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val securityRepository: SecurityRepository,
    private val appPrefs: AppPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountStore.accounts.collect { list -> _uiState.update { it.copy(accounts = list) } }
        }
        viewModelScope.launch {
            accountStore.selectedAccountId.collect { id ->
                _uiState.update { it.copy(selectedAccountId = id) }
            }
        }
        viewModelScope.launch {
            appPrefs.pinnedResources.collect { pinned ->
                _uiState.update {
                    it.copy(
                        pinnedIds = pinned,
                        pinnedResources = it.resources.filter { resource -> resource.encoded in pinned },
                    )
                }
            }
        }
        // 域名计数 / 最近访问：持续观察 Room 缓存（切账号自动切流）。
        // refreshZones 写入缓存后这里会自动更新——修复冷启动一次性读空缓存恒显 0 的问题。
        viewModelScope.launch {
            accountStore.selectedAccountId
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else zoneRepository.observeZones(id) }
                .collect { zones ->
                    _uiState.update { it.copy(zoneCount = zones.size.toString(), recentZones = zones.take(4)) }
                }
        }
        refresh()
    }

    fun selectAccount(accountId: String) {
        accountStore.select(accountId)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                accountStore.ensureLoaded()
                val accountId = accountStore.selectedAccountId.value
                val account = accountStore.selectedAccount
                val email = authRepository.state.value.currentSession?.label.orEmpty()
                _uiState.update {
                    it.copy(accountName = account?.name.orEmpty(), accountEmail = email)
                }
                if (accountId == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                // 先网络刷新域名缓存（zoneCount / recentZones 由 init 的持续观察自动反映）。
                // 放在读取派生数据之前，确保今日请求量基于最新域名集计算，而非冷启动的空缓存。
                runCatching { zoneRepository.refreshZones(accountId) }
                val zones = zoneRepository.observeZones(accountId).first()

                // 各项计数独立 best-effort（缺 scope / 出错不互相拖累）
                val workers = async { runCatching { workerRepository.refreshWorkers(accountId) }.getOrNull() }
                val buckets = async {
                    if (authRepository.hasScope(Scopes.R2_READ)) {
                        runCatching { storageRepository.listBuckets(accountId) }.getOrNull()
                    } else null
                }
                val databases = async {
                    if (authRepository.hasScope(Scopes.D1_READ)) runCatching { storageRepository.listDatabases(accountId) }.getOrNull()
                    else null
                }
                val namespaces = async {
                    if (authRepository.hasScope(Scopes.KV_READ)) runCatching { storageRepository.listNamespaces(accountId) }.getOrNull()
                    else null
                }
                val r2Usage = async {
                    if (authRepository.hasScope(Scopes.ACCOUNT_ANALYTICS_READ)) {
                        runCatching { analyticsRepository.r2UsageByBucket(accountId).values.toList() }.getOrNull()
                    } else null
                }
                val tunnels = async {
                    if (authRepository.hasScope(Scopes.TUNNEL_READ)) {
                        runCatching { securityRepository.listTunnels(accountId) }.getOrNull()
                    } else null
                }
                val requests = async { sumRequests(zones) }

                workers.await()
                val workerList = workerRepository.observeWorkers(accountId).first()
                _uiState.update { it.copy(workerCount = workerList.size.toString()) }
                val bucketList = buckets.await().orEmpty()
                val databaseList = databases.await().orEmpty()
                val namespaceList = namespaces.await().orEmpty()
                _uiState.update { st -> st.copy(bucketCount = bucketList.size.toString()) }
                r2Usage.await()?.let { usage ->
                    val totalStorage = usage.sumOf { it.storageBytes }
                    val totalRequests = usage.sumOf { it.totalRequests }
                    _uiState.update { st ->
                        st.copy(
                            r2Storage = formatBytes(totalStorage),
                            r2RequestsMonth = formatCount(totalRequests),
                        )
                    }
                }
                val tunnelList = tunnels.await().orEmpty()
                tunnelList.takeIf { it.isNotEmpty() }?.let { list ->
                    val healthy = list.count { it.status == "healthy" }
                    _uiState.update { st ->
                        st.copy(
                            tunnelCount = list.size.toString(),
                            tunnelHealthy = "$healthy/${list.size}",
                            attentionItems = buildAttentionItems(
                                inactiveZones = zones.count { !it.isActive },
                                tunnelIssues = list.count { it.status != "healthy" },
                                workerCount = workerList.size,
                            ),
                        )
                    }
                } ?: _uiState.update { st ->
                    st.copy(
                        attentionItems = buildAttentionItems(
                            inactiveZones = zones.count { !it.isActive },
                            tunnelIssues = 0,
                            workerCount = workerList.size,
                        ),
                    )
                }
                requests.await()?.let { req -> _uiState.update { st -> st.copy(requestsToday = req) } }
                val resources = buildResources(zones, workerList, bucketList, databaseList, namespaceList, tunnelList)
                val alerts = buildAlerts(zones, tunnelList, workerList.size)
                _uiState.update { st ->
                    st.copy(
                        resources = resources,
                        pinnedResources = resources.filter { it.encoded in st.pinnedIds },
                        alerts = alerts,
                    )
                }
            } catch (e: Exception) {
                // 顶层失败不致命，保留已有数据
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun togglePinned(resource: DashboardResource) {
        viewModelScope.launch { appPrefs.togglePinnedResource(resource.encoded) }
    }

    /** 今日请求总数 = 各域名 24h 请求之和（best-effort，缺 analytics scope 返回 null）。 */
    private suspend fun sumRequests(zones: List<Zone>): String? {
        if (!authRepository.hasScope(Scopes.ANALYTICS_READ) || zones.isEmpty()) return null
        return try {
            var total = 0L
            for (zone in zones.take(12)) {
                val points = runCatching { analyticsRepository.zoneTraffic(zone.id, AnalyticsTimeRange.LAST_24H) }.getOrNull()
                total += points?.sumOf { it.requests.toLong() } ?: 0L
            }
            formatCount(total)
        } catch (e: Exception) {
            null
        }
    }

    private fun formatCount(n: Long): String = when {
        n >= 1_000_000 -> "%.2fM".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1fK".format(n / 1_000.0)
        else -> n.toString()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB", "PB")
        var value = bytes.toDouble() / 1024
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        return "%.1f %s".format(value, units[index])
    }

    private fun buildAttentionItems(inactiveZones: Int, tunnelIssues: Int, workerCount: Int): List<String> = buildList {
        if (inactiveZones > 0) add("有 $inactiveZones 个域名未激活")
        if (tunnelIssues > 0) add("有 $tunnelIssues 条 Tunnel 需要检查")
        if (workerCount == 0) add("当前账号还没有 Worker")
        if (isEmpty()) add("核心服务状态正常")
    }

    private fun buildResources(
        zones: List<Zone>,
        workers: List<jiamin.chen.orangecloud.data.model.WorkerScript>,
        buckets: List<jiamin.chen.orangecloud.data.model.R2Bucket>,
        databases: List<jiamin.chen.orangecloud.data.model.D1Database>,
        namespaces: List<jiamin.chen.orangecloud.data.model.KVNamespace>,
        tunnels: List<jiamin.chen.orangecloud.data.model.Tunnel>,
    ): List<DashboardResource> = buildList {
        zones.forEach { add(DashboardResource(DashboardResourceType.Zone, it.id, it.name, "域名")) }
        workers.forEach { add(DashboardResource(DashboardResourceType.Worker, it.id, it.id, "Worker")) }
        buckets.forEach { add(DashboardResource(DashboardResourceType.R2Bucket, it.name, it.name, "R2 Bucket")) }
        databases.forEach { add(DashboardResource(DashboardResourceType.D1Database, it.uuid, it.name, "D1 数据库")) }
        namespaces.forEach { add(DashboardResource(DashboardResourceType.KVNamespace, it.id, it.title, "KV 命名空间")) }
        tunnels.forEach { add(DashboardResource(DashboardResourceType.Tunnel, it.id, it.name, "Tunnel · ${it.status ?: "unknown"}")) }
    }

    private fun buildAlerts(
        zones: List<Zone>,
        tunnels: List<jiamin.chen.orangecloud.data.model.Tunnel>,
        workerCount: Int,
    ): List<AlertItem> = buildList {
        zones.filterNot { it.isActive }.take(5).forEach {
            add(AlertItem("warn", "域名未激活", it.name))
        }
        tunnels.filter { it.status != "healthy" }.take(5).forEach {
            add(AlertItem(if (it.status == "down") "critical" else "warn", "Tunnel 状态异常", "${it.name} · ${it.status ?: "unknown"}"))
        }
        if (workerCount == 0) add(AlertItem("info", "还没有 Worker", "当前账号未发现 Worker 脚本"))
        if (isEmpty()) add(AlertItem("ok", "暂无告警", "打开 App 时检查到的核心资源状态正常"))
    }
}
