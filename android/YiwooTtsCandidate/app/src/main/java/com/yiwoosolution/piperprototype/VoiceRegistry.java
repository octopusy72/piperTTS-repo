package com.yiwoosolution.piperprototype;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Central registry; unavailable future voices are never exposed as selectable. */
final class VoiceRegistry {
  static final VoiceDescriptor KOREAN = new VoiceDescriptor(
      "ko_4517_piper5k", "Korean Development Voice (Speaker 4517)", "ko-KR", "4517",
      "piper-low", "v1-compat-jamo-35", "v1", "models/lsr4517_piper_low_step5000.onnx",
      16000, false, false, "development_baseline");
  static final VoiceDescriptor KOREAN_8523 = new VoiceDescriptor(
      "ko_8523_piper_v2_500k", "YIWOO Korean Voice", "ko-KR", "8523",
      "piper-low", "V2-special-token", "V2-62", "models/ko_8523_piper_v2_500k.onnx", 16000, true, true, "production_500k");
  static final VoiceDescriptor ENGLISH = new VoiceDescriptor(
      "en_lessac_low", "English Lessac Low", "en-US", "0",
      "piper-low", "eSpeak en-us", "Lessac154", "models/en_US-lessac-low.onnx", 16000,
      com.yiwoosolution.koreantts.BuildConfig.ENGLISH_VOICE_INCLUDED,
      com.yiwoosolution.koreantts.BuildConfig.ENGLISH_VOICE_INCLUDED, "development_only_not_in_release");
  static final VoiceDescriptor ACTIVE = KOREAN_8523;

  private VoiceRegistry() {}
  static boolean englishIncluded() { return com.yiwoosolution.koreantts.BuildConfig.ENGLISH_VOICE_INCLUDED; }
  static boolean selectedEnglish(android.content.Context context) {
    return englishIncluded() && context.getSharedPreferences("product_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("voice_language_english", false);
  }
  static List<VoiceDescriptor> all() { return Collections.unmodifiableList(Arrays.asList(KOREAN, KOREAN_8523, ENGLISH)); }
  static VoiceDescriptor forLocale(String locale) {
    if (locale == null) return null;
    if (locale.toLowerCase(java.util.Locale.US).startsWith("ko")) return ACTIVE;
    if (locale.toLowerCase(java.util.Locale.US).startsWith("en")) return ENGLISH;
    return null;
  }
}
