package com.inkora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inkora.study.QuizQuestion
import com.inkora.study.QuizQuestionType
import com.inkora.study.ReviewRating
import com.inkora.study.ReviewSchedule
import com.inkora.study.review
import com.inkora.platform.platformEpochMillis
import com.inkora.study.StudySummaryGenerator
import com.inkora.study.Quiz
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** AI quiz player backed by the authenticated ChatGPT Edge Function. */
@Composable
fun QuizDialog(
    title: String,
    sourceText: String,
    initialSchedule: ReviewSchedule = ReviewSchedule(),
    onScheduleChange: (ReviewSchedule) -> Unit = {},
    onGenerateAiQuiz: (suspend (sourceText: String, requestedCount: Int, difficulty: String) -> Quiz)? = null,
    onDismiss: () -> Unit,
) {
    var quiz by remember(sourceText) { mutableStateOf<Quiz?>(null) }
    var index by remember(sourceText) { mutableIntStateOf(0) }
    var score by remember(sourceText) { mutableIntStateOf(0) }
    var selected by remember(sourceText) { mutableStateOf<String?>(null) }
    var answerText by remember(sourceText) { mutableStateOf("") }
    var submitted by remember(sourceText) { mutableStateOf(false) }
    var schedule by remember(sourceText, initialSchedule) { mutableStateOf(initialSchedule) }
    var showGuide by remember(sourceText) { mutableStateOf(false) }
    var difficulty by remember(sourceText) { mutableStateOf("mixed") }
    var aiBusy by remember(sourceText) { mutableStateOf(false) }
    var aiError by remember(sourceText) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun generateAiQuiz() {
        val generator = onGenerateAiQuiz ?: return
        if (aiBusy) return
        scope.launch {
            aiBusy = true
            aiError = null
            try {
                quiz = generator(sourceText, 8, difficulty)
                index = 0
                score = 0
                selected = null
                answerText = ""
                submitted = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                aiError = failure.message ?: "ChatGPT could not generate a quiz."
            } finally {
                aiBusy = false
            }
        }
    }
    val question = quiz?.questions?.getOrNull(index)

    if (question == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Generate a ChatGPT quiz") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Inkora sends this study text to your configured OpenAI service and receives structured questions. Nothing is generated locally.")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Difficulty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf("easy", "medium", "hard", "mixed").forEach { option ->
                            FilterChip(selected = difficulty == option, onClick = { difficulty = option }, label = { Text(option.replaceFirstChar { it.uppercase() }) }, enabled = !aiBusy)
                        }
                    }
                    aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Close") }
            },
            dismissButton = if (onGenerateAiQuiz != null) {
                { Button(onClick = ::generateAiQuiz, enabled = !aiBusy) { Text(if (aiBusy) "Generating…" else "Generate") } }
            } else null,
        )
        return
    }
    val activeQuiz = quiz ?: return

    val answered = selected ?: answerText.takeIf { it.isNotBlank() }
    val correct = answered?.let { response ->
        if (question.type == QuizQuestionType.SHORT_ANSWER) {
            response.trim().contains(question.answer.trim(), ignoreCase = true) ||
                question.answer.trim().contains(response.trim(), ignoreCase = true) && response.trim().length >= 8
        } else response.trim().equals(question.answer.trim(), ignoreCase = true)
    } ?: false

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quiz · $title") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Question ${index + 1} of ${activeQuiz.questions.size} · Score $score", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showGuide = !showGuide }) { Text(if (showGuide) "Quiz" else "Study guide") }
                        onGenerateAiQuiz?.let { _ ->
                            TextButton(enabled = !aiBusy, onClick = ::generateAiQuiz) { Text(if (aiBusy) "Generating…" else "ChatGPT quiz") }
                        }
                    }
                }
                onGenerateAiQuiz?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Difficulty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf("easy", "medium", "hard", "mixed").forEach { option ->
                            FilterChip(selected = difficulty == option, onClick = { difficulty = option }, label = { Text(option.replaceFirstChar(Char::uppercase)) }, enabled = !aiBusy)
                        }
                    }
                    Text("Uses your signed-in cloud account; the API key stays on the server.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (showGuide) {
                    Text("Key points", style = MaterialTheme.typography.titleMedium)
                    StudySummaryGenerator.generate(sourceText).forEach { point -> Text("• $point", style = MaterialTheme.typography.bodyMedium) }
                }
                Text(question.prompt, style = MaterialTheme.typography.titleMedium)
                when (question.type) {
                    QuizQuestionType.MULTIPLE_CHOICE, QuizQuestionType.TRUE_FALSE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            question.options.forEach { option ->
                                FilterChip(
                                    selected = selected == option,
                                    onClick = { if (!submitted) selected = option },
                                    label = { Text(option) },
                                    enabled = !submitted,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    QuizQuestionType.SHORT_ANSWER -> OutlinedTextField(
                        value = answerText,
                        onValueChange = { if (!submitted) answerText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text("Your answer") },
                        enabled = !submitted,
                    )
                }
                if (submitted) {
                    Text(if (correct) "Correct" else "Not quite", color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                    Text("Answer: ${question.answer}", style = MaterialTheme.typography.bodyMedium)
                    if (question.explanation.isNotBlank()) Text(question.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Review interval: ${schedule.intervalDays} day${if (schedule.intervalDays == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(ReviewRating.AGAIN to "Again", ReviewRating.HARD to "Hard", ReviewRating.GOOD to "Good", ReviewRating.EASY to "Easy").forEach { (rating, label) ->
                            TextButton(onClick = { schedule = schedule.review(rating, platformEpochMillis()); onScheduleChange(schedule) }) { Text(label) }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!submitted) {
                        Button(onClick = {
                            if (answered != null) {
                                if (correct) score++
                                submitted = true
                            }
                        }, enabled = answered != null) { Text("Check answer") }
                    } else {
                        Button(onClick = {
                            if (index == activeQuiz.questions.lastIndex) {
                                index = 0
                                score = 0
                            } else index++
                            selected = null
                            answerText = ""
                            submitted = false
                        }) { Text(if (index == activeQuiz.questions.lastIndex) "Restart" else "Next") }
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        },
        confirmButton = {},
    )
}
