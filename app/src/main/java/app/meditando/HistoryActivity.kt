package app.meditando

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Historial: totales, exportación CSV y sesiones por día (borrado con pulsación larga). */
class HistoryActivity : Activity() {
    private val zone: ZoneId = ZoneId.systemDefault()
    // Se construye al primer uso (después de attachBaseContext) para respetar el idioma elegido
    private val dayFmt by lazy { DateTimeFormatter.ofPattern("EEEE d MMM", Lang.locale(this)) }
    private val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val hourFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val db by lazy { Db(applicationContext) }

    private lateinit var body: LinearLayout
    private var sessions: List<SessionRec> = emptyList()

    /** Idioma con el que se creó la pantalla: si cambia en Ajustes, al volver se recrea. */
    private var shownLang = ""

    override fun onResume() {
        super.onResume()
        if (Lang.get(this) != shownLang) recreate()
    }

    override fun attachBaseContext(base: Context) {
        shownLang = Lang.get(base)
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        val content = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        content.addView(Ui.text(c, getString(R.string.history_title), 28f, Ui.TEXT, 0), Ui.lp(c, t = 8, b = 8))
        content.addView(body, Ui.lp(c))

        val page = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(ScrollView(c).apply { addView(content) }, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        page.addView(Ui.tabBar(c, 1), Ui.lp(c, t = 8))
        Ui.page(c, page)
        setContentView(page)
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
        body.addView(Ui.secondaryButton(c, getString(R.string.history_export_csv)) { exportCsv() }, Ui.lp(c, t = 16))

        if (sessions.isEmpty()) {
            body.addView(Ui.text(c, getString(R.string.history_empty), 16f, Ui.MUTED, 1, true), Ui.lp(c, t = 48))
            return
        }

        // groupBy conserva el orden: los días salen con el más reciente primero
        val byDay = sessions.groupBy { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
        for ((day, list) in byDay) {
            body.addView(Ui.text(c, day.format(dayFmt).uppercase(Lang.locale(c)), 12f, Ui.MUTED),
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
            Triple(getString(R.string.history_week), Stats.totalSec(sessions, week), Stats.count(sessions, week)),
            Triple(getString(R.string.history_month), Stats.totalSec(sessions, month), Stats.count(sessions, month)),
            Triple(getString(R.string.history_total), Stats.totalSec(sessions), Stats.count(sessions)),
        )
        for ((label, sec, n) in cols) {
            val col = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(Ui.text(c, Stats.format(sec), 22f, Ui.TEXT, 0, true))
                addView(Ui.text(c, label, 14f, Ui.TEXT, 1, true), Ui.lp(c, t = 4))
                addView(Ui.text(c, resources.getQuantityString(R.plurals.history_sessions, n, n), 12f, Ui.MUTED, 1, true))
            }
            card.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        }
        return card
    }

    private fun exportCsv() {
        // La cabecera es un formato de datos: los nombres de columna no se traducen
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
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.history_mail_subject))
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(send, getString(R.string.history_export_csv)))
    }

    /** Escapa un campo CSV si contiene comas, comillas o saltos de línea. */
    private fun csvField(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"${s.replace("\"", "\"\"")}\"" else s

    /** Fila: hora y preset a la izquierda, duración y estado a la derecha. */
    private fun row(s: SessionRec): View {
        val c = this
        val hora = Instant.ofEpochMilli(s.startMs).atZone(zone).format(hourFmt)
        val estado = if (s.completed) "✓" else getString(R.string.history_stopped)
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
        Ui.dialog(this) {
            title(getString(R.string.history_delete_title))
            danger(getString(R.string.history_delete_confirm)) { db.deleteSession(s.id); refresh() }
            cancel(getString(R.string.history_cancel))
        }
    }
}
