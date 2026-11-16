package app.meditando

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Historial: totales, exportación CSV y sesiones por día (borrado con pulsación larga). */
class HistoryActivity : Activity() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMM", Locale("es"))
    private val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val hourFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val db by lazy { Db(applicationContext) }

    private lateinit var body: LinearLayout
    private var sessions: List<SessionRec> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Ui.linkButton(c, "‹ Volver") { finish() }, Ui.lp(c))
        root.addView(Ui.text(c, "Historial", 28f, Ui.TEXT, 0), Ui.lp(c, t = 8, b = 8))
        root.addView(body, Ui.lp(c))

        val scroll = ScrollView(c).apply { setBackgroundColor(Ui.BG); addView(root) }
        Ui.page(c, root)
        setContentView(scroll)
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        db.close()
    }

    /** Reconstruye totales, botón de exportación y lista de sesiones. */
    private fun refresh() {
        val c = this
        sessions = db.sessions()
        val now = System.currentTimeMillis()
        body.removeAllViews()
        body.addView(totalsCard(now), Ui.lp(c, t = 20))
        body.addView(Ui.secondaryButton(c, "Exportar CSV") { exportCsv() }, Ui.lp(c, t = 16))

        if (sessions.isEmpty()) {
            body.addView(Ui.text(c, "Aún no has registrado ninguna sesión.", 16f, Ui.MUTED, 1, true), Ui.lp(c, t = 48))
            return
        }

        // groupBy conserva el orden: los días salen con el más reciente primero
        val byDay = sessions.groupBy { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
        for ((day, list) in byDay) {
            body.addView(Ui.text(c, day.format(dayFmt).uppercase(Locale("es")), 12f, Ui.MUTED),
                Ui.lp(c, l = 4, t = 32, b = 4))
            list.forEach { body.addView(row(it), Ui.lp(c)) }
        }
    }

    /** Tres columnas: total de la semana, del mes y global, con su nº de sesiones. */
    private fun totalsCard(now: Long): View {
        val c = this
        val week = Stats.weekStart(now, zone)
        val month = Stats.monthStart(now, zone)
        val card = Ui.card(c).apply { orientation = LinearLayout.HORIZONTAL }
        val cols = listOf(
            Triple("Esta semana", Stats.totalSec(sessions, week), Stats.count(sessions, week)),
            Triple("Este mes", Stats.totalSec(sessions, month), Stats.count(sessions, month)),
            Triple("Total", Stats.totalSec(sessions), Stats.count(sessions)),
        )
        for ((label, sec, n) in cols) {
            val col = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(Ui.text(c, Stats.format(sec), 22f, Ui.TEXT, 0, true))
                addView(Ui.text(c, label, 14f, Ui.TEXT, 1, true), Ui.lp(c, t = 4))
                addView(Ui.text(c, if (n == 1) "1 sesión" else "$n sesiones", 12f, Ui.MUTED, 1, true))
            }
            card.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        }
        return card
    }

    private fun exportCsv() {
        val sb = StringBuilder("fecha,preset,previsto_seg,real_seg,completada\n")
        for (s in sessions) {
            val fecha = Instant.ofEpochMilli(s.startMs).atZone(zone).format(isoFmt)
            sb.append(fecha).append(',')
                .append(csvField(s.preset)).append(',')
                .append(s.plannedSec).append(',')
                .append(s.actualSec).append(',')
                .append(if (s.completed) 1 else 0).append('\n')
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Historial de meditación")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(send, "Exportar CSV"))
    }

    /** Escapa un campo CSV si contiene comas, comillas o saltos de línea. */
    private fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"${s.replace("\"", "\"\"")}\"" else s

    /** Fila: hora y preset a la izquierda, duración y estado a la derecha. */
    private fun row(s: SessionRec): View {
        val c = this
        val hora = Instant.ofEpochMilli(s.startMs).atZone(zone).format(hourFmt)
        val estado = if (s.completed) "✓" else "(parada)"
        val r = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12))
            setOnLongClickListener { confirmDelete(s); true }
        }
        r.addView(Ui.text(c, "$hora · ${s.preset}", 17f), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        r.addView(Ui.text(c, "${Stats.format(s.actualSec)}  $estado", 15f, Ui.MUTED))
        Ui.press(r)
        return r
    }

    private fun confirmDelete(s: SessionRec) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("¿Borrar esta sesión?")
            .setPositiveButton("Borrar") { _, _ -> db.deleteSession(s.id); refresh() }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
