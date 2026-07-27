package com.codex.smsrelay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public final class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())
                || !AppSettings.enabled(context)
                || !AppSettings.isConfigured(context)) {
            return;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }
        Object[] pdus = (Object[]) extras.get("pdus");
        if (pdus == null || pdus.length == 0) {
            return;
        }
        String format = extras.getString("format");
        String sender = "";
        StringBuilder body = new StringBuilder();
        long receivedAt = System.currentTimeMillis();
        for (Object pdu : pdus) {
            SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (message == null) {
                continue;
            }
            if (sender.isEmpty()) {
                sender = message.getDisplayOriginatingAddress();
                receivedAt = message.getTimestampMillis();
            }
            body.append(message.getDisplayMessageBody());
        }
        if (body.length() == 0) {
            return;
        }

        MessageQueue queue = new MessageQueue(context);
        try {
            queue.enqueue(sender, body.toString(), receivedAt);
        } finally {
            queue.close();
        }
        context.startService(new Intent(context, RelayService.class));
    }
}
