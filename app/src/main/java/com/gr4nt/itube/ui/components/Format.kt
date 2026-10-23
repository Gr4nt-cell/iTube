package com.gr4nt.itube.ui.components

import java.util.Locale

object Format {
    fun count(n: Long): String = when {
        n < 0 -> ""
        n < 1_000 -> n.toString()
        n < 1_000_000 -> trim(n / 1_000.0) + "K"
        n < 1_000_000_000 -> trim(n / 1_000_000.0) + "M"
        else -> trim(n / 1_000_000_000.0) + "B"
    }

    private fun trim(v: Double): String =
        if (v >= 100) v.toLong().toString() else String.format(Locale.US, "%.1f", v).removeSuffix(".0")

    fun views(n: Long) = if (n < 0) "" else "${count(n)} views"

    fun subs(n: Long) = if (n < 0) "" else "${count(n)} subscribers"

    fun duration(sec: Long): String {
        if (sec <= 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun time(ms: Long) = duration(ms / 1000).ifEmpty { "0:00" }

    fun bytes(b: Long): String = when {
        b <= 0 -> ""
        b < 1024 * 1024 -> "${b / 1024} KB"
        b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
        else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
    }

    fun meta(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")
}
