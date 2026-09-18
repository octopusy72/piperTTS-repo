package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.content.SharedPreferences;

final class TimeAnnouncementSettings {
  static final String PREFS = "time_announcements";
  static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
  static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", false); }
  static boolean hourly(Context c) { return prefs(c).getBoolean("hourly", true); }
  static int minutes(Context c) { return Math.max(1, Math.min(1440, prefs(c).getInt("minutes", 30))); }
  static long next(Context c) { return prefs(c).getLong("next", 0); }
  static void setEnabled(Context c, boolean enabled) {
    prefs(c).edit().putBoolean("enabled", enabled).apply();
    changed(c);
  }
  static void setPeriod(Context c, boolean hourly, int minutes) {
    if (minutes < 1 || minutes > 1440) throw new IllegalArgumentException("1분부터 24시간까지 지정해 주세요.");
    prefs(c).edit().putBoolean("hourly", hourly).putInt("minutes", minutes).apply();
    changed(c);
  }
  private static void changed(Context c) {
    TimeAnnouncementPlayer.interrupt("SETTINGS_CHANGED");
    TimeAnnouncementScheduler.reset(c);
    RuntimeRetentionService.refresh(c);
  }
  static String summary(Context c) {
    if (!enabled(c)) return "원하는 간격으로 현재 시간을 들어보세요";
    return hourly(c) ? "매 정각 알려드려요" : intervalLabel(minutes(c)) + "마다 알려드려요";
  }
  static String intervalLabel(int minutes) {
    return (minutes >= 60 ? minutes / 60 + "시간" : "")
        + (minutes >= 60 && minutes % 60 != 0 ? " " : "")
        + (minutes % 60 != 0 ? minutes % 60 + "분" : "");
  }
}
