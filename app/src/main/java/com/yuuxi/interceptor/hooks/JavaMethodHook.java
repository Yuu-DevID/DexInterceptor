package com.yuuxi.interceptor.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.yuuxi.interceptor.logger.MethodCallLogger;
import com.yuuxi.interceptor.util.Const;


public class JavaMethodHook {

    private static final String TAG = "JavaHook";

    // Recursion protection — prevents infinite loops when hooks call themselves
    private static final ThreadLocal<Integer> HOOK_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_HOOK_DEPTH = 50;

    // Methods to skip — these are called millions of times and cause crashes
    private static final Set<String> SKIP_METHODS = new HashSet<>(Arrays.asList(
        "toString", "hashCode", "equals", "getClass", "notify", "notifyAll",
        "wait", "clone", "finalize", "access$000", "access$100", "access$200",
        "access$300", "access$400", "access$500", "access$600", "access$700"
    ));

    // Packages to skip entirely — Android framework internals
    private static final Set<String> SKIP_PACKAGES = new HashSet<>(Arrays.asList(
        "android.os.", "android.view.", "android.widget.", "android.graphics.",
        "android.text.", "android.util.", "android.content.res.",
        "android.app.", "java.lang.", "java.util.", "java.io.",
        "java.net.", "java.nio.", "javax.", "sun.", "dalvik."
    ));

    public static void hookClass(String className, ClassLoader cl) {
        try {
            Class<?> targetClass = XposedHelpers.findClass(className, cl);
            hookAllMethodsOfClass(targetClass, className);
            MethodCallLogger.logSystem("Hooked class: " + className);
        } catch (Throwable t) {
            MethodCallLogger.logError("Failed to hook class: " + className, t);
        }
    }

    public static void hookAllClasses(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            ClassLoader cl = lpparam.classLoader;

            // Hook Instrumentation.newActivity to intercept Activity creation
            // This is SAFER than hooking ALL classes — only hooks when an Activity is created
            XposedHelpers.findAndHookMethod(
                    "android.app.Instrumentation", cl,
                    "newActivity", Class.class, Context.class, IBinder.class,
                    Intent.class, Activity.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Activity activity = (Activity) param.getResult();
                                if (activity != null) {
                                    Class<?> activityClass = activity.getClass();
                                    String name = activityClass.getName();

                                    // Skip framework classes
                                    if (!shouldSkipClass(name)) {
                                        hookAllMethodsOfClass(activityClass, name);
                                        MethodCallLogger.logSystem("Auto-hooked Activity: " + name);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
            );
        } catch (Throwable t) {
            MethodCallLogger.logError("hookAllClasses failed", t);
        }
    }

    private static boolean shouldSkipClass(String className) {
        for (String pkg : SKIP_PACKAGES) {
            if (className.startsWith(pkg)) return true;
        }
        return false;
    }

    private static boolean shouldSkipMethod(String methodName) {
        return SKIP_METHODS.contains(methodName);
    }

    public static void hookAllMethodsOfClass(Class<?> clazz, String displayName) {
        try {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (Modifier.isAbstract(method.getModifiers())) continue;
                if (Modifier.isNative(method.getModifiers())) continue;
                if (shouldSkipMethod(method.getName())) continue;
                if (shouldSkipClass(method.getDeclaringClass().getName())) continue;

                try {
                    XC_MethodHook hook = createMethodHook(displayName, method.getName());
                    XposedBridge.hookMethod(method, hook);
                } catch (Throwable ignored) {}
            }

            Constructor<?>[] constructors = clazz.getDeclaredConstructors();
            for (Constructor<?> ctor : constructors) {
                try {
                    XC_MethodHook hook = createMethodHook(displayName, "<init>");
                    XposedBridge.hookMethod(ctor, hook);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            MethodCallLogger.logError("hookAllMethodsOfClass failed: " + displayName, t);
        }
    }

    private static XC_MethodHook createMethodHook(String className, String methodName) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                // Recursion protection
                int depth = HOOK_DEPTH.get();
                if (depth > MAX_HOOK_DEPTH) return;
                HOOK_DEPTH.set(depth + 1);

                try {
                    String argTypes = buildArgTypes(param.args);
                    String returnType = "void";
                    if (param.method instanceof java.lang.reflect.Method) {
                        returnType = ((java.lang.reflect.Method) param.method).getReturnType().getSimpleName();
                    }

                    MethodCallLogger.logJavaCall(
                            className,
                            methodName,
                            returnType,
                            argTypes,
                            System.identityHashCode(param.thisObject)
                    );

                    String signature = className + "->" + methodName + "(" + argTypes + ")";
                    sendLiveUpdate("CALL", signature, param.args);
                } catch (Throwable ignored) {}
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    String resultStr = param.getThrowable() != null
                            ? "ERR:" + param.getThrowable().getMessage()
                            : summarize(param.getResult());

                    MethodCallLogger.logJavaReturn(
                            className,
                            methodName,
                            resultStr,
                            0,
                            param.getThrowable() != null
                    );

                    if (param.getThrowable() != null) {
                        MethodCallLogger.logError(
                                className + "->" + methodName + " threw",
                                param.getThrowable()
                        );
                    }
                } catch (Throwable ignored) {}
                finally {
                    // Decrement recursion depth
                    HOOK_DEPTH.set(Math.max(0, HOOK_DEPTH.get() - 1));
                }
            }
        };
    }

    public static void hookActivityLifecycle(ClassLoader cl) {
        try {
            XC_MethodHook lifecycleHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        int depth = HOOK_DEPTH.get();
                        if (depth > MAX_HOOK_DEPTH) return;
                        HOOK_DEPTH.set(depth + 1);

                        Activity activity = (Activity) param.thisObject;
                        String cls = activity.getClass().getSimpleName();
                        String method = param.method.getName();
                        MethodCallLogger.logSystem("[Lifecycle] " + cls + "." + method);
                    } catch (Throwable ignored) {}
                    finally {
                        HOOK_DEPTH.set(Math.max(0, HOOK_DEPTH.get() - 1));
                    }
                }
            };

            // Hook each lifecycle method individually — NO duplicates
            String[] lifecycleMethods = {
                    "onCreate", "onStart", "onResume",
                    "onPause", "onStop", "onDestroy",
                    "onSaveInstanceState"
            };

            for (String m : lifecycleMethods) {
                try {
                    if (m.equals("onCreate") || m.equals("onResume") || m.equals("onPause")) {
                        XposedHelpers.findAndHookMethod(
                                Activity.class, m, Bundle.class, lifecycleHook);
                    } else if (m.equals("onStart") || m.equals("onStop")) {
                        XposedHelpers.findAndHookMethod(
                                Activity.class, m, lifecycleHook);
                    } else if (m.equals("onDestroy")) {
                        XposedHelpers.findAndHookMethod(
                                Activity.class, m, lifecycleHook);
                    } else if (m.equals("onSaveInstanceState")) {
                        XposedHelpers.findAndHookMethod(
                                Activity.class, m, Bundle.class, lifecycleHook);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            MethodCallLogger.logError("hookActivityLifecycle failed", t);
        }
    }

    public static void hookIntent(ClassLoader cl) {
        try {
            XC_MethodHook intentHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        int depth = HOOK_DEPTH.get();
                        if (depth > MAX_HOOK_DEPTH) return;
                        HOOK_DEPTH.set(depth + 1);

                        String method = param.method.getName();
                        String result = summarize(param.getResult());
                        MethodCallLogger.logSystem("[Intent] " + method + " -> " + result);
                    } catch (Throwable ignored) {}
                    finally {
                        HOOK_DEPTH.set(Math.max(0, HOOK_DEPTH.get() - 1));
                    }
                }
            };

            XposedHelpers.findAndHookMethod(
                    Intent.class, "getStringExtra", String.class, intentHook);
            XposedHelpers.findAndHookMethod(
                    Intent.class, "getParcelableExtra", String.class, intentHook);
            XposedHelpers.findAndHookMethod(
                    Intent.class, "putExtra", String.class, String.class, intentHook);
        } catch (Throwable t) {}
    }

    public static void hookBundle(ClassLoader cl) {
        try {
            XC_MethodHook bundleHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        int depth = HOOK_DEPTH.get();
                        if (depth > MAX_HOOK_DEPTH) return;
                        HOOK_DEPTH.set(depth + 1);

                        String method = param.method.getName();
                        MethodCallLogger.logSystem("[Bundle] " + method + " args=" + summarize(param.args));
                    } catch (Throwable ignored) {}
                    finally {
                        HOOK_DEPTH.set(Math.max(0, HOOK_DEPTH.get() - 1));
                    }
                }
            };

            XposedHelpers.findAndHookMethod(
                    Bundle.class, "putString", String.class, String.class, bundleHook);
            XposedHelpers.findAndHookMethod(
                    Bundle.class, "getString", String.class, bundleHook);
            XposedHelpers.findAndHookMethod(
                    Bundle.class, "putSerializable", String.class, java.io.Serializable.class, bundleHook);
        } catch (Throwable t) {}
    }

    public static void hookRuntimeLoadLibrary(ClassLoader cl) {
        try {
            XC_MethodHook loadHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        int depth = HOOK_DEPTH.get();
                        if (depth > MAX_HOOK_DEPTH) return;
                        HOOK_DEPTH.set(depth + 1);

                        if (param.args == null || param.args.length == 0) return;
                        Object lastArg = param.args[param.args.length - 1];
                        if (lastArg instanceof String) {
                            String libName = (String) lastArg;
                            MethodCallLogger.logNativeLoad(libName, "Runtime.loadLibrary");
                        }
                    } catch (Throwable ignored) {}
                    finally {
                        HOOK_DEPTH.set(Math.max(0, HOOK_DEPTH.get() - 1));
                    }
                }
            };

            try {
                XposedHelpers.findAndHookMethod(
                        "java.lang.Runtime", cl,
                        "loadLibrary0", ClassLoader.class, String.class,
                        loadHook
                );
            } catch (Throwable ignored) {}

            try {
                XposedHelpers.findAndHookMethod(
                        "java.lang.Runtime", cl,
                        "loadLibrary", String.class,
                        loadHook
                );
            } catch (Throwable ignored) {}

        } catch (Throwable t) {}
    }

    public static void hookSystemLoadLibrary(ClassLoader cl) {
        try {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        int depth = HOOK_DEPTH.get();
                        if (depth > MAX_HOOK_DEPTH) return;
                        HOOK_DEPTH.set(depth + 1);

                        if (param.args == null || param.args.length == 0) return;
                        Object arg0 = param.args[0];
                        if (arg0 instanceof String) {
                            String libName = (String) arg0;
                            MethodCallLogger.logNativeLoad(libName, "System.loadLibrary");
                        }
                    } catch (Throwable ignored) {}
                    finally {
                        HOOK_DEPTH.set(Math.max(0, HOOK_DEPTH.get() - 1));
                    }
                }
            };

            XposedHelpers.findAndHookMethod(
                    System.class, "loadLibrary", String.class, hook);
            XposedHelpers.findAndHookMethod(
                    System.class, "load", String.class, hook);
        } catch (Throwable t) {}
    }

    private static String buildArgTypes(Object[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            if (args[i] == null) {
                sb.append("null");
            } else {
                sb.append(args[i].getClass().getSimpleName());
                sb.append("=");
                sb.append(summarize(args[i]));
            }
        }
        return sb.toString();
    }

    private static String summarize(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) {
            String s = (String) obj;
            return s.length() > 100 ? "\"" + s.substring(0, 100) + "...\"" : "\"" + s + "\"";
        }
        if (obj instanceof byte[]) {
            byte[] b = (byte[]) obj;
            return "byte[" + b.length + "]";
        }
        if (obj.getClass().isArray()) {
            return obj.getClass().getComponentType().getSimpleName() + "[" + java.lang.reflect.Array.getLength(obj) + "]";
        }
        try {
            String s = obj.toString();
            return s.length() > 200 ? s.substring(0, 200) + "..." : s;
        } catch (Throwable t) {
            return obj.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(obj));
        }
    }

    private static void sendLiveUpdate(String type, String signature, Object[] args) {
        try {
            String argsJson = "";
            if (args != null) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(summarize(args[i]));
                }
                sb.append("]");
                argsJson = sb.toString();
            }

            String entry = type + "|" + signature + "|" + argsJson;
            android.content.Intent intent = new android.content.Intent(Const.ACTION_LOG_ENTRY);
            intent.putExtra(Const.EXTRA_LOG_JSON, entry);
            intent.setPackage(Const.MY_PACKAGE);
            if (MethodCallLogger.getContext() != null) {
                MethodCallLogger.getContext().sendBroadcast(intent);
            }
        } catch (Throwable ignored) {}
    }
}
