package com.yiwoosolution.piperprototype;

import android.app.Application;

/** Starts the same process-scoped model preparation used by TTS requests. */
public final class YiwooCandidateApplication extends Application {
  @Override public void onCreate() {
    super.onCreate();
    RuntimePreloadCoordinator.ensureRuntimePreloaded(this);
    RuntimeRetentionService.ensureStarted(this);
  }
}
