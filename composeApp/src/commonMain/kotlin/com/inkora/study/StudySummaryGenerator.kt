package com.inkora.study

/** Extractive study guide generator that works offline and never invents facts. */
object StudySummaryGenerator {
    fun generate(source: String, maxPoints: Int = 8): List<String> {
        val sentences = source
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.split(' ').size >= 6 }
            .distinct()
        if (sentences.size <= maxPoints) return sentences
        return sentences.sortedWith(compareByDescending<String> { score(it) }.thenBy { it }).take(maxPoints)
    }

    private fun score(sentence: String): Int {
        val words = sentence.split(Regex("[^A-Za-z0-9']+")).filter { it.length >= 5 }
        val signal = words.count { it.lowercase() !in setOf("there", "these", "those", "which", "about", "often") }
        return signal * 3 + words.distinctBy { it.lowercase() }.size
    }
}
