package blbl.cat3399.core.util

import java.util.Locale

object Format {
    fun duration(sec: Int): String {
        val s = if (sec < 0) 0 else sec
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, ss)
        else String.format(Locale.US, "%02d:%02d", m, ss)
    }

    fun count(n: Long?): String {
        val v = n ?: return "-"
        return when {
            v >= 100_000_000 -> String.format(Locale.US, "%.1f亿", v / 100_000_000.0)
            v >= 10_000 -> String.format(Locale.US, "%.1f万", v / 10_000.0)
            else -> v.toString()
        }
    }
}

