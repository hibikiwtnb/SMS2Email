package com.codex.smsrelay;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class AppSettings {
    private static final String PREFS = "relay_settings";
    private static final String KEY_ALIAS = "sms_relay_auth";
    private static final String KEY_SENDER = "sender";
    private static final String KEY_RECIPIENT = "recipient";
    private static final String KEY_AUTH = "auth";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_SECURITY = "security";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_LAST_SUCCESS = "last_success";
    static final String SECURITY_SSL = "SSL/TLS";
    static final String SECURITY_STARTTLS = "STARTTLS";
    static final String SECURITY_NONE = "None";

    private AppSettings() {
    }

    static void save(
            Context context,
            String host,
            int port,
            String security,
            String username,
            String sender,
            String authCode,
            String recipient,
            boolean enabled
    )
            throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_SENDER, sender.trim())
                .putString(KEY_RECIPIENT, recipient.trim().isEmpty() ? sender.trim() : recipient.trim())
                .putString(KEY_HOST, host.trim())
                .putInt(KEY_PORT, port)
                .putString(KEY_SECURITY, security)
                .putString(KEY_USERNAME, username.trim().isEmpty() ? sender.trim() : username.trim())
                .putBoolean(KEY_ENABLED, enabled);
        if (!authCode.isEmpty()) {
            editor.putString(KEY_AUTH, encrypt(authCode));
        } else {
            editor.remove(KEY_AUTH);
        }
        editor.apply();
    }

    static String sender(Context context) {
        return prefs(context).getString(KEY_SENDER, "");
    }

    static String username(Context context) {
        return prefs(context).getString(KEY_USERNAME, sender(context));
    }

    static String host(Context context) {
        return prefs(context).getString(KEY_HOST, "smtp.qq.com");
    }

    static int port(Context context) {
        return prefs(context).getInt(KEY_PORT, 465);
    }

    static String security(Context context) {
        return prefs(context).getString(KEY_SECURITY, SECURITY_SSL);
    }

    static String recipient(Context context) {
        return prefs(context).getString(KEY_RECIPIENT, "");
    }

    static String authCode(Context context) throws Exception {
        String encrypted = prefs(context).getString(KEY_AUTH, "");
        return encrypted.isEmpty() ? "" : decrypt(encrypted);
    }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    static void setLastError(Context context, String value) {
        prefs(context).edit().putString(KEY_LAST_ERROR, value).apply();
    }

    static String lastError(Context context) {
        return prefs(context).getString(KEY_LAST_ERROR, "");
    }

    static void markSuccess(Context context) {
        prefs(context).edit()
                .remove(KEY_LAST_ERROR)
                .putLong(KEY_LAST_SUCCESS, System.currentTimeMillis())
                .apply();
    }

    static boolean isConfigured(Context context) {
        try {
            return !host(context).isEmpty()
                    && port(context) > 0
                    && !username(context).isEmpty()
                    && !sender(context).isEmpty()
                    && !recipient(context).isEmpty()
                    && !authCode(context).isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + "."
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private static String decrypt(String stored) throws Exception {
        String[] parts = stored.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid encrypted value");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP));
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec);
        byte[] clear = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
        return new String(clear, StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
