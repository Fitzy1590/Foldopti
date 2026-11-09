package com.fitzy.foldopti.core.security

import android.util.Base64
import com.fitzy.foldopti.BuildConfig
import com.fitzy.foldopti.core.update.GitHubReleases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SigUpdate {
    data object Disabled : SigUpdate
    data object NoAsset : SigUpdate
    data class Updated(val count: Int) : SigUpdate
    data class Failed(val message: String) : SigUpdate
}

/** Optional (7d): download only, verified with a public key baked into the build. Disabled while the key is empty. */
@Singleton
class SignatureUpdater @Inject constructor(private val api: GitHubReleases, private val db: SignatureDb) {
    val enabled get() = BuildConfig.SIGNATURE_PUBKEY_B64.isNotBlank()

    suspend fun update(): SigUpdate = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext SigUpdate.Disabled
        try {
            val r = api.latest() ?: return@withContext SigUpdate.NoAsset
            val bin = r.asset("signatures.bin"); val sig = r.asset("signatures.bin.sig") ?: return@withContext SigUpdate.NoAsset
            if (bin == null) return@withContext SigUpdate.NoAsset
            val tmp = File(db.updatedFile.parentFile, "signatures.tmp"); api.download(bin.url, tmp)
            val sigBytes = Base64.decode(api.text(sig.url).trim(), Base64.DEFAULT)
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(BuildConfig.SIGNATURE_PUBKEY_B64, Base64.DEFAULT)))
            val ok = Signature.getInstance("SHA256withECDSA").run { initVerify(key); update(tmp.readBytes()); verify(sigBytes) }
            if (!ok || tmp.length() % 32L != 0L) { tmp.delete(); return@withContext SigUpdate.Failed("Signature check failed. The file was discarded.") }
            tmp.renameTo(db.updatedFile); db.reload(); SigUpdate.Updated(db.count)
        } catch (t: Throwable) { SigUpdate.Failed(t.message ?: "Download failed") }
    }
}
