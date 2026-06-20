package com.yuuxi.interceptor;

import com.yuuxi.interceptor.hooks.NativeHookManager;
import com.yuuxi.interceptor.logger.MethodCallLogger;

public class NativeBridge {
    private static boolean sLoaded = false;

    static {
        try {
            System.loadLibrary("interceptor_native");
            sLoaded = true;
        } catch (Throwable t) {
            // Native lib not available — that's OK, we'll run in Java-only mode
            sLoaded = false;
        }
    }

    public static native void nativeInit();
    public static native long[] nativeGetExportedFunctions(String libName);
    public static native int nativeHookFunction(String libName, long funcAddr);
    public static native void nativeLogCall(String libName, String funcName, long addr, String args);
    public static native void nativeLogReturn(String libName, String funcName, long retval, long elapsed);

    public static void init() {
        if (!sLoaded) return;
        try {
            nativeInit();
        } catch (Throwable t) {
            sLoaded = false;
        }
    }

    public static long[] getExportedFunctions(String libName) {
        if (!sLoaded) return null;
        try {
            return nativeGetExportedFunctions(libName);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean hookFunction(String libName, long funcAddr) {
        if (!sLoaded) return false;
        try {
            return nativeHookFunction(libName, funcAddr) == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void onNativeCall(String libName, String funcName, long addr, String args) {
        NativeHookManager.logNativeCall(libName, funcName, addr, args);
    }

    public static void onNativeReturn(String libName, String funcName, long retval, long elapsed) {
        NativeHookManager.logNativeReturn(libName, funcName, retval, elapsed);
    }
}
