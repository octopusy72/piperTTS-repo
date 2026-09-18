package com.yiwoosolution.piperprototype;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** One low-priority utterance. Alarm/focus loss cancels it without replay. */
final class TimeAnnouncementPlayer {
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static volatile TimeAnnouncementPlayer active;
  private final Context context;
  private final AudioManager audio;
  private final long due;
  private final Runnable finished;
  private TextToSpeech tts;
  private AudioFocusRequest focus;
  private PowerManager.WakeLock wake;
  private boolean closed;
  private boolean playbackRegistered;
  private int audioBytes;
  private final Runnable timeout = () -> close("TIMEOUT");
  private final AudioManager.AudioPlaybackCallback playback = new AudioManager.AudioPlaybackCallback() {
    @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
      if (containsAlarm(configs)) close("ALARM_ACTIVE");
    }
  };

  private TimeAnnouncementPlayer(Context c, long due, Runnable finished) {
    context = c.getApplicationContext(); audio = c.getSystemService(AudioManager.class);
    this.due = due; this.finished = finished;
  }
  static void start(Context c, long due, Runnable finished) {
    if (active != null) { Log.i("YiwooTime", "TIME_SKIPPED reason=ALREADY_SPEAKING"); finished.run(); return; }
    active = new TimeAnnouncementPlayer(c, due, finished);
    active.begin();
  }
  static void interrupt(String reason) { MAIN.post(() -> { if (active != null) active.close(reason); }); }
  static boolean isSpeaking() { return active != null; }
  static boolean alarmActive(Context c) {
    AudioManager audio = c.getSystemService(AudioManager.class);
    return audio != null && containsAlarm(audio.getActivePlaybackConfigurations());
  }
  private static boolean containsAlarm(List<AudioPlaybackConfiguration> configurations) {
    for (AudioPlaybackConfiguration c : configurations)
      if (c.getAudioAttributes().getUsage() == AudioAttributes.USAGE_ALARM) return true;
    return false;
  }
  private String blocked() {
    if (!TimeAnnouncementSettings.enabled(context)) return "DISABLED";
    long age = System.currentTimeMillis() - due;
    if (age < 0 || age >= TimeAnnouncementSchedule.MAX_LATENESS) return "STALE";
    if (alarmActive(context)) return "ALARM_ACTIVE";
    if (audio.getMode() != AudioManager.MODE_NORMAL) return "CALL_ACTIVE";
    if (audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return "SILENT_MODE";
    if (context.getSystemService(NotificationManager.class).getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL) return "DO_NOT_DISTURB";
    if (NotificationPriorityState.ordinaryBusyDelayMs() > 0 || MainActivity.voiceTestActive()) return "ORDINARY_SPEECH";
    return null;
  }
  private void begin() {
    String reason = blocked();
    if (reason != null) { close(reason); return; }
    audio.registerAudioPlaybackCallback(playback, MAIN); playbackRegistered = true;
    wake = context.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "yiwoo:time-speech");
    wake.acquire(TimeAnnouncementSchedule.MAX_LATENESS);
    MAIN.postDelayed(timeout, TimeAnnouncementSchedule.MAX_LATENESS);
    tts = new TextToSpeech(context, result -> MAIN.post(() -> ready(result)), context.getPackageName());
  }
  private void ready(int result) {
    if (closed) return;
    if (result != TextToSpeech.SUCCESS || tts.setLanguage(Locale.KOREA) < 0) { close("TTS_INIT_ERROR"); return; }
    String reason = blocked();
    if (reason != null) { close(reason); return; }
    AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true).setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener(change -> { if (change < 0) close("AUDIO_FOCUS_LOST"); }, MAIN).build();
    if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { close("FOCUS_DENIED"); return; }
    reason = blocked();
    if (reason != null) { close(reason); return; }
    tts.setAudioAttributes(attributes);
    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
      @Override public void onStart(String id) { MAIN.post(() -> {
        if (closed) return;
        String reason = blocked();
        if (reason != null) close(reason); else Log.i("YiwooTime", "TIME_AUDIO_START id=" + id);
      }); }
      @Override public void onAudioAvailable(String id, byte[] bytes) {
        if (audioBytes == 0 && bytes.length > 0) Log.i("YiwooTime", "TIME_FIRST_PCM due=" + due + " bytes=" + bytes.length);
        audioBytes += bytes.length;
      }
      @Override public void onDone(String id) { MAIN.post(() -> close("DONE")); }
      @Override public void onError(String id) { MAIN.post(() -> close("TTS_ERROR")); }
      @Override public void onStop(String id, boolean interrupted) { MAIN.post(() -> close("STOPPED")); }
    });
    SpeechPlaybackSettings.applyTo(context, tts);
    Bundle params = new Bundle();
    params.putString(NotificationPriorityState.PARAM_ORIGIN, NotificationPriorityState.ORIGIN_TIME);
    String text = TimeAnnouncementSchedule.speech(System.currentTimeMillis(), ZoneId.systemDefault());
    Log.i("YiwooTime", "TIME_TTS_REQUEST due=" + due + " text=" + text);
    if (tts.speak(text, TextToSpeech.QUEUE_ADD, params, "time_" + due) != TextToSpeech.SUCCESS) close("REQUEST_FAILED");
  }
  private void close(String reason) {
    if (closed) return;
    closed = true;
    MAIN.removeCallbacks(timeout);
    if (playbackRegistered) audio.unregisterAudioPlaybackCallback(playback);
    if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    if (focus != null) audio.abandonAudioFocusRequest(focus);
    if (wake != null && wake.isHeld()) wake.release();
    if (active == this) active = null;
    Log.i("YiwooTime", "TIME_FINISHED due=" + due + " reason=" + reason + " audioBytes=" + audioBytes);
    finished.run();
  }
}
