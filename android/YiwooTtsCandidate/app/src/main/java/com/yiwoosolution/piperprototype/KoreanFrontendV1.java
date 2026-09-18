package com.yiwoosolution.piperprototype;

import java.util.HashMap;
import java.util.Map;

/** Frozen compatibility-jamo frontend used by the 4517 V1 baseline export. */
final class KoreanFrontendV1 {
  private static final String L = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
  private static final String V = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
  private static final String[] T = {"", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"};
  private static final Map<String, Integer> IDS = new HashMap<>();
  private static final Map<Character, Character> FALLBACK = new HashMap<>();
  static {
    String symbols = " !$,.?^_ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㅏㅐㅓㅔㅗㅜㅡㅣ";
    for (int i = 0; i < symbols.length(); i++) IDS.put(String.valueOf(symbols.charAt(i)), i);
    String from = "ㅑㅒㅕㅖㅘㅙㅚㅛㅝㅞㅟㅢ";
    String to = "ㅏㅐㅓㅔㅏㅐㅔㅗㅓㅔㅜㅡ";
    for (int i = 0; i < from.length(); i++) FALLBACK.put(from.charAt(i), to.charAt(i));
  }
  static String normalize(String text) { return text.replace("GTX-A", "지티엑스 에이").replace("372번", "삼백칠십이 번"); }
  static String phonemes(String text) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < text.length();) {
      int cp = text.codePointAt(i); i += Character.charCount(cp);
      if (cp >= 0xAC00 && cp <= 0xD7A3) {
        int n = cp - 0xAC00; out.append(L.charAt(n / 588)).append(V.charAt((n % 588) / 28)).append(T[n % 28]);
      } else out.appendCodePoint(cp);
    }
    return out.toString();
  }
  static long[] ids(String phonemes) {
    long[] result = new long[2 + phonemes.length() * 2 + 1]; int p = 0;
    result[p++] = IDS.get("^"); result[p++] = IDS.get("_");
    for (int i = 0; i < phonemes.length(); i++) {
      char symbol = phonemes.charAt(i); Integer id = IDS.get(String.valueOf(symbol));
      if (id == null) id = IDS.get(String.valueOf(FALLBACK.get(symbol)));
      if (id == null) throw new IllegalArgumentException("unsupported V1 phoneme=" + symbol);
      result[p++] = id; result[p++] = IDS.get("_");
    }
    result[p] = IDS.get("$"); return result;
  }
}
