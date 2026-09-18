package com.yiwoosolution.koreantts.speech

class ContextClassifier {
    data class Classification(val type: SpeechSpanType, val score: Int, val reason: String)

    private val time = setOf("오전", "오후", "새벽", "아침", "점심", "저녁", "밤", "오늘", "내일", "어제", "시간", "시각", "알람", "예약", "출발", "도착", "회의", "약속", "시작", "종료")
    private val ratio = setOf("비율", "비례", "배합", "혼합", "섞다", "섞어", "희석", "나누다", "분할", "가로", "세로", "화면비")
    private val meeting = setOf("미팅", "소개팅", "면접", "대결", "매치", "팀")
    private val score = setOf("경기", "점수", "스코어", "승리", "패배", "무승부", "비겼다", "이겼다", "졌다", "득점", "전반", "후반", "세트")

    fun classifyColon(text: String, start: Int, end: Int, left: Int, right: Int): Classification {
        val before = text.substring(maxOf(0, start - 24), start)
        val after = text.substring(end, minOf(text.length, end + 24))
        // A cue attached to this value outranks words in the next clause.
        if (DIRECT_TIME.containsMatchIn(before) && left in 0..23 && right in 0..59)
            return Classification(SpeechSpanType.TIME, 100, "direct-time-context")
        if (DIRECT_SCORE.containsMatchIn(before))
            return Classification(SpeechSpanType.SCORE, 100, "direct-score-context")
        if (DIRECT_RATIO.containsMatchIn(before))
            return Classification(SpeechSpanType.RATIO, 100, "direct-ratio-context")
        val scores = mutableMapOf(
            SpeechSpanType.TIME to 0,
            SpeechSpanType.RATIO to 0,
            SpeechSpanType.MEETING_RATIO to 0,
            SpeechSpanType.SCORE to 0,
        )
        val reasons = mutableListOf<String>()
        fun add(type: SpeechSpanType, cue: String, weight: Int) {
            scores[type] = scores.getValue(type) + weight
            reasons += "$cue:$weight"
        }
        listOf("에", "부터", "까지").firstOrNull { after.startsWith(it) }?.let { add(SpeechSpanType.TIME, "suffix=$it", 5) }
        scoreCues(before, after, time) { cue, weight -> add(SpeechSpanType.TIME, cue, weight) }
        scoreCues(before, after, ratio) { cue, weight -> add(SpeechSpanType.RATIO, cue, weight) }
        scoreCues(before, after, meeting) { cue, weight -> add(SpeechSpanType.MEETING_RATIO, cue, weight) }
        scoreCues(before, after, score) { cue, weight -> add(SpeechSpanType.SCORE, cue, weight) }
        if (left > 12 || right > 59) add(SpeechSpanType.RATIO, "invalid-clock", 4)
        val best = scores.maxBy { it.value }
        return if (best.value <= 0) Classification(SpeechSpanType.UNKNOWN, 0, "no-context")
        else Classification(best.key, best.value, reasons.joinToString(","))
    }

    fun hasDateContext(text: String, start: Int, end: Int): Boolean {
        val window = text.substring(maxOf(0, start - 16), minOf(text.length, end + 16))
        // Bare M/D is ambiguous. Only explicit date nouns are strong enough to beat FRACTION;
        // broad temporal words such as "오늘" also occur naturally in mathematical sentences.
        return listOf("날짜", "생일", "일정").any(window::contains)
    }

    fun isRepetitionCounter(text: String, end: Int): Boolean {
        val after = text.substring(end, minOf(text.length, end + 20)).trimStart()
        return REPETITION_CUES.any(after::startsWith)
    }

    fun classifyIdentifier(text: String, start: Int, end: Int, value: String): Classification? {
        val before = text.substring(maxOf(0, start - 24), start)
        val after = text.substring(end, minOf(text.length, end + 24))
        if (value in setOf("112", "119") &&
            (EMERGENCY_PREFIX.containsMatchIn(before) || EMERGENCY_SUFFIX.containsMatchIn(after))) {
            return Classification(SpeechSpanType.EMERGENCY_NUMBER, 100, "emergency-number-context")
        }
        if (isUnambiguousStandaloneCode(value)) {
            return Classification(SpeechSpanType.IDENTIFIER, 90, "standalone-structured-code")
        }
        val alias = identifierAliasBefore(before) ?: return null
        if (alias.contextual && !isCodeLike(value)) return null
        return Classification(
            SpeechSpanType.IDENTIFIER,
            if (alias.contextual) 95 else 100,
            "identifier-context:${alias.name}",
        )
    }

    private fun identifierAliasBefore(before: String): IdentifierAlias? {
        STRONG_IDENTIFIER_ALIASES.firstOrNull { it.pattern.containsMatchIn(before) }?.let { return it }
        return CONTEXTUAL_IDENTIFIER_ALIASES.firstOrNull { it.pattern.containsMatchIn(before) }
    }

    private fun isCodeLike(value: String): Boolean {
        val compact = value.filter(Char::isLetterOrDigit)
        return compact.any(Char::isDigit) && compact.length >= 2
    }

    private fun isUnambiguousStandaloneCode(value: String): Boolean {
        if (STANDALONE_MEASUREMENT.matches(value)) return false
        val transport = Regex("(?i)([A-Za-z]{2}|[0-9][A-Za-z]|KTX|SRT|ITX)[ -]?(\\d{1,4})").matchEntire(value)
        if (transport != null && transport.groupValues[1].uppercase() in TRANSPORT_DESIGNATORS) return true
        val compact = value.filter(Char::isLetterOrDigit)
        if (compact.length < 3 || compact.none(Char::isDigit) || compact.none(Char::isLetter)) return false
        val hasSeparator = value.any { it in "-_/" }
        val digitRuns = Regex("\\d+").findAll(value).map(MatchResult::value).toList()
        val latinCount = value.count { it in 'A'..'Z' || it in 'a'..'z' }
        return hasSeparator || digitRuns.any { it.length >= 2 && it.startsWith('0') } ||
            latinCount == 1 && digitRuns.any { it.length >= 3 }
    }

    private data class IdentifierAlias(val name: String, val pattern: Regex, val contextual: Boolean = false)

    private companion object {
        val DIRECT_TIME = Regex("(?:시간|시각|오전|오후|새벽|아침|저녁|밤|알람)(?:은|는|이|가)?\\s*$")
        val DIRECT_SCORE = Regex("(?:경기|점수|스코어)(?:은|는|이|가)?\\s*$")
        val DIRECT_RATIO = Regex("(?:비율|화면비|배합비)(?:은|는|이|가)?\\s*$")
        fun alias(name: String, expression: String, contextual: Boolean = false) =
            IdentifierAlias(name, Regex("(?i)(?:$expression)(?:은|는|이|가)?\\s*(?::\\s*)?$"), contextual)

        val STRONG_IDENTIFIER_ALIASES = listOf(
            alias("serial", "시리얼\\s*(?:번호|넘버)|serial\\s+(?:number|no\\.?)"),
            alias("part", "(?:파트|부품)\\s*(?:번호|넘버)|part\\s+(?:number|no\\.?)"),
            alias("model", "모델\\s*(?:번호|넘버)|model\\s+(?:number|no\\.?)"),
            alias("product", "(?:제품|상품)\\s*(?:번호|코드)|코드\\s*번호|product\\s+(?:number|code)|SKU"),
            alias("structured-code", "좌석|게이트|주차\\s*구역|오류\\s*코드"),
            alias("order", "주문\\s*번호|order\\s+(?:number|no\\.?|ID)"),
            alias("shipping", "(?:송장|운송장)\\s*번호|tracking\\s+(?:number|no\\.?|ID)|waybill"),
            alias("reservation", "(?:예약|예매)\\s*번호|(?:reservation|booking)\\s+number"),
            alias("security", "비밀번호|password|passcode|(?:핀|PIN)\\s*번호|pin\\s+number|인증\\s*(?:번호|코드)|security\\s+code"),
            alias("person-org", "학번|사번|회원\\s*번호|고객\\s*번호"),
            alias("financial", "계좌\\s*번호|카드\\s*번호"),
            alias("government", "여권\\s*번호|사업자\\s*등록\\s*번호|주민\\s*등록\\s*번호|운전\\s*면허\\s*번호|우편\\s*번호"),
            alias("vehicle", "차량\\s*번호"),
            alias("device", "device\\s+ID"),
        )
        val CONTEXTUAL_IDENTIFIER_ALIASES = listOf(
            alias("serial-short", "S/N|SN", contextual = true),
            alias("part-short", "P/N|PN", contextual = true),
            alias("security-short", "PIN|OTP", contextual = true),
            alias("device-short", "IMEI|IMSI|ICCID", contextual = true),
        )

        val EMERGENCY_PREFIX = Regex("긴급\\s*전화(?:번호)?(?:은|는|이|가|:)?\\s*$")
        val EMERGENCY_SUFFIX = Regex("^\\s*(?:에(?:\\s*(?:전화|신고))?|로)")
        val REPETITION_CUES = listOf("눌러", "누르", "반복", "복용")
        val STANDALONE_MEASUREMENT = Regex(
            "(?i)\\d+(?:GHz|GB|MB|kcal|kg|mg|km(?:/h)?|cm|mm|mL|ml|cc|Hz|°C|g|m|L|V|W)",
        )
        val TRANSPORT_DESIGNATORS = setOf(
            "KE", "OZ", "7C", "TW", "LJ", "BX", "ZE", "AA", "DL", "UA", "BA",
            "LH", "AF", "SQ", "EK", "QR", "JL", "NH", "CX", "QF", "KTX", "SRT", "ITX",
        )
    }

    private fun scoreCues(before: String, after: String, cues: Set<String>, add: (String, Int) -> Unit) {
        cues.forEach { cue ->
            val bd = before.lastIndexOf(cue).let { if (it < 0) Int.MAX_VALUE else before.length - it - cue.length }
            val ad = after.indexOf(cue).let { if (it < 0) Int.MAX_VALUE else it }
            val distance = minOf(bd, ad)
            val weight = when {
                distance == 0 -> 5
                distance <= 4 -> 3
                distance <= 12 -> 2
                distance < Int.MAX_VALUE -> 1
                else -> 0
            }
            if (weight > 0) add("cue=$cue", weight)
        }
    }

}
