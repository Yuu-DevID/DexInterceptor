package com.yuuxi.interceptor;

import com.yuuxi.interceptor.hooks.NativeHookManager;
import com.yuuxi.interceptor.logger.MethodCallLogger;

public class NativeBridge {

    static {
        try {
            System.loadLibrary("interceptor_native");
        } catch (Throwable t) {
            MethodCallLogger.logError("Failed to load interceptor_native", t);
        }
    }

    public static native void nativeInit();
    public static native long[] nativeGetExportedFunctions(String libName);
    public static native int nativeHookFunction(String libName, long funcAddr);
    public static native void nativeLogCall(String libName, String funcName, long addr, String args);
    public static native void nativeLogReturn(String libName, String funcName, long retval, long elapsed);

    public static void init() {
        nativeInit();
    }

    public static long[] getExportedFunctions(String libName) {
        return nativeGetExportedFunctions(libName);
    }

    public static boolean hookFunction(String libName, long funcAddr) {
        return nativeHookFunction(libName, funcAddr) == 0;
    }

    public static void onNativeCall(String libName, String funcName, long addr, String args) {
        NativeHookManager.logNativeCall(libName, funcName, addr, args);
    }

    public static void onNativeReturn(String libName, String funcName, long retval, long elapsed) {
        NativeHookManager.logNativeReturn(libName, funcName, retval, elapsed);
    }
}
