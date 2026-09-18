package com.yiwoosolution.koreantts.speech

/** Calendar expressions must be consumed before generic Latin and numeric fallback. */
object KoreanCalendarText {
    private val months = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")
    private val monthPattern = "(?:January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\\.?"
    private val englishDate = Regex("(?i)(?<![A-Za-z0-9])(?:($monthPattern)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?|(\\d{1,2})(?:st|nd|rd|th)?\\s+($monthPattern)(?:\\s+(\\d{4}))?)(?![A-Za-z0-9])")
    private val numericDate = Regex("(?<![A-Za-z0-9./-])(?:(미국식|영국식|MM/DD/YYYY|DD/MM/YYYY)\\s*[:：]?\\s*)?(\\d{4}|\\d{1,2})([-./])(\\d{1,2})\\3(\\d{4}|\\d{1,2})(?![0-9./-])")
    private val koreanDate = Regex("(?<!\\d)(?:(\\d{4})\\s*년\\s*)?(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")
    private val monthOnly = Regex("(?<!\\d)(1[0-2]|0?[1-9])\\s*월")
    private val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val koreanDays = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")
    private val weekday = Regex("(?i)(?<![A-Za-z])(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tue(?:s)?|Wed|Thu(?:rs)?|Fri|Sat|Sun)\\.?(?![A-Za-z])")
    private val ampm = "(?:오전|오후|(?i:a\\.?m\\.?|p\\.?m\\.?))"
    private val clock = "(?:(?:$ampm)\\s*)?\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*(?:$ampm))?"
    private val timeRange = Regex("(?<![A-Za-z0-9:])($clock)\\s*[~∼–—-]\\s*($clock)(?![A-Za-z0-9:])")
    private val time = Regex("(?<![A-Za-z0-9:])$clock(?![A-Za-z0-9:])")
    private val koreanTime = Regex("(?<!\\d)(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?(?:\\s*(\\d{1,2})\\s*초)?")
    private val weekdaySuffix = Regex("(?<=일)\\s*\\(([월화수목금토일])\\)")
    private val weekdaySeries = Regex("(?<![가-힣])(?:월화수목금토일|월화수목금토|월화수목금)(?![가-힣])")
    private val shortDate = Regex("(?<![A-Za-z0-9./-])(\\d{1,2})([-/])(\\d{1,2})\\2(\\d{2})(?![A-Za-z0-9./-])")
    private val hourWithMeridiem = Regex("(?<![A-Za-z0-9:])(?:(($ampm)\\s*)(\\d{1,2})|(\\d{1,2})\\s+($ampm))(?![A-Za-z0-9:]|\\s*시)")
    private val dayList = Regex("(?<![가-힣])[월화수목금토일](?:\\s*[/,·]\\s*[월화수목금토일])+(?![가-힣])")
    private val iso = Regex("(?<![A-Za-z0-9])(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}:\\d{2}(?::\\d{2})?)(Z|[+-]\\d{2}:\\d{2})?(?![A-Za-z0-9:.])", RegexOption.IGNORE_CASE)

    fun normalize(input: String): String {
        var text = iso.replace(input) { m ->
            val y = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); val d = m.groupValues[3].toInt()
            if (!validDate(y, mo, d)) return@replace m.value
            val zone = m.groupValues[5]
            val zoneText = if (zone.equals("Z", true)) " 유티씨" else if (zone.isNotEmpty())
                " 유티씨 " + (if (zone[0] == '+') "플러스 " else "마이너스 ") + zone.drop(1).split(':').joinToString(" 콜론 ") { NumberReader.digits(it).replace(" ", "") } else ""
            date(y, mo, d) + " " + clock(m.groupValues[4]) + zoneText
        }
        text = numericDate.replace(text) { m ->
            val a = m.groupValues[2].toInt(); val b = m.groupValues[4].toInt(); val c = m.groupValues[5].toInt()
            val yearFirst = m.groupValues[2].length == 4
            if (!yearFirst && m.groupValues[5].length != 4) return@replace m.value
            val dmy = m.groupValues[1] in setOf("영국식", "DD/MM/YYYY") || (m.groupValues[1] !in setOf("미국식", "MM/DD/YYYY") && a > 12)
            val year = if (yearFirst) a else c
            val month = if (yearFirst || dmy) b else a
            val day = if (yearFirst) c else if (dmy) a else b
            val prefix = m.groupValues[1].let { if (it.isEmpty()) "" else if (it == "MM/DD/YYYY") "미국식 " else if (it == "DD/MM/YYYY") "영국식 " else "$it " }
            prefix + if (validDate(year, month, day)) date(year, month, day) else literalDate(m.groupValues[2], m.groupValues[4], m.groupValues[5], m.groupValues[3])
        }
        // Two-digit years have no configured century pivot; preserve the notation.
        text = shortDate.replace(text) { literalDate(it.groupValues[1], it.groupValues[3], it.groupValues[4], it.groupValues[2]) }
        text = englishDate.replace(text) { m ->
            val monthName = m.groupValues[1].ifEmpty { m.groupValues[5] }.lowercase().trimEnd('.')
            val month = months.indexOfFirst { it.startsWith(monthName.take(3)) } + 1
            val day = m.groupValues[2].ifEmpty { m.groupValues[4] }.toInt()
            val year = m.groupValues[3].ifEmpty { m.groupValues[6] }.toIntOrNull()
            if (validDate(year ?: 2000, month, day)) date(year, month, day) else m.value
        }
        text = koreanDate.replace(text) { m ->
            val y = m.groupValues[1].toIntOrNull(); val mo = m.groupValues[2].toInt(); val d = m.groupValues[3].toInt()
            if (validDate(y ?: 2000, mo, d)) date(y, mo, d) else
                (y?.let { NumberReader.digits(it.toString()).replace(" ", "") + " 년 " } ?: "") + NumberReader.digits(m.groupValues[2]).replace(" ", "") + " 월 " + NumberReader.digits(m.groupValues[3]).replace(" ", "") + " 일"
        }
        text = monthOnly.replace(text) { month(it.groupValues[1].toInt()) }
        text = weekdaySuffix.replace(text) { " ${koreanDays["월화수목금토일".indexOf(it.groupValues[1])]}" }
        text = weekdaySeries.replace(text) { it.value.map { ch -> koreanDays["월화수목금토일".indexOf(ch)] }.joinToString(" ") }
        text = dayList.replace(text) { it.value.filter { ch -> ch in "월화수목금토일" }.map { ch -> koreanDays["월화수목금토일".indexOf(ch)] }.joinToString(" ") }
        text = Regex("\\(([월화수목금토일])\\)").replace(text) { koreanDays["월화수목금토일".indexOf(it.groupValues[1])] }
        text = weekday.replace(text) { m -> koreanDays[weekdays.indexOfFirst { it.take(3).equals(m.value.take(3), true) }] }
        text = timeRange.replace(text) { "${clock(it.groupValues[1])}에서 ${clock(it.groupValues[2])}" }
        text = time.replace(text) { clock(it.value) }
        text = hourWithMeridiem.replace(text) { m ->
            val marker = m.groupValues[2].ifEmpty { m.groupValues[5] }
            val h = m.groupValues[3].ifEmpty { m.groupValues[4] }.toInt()
            if (h !in 1..12) m.value else clock("$marker $h:00")
        }
        text = koreanTime.replace(text) { m ->
            val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toIntOrNull(); val sec = m.groupValues[3].toIntOrNull()
            if (h !in 0..23 || (min != null && min !in 0..59) || (sec != null && sec !in 0..59)) m.value
            else hour(h) + " 시" + (min?.let { " ${NumberReader.sino(it.toLong())} 분" } ?: "") + (sec?.let { " ${NumberReader.sino(it.toLong())} 초" } ?: "")
        }
        return text.replace(Regex("(?<=시)\\s*[~∼–—-]\\s*(?=(?:오전|오후)\\s|[가-힣]+ 시)"), "에서 ")
    }

    private fun clock(source: String): String {
        val values = Regex("\\d+").findAll(source).map { it.value.toInt() }.toList()
        val marker = Regex(ampm).find(source)?.value?.lowercase()?.replace(".", "")
        val h = values[0]; val min = values[1]; val sec = values.getOrNull(2)
        if (h !in 0..23 || min !in 0..59 || (sec != null && sec !in 0..59) || (marker != null && h !in 1..12))
            return source.replace(Regex("\\d+")) { NumberReader.digits(it.value).replace(" ", "") }.replace(":", " 콜론 ")
        val prefix = when (marker) { "am", "오전" -> "오전 "; "pm", "오후" -> "오후 "; else -> "" }
        return prefix + hour(h) + " 시" + (if (min != 0 || sec != null) " ${NumberReader.sino(min.toLong())} 분" else "") + (sec?.let { " ${NumberReader.sino(it.toLong())} 초" } ?: "")
    }
    private fun hour(h: Int) = if (h in 1..12) NumberReader.native(h) else NumberReader.sino(h.toLong())
    private fun month(m: Int) = when (m) { 6 -> "유월"; 10 -> "시월"; else -> NumberReader.sino(m.toLong()) + "월" }
    private fun date(y: Int?, m: Int, d: Int) = (y?.let { NumberReader.sino(it.toLong()) + "년 " } ?: "") + month(m) + " " + NumberReader.sino(d.toLong()) + "일"
    private fun literalDate(a: String, b: String, c: String, separator: String): String = listOf(a, b, c).joinToString(when (separator) { "/" -> " 슬래시 "; "-" -> " 하이픈 "; else -> " 점 " }) { NumberReader.digits(it).replace(" ", "") }
    private fun validDate(y: Int, m: Int, d: Int): Boolean {
        if (y !in 1..9999 || m !in 1..12) return false
        val leap = y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)
        return d in 1..(intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[m - 1])
    }
}
