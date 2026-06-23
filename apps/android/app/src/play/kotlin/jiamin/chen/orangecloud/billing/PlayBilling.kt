package jiamin.chen.orangecloud.billing

import android.app.Activity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jiamin.chen.orangecloud.core.purchase.BillingGateway
import jiamin.chen.orangecloud.core.purchase.EntitlementStore
import javax.inject.Inject
import javax.inject.Singleton

/** play 风味本地自用版：不接入 Play Billing，点击解锁后直接授予 Pro。 */
@Singleton
class PlayBillingGateway @Inject constructor(
    private val entitlementStore: EntitlementStore,
) : BillingGateway {
    override fun connect() = Unit

    override fun launchPurchase(activity: Activity, planId: String) {
        entitlementStore.setPro(true)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    abstract fun bindBillingGateway(impl: PlayBillingGateway): BillingGateway
}
