package com.example.podbox.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class Http {
    private static final int MAX_BYTES = 3 * 1024 * 1024;

    private Http() {
    }

    public static InputStream open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(18000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "PodBox/0.1 Android");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("HTTP " + status);
        }
        return new BufferedInputStream(connection.getInputStream()) {
            @Override
            public void close() throws IOException {
                super.close();
                connection.disconnect();
            }
        };
    }

    public static String getText(String url) throws IOException {
        InputStream input = open(url);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) {
                    throw new IOException("Response too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        } finally {
            input.close();
        }
    }
}
