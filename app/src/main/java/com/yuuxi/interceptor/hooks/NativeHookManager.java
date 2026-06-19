package com.yuuxi.interceptor.hooks;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.yuuxi.interceptor.logger.MethodCallLogger;

public class NativeHookManager {

    private static final String TAG = "NativeHookManager";
    private static final Set<String> monitoredLibs = new HashSet<>();
    private static final Set<String> hookedLibs = new HashSet<>();
    private static XC_LoadPackage.LoadPackageParam sLpparam;
    private static boolean sNativeLibLoaded = false;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam) {
        sLpparam = lpparam;
        try {
            System.loadLibrary("interceptor_native");
            sNativeLibLoaded = true;
            MethodCallLogger.logSystem("Native interceptor lib loaded");
        } catch (Throwable t) {
            MethodCallLogger.logError("Failed to load interceptor_native", t);
            sNativeLibLoaded = false;
        }
    }

    public static void monitorLibrary(String libName) {
        monitoredLibs.add(libName);
        MethodCallLogger.logSystem("Monitoring native library: " + libName);
    }

    public static void onLibraryLoaded(String libName, long handle) {
        if (hookedLibs.contains(libName)) return;

        if (monitoredLibs.isEmpty() || monitoredLibs.contains(libName)) {
            hookNativeLibrary(libName, handle);
            hookedLibs.add(libName);
        }
    }

    private static void hookNativeLibrary(String libName, long handle) {
        MethodCallLogger.logSystem("Native lib loaded: " + libName + " @ 0x" + Long.toHexString(handle));

        if (!sNativeLibLoaded) return;

        try {
            long[] exportedFuncs = getExportedFunctions(libName);
            if (exportedFuncs != null) {
                for (long funcAddr : exportedFuncs) {
                    hookNativeFunctionAt(libName, funcAddr);
                }
            }
        } catch (Throwable t) {
            MethodCallLogger.logError("hookNativeLibrary failed: " + libName, t);
        }
    }

    private static long[] getExportedFunctions(String libName) {
        try {
            Method m = Class.forName("com.yuuxi.interceptor.NativeBridge")
                    .getMethod("getExportedFunctions", String.class);
            Object result = m.invoke(null, libName);
            if (result instanceof long[]) {
                return (long[]) result;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void hookNativeFunctionAt(String libName, long funcAddr) {
        try {
            Method m = Class.forName("com.yuuxi.interceptor.NativeBridge")
                    .getMethod("hookFunction", String.class, long.class);
            m.invoke(null, libName, funcAddr);
        } catch (Throwable t) {
            MethodCallLogger.logError("hookNativeFunctionAt failed", t);
        }
    }

    public static void logNativeCall(String libName, String funcName, long address, String args) {
        MethodCallLogger.logNativeCall(libName, funcName, address, args);
    }

    public static void logNativeReturn(String libName, String funcName, long retval, long elapsedUs) {
        MethodCallLogger.logNativeReturn(libName, funcName, retval, elapsedUs);
    }

    public static Set<String> getMonitoredLibs() {
        return new HashSet<>(monitoredLibs);
    }

    public static Set<String> getHookedLibs() {
        return new HashSet<>(hookedLibs);
    }
}
