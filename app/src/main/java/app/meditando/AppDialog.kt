package app.meditando

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Diálogo con el estilo de la app (superficie oscura, título ligero, botones pastilla).
 * Los botones se apilan en el orden en que se añaden y cierran el diálogo al pulsarse.
 *
 *   Ui.dialog(this) { title("…"); message("…"); primary("Guardar") { … }; cancel("Cancelar") }
 */
class AppDialog(private val a: Activity) {
    private val col = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.shape(a, Ui.SURFACE, 26, Ui.GOLD_DIM, 1)
        setPadding(Ui.dp(a, 24), Ui.dp(a, 24), Ui.dp(a, 24), Ui.dp(a, 16))
    }
    private var dialog: Dialog? = null

    fun title(s: String) = apply { col.addView(Ui.text(a, s, 22f, Ui.TEXT, 0), Ui.lp(a, b = 12)) }

    fun message(s: String) = apply {
        col.addView(Ui.text(a, s, 15f, Ui.MUTED).apply { setLineSpacing(0f, 1.25f) }, Ui.lp(a, b = 16))
    }

    /** Contenido propio (por ejemplo, un campo de texto de [Ui.inputField]). */
    fun view(v: View) = apply { col.addView(v, Ui.lp(a, b = 8)) }

    /** Acción principal (pastilla dorada). */
    fun primary(label: String, onClick: () -> Unit = {}) = apply {
        col.addView(Ui.primaryButton(a, label) { dialog?.dismiss(); onClick() }, Ui.lp(a, t = 8))
    }

    /** Acción secundaria (pastilla con borde). */
    fun secondary(label: String, onClick: () -> Unit = {}) = apply {
        col.addView(Ui.secondaryButton(a, label) { dialog?.dismiss(); onClick() }, Ui.lp(a, t = 8))
    }

    /** Acción destructiva (pastilla con borde rojizo). */
    fun danger(label: String, onClick: () -> Unit = {}) = apply {
        val b = Ui.secondaryButton(a, label) { dialog?.dismiss(); onClick() }
        b.setTextColor(DANGER)
        b.background = Ui.shape(a, 0, 28, DANGER_DIM, 1)
        col.addView(b, Ui.lp(a, t = 8))
    }

    /** Cerrar sin hacer nada (texto sin fondo, centrado). */
    fun cancel(label: String) = apply {
        col.addView(Ui.linkButton(a, label) { dialog?.dismiss() }.apply { gravity = Gravity.CENTER }, Ui.lp(a, t = 4))
    }

    fun show(): Dialog {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) })
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val w = minOf((a.resources.displayMetrics.widthPixels * 0.92f).toInt(), Ui.dp(a, 440))
            setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        d.setCanceledOnTouchOutside(true)
        dialog = d
        d.show()
        return d
    }

    private companion object {
        val DANGER = 0xFFE08A7C.toInt()
        val DANGER_DIM = 0xFF8A4A44.toInt()
    }
}

/** Crea y muestra un [AppDialog]. */
fun Ui.dialog(a: Activity, build: AppDialog.() -> Unit): Dialog = AppDialog(a).apply(build).show()
