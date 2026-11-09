package com.fitzy.foldopti

import com.fitzy.foldopti.core.whitelist.ProtectedApp
import com.fitzy.foldopti.core.whitelist.ProtectedSource
import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun protectedAppKeepsPackage() {
        val a = ProtectedApp("com.example", "test", 0L, ProtectedSource.USER)
        assertEquals("com.example", a.pkg)
    }
}
