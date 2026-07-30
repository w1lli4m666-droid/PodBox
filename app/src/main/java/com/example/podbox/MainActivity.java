package com.example.podbox;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.podbox.cache.CacheCleaner;
import com.example.podbox.cache.CacheCleanupReceiver;
import com.example.podbox.data.AppDatabase;
import com.example.podbox.model.Episode;
import com.example.podbox.model.Podcast;
import com.example.podbox.net.AppleSearchClient;
import com.example.podbox.net.RssParser;
import com.example.podbox.playback.PlaybackService;
import com.example.podbox.search.PinyinCandidates;
import com.example.podbox.ui.ImageLoader;

import java.util.ArrayList;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int PAGE_NONE = 0;
    private static final int PAGE_RECENT = 1;
    private static final int PAGE_SUBSCRIPTIONS = 2;
    private static final int PAGE_SEARCH = 3;
    private static final int COLOR_TEXT = Color.WHITE;
    private static final int COLOR_MUTED = 0xffb8c0cc;
    private static final AtomicInteger NEXT_VIEW_ID = new AtomicInteger(1);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AppleSearchClient searchClient = new AppleSearchClient();
    private final RssParser rssParser = new RssParser();
    private final PinyinCandidates pinyinCandidates = new PinyinCandidates();

    private AppDatabase database;
    private FrameLayout content;
    private View miniPlayer;
    private ImageView playerArtwork;
    private TextView playerTitle;
    private TextView playerTime;
    private ImageButton playerToggle;
    private Button playerSpeed;
    private Button playerSpeedUp;
    private Button playerSpeedDown;
    private ImageButton playerBack;
    private ImageButton playerForward;
    private ImageButton playerQueue;
    private Button tabRecent;
    private Button tabSubscriptions;
    private Button tabSearch;
    private View firstEpisodeButton;
    private View firstContentButton;
    private View firstQueueButton;
    private View previousEpisodeButton;
    private View previousQueueButton;
    private float pullStartY;
    private boolean loading;
    private boolean speedWarningShown;
    private boolean subscriptionsAutoRefreshed;
    private boolean subscriptionDetailVisible;
    private int currentPage = PAGE_NONE;
    private PopupWindow queuePopup;
    private String[] queueTitles = new String[0];
    private String[] queueGuids = new String[0];
    private String[] queueArtworks = new String[0];
    private int queueIndex = -1;
    private int repeatMode;
    private String currentPlaybackGuid = "";
    private boolean currentPlaybackPlaying;
    private final List<EpisodePlayControl> episodePlayControls = new ArrayList<EpisodePlayControl>();

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String title = intent.getStringExtra(PlaybackService.EXTRA_TITLE);
            boolean playing = intent.getBooleanExtra(PlaybackService.EXTRA_PLAYING, false);
            currentPlaybackGuid = value(intent.getStringExtra(PlaybackService.EXTRA_GUID));
            currentPlaybackPlaying = playing;
            miniPlayer.setVisibility(View.VISIBLE);
            playerTitle.setText(title);
            playerTime.setText(formatDuration(
                    intent.getLongExtra(PlaybackService.EXTRA_POSITION, 0))
                    + " / "
                    + formatDuration(intent.getLongExtra(PlaybackService.EXTRA_DURATION, 0)));
            playerToggle.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            playerToggle.setContentDescription(playing ? "暂停" : "播放");
            ImageLoader.load(playerArtwork, intent.getStringExtra(PlaybackService.EXTRA_ARTWORK), dp(42));
            float speed = intent.getFloatExtra(PlaybackService.EXTRA_SPEED, 1.0f);
            playerSpeed.setText(formatSpeed(speed));
            queueTitles = nonNull(intent.getStringArrayExtra(PlaybackService.EXTRA_QUEUE_TITLES));
            queueGuids = nonNull(intent.getStringArrayExtra(PlaybackService.EXTRA_QUEUE_GUIDS));
            queueArtworks = nonNull(intent.getStringArrayExtra(PlaybackService.EXTRA_QUEUE_ARTWORKS));
            queueIndex = intent.getIntExtra(PlaybackService.EXTRA_QUEUE_INDEX, -1);
            repeatMode = intent.getIntExtra(PlaybackService.EXTRA_REPEAT_MODE, 0);
            boolean supported = intent.getBooleanExtra(
                    PlaybackService.EXTRA_SPEED_SUPPORTED, Build.VERSION.SDK_INT >= 23);
            playerSpeedUp.setEnabled(supported);
            playerSpeedDown.setEnabled(supported);
            if (!supported && !speedWarningShown) {
                speedWarningShown = true;
                Toast.makeText(MainActivity.this,
                        "此设备的系统播放器不支持可靠倍速", Toast.LENGTH_SHORT).show();
            }
            updateEpisodePlayButtons();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        database = new AppDatabase(this);
        content = (FrameLayout) findViewById(R.id.content);
        miniPlayer = findViewById(R.id.mini_player);
        playerArtwork = (ImageView) findViewById(R.id.player_artwork);
        playerTitle = (TextView) findViewById(R.id.player_title);
        playerTime = (TextView) findViewById(R.id.player_time);
        playerToggle = (ImageButton) findViewById(R.id.player_toggle);
        playerSpeed = (Button) findViewById(R.id.player_speed);
        playerSpeedUp = (Button) findViewById(R.id.player_speed_up);
        playerSpeedDown = (Button) findViewById(R.id.player_speed_down);
        playerQueue = (ImageButton) findViewById(R.id.player_queue);
        playerBack = (ImageButton) findViewById(R.id.player_back);
        playerForward = (ImageButton) findViewById(R.id.player_forward);

        tabSubscriptions = (Button) findViewById(R.id.tab_subscriptions);
        tabRecent = (Button) findViewById(R.id.tab_recent);
        tabSearch = (Button) findViewById(R.id.tab_search);

        tabSubscriptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSubscriptions();
            }
        });
        tabRecent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openRecent();
            }
        });
        tabSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSearch();
            }
        });
        findViewById(R.id.action_clear_cache).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cleanCache();
            }
        });
        playerToggle.setOnClickListener(playbackAction(PlaybackService.ACTION_TOGGLE, 0));
        playerQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleQueuePopup();
            }
        });
        installSeekButton(playerBack, -1);
        installSeekButton(playerForward, 1);
        playerSpeed.setFocusable(false);
        playerSpeed.setClickable(false);
        playerSpeedUp.setOnClickListener(speedAction(0.1f));
        playerSpeedDown.setOnClickListener(speedAction(-0.1f));
        configurePlayerFocus();
        finishSetup();
    }

    private View.OnClickListener speedAction(final float delta) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, PlaybackService.class);
                intent.setAction(PlaybackService.ACTION_SPEED);
                intent.putExtra(PlaybackService.EXTRA_SPEED, delta);
                startService(intent);
            }
        };
    }

    private void finishSetup() {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(playbackReceiver, new IntentFilter(PlaybackService.ACTION_STATE),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(playbackReceiver, new IntentFilter(PlaybackService.ACTION_STATE));
        }
        scheduleCleanup();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                CacheCleaner.clean(MainActivity.this);
            }
        });
        refreshSubscriptions(PAGE_RECENT, false);
    }

    private View.OnClickListener playbackAction(final String action, final int delta) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, PlaybackService.class);
                intent.setAction(action);
                intent.putExtra(PlaybackService.EXTRA_DELTA, delta);
                startService(intent);
            }
        };
    }

    private void showSubscriptions() {
        currentPage = PAGE_SUBSCRIPTIONS;
        subscriptionDetailVisible = false;
        LinearLayout list = verticalList();
        List<Podcast> subscriptions = database.subscriptions();
        if (subscriptions.isEmpty()) {
            list.addView(message(getString(R.string.empty_subscriptions)));
        }
        for (final Podcast podcast : subscriptions) {
            LinearLayout row = row();
            row.addView(cover(podcast.artworkUrl, 56));
            row.addView(text(podcast.title + "\n" + podcast.author, 1));
            Button open = button("节目");
            open.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showPodcastEpisodes(podcast);
                }
            });
            row.addView(open);
            Button remove = button("取消订阅");
            remove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    database.unsubscribe(podcast.collectionId);
                    showSubscriptions();
                }
            });
            row.addView(remove);
            list.addView(row);
        }
        setScrollableContent(list, true);
    }

    private void showRecent() {
        currentPage = PAGE_RECENT;
        subscriptionDetailVisible = false;
        resetEpisodeFocusChain();
        LinearLayout list = verticalList();
        final List<Episode> episodes = database.recentEpisodes(100);
        if (episodes.isEmpty()) {
            list.addView(message(getString(R.string.empty_recent)));
        } else {
            ImageButton playAll = iconButton(R.drawable.ic_play, "全部播放");
            playAll.setId(generateViewIdCompat());
            playAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    playAll(episodes, 0);
                }
            });
            list.addView(playAll);
            firstContentButton = playAll;
            addEpisodes(list, episodes);
        }
        setScrollableContent(list, true);
        connectHeaderToFirstEpisode();
    }

    private void showPodcastEpisodes(final Podcast podcast) {
        currentPage = PAGE_SUBSCRIPTIONS;
        subscriptionDetailVisible = true;
        resetEpisodeFocusChain();
        LinearLayout list = verticalList();
        LinearLayout headingRow = row();
        headingRow.addView(cover(podcast.artworkUrl, 56));
        TextView heading = text(podcast.title, 1);
        heading.setTextSize(24);
        headingRow.addView(heading);
        final List<Episode> episodes = new ArrayList<Episode>();
        List<Episode> all = database.recentEpisodes(500);
        for (Episode episode : all) {
            if (episode.podcastId == podcast.collectionId) {
                episodes.add(episode);
            }
        }
        ImageButton playAll = iconButton(R.drawable.ic_play, "全部播放");
        playAll.setId(generateViewIdCompat());
        playAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playAll(episodes, 0);
            }
        });
        headingRow.addView(playAll);
        list.addView(headingRow);
        firstContentButton = playAll;
        addEpisodes(list, episodes);
        setScrollableContent(list, true);
        connectHeaderToFirstEpisode();
    }

    private void addEpisodes(LinearLayout list, List<Episode> episodes) {
        for (Episode episode : episodes) {
            addEpisodeRow(list, episode);
        }
    }

    private void addEpisodeRow(LinearLayout list, final Episode episode) {
        LinearLayout row = row();
        String date = episode.publishedAt == 0 ? "" :
                DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(episode.publishedAt));
        row.addView(cover(episode.artworkUrl, 56));
        TextView label = text(episode.title + "\n" + episode.podcastTitle + "  " + date, 1);
        if (episode.played) {
            label.setTextColor(COLOR_MUTED);
        }
        row.addView(label);

        ImageButton playNext = iconButton(R.drawable.ic_play_next, "下一个播放");
        playNext.setId(generateViewIdCompat());
        playNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = episodeIntent(episode, PlaybackService.ACTION_PLAY_NEXT);
                startService(intent);
                Toast.makeText(MainActivity.this,
                        "已加入下一个播放：" + episode.title, Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(playNext);

        final ImageButton play = iconButton(playIconFor(episode.guid), "播放");
        play.setId(generateViewIdCompat());
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isCurrentEpisode(episode.guid)) {
                    startService(playbackIntent(PlaybackService.ACTION_TOGGLE));
                } else {
                    startService(episodeIntent(episode, PlaybackService.ACTION_PLAY));
                }
            }
        });
        row.addView(play);
        episodePlayControls.add(new EpisodePlayControl(episode.guid, play));
        linkEpisodeFocusRow(playNext, play);
        list.addView(row);
    }

    private Intent episodeIntent(Episode episode, String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        intent.putExtra(PlaybackService.EXTRA_URL, episode.audioUrl);
        intent.putExtra(PlaybackService.EXTRA_TITLE, episode.title);
        intent.putExtra(PlaybackService.EXTRA_GUID, episode.guid);
        intent.putExtra(PlaybackService.EXTRA_ARTWORK, episode.artworkUrl);
        intent.putExtra(PlaybackService.EXTRA_POSITION, (int) episode.playbackPositionMs);
        return intent;
    }

    private void playAll(List<Episode> episodes, int startIndex) {
        if (episodes.isEmpty()) {
            return;
        }
        String[] urls = new String[episodes.size()];
        String[] titles = new String[episodes.size()];
        String[] guids = new String[episodes.size()];
        String[] artworks = new String[episodes.size()];
        for (int i = 0; i < episodes.size(); i++) {
            Episode episode = episodes.get(i);
            urls[i] = episode.audioUrl;
            titles[i] = episode.title;
            guids[i] = episode.guid;
            artworks[i] = episode.artworkUrl;
        }
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PLAY_LIST);
        intent.putExtra(PlaybackService.EXTRA_URLS, urls);
        intent.putExtra(PlaybackService.EXTRA_TITLES, titles);
        intent.putExtra(PlaybackService.EXTRA_GUIDS, guids);
        intent.putExtra(PlaybackService.EXTRA_ARTWORKS, artworks);
        intent.putExtra(PlaybackService.EXTRA_INDEX, startIndex);
        startService(intent);
    }

    private void resetEpisodeFocusChain() {
        firstEpisodeButton = null;
        firstContentButton = null;
        firstQueueButton = null;
        previousEpisodeButton = null;
        previousQueueButton = null;
        episodePlayControls.clear();
    }

    private void linkEpisodeFocusRow(View queue, View play) {
        queue.setNextFocusLeftId(queue.getId());
        queue.setNextFocusRightId(play.getId());
        play.setNextFocusLeftId(queue.getId());
        play.setNextFocusRightId(play.getId());
        queue.setNextFocusDownId(queue.getId());
        play.setNextFocusDownId(play.getId());

        if (previousQueueButton == null) {
            firstQueueButton = queue;
            firstEpisodeButton = play;
            if (firstContentButton != null) {
                firstContentButton.setNextFocusDownId(play.getId());
                queue.setNextFocusUpId(firstContentButton.getId());
                play.setNextFocusUpId(firstContentButton.getId());
            } else {
                int tabId = currentTab().getId();
                queue.setNextFocusUpId(tabId);
                play.setNextFocusUpId(tabId);
            }
        } else {
            previousQueueButton.setNextFocusDownId(queue.getId());
            previousEpisodeButton.setNextFocusDownId(play.getId());
            queue.setNextFocusUpId(previousQueueButton.getId());
            play.setNextFocusUpId(previousEpisodeButton.getId());
        }
        previousQueueButton = queue;
        previousEpisodeButton = play;
    }

    private int playIconFor(String guid) {
        return isCurrentEpisode(guid) && currentPlaybackPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
    }

    private boolean isCurrentEpisode(String guid) {
        return currentPlaybackGuid.length() > 0 && currentPlaybackGuid.equals(value(guid));
    }

    private void updateEpisodePlayButtons() {
        for (EpisodePlayControl control : episodePlayControls) {
            control.button.setImageResource(playIconFor(control.guid));
            control.button.setContentDescription(isCurrentEpisode(control.guid) && currentPlaybackPlaying ? "暂停" : "播放");
        }
    }

    private Intent playbackIntent(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        return intent;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private static final class EpisodePlayControl {
        final String guid;
        final ImageButton button;

        EpisodePlayControl(String guid, ImageButton button) {
            this.guid = guid == null ? "" : guid;
            this.button = button;
        }
    }

    private void showSearch() {
        currentPage = PAGE_SEARCH;
        final LinearLayout root = verticalList();
        final EditText input = new EditText(this);
        input.setHint(R.string.search_hint);
        input.setSingleLine(true);
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        input.setTextSize(20);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.addView(input, new LinearLayout.LayoutParams(
                0, dp(52), 1));
        Button search = button("开始搜索");
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                search(input.getText().toString().trim());
            }
        });
        inputRow.addView(search);
        root.addView(inputRow);

        final LinearLayout candidates = new LinearLayout(this);
        candidates.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(candidates);

        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        addKeyboardRow(keyboard, input, "ABCDEFG");
        addKeyboardRow(keyboard, input, "HIJKLMN");
        addKeyboardRow(keyboard, input, "OPQRSTU");
        addKeyboardLastRow(keyboard, input);
        root.addView(keyboard);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                updateCandidates(input, candidates);
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        input.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            search(input.getText().toString().trim());
            return true;
        });
        setScrollableContent(root, false);
    }

    private void addKeyboardRow(LinearLayout keyboard, final EditText input, String letters) {
        LinearLayout row = keyboardRow();
        for (int i = 0; i < letters.length(); i++) {
            final String letter = String.valueOf(letters.charAt(i)).toLowerCase(Locale.US);
            Button key = button(letter.toUpperCase(Locale.US));
            key.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    input.append(letter);
                }
            });
            addEqualKey(row, key);
        }
        keyboard.addView(row);
    }

    private void addKeyboardLastRow(LinearLayout keyboard, final EditText input) {
        LinearLayout row = keyboardRow();
        addKeyboardLetters(row, input, "VWXYZ");

        Button delete = button("删除");
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int length = input.length();
                if (length > 0) {
                    input.getText().delete(length - 1, length);
                }
            }
        });
        addEqualKey(row, delete);

        Button clear = button("清空");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                input.setText("");
            }
        });
        addEqualKey(row, clear);
        keyboard.addView(row);
    }

    private void addKeyboardLetters(LinearLayout row, final EditText input, String letters) {
        for (int i = 0; i < letters.length(); i++) {
            final String letter = String.valueOf(letters.charAt(i)).toLowerCase(Locale.US);
            Button key = button(letter.toUpperCase(Locale.US));
            key.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    input.append(letter);
                }
            });
            addEqualKey(row, key);
        }
    }

    private LinearLayout keyboardRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(7);
        return row;
    }

    private void addEqualKey(LinearLayout row, Button key) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        key.setMinWidth(0);
        key.setLayoutParams(params);
        row.addView(key);
    }

    private void updateCandidates(final EditText input, LinearLayout container) {
        container.removeAllViews();
        if (input.length() == 0) {
            return;
        }
        for (final String candidate : pinyinCandidates.find(input.getText().toString())) {
            Button button = button(candidate);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    input.setText(candidate);
                    input.setSelection(input.length());
                }
            });
            container.addView(button);
        }
    }

    private void search(final String term) {
        if (TextUtils.isEmpty(term) || loading) {
            return;
        }
        showLoading("正在搜索“" + term + "”...");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Podcast> results = searchClient.search(term);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loading = false;
                            showSearchResults(results);
                        }
                    });
                } catch (final Exception error) {
                    showError(error);
                }
            }
        });
    }

    private void showSearchResults(List<Podcast> results) {
        currentPage = PAGE_SEARCH;
        LinearLayout list = verticalList();
        Button back = button("返回键盘");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSearch();
            }
        });
        list.addView(back);
        if (results.isEmpty()) {
            list.addView(message("没有找到结果，请尝试选择中文候选词。"));
        }
        for (final Podcast podcast : results) {
            LinearLayout row = row();
            row.addView(cover(podcast.artworkUrl, 56));
            row.addView(text(podcast.title + "\n" + podcast.author, 1));
            final boolean subscribed = database.isSubscribed(podcast.collectionId);
            Button action = button(subscribed ? "取消订阅" : "订阅");
            action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (database.isSubscribed(podcast.collectionId)) {
                        database.unsubscribe(podcast.collectionId);
                        ((Button) view).setText("订阅");
                    } else {
                        database.subscribe(podcast);
                        ((Button) view).setText("取消订阅");
                    }
                }
            });
            row.addView(action);
            list.addView(row);
        }
        setScrollableContent(list, false);
    }

    private void openRecent() {
        if (currentPage == PAGE_RECENT) {
            refreshSubscriptions(PAGE_RECENT, true);
        } else {
            showRecent();
        }
    }

    private void openSubscriptions() {
        if (currentPage == PAGE_SUBSCRIPTIONS) {
            refreshSubscriptions(PAGE_SUBSCRIPTIONS, true);
        } else if (!subscriptionsAutoRefreshed) {
            subscriptionsAutoRefreshed = true;
            refreshSubscriptions(PAGE_SUBSCRIPTIONS, false);
        } else {
            showSubscriptions();
        }
    }

    private void refreshSubscriptions(final int targetPage, final boolean manual) {
        if (loading) {
            return;
        }
        currentPage = targetPage;
        final List<Podcast> subscriptions = database.subscriptions();
        if (subscriptions.isEmpty()) {
            if (targetPage == PAGE_SUBSCRIPTIONS) {
                showSubscriptions();
            } else {
                showRecent();
            }
            return;
        }
        showLoading("正在刷新 " + subscriptions.size() + " 个订阅...");
        worker.execute(new Runnable() {
            @Override
            public void run() {
                int success = 0;
                for (Podcast podcast : subscriptions) {
                    try {
                        database.saveEpisodes(rssParser.fetch(podcast));
                        success++;
                    } catch (Exception ignored) {
                    }
                }
                final int completed = success;
                CacheCleaner.clean(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        Toast.makeText(MainActivity.this,
                                "刷新完成：" + completed + "/" + subscriptions.size(),
                                Toast.LENGTH_SHORT).show();
                        if (currentPage == targetPage) {
                            if (targetPage == PAGE_SUBSCRIPTIONS) {
                                showSubscriptions();
                            } else {
                                showRecent();
                            }
                        } else if (manual) {
                            Toast.makeText(MainActivity.this,
                                    "后台刷新已完成", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void cleanCache() {
        worker.execute(new Runnable() {
            @Override
            public void run() {
                final long bytes = CacheCleaner.clean(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                "已清理 " + (bytes / 1024) + " KB", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showLoading(String label) {
        loading = true;
        LinearLayout box = verticalList();
        box.setGravity(Gravity.CENTER);
        box.addView(new ProgressBar(this));
        box.addView(message(label));
        replaceContent(box);
    }

    private void showError(final Exception error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loading = false;
                Toast.makeText(MainActivity.this,
                        "操作失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
                if (currentPage == PAGE_SUBSCRIPTIONS) {
                    showSubscriptions();
                } else if (currentPage == PAGE_SEARCH) {
                    showSearch();
                } else {
                    showRecent();
                }
            }
        });
    }

    private void setScrollableContent(LinearLayout child, boolean pullToRefresh) {
        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(child);
        if (pullToRefresh) {
            scroll.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN && scroll.getScrollY() == 0) {
                        pullStartY = event.getY();
                    } else if (event.getAction() == MotionEvent.ACTION_UP
                            && scroll.getScrollY() == 0
                            && event.getY() - pullStartY > dp(90)) {
                        if (currentPage == PAGE_SUBSCRIPTIONS) {
                            refreshSubscriptions(PAGE_SUBSCRIPTIONS, true);
                        } else if (currentPage == PAGE_RECENT) {
                            refreshSubscriptions(PAGE_RECENT, true);
                        }
                    }
                    return false;
                }
            });
        }
        replaceContent(scroll);
    }

    private void replaceContent(View view) {
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout verticalList() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(4), dp(4), dp(4), dp(12));
        return list;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        return row;
    }

    private TextView text(String value, int weight) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(COLOR_TEXT);
        text.setTextSize(18);
        text.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                weight == 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : 0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        text.setLayoutParams(params);
        return text;
    }

    private TextView message(String value) {
        TextView message = text(value, 0);
        message.setTextColor(COLOR_MUTED);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(20), dp(40), dp(20), dp(40));
        return message;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(16);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setBackgroundResource(R.drawable.focusable_button);
        button.setMinHeight(dp(48));
        button.setMinWidth(dp(72));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setLayoutParams(new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        return button;
    }

    private ImageView cover(String url, int sizeDp) {
        ImageView image = new ImageView(this);
        int size = dp(sizeDp);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(0, 0, dp(8), 0);
        image.setLayoutParams(params);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundResource(R.drawable.ic_cover_default);
        ImageLoader.load(image, url, size);
        return image;
    }

    private ImageButton iconButton(int drawable, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setContentDescription(description);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setBackgroundResource(R.drawable.focusable_button);
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(40)));
        return button;
    }

    private void toggleQueuePopup() {
        if (queuePopup != null && queuePopup.isShowing()) {
            queuePopup.dismiss();
            playerQueue.requestFocus();
            return;
        }
        showQueuePopup();
    }

    private void showQueuePopup() {
        if (queuePopup != null) {
            queuePopup.dismiss();
        }
        LinearLayout root = verticalList();
        root.setBackgroundColor(0xff1c2027);
        LinearLayout header = row();
        TextView title = text("播放列表", 1);
        title.setTextSize(20);
        header.addView(title);
        ImageButton collapse = iconButton(R.drawable.ic_collapse_queue, "收起列表");
        collapse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queuePopup.dismiss();
                playerQueue.requestFocus();
            }
        });
        header.addView(collapse);
        ImageButton repeat = iconButton(repeatIcon(), "播放顺序");
        repeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queueAction(PlaybackService.ACTION_REPEAT_MODE, -1, 0);
            }
        });
        header.addView(repeat);
        ImageButton clear = iconButton(R.drawable.ic_clear_queue, "清空列表");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queueAction(PlaybackService.ACTION_QUEUE_CLEAR, -1, 0);
            }
        });
        header.addView(clear);
        root.addView(header);

        if (queueTitles.length == 0) {
            root.addView(message("当前播放列表为空"));
        } else {
            for (int i = 0; i < queueTitles.length; i++) {
                addQueueRow(root, i);
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        queuePopup = new PopupWindow(scroll, ViewGroup.LayoutParams.MATCH_PARENT, dp(280), true);
        queuePopup.setBackgroundDrawable(new ColorDrawable(0xff1c2027));
        queuePopup.setOutsideTouchable(true);
        queuePopup.showAsDropDown(miniPlayer, 0, -dp(338));
        collapse.post(new Runnable() {
            @Override
            public void run() {
                collapse.requestFocus();
            }
        });
    }

    private void addQueueRow(LinearLayout root, final int index) {
        LinearLayout row = row();
        row.addView(cover(index < queueArtworks.length ? queueArtworks[index] : "", 42));
        ImageButton play = iconButton(queuePlayIcon(index), "播放");
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (index == queueIndex) {
                    queueAction(PlaybackService.ACTION_TOGGLE, index, 0);
                } else {
                    queueAction(PlaybackService.ACTION_PLAY_INDEX, index, 0);
                }
            }
        });
        row.addView(play);
        TextView title = text((index == queueIndex ? "▶ " : "") + queueTitles[index], 1);
        title.setMaxLines(2);
        title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queueAction(PlaybackService.ACTION_PLAY_INDEX, index, 0);
            }
        });
        title.setFocusable(true);
        row.addView(title);

        ImageButton up = iconButton(R.drawable.ic_up, "上移");
        up.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queueAction(PlaybackService.ACTION_QUEUE_MOVE, index, -1);
            }
        });
        row.addView(up);

        ImageButton down = iconButton(R.drawable.ic_down, "下移");
        down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queueAction(PlaybackService.ACTION_QUEUE_MOVE, index, 1);
            }
        });
        row.addView(down);

        ImageButton remove = iconButton(R.drawable.ic_delete, "删除");
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queueAction(PlaybackService.ACTION_QUEUE_REMOVE, index, 0);
            }
        });
        row.addView(remove);
        root.addView(row);
    }

    private int queuePlayIcon(int index) {
        if (index == queueIndex && currentPlaybackPlaying) {
            return R.drawable.ic_pause;
        }
        return R.drawable.ic_play;
    }

    private int repeatIcon() {
        if (repeatMode == 1) {
            return R.drawable.ic_repeat_one;
        }
        if (repeatMode == 2) {
            return R.drawable.ic_repeat_all;
        }
        return R.drawable.ic_order;
    }

    private void queueAction(String action, int index, int delta) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        intent.putExtra(PlaybackService.EXTRA_INDEX, index);
        intent.putExtra(PlaybackService.EXTRA_DELTA, delta);
        startService(intent);
        if (queuePopup != null && queuePopup.isShowing()) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (queuePopup != null && queuePopup.isShowing()) {
                        showQueuePopup();
                    }
                }
            }, 250);
        }
    }

    private String[] nonNull(String[] value) {
        return value == null ? new String[0] : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int generateViewIdCompat() {
        if (Build.VERSION.SDK_INT >= 17) {
            return View.generateViewId();
        }
        for (;;) {
            int current = NEXT_VIEW_ID.get();
            int next = current + 1;
            if (next > 0x00FFFFFF) {
                next = 1;
            }
            if (NEXT_VIEW_ID.compareAndSet(current, next)) {
                return current;
            }
        }
    }

    private String formatSpeed(float speed) {
        if (speed == (int) speed) {
            return ((int) speed) + ".0×";
        }
        return speed + "×";
    }

    private String formatDuration(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void installSeekButton(View button, int direction) {
        SeekHoldListener listener = new SeekHoldListener(direction);
        button.setOnKeyListener(listener);
        button.setOnTouchListener(listener);
    }

    private final class SeekHoldListener implements View.OnKeyListener, View.OnTouchListener {
        private static final long HOLD_DELAY_MS = 600;
        private static final long ACCELERATE_AFTER_MS = 5000;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final int direction;
        private long pressedAt;
        private boolean pressing;
        private boolean repeated;
        private final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                if (!pressing) {
                    return;
                }
                repeated = true;
                long held = SystemClock.uptimeMillis() - pressedAt;
                seekBy(direction * (held >= ACCELERATE_AFTER_MS ? 60000 : 30000));
                handler.postDelayed(this, held >= ACCELERATE_AFTER_MS ? 1000 : 800);
            }
        };

        SeekHoldListener(int direction) {
            this.direction = direction;
        }

        private void start() {
            if (pressing) {
                return;
            }
            pressing = true;
            repeated = false;
            pressedAt = SystemClock.uptimeMillis();
            handler.postDelayed(repeat, HOLD_DELAY_MS);
        }

        private void finish(boolean allowShortSeek) {
            if (!pressing) {
                return;
            }
            pressing = false;
            handler.removeCallbacks(repeat);
            if (!repeated && allowShortSeek) {
                seekBy(direction * 15000);
            }
        }

        @Override
        public boolean onKey(View view, int keyCode, KeyEvent event) {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER
                    && keyCode != KeyEvent.KEYCODE_ENTER
                    && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) {
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                start();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                finish(true);
            }
            return true;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                start();
                view.setPressed(true);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                finish(true);
                view.setPressed(false);
                view.performClick();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                finish(false);
                view.setPressed(false);
                return true;
            }
            return true;
        }
    }

    private void seekBy(int deltaMs) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_SEEK);
        intent.putExtra(PlaybackService.EXTRA_DELTA, deltaMs);
        startService(intent);
    }

    private void connectHeaderToFirstEpisode() {
        View first = firstContentButton == null ? firstEpisodeButton : firstContentButton;
        if (first == null) {
            return;
        }
        int firstId = first.getId();
        tabSubscriptions.setNextFocusDownId(firstId);
        tabRecent.setNextFocusDownId(firstId);
        tabSearch.setNextFocusDownId(firstId);
        findViewById(R.id.action_clear_cache).setNextFocusDownId(firstId);
    }

    private void configurePlayerFocus() {
        playerQueue.setNextFocusLeftId(R.id.player_speed_down);
        playerQueue.setNextFocusRightId(R.id.player_back);
        playerBack.setNextFocusLeftId(R.id.player_queue);
        playerBack.setNextFocusRightId(R.id.player_forward);
        playerForward.setNextFocusLeftId(R.id.player_back);
        playerForward.setNextFocusRightId(R.id.player_toggle);
        playerToggle.setNextFocusLeftId(R.id.player_forward);
        playerToggle.setNextFocusRightId(R.id.player_speed_up);
        playerSpeedUp.setNextFocusLeftId(R.id.player_toggle);
        playerSpeedUp.setNextFocusRightId(R.id.player_speed_down);
        playerSpeedDown.setNextFocusLeftId(R.id.player_speed_up);
        playerSpeedDown.setNextFocusRightId(R.id.player_queue);
    }

    private void scheduleCleanup() {
        AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, CacheCleanupReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarms.setInexactRepeating(AlarmManager.RTC,
                System.currentTimeMillis() + AlarmManager.INTERVAL_DAY,
                AlarmManager.INTERVAL_DAY, pending);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && miniPlayer.getVisibility() == View.VISIBLE) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                playerToggle.performClick();
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS
                    || event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                playerToggle.requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (queuePopup != null && queuePopup.isShowing()) {
            queuePopup.dismiss();
            return;
        }
        if (subscriptionDetailVisible) {
            showSubscriptions();
            tabSubscriptions.requestFocus();
            return;
        }
        Button tab = currentTab();
        if (tab != null && !tab.hasFocus()) {
            tab.requestFocus();
            return;
        }
        moveTaskToBack(true);
    }

    private Button currentTab() {
        if (currentPage == PAGE_SUBSCRIPTIONS) {
            return tabSubscriptions;
        }
        if (currentPage == PAGE_SEARCH) {
            return tabSearch;
        }
        return tabRecent;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(playbackReceiver);
        worker.shutdownNow();
        database.close();
        super.onDestroy();
    }
}
