package com.yiwoosolution.piperprototype;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

/** Legacy-compatible read-only PROCESS_TEXT entrypoint using Android TTS. */
public final class ProcessTextActivity extends Activity {
  private TextToSpeech tts;
  private boolean utteranceCompleted;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    boolean enabled = getSharedPreferences("product_settings", MODE_PRIVATE).getBoolean("process_text_enabled", false);
    android.util.Log.i("YiwooSelectedText", "YIWOO_SELECTED_TEXT_RECEIVED enabled=" + enabled);
    if (!enabled) { android.util.Log.i("YiwooSelectedText", "YIWOO_SELECTED_TEXT_REJECT reason=DISABLED"); finish(); return; }
    CharSequence selected = getIntent().getCharSequenceExtra(IntentCompat.EXTRA_PROCESS_TEXT);
    final String text = selected == null ? "" : selected.toString().trim();
    if (text.isEmpty()) { android.util.Log.i("YiwooSelectedText", "YIWOO_SELECTED_TEXT_REJECT reason=EMPTY"); finish(); return; }
    boolean english = VoiceRegistry.selectedEnglish(this);
    android.util.Log.i("YiwooSelectedText", "YIWOO_SELECTED_TEXT_ACCEPT chars=" + text.length() + " language=" + (english ? "en-US" : "ko-KR"));
    NotificationPriorityState.interruptLowPriority();
    tts = new TextToSpeech(this, result -> {
      if (result != TextToSpeech.SUCCESS || tts == null) { android.util.Log.i("YiwooSelectedText", "YIWOO_SELECTED_TEXT_INIT_ERROR"); finish(); return; }
      tts.setLanguage(english ? Locale.US : Locale.KOREA);
      tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
        @Override public void onStart(String id) {}
        @Override public void onDone(String id) { utteranceCompleted = true; android.util.Log.i("YiwooSelectedText", "YIWOO_SELECTED_TEXT_ACTIVITY_FINISH"); runOnUiThread(ProcessTextActivity.this::finish); }
        @Override public void onError(String id) { android.util.Log.i("YiwooSelectedText", "YIWOO_SELECTED_TEXT_ERROR"); runOnUiThread(ProcessTextActivity.this::finish); }
      });
      android.os.Bundle params = new android.os.Bundle();
      params.putString(NotificationPriorityState.PARAM_ORIGIN, "selected_text");
      SpeechPlaybackSettings.applyTo(this, tts);
      if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "process-text-" + System.nanoTime()) != TextToSpeech.SUCCESS) finish();
    }, getPackageName());
  }
  @Override protected void onDestroy() {
    android.util.Log.i("YiwooSelectedText", "YIWOO_SELECTED_TEXT_ACTIVITY_DESTROY completed=" + utteranceCompleted);
    if (tts != null && !utteranceCompleted) { tts.stop(); tts.shutdown(); }
    tts=null;
    super.onDestroy();
  }
  private static final class IntentCompat { static final String EXTRA_PROCESS_TEXT = "android.intent.extra.PROCESS_TEXT"; }
}
