package app.meditando

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class SessionRec(
    val id: Long,
    val startMs: Long,
    val plannedSec: Int,
    val actualSec: Int,
    val completed: Boolean,
    val preset: String,
)

/** Agregados del historial (semana lunes–domingo, mes natural, zona local). */
object Stats {
    fun weekStart(nowMs: Long, zone: ZoneId): Long =
        LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthStart(nowMs: Long, zone: ZoneId): Long =
        LocalDate.ofInstant(Instant.ofEpochMilli(nowMs), zone)
            .withDayOfMonth(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun totalSec(sessions: List<SessionRec>, fromMs: Long = Long.MIN_VALUE): Int =
        sessions.filter { it.startMs >= fromMs }.sumOf { it.actualSec }

    fun count(sessions: List<SessionRec>, fromMs: Long = Long.MIN_VALUE): Int =
        sessions.count { it.startMs >= fromMs }

    fun format(sec: Int): String {
        if (sec < 60) return "$sec s"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "%d h %02d min".format(h, m) else "$m min"
    }

    fun clock(sec: Long): String {
        val s = sec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%02d:%02d".format(m, r)
    }
}
