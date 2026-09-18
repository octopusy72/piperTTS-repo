package com.yiwoosolution.piperprototype;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;

/** Debug-only framework route probe; it uses android.speech.tts.TextToSpeech, not helpers. */
public final class FrameworkEnglishSmokeActivity extends Activity {
  private static final String TAG = "YiwooEnglishSmoke";
  private TextToSpeech tts;
  private int phase;
  private int controlledRuns;
  private int controlledCompleted;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    final String probeText = getIntent().getStringExtra("probe_text");
    final boolean customEnglishProbe = getIntent().getBooleanExtra("probe_english", false);
    final int probeVariant = getIntent().getIntExtra("probe_variant", 0);
    controlledRuns = Math.max(1, getIntent().getIntExtra("controlled_runs", 1));
    final boolean koreanProbe = probeText != null && !customEnglishProbe;
    final String englishText = probeVariant == 1
        ? "The package will arrive tomorrow afternoon. Please check your delivery information."
        : probeVariant == 2
        ? "Your appointment is scheduled for three thirty this afternoon. Please arrive ten minutes early."
        : "Hello, this is YIWOO TTS. The package will arrive tomorrow afternoon. Please check your delivery information.";
    tts = new TextToSpeech(this, result -> {
      Log.i(TAG, "INIT result=" + result);
      if (result != TextToSpeech.SUCCESS) { finish(); return; }
      Log.i(TAG, "IS_LANGUAGE_AVAILABLE ja-JP result=" + tts.isLanguageAvailable(Locale.JAPAN));
      int language = tts.setLanguage(koreanProbe ? Locale.KOREA : Locale.US);
      Log.i(TAG, "SET_LANGUAGE " + (koreanProbe ? "ko-KR" : "en-US") + " result=" + language);
      if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        tts.setSpeechRate(getIntent().getIntExtra("probe_rate_percent", 100) / 100f);
        tts.setPitch(getIntent().getIntExtra("probe_pitch_percent", 100) / 100f);
      }
      tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
        @Override public void onStart(String id) { Log.i(TAG, "UTTERANCE_START id=" + id); }
        @Override public void onDone(String id) {
          Log.i(TAG, "UTTERANCE_DONE id=" + id);
          if (!koreanProbe && controlledCompleted + 1 < controlledRuns) {
            controlledCompleted++;
            Log.i(TAG, "CONTROLLED_RUN_NEXT index=" + (controlledCompleted + 1) + " total=" + controlledRuns);
            tts.speak(englishText, TextToSpeech.QUEUE_FLUSH, null, "english-smoke-" + (controlledCompleted + 1));
            return;
          }
          if (!koreanProbe && phase == 0) {
            phase = 1;
            int ko = tts.setLanguage(Locale.KOREA);
            Log.i(TAG, "SET_LANGUAGE ko-KR result=" + ko);
            int result = tts.speak("오늘은 맑습니다.", TextToSpeech.QUEUE_FLUSH, null, "korean-return");
            Log.i(TAG, "SPEAK ko-return result=" + result);
          } else runOnUiThread(FrameworkEnglishSmokeActivity.this::finish);
        }
        @Override public void onError(String id) { Log.e(TAG, "UTTERANCE_ERROR id=" + id); runOnUiThread(FrameworkEnglishSmokeActivity.this::finish); }
      });
      int speak = tts.speak(koreanProbe ? probeText : (customEnglishProbe ? probeText : englishText), TextToSpeech.QUEUE_FLUSH, null, koreanProbe ? "korean-parity-probe" : "english-smoke");
      Log.i(TAG, "SPEAK result=" + speak);
    }, getPackageName());
  }
  @Override protected void onDestroy() { if (tts != null) { tts.stop(); tts.shutdown(); } super.onDestroy(); }
}
