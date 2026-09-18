package com.yiwoosolution.koreantts.speech

class EmoticonNormalizer {
    fun normalize(input: String, mode: SpeechMode): String {
        var text = input
        val fixed = linkedMapOf(
            "^^;" to " 멋쩍은 웃음 ", ":-)" to " 웃는 표정 ", ":)" to " 웃는 표정 ",
            ":-(" to " 슬픈 표정 ", ":(" to " 슬픈 표정 ", "-_-" to " 무표정 ", "^^" to " 웃는 표정 ",
        )
        fixed.forEach { (source, spoken) -> text = text.replace(source, spoken) }
        text = text.replace(Regex("(?<![가-힣A-Za-z0-9])[ㅇᄋ]{2}(?![가-힣A-Za-z0-9])"), " 응응 ")
        text = text.replace(Regex("(?<![가-힣A-Za-z0-9])[ㅇᄋ][ㅋᄏ](?![가-힣A-Za-z0-9])"), " 오케이 ")
        text = text.replace(Regex("(?<![가-힣A-Za-z0-9])[ㄱᄀ]{2}(?![가-힣A-Za-z0-9])"), " 고고 ")
        text = text.replace(Regex("(?<![가-힣A-Za-z0-9])[ㄴᄂ]{2}(?![가-힣A-Za-z0-9])"), " 노노 ")
        text = text.replace(Regex("(?<![가-힣A-Za-z0-9])[ㅂᄇ]{2}(?![가-힣A-Za-z0-9])"), " 바이바이 ")
        text = text.replace(Regex("(?<![가-힣A-Za-z0-9])[ㄷᄃ]{2}(?![가-힣A-Za-z0-9])"), " 덜덜 ")
        text = text.replace(Regex("[ㅋᄏ]{2,}")) { match ->
            if (mode == SpeechMode.LITERAL) List(match.value.codePointCount(0, match.value.length)) { "키읔" }.joinToString(" ")
            else "크".repeat(match.value.codePointCount(0, match.value.length))
        }
        text = text.replace(Regex("[ㅎᄒ]{2,}")) { match ->
            if (mode == SpeechMode.LITERAL) List(match.value.codePointCount(0, match.value.length)) { "히읗" }.joinToString(" ")
            else "흐".repeat(match.value.codePointCount(0, match.value.length))
        }
        text = text.replace(Regex("(?:ㅠ|ㅜ|ᅲ|ᅮ){2,}"), " 우는 표정 ")
        return text
    }
}
