package com.yiwoosolution.koreantts.speech

/** Consume existing semantic spans before the Latin pronunciation fallback. */
object KoreanStructuredText {
    private val detector by lazy { SpeechPatternDetector() }
    fun normalizeMath(raw: String): String {
        val input = normalizeCalendar(raw)
        if (input.none { it in "²³⁻⁰¹⁴⁵⁶⁷⁸⁹^+-−×÷*=<>≤≥≠≈∧∨¬&|!√±" }) return input
        val spans = detector.detect(input).filter { it.type == SpeechSpanType.MATH }
        if (spans.isEmpty()) return input
        return buildString {
            var cursor = 0
            for (span in spans) {
                append(input.substring(cursor, span.start))
                append(span.spoken)
                cursor = span.endExclusive
            }
            append(input.substring(cursor))
        }
    }

    private fun normalizeCalendar(input: String): String = buildString {
        var cursor = 0
        // Dates and weekdays inside an address are identifiers, not calendar text.
        val ratios = setOf(SpeechSpanType.RATIO, SpeechSpanType.SCORE, SpeechSpanType.MEETING_RATIO)
        val calendarUnits = setOf(SpeechSpanType.DURATION_RANGE, SpeechSpanType.MEASUREMENT_RANGE, SpeechSpanType.QUARTER)
        for (span in detector.detect(input).filter { it.type in setOf(SpeechSpanType.URL, SpeechSpanType.EMAIL, SpeechSpanType.IPV4, SpeechSpanType.IPV6, SpeechSpanType.VERSION) || it.type in calendarUnits || (it.type in ratios && it.reason != "invalid-clock:4") }) {
            append(KoreanCalendarText.normalize(input.substring(cursor, span.start)))
            append(if (span.type in ratios || span.type in calendarUnits) span.spoken else span.source)
            cursor = span.endExclusive
        }
        append(KoreanCalendarText.normalize(input.substring(cursor)))
    }
    private val mixed = Regex("(?<![A-Za-z0-9])(?=[A-Za-z0-9-]*[A-Za-z])(?=[A-Za-z0-9-]*[0-9])[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*(?![A-Za-z0-9])")

    fun normalize(input: String): String {
        val chat = input.replace(Regex("(?<![A-Za-z0-9])(?:o_o|O_O|o.O|O_o|o\\s+o|O\\s+O)(?![A-Za-z0-9])"), " 당황한 표정 ")
        val spans = detector.detect(chat)
        return buildString {
            var cursor = 0
            for (span in spans) {
                if (span.type !in setOf(SpeechSpanType.MEASUREMENT, SpeechSpanType.IDENTIFIER,
                        SpeechSpanType.IPV4, SpeechSpanType.IPV6, SpeechSpanType.URL, SpeechSpanType.EMAIL, SpeechSpanType.VERSION, SpeechSpanType.QUARTER, SpeechSpanType.DURATION_RANGE,
                        SpeechSpanType.MEASUREMENT_RANGE, SpeechSpanType.LABEL_GROUP, SpeechSpanType.CONNECTOR, SpeechSpanType.WIFI_GENERATION)) continue
                append(normalizeMixed(chat.substring(cursor, span.start)))
                append(when (span.type) {
                    SpeechSpanType.MEASUREMENT, SpeechSpanType.IDENTIFIER, SpeechSpanType.IPV4, SpeechSpanType.IPV6 -> span.spoken
                    SpeechSpanType.URL -> KoreanUrlNormalizer.normalizeUrl(span.source)
                    SpeechSpanType.EMAIL -> KoreanUrlNormalizer.normalizeEmail(span.source)
                    else -> span.spoken
                })
                cursor = span.endExclusive
            }
            append(normalizeMixed(chat.substring(cursor)))
        }
    }

    private fun normalizeMixed(input: String) = mixed.replace(input) {
        SemanticNormalizers.normalizeIdentifier(it.value)
    }
}
