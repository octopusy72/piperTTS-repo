package com.yiwoosolution.koreantts.speech

import java.io.InputStream

class KoreanSpeechNormalizer(
    private val mode: SpeechMode = SpeechMode.NATURAL,
    private val sanitizer: UnicodeSanitizer = UnicodeSanitizer(),
    private val detector: SpeechPatternDetector = SpeechPatternDetector(),
    private val emoticons: EmoticonNormalizer = EmoticonNormalizer(),
    private val emoji: EmojiNormalizer = EmojiNormalizer(),
    private val loanwords: LoanwordNormalizer = LoanwordNormalizer.empty(),
    private val jamo: HangulJamoNormalizer = HangulJamoNormalizer(),
    private val english: EnglishNormalizer = EnglishNormalizer(),
    private val symbols: SymbolNormalizer = SymbolNormalizer(),
    private val validator: SafeOutputValidator = SafeOutputValidator(),
) {
    /** Public/frontend text never exposes the internal list-boundary marker. */
    fun normalize(input: String): String = normalizeDetailed(input).spokenText
        .replace(LIST_BOUNDARY.toString(), "")
        .replace(LIST_MARKER_BODY.toString(), "")
        .replace(LIST_START.toString(), "")

    fun normalizeDetailed(input: String): SpeechNormalization = try {
        val sanitized = sanitizer.sanitize(input.take(MAX_INPUT_CHARS).mapCircledDigits())
        val normalizedInput = sanitizer.filterForSpeech(sanitized, mode)
            .let(::normalizeListMarkers)
            .let(::normalizeNaturalCitationAndBracketPunctuation)
        if (normalizedInput.isEmpty()) return SpeechNormalization(input, sanitized, normalizedInput, "", emptyList())
        val spans = detector.detect(normalizedInput)
        val semantic = normalizeStageSpacing(replaceSpans(normalizedInput, spans, includeProsody = false))
        val prosody = normalizeStageSpacing(replaceSpans(normalizedInput, spans, includeProsody = true))
        val emoticonText = emoticons.normalize(prosody, mode)
        var spoken = emoticonText
        spoken = emoji.normalize(spoken)
        val emojiText = spoken
        spoken = loanwords.normalize(spoken)
        val loanwordText = spoken
        spoken = jamo.normalize(spoken)
        val jamoText = spoken
        spoken = english.normalize(spoken)
        val englishText = spoken
        spoken = symbols.normalize(spoken, mode)
        val symbolText = spoken
        spoken = repairCommonSpacing(spoken)
        spoken = spoken.replace(EMERGENCY_BOUNDARY, ' ')
        spoken = validator.validate(spoken)
        SpeechNormalization(
            input, sanitized, normalizedInput, spoken, spans,
            SpeechNormalizationStages(semantic, prosody, emoticonText, emojiText, loanwordText, jamoText, englishText, symbolText),
        )
    } catch (_: Throwable) {
        SpeechNormalization(input, "", "", "", emptyList())
    }

    private fun replaceSpans(text: String, spans: List<SpeechSpan>, includeProsody: Boolean): String = buildString(text.length) {
        var cursor = 0
        spans.forEach { span ->
            append(text, cursor, span.start)
            val spoken = if (includeProsody && span.type == SpeechSpanType.IDENTIFIER) {
                SemanticNormalizers.normalizeIdentifierWithProsody(span.source)
            } else span.spoken
            append(' ').append(spoken)
            if (span.type == SpeechSpanType.EMERGENCY_NUMBER &&
                text.substring(span.endExclusive).matches(Regex("^[에로].*"))) {
                append(EMERGENCY_BOUNDARY)
            } else {
                append(' ')
            }
            cursor = span.endExclusive
        }
        append(text, cursor, text.length)
    }

    private fun repairCommonSpacing(input: String): String = input
        .replace("할건데", "할 건데")
        .replace("호 차", "호차")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s+(에|로|은|는|을|를|와|과|만|부터|까지|입니다)(?=\\s|[,.!?]|$)"), "$1")
        .replace(Regex("\\s+([,.!?])"), "$1")
        .trim()

    private fun normalizeStageSpacing(input: String): String = input.replace(Regex("\\s+"), " ").trim()

    /** Preserve numbered-list structure so the existing sentence chunker creates an item boundary. */
    private fun normalizeListMarkers(input: String): String {
        val numeric = Regex("(?m)(^|\\s)(?:\\(([0-9]{1,2})\\)|([0-9]{1,2})[.)])(?=\\s|$)")
        val korean = Regex("(?m)(^|\\s)(?:\\(([가-힣])\\)|([가-힣])[.)])(?=\\s|$)")
        val alphabetic = Regex("(?m)(^|\\s)(?:\\(([A-Za-z])\\)|([A-Za-z])[.)])(?=\\s|$)")
        val ordinals = Regex(
            "(?m)(^|\\s)(첫째|둘째|셋째|넷째|다섯째|여섯째|일곱째|여덟째|아홉째|열째|" +
                "첫 번째|두 번째|세 번째|네 번째|다섯 번째|여섯 번째|일곱 번째|여덟 번째|아홉 번째|열 번째)" +
                "(?:\\s*[,.:])?(?=\\s|$)",
        )
        val bullets = Regex("(?m)(^|\\s)[-–—•▪◦○](?=\\s|$)")

        var output = input
        output = markListFamily(output, numeric) { match -> match.groupValues[2].ifEmpty { match.groupValues[3] } }
        output = markListFamily(output, korean) { match -> match.groupValues[2].ifEmpty { match.groupValues[3] } }
        output = markListFamily(output, alphabetic) { match -> match.groupValues[2].ifEmpty { match.groupValues[3] } }
        output = markListFamily(output, ordinals) { match -> match.groupValues[2] }
        output = markListFamily(output, bullets, spoken = false) { "" }
        return output
    }

    private fun markListFamily(
        input: String,
        pattern: Regex,
        spoken: Boolean = true,
        token: (MatchResult) -> String,
    ): String {
        if (pattern.findAll(input).count() < 2) return input
        val tagged = pattern.replace(input) { match ->
            val marker = token(match)
            "${match.groupValues[1]}$LIST_ITEM_PREFIX$marker${if (spoken) LIST_MARKER_BODY else ""}"
        }
        var first = true
        return tagged.replace(Regex("(\\s*)${LIST_ITEM_PREFIX}([^\\s$LIST_BOUNDARY$LIST_MARKER_BODY]*)")) { match ->
            val leadingSpace = match.groupValues[1]
            val marker = match.groupValues[2]
            if (first) {
                first = false
                if (leadingSpace.isNotEmpty()) "$LIST_START $marker" else marker
            } else {
                "$LIST_BOUNDARY$marker"
            }
        }
    }

    private fun String.mapCircledDigits(): String = buildString(length) {
        for (char in this@mapCircledDigits) {
            val number = when (char) {
                '①' -> 1; '②' -> 2; '③' -> 3; '④' -> 4; '⑤' -> 5
                '⑥' -> 6; '⑦' -> 7; '⑧' -> 8; '⑨' -> 9; '⑩' -> 10
                '⑪' -> 11; '⑫' -> 12; '⑬' -> 13; '⑭' -> 14; '⑮' -> 15
                '⑯' -> 16; '⑰' -> 17; '⑱' -> 18; '⑲' -> 19; '⑳' -> 20
                else -> null
            }
            if (number == null) append(char) else append(number).append('.')
        }
    }

    /** Keep all bracketed/quoted content while making punctuation silent in natural speech. */
    private fun normalizeNaturalCitationAndBracketPunctuation(input: String): String = input
        .replace(Regex("[\\[\\]()]"), " ")
        .replace(Regex("[‘’“”\\\"']"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        const val MAX_INPUT_CHARS = 100_000
        const val EMERGENCY_BOUNDARY = '\uE000'
        /** Internal marker retained until chunking; never sent to the frontend. */
        const val LIST_BOUNDARY = '\uE001'
        const val LIST_MARKER_BODY = '\uE003'
        const val LIST_START = '\uE004'
        private const val LIST_ITEM_PREFIX = '\uE002'

        fun production(cmuDictionary: InputStream, loanwordLexicon: InputStream): KoreanSpeechNormalizer =
            KoreanSpeechNormalizer(
                loanwords = LoanwordNormalizer.fromTsv(loanwordLexicon),
                english = EnglishNormalizer.fromCmuDictionary(cmuDictionary),
            )
    }
}
