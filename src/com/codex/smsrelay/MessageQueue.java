package com.codex.smsrelay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

final class MessageQueue extends SQLiteOpenHelper {
    static final class Item {
        final long id;
        final String sender;
        final String body;
        final long receivedAt;

        Item(long id, String sender, String body, long receivedAt) {
            this.id = id;
            this.sender = sender;
            this.body = body;
            this.receivedAt = receivedAt;
        }
    }

    MessageQueue(Context context) {
        super(context, "relay_queue.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "sender TEXT NOT NULL,"
                + "body TEXT NOT NULL,"
                + "received_at INTEGER NOT NULL,"
                + "attempts INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    long enqueue(String sender, String body, long receivedAt) {
        ContentValues values = new ContentValues();
        values.put("sender", sender);
        values.put("body", body);
        values.put("received_at", receivedAt);
        return getWritableDatabase().insertOrThrow("messages", null, values);
    }

    Item first() {
        Cursor cursor = getReadableDatabase().query(
                "messages",
                new String[]{"id", "sender", "body", "received_at"},
                null,
                null,
                null,
                null,
                "id ASC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new Item(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getLong(3)
            );
        } finally {
            cursor.close();
        }
    }

    void markSuccess(long id) {
        getWritableDatabase().delete("messages", "id=?", new String[]{Long.toString(id)});
    }

    void markFailure(long id) {
        getWritableDatabase().execSQL(
                "UPDATE messages SET attempts=attempts+1 WHERE id=?",
                new Object[]{id}
        );
    }

    int count() {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM messages", null);
        try {
            cursor.moveToFirst();
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }
}
