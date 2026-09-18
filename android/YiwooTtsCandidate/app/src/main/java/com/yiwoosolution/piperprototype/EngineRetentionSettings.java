package com.yiwoosolution.piperprototype;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent policy for the process-scoped Korean runtime. */
final class EngineRetentionSettings {
  enum Mode { ON_DEMAND, TIMED, ALWAYS }
  private static final String PREFS = "engine_retention";
  private static final String MODE = "mode";
  private static final String MINUTES = "idle_minutes";
  private static final int DEFAULT_MINUTES = 5;

  private EngineRetentionSettings() {}

  static Mode mode(Context context) {
    String value = prefs(context).getString(MODE, Mode.ALWAYS.name());
    try { return Mode.valueOf(value); } catch (IllegalArgumentException ignored) { return Mode.ALWAYS; }
  }

  static void setMode(Context context, Mode mode) {
    prefs(context).edit().putString(MODE, mode.name()).apply();
    PiperModelManager.shared(context).onRetentionPolicyChanged();
    EnglishModelManager.shared(context).onRetentionPolicyChanged();
    RuntimeRetentionService.runtimeChanged();
    RuntimePreloadCoordinator.ensureRuntimePreloaded(context);
  }

  static int idleMinutes(Context context) {
    return Math.max(1, Math.min(60, prefs(context).getInt(MINUTES, DEFAULT_MINUTES)));
  }

  static void setIdleMinutes(Context context, int minutes) {
    prefs(context).edit().putInt(MINUTES, Math.max(1, Math.min(60, minutes))).apply();
    PiperModelManager.shared(context).onRetentionPolicyChanged();
    EnglishModelManager.shared(context).onRetentionPolicyChanged();
    RuntimePreloadCoordinator.ensureRuntimePreloaded(context);
  }

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}
