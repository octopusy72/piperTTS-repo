package com.yiwoosolution.koreantts.speech

/** Removes only web markup that should never be spoken by the TTS engine. */
object TtsInputSanitizer {
    private val footnoteLink = Regex("\\[\\[[0-9]+]]\\([^)]*\\)")
    private val markdownLink = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val wikiMarker = Regex("(?:\\[\\[|\\[\\\\\\[)(?:[0-9]+|edit)(?:]]|\\\\\\]\\])", RegexOption.IGNORE_CASE)
    private val standaloneUrl = Regex("(?i)(?<![A-Za-z0-9])https?://\\S+")

    fun sanitize(input: String): String = input
        .replace(footnoteLink, "")
        .replace(markdownLink) {
            val label = it.groupValues[1]
            if (label.matches(Regex("\\\\?\\[?\\d+\\.?\\]?"))) "" else label
        }
        .replace(wikiMarker, "")
        .replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
        .replace(standaloneUrl, "")
        .replace(Regex("[ \\t\\r\\n]+"), " ")
        .trim()
}
