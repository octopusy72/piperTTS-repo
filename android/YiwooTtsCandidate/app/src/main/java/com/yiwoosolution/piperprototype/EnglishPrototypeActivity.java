package com.yiwoosolution.piperprototype;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.concurrent.Executors;

/** Debug-only A/B prototype. Both variants synthesize with Korean 8523. */
public final class EnglishPrototypeActivity extends Activity {
  private static final String TAG = "YiwooEnglishKoProto";
  private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
  private EnglishToKoreanPronunciationPrototype prototype;
  private String original, current, approximation;
  private short[] currentPcm, prototypePcm;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    prototype = new EnglishToKoreanPronunciationPrototype(this);
    original = getIntent().getStringExtra("probe_text");
    if (original == null) original = "computer";
    if (getIntent().getBooleanExtra("normalize_only", false)) {
      Log.i(TAG, "NORMALIZE_ONLY_INPUT=" + original);
      Log.i(TAG, "NORMALIZE_ONLY_OUTPUT=" + KoreanFrontend.normalize(this, original));
      finish();
      return;
    }
    LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    Button currentButton = new Button(this); currentButton.setText("A Current");
    Button prototypeButton = new Button(this); prototypeButton.setText("B Prototype");
    root.addView(currentButton); root.addView(prototypeButton); setContentView(root);
    executor.execute(() -> prepare());
    currentButton.setOnClickListener(v -> play(currentPcm));
    prototypeButton.setOnClickListener(v -> play(prototypePcm));
  }

  private void prepare() {
    try {
      if (getIntent().getBooleanExtra("corpus", false)) {
        String[] items = {"computer", "software", "hardware", "internet", "server", "client", "browser", "website", "application", "program", "system", "summer", "vacation", "coffee", "camera", "hotel", "restaurant", "shopping", "music", "Google", "Microsoft", "YouTube", "OpenAI", "ChatGPT", "machine learning", "deep learning", "artificial intelligence", "computer science", "smart phone", "AI", "CPU", "USB-C", "Wi-Fi", "Bluetooth", "Android", "ONNX", "GTX-A"};
        for (String item : items) logComparison(item);
        return;
      }
      logComparison(original);
    } catch (Throwable error) { Log.e(TAG, "PROTOTYPE_ERROR", error); }
  }

  private void logComparison(String input) {
    try {
      EnglishToKoreanPronunciationPrototype.Result result = prototype.convert(input);
      // Debug-only A side uses the restored shared Legacy CMU runtime, not the
      // historical Java fallback that this comparison screen was created for.
      current = KoreanFrontend.normalize(this, result.original);
      approximation = result.approximation;
      Log.i(TAG, "INPUT=" + result.original);
      Log.i(TAG, "CURRENT_OUTPUT=" + current);
      Log.i(TAG, "RESTORED_LEGACY_OUTPUT=" + current);
      Log.i(TAG, "ESPEAK_PHONEMES=" + result.espeak);
      Log.i(TAG, "PROTOTYPE_OUTPUT=" + approximation);
      PiperModelManager manager = PiperModelManager.shared(this);
      currentPcm = manager.synthesize(current);
      prototypePcm = manager.synthesize(approximation);
      Log.i(TAG, "CURRENT_PCM_FRAMES=" + currentPcm.length);
      Log.i(TAG, "PROTOTYPE_PCM_FRAMES=" + prototypePcm.length);
    } catch (Throwable error) { Log.e(TAG, "PROTOTYPE_ITEM_ERROR input=" + input, error); }
  }

  private void play(short[] pcm) {
    if (pcm == null || pcm.length == 0) return;
    AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, 16000,
        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        Math.max(AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), pcm.length * 2), AudioTrack.MODE_STATIC);
    track.write(pcm, 0, pcm.length); track.play();
    new android.os.Handler(getMainLooper()).postDelayed(track::release, Math.max(100, pcm.length * 1000 / 16000 + 100));
  }
  @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
