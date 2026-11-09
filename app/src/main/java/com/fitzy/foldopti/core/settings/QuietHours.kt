package com.fitzy.foldopti.core.settings

object QuietHours {
    /** True when [nowMin] (minute of day) falls inside [start, end), wrapping past midnight. */
    fun isQuiet(nowMin: Int, start: Int, end: Int): Boolean =
        if (start == end) false else if (start < end) nowMin in start until end else nowMin >= start || nowMin < end
}
