package io.github.sebasgl1.mrwhite

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.text.Normalizer

enum class Role(val label: String) { CIVIL("Civil"), UNDERCOVER("Undercover"), MR_WHITE("Mr. White") }

enum class Winner { CIVILES, IMPOSTORES, MR_WHITE }

data class WordPair(
    val id: Int,
    val modo: String,
    val categoria: String,
    val a: String,
    val b: String,
    val picante: Boolean,
)

data class Player(val name: String, val role: Role, val word: String?, val alive: Boolean = true)

sealed class Screen {
    data object Home : Screen()
    data object HowTo : Screen()
    data object Setup : Screen()
    data class Reveal(val index: Int, val shown: Boolean) : Screen()
    data object Discuss : Screen()
    data object Vote : Screen()
    data class Eliminated(val index: Int) : Screen()
    data class WhiteGuess(val index: Int) : Screen()
    data class GuessFailed(val index: Int, val guess: String) : Screen()
    data class Result(val winner: Winner, val gained: Map<String, Int>) : Screen()
}

val MODOS = listOf("Todo", "General", "Colombiano")

class Game(context: Context) {
    private val prefs = context.getSharedPreferences("mrwhite", Context.MODE_PRIVATE)
    private val allWords: List<WordPair> = loadWords(context)

    // Configuración (se guarda entre sesiones)
    val names = mutableStateListOf<String>().apply {
        addAll((prefs.getString("players", "") ?: "").split("\n").filter { it.isNotBlank() })
    }
    var undercover by mutableIntStateOf(prefs.getInt("uc", 1))
    var white by mutableIntStateOf(prefs.getInt("mw", 1))
    var modo by mutableStateOf(prefs.getString("modo", "Todo") ?: "Todo")
    var picante by mutableStateOf(prefs.getBoolean("picante", false))

    // Partida en curso
    val players = mutableStateListOf<Player>()
    val scores = mutableStateMapOf<String, Int>()
    var screen by mutableStateOf<Screen>(Screen.Home)
    var order by mutableStateOf(listOf<Int>())
    var round by mutableIntStateOf(1)
    var civilWord by mutableStateOf("")
    var undercoverWord by mutableStateOf("")
    var category by mutableStateOf("")

    val impostors get() = undercover + white
    val civilians get() = names.size - impostors

    fun maxImpostors(n: Int = names.size) = maxOf(1, (n - 1) / 2)

    /** Mensaje de error de la configuración, o null si se puede jugar. */
    fun setupError(): String? = when {
        names.size < 3 -> "Se necesitan mínimo 3 jugadores"
        impostors < 1 -> "Debe haber al menos un Undercover o un Mr. White"
        civilians < 2 || civilians <= impostors -> "Hay demasiados impostores para ${names.size} jugadores"
        poolFor().isEmpty() -> "No hay palabras para este modo"
        else -> null
    }

    fun addName(raw: String): Boolean {
        val n = raw.trim().replace("\n", " ")
        if (n.isEmpty() || names.any { it.equals(n, ignoreCase = true) }) return false
        names.add(n)
        applyRecommended()
        return true
    }

    fun removeName(n: String) {
        names.remove(n); applyRecommended()
    }

    fun moveName(from: Int, to: Int) {
        if (to !in names.indices) return
        val n = names.removeAt(from); names.add(to, n); saveConfig()
    }

    fun setCounts(uc: Int, mw: Int) {
        undercover = uc.coerceAtLeast(0); white = mw.coerceAtLeast(0); saveConfig()
    }

    /** Cantidad sugerida (Undercover, Mr. White) según el número de jugadores. */
    fun recommended(n: Int = names.size): Pair<Int, Int> {
        if (n < 3) return 1 to 0
        val total = Math.round(n * 0.4).toInt().coerceIn(1, maxImpostors(n))
        val mw = when { n >= 12 -> 2; n >= 5 -> 1; else -> 0 }.coerceAtMost(total - 1).coerceAtLeast(0)
        return (total - mw) to mw
    }

    val isRecommended get() = recommended() == (undercover to white)

    /** Al cambiar los jugadores se ponen las cantidades sugeridas; luego se pueden ajustar a mano. */
    fun applyRecommended() {
        val (uc, mw) = recommended()
        undercover = uc; white = mw
        saveConfig()
    }

    private fun fixCounts() {
        val max = maxImpostors()
        while (impostors > max && (undercover > 0 || white > 0)) {
            if (white > 0 && white >= undercover) white-- else undercover--
        }
        if (impostors == 0 && names.size >= 3) undercover = 1
    }

    fun saveConfig() {
        prefs.edit()
            .putString("players", names.joinToString("\n"))
            .putInt("uc", undercover).putInt("mw", white)
            .putString("modo", modo).putBoolean("picante", picante)
            .apply()
    }

    private fun poolFor(): List<WordPair> = allWords.filter {
        (modo == "Todo" || it.modo == modo) && (picante || !it.picante)
    }

    fun start() {
        if (setupError() != null) return
        saveConfig()
        val pool = poolFor()
        val used = (prefs.getStringSet("used", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
        var fresh = pool.filter { it.id !in used }
        var newUsed = used
        if (fresh.isEmpty()) { fresh = pool; newUsed = used - pool.map { it.id }.toSet() }
        val pair = fresh.random()
        prefs.edit().putStringSet("used", (newUsed + pair.id).map { it.toString() }.toSet()).apply()

        // Al azar cuál palabra es la de los civiles, para que no siempre sea la misma
        if (Math.random() < 0.5) { civilWord = pair.a; undercoverWord = pair.b } else { civilWord = pair.b; undercoverWord = pair.a }
        category = pair.categoria

        val roles = (List(undercover) { Role.UNDERCOVER } + List(white) { Role.MR_WHITE } +
            List(names.size - impostors) { Role.CIVIL }).shuffled()
        players.clear()
        names.forEachIndexed { i, n ->
            val r = roles[i]
            players.add(Player(n, r, when (r) { Role.CIVIL -> civilWord; Role.UNDERCOVER -> undercoverWord; Role.MR_WHITE -> null }))
        }
        names.forEach { if (it !in scores) scores[it] = 0 }
        round = 1
        order = newOrder()
        screen = Screen.Reveal(0, false)
    }

    /** Orden de pistas: empieza alguien al azar, pero nunca Mr. White. */
    private fun newOrder(): List<Int> {
        val alive = players.indices.filter { players[it].alive }
        val starters = alive.filter { players[it].role != Role.MR_WHITE }.ifEmpty { alive }
        val first = starters.random()
        val start = alive.indexOf(first)
        return alive.drop(start) + alive.take(start)
    }

    fun nextReveal(index: Int) {
        screen = if (index + 1 < players.size) Screen.Reveal(index + 1, false) else Screen.Discuss
    }

    fun eliminate(index: Int) {
        players[index] = players[index].copy(alive = false)
        screen = if (players[index].role == Role.MR_WHITE) Screen.WhiteGuess(index) else Screen.Eliminated(index)
    }

    fun whiteGuess(index: Int, guess: String) {
        if (normalize(guess).isNotEmpty() && normalize(guess) == normalize(civilWord)) finish(Winner.MR_WHITE, index)
        else screen = Screen.GuessFailed(index, guess.trim())
    }

    fun continueAfterElimination() {
        val alive = players.filter { it.alive }
        val impostorsAlive = alive.count { it.role != Role.CIVIL }
        val civAlive = alive.size - impostorsAlive
        when {
            impostorsAlive == 0 -> finish(Winner.CIVILES)
            civAlive <= 1 -> finish(Winner.IMPOSTORES)
            else -> { round++; order = newOrder(); screen = Screen.Discuss }
        }
    }

    private fun finish(w: Winner, whiteIndex: Int = -1) {
        val gained = mutableMapOf<String, Int>()
        players.forEachIndexed { i, p ->
            val pts = when (w) {
                Winner.CIVILES -> if (p.role == Role.CIVIL) 2 else 0
                Winner.IMPOSTORES -> when {
                    !p.alive -> 0
                    p.role == Role.UNDERCOVER -> 10
                    p.role == Role.MR_WHITE -> 6
                    else -> 0
                }
                Winner.MR_WHITE -> if (i == whiteIndex) 6 else 0
            }
            gained[p.name] = pts
            scores[p.name] = (scores[p.name] ?: 0) + pts
        }
        screen = Screen.Result(w, gained)
    }

    fun resetScores() { scores.keys.toList().forEach { scores[it] = 0 } }

    companion object {
        fun normalize(s: String): String =
            Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9ñ ]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

        private fun loadWords(context: Context): List<WordPair> {
            val text = context.assets.open("palabras.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(text)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WordPair(
                    id = i,
                    modo = o.getString("modo"),
                    categoria = o.getString("categoria"),
                    a = o.getString("civil"),
                    b = o.getString("undercover"),
                    picante = o.optBoolean("picante", false),
                )
            }
        }
    }
}
