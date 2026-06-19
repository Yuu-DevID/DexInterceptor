package com.yuuxi.interceptor.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.yuuxi.interceptor.R;
import com.yuuxi.interceptor.logger.MethodCallLogger;
import com.yuuxi.interceptor.model.HookCallEntry;
import com.yuuxi.interceptor.util.Const;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InterceptorActivity extends AppCompatActivity {

    private TextView tvLogOutput;
    private ScrollView scrollView;
    private TextView tvHeader;
    private Button btnClear;
    private Button btnFilter;
    private Button btnExport;
    private SharedPreferences prefs;

    private ScheduledExecutorService autoRefresh;
    private Handler uiHandler;
    private int lastEntryCount = 0;
    private StringBuilder logBuffer = new StringBuilder();
    private int maxBufferSize = 50000;

    private BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String data = intent.getStringExtra(Const.EXTRA_LOG_JSON);
            if (data != null) {
                appendLogLine(data);
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
        setupAutoRefresh();
    }

    private void initViews() {
        tvHeader = findViewById(R.id.tv_header);
        tvLogOutput = findViewById(R.id.tv_log_output);
        scrollView = findViewById(R.id.scroll_log);
        btnClear = findViewById(R.id.btn_clear);
        btnFilter = findViewById(R.id.btn_filter);
        btnExport = findViewById(R.id.btn_export);

        tvLogOutput.setMovementMethod(new ScrollingMovementMethod());
        tvLogOutput.setTypeface(Typeface.MONOSPACE);
        tvLogOutput.setTextSize(11);

        String targetPkg = prefs.getString(Const.KEY_TARGET_PACKAGE, "unknown");
        tvHeader.setText("DexInterceptor | " + targetPkg);

        btnClear.setOnClickListener(v -> {
            logBuffer.setLength(0);
            tvLogOutput.setText("");
            lastEntryCount = 0;
            MethodCallLogger.clearEntries();
        });

        btnFilter.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        });

        btnExport.setOnClickListener(v -> exportLog());

        loadExistingLogs();
    }

    private void setupAutoRefresh() {
        autoRefresh = Executors.newSingleThreadScheduledExecutor();
        autoRefresh.scheduleAtFixedRate(() -> {
            List<HookCallEntry> entries = MethodCallLogger.getRecentEntries(200);
            if (entries.size() > lastEntryCount) {
                uiHandler.post(() -> {
                    for (int i = lastEntryCount; i < entries.size(); i++) {
                        appendEntry(entries.get(i));
                    }
                    lastEntryCount = entries.size();
                    scrollToBottom();
                });
            }
        }, 500, 1000, TimeUnit.MILLISECONDS);
    }

    private void loadExistingLogs() {
        List<HookCallEntry> entries = MethodCallLogger.getAllEntries();
        for (HookCallEntry entry : entries) {
            appendEntry(entry);
        }
        lastEntryCount = entries.size();
        scrollToBottom();
    }

    private void appendEntry(HookCallEntry entry) {
        String line = formatEntry(entry);
        if (line != null) {
            logBuffer.append(line).append("\n");
            if (logBuffer.length() > maxBufferSize) {
                logBuffer.delete(0, logBuffer.length() - maxBufferSize / 2);
            }
            tvLogOutput.setText(logBuffer.toString());
        }
    }

    private void appendLogLine(String raw) {
        uiHandler.post(() -> {
            logBuffer.append(raw).append("\n");
            if (logBuffer.length() > maxBufferSize) {
                logBuffer.delete(0, logBuffer.length() - maxBufferSize / 2);
            }
            tvLogOutput.setText(logBuffer.toString());
            scrollToBottom();
        });
    }

    private String formatEntry(HookCallEntry entry) {
        switch (entry.type) {
            case JAVA_CALL:
                return "\u001b[36m->\u001b[0m " + entry.className + "->" + entry.methodName + "(" + (entry.args != null ? entry.args : "") + ")";
            case JAVA_RETURN:
                return "\u001b[32m<-\u001b[0m " + entry.className + "->" + entry.methodName + " = " + (entry.result != null ? entry.result : "void") + (entry.elapsedUs > 0 ? " [" + entry.elapsedUs + "us]" : "") + (entry.threw ? " THREW!" : "");
            case NATIVE_CALL:
                return "\u001b[35m->\u001b[0m [" + entry.libName + "] " + entry.funcName + "() @0x" + Long.toHexString(entry.address);
            case NATIVE_RETURN:
                return "\u001b[35m<-\u001b[0m [" + entry.libName + "] " + entry.funcName + " = 0x" + Long.toHexString(entry.retval) + " [" + entry.elapsedUs + "us]";
            case NATIVE_LOAD:
                return "\u001b[33m[LOAD]\u001b[0m " + entry.libName + " (" + entry.message + ")";
            case SYSTEM:
                return "\u001b[37m[SYS]\u001b[0m " + entry.message;
            case ERROR:
                return "\u001b[31m[ERR]\u001b[0m " + entry.message;
            default:
                return null;
        }
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
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
            tvLogOutput.append("\n[EXPORT ERROR] " + t.getMessage() + "\n");
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
