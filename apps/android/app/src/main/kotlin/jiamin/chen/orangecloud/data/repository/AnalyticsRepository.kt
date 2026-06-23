package jiamin.chen.orangecloud.data.repository

import jiamin.chen.orangecloud.core.network.CfApiClient
import jiamin.chen.orangecloud.data.model.AnalyticsGroup
import jiamin.chen.orangecloud.data.model.AnalyticsQueries
import jiamin.chen.orangecloud.data.model.AnalyticsTimeRange
import jiamin.chen.orangecloud.data.model.R2BucketUsage
import jiamin.chen.orangecloud.data.model.R2UsageData
import jiamin.chen.orangecloud.data.model.R2UsageVariables
import jiamin.chen.orangecloud.data.model.TrafficDataPoint
import jiamin.chen.orangecloud.data.model.ZoneAnalyticsData
import jiamin.chen.orangecloud.data.model.ZoneAnalyticsVariables
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zone 流量分析：GraphQL Analytics API → 归一化数据点（对应 iOS AnalyticsService.zoneTraffic）。
 * 分析为只读派生数据，不入 Room；按时间范围在 ViewModel 会话级缓存。
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val api: CfApiClient,
) {
    /** 单个 Worker 指标（请求/错误/子请求），对应 iOS workerMetrics 摘要。 */
    suspend fun workerMetrics(
        accountId: String,
        scriptName: String,
        range: AnalyticsTimeRange = AnalyticsTimeRange.LAST_24H,
    ): jiamin.chen.orangecloud.data.model.WorkerMetrics {
        val (since, until) = range.sinceUntil()
        val data = api.graphQL<jiamin.chen.orangecloud.data.model.WorkerMetricsData, jiamin.chen.orangecloud.data.model.WorkerMetricsVariables>(
            AnalyticsQueries.workerSummary(),
            jiamin.chen.orangecloud.data.model.WorkerMetricsVariables(accountId, scriptName, since, until),
        )
        val node = data.viewer.accounts.firstOrNull()
        val sums = node?.summary.orEmpty()
        val quantiles = sums.firstOrNull()?.quantiles
        val breakdown = node?.byStatus.orEmpty()
            .mapNotNull { g -> g.dimensions?.status?.let { jiamin.chen.orangecloud.data.model.WorkerStatusCount(it, g.sum?.requests ?: 0L) } }
            .filter { it.requests > 0 }
            .sortedByDescending { it.requests }
        return jiamin.chen.orangecloud.data.model.WorkerMetrics(
            requests = sums.sumOf { it.sum?.requests ?: 0L },
            errors = sums.sumOf { it.sum?.errors ?: 0L },
            subrequests = sums.sumOf { it.sum?.subrequests ?: 0L },
            cpuP50Us = quantiles?.cpuTimeP50,
            cpuP99Us = quantiles?.cpuTimeP99,
            statusBreakdown = breakdown,
        )
    }

    /** 单个 Worker 调用趋势（请求/错误时间序列）。 */
    suspend fun workerSeries(
        accountId: String,
        scriptName: String,
        range: AnalyticsTimeRange,
    ): List<jiamin.chen.orangecloud.data.model.WorkerSeriesPoint> {
        val (since, until) = range.sinceUntil()
        val data = api.graphQL<jiamin.chen.orangecloud.data.model.WorkerSeriesData, jiamin.chen.orangecloud.data.model.WorkerMetricsVariables>(
            AnalyticsQueries.workerSeries(daily = !range.usesHourlyGroups),
            jiamin.chen.orangecloud.data.model.WorkerMetricsVariables(accountId, scriptName, since, until),
        )
        return data.viewer.accounts.firstOrNull()?.series.orEmpty().mapNotNull { g ->
            val date = AnalyticsTimeRange.parseDimension(g.dimensions?.datetimeHour, g.dimensions?.date) ?: return@mapNotNull null
            jiamin.chen.orangecloud.data.model.WorkerSeriesPoint(date, g.sum?.requests ?: 0L, g.sum?.errors ?: 0L)
        }
    }

    suspend fun zoneTraffic(zoneId: String, range: AnalyticsTimeRange): List<TrafficDataPoint> {
        val (since, until) = range.sinceUntil()
        val query = if (range.usesHourlyGroups) {
            AnalyticsQueries.zoneHourly(range.limit)
        } else {
            AnalyticsQueries.zoneDaily(range.limit)
        }
        val data = api.graphQL<ZoneAnalyticsData, ZoneAnalyticsVariables>(
            query,
            ZoneAnalyticsVariables(zoneTag = zoneId, since = since, until = until),
        )
        val zone = data.viewer.zones.firstOrNull() ?: return emptyList()
        return zone.groups.mapNotNull { it.toDataPoint() }
    }

    /** R2 每桶本月操作量 + 当前存储快照。失败由调用方降级，不影响桶列表。 */
    suspend fun r2UsageByBucket(
        accountId: String,
        now: Instant = Instant.now(),
    ): Map<String, R2BucketUsage> {
        val nowString = DateTimeFormatter.ISO_INSTANT.format(now)
        val todayStart = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
        val monthStart = now.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val data = api.graphQL<R2UsageData, R2UsageVariables>(
            AnalyticsQueries.r2Usage(),
            R2UsageVariables(
                accountTag = accountId,
                monthStart = DateTimeFormatter.ISO_INSTANT.format(monthStart),
                todayStart = DateTimeFormatter.ISO_INSTANT.format(todayStart),
                now = nowString,
            ),
        )
        val account = data.viewer.accounts.firstOrNull() ?: return emptyMap()
        val byBucket = linkedMapOf<String, R2BucketUsage>()

        for (group in account.r2Storage.orEmpty()) {
            val bucket = group.dimensions?.bucketName?.takeIf { it.isNotBlank() } ?: continue
            val max = group.max
            byBucket[bucket] = byBucket[bucket].orEmpty(bucket).copy(
                storageBytes = (max?.payloadSize ?: 0L) + (max?.metadataSize ?: 0L),
                objectCount = max?.objectCount ?: 0L,
            )
        }

        for (group in account.r2Ops.orEmpty()) {
            val bucket = group.dimensions?.bucketName?.takeIf { it.isNotBlank() } ?: continue
            val action = group.dimensions?.actionType ?: continue
            val requests = group.sum?.requests ?: 0L
            val current = byBucket[bucket].orEmpty(bucket)
            byBucket[bucket] = when {
                action in r2ClassAOperations -> current.copy(classARequests = current.classARequests + requests)
                action in r2ClassBOperations -> current.copy(classBRequests = current.classBRequests + requests)
                else -> current
            }
        }

        return byBucket
    }
}

private fun AnalyticsGroup.toDataPoint(): TrafficDataPoint? {
    val date = AnalyticsTimeRange.parseDimension(dimensions?.datetime, dimensions?.date) ?: return null
    return TrafficDataPoint(
        date = date,
        requests = sum?.requests ?: 0,
        bytes = sum?.bytes ?: 0,
        threats = sum?.threats ?: 0,
        pageViews = sum?.pageViews ?: 0,
        uniques = uniq?.uniques ?: 0,
        cachedRequests = sum?.cachedRequests ?: 0,
    )
}

private fun R2BucketUsage?.orEmpty(bucketName: String): R2BucketUsage =
    this ?: R2BucketUsage(bucketName = bucketName)

private val r2ClassAOperations = setOf(
    "ListBuckets",
    "PutBucket",
    "ListObjects",
    "PutObject",
    "CopyObject",
    "CompleteMultipartUpload",
    "CreateMultipartUpload",
    "UploadPart",
    "UploadPartCopy",
    "ListMultipartUploads",
    "ListParts",
    "PutBucketEncryption",
    "PutBucketCors",
    "PutBucketLifecycleConfiguration",
    "LifecycleStorageTierTransition",
)

private val r2ClassBOperations = setOf(
    "HeadBucket",
    "HeadObject",
    "GetObject",
    "UsageSummary",
    "GetBucketEncryption",
    "GetBucketLocation",
    "GetBucketCors",
    "GetBucketLifecycleConfiguration",
)
