package com.yiwoosolution.koreantts.speech

import java.text.Normalizer

class UnicodeSanitizer {
    fun sanitize(input: String): String {
        val valid = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val ch = input[index]
            when {
                Character.isHighSurrogate(ch) && index + 1 < input.length &&
                    Character.isLowSurrogate(input[index + 1]) -> {
                    valid.append(ch).append(input[index + 1])
                    index += 2
                }
                Character.isSurrogate(ch) -> index++
                else -> {
                    val type = Character.getType(ch)
                    when {
                        ch == '\n' || ch == '\r' || ch == '\t' -> valid.append(' ')
                        type == Character.CONTROL.toInt() -> Unit
                        isUnsafeFormat(ch.code) -> Unit
                        else -> valid.append(ch)
                    }
                    index++
                }
            }
        }
        return Normalizer.normalize(valid, Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), " ").trim()
    }

    fun filterForSpeech(input: String, mode: SpeechMode): String {
        val output = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val codePoint = input.codePointAt(index)
            when {
                codePoint == ARAEA || codePoint == COMPATIBILITY_ARAEA -> {
                    if (mode != SpeechMode.NATURAL) output.append(" 아래아 ")
                }
                codePoint in DOT_LIKE -> output.append(' ')
                isUnsupportedHangulJamo(codePoint) -> Unit
                else -> output.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        return output.toString().replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        const val COMPATIBILITY_ARAEA = 0x318D
        const val ARAEA = 0x119E
        val DOT_LIKE = setOf(0x00B7, 0x30FB, 0x22C5, 0x2219)

        fun isUnsupportedHangulJamo(codePoint: Int): Boolean {
            val inJamoBlock = codePoint in 0x1100..0x11FF || codePoint in 0xA960..0xA97F || codePoint in 0xD7B0..0xD7FF
            if (!inJamoBlock) return false
            return codePoint !in 0x1100..0x1112 && codePoint !in 0x1161..0x1175 && codePoint !in 0x11A8..0x11C2
        }
    }

    private fun isUnsafeFormat(codePoint: Int): Boolean =
        codePoint == 0x200B || codePoint == 0x200C ||
            codePoint in 0x202A..0x202E || codePoint in 0x2060..0x206F ||
            codePoint == 0xFEFF
}
