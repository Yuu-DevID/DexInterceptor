package com.yuuxi.interceptor.hooks;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.yuuxi.interceptor.logger.MethodCallLogger;
import com.yuuxi.interceptor.util.Const;

public class HookEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    public static XSharedPreferences sPrefs;
    private static boolean sNativeEnabled = true;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        try {
            sPrefs = new XSharedPreferences(Const.MY_PACKAGE, Const.PREFS_NAME);
            sPrefs.makeWorldReadable();
        } catch (Throwable t) {
            // XSharedPreferences may fail on some ROMs — not critical
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals(Const.MY_PACKAGE)) return;

        // Safely reload prefs
        try {
            if (sPrefs != null) sPrefs.reload();
        } catch (Throwable ignored) {}

        String targetPackage = "";
        try {
            targetPackage = sPrefs.getString(Const.KEY_TARGET_PACKAGE, "");
        } catch (Throwable ignored) {}

        if (!targetPackage.isEmpty() && !lpparam.packageName.equals(targetPackage)) {
            return;
        }

        try {
            File logDir = new File(lpparam.appInfo.dataDir, "interceptor_logs");
            if (!logDir.exists()) logDir.mkdirs();

            MethodCallLogger.init(logDir, lpparam.packageName);
            MethodCallLogger.logSystem("=== DexInterceptor active on " + lpparam.packageName + " ===");

            initJavaHooks(lpparam);
            initNativeHooks(lpparam);
        } catch (Throwable t) {
            // NEVER let interceptor crash the target app
            try {
                android.util.Log.e("DexInterceptor", "handleLoadPackage failed", t);
            } catch (Throwable ignored) {}
        }
    }

    private void initJavaHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            MethodCallLogger.logSystem("Initializing Java hooks...");

            Set<String> targetClasses = getTargetClasses();
            for (String className : targetClasses) {
                try {
                    JavaMethodHook.hookClass(className, lpparam.classLoader);
                } catch (Throwable ignored) {}
            }

            String wildcard = "";
            try {
                wildcard = sPrefs.getString(Const.KEY_HOOK_ALL, "");
            } catch (Throwable ignored) {}

            if (wildcard.equals("*")) {
                JavaMethodHook.hookAllClasses(lpparam);
                MethodCallLogger.logSystem("Hook mode: Activity auto-hook");
            }

            JavaMethodHook.hookActivityLifecycle(lpparam.classLoader);
            JavaMethodHook.hookIntent(lpparam.classLoader);
            JavaMethodHook.hookBundle(lpparam.classLoader);

            MethodCallLogger.logSystem("Java hooks initialized successfully");
        } catch (Throwable t) {
            try {
                MethodCallLogger.logError("Java hook init failed", t);
            } catch (Throwable ignored) {}
        }
    }

    private void initNativeHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            sNativeEnabled = sPrefs.getBoolean(Const.KEY_ENABLE_NATIVE, true);
        } catch (Throwable ignored) {
            sNativeEnabled = false;
        }

        if (!sNativeEnabled) return;

        try {
            MethodCallLogger.logSystem("Initializing native hooks...");
            NativeHookManager.init(lpparam);

            Set<String> targetLibs = getTargetLibs();
            for (String libName : targetLibs) {
                try {
                    NativeHookManager.monitorLibrary(libName);
                } catch (Throwable ignored) {}
            }

            JavaMethodHook.hookRuntimeLoadLibrary(lpparam.classLoader);
            JavaMethodHook.hookSystemLoadLibrary(lpparam.classLoader);

            MethodCallLogger.logSystem("Native hooks initialized successfully");
        } catch (Throwable t) {
            // Don't let native hook failures crash the target app
            try {
                MethodCallLogger.logError("Native hook init failed (continuing without native hooks)", t);
            } catch (Throwable ignored) {}
        }
    }

    private Set<String> getTargetClasses() {
        Set<String> classes = new LinkedHashSet<>();
        try {
            String stored = sPrefs.getString(Const.KEY_TARGET_CLASSES, "");
            if (!stored.isEmpty()) {
                for (String c : stored.split("\\|")) {
                    String trimmed = c.trim();
                    if (!trimmed.isEmpty()) classes.add(trimmed);
                }
            }
        } catch (Throwable ignored) {}
        return classes;
    }

    private Set<String> getTargetLibs() {
        Set<String> libs = new LinkedHashSet<>();
        try {
            String stored = sPrefs.getString(Const.KEY_TARGET_LIBS, "");
            if (!stored.isEmpty()) {
                for (String l : stored.split("\\|")) {
                    String trimmed = l.trim();
                    if (!trimmed.isEmpty()) libs.add(trimmed);
                }
            }
        } catch (Throwable ignored) {}
        return libs;
    }
}
