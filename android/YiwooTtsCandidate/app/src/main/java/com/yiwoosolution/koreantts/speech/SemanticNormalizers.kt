package com.yiwoosolution.koreantts.speech

object SemanticNormalizers {
    const val IDENTIFIER_BOUNDARY_MS = 100
    private val AIRLINE_DESIGNATORS = setOf(
        "KE", "OZ", "7C", "TW", "LJ", "BX", "ZE", "AA", "DL", "UA", "BA",
        "LH", "AF", "SQ", "EK", "QR", "JL", "NH", "CX", "QF",
    )
    private val FLIGHT_PATTERN = Regex("(?i)([A-Za-z]{2}|[0-9][A-Za-z])[ -]?(\\d{1,4})")
    private val TRAIN_PATTERN = Regex("(?i)(KTX|SRT|ITX)[ -]?(\\d{1,4})")
    private val TRAIN_NAMES = mapOf("KTX" to "케이티엑스", "SRT" to "에스알티", "ITX" to "아이티엑스")
    private val MEASUREMENT_PATTERN = Regex(
        "([+\\-]?\\d[\\d,]*(?:\\.\\d+)?)\\s*(km\\s*/\\s*h|m\\s*/\\s*s|GHz|GB|MB|kcal|Kcal|kg|mg|km|cm|mm|mL|ml|cc|Hz|°\\s*C|°\\s*F|℃|℉|도씨|g|m|L|l|V|A|W)",
        RegexOption.IGNORE_CASE,
    )
    private val units = mapOf(
        "ghz" to "기가헤르츠", "gb" to "기가바이트", "mb" to "메가바이트",
        "kg" to "킬로그램", "mg" to "밀리그램", "km" to "킬로미터",
        "cm" to "센티미터", "mm" to "밀리미터", "ml" to "밀리리터",
        "kcal" to "킬로칼로리", "cc" to "씨씨", "g" to "그램", "m" to "미터",
        "l" to "리터", "v" to "볼트", "a" to "암페어", "w" to "와트", "hz" to "헤르츠",
        "℃" to "도", "°c" to "도",
    )

    fun normalize(type: SpeechSpanType, source: String): String = when (type) {
        SpeechSpanType.URL -> KoreanUrlNormalizer.normalizeUrl(source)
        SpeechSpanType.EMAIL -> KoreanUrlNormalizer.normalizeEmail(source)
        SpeechSpanType.IPV4 -> readIpv4(source)
        SpeechSpanType.IPV6 -> source.split(':').joinToString(" 콜론 ") { part ->
            part.map { ch -> if (ch in '0'..'9') NumberReader.digits(ch.toString(), zeroAsGong = true) else EnglishReader.readToken(ch.toString()) }.joinToString(" ")
        }.replace(Regex(" +"), " ").trim()
        SpeechSpanType.PHONE -> readPhone(source)
        SpeechSpanType.EMERGENCY_NUMBER -> NumberReader.digits(source)
        SpeechSpanType.IDENTIFIER -> normalizeIdentifier(source)
        SpeechSpanType.DATE -> readDate(source)
        SpeechSpanType.VERSION -> readVersion(source)
        SpeechSpanType.TIME -> readTime(source)
        SpeechSpanType.TIME_RANGE -> readTimeRange(source)
        SpeechSpanType.RATIO, SpeechSpanType.SCORE, SpeechSpanType.MEETING_RATIO -> readColon(source, "대")
        SpeechSpanType.UNKNOWN -> readColon(source, "콜론")
        SpeechSpanType.FRACTION -> readFraction(source)
        SpeechSpanType.QUARTER -> "${NumberReader.sino(source.first { it.isDigit() }.digitToInt().toLong())}사분기"
        SpeechSpanType.DURATION_RANGE -> {
            val numbers = Regex("\\d+").findAll(source).map { NumberReader.decimal(it.value) }.toList()
            val unit = Regex("개월|시간|분|초|일|주|년").find(source)!!.value
            "${numbers[0]} ${unit}에서 ${numbers[1]} $unit"
        }
        SpeechSpanType.MEASUREMENT_RANGE -> {
            val match = Regex("([+\\-]?\\d[\\d,]*(?:\\.\\d+)?)\\s*[-~∼–—]\\s*([+\\-]?\\d[\\d,]*(?:\\.\\d+)?)\\s*(.+)").matchEntire(source)!!
            val unit = match.groupValues[3]
            fun endpoint(number: String) = if (unit == "%") readPercent(number + unit) else readMeasurement(number + unit)
            "${endpoint(match.groupValues[1])}에서 ${endpoint(match.groupValues[2])}"
        }
        SpeechSpanType.LABEL_GROUP -> source.split('/').joinToString(" ") { EnglishReader.readToken(it.trim()) }
        SpeechSpanType.CONNECTOR -> "유에스비 씨 타입"
        SpeechSpanType.WIFI_GENERATION -> "와이파이 " + when (source.last()) {
            '4' -> "포"; '5' -> "파이브"; '6' -> "식스"; '7' -> "세븐"; else -> "식스 이"
        }
        SpeechSpanType.CURRENCY -> readCurrency(source)
        SpeechSpanType.MEASUREMENT -> readMeasurement(source)
        SpeechSpanType.PERCENT -> readPercent(source)
        SpeechSpanType.DECIMAL -> NumberReader.decimal(source)
        SpeechSpanType.SIGNED_NUMBER -> readSigned(source)
        SpeechSpanType.ELLIPSIS -> if (source == "..") "점점" else "점점점"
        SpeechSpanType.MATH -> readMath(source)
        SpeechSpanType.NUMBER -> readNumberWithCounter(source)
    }

    private fun readTime(source: String): String {
        val values = Regex("\\d+").findAll(source).map { it.value.toInt() }.toList()
        if (values.isEmpty()) return source
        val hour = values[0]
        val minute = values.getOrElse(1) { 0 }
        val hourText = if (hour in 1..12) NumberReader.native(hour) else NumberReader.sino(hour.toLong())
        return if (minute == 0) "$hourText 시" else "$hourText 시 ${NumberReader.sino(minute.toLong())} 분"
    }

    private fun readTimeRange(source: String): String {
        val parts = Regex("\\d{1,2}:\\d{2}").findAll(source).map { it.value }.toList()
        return if (parts.size == 2) "${readTime(parts[0])}에서 ${readTime(parts[1])}" else source
    }

    private fun readPhone(source: String): String = source.split('-').joinToString(", ") { group ->
        NumberReader.digits(group, zeroAsGong = true).replace(" ", "")
    }

    fun normalizeIdentifier(source: String): String = normalizeIdentifier(source, includeClassBoundaries = false)

    fun normalizeIdentifierWithProsody(source: String): String = normalizeIdentifier(source, includeClassBoundaries = true)

    private fun normalizeIdentifier(source: String, includeClassBoundaries: Boolean): String {
        if (source.equals("IPv4", true)) return "아이피 브이 포"
        if (source.equals("IPv6", true)) return "아이피 브이 식스"
        flightMatch(source)?.let { (code, number) ->
            return "${readFlightCode(code).replace(" ", "")} ${NumberReader.digits(number, zeroAsGong = true).replace(" ", "")}"
        }
        trainMatch(source)?.let { (code, number) ->
            val name = TRAIN_NAMES[code.uppercase()] ?: code
            return "$name ${NumberReader.sino(number.toLong())}"
        }
        return source
            .split(Regex("[-_/ ]+"))
            .filter(String::isNotEmpty)
            .joinToString(", ") { readIdentifierGroup(it, includeClassBoundaries) }
    }

    private fun readSigned(source: String): String {
        val sign = if (source.startsWith('-')) "마이너스" else "플러스"
        return "$sign ${NumberReader.decimal(source.drop(1))}"
    }

    private fun flightMatch(source: String): Pair<String, String>? {
        val match = FLIGHT_PATTERN.matchEntire(source.replace("-", "")) ?: return null
        val code = match.groupValues[1].uppercase()
        return if (code in AIRLINE_DESIGNATORS) code to match.groupValues[2] else null
    }

    private fun trainMatch(source: String): Pair<String, String>? {
        val match = TRAIN_PATTERN.matchEntire(source) ?: return null
        return match.groupValues[1].uppercase() to match.groupValues[2]
    }

    private fun readFlightCode(code: String): String = code.map { ch ->
        when {
            ch == 'Z' -> "지"
            ch.isDigit() -> if (ch == '7') "세븐" else NumberReader.digits(ch.toString(), zeroAsGong = true)
            else -> EnglishReader.readToken(ch.toString())
        }
    }.joinToString(" ")

    fun identifierBoundaries(source: String): List<SpeechProsodyBoundary> = buildList {
        var previousClass: IdentifierCharacterClass? = null
        source.forEachIndexed { index, char ->
            val currentClass = identifierClass(char)
            when {
                currentClass == IdentifierCharacterClass.SEPARATOR -> {
                    if (lastOrNull()?.sourceOffset != index - 1) addBoundary(index, "separator")
                    previousClass = null
                }
                currentClass != null -> {
                    if (previousClass != null && previousClass != currentClass) addBoundary(index, "character-class-transition")
                    previousClass = currentClass
                }
                else -> previousClass = null
            }
        }
    }

    private fun MutableList<SpeechProsodyBoundary>.addBoundary(offset: Int, reason: String) {
        add(SpeechProsodyBoundary(offset, reason, IDENTIFIER_BOUNDARY_MS))
    }

    private enum class IdentifierCharacterClass { LATIN, DIGIT, SEPARATOR }

    private fun identifierClass(char: Char): IdentifierCharacterClass? = when {
        char in 'A'..'Z' || char in 'a'..'z' -> IdentifierCharacterClass.LATIN
        char.isDigit() -> IdentifierCharacterClass.DIGIT
        char in "-_/ " -> IdentifierCharacterClass.SEPARATOR
        else -> null
    }

    private fun readIdentifierGroup(group: String, includeClassBoundaries: Boolean): String {
        val tokens = mutableListOf<String>()
        var index = 0
        var previousClass: IdentifierCharacterClass? = null
        while (index < group.length) {
            if (group[index] in '가'..'힣') {
                val start = index
                while (index < group.length && group[index] in '가'..'힣') index++
                tokens += group.substring(start, index)
                previousClass = null
            } else {
                val char = group[index++]
                val currentClass = identifierClass(char)
                if (includeClassBoundaries && previousClass != null && currentClass != null && previousClass != currentClass) tokens += ","
                val spoken = when {
                    char.isDigit() -> NumberReader.digits(char.toString(), zeroAsGong = true)
                    char in 'A'..'Z' || char in 'a'..'z' -> EnglishReader.readToken(char.toString())
                    else -> ""
                }
                if (spoken.isNotEmpty()) tokens += spoken
                previousClass = currentClass
            }
        }
        return tokens.joinToString(" ").replace(" , ", ", ")
    }

    private fun readIpv4(source: String): String = source.split('.').joinToString(" 점 ") {
        // IP octets are identifiers: preserve each digit and leading zero.
        NumberReader.digits(it, zeroAsGong = true).replace(" ", "")
    }

    private fun readColon(source: String, separator: String): String {
        val parts = source.split(':')
        return "${NumberReader.sino(parts[0].toLong())} $separator ${NumberReader.sino(parts[1].toLong())}"
    }

    private fun readDate(source: String): String {
        val values = Regex("\\d+").findAll(source).map { it.value.toLong() }.toList()
        if (values.size == 3 && values[2] >= 1000) {
            val month = if (values[0] > 12) values[1] else values[0]
            val day = if (values[0] > 12) values[0] else values[1]
            return "${NumberReader.sino(values[2])}년 ${NumberReader.sino(month)}월 ${NumberReader.sino(day)}일"
        }
        return when {
            source.contains('년') || values.size == 3 -> "${NumberReader.sino(values[0])}년 ${NumberReader.sino(values[1])}월 ${NumberReader.sino(values[2])}일"
            values.size == 2 -> "${NumberReader.sino(values[0])}월 ${NumberReader.sino(values[1])}일"
            else -> source
        }
    }

    private fun readFraction(source: String): String {
        val (numerator, denominator) = source.split('/').map { it.toLong() }
        return "${NumberReader.sino(denominator)}분의 ${NumberReader.sino(numerator)}"
    }

    private fun readCurrency(source: String): String {
        val number = Regex("\\d[\\d,]*(?:\\.\\d+)?").find(source)?.value ?: return source
        val symbol = source.firstOrNull { it in "₩$€£¥￥₹₽₺" }
        val code = Regex("(?i)\\b(USD|EUR|GBP|JPY|CNY|INR|RUB|TRY)\\b").find(source)?.value?.uppercase()
        val currency = when (symbol ?: code?.firstOrNull()) {
            '$' -> "달러"; '€' -> "유로"; '£' -> "파운드"; '¥', '￥' -> "엔"
            '₹' -> "루피"; '₽' -> "루블"; '₺' -> "리라"; '₩' -> "원"; else -> "원"
        }
        val namedCurrency = when (code) {
            "USD" -> "달러"; "EUR" -> "유로"; "GBP" -> "파운드"; "JPY" -> "엔"
            "CNY" -> "위안"; "INR" -> "루피"; "RUB" -> "루블"; "TRY" -> "리라"; else -> currency
        }
        val pieces = number.replace(",", "").split('.')
        val whole = NumberReader.sino(pieces[0].toLong())
        if (pieces.size == 1 || pieces[1].isEmpty()) return "$whole $namedCurrency"
        val fraction = NumberReader.sino(pieces[1].toLong())
        val minor = when (symbol ?: code?.firstOrNull()) {
            '$', '€' -> "센트"; '£' -> "펜스"; '₹' -> "파이사"
            else -> if (code == "USD" || code == "EUR") "센트" else if (code == "GBP") "펜스" else ""
        }
        return if (minor.isEmpty()) "${NumberReader.decimal(number)} $namedCurrency"
        else "$whole $namedCurrency $fraction $minor"
    }

    private fun readPercent(source: String): String =
        "${NumberReader.decimal(source.removeSuffix("%"))} 퍼센트"

    fun normalizeMeasurement(source: String, precedingText: String = ""): String = readMeasurement(source, precedingText)

    private fun readMeasurement(source: String, precedingText: String = ""): String {
        val match = MEASUREMENT_PATTERN.matchEntire(source) ?: return source
        // Reuse the shared numeric reader so measurement decimals keep the
        // application's established numeric pronunciation and spacing.
        val number = NumberReader.decimal(match.groupValues[1])
        val unit = match.groupValues[2].replace(Regex("\\s"), "").lowercase()
        return when (unit) {
            "km/h" -> "${if (precedingText.trimEnd().endsWith("시속")) "" else "시속 "}$number 킬로미터"
            "m/s" -> "${if (precedingText.trimEnd().endsWith("초속")) "" else "초속 "}$number 미터"
            "℃", "°c", "도씨" -> "$number 도"
            "℉", "°f" -> "${if (precedingText.trimEnd().endsWith("화씨")) "" else "화씨 "}$number 도"
            else -> "$number ${units[unit] ?: return source}"
        }
    }

    fun readVersion(source: String): String {
        val numberMatch = Regex("\\d+(?:\\.\\d+)+").find(source) ?: return source
        val particle = Regex("버전(은|는|이|가)", RegexOption.IGNORE_CASE)
            .find(source.substring(0, numberMatch.range.first))?.groupValues?.get(1).orEmpty()
        val number = numberMatch.value
        val segments = number.split('.')
        val spoken = buildList {
            val first = segments.first()
            add(if (first.length > 1 && first.startsWith('0')) NumberReader.digits(first, zeroAsGong = true) else NumberReader.decimal(first))
            segments.drop(1).forEach { add(NumberReader.digits(it, zeroAsGong = true)) }
        }.joinToString(" 점 ")
        val prefix = source.substring(0, numberMatch.range.first).trim()
        val hasV = prefix.endsWith("v", true)
        val named = prefix.startsWith("버전") || prefix.startsWith("version", true)
        return (if (named) "버전$particle " else "") + (if (hasV) "브이 " else "") + spoken
    }

    private fun readMath(source: String): String {
        val logic = Regex("^(.+?)\\s*(&&|\\|\\||∧|∨)\\s*(.+)$").matchEntire(source.trim())
        if (logic != null) {
            val op = if (logic.groupValues[2] in setOf("&&", "∧")) "그리고" else "또는"
            return "${readMath(logic.groupValues[1])} $op ${readMath(logic.groupValues[3])}"
        }
        if (source.trim().startsWith('¬') || source.trim().startsWith('!'))
            return "${readMathOperand(source.trim().drop(1).trim())}의 부정"
        if (source.contains('√')) return "루트 ${readMathOperand(source.substringAfter('√').trim())}"
        if (source.contains('±')) return "플러스마이너스 ${readMathOperand(source.substringAfter('±').trim())}"
        val comparison = Regex("^(.+?)\\s*(<=|>=|!=|==|≤|≥|≠|≈|<|>|=)\\s*(.+)$").matchEntire(source.trim())
        if (comparison != null) {
            val symbol = comparison.groupValues[2]
            val op = when (comparison.groupValues[2]) {
                "<" -> "보다 작다"; ">" -> "보다 크다"; "≤", "<=" -> "보다 작거나 같다"; "≥", ">=" -> "보다 크거나 같다"
                "≠", "!=" -> "와 같지 않다"; "≈" -> "와 거의 같다"; else -> "와 같다"
            }
            val left = readMath(comparison.groupValues[1].trim())
            val right = readMath(comparison.groupValues[3].trim())
            // An equality is read as a formula, not as a prose conclusion.
            if (symbol == "=" || symbol == "==") {
                return "$left${if (hasFinalConsonant(left)) "은" else "는"} $right"
            }
            val suffix = if (op.startsWith("와") && hasFinalConsonant(right)) "과" + op.drop(1) else op
            return "$left${if (hasFinalConsonant(left)) "은" else "는"} $right$suffix"
        }
        val exponent = Regex("^([+\\-]?[A-Za-z가-힣0-9]+)\\s*(?:\\^\\s*\\(?\\s*([+\\-]?\\d+)\\s*\\)?|([⁻⁰¹²³⁴⁵⁶⁷⁸⁹]+))$").matchEntire(source.trim())
        if (exponent != null) {
            val base = readMathOperand(exponent.groupValues[1])
            val expRaw = exponent.groupValues[2].ifEmpty { exponent.groupValues[3].mapSuperscript() }
            val exp = readMathOperand(expRaw)
            val letterBase = exponent.groupValues[1].length == 1 && exponent.groupValues[1][0].isLetter()
            val spokenPower = when {
                expRaw == "2" && letterBase -> "제곱"
                expRaw == "3" && letterBase -> "세제곱"
                expRaw == "2" -> "제곱"
                expRaw == "3" -> "세제곱"
                else -> "${exp}승"
            }
            return if (letterBase && (expRaw == "2" || expRaw == "3")) "$base $spokenPower" else "${base}의 $spokenPower"
        }
        if (source.trim().matches(Regex("[A-Za-z]|[+\\-]?\\d+(?:\\.\\d+)?"))) return readMathOperand(source.trim())
        val output = StringBuilder()
        val arithmetic = source.replace(Regex("(?<=\\d)\\s*[xX*]\\s*(?=\\d)"), " 곱하기 ")
        Regex("(?:[A-Za-z]|\\d+(?:\\.\\d+)?)(?:[⁻⁰¹²³⁴⁵⁶⁷⁸⁹]+|\\s*\\^\\s*(?:\\([+\\-]?\\d+\\)|[+\\-]?\\d+))|곱하기|\\d+(?:\\.\\d+)?|[A-Za-z가-힣]|[+\\-−×÷*=]").findAll(arithmetic).forEach { token ->
            if (output.isNotEmpty()) output.append(' ')
            output.append(when (token.value) {
                "+" -> "더하기"
                "-", "−" -> "빼기"
                "×", "*" -> "곱하기"
                "÷" -> "나누기"
                "=" -> "는"
                "곱하기" -> "곱하기"
                else -> if (token.value.contains('^') || token.value.any { it in "⁻⁰¹²³⁴⁵⁶⁷⁸⁹" }) readMath(token.value) else readMathOperand(token.value)
            })
        }
        return output.toString()
    }

    private fun hasFinalConsonant(value: String): Boolean =
        value.lastOrNull()?.let { it in '가'..'힣' && (it.code - '가'.code) % 28 != 0 } ?: false

    private fun readMathOperand(value: String): String = when {
        value.matches(Regex("[+\\-]?\\d+(?:\\.\\d+)?")) -> if (value.startsWith('+') || value.startsWith('-')) readSigned(value) else NumberReader.decimal(value)
        value.length == 1 && value[0].isLetter() -> EnglishReader.readToken(value)
        else -> value
    }

    private fun String.mapSuperscript(): String = mapNotNull { ch ->
        when (ch) { '⁰' -> '0'; '¹' -> '1'; '²' -> '2'; '³' -> '3'; '⁴' -> '4'; '⁵' -> '5'; '⁶' -> '6'; '⁷' -> '7'; '⁸' -> '8'; '⁹' -> '9'; '⁻' -> '-'; else -> null }
    }.joinToString("")

    fun normalizeNumber(source: String, nativeBeon: Boolean): String = readNumberWithCounter(source, nativeBeon)

    private fun readNumberWithCounter(source: String, nativeBeon: Boolean = false): String {
        val match = Regex("(\\d[\\d,]*)\\s*(개월|시|분|개|명|살|병|번|호|가지)?").matchEntire(source) ?: return source
        val value = match.groupValues[1].replace(",", "").toLong()
        val counter = match.groupValues[2]
        val number = if ((counter in setOf("시", "개", "명", "살", "병", "가지") || counter == "번" && nativeBeon) && value in 1..99) {
            if (value == 20L && counter in setOf("개", "명", "살", "병", "번")) "스무" else NumberReader.native(value.toInt())
        } else {
            NumberReader.sino(value)
        }
        return if (counter.isEmpty()) number else "$number $counter"
    }

    private fun readEmail(source: String): String {
        val parts = source.split('@', limit = 2)
        val local = Regex("[A-Za-z]+|\\d+|[^A-Za-z0-9]").findAll(parts[0]).joinToString(" ") { match ->
            when {
                match.value.all(Char::isDigit) -> NumberReader.digits(match.value, zeroAsGong = true)
                match.value.all(Char::isLetter) -> EnglishReader.readToken(match.value)
                match.value == "." -> "점"
                else -> EnglishReader.readIdentifier(match.value)
            }
        }
        return "$local 골뱅이 ${EnglishReader.readDomain(parts[1])}"
    }
}

object EnglishReader {
    private val letters = mapOf(
        'a' to "에이", 'b' to "비", 'c' to "씨", 'd' to "디", 'e' to "이", 'f' to "에프",
        'g' to "지", 'h' to "에이치", 'i' to "아이", 'j' to "제이", 'k' to "케이", 'l' to "엘",
        'm' to "엠", 'n' to "엔", 'o' to "오", 'p' to "피", 'q' to "큐", 'r' to "알",
        's' to "에스", 't' to "티", 'u' to "유", 'v' to "브이", 'w' to "더블유", 'x' to "엑스",
        'y' to "와이", 'z' to "제트",
    )
    private val words = mapOf(
        "test" to "테스트", "user" to "유저", "support" to "서포트", "example" to "이그잼플",
        "com" to "컴", "android" to "안드로이드",
    )

    fun readToken(token: String): String = words[token.lowercase()] ?: token.lowercase().mapNotNull(letters::get).joinToString(" ")
    fun spell(token: String): String = token.lowercase().mapNotNull(letters::get).joinToString("")
    fun readIdentifier(value: String): String = value.split('.').joinToString(" 점 ") { readToken(it) }
    fun readDomain(value: String): String = value.split('.').joinToString(" 점 ") { readToken(it) }
}
