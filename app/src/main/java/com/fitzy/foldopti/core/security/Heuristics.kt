package com.fitzy.foldopti.core.security

enum class Band { CLEAN, REVIEW, SUSPICIOUS }

data class Reason(val id: String, val points: Int, val text: String)

data class ScanInput(
    val pkg: String, val label: String, val permissions: Set<String>,
    val hasAccessibilityService: Boolean, val hasDeviceAdmin: Boolean,
    val installer: String?, val isSystemApp: Boolean, val signerSha256: String?,
    val isDebugCert: Boolean, val knownSignerSha256: String?, val targetSdk: Int,
    val lastUpdateMs: Long, val hashMatch: Boolean, val nowMs: Long,
)

data class Finding(val pkg: String, val label: String, val score: Int, val band: Band, val reasons: List<Reason>)

/** Pure scoring. Rules and weights follow SPEC section 7. */
object Heuristics {
    private val stores = setOf(
        "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "org.fdroid.fdroid",
        "com.huawei.appmarket", "com.xiaomi.mipicks", "com.google.android.packageinstaller.store",
    )
    private val dangerousGroups = mapOf(
        "SMS" to listOf("READ_SMS", "SEND_SMS", "RECEIVE_SMS"), "Contacts" to listOf("READ_CONTACTS"),
        "Call log" to listOf("READ_CALL_LOG"), "Microphone" to listOf("RECORD_AUDIO"), "Camera" to listOf("CAMERA"),
        "Location" to listOf("ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION"), "Phone" to listOf("READ_PHONE_STATE", "CALL_PHONE"),
    )
    const val TWO_YEARS_MS = 2L * 365 * 86_400_000

    fun isSideloaded(installer: String?) = installer == null || installer !in stores

    private fun has(p: Set<String>, n: String) = p.any { it.endsWith(".$n") || it == n }

    fun evaluate(i: ScanInput): Finding {
        val r = mutableListOf<Reason>()
        if (i.hashMatch) r += Reason("hash", 100, "Matches a known malware signature")
        if (!i.isSystemApp) {
            val overlay = has(i.permissions, "SYSTEM_ALERT_WINDOW")
            val sms = has(i.permissions, "READ_SMS") || has(i.permissions, "SEND_SMS") || has(i.permissions, "RECEIVE_SMS")
            if (i.hasAccessibilityService && overlay) r += Reason("a11y_overlay", 35, "Accessibility service plus draw-over-apps")
            if (i.hasAccessibilityService && sms) r += Reason("a11y_sms", 40, "Accessibility service plus SMS access")
            if (i.hasDeviceAdmin) r += Reason("device_admin", 30, "Registers as a device administrator")
            val side = isSideloaded(i.installer)
            if (side && has(i.permissions, "REQUEST_INSTALL_PACKAGES")) r += Reason("installer", 15, "Sideloaded and can install other apps")
            if (side) {
                val groups = dangerousGroups.filterValues { l -> l.any { has(i.permissions, it) } }.keys
                if (groups.size >= 3) r += Reason("perm_groups", 15, "Sideloaded and asks for ${groups.size} sensitive groups: ${groups.joinToString()}")
            }
            if (i.isDebugCert) r += Reason("debug_cert", 20, "Signed with a debug certificate")
            if (i.knownSignerSha256 != null && i.signerSha256 != null && !i.knownSignerSha256.equals(i.signerSha256, true))
                r += Reason("cert_mismatch", 40, "Signing certificate differs from the known publisher")
            val sensitive = dangerousGroups.values.flatten().any { has(i.permissions, it) }
            if (sensitive && (i.targetSdk <= 27 || i.nowMs - i.lastUpdateMs > TWO_YEARS_MS))
                r += Reason("outdated", 10, "Outdated app with sensitive permissions")
        }
        val score = r.sumOf { it.points }.coerceAtMost(100)
        val band = when { score >= 60 -> Band.SUSPICIOUS; score >= 30 -> Band.REVIEW; else -> Band.CLEAN }
        return Finding(i.pkg, i.label, score, band, r)
    }
}
