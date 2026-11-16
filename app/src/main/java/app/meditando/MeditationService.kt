package app.meditando

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
        const val EXTRA_MIN = "min"
        const val EXTRA_BELLS = "bells"
        const val EXTRA_PREP = "prep"
        const val EXTRA_NAME = "name"
        const val EXTRA_LABELS = "labels"

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
    private val tick = Runnable { fire() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(intent)
            ACTION_STOP -> stopByUser()
            else -> if (!SessionState.running) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startSession(intent: Intent) {
        // Si ya hay sesión, no se reinicia; solo se confirma el primer plano.
        if (SessionState.running) {
            goForeground(buildNotif(SessionState.presetName, SessionState.medEnd))
            return
        }
        plannedMin = intent.getIntExtra(EXTRA_MIN, 1).coerceAtLeast(1) // evita 0 o negativos
        val labels = intent.getStringArrayExtra(EXTRA_LABELS)?.toList() ?: emptyList()
        val bells = if (labels.isNotEmpty()) labels.size else intent.getIntExtra(EXTRA_BELLS, 0)
        val prep = intent.getBooleanExtra(EXTRA_PREP, false)
        sessionName = intent.getStringExtra(EXTRA_NAME) ?: ""

        val t0 = SystemClock.elapsedRealtime()
        val prepEnd = t0 + (if (prep) PREP_MS else 0L)
        val medEnd = prepEnd + plannedMin * 60_000L
        val offsets = Schedule.bellOffsetsSec(plannedMin, bells)
        SessionState.presetName = sessionName
        SessionState.prepEnd = prepEnd
        SessionState.medStart = prepEnd
        SessionState.medEnd = medEnd
        SessionState.plannedSec = plannedMin * 60
        SessionState.labels = labels
        SessionState.totalBells = offsets.size
        SessionState.bellsDone = 0
        SessionState.bellCounter = 0
        SessionState.currentLabel = ""
        SessionState.phase = if (prep) SessionState.PREP else SessionState.MEDITATING
        if (!prep) startWallMs = System.currentTimeMillis()

        // Primer plano lo antes posible
        goForeground(buildNotif(sessionName, medEnd))

        // Mantener la CPU despierta toda la sesión + margen
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "meditando:sesion").apply {
            acquire((medEnd - t0) + TAIL_MS)
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

    /** Dispara el evento pendiente y planifica el siguiente. */
    private fun fire() {
        val ev = events.getOrNull(idx) ?: return
        idx++
        when (ev.kind) {
            Kind.INICIO_SUAVE -> {
                startWallMs = System.currentTimeMillis()
                SessionState.phase = SessionState.MEDITATING
                feedback(false)
            }
            Kind.CAMPANA -> {
                SessionState.bellsDone++
                SessionState.currentLabel = SessionState.labels.getOrElse(SessionState.bellsDone - 1) { "" }
                SessionState.bellCounter++
                feedback(false)
                if (SessionState.labels.isNotEmpty()) updateNotif()
            }
            Kind.FINAL -> finish()
        }
        updateState()
        scheduleNext()
    }

    private fun finish() {
        SessionState.phase = SessionState.FINISHING
        saveSession(SessionRec(0, startWallMs, plannedMin * 60, plannedMin * 60, true, sessionName))
        feedback(true) { stopSelf() }
    }

    private fun scheduleNext() {
        handler.removeCallbacks(tick)
        val ev = events.getOrNull(idx) ?: return
        handler.postDelayed(tick, (ev.at - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
    }

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
