package app.meditando

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Sistema de diseño de Meditando: paleta oscura cálida, tipografía ligera, botones pill. */
object Ui {
    val BG = 0xFF0F1115.toInt()
    val SURFACE = 0xFF1A1D23.toInt()
    val SURFACE2 = 0xFF252932.toInt()
    val TEXT = 0xFFF1ECE2.toInt()
    val MUTED = 0xFF8F8B83.toInt()
    val GOLD = 0xFFD4B073.toInt()
    val GOLD_DIM = 0xFF7A6740.toInt()

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density + 0.5f).toInt()
    fun dpf(c: Context, v: Float) = v * c.resources.displayMetrics.density

    /** Fuente: 0 = ligera, 1 = normal, 2 = media. */
    fun font(weight: Int): Typeface = Typeface.create(
        when (weight) { 0 -> "sans-serif-light"; 2 -> "sans-serif-medium"; else -> "sans-serif" }, Typeface.NORMAL)

    fun shape(c: Context, color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpf(c, radiusDp.toFloat())
            if (strokeDp > 0) setStroke(dp(c, strokeDp), strokeColor)
        }

    /** LayoutParams de LinearLayout con márgenes en dp. */
    fun lp(c: Context, w: Int = MATCH, h: Int = WRAP, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h).apply { setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b)) }

    fun space(c: Context, hDp: Int) = View(c).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(c, hDp)) }

    fun text(c: Context, s: String, sp: Float, color: Int = TEXT, weight: Int = 1, center: Boolean = false) =
        TextView(c).apply {
            text = s; textSize = sp; setTextColor(color); typeface = font(weight)
            includeFontPadding = false
            if (center) gravity = Gravity.CENTER
        }

    /** Efecto de pulsación suave (escala) sin interferir con el clic. */
    fun press(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    private fun clickable(v: View, onClick: () -> Unit) {
        press(v)
        v.setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onClick() }
    }

    /** Botón principal: pastilla dorada, 56 dp de alto. */
    fun primaryButton(c: Context, label: String, onClick: () -> Unit) =
        text(c, label, 17f, BG, 2, true).apply {
            background = shape(c, GOLD, 28)
            minHeight = dp(c, 56)
            setPadding(dp(c, 24), 0, dp(c, 24), 0)
            clickable(this, onClick)
        }

    /** Botón secundario: pastilla con borde. */
    fun secondaryButton(c: Context, label: String, onClick: () -> Unit) =
        text(c, label, 16f, GOLD, 2, true).apply {
            background = shape(c, 0, 28, GOLD_DIM, 1)
            minHeight = dp(c, 52)
            setPadding(dp(c, 24), 0, dp(c, 24), 0)
            clickable(this, onClick)
        }

    /** Texto tocable sin fondo (acciones de cabecera). */
    fun linkButton(c: Context, label: String, onClick: () -> Unit) =
        text(c, label, 15f, GOLD, 2, true).apply {
            minHeight = dp(c, 44)
            setPadding(dp(c, 12), 0, dp(c, 12), 0)
            clickable(this, onClick)
        }

    /** Pastilla seleccionable (atajos de minutos, opciones de modo). */
    fun chip(c: Context, label: String, selected: Boolean, onClick: () -> Unit) =
        text(c, label, 15f, if (selected) BG else TEXT, 2, true).apply {
            background = shape(c, if (selected) GOLD else SURFACE2, 20)
            minHeight = dp(c, 40)
            setPadding(dp(c, 16), 0, dp(c, 16), 0)
            clickable(this, onClick)
        }

    /** Tarjeta vertical con fondo de superficie. */
    fun card(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(c, SURFACE, 22)
        setPadding(dp(c, 18), dp(c, 18), dp(c, 18), dp(c, 18))
    }

    /** Fondo de la ventana y relleno con las barras del sistema (edge-to-edge). */
    @Suppress("DEPRECATION")
    fun page(a: Activity, root: View, sidePaddingDp: Int = 20) {
        a.window.decorView.setBackgroundColor(BG)
        a.window.statusBarColor = BG
        a.window.navigationBarColor = BG
        val side = dp(a, sidePaddingDp)
        root.setPadding(side, dp(a, 12), side, dp(a, 12))
        root.setOnApplyWindowInsetsListener { v, ins ->
            v.setPadding(side + ins.systemWindowInsetLeft, dp(a, 12) + ins.systemWindowInsetTop,
                side + ins.systemWindowInsetRight, dp(a, 12) + ins.systemWindowInsetBottom)
            ins
        }
    }
}
