package com.yiwoosolution.koreantts.speech

/** Compact offline names derived from Unicode CLDR 48 annotations (Emoji 17). */
object EmojiSemanticNames {
    enum class Locale { KOREAN, ENGLISH }

    private val ko = mapOf(
        "😀" to "활짝 웃는 얼굴", "😂" to "기쁨의 눈물을 흘리는 얼굴", "🥲" to "눈물 흘리며 웃는 얼굴",
        "❤" to "빨간색 하트", "💔" to "깨진 하트", "❤‍🔥" to "불 난 하트", "❤‍🩹" to "낫고 있는 하트",
        "👍" to "올린 엄지", "👍🏻" to "밝은 피부 올린 엄지", "👍🏽" to "중간 피부 올린 엄지", "👍🏿" to "어두운 피부 올린 엄지",
        "👎" to "내린 엄지", "👏" to "손뼉", "🙏" to "기도", "🔥" to "불", "🎉" to "파티",
        "🚗" to "자동차", "✈" to "비행기", "☕" to "뜨거운 음료", "📱" to "휴대전화", "💻" to "노트북",
        "👩‍💻" to "여자 기술 전문가", "👨‍👩‍👧‍👦" to "가족", "🇰🇷" to "대한민국 국기", "🇺🇸" to "미국 국기",
        "🇯🇵" to "일본 국기", "🇬🇧" to "영국 국기", "🇨🇳" to "중국 국기", "🇪🇺" to "유럽 연합 국기",
        "0️⃣" to "키 캡 0", "1️⃣" to "키 캡 1", "5️⃣" to "키 캡 5", "#️⃣" to "키 캡 샵", "*️⃣" to "키 캡 별표", "🔟" to "키 캡 10",
    )
    private val en = mapOf(
        "😀" to "grinning face", "😂" to "face with tears of joy", "🥲" to "smiling face with tear",
        "❤" to "red heart", "💔" to "broken heart", "❤‍🔥" to "heart on fire", "❤‍🩹" to "mending heart",
        "👍" to "thumbs up", "👍🏻" to "light skin tone thumbs up", "👍🏽" to "medium skin tone thumbs up", "👍🏿" to "dark skin tone thumbs up",
        "👎" to "thumbs down", "👏" to "clapping hands", "🙏" to "folded hands", "🔥" to "fire", "🎉" to "party popper",
        "🚗" to "automobile", "✈" to "airplane", "☕" to "hot beverage", "📱" to "mobile phone", "💻" to "laptop",
        "👩‍💻" to "woman technologist", "👨‍👩‍👧‍👦" to "family", "🇰🇷" to "South Korea flag", "🇺🇸" to "United States flag",
        "🇯🇵" to "Japan flag", "🇬🇧" to "United Kingdom flag", "🇨🇳" to "China flag", "🇪🇺" to "European Union flag",
        "0️⃣" to "keycap 0", "1️⃣" to "keycap 1", "5️⃣" to "keycap 5", "#️⃣" to "keycap number sign", "*️⃣" to "keycap asterisk", "🔟" to "keycap 10",
    )

    fun replace(input: String, locale: Locale): String {
        val table = if (locale == Locale.KOREAN) ko else en
        val ordered = table.keys.sortedByDescending { it.codePointCount(0, it.length) }
        var value = input
        for (key in ordered) {
            val variants = listOf(key, key.replace("❤", "❤️").replace("✈", "✈️"))
            variants.distinct().forEach { variant ->
                value = value.replace(variant, " ${table[key]} ")
            }
        }
        // Remove unhandled pictographs without exposing variation selectors or ZWJ names.
        return buildString(value.length) {
            var i = 0
            while (i < value.length) {
                val cp = value.codePointAt(i)
                if (cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp == 0xFE0F || cp == 0x200D || cp in 0x1F3FB..0x1F3FF) {
                    i += Character.charCount(cp)
                } else {
                    appendCodePoint(cp)
                    i += Character.charCount(cp)
                }
            }
        }
    }
}
