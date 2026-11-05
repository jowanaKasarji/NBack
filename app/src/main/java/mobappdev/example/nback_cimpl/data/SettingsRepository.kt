package mobappdev.example.nback_cimpl.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import mobappdev.example.nback_cimpl.ui.viewmodels.GameType
import java.io.IOException

/**
 * Reads and writes the user's game settings using DataStore<Preferences>.
 *
 * The ViewModel observes [gameSettings] (a Flow that emits a new [GameSettings] whenever
 * any single setting changes) and calls the save... functions when the user changes a control.
 *
 * Note: the high score is NOT stored here; scores live in the Room database.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>
) {

    private companion object {
        const val LOG_TAG = "SettingsRepository"

        // Each setting is stored under its own typed key.
        val GAME_TYPE_KEY = stringPreferencesKey("game_type_name")
        val N_BACK_VALUE_KEY = intPreferencesKey("n_back_value")
        val EVENT_DURATION_MILLIS_KEY = longPreferencesKey("event_duration_millis")
        val EVENTS_PER_ROUND_KEY = intPreferencesKey("events_per_round")
    }

    /**
     * Emits the current settings and every later change.
     * Missing keys (first app start) fall back to the defaults in [GameSettings].
     */
    val gameSettings: Flow<GameSettings> = dataStore.data
        .catch { error ->
            // An IOException means the file could not be read: continue with empty preferences,
            // which results in default settings. Any other error is a real bug, so rethrow it.
            if (error is IOException) {
                Log.e(LOG_TAG, "Could not read settings, using defaults", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> convertPreferencesToGameSettings(preferences) }

    /** Saves which stimulus type (Visual / Audio / AudioVisual) the player selected. */
    suspend fun saveGameType(gameType: GameType) {
        dataStore.edit { preferences -> preferences[GAME_TYPE_KEY] = gameType.name }
    }

    /** Saves the n value of the n-back task. */
    suspend fun saveNBackValue(nBackValue: Int) {
        dataStore.edit { preferences -> preferences[N_BACK_VALUE_KEY] = nBackValue }
    }

    /** Saves how long each event lasts, in milliseconds. */
    suspend fun saveEventDurationMillis(eventDurationMillis: Long) {
        dataStore.edit { preferences ->
            preferences[EVENT_DURATION_MILLIS_KEY] = eventDurationMillis
        }
    }

    /** Saves the number of events in one round. */
    suspend fun saveEventsPerRound(eventsPerRound: Int) {
        dataStore.edit { preferences -> preferences[EVENTS_PER_ROUND_KEY] = eventsPerRound }
    }

    /**
     * Builds a [GameSettings] object from the raw stored key/value pairs.
     * Any missing or unreadable value is replaced by its default.
     */
    private fun convertPreferencesToGameSettings(preferences: Preferences): GameSettings {
        val storedGameTypeName = preferences[GAME_TYPE_KEY]

        // Look the stored name up among the enum constants; null (not found) -> default.
        val gameType = GameType.values().firstOrNull { it.name == storedGameTypeName }
            ?: GameSettings.DEFAULT_GAME_TYPE

        return GameSettings(
            gameType = gameType,
            nBackValue = preferences[N_BACK_VALUE_KEY] ?: GameSettings.DEFAULT_N_BACK_VALUE,
            eventDurationMillis = preferences[EVENT_DURATION_MILLIS_KEY]
                ?: GameSettings.DEFAULT_EVENT_DURATION_MILLIS,
            eventsPerRound = preferences[EVENTS_PER_ROUND_KEY]
                ?: GameSettings.DEFAULT_EVENTS_PER_ROUND
        )
    }
}