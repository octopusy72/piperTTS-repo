package com.yiwoosolution.koreantts.speech

object KoreanUrlNormalizer {
    fun normalizeUrl(source: String): String {
        val trailing = source.takeLastWhile { it in ".,!" }
        val withoutTrailing = source.dropLast(trailing.length)
        val scheme = Regex("(?i)^(https?)://").find(withoutTrailing)?.groupValues?.get(1)
        val raw = withoutTrailing.replace(Regex("(?i)^https?://"), "")
        val fragmentParts = raw.split('#', limit = 2)
        val queryParts = fragmentParts[0].split('?', limit = 2)
        val addressParts = queryParts[0].split('/', limit = 2)
        val output = StringBuilder()
        if (scheme != null) {
            output.append(if (scheme.equals("https", true)) "에이치티티피에스" else "에이치티티피")
                .append(" 콜론 슬래시 슬래시 ")
        }
        output.append(readHost(addressParts[0]))
        if (addressParts.size == 2) {
            addressParts[1].split('/').forEach { output.append(" 슬래시 ").append(readComponent(it)) }
        }
        if (queryParts.size == 2 && queryParts[1].isNotEmpty()) {
            output.append(" 쿼리 ")
            queryParts[1].split('&').forEachIndexed { index, parameter ->
                if (index > 0) output.append(" 앤드 ")
                val pair = parameter.split('=', limit = 2)
                output.append(readComponent(pair[0]))
                if (pair.size == 2) output.append(" 이퀄 ").append(readQueryValue(pair[1]))
            }
        }
        if (fragmentParts.size == 2) output.append(" 샵 ").append(readComponent(fragmentParts[1]))
        return output.append(trailing).toString().trim()
    }

    fun normalizeEmail(source: String): String {
        val trailing = source.takeLastWhile { it in ".,!" }
        val raw = source.dropLast(trailing.length)
        val parts = raw.split('@', limit = 2)
        if (parts.size != 2) return source
        return "${readLocal(parts[0])} 골뱅이 ${readEmailHost(parts[1])}$trailing"
    }

    private fun readHost(host: String): String = host.split('.').filter(String::isNotEmpty).joinToString(" 점 ") { label ->
        when {
            label.equals("www", true) -> "더블유 더블유 더블유"
            label.equals("kr", true) -> "케이 알"
            else -> readWordOrSpelling(label)
        }
    }

    private fun readWordOrSpelling(value: String): String = when (value.lowercase()) {
        "section" -> "섹션"
        "cafe" -> "카페"
        "naver" -> "네이버"
        "home" -> "홈"
        "com" -> "컴"
        "net" -> "넷"
        "org" -> "오그"
        else -> readComponent(value)
    }

    private fun readLocal(local: String): String = Regex("[A-Za-z]+|\\d+|.").findAll(local).joinToString(" ") { match ->
        when {
            match.value.all(Char::isDigit) -> NumberReader.digits(match.value, zeroAsGong = true)
            match.value.all(Char::isLetter) -> EnglishReader.readToken(match.value)
            match.value == "." -> "점"
            else -> readComponent(match.value)
        }
    }

    private fun readEmailHost(host: String): String = host.split('.').joinToString(" 점 ") { label ->
        if (label.equals("kr", true)) "케이 알" else readWordOrSpelling(label)
    }

    private fun readQueryValue(value: String): String = readComponent(value)

    private fun readComponent(value: String): String {
        val lexical = when (value.lowercase()) {
            "ca-fe" -> "카페"
            else -> null
        }
        if (lexical != null) return lexical
        return Regex("[A-Za-z]+|[0-9]+|.").findAll(value).joinToString(" ") { match ->
            val part = match.value
            when {
                part.all { it in '0'..'9' } -> NumberReader.digits(part, zeroAsGong = true).replace(" ", "")
                part.all { it in 'A'..'Z' || it in 'a'..'z' } -> readWordOrSpellingDirect(part)
                else -> when (part) {
                    "-" -> "하이픈"; "_" -> "언더스코어"; "." -> "점"; ":" -> "콜론"
                    "/" -> "슬래시"; "%" -> "퍼센트"; "+" -> "플러스"; "=" -> "이퀄"
                    "@" -> "골뱅이"; "#" -> "샵"; "&" -> "앤드"; "?" -> "물음표"
                    else -> part
                }
            }
        }
    }

    private fun readWordOrSpellingDirect(value: String): String = when (value.lowercase()) {
        "section" -> "섹션"
        "cafe" -> "카페"
        "naver" -> "네이버"
        "home" -> "홈"
        "example" -> "이그잼플"
        "com" -> "컴"
        "net" -> "넷"
        "org" -> "오그"
        else -> EnglishReader.readToken(value)
    }
}
