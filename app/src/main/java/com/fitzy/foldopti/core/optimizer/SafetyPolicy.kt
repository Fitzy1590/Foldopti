package com.fitzy.foldopti.core.optimizer

/**
 * Hard safety limits that apply on top of the user's whitelist. FoldOpti only ever touches ordinary,
 * user-visible apps. Anything that is part of the OS, the OEM layer, or provides a system-facing service is off limits.
 */
object SafetyPolicy {
    private val denyPrefixes = listOf(
        "android", "com.android.", "com.samsung.", "com.sec.", "com.knox.", "com.qualcomm", "org.codeaurora", "vendor.",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.ext.", "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller", "com.google.android.webview", "com.google.android.inputmethod", "com.google.android.tts",
        "com.nvidia.", "com.google.android.tv", "com.google.android.leanback",
    )
    /** Ordinary apps that live under a denied prefix. */
    private val allowed = setOf("com.android.chrome", "com.sec.android.app.sbrowser", "com.samsung.android.app.notes", "com.samsung.android.messaging")

    /** Permissions that mark a service the OS binds to. Apps that expose one must keep running. */
    val bindingPermissions = setOf(
        "android.permission.BIND_ACCESSIBILITY_SERVICE", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        "android.permission.BIND_INPUT_METHOD", "android.permission.BIND_VPN_SERVICE", "android.permission.BIND_WALLPAPER",
        "android.permission.BIND_DEVICE_ADMIN", "android.permission.BIND_AUTOFILL_SERVICE", "android.permission.BIND_QUICK_SETTINGS_TILE",
        "android.permission.BIND_CONTROLS", "android.permission.BIND_DREAM_SERVICE", "android.permission.BIND_CARRIER_SERVICES",
        "android.permission.BIND_TEXT_SERVICE", "android.permission.BIND_CONDITION_PROVIDER_SERVICE", "android.permission.BIND_VOICE_INTERACTION",
        "android.permission.BIND_APPWIDGET", "android.permission.BIND_TV_INPUT", "android.permission.BIND_INCALL_SERVICE",
        "android.permission.BIND_SCREENING_SERVICE", "android.permission.BIND_CALL_REDIRECTION_SERVICE",
    )

    fun isSystemCritical(pkg: String) = pkg !in allowed && denyPrefixes.any { pkg == it || pkg.startsWith(it) }

    /** Cache clearing: launcher-visible, not OS or OEM, and either user-installed or an updatable preinstalled app. */
    fun canClearCache(pkg: String, isSystem: Boolean, isUpdatedSystem: Boolean, hasLauncher: Boolean) =
        hasLauncher && !isSystemCritical(pkg) && (!isSystem || isUpdatedSystem)

    /** Background restriction: only user-installed, launcher-visible apps that expose no system-bound service. */
    fun canRestrict(pkg: String, isSystem: Boolean, hasLauncher: Boolean, exposesBoundService: Boolean) =
        hasLauncher && !isSystem && !isSystemCritical(pkg) && !exposesBoundService

    const val MAX_CACHE_CLEARS_PER_RUN = 60
    const val MAX_RESTRICTIONS_PER_RUN = 15
}
