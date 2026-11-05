package mobappdev.example.nback_cimpl.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row in the "high_scores" table: the result of a single finished round.
 *
 * Every finished round is stored together with the settings it was played with,
 * so scores can later be compared fairly (e.g. a 2-back score vs. a 3-back score).
 */
@Entity(tableName = "high_scores")
data class HighScoreEntity(

    /** Unique row id. Room generates it automatically when we insert with id = 0. */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Number of correct matches the player registered during the round. */
    val scoreValue: Int,

    /** Name of the GameType enum constant used (e.g. "Visual"). Stored as text for simplicity. */
    val gameTypeName: String,

    /** The n in n-back that was used in this round. */
    val nBackValue: Int,

    /** How many events (stimuli) the round contained. */
    val eventsPerRound: Int,

    /** Time the round finished, in milliseconds since 1970 (System.currentTimeMillis()). */
    val achievedAtEpochMillis: Long
)