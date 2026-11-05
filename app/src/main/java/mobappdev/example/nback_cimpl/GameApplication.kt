package mobappdev.example.nback_cimpl

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import mobappdev.example.nback_cimpl.data.HighScoreDao
import mobappdev.example.nback_cimpl.data.NBackDatabase
import mobappdev.example.nback_cimpl.data.SettingsRepository

/**
 * This is the Main Application
 *
 * Custom Application class used for manual dependency injection: it creates the
 * settings repository (DataStore) and the high score DAO (Room) once, so that
 * GameVM.Factory can hand them to the ViewModel.
 *
 * Date: 25-08-2023
 * Version: Version 1.0
 * Author: Yeetivity
 */

private const val APP_PREFERENCES_NAME = "game_preferences"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = APP_PREFERENCES_NAME
)

/*
* Custom app entry point for manual dependency injection
*/
class GameApplication: Application() {

    /** Reads and writes the user's settings (stimulus type, n, event duration, events per round). */
    lateinit var settingsRepository: SettingsRepository

    /** Reads and writes the stored high scores in the Room database. */
    lateinit var highScoreDao: HighScoreDao

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(dataStore)
        highScoreDao = NBackDatabase.getInstance(this).highScoreDao()
    }
}