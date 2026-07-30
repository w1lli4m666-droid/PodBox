package com.example.podbox.net;

import android.util.Xml;

import com.example.podbox.model.Episode;
import com.example.podbox.model.Podcast;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RssParser {
    private static final int MAX_EPISODES = 50;

    public List<Episode> fetch(Podcast podcast) throws Exception {
        InputStream input = Http.open(podcast.feedUrl);
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, null);
            return parse(parser, podcast);
        } finally {
            input.close();
        }
    }

    private List<Episode> parse(XmlPullParser parser, Podcast podcast) throws Exception {
        ArrayList<Episode> result = new ArrayList<Episode>();
        Episode current = null;
        String text = "";
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT && result.size() < MAX_EPISODES) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) {
                    current = new Episode();
                    current.podcastId = podcast.collectionId;
                    current.podcastTitle = podcast.title;
                    current.artworkUrl = podcast.artworkUrl;
                } else if (current != null && "enclosure".equalsIgnoreCase(name)) {
                    String url = parser.getAttributeValue(null, "url");
                    if (url != null) {
                        current.audioUrl = url;
                    }
                } else if (current != null && "link".equalsIgnoreCase(name)) {
                    String rel = parser.getAttributeValue(null, "rel");
                    String href = parser.getAttributeValue(null, "href");
                    String type = parser.getAttributeValue(null, "type");
                    if ("enclosure".equals(rel) || (type != null && type.startsWith("audio/"))) {
                        current.audioUrl = href == null ? "" : href;
                    }
                }
                text = "";
            } else if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
                text += parser.getText();
            } else if (event == XmlPullParser.END_TAG && current != null) {
                String name = parser.getName();
                String value = text.trim();
                if ("title".equalsIgnoreCase(name) && current.title.length() == 0) {
                    current.title = value;
                } else if ("guid".equalsIgnoreCase(name) || "id".equalsIgnoreCase(name)) {
                    current.guid = value;
                } else if ("pubDate".equalsIgnoreCase(name) || "published".equalsIgnoreCase(name)
                        || "updated".equalsIgnoreCase(name)) {
                    current.publishedAt = parseDate(value);
                } else if ("duration".equalsIgnoreCase(name)) {
                    current.durationMs = parseDuration(value);
                } else if ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) {
                    if (current.audioUrl.length() > 0) {
                        if (current.guid.length() == 0) {
                            current.guid = current.audioUrl;
                        }
                        if (current.title.length() == 0) {
                            current.title = "未命名节目";
                        }
                        result.add(current);
                    }
                    current = null;
                }
                text = "";
            }
            event = parser.next();
        }
        return result;
    }

    private long parseDate(String value) {
        String[] patterns = {
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, d MMM yyyy HH:mm:ss Z",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String pattern : patterns) {
            try {
                Date date = new SimpleDateFormat(pattern, Locale.US).parse(value);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }

    private long parseDuration(String value) {
        try {
            String[] parts = value.split(":");
            long seconds = 0;
            for (String part : parts) {
                seconds = seconds * 60 + Long.parseLong(part);
            }
            return seconds * 1000;
        } catch (Exception ignored) {
            return 0;
        }
    }
}
