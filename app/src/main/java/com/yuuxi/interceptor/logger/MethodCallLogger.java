package com.yuuxi.interceptor.logger;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yuuxi.interceptor.model.HookCallEntry;
import com.yuuxi.interceptor.util.Const;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MethodCallLogger {

    private static final String TAG = "DexInterceptor";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static final int MAX_MEMORY_ENTRIES = 2000;

    private static File sLogDir;
    private static String sPackageName;
    private static BufferedWriter sWriter;
    private static final ConcurrentLinkedDeque<HookCallEntry> sMemoryLog = new ConcurrentLinkedDeque<>();
    private static final ExecutorService sIOExecutor = Executors.newSingleThreadExecutor();
    private static Context sContext;
    private static boolean sInitialized = false;

    public static void init(File logDir, String packageName) {
        sLogDir = logDir;
        sPackageName = packageName;
        sInitialized = true;

        try {
            File logFile = new File(logDir, Const.LOG_FILE_NAME);
            sWriter = new BufferedWriter(new FileWriter(logFile, true), 8192);
        } catch (IOException e) {
            Log.e(TAG, "Failed to open log file", e);
        }
    }

    public static void setContext(Context ctx) {
        sContext = ctx.getApplicationContext();
    }

    public static Context getContext() {
        return sContext;
    }

    public static void logJavaCall(String className, String methodName, String returnType, String args, int instanceId) {
        if (!sInitialized) return;

        HookCallEntry entry = HookCallEntry.javaCall(
                className, methodName, returnType, args, instanceId
        );
        addEntry(entry);
    }

    public static void logJavaReturn(String className, String methodName, String result, long elapsedNs, boolean threw) {
        if (!sInitialized) return;

        HookCallEntry entry = HookCallEntry.javaReturn(
                className, methodName, result, elapsedNs / 1000, threw
        );
        addEntry(entry);
    }

    public static void logNativeCall(String libName, String funcName, long address, String args) {
        if (!sInitialized) return;

        HookCallEntry entry = HookCallEntry.nativeCall(
                libName, funcName, address, args
        );
        addEntry(entry);
    }

    public static void logNativeReturn(String libName, String funcName, long retval, long elapsedUs) {
        if (!sInitialized) return;

        HookCallEntry entry = HookCallEntry.nativeReturn(
                libName, funcName, retval, elapsedUs
        );
        addEntry(entry);
    }

    public static void logNativeLoad(String libName, String trigger) {
        if (!sInitialized) return;

        HookCallEntry entry = HookCallEntry.nativeLoad(libName, trigger);
        addEntry(entry);
    }

    public static void logSystem(String message) {
        if (!sInitialized) return;

        HookCallEntry entry = HookCallEntry.system(message);
        addEntry(entry);
        Log.i(TAG, message);
    }

    public static void logError(String message, Throwable t) {
        if (!sInitialized) return;

        String fullMessage = message + (t != null ? ": " + t.getMessage() : "");
        HookCallEntry entry = HookCallEntry.error(fullMessage);
        addEntry(entry);
        Log.e(TAG, fullMessage, t);
    }

    private static void addEntry(HookCallEntry entry) {
        sMemoryLog.addLast(entry);
        while (sMemoryLog.size() > MAX_MEMORY_ENTRIES) {
            sMemoryLog.pollFirst();
        }

        sIOExecutor.execute(() -> {
            try {
                if (sWriter != null) {
                    sWriter.write(GSON.toJson(entry));
                    sWriter.newLine();
                    sWriter.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "Write failed", e);
            }
        });
    }

    public static List<HookCallEntry> getRecentEntries(int count) {
        List<HookCallEntry> list = new ArrayList<>(sMemoryLog);
        int size = list.size();
        int from = Math.max(0, size - count);
        return new ArrayList<>(list.subList(from, size));
    }

    public static List<HookCallEntry> getAllEntries() {
        return new ArrayList<>(sMemoryLog);
    }

    public static void clearEntries() {
        sMemoryLog.clear();
    }

    public static int getEntryCount() {
        return sMemoryLog.size();
    }

    public static File getLogFile() {
        return sLogDir != null ? new File(sLogDir, Const.LOG_FILE_NAME) : null;
    }

    public static void shutdown() {
        try {
            if (sWriter != null) {
                sWriter.flush();
                sWriter.close();
            }
        } catch (IOException ignored) {}
        sIOExecutor.shutdown();
    }

    public static String formatTimestamp(long timestamp) {
        return SDF.format(new Date(timestamp));
    }
}
