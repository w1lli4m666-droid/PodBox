package com.example.podbox.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PinyinCandidates {
    private final Map<String, String[]> words = new LinkedHashMap<String, String[]>();

    public PinyinCandidates() {
        words.put("gushi", new String[]{"故事", "股市", "古诗"});
        words.put("lishi", new String[]{"历史", "李氏"});
        words.put("keji", new String[]{"科技"});
        words.put("xinwen", new String[]{"新闻"});
        words.put("yinyue", new String[]{"音乐"});
        words.put("ertong", new String[]{"儿童"});
        words.put("xinli", new String[]{"心理"});
        words.put("wenhua", new String[]{"文化"});
        words.put("dianying", new String[]{"电影"});
        words.put("shangye", new String[]{"商业"});
        words.put("caijing", new String[]{"财经"});
        words.put("yingyu", new String[]{"英语"});
        words.put("luoji", new String[]{"逻辑"});
        words.put("siwei", new String[]{"思维"});
        words.put("luojisiwei", new String[]{"逻辑思维"});
    }

    public List<String> find(String input) {
        String normalized = input.toLowerCase(Locale.US).replace(" ", "");
        ArrayList<String> result = new ArrayList<String>();
        String[] exact = words.get(normalized);
        if (exact != null) {
            for (String word : exact) {
                result.add(word);
            }
        }
        if (result.size() < 5) {
            for (Map.Entry<String, String[]> entry : words.entrySet()) {
                if (entry.getKey().startsWith(normalized) && !entry.getKey().equals(normalized)) {
                    for (String word : entry.getValue()) {
                        if (!result.contains(word)) {
                            result.add(word);
                            if (result.size() == 5) {
                                return result;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
}
