package io.github.sebasgl1.mrwhite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MrWhiteTheme { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val game = remember { Game(ctx.applicationContext) }
    var askQuit by remember { mutableStateOf(false) }
    val s = game.screen
    val inGame = s !is Screen.Home && s !is Screen.HowTo && s !is Screen.Setup && s !is Screen.Result

    BackHandler(enabled = s !is Screen.Home) {
        when {
            inGame -> askQuit = true
            s is Screen.Result -> game.screen = Screen.Home
            else -> game.screen = Screen.Home
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        AnimatedContent(
            targetState = s,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(game)
                Screen.HowTo -> HowToScreen(game)
                Screen.Setup -> SetupScreen(game)
                is Screen.Reveal -> RevealScreen(game, screen)
                Screen.Discuss -> DiscussScreen(game)
                Screen.Vote -> VoteScreen(game)
                is Screen.Eliminated -> EliminatedScreen(game, screen.index)
                is Screen.WhiteGuess -> WhiteGuessScreen(game, screen.index)
                is Screen.GuessFailed -> GuessFailedScreen(game, screen)
                is Screen.Result -> ResultScreen(game, screen)
            }
        }
    }

    if (askQuit) {
        AlertDialog(
            onDismissRequest = { askQuit = false },
            title = { Text("¿Salir de la partida?") },
            text = { Text("Se pierde la ronda actual. Los puntajes se mantienen.") },
            confirmButton = { TextButton(onClick = { askQuit = false; game.screen = Screen.Setup }) { Text("Salir") } },
            dismissButton = { TextButton(onClick = { askQuit = false }) { Text("Seguir jugando") } },
        )
    }
}

// ---------- Piezas comunes ----------

@Composable
fun Page(
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            content = content,
        )
        if (bottom != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), content = bottom)
        }
    }
}

@Composable
fun BigButton(text: String, enabled: Boolean = true, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(58.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Ink),
    ) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Text(text, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground) }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, textAlign = align, modifier = modifier)
}

fun roleColor(r: Role) = when (r) { Role.CIVIL -> CivilColor; Role.UNDERCOVER -> UndercoverColor; Role.MR_WHITE -> WhiteColor }

// ---------- Inicio ----------

@Composable
fun HomeScreen(game: Game) {
    Page(scroll = false, bottom = {
        BigButton("Jugar") { game.screen = Screen.Setup }
        Spacer(Modifier.height(10.dp))
        GhostButton("Cómo jugar") { game.screen = Screen.HowTo }
    }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎩", fontSize = 84.sp)
            Spacer(Modifier.height(10.dp))
            Text("Mr. White", fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Spacer(Modifier.height(6.dp))
            Muted("¿Quién no tiene la palabra?", align = TextAlign.Center)
        }
    }
}

@Composable
fun HowToScreen(game: Game) {
    Page(bottom = { BigButton("Entendido") { game.screen = Screen.Home } }) {
        Text("Cómo jugar", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        val rules = listOf(
            "👥 Civiles" to "Todos reciben la misma palabra secreta.",
            "🕵️ Undercover" to "Recibe una palabra parecida, pero no sabe que es diferente. Si sospecha, le toca disimular.",
            "🎩 Mr. White" to "No recibe palabra. Escucha las pistas e improvisa sin que lo descubran.",
            "1. Repartir" to "Pasen el celular: cada quien ve su palabra en secreto.",
            "2. Pistas" to "En el orden que muestra la app, cada uno dice UNA palabra relacionada con la suya. Ni muy obvia ni muy rara.",
            "3. Votar" to "Discutan y voten a quién eliminar. La app revela qué era.",
            "4. Última chance" to "Si eliminan a Mr. White, puede ganar adivinando la palabra de los civiles.",
            "🏆 Ganan" to "Los civiles, si eliminan a todos los impostores. Los impostores, si llegan a quedar con solo un civil vivo.",
            "⭐ Puntos" to "Civiles que ganan: +2. Undercover vivo al ganar: +10. Mr. White vivo al ganar o si adivina: +6.",
        )
        rules.forEach { (t, d) ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(t, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(4.dp))
                    Muted(d)
                }
            }
        }
    }
}

// ---------- Configuración ----------

@Composable
fun SetupScreen(game: Game) {
    var input by remember { mutableStateOf("") }
    val error = game.setupError()
    val add = { if (game.addName(input)) input = "" }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nueva partida", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { game.screen = Screen.Home }) { Text("Inicio") }
                }
                SectionTitle("Jugadores · ${game.names.size}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(20) },
                        placeholder = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = add,
                        enabled = input.isNotBlank(),
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(contentColor = Ink),
                    ) { Text("Agregar") }
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(game.names, key = { _, n -> n }) { i, n ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(26.dp))
                    Text(n, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { game.moveName(i, i - 1) }, enabled = i > 0) { Text("▲") }
                    TextButton(onClick = { game.removeName(n) }) { Text("✕", color = UndercoverColor) }
                }
            }
            item {
                SectionTitle("Roles")
                Stepper("🕵️ Undercover", game.undercover,
                    canDec = game.undercover > 0 && game.impostors > 1,
                    canInc = game.impostors < game.maxImpostors(),
                ) { game.setCounts(game.undercover + it, game.white) }
                Spacer(Modifier.height(8.dp))
                Stepper("🎩 Mr. White", game.white,
                    canDec = game.white > 0 && game.impostors > 1,
                    canInc = game.impostors < game.maxImpostors(),
                ) { game.setCounts(game.undercover, game.white + it) }
                Spacer(Modifier.height(8.dp))
                Muted("👥 Civiles: ${maxOf(0, game.civilians)}", Modifier.padding(start = 4.dp))

                SectionTitle("Palabras")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MODOS.forEach { m ->
                        FilterChip(
                            selected = game.modo == m,
                            onClick = { game.modo = m; game.saveConfig() },
                            label = { Text(if (m == "Colombiano") "🇨🇴 Colombiano" else m) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Incluir groserías", fontSize = 16.sp)
                        Muted("Jerga subida de tono", Modifier.padding(top = 2.dp))
                    }
                    Switch(checked = game.picante, onCheckedChange = { game.picante = it; game.saveConfig() })
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp)) {
            if (error != null) {
                Muted(error, Modifier.fillMaxWidth().padding(bottom = 8.dp), align = TextAlign.Center)
            }
            BigButton("Repartir palabras", enabled = error == null) { game.start() }
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, canDec: Boolean, canInc: Boolean, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        RoundBtn("−", canDec) { onChange(-1) }
        Text("$value", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
        RoundBtn("+", canInc) { onChange(1) }
    }
}

@Composable
fun RoundBtn(t: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp)
            .background(if (enabled) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(t, fontSize = 22.sp, color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline)
    }
}

// ---------- Repartir ----------

@Composable
fun RevealScreen(game: Game, s: Screen.Reveal) {
    val p = game.players[s.index]
    Page(scroll = false, bottom = {
        if (!s.shown) BigButton("Ver mi palabra") { game.screen = s.copy(shown = true) }
        else BigButton("Listo, ocultar y pasar") { game.nextReveal(s.index) }
    }) {
        Muted("${s.index + 1} de ${game.players.size}")
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (!s.shown) {
                Text("📱", fontSize = 64.sp)
                Spacer(Modifier.height(16.dp))
                Muted("Pásale el celular a", align = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(p.name, fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Muted("Que nadie más mire la pantalla 👀", align = TextAlign.Center)
            } else if (p.role == Role.MR_WHITE) {
                Text("🎩", fontSize = 72.sp)
                Spacer(Modifier.height(12.dp))
                Text("Eres Mr. White", fontSize = 36.sp, fontWeight = FontWeight.Black, color = WhiteColor, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Muted("No tienes palabra. Escucha las pistas de los demás y disimula.", align = TextAlign.Center)
            } else {
                Muted("${p.name}, tu palabra es", align = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                        .padding(vertical = 36.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.word ?: "", fontSize = 40.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = Gold)
                }
                Spacer(Modifier.height(14.dp))
                Muted("Memorízala y no se la muestres a nadie", align = TextAlign.Center)
            }
        }
    }
}

// ---------- Pistas ----------

@Composable
fun DiscussScreen(game: Game) {
    Page(bottom = { BigButton("Ir a votar") { game.screen = Screen.Vote } }) {
        Text("Ronda ${game.round}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Muted("Cada jugador dice una pista de su palabra, en este orden:")
        Spacer(Modifier.height(14.dp))
        game.order.forEachIndexed { pos, idx ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).background(if (pos == 0) Gold else MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${pos + 1}", fontWeight = FontWeight.Bold, color = if (pos == 0) Ink else MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.width(14.dp))
                Text(game.players[idx].name, fontSize = 18.sp)
            }
        }
        val out = game.players.filter { !it.alive }
        if (out.isNotEmpty()) {
            SectionTitle("Eliminados")
            out.forEach { Muted("${it.name} · ${it.role.label}", Modifier.padding(vertical = 2.dp)) }
        }
        SectionTitle("Temporizador de discusión")
        DiscussionTimer()
    }
}

@Composable
fun DiscussionTimer() {
    var left by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && left > 0) { delay(1000); left-- }
        running = false
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "%d:%02d".format(left / 60, left % 60), fontSize = 28.sp, fontWeight = FontWeight.Bold,
            color = if (running && left <= 10) UndercoverColor else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(86.dp),
        )
        listOf(60, 120, 180).forEach { secs ->
            FilterChip(selected = false, onClick = { left = secs; running = true }, label = { Text("${secs / 60} min") })
        }
        if (running) TextButton(onClick = { running = false; left = 0 }) { Text("Parar") }
    }
}

// ---------- Votación ----------

@Composable
fun VoteScreen(game: Game) {
    var pick by remember { mutableStateOf(-1) }
    Page(bottom = { GhostButton("Volver a las pistas") { game.screen = Screen.Discuss } }) {
        Text("¿A quién eliminan?", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Muted("Discutan, voten y toquen al más votado.")
        Spacer(Modifier.height(14.dp))
        game.players.forEachIndexed { i, p ->
            if (p.alive) {
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { pick = i },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(p.name, fontSize = 19.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp))
                }
            }
        }
    }
    if (pick >= 0) {
        AlertDialog(
            onDismissRequest = { pick = -1 },
            title = { Text("¿Eliminar a ${game.players[pick].name}?") },
            confirmButton = { TextButton(onClick = { val i = pick; pick = -1; game.eliminate(i) }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { pick = -1 }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun EliminatedScreen(game: Game, index: Int) {
    val p = game.players[index]
    Page(scroll = false, bottom = { BigButton("Continuar") { game.continueAfterElimination() } }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (p.role == Role.CIVIL) "😬" else "🎯", fontSize = 72.sp)
            Spacer(Modifier.height(14.dp))
            Text(p.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Muted("era", align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(p.role.label, fontSize = 44.sp, fontWeight = FontWeight.Black, color = roleColor(p.role), textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Muted(
                if (p.role == Role.CIVIL) "Eliminaron a un inocente." else "¡Bien! Cayó un impostor.",
                align = TextAlign.Center,
            )
        }
    }
}

@Composable
fun WhiteGuessScreen(game: Game, index: Int) {
    val p = game.players[index]
    var guess by remember { mutableStateOf("") }
    Page(bottom = { BigButton("Adivinar", enabled = guess.isNotBlank(), color = WhiteColor) { game.whiteGuess(index, guess) } }) {
        Spacer(Modifier.height(30.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎩", fontSize = 72.sp)
            Spacer(Modifier.height(10.dp))
            Text("¡${p.name} era Mr. White!", fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Muted("Tiene una última oportunidad: si adivina la palabra de los civiles, gana.", align = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                value = guess,
                onValueChange = { guess = it.take(40) },
                placeholder = { Text("La palabra es…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (guess.isNotBlank()) game.whiteGuess(index, guess) }),
            )
            Spacer(Modifier.height(8.dp))
            Muted("No importan tildes ni mayúsculas.", align = TextAlign.Center)
        }
    }
}

@Composable
fun GuessFailedScreen(game: Game, s: Screen.GuessFailed) {
    Page(scroll = false, bottom = { BigButton("Continuar") { game.continueAfterElimination() } }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("❌", fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text("No era \"${s.guess}\"", fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Muted("${game.players[s.index].name} queda eliminado. El juego sigue.", align = TextAlign.Center)
        }
    }
}

// ---------- Resultado ----------

@Composable
fun ResultScreen(game: Game, s: Screen.Result) {
    val (emoji, title, color) = when (s.winner) {
        Winner.CIVILES -> Triple("🎉", "¡Ganan los civiles!", CivilColor)
        Winner.IMPOSTORES -> Triple("🕵️", "¡Ganan los impostores!", UndercoverColor)
        Winner.MR_WHITE -> Triple("🎩", "¡Mr. White adivinó!", WhiteColor)
    }
    Page(bottom = {
        BigButton("Otra ronda") { game.start() }
        Spacer(Modifier.height(10.dp))
        GhostButton("Cambiar jugadores") { game.screen = Screen.Setup }
    }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 64.sp)
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = color, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WordBox("Civiles", game.civilWord, CivilColor, Modifier.weight(1f))
            WordBox("Undercover", game.undercoverWord, UndercoverColor, Modifier.weight(1f))
        }
        Muted("Categoría: ${game.category}", Modifier.fillMaxWidth().padding(top = 8.dp), align = TextAlign.Center)

        SectionTitle("Esta ronda")
        game.players.forEach { p ->
            val pts = s.gained[p.name] ?: 0
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(roleColor(p.role), CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(p.name + if (!p.alive) " ✝" else "", fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(p.role.label, color = roleColor(p.role), fontSize = 15.sp)
                Text(if (pts > 0) "  +$pts" else "", fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Puntaje acumulado")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { game.resetScores() }, modifier = Modifier.padding(top = 10.dp)) { Text("Reiniciar") }
        }
        game.scores.entries.filter { it.key in game.names }.sortedByDescending { it.value }.forEachIndexed { i, (n, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(if (i == 0 && v > 0) "👑 $n" else n, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text("$v", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WordBox(label: String, word: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 13.sp, color = color)
        Spacer(Modifier.height(4.dp))
        Text(word, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
