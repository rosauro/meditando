package app.meditando

/** Lista de textos de las campanas: uno por línea, o separados por «;». */
object Labels {
    private val SEP = Regex("[\\n;]")

    fun parse(raw: String): List<String> =
        raw.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }

    fun join(list: List<String>): String = list.joinToString("\n")
}
