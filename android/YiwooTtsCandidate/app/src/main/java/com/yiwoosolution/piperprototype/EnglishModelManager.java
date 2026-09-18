package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.util.Log;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** English Lessac frontend + ONNX session, separate from Korean RAW59. */
final class EnglishModelManager {
  private static final String TAG = "YiwooPiperEn";
  private static final Pattern LANGUAGE_MARK = Pattern.compile("\\([^)]*\\)");
  private final Context context;
  private OrtEnvironment environment;
  private OrtSession session;
  private Map<String, long[]> ids;
  private int sampleRate = 16000;
  private int numSpeakers = 1;
  private float noiseScale = 0.667f, lengthScale = 1f, noiseW = 0.8f;
  private volatile boolean initialized;
  private final java.util.concurrent.ScheduledExecutorService lifecycleExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "yiwoo-english-lifecycle"));
  private java.util.concurrent.ScheduledFuture<?> releaseTask;
  private long retentionGeneration;
  private volatile String initState = "NOT_STARTED";
  private static volatile double lastInferenceMs = Double.NaN;
  private static volatile int lastPcmSamples;
  private static volatile EnglishModelManager INSTANCE;

  private EnglishModelManager(Context context) { this.context = context.getApplicationContext(); }

  static EnglishModelManager shared(Context context) {
    EnglishModelManager value = INSTANCE;
    if (value != null) return value;
    synchronized (EnglishModelManager.class) {
      if (INSTANCE == null) INSTANCE = new EnglishModelManager(context);
      return INSTANCE;
    }
  }

  int sampleRate() { return sampleRate; }
  String initState() { return initState; }
  boolean isReady() { return initialized; }

  void onRetentionPolicyChanged() {
    lifecycleExecutor.execute(() -> { synchronized (this) { scheduleRetentionLocked(); } });
  }

  private void cancelReleaseLocked() {
    retentionGeneration++;
    if (releaseTask != null) { releaseTask.cancel(false); releaseTask = null; }
  }

  private void scheduleRetentionLocked() {
    cancelReleaseLocked();
    EngineRetentionSettings.Mode mode = EngineRetentionSettings.mode(context);
    if (!initialized || mode == EngineRetentionSettings.Mode.ALWAYS) return;
    long delay = mode == EngineRetentionSettings.Mode.ON_DEMAND ? 0L : EngineRetentionSettings.idleMinutes(context) * 60_000L;
    long generation = retentionGeneration;
    releaseTask = lifecycleExecutor.schedule(() -> { synchronized (this) {
      if (generation != retentionGeneration || !initialized) return;
      if (mode != EngineRetentionSettings.mode(context)) { scheduleRetentionLocked(); return; }
      try { session.close(); } catch (Exception error) { Log.w(TAG, "ENGLISH_RELEASE_FAILED", error); }
      session = null; initialized = false; initState = "NOT_STARTED"; ids = null;
      Log.i(TAG, "ENGLISH_MODEL_RELEASED reason=idle");
      RuntimeRetentionService.runtimeChanged();
    } }, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
  }

  void preloadAsync() {
    RuntimeRetentionService.ensureStarted(context);
    RuntimePreloadCoordinator.prepare(context, true);
  }

  void preloadIfConfigured() {
    if (EngineRetentionSettings.mode(context) == EngineRetentionSettings.Mode.ON_DEMAND) {
      Log.i(TAG, "ENGLISH_PRELOAD_SKIPPED policy=ON_DEMAND");
      return;
    }
    preloadAsync();
  }

  synchronized void ensureReady() throws Exception {
    cancelReleaseLocked();
    if (initialized) return;
    if ("NOT_STARTED".equals(initState)) initState = "INITIALIZING";
    long started = System.nanoTime();
    long espeakStart = System.nanoTime();
    Log.i(TAG, "ENGLISH_TRACE T5_ESPEAK_INIT_START ns=" + espeakStart);
    EnglishEspeak.ensureReady(context);
    Log.i(TAG, "ENGLISH_TRACE T6_ESPEAK_INIT_END ns=" + System.nanoTime() + " elapsedMs=" + ((System.nanoTime() - espeakStart) / 1_000_000.0));
    JSONObject config = new JSONObject(readAsset("models/en_US-lessac-low.onnx.json"));
    JSONObject audio = config.getJSONObject("audio");
    sampleRate = audio.getInt("sample_rate");
    numSpeakers = config.optInt("num_speakers", 1);
    JSONObject inference = config.optJSONObject("inference");
    if (inference != null) {
      noiseScale = (float) inference.optDouble("noise_scale", noiseScale);
      lengthScale = (float) inference.optDouble("length_scale", lengthScale);
      noiseW = (float) inference.optDouble("noise_w", noiseW);
    }
    ids = loadIds(config.getJSONObject("phoneme_id_map"));
    if (ids.size() != 154 || !ids.containsKey("^") || !ids.containsKey("_") || !ids.containsKey("$")) throw new IllegalStateException("ENGLISH_LESSAC_MAP_CONTRACT_MISMATCH");
    long modelStart = System.nanoTime();
    Log.i(TAG, "ENGLISH_TRACE T1_MODEL_ASSET_START ns=" + modelStart);
    File model = copyAsset("models/en_US-lessac-low.onnx", "en_lessac_low.onnx");
    Log.i(TAG, "ENGLISH_TRACE T2_MODEL_ASSET_READY ns=" + System.nanoTime() + " elapsedMs=" + ((System.nanoTime() - modelStart) / 1_000_000.0));
    long sessionStart = System.nanoTime();
    Log.i(TAG, "ENGLISH_TRACE T3_ORT_SESSION_START ns=" + sessionStart);
    environment = OrtEnvironment.getEnvironment();
    session = environment.createSession(model.getAbsolutePath(), new OrtSession.SessionOptions());
    Log.i(TAG, "ENGLISH_TRACE T4_ORT_SESSION_END ns=" + System.nanoTime() + " elapsedMs=" + ((System.nanoTime() - sessionStart) / 1_000_000.0));
    initialized = true;
    initState = "READY";
    Log.i(TAG, "ENGLISH_MODEL_READY model=en_US-lessac-low sampleRate=" + sampleRate + " speakers=" + numSpeakers + " mapSymbols=" + ids.size() + " loadMs=" + ((System.nanoTime() - started) / 1_000_000.0));
  }

  void synthesizeChunks(String text, LongTextStreamingSynthesizer.ChunkListener listener) throws Exception {
    synthesizeChunks(text, 1f, listener);
  }

  synchronized void synthesizeChunks(String text, float rate, LongTextStreamingSynthesizer.ChunkListener listener) throws Exception {
    RuntimeRetentionService.ensureStarted(context);
    try {
    long requestStarted = System.nanoTime();
    Log.i(TAG, "ENGLISH_REQUEST_START thread=" + Thread.currentThread().getName() + " textLength=" + (text == null ? 0 : text.length()) + " traceT0Ns=" + requestStarted);
    ensureReady();
    long frontendStarted = System.nanoTime();
    Log.i(TAG, "ENGLISH_TRACE T7_FRONTEND_START ns=" + frontendStarted + " sinceT0Ms=" + ((frontendStarted - requestStarted) / 1_000_000.0));
    List<Segment> segments = segments(text);
    Log.i(TAG, "ENGLISH_FRONTEND_DONE segments=" + segments.size() + " elapsedMs=" + ((System.nanoTime() - frontendStarted) / 1_000_000.0));
    if (segments.isEmpty()) throw new IllegalArgumentException("EMPTY_ENGLISH_SYNTHESIS_TEXT");
    double totalInference = 0; int totalSamples = 0;
    for (int i = 0; i < segments.size(); i++) {
      Segment segment = segments.get(i);
      Log.i(TAG, "ENGLISH_SEGMENT index=" + i + " total=" + segments.size() + " boundary=" + segment.boundary + " phonemes=" + segment.phonemes.size() + " SYNTH_START");
      long[] sequence = idsFor(segment.phonemes);
      Log.i(TAG, "ENGLISH_TRACE T8_TOKEN_IDS_READY index=" + i + " tokens=" + sequence.length + " ns=" + System.nanoTime() + " sinceT0Ms=" + ((System.nanoTime() - requestStarted) / 1_000_000.0));
      short[] pcm = run(sequence, rate);
      totalInference += lastInferenceMs; totalSamples += pcm.length;
      int pause = i == segments.size() - 1 ? 0 : SentenceBoundaryPausePolicy.effectivePause(context, segment.boundary);
      Log.i(TAG, "ENGLISH_CHUNK index=" + i + " tokens=" + sequence.length + " pcmFrames=" + pcm.length + " ortMs=" + lastInferenceMs + " pauseMs=" + pause);
      listener.onChunk(pcm, i, segments.size(), lastInferenceMs, pause);
    }
    lastInferenceMs = totalInference; lastPcmSamples = totalSamples;
    Log.i(TAG, "ENGLISH_REQUEST_END elapsedMs=" + ((System.nanoTime() - requestStarted) / 1_000_000.0) + " ortMs=" + totalInference + " pcmFrames=" + totalSamples);
    } finally { scheduleRetentionLocked(); }
  }

  private short[] run(long[] sequence, float rate) throws Exception {
    try (OnnxTensor input = OnnxTensor.createTensor(environment, LongBuffer.wrap(sequence), new long[]{1, sequence.length});
         OnnxTensor lengths = OnnxTensor.createTensor(environment, LongBuffer.wrap(new long[]{sequence.length}), new long[]{1});
         OnnxTensor scales = OnnxTensor.createTensor(environment, new float[]{noiseScale, SpeechRatePolicy.lengthScale(lengthScale, rate), noiseW})) {
      Map<String, OnnxTensor> feed = new HashMap<>(); feed.put("input", input); feed.put("input_lengths", lengths); feed.put("scales", scales);
      long started = System.nanoTime();
      Log.i(TAG, "ENGLISH_TRACE T9_FIRST_ORT_START ns=" + started);
      try (OrtSession.Result result = session.run(feed)) {
        float[] audio = ((float[][][][]) result.get(0).getValue())[0][0][0];
        short[] pcm = new short[audio.length];
        for (int i = 0; i < audio.length; i++) pcm[i] = (short)(Math.max(-1f, Math.min(1f, audio[i])) * 32767f);
        lastInferenceMs = (System.nanoTime() - started) / 1_000_000.0; lastPcmSamples = pcm.length;
        Log.i(TAG, "ENGLISH_TRACE T10_FIRST_ORT_END ns=" + System.nanoTime() + " elapsedMs=" + lastInferenceMs);
        return pcm;
      }
    }
  }

  private List<Segment> segments(String text) throws Exception {
    List<Segment> out = new ArrayList<>();
    String raw = EnglishEspeak.phonemize(context, text == null ? "" : text);
    for (String line : raw.split("\\n")) {
      if (line.isEmpty()) continue;
      String[] parts = line.split("\\t", -1); if (parts.length < 3) continue;
      String phones = Normalizer.normalize(LANGUAGE_MARK.matcher(parts[0]).replaceAll("") + parts[1], Normalizer.Form.NFD);
      List<String> symbols = new ArrayList<>();
      for (int i = 0; i < phones.length();) { int cp = phones.codePointAt(i); i += Character.charCount(cp); symbols.add(new String(Character.toChars(cp))); }
      if (",".equals(parts[1]) || ":".equals(parts[1]) || ";".equals(parts[1])) symbols.add(" ");
      SentenceBoundaryPausePolicy.Boundary boundary = "1".equals(parts[2]) ? (".".equals(parts[1]) || "?".equals(parts[1]) || "!".equals(parts[1]) ? SentenceBoundaryPausePolicy.Boundary.SENTENCE : SentenceBoundaryPausePolicy.Boundary.NONE) : SentenceBoundaryPausePolicy.Boundary.NONE;
      if (!symbols.isEmpty()) out.add(new Segment(symbols, boundary));
    }
    if (!out.isEmpty()) { Segment last = out.get(out.size() - 1); out.set(out.size() - 1, new Segment(last.phonemes, SentenceBoundaryPausePolicy.Boundary.NONE)); }
    return out;
  }

  private long[] idsFor(List<String> symbols) throws Exception {
    ArrayList<Long> values = new ArrayList<>(); add(values, ids.get("^")); add(values, ids.get("_"));
    for (String symbol : symbols) { long[] mapped = ids.get(symbol); if (mapped == null) throw new IllegalArgumentException("UNSUPPORTED_ENGLISH_PHONEME=" + symbol); add(values, mapped); add(values, ids.get("_")); }
    add(values, ids.get("$")); long[] out = new long[values.size()]; for (int i = 0; i < out.length; i++) out[i] = values.get(i); return out;
  }
  private static void add(ArrayList<Long> dst, long[] values) { if (values != null) for (long value : values) dst.add(value); }
  private Map<String, long[]> loadIds(JSONObject object) throws Exception {
    Map<String, long[]> result = new HashMap<>();
    java.util.Iterator<String> keys = object.keys();
    while (keys.hasNext()) { String key = keys.next(); JSONArray array = object.getJSONArray(key); long[] values = new long[array.length()]; for (int i = 0; i < values.length; i++) values[i] = array.getLong(i); result.put(key, values); }
    return result;
  }
  private String readAsset(String path) throws Exception { try (InputStream in = context.getAssets().open(path)) { byte[] b = new byte[in.available()]; int n = in.read(b); return new String(b, 0, n, "UTF-8"); } }
  private File copyAsset(String asset, String name) throws Exception { File file = new File(context.getCacheDir(), name); if (file.isFile() && file.length() > 100_000_000) return file; try (InputStream in = context.getAssets().open(asset); FileOutputStream out = new FileOutputStream(file)) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n); } return file; }
  static double lastInferenceMs() { return lastInferenceMs; }
  static int lastPcmSamples() { return lastPcmSamples; }
  private static final class Segment { final List<String> phonemes; final SentenceBoundaryPausePolicy.Boundary boundary; Segment(List<String> p, SentenceBoundaryPausePolicy.Boundary b) { phonemes = p; boundary = b; } }
}
