package com.example.podbox.cache;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class CacheCleanupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    CacheCleaner.clean(context);
                } finally {
                    pending.finish();
                }
            }
        }, "cache-cleanup").start();
    }
}
