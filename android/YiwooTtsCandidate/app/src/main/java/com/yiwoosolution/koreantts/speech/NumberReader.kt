package com.yiwoosolution.koreantts.speech

object NumberReader {
    private val digits = arrayOf("영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
    private val nativeOnes = arrayOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉")
    private val nativeTens = arrayOf("", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔")

    fun sino(value: Long): String {
        if (value == 0L) return "영"
        if (value < 0) return "마이너스 ${sino(-value)}"
        val groups = arrayOf("", "만", "억", "조", "경")
        var remaining = value
        var group = 0
        val parts = mutableListOf<String>()
        while (remaining > 0) {
            val chunk = (remaining % 10_000).toInt()
            if (chunk != 0) {
                // Legacy G2P omits the coefficient one for 만, but retains it for 억/조/경.
                val chunkText = if (group == 1 && chunk == 1) "" else readChunk(chunk)
                parts += chunkText + groups[group]
            }
            remaining /= 10_000
            group++
        }
        return parts.asReversed().joinToString("")
    }

    fun digits(text: String, zeroAsGong: Boolean = false): String = text
        .filter(Char::isDigit)
        .map { if (it == '0' && zeroAsGong) "공" else digits[it - '0'] }
        .joinToString(" ")

    fun decimal(text: String): String {
        val pieces = text.replace(",", "").split('.')
        val whole = pieces[0].toLongOrNull()?.let(::sino) ?: digits(pieces[0])
        return if (pieces.size == 1) whole else "${whole}점${digits(pieces[1]).replace(" ", "")}"
    }

    fun money(text: String): String {
        val pieces = text.replace(",", "").split('.')
        val whole = pieces[0].toLongOrNull()?.let(::sinoMoney) ?: digits(pieces[0])
        return if (pieces.size == 1) whole else "$whole 점 ${digits(pieces[1])}"
    }

    fun native(value: Int): String {
        if (value !in 1..99) return sino(value.toLong())
        val tens = value / 10
        val ones = value % 10
        return nativeTens[tens] + nativeOnes[ones]
    }

    private fun readChunk(value: Int): String {
        val units = arrayOf("", "십", "백", "천")
        var divisor = 1000
        val out = StringBuilder()
        for (position in 3 downTo 0) {
            val digit = value / divisor % 10
            if (digit != 0) {
                if (digit != 1 || position == 0) out.append(digits[digit])
                out.append(units[position])
            }
            divisor /= 10
        }
        return out.toString()
    }

    private fun sinoMoney(value: Long): String {
        val lowChunk = (value % 10_000).toInt()
        val thousands = lowChunk / 1_000
        val remainder = lowChunk % 1_000
        if (thousands == 0 || remainder == 0) return sino(value)

        val highValue = value - lowChunk
        val thousandsText = if (thousands == 1) "천" else "${sino(thousands.toLong())}천"
        val lowText = "$thousandsText ${sino(remainder.toLong())}"
        return if (highValue == 0L) lowText else "${sino(highValue)} $lowText"
    }
}
