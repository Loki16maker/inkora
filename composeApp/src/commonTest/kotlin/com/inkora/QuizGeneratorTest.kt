package com.inkora

import com.inkora.study.QuizGenerator
import com.inkora.study.QuizQuestionType
import com.inkora.study.ReviewRating
import com.inkora.study.ReviewSchedule
import com.inkora.study.review
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuizGeneratorTest {
    private val notes = """
        The urinary system removes waste from the blood and helps regulate fluid balance.
        The kidneys filter plasma and produce urine through a network of nephrons.
        The ureters carry urine from each kidney to the urinary bladder.
        The bladder stores urine until it leaves the body through the urethra.
        Antidiuretic hormone increases water reabsorption in the collecting ducts.
    """.trimIndent()

    @Test
    fun generatesTraceableQuestionsWithAnswers() {
        val quiz = QuizGenerator.generate(notes, 5)
        assertEquals(5, quiz.questions.size)
        assertTrue(quiz.questions.all { it.answer.isNotBlank() })
        assertTrue(quiz.questions.any { it.type == QuizQuestionType.MULTIPLE_CHOICE })
        assertTrue(quiz.questions.any { it.type == QuizQuestionType.TRUE_FALSE })
        assertTrue(quiz.questions.any { it.type == QuizQuestionType.SHORT_ANSWER })
    }

    @Test
    fun handlesEmptyMaterialWithoutInventingQuestions() {
        assertFalse(QuizGenerator.generate("", 8).questions.iterator().hasNext())
    }

    @Test
    fun spacedRepetitionMovesDueDateForwardForGoodAnswers() {
        val schedule = ReviewSchedule().review(ReviewRating.GOOD, 1_000L)
        assertEquals(1, schedule.intervalDays)
        assertTrue(schedule.dueAtEpochMs > 1_000L)
    }
}
