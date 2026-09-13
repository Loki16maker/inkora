package com.inkora.study

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val quizJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

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

/** Accepts the JSON returned by ChatGPT, including a fenced JSON code block. */
fun parseQuizJson(raw: String): Quiz {
    val cleaned = raw.trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val quiz = quizJson.decodeFromString<Quiz>(cleaned)
    require(quiz.questions.isNotEmpty()) { "The pasted quiz has no questions." }
    require(quiz.questions.all { it.prompt.isNotBlank() && it.answer.isNotBlank() }) { "Every pasted question needs a prompt and answer." }
    return quiz.copy(questions = quiz.questions.take(20).map { question ->
        when (question.type) {
            QuizQuestionType.TRUE_FALSE -> question.copy(answer = if (question.answer.equals("true", true)) "true" else "false", options = listOf("true", "false"))
            QuizQuestionType.MULTIPLE_CHOICE -> question.copy(options = question.options.distinct().filter(String::isNotBlank).take(6))
            QuizQuestionType.SHORT_ANSWER -> question
        }
    })
}
