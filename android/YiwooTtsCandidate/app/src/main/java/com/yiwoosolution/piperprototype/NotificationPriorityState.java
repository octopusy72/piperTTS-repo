package com.yiwoosolution.piperprototype;

import android.os.SystemClock;

/** Shared ordinary-TTS versus notification priority state, adapted from production. */
final class NotificationPriorityState {
  static final String PARAM_ORIGIN = "com.yiwoosolution.koreantts.ORIGIN";
  static final String ORIGIN_NOTIFICATION = "notification";
  static final String ORIGIN_TIME = "time_announcement";
  private static final long QUIET_GRACE_MS = 600L;
  private static int ordinaryActive;
  private static long busyUntil;
  private static volatile Runnable interruptNotification;
  private static volatile boolean selectedTextRequest;

  private NotificationPriorityState() {}
  static synchronized void ordinaryStarted() { ordinaryActive++; }
  static synchronized void ordinaryFinished(long audioDurationMs) { if (ordinaryActive > 0) ordinaryActive--; busyUntil = Math.max(busyUntil, SystemClock.elapsedRealtime() + Math.max(0, audioDurationMs) + QUIET_GRACE_MS); }
  static synchronized void ordinaryStopped() { ordinaryActive = 0; busyUntil = SystemClock.elapsedRealtime() + QUIET_GRACE_MS; }
  static synchronized long ordinaryBusyDelayMs() { long now=SystemClock.elapsedRealtime(); return ordinaryActive>0 ? QUIET_GRACE_MS : Math.max(0, busyUntil-now); }
  static void registerInterrupt(Runnable callback) { interruptNotification = callback; }
  static void interruptLowPriority() { Runnable callback=interruptNotification; if(callback!=null) callback.run(); }
  static void selectedTextStarted() { selectedTextRequest = true; }
  static void selectedTextFinished() { selectedTextRequest = false; }
  static boolean isSelectedTextRequest() { return selectedTextRequest; }
}
