package com.fitzy.foldopti.core.update

import com.fitzy.foldopti.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseAsset(val name: String, val url: String, val size: Long)
data class Release(val tag: String, val version: SemVer, val notes: String, val prerelease: Boolean, val assets: List<ReleaseAsset>) {
    fun asset(suffix: String) = assets.firstOrNull { it.name.endsWith(suffix, true) }
}

/** Only network client in the app besides the optional signature download. Talks to api.github.com over HTTPS. */
@Singleton
class GitHubReleases @Inject constructor() {
    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json"); setRequestProperty("User-Agent", "FoldOpti/${BuildConfig.VERSION_NAME}")
    }

    /** Latest non-prerelease. Null when the repo has no releases yet. Throws on network errors. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val c = open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        try {
            if (c.responseCode == 404) return@withContext null
            if (c.responseCode != 200) error("GitHub returned ${c.responseCode}")
            parse(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }

    suspend fun download(url: String, dest: File, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val c = open(url).apply { setRequestProperty("Accept", "application/octet-stream") }
        try {
            if (c.responseCode != 200) error("Download failed (${c.responseCode})")
            val total = c.contentLengthLong
            dest.parentFile?.mkdirs()
            c.inputStream.use { i -> dest.outputStream().use { o ->
                val buf = ByteArray(65536); var read = 0L
                while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); read += n; if (total > 0) onProgress(read.toFloat() / total) }
            } }
        } finally { c.disconnect() }
    }

    suspend fun text(url: String): String = withContext(Dispatchers.IO) {
        val c = open(url).apply { setRequestProperty("Accept", "application/octet-stream") }
        try { if (c.responseCode != 200) error("Download failed (${c.responseCode})"); c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    companion object {
        fun parse(json: String): Release? {
            val o = JSONObject(json); val tag = o.optString("tag_name")
            val v = SemVer.parse(tag) ?: return null
            val arr = o.optJSONArray("assets")
            val assets = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
                .map { ReleaseAsset(it.getString("name"), it.getString("browser_download_url"), it.optLong("size")) }
            return Release(tag, v, o.optString("body"), o.optBoolean("prerelease"), assets)
        }
    }
}
