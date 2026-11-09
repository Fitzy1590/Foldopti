package com.fitzy.foldopti.core.whitelist

import javax.inject.Inject
import javax.inject.Singleton

enum class CleanAction { CLEAR_CACHE, STOP, RESTRICT, DISABLE, UNINSTALL }

class ProtectedAppException(val pkg: String, val action: CleanAction) :
    SecurityException("$pkg is protected from $action")

/** Single choke point: every engine asks here before acting on a package. */
@Singleton
class WhitelistGuard @Inject constructor(private val dao: WhitelistDao) {
    suspend fun allows(pkg: String, @Suppress("UNUSED_PARAMETER") action: CleanAction): Boolean =
        !ProtectedPackages.isCore(pkg) && !dao.contains(pkg)

    suspend fun assertAllowed(pkg: String, action: CleanAction) {
        if (!allows(pkg, action)) throw ProtectedAppException(pkg, action)
    }

    suspend fun filterAllowed(pkgs: Collection<String>, action: CleanAction): List<String> =
        pkgs.filter { allows(it, action) }
}
