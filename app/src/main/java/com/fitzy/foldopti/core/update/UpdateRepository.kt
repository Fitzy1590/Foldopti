package com.fitzy.foldopti.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.fitzy.foldopti.BuildConfig
import com.fitzy.foldopti.core.optimizer.ShizukuShell
import com.fitzy.foldopti.core.security.SignatureDb
import com.fitzy.foldopti.core.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class NoReleases(val checkedAt: Long) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Self-update from GitHub Releases: check, download, verify SHA-256 and signing certificate, install. */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val ctx: Context, private val api: GitHubReleases,
    private val settings: SettingsStore, private val shell: ShizukuShell,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    val currentVersion: String get() = BuildConfig.VERSION_NAME
    private val sixHours = 6 * 3_600_000L

    /** Called on app launch. Checks at most every six hours when auto-update is on; downloads when a newer release exists. */
    suspend fun autoCheck(force: Boolean = false) {
        val s = settings.current()
        if (!s.autoUpdate) return
        if (!force && System.currentTimeMillis() - s.lastUpdateCheck < sixHours) return
        check()
        (state.value as? UpdateState.Available)?.let { download(it.release) }
    }

    suspend fun check() {
        _state.value = UpdateState.Checking
        _state.value = try {
            val r = api.latest(); val now = System.currentTimeMillis(); settings.setLastUpdateCheck(now)
            val mine = SemVer.parse(currentVersion)
            when {
                r == null -> UpdateState.NoReleases(now)
                r.prerelease || r.version.pre != null -> UpdateState.UpToDate(now)
                mine != null && r.version > mine -> UpdateState.Available(r)
                else -> UpdateState.UpToDate(now)
            }
        } catch (t: Throwable) { UpdateState.Failed(t.message ?: "Could not check for updates") }
    }

    suspend fun download(r: Release) {
        val apk = r.asset(".apk"); val sum = r.asset(".sha256")
        if (apk == null || sum == null) { _state.value = UpdateState.Failed("Release has no APK or checksum file"); return }
        try {
            val dest = File(ctx.cacheDir, "update/${apk.name}")
            _state.value = UpdateState.Downloading(r, 0f)
            api.download(apk.url, dest) { _state.value = UpdateState.Downloading(r, it) }
            val expected = api.text(sum.url).trim().split(Regex("\\s+")).first().lowercase()
            val actual = SignatureDb.hex(SignatureDb.sha256(dest))
            if (expected != actual) { dest.delete(); _state.value = UpdateState.Failed("Checksum mismatch. The download was discarded."); return }
            if (!sameSigner(dest)) { dest.delete(); _state.value = UpdateState.Failed("The update is signed with a different key than the installed app. Uninstall first to switch keys."); return }
            _state.value = UpdateState.Ready(r, dest)
            if (shell.ready) install(r, dest)   // silent path
        } catch (t: Throwable) { _state.value = UpdateState.Failed(t.message ?: "Download failed") }
    }

    private fun certs(info: android.content.pm.PackageInfo?): Set<String> =
        info?.signingInfo?.apkContentsSigners?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }?.toSet().orEmpty()

    private fun sameSigner(apk: File): Boolean {
        val pm = ctx.packageManager
        val theirs = certs(pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES))
        val mine = certs(pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
        return theirs.isNotEmpty() && theirs == mine
    }

    /** Silent with Shizuku, otherwise the system confirmation screen (one tap). */
    suspend fun install(r: Release, file: File) {
        _state.value = UpdateState.Installing(r)
        try {
            if (shell.ready) {
                val res = file.inputStream().use { shell.exec("pm", "install", "-r", "-S", file.length().toString(), stdin = it) }
                if (!res.ok) _state.value = UpdateState.Failed("Install failed: ${res.err.ifBlank { res.out }.trim()}")
            } else withContext(Dispatchers.IO) { sessionInstall(file) }
        } catch (t: Throwable) { _state.value = UpdateState.Failed(t.message ?: "Install failed") }
    }

    private fun sessionInstall(file: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            file.inputStream().use { i -> s.openWrite("update.apk", 0, file.length()).use { o -> i.copyTo(o); s.fsync(o) } }
            val intent = Intent(ctx, InstallStatusReceiver::class.java).setAction("com.fitzy.foldopti.INSTALL_STATUS")
            s.commit(PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT).intentSender)
        }
    }

    fun onInstallStatus(status: Int, message: String?) {
        if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_PENDING_USER_ACTION)
            _state.value = UpdateState.Failed(message ?: "Install failed ($status)")
    }
}
