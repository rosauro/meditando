package app.meditando

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    @Test fun sinCampanasIntermedias() =
        assertEquals(emptyList<Int>(), Schedule.bellOffsetsSec(20, 0))

    @Test fun unaHoraNueveCampanas() =
        assertEquals(listOf(360, 720, 1080, 1440, 1800, 2160, 2520, 2880, 3240),
            Schedule.bellOffsetsSec(60, 9))

    @Test fun reparteEnTramosIguales() =
        assertEquals(listOf(150, 300, 450), Schedule.bellOffsetsSec(10, 3))

    @Test fun siempreCrecienteYDentroDeLaSesion() {
        for (min in listOf(1, 5, 7, 13, 60, 90)) for (n in 0..Schedule.MAX_BELLS) {
            val t = Schedule.bellOffsetsSec(min, n)
            assertEquals(n, t.size)
            assertTrue(t.zipWithNext().all { (a, b) -> a < b })
            assertTrue(t.all { it in 1 until min * 60 })
        }
    }

    @Test fun limitaElNumeroDeCampanas() =
        assertEquals(Schedule.MAX_BELLS, Schedule.bellOffsetsSec(60, 999).size)

    @Test(expected = IllegalArgumentException::class)
    fun duracionCeroNoValida() { Schedule.bellOffsetsSec(0, 1) }
}

class StatsTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 8) =
        ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun rec(start: Long, sec: Int) = SessionRec(0, start, sec, sec, true, "x")

    @Test fun semanaEmpiezaEnLunes() {
        // miércoles 12-nov-2025 → lunes 10-nov-2025 00:00
        assertEquals(ms(2025, 11, 10, 0), Stats.weekStart(ms(2025, 11, 12), zone))
        // domingo 16-nov → mismo lunes 10
        assertEquals(ms(2025, 11, 10, 0), Stats.weekStart(ms(2025, 11, 16, 23), zone))
        // lunes a las 00:00 exactas es de la propia semana
        assertEquals(ms(2025, 11, 10, 0), Stats.weekStart(ms(2025, 11, 10, 0), zone))
    }

    @Test fun mesEmpiezaElDiaUno() =
        assertEquals(ms(2025, 11, 1, 0), Stats.monthStart(ms(2025, 11, 20), zone))

    @Test fun totalesPorPeriodo() {
        val now = ms(2025, 11, 12)
        val s = listOf(
            rec(ms(2025, 11, 12), 1200),  // esta semana
            rec(ms(2025, 11, 10), 600),   // esta semana (lunes)
            rec(ms(2025, 11, 5), 3600),   // este mes, no esta semana
            rec(ms(2025, 10, 30), 300),   // mes anterior
        )
        assertEquals(1800, Stats.totalSec(s, Stats.weekStart(now, zone)))
        assertEquals(5400, Stats.totalSec(s, Stats.monthStart(now, zone)))
        assertEquals(5700, Stats.totalSec(s))
        assertEquals(2, Stats.count(s, Stats.weekStart(now, zone)))
    }

    @Test fun formatoDeTiempo() {
        assertEquals("45 s", Stats.format(45))
        assertEquals("20 min", Stats.format(1200))
        assertEquals("1 h 05 min", Stats.format(3900))
        assertEquals("59:30", Stats.clock(3570))
        assertEquals("1:00:00", Stats.clock(3600))
    }
}

class LabelsTest {
    @Test fun unaPorLinea() =
        assertEquals(listOf("Rin", "Kyo", "Toh"), Labels.parse("Rin\nKyo\n Toh \n"))

    @Test fun separadasPorPuntoYComa() =
        assertEquals(listOf("Om Dhïm Klïm", "Vishwa Shakti", "Dhïm Ra Ra"),
            Labels.parse("Om Dhïm Klïm; Vishwa Shakti;Dhïm Ra Ra"))

    @Test fun ignoraVaciosYMezcla() =
        assertEquals(listOf("a", "b", "c"), Labels.parse(";\n a ;; b\n\n c;"))

    @Test fun vacioDaListaVacia() = assertEquals(emptyList<String>(), Labels.parse("  \n ; "))

    @Test fun presetsPorDefectoTienenNueveTextos() {
        assertEquals(9, Db.KUJI_IN.bellCount)
        assertEquals(9, Db.KAMA_CHAKRA.bellCount)
        assertEquals(5, Preset("x", 10, 5, false).bellCount)
    }
}
