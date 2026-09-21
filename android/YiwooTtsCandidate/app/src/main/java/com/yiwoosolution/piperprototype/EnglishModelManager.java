package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.util.Log;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.LongBuffer;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** English LJSpeech frontend + ONNX session, separate from Korean RAW59. */
final class EnglishModelManager {
  private static final String TAG = "YiwooPiperEn";
  private static final String MODEL_ASSET = "models/en_ljspeech_piper_1m.onnx";
  private static final String CONFIG_ASSET = "models/en_ljspeech_piper_1m.onnx.json";
  private static final String CACHE_NAME = "en_ljspeech_piper_1m.onnx";
  private static final long MODEL_SIZE = 63_446_931L;
  private static final String MODEL_SHA256 = "9dc11ae3388f9a9d0ae6f0b5e59a3a05c5df35d462044e7899c316d091b6d226";
  private static final int MAX_STREAMING_PHONEMES = 56;
  private static final int MIN_SPLIT_PHONEMES = 24;
  private static final long MAX_STARTUP_RESERVE_MS = 2_500L;
  private static final Pattern LANGUAGE_MARK = Pattern.compile("\\([^)]*\\)");
  private final Context context;
  private OrtEnvironment environment;
  private OrtSession session;
  private Map<String, long[]> ids;
  private int sampleRate = 22050;
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
    JSONObject config = new JSONObject(readAsset(CONFIG_ASSET));
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
    if (ids.size() != 166 || !ids.containsKey("^") || !ids.containsKey("_") || !ids.containsKey("$")) throw new IllegalStateException("ENGLISH_LJSPEECH_MAP_CONTRACT_MISMATCH");
    long modelStart = System.nanoTime();
    Log.i(TAG, "ENGLISH_TRACE T1_MODEL_ASSET_START ns=" + modelStart);
    File model = copyVerifiedModel();
    Log.i(TAG, "ENGLISH_TRACE T2_MODEL_ASSET_READY ns=" + System.nanoTime() + " elapsedMs=" + ((System.nanoTime() - modelStart) / 1_000_000.0));
    long sessionStart = System.nanoTime();
    Log.i(TAG, "ENGLISH_TRACE T3_ORT_SESSION_START ns=" + sessionStart);
    environment = OrtEnvironment.getEnvironment();
    session = environment.createSession(model.getAbsolutePath(), new OrtSession.SessionOptions());
    Log.i(TAG, "ENGLISH_TRACE T4_ORT_SESSION_END ns=" + System.nanoTime() + " elapsedMs=" + ((System.nanoTime() - sessionStart) / 1_000_000.0));
    initialized = true;
    initState = "READY";
    Log.i(TAG, "ENGLISH_MODEL_READY model=en_ljspeech_piper_1m sampleRate=" + sampleRate + " speakers=" + numSpeakers + " mapSymbols=" + ids.size() + " sha256=" + MODEL_SHA256 + " loadMs=" + ((System.nanoTime() - started) / 1_000_000.0));
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
      if (i == 0 && segments.size() > 1) {
        long pcmMs = Math.round(pcm.length * 1000.0 / sampleRate);
        // The stream releases this reserve early when the second chunk is ready.
        listener.onStartupReserve(Math.min(MAX_STARTUP_RESERVE_MS, Math.max(0L, pcmMs)));
      }
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
      if (!symbols.isEmpty()) out.addAll(splitForStreaming(symbols, boundary));
    }
    if (!out.isEmpty()) { Segment last = out.get(out.size() - 1); out.set(out.size() - 1, new Segment(last.phonemes, SentenceBoundaryPausePolicy.Boundary.NONE)); }
    return out;
  }

  static List<Segment> splitForStreaming(List<String> symbols, SentenceBoundaryPausePolicy.Boundary boundary) {
    List<Segment> result = new ArrayList<>();
    int start = 0;
    while (symbols.size() - start > MAX_STREAMING_PHONEMES) {
      int remaining = symbols.size() - start;
      int pieces = (remaining + MAX_STREAMING_PHONEMES - 1) / MAX_STREAMING_PHONEMES;
      int target = start + remaining / pieces;
      int limit = Math.min(symbols.size() - 1, start + MAX_STREAMING_PHONEMES);
      int cut = -1;
      // Balance the remainder as well, so a short final word is not synthesized alone.
      for (int i = start + 1; i <= limit; i++) {
        if (" ".equals(symbols.get(i)) && i - start >= MIN_SPLIT_PHONEMES
            && symbols.size() - i - 1 >= MIN_SPLIT_PHONEMES
            && (cut < 0 || Math.abs(i - target) < Math.abs(cut - target))) cut = i;
      }
      // Never split an IPA word or combining sequence merely to meet a size target.
      if (cut <= start) break;
      List<String> piece = new ArrayList<>(symbols.subList(start, cut));
      if (!piece.isEmpty()) result.add(new Segment(piece, SentenceBoundaryPausePolicy.Boundary.NONE));
      start = cut + 1;
    }
    if (start < symbols.size()) {
      List<String> piece = new ArrayList<>(symbols.subList(start, symbols.size()));
      if (!piece.isEmpty()) result.add(new Segment(piece, boundary));
    }
    if (result.isEmpty() && !symbols.isEmpty()) result.add(new Segment(new ArrayList<>(symbols), boundary));
    return result;
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
  private String readAsset(String path) throws Exception {
    try (InputStream in = context.getAssets().open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192]; int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      return out.toString("UTF-8");
    }
  }

  private File copyVerifiedModel() throws Exception {
    File model = new File(context.getCacheDir(), CACHE_NAME);
    if (isExpectedModel(model)) {
      removeLegacyCache();
      return model;
    }
    File temporary = new File(context.getCacheDir(), CACHE_NAME + ".tmp");
    if (temporary.exists() && !temporary.delete()) throw new IllegalStateException("STALE_ENGLISH_MODEL_TEMP_DELETE_FAILED");
    try (InputStream in = context.getAssets().open(MODEL_ASSET); FileOutputStream out = new FileOutputStream(temporary)) {
      byte[] buffer = new byte[64 * 1024]; int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      out.getFD().sync();
    }
    if (!isExpectedModel(temporary)) {
      temporary.delete();
      throw new IllegalStateException("ENGLISH_MODEL_ASSET_INTEGRITY_FAILED");
    }
    if (model.exists() && !model.delete()) throw new IllegalStateException("STALE_ENGLISH_MODEL_DELETE_FAILED");
    if (!temporary.renameTo(model)) throw new IllegalStateException("ENGLISH_MODEL_ATOMIC_INSTALL_FAILED");
    removeLegacyCache();
    return model;
  }

  private void removeLegacyCache() {
    File legacy = new File(context.getCacheDir(), "en_lessac_low.onnx");
    if (legacy.exists() && !legacy.delete()) Log.w(TAG, "LEGACY_ENGLISH_CACHE_DELETE_FAILED path=" + legacy);
    else if (!legacy.exists()) Log.i(TAG, "LEGACY_ENGLISH_CACHE_ABSENT");
    else Log.i(TAG, "LEGACY_ENGLISH_CACHE_REMOVED");
  }

  private static boolean isExpectedModel(File model) throws Exception {
    return model.isFile() && model.length() == MODEL_SIZE && MODEL_SHA256.equals(sha256(model));
  }

  private static String sha256(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(file)) {
      byte[] buffer = new byte[64 * 1024]; int count;
      while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
    }
    StringBuilder result = new StringBuilder(64);
    for (byte value : digest.digest()) result.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
    return result.toString();
  }
  static double lastInferenceMs() { return lastInferenceMs; }
  static int lastPcmSamples() { return lastPcmSamples; }
  static final class Segment { final List<String> phonemes; final SentenceBoundaryPausePolicy.Boundary boundary; Segment(List<String> p, SentenceBoundaryPausePolicy.Boundary b) { phonemes = p; boundary = b; } }
}
