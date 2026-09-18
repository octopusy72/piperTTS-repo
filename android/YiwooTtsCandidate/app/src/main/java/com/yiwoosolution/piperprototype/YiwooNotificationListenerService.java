package com.yiwoosolution.piperprototype;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/** Reads filtered notifications through the Android TTS framework only. */
public final class YiwooNotificationListenerService extends NotificationListenerService {
  private static final String TAG = "YiwooNotification";
  private static final int MAX_PENDING = 3;
  private static final long DEDUPE_WINDOW_MS = 10 * 60 * 1000L;
  private static final long BURST_WINDOW_MS = 550L;
  private final Map<String, DedupeEntry> recent = new LinkedHashMap<String, DedupeEntry>(128, 0.75f, true);
  private TextToSpeech tts;
  private boolean ttsReady;
  private int pending;
  private String waitingText;
  private int burstCount;
  private String burstFirstText;
  private Runnable burstTask;
  private final Handler handler = new Handler(Looper.getMainLooper());

  @Override public void onListenerConnected() {
    super.onListenerConnected();
    Log.i(TAG, "LISTENER_CONNECTED enabled=" + NotificationSettings.enabled(this));
    RuntimeRetentionService.refresh(this);
    NotificationPriorityState.registerInterrupt(() -> handler.post(() -> { if (tts != null) { tts.stop(); pending = 0; } }));
    if (NotificationSettings.enabled(this)) {
      ensureTts();
      // Reconnect must honor the persisted retention policy without starting
      // an unconditional background model load.
      RuntimePreloadCoordinator.ensureRuntimePreloaded(this);
    }
  }

  @Override public void onNotificationPosted(StatusBarNotification sbn) {
    if (sbn.getNotification() != null && Notification.CATEGORY_ALARM.equals(sbn.getNotification().category))
      TimeAnnouncementPlayer.interrupt("ALARM_NOTIFICATION");
    Log.i(TAG, "YIWOO_NOTIF_RECEIVED key=" + sbn.getKey());
    if (!NotificationSettings.enabled(this)) { Log.i(TAG, "YIWOO_NOTIF_FILTER_REJECT reason=DISABLED"); return; }
    Notification n = sbn.getNotification();
    Log.i(TAG, "YIWOO_NOTIF_PROPERTIES category=" + (n == null ? "null" : n.category) + " flags=" + (n == null ? -1 : n.flags));
    if (sbn.getPackageName().equals(getPackageName())
        || n == null
        || Notification.CATEGORY_ALARM.equals(n.category)
        || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0
        || (n.flags & Notification.FLAG_ONGOING_EVENT) != 0) { Log.i(TAG, "YIWOO_NOTIF_FILTER_REJECT reason=OWN_ALARM_GROUP_ONGOING"); return; }
    if (!isAllowed(sbn)) { Log.i(TAG, "YIWOO_NOTIF_FILTER_REJECT reason=POLICY"); return; }
    Log.i(TAG, "YIWOO_NOTIF_FILTER_ACCEPT");
    String text = extractSpeech(sbn);
    if (text == null || text.isEmpty()) { Log.i(TAG, "YIWOO_NOTIF_FILTER_REJECT reason=EMPTY"); return; }
    if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
      Log.i(TAG, "YIWOO_NOTIF_FINAL_TEXT text=" + text);
    } else {
      Log.i(TAG, "YIWOO_NOTIF_FINAL_TEXT_LENGTH chars=" + text.length());
    }
    if (duplicate(sbn, text)) { Log.i(TAG, "YIWOO_NOTIF_DEDUPE_REJECT"); return; }
    Log.i(TAG, "YIWOO_NOTIF_DEDUPE_ACCEPT");
    offerBurst(text);
  }

  private boolean isAllowed(StatusBarNotification sbn) {
    if (getPackageName().equals(sbn.getPackageName())) return false;
    Notification n = sbn.getNotification();
    if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return false;
    // Match the legacy low-value policy: ongoing service/media/progress events
    // are not spoken. User-facing message notifications are not ongoing.
    if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return false;
    if (NotificationSettings.respectRinger(this)) {
      AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
      if (audio != null && (audio.getRingerMode() == AudioManager.RINGER_MODE_SILENT || audio.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)) return false;
    }
    NotificationManager notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    try { if (notifications != null && notifications.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL) return false; }
    catch (SecurityException ignored) { return false; }
    String mode = NotificationSettings.mode(this);
    Set<String> packages = NotificationSettings.packages(this);
    return NotificationSettings.MODE_ALLOW.equals(mode) ? packages.contains(sbn.getPackageName()) : !packages.contains(sbn.getPackageName());
  }

  private String extractSpeech(StatusBarNotification sbn) {
    Notification n = sbn.getNotification();
    Bundle extras = n.extras;
    String title = clean(extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE));
    String body = clean(extras == null ? null : extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
    if (body == null) body = clean(extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT));
    String sender = null;
    if (android.os.Build.VERSION.SDK_INT >= 24 && extras != null) {
      Parcelable[] rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
      if (rawMessages != null) {
        List<Notification.MessagingStyle.Message> messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(rawMessages);
        if (!messages.isEmpty()) {
          Notification.MessagingStyle.Message latest = messages.get(messages.size() - 1);
          body = clean(latest.getText());
          sender = clean(latest.getSender());
          if (title == null) title = sender;
        }
      }
    }
    String app = appLabel(sbn.getPackageName());
    List<String> parts = new ArrayList<>();
    if (isReadableNotificationText(app)) addDistinct(parts, app + "에서 알림이 왔습니다");
    String source = sender != null ? sender : title;
    String normalizedSource = normalizePhone(source);
    if (normalizedSource != null) source = "발신자는 " + normalizedSource + " 번호입니다";
    if (isReadableNotificationText(source)) addDistinct(parts, source);
    if (NotificationSettings.fullContent(this) && isReadableNotificationText(body)
        && !body.equals(source) && !body.equals(app)) addDistinct(parts, body);
    if (parts.isEmpty()) return null;
    return joinSentences(parts);
  }

  private boolean duplicate(StatusBarNotification sbn, String text) {
    long now = System.currentTimeMillis();
    recent.entrySet().removeIf(e -> now - e.getValue().timestamp >= DEDUPE_WINDOW_MS);
    String key = sbn.getKey();
    DedupeEntry previous = recent.get(key);
    if (previous != null && previous.fingerprint.equals(text)) {
      previous.timestamp = now;
      return true;
    }
    recent.put(key, new DedupeEntry(text, now));
    while (recent.size() > 128) recent.remove(recent.keySet().iterator().next());
    return false;
  }

  private synchronized void enqueue(String text) {
    ensureTts();
    if (!ttsReady) { waitingText = text; return; }
    long ordinaryDelay = NotificationPriorityState.ordinaryBusyDelayMs();
    if (ordinaryDelay > 0) { handler.postDelayed(() -> enqueue(text), ordinaryDelay); return; }
    if (pending >= MAX_PENDING) { tts.stop(); pending = 0; }
    pending++;
    String id = "notification_" + System.nanoTime();
    android.os.Bundle params = new android.os.Bundle(); params.putString(NotificationPriorityState.PARAM_ORIGIN, NotificationPriorityState.ORIGIN_NOTIFICATION);
    SpeechPlaybackSettings.applyTo(this, tts);
    tts.speak(text, TextToSpeech.QUEUE_ADD, params, id);
    Log.i(TAG, "YIWOO_NOTIF_TTS_REQUEST");
    Log.i(TAG, "NOTIFICATION_QUEUED chars=" + text.length() + " pending=" + pending);
  }

  /** Legacy behavior: globally coalesce notifications arriving within 550 ms. */
  private synchronized void offerBurst(String text) {
    if (burstCount == 0) burstFirstText = text;
    else burstFirstText = null;
    burstCount++;
    if (burstTask != null) return;
    burstTask = () -> {
      synchronized (YiwooNotificationListenerService.this) {
        int count = burstCount;
        String first = burstFirstText;
        burstCount = 0;
        burstFirstText = null;
        burstTask = null;
        if (count == 1 && first != null) { Log.i(TAG, "YIWOO_NOTIF_BURST_SINGLE"); enqueue(first); }
        else if (count > 1) { Log.i(TAG, "YIWOO_NOTIF_BURST_SUMMARY count=" + count); enqueue("새로운 알림이 " + count + "개 왔습니다."); }
      }
    };
    handler.postDelayed(burstTask, BURST_WINDOW_MS);
  }

  private synchronized void ensureTts() {
    if (tts != null) return;
    tts = new TextToSpeech(this, result -> handler.postDelayed(() -> finishTtsInit(result, 0), 300L), getPackageName());
    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
      @Override public void onStart(String id) { Log.i(TAG, "YIWOO_NOTIF_CALLBACK_START"); }
      @Override public void onDone(String id) { Log.i(TAG, "YIWOO_NOTIF_CALLBACK_DONE"); synchronized (YiwooNotificationListenerService.this) { if (pending > 0) pending--; } }
      @Override public void onError(String id) { Log.i(TAG, "YIWOO_NOTIF_CALLBACK_ERROR"); synchronized (YiwooNotificationListenerService.this) { if (pending > 0) pending--; } }
    });
  }

  private synchronized void finishTtsInit(int initResult, int attempt) {
    if (tts == null) return;
    int languageResult = initResult == TextToSpeech.SUCCESS ? tts.setLanguage(Locale.KOREA) : TextToSpeech.ERROR;
    // The engine may report a transient voice-list warning while onLoadLanguage
    // is completing; the framework service still accepts the Korean request.
    ttsReady = initResult == TextToSpeech.SUCCESS;
    Log.i(TAG, "TTS_READY=" + ttsReady + " languageResult=" + languageResult + " attempt=" + attempt);
    if (!ttsReady && attempt < 5) {
      handler.postDelayed(() -> finishTtsInit(TextToSpeech.SUCCESS, attempt + 1), 500L);
      return;
    }
    if (ttsReady && waitingText != null) { String text = waitingText; waitingText = null; enqueue(text); }
  }

  private String appLabel(String packageName) {
    try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString(); }
    catch (Exception ignored) { return null; }
  }
  private static String normalizePhone(String value) {
    if (value == null) return null;
    String text = value.trim().replace('\u2011','-').replace('\u2012','-').replace('\u2013','-').replace('\u2014','-').replace('\u2212','-').replaceAll("\\s+", " ");
    text = text.replaceFirst("^\\((0\\d{1,2})\\)[ -]?", "$1 ");
    String digits;
    if (text.matches("^010[- ]?\\d{3,4}[- ]?\\d{4}$") || text.matches("^02[- ]\\d{3,4}[- ]\\d{4}$") || text.matches("^0\\d{2}[- ]\\d{3,4}[- ]\\d{4}$") || text.matches("^1\\d{3}[- ]\\d{4}$")) {
      digits = text.replaceAll("[^0-9]", "");
    } else return null;
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < digits.length(); i++) {
      if (i > 0) out.append(' ');
      out.append(new String[]{"공","일","이","삼","사","오","육","칠","팔","구"}[digits.charAt(i)-'0']);
    }
    return out.toString();
  }
  private static String clean(CharSequence value) { if (value == null) return null; String s = value.toString().replaceAll("\\s+", " ").trim(); return s.isEmpty() ? null : s; }
  private static void addDistinct(List<String> parts, String value) { if (value != null && !parts.contains(value)) parts.add(value); }
  private static boolean isReadableNotificationText(String value) {
    if (value == null || value.isEmpty()) return false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isISOControl(c) && !Character.isWhitespace(c)) return false;
    }
    return true;
  }
  private static String joinSentences(List<String> parts) { StringBuilder out = new StringBuilder(); for (String p : parts) { if (out.length() > 0) out.append(' '); String s=p.trim(); if (!s.matches(".*[.!?]$")) s += "."; out.append(s); } return out.toString(); }
  private static final class DedupeEntry { final String fingerprint; long timestamp; DedupeEntry(String f,long t){fingerprint=f;timestamp=t;} }

  @Override public void onDestroy() {
    RuntimeRetentionService.refresh(this);
    NotificationPriorityState.registerInterrupt(null);
    synchronized (this) {
      if (burstTask != null) handler.removeCallbacks(burstTask);
      burstTask = null; burstCount = 0; burstFirstText = null;
      if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }
    super.onDestroy();
    Log.i(TAG, "LISTENER_DESTROYED");
  }
}
