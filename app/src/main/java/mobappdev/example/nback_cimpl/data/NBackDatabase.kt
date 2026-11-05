package mobappdev.example.nback_cimpl.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Room database of the app. Currently it holds one table: "high_scores".
 *
 * Only one instance should exist for the whole app, so access it through
 * [getInstance] (for example from GameApplication) rather than building it yourself.
 */
@Database(
    entities = [HighScoreEntity::class],
    version = 1,
    exportSchema = false // No schema export needed for a lab project
)
abstract class NBackDatabase : RoomDatabase() {

    /** Gives access to the queries on the "high_scores" table. */
    abstract fun highScoreDao(): HighScoreDao

    companion object {

        private const val DATABASE_FILE_NAME = "nback_database"

        /** Cached instance. @Volatile makes writes visible to all threads immediately. */
        @Volatile
        private var cachedInstance: NBackDatabase? = null

        /**
         * Returns the shared database instance, creating it on first use.
         * synchronized guarantees that two threads cannot create two databases at once.
         */
        fun getInstance(context: Context): NBackDatabase {
            return cachedInstance ?: synchronized(this) {
                cachedInstance ?: Room.databaseBuilder(
                    context.applicationContext, // application context avoids leaking an Activity
                    NBackDatabase::class.java,
                    DATABASE_FILE_NAME
                ).build().also { cachedInstance = it }
            }
        }
    }
}