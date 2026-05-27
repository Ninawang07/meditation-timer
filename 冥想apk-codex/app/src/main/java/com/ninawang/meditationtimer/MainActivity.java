package com.ninawang.meditationtimer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(27, 24, 20);
    private static final int PANEL = Color.rgb(36, 32, 27);
    private static final int PANEL_2 = Color.rgb(45, 40, 34);
    private static final int GOLD = Color.rgb(200, 164, 92);
    private static final int TEXT = Color.rgb(229, 222, 213);
    private static final int MUTED = Color.rgb(154, 146, 136);
    private static final int DIM = Color.rgb(94, 88, 80);

    private static final int[] PRESET_MINUTES = {1, 3, 5, 10, 15, 20, 30};
    private static final String STORAGE_KEY = "meditation_sessions";
    private static final long BOWL_FADE_DURATION = 26000L;
    private static final long BOWL_SECOND_STRIKE_START = 6800L;
    private static final long BOWL_SECOND_STRIKE_END = 8800L;
    private static final float BOWL_SECOND_STRIKE_GAIN = 0.45f;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private MeditationRingView ringView;
    private TextView audioIndicator;
    private LinearLayout presetSection;
    private LinearLayout breathModes;
    private EditText customInput;

    private boolean countdownMode = true;
    private int durationSeconds = 300;
    private boolean running = false;
    private boolean paused = false;
    private boolean breathing = false;
    private String breathMode = "box";
    private int breathPhase = 0;
    private long startTime = 0L;
    private int pausedCurrent = 0;

    private Runnable tickRunnable;
    private Runnable breathRunnable;
    private Runnable breathDelayRunnable;
    private Runnable audioIndicatorRunnable;
    private Runnable bowlFadeRunnable;
    private Runnable cueFadeRunnable;

    private MediaPlayer bowlPlayer;
    private MediaPlayer cuePlayer;
    private long bowlFadeStartedAt = 0L;
    private float bowlFadeFrom = 1f;
    private float bowlFadeTo = 0.08f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildTimerView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (running && !paused) {
            pausedCurrent = calcCurrent();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearAllTimers();
        stopAllAudio();
        releaseAudio();
    }

    private void buildTimerView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(16), dp(28), dp(22));
        root.setBackgroundColor(BG);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        setContentView(scrollView);

        root.addView(buildModeTabs());

        ringView = new MeditationRingView(this);
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(dp(270), dp(270));
        ringParams.gravity = Gravity.CENTER_HORIZONTAL;
        ringParams.setMargins(0, dp(24), 0, dp(22));
        root.addView(ringView, ringParams);

        audioIndicator = text("颂钵", 12, DIM, Typeface.NORMAL);
        audioIndicator.setGravity(Gravity.CENTER);
        root.addView(audioIndicator, fullWidth(dp(30)));

        presetSection = new LinearLayout(this);
        presetSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(presetSection, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));
        buildPresets();

        root.addView(buildBreathingSection());
        root.addView(buildControls());

        TextView history = text("冥想记录 >", 14, DIM, Typeface.NORMAL);
        history.setGravity(Gravity.CENTER);
        history.setPadding(0, dp(20), 0, dp(4));
        history.setOnClickListener(v -> buildHistoryView());
        root.addView(history, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT));

        updateDisplay(durationSeconds);
    }

    private View buildModeTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        tabs.setBackground(round(PANEL, dp(14)));
        tabs.addView(tabButton("定时", true, () -> setMode(true)), weightParams());
        tabs.addView(tabButton("不定时", false, () -> setMode(false)), weightParams());

        LinearLayout.LayoutParams params = fullWidth(dp(48));
        params.setMargins(0, dp(16), 0, 0);
        tabs.setLayoutParams(params);
        return tabs;
    }

    private Button tabButton(String label, boolean selected, Runnable action) {
        Button button = smallButton(label);
        button.setTextColor(selected == countdownMode ? GOLD : MUTED);
        button.setBackground(round(selected == countdownMode ? PANEL_2 : Color.TRANSPARENT, dp(11)));
        button.setOnClickListener(v -> {
            if (!running) {
                action.run();
                buildTimerView();
            }
        });
        return button;
    }

    private void setMode(boolean countdown) {
        countdownMode = countdown;
        if (countdownMode) {
            pausedCurrent = durationSeconds;
            updateDisplay(durationSeconds);
        } else {
            pausedCurrent = 0;
            updateDisplay(0);
        }
    }

    private void buildPresets() {
        presetSection.removeAllViews();
        presetSection.setVisibility(countdownMode ? View.VISIBLE : View.GONE);
        if (!countdownMode) return;

        LinearLayout row = null;
        for (int i = 0; i < PRESET_MINUTES.length; i++) {
            if (i % 4 == 0) {
                row = row();
                presetSection.addView(row, fullWidth(dp(48)));
            }
            int minute = PRESET_MINUTES[i];
            Button button = smallButton(minute + " 分");
            button.setTextColor(durationSeconds == minute * 60 ? GOLD : MUTED);
            button.setBackground(round(durationSeconds == minute * 60 ? Color.rgb(55, 45, 30) : PANEL, dp(12)));
            button.setOnClickListener(v -> {
                if (!running) {
                    durationSeconds = minute * 60;
                    if (customInput != null) customInput.setText(String.valueOf(durationSeconds));
                    updateDisplay(durationSeconds);
                    buildPresets();
                }
            });
            row.addView(button, weightParamsWithMargin());
        }

        LinearLayout customRow = row();
        TextView label = text("自定义秒数", 14, MUTED, Typeface.NORMAL);
        customInput = new EditText(this);
        customInput.setText(String.valueOf(durationSeconds));
        customInput.setTextColor(TEXT);
        customInput.setTextSize(15);
        customInput.setGravity(Gravity.CENTER);
        customInput.setSingleLine(true);
        customInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        customInput.setBackgroundColor(Color.TRANSPARENT);
        customInput.setOnEditorActionListener((v, actionId, event) -> {
            applyCustomSeconds();
            return false;
        });
        customInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyCustomSeconds();
        });
        customRow.addView(label, weightParams());
        customRow.addView(customInput, weightParams());
        customRow.setBackground(round(PANEL, dp(12)));
        customRow.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams params = fullWidth(dp(48));
        params.setMargins(0, dp(8), 0, dp(16));
        presetSection.addView(customRow, params);
    }

    private View buildBreathingSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(8), 0, dp(16));

        Button toggle = smallButton(breathing ? "呼吸引导：开" : "呼吸引导：关");
        toggle.setTextColor(breathing ? GOLD : MUTED);
        toggle.setBackground(round(PANEL, dp(12)));
        toggle.setOnClickListener(v -> {
            if (!running) {
                breathing = !breathing;
                buildTimerView();
            }
        });
        section.addView(toggle, fullWidth(dp(48)));

        breathModes = row();
        breathModes.setVisibility(breathing ? View.VISIBLE : View.GONE);
        breathModes.addView(breathModeButton("箱式 4-4-4-4", "box"), weightParamsWithMargin());
        breathModes.addView(breathModeButton("4-7-8 助眠", "478"), weightParamsWithMargin());
        LinearLayout.LayoutParams params = fullWidth(dp(48));
        params.setMargins(0, dp(8), 0, 0);
        section.addView(breathModes, params);
        return section;
    }

    private Button breathModeButton(String label, String mode) {
        Button button = smallButton(label);
        button.setTextColor(mode.equals(breathMode) ? GOLD : MUTED);
        button.setBackground(round(mode.equals(breathMode) ? Color.rgb(55, 45, 30) : PANEL, dp(10)));
        button.setOnClickListener(v -> {
            if (!running) {
                breathMode = mode;
                buildTimerView();
            }
        });
        return button;
    }

    private View buildControls() {
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(0, dp(12), 0, dp(8));

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);

        if (running && !paused) {
            controls.addView(circleButton("暂停", false, this::onPauseTap));
            controls.addView(circleButton("停止", true, this::onStopTap));
        } else if (running) {
            controls.addView(circleButton("停止", false, this::onStopTap));
            controls.addView(circleButton("继续", true, this::onStartTap));
        } else {
            controls.addView(circleButton("开始", true, this::onStartTap));
        }

        frame.addView(controls);
        return frame;
    }

    private Button circleButton(String label, boolean primary, Runnable action) {
        Button button = smallButton(label);
        int size = primary ? dp(74) : dp(56);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(dp(10), 0, dp(10), 0);
        button.setLayoutParams(params);
        button.setTextColor(primary ? Color.rgb(20, 16, 8) : MUTED);
        button.setTextSize(primary ? 15 : 13);
        button.setBackground(round(primary ? GOLD : PANEL, size / 2));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void onStartTap() {
        if (running && paused) {
            long now = System.currentTimeMillis();
            if (countdownMode) {
                startTime = now - (long) (durationSeconds - pausedCurrent) * 1000L;
            } else {
                startTime = now - (long) pausedCurrent * 1000L;
            }
            paused = false;
            startTickLoop();
            if (breathing) startBreathCycle();
            buildTimerView();
            return;
        }

        applyCustomSeconds();
        startTime = System.currentTimeMillis();
        breathPhase = 0;
        running = true;
        paused = false;
        startTickLoop();
        playBowl(true);
        flashAudioIndicator();

        if (breathing) {
            clearBreathDelay();
            breathDelayRunnable = () -> {
                breathDelayRunnable = null;
                if (running && !paused) startBreathCycle();
            };
            handler.postDelayed(breathDelayRunnable, 27000L);
        }
        buildTimerView();
    }

    private void onPauseTap() {
        if (!running || paused) return;
        clearAllTimers();
        pausedCurrent = calcCurrent();
        paused = true;
        buildTimerView();
    }

    private void onStopTap() {
        boolean wasRunning = running;
        clearAllTimers();
        int meditatedSeconds = wasRunning ? getMeditatedSeconds() : 0;

        running = false;
        paused = false;
        breathPhase = 0;
        stopAllAudio();

        if (meditatedSeconds > 6) {
            addSession(Math.round(meditatedSeconds / 6f) / 10f, getModeLabel());
        }

        updateDisplay(countdownMode ? durationSeconds : 0);
        buildTimerView();
    }

    private void finishCountdown() {
        clearAllTimers();
        running = false;
        paused = false;
        breathPhase = 0;
        ringView.setState("00:00", "", 0f);
        playBowl(false);
        flashAudioIndicator();
        addSession(Math.round(durationSeconds / 60f * 10f) / 10f, getModeLabel());

        handler.postDelayed(() -> {
            if (!running) {
                updateDisplay(durationSeconds);
                buildTimerView();
            }
        }, 2000L);
    }

    private void startTickLoop() {
        clearTickLoop();
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                int current = calcCurrent();
                pausedCurrent = current;
                if (countdownMode && current <= 0) {
                    finishCountdown();
                    return;
                }
                updateDisplay(current);
                handler.postDelayed(this, 250L);
            }
        };
        handler.post(tickRunnable);
    }

    private int calcCurrent() {
        int elapsed = (int) ((System.currentTimeMillis() - startTime) / 1000L);
        if (countdownMode) return Math.max(0, durationSeconds - elapsed);
        return elapsed;
    }

    private int getMeditatedSeconds() {
        if (paused) {
            return countdownMode ? Math.max(0, durationSeconds - pausedCurrent) : Math.max(0, pausedCurrent);
        }
        int elapsed = (int) ((System.currentTimeMillis() - startTime) / 1000L);
        return countdownMode ? Math.min(elapsed, durationSeconds) : elapsed;
    }

    private void updateDisplay(int total) {
        String display = formatTime(total);
        float fraction = countdownMode ? total / (float) durationSeconds : Math.min(total / 7200f, 1f);
        String label = breathing && running ? getCurrentBreathLabel() : (countdownMode ? "分钟" : "已过");
        if (ringView != null) ringView.setState(display, label, fraction);
    }

    private void startBreathCycle() {
        clearBreathTimer();
        runBreathPhase();
    }

    private void runBreathPhase() {
        if (!running || paused) return;
        String[] phases = breathMode.equals("478")
                ? new String[]{"吸气", "屏息", "呼气"}
                : new String[]{"吸气", "屏息", "呼气", "屏息"};
        int[] durations = breathMode.equals("478")
                ? new int[]{4, 7, 8}
                : new int[]{4, 4, 4, 4};

        String phase = phases[breathPhase];
        updateDisplay(calcCurrent());
        if ("吸气".equals(phase)) playCue();

        breathRunnable = () -> {
            breathPhase = (breathPhase + 1) % phases.length;
            runBreathPhase();
        };
        handler.postDelayed(breathRunnable, durations[breathPhase] * 1000L);
    }

    private String getCurrentBreathLabel() {
        if (!breathing) return "";
        String[] phases = breathMode.equals("478")
                ? new String[]{"吸气", "屏息", "呼气"}
                : new String[]{"吸气", "屏息", "呼气", "屏息"};
        return phases[Math.max(0, Math.min(breathPhase, phases.length - 1))];
    }

    private void playBowl(boolean fadeOut) {
        stopBowl();
        bowlPlayer = MediaPlayer.create(this, R.raw.bowl);
        if (bowlPlayer == null) return;
        bowlFadeFrom = fadeOut ? 1f : 0.08f;
        bowlFadeTo = fadeOut ? 0.08f : 1f;
        bowlPlayer.setVolume(bowlFadeFrom, bowlFadeFrom);
        bowlPlayer.setOnCompletionListener(mp -> stopBowl());
        bowlPlayer.start();
        bowlFadeStartedAt = System.currentTimeMillis();
        bowlFadeRunnable = new Runnable() {
            @Override
            public void run() {
                if (bowlPlayer == null) return;
                long elapsed = System.currentTimeMillis() - bowlFadeStartedAt;
                float progress = Math.min(elapsed / (float) BOWL_FADE_DURATION, 1f);
                float base = bowlFadeFrom + (bowlFadeTo - bowlFadeFrom) * progress;
                float volume = clamp(base * getSecondStrikeGain(elapsed));
                bowlPlayer.setVolume(volume, volume);
                if (progress < 1f) handler.postDelayed(this, 100L);
            }
        };
        handler.post(bowlFadeRunnable);
    }

    private float getSecondStrikeGain(long elapsed) {
        if (elapsed < BOWL_SECOND_STRIKE_START || elapsed > BOWL_SECOND_STRIKE_END) return 1f;
        float progress = (elapsed - BOWL_SECOND_STRIKE_START) /
                (float) (BOWL_SECOND_STRIKE_END - BOWL_SECOND_STRIKE_START);
        float smooth = (float) Math.sin(Math.PI * progress);
        return 1f - (1f - BOWL_SECOND_STRIKE_GAIN) * smooth;
    }

    private void playCue() {
        stopCue();
        cuePlayer = MediaPlayer.create(this, R.raw.cue);
        if (cuePlayer == null) return;
        final float startVolume = 0.55f;
        cuePlayer.setVolume(startVolume, startVolume);
        cuePlayer.setOnCompletionListener(mp -> stopCue());
        cuePlayer.start();
        final long startedAt = System.currentTimeMillis();
        cueFadeRunnable = new Runnable() {
            @Override
            public void run() {
                if (cuePlayer == null) return;
                float progress = Math.min((System.currentTimeMillis() - startedAt) / 4500f, 1f);
                float volume = startVolume * (1f - progress);
                cuePlayer.setVolume(volume, volume);
                if (progress >= 1f) stopCue();
                else handler.postDelayed(this, 250L);
            }
        };
        handler.post(cueFadeRunnable);
    }

    private void stopAllAudio() {
        stopBowl();
        stopCue();
    }

    private void stopBowl() {
        if (bowlFadeRunnable != null) handler.removeCallbacks(bowlFadeRunnable);
        bowlFadeRunnable = null;
        if (bowlPlayer != null) {
            try {
                bowlPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            bowlPlayer.release();
            bowlPlayer = null;
        }
    }

    private void stopCue() {
        if (cueFadeRunnable != null) handler.removeCallbacks(cueFadeRunnable);
        cueFadeRunnable = null;
        if (cuePlayer != null) {
            try {
                cuePlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            cuePlayer.release();
            cuePlayer = null;
        }
    }

    private void releaseAudio() {
        stopAllAudio();
    }

    private void flashAudioIndicator() {
        if (audioIndicator == null) return;
        audioIndicator.setTextColor(GOLD);
        if (audioIndicatorRunnable != null) handler.removeCallbacks(audioIndicatorRunnable);
        audioIndicatorRunnable = () -> {
            if (audioIndicator != null) audioIndicator.setTextColor(DIM);
        };
        handler.postDelayed(audioIndicatorRunnable, 1500L);
    }

    private void addSession(float minutes, String mode) {
        try {
            JSONArray sessions = getSessions();
            JSONObject item = new JSONObject();
            Date now = new Date();
            item.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(now));
            item.put("time", new SimpleDateFormat("HH:mm", Locale.CHINA).format(now));
            item.put("duration", minutes);
            item.put("mode", mode);

            JSONArray next = new JSONArray();
            next.put(item);
            for (int i = 0; i < Math.min(sessions.length(), 99); i++) {
                next.put(sessions.getJSONObject(i));
            }
            prefs().edit().putString(STORAGE_KEY, next.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private void buildHistoryView() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(22), dp(24), dp(22));
        page.setBackgroundColor(BG);

        Button back = smallButton("< 返回计时");
        back.setTextColor(GOLD);
        back.setBackground(round(PANEL, dp(12)));
        back.setOnClickListener(v -> buildTimerView());
        page.addView(back, fullWidth(dp(48)));

        LinearLayout stats = row();
        int count = 0;
        float totalMinutes = 0f;
        Set<String> dates = new HashSet<>();
        JSONArray sessions = getSessions();
        for (int i = 0; i < sessions.length(); i++) {
            try {
                JSONObject item = sessions.getJSONObject(i);
                count++;
                totalMinutes += (float) item.optDouble("duration", 0);
                dates.add(item.optString("date"));
            } catch (JSONException ignored) {
            }
        }
        stats.addView(statCard(String.valueOf(count), "次冥想"), weightParamsWithMargin());
        stats.addView(statCard(String.valueOf(Math.round(totalMinutes)), "分钟"), weightParamsWithMargin());
        stats.addView(statCard(String.valueOf(calcStreak(dates)), "天连续"), weightParamsWithMargin());
        LinearLayout.LayoutParams statParams = fullWidth(dp(92));
        statParams.setMargins(0, dp(18), 0, dp(20));
        page.addView(stats, statParams);

        if (sessions.length() == 0) {
            TextView empty = text("暂无冥想记录\n开始你的第一次冥想吧", 16, DIM, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            page.addView(empty, fullWidth(dp(220)));
        } else {
            for (int i = 0; i < sessions.length(); i++) {
                try {
                    JSONObject item = sessions.getJSONObject(i);
                    TextView row = text(
                            item.optString("date") + " " + item.optString("time") +
                                    "\n" + item.optDouble("duration") + " 分钟 · " + item.optString("mode"),
                            15,
                            TEXT,
                            Typeface.NORMAL
                    );
                    row.setPadding(dp(12), dp(12), dp(12), dp(12));
                    row.setBackground(round(PANEL, dp(8)));
                    LinearLayout.LayoutParams params = fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, 0, 0, dp(10));
                    page.addView(row, params);
                } catch (JSONException ignored) {
                }
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
    }

    private View statCard(String value, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(round(PANEL, dp(10)));
        TextView valueView = text(value, 26, GOLD, Typeface.NORMAL);
        valueView.setGravity(Gravity.CENTER);
        TextView labelView = text(label, 12, DIM, Typeface.NORMAL);
        labelView.setGravity(Gravity.CENTER);
        card.addView(valueView);
        card.addView(labelView);
        return card;
    }

    private JSONArray getSessions() {
        String raw = prefs().getString(STORAGE_KEY, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private int calcStreak(Set<String> dates) {
        int streak = 0;
        long oneDay = 24L * 60L * 60L * 1000L;
        long cursor = System.currentTimeMillis();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        while (dates.contains(fmt.format(new Date(cursor)))) {
            streak++;
            cursor -= oneDay;
        }
        return streak;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("meditation_timer", MODE_PRIVATE);
    }

    private String getModeLabel() {
        if (!countdownMode) return "不定时";
        if (breathing) return "定时+呼吸";
        return "定时";
    }

    private void applyCustomSeconds() {
        if (customInput == null || running) return;
        try {
            int value = Integer.parseInt(customInput.getText().toString().trim());
            durationSeconds = Math.max(1, Math.min(7200, value));
        } catch (NumberFormatException ignored) {
            durationSeconds = Math.max(1, durationSeconds);
        }
    }

    private void clearAllTimers() {
        clearTickLoop();
        clearBreathTimer();
        clearBreathDelay();
    }

    private void clearTickLoop() {
        if (tickRunnable != null) handler.removeCallbacks(tickRunnable);
        tickRunnable = null;
    }

    private void clearBreathTimer() {
        if (breathRunnable != null) handler.removeCallbacks(breathRunnable);
        breathRunnable = null;
    }

    private void clearBreathDelay() {
        if (breathDelayRunnable != null) handler.removeCallbacks(breathDelayRunnable);
        breathDelayRunnable = null;
    }

    private String formatTime(int total) {
        int safe = Math.max(0, total);
        int mins = safe / 60;
        int secs = safe % 60;
        return String.format(Locale.CHINA, "%02d:%02d", mins, secs);
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        return view;
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setPadding(dp(6), 0, dp(6), 0);
        return button;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidth(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private LinearLayout.LayoutParams weightParamsWithMargin() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    public static class MeditationRingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String time = "05:00";
        private String label = "分钟";
        private float fraction = 1f;

        public MeditationRingView(Context context) {
            super(context);
        }

        public void setState(String time, String label, float fraction) {
            this.time = time;
            this.label = label == null ? "" : label;
            this.fraction = Math.max(0f, Math.min(1f, fraction));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int width = getWidth();
            int height = getHeight();
            float size = Math.min(width, height);
            float stroke = size * 0.014f;
            float radius = size * 0.44f;
            float cx = width / 2f;
            float cy = height / 2f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.rgb(45, 40, 34));
            canvas.drawCircle(cx, cy, radius, paint);

            RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            paint.setColor(Color.rgb(200, 164, 92));
            canvas.drawArc(rect, -90f, 360f * fraction, false, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            paint.setColor(Color.rgb(229, 222, 213));
            paint.setTextSize(size * 0.19f);
            canvas.drawText(time, cx, cy + size * 0.02f, paint);

            paint.setColor(Color.rgb(154, 146, 136));
            paint.setTextSize(size * 0.075f);
            canvas.drawText(label, cx, cy + size * 0.15f, paint);
        }
    }
}
