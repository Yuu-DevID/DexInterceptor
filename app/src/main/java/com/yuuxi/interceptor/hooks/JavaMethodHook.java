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

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.yuuxi.interceptor.logger.MethodCallLogger;
import com.yuuxi.interceptor.util.Const;


public class JavaMethodHook {

    private static final String TAG = "JavaHook";

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
            String pkgPrefix = lpparam.packageName;

            XposedHelpers.findAndHookMethod(
                    "android.app.Instrumentation", cl,
                    "newActivity", Class.class, Context.class, IBinder.class,
                    Intent.class, Activity.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Activity activity = (Activity) param.getResult();
                            if (activity != null) {
                                String activityName = activity.getClass().getName();
                                try {
                                    Class<?> activityClass = activity.getClass();
                                    hookAllMethodsOfClass(activityClass, activityClass.getName());
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            MethodCallLogger.logError("hookAllClasses failed", t);
        }
    }

    public static void hookAllMethodsOfClass(Class<?> clazz, String displayName) {
        try {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (Modifier.isAbstract(method.getModifiers())) continue;
                if (Modifier.isNative(method.getModifiers())) continue;

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
            private final long startTime = System.nanoTime();

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String argTypes = buildArgTypes(param.args);
                String signature = className + "->" + methodName + "(" + argTypes + ")";

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

                sendLiveUpdate("CALL", signature, param.args);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                long elapsed = (System.nanoTime() - startTime) / 1000;
                String resultStr = param.getThrowable() != null
                        ? "ERR:" + param.getThrowable().getMessage()
                        : summarize(param.getResult());

                MethodCallLogger.logJavaReturn(
                        className,
                        methodName,
                        resultStr,
                        elapsed,
                        param.getThrowable() != null
                );

                if (param.getThrowable() != null) {
                    MethodCallLogger.logError(
                            className + "->" + methodName + " threw",
                            param.getThrowable()
                    );
                }
            }
        };
    }

    public static void hookActivityLifecycle(ClassLoader cl) {
        try {
            XC_MethodHook lifecycleHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;
                    String cls = activity.getClass().getSimpleName();
                    String method = param.method.getName();
                    MethodCallLogger.logSystem("[Lifecycle] " + cls + "." + method);
                }
            };

            String[] lifecycleMethods = {
                    "onCreate", "onStart", "onResume",
                    "onPause", "onStop", "onDestroy",
                    "onSaveInstanceState"
            };

            for (String m : lifecycleMethods) {
                try {
                    XposedHelpers.findAndHookMethod(
                            Activity.class, m,
                            Bundle.class,
                            lifecycleHook
                    );
                } catch (Throwable ignored) {}
            }

            XposedHelpers.findAndHookMethod(
                    Activity.class, "onCreate", Bundle.class,
                    lifecycleHook
            );
            XposedHelpers.findAndHookMethod(
                    Activity.class, "onResume",
                    lifecycleHook
            );
            XposedHelpers.findAndHookMethod(
                    Activity.class, "onPause",
                    lifecycleHook
            );
        } catch (Throwable t) {
            MethodCallLogger.logError("hookActivityLifecycle failed", t);
        }
    }

    public static void hookIntent(ClassLoader cl) {
        try {
            XC_MethodHook intentHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String method = param.method.getName();
                    String result = summarize(param.getResult());
                    MethodCallLogger.logSystem("[Intent] " + method + " -> " + result);
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
                    String method = param.method.getName();
                    MethodCallLogger.logSystem("[Bundle] " + method + " args=" + summarize(param.args));
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
                    String libName = (String) param.args[param.args.length - 1];
                    if (libName instanceof String) {
                        MethodCallLogger.logNativeLoad(libName, "Runtime.loadLibrary");
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
                    String libName = (String) param.args[0];
                    MethodCallLogger.logNativeLoad(libName, "System.loadLibrary");
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
        String s = obj.toString();
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
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
            MethodCallLogger.getContext().sendBroadcast(intent);
        } catch (Throwable ignored) {}
    }
}
