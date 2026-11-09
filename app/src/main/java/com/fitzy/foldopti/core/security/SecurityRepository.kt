package com.fitzy.foldopti.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ScanProgress(val done: Int = 0, val total: Int = 0, val running: Boolean = false)

@Singleton
class SecurityRepository @Inject constructor(private val scanner: MalwareScanner) {
    private val _findings = MutableStateFlow<List<Finding>>(emptyList())
    val findings: StateFlow<List<Finding>> = _findings.asStateFlow()
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()
    private val _lastScan = MutableStateFlow(0L)
    val lastScan: StateFlow<Long> = _lastScan.asStateFlow()

    suspend fun scan(): List<Finding> {
        _progress.value = ScanProgress(0, 0, true)
        return try {
            scanner.scan { d, t -> _progress.value = ScanProgress(d, t, true) }.also { _findings.value = it; _lastScan.value = System.currentTimeMillis() }
        } finally { _progress.value = ScanProgress(running = false) }
    }
}
