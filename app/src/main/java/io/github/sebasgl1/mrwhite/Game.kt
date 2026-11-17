package io.github.sebasgl1.mrwhite

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

data class WordPair(
    val id: String,
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
    data class Reveal(val index: Int) : Screen()
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
    private val rng = SecureRandom()

    // Configuración (se guarda entre sesiones)
    val names = mutableStateListOf<String>().apply {
        addAll((prefs.getString("players", "") ?: "").split("\n").filter { it.isNotBlank() })
    }
    var undercover by mutableIntStateOf(prefs.getInt("uc", 1))
    var white by mutableIntStateOf(prefs.getInt("mw", 1))
    var modo by mutableStateOf(prefs.getString("modo", "Todo") ?: "Todo")
    var picante by mutableStateOf(prefs.getBoolean("picante", false))
    var bigText by mutableStateOf(prefs.getBoolean("bigText", false))

    // Partida en curso
    val players = mutableStateListOf<Player>()
    val scores = mutableStateMapOf<String, Int>()
    var screen by mutableStateOf<Screen>(Screen.Home)
    var order by mutableStateOf(listOf<Int>())
    var round by mutableIntStateOf(1)
    var civilWord by mutableStateOf("")
    var undercoverWord by mutableStateOf("")
    var category by mutableStateOf("")
    /** Hora (ms) en que se acaba el temporizador de discusión; 0 = apagado. Vive aquí para no reiniciarse al ir a votar. */
    var timerEnd by mutableStateOf(0L)

    init {
        loadScores()
        restoreState()
    }

    val impostors get() = undercover + white
    val civilians get() = names.size - impostors

    fun maxImpostors() = Rules.maxImpostors(names.size)

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

    fun recommended(): Pair<Int, Int> = Rules.recommended(names.size)

    val isRecommended get() = recommended() == (undercover to white)

    /** Al cambiar los jugadores se ponen las cantidades sugeridas; luego se pueden ajustar a mano. */
    fun applyRecommended() {
        val (uc, mw) = recommended()
        undercover = uc; white = mw
        saveConfig()
    }

    fun saveConfig() {
        prefs.edit()
            .putString("players", names.joinToString("\n"))
            .putInt("uc", undercover).putInt("mw", white)
            .putString("modo", modo).putBoolean("picante", picante).putBoolean("bigText", bigText)
            .apply()
    }

    private fun poolFor(): List<WordPair> = allWords.filter {
        (modo == "Todo" || it.modo == modo) && (picante || !it.picante)
    }

    // ---------- Partida ----------

    /** "Otra ronda": si la configuración ya no sirve, lleva a la pantalla de jugadores en vez de no hacer nada. */
    fun startOrSetup() {
        if (setupError() != null) screen = Screen.Setup else start()
    }

    fun start() {
        if (setupError() != null) return
        timerEnd = 0L
        saveConfig()
        val pool = poolFor()
        // "used2": ids estables por texto (los de antes eran por posición en la librería)
        val used = prefs.getStringSet("used2", emptySet()) ?: emptySet()
        var fresh = pool.filter { it.id !in used }
        var newUsed = used
        if (fresh.isEmpty()) { fresh = pool; newUsed = used - pool.map { it.id }.toSet() }
        val pair = fresh[rng.nextInt(fresh.size)]
        prefs.edit().putStringSet("used2", newUsed + pair.id).remove("used").apply()

        // Al azar cuál palabra es la de los civiles, para que no siempre sea la misma
        if (rng.nextBoolean()) { civilWord = pair.a; undercoverWord = pair.b } else { civilWord = pair.b; undercoverWord = pair.a }
        category = pair.categoria

        val list = names.toList()
        val hist = loadHistory()
        val roles = Rules.assignRoles(list, undercover, white, hist, rng)
        saveHistory(Rules.pushHistory(hist, list, roles))

        players.clear()
        list.forEachIndexed { i, n ->
            val r = roles[i]
            players.add(Player(n, r, when (r) { Role.CIVIL -> civilWord; Role.UNDERCOVER -> undercoverWord; Role.MR_WHITE -> null }))
        }
        list.forEach { if (it !in scores) scores[it] = 0 }
        round = 1
        order = newOrder()
        screen = Screen.Reveal(0)
    }

    private fun newOrder() = Rules.clueOrder(players.map { it.role }, players.map { it.alive }, rng)

    fun nextReveal(index: Int) {
        screen = if (index + 1 < players.size) Screen.Reveal(index + 1) else Screen.Discuss
    }

    fun eliminate(index: Int) {
        players[index] = players[index].copy(alive = false)
        screen = if (players[index].role == Role.MR_WHITE) Screen.WhiteGuess(index) else Screen.Eliminated(index)
    }

    fun whiteGuess(index: Int, guess: String) {
        if (Rules.sameWord(guess, civilWord)) finish(Winner.MR_WHITE, index)
        else screen = Screen.GuessFailed(index, guess.trim())
    }

    /** El grupo decide que la adivinanza de Mr. White sí era correcta. */
    fun acceptGuess(index: Int) = finish(Winner.MR_WHITE, index)

    fun continueAfterElimination() {
        val w = Rules.winner(players.map { it.role }, players.map { it.alive })
        if (w != null) finish(w)
        else { round++; order = newOrder(); timerEnd = 0L; screen = Screen.Discuss }
    }

    private fun finish(w: Winner, whiteIndex: Int = -1) {
        val pts = Rules.points(w, players.map { it.role }, players.map { it.alive }, whiteIndex)
        val gained = mutableMapOf<String, Int>()
        players.forEachIndexed { i, p ->
            gained[p.name] = pts[i]
            scores[p.name] = (scores[p.name] ?: 0) + pts[i]
        }
        saveScores()
        screen = Screen.Result(w, gained)
    }

    fun resetScores() {
        scores.keys.toList().forEach { scores[it] = 0 }
        saveScores()
    }

    // ---------- Guardado ----------

    private fun saveScores() {
        val o = JSONObject()
        scores.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("scores", o.toString()).apply()
    }

    private fun loadScores() {
        runCatching {
            val o = JSONObject(prefs.getString("scores", "{}") ?: "{}")
            for (k in o.keys()) scores[k] = o.getInt(k)
        }
    }

    private fun loadHistory(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString("history", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrElse { emptyMap() }

    private fun saveHistory(h: Map<String, String>) {
        prefs.edit().putString("history", JSONObject(h).toString()).apply()
    }

    /** Guarda la partida en curso para retomarla si Android cierra la app. */
    fun saveState() {
        val s = screen
        if (s is Screen.Home || s is Screen.HowTo || s is Screen.Setup || players.isEmpty()) {
            prefs.edit().remove("state").apply()
            return
        }
        val o = JSONObject()
        o.put("t", System.currentTimeMillis())
        val ps = JSONArray()
        players.forEach { p ->
            ps.put(JSONObject().put("n", p.name).put("r", p.role.name).put("w", p.word ?: "").put("a", p.alive))
        }
        val ord = JSONArray()
        order.forEach { ord.put(it) }
        o.put("players", ps).put("order", ord)
        o.put("round", round).put("civil", civilWord).put("under", undercoverWord).put("cat", category)
        when (s) {
            is Screen.Reveal -> o.put("screen", "reveal").put("index", s.index)
            is Screen.Discuss -> o.put("screen", "discuss")
            is Screen.Vote -> o.put("screen", "vote")
            is Screen.Eliminated -> o.put("screen", "eliminated").put("index", s.index)
            is Screen.WhiteGuess -> o.put("screen", "whiteguess").put("index", s.index)
            is Screen.GuessFailed -> o.put("screen", "guessfailed").put("index", s.index).put("guess", s.guess)
            is Screen.Result -> {
                val g = JSONObject()
                s.gained.forEach { (k, v) -> g.put(k, v) }
                o.put("screen", "result").put("winner", s.winner.name).put("gained", g)
            }
            else -> {}
        }
        prefs.edit().putString("state", o.toString()).apply()
    }

    private fun restoreState() {
        val raw = prefs.getString("state", null) ?: return
        try {
            val o = JSONObject(raw)
            // Una partida abandonada hace más de 6 horas no se retoma
            if (System.currentTimeMillis() - o.optLong("t", 0L) > RESUME_MS) {
                prefs.edit().remove("state").apply()
                return
            }
            val ps = o.getJSONArray("players")
            val list = (0 until ps.length()).map { i ->
                val p = ps.getJSONObject(i)
                val role = Role.valueOf(p.getString("r"))
                Player(p.getString("n"), role, if (role == Role.MR_WHITE) null else p.getString("w"), p.getBoolean("a"))
            }
            if (list.isEmpty()) return
            val ord = o.getJSONArray("order")
            val index = o.optInt("index", 0).coerceIn(0, list.size - 1)
            val scr: Screen? = when (o.getString("screen")) {
                "reveal" -> Screen.Reveal(index)
                "discuss" -> Screen.Discuss
                "vote" -> Screen.Vote
                "eliminated" -> Screen.Eliminated(index)
                "whiteguess" -> Screen.WhiteGuess(index)
                "guessfailed" -> Screen.GuessFailed(index, o.optString("guess"))
                "result" -> {
                    val g = o.getJSONObject("gained")
                    Screen.Result(Winner.valueOf(o.getString("winner")), g.keys().asSequence().associateWith { g.getInt(it) })
                }
                else -> null
            }
            if (scr == null) return
            players.clear()
            players.addAll(list)
            order = (0 until ord.length()).map { ord.getInt(it) }.filter { it in list.indices }
            round = o.getInt("round")
            civilWord = o.getString("civil")
            undercoverWord = o.getString("under")
            category = o.getString("cat")
            screen = scr
        } catch (e: Exception) {
            prefs.edit().remove("state").apply()
        }
    }

    companion object {
        const val RESUME_MS = 6 * 60 * 60 * 1000L

        private fun loadWords(context: Context): List<WordPair> {
            val text = context.assets.open("palabras.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONArray(text)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WordPair(
                    id = Rules.pairId(o.getString("civil"), o.getString("undercover")),
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
