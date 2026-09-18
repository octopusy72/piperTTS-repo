package com.yiwoosolution.piperprototype;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.time.ZoneId;

final class TimeAnnouncementScheduler {
  static final String ACTION = "com.yiwoosolution.koreantts.ANNOUNCE_TIME";
  static final String PREPARE = "com.yiwoosolution.koreantts.PREPARE_TIME";
  static boolean exactAllowed(Context c) {
    return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
  }
  static synchronized void reset(Context c) {
    TimeAnnouncementPreparation.cancelAll();
    TimeAnnouncementSettings.prefs(c).edit().putLong("anchor", System.currentTimeMillis() / 60000 * 60000)
        .remove("next").commit();
    ensureScheduled(c);
  }
  static synchronized void ensureScheduled(Context c) {
    AlarmManager alarms = c.getSystemService(AlarmManager.class);
    if (!TimeAnnouncementSettings.enabled(c)) {
      alarms.cancel(operation(c, 0));
      alarms.cancel(preparation(c, 0));
      TimeAnnouncementPreparation.cancelAll();
      TimeAnnouncementSettings.prefs(c).edit().remove("next").commit();
      return;
    }
    long now = System.currentTimeMillis();
    long next = TimeAnnouncementSettings.next(c);
    // Preserve a just-delivered alarm during cold Application/Service startup.
    if (next == 0 || now - next >= TimeAnnouncementSchedule.MAX_LATENESS) next = calculateNext(c, now);
    schedule(c, next);
  }
  private static long calculateNext(Context c, long now) {
    return TimeAnnouncementSchedule.next(now, ZoneId.systemDefault(), TimeAnnouncementSettings.hourly(c),
        TimeAnnouncementSettings.minutes(c), TimeAnnouncementSettings.prefs(c).getLong("anchor", now / 60000 * 60000));
  }
  private static void schedule(Context c, long next) {
    TimeAnnouncementSettings.prefs(c).edit().putLong("next", next).commit();
    AlarmManager alarms = c.getSystemService(AlarmManager.class);
    PendingIntent operation = operation(c, next);
    try {
      if (exactAllowed(c)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, operation);
      else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, operation);
    } catch (SecurityException denied) {
      alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, operation);
    }
    Log.i("YiwooTime", "TIME_SCHEDULED at=" + next + " exact=" + exactAllowed(c));
    if (!TimeAnnouncementPreparation.contains(next) && next > System.currentTimeMillis()) {
      long warmAt = Math.max(System.currentTimeMillis() + 100, next - TimeAnnouncementPreparation.LEAD_MS);
      // Do not spend the allow-while-idle quota on preparation and delay the actual announcement.
      try {
        if (exactAllowed(c)) alarms.setExact(AlarmManager.RTC_WAKEUP, warmAt, preparation(c, next));
        else alarms.set(AlarmManager.RTC_WAKEUP, warmAt, preparation(c, next));
      } catch (SecurityException denied) {
        alarms.set(AlarmManager.RTC_WAKEUP, warmAt, preparation(c, next));
      }
    }
  }
  private static PendingIntent preparation(Context c, long due) {
    return PendingIntent.getForegroundService(c, 9032,
        new Intent(c, RuntimeRetentionService.class).setAction(PREPARE).putExtra("due", due),
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
  private static PendingIntent operation(Context c, long due) {
    Intent intent = new Intent(c, RuntimeRetentionService.class).setAction(ACTION).putExtra("due", due);
    return PendingIntent.getForegroundService(c, 9031, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
  }
  static synchronized boolean consume(Context c, long received) {
    if (!TimeAnnouncementSettings.enabled(c)) return false;
    SharedPreferences p = TimeAnnouncementSettings.prefs(c);
    long now = System.currentTimeMillis(), expected = p.getLong("next", 0);
    boolean valid = TimeAnnouncementSchedule.due(now, expected, received, p.getLong("last", 0));
    if (received == expected && now >= expected) {
      p.edit().putLong("last", received).commit();
      schedule(c, calculateNext(c, now));
    }
    Log.i("YiwooTime", "TIME_DUE accepted=" + valid + " due=" + received);
    return valid;
  }
}
