package com.fitzy.foldopti

import com.fitzy.foldopti.core.optimizer.*
import com.fitzy.foldopti.core.whitelist.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GuardTest {
    private class FakeDao(val items: MutableSet<String> = mutableSetOf()) : WhitelistDao {
        override fun observeAll(): Flow<List<ProtectedApp>> = flowOf(emptyList())
        override suspend fun contains(pkg: String) = pkg in items
        override suspend fun upsert(app: ProtectedApp) { items += app.pkg }
        override suspend fun delete(app: ProtectedApp) { items -= app.pkg }
    }

    @Test fun everyActionIsBlockedForProtectedApps() = runBlocking {
        val guard = WhitelistGuard(FakeDao(mutableSetOf("com.plexapp.android")))
        for (a in CleanAction.entries) {
            assertFalse("$a", guard.allows("com.plexapp.android", a))
            assertFalse("$a core", guard.allows("com.google.android.tvlauncher", a))
            assertTrue("$a other", guard.allows("com.random.app", a))
        }
    }

    @Test fun assertAllowedThrowsAndFilterDrops() = runBlocking {
        val guard = WhitelistGuard(FakeDao(mutableSetOf("a.b")))
        try { guard.assertAllowed("a.b", CleanAction.STOP); fail("expected exception") } catch (e: ProtectedAppException) { assertEquals("a.b", e.pkg) }
        assertEquals(listOf("c.d"), guard.filterAllowed(listOf("a.b", "c.d"), CleanAction.CLEAR_CACHE))
    }

    /** 2a: user data must never be wiped. Any `clear` shell call must be cache-only. */
    @Test fun noDataWipingShellCalls() {
        val root = File("src/main/java")
        val bad = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) -> Regex("\"pm\"\\s*,\\s*\"clear\"").containsMatchIn(l) && !l.contains("--cache-only") }.map { "${f.name}:${it.index + 1}" }
        }.toList()
        assertTrue("pm clear without --cache-only: $bad", bad.isEmpty())
    }

    /** 7e: the scanner must not call any package-removal or disable API. */
    @Test fun scannerNeverRemovesApps() {
        val src = File("src/main/java/com/fitzy/foldopti/core/security").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf("disable-user", "uninstall", "deletePackage", "ACTION_DELETE", "force-stop").forEach { assertFalse(it, src.contains(it)) }
    }
}

class SummaryTest {
    private fun fmt(b: Long) = "${b}B"
    @Test fun cleanSummaryExplainsFailures() {
        val r = CleanResult(0, 0, 2, 5, Tier.T2, null, "Unknown option --cache-only").describe(::fmt)
        assertTrue(r.any { it.contains("5 apps could not be cleared") && it.contains("Unknown option") }); assertTrue(r.any { it.contains("2 protected") })
    }
    @Test fun cleanSummaryReportsSuccessAndNotes() {
        assertEquals(listOf("Cleared 100B from 1 app"), CleanResult(100, 1, 0, 0, Tier.T2, null).describe(::fmt))
        assertEquals(listOf("Turn on Shizuku"), CleanResult(0, 0, 0, 0, Tier.T0, "Turn on Shizuku").describe(::fmt))
    }
    @Test fun boostSummary() {
        assertTrue(BoostResult(listOf("a"), mapOf("b" to "x"), null).describe().first().contains("Restricted 1"))
    }
}

class SafetyPolicyTest {
    @Test fun systemAndOemPackagesAreNeverTouched() {
        listOf("com.android.systemui", "com.samsung.android.app.quickpanel", "com.sec.android.app.launcher", "com.google.android.gms",
            "com.qualcomm.qti.services", "android", "com.nvidia.shieldtech.hub", "com.samsung.systemui.lockstar").forEach { assertTrue(it, SafetyPolicy.isSystemCritical(it)) }
        assertFalse(SafetyPolicy.isSystemCritical("com.android.chrome")); assertFalse(SafetyPolicy.isSystemCritical("com.facebook.katana"))
    }
    @Test fun cacheClearRules() {
        assertTrue(SafetyPolicy.canClearCache("com.android.chrome", isSystem = true, isUpdatedSystem = true, hasLauncher = true))
        assertTrue(SafetyPolicy.canClearCache("com.facebook.katana", false, false, true))
        assertFalse(SafetyPolicy.canClearCache("com.facebook.katana", false, false, false))        // no launcher icon
        assertFalse(SafetyPolicy.canClearCache("com.some.preinstalled", true, false, true))         // pure system app
        assertFalse(SafetyPolicy.canClearCache("com.samsung.android.app.spage", true, true, true))  // OEM layer
    }
    @Test fun restrictRules() {
        assertTrue(SafetyPolicy.canRestrict("com.facebook.katana", isSystem = false, hasLauncher = true, exposesBoundService = false))
        assertFalse(SafetyPolicy.canRestrict("com.android.chrome", true, true, false))              // system flag, even if updated
        assertFalse(SafetyPolicy.canRestrict("com.thirdparty.tiles", false, true, true))            // quick settings tile / listener / IME
        assertFalse(SafetyPolicy.canRestrict("com.thirdparty.x", false, false, false))              // no launcher icon
        assertTrue("android.permission.BIND_QUICK_SETTINGS_TILE" in SafetyPolicy.bindingPermissions)
    }
}

class DisabledScannerTest {
    @Test fun parsesQueryOpOutput() {
        val out = "Packages:\n  com.a.b\n  com.not.installed\nUid mode: x\n  com.c.d\n"
        assertEquals(listOf("com.a.b", "com.c.d"), DisabledScanner.parseQueryOp(out, setOf("com.a.b", "com.c.d")))
    }
    @Test fun flagsShadeRelatedPackages() {
        assertTrue(DisabledScanner.isShadeRelated("com.android.systemui")); assertTrue(DisabledScanner.isShadeRelated("com.samsung.android.app.quickpanel"))
        assertFalse(DisabledScanner.isShadeRelated("com.spotify.music"))
    }
}
