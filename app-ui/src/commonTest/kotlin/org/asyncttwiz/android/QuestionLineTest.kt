package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** STK-181 (owner 2026-11-22, F1): a choice's question line shows only when it says something the ability text above it doesn't. */
class QuestionLineTest {
    private val forage = "When this creature enters, you may forage. If you do, put two +1/+1 counters on it."

    // STK-181: a "you may" lifted from the ability text, whole clauses of it, repeats it, whatever its case or punctuation
    @Test fun clausesOfTheAbilityTextRepeatIt() {
        assertTrue(QuestionLine.repeats("You may forage. If you do, put two +1/+1 counters on it.", forage))
        assertTrue(QuestionLine.repeats("you may forage", forage))
        assertTrue(QuestionLine.repeats("You may forage?", forage))
        assertTrue(QuestionLine.repeats(forage, forage), "the whole text")
        assertTrue(QuestionLine.repeats("You may pay {2}.", "When this creature dies, you may pay {2}. If you do, draw a card."))
    }

    // STK-181, STK-227: a question with words of its own still shows: a copy's new targets, part of a clause, other words
    @Test fun aQuestionWithWordsOfItsOwnShows() {
        assertFalse(QuestionLine.repeats("Choose new targets for the copy?", "When this creature enters, it deals 1 damage to any target."))
        assertFalse(QuestionLine.repeats("Forage?", forage), "only part of a clause")
        assertFalse(QuestionLine.repeats("Put two +1/+1 counters on it", forage.replace("two", "three")))
        assertFalse(QuestionLine.repeats("You may forage.", null), "no ability text shown")
        assertFalse(QuestionLine.repeats("", forage))
    }
}
