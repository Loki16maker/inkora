package com.inkora.study

/** A question type that can be generated without a network connection. */
enum class QuizQuestionType { MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER }

data class QuizQuestion(
    val prompt: String,
    val answer: String,
    val options: List<String> = emptyList(),
    val type: QuizQuestionType = QuizQuestionType.MULTIPLE_CHOICE,
    val explanation: String = "",
)

data class Quiz(
    val title: String,
    val questions: List<QuizQuestion>,
)

/**
 * Small, deterministic question generator for offline study.
 *
 * It deliberately uses sentences from the user's material as the source of truth. This makes
 * quizzes useful immediately on Windows and Android without an API key, while keeping the
 * generated answer traceable to the original notes.
 */
object QuizGenerator {
    fun generate(source: String, requestedCount: Int = 8): Quiz {
        val sentences = source
            .replace('\u00a0', ' ')
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.replace(Regex("\\s+"), " ").trim(' ', '-', '\u2022', '\t') }
            .filter { it.split(' ').size >= 5 }
            .distinct()
            .take(80)

        if (sentences.isEmpty()) return Quiz("Quick quiz", emptyList())
        val keywords = sentences.flatMap(::keywords).distinct()
        val count = requestedCount.coerceIn(1, 20)
        val questions = buildList {
            sentences.take(count).forEachIndexed { index, sentence ->
                val keyword = keywords.getOrNull(index) ?: keywords.firstOrNull()
                if (index % 5 == 4) {
                    add(QuizQuestion("Explain this idea in your own words:\n$sentence", sentence, type = QuizQuestionType.SHORT_ANSWER, explanation = sentence))
                } else if (index % 4 == 3 && keyword != null && keyword.length >= 4) {
                    val statement = sentence.replaceFirst(Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE), "not $keyword")
                    add(QuizQuestion("True or false:\n$statement", "false", listOf("true", "false"), QuizQuestionType.TRUE_FALSE, sentence))
                } else if (keyword != null && keyword.length >= 4) {
                    val distractors = keywords.filter { it != keyword }.take(3)
                    val blanked = sentence.replaceFirst(Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE), "_____")
                    val options = (listOf(keyword) + distractors).distinct().take(4).shuffled(seed = index * 31 + source.length)
                    add(QuizQuestion("Complete the statement:\n$blanked", keyword, options, QuizQuestionType.MULTIPLE_CHOICE, sentence))
                } else {
                    add(QuizQuestion("Explain this idea in your own words:\n$sentence", sentence, type = QuizQuestionType.SHORT_ANSWER, explanation = sentence))
                }
            }
        }.toMutableList()

        return Quiz("Study quiz", questions.take(count))
    }

    private fun keywords(sentence: String): List<String> = sentence
        .split(Regex("[^A-Za-z0-9']+"))
        .filter { it.length >= 5 && it.lowercase() !in STOP_WORDS }
        .sortedWith(compareByDescending<String> { it.length }.thenBy { it.lowercase() })
        .take(3)

    private fun <T> List<T>.shuffled(seed: Int): List<T> {
        if (size < 2) return this
        val result = toMutableList()
        var state = seed.toLong() * 1103515245L + 12345L
        for (i in result.lastIndex downTo 1) {
            state = state * 1103515245L + 12345L
            val j = ((state ushr 16) % (i + 1)).toInt()
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }

    private val STOP_WORDS = setOf(
        "about", "after", "again", "being", "between", "could", "first", "found", "their",
        "there", "these", "those", "which", "where", "while", "would", "because", "often",
        "using", "other", "each", "than", "with", "from", "into", "also", "more", "some",
    )
}
