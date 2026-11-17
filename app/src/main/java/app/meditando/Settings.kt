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
        get() = p.getInt("mode", MODE_BOTH)
        set(v) { p.edit().putInt("mode", v).apply() }

    /** Volumen del bong final, 0–100. */
    var volFinal: Int
        get() = p.getInt("volFinal", 100)
        set(v) { p.edit().putInt("volFinal", v.coerceIn(0, 100)).apply() }

    /** Volumen de las campanas intermedias, 0–100. */
    var volSoft: Int
        get() = p.getInt("volSoft", 70)
        set(v) { p.edit().putInt("volSoft", v.coerceIn(0, 100)).apply() }

    /** Idioma de la interfaz: «es» (por defecto) o «en». No sigue al idioma del móvil. */
    var lang: String
        get() = p.getString("lang", Lang.ES) ?: Lang.ES
        set(v) { p.edit().putString("lang", v).apply() }

    /** Buscar actualizaciones en GitHub automáticamente. */
    var autoUpdate: Boolean
        get() = p.getBoolean("autoUpdate", true)
        set(v) { p.edit().putBoolean("autoUpdate", v).apply() }

    var lastCheck: Long
        get() = p.getLong("lastCheck", 0L)
        set(v) { p.edit().putLong("lastCheck", v).apply() }

    val sound get() = mode != MODE_VIBRATION
    val vibrate get() = mode != MODE_BELLS
}
