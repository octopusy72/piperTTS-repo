package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Persistent notification-reading preferences; package names are canonical keys. */
final class NotificationSettings {
  static final String MODE_EXCLUDE = "exclude";
  static final String MODE_ALLOW = "allow";
  private static final String PREFS = "notification_reading";
  private static final String ENABLED = "enabled";
  private static final String MODE = "mode";
  private static final String PACKAGES = "packages";
  private static final String RESPECT_RINGER = "respect_ringer";
  private static final String CONTENT_MODE = "content_mode";

  private NotificationSettings() {}
  private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
  private static SharedPreferences legacy(Context c) { return c.getSharedPreferences("product_settings", Context.MODE_PRIVATE); }
  static boolean enabled(Context c) { return prefs(c).contains(ENABLED) ? prefs(c).getBoolean(ENABLED, false) : legacy(c).getBoolean("notification_read_enabled", false); }
  static void setEnabled(Context c, boolean value) { prefs(c).edit().putBoolean(ENABLED, value).apply(); legacy(c).edit().putBoolean("notification_read_enabled", value).apply(); RuntimeRetentionService.refresh(c); }
  static String mode(Context c) { if (prefs(c).contains(MODE)) return prefs(c).getString(MODE, MODE_EXCLUDE); return "ONLY_ALLOWED".equals(legacy(c).getString("app_filter_mode", "")) ? MODE_ALLOW : MODE_EXCLUDE; }
  static void setMode(Context c, String value) { prefs(c).edit().putString(MODE, value).apply(); legacy(c).edit().putString("app_filter_mode", MODE_ALLOW.equals(value) ? "ONLY_ALLOWED" : "ALL_EXCEPT_BLOCKED").apply(); }
  static Set<String> packages(Context c) { if (prefs(c).contains(PACKAGES)) return Collections.unmodifiableSet(new HashSet<>(prefs(c).getStringSet(PACKAGES, Collections.emptySet()))); String key=MODE_ALLOW.equals(mode(c))?"allowed_packages":"blocked_packages"; return Collections.unmodifiableSet(new HashSet<>(legacy(c).getStringSet(key, Collections.emptySet()))); }
  static void setPackages(Context c, Set<String> values) { prefs(c).edit().putStringSet(PACKAGES, new HashSet<>(values)).apply(); String key=MODE_ALLOW.equals(mode(c))?"allowed_packages":"blocked_packages"; legacy(c).edit().putStringSet(key, new HashSet<>(values)).apply(); }
  static boolean respectRinger(Context c) { return prefs(c).contains(RESPECT_RINGER) ? prefs(c).getBoolean(RESPECT_RINGER, true) : legacy(c).getBoolean("notification_respect_ringer_mode", true); }
  static void setRespectRinger(Context c, boolean value) { prefs(c).edit().putBoolean(RESPECT_RINGER, value).apply(); legacy(c).edit().putBoolean("notification_respect_ringer_mode", value).apply(); }
  static boolean fullContent(Context c) { return prefs(c).contains(CONTENT_MODE) ? "full".equals(prefs(c).getString(CONTENT_MODE, "alert")) : "FULL_CONTENT".equals(legacy(c).getString("notification_content_mode", "ALERT_ONLY")); }
  static void setFullContent(Context c, boolean value) { prefs(c).edit().putString(CONTENT_MODE, value ? "full" : "alert").apply(); legacy(c).edit().putString("notification_content_mode", value ? "FULL_CONTENT" : "ALERT_ONLY").apply(); }
}
