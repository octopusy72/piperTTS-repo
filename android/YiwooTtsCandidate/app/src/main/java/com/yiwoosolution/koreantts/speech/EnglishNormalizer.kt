package com.yiwoosolution.koreantts.speech

import java.io.InputStream

class EnglishNormalizer(private val knownEnglishWords: Set<String> = DEFAULT_ENGLISH_WORDS) {
    fun normalize(input: String): String {
        val usbTerms = USB_C_PATTERN.replace(input, "유에스비 씨")
        val knownTerms = GTX_SUFFIX_PATTERN.replace(WIFI_PATTERN.replace(usbTerms, "와이파이")) {
            "GTX ${it.groupValues[1]}"
        }
        return Regex("[A-Za-z]+").replace(knownTerms) { match ->
            val token = match.value
            val key = token.lowercase()
            when {
                key in LEXICAL_PRONUNCIATIONS -> LEXICAL_PRONUNCIATIONS.getValue(key)
                key in ACRONYMS -> ACRONYMS.getValue(key)
                token.length == 1 -> EnglishReader.readToken(token)
                key in knownEnglishWords -> token
                else -> EnglishReader.spell(token)
            }
        }
    }

    companion object {
        private val WIFI_PATTERN = Regex("(?i)(?<![A-Za-z])wi-?fi(?![A-Za-z])")
        private val USB_C_PATTERN = Regex("(?i)(?<![A-Za-z])usb-c(?![A-Za-z])")
        private val GTX_SUFFIX_PATTERN = Regex("(?i)(?<![A-Za-z])GTX-([A-Za-z])(?![A-Za-z])")
        private val LEXICAL_PRONUNCIATIONS = mapOf(
            "android" to "안드로이드",
            "samsung" to "삼성",
            "pin" to "핀",
            "callback" to "콜백",
            "spacex" to "스페이스 엑스",
        )
        private val DEFAULT_ENGLISH_WORDS = setOf("hello", "world", "computer", "test", "galaxy", "ultra", "openai")
        private val ACRONYMS = mapOf(
            "tts" to "티티에스", "gtx" to "지티엑스", "ai" to "에이아이", "api" to "에이피아이", "apk" to "에이피케이",
            "cpu" to "씨피유", "gpu" to "지피유", "usb" to "유에스비", "sms" to "에스엠에스",
            "gps" to "지피에스", "url" to "유알엘", "pdf" to "피디에프", "html" to "에이치티엠엘",
            "http" to "에이치티티피", "https" to "에이치티티피에스", "ram" to "램", "rom" to "롬",
            "ssd" to "에스에스디", "hdd" to "에이치디디", "nfc" to "엔에프씨", "lte" to "엘티이",
            "vpn" to "브이피엔", "ip" to "아이피", "dns" to "디엔에스", "sdk" to "에스디케이",
            "ui" to "유아이", "ux" to "유엑스", "kbs" to "케이비에스",
        )

        fun fromCmuDictionary(input: InputStream): EnglishNormalizer {
            val words = input.bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val word = line.substringBefore(' ').lowercase()
                    word.takeIf { it.isNotEmpty() && it.all(Char::isLetter) }
                }.toMutableSet()
            }
            words += DEFAULT_ENGLISH_WORDS
            return EnglishNormalizer(words)
        }
    }
}
