package mobappdev.example.nback_cimpl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import mobappdev.example.nback_cimpl.ui.screens.GameScreen
import mobappdev.example.nback_cimpl.ui.screens.HomeScreen
import mobappdev.example.nback_cimpl.ui.theme.NBack_CImplTheme
import mobappdev.example.nback_cimpl.ui.viewmodels.GameVM

/**
 * This is the MainActivity of the application.
 *
 * It owns the single GameVM that both screens share and switches between the Home screen and
 * the Game screen. Only two screens exist, so a Boolean is enough as "navigation"
 * (no navigation library needed).
 */
class MainActivity : ComponentActivity() {

    /** The ViewModel survives screen rotation; GameVM.Factory builds it on first use. */
    private val gameViewModel: GameVM by viewModels { GameVM.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NBack_CImplTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // rememberSaveable: the choice of screen also survives rotation.
                    var isGameScreenShown by rememberSaveable { mutableStateOf(false) }

                    // Leaving the Game screen (button or system back) must also stop the round.
                    val leaveGameScreen = {
                        gameViewModel.stopGame()
                        isGameScreenShown = false
                    }

                    // On the Game screen the system back button returns to Home instead of closing the app.
                    BackHandler(enabled = isGameScreenShown) { leaveGameScreen() }

                    if (isGameScreenShown) {
                        GameScreen(
                            vm = gameViewModel,
                            onLeaveGame = leaveGameScreen
                        )
                    } else {
                        HomeScreen(
                            vm = gameViewModel,
                            onStartGame = {
                                gameViewModel.startGame()
                                isGameScreenShown = true
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Called when the app is no longer visible (user pressed Home, switched app, screen off).
     * A running round is stopped so the timer and the speech do not continue in the background.
     * Screen rotation also calls onStop, but must not end the round, hence the check.
     */
    override fun onStop() {
        if (!isChangingConfigurations) {
            gameViewModel.stopGame()
        }
        super.onStop()
    }
}