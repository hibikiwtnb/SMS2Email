package com.codex.smsrelay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (AppSettings.enabled(context) && AppSettings.isConfigured(context)) {
            RelayScheduler.schedule(context);
        }
    }
}
