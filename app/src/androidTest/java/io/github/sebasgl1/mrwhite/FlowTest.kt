package io.github.sebasgl1.mrwhite

import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pruebas de pantalla: juegan partidas completas tocando la app como lo haría una persona. */
@RunWith(AndroidJUnit4::class)
class FlowTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val names = listOf("Ana", "Beto", "Caro", "Dani", "Eli")

    @Before fun clean() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("mrwhite", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun SemanticsNodeInteractionsProvider.has(text: String) =
        onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun launchAndAddPlayers(count: Int): ActivityScenario<MainActivity> {
        val sc = ActivityScenario.launch(MainActivity::class.java)
        rule.onNodeWithText("Jugar").performClick()
        names.take(count).forEach { n ->
            rule.onNode(hasSetTextAction()).performTextInput(n)
            rule.onNodeWithText("Agregar").performClick()
        }
        return sc
    }

    /** Cada jugador mantiene presionado para ver su palabra y la pasa. */
    private fun revealAll(count: Int) {
        repeat(count) { i ->
            rule.onNodeWithTag("reveal-box").performTouchInput { down(center) }
            rule.waitForIdle()
            assertTrue("jugador $i no vio nada", rule.has("Tu palabra es") || rule.has("Eres Mr. White"))
            rule.onNodeWithTag("reveal-box").performTouchInput { up() }
            rule.waitForIdle()
            assertTrue("la palabra quedó a la vista", !rule.has("Tu palabra es") && !rule.has("Eres Mr. White"))
            val next = if (i + 1 < count) "Listo, pasar al siguiente" else "Listo, empezar"
            rule.onNodeWithText(next).performClick()
        }
    }

    @Test fun partidaCompletaHastaElResultado() {
        launchAndAddPlayers(5).use {
            rule.onNodeWithTag("setup-list").performScrollToNode(hasText("1 Undercover · 1 Mr. White", substring = true))
            rule.onNodeWithText("1 Undercover · 1 Mr. White", substring = true).assertExists()
            rule.onNodeWithText("Repartir palabras").performClick()
            revealAll(5)
            var done = false
            repeat(25) {
                if (done) return@repeat
                rule.waitForIdle()
                when {
                    rule.has("Otra ronda") -> done = true
                    rule.has("Ir a votar") -> rule.onNodeWithText("Ir a votar").performClick()
                    rule.has("¿A quién eliminan?") -> {
                        val target = names.first { n -> rule.onAllNodes(hasText(n)).fetchSemanticsNodes().isNotEmpty() }
                        rule.onNodeWithText(target).performClick()
                        rule.onNodeWithText("Eliminar").performClick()
                    }
                    rule.has("No era, continuar") -> rule.onNodeWithText("No era, continuar").performClick()
                    rule.has("Adivinar") -> {
                        rule.onNode(hasSetTextAction()).performTextInput("zzzz")
                        rule.onNodeWithText("Adivinar").performClick()
                    }
                    rule.has("Continuar") -> rule.onNodeWithText("Continuar").performClick()
                }
            }
            assertTrue("la partida no terminó", done)
            rule.onNodeWithText("Puntaje acumulado", substring = true, ignoreCase = true).assertExists()
            // Otra ronda vuelve a repartir
            rule.onNodeWithText("Otra ronda").performClick()
            rule.onNodeWithTag("reveal-box").assertExists()
        }
    }

    @Test fun atrasPideConfirmacionDuranteLaPartida() {
        launchAndAddPlayers(4).use { sc ->
            rule.onNodeWithText("Repartir palabras").performClick()
            revealAll(4)
            rule.onNodeWithText("Ir a votar").assertExists()
            sc.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            rule.onNodeWithText("¿Salir de la partida?").assertExists()
            rule.onNodeWithText("Seguir jugando").performClick()
            rule.onNodeWithText("Ir a votar").assertExists()
        }
    }

    @Test fun comoJugarYLetraGrande() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.onNodeWithText("Cómo jugar").performClick()
            rule.onNodeWithText("Entendido").performClick()
            rule.onNodeWithText("Jugar").performClick()
            rule.onNodeWithTag("setup-list").performScrollToNode(hasTestTag("big-text"))
            rule.onNodeWithTag("big-text").performClick()
            rule.onNodeWithTag("big-text").assertIsOn()
            // Con letra grande se puede seguir jugando
            rule.onNodeWithText("Repartir palabras").assertExists()
        }
    }
}
