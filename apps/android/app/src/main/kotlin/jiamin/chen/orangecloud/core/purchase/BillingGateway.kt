package jiamin.chen.orangecloud.core.purchase

import android.app.Activity

/**
 * Pro 解锁网关抽象。play 风味本地写入授权，oss 风味为空实现（isPro 由 EntitlementStore 恒真）。
 */
interface BillingGateway {
    /** App 启动时预留的网关初始化入口。 */
    fun connect()

    /** 执行解锁动作。 */
    fun launchPurchase(activity: Activity, planId: String)

    companion object {
        const val PLAN_LIFETIME = "lifetime"
    }
}
