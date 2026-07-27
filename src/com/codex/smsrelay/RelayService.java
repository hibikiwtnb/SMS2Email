package com.codex.smsrelay;

import android.app.IntentService;
import android.content.Intent;

public final class RelayService extends IntentService {
    public RelayService() {
        super("sms-relay-immediate");
        setIntentRedelivery(true);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (RelayWorker.processQueue(this)) {
            RelayScheduler.schedule(this);
        }
    }
}
