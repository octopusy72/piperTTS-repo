package com.yiwoosolution.koreantts.speech

class SpeechPatternDetector(private val classifier: ContextClassifier = ContextClassifier()) {
    private data class Rule(val type: SpeechSpanType, val regex: Regex, val priority: Int)
    private val mathAtom = "(?:[A-Za-z]|\\d+(?:\\.\\d+)?)"
    private val mathTerm = "$mathAtom(?:[⁻⁰¹²³⁴⁵⁶⁷⁸⁹]+|\\s*\\^\\s*(?:\\([+\\-]?\\d+\\)|[+\\-]?\\d+))?"

    private val rules = listOf(
        Rule(SpeechSpanType.DURATION_RANGE, Regex("(?<![A-Za-z0-9./:-])(\\d+)\\s*[-~∼–—]\\s*(\\d+)\\s*(개월|시간|분|초|일|주|년)(?=\\s|[.,!?]|$|간|동안|뒤|후|전|이|에|을|로|은|는)"), 98),
        Rule(SpeechSpanType.MEASUREMENT_RANGE, Regex("(?<![A-Za-z0-9./:-])([+\\-]?\\d[\\d,]*(?:\\.\\d+)?)\\s*[-~∼–—]\\s*([+\\-]?\\d[\\d,]*(?:\\.\\d+)?)\\s*(km/h|m/s|GHz|GB|MB|kcal|kg|mg|km|cm|mm|mL|ml|cc|Hz|°\\s*C|°\\s*F|℃|℉|g|m|L|V|A|W|%)(?![A-Za-z0-9])"), 99),
        Rule(SpeechSpanType.LABEL_GROUP, Regex("(?<![A-Za-z0-9/.:_-])[A-Z](?:\\s*/\\s*[A-Z])+(?![A-Za-z0-9/._-])"), 95),
        Rule(SpeechSpanType.CONNECTOR, Regex("(?i)(?<![A-Za-z0-9-])USB-C(?![A-Za-z0-9-])"), 99),
        Rule(SpeechSpanType.WIFI_GENERATION, Regex("(?i)(?<![A-Za-z0-9가-힣-])(?:Wi[-‐‑–]?Fi|와이파이)\\s*(?:6E|[4-7])(?![A-Za-z0-9-]|\\.\\d)"), 99),
        Rule(SpeechSpanType.QUARTER, Regex("(?<![A-Za-z0-9/])([1-4])\\s*/\\s*4\\s*분기"), 98),
        // Whole expressions outrank constituent powers/numbers, but not URLs/email.
        Rule(SpeechSpanType.MATH, Regex("(?<![A-Za-z0-9./-])$mathTerm(?:\\s*(?:<=|>=|!=|==|&&|\\|\\||[+×÷*=<>≤≥≠≈∧∨−]|\\s-\\s)\\s*$mathTerm)+(?![A-Za-z0-9/])"), 97),
        Rule(SpeechSpanType.MATH, Regex("(?<![A-Za-z0-9])(?:¬|!)\\s*$mathAtom(?![A-Za-z0-9])"), 97),
        Rule(SpeechSpanType.EMAIL, Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), 105),
        Rule(SpeechSpanType.URL, Regex("(?i)https?://[^\\s]+"), 100),
        Rule(SpeechSpanType.URL, Regex("(?i)(?<![.@A-Za-z0-9])(?:www\\.)?(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:/[a-z0-9._~!$&'()*+,;=:@%-]*)?(?:\\?[a-z0-9._~!$&'()*+,;=:@%/?-]*)?"), 100),
        Rule(SpeechSpanType.IPV4, Regex("(?<![\\d.])\\d{1,3}(?:\\.\\d{1,3}){3}(?![\\d.])"), 92),
        Rule(SpeechSpanType.IPV6, Regex("(?i)(?<![A-Za-z0-9:.])[0-9a-f:]*:[0-9a-f:]+(?![A-Za-z0-9:.])"), 93),
        Rule(SpeechSpanType.PHONE, Regex("(?<![\\d-])(?:0\\d{1,2}-\\d{3,4}-\\d{4}|1\\d{3}-\\d{4})(?!-\\d)"), 90),
        Rule(SpeechSpanType.IDENTIFIER, Regex("(?<=좌석 )[A-Za-z0-9]+(?=[\\s,.!?]|$)"), 98),
        Rule(SpeechSpanType.IDENTIFIER, Regex("(?<=게이트 )[A-Za-z0-9]+(?=[\\s,.!?]|$)"), 98),
        Rule(SpeechSpanType.IDENTIFIER, Regex("(?<=주차구역 )[A-Za-z0-9]+(?=[\\s,.!?]|$)"), 98),
        Rule(SpeechSpanType.IDENTIFIER, Regex("(?<=오류코드 )[A-Za-z0-9]+(?=[\\s,.!?]|$)"), 98),
        Rule(SpeechSpanType.IDENTIFIER, Regex("(?i)(?:KE|OZ|7C|TW|LJ|BX|ZE|AA|DL|UA|BA|LH|AF|SQ|EK|QR|JL|NH|CX|QF|KTX|SRT|ITX)[ -]?\\d{1,4}"), 91),
        Rule(SpeechSpanType.IDENTIFIER, Regex("(?<![가-힣0-9])(?:[가-힣]{2})?\\d{2,3}[가-힣]\\d{4}(?![가-힣0-9])"), 89),
        Rule(SpeechSpanType.IDENTIFIER, Regex("(?<![A-Za-z0-9])(?=[A-Za-z0-9_-]*\\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9]+(?:[-_/][A-Za-z0-9]+)*(?![A-Za-z0-9])"), 89),
        Rule(SpeechSpanType.IDENTIFIER, Regex("(?<!\\d)\\d+(?:[-_/ ]\\d+)+(?!\\d)"), 89),
        Rule(SpeechSpanType.IDENTIFIER, Regex("(?<!\\d)\\d+(?!\\d)"), 89),
        Rule(SpeechSpanType.DATE, Regex("\\d{4}\\s*년\\s*\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}|\\d{1,2}[-./]\\d{1,2}[-./]\\d{4}"), 86),
        Rule(SpeechSpanType.VERSION, Regex("(?i)(?<![A-Za-z0-9.])(?:(?:버전(?:은|는|이|가)?|version)\\s*v?\\d+(?:\\.\\d+)+|v\\d+(?:\\.\\d+)+)(?![A-Za-z0-9]|\\.\\d)"), 99),
        Rule(SpeechSpanType.CURRENCY, Regex("(?:₩|\\$|€|£|¥|￥|₹|₽|₺)\\s*\\d[\\d,]*(?:\\.\\d+)?|(?i:USD|EUR|GBP|JPY|CNY|INR|RUB|TRY)\\s*\\d[\\d,]*(?:\\.\\d+)?|\\d[\\d,]*(?:\\.\\d+)?\\s*원"), 80),
        Rule(SpeechSpanType.MEASUREMENT, Regex("(?<![A-Za-z0-9])[+\\-]?\\d[\\d,]*(?:\\.\\d+)?\\s*(?:(?i:km\\s*/\\s*h|m\\s*/\\s*s|°\\s*C|°\\s*F)|GHz|GB|MB|kcal|Kcal|kg|mg|km|cm|mm|mL|ml|cc|Hz|℃|℉|도씨|g|m|L|l|V|A|W)(?![A-Za-z0-9])"), 96),
        Rule(SpeechSpanType.PERCENT, Regex("\\d[\\d,]*(?:\\.\\d+)?%"), 76),
        Rule(SpeechSpanType.SIGNED_NUMBER, Regex("(?<![A-Za-z0-9])[+\\-]\\d+(?:\\.\\d+)?(?![A-Za-z0-9])"), 95),
        Rule(SpeechSpanType.MATH, Regex("(?<![A-Za-z0-9])(?:[A-Za-z]|\\d+(?:\\.\\d+)?)(?:\\s*(?:≤|≥|≠|≈|<|>)\\s*)(?:[A-Za-z]|[+\\-]?\\d+(?:\\.\\d+)?)(?![A-Za-z0-9])"), 78),
        Rule(SpeechSpanType.MATH, Regex("(?<![A-Za-z0-9])±\\s*[+\\-]?\\d+(?:\\.\\d+)?(?![A-Za-z0-9])"), 78),
        Rule(SpeechSpanType.MATH, Regex("(?<![A-Za-z0-9])√\\s*[A-Za-z0-9]+(?![A-Za-z0-9])"), 78),
        Rule(SpeechSpanType.MATH, Regex("(?<![A-Za-z0-9])[+\\-]?\\d+\\s*\\^\\s*\\(?\\s*[+\\-]?\\d+\\s*\\)?(?![A-Za-z0-9])"), 97),
        Rule(SpeechSpanType.MATH, Regex("(?<![A-Za-z0-9])(?:[A-Za-z0-9]+(?:[²³⁻⁰¹²³⁴⁵⁶⁷⁸⁹]+|\\s*\\^\\s*[+\\-]?\\d+))(?![A-Za-z0-9])"), 78),
        Rule(SpeechSpanType.MATH, Regex("(?<![A-Za-z0-9])(?:\\d+\\s*[+\\-]\\s*)+\\d+\\s*[xX×*]\\s*\\d+(?![A-Za-z0-9])"), 78),
        Rule(SpeechSpanType.MATH, Regex("(?<![A-Za-z0-9])\\d+\\s*[xX×*]\\s*\\d+(?![A-Za-z0-9])"), 78),
        Rule(SpeechSpanType.MATH, Regex("\\d+(?:\\s*[+\\-×÷]\\s*\\d+)+(?:\\s*=\\s*\\d+)?"), 74),
        Rule(SpeechSpanType.TIME_RANGE, Regex("\\d{1,2}:\\d{2}\\s*[~∼–—-]\\s*\\d{1,2}:\\d{2}"), 86),
        Rule(SpeechSpanType.TIME, Regex("\\d{1,2}\\s*시(?:\\s*\\d{1,2}\\s*분)?|\\d{1,2}:\\d{1,2}"), 70),
        Rule(SpeechSpanType.FRACTION, Regex("\\d+/\\d+"), 68),
        // Claim the complete span before the generic numeric identifier can consume 987.
        Rule(SpeechSpanType.DECIMAL, Regex("(?<![A-Za-z0-9.])\\.\\d+(?![A-Za-z0-9.])"), 96),
        Rule(SpeechSpanType.DECIMAL, Regex("\\d+\\.\\d+"), 60),
        Rule(SpeechSpanType.ELLIPSIS, Regex("(?:\\.{2,}|…+)"), 50),
        Rule(SpeechSpanType.NUMBER, Regex("\\d[\\d,]*\\s*(?:개월|시|분|개|명|살|병|번|호|가지|일)?"), 10),
    )

    fun detect(text: String): List<SpeechSpan> {
        val candidates = rules.flatMap { rule ->
            rule.regex.findAll(text).map { match -> Candidate(rule, match) }.toList()
        }.sortedWith(compareByDescending<Candidate> { it.rule.priority }.thenBy { it.match.range.first })
        val occupied = BooleanArray(text.length)
        val selected = mutableListOf<SpeechSpan>()
        candidates.forEach { candidate ->
            val start = candidate.match.range.first
            val end = candidate.match.range.last + 1
            if ((start until end).any { occupied[it] }) return@forEach
            val span = createSpan(text, candidate.rule.type, candidate.match, start, end) ?: return@forEach
            selected += span
            (start until end).forEach { occupied[it] = true }
        }
        return selected.sortedBy(SpeechSpan::start)
    }

    private fun createSpan(text: String, initial: SpeechSpanType, match: MatchResult, start: Int, end: Int): SpeechSpan? {
        val source = match.value
        // S/N is an existing serial-number prefix, not a choice of two labels.
        if (initial == SpeechSpanType.LABEL_GROUP && source.replace(" ", "") == "S/N" &&
            text.substring(end).trimStart(' ', ':').firstOrNull()?.isDigit() == true) return null
        var type = initial
        var score = 100
        var reason = "pattern:$initial"
        if (initial == SpeechSpanType.IDENTIFIER) {
            val result = classifier.classifyIdentifier(text, start, end, source) ?: return null
            type = result.type
            score = result.score
            reason = result.reason
        } else if (initial == SpeechSpanType.IPV6 && !validIpv6(source)) {
            return null
        } else if (initial == SpeechSpanType.IPV4 && source.split('.').any { it.toInt() !in 0..255 }) {
            return null
        } else if (initial == SpeechSpanType.TIME && source.contains(':')) {
            val parts = source.split(':').map(String::toInt)
            val result = classifier.classifyColon(text, start, end, parts[0], parts[1])
            type = result.type
            score = result.score
            reason = result.reason
        } else if (initial == SpeechSpanType.FRACTION &&
            (classifier.hasDateContext(text, start, end) || text.substring(end).startsWith("에"))) {
            type = SpeechSpanType.DATE
            reason = "date-context"
        }
        val spoken = if (type == SpeechSpanType.NUMBER && source.trimEnd().endsWith("번")) {
            SemanticNormalizers.normalizeNumber(source, classifier.isRepetitionCounter(text, end))
        } else if (type == SpeechSpanType.MEASUREMENT) {
            SemanticNormalizers.normalizeMeasurement(source, text.substring(0, start))
        } else SemanticNormalizers.normalize(type, source)
        val boundaries = if (type == SpeechSpanType.IDENTIFIER) SemanticNormalizers.identifierBoundaries(source) else emptyList()
        return SpeechSpan(start, end, source, type, spoken, reason, score, boundaries)
    }

    private data class Candidate(val rule: Rule, val match: MatchResult)

    private fun validIpv6(source: String): Boolean {
        if (source.contains(":::")) return false
        val compressed = source.contains("::")
        if (compressed && source.indexOf("::") != source.lastIndexOf("::")) return false
        if (!compressed && (source.startsWith(':') || source.endsWith(':'))) return false
        val groups = source.split(':').filter(String::isNotEmpty)
        return groups.all { it.length in 1..4 } && if (compressed) groups.size < 8 else groups.size == 8
    }
}
