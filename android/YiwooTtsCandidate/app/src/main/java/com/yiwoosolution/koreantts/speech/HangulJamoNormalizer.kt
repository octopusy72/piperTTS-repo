package com.yiwoosolution.koreantts.speech

class HangulJamoNormalizer {
    private val names = mapOf(
        'ㄱ' to "기역", 'ㄴ' to "니은", 'ㄷ' to "디귿", 'ㄹ' to "리을", 'ㅁ' to "미음", 'ㅂ' to "비읍",
        'ㅅ' to "시옷", 'ㅇ' to "이응", 'ㅈ' to "지읒", 'ㅊ' to "치읓", 'ㅋ' to "키읔", 'ㅌ' to "티읕",
        'ㅍ' to "피읖", 'ㅎ' to "히읗", 'ㄲ' to "쌍기역", 'ㄸ' to "쌍디귿", 'ㅃ' to "쌍비읍",
        'ㅆ' to "쌍시옷", 'ㅉ' to "쌍지읒", 'ㅏ' to "아", 'ㅑ' to "야", 'ㅓ' to "어", 'ㅕ' to "여",
        'ㅗ' to "오", 'ㅛ' to "요", 'ㅜ' to "우", 'ㅠ' to "유", 'ㅡ' to "으", 'ㅣ' to "이", 'ㅐ' to "애",
        'ㅔ' to "에", 'ㅚ' to "외", 'ㅟ' to "위", 'ㅘ' to "와", 'ㅝ' to "워", 'ㅢ' to "의",
    )
    private val canonical = "ᄀᄁᄂᄃᄄᄅᄆᄇᄈᄉᄊᄋᄌᄍᄎᄏᄐᄑ하ᅢᅣᅥᅦᅧᅩᅪᅬᅭᅮᅯᅱᅲᅳᅴᅵ"
    private val compatibility = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㅏㅐㅑㅓㅔㅕㅗㅘㅚㅛㅜㅝㅟㅠㅡㅢㅣ"

    fun normalize(input: String): String = buildString(input.length) {
        input.forEach { char ->
            val mapped = canonical.indexOf(char).takeIf { it >= 0 }?.let { names[compatibility[it]] } ?: names[char]
            if (mapped == null) append(char) else append(' ').append(mapped).append(' ')
        }
    }
}
