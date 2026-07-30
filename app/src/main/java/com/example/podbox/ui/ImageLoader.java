package com.example.podbox.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.widget.ImageView;

import com.example.podbox.R;
import com.example.podbox.net.Http;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private ImageLoader() {
    }

    public static void load(final ImageView view, final String url, final int sizePx) {
        view.setImageResource(R.drawable.ic_cover_default);
        view.setTag(url);
        if (TextUtils.isEmpty(url)) {
            return;
        }
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        WORKER.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bitmap = decode(download(url), sizePx);
                    if (bitmap == null) {
                        return;
                    }
                    CACHE.put(url, bitmap);
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            if (url.equals(view.getTag())) {
                                view.setImageBitmap(bitmap);
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static byte[] download(String url) throws Exception {
        InputStream input = Http.open(url);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    break;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static Bitmap decode(byte[] data, int sizePx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds, sizePx);
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static int sampleSize(BitmapFactory.Options bounds, int sizePx) {
        int sample = 1;
        while (bounds.outWidth / sample > sizePx * 2 || bounds.outHeight / sample > sizePx * 2) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }
}
