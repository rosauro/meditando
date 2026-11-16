package app.meditando

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator

/**
 * Anillo de progreso: pista, arco dorado, un punto por cada campana intermedia
 * (se enciende al pasar), texto central y respiración suave. [ripple] lanza una onda al sonar una campana.
 */
class RingTimerView(c: Context) : View(c) {
    /** Posiciones de las campanas como fracción 0..1 de la sesión. */
    var ticks: List<Float> = emptyList()
        set(v) { field = v; invalidate() }
    var centerText = ""
        set(v) { field = v; invalidate() }
    var subText = ""
        set(v) { field = v; invalidate() }
    var breathing = false
        set(v) { field = v; if (isAttachedToWindow) updateBreath() }

    private var shown = 0f
    private var target = 0f
    private var breath = 0f
    private var rippleT = 0f
    private var progressAnim: ValueAnimator? = null
    private var breathAnim: ValueAnimator? = null
    private var rippleAnim: ValueAnimator? = null
    private val rect = RectF()

    private fun paint(style: Paint.Style = Paint.Style.FILL) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = style }
    private val track = paint(Paint.Style.STROKE).apply { color = Ui.SURFACE2 }
    private val arc = paint(Paint.Style.STROKE).apply { color = Ui.GOLD; strokeCap = Paint.Cap.ROUND }
    private val startMark = paint(Paint.Style.STROKE).apply { color = Ui.TEXT; strokeCap = Paint.Cap.ROUND }
    private val dot = paint()
    private val dotRing = paint(Paint.Style.STROKE)
    private val glow = paint().apply { color = Ui.GOLD }
    private val ripplePaint = paint(Paint.Style.STROKE).apply { color = Ui.GOLD }
    private val main = paint().apply { color = Ui.TEXT; textAlign = Paint.Align.CENTER; typeface = Ui.font(0) }
    private val sub = paint().apply { color = Ui.MUTED; textAlign = Paint.Align.CENTER; typeface = Ui.font(1) }

    /** Fija el progreso (0..1) con una animación corta. */
    fun setProgress(p: Float, animate: Boolean = true) {
        val np = p.coerceIn(0f, 1f)
        if (np == target) return
        target = np
        progressAnim?.cancel()
        if (!animate) { shown = np; invalidate(); return }
        progressAnim = ValueAnimator.ofFloat(shown, np).apply {
            duration = 450; interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** Onda expansiva al sonar una campana. */
    fun ripple() {
        rippleAnim?.cancel()
        rippleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600; interpolator = DecelerateInterpolator()
            addUpdateListener { rippleT = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun updateBreath() {
        breathAnim?.cancel()
        if (!breathing) { breath = 0f; invalidate(); return }
        breathAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4500; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { breath = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateBreath() }
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breathAnim?.cancel(); progressAnim?.cancel(); rippleAnim?.cancel()
    }

    override fun onMeasure(w: Int, h: Int) {
        val size = minOf(MeasureSpec.getSize(w), if (MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else MeasureSpec.getSize(h))
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val size = width.toFloat()
        val stroke = size * 0.04f
        val cx = size / 2f
        val cy = size / 2f
        val r = size / 2f - stroke * 2.2f
        track.strokeWidth = stroke; arc.strokeWidth = stroke

        // respiración: halo muy tenue
        if (breathing) {
            glow.alpha = (14 + 22 * breath).toInt()
            canvas.drawCircle(cx, cy, r * (0.88f + 0.05f * breath), glow)
        }
        // onda de campana
        if (rippleT > 0f && rippleT < 1f) {
            ripplePaint.strokeWidth = stroke * 0.6f
            ripplePaint.alpha = ((1f - rippleT) * 150).toInt()
            canvas.drawCircle(cx, cy, r * (0.35f + 0.65f * rippleT), ripplePaint)
        }
        canvas.drawCircle(cx, cy, r, track)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        if (shown > 0f) canvas.drawArc(rect, -90f, 360f * shown, false, arc)

        // raya de inicio/fin en lo alto
        startMark.strokeWidth = stroke * 0.4f
        canvas.drawLine(cx, cy - r - stroke * 1.25f, cx, cy - r + stroke * 1.25f, startMark)

        // puntos de campana
        val dr = stroke * 0.85f
        dotRing.strokeWidth = stroke * 0.35f
        for (t in ticks) {
            val ang = Math.toRadians((-90.0 + 360.0 * t))
            val px = cx + r * Math.cos(ang).toFloat()
            val py = cy + r * Math.sin(ang).toFloat()
            val passed = t <= shown + 0.0005f
            dot.color = if (passed) Ui.GOLD else Ui.BG
            dotRing.color = if (passed) Ui.BG else Ui.GOLD_DIM
            canvas.drawCircle(px, py, dr, dot)
            canvas.drawCircle(px, py, dr, dotRing)
        }

        // texto central
        main.textSize = r * (if (centerText.length > 5) 0.36f else 0.46f)
        sub.textSize = r * 0.13f
        val mainShift = if (subText.isEmpty()) 0f else -r * 0.06f
        canvas.drawText(centerText, cx, cy - (main.ascent() + main.descent()) / 2f + mainShift, main)
        if (subText.isNotEmpty()) canvas.drawText(subText, cx, cy + r * 0.42f, sub)
    }
}
