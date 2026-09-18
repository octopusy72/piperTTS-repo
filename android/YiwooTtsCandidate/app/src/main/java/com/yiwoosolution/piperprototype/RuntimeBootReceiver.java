package com.yiwoosolution.piperprototype;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class RuntimeBootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
        && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
    Log.i("YiwooRuntime", "BOOT_PRELOAD_TRIGGER action=" + intent.getAction());
    TimeAnnouncementScheduler.ensureScheduled(context);
    RuntimeRetentionService.ensureStarted(context);
    RuntimePreloadCoordinator.ensureRuntimePreloaded(context);
  }
}
