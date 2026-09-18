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
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.json.JSONObject;

/** Process-scoped lazy ORT session for the isolated prototype engine. */
final class PiperModelManager {
  private static final String TAG = "YiwooPiperKo";
  private static final int MAX_CHUNK_TOKENS = 256;
  private final Context context;
  /** Serializes the single ORT session without holding the state/preload monitor. */
  private final ReentrantLock synthesisLock = new ReentrantLock(true);
  private final SynthesisPerformance performance = new SynthesisPerformance();
  private OrtEnvironment environment;
  private volatile OrtSession session;
  private File modelFile;
  private final VoiceDescriptor activeVoice = VoiceRegistry.ACTIVE;
  private static volatile double lastInferenceMs = Double.NaN;
  private static volatile int lastPcmSamples;
  private static PiperModelManager shared;
  private final ScheduledExecutorService lifecycleExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "yiwoo-engine-lifecycle"); t.setDaemon(true); return t;
  });
  private ScheduledFuture<?> releaseTask;
  private long retentionGeneration;
  private final java.util.concurrent.atomic.AtomicInteger scheduledHolds = new java.util.concurrent.atomic.AtomicInteger();
  private volatile String state = "UNINITIALIZED";
  private volatile long lastUseElapsedMs;

  PiperModelManager(Context context) { this.context = context.getApplicationContext(); }

  static synchronized PiperModelManager shared(Context context) {
    if (shared == null) shared = new PiperModelManager(context);
    return shared;
  }

  void ensureReady() throws Exception {
    cancelReleaseLocked();
    if (session != null) { state = "READY"; lastUseElapsedMs = android.os.SystemClock.elapsedRealtime(); return; }
    state = "LOADING";
    long start = System.nanoTime();
    try {
      verifyCompatibility(activeVoice);
      modelFile = copyAsset(activeVoice.modelPath);
      environment = OrtEnvironment.getEnvironment();
      session = environment.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions());
      state = "READY";
      lastUseElapsedMs = android.os.SystemClock.elapsedRealtime();
      Log.i(TAG, "MODEL_READY voiceId=" + activeVoice.voiceId + " modelPath=" + activeVoice.modelPath + " frontendVersion=" + activeVoice.frontendVersion + " sampleRate=" + activeVoice.sampleRate + " loadMs=" + ((System.nanoTime() - start) / 1_000_000.0));
    } catch (Exception error) {
      state = "FAILED";
      throw error;
    }
  }

  short[] synthesize(String text) throws Exception {
    RuntimeRetentionService.ensureStarted(context);
    Log.i(TAG, "SYNTH_ENTER thread=" + Thread.currentThread().getName() + " manager=" + System.identityHashCode(this));
    synthesisLock.lock();
    try {
    ensureReady();
    Log.i(TAG, "SYNTH_READY thread=" + Thread.currentThread().getName() + " session=" + (session != null));
    Log.i(TAG, "SYNTH_PREPARE_TEXT_BEGIN");
    text = prepareKoreanText(text);
    Log.i(TAG, "SYNTH_PREPARE_TEXT_END text=" + text);
    List<PauseChunk> chunks = splitForSynthesis(text);
    Log.i(TAG, "SYNTH_SPLIT_DONE chunks=" + chunks.size());
    if (chunks.isEmpty()) throw new IllegalArgumentException("EMPTY_SYNTHESIS_TEXT");
    Log.i(TAG, "SYNTHESIS_REQUEST chars=" + text.length() + " chunks=" + chunks.size());
    List<short[]> outputs = new ArrayList<>();
    long sampleCount = 0;
    double inferenceMs = 0.0;
    try {
      for (PauseChunk chunk : chunks) {
        Log.i(TAG, "SYNTHESIS_CHUNK chars=" + chunk.text.length() + " index=" + outputs.size() + " total=" + chunks.size());
        short[] pcm = synthesizeChunkLocked(chunk.text);
        outputs.add(pcm);
        sampleCount += pcm.length;
        if (Double.isFinite(lastInferenceMs)) inferenceMs += lastInferenceMs;
      }
    } catch (Exception error) {
      Log.e(TAG, "SYNTHESIS_FAILED stage=chunk_inference completedChunks=" + outputs.size() + "/" + chunks.size(), error);
      throw error;
    }
    short[] combined = new short[(int) sampleCount];
    int offset = 0;
    for (short[] pcm : outputs) { System.arraycopy(pcm, 0, combined, offset, pcm.length); offset += pcm.length; }
    lastInferenceMs = inferenceMs;
    lastPcmSamples = combined.length;
    Log.i(TAG, "ONNX_RUN_COMPLETE chunks=" + chunks.size() + " onnxMs=" + lastInferenceMs + " pcmSamples=" + combined.length);
    return combined;
    } finally { lastUseElapsedMs = android.os.SystemClock.elapsedRealtime(); scheduleRetentionLocked(); synthesisLock.unlock(); Log.i(TAG, "SYNTH_EXIT thread=" + Thread.currentThread().getName()); }
  }

  /** Synthesizes in source order so callers can start playback after chunk one. */
  void synthesizeChunks(String text, LongTextStreamingSynthesizer.ChunkListener listener) throws Exception {
    synthesizeChunks(text, 1f, listener);
  }

  void synthesizeChunks(String text, float rate, LongTextStreamingSynthesizer.ChunkListener listener) throws Exception {
    synthesizeChunks(text, rate, listener, true);
  }

  /** The legacy plan is retained for focused, same-session instrumentation comparisons. */
  void synthesizeChunks(String text, float rate, LongTextStreamingSynthesizer.ChunkListener listener, boolean streamingPlan) throws Exception {
    RuntimeRetentionService.ensureStarted(context);
    Log.i(TAG, "SYNTH_ENTER thread=" + Thread.currentThread().getName() + " manager=" + System.identityHashCode(this));
    synthesisLock.lock();
    try {
    ensureReady();
    Log.i(TAG, "SYNTH_READY thread=" + Thread.currentThread().getName() + " session=" + (session != null));
    text = prepareKoreanText(text);
    List<PauseChunk> chunks;
    if (streamingPlan && !activeVoice.frontendVersion.startsWith("v1")) {
      chunks = new ArrayList<>();
      SynthesisPerformance.Snapshot measured = performance.snapshot();
      Log.i(TAG, "ADAPTIVE_CHUNK_PROFILE samples=" + measured.samples + " rtf=" + measured.rtf()
          + " inferenceMsPerPhone=" + measured.inferencePerPhone + " fixedMs=" + measured.inferenceFixedMs + " rate=" + rate);
      for (SentenceBoundaryPausePolicy.Segment piece : KoreanStreamingChunkPlanner.plan(text,
          value -> KoreanFrontend.normalize(context, value), value -> KoreanFrontend.phonemes(value).length(),
          measured, rate, SentenceBoundaryPausePolicy.pauseMs(context))) {
        chunks.add(new PauseChunk(piece.text, piece.boundary));
      }
    } else chunks = splitForSynthesis(text);
    if (chunks.isEmpty()) throw new IllegalArgumentException("EMPTY_SYNTHESIS_TEXT");
    Log.i(TAG, "SENTENCE_PAUSE_CONFIG requestedMs=" + SentenceBoundaryPausePolicy.pauseMs(context) + " chunks=" + chunks.size());
    double inferenceMs = 0.0;
    long sampleCount = 0;
    for (int i = 0; i < chunks.size(); i++) {
      PauseChunk chunk = chunks.get(i);
      Log.i(TAG, "SYNTHESIS_CHUNK_STREAM chars=" + chunk.text.length() + " index=" + i + " total=" + chunks.size());
      short[] pcm = synthesizeChunkLocked(chunk.text, rate);
      sampleCount += pcm.length;
      if (Double.isFinite(lastInferenceMs)) inferenceMs += lastInferenceMs;
      double pcmMs = pcm.length * 1000.0 / activeVoice.sampleRate;
      double chunkRtf = pcmMs > 0.0 ? lastInferenceMs / pcmMs : Double.NaN;
      Log.i(TAG, "CHUNK_METRICS index=" + i + " ortMs=" + lastInferenceMs + " pcmFrames=" + pcm.length + " pcmDurationMs=" + pcmMs + " chunkRtf=" + chunkRtf);
      int pauseMs = SentenceBoundaryPausePolicy.effectivePause(context, chunk.boundary);
      if (streamingPlan && i == 0 && chunks.size() > 1) {
        long reserve = performance.snapshot().startupReserveMs(tokenCount(chunks.get(1).text), rate, pcmMs + pauseMs);
        listener.onStartupReserve(reserve);
        Log.i(TAG, "ADAPTIVE_STARTUP_RESERVE_MS=" + reserve);
      }
      if (pauseMs > 0) Log.i(TAG, "BOUNDARY_DETECTED index=" + i + " type=" + chunk.boundary + " effectivePauseMs=" + pauseMs);
      listener.onChunk(pcm, i, chunks.size(), lastInferenceMs, pauseMs);
    }
    lastInferenceMs = inferenceMs;
    lastPcmSamples = (int) Math.min(Integer.MAX_VALUE, sampleCount);
    Log.i(TAG, "ONNX_STREAM_COMPLETE chunks=" + chunks.size() + " onnxMs=" + inferenceMs);
    } finally { lastUseElapsedMs = android.os.SystemClock.elapsedRealtime(); scheduleRetentionLocked(); synthesisLock.unlock(); Log.i(TAG, "SYNTH_EXIT thread=" + Thread.currentThread().getName()); }
  }

  private short[] synthesizeChunkLocked(String text) throws Exception {
    return synthesizeChunkLocked(text, 1f);
  }

  private short[] synthesizeChunkLocked(String text, float rate) throws Exception {
    lastInferenceMs = Double.NaN;
    lastPcmSamples = 0;
    boolean v1 = activeVoice.frontendVersion.startsWith("v1");
    Log.i(TAG, "SYNTH_PHONEMIZE_BEGIN text=" + text);
    String normalized = v1 ? KoreanFrontendV1.normalize(text) : KoreanFrontend.normalize(context, text);
    Log.i(TAG, "SYNTH_PHONEMIZE_END normalized=" + normalized);
    if (!v1 && NotificationPriorityState.isSelectedTextRequest() && (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
      Log.i(TAG, "YIWOO_SELECTED_TEXT_CANONICAL text=" + normalized);
    }
    // Training utterances use an explicit terminal punctuation token. Keep the
    // user/canonical text unchanged, but supply that learned context only to
    // the model input for an otherwise unpunctuated Korean segment.
    String modelNormalized = v1 ? normalized : ensureModelTerminalContext(normalized);
    if (!v1 && !modelNormalized.equals(normalized)
        && (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
      Log.i(TAG, "KOREAN_MODEL_TERMINATOR_ADDED");
    }
    String phonemes = v1 ? KoreanFrontendV1.phonemes(modelNormalized) : KoreanFrontend.phonemes(modelNormalized);
    long[] ids = v1 ? KoreanFrontendV1.ids(phonemes) : KoreanFrontend.ids(phonemes);
    if (!v1 && isLegacyPortSentinel(text)) {
      Log.i(TAG, "KOREAN_TEXT_TRACE RAW=" + text
          + " AFTER_CANONICAL_NORMALIZER=" + normalized
          + " BEFORE_FRONTEND_V2=" + modelNormalized
          + " TOKEN_IDS=" + Arrays.toString(ids)
          + " SYNTHESIS_ENTRY_POINT=SHARED_PIPER_MODEL_MANAGER");
    }
    Map<String, OnnxTensor> feed = new HashMap<>();
    try (OnnxTensor input = OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), new long[]{1, ids.length});
         OnnxTensor lengths = OnnxTensor.createTensor(environment, LongBuffer.wrap(new long[]{ids.length}), new long[]{1});
         OnnxTensor scales = OnnxTensor.createTensor(environment, new float[]{0.667f, SpeechRatePolicy.lengthScale(1f, rate), 0.8f})) {
      feed.put("input", input); feed.put("input_lengths", lengths); feed.put("scales", scales);
      long onnxStart = System.nanoTime();
      try (OrtSession.Result result = session.run(feed)) {
        float[] audio = ((float[][][][]) result.get(0).getValue())[0][0][0];
        short[] pcm = new short[audio.length];
        for (int i = 0; i < audio.length; i++) pcm[i] = (short) (Math.max(-1f, Math.min(1f, audio[i])) * 32767f);
        lastInferenceMs = (System.nanoTime() - onnxStart) / 1_000_000.0;
        lastPcmSamples = pcm.length;
        performance.observe(phonemes.length(), lastInferenceMs, pcm.length * 1000.0 / activeVoice.sampleRate, rate);
        Log.i(TAG, "ONNX_RUN voiceId=" + activeVoice.voiceId + " onnxMs=" + lastInferenceMs + " pcmSamples=" + pcm.length);
        lastUseElapsedMs = android.os.SystemClock.elapsedRealtime();
        return pcm;
      }
    }
  }

  /** Applies the runtime pronunciation layer once, before sentence splitting and Korean normalization. */
  private String prepareKoreanText(String text) {
    if (activeVoice.frontendVersion.startsWith("v1")) return text;
    String resolved = PronunciationFrontend.shared(context).normalize(text);
    if (!resolved.equals(text) && (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
      Log.i(TAG, "PRONUNCIATION_FRONTEND_APPLIED changed=true inputChars=" + (text == null ? 0 : text.length()) + " outputChars=" + resolved.length() + " output=" + resolved);
    }
    return resolved;
  }

  private static String ensureModelTerminalContext(String normalized) {
    if (normalized == null || normalized.isEmpty()) return normalized;
    boolean hasKorean = false;
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if (c >= '\uAC00' && c <= '\uD7A3') { hasKorean = true; break; }
    }
    if (!hasKorean) return normalized;
    char last = normalized.charAt(normalized.length() - 1);
    if (last == '.' || last == '!' || last == '?' || last == '…' || last == '。'
        || last == '！' || last == '？') return normalized;
    return normalized + ".";
  }

  private static boolean isLegacyPortSentinel(String text) {
    return text != null && (text.contains("A-20260916")
        || text.contains("v2.1.3")
        || text.contains("오후 3:30~5:00")
        || text.contains("15,000원")
        || text.contains("오늘은 1/3과 3/4의 크기를 비교합니다"));
  }

  private int tokenCount(String text) {
    String normalized = KoreanFrontend.normalize(context, text);
    return KoreanFrontend.phonemes(normalized).length();
  }

  private static final class PauseChunk { final String text; final SentenceBoundaryPausePolicy.Boundary boundary; PauseChunk(String text, SentenceBoundaryPausePolicy.Boundary boundary) { this.text = text; this.boundary = boundary; } }
  private List<PauseChunk> splitForSynthesis(String text) {
    List<PauseChunk> result = new ArrayList<>();
    if (text == null) return result;
    String cleaned = text.replace("\r\n", "\n").replace('\r', '\n').trim();
    if (cleaned.isEmpty()) return result;
    for (SentenceBoundaryPausePolicy.Segment segment : SentenceBoundaryPausePolicy.segment(cleaned)) {
      String remaining = segment.text;
      SentenceBoundaryPausePolicy.Boundary boundary = segment.boundary;
      // Hangul expands to multiple phonemes; character count cannot prove that
      // a sentence is below the model's token budget.
      while (!remaining.isEmpty() && tokenCount(remaining) > MAX_CHUNK_TOKENS) {
        int cut = remaining.length();
        while (cut > 1) {
          int space = remaining.lastIndexOf(' ', cut - 1);
          if (space <= 0) break;
          String candidate = remaining.substring(0, space).trim();
          if (tokenCount(candidate) <= MAX_CHUNK_TOKENS) { cut = space; break; }
          cut = space;
        }
        if (cut == remaining.length() || cut <= 0) {
          // A pathological unspaced sentence is split at a token-safe code-point boundary.
          int low = 1, high = remaining.length(), best = 1;
          while (low <= high) {
            int mid = (low + high) >>> 1;
            if (tokenCount(remaining.substring(0, mid)) <= MAX_CHUNK_TOKENS) { best = mid; low = mid + 1; }
            else high = mid - 1;
          }
          cut = Math.max(1, best);
        }
        result.add(new PauseChunk(remaining.substring(0, cut).trim(), SentenceBoundaryPausePolicy.Boundary.NONE));
        remaining = remaining.substring(cut).trim();
      }
      if (!remaining.isEmpty()) result.add(new PauseChunk(remaining, boundary));
    }
    return result;
  }

  void preloadIfConfigured() {
    if (EngineRetentionSettings.mode(context) == EngineRetentionSettings.Mode.ON_DEMAND) {
      Log.i(TAG, "PRELOAD_SKIPPED policy=ON_DEMAND");
      return;
    }
    RuntimeRetentionService.ensureStarted(context);
    RuntimePreloadCoordinator.prepare(context, false);
  }

  void onRetentionPolicyChanged() {
    lifecycleExecutor.execute(() -> {
      synthesisLock.lock();
      try { scheduleRetentionLocked(); } finally { synthesisLock.unlock(); }
    });
  }

  // A scheduled announcement owns a bounded lease, not a change to the user's policy.
  Runnable holdForScheduledSpeech() {
    scheduledHolds.incrementAndGet();
    onRetentionPolicyChanged();
    java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
    return () -> {
      if (released.compareAndSet(false, true)) {
        scheduledHolds.decrementAndGet();
        onRetentionPolicyChanged();
      }
    };
  }

  private void scheduleRetentionLocked() {
    cancelReleaseLocked();
    EngineRetentionSettings.Mode mode = EngineRetentionSettings.mode(context);
    if (session == null || mode == EngineRetentionSettings.Mode.ALWAYS || scheduledHolds.get() > 0) return;
    long delay = mode == EngineRetentionSettings.Mode.ON_DEMAND ? 0L : EngineRetentionSettings.idleMinutes(context) * 60_000L;
    final long generation = retentionGeneration;
    releaseTask = lifecycleExecutor.schedule(() -> {
      synthesisLock.lock();
      try {
        if (session == null || generation != retentionGeneration || scheduledHolds.get() > 0) return;
        if (mode != EngineRetentionSettings.mode(context)) { scheduleRetentionLocked(); return; }
        long idle = android.os.SystemClock.elapsedRealtime() - lastUseElapsedMs;
        if (mode == EngineRetentionSettings.Mode.ON_DEMAND || idle >= delay) releaseLocked("idle");
        else scheduleRetentionLocked();
      } finally { synthesisLock.unlock(); }
    }, delay, TimeUnit.MILLISECONDS);
  }

  private void cancelReleaseLocked() {
    retentionGeneration++;
    if (releaseTask != null) { releaseTask.cancel(false); releaseTask = null; }
  }

  private void releaseLocked(String reason) {
    cancelReleaseLocked();
    if (session != null) {
      try { session.close(); } catch (Exception ignored) {}
      session = null;
      state = "UNINITIALIZED";
      Log.i(TAG, "MODEL_RELEASED reason=" + reason);
      RuntimeRetentionService.runtimeChanged();
    }
  }

  void releaseForDiagnostics() { synthesisLock.lock(); try { releaseLocked("manual"); } finally { synthesisLock.unlock(); } }

  boolean isReady() { return session != null; }
  String lifecycleState() { return state; }
  long lastUseElapsedMs() { return lastUseElapsedMs; }
  String retentionMode() { return EngineRetentionSettings.mode(context).name(); }
  int retentionMinutes() { return EngineRetentionSettings.idleMinutes(context); }

  VoiceDescriptor activeVoice() { return activeVoice; }

  static double lastInferenceMs() { return lastInferenceMs; }
  static int lastPcmSamples() { return lastPcmSamples; }

  private void verifyCompatibility(VoiceDescriptor voice) throws Exception {
    if (!voice.enabled || !voice.modelAvailable) throw new IllegalStateException("VOICE_UNAVAILABLE:" + voice.voiceId);
    try (InputStream in = context.getAssets().open("models/model_descriptor.json")) {
      byte[] bytes = new byte[in.available()]; int offset = 0; int n;
      while ((n = in.read(bytes, offset, bytes.length - offset)) > 0) offset += n;
      JSONObject model = new JSONObject(new String(bytes, 0, offset, "UTF-8"));
      String modelFrontend = model.optString("frontendVersion", "");
      if (!voice.frontendVersion.equals(modelFrontend)) throw new IllegalStateException("FRONTEND_MISMATCH:model=" + modelFrontend + ",voice=" + voice.frontendVersion);
      String modelVocab = model.optString("vocabVersion", "");
      if (!voice.vocabVersion.equals(modelVocab)) throw new IllegalStateException("VOCAB_MISMATCH:model=" + modelVocab + ",voice=" + voice.vocabVersion);
    }
  }

  private File copyAsset(String name) throws Exception {
    File file = new File(context.getCacheDir(), activeVoice.voiceId + ".onnx");
    long expectedSize = 0;
    try (InputStream asset = context.getAssets().open(name)) {
      byte[] probe = new byte[8192]; int n;
      while ((n = asset.read(probe)) > 0) expectedSize += n;
    }
    if (file.isFile() && file.length() == expectedSize) return file;
    try (InputStream in = context.getAssets().open(name); FileOutputStream out = new FileOutputStream(file)) {
      byte[] buffer = new byte[8192]; int count; while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
    }
    return file;
  }
}
