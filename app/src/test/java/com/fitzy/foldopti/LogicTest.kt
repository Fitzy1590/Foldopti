package com.fitzy.foldopti

import com.fitzy.foldopti.core.notifications.RateLimit
import com.fitzy.foldopti.core.optimizer.*
import com.fitzy.foldopti.core.scheduler.*
import com.fitzy.foldopti.core.security.*
import com.fitzy.foldopti.core.settings.QuietHours
import com.fitzy.foldopti.core.update.GitHubReleases
import com.fitzy.foldopti.core.update.SemVer
import com.fitzy.foldopti.core.whitelist.ProtectedPackages
import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    private val now = 1_000_000_000_000L
    private fun cand(
        protectedApp: Boolean = false, role: Boolean = false, fg: Boolean = false, media: Boolean = false,
        system: Boolean = false, top: Boolean = false, acted: List<Long> = emptyList(),
    ) = Candidate("com.x", protectedApp, role, fg, media, system, top, acted)

    @Test fun boostPolicyExclusions() {
        assertFalse(BoostPolicy.decide(cand(protectedApp = true), now).allow)
        assertFalse(BoostPolicy.decide(cand(role = true), now).allow)
        assertFalse(BoostPolicy.decide(cand(fg = true), now).allow)
        assertFalse(BoostPolicy.decide(cand(media = true), now).allow)
        assertFalse(BoostPolicy.decide(cand(system = true), now).allow)
        assertFalse(BoostPolicy.decide(cand(top = true), now).allow)
        assertFalse(BoostPolicy.decide(cand(acted = listOf(now - 1000)), now).allow)
        assertTrue(BoostPolicy.decide(cand(), now).allow)
    }

    @Test fun boostLoopGuard() {
        val hour = 3_600_000L
        // three actions inside 24h, all older than the 6h cooldown
        val hist = listOf(now - 8 * hour, now - 12 * hour, now - 20 * hour)
        assertEquals("Restart loop guard", BoostPolicy.decide(cand(acted = hist), now).reason)
        assertTrue(BoostPolicy.decide(cand(acted = hist.take(2)), now).allow)
    }

    @Test fun thermalPolicyHysteresis() {
        val p = ThermalPolicy(holdMs = 120_000)
        assertEquals(ThermalEvent.NONE, p.update(0, 0))
        assertEquals(ThermalEvent.INFO, p.update(2, 1_000))
        assertEquals(ThermalEvent.CRITICAL, p.update(3, 2_000))
        assertEquals(ThermalEvent.NONE, p.update(0, 3_000))            // below, hold starts
        assertEquals(ThermalEvent.NONE, p.update(0, 100_000))          // < 2 min
        assertEquals(ThermalEvent.CLEARED, p.update(0, 130_000))       // >= 2 min
    }

    @Test fun thermalHoldResetsWhenHeatReturns() {
        val p = ThermalPolicy(120_000)
        p.update(3, 0); p.update(0, 1_000); assertEquals(ThermalEvent.NONE, p.update(3, 60_000))
        assertEquals(ThermalEvent.NONE, p.update(0, 70_000)); assertEquals(ThermalEvent.NONE, p.update(0, 150_000))
        assertEquals(ThermalEvent.CLEARED, p.update(0, 200_000))
    }

    @Test fun quietHoursWrapMidnight() {
        assertTrue(QuietHours.isQuiet(23 * 60, 22 * 60, 7 * 60)); assertTrue(QuietHours.isQuiet(6 * 60, 22 * 60, 7 * 60))
        assertFalse(QuietHours.isQuiet(12 * 60, 22 * 60, 7 * 60)); assertFalse(QuietHours.isQuiet(5, 0, 0))
    }

    @Test fun rateLimit() {
        assertTrue(RateLimit.allow(null, now)); assertFalse(RateLimit.allow(now - 10 * 60_000, now)); assertTrue(RateLimit.allow(now - 31 * 60_000, now))
    }

    @Test fun intervalPolicy() {
        assertEquals(60, IntervalPolicy.effectiveMinutes(30, coverScreen = true, charging = false))
        assertEquals(30, IntervalPolicy.effectiveMinutes(30, coverScreen = true, charging = true))
        assertEquals(30, IntervalPolicy.effectiveMinutes(30, coverScreen = false, charging = false))
    }

    @Test fun usageShare() {
        assertNull(UsageShare.top(emptyMap()))
        val (p, s) = UsageShare.top(mapOf("a" to 30L, "b" to 70L))!!; assertEquals("b", p); assertEquals(0.7, s, 1e-9)
    }

    @Test fun dormancyTiers() {
        assertNull(Dormancy.tier(13)); assertEquals(DormantTier.D14, Dormancy.tier(14)); assertEquals(DormantTier.D30, Dormancy.tier(45)); assertEquals(DormantTier.D60, Dormancy.tier(400))
    }

    @Test fun protectedPackages() {
        listOf("com.google.android.tvlauncher", "com.google.android.katniss", "com.google.android.backdrop", "com.nvidia.shieldtech.hub", "com.fitzy.foldopti").forEach { assertTrue(it, ProtectedPackages.isCore(it)) }
        assertFalse(ProtectedPackages.isCore("com.random.app"))
    }

    private fun input(
        perms: Set<String> = emptySet(), a11y: Boolean = false, admin: Boolean = false, installer: String? = "com.android.vending",
        debug: Boolean = false, known: String? = null, signer: String? = "aa", target: Int = 34, updated: Long = now, hash: Boolean = false, system: Boolean = false,
    ) = ScanInput("p", "P", perms, a11y, admin, installer, system, signer, debug, known, target, updated, hash, now)

    @Test fun heuristicsRules() {
        assertEquals(Band.CLEAN, Heuristics.evaluate(input()).band)
        assertEquals(35, Heuristics.evaluate(input(a11y = true, perms = setOf("android.permission.SYSTEM_ALERT_WINDOW"))).score)
        assertEquals(Band.REVIEW, Heuristics.evaluate(input(a11y = true, perms = setOf("android.permission.SYSTEM_ALERT_WINDOW"))).band)
        val sms = Heuristics.evaluate(input(a11y = true, perms = setOf("android.permission.READ_SMS")))
        assertEquals(40, sms.score)
        val bad = Heuristics.evaluate(input(a11y = true, admin = true, perms = setOf("android.permission.READ_SMS")))
        assertEquals(Band.SUSPICIOUS, bad.band)
        assertEquals(Band.SUSPICIOUS, Heuristics.evaluate(input(hash = true)).band)
        assertEquals(40, Heuristics.evaluate(input(known = "bb")).score)
        assertEquals(20, Heuristics.evaluate(input(debug = true)).score)
        assertEquals(0, Heuristics.evaluate(input(a11y = true, admin = true, system = true, hash = false)).score)   // system apps are exempt from permission rules
        assertEquals(15, Heuristics.evaluate(input(installer = null, perms = setOf("android.permission.REQUEST_INSTALL_PACKAGES"))).score)
    }

    @Test fun heuristicsOutdatedNeedsSensitivePermission() {
        val old = now - Heuristics.TWO_YEARS_MS - 1
        assertEquals(0, Heuristics.evaluate(input(updated = old)).score)
        assertEquals(10, Heuristics.evaluate(input(updated = old, perms = setOf("android.permission.CAMERA"))).score)
    }

    @Test fun hashSetBinarySearch() {
        val set = BinaryHashSet.fromHex(listOf("ff".repeat(32), "00".repeat(32), "7a".repeat(32)))
        assertEquals(3, set.size)
        fun b(h: String) = ByteArray(32) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        assertTrue(set.contains(b("7a".repeat(32)))); assertTrue(set.contains(b("00".repeat(32)))); assertTrue(set.contains(b("ff".repeat(32))))
        assertFalse(set.contains(b("01".repeat(32))))
    }

    @Test fun semver() {
        assertTrue(SemVer.parse("v1.2.3")!! > SemVer.parse("1.2.2")!!)
        assertTrue(SemVer.parse("1.0.0")!! > SemVer.parse("1.0.0-beta")!!)
        assertTrue(SemVer.parse("1.10.0")!! > SemVer.parse("1.9.9")!!)
        assertNull(SemVer.parse("nightly"))
    }

    @Test fun releaseParse() {
        val r = GitHubReleases.parse("""{"tag_name":"v1.1.0","body":"notes","prerelease":false,"assets":[{"name":"foldopti.apk","browser_download_url":"https://x/a.apk","size":5},{"name":"foldopti.apk.sha256","browser_download_url":"https://x/a.sha","size":1}]}""")!!
        assertEquals("1.1.0", "${r.version.major}.${r.version.minor}.${r.version.patch}")
        assertEquals("https://x/a.apk", r.asset(".apk")!!.url); assertEquals("https://x/a.sha", r.asset(".sha256")!!.url)
    }

    @Test fun processParsers() {
        assertEquals(setOf("com.a.b", "com.c"), ProcessProbe.parseRunning("NAME\ncom.a.b\ncom.c:remote\nsystem_server\n[kworker]"))
        val svc = "  * ServiceRecord{1a2b u0 com.music/.Player}\n    isForeground=true\n  * ServiceRecord{3c4d u0 com.idle/.Svc}\n    isForeground=false"
        assertEquals(setOf("com.music"), ProcessProbe.parseForegroundServices(svc))
        assertEquals(setOf("com.spotify"), ProcessProbe.parseMediaSessions("  package=com.spotify\n"))
        assertEquals(setOf("com.top"), ProcessProbe.parseTopApp("  mResumedActivity: ActivityRecord{1 u0 com.top/.Main t5}"))
        assertEquals(30.5, ProcessProbe.parseCpu("  30.5% 1234/com.hog: 20% user + 10% kernel").first().percent, 1e-9)
    }
}
