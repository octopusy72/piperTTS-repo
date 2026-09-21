package com.yiwoosolution.piperprototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/** Keeps user-requested readiness alive independently of the activity/TTS client. */
public final class RuntimeRetentionService extends Service {
  static final String ACTION_STOP_NOTIFICATION_READING = "com.yiwoosolution.koreantts.STOP_NOTIFICATION_READING";
  private static final String CHANNEL = "runtime_readiness";
  private static final int NOTIFICATION = 8523;
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static RuntimeRetentionService active;
  private static volatile boolean notificationReading;
  private boolean preparing;
  private String statusText = "준비 중 · 잠시만 기다려 주세요";
  private int failures;
  private final Runnable retry = this::prepare;

  static void ensureStarted(Context context) {
    if (EngineRetentionSettings.mode(context) == EngineRetentionSettings.Mode.ON_DEMAND && !automationEnabled(context)) return;
    try { context.startForegroundService(new Intent(context, RuntimeRetentionService.class)); }
    catch (IllegalStateException | SecurityException error) {
      // Background-start restrictions must not prevent the bound TTS request.
      Log.w("YiwooRuntime", "RETENTION_START_DEFERRED", error);
    }
  }

  static void runtimeChanged() {
    MAIN.post(() -> { if (active != null) active.checkRetention(); });
  }

  static void notificationPlaybackChanged(boolean reading) {
    notificationReading = reading;
    MAIN.post(() -> { if (active != null) active.updateNotification(); });
  }

  static boolean notificationAccess(Context c) {
    String listeners = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
    return listeners != null && listeners.contains(new android.content.ComponentName(c, YiwooNotificationListenerService.class).flattenToString());
  }
  private static boolean automationEnabled(Context c) {
    return TimeAnnouncementSettings.enabled(c) || NotificationSettings.enabled(c) && notificationAccess(c);
  }
  static void refresh(Context c) {
    MAIN.post(() -> {
      if (active != null) active.checkRetention();
      ensureStarted(c.getApplicationContext());
    });
  }

  @Override public void onCreate() {
    super.onCreate();
    active = this;
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel(CHANNEL, "TTS 음성 서비스 상태", NotificationManager.IMPORTANCE_LOW));
    startForeground(NOTIFICATION, notification("준비 중 · 잠시만 기다려 주세요"));
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i("YiwooRuntime", "RETENTION_SERVICE_START restarted=" + (intent == null)
        + " policy=" + EngineRetentionSettings.mode(this));
    if (intent != null && ACTION_STOP_NOTIFICATION_READING.equals(intent.getAction())) {
      NotificationPriorityState.interruptLowPriority();
      notificationPlaybackChanged(false);
      checkRetention();
      return EngineRetentionSettings.mode(this) == EngineRetentionSettings.Mode.ALWAYS || automationEnabled(this)
          ? START_STICKY : START_NOT_STICKY;
    }
    boolean tick = intent != null && TimeAnnouncementScheduler.ACTION.equals(intent.getAction());
    boolean warm = intent != null && TimeAnnouncementScheduler.PREPARE.equals(intent.getAction());
    if (warm) TimeAnnouncementPreparation.prepare(this, intent.getLongExtra("due", 0));
    if (tick && TimeAnnouncementScheduler.consume(this, intent.getLongExtra("due", 0))) {
      long due = intent.getLongExtra("due", 0);
      Log.i("YiwooTime", "TIME_READY_AT_DEADLINE due=" + due + " ready=" + TimeAnnouncementPreparation.ready(due));
      TimeAnnouncementPlayer.start(this, due, () -> { TimeAnnouncementPreparation.finish(due); checkRetention(); });
    }
    TimeAnnouncementScheduler.ensureScheduled(this);
    if (EngineRetentionSettings.mode(this) == EngineRetentionSettings.Mode.ON_DEMAND && !automationEnabled(this)) {
      stopSelf(); return START_NOT_STICKY;
    }
    if (EngineRetentionSettings.mode(this) == EngineRetentionSettings.Mode.ON_DEMAND) {
      checkRetention();
    } else if (!tick && !warm && !TimeAnnouncementPreparation.contains(TimeAnnouncementSettings.next(this))) prepare();
    return EngineRetentionSettings.mode(this) == EngineRetentionSettings.Mode.ALWAYS || automationEnabled(this) ? START_STICKY : START_NOT_STICKY;
  }

  private void prepare() {
    if (preparing || active != this) return;
    if (EngineRetentionSettings.mode(this) == EngineRetentionSettings.Mode.ON_DEMAND) { checkRetention(); return; }
    MAIN.removeCallbacks(retry);
    preparing = true;
    boolean en = VoiceRegistry.selectedEnglish(this);
    java.util.concurrent.CompletableFuture<Void> ready = RuntimePreloadCoordinator.prepare(this, en);
    if (!ready.isDone()) { statusText = "준비 중 · 잠시만 기다려 주세요"; updateNotification(); }
    ready.whenComplete((unused, error) -> MAIN.post(() -> {
      if (active != this) return;
      preparing = false;
      if (EngineRetentionSettings.mode(this) == EngineRetentionSettings.Mode.ON_DEMAND) { checkRetention(); return; }
      if (error == null) {
        failures = 0;
        statusText = "동작 중 · 음성 요청을 기다리고 있습니다"; updateNotification();
        checkRetention();
      } else {
        statusText = "준비 지연 · 다시 요청하면 재시도합니다"; updateNotification();
        if (EngineRetentionSettings.mode(this) == EngineRetentionSettings.Mode.ALWAYS) {
          statusText = "준비 지연 · 자동으로 다시 시도합니다"; updateNotification();
          long delay = Math.min(60_000L, 5_000L << Math.min(failures++, 4));
          Log.w("YiwooRuntime", "PRELOAD_RETRY delayMs=" + delay, error);
          MAIN.postDelayed(retry, delay);
        } else if (!automationEnabled(this)) stopSelf();
      }
    }));
  }

  private void checkRetention() {
    EngineRetentionSettings.Mode mode = EngineRetentionSettings.mode(this);
    if (mode == EngineRetentionSettings.Mode.ON_DEMAND) {
      long next = TimeAnnouncementSettings.next(this);
      statusText = TimeAnnouncementPreparation.ready(next) ? "시간 안내 준비 완료 · 예약 시각을 기다리고 있습니다"
          : TimeAnnouncementPreparation.contains(next) ? "시간 안내 준비 중 · 예약 전에 음성을 준비합니다"
          : "대기 중 · 필요한 때 음성을 준비합니다";
      if (!automationEnabled(this) && !TimeAnnouncementPlayer.isSpeaking()) { stopSelf(); return; }
    }
    if (mode == EngineRetentionSettings.Mode.TIMED && !preparing && !RuntimePreloadCoordinator.isPreparing()
        && !PiperModelManager.shared(this).isReady() && !EnglishModelManager.shared(this).isReady()) {
      statusText = "대기 중 · 필요한 때 음성을 준비합니다";
      if (!automationEnabled(this) && !TimeAnnouncementPlayer.isSpeaking()) { stopSelf(); return; }
    }
    updateNotification();
  }

  private void updateNotification() {
    getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(statusText));
  }

  private Notification notification(String text) {
    Intent intent = new Intent(this, MainActivity.class);
    PendingIntent open = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    String language = VoiceRegistry.selectedEnglish(this) ? "영어" : "한국어";
    String retention = EngineRetentionSettings.mode(this) == EngineRetentionSettings.Mode.ALWAYS
        ? "항상 준비" : EngineRetentionSettings.mode(this) == EngineRetentionSettings.Mode.ON_DEMAND
        ? "사용할 때 준비" : EngineRetentionSettings.idleMinutes(this) + "분간 유지";
    StringBuilder features = new StringBuilder();
    if (NotificationSettings.enabled(this)) features.append(notificationAccess(this) ? "알림 읽기 동작 중" : "알림 읽기 · 접근 권한 필요");
    if (TimeAnnouncementSettings.enabled(this)) {
      if (features.length() > 0) features.append(" · ");
      features.append("시간 알려주기 동작 중");
    }
    String summary = notificationReading ? "현재 알림 내용을 음성으로 읽고 있습니다" : features.length() > 0 ? features.toString() : text;
    String title = notificationReading ? "알림을 읽고 있습니다" : "TTS 음성 서비스";
    Notification.Builder builder = new Notification.Builder(this, CHANNEL).setSmallIcon(com.yiwoosolution.koreantts.R.drawable.ic_tts_status)
        .setContentTitle(title).setContentText(summary).setContentIntent(open)
        .setStyle(new Notification.BigTextStyle().bigText(notificationReading ? summary : text + (features.length() > 0 ? "\n" + features : "")))
        .setSubText(language + " · " + retention).setColor(0xff19634e)
        .setCategory(Notification.CATEGORY_SERVICE).setShowWhen(false)
        .setOngoing(true).setOnlyAlertOnce(true);
    if (notificationReading) {
      Intent stopIntent = new Intent(this, RuntimeRetentionService.class).setAction(ACTION_STOP_NOTIFICATION_READING);
      PendingIntent stop = PendingIntent.getService(this, 1, stopIntent,
          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      builder.addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "읽기 중지", stop).build());
    }
    return builder.build();
  }

  @Override public void onDestroy() {
    if (active == this) active = null;
    MAIN.removeCallbacks(retry);
    TimeAnnouncementPlayer.interrupt("SERVICE_STOPPED");
    notificationReading = false;
    TimeAnnouncementPreparation.cancelAll();
    stopForeground(true);
    Log.i("YiwooRuntime", "RETENTION_SERVICE_STOP");
    super.onDestroy();
  }
  @Override public IBinder onBind(Intent intent) { return null; }
}
