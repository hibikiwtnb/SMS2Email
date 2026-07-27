package com.codex.smsrelay;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

final class SmtpClient {
    private static final int TIMEOUT_MS = 15000;

    private SmtpClient() {
    }

    static void send(
            String host,
            int port,
            String security,
            String username,
            String authCode,
            String sender,
            String recipient,
            String subject,
            String body
    )
            throws Exception {
        Socket socket;
        if (AppSettings.SECURITY_SSL.equals(security)) {
            socket = SSLSocketFactory.getDefault().createSocket();
        } else {
            socket = new Socket();
        }
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        if (socket instanceof SSLSocket) {
            ((SSLSocket) socket).startHandshake();
        }
        try {
            Session session = new Session(socket);
            expect(session.reader, 220);
            command(session.writer, session.reader, "EHLO localhost", 250);

            if (AppSettings.SECURITY_STARTTLS.equals(security)) {
                command(session.writer, session.reader, "STARTTLS", 220);
                SSLSocketFactory sslFactory =
                        (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket secureSocket = (SSLSocket) sslFactory.createSocket(
                        socket,
                        host,
                        port,
                        true
                );
                secureSocket.setSoTimeout(TIMEOUT_MS);
                secureSocket.startHandshake();
                socket = secureSocket;
                session = new Session(socket);
                command(session.writer, session.reader, "EHLO localhost", 250);
            }

            command(session.writer, session.reader, "AUTH LOGIN", 334);
            command(session.writer, session.reader, base64(username), 334);
            command(session.writer, session.reader, base64(authCode), 235);
            command(session.writer, session.reader, "MAIL FROM:<" + sender + ">", 250);
            command(session.writer, session.reader, "RCPT TO:<" + recipient + ">", 250);
            command(session.writer, session.reader, "DATA", 354);

            session.writer.write("From: <" + sender + ">\r\n");
            session.writer.write("To: <" + recipient + ">\r\n");
            session.writer.write("Date: " + rfc2822Date() + "\r\n");
            session.writer.write("Subject: =?UTF-8?B?" + base64(subject) + "?=\r\n");
            session.writer.write("MIME-Version: 1.0\r\n");
            session.writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
            session.writer.write("Content-Transfer-Encoding: base64\r\n");
            session.writer.write("\r\n");
            session.writer.write(wrapBase64(body));
            session.writer.write("\r\n.\r\n");
            session.writer.flush();
            expect(session.reader, 250);
            command(session.writer, session.reader, "QUIT", 221);
        } finally {
            socket.close();
        }
    }

    private static final class Session {
        final BufferedReader reader;
        final BufferedWriter writer;

        Session(Socket socket) throws Exception {
            reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)
            );
            writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII)
            );
        }
    }

    private static void command(
            BufferedWriter writer,
            BufferedReader reader,
            String value,
            int expectedCode
    ) throws Exception {
        writer.write(value);
        writer.write("\r\n");
        writer.flush();
        expect(reader, expectedCode);
    }

    private static void expect(BufferedReader reader, int expectedCode) throws Exception {
        String line = reader.readLine();
        if (line == null || line.length() < 3) {
            throw new IllegalStateException("SMTP connection closed");
        }
        String expected = Integer.toString(expectedCode);
        if (!line.startsWith(expected)) {
            throw new IllegalStateException("SMTP error: " + line);
        }
        while (line.length() > 3 && line.charAt(3) == '-') {
            line = reader.readLine();
            if (line == null) {
                throw new IllegalStateException("SMTP connection closed");
            }
        }
    }

    private static String base64(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String wrapBase64(String value) {
        String encoded = base64(value);
        StringBuilder wrapped = new StringBuilder(encoded.length() + encoded.length() / 76 * 2);
        for (int start = 0; start < encoded.length(); start += 76) {
            int end = Math.min(start + 76, encoded.length());
            wrapped.append(encoded, start, end).append("\r\n");
        }
        return wrapped.toString();
    }

    private static String rfc2822Date() {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date());
    }
}
