package com.yiwoosolution.koreantts.speech

class EmojiNormalizer {
    fun normalize(input: String): String = EmojiSemanticNames.replace(input, EmojiSemanticNames.Locale.KOREAN)
}
