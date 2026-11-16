package app.meditando

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar

/** Ajustes: modo de aviso, volúmenes y datos de la app. */
class SettingsActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var modeCard: LinearLayout

    private val modes = listOf(
        "Bongs" to Settings.MODE_BELLS,
        "Vibración" to Settings.MODE_VIBRATION,
        "Ambos" to Settings.MODE_BOTH,
    )
    private val modeHints = mapOf(
        Settings.MODE_BELLS to "Suena el bong y no vibra.",
        Settings.MODE_VIBRATION to "Solo vibra, sin sonido: ideal en silencio.",
        Settings.MODE_BOTH to "Suena el bong y además vibra.",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        settings = Settings(c)
        modeCard = Ui.card(c)

        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Ui.text(c, "Ajustes", 28f, Ui.TEXT, 0), Ui.lp(c, t = 8, b = 8))

        // AVISO
        root.addView(Ui.text(c, "AVISO", 12f, Ui.MUTED), Ui.lp(c, t = 28, b = 10, l = 4))
        root.addView(modeCard, Ui.lp(c))
        renderMode()

        // VOLUMEN
        root.addView(Ui.text(c, "VOLUMEN", 12f, Ui.MUTED), Ui.lp(c, t = 28, b = 10, l = 4))
        root.addView(Ui.card(c).apply {
            addView(volumeBlock("Bong final", settings.volFinal, isFinal = true) { settings.volFinal = it }, Ui.lp(c))
            addView(volumeBlock("Campanas intermedias", settings.volSoft, isFinal = false) { settings.volSoft = it },
                Ui.lp(c, t = 24))
            addView(Ui.text(c, "Se aplica sobre el volumen multimedia del móvil.", 14f, Ui.MUTED), Ui.lp(c, t = 20))
        }, Ui.lp(c))

        // ACERCA DE
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull() ?: "?"
        root.addView(Ui.text(c, "ACERCA DE", 12f, Ui.MUTED), Ui.lp(c, t = 28, b = 10, l = 4))
        root.addView(Ui.card(c).apply {
            addView(Ui.text(c, "Meditando", 22f, Ui.TEXT, 0))
            addView(Ui.text(c, "Versión $version", 14f, Ui.MUTED), Ui.lp(c, t = 4))
            addView(Ui.text(c,
                "Temporizador de meditación sencillo: eliges la duración, campanas intermedias con texto " +
                    "opcional y un bong final. Guarda preselecciones y un historial de tus sesiones. " +
                    "Sin anuncios, sin donativos y sin conexión a internet: todo se queda en tu móvil.",
                15f).apply { setLineSpacing(0f, 1.3f) }, Ui.lp(c, t = 16))
        }, Ui.lp(c))

        val page = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(ScrollView(c).apply { addView(root) }, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        page.addView(Ui.tabBar(c, 2), Ui.lp(c, t = 8))
        Ui.page(c, page)
        setContentView(page)
    }

    /** Reconstruye la fila de modos y el texto explicativo del modo elegido. */
    private fun renderMode() {
        val c = this
        modeCard.removeAllViews()
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        for ((name, m) in modes) {
            row.addView(Ui.chip(c, name, settings.mode == m) {
                settings.mode = m
                renderMode()
            }.apply { setSingleLine(true); setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0); textSize = 14f }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0) })
        }
        modeCard.addView(row, Ui.lp(c))
        modeCard.addView(Ui.text(c, modeHints.getValue(settings.mode), 14f, Ui.MUTED), Ui.lp(c, t = 16))
    }

    /** Título con porcentaje, barra dorada que guarda al cambiar y botón de prueba. */
    private fun volumeBlock(title: String, initial: Int, isFinal: Boolean, save: (Int) -> Unit): LinearLayout {
        val c = this
        val pct = Ui.text(c, "$initial %", 15f, Ui.MUTED)
        val head = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(c, title, 17f), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            addView(pct)
        }
        val bar = SeekBar(c).apply {
            max = 100
            progress = initial
            progressTintList = ColorStateList.valueOf(Ui.GOLD)
            thumbTintList = ColorStateList.valueOf(Ui.GOLD)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    save(p)
                    pct.text = "$p %"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(head, Ui.lp(c))
            addView(bar, Ui.lp(c, t = 8))
            addView(Ui.linkButton(c, "Probar") { Feedback.trigger(c, isFinal) }, Ui.lp(c, Ui.WRAP, Ui.WRAP))
        }
    }
}
