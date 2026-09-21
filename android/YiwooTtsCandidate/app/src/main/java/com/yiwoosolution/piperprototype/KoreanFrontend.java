package com.yiwoosolution.piperprototype;

import java.util.HashMap;
import java.util.Map;

/** Frozen special-token frontend for the 8523 production model lineage. */
final class KoreanFrontend {
  private static final String L = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
  private static final String V = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
  private static final String[] T = {"", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"};
  private static final Map<String, Integer> IDS = new HashMap<>();
  static {
    String symbols = " !$,.?^_ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣㄳㄵㄶㄺㄻㄼㄽㄾㄿㅀㅄ";
    for (int i = 0; i < symbols.length(); i++) IDS.put(String.valueOf(symbols.charAt(i)), i);
  }
  /** Keeps the trained special-token contract while using the shared normalizer. */
  static String normalize(String text) {
    return safeModelText(LegacyKoreanNormalizerBridge.normalize(null, text));
  }

  static String normalize(android.content.Context context, String text) {
    String normalized = LegacyKoreanNormalizerBridge.normalize(context, text);
    String safe = safeModelText(normalized);
    if (!safe.equals(normalized)) android.util.Log.w("YiwooPiperKo", "MODEL_TEXT_UNSUPPORTED_CHARACTERS_SANITIZED");
    return safe;
  }
  /** Last-resort boundary after semantic normalization, not a replacement for it.
   * Unknown code points become separators rather than joining neighboring words.
   * The ID encoder remains strict so internal vocabulary bugs are still visible.
   */
  static String safeModelText(String text) {
    StringBuilder safe = null;
    for (int i = 0; i < text.length();) {
      int cp = text.codePointAt(i);
      int next = i + Character.charCount(cp);
      boolean supported = (cp >= 0xAC00 && cp <= 0xD7A3)
          || (cp <= Character.MAX_VALUE && IDS.containsKey(String.valueOf((char) cp)));
      if (!supported && safe == null) safe = new StringBuilder(text.length()).append(text, 0, i);
      if (safe != null) {
        if (supported) safe.appendCodePoint(cp);
        else safe.append(' ');
      }
      i = next;
    }
    return safe == null ? text : safe.toString().trim();
  }
  static String phonemes(String text) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < text.length();) {
      int cp = text.codePointAt(i); i += Character.charCount(cp);
      if (cp >= 0xAC00 && cp <= 0xD7A3) {
        int n = cp - 0xAC00; int li = n / 588; int vi = (n % 588) / 28; int ti = n % 28;
        out.append(L.charAt(li)).append(V.charAt(vi)).append(T[ti]);
      } else out.appendCodePoint(cp);
    }
    return out.toString();
  }
  static long[] ids(String phonemes) {
    // Reference Piper topology used by the special-token training lineage.
    long[] result = new long[3 + phonemes.length() * 2]; int p = 0;
    result[p++] = 60; result[p++] = 59;
    for (int i = 0; i < phonemes.length(); i++) {
      Integer id = IDS.get(String.valueOf(phonemes.charAt(i)));
      if (id == null) throw new IllegalArgumentException("unsupported special-token phoneme=" + phonemes.charAt(i));
      result[p++] = id; result[p++] = 59;
    }
    result[p] = 61;
    return result;
  }
}
