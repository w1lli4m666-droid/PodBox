package com.example.podbox.net;

import com.example.podbox.model.Podcast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public final class AppleSearchClient {
    public List<Podcast> search(String term) throws Exception {
        String url = "https://itunes.apple.com/search?term=" +
                URLEncoder.encode(term, "UTF-8") +
                "&media=podcast&entity=podcast&country=CN&limit=30";
        JSONObject root = new JSONObject(Http.getText(url));
        JSONArray items = root.getJSONArray("results");
        ArrayList<Podcast> result = new ArrayList<Podcast>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String feedUrl = item.optString("feedUrl", "");
            if (feedUrl.length() == 0) {
                continue;
            }
            Podcast podcast = new Podcast();
            podcast.collectionId = item.optLong("collectionId");
            podcast.title = item.optString("collectionName", "");
            podcast.author = item.optString("artistName", "");
            podcast.feedUrl = feedUrl;
            podcast.artworkUrl = item.optString("artworkUrl100", "");
            result.add(podcast);
        }
        return result;
    }
}
