package com.fitzy.foldopti.core.whitelist

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.fitzy.foldopti.core.settings.SettingsStore
import com.fitzy.foldopti.core.system.Access
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhitelistRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: WhitelistDao,
    private val settings: SettingsStore,
    private val access: Access,
) {
    val all: Flow<List<ProtectedApp>> = dao.observeAll()

    suspend fun add(pkg: String, reason: String, source: ProtectedSource = ProtectedSource.USER) =
        dao.upsert(ProtectedApp(pkg, reason, System.currentTimeMillis(), source))

    suspend fun remove(app: ProtectedApp) = dao.delete(app)

    private fun installed(pkg: String) = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.isSuccess

    /** Seeds launcher, keyboard, dialer, SMS and (on TV) the Shield core list. Runs once. */
    suspend fun seedDefaults() {
        if (settings.current().seeded) return
        val pm = ctx.packageManager
        val roles = buildMap<String, String> {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName?.let { put(it, "Default launcher") }
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { put(it, "Keyboard") }
            (ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage?.let { put(it, "Dialer") }
            runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()?.let { put(it, "SMS") }
            put(ctx.packageName, "FoldOpti"); put("moe.shizuku.privileged.api", "Shizuku")
        }
        roles.filterKeys(::installed).forEach { (p, r) -> add(p, r, ProtectedSource.DEFAULT) }
        settings.setSeeded(true)
    }

    fun suggestions(): Map<String, String> = ProtectedPackages.suggestions.filterKeys(::installed)

    suspend fun exportJson(items: List<ProtectedApp>): String = JSONArray().also { arr ->
        items.forEach { arr.put(JSONObject().put("pkg", it.pkg).put("reason", it.reason).put("source", it.source.name)) }
    }.toString(2)

    suspend fun importJson(json: String): Int {
        val arr = JSONArray(json); var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val pkg = o.optString("pkg").takeIf { it.isNotBlank() } ?: continue
            add(pkg, o.optString("reason", "Imported"), ProtectedSource.USER); n++
        }
        return n
    }
}
