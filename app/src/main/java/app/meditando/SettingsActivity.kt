package app.meditando

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
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
import android.widget.Switch
import android.widget.Toast

/** Ajustes: modo de aviso, idioma, volúmenes, fiabilidad, actualizaciones y datos de la app. */
class SettingsActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var modeCard: LinearLayout
    private lateinit var langCard: LinearLayout
    private lateinit var reliabilityCard: LinearLayout
    private lateinit var updatesCard: LinearLayout
    /** Estado transitorio de actualizaciones (buscando, descargando, error...); null = estado real. */
    private var updateStatus: String? = null

    // Lazy: getString necesita el contexto, que no existe al construir la clase
    private val modes by lazy {
        listOf(
            getString(R.string.settings_mode_bells) to Settings.MODE_BELLS,
            getString(R.string.settings_mode_vibration) to Settings.MODE_VIBRATION,
            getString(R.string.settings_mode_both) to Settings.MODE_BOTH,
        )
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        settings = Settings(c)
        modeCard = Ui.card(c)

        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Ui.text(c, getString(R.string.settings_title), 28f, Ui.TEXT, 0), Ui.lp(c, t = 8, b = 8))

        // AVISO
        root.addView(Ui.text(c, getString(R.string.settings_section_notice), 12f, Ui.MUTED), Ui.lp(c, t = 28, b = 10, l = 4))
        root.addView(modeCard, Ui.lp(c))
        renderMode()

        // IDIOMA
        root.addView(Ui.text(c, getString(R.string.settings_section_language), 12f, Ui.MUTED), Ui.lp(c, t = 28, b = 10, l = 4))
        langCard = Ui.card(c)
        root.addView(langCard, Ui.lp(c))
        renderLanguage()

        // VOLUMEN
        root.addView(Ui.text(c, getString(R.string.settings_section_volume), 12f, Ui.MUTED), Ui.lp(c, t = 28, b = 10, l = 4))
        root.addView(Ui.card(c).apply {
            addView(volumeBlock(getString(R.string.settings_volume_final), settings.volFinal, isFinal = true) { settings.volFinal = it }, Ui.lp(c))
            addView(volumeBlock(getString(R.string.settings_volume_soft), settings.volSoft, isFinal = false) { settings.volSoft = it },
                Ui.lp(c, t = 24))
            addView(Ui.text(c, getString(R.string.settings_volume_note), 14f, Ui.MUTED), Ui.lp(c, t = 20))
        }, Ui.lp(c))

        // FIABILIDAD: el contenido se rellena en onResume()
        root.addView(Ui.text(c, getString(R.string.settings_section_reliability), 12f, Ui.MUTED), Ui.lp(c, t = 28, b = 10, l = 4))
        reliabilityCard = Ui.card(c)
        root.addView(reliabilityCard, Ui.lp(c))

        // ACTUALIZACIONES: el contenido se rellena en onResume()
        root.addView(Ui.text(c, getString(R.string.settings_section_updates), 12f, Ui.MUTED), Ui.lp(c, t = 28, b = 10, l = 4))
        updatesCard = Ui.card(c)
        root.addView(updatesCard, Ui.lp(c))

        // ACERCA DE
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull() ?: "?"
        root.addView(Ui.text(c, getString(R.string.settings_section_about), 12f, Ui.MUTED), Ui.lp(c, t = 28, b = 10, l = 4))
        root.addView(Ui.card(c).apply {
            addView(Ui.text(c, getString(R.string.settings_app_name), 22f, Ui.TEXT, 0))
            addView(Ui.text(c, getString(R.string.settings_version, version), 14f, Ui.MUTED), Ui.lp(c, t = 4))
            addView(Ui.text(c, getString(R.string.settings_about_text), 15f).apply { setLineSpacing(0f, 1.3f) },
                Ui.lp(c, t = 16))
        }, Ui.lp(c))

        val page = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(ScrollView(c).apply { addView(root) }, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        page.addView(Ui.tabBar(c, 2), Ui.lp(c, t = 8))
        Ui.page(c, page)
        setContentView(page)
    }

    override fun onResume() {
        super.onResume()
        // Con la app a la vista, instalar una actualización abre la confirmación directamente
        Updater.userInitiated = true
        renderReliability()
        renderUpdates()
    }

    override fun onPause() {
        Updater.userInitiated = false
        super.onPause()
    }

    /** Dos pastillas de igual peso; al cambiar de idioma guarda y recrea la Activity en el idioma nuevo. */
    private fun renderLanguage() {
        val c = this
        langCard.removeAllViews()
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Español" to Lang.ES, "English" to Lang.EN).forEach { (name, code) ->
            row.addView(Ui.chip(c, name, settings.lang == code) {
                if (settings.lang != code) {
                    settings.lang = code
                    recreate()
                }
            }.apply { maxLines = 1; setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0); textSize = 14f; minHeight = Ui.dp(c, 40); minimumHeight = Ui.dp(c, 40) },
                LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0) })
        }
        langCard.addView(row, Ui.lp(c))
    }

    /** Reconstruye la tarjeta de actualizaciones con el estado real (versión, nueva versión, permisos). */
    private fun renderUpdates() {
        val c = this
        updatesCard.removeAllViews()
        val installed = runCatching { Updater.currentVersion(c) }.getOrDefault("?")
        val rel = Updater.available

        val switchRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Ui.text(c, getString(R.string.settings_auto_update), 16f, Ui.TEXT, 2),
                LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            addView(Switch(c).apply {
                isChecked = settings.autoUpdate
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
                thumbTintList = ColorStateList(states, intArrayOf(Ui.GOLD, Ui.MUTED))
                trackTintList = ColorStateList(states, intArrayOf(Ui.GOLD, Ui.SURFACE2))
                setOnCheckedChangeListener { _, on ->
                    settings.autoUpdate = on
                    if (on) UpdateJob.schedule(c) else UpdateJob.cancel(c)
                }
            })
        }
        updatesCard.addView(switchRow, Ui.lp(c))
        updatesCard.addView(Ui.text(c, getString(R.string.settings_auto_update_hint), 13f, Ui.MUTED)
            .apply { setLineSpacing(0f, 1.3f) }, Ui.lp(c, t = 6))

        // Estado: transitorio (buscando/descargando/error) o el real
        val state = updateStatus ?: when {
            rel != null -> getString(R.string.settings_update_available, rel.version)
            settings.lastCheck > 0 -> getString(R.string.settings_update_latest)
            else -> null
        }
        updatesCard.addView(Ui.text(c, getString(R.string.settings_installed_version, installed), 14f, Ui.MUTED), Ui.lp(c, t = 20))
        if (state != null) updatesCard.addView(Ui.text(c, state, 15f), Ui.lp(c, t = 4))

        updatesCard.addView(Ui.secondaryButton(c, getString(R.string.settings_btn_check)) { checkNow() },
            Ui.lp(c, Ui.WRAP, Ui.WRAP, t = 16))
        if (rel != null) {
            updatesCard.addView(Ui.primaryButton(c, getString(R.string.settings_btn_update, rel.version)) { installNow(rel) },
                Ui.lp(c, t = 12))
        }

        // Permiso «Instalar apps desconocidas», con el mismo formato que FIABILIDAD
        val canInstall = Updater.canInstall(c)
        updatesCard.addView(reliabilityRow(canInstall, getString(R.string.settings_unknown_title),
            getString(if (canInstall) R.string.settings_unknown_ok else R.string.settings_unknown_missing),
            getString(R.string.settings_btn_allow)) { openUnknownSources() }, Ui.lp(c, t = 20))
    }

    private fun openUnknownSources() {
        startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
    }

    /** Busca una versión nueva en GitHub en un hilo aparte. */
    private fun checkNow() {
        val c = this
        if (Updater.busy) return
        Updater.busy = true
        updateStatus = getString(R.string.settings_update_checking)
        renderUpdates()
        Thread {
            val error = try { Updater.check(c); null } catch (e: Exception) { e.message ?: "?" }
            Updater.busy = false
            runOnUiThread {
                updateStatus = error?.let { getString(R.string.settings_update_error, it) }
                renderUpdates()
            }
        }.start()
    }

    /** Descarga (con verificación SHA-256) e instala la versión disponible, en un hilo aparte. */
    private fun installNow(rel: Updater.Release) {
        val c = this
        if (Updater.busy) return
        if (!Updater.canInstall(c)) {
            openUnknownSources()
            Toast.makeText(c, R.string.settings_toast_allow_unknown, Toast.LENGTH_LONG).show()
            return
        }
        if (Updater.sessionActive(c)) {
            Toast.makeText(c, R.string.settings_toast_session, Toast.LENGTH_LONG).show()
            return
        }
        Updater.busy = true
        updateStatus = getString(R.string.settings_update_downloading)
        renderUpdates()
        Thread {
            var error: String? = null
            try {
                val file = Updater.download(c, rel)
                runOnUiThread {
                    updateStatus = getString(R.string.settings_update_installing)
                    renderUpdates()
                }
                Updater.install(c, file)
            } catch (e: Exception) {
                error = e.message ?: "?"
            }
            Updater.busy = false
            val err = error
            if (err != null) runOnUiThread {
                updateStatus = getString(R.string.settings_update_error, err)
                renderUpdates()
            }
        }.start()
    }

    /** Reconstruye la tarjeta de fiabilidad con el estado real (se llama al volver de los ajustes del sistema). */
    private fun renderReliability() {
        val c = this
        reliabilityCard.removeAllViews()
        val batteryOk = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val notifOk = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        // Alarmas exactas: permiso propio desde Android 12 (API 31); antes siempre disponibles
        val exactOk = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        reliabilityCard.addView(reliabilityRow(batteryOk, getString(R.string.settings_battery_title),
            getString(if (batteryOk) R.string.settings_battery_ok else R.string.settings_battery_missing),
            getString(R.string.settings_btn_allow)) {
            launch(
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, Ui.lp(c))

        reliabilityCard.addView(reliabilityRow(notifOk, getString(R.string.settings_notif_title),
            getString(if (notifOk) R.string.settings_notif_ok else R.string.settings_notif_missing),
            getString(R.string.settings_btn_enable)) {
            startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
        }, Ui.lp(c, t = 16))

        reliabilityCard.addView(reliabilityRow(exactOk, getString(R.string.settings_exact_title),
            getString(if (exactOk) R.string.settings_exact_ok else R.string.settings_exact_missing),
            getString(R.string.settings_btn_allow)) {
            launch(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, Ui.lp(c, t = 16))

        reliabilityCard.addView(Ui.text(c, getString(R.string.settings_oem_note),
            13f, Ui.MUTED).apply { setLineSpacing(0f, 1.3f) }, Ui.lp(c, t = 20))
        reliabilityCard.addView(Ui.linkButton(c, getString(R.string.settings_open_app_settings)) {
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

    /** Texto explicativo del modo elegido. */
    private fun modeHint(mode: Int): String = getString(
        when (mode) {
            Settings.MODE_BELLS -> R.string.settings_hint_bells
            Settings.MODE_VIBRATION -> R.string.settings_hint_vibration
            else -> R.string.settings_hint_both
        })

    /** Reconstruye la fila de modos y el texto explicativo del modo elegido. */
    private fun renderMode() {
        val c = this
        modeCard.removeAllViews()
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        for ((name, m) in modes) {
            row.addView(Ui.chip(c, name, settings.mode == m) {
                settings.mode = m
                renderMode()
            }.apply { maxLines = 1; setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0); textSize = 14f; minHeight = Ui.dp(c, 40); minimumHeight = Ui.dp(c, 40) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0) })
        }
        modeCard.addView(row, Ui.lp(c))
        modeCard.addView(Ui.text(c, modeHint(settings.mode), 14f, Ui.MUTED), Ui.lp(c, t = 16))
    }

    /** Título con porcentaje, barra dorada que guarda al cambiar y botón de prueba. */
    private fun volumeBlock(title: String, initial: Int, isFinal: Boolean, save: (Int) -> Unit): LinearLayout {
        val c = this
        val pct = Ui.text(c, getString(R.string.settings_percent, initial), 15f, Ui.MUTED)
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
                    pct.text = getString(R.string.settings_percent, p)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(head, Ui.lp(c))
            addView(bar, Ui.lp(c, t = 8))
            addView(Ui.linkButton(c, getString(R.string.settings_btn_test)) { Feedback.trigger(c, isFinal) }, Ui.lp(c, Ui.WRAP, Ui.WRAP))
        }
    }
}
