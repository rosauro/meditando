package app.meditando

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar

/** Ajustes: modo de aviso, volúmenes y datos de la app. */
class SettingsActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var modeCard: LinearLayout
    private lateinit var reliabilityCard: LinearLayout

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

        // FIABILIDAD: el contenido se rellena en onResume()
        root.addView(Ui.text(c, "FIABILIDAD", 12f, Ui.MUTED), Ui.lp(c, t = 28, b = 10, l = 4))
        reliabilityCard = Ui.card(c)
        root.addView(reliabilityCard, Ui.lp(c))

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

    override fun onResume() {
        super.onResume()
        renderReliability()
    }

    /** Reconstruye la tarjeta de fiabilidad con el estado real (se llama al volver de los ajustes del sistema). */
    private fun renderReliability() {
        val c = this
        reliabilityCard.removeAllViews()
        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val notifOk = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        // Alarmas exactas: permiso propio desde Android 12 (API 31); antes siempre disponibles
        val exactOk = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        reliabilityCard.addView(reliabilityRow(batteryOk, "Batería sin restricciones",
            if (batteryOk) "Correcto: los avisos llegan con la pantalla apagada."
            else "Android puede frenar la app en segundo plano y retrasar los avisos.",
            "Permitir") {
            launch(
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, Ui.lp(c))

        reliabilityCard.addView(reliabilityRow(notifOk, "Notificaciones",
            if (notifOk) "Correcto: la sesión en curso se muestra en la barra de estado."
            else "Desactivadas: no se mostrará la notificación de la sesión en curso.",
            "Activar") {
            startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
        }, Ui.lp(c, t = 16))

        reliabilityCard.addView(reliabilityRow(exactOk, "Alarmas exactas",
            if (exactOk) "Correcto: los avisos suenan en el segundo previsto."
            else "Sin este permiso, los avisos pueden llegar con retraso.",
            "Permitir") {
            launch(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, Ui.lp(c, t = 16))

        reliabilityCard.addView(Ui.text(c,
            "En Xiaomi, Oppo, Samsung u otras marcas, activa también «Inicio automático» y «Sin restricciones» " +
                "en los ajustes de batería de esta app, y no la cierres desde recientes.",
            13f, Ui.MUTED).apply { setLineSpacing(0f, 1.3f) }, Ui.lp(c, t = 20))
        reliabilityCard.addView(Ui.linkButton(c, "Abrir ajustes de la app") {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, Ui.lp(c, Ui.WRAP, Ui.WRAP, t = 4))
    }

    /** Fila con punto de estado (verde = bien, ámbar = falta), título, explicación y botón solo si falta. */
    private fun reliabilityRow(ok: Boolean, title: String, detail: String, buttonLabel: String,
                               onAction: () -> Unit): LinearLayout {
        val c = this
        val dot = View(c).apply {
            background = Ui.shape(c, if (ok) 0xFF7FB77E.toInt() else 0xFFE0A458.toInt(), 5)
        }
        val column = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        column.addView(Ui.text(c, title, 16f, Ui.TEXT, 2))
        column.addView(Ui.text(c, detail, 13f, Ui.MUTED), Ui.lp(c, t = 4))
        if (!ok) {
            column.addView(Ui.secondaryButton(c, buttonLabel, onAction).apply {
                textSize = 14f
                minHeight = Ui.dp(c, 40)
            }, Ui.lp(c, Ui.WRAP, Ui.WRAP, t = 10))
        }
        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(dot, LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10))
                .apply { setMargins(0, Ui.dp(c, 6), Ui.dp(c, 12), 0) })
            addView(column, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        }
    }

    /** Abre [primary]; si el sistema no la resuelve, abre [fallback]. */
    private fun launch(primary: Intent, fallback: Intent) {
        try { startActivity(primary) } catch (e: Exception) { startActivity(fallback) }
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
