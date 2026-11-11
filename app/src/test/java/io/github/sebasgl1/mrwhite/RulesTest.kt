package io.github.sebasgl1.mrwhite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class RulesTest {
    private val names = listOf("Ana", "Beto", "Caro", "Dani", "Eli", "Fer")

    @Test fun recomendadosSegunJugadores() {
        assertEquals(1 to 0, Rules.recommended(3))
        assertEquals(1 to 0, Rules.recommended(4))
        assertEquals(1 to 1, Rules.recommended(5))
        assertEquals(1 to 1, Rules.recommended(6))
        assertEquals(2 to 1, Rules.recommended(8))
        assertEquals(3 to 1, Rules.recommended(10))
        assertEquals(3 to 2, Rules.recommended(12))
    }

    @Test fun siempreHayMasCivilesQueImpostores() {
        for (n in 3..20) {
            val (uc, mw) = Rules.recommended(n)
            assertTrue("n=$n", uc + mw >= 1)
            assertTrue("n=$n", uc + mw <= Rules.maxImpostors(n))
            assertTrue("n=$n", n - uc - mw > uc + mw)
        }
    }

    @Test fun repartoTieneLasCantidadesPedidas() {
        val rng = Random(1)
        repeat(200) {
            val roles = Rules.assignRoles(names, 1, 1, emptyMap(), rng)
            assertEquals(1, roles.count { it == Role.UNDERCOVER })
            assertEquals(1, roles.count { it == Role.MR_WHITE })
            assertEquals(4, roles.count { it == Role.CIVIL })
        }
    }

    @Test fun repartoEsParejoYCasiSinRachasDeTres() {
        val rng = Random(7)
        var hist = mapOf<String, String>()
        val times = IntArray(names.size)
        var streaks = 0
        val rounds = 6000
        repeat(rounds) {
            val roles = Rules.assignRoles(names, 1, 1, hist, rng)
            hist = Rules.pushHistory(hist, names, roles)
            names.forEachIndexed { i, n ->
                if (roles[i] != Role.CIVIL) times[i]++
                if ((hist[n] ?: "").takeLast(3).let { it.length == 3 && 'C' !in it }) streaks++
            }
        }
        // Cada jugador debe ser impostor cerca de 1/3 de las rondas
        times.forEach { assertTrue("reparto $it", it in (rounds * 0.29).toInt()..(rounds * 0.38).toInt()) }
        // Un sorteo puro da ~22 rachas de 3 por cada 100 rondas; aquí deben ser menos de 4
        assertTrue("rachas $streaks", streaks < rounds * 0.04)
    }

    @Test fun mrWhiteNuncaEmpiezaLasPistas() {
        val rng = Random(3)
        val roles = listOf(Role.MR_WHITE, Role.CIVIL, Role.CIVIL, Role.UNDERCOVER, Role.CIVIL)
        val alive = listOf(true, true, false, true, true)
        repeat(100) {
            val order = Rules.clueOrder(roles, alive, rng)
            assertEquals(setOf(0, 1, 3, 4), order.toSet())
            assertTrue(roles[order.first()] != Role.MR_WHITE)
        }
    }

    @Test fun quienGana() {
        val roles = listOf(Role.CIVIL, Role.CIVIL, Role.CIVIL, Role.UNDERCOVER, Role.MR_WHITE)
        assertNull(Rules.winner(roles, listOf(true, true, true, true, true)))
        assertNull(Rules.winner(roles, listOf(true, true, true, false, true)))
        assertEquals(Winner.CIVILES, Rules.winner(roles, listOf(true, true, true, false, false)))
        assertEquals(Winner.IMPOSTORES, Rules.winner(roles, listOf(true, false, false, true, true)))
        assertNull(Rules.winner(roles, listOf(true, true, false, true, false)))
    }

    @Test fun puntos() {
        val roles = listOf(Role.CIVIL, Role.CIVIL, Role.UNDERCOVER, Role.MR_WHITE)
        assertEquals(listOf(2, 2, 0, 0), Rules.points(Winner.CIVILES, roles, listOf(true, false, false, false)))
        assertEquals(listOf(0, 0, 10, 0), Rules.points(Winner.IMPOSTORES, roles, listOf(true, false, true, false)))
        assertEquals(listOf(0, 0, 10, 6), Rules.points(Winner.IMPOSTORES, roles, listOf(true, false, true, true)))
        assertEquals(listOf(0, 0, 2, 6), Rules.points(Winner.MR_WHITE, roles, listOf(true, true, true, false), 3))
        assertEquals(listOf(0, 0, 0, 6), Rules.points(Winner.MR_WHITE, roles, listOf(true, true, false, false), 3))
    }

    @Test fun adivinanzaFlexible() {
        assertTrue(Rules.sameWord("papa frita", "Papas fritas"))
        assertTrue(Rules.sameWord("  CAFE ", "Café"))
        assertTrue(Rules.sameWord("la playa", "Playa"))
        assertTrue(Rules.sameWord("flor", "Flores"))
        assertTrue(Rules.sameWord("vaca", "Vaca (recolecta)"))
        assertTrue(Rules.sameWord("Año nuevo", "Año Nuevo"))
        assertFalse(Rules.sameWord("piscina", "Playa"))
        assertFalse(Rules.sameWord("", "Playa"))
        assertFalse(Rules.sameWord("  ", "Playa"))
    }
}
