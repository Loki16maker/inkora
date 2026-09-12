package com.inkora.study

import kotlinx.serialization.Serializable

enum class ReviewRating { AGAIN, HARD, GOOD, EASY }

@Serializable
data class ReviewSchedule(
    val streak: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Float = 2.5f,
    val dueAtEpochMs: Long = 0L,
)

/** Compact SM-2-inspired scheduling for quiz cards. State is serializable by callers if needed. */
fun ReviewSchedule.review(rating: ReviewRating, nowEpochMs: Long): ReviewSchedule {
    val nextStreak = when (rating) {
        ReviewRating.AGAIN -> 0
        else -> streak + 1
    }
    val nextEase = when (rating) {
        ReviewRating.AGAIN -> (easeFactor - .2f).coerceAtLeast(1.3f)
        ReviewRating.HARD -> (easeFactor - .05f).coerceAtLeast(1.3f)
        ReviewRating.GOOD -> easeFactor
        ReviewRating.EASY -> (easeFactor + .1f).coerceAtMost(3.2f)
    }
    val nextInterval = when (rating) {
        ReviewRating.AGAIN -> 0
        ReviewRating.HARD -> maxOf(1, (intervalDays * 1.2f).toInt())
        ReviewRating.GOOD -> when (nextStreak) { 1 -> 1; 2 -> 3; else -> maxOf(1, (intervalDays * nextEase).toInt()) }
        ReviewRating.EASY -> when (nextStreak) { 1 -> 2; 2 -> 5; else -> maxOf(2, (intervalDays * nextEase * 1.3f).toInt()) }
    }
    return copy(streak = nextStreak, intervalDays = nextInterval, easeFactor = nextEase,
        dueAtEpochMs = nowEpochMs + nextInterval * 86_400_000L)
}
