package com.yiwoosolution.koreantts.speech

enum class SpeechMode { NATURAL, INFORMATIVE, LITERAL }

enum class SpeechSpanType {
    URL, EMAIL, IPV4, IPV6, PHONE, EMERGENCY_NUMBER, IDENTIFIER, DATE, VERSION, TIME, RATIO, SCORE, MEETING_RATIO,
    FRACTION, QUARTER, DURATION_RANGE, MEASUREMENT_RANGE, LABEL_GROUP, CONNECTOR, WIFI_GENERATION, CURRENCY, MEASUREMENT, PERCENT, DECIMAL, SIGNED_NUMBER, MATH, TIME_RANGE, ELLIPSIS, NUMBER, UNKNOWN,
}

data class SpeechSpan(
    val start: Int,
    val endExclusive: Int,
    val source: String,
    val type: SpeechSpanType,
    val spoken: String,
    val reason: String,
    val score: Int = 0,
    val prosodyBoundaries: List<SpeechProsodyBoundary> = emptyList(),
)

data class SpeechProsodyBoundary(
    val sourceOffset: Int,
    val reason: String,
    val targetMillis: Int,
)

data class SpeechNormalization(
    val raw: String,
    val sanitized: String,
    val normalizedInput: String,
    val spokenText: String,
    val spans: List<SpeechSpan>,
    val stages: SpeechNormalizationStages? = null,
) {
    val isEmpty: Boolean get() = spokenText.isBlank()
}

data class SpeechNormalizationStages(
    val semantic: String,
    val prosody: String,
    val emoticon: String,
    val emoji: String,
    val loanword: String,
    val jamo: String,
    val english: String,
    val symbol: String,
)
