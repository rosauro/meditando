package app.meditando

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Garantiza que español (values/) e inglés (values-en/) tienen las mismas claves y los mismos %1$s/%1$d. */
class StringsTest {
    private val res = File("src/main/res")
    private val keyRe = Regex("""<(string|plurals)\s+name="([^"]+)"[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
    private val phRe = Regex("""%\d\$[sd]""")

    /** clave -> marcadores de posición ordenados */
    private fun load(dir: String): Map<String, List<String>> {
        val out = HashMap<String, List<String>>()
        File(res, dir).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.forEach { f ->
            keyRe.findAll(f.readText()).forEach { m ->
                val key = m.groupValues[2]
                assertTrue("clave duplicada en $dir: $key", key !in out)
                out[key] = phRe.findAll(m.groupValues[3]).map { it.value }.toSet().sorted()
            }
        }
        return out
    }

    @Test fun mismasClavesEnEspanolEIngles() {
        val es = load("values")
        val en = load("values-en")
        assertTrue("no hay textos", es.isNotEmpty())
        assertEquals("faltan en inglés", emptySet<String>(), es.keys - en.keys)
        assertEquals("sobran en inglés", emptySet<String>(), en.keys - es.keys)
    }

    @Test fun mismosMarcadoresDePosicion() {
        val es = load("values")
        val en = load("values-en")
        for ((k, v) in es) assertEquals("marcadores distintos en «$k»", v, en[k])
    }
}
