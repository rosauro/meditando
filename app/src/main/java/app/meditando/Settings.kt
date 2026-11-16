package app.meditando

import android.content.Context

/** Ajustes globales: modo de aviso (bongs / vibración / ambos) y volúmenes. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        const val MODE_BELLS = 0
        const val MODE_VIBRATION = 1
        const val MODE_BOTH = 2
    }

    var mode: Int
        get() = p.getInt("mode", MODE_BELLS)
        set(v) { p.edit().putInt("mode", v).apply() }

    /** Volumen del bong final, 0–100. */
    var volFinal: Int
        get() = p.getInt("volFinal", 100)
        set(v) { p.edit().putInt("volFinal", v.coerceIn(0, 100)).apply() }

    /** Volumen de las campanas intermedias, 0–100. */
    var volSoft: Int
        get() = p.getInt("volSoft", 70)
        set(v) { p.edit().putInt("volSoft", v.coerceIn(0, 100)).apply() }

    val sound get() = mode != MODE_VIBRATION
    val vibrate get() = mode != MODE_BELLS
}
