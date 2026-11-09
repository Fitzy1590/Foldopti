package com.fitzy.foldopti.core.optimizer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in, foreground-only. Opens App info -> Storage -> Clear cache for one package at a time.
 * Limited to com.android.settings. English labels only [verify per OEM]. Cancels on any touch.
 */
class CacheClearAccessibilityService : AccessibilityService() {
    @Volatile private var cancelled = false
    @Volatile private var busy = false

    override fun onServiceConnected() { AccessibilityHolder.service = this }
    override fun onUnbind(intent: Intent?): Boolean { AccessibilityHolder.service = null; return super.onUnbind(intent) }
    override fun onInterrupt() { cancelled = true }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (busy && event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) cancelled = true
    }

    suspend fun clearCache(pkg: String): Boolean {
        busy = true; cancelled = false
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (!clickAny(STORAGE_LABELS)) return false
            val ok = clickAny(CLEAR_LABELS)
            delay(300)
            performGlobalAction(GLOBAL_ACTION_BACK); delay(250); performGlobalAction(GLOBAL_ACTION_BACK)
            return ok
        } finally { busy = false }
    }

    private suspend fun clickAny(labels: List<String>): Boolean {
        repeat(24) {
            if (cancelled) return false
            val root = rootInActiveWindow
            if (root != null) for (l in labels) {
                val node = root.findAccessibilityNodeInfosByText(l).firstOrNull { it.text?.toString().equals(l, true) }
                if (node != null && clickUp(node)) { delay(600); return true }
            }
            delay(250)
        }
        return false
    }

    private fun clickUp(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        while (cur != null) { if (cur.isClickable && cur.isEnabled) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK); cur = cur.parent }
        return false
    }

    private companion object {
        val STORAGE_LABELS = listOf("Storage", "Storage usage", "Storage & cache")
        val CLEAR_LABELS = listOf("Clear cache")
    }
}

object AccessibilityHolder { @Volatile var service: CacheClearAccessibilityService? = null }

@Singleton
class AccessibilityBridge @Inject constructor() {
    val connected: Boolean get() = AccessibilityHolder.service != null

    suspend fun clearCaches(pkgs: List<String>, onProgress: (Int) -> Unit = {}): Set<String> {
        val svc = AccessibilityHolder.service ?: return emptySet()
        val done = mutableSetOf<String>()
        for (p in pkgs) { if (svc.clearCache(p)) { done += p; onProgress(done.size) } else break }
        return done
    }
}
