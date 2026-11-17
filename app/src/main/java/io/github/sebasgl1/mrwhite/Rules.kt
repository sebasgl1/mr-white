package io.github.sebasgl1.mrwhite

import java.text.Normalizer
import java.util.Random

enum class Role(val label: String) { CIVIL("Civil"), UNDERCOVER("Undercover"), MR_WHITE("Mr. White") }

enum class Winner { CIVILES, IMPOSTORES, MR_WHITE }

/** Reglas del juego sin nada de Android, para poder probarlas en cada compilación. */
object Rules {
    fun maxImpostors(n: Int): Int = maxOf(1, (n - 1) / 2)

    /** Cantidad sugerida (Undercover, Mr. White) según el número de jugadores. */
    fun recommended(n: Int): Pair<Int, Int> {
        if (n < 3) return 1 to 0
        val total = Math.round(n * 0.4).toInt().coerceIn(1, maxImpostors(n))
        val wanted = if (n >= 12) 2 else if (n >= 5) 1 else 0
        val mw = wanted.coerceAtMost(total - 1).coerceAtLeast(0)
        return (total - mw) to mw
    }

    /** Peso para volver a ser impostor según el historial reciente (C/U/W por ronda). */
    fun impostorWeight(h: String): Double {
        val streak = h.reversed().takeWhile { it != 'C' }.length
        val recent = h.takeLast(3).count { it != 'C' }
        return if (streak >= 2) 0.05 else 1.0 / (1.0 + 1.5 * recent)
    }

    /**
     * Reparto "justo": al azar, pero quien fue impostor hace poco tiene menos probabilidad
     * de volver a serlo, y casi nunca tres veces seguidas.
     */
    fun assignRoles(names: List<String>, undercover: Int, white: Int, hist: Map<String, String>, rng: Random): List<Role> {
        val pool = names.indices.toMutableList()
        val chosen = mutableListOf<Int>()
        repeat(undercover + white) {
            val w = pool.map { impostorWeight(hist[names[it]] ?: "") }
            var r = rng.nextDouble() * w.sum()
            var pick = pool.last()
            for ((j, i) in pool.withIndex()) {
                r -= w[j]
                if (r <= 0) { pick = i; break }
            }
            chosen.add(pick)
            pool.remove(pick)
        }
        // Mr. White: preferir a quien no lo fue en la ronda anterior
        val whites = chosen
            .sortedBy { (if ((hist[names[it]] ?: "").endsWith("W")) 1.0 else 0.0) + rng.nextDouble() * 0.9 }
            .take(white).toSet()
        return names.indices.map { i ->
            if (i in whites) Role.MR_WHITE else if (i in chosen) Role.UNDERCOVER else Role.CIVIL
        }
    }

    fun pushHistory(hist: Map<String, String>, names: List<String>, roles: List<Role>): Map<String, String> {
        val out = hist.toMutableMap()
        names.forEachIndexed { i, n ->
            val c = when (roles[i]) { Role.CIVIL -> 'C'; Role.UNDERCOVER -> 'U'; Role.MR_WHITE -> 'W' }
            out[n] = ((out[n] ?: "") + c).takeLast(6)
        }
        return out
    }

    /** Orden de pistas: empieza alguien vivo al azar, pero nunca Mr. White. */
    fun clueOrder(roles: List<Role>, alive: List<Boolean>, rng: Random): List<Int> {
        val live = roles.indices.filter { alive[it] }
        if (live.isEmpty()) return live
        val starters = live.filter { roles[it] != Role.MR_WHITE }.ifEmpty { live }
        val start = live.indexOf(starters[rng.nextInt(starters.size)])
        return live.drop(start) + live.take(start)
    }

    /** Quién gana tras una eliminación, o null si el juego sigue. */
    fun winner(roles: List<Role>, alive: List<Boolean>): Winner? {
        val impostors = roles.indices.count { alive[it] && roles[it] != Role.CIVIL }
        val civilians = roles.indices.count { alive[it] && roles[it] == Role.CIVIL }
        return if (impostors == 0) Winner.CIVILES else if (civilians <= 1) Winner.IMPOSTORES else null
    }

    /**
     * Puntos de la ronda. Civiles que ganan: +2. Impostores vivos al ganar: Undercover +10, Mr. White +6.
     * Si Mr. White adivina: +6 para él y +2 para los demás impostores que sigan vivos.
     */
    fun points(w: Winner, roles: List<Role>, alive: List<Boolean>, whiteIndex: Int = -1): List<Int> =
        roles.indices.map { i ->
            when (w) {
                Winner.CIVILES -> if (roles[i] == Role.CIVIL) 2 else 0
                Winner.IMPOSTORES ->
                    if (!alive[i]) 0 else if (roles[i] == Role.UNDERCOVER) 10 else if (roles[i] == Role.MR_WHITE) 6 else 0
                Winner.MR_WHITE ->
                    if (i == whiteIndex) 6 else if (roles[i] != Role.CIVIL && alive[i]) 2 else 0
            }
        }

    fun normalize(s: String): String =
        Normalizer.normalize(s.replace(Regex("\\(.*?\\)"), " ").trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val ARTICLES = setOf("el", "la", "los", "las", "un", "una", "unos", "unas")

    /** Formas posibles de una palabra sin plural: mesas→mesa, trenes→tren, cafés→café, luces→luz. */
    private fun forms(t: String): Set<String> {
        val out = mutableSetOf(t)
        if (t.length > 3 && t.endsWith("s")) out.add(t.dropLast(1))
        if (t.length > 4 && t.endsWith("es")) out.add(t.dropLast(2))
        if (t.length > 4 && t.endsWith("ces")) out.add(t.dropLast(3) + "z")
        return out
    }

    private fun tokens(s: String): List<String> =
        normalize(s).split(" ").filter { it.isNotEmpty() && it !in ARTICLES }

    /** Compara la adivinanza con la palabra sin importar tildes, mayúsculas, artículos ni plurales. */
    fun sameWord(guess: String, word: String): Boolean {
        val g = tokens(guess)
        val w = tokens(word)
        if (g.isEmpty() || g.size != w.size) return false
        return g.indices.all { (forms(g[it]) intersect forms(w[it])).isNotEmpty() }
    }

    /** Identificador estable de un par: no cambia si se agregan o quitan otros pares de la librería. */
    fun pairId(a: String, b: String): String {
        val crc = java.util.zip.CRC32()
        crc.update("$a|$b".toByteArray(Charsets.UTF_8))
        return "p" + java.lang.Long.toHexString(crc.value)
    }
}
