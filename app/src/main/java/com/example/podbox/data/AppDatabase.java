package com.example.podbox.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.podbox.model.Episode;
import com.example.podbox.model.Podcast;

import java.util.ArrayList;
import java.util.List;

public final class AppDatabase extends SQLiteOpenHelper {
    private static final String NAME = "podbox.db";
    private static final int VERSION = 2;

    public AppDatabase(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE subscriptions (" +
                "collection_id INTEGER PRIMARY KEY, title TEXT NOT NULL, author TEXT, " +
                "feed_url TEXT NOT NULL, artwork_url TEXT, etag TEXT, last_modified TEXT, refreshed_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE episodes (" +
                "guid TEXT PRIMARY KEY, podcast_id INTEGER NOT NULL, podcast_title TEXT, title TEXT NOT NULL, " +
                "audio_url TEXT NOT NULL, artwork_url TEXT, published_at INTEGER DEFAULT 0, duration_ms INTEGER DEFAULT 0, " +
                "position_ms INTEGER DEFAULT 0, played INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_episode_date ON episodes(published_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE episodes ADD COLUMN artwork_url TEXT");
        }
    }

    public synchronized void subscribe(Podcast podcast) {
        ContentValues values = new ContentValues();
        values.put("collection_id", podcast.collectionId);
        values.put("title", podcast.title);
        values.put("author", podcast.author);
        values.put("feed_url", podcast.feedUrl);
        values.put("artwork_url", podcast.artworkUrl);
        getWritableDatabase().insertWithOnConflict("subscriptions", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void unsubscribe(long collectionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("episodes", "podcast_id=?", new String[]{String.valueOf(collectionId)});
            db.delete("subscriptions", "collection_id=?", new String[]{String.valueOf(collectionId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized boolean isSubscribed(long collectionId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM subscriptions WHERE collection_id=? LIMIT 1",
                new String[]{String.valueOf(collectionId)});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public synchronized List<Podcast> subscriptions() {
        ArrayList<Podcast> result = new ArrayList<Podcast>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT collection_id,title,author,feed_url,artwork_url FROM subscriptions ORDER BY title COLLATE NOCASE", null);
        try {
            while (cursor.moveToNext()) {
                Podcast podcast = new Podcast();
                podcast.collectionId = cursor.getLong(0);
                podcast.title = cursor.getString(1);
                podcast.author = cursor.getString(2);
                podcast.feedUrl = cursor.getString(3);
                podcast.artworkUrl = cursor.getString(4);
                result.add(podcast);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public synchronized void saveEpisodes(List<Episode> episodes) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Episode episode : episodes) {
                ContentValues values = new ContentValues();
                values.put("guid", episode.guid);
                values.put("podcast_id", episode.podcastId);
                values.put("podcast_title", episode.podcastTitle);
                values.put("title", episode.title);
                values.put("audio_url", episode.audioUrl);
                values.put("artwork_url", episode.artworkUrl);
                values.put("published_at", episode.publishedAt);
                values.put("duration_ms", episode.durationMs);
                db.insertWithOnConflict("episodes", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized List<Episode> recentEpisodes(int limit) {
        ArrayList<Episode> result = new ArrayList<Episode>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT e.guid,e.podcast_id,e.podcast_title,e.title,e.audio_url," +
                        "COALESCE(e.artwork_url,s.artwork_url,''),e.published_at,e.duration_ms,e.position_ms,e.played " +
                        "FROM episodes e LEFT JOIN subscriptions s ON s.collection_id=e.podcast_id " +
                        "ORDER BY e.published_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
        try {
            while (cursor.moveToNext()) {
                Episode episode = new Episode();
                episode.guid = cursor.getString(0);
                episode.podcastId = cursor.getLong(1);
                episode.podcastTitle = cursor.getString(2);
                episode.title = cursor.getString(3);
                episode.audioUrl = cursor.getString(4);
                episode.artworkUrl = cursor.getString(5);
                episode.publishedAt = cursor.getLong(6);
                episode.durationMs = cursor.getLong(7);
                episode.playbackPositionMs = cursor.getLong(8);
                episode.played = cursor.getInt(9) != 0;
                result.add(episode);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public synchronized void savePlayback(String guid, long positionMs, boolean played) {
        ContentValues values = new ContentValues();
        values.put("position_ms", positionMs);
        values.put("played", played ? 1 : 0);
        getWritableDatabase().update("episodes", values, "guid=?", new String[]{guid});
    }
}
