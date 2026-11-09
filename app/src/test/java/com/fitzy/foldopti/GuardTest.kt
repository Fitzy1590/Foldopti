package com.fitzy.foldopti

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
