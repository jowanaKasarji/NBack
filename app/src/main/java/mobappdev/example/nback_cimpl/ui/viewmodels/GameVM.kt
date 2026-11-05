package mobappdev.example.nback_cimpl.ui.viewmodels

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobappdev.example.nback_cimpl.GameApplication
import mobappdev.example.nback_cimpl.NBackHelper
import mobappdev.example.nback_cimpl.data.GameSettings
import mobappdev.example.nback_cimpl.data.HighScoreDao
import mobappdev.example.nback_cimpl.data.HighScoreEntity
import mobappdev.example.nback_cimpl.data.SettingsRepository
import java.util.Locale

/**
 * This is the GameViewModel.
 *
 * It is good practice to first make an interface, which acts as the blueprint
 * for your implementation. With this interface we can create fake versions
 * of the viewmodel, which we can use to test other parts of our app that depend on the VM.
 *
 * Our viewmodel itself has functions to start a game, to specify a gametype,
 * to stop a game, and to check if we are having a match.
 *
 * Date: 25-08-2023
 * Version: Version 1.0
 * Author: Yeetivity
 */

interface GameViewModel {
    val gameState: StateFlow<GameState>

    /** Score of the current round = number of correct match presses. Wrong presses do not change it. */
    val score: StateFlow<Int>

    /** The best scores ever saved, best first (at most [GameVM.TOP_SCORES_COUNT] entries). */
    val topScores: StateFlow<List<HighScoreEntity>>

    val nBack: Int

    /** The saved settings (stimulus type, n, event duration, events per round) for the Home screen. */
    val settings: StateFlow<GameSettings>

    fun setGameType(gameType: GameType)
    fun setNBackValue(nBackValue: Int)
    fun setEventDurationMillis(eventDurationMillis: Long)
    fun setEventsPerRound(eventsPerRound: Int)
    fun startGame()

    /** Ends a running round without saving a score. Call this when the player leaves the Game screen. */
    fun stopGame()

    fun checkMatch()
}

/**
 * ViewModel of the game: owns the round timer (a coroutine), the game state, the scoring and
 * the text-to-speech engine.
 *
 * @param settingsRepository source of the user's saved settings (DataStore).
 * @param highScoreDao access to the stored scores (Room).
 * @param applicationContext needed by TextToSpeech. Pass the Application (not an Activity) so
 *                           the ViewModel can never leak a screen.
 */
class GameVM(
    private val settingsRepository: SettingsRepository,
    private val highScoreDao: HighScoreDao,
    applicationContext: Context
) : GameViewModel, ViewModel() {

    // ------------------------------------------------------------------------------------
    // State that the UI observes
    // ------------------------------------------------------------------------------------

    private val _gameState = MutableStateFlow(GameState())
    override val gameState: StateFlow<GameState>
        get() = _gameState.asStateFlow()

    /** Score of the current round: +1 per correct press, wrong presses change nothing. */
    private val _score = MutableStateFlow(0)
    override val score: StateFlow<Int>
        get() = _score

    /** The best saved scores, best first, limited to TOP_SCORES_COUNT entries (read from Room). */
    private val _topScores = MutableStateFlow<List<HighScoreEntity>>(emptyList())
    override val topScores: StateFlow<List<HighScoreEntity>>
        get() = _topScores.asStateFlow()

    /** Latest saved settings; starts with defaults until DataStore has delivered its first value. */
    private val _currentSettings = MutableStateFlow(GameSettings())

    override val nBack: Int
        get() = _currentSettings.value.nBackValue

    override val settings: StateFlow<GameSettings>
        get() = _currentSettings.asStateFlow()

    // ------------------------------------------------------------------------------------
    // Internal state of the round that is currently being played
    // (only touched from the main thread, see the note above checkMatch)
    // ------------------------------------------------------------------------------------

    /** The coroutine that runs the round. Cancelling it stops the round timer. */
    private var gameLoopJob: Job? = null

    private val nBackHelper = NBackHelper()

    /** All events of the current round, generated once when the round starts. */
    private var currentRoundEvents = IntArray(0)

    /** The n of the current round. Copied at round start so changing settings mid-round has no effect. */
    private var currentRoundNBack = 1

    /** 0-based index of the event being shown right now; -1 before the first event appears. */
    private var currentEventIndex = -1

    /** True once the player has pressed "match" during the current event (extra presses are ignored). */
    private var hasMatchPressForCurrentEvent = false

    /** Text-to-speech engine for the auditory stimuli; null after it was shut down. */
    private var textToSpeech: TextToSpeech? = null

    /** True once the engine reported that it is initialised and a language is set. */
    private var isTextToSpeechReady = false

    // ------------------------------------------------------------------------------------
    // Public functions called by the UI
    // ------------------------------------------------------------------------------------

    override fun setGameType(gameType: GameType) {
        // Update the state right away so the UI reacts immediately...
        _gameState.value = _gameState.value.copy(gameType = gameType)
        // ...and persist it, so the choice survives an app restart.
        viewModelScope.launch { settingsRepository.saveGameType(gameType) }
    }

    // The three setters below only write to DataStore. The settings collector in init then
    // delivers the saved value back into _currentSettings, which is what the UI observes.
    // coerceIn keeps every value inside the range the Home screen offers.

    override fun setNBackValue(nBackValue: Int) {
        val allowedNBackValue =
            nBackValue.coerceIn(GameSettings.MIN_N_BACK_VALUE, GameSettings.MAX_N_BACK_VALUE)
        viewModelScope.launch { settingsRepository.saveNBackValue(allowedNBackValue) }
    }

    override fun setEventDurationMillis(eventDurationMillis: Long) {
        val allowedDurationMillis = eventDurationMillis.coerceIn(
            GameSettings.MIN_EVENT_DURATION_MILLIS,
            GameSettings.MAX_EVENT_DURATION_MILLIS
        )
        viewModelScope.launch { settingsRepository.saveEventDurationMillis(allowedDurationMillis) }
    }

    override fun setEventsPerRound(eventsPerRound: Int) {
        val allowedEventsPerRound = eventsPerRound.coerceIn(
            GameSettings.MIN_EVENTS_PER_ROUND,
            GameSettings.MAX_EVENTS_PER_ROUND
        )
        viewModelScope.launch { settingsRepository.saveEventsPerRound(allowedEventsPerRound) }
    }

    override fun startGame() {
        // 1. Stop anything that is still running from a previous round.
        gameLoopJob?.cancel()
        textToSpeech?.stop()

        // 2. Take a snapshot of the settings. The whole round uses these values, even if the
        //    saved settings change while the round is running.
        val roundSettings = _currentSettings.value
        val roundNBack = roundSettings.nBackValue.coerceAtLeast(1)
        // The generator needs more events than n, so a too-small setting is raised to n + 1.
        val eventsInRound = roundSettings.eventsPerRound.coerceAtLeast(roundNBack + 1)
        val roundGameType = _gameState.value.gameType
        val eventDurationMillis = roundSettings.eventDurationMillis

        // 3. Generate the whole sequence up front (pure Kotlin, see NBackHelper).
        val roundEvents = nBackHelper.generateNBackString(
            size = eventsInRound,
            combinations = NUMBER_OF_DISTINCT_STIMULI,
            percentMatch = TARGET_MATCH_PERCENT,
            nBack = roundNBack
        )
        Log.d("GameVM", "The following sequence was generated: ${roundEvents.contentToString()}")

        // 4. Reset all per-round bookkeeping.
        currentRoundEvents = roundEvents
        currentRoundNBack = roundNBack
        currentEventIndex = -1               // no event shown yet -> match presses are ignored
        hasMatchPressForCurrentEvent = false
        _score.value = 0
        _gameState.value = _gameState.value.copy(
            eventValue = NO_EVENT_VALUE,
            currentEventNumber = 0,
            eventsInRound = eventsInRound,
            isRoundRunning = true,           // from now on match presses are considered
            incorrectPressCount = 0
        )

        // 5. Start the round timer. viewModelScope is cancelled automatically when the ViewModel
        //    is cleared, and we keep the Job so we can also cancel it ourselves (stopGame, new round).
        gameLoopJob = viewModelScope.launch {
            when (roundGameType) {
                GameType.Visual -> runVisualGame(roundEvents, eventDurationMillis)
                GameType.Audio -> runAudioGame(roundEvents, eventDurationMillis)
                GameType.AudioVisual -> runAudioVisualGame(roundEvents, eventDurationMillis)
            }

            // We only get here when ALL events were played. If the coroutine is cancelled
            // (player left, or a new round started), delay() throws a CancellationException,
            // the code below is skipped and no score is saved for the unfinished round.
            finishRound(roundGameType, roundNBack, eventsInRound)
        }
    }

    override fun stopGame() {
        // Cancelling the job makes the pending delay() throw and ends the loop cleanly.
        gameLoopJob?.cancel()
        gameLoopJob = null
        textToSpeech?.stop() // silence a letter that is still being spoken

        // Set the state here (not in a 'finally' inside the coroutine): a cancelled coroutine
        // finishes later, and could otherwise overwrite the state of a round started in between.
        _gameState.value = _gameState.value.copy(
            isRoundRunning = false,
            eventValue = NO_EVENT_VALUE
        )
    }

    /**
     * Called when the player presses the "match" button.
     *
     * Threading note: this function and the game loop coroutine both run on the main thread
     * (viewModelScope uses Dispatchers.Main), so they never run at the same time and the
     * round bookkeeping variables need no locking.
     */
    override fun checkMatch() {
        // Rule 1: ignore presses before the round started and after it ended.
        if (!_gameState.value.isRoundRunning) return

        // Rule 2: ignore presses during the first n events (indexes 0 until n),
        // and before the first event appeared (index -1).
        if (currentEventIndex < currentRoundNBack) return

        // Rule 3: only the first press per event counts. Set the flag BEFORE judging the press,
        // so a second press is ignored whether the first one was right or wrong.
        if (hasMatchPressForCurrentEvent) return
        hasMatchPressForCurrentEvent = true

        // Judge the press with the same definition of "match" that the generator and tests use.
        val pressWasCorrect =
            nBackHelper.isMatchAt(currentRoundEvents, currentEventIndex, currentRoundNBack)

        // The scoring rule lives in NBackHelper (correct: +1, incorrect: unchanged).
        _score.value = nBackHelper.calculateNewScore(_score.value, pressWasCorrect)

        if (!pressWasCorrect) {
            // The UI watches this counter and plays its (non-offensive) animation when it changes.
            _gameState.value = _gameState.value.copy(
                incorrectPressCount = _gameState.value.incorrectPressCount + 1
            )
        }
    }

    // ------------------------------------------------------------------------------------
    // The three game modes. They only differ in WHICH stimuli are presented;
    // the timing loop is shared in playAllEvents.
    // ------------------------------------------------------------------------------------

    /** Visual mode: the grid cell is highlighted, nothing is spoken. */
    private suspend fun runVisualGame(events: IntArray, eventDurationMillis: Long) {
        playAllEvents(events, eventDurationMillis, showVisualStimulus = true, speakAudioStimulus = false)
    }

    /** Audio mode: a letter is spoken, no cell is highlighted. */
    private suspend fun runAudioGame(events: IntArray, eventDurationMillis: Long) {
        playAllEvents(events, eventDurationMillis, showVisualStimulus = false, speakAudioStimulus = true)
    }

    /**
     * Audio + visual mode: the cell is highlighted AND its letter is spoken.
     * Both stimuli come from the same sequence, so one match button covers both of them.
     */
    private suspend fun runAudioVisualGame(events: IntArray, eventDurationMillis: Long) {
        playAllEvents(events, eventDurationMillis, showVisualStimulus = true, speakAudioStimulus = true)
    }

    /**
     * The round timer: presents the events one by one, each lasting [eventDurationMillis].
     *
     * This is a suspend function: delay() does not block the thread, it suspends the coroutine
     * and resumes it later. If the coroutine's Job is cancelled while it waits in delay(),
     * delay() throws a CancellationException, which ends this loop immediately.
     */
    private suspend fun playAllEvents(
        events: IntArray,
        eventDurationMillis: Long,
        showVisualStimulus: Boolean,
        speakAudioStimulus: Boolean
    ) {
        // Each event = a "visible" part followed by a short blank pause. The pause makes two equal
        // events in a row (possible when n = 1) look like two separate flashes.
        val blankPauseMillis = eventDurationMillis / BLANK_PAUSE_DIVISOR
        val visibleMillis = eventDurationMillis - blankPauseMillis

        for ((eventIndex, eventValue) in events.withIndex()) {
            // A new event begins: remember which one it is and re-enable match presses for it.
            currentEventIndex = eventIndex
            hasMatchPressForCurrentEvent = false

            // Publish the event. eventValue is only passed on in modes that show the grid.
            _gameState.value = _gameState.value.copy(
                eventValue = if (showVisualStimulus) eventValue else NO_EVENT_VALUE,
                currentEventNumber = eventIndex + 1 // shown to the player as 1-based
            )
            if (speakAudioStimulus) speakLetterForEvent(eventValue)

            delay(visibleMillis)

            // End of the visible part: remove the highlight, then wait out the blank pause.
            _gameState.value = _gameState.value.copy(eventValue = NO_EVENT_VALUE)
            delay(blankPauseMillis)
        }
    }

    /**
     * Called after the last event of a round: ends the round and stores its score in Room.
     */
    private suspend fun finishRound(
        roundGameType: GameType,
        roundNBack: Int,
        eventsInRound: Int
    ) {
        // Mark the round as over FIRST, so match presses are ignored from now on.
        _gameState.value = _gameState.value.copy(
            isRoundRunning = false,
            eventValue = NO_EVENT_VALUE
        )

        // NonCancellable: the round was completed, so its score must be saved even if the player
        // leaves the screen (cancelling this coroutine) at the very moment the database write runs.
        withContext(NonCancellable) {
            highScoreDao.insertHighScore(
                HighScoreEntity(
                    scoreValue = _score.value,
                    gameTypeName = roundGameType.name,
                    nBackValue = roundNBack,
                    eventsPerRound = eventsInRound,
                    achievedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
        // The Home screen's leaderboard updates by itself: observeAllScoresSortedBestFirst() is a Flow.
    }

    // ------------------------------------------------------------------------------------
    // Text to speech
    // ------------------------------------------------------------------------------------

    /**
     * Creates the TextToSpeech engine. Engine start-up is asynchronous: the lambda below is
     * called later, on the main thread, with the result.
     */
    private fun initializeTextToSpeech(context: Context) {
        textToSpeech = TextToSpeech(context) { initializationStatus ->
            if (initializationStatus == TextToSpeech.SUCCESS) {
                val languageResult = textToSpeech?.setLanguage(Locale.US)
                isTextToSpeechReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                if (!isTextToSpeechReady) Log.e("GameVM", "TextToSpeech language not available")
            } else {
                Log.e("GameVM", "TextToSpeech initialisation failed (status $initializationStatus)")
            }
        }
    }

    /** Speaks the letter that belongs to [eventValue]. Does nothing if the engine is not ready. */
    private fun speakLetterForEvent(eventValue: Int) {
        if (!isTextToSpeechReady) return
        // QUEUE_FLUSH drops anything still queued, so a slow engine never falls behind the game.
        textToSpeech?.speak(
            SPOKEN_LETTERS[eventValue],
            TextToSpeech.QUEUE_FLUSH,
            null,
            "event_$currentEventIndex"
        )
    }

    // ------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------

    /** Called by Android when the ViewModel is destroyed for good: release everything we own. */
    override fun onCleared() {
        gameLoopJob?.cancel()          // viewModelScope would cancel it too; explicit is clearer
        textToSpeech?.stop()
        textToSpeech?.shutdown()       // frees the speech engine's resources
        textToSpeech = null
        isTextToSpeechReady = false
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as GameApplication)
                GameVM(application.settingsRepository, application.highScoreDao, application)
            }
        }

        /** Value of GameState.eventValue when no cell should be highlighted. */
        const val NO_EVENT_VALUE = -1

        /** Number of different stimuli = number of cells in the 3x3 grid. */
        const val NUMBER_OF_DISTINCT_STIMULI = 9

        /** Share of matches in a sequence (SPEC.md requires 20-40 %). */
        const val TARGET_MATCH_PERCENT = 30

        /** How many scores the Home screen leaderboard shows. */
        const val TOP_SCORES_COUNT = 5

        /** The last 1/5 of every event is a blank pause (see playAllEvents). */
        private const val BLANK_PAUSE_DIVISOR = 5

        /**
         * Letters for the auditory stimuli: event value 0 is spoken as "C", 1 as "H", and so on.
         * The letters were chosen to sound clearly different from each other.
         * The list must contain exactly NUMBER_OF_DISTINCT_STIMULI entries.
         */
        private val SPOKEN_LETTERS = listOf("C", "H", "K", "L", "Q", "R", "S", "T", "W")
    }

    init {
        // Code that runs during creation of the vm

        initializeTextToSpeech(applicationContext)

        // Keep the leaderboard (Room) in sync. The query already sorts best-first, so we only
        // have to cut the list down to the top entries. Room re-emits whenever a score is inserted.
        viewModelScope.launch {
            highScoreDao.observeAllScoresSortedBestFirst().collect { allScoresSortedBestFirst ->
                _topScores.value = allScoresSortedBestFirst.take(TOP_SCORES_COUNT)
            }
        }

        // Keep the settings (DataStore) in sync, and apply the saved game type to the game state
        viewModelScope.launch {
            settingsRepository.gameSettings.collect { savedSettings ->
                _currentSettings.value = savedSettings
                _gameState.value = _gameState.value.copy(gameType = savedSettings.gameType)
            }
        }
    }
}

// Class with the different game types
enum class GameType {
    Audio,
    Visual,
    AudioVisual
}

/**
 * Everything the UI needs to draw the Game screen. Immutable: the ViewModel publishes a new copy
 * whenever something changes.
 */
data class GameState(
    val gameType: GameType = GameType.Visual,  // Type of the game
    val eventValue: Int = -1,                  // Cell to highlight (0-8), or -1 for none

    val currentEventNumber: Int = 0,           // 1-based number of the event being shown (0 = not started)
    val eventsInRound: Int = 0,                // Total number of events in the current round
    val isRoundRunning: Boolean = false,       // True from round start until the last event ended

    /** Increases by one for every wrong match press; the UI plays its feedback animation on change. */
    val incorrectPressCount: Int = 0
)

/** A ViewModel with fixed example data, used only by the @Preview functions. */
class FakeVM : GameViewModel {
    override val gameState: StateFlow<GameState>
        get() = MutableStateFlow(GameState()).asStateFlow()
    override val score: StateFlow<Int>
        get() = MutableStateFlow(2).asStateFlow()
    override val topScores: StateFlow<List<HighScoreEntity>>
        get() = MutableStateFlow(
            listOf(
                HighScoreEntity(1, 12, "Visual", 2, 20, 0L),
                HighScoreEntity(2, 9, "AudioVisual", 3, 20, 0L),
                HighScoreEntity(3, 7, "Audio", 2, 30, 0L)
            )
        ).asStateFlow()
    override val nBack: Int
        get() = 2
    override val settings: StateFlow<GameSettings>
        get() = MutableStateFlow(GameSettings()).asStateFlow()

    override fun setGameType(gameType: GameType) {
    }

    override fun setNBackValue(nBackValue: Int) {
    }

    override fun setEventDurationMillis(eventDurationMillis: Long) {
    }

    override fun setEventsPerRound(eventsPerRound: Int) {
    }

    override fun startGame() {
    }

    override fun stopGame() {
    }

    override fun checkMatch() {
    }
}