package com.yiwoosolution.koreantts.speech

import java.io.InputStream

class LoanwordNormalizer private constructor(
    private val pronunciations: Map<String, String>,
) {
    private val entryPattern = pronunciations.keys
        .sortedByDescending(String::length)
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString("|") { key -> key.split(' ').joinToString("\\s+") { Regex.escape(it) } }
        ?.let { Regex("(?i)(?<![A-Za-z])(?:$it)(?![A-Za-z])") }

    fun normalize(input: String): String {
        if (entryPattern == null || input.none(::isHangulContext)) return input
        return entryPattern.replace(input) { match ->
            pronunciations[match.value.lowercase().replace(Regex("\\s+"), " ")] ?: match.value
        }
    }

    companion object {
        private val EMPTY = LoanwordNormalizer(emptyMap())

        fun empty(): LoanwordNormalizer = EMPTY

        fun fromTsv(input: InputStream): LoanwordNormalizer {
            val entries = linkedMapOf<String, String>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEachIndexed { index, line ->
                    if (line.isBlank()) return@forEachIndexed
                    val fields = line.split('\t')
                    require(fields.size == 2) { "Invalid loanword TSV at line ${index + 1}" }
                    val key = fields[0].trim().lowercase()
                    val pronunciation = fields[1].trim()
                    require(key.isNotEmpty() && pronunciation.isNotEmpty()) {
                        "Empty loanword TSV field at line ${index + 1}"
                    }
                    entries[key] = pronunciation
                }
            }
            return LoanwordNormalizer(entries)
        }

        private fun isHangulContext(char: Char): Boolean =
            char in '\u1100'..'\u11ff' || char in '\u3130'..'\u318f' || char in '\uac00'..'\ud7af'
    }
}
