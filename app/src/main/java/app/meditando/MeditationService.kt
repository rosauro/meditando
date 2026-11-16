package app.meditando

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/** Servicio en primer plano que cronometra la meditación, lanza las campanas y guarda la sesión. */
class MeditationService : Service() {

    companion object {
        const val ACTION_START = "app.meditando.START"
        const val ACTION_STOP = "app.meditando.STOP"
        const val ACTION_TICK = "app.meditando.TICK" // la dispara AlarmManager
        const val EXTRA_MIN = "min"
        const val EXTRA_BELLS = "bells"
        const val EXTRA_PREP = "prep"
        const val EXTRA_NAME = "name"
        const val EXTRA_LABELS = "labels"
        private const val LATE_MS = 8_000L        // un aviso así de atrasado, en una sesión recuperada, ya no suena
        private const val LATE_FINAL_MS = 60_000L // el bong final pierde su sentido pasado este tiempo

        private const val CHANNEL_ID = "meditando"
        private const val NOTIF_ID = 1
        private const val PREP_MS = 10_000L
        private const val TAIL_MS = 60_000L

        /** Si [labels] no está vacío, define el nº de campanas (ignora [bells]). */
        fun start(ctx: Context, min: Int, bells: Int, prep: Boolean, name: String, labels: List<String> = emptyList()) {
            ctx.startForegroundService(
                Intent(ctx, MeditationService::class.java).setAction(ACTION_START)
                    .putExtra(EXTRA_MIN, min).putExtra(EXTRA_BELLS, bells)
                    .putExtra(EXTRA_PREP, prep).putExtra(EXTRA_NAME, name)
                    .putExtra(EXTRA_LABELS, labels.toTypedArray())
            )
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, MeditationService::class.java).setAction(ACTION_STOP))
        }
    }

    private enum class Kind { INICIO_SUAVE, CAMPANA, FINAL }
    private class Ev(val at: Long, val kind: Kind) // at = instante elapsedRealtime en ms

    private val handler = Handler(Looper.getMainLooper())
    private val players = mutableListOf<MediaPlayer>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var events: List<Ev> = emptyList()
    private var idx = 0
    private var plannedMin = 0
    private var sessionName = ""
    private var startWallMs = 0L
    private val tick = Runnable { fireDue() }
    private var restored = false // true solo durante la puesta al día de una sesión recuperada

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(intent)
            ACTION_STOP -> stopByUser()
            ACTION_TICK -> onTick()
            else -> if (!SessionState.running) stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Alarma: si el proceso fue matado y la sesión sigue vigente, se reconstruye desde disco. */
    private fun onTick() {
        if (SessionState.running) { fireDue(); return }
        if (!restoreSession()) {
            // Llegó como servicio en primer plano: hay que llamar a startForeground antes de salir
            goForeground(buildNotif("", SystemClock.elapsedRealtime()))
            stopSelf()
        }
    }

    private fun startSession(intent: Intent) {
        // Si ya hay sesión, no se reinicia; solo se confirma el primer plano.
        if (SessionState.running) {
            goForeground(buildNotif(SessionState.presetName, SessionState.medEnd))
            return
        }
        val min = intent.getIntExtra(EXTRA_MIN, 1).coerceAtLeast(1) // evita 0 o negativos
        val labels = intent.getStringArrayExtra(EXTRA_LABELS)?.toList() ?: emptyList()
        val bells = if (labels.isNotEmpty()) (labels.size - 1).coerceAtLeast(0) else intent.getIntExtra(EXTRA_BELLS, 0)
        val prep = intent.getBooleanExtra(EXTRA_PREP, false)
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""
        val t0El = SystemClock.elapsedRealtime()
        val t0Wall = System.currentTimeMillis()
        saveState(min, bells, prep, name, labels, t0El, t0Wall)
        begin(min, bells, prep, name, labels, t0El, t0Wall)
    }

    /** Monta la sesión (nueva o recuperada) a partir de su instante de inicio [t0El] (elapsedRealtime). */
    private fun begin(min: Int, bells: Int, prep: Boolean, name: String, labels: List<String>, t0El: Long, t0Wall: Long) {
        plannedMin = min
        sessionName = name
        val prepEnd = t0El + (if (prep) PREP_MS else 0L)
        val medEnd = prepEnd + plannedMin * 60_000L
        val offsets = Schedule.bellOffsetsSec(plannedMin, bells)
        startWallMs = t0Wall + (prepEnd - t0El)
        SessionState.presetName = sessionName
        SessionState.prepEnd = prepEnd
        SessionState.medStart = prepEnd
        SessionState.medEnd = medEnd
        SessionState.plannedSec = plannedMin * 60
        SessionState.labels = labels
        SessionState.totalBells = offsets.size
        SessionState.bellsDone = 0
        SessionState.bellCounter = 0
        SessionState.currentLabel = if (prep) "" else labels.getOrElse(0) { "" } // el 1.º texto suena al empezar
        SessionState.phase = if (prep) SessionState.PREP else SessionState.MEDITATING

        // Primer plano lo antes posible
        goForeground(buildNotif(sessionName, medEnd, SessionState.currentLabel.ifEmpty { "Meditando · $sessionName" }))

        // Mantener la CPU despierta lo que queda de sesión + margen
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "meditando:sesion").apply {
            acquire((medEnd - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + TAIL_MS)
        }

        events = buildList {
            if (prep) add(Ev(prepEnd, Kind.INICIO_SUAVE))
            offsets.forEach { add(Ev(prepEnd + it * 1000L, Kind.CAMPANA)) }
            add(Ev(medEnd, Kind.FINAL))
        }
        idx = 0
        updateState()
        scheduleNext()
    }

    // --- Persistencia: si el sistema mata el proceso, la alarma exacta reconstruye la sesión ---
    private fun prefs() = getSharedPreferences("session", Context.MODE_PRIVATE)

    private fun saveState(min: Int, bells: Int, prep: Boolean, name: String, labels: List<String>, t0El: Long, t0Wall: Long) {
        prefs().edit().clear().putBoolean("active", true).putInt("min", min).putInt("bells", bells)
            .putBoolean("prep", prep).putString("name", name).putString("labels", Labels.join(labels))
            .putLong("t0El", t0El).putLong("t0Wall", t0Wall).apply()
    }

    private fun clearState() { prefs().edit().clear().apply() }

    /** Recupera una sesión vigente tras morir el proceso. Los avisos ya pasados se saltan sin sonar. */
    private fun restoreSession(): Boolean {
        val p = prefs()
        if (!p.getBoolean("active", false)) return false
        val t0El = p.getLong("t0El", 0L)
        if (t0El > SystemClock.elapsedRealtime()) { clearState(); return false } // el móvil se reinició
        val labels = Labels.parse(p.getString("labels", "") ?: "")
        begin(p.getInt("min", 1), p.getInt("bells", 0), p.getBoolean("prep", false),
            p.getString("name", "") ?: "", labels, t0El, p.getLong("t0Wall", System.currentTimeMillis()))
        restored = true
        fireDue()
        restored = false
        if (SessionState.running && labels.isNotEmpty()) updateNotif()
        return true
    }

    /** Dispara todos los eventos ya vencidos (Handler y alarma pueden llamar a la vez) y planifica el siguiente. */
    private fun fireDue() {
        while (SessionState.running) {
            val ev = events.getOrNull(idx) ?: break
            val now = SystemClock.elapsedRealtime()
            if (ev.at > now + 50) break
            val limit = if (ev.kind == Kind.FINAL) LATE_FINAL_MS else LATE_MS
            fire(ev, silent = restored && now - ev.at > limit)
        }
        scheduleNext()
    }

    private fun fire(ev: Ev, silent: Boolean = false) {
        idx++
        when (ev.kind) {
            Kind.INICIO_SUAVE -> {
                startWallMs = System.currentTimeMillis()
                SessionState.phase = SessionState.MEDITATING
                SessionState.currentLabel = SessionState.labels.getOrElse(0) { "" }
                if (!silent) {
                    if (SessionState.labels.isNotEmpty()) updateNotif()
                    feedback(false)
                }
            }
            Kind.CAMPANA -> {
                SessionState.bellsDone++
                SessionState.currentLabel = SessionState.labels.getOrElse(SessionState.bellsDone) { "" }
                SessionState.bellCounter++
                if (!silent) {
                    feedback(false)
                    if (SessionState.labels.isNotEmpty()) updateNotif()
                }
            }
            Kind.FINAL -> finish(silent)
        }
        updateState()
    }

    private fun finish(silent: Boolean = false) {
        SessionState.phase = SessionState.FINISHING
        saveSession(SessionRec(0, startWallMs, plannedMin * 60, plannedMin * 60, true, sessionName))
        clearState() // ya guardada: no debe recuperarse otra vez
        if (silent) stopSelf() else feedback(true) { stopSelf() }
    }

    private fun tickPi() = PendingIntent.getForegroundService(
        this, 2, Intent(this, MeditationService::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Planifica el siguiente evento con Handler y con una alarma exacta de respaldo (sobrevive al Doze). */
    private fun scheduleNext() {
        handler.removeCallbacks(tick)
        val ev = events.getOrNull(idx) ?: return
        handler.postDelayed(tick, (ev.at - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        val am = getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, ev.at, tickPi())
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, ev.at, tickPi())
        }
    }

    private fun cancelAlarm() = getSystemService(AlarmManager::class.java).cancel(tickPi())

    /** Refresca los campos de SessionState según los eventos que quedan. */
    private fun updateState() {
        val pending = events.drop(idx)
        val bells = pending.filter { it.kind == Kind.CAMPANA }
        SessionState.nextBellAt = bells.firstOrNull()?.at ?: 0L
        SessionState.bellsLeft = bells.size
    }

    /** Parada pedida por el usuario: solo se guarda si la meditación estaba en curso. */
    private fun stopByUser() {
        if (SessionState.phase == SessionState.MEDITATING) {
            val actualSec = ((SystemClock.elapsedRealtime() - SessionState.prepEnd) / 1000)
                .coerceIn(0L, plannedMin * 60L).toInt()
            if (actualSec >= 10) {
                saveSession(SessionRec(0, startWallMs, plannedMin * 60, actualSec, false, sessionName))
            }
        }
        clearState()
        cancelAlarm()
        handler.removeCallbacks(tick)
        events = emptyList()
        idx = 0
        stopSelf()
    }

    private fun saveSession(rec: SessionRec) {
        val db = Db(this)
        try { db.addSession(rec) } finally { db.close() }
    }

    /** Aviso sonoro/vibración; el player se guarda para liberarlo en onDestroy. */
    private fun feedback(final: Boolean, onDone: (() -> Unit)? = null) {
        Feedback.trigger(this, final, onDone)?.let { players.add(it) }
    }

    private fun buildNotif(name: String, medEndElapsed: Long, text: String = "Meditando · $name"): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Meditando", NotificationManager.IMPORTANCE_LOW))

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, MeditationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val remainingMs = (medEndElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + remainingMs)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_launcher), "Parar", stopPi
                ).build()
            )
            .build()
    }

    /** Actualiza la notificación (mismo id y cronómetro) con la última campana. */
    private fun updateNotif() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotif(sessionName, SessionState.medEnd, SessionState.currentLabel))
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun removeForeground() {
        if (Build.VERSION.SDK_INT >= 33) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
    }

    override fun onDestroy() {
        cancelAlarm()
        clearState()
        handler.removeCallbacksAndMessages(null)
        players.forEach { runCatching { it.release() } } // puede estar ya liberado: no pasa nada
        players.clear()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        SessionState.phase = SessionState.IDLE
        SessionState.nextBellAt = 0L
        SessionState.bellsLeft = 0
        SessionState.labels = emptyList()
        SessionState.currentLabel = ""
        SessionState.bellsDone = 0
        SessionState.totalBells = 0
        removeForeground()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
