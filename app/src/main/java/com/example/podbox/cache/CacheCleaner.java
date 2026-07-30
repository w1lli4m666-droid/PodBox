package com.example.podbox.cache;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class CacheCleaner {
    private static final long MAX_AGE_MS = 24L * 60L * 60L * 1000L;
    private static final long MAX_BYTES = 48L * 1024L * 1024L;

    private CacheCleaner() {
    }

    public static long clean(Context context) {
        File root = context.getCacheDir();
        File[] files = root.listFiles();
        if (files == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long removed = 0;
        ArrayList<File> remaining = new ArrayList<File>();
        for (File file : files) {
            if (file.isFile() && now - file.lastModified() > MAX_AGE_MS) {
                long size = file.length();
                if (file.delete()) {
                    removed += size;
                }
            } else if (file.isFile()) {
                remaining.add(file);
            }
        }
        long total = sizeOf(remaining);
        if (total > MAX_BYTES) {
            Collections.sort(remaining, new Comparator<File>() {
                @Override
                public int compare(File left, File right) {
                    return left.lastModified() < right.lastModified() ? -1 :
                            (left.lastModified() == right.lastModified() ? 0 : 1);
                }
            });
            for (File file : remaining) {
                if (total <= MAX_BYTES) {
                    break;
                }
                long size = file.length();
                if (file.delete()) {
                    total -= size;
                    removed += size;
                }
            }
        }
        return removed;
    }

    private static long sizeOf(List<File> files) {
        long size = 0;
        for (File file : files) {
            size += file.length();
        }
        return size;
    }
}
