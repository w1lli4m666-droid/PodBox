package com.example.podbox.playback;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.example.podbox.MainActivity;
import com.example.podbox.R;
import com.example.podbox.data.AppDatabase;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;

public final class PlaybackService extends Service implements Player.Listener {
    public static final String ACTION_PLAY = "com.example.podbox.PLAY";
    public static final String ACTION_PLAY_LIST = "com.example.podbox.PLAY_LIST";
    public static final String ACTION_PLAY_NEXT = "com.example.podbox.PLAY_NEXT";
    public static final String ACTION_TOGGLE = "com.example.podbox.TOGGLE";
    public static final String ACTION_SEEK = "com.example.podbox.SEEK";
    public static final String ACTION_SPEED = "com.example.podbox.SPEED";
    public static final String ACTION_QUEUE_MOVE = "com.example.podbox.QUEUE_MOVE";
    public static final String ACTION_QUEUE_REMOVE = "com.example.podbox.QUEUE_REMOVE";
    public static final String ACTION_QUEUE_CLEAR = "com.example.podbox.QUEUE_CLEAR";
    public static final String ACTION_PLAY_INDEX = "com.example.podbox.PLAY_INDEX";
    public static final String ACTION_REPEAT_MODE = "com.example.podbox.REPEAT_MODE";
    public static final String ACTION_STATE = "com.example.podbox.STATE";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_URLS = "urls";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TITLES = "titles";
    public static final String EXTRA_GUID = "guid";
    public static final String EXTRA_GUIDS = "guids";
    public static final String EXTRA_ARTWORK = "artwork";
    public static final String EXTRA_ARTWORKS = "artworks";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_DELTA = "delta";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_SPEED = "speed";
    public static final String EXTRA_SPEED_SUPPORTED = "speed_supported";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_QUEUE_TITLES = "queue_titles";
    public static final String EXTRA_QUEUE_GUIDS = "queue_guids";
    public static final String EXTRA_QUEUE_ARTWORKS = "queue_artworks";
    public static final String EXTRA_QUEUE_INDEX = "queue_index";
    public static final String EXTRA_REPEAT_MODE = "repeat_mode";

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "podcast_playback";

    private ExoPlayer player;
    private AppDatabase database;
    private String currentGuid = "";
    private String currentTitle = "";
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.getMediaItemCount() > 0) {
                broadcastProgress();
                progressHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        database = new AppDatabase(this);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(C.USAGE_MEDIA)
                .build();
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(attributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            playNow(intent);
        } else if (ACTION_PLAY_LIST.equals(action)) {
            playList(intent);
        } else if (ACTION_PLAY_NEXT.equals(action)) {
            playNext(intent);
        } else if (ACTION_TOGGLE.equals(action)) {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
        } else if (ACTION_SEEK.equals(action)) {
            long target = Math.max(0, player.getCurrentPosition()
                    + intent.getIntExtra(EXTRA_DELTA, 0));
            if (player.getDuration() > 0) {
                target = Math.min(target, player.getDuration());
            }
            player.seekTo(target);
        } else if (ACTION_SPEED.equals(action)) {
            adjustSpeed(intent.getFloatExtra(EXTRA_SPEED, 0));
        } else if (ACTION_QUEUE_MOVE.equals(action)) {
            moveQueueItem(intent.getIntExtra(EXTRA_INDEX, -1), intent.getIntExtra(EXTRA_DELTA, 0));
        } else if (ACTION_QUEUE_REMOVE.equals(action)) {
            removeQueueItem(intent.getIntExtra(EXTRA_INDEX, -1));
        } else if (ACTION_QUEUE_CLEAR.equals(action)) {
            clearQueue();
        } else if (ACTION_PLAY_INDEX.equals(action)) {
            playIndex(intent.getIntExtra(EXTRA_INDEX, -1));
        } else if (ACTION_REPEAT_MODE.equals(action)) {
            cycleRepeatMode();
        }
        broadcast();
        return START_NOT_STICKY;
    }

    private void playNow(Intent intent) {
        persistCurrent(false);
        MediaItem item = mediaItem(intent);
        player.setMediaItem(item, Math.max(0, intent.getIntExtra(EXTRA_POSITION, 0)));
        player.prepare();
        player.play();
        updateCurrent(item);
        startForeground(NOTIFICATION_ID, buildNotification());
        startProgressTicker();
    }

    private void playList(Intent intent) {
        persistCurrent(false);
        String[] urls = intent.getStringArrayExtra(EXTRA_URLS);
        String[] titles = intent.getStringArrayExtra(EXTRA_TITLES);
        String[] guids = intent.getStringArrayExtra(EXTRA_GUIDS);
        String[] artworks = intent.getStringArrayExtra(EXTRA_ARTWORKS);
        if (urls == null || titles == null || guids == null || urls.length == 0) {
            return;
        }
        player.clearMediaItems();
        int count = Math.min(urls.length, Math.min(titles.length, guids.length));
        for (int i = 0; i < count; i++) {
            player.addMediaItem(mediaItem(urls[i], titles[i], guids[i], artworks == null || i >= artworks.length ? "" : artworks[i]));
        }
        int index = Math.max(0, Math.min(intent.getIntExtra(EXTRA_INDEX, 0), count - 1));
        player.seekToDefaultPosition(index);
        player.prepare();
        player.play();
        updateCurrent(player.getCurrentMediaItem());
        startForeground(NOTIFICATION_ID, buildNotification());
        startProgressTicker();
    }

    private void playNext(Intent intent) {
        MediaItem item = mediaItem(intent);
        if (player.getMediaItemCount() == 0) {
            player.setMediaItem(item);
            player.prepare();
            player.play();
            updateCurrent(item);
            startForeground(NOTIFICATION_ID, buildNotification());
            startProgressTicker();
            return;
        }
        int insertAt = Math.min(player.getCurrentMediaItemIndex() + 1, player.getMediaItemCount());
        player.addMediaItem(insertAt, item);
    }

    private MediaItem mediaItem(Intent intent) {
        return mediaItem(
                intent.getStringExtra(EXTRA_URL),
                intent.getStringExtra(EXTRA_TITLE),
                intent.getStringExtra(EXTRA_GUID),
                intent.getStringExtra(EXTRA_ARTWORK));
    }

    private MediaItem mediaItem(String url, String titleValue, String guid, String artwork) {
        String title = value(titleValue);
        MediaMetadata.Builder metadata = new MediaMetadata.Builder().setTitle(title);
        if (value(artwork).length() > 0) {
            metadata.setArtworkUri(Uri.parse(artwork));
        }
        return new MediaItem.Builder()
                .setUri(value(url))
                .setMediaId(value(guid))
                .setMediaMetadata(metadata.build())
                .build();
    }

    private void adjustSpeed(float delta) {
        float speed = player.getPlaybackParameters().speed;
        float requested = Math.max(0.5f,
                Math.min(2.0f, Math.round((speed + delta) * 10f) / 10f));
        player.setPlaybackParameters(new PlaybackParameters(requested, 1.0f));
    }

    private void moveQueueItem(int index, int delta) {
        int target = index + delta;
        if (index < 0 || target < 0 || index >= player.getMediaItemCount()
                || target >= player.getMediaItemCount()) {
            return;
        }
        player.moveMediaItem(index, target);
        updateCurrent(player.getCurrentMediaItem());
    }

    private void removeQueueItem(int index) {
        if (index < 0 || index >= player.getMediaItemCount()) {
            return;
        }
        player.removeMediaItem(index);
        updateCurrent(player.getCurrentMediaItem());
    }

    private void clearQueue() {
        persistCurrent(false);
        player.clearMediaItems();
        currentGuid = "";
        currentTitle = "";
        progressHandler.removeCallbacks(progressTicker);
        stopForeground(false);
    }

    private void playIndex(int index) {
        if (index < 0 || index >= player.getMediaItemCount()) {
            return;
        }
        player.seekToDefaultPosition(index);
        player.play();
        updateCurrent(player.getCurrentMediaItem());
    }

    private void cycleRepeatMode() {
        int mode = player.getRepeatMode();
        if (mode == Player.REPEAT_MODE_OFF) {
            player.setRepeatMode(Player.REPEAT_MODE_ALL);
        } else if (mode == Player.REPEAT_MODE_ALL) {
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
        } else {
            player.setRepeatMode(Player.REPEAT_MODE_OFF);
        }
    }

    @Override
    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            persistCurrent(true);
        }
        updateCurrent(mediaItem);
        broadcast();
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        broadcast();
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_ENDED) {
            persistCurrent(true);
        }
        broadcast();
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        broadcast();
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        persistCurrent(false);
        broadcast();
    }

    private void updateCurrent(MediaItem item) {
        if (item == null) {
            return;
        }
        currentGuid = item.mediaId;
        CharSequence title = item.mediaMetadata.title;
        currentTitle = title == null ? "" : title.toString();
    }

    private void persistCurrent(boolean played) {
        if (currentGuid.length() > 0 && player.getCurrentPosition() >= 0) {
            database.savePlayback(currentGuid, player.getCurrentPosition(), played);
        }
    }

    private void broadcast() {
        sendStateBroadcast();
        if (player.getMediaItemCount() > 0) {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
    }

    private void broadcastProgress() {
        sendStateBroadcast();
    }

    private void sendStateBroadcast() {
        Intent state = new Intent(ACTION_STATE);
        state.setPackage(getPackageName());
        state.putExtra(EXTRA_TITLE, currentTitle);
        state.putExtra(EXTRA_GUID, currentGuid);
        state.putExtra(EXTRA_PLAYING, player.isPlaying());
        state.putExtra(EXTRA_SPEED, player.getPlaybackParameters().speed);
        state.putExtra(EXTRA_SPEED_SUPPORTED, true);
        long duration = player.getDuration();
        state.putExtra(EXTRA_POSITION, Math.max(0, player.getCurrentPosition()));
        state.putExtra(EXTRA_DURATION,
                duration == C.TIME_UNSET || duration < 0 ? 0 : duration);
        state.putExtra(EXTRA_ARTWORK, currentArtwork());
        state.putExtra(EXTRA_QUEUE_TITLES, queueTitles());
        state.putExtra(EXTRA_QUEUE_GUIDS, queueGuids());
        state.putExtra(EXTRA_QUEUE_ARTWORKS, queueArtworks());
        state.putExtra(EXTRA_QUEUE_INDEX, player.getCurrentMediaItemIndex());
        state.putExtra(EXTRA_REPEAT_MODE, player.getRepeatMode());
        sendBroadcast(state);
    }

    private String currentArtwork() {
        MediaItem item = player.getCurrentMediaItem();
        if (item == null || item.mediaMetadata.artworkUri == null) {
            return "";
        }
        return item.mediaMetadata.artworkUri.toString();
    }

    private String[] queueTitles() {
        String[] titles = new String[player.getMediaItemCount()];
        for (int i = 0; i < titles.length; i++) {
            CharSequence title = player.getMediaItemAt(i).mediaMetadata.title;
            titles[i] = title == null ? "" : title.toString();
        }
        return titles;
    }

    private String[] queueGuids() {
        String[] guids = new String[player.getMediaItemCount()];
        for (int i = 0; i < guids.length; i++) {
            guids[i] = player.getMediaItemAt(i).mediaId;
        }
        return guids;
    }

    private String[] queueArtworks() {
        String[] artworks = new String[player.getMediaItemCount()];
        for (int i = 0; i < artworks.length; i++) {
            Uri artwork = player.getMediaItemAt(i).mediaMetadata.artworkUri;
            artworks[i] = artwork == null ? "" : artwork.toString();
        }
        return artworks;
    }

    private void startProgressTicker() {
        progressHandler.removeCallbacks(progressTicker);
        progressHandler.post(progressTicker);
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "播客播放", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT);
        return builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(currentTitle.length() == 0 ? "播客盒子" : currentTitle)
                .setContentText(player.isPlaying() ? "正在播放" : "已暂停")
                .setContentIntent(pending)
                .setOngoing(player.isPlaying())
                .build();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onDestroy() {
        progressHandler.removeCallbacksAndMessages(null);
        persistCurrent(false);
        player.release();
        database.close();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
