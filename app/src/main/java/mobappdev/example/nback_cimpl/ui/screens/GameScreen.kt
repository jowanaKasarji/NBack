package mobappdev.example.nback_cimpl.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mobappdev.example.nback_cimpl.ui.viewmodels.FakeVM
import mobappdev.example.nback_cimpl.ui.viewmodels.GameType
import mobappdev.example.nback_cimpl.ui.viewmodels.GameViewModel
import kotlin.math.roundToInt

/** Number of rows and columns of the stimulus grid. */
private const val GRID_SIZE = 3

/** Soft amber used for the "incorrect press" flash. Deliberately not red, so it feels gentle. */
private val IncorrectPressFlashColor = Color(0xFFF9A825)

/**
 * The Game screen.
 *
 * Shows the live round status (event number, correct responses), the 3x3 grid with the active
 * cell highlighted, and the Match button. Spoken letters are played by the ViewModel
 * (TextToSpeech), so this screen only has to display state and report button presses.
 *
 * @param vm            the ViewModel
 * @param onLeaveGame   called when the player wants to go back to the Home screen;
 *                      MainActivity stops the round and switches screens
 */
@Composable
fun GameScreen(
    vm: GameViewModel,
    onLeaveGame: () -> Unit
) {
    val gameState by vm.gameState.collectAsState()
    // The score equals the number of correct responses (wrong presses do not change it).
    val correctResponses by vm.score.collectAsState()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Live game state ----
            Text(
                text = "Event ${gameState.currentEventNumber} / ${gameState.eventsInRound}",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Correct responses: $correctResponses",
                style = MaterialTheme.typography.titleLarge
            )
            // Reserve the line even when empty, so the layout does not jump when the round ends.
            Text(
                text = if (!gameState.isRoundRunning && gameState.currentEventNumber > 0) {
                    "Round finished!"
                } else {
                    " "
                },
                style = MaterialTheme.typography.titleMedium
            )

            // ---- Stimulus area: takes all the space that is left ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (gameState.gameType == GameType.Audio) {
                    // Audio only: nothing to look at, the letters are spoken by the ViewModel.
                    Text(
                        text = if (gameState.isRoundRunning) "Listen carefully..." else "Audio round",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // BoxWithConstraints tells us how much room there is, so the square grid
                    // fits both phones and tablets in portrait and landscape.
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val gridSideLength = minOf(maxWidth, maxHeight).coerceAtMost(420.dp)
                        StimulusGrid(
                            highlightedCellIndex = gameState.eventValue,
                            modifier = Modifier.size(gridSideLength)
                        )
                    }
                }
            }

            // ---- Controls ----
            MatchButton(
                incorrectPressCount = gameState.incorrectPressCount,
                onMatchPressed = vm::checkMatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A new round can only be started when no round is running.
                OutlinedButton(
                    onClick = vm::startGame,
                    enabled = !gameState.isRoundRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("New round")
                }
                TextButton(
                    onClick = onLeaveGame,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back to menu")
                }
            }
        }
    }
}

/**
 * The 3x3 grid. Cells are numbered 0..8 row by row (0 = top left, 8 = bottom right),
 * which matches the event values produced by the sequence generator.
 *
 * @param highlightedCellIndex index of the cell to highlight, or -1 for "no cell"
 */
@Composable
private fun StimulusGrid(
    highlightedCellIndex: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (rowIndex in 0 until GRID_SIZE) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (columnIndex in 0 until GRID_SIZE) {
                    // Convert (row, column) to the single index 0..8 used by the game logic.
                    val cellIndex = rowIndex * GRID_SIZE + columnIndex
                    GridCell(
                        isHighlighted = cellIndex == highlightedCellIndex,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/** One square of the grid. Its colour fades quickly between "off" and "highlighted". */
@Composable
private fun GridCell(
    isHighlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val cellColor by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 80),
        label = "gridCellColor"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cellColor)
    )
}

/**
 * The "Match" button with feedback for incorrect presses.
 *
 * Every time [incorrectPressCount] goes up, the button briefly flashes amber and wiggles
 * sideways. Two [Animatable] values drive this:
 *  - flashFraction: 0f = normal colour, 1f = full flash colour
 *  - shakeOffsetPixels: horizontal displacement of the button in pixels
 *
 * @param incorrectPressCount number of wrong presses so far in this round (from GameState)
 * @param onMatchPressed      called on every press; the ViewModel decides if it counts
 */
@Composable
private fun MatchButton(
    incorrectPressCount: Int,
    onMatchPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val flashFraction = remember { Animatable(0f) }
    val shakeOffsetPixels = remember { Animatable(0f) }
    val shakeDistancePixels = with(LocalDensity.current) { 12.dp.toPx() }

    // The last count we have already reacted to. It starts at the current count, so a counter
    // that was already above 0 when the screen appeared does not trigger an animation.
    var handledIncorrectPressCount by remember { mutableStateOf(incorrectPressCount) }

    // LaunchedEffect runs its block again whenever its key (the counter) changes.
    LaunchedEffect(incorrectPressCount) {
        val isNewIncorrectPress = incorrectPressCount > handledIncorrectPressCount
        handledIncorrectPressCount = incorrectPressCount // also catches the reset to 0 at round start

        if (isNewIncorrectPress) {
            // Colour flash in its own coroutine, so it runs at the same time as the shake.
            launch {
                flashFraction.animateTo(1f, animationSpec = tween(durationMillis = 120))
                flashFraction.animateTo(0f, animationSpec = tween(durationMillis = 400))
            }
            // Shake: right, left, a smaller right, a smaller left, then back to the centre.
            val shakeSteps = listOf(1f, -1f, 0.6f, -0.6f, 0f)
            for (step in shakeSteps) {
                shakeOffsetPixels.animateTo(
                    targetValue = step * shakeDistancePixels,
                    animationSpec = tween(durationMillis = 60)
                )
            }
        }
    }

    Button(
        onClick = onMatchPressed,
        // offset { } moves the button when it is drawn, without recomposing every frame.
        modifier = modifier.offset { IntOffset(shakeOffsetPixels.value.roundToInt(), 0) },
        colors = ButtonDefaults.buttonColors(
            // lerp = blend between two colours; flashFraction 0 -> normal, 1 -> amber.
            containerColor = lerp(
                MaterialTheme.colorScheme.primary,
                IncorrectPressFlashColor,
                flashFraction.value
            )
        )
    ) {
        Text(
            text = "MATCH",
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Preview
@Composable
fun GameScreenPreview() {
    Surface {
        GameScreen(vm = FakeVM(), onLeaveGame = {})
    }
}