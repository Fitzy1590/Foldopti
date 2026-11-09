package com.fitzy.foldopti.core.optimizer

import javax.inject.Inject
import javax.inject.Singleton

data class ProcessSnapshot(
    val running: Set<String>, val foregroundServices: Set<String>,
    val mediaSessions: Set<String>, val topApp: Set<String>,
)

data class CpuUse(val pkg: String, val percent: Double)

/** Parses shell dumps. Needs Shizuku; returns empty data otherwise. Parsers are pure for unit tests. */
@Singleton
class ProcessProbe @Inject constructor(private val shell: ShizukuShell) {
    suspend fun snapshot(): ProcessSnapshot {
        if (!shell.ready) return ProcessSnapshot(emptySet(), emptySet(), emptySet(), emptySet())
        return ProcessSnapshot(
            running = parseRunning(shell.exec("ps", "-A", "-o", "NAME").out),
            foregroundServices = parseForegroundServices(shell.exec("dumpsys", "activity", "services").out),
            mediaSessions = parseMediaSessions(shell.exec("dumpsys", "media_session").out),
            topApp = parseTopApp(shell.exec("dumpsys", "activity", "activities").out),
        )
    }

    suspend fun cpu(): List<CpuUse> = if (shell.ready) parseCpu(shell.exec("dumpsys", "cpuinfo").out) else emptyList()

    companion object {
        private val pkgRe = Regex("^[a-z][a-z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
        fun parseRunning(ps: String): Set<String> =
            ps.lineSequence().map { it.trim().substringBefore(':') }.filter { pkgRe.matches(it) }.toSet()

        fun parseForegroundServices(dump: String): Set<String> {
            val rec = Regex("ServiceRecord\\{[0-9a-f]+ u\\d+ ([\\w.]+)/")
            val out = mutableSetOf<String>(); var cur: String? = null
            for (line in dump.lineSequence()) {
                rec.find(line)?.let { cur = it.groupValues[1] }
                if (line.contains("isForeground=true")) cur?.let(out::add)
            }
            return out
        }

        fun parseMediaSessions(dump: String): Set<String> =
            Regex("package=([\\w.]+)").findAll(dump).map { it.groupValues[1] }.toSet()

        fun parseTopApp(dump: String): Set<String> =
            Regex("(?:mResumedActivity|topResumedActivity|ResumedActivity).*? ([\\w.]+)/").findAll(dump)
                .map { it.groupValues[1] }.toSet()

        fun parseCpu(dump: String): List<CpuUse> =
            Regex("^\\s*([\\d.]+)% \\d+/([\\w.]+):", RegexOption.MULTILINE).findAll(dump)
                .map { CpuUse(it.groupValues[2], it.groupValues[1].toDoubleOrNull() ?: 0.0) }.toList()
    }
}
