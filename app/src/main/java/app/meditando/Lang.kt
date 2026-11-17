package app.meditando

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Idioma de la app: español por defecto, inglés opcional (independiente del idioma del móvil). */
object Lang {
    const val ES = "es"
    const val EN = "en"

    fun get(ctx: Context): String =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", ES) ?: ES

    fun locale(ctx: Context): Locale = Locale(get(ctx))

    /** Contexto con el idioma elegido; usar en attachBaseContext de Activities y servicio. */
    fun wrap(base: Context): Context {
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale(base))
        return base.createConfigurationContext(cfg)
    }
}
