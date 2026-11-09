package com.fitzy.foldopti.core.optimizer

import com.fitzy.foldopti.core.system.Access
import javax.inject.Inject
import javax.inject.Singleton

/** T0 measure/alert only, T1 accessibility (foreground), T2 Shizuku (silent). */
enum class Tier { T0, T1, T2 }

data class Capabilities(
    val tier: Tier, val shizuku: ShizukuState, val accessibility: Boolean,
    val usageAccess: Boolean, val notifications: Boolean, val secureSettings: Boolean,
)

@Singleton
class CapabilityManager @Inject constructor(private val shell: ShizukuShell, private val access: Access) {
    fun current(): Capabilities {
        shell.refresh()
        val sh = shell.state.value
        val acc = access.isAccessibilityEnabled()
        val tier = when { sh == ShizukuState.READY -> Tier.T2; acc && !access.isTv -> Tier.T1; else -> Tier.T0 }
        return Capabilities(tier, sh, acc, access.hasUsageAccess(), access.hasNotificationPermission(), access.canWriteSecureSettings())
    }
}
