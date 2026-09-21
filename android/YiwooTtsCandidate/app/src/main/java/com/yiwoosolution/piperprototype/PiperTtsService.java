package com.yiwoosolution.piperprototype;

import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.Voice;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** Android framework TTS adapter for the isolated Korean Piper prototype. */
public final class PiperTtsService extends TextToSpeechService {
  private static final String TAG = "YiwooPiperKo";
  private final AtomicLong generation = new AtomicLong();
  private PiperModelManager manager;
  private EnglishModelManager englishManager;
  private LongTextStreamingSynthesizer streamSynthesizer;
  private LongTextStreamingSynthesizer englishStreamSynthesizer;
  private volatile LongTextStreamingSynthesizer.StreamHandle activeStream;
  private volatile String language = "ko-KR";

  @Override public void onCreate() { super.onCreate(); manager = PiperModelManager.shared(this); englishManager = EnglishModelManager.shared(this); streamSynthesizer = new LongTextStreamingSynthesizer(manager); englishStreamSynthesizer = new LongTextStreamingSynthesizer(englishManager); RuntimePreloadCoordinator.ensureRuntimePreloaded(this); Log.i(TAG, "TTS_SERVICE_CREATED runtimePreload=TRIGGERED"); }

  @Override public int onIsLanguageAvailable(String lang, String country, String variant) {
    Log.i(TAG, "LANGUAGE_AVAILABLE lang=" + lang + " country=" + country + " variant=" + variant);
    if ("kor".equalsIgnoreCase(lang) || "ko".equalsIgnoreCase(lang)) {
      if (country == null || country.isEmpty()) return TextToSpeech.LANG_AVAILABLE;
      return ("KR".equalsIgnoreCase(country) || "KOR".equalsIgnoreCase(country))
          ? TextToSpeech.LANG_COUNTRY_AVAILABLE : TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
    }
    if ("eng".equalsIgnoreCase(lang) || "en".equalsIgnoreCase(lang)) {
      if (country == null || country.isEmpty()) return TextToSpeech.LANG_AVAILABLE;
      return ("US".equalsIgnoreCase(country) || "USA".equalsIgnoreCase(country))
          ? TextToSpeech.LANG_COUNTRY_AVAILABLE : TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
    }
    return TextToSpeech.LANG_NOT_SUPPORTED;
  }

  @Override public List<Voice> onGetVoices() {
    List<Voice> voices = new ArrayList<>();
    voices.add(new Voice("ko_8523_piper_v2_1000k", Locale.KOREA, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, new HashSet<String>()));
    voices.add(new Voice("en_ljspeech_piper_1m", Locale.US, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, new HashSet<String>()));
    return voices;
  }

  @Override protected String[] onGetLanguage() { return language.startsWith("en") ? new String[]{"eng", "USA", ""} : new String[]{"kor", "KOR", ""}; }

  @Override public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
    if ("eng".equalsIgnoreCase(lang) || "en".equalsIgnoreCase(lang)) return "en_ljspeech_piper_1m";
    if ("kor".equalsIgnoreCase(lang) || "ko".equalsIgnoreCase(lang)) return "ko_8523_piper_v2_1000k";
    return null;
  }

  @Override public int onIsValidVoiceName(String voiceName) {
    if ("en_ljspeech_piper_1m".equals(voiceName) || "ko_8523_piper_v2_1000k".equals(voiceName)) return TextToSpeech.LANG_COUNTRY_AVAILABLE;
    return TextToSpeech.LANG_NOT_SUPPORTED;
  }

  @Override public int onLoadVoice(String voiceName) {
    if ("en_ljspeech_piper_1m".equals(voiceName)) { language = "en-US"; return TextToSpeech.LANG_COUNTRY_AVAILABLE; }
    if ("ko_8523_piper_v2_1000k".equals(voiceName)) { language = "ko-KR"; return TextToSpeech.LANG_COUNTRY_AVAILABLE; }
    return TextToSpeech.LANG_NOT_SUPPORTED;
  }

  @Override public int onLoadLanguage(String lang, String country, String variant) {
    Log.i(TAG, "LANGUAGE_LOAD lang=" + lang + " country=" + country + " variant=" + variant);
    if (onIsLanguageAvailable(lang, country, variant) == TextToSpeech.LANG_NOT_SUPPORTED) return TextToSpeech.LANG_NOT_SUPPORTED;
    language = ("eng".equalsIgnoreCase(lang) || "en".equalsIgnoreCase(lang)) ? "en-US" : "ko-KR";
    return ("eng".equalsIgnoreCase(lang) || "en".equalsIgnoreCase(lang))
        ? TextToSpeech.LANG_COUNTRY_AVAILABLE : TextToSpeech.LANG_AVAILABLE;
  }

  @Override protected void onSynthesizeText(android.speech.tts.SynthesisRequest request, SynthesisCallback callback) {
    final long requestId = generation.incrementAndGet(); final String text = request.getCharSequenceText() == null ? "" : request.getCharSequenceText().toString().trim();
    final boolean notificationRequest = request.getParams() != null && NotificationPriorityState.ORIGIN_NOTIFICATION.equals(request.getParams().getString(NotificationPriorityState.PARAM_ORIGIN));
    final boolean timeRequest = request.getParams() != null && NotificationPriorityState.ORIGIN_TIME.equals(request.getParams().getString(NotificationPriorityState.PARAM_ORIGIN));
    final boolean selectedTextRequest = request.getParams() != null && "selected_text".equals(request.getParams().getString(NotificationPriorityState.PARAM_ORIGIN));
    final boolean english = language.startsWith("en") || (request.getLanguage() != null && ("eng".equalsIgnoreCase(request.getLanguage()) || "en".equalsIgnoreCase(request.getLanguage())));
    if (text.isEmpty()) { callback.start(english ? englishManager.sampleRate() : 16000, AudioFormatCompat.PCM_16BIT, 1); callback.done(); return; }
    if (!english && (request.getLanguage() == null || !"kor".equalsIgnoreCase(request.getLanguage()))) {
      Log.w(TAG, "TTS_LANGUAGE_UNAVAILABLE language=" + request.getLanguage()); callback.error(); return;
    }
    final float rate = SpeechPcmProcessor.bounded(request.getSpeechRate() / 100f);
    final float pitch = SpeechPcmProcessor.bounded(request.getPitch() / 100f);
    Log.i(TAG, "TTS_PLAYBACK_PARAMETERS modelRate=" + rate + " pitch=" + pitch);
    long started = System.nanoTime();
    if (!timeRequest) TimeAnnouncementPlayer.interrupt("OTHER_SPEECH");
    if (!notificationRequest && !timeRequest) NotificationPriorityState.ordinaryStarted();
    if (selectedTextRequest) Log.i(TAG, "YIWOO_SELECTED_TEXT_TTS_REQUEST chars=" + text.length());
    if (selectedTextRequest) NotificationPriorityState.selectedTextStarted();
    int totalSamples = 0;
    int totalAudioSamples = 0;
    final int sampleRate = english ? englishManager.sampleRate() : VoiceRegistry.ACTIVE.sampleRate;
    try {
      callback.start(sampleRate, AudioFormatCompat.PCM_16BIT, 1);
      Log.i(TAG, "TTS_SERVICE_SENTENCE_PAUSE configuredMs=" + SentenceBoundaryPausePolicy.pauseMs(this) + " sampleRate=" + sampleRate + " locale=" + (english ? "en-US" : "ko-KR"));
      String processedText = english ? ReadingRuleEngine.apply(text, new ReadingRuleRepository(this), ReadingRule.Language.EN_US) : text;
      if (selectedTextRequest) Log.i(TAG, "YIWOO_SELECTED_TEXT_FINAL chars=" + processedText.length());
      LongTextStreamingSynthesizer.StreamHandle stream = (english ? englishStreamSynthesizer : streamSynthesizer).start(processedText, rate);
      activeStream = stream;
      byte[] bytes = new byte[8192];
      boolean first = true; long firstPcmMs = 0;
      LongTextStreamingSynthesizer.Chunk chunk;
      while (requestId == generation.get() && (chunk = stream.take()) != null) {
        if (first) { first = false; long callbackNs = System.nanoTime(); firstPcmMs = (callbackNs - started) / 1_000_000L; Log.i(TAG, "ENGLISH_TRACE T11_FIRST_AUDIO_CALLBACK ns=" + callbackNs + " postOrtToCallbackUnknown=true"); Log.i(TAG, "TTS_FIRST_PCM textLength=" + text.length() + " latencyMs=" + firstPcmMs); }
        short[] pcm = SpeechPcmProcessor.process(chunk.pcm, sampleRate, 1f, pitch); totalSamples += pcm.length; totalAudioSamples += pcm.length;
        for (int offset = 0; offset < pcm.length && requestId == generation.get(); ) {
          int count = Math.min(4096, pcm.length - offset);
          for (int i = 0; i < count; i++) { short sample = pcm[offset + i]; bytes[2*i] = (byte) (sample & 0xff); bytes[2*i+1] = (byte) ((sample >>> 8) & 0xff); }
          callback.audioAvailable(bytes, 0, count * 2); Log.i(TAG, "TTS_PLAYBACK_WRITE type=SPEECH frames=" + count + " chunk=" + chunk.index); offset += count;
        }
        if (chunk.pauseMs > 0 && requestId == generation.get()) {
          short[] silence = SentenceBoundaryPausePolicy.silence(sampleRate, chunk.pauseMs);
          totalAudioSamples += silence.length;
          Log.i(TAG, "PAUSE_PCM pauseMs=" + chunk.pauseMs + " sampleRate=" + sampleRate + " frames=" + silence.length + " samples=" + silence.length);
          for (int offset = 0; offset < silence.length && requestId == generation.get(); ) {
            int count = Math.min(4096, silence.length - offset);
            for (int i = 0; i < count; i++) { bytes[2*i] = 0; bytes[2*i+1] = 0; }
            callback.audioAvailable(bytes, 0, count * 2); Log.i(TAG, "TTS_PLAYBACK_WRITE type=SENTENCE_SILENCE frames=" + count + " chunk=" + chunk.index); offset += count;
          }
        }
      }
      if (requestId == generation.get()) { callback.done(); double measuredInference = english ? EnglishModelManager.lastInferenceMs() : PiperModelManager.lastInferenceMs(); if (selectedTextRequest) Log.i(TAG, "YIWOO_SELECTED_TEXT_DONE MODEL_PCM_FRAMES=" + totalSamples + " CALLBACK_PCM_FRAMES=" + totalAudioSamples + " pcmBytes=" + (totalAudioSamples * 2)); if (notificationRequest) Log.i(TAG, "YIWOO_NOTIFICATION_DONE MODEL_PCM_FRAMES=" + totalSamples + " CALLBACK_PCM_FRAMES=" + totalAudioSamples + " pcmBytes=" + (totalAudioSamples * 2)); Log.i(TAG, "TTS_REQUEST_DONE textLength=" + text.length() + " firstPcmMs=" + firstPcmMs + " inferenceMs=" + measuredInference + " pcmSamples=" + totalSamples + " totalAudioFrames=" + totalAudioSamples + " locale=" + (english ? "en-US" : "ko-KR")); }
      else { Log.i(TAG, "TTS_REQUEST_CANCELLED textLength=" + text.length()); callback.error(); }
    } catch (Throwable error) { if (activeStream != null) activeStream.cancel(); Log.e(TAG, "TTS_REQUEST_ERROR textLength=" + text.length(), error); callback.error(); }
    finally { activeStream = null; if (selectedTextRequest) NotificationPriorityState.selectedTextFinished(); if (!notificationRequest && !timeRequest) NotificationPriorityState.ordinaryFinished(totalSamples * 1000L / sampleRate); }
  }

  @Override protected void onStop() { generation.incrementAndGet(); if (activeStream != null) activeStream.cancel(); NotificationPriorityState.ordinaryStopped(); Log.i(TAG, "TTS_STOP"); }

  private static final class AudioFormatCompat { static final int PCM_16BIT = 2; }
}
