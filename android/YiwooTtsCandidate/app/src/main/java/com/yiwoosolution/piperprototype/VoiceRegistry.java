package com.yiwoosolution.piperprototype;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Central registry; unavailable future voices are never exposed as selectable. */
final class VoiceRegistry {
  static final VoiceDescriptor KOREAN_8523 = new VoiceDescriptor(
      "ko_8523_piper_v2_1000k", "YIWOO Korean Voice", "ko-KR", "8523",
      "piper-low", "V2-special-token", "V2-62", "models/ko_8523_piper_v2_1000k.onnx", 16000, true, true, "production_1000k");
  static final VoiceDescriptor ENGLISH = new VoiceDescriptor(
      "en_ljspeech_piper_1m", "YIWOO English Voice", "en-US", "0",
      "piper-low", "eSpeak en-us", "LJSpeech166", "models/en_ljspeech_piper_1m.onnx", 22050,
      com.yiwoosolution.koreantts.BuildConfig.ENGLISH_VOICE_INCLUDED,
      com.yiwoosolution.koreantts.BuildConfig.ENGLISH_VOICE_INCLUDED, "production_1000k");
  static final VoiceDescriptor ACTIVE = KOREAN_8523;

  private VoiceRegistry() {}
  static boolean englishIncluded() { return com.yiwoosolution.koreantts.BuildConfig.ENGLISH_VOICE_INCLUDED; }
  static boolean selectedEnglish(android.content.Context context) {
    return englishIncluded() && context.getSharedPreferences("product_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("voice_language_english", false);
  }
  static List<VoiceDescriptor> all() { return Collections.unmodifiableList(Arrays.asList(KOREAN_8523, ENGLISH)); }
  static VoiceDescriptor forLocale(String locale) {
    if (locale == null) return null;
    if (locale.toLowerCase(java.util.Locale.US).startsWith("ko")) return ACTIVE;
    if (locale.toLowerCase(java.util.Locale.US).startsWith("en")) return ENGLISH;
    return null;
  }
}
