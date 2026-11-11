package io.github.sebasgl1.mrwhite

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Revisa la librería de palabras: sin vacíos y sin que una palabra aparezca en dos pares. */
class WordsTest {
    private val words = JSONArray(File("src/main/assets/palabras.json").readText(Charsets.UTF_8))

    @Test fun hayPalabrasEnAmbosModos() {
        val modos = (0 until words.length()).map { words.getJSONObject(it).getString("modo") }
        assertTrue(modos.count { it == "General" } > 100)
        assertTrue(modos.count { it == "Colombiano" } > 50)
    }

    @Test fun ningunaPalabraSeRepiteEntrePares() {
        val all = mutableListOf<String>()
        for (i in 0 until words.length()) {
            val o = words.getJSONObject(i)
            val a = o.getString("civil").trim(); val b = o.getString("undercover").trim()
            assertTrue("par $i vacío", a.isNotEmpty() && b.isNotEmpty())
            assertTrue("par $i con palabras iguales", !Rules.sameWord(a, b))
            all.add(a.lowercase()); all.add(b.lowercase())
        }
        val dup = all.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        assertEquals("palabras repetidas: $dup", 0, dup.size)
    }
}
