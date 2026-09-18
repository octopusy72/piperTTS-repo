package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;

/** App requests follow Android defaults unless the user chooses an app override. */
public final class SpeechPlaybackSettings {
  private SpeechPlaybackSettings() {}
  private static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("product_settings", Context.MODE_PRIVATE);
  }
  public static boolean followsSystem(Context c) {
    return prefs(c).getBoolean("speech_follow_system", true);
  }
  public static void followSystem(Context c, boolean value) {
    if (!value) {
      float rate = rate(c), pitch = pitch(c);
      prefs(c).edit().putFloat("speech_rate", rate).putFloat("speech_pitch", pitch)
          .putBoolean("speech_follow_system", false).apply();
    } else prefs(c).edit().putBoolean("speech_follow_system", true).apply();
  }
  private static float value(Context c, String key, String systemKey) {
    float v = followsSystem(c)
        ? Settings.Secure.getInt(c.getContentResolver(), systemKey, 100) / 100f
        : prefs(c).getFloat(key, 1f);
    return SpeechPcmProcessor.bounded(v);
  }
  public static float rate(Context c) { return value(c, "speech_rate", "tts_default_rate"); }
  public static float pitch(Context c) { return value(c, "speech_pitch", "tts_default_pitch"); }
  public static void set(Context c, boolean pitch, float value) {
    prefs(c).edit().putFloat(pitch ? "speech_pitch" : "speech_rate", value).apply();
  }
  public static void applyTo(Context c, TextToSpeech tts) {
    tts.setSpeechRate(rate(c));
    tts.setPitch(pitch(c));
  }
}
