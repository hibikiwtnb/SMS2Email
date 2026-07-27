package com.codex.smsrelay;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int SMS_PERMISSION_REQUEST = 100;

    private EditText senderField;
    private EditText usernameField;
    private EditText authField;
    private EditText recipientField;
    private EditText hostField;
    private EditText portField;
    private Spinner securityField;
    private CheckBox enabledField;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildContent());
        loadSettings();
        requestSmsPermissionIfNeeded();
        updateStatus();
    }

    private View buildContent() {
        int padding = dp(16);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        TextView title = text("M02 短信轉寄", 24);
        title.setTextColor(Color.rgb(13, 71, 161));
        content.addView(title, matchWrap());

        TextView intro = text(getString(R.string.intro), 15);
        intro.setPadding(0, dp(8), 0, dp(16));
        content.addView(intro, matchWrap());

        hostField = field(getString(R.string.smtp_host), false);
        content.addView(hostField, matchWrap());

        portField = field(getString(R.string.smtp_port), false);
        portField.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(portField, matchWrap());

        securityField = new Spinner(this);
        String[] securityOptions = {
                AppSettings.SECURITY_SSL,
                AppSettings.SECURITY_STARTTLS,
                AppSettings.SECURITY_NONE
        };
        ArrayAdapter<String> securityAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                securityOptions
        );
        securityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        securityField.setAdapter(securityAdapter);
        securityField.setMinimumHeight(dp(48));
        content.addView(securityField, matchWrap());

        usernameField = field(getString(R.string.smtp_username), false);
        content.addView(usernameField, matchWrap());

        senderField = field(getString(R.string.sender_email), false);
        content.addView(senderField, matchWrap());

        authField = field(getString(R.string.auth_code), true);
        content.addView(authField, matchWrap());

        recipientField = field(getString(R.string.recipient_email), false);
        content.addView(recipientField, matchWrap());

        enabledField = new CheckBox(this);
        enabledField.setText(R.string.enabled);
        enabledField.setMinHeight(dp(48));
        content.addView(enabledField, matchWrap());

        Button saveButton = new Button(this);
        saveButton.setText(R.string.save);
        saveButton.setMinHeight(dp(48));
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
            }
        });
        content.addView(saveButton, matchWrap());

        Button testButton = new Button(this);
        testButton.setText(R.string.test);
        testButton.setMinHeight(dp(48));
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendTest();
            }
        });
        content.addView(testButton, matchWrap());

        statusView = text("", 14);
        statusView.setPadding(0, dp(16), 0, dp(8));
        content.addView(statusView, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        return scroll;
    }

    private void loadSettings() {
        hostField.setText(AppSettings.host(this));
        portField.setText(Integer.toString(AppSettings.port(this)));
        setSecuritySelection(AppSettings.security(this));
        usernameField.setText(AppSettings.username(this));
        senderField.setText(AppSettings.sender(this));
        recipientField.setText(AppSettings.recipient(this));
        enabledField.setChecked(AppSettings.enabled(this));
        try {
            authField.setText(AppSettings.authCode(this));
        } catch (Exception error) {
            authField.setText("");
        }
    }

    private void saveSettings() {
        try {
            String sender = senderField.getText().toString().trim();
            String username = usernameField.getText().toString().trim();
            String recipient = recipientField.getText().toString().trim();
            String host = hostField.getText().toString().trim();
            String portText = portField.getText().toString().trim();
            String security = securityField.getSelectedItem().toString();
            String authCode = authField.getText().toString();
            int port;
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException error) {
                toast("請輸入有效的 SMTP 連接埠");
                return;
            }
            if (host.isEmpty() || port < 1 || port > 65535) {
                toast("請輸入有效的 SMTP 伺服器與連接埠");
                return;
            }
            if (!isEmail(sender) || (!recipient.isEmpty() && !isEmail(recipient))) {
                toast("請輸入有效的郵箱地址");
                return;
            }
            boolean requestedEnabled = enabledField.isChecked();
            boolean effectiveEnabled = requestedEnabled && !authCode.isEmpty();
            AppSettings.save(
                    this,
                    host,
                    port,
                    security,
                    username,
                    sender,
                    authCode,
                    recipient,
                    effectiveEnabled
            );
            enabledField.setChecked(effectiveEnabled);
            if (effectiveEnabled) {
                requestSmsPermissionIfNeeded();
                RelayScheduler.schedule(this);
            }
            updateStatus();
            if (requestedEnabled && authCode.isEmpty()) {
                toast("授權碼已清除，自動轉寄已停用");
            } else {
                toast("設定已儲存");
            }
        } catch (Exception error) {
            toast("儲存失敗：" + error.getMessage());
        }
    }

    private void sendTest() {
        saveSettings();
        if (!AppSettings.isConfigured(this)) {
            return;
        }
        statusView.setText("正在發送測試郵件…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SmtpClient.send(
                            AppSettings.host(MainActivity.this),
                            AppSettings.port(MainActivity.this),
                            AppSettings.security(MainActivity.this),
                            AppSettings.username(MainActivity.this),
                            AppSettings.authCode(MainActivity.this),
                            AppSettings.sender(MainActivity.this),
                            AppSettings.recipient(MainActivity.this),
                            "[M02 短信轉寄] 測試成功",
                            "M02 已可經 SMTP 發送郵件。"
                    );
                    showResult("測試郵件已發送");
                } catch (final Exception error) {
                    showResult("發送失敗：" + error.getMessage());
                }
            }
        }, "smtp-test").start();
    }

    private void showResult(final String result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusView.setText(result);
                toast(result);
            }
        });
    }

    private void updateStatus() {
        boolean permissionGranted = checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
        MessageQueue queue = new MessageQueue(this);
        int pending;
        try {
            pending = queue.count();
        } finally {
            queue.close();
        }
        String configured = AppSettings.isConfigured(this) ? "已設定" : "尚未設定";
        String enabled = AppSettings.enabled(this) ? "已啟用" : "未啟用";
        String permission = permissionGranted ? "SMS 權限正常" : "尚未取得 SMS 權限";
        String lastError = AppSettings.lastError(this);
        String status = configured + " · " + enabled + " · " + permission + " · 待寄 " + pending + " 封";
        if (!lastError.isEmpty()) {
            status += "\n最近錯誤：" + lastError;
        }
        statusView.setText(status);
    }

    private void requestSmsPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, SMS_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_REQUEST) {
            updateStatus();
        }
    }

    private EditText field(String hint, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setMinHeight(dp(56));
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        return input;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean isEmail(String value) {
        return value.contains("@") && value.indexOf('@') > 0 && value.indexOf('@') < value.length() - 1;
    }

    private void setSecuritySelection(String security) {
        for (int index = 0; index < securityField.getCount(); index++) {
            if (security.equals(securityField.getItemAtPosition(index).toString())) {
                securityField.setSelection(index);
                return;
            }
        }
        securityField.setSelection(0);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }
}
