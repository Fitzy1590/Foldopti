package com.fitzy.foldopti.core.update

data class SemVer(val major: Int, val minor: Int, val patch: Int, val pre: String?) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }).let { if (it != 0) return it }
        return when { pre == other.pre -> 0; pre == null -> 1; other.pre == null -> -1; else -> pre.compareTo(other.pre) }
    }
    companion object {
        fun parse(tag: String): SemVer? {
            val m = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?").find(tag.trim()) ?: return null
            return SemVer(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].ifEmpty { null })
        }
    }
}
