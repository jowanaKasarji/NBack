package mobappdev.example.nback_cimpl.data

import mobappdev.example.nback_cimpl.ui.viewmodels.GameType

/**
 * Immutable snapshot of all user-adjustable settings shown on the Home screen.
 *
 * The default values are used the first time the app starts, before the user changed anything.
 */
data class GameSettings(

    /** Which stimuli are used: Visual, Audio or AudioVisual. */
    val gameType: GameType = DEFAULT_GAME_TYPE,

    /** The n in n-back: how many events back the player must compare. */
    val nBackValue: Int = DEFAULT_N_BACK_VALUE,

    /** How long each event is shown/spoken, in milliseconds. */
    val eventDurationMillis: Long = DEFAULT_EVENT_DURATION_MILLIS,

    /** Total number of events in one round. */
    val eventsPerRound: Int = DEFAULT_EVENTS_PER_ROUND
) {
    companion object {
        val DEFAULT_GAME_TYPE = GameType.Visual
        const val DEFAULT_N_BACK_VALUE = 2
        const val DEFAULT_EVENT_DURATION_MILLIS = 2000L
        const val DEFAULT_EVENTS_PER_ROUND = 20

        // Allowed ranges and step sizes. The Home screen's +/- buttons and the ViewModel
        // both use these, so the two can never disagree. MIN_EVENTS_PER_ROUND is larger than
        // MAX_N_BACK_VALUE, so a round always has more events than n (the generator needs that).
        const val MIN_N_BACK_VALUE = 1
        const val MAX_N_BACK_VALUE = 5

        const val MIN_EVENT_DURATION_MILLIS = 1000L
        const val MAX_EVENT_DURATION_MILLIS = 4000L
        const val EVENT_DURATION_STEP_MILLIS = 500L

        const val MIN_EVENTS_PER_ROUND = 10
        const val MAX_EVENTS_PER_ROUND = 50
        const val EVENTS_PER_ROUND_STEP = 5
    }
}