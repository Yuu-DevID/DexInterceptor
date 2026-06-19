package com.yuuxi.interceptor.util;

public final class Const {
    public static final String MY_PACKAGE = "com.yuuxi.interceptor";
    public static final String PREFS_NAME = "DexInterceptorPrefs";

    public static final String KEY_TARGET_PACKAGE = "target_package";
    public static final String KEY_TARGET_CLASSES = "target_classes";
    public static final String KEY_TARGET_LIBS = "target_libs";
    public static final String KEY_HOOK_ALL = "hook_all";
    public static final String KEY_ENABLE_NATIVE = "enable_native";
    public static final String KEY_LOG_LEVEL = "log_level";
    public static final String KEY_MAX_LOG_ENTRIES = "max_log_entries";
    public static final String KEY_ENABLE_FLOATING = "enable_floating";

    public static final String ACTION_LOG_ENTRY = "com.yuuxi.interceptor.LOG_ENTRY";
    public static final String EXTRA_LOG_JSON = "log_json";

    public static final String LOG_DIR_NAME = "interceptor_logs";
    public static final String LOG_FILE_NAME = "calls.jsonl";

    public static final int LOG_LEVEL_ALL = 0;
    public static final int LOG_LEVEL_JAVA = 1;
    public static final int LOG_LEVEL_NATIVE = 2;
    public static final int LOG_LEVEL_SYSTEM = 3;

    public static final int MAX_LOG_ENTRIES_DEFAULT = 5000;

    public static final String PREF_FILE = "interceptor_prefs.xml";
}
