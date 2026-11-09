package com.fitzy.foldopti.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Sorted array of 32-byte SHA-256 hashes, binary searched. */
class BinaryHashSet(private val data: ByteArray) {
    val size get() = data.size / 32
    fun contains(hash: ByteArray): Boolean {
        require(hash.size == 32)
        var lo = 0; var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(mid, hash)
            if (c == 0) return true
            if (c < 0) lo = mid + 1 else hi = mid - 1
        }
        return false
    }
    private fun compare(idx: Int, h: ByteArray): Int {
        for (k in 0 until 32) {
            val a = data[idx * 32 + k].toInt() and 0xFF; val b = h[k].toInt() and 0xFF
            if (a != b) return a - b
        }
        return 0
    }
    companion object {
        fun fromHex(lines: List<String>): BinaryHashSet {
            val hashes = lines.map { it.trim().lowercase() }.filter { it.length == 64 }
                .map { h -> ByteArray(32) { i -> h.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }
                .sortedWith { a, b -> a.zip(b).firstOrNull { it.first != it.second }?.let { (x, y) -> (x.toInt() and 255) - (y.toInt() and 255) } ?: 0 }
            return BinaryHashSet(hashes.fold(ByteArray(0)) { acc, h -> acc + h })
        }
    }
}

@Singleton
class SignatureDb @Inject constructor(@ApplicationContext private val ctx: Context) {
    val updatedFile get() = File(ctx.filesDir, "signatures.bin")

    @Volatile private var cached: List<BinaryHashSet>? = null

    private fun load(): List<BinaryHashSet> = cached ?: buildList {
        runCatching { add(BinaryHashSet(ctx.assets.open("signatures.bin").use { it.readBytes() })) }
        if (updatedFile.exists()) runCatching { add(BinaryHashSet(updatedFile.readBytes())) }
    }.also { cached = it }

    fun reload() { cached = null }
    val count get() = load().sumOf { it.size }
    fun contains(sha256: ByteArray) = load().any { it.contains(sha256) }

    companion object {
        fun sha256(file: File): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { s -> val b = ByteArray(65536); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest()
        }
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    }
}
