package com.codex.smsrelay;

import android.content.Context;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class RelayWorker {
    private static final String TAG = "M02SmsRelay";
    private RelayWorker() {
    }

    static boolean processQueue(Context context) {
        if (!AppSettings.enabled(context) || !AppSettings.isConfigured(context)) {
            return false;
        }
        MessageQueue queue = new MessageQueue(context);
        try {
            for (int processed = 0; processed < 20; processed++) {
                MessageQueue.Item item = queue.first();
                if (item == null) {
                    return false;
                }
                try {
                    String time = new SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.getDefault()
                    ).format(new Date(item.receivedAt));
                    String subject = "[M02 短信] " + item.sender;
                    String body = "寄件號碼：" + item.sender
                            + "\n收到時間：" + time
                            + "\n\n"
                            + item.body;
                    SmtpClient.send(
                            AppSettings.host(context),
                            AppSettings.port(context),
                            AppSettings.security(context),
                            AppSettings.username(context),
                            AppSettings.authCode(context),
                            AppSettings.sender(context),
                            AppSettings.recipient(context),
                            subject,
                            body
                    );
                    queue.markSuccess(item.id);
                    AppSettings.markSuccess(context);
                } catch (Exception error) {
                    queue.markFailure(item.id);
                    String message = error.getClass().getSimpleName()
                            + ": "
                            + (error.getMessage() == null ? "unknown error" : error.getMessage());
                    AppSettings.setLastError(context, message);
                    Log.e(TAG, "Background SMTP delivery failed", error);
                    return true;
                }
            }
            return queue.count() > 0;
        } finally {
            queue.close();
        }
    }
}
