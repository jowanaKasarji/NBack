package mobappdev.example.nback_cimpl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mobappdev.example.nback_cimpl.data.GameSettings
import mobappdev.example.nback_cimpl.data.HighScoreEntity
import mobappdev.example.nback_cimpl.ui.viewmodels.FakeVM
import mobappdev.example.nback_cimpl.ui.viewmodels.GameType
import mobappdev.example.nback_cimpl.ui.viewmodels.GameViewModel

/**
 * The Home screen.
 *
 * It shows the top scores, lets the player change the four settings (stimulus type, n,
 * event duration, events per round) and starts a round.
 *
 * The screen holds no state of its own: it displays what the ViewModel exposes and reports
 * every user action back to the ViewModel (state flows down, events flow up).
 *
 * @param vm            the ViewModel (an interface, so a fake one can be used in previews)
 * @param onStartGame   called when the player presses "Start game"; MainActivity then starts
 *                      the round and switches to the Game screen
 */
@Composable
fun HomeScreen(
    vm: GameViewModel,
    onStartGame: () -> Unit
) {
    // collectAsState turns a StateFlow into Compose state: the screen redraws when it changes.
    val topScores by vm.topScores.collectAsState()
    val gameState by vm.gameState.collectAsState()
    val settings by vm.settings.collectAsState()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                // Scrollable so the screen still works on small phones and in landscape.
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "N-Back",
                style = MaterialTheme.typography.displaySmall
            )

            TopScoresLeaderboard(topScores = topScores)

            // ---- Stimulus type: Visual / Audio / Both ----
            Text(
                text = "Stimulus type",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // One button per enum constant; the selected one is drawn filled.
                for (gameType in GameType.values()) {
                    StimulusTypeButton(
                        label = describeGameType(gameType),
                        isSelected = gameState.gameType == gameType,
                        onClick = { vm.setGameType(gameType) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ---- Numeric settings: each row shows the current value with - and + buttons ----
            SettingStepperRow(
                label = "n (steps back)",
                valueText = settings.nBackValue.toString(),
                canDecrease = settings.nBackValue > GameSettings.MIN_N_BACK_VALUE,
                canIncrease = settings.nBackValue < GameSettings.MAX_N_BACK_VALUE,
                onDecrease = { vm.setNBackValue(settings.nBackValue - 1) },
                onIncrease = { vm.setNBackValue(settings.nBackValue + 1) }
            )
            SettingStepperRow(
                label = "Event duration",
                valueText = "${settings.eventDurationMillis / 1000.0} s",
                canDecrease = settings.eventDurationMillis > GameSettings.MIN_EVENT_DURATION_MILLIS,
                canIncrease = settings.eventDurationMillis < GameSettings.MAX_EVENT_DURATION_MILLIS,
                onDecrease = {
                    vm.setEventDurationMillis(
                        settings.eventDurationMillis - GameSettings.EVENT_DURATION_STEP_MILLIS
                    )
                },
                onIncrease = {
                    vm.setEventDurationMillis(
                        settings.eventDurationMillis + GameSettings.EVENT_DURATION_STEP_MILLIS
                    )
                }
            )
            SettingStepperRow(
                label = "Events per round",
                valueText = settings.eventsPerRound.toString(),
                canDecrease = settings.eventsPerRound > GameSettings.MIN_EVENTS_PER_ROUND,
                canIncrease = settings.eventsPerRound < GameSettings.MAX_EVENTS_PER_ROUND,
                onDecrease = {
                    vm.setEventsPerRound(settings.eventsPerRound - GameSettings.EVENTS_PER_ROUND_STEP)
                },
                onIncrease = {
                    vm.setEventsPerRound(settings.eventsPerRound + GameSettings.EVENTS_PER_ROUND_STEP)
                }
            )

            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(
                    text = "START GAME",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

/**
 * A small leaderboard card: one row per saved score, best first.
 * Each row shows the rank, the score, the stimulus type and the n of that round.
 * Shows a hint instead of the list while no round has been finished yet.
 */
@Composable
private fun TopScoresLeaderboard(
    topScores: List<HighScoreEntity>,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().widthIn(max = 480.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Top 5 Scores",
                style = MaterialTheme.typography.titleLarge
            )
            if (topScores.isEmpty()) {
                Text(
                    text = "No scores yet. Play a round to get on the board!",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                topScores.forEachIndexed { index, highScore ->
                    LeaderboardRow(rank = index + 1, highScore = highScore)
                }
            }
        }
    }
}

/** One leaderboard line, e.g. "1.  12 pts      Visual · n = 2". */
@Composable
private fun LeaderboardRow(rank: Int, highScore: HighScoreEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$rank.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.widthIn(min = 32.dp)
        )
        Text(
            text = "${highScore.scoreValue} pts",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${describeStoredGameType(highScore.gameTypeName)} · n = ${highScore.nBackValue}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** The entity stores the GameType name as text; this maps it to the label used on the buttons. */
private fun describeStoredGameType(gameTypeName: String): String =
    if (gameTypeName == GameType.AudioVisual.name) "Both" else gameTypeName

/** Text shown on the stimulus-type button of [gameType]. */
private fun describeGameType(gameType: GameType): String = when (gameType) {
    GameType.Visual -> "Visual"
    GameType.Audio -> "Audio"
    GameType.AudioVisual -> "Both"
}

/**
 * One choice of the stimulus-type selector. The selected choice is a filled button,
 * the others are outlined, so the current selection is visible at a glance.
 */
@Composable
private fun StimulusTypeButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

/**
 * A settings row: label on the left, then "-", the current value and "+".
 * A button is disabled when the value is already at its limit.
 */
@Composable
private fun SettingStepperRow(
    label: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onDecrease, enabled = canDecrease) { Text("-") }
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp)
        )
        OutlinedButton(onClick = onIncrease, enabled = canIncrease) { Text("+") }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    // The real ViewModel needs the Application context, so the preview uses the fake one.
    Surface {
        HomeScreen(vm = FakeVM(), onStartGame = {})
    }
}