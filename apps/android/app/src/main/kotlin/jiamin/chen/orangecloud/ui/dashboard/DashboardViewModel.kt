package jiamin.chen.orangecloud.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jiamin.chen.orangecloud.core.auth.AuthRepository
import jiamin.chen.orangecloud.core.auth.Scopes
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
                        runCatching { storageRepository.listBuckets(accountId).size }.getOrNull()
                    } else null
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
                buckets.await()?.let { count -> _uiState.update { st -> st.copy(bucketCount = count.toString()) } }
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
                tunnels.await()?.let { list ->
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
            } catch (e: Exception) {
                // 顶层失败不致命，保留已有数据
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
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
}
