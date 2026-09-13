package com.inkora.study

import kotlinx.serialization.Serializable

@Serializable
enum class QuizQuestionType { MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER }

@Serializable
data class QuizQuestion(
    val prompt: String,
    val answer: String,
    val options: List<String> = emptyList(),
    val type: QuizQuestionType = QuizQuestionType.MULTIPLE_CHOICE,
    val explanation: String = "",
)

@Serializable
data class Quiz(
    val title: String,
    val questions: List<QuizQuestion>,
)
