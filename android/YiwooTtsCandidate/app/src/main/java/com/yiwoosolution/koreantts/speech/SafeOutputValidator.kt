package com.yiwoosolution.koreantts.speech

class SafeOutputValidator {
    fun validate(input: String): String {
        val output = StringBuilder(input.length)
        var index = 0
        while (index < input.length) {
            val codePoint = input.codePointAt(index)
            val type = Character.getType(codePoint)
            if (!UnicodeSanitizer.isUnsupportedHangulJamo(codePoint) && codePoint != UnicodeSanitizer.ARAEA &&
                codePoint != UnicodeSanitizer.COMPATIBILITY_ARAEA &&
                (Character.isLetterOrDigit(codePoint) || Character.isWhitespace(codePoint) ||
                codePoint in ALLOWED_PUNCTUATION || codePoint == LIST_BOUNDARY.code ||
                    codePoint == LIST_MARKER_BODY.code ||
                    codePoint == LIST_START.code ||
                    type == Character.NON_SPACING_MARK.toInt()
                )
            ) {
                output.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        return output.toString().replace(Regex("\\s+"), " ").trim()
    }

    private companion object {
        val ALLOWED_PUNCTUATION = setOf('.'.code, ','.code, '!'.code, '?'.code, ':'.code, '\''.code, '…'.code)
        const val LIST_BOUNDARY = '\uE001'
        const val LIST_MARKER_BODY = '\uE003'
        const val LIST_START = '\uE004'
    }
}
