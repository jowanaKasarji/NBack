package mobappdev.example.nback_cimpl

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Generates and analyses n-back event sequences in pure Kotlin (no native/C code).
 *
 * Terminology used in the comments below:
 *  - "event":   one stimulus in the sequence (a highlighted grid cell and/or a spoken letter).
 *               It is represented as an Int in the range 0 until [combinations].
 *  - "match":   event number i is a match when i >= n and event[i] == event[i - n].
 *               Because there is no event "n steps earlier" for the first n events,
 *               those events can never be matches.
 */
class NBackHelper {

    /**
     * Creates a random sequence of [size] events where exactly the requested share of the
     * events are n-back matches.
     *
     * Guarantees:
     *  1. The result has exactly [size] elements, each in 0 until [combinations].
     *  2. The first [nBack] events are never matches.
     *  3. The number of matches is exactly [calculateTargetMatchCount] (see that function for
     *     the formula). Events that are NOT meant to be matches are forced to differ from the
     *     event n steps earlier, so no accidental extra matches can appear.
     *
     * @param size          total number of events in the round
     * @param combinations  number of distinct stimuli (9 for a 3x3 grid); must be at least 2
     * @param percentMatch  wanted share of matches in percent, 20..40 according to the spec
     * @param nBack         the n in n-back (at least 1, and smaller than [size])
     * @param random        source of randomness; tests pass a seeded Random for repeatable results
     */
    fun generateNBackString(
        size: Int,
        combinations: Int,
        percentMatch: Int,
        nBack: Int,
        random: Random = Random.Default
    ): IntArray {

        // ---- Step 0: reject input that cannot produce a sensible sequence ----
        require(nBack >= 1) { "nBack must be at least 1 but was $nBack" }
        require(size > nBack) { "size ($size) must be larger than nBack ($nBack)" }
        // With only one possible stimulus every event would be a match, so we need >= 2.
        require(combinations >= 2) { "combinations must be at least 2 but was $combinations" }
        require(percentMatch in MIN_MATCH_PERCENT..MAX_MATCH_PERCENT) {
            "percentMatch must be between $MIN_MATCH_PERCENT and $MAX_MATCH_PERCENT but was $percentMatch"
        }

        // ---- Step 1: decide HOW MANY matches the sequence must contain ----
        val targetMatchCount = calculateTargetMatchCount(size, percentMatch, nBack)

        // ---- Step 2: decide WHERE the matches go ----
        // Only positions nBack, nBack+1, ..., size-1 have an event n steps earlier to compare
        // with. Positions 0 until nBack are therefore never eligible: this is what guarantees
        // that the first n events can never be matches.
        val eligibleMatchPositions = (nBack until size).toList()

        // Shuffle the eligible positions and keep the first targetMatchCount of them.
        // Every eligible position has the same chance of being picked, so matches are spread
        // randomly. A Set gives a fast "is position i a match?" lookup in Step 3.
        val matchPositions: Set<Int> =
            eligibleMatchPositions.shuffled(random).take(targetMatchCount).toSet()

        // ---- Step 3: build the sequence from left to right ----
        // Going left to right means event[i - nBack] is always already known when we need it.
        val sequence = IntArray(size)
        for (position in 0 until size) {
            sequence[position] = when {

                // Case A: the first n events. Nothing to compare with, so any stimulus is fine.
                position < nBack -> random.nextInt(combinations)

                // Case B: this position was chosen to be a match -> copy the event n steps back.
                position in matchPositions -> sequence[position - nBack]

                // Case C: this position must NOT be a match -> pick any stimulus except the one
                // n steps back (see pickValueDifferentFrom for how this is done fairly).
                else -> pickValueDifferentFrom(
                    forbiddenValue = sequence[position - nBack],
                    combinations = combinations,
                    random = random
                )
            }
        }
        return sequence
    }

    /**
     * Returns how many matches a sequence of [size] events must contain.
     *
     * Formula: targetMatches = round(size * percentMatch / 100)
     * Example: size = 10, percentMatch = 30  ->  10 * 30 / 100 = 3.0  ->  3 matches.
     *
     * The result is capped at the number of positions that CAN be matches (size - nBack),
     * because the first n events are never matches. This only matters for very short rounds
     * with a large n, e.g. size = 5, nBack = 4 leaves only one eligible position.
     */
    fun calculateTargetMatchCount(size: Int, percentMatch: Int, nBack: Int): Int {
        val wantedMatches = (size * percentMatch / 100.0).roundToInt()
        val eligiblePositionCount = size - nBack
        return minOf(wantedMatches, eligiblePositionCount)
    }

    /**
     * True when the event at [position] is an n-back match, i.e. it equals the event [nBack]
     * steps earlier. Always false for the first [nBack] events (and for out-of-range positions).
     *
     * The game logic (checkMatch) and the tests both use this single definition of "match".
     */
    fun isMatchAt(sequence: IntArray, position: Int, nBack: Int): Boolean {
        if (position < nBack || position >= sequence.size) return false
        return sequence[position] == sequence[position - nBack]
    }

    /** Returns every position of [sequence] that is an n-back match, in ascending order. */
    fun findMatchPositions(sequence: IntArray, nBack: Int): List<Int> {
        return sequence.indices.filter { position -> isMatchAt(sequence, position, nBack) }
    }

    /**
     * Scoring formula: a correct match press adds [CORRECT_PRESS_POINTS]; an incorrect press
     * leaves the score unchanged. The score therefore equals the number of correct responses
     * and can never decrease.
     *
     * Pure function: no state, no Android classes, so it is trivial to unit test.
     *
     * @param currentScore    the score before this press
     * @param pressWasCorrect true if the press was on an actual n-back match
     * @return the score after this press
     */
    fun calculateNewScore(currentScore: Int, pressWasCorrect: Boolean): Int {
        return if (pressWasCorrect) currentScore + CORRECT_PRESS_POINTS else currentScore
    }

    /**
     * Picks a uniformly random value from 0 until [combinations] that is NOT [forbiddenValue].
     *
     * Trick: draw from the smaller range 0..combinations-2 (that is combinations-1 numbers),
     * then "skip over" the forbidden value:
     *  - a draw below the forbidden value is used as it is,
     *  - a draw equal to or above it is shifted up by one.
     * Example with combinations = 4, forbiddenValue = 2: draws 0,1,2 become 0,1,3,
     * so the possible results are {0, 1, 3}, each equally likely, and 2 can never occur.
     * This needs no retry loop, so it always finishes in one step.
     */
    private fun pickValueDifferentFrom(
        forbiddenValue: Int,
        combinations: Int,
        random: Random
    ): Int {
        val drawnValue = random.nextInt(combinations - 1)
        return if (drawnValue >= forbiddenValue) drawnValue + 1 else drawnValue
    }

    companion object {
        /** Lowest allowed match rate in percent (from SPEC.md). */
        const val MIN_MATCH_PERCENT = 20

        /** Highest allowed match rate in percent (from SPEC.md). */
        const val MAX_MATCH_PERCENT = 40

        /** Points gained for a correct match press. */
        const val CORRECT_PRESS_POINTS = 1
    }
}