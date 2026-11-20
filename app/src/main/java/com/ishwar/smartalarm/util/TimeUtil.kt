package com.ishwar.smartalarm.util

import com.ishwar.smartalarm.data.Prefs
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtil {
    private fun zone(): ZoneId = ZoneId.systemDefault()
    private fun locale(): Locale = if (Prefs.hindi) Locale("hi", "IN") else Locale.ENGLISH

    fun today(): Long = LocalDate.now(zone()).toEpochDay()

    fun dayOf(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().toEpochDay()

    fun minuteOfDay(millis: Long): Int {
        val t = Instant.ofEpochMilli(millis).atZone(zone()).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /** epochDay + minute -> millis */
    fun millis(day: Long, minute: Int): Long =
        LocalDate.ofEpochDay(day).atStartOfDay(zone()).plusMinutes(minute.toLong()).toInstant().toEpochMilli()

    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)

    /** Som = 0 ... Ravi = 6 */
    fun weekdayIndex(day: Long): Int = LocalDate.ofEpochDay(day).dayOfWeek.value - 1

    fun formatMin(minute: Int): String {
        val m = ((minute % 1440) + 1440) % 1440
        val h = m / 60
        val mm = m % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.US, "%d:%02d %s", h12, mm, if (h < 12) "AM" else "PM")
    }

    fun formatRange(start: Int, end: Int?): String =
        if (end == null) formatMin(start) else "${formatMin(start)} – ${formatMin(end)}"

    fun formatDay(day: Long): String =
        LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale()))

    fun formatDayShort(day: Long): String =
        LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("d MMM", locale()))

    fun formatWeekday(day: Long): String =
        LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEE", locale()))

    fun formatMonth(year: Int, month: Int): String =
        LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale()))

    fun formatClock(millis: Long): String {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone())
        return formatMin(t.hour * 60 + t.minute)
    }

    fun dayLabel(day: Long): String {
        val t = today()
        return when (day) {
            t -> if (Prefs.hindi) "आज" else "Today"
            t + 1 -> if (Prefs.hindi) "कल" else "Tomorrow"
            t - 1 -> if (Prefs.hindi) "बीता कल" else "Yesterday"
            else -> formatDay(day)
        }
    }

    /** "2 घंटे 15 मिनट बाद" */
    fun relative(time: Long): String {
        val mins = ((time - System.currentTimeMillis()) / 60_000L).toInt() + 1
        if (mins <= 0) return if (Prefs.hindi) "अभी" else "now"
        val d = mins / 1440
        val h = (mins % 1440) / 60
        val m = mins % 60
        val p = mutableListOf<String>()
        if (Prefs.hindi) {
            if (d > 0) p.add("$d दिन")
            if (h > 0) p.add("$h घंटे")
            if (m > 0 || p.isEmpty()) p.add("$m मिनट")
            return p.joinToString(" ") + " बाद"
        }
        if (d > 0) p.add("${d}d")
        if (h > 0) p.add("${h}h")
        if (m > 0 || p.isEmpty()) p.add("${m}m")
        return "in " + p.joinToString(" ")
    }

    val DAY_EN = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val DAY_HI = arrayOf("सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि", "रवि")
    fun dayName(i: Int) = if (Prefs.hindi) DAY_HI[i] else DAY_EN[i]

    fun daysText(mask: Int): String = when (mask) {
        0b1111111 -> if (Prefs.hindi) "रोज़" else "Every day"
        0b0011111 -> if (Prefs.hindi) "सोम – शुक्र" else "Mon – Fri"
        0b1100000 -> if (Prefs.hindi) "शनि – रवि" else "Weekend"
        0 -> if (Prefs.hindi) "कोई दिन नहीं" else "No days"
        else -> (0..6).filter { (mask and (1 shl it)) != 0 }.joinToString(", ") { dayName(it) }
    }
}
