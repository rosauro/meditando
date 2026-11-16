package app.meditando

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Aviso al usuario (bong y/o vibración) según los ajustes. */
object Feedback {
    private val main = Handler(Looper.getMainLooper())

    /**
     * Devuelve el player si suena (se libera solo al acabar o al fallar).
     * [onDone] se llama al terminar el sonido; si no hay sonido (o falla), tras 1,5 s (final: 2,5 s).
     */
    fun trigger(ctx: Context, final: Boolean, onDone: (() -> Unit)? = null): MediaPlayer? {
        val s = Settings(ctx)
        if (s.vibrate) vibrate(ctx, final)
        val mp = if (s.sound) create(ctx, final, s) else null
        if (mp == null) {
            main.postDelayed({ onDone?.invoke() }, if (final) 2500L else 1500L)
            return null
        }
        mp.setOnCompletionListener { p -> p.release(); onDone?.invoke() }
        mp.setOnErrorListener { p, _, _ -> p.release(); onDone?.invoke(); true }
        mp.start()
        return mp
    }

    private fun create(ctx: Context, final: Boolean, s: Settings): MediaPlayer? = try {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        MediaPlayer.create(ctx, if (final) R.raw.bong_final else R.raw.bong_suave, attrs,
            AudioManager.AUDIO_SESSION_ID_GENERATE)?.also {
            val v = (if (final) s.volFinal else s.volSoft) / 100f
            it.setVolume(v, v)
        }
    } catch (e: Exception) {
        null
    }

    private fun vibrate(ctx: Context, final: Boolean) {
        val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val effect = if (final) VibrationEffect.createWaveform(longArrayOf(0, 600, 250, 600, 250, 900), -1)
        else VibrationEffect.createOneShot(250, 120)
        vib?.vibrate(effect)
    }
}
