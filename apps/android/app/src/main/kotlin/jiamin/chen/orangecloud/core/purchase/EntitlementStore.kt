package jiamin.chen.orangecloud.core.purchase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pro 授权状态（对应 iOS EntitlementStore）。
 * 本地自用版安装即永久 VIP，不接入 Google Play 付款。
 *
 * 六处闸门统一读 [isPro]；非 Pro 时展示 Paywall。
 */
@Singleton
class EntitlementStore @Inject constructor() {
    private val _isPro = MutableStateFlow(true)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /** 保留给解锁入口调用；本地自用版始终保持 Pro。 */
    fun setPro(value: Boolean) {
        _isPro.value = true
    }
}
