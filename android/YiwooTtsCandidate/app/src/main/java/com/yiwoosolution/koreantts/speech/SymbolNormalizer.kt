package com.yiwoosolution.koreantts.speech

class SymbolNormalizer {
    private val names = mapOf(
        '#' to "샵", '@' to "골뱅이", '%' to "퍼센트", '&' to "앤드", '+' to "플러스", '=' to "이퀄",
        ':' to "콜론", ';' to "세미콜론", '/' to "슬래시", '\\' to "역슬래시", '_' to "언더바",
        '-' to "하이픈", '*' to "별표", '^' to "캐럿", '~' to "물결표", '|' to "세로줄",
        '(' to "여는 괄호", ')' to "닫는 괄호", '[' to "여는 대괄호", ']' to "닫는 대괄호",
        '{' to "여는 중괄호", '}' to "닫는 중괄호",
    )

    fun normalize(input: String, mode: SpeechMode): String {
        val punctuation = if (mode == SpeechMode.NATURAL) input
            .replace(Regex("!+"), "!").replace(Regex("\\?+"), "?").replace(Regex("\\.{2,}"), ".") else input
        return buildString(punctuation.length) {
            punctuation.forEachIndexed { index, char ->
                val name = names[char]
                if (char == ':' && punctuation.substring(0, index).matches(Regex("(?:^|.*\\s)핀\\s*$"))) append(char)
                else if (name != null) append(' ').append(name).append(' ')
                else if (mode != SpeechMode.NATURAL && char in setOf('!', '?')) {
                    append(' ').append(if (char == '!') "느낌표" else "물음표").append(' ')
                } else if (char == ',' && punctuation.length == 1) append("쉼표")
                else if (char == '.' && punctuation.length == 1) append("마침표")
                else append(char)
            }
        }
    }
}
