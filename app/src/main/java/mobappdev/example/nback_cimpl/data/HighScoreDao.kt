package mobappdev.example.nback_cimpl.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the "high_scores" table.
 *
 * Room generates the implementation of this interface at build time.
 * Functions returning Flow re-emit automatically whenever the table changes,
 * so the UI always shows up-to-date scores.
 */
@Dao
interface HighScoreDao {

    /** Adds one finished-round result to the table. Must be called off the main thread (suspend). */
    @Insert
    suspend fun insertHighScore(highScore: HighScoreEntity)

    /**
     * Returns all stored scores sorted best-first.
     * Ties are broken by age: the score that was achieved earlier is listed first.
     */
    @Query(
        "SELECT * FROM high_scores " +
                "ORDER BY scoreValue DESC, achievedAtEpochMillis ASC"
    )
    fun observeAllScoresSortedBestFirst(): Flow<List<HighScoreEntity>>
}