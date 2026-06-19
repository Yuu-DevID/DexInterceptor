package com.yuuxi.interceptor.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yuuxi.interceptor.R;
import com.yuuxi.interceptor.logger.MethodCallLogger;
import com.yuuxi.interceptor.model.HookCallEntry;
import com.yuuxi.interceptor.util.Const;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InterceptorActivity extends AppCompatActivity {

    private RecyclerView recyclerLog;
    private TextView tvEmptyState;
    private TextView tvHeader;
    private TextView tvLogCount;
    private Button btnClear;
    private Button btnFilter;
    private Button btnExport;
    private Button btnAutoScroll;
    private EditText editSearch;

    private LogAdapter logAdapter;
    private SharedPreferences prefs;

    private ScheduledExecutorService autoRefresh;
    private Handler uiHandler;
    private int lastEntryCount = 0;
    private boolean autoScrollEnabled = true;
    private LinearLayoutManager layoutManager;

    // Filter chips
    private TextView chipAll, chipJava, chipNative, chipSystem, chipError;
    private View[] allChips;
    private int currentFilterChip = 0; // 0=All, 1=Java, 2=Native, 3=System, 4=Error
    private String searchQuery = "";

    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String data = intent.getStringExtra(Const.EXTRA_LOG_JSON);
            if (data != null) {
                // Parse and add directly via adapter
                parseAndAddLogEntry(data);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        );

        setContentView(R.layout.activity_interceptor);

        prefs = getSharedPreferences(Const.PREFS_NAME, MODE_PRIVATE);
        uiHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupRecyclerView();
        setupFilterChips();
        setupSearch();
        setupAutoRefresh();
        loadExistingLogs();
    }

    private void initViews() {
        tvHeader = findViewById(R.id.tv_header);
        tvLogCount = findViewById(R.id.tv_log_count);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        btnClear = findViewById(R.id.btn_clear);
        btnFilter = findViewById(R.id.btn_filter);
        btnExport = findViewById(R.id.btn_export);
        btnAutoScroll = findViewById(R.id.btn_auto_scroll);
        editSearch = findViewById(R.id.edit_search);

        chipAll = findViewById(R.id.chip_all);
        chipJava = findViewById(R.id.chip_java);
        chipNative = findViewById(R.id.chip_native);
        chipSystem = findViewById(R.id.chip_system);
        chipError = findViewById(R.id.chip_error);
        allChips = new View[]{chipAll, chipJava, chipNative, chipSystem, chipError};

        String targetPkg = prefs.getString(Const.KEY_TARGET_PACKAGE, "unknown");
        tvHeader.setText("DexInterceptor | " + targetPkg);

        btnClear.setOnClickListener(v -> {
            logAdapter.clear();
            lastEntryCount = 0;
            MethodCallLogger.clearEntries();
            updateEmptyState();
            updateLogCount();
        });

        btnFilter.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        });

        btnExport.setOnClickListener(v -> exportLog());

        btnAutoScroll.setOnClickListener(v -> {
            autoScrollEnabled = !autoScrollEnabled;
            updateAutoScrollButton();
            if (autoScrollEnabled) {
                scrollToBottom();
            }
        });

        updateAutoScrollButton();
    }

    private void setupRecyclerView() {
        recyclerLog = findViewById(R.id.recycler_log);
        logAdapter = new LogAdapter();
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerLog.setLayoutManager(layoutManager);
        recyclerLog.setAdapter(logAdapter);

        // Pause auto-scroll when user scrolls up
        recyclerLog.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    // User is scrolling - check if near bottom
                    int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
                    int totalItems = logAdapter.getItemCount();
                    if (lastVisible < totalItems - 3) {
                        autoScrollEnabled = false;
                        updateAutoScrollButton();
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // If user scrolled to bottom, re-enable auto-scroll
                int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
                int totalItems = logAdapter.getItemCount();
                if (lastVisible >= totalItems - 1 && !autoScrollEnabled) {
                    autoScrollEnabled = true;
                    updateAutoScrollButton();
                }
            }
        });
    }

    private void setupFilterChips() {
        chipAll.setOnClickListener(v -> setFilter(0));
        chipJava.setOnClickListener(v -> setFilter(1));
        chipNative.setOnClickListener(v -> setFilter(2));
        chipSystem.setOnClickListener(v -> setFilter(3));
        chipError.setOnClickListener(v -> setFilter(4));

        updateChipAppearance(0);
    }

    private void setFilter(int filterIndex) {
        currentFilterChip = filterIndex;
        updateChipAppearance(filterIndex);
        // Clear and reload with filter
        logAdapter.clear();
        lastEntryCount = 0;

        List<HookCallEntry> allEntries = MethodCallLogger.getAllEntries();
        List<HookCallEntry> filtered = filterEntries(allEntries, filterIndex, searchQuery);
        logAdapter.addEntries(filtered);
        lastEntryCount = allEntries.size();
        updateEmptyState();
        updateLogCount();

        if (autoScrollEnabled) {
            scrollToBottom();
        }
    }

    private void updateChipAppearance(int selected) {
        for (int i = 0; i < allChips.length; i++) {
            TextView chip = (TextView) allChips[i];
            if (i == selected) {
                // Selected chip
                chip.setTextColor(Color.parseColor("#FF1A1A2E"));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(14 * getResources().getDisplayMetrics().density);
                bg.setColor(getChipActiveColor(i));
                chip.setBackground(bg);
            } else {
                // Unselected chip
                chip.setTextColor(getChipColor(i));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(14 * getResources().getDisplayMetrics().density);
                bg.setColor(Color.TRANSPARENT);
                bg.setStroke((int) (1 * getResources().getDisplayMetrics().density), getChipColor(i));
                chip.setBackground(bg);
            }
        }
    }

    private int getChipColor(int index) {
        switch (index) {
            case 0: return Color.parseColor("#FF00D4FF");
            case 1: return Color.parseColor("#FF00D4FF");
            case 2: return Color.parseColor("#FFFF00FF");
            case 3: return Color.parseColor("#FFAABBCC");
            case 4: return Color.parseColor("#FFFF4444");
            default: return Color.WHITE;
        }
    }

    private int getChipActiveColor(int index) {
        switch (index) {
            case 0: return Color.parseColor("#FF00D4FF");
            case 1: return Color.parseColor("#FF00D4FF");
            case 2: return Color.parseColor("#FFFF00FF");
            case 3: return Color.parseColor("#FFAABBCC");
            case 4: return Color.parseColor("#FFFF4444");
            default: return Color.WHITE;
        }
    }

    private void setupSearch() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase();
                applyFilterAndSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void applyFilterAndSearch() {
        logAdapter.clear();
        lastEntryCount = 0;

        List<HookCallEntry> allEntries = MethodCallLogger.getAllEntries();
        List<HookCallEntry> filtered = filterEntries(allEntries, currentFilterChip, searchQuery);
        logAdapter.addEntries(filtered);
        lastEntryCount = allEntries.size();
        updateEmptyState();
        updateLogCount();

        if (autoScrollEnabled) {
            scrollToBottom();
        }
    }

    private List<HookCallEntry> filterEntries(List<HookCallEntry> entries, int filterChip, String query) {
        List<HookCallEntry> result = new ArrayList<>();
        for (HookCallEntry entry : entries) {
            // Type filter
            if (!matchesFilter(entry, filterChip)) continue;
            // Search filter
            if (!query.isEmpty() && !matchesSearch(entry, query)) continue;
            result.add(entry);
        }
        return result;
    }

    private boolean matchesFilter(HookCallEntry entry, int filterChip) {
        switch (filterChip) {
            case 0: return true; // All
            case 1: return entry.type == HookCallEntry.Type.JAVA_CALL || entry.type == HookCallEntry.Type.JAVA_RETURN;
            case 2: return entry.type == HookCallEntry.Type.NATIVE_CALL || entry.type == HookCallEntry.Type.NATIVE_RETURN || entry.type == HookCallEntry.Type.NATIVE_LOAD;
            case 3: return entry.type == HookCallEntry.Type.SYSTEM;
            case 4: return entry.type == HookCallEntry.Type.ERROR;
            default: return true;
        }
    }

    private boolean matchesSearch(HookCallEntry entry, String query) {
        if (entry.className != null && entry.className.toLowerCase().contains(query)) return true;
        if (entry.methodName != null && entry.methodName.toLowerCase().contains(query)) return true;
        if (entry.libName != null && entry.libName.toLowerCase().contains(query)) return true;
        if (entry.funcName != null && entry.funcName.toLowerCase().contains(query)) return true;
        if (entry.message != null && entry.message.toLowerCase().contains(query)) return true;
        if (entry.args != null && entry.args.toLowerCase().contains(query)) return true;
        if (entry.result != null && entry.result.toLowerCase().contains(query)) return true;
        return false;
    }

    private void setupAutoRefresh() {
        autoRefresh = Executors.newSingleThreadScheduledExecutor();
        autoRefresh.scheduleAtFixedRate(() -> {
            List<HookCallEntry> entries = MethodCallLogger.getRecentEntries(200);
            if (entries.size() > lastEntryCount) {
                uiHandler.post(() -> {
                    List<HookCallEntry> newEntries = entries.subList(lastEntryCount, entries.size());
                    // Apply filter to new entries
                    List<HookCallEntry> filteredNew = filterEntries(newEntries, currentFilterChip, searchQuery);
                    logAdapter.addEntries(filteredNew);
                    lastEntryCount = entries.size();
                    updateEmptyState();
                    updateLogCount();
                    if (autoScrollEnabled) {
                        scrollToBottom();
                    }
                });
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    private void loadExistingLogs() {
        List<HookCallEntry> entries = MethodCallLogger.getAllEntries();
        List<HookCallEntry> filtered = filterEntries(entries, currentFilterChip, searchQuery);
        logAdapter.addEntries(filtered);
        lastEntryCount = entries.size();
        updateEmptyState();
        updateLogCount();
        if (autoScrollEnabled) {
            scrollToBottom();
        }
    }

    private void parseAndAddLogEntry(String raw) {
        uiHandler.post(() -> {
            // Simple pipe-delimited format: TYPE|SIGNATURE|ARGS
            // We'll just show it as a system entry
            // The real entries come from MethodCallLogger
        });
    }

    private void scrollToBottom() {
        if (logAdapter.getItemCount() > 0) {
            recyclerLog.smoothScrollToPosition(logAdapter.getItemCount() - 1);
        }
    }

    private void updateEmptyState() {
        if (logAdapter.getItemCount() == 0) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerLog.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerLog.setVisibility(View.VISIBLE);
        }
    }

    private void updateLogCount() {
        tvLogCount.setText(logAdapter.getUnfilteredSize() + " entries");
    }

    private void updateAutoScrollButton() {
        if (autoScrollEnabled) {
            btnAutoScroll.setText("⬇ AUTO");
            btnAutoScroll.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#4000FF88")));
        } else {
            btnAutoScroll.setText("⏸ PAUSED");
            btnAutoScroll.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#40FFAA00")));
        }
    }

    private void exportLog() {
        try {
            java.io.File logFile = MethodCallLogger.getLogFile();
            if (logFile != null && logFile.exists()) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/json");
                shareIntent.putExtra(Intent.EXTRA_STREAM,
                        androidx.core.content.FileProvider.getUriForFile(
                                this,
                                getPackageName() + ".fileprovider",
                                logFile
                        ));
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Export Log"));
            }
        } catch (Throwable t) {
            Toast.makeText(this, "Export failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Const.ACTION_LOG_ENTRY);
        registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(logReceiver);
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoRefresh != null) {
            autoRefresh.shutdown();
        }
    }
}
