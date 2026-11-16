package app.meditando

/** Cálculo (puro) de cuándo suenan las campanas intermedias. */
object Schedule {
    const val MAX_BELLS = 30

    /**
     * Instantes (segundos desde el inicio) de las [bells] campanas intermedias,
     * repartidas a intervalos iguales dentro de [durationMin] minutos.
     * Con N campanas se divide la sesión en N+1 tramos iguales.
     */
    fun bellOffsetsSec(durationMin: Int, bells: Int): List<Int> {
        require(durationMin > 0) { "La duración debe ser positiva" }
        val n = bells.coerceIn(0, MAX_BELLS)
        val total = durationMin * 60
        return (1..n).map { k -> Math.round(total.toDouble() * k / (n + 1)).toInt() }
    }

    /** Posición de las campanas intermedias en el anillo (0..1): 1 → [0.5]; 2 → [1/3, 2/3]. El inicio/fin es la raya en lo alto. */
    fun tickFractions(bells: Int): List<Float> {
        val n = bells.coerceIn(0, MAX_BELLS)
        return (1..n).map { it / (n + 1f) }
    }
}
