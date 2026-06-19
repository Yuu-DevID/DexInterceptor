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
        sPrefs = new XSharedPreferences(Const.MY_PACKAGE, Const.PREFS_NAME);
        sPrefs.makeWorldReadable();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals(Const.MY_PACKAGE)) return;

        sPrefs.reload();

        String targetPackage = sPrefs.getString(Const.KEY_TARGET_PACKAGE, "");
        if (!targetPackage.isEmpty() && !lpparam.packageName.equals(targetPackage)) {
            return;
        }

        File logDir = new File(lpparam.appInfo.dataDir, "interceptor_logs");
        if (!logDir.exists()) logDir.mkdirs();

        MethodCallLogger.init(logDir, lpparam.packageName);
        MethodCallLogger.logSystem("=== DexInterceptor active on " + lpparam.packageName + " ===");

        initJavaHooks(lpparam);
        initNativeHooks(lpparam);
    }

    private void initJavaHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            MethodCallLogger.logSystem("Initializing Java hooks...");

            Set<String> targetClasses = getTargetClasses();
            for (String className : targetClasses) {
                JavaMethodHook.hookClass(className, lpparam.classLoader);
            }

            String wildcard = sPrefs.getString(Const.KEY_HOOK_ALL, "");
            if (wildcard.equals("*")) {
                JavaMethodHook.hookAllClasses(lpparam);
                MethodCallLogger.logSystem("Hook mode: ALL classes");
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
        sNativeEnabled = sPrefs.getBoolean(Const.KEY_ENABLE_NATIVE, true);
        if (!sNativeEnabled) return;

        try {
            MethodCallLogger.logSystem("Initializing native hooks...");
            NativeHookManager.init(lpparam);

            Set<String> targetLibs = getTargetLibs();
            for (String libName : targetLibs) {
                NativeHookManager.monitorLibrary(libName);
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
        String stored = sPrefs.getString(Const.KEY_TARGET_CLASSES, "");
        if (!stored.isEmpty()) {
            for (String c : stored.split("\\|")) {
                String trimmed = c.trim();
                if (!trimmed.isEmpty()) classes.add(trimmed);
            }
        }
        return classes;
    }

    private Set<String> getTargetLibs() {
        Set<String> libs = new LinkedHashSet<>();
        String stored = sPrefs.getString(Const.KEY_TARGET_LIBS, "");
        if (!stored.isEmpty()) {
            for (String l : stored.split("\\|")) {
                String trimmed = l.trim();
                if (!trimmed.isEmpty()) libs.add(trimmed);
            }
        }
        return libs;
    }
}
