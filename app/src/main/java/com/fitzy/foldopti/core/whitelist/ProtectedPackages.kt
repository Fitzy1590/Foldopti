package com.fitzy.foldopti.core.whitelist

/** Packages no engine may touch, regardless of the user's list. Pure data, unit-tested. */
object ProtectedPackages {
    private val exact = setOf(
        "android", "com.android.systemui", "com.android.phone", "com.android.settings", "com.android.providers.settings",
        "com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.android.shell", "moe.shizuku.privileged.api",
        // Shield / Android TV: launcher, voice search, screensaver, setup, GameStream
        "com.google.android.tvlauncher", "com.google.android.leanbacklauncher", "com.google.android.katniss",
        "com.google.android.backdrop", "com.google.android.tvrecommendations", "com.google.android.tv",
        "com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith",
    )
    private val prefixes = listOf("com.nvidia.shieldtech.", "com.nvidia.tegrazone", "com.nvidia.ota", "com.nvidia.stats")

    fun isCore(pkg: String, ownPackage: String = "com.fitzy.foldopti"): Boolean =
        pkg == ownPackage || pkg in exact || prefixes.any { pkg.startsWith(it) }

    /** Apps offered as one-tap whitelist suggestions when installed. */
    val suggestions = mapOf(
        "com.plexapp.android" to "Media server client", "com.plexapp.mediaserver.smb" to "Plex Media Server",
        "org.xbmc.kodi" to "Kodi", "com.whatsapp" to "Messaging", "org.thoughtcrime.securesms" to "Signal",
        "org.telegram.messenger" to "Telegram", "com.google.android.apps.messaging" to "Messages",
        "com.samsung.android.messaging" to "Messages",
    )
}
