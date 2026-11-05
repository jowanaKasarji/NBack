package mobappdev.example.nback_cimpl

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local JVM unit tests for the sequence generation and scoring in [NBackHelper].
 *
 * Because the generator is pure Kotlin, these tests need no emulator:
 * place this file in src/test/java/mobappdev/example/nback_cimpl/ and run it from Android Studio
 * (right-click -> Run) or with `./gradlew testDebugUnitTest`.
 *
 * Every generator test is repeated for many seeds and settings, because the generator is random
 * and a single lucky run would prove very little.
 */
class NBackHelperTest {

    private val nBackHelper = NBackHelper()

    // Settings that are combined with each other in the tests below
    private val sizesToTest = listOf(10, 20, 30, 50)
    private val nBackValuesToTest = listOf(1, 2, 3, 4)
    private val matchPercentagesToTest = listOf(20, 25, 30, 40)
    private val combinationsToTest = listOf(2, 4, 9)
    private val seedsToTest = 0 until 20

    /** Runs [check] once for every combination of the settings above. */
    private fun forEveryTestSetting(
        check: (sequence: IntArray, size: Int, combinations: Int, percentMatch: Int, nBack: Int) -> Unit
    ) {
        for (size in sizesToTest)
            for (nBack in nBackValuesToTest)
                for (percentMatch in matchPercentagesToTest)
                    for (combinations in combinationsToTest)
                        for (seed in seedsToTest) {
                            val sequence = nBackHelper.generateNBackString(
                                size, combinations, percentMatch, nBack, Random(seed)
                            )
                            check(sequence, size, combinations, percentMatch, nBack)
                        }
    }

    // ---------- 1. Length (and value range) ----------

    @Test
    fun generatedSequence_hasExactlyTheRequestedLength() {
        forEveryTestSetting { sequence, size, _, _, _ ->
            assertEquals(size, sequence.size)
        }
    }

    @Test
    fun generatedSequence_onlyContainsValuesInsideTheStimulusRange() {
        forEveryTestSetting { sequence, _, combinations, _, _ ->
            assertTrue(
                "All values must be in 0 until $combinations",
                sequence.all { value -> value in 0 until combinations }
            )
        }
    }

    // ---------- 2. Match-rate enforcement ----------

    @Test
    fun generatedSequence_containsExactlyTheTargetNumberOfMatches() {
        forEveryTestSetting { sequence, size, _, percentMatch, nBack ->
            val expectedMatches = nBackHelper.calculateTargetMatchCount(size, percentMatch, nBack)
            val actualMatches = nBackHelper.findMatchPositions(sequence, nBack).size
            assertEquals(
                "size=$size, nBack=$nBack, percent=$percentMatch, " +
                        "sequence=${sequence.joinToString()}",
                expectedMatches,
                actualMatches
            )
        }
    }

    @Test
    fun targetMatchCount_followsTheRoundingFormula() {
        assertEquals(3, nBackHelper.calculateTargetMatchCount(size = 10, percentMatch = 30, nBack = 2))
        assertEquals(4, nBackHelper.calculateTargetMatchCount(size = 20, percentMatch = 20, nBack = 2))
        assertEquals(8, nBackHelper.calculateTargetMatchCount(size = 20, percentMatch = 40, nBack = 3))
        // 25 * 30 / 100 = 7.5 -> rounds up to 8
        assertEquals(8, nBackHelper.calculateTargetMatchCount(size = 25, percentMatch = 30, nBack = 2))
    }

    @Test
    fun targetMatchCount_isCappedByTheNumberOfPositionsThatCanBeMatches() {
        // size 5, nBack 4 -> only position 4 can be a match, although 40% of 5 would be 2
        assertEquals(1, nBackHelper.calculateTargetMatchCount(size = 5, percentMatch = 40, nBack = 4))
    }

    // ---------- 3. First-n guarantee ----------

    @Test
    fun firstNEvents_areNeverMatches() {
        forEveryTestSetting { sequence, _, _, _, nBack ->
            for (position in 0 until nBack) {
                assertFalse(
                    "Position $position is within the first $nBack events and must not be a match",
                    nBackHelper.isMatchAt(sequence, position, nBack)
                )
            }
            // Equivalent view: the earliest match (if any) is at position nBack or later.
            val firstMatchPosition = nBackHelper.findMatchPositions(sequence, nBack).firstOrNull()
            assertTrue(firstMatchPosition == null || firstMatchPosition >= nBack)
        }
    }

    @Test
    fun matchesCanAppearDirectlyAtPositionN_soTheEligibleRangeIsFullyUsed() {
        // With a very short round almost all eligible positions must become matches, so over
        // many seeds position nBack has to be chosen at least once.
        val nBack = 2
        val positionNWasAMatchAtLeastOnce = (0 until 50).any { seed ->
            val sequence = nBackHelper.generateNBackString(5, 9, 40, nBack, Random(seed))
            nBackHelper.isMatchAt(sequence, nBack, nBack)
        }
        assertTrue(positionNWasAMatchAtLeastOnce)
    }

    // ---------- 4. Scoring formula (correct = +1, incorrect = unchanged) ----------

    /** One row of the scoring table: score before the press, was it correct, expected score after. */
    private data class ScoringCase(
        val scoreBefore: Int,
        val pressWasCorrect: Boolean,
        val expectedScoreAfter: Int
    )

    @Test
    fun calculateNewScore_followsTheScoringTable() {
        val cases = listOf(
            ScoringCase(scoreBefore = 0, pressWasCorrect = true, expectedScoreAfter = 1),
            ScoringCase(scoreBefore = 5, pressWasCorrect = true, expectedScoreAfter = 6),
            ScoringCase(scoreBefore = 100, pressWasCorrect = true, expectedScoreAfter = 101),
            // An incorrect press never changes the score.
            ScoringCase(scoreBefore = 0, pressWasCorrect = false, expectedScoreAfter = 0),
            ScoringCase(scoreBefore = 5, pressWasCorrect = false, expectedScoreAfter = 5),
            ScoringCase(scoreBefore = 100, pressWasCorrect = false, expectedScoreAfter = 100)
        )
        for (case in cases) {
            assertEquals(
                "scoreBefore=${case.scoreBefore}, correct=${case.pressWasCorrect}",
                case.expectedScoreAfter,
                nBackHelper.calculateNewScore(case.scoreBefore, case.pressWasCorrect)
            )
        }
    }

    @Test
    fun calculateNewScore_neverDecreases() {
        var score = 0
        val presses = listOf(true, false, false, true, false, true, true, false)
        for (pressWasCorrect in presses) {
            val newScore = nBackHelper.calculateNewScore(score, pressWasCorrect)
            assertTrue("Score went down from $score to $newScore", newScore >= score)
            score = newScore
        }
        assertEquals(4, score) // exactly the four correct presses
    }

    @Test
    fun scoreEqualsTheNumberOfCorrectPresses_whenPressingOnEveryAllowedEvent() {
        // Documents the rule: a player who presses "match" on every event where a press is
        // allowed (position >= nBack) ends with exactly the number of matches in the sequence.
        forEveryTestSetting { sequence, _, _, _, nBack ->
            var score = 0
            for (position in nBack until sequence.size) {
                val pressWasCorrect = nBackHelper.isMatchAt(sequence, position, nBack)
                score = nBackHelper.calculateNewScore(score, pressWasCorrect)
            }
            assertEquals(nBackHelper.findMatchPositions(sequence, nBack).size, score)
        }
    }

    // ---------- Extra: repeatability and invalid input ----------

    @Test
    fun sameSeed_producesTheSameSequence() {
        val first = nBackHelper.generateNBackString(20, 9, 30, 2, Random(42))
        val second = nBackHelper.generateNBackString(20, 9, 30, 2, Random(42))
        assertTrue(first.contentEquals(second))
    }

    @Test(expected = IllegalArgumentException::class)
    fun matchPercentageOutsideTheAllowedRange_isRejected() {
        nBackHelper.generateNBackString(size = 20, combinations = 9, percentMatch = 60, nBack = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nBackNotSmallerThanSize_isRejected() {
        nBackHelper.generateNBackString(size = 3, combinations = 9, percentMatch = 30, nBack = 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fewerThanTwoStimuli_isRejected() {
        nBackHelper.generateNBackString(size = 20, combinations = 1, percentMatch = 30, nBack = 2)
    }
}