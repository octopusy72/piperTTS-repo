package com.yiwoosolution.piperprototype;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Rebase after wall-clock or zone changes; never replay missed announcements. */
public final class TimeAnnouncementReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    TimeAnnouncementScheduler.reset(context);
    RuntimeRetentionService.refresh(context);
  }
}
