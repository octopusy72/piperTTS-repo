package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Silent, bounded preparation of the same Korean session used by production TTS. */
final class TimeAnnouncementPreparation {
  static final long LEAD_MS = 60_000;
  private static final ScheduledExecutorService EXPIRY = Executors.newSingleThreadScheduledExecutor();
  private static final java.util.concurrent.ExecutorService WORKER = Executors.newSingleThreadExecutor();
  private static final Map<Long, Lease> leases = new HashMap<>();
  private static final class Lease {
    final Runnable release;
    final PowerManager.WakeLock wake;
    volatile boolean ready;
    Lease(Context c) {
      release = PiperModelManager.shared(c).holdForScheduledSpeech();
      wake = c.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yiwoo:TimePreparation");
    }
  }
  static synchronized boolean contains(long due) { return leases.containsKey(due); }
  static synchronized boolean ready(long due) { Lease l = leases.get(due); return l != null && l.ready; }
  static synchronized void prepare(Context context, long due) {
    Context c = context.getApplicationContext();
    long now = System.currentTimeMillis();
    if (!TimeAnnouncementSettings.enabled(c) || due != TimeAnnouncementSettings.next(c)
        || due <= now || leases.containsKey(due)) return;
    Lease lease = new Lease(c);
    leases.put(due, lease);
    long lifetime = Math.min(LEAD_MS, due - now) + TimeAnnouncementSchedule.MAX_LATENESS;
    lease.wake.acquire(lifetime);
    EXPIRY.schedule(() -> finish(due), lifetime, TimeUnit.MILLISECONDS);
    WORKER.execute(() -> {
      synchronized (TimeAnnouncementPreparation.class) { if (leases.get(due) != lease) return; }
      long start = android.os.SystemClock.elapsedRealtime();
      Log.i("YiwooTime", "TIME_PREPARE_BEGIN due=" + due);
      try {
        // Discard PCM; initialize and warm the real frontend/ORT path without audible playback.
        PiperModelManager.shared(c).synthesize("정각 한 시입니다.");
        lease.ready = true;
        Log.i("YiwooTime", "TIME_PREPARE_READY due=" + due + " remainingMs=" + (due - System.currentTimeMillis())
            + " loadMs=" + (android.os.SystemClock.elapsedRealtime() - start));
      } catch (Exception error) {
        Log.w("YiwooTime", "TIME_PREPARE_FAILED due=" + due, error);
        finish(due);
      }
      RuntimeRetentionService.runtimeChanged();
    });
  }
  static synchronized void finish(long due) {
    Lease lease = leases.remove(due);
    if (lease == null) return;
    lease.release.run();
    if (lease.wake.isHeld()) lease.wake.release();
    Log.i("YiwooTime", "TIME_PREPARE_RELEASE due=" + due);
  }
  static synchronized void cancelAll() {
    for (Long due : new java.util.ArrayList<>(leases.keySet())) finish(due);
  }
}
