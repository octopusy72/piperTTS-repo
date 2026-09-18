package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.util.Log;
import com.yiwoosolution.koreantts.speech.KoreanSpeechNormalizer;
import com.yiwoosolution.koreantts.speech.SpeechNormalization;
import java.io.ByteArrayInputStream;

/** Uses the production Legacy normalizer verbatim, then adapts residual Latin text for RAW59. */
final class LegacyKoreanNormalizerBridge {
  private static volatile KoreanSpeechNormalizer production;

  private LegacyKoreanNormalizerBridge() {}

  static String normalize(Context context, String input) {
    String value = input == null ? "" : input;
    // Resolver output is commonly already Hangul. For text with no semantic
    // spans (digits, Latin, URLs, symbols), avoid invoking the heavyweight
    // rule detector; this is equivalent to the normalizer's spacing-preserving
    // result while keeping all structured inputs on the original path.
    if (context != null && isPlainHangulSentence(value)) {
      Log.i("YiwooPiperKo", "NORMALIZER_FAST_PATH kind=plain_hangul");
      return value.trim().replaceAll("\\s+", " ");
    }
    if (context != null) value = ReadingRuleEngine.apply(value, new ReadingRuleRepository(context));
    value = LegacyTextNormalizer.applyPreLegacyExtensions(value);
    KoreanSpeechNormalizer normalizer = context == null ? new KoreanSpeechNormalizer() : production(context);
    SpeechNormalization detailed = normalizer.normalizeDetailed(value);
    if (context != null && input != null && input.contains("오늘은 1/3과 3/4의 크기를 비교합니다")) {
      String semantic = detailed.getStages() == null ? detailed.getSpokenText() : detailed.getStages().getSemantic();
      Log.i("YiwooPiperKo", "KOREAN_SLASH_TRACE RAW_TEXT=" + input
          + " AFTER_PROTECTION=" + value
          + " AFTER_DATE_RULE=" + value
          + " AFTER_FRACTION_RULE=" + semantic
          + " SPANS=" + detailed.getSpans());
    }
    if (context != null && input != null && (input.contains("10~20m") || input.contains("x²") || input.contains("x³"))) {
      String semantic = detailed.getStages() == null ? detailed.getSpokenText() : detailed.getStages().getSemantic();
      Log.i("YiwooPiperKo", "KOREAN_RANGE_MATH_TRACE RAW=" + input
          + " AFTER_PROTECTION=" + value
          + " AFTER_RANGE=" + value
          + " AFTER_UNIT=" + value
          + " AFTER_MATH_RULE=" + semantic
          + " AFTER_SYMBOL_RULE=" + semantic
          + " AFTER_CANONICAL_NORMALIZER=" + detailed.getSpokenText());
    }
    // Legacy leaves CMU-known Latin words for the frontend's ARPAbet-to-Hangul
    // conversion. Apply that same runtime step before the RAW-59 safety pass;
    // Hangul-context loanword replacements are already Hangul and are untouched.
    String legacyConverted = context == null
        ? detailed.getSpokenText()
        : LegacyCmuEnglishConverter.convert(context, detailed.getSpokenText());
    if (context != null && (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        && input != null && (input.contains("computer") || input.contains("software") || input.equals("컴퓨터"))) {
      Log.i("YiwooPiperKo", "LEGACY_CMU_TRACE INPUT=" + input
          + " AFTER_NORMALIZER=" + detailed.getSpokenText()
          + " AFTER_ARPABET_HANGUL=" + legacyConverted
          + " FINAL=" + LegacyTextNormalizer.adaptLegacyOutputForRaw59(legacyConverted));
    }
    return LegacyTextNormalizer.adaptLegacyOutputForRaw59(legacyConverted);
  }

  private static boolean isPlainHangulSentence(String value) {
    if (value == null || value.isEmpty()) return true;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if ((c >= '\uAC00' && c <= '\uD7A3') || Character.isWhitespace(c) || ".,!?'\u2019\u2018\u2026".indexOf(c) >= 0) continue;
      return false;
    }
    return true;
  }

  private static boolean isHangulNumericSentence(String value) {
    if (value == null || value.isEmpty()) return true;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if ((c >= '\uAC00' && c <= '\uD7A3') || (c >= '0' && c <= '9')
          || Character.isWhitespace(c) || ".,!?'\u2019\u2018…:/.+-".indexOf(c) >= 0) continue;
      return false;
    }
    return true;
  }

  private static KoreanSpeechNormalizer production(Context context) {
    KoreanSpeechNormalizer current = production;
    if (current != null) return current;
    synchronized (LegacyKoreanNormalizerBridge.class) {
      if (production == null) {
        long startNs = System.nanoTime();
        Log.i("YiwooPiperKo", "NORMALIZER_INIT_ENTER");
        try {
          // PronunciationFrontend resolves CMUdict/Latin before this bridge.
          // Do not expand the 3.7 MiB CMUdict into 126k transient Strings and
          // a HashSet on first Korean synthesis; the normalizer's own rules
          // and loanword table remain unchanged.
          production = KoreanSpeechNormalizer.Companion.production(
              new ByteArrayInputStream(new byte[0]),
              context.getAssets().open("normalization/android_loanwords.tsv"));
          Log.i("YiwooPiperKo", "NORMALIZER_INIT_READY elapsedMs=" + ((System.nanoTime() - startNs) / 1_000_000L));
        } catch (Exception error) {
          throw new IllegalStateException("LEGACY_NORMALIZER_ASSET_LOAD_FAILED", error);
        }
      }
      return production;
    }
  }
}
