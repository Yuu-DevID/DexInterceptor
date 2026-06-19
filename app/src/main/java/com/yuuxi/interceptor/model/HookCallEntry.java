package com.yuuxi.interceptor.model;

import com.google.gson.annotations.SerializedName;

public class HookCallEntry {

    public enum Type {
        JAVA_CALL,
        JAVA_RETURN,
        NATIVE_CALL,
        NATIVE_RETURN,
        NATIVE_LOAD,
        SYSTEM,
        ERROR
    }

    public enum Level {
        INFO,
        WARN,
        ERROR,
        DEBUG
    }

    @SerializedName("id")
    public long id;

    @SerializedName("timestamp")
    public long timestamp;

    @SerializedName("type")
    public Type type;

    @SerializedName("level")
    public Level level;

    @SerializedName("className")
    public String className;

    @SerializedName("methodName")
    public String methodName;

    @SerializedName("returnType")
    public String returnType;

    @SerializedName("args")
    public String args;

    @SerializedName("result")
    public String result;

    @SerializedName("libName")
    public String libName;

    @SerializedName("funcName")
    public String funcName;

    @SerializedName("address")
    public long address;

    @SerializedName("retval")
    public long retval;

    @SerializedName("elapsedUs")
    public long elapsedUs;

    @SerializedName("threw")
    public boolean threw;

    @SerializedName("message")
    public String message;

    @SerializedName("instanceId")
    public int instanceId;

    private static long sCounter = 0;

    private HookCallEntry() {
        this.id = ++sCounter;
        this.timestamp = System.currentTimeMillis();
    }

    public static HookCallEntry javaCall(String className, String methodName, String returnType, String args, int instanceId) {
        HookCallEntry e = new HookCallEntry();
        e.type = Type.JAVA_CALL;
        e.level = Level.INFO;
        e.className = className;
        e.methodName = methodName;
        e.returnType = returnType;
        e.args = args;
        e.instanceId = instanceId;
        return e;
    }

    public static HookCallEntry javaReturn(String className, String methodName, String result, long elapsedUs, boolean threw) {
        HookCallEntry e = new HookCallEntry();
        e.type = Type.JAVA_RETURN;
        e.level = threw ? Level.ERROR : Level.INFO;
        e.className = className;
        e.methodName = methodName;
        e.result = result;
        e.elapsedUs = elapsedUs;
        e.threw = threw;
        return e;
    }

    public static HookCallEntry nativeCall(String libName, String funcName, long address, String args) {
        HookCallEntry e = new HookCallEntry();
        e.type = Type.NATIVE_CALL;
        e.level = Level.INFO;
        e.libName = libName;
        e.funcName = funcName;
        e.address = address;
        e.args = args;
        return e;
    }

    public static HookCallEntry nativeReturn(String libName, String funcName, long retval, long elapsedUs) {
        HookCallEntry e = new HookCallEntry();
        e.type = Type.NATIVE_RETURN;
        e.level = Level.INFO;
        e.libName = libName;
        e.funcName = funcName;
        e.retval = retval;
        e.elapsedUs = elapsedUs;
        return e;
    }

    public static HookCallEntry nativeLoad(String libName, String trigger) {
        HookCallEntry e = new HookCallEntry();
        e.type = Type.NATIVE_LOAD;
        e.level = Level.INFO;
        e.libName = libName;
        e.message = "Loaded via " + trigger;
        return e;
    }

    public static HookCallEntry system(String message) {
        HookCallEntry e = new HookCallEntry();
        e.type = Type.SYSTEM;
        e.level = Level.INFO;
        e.message = message;
        return e;
    }

    public static HookCallEntry error(String message) {
        HookCallEntry e = new HookCallEntry();
        e.type = Type.ERROR;
        e.level = Level.ERROR;
        e.message = message;
        return e;
    }

    public String getDisplayTitle() {
        switch (type) {
            case JAVA_CALL:
                return "-> " + simpleClassName(className) + "->" + methodName + "()";
            case JAVA_RETURN:
                return "<- " + simpleClassName(className) + "->" + methodName + " = " + (result != null ? result : "void");
            case NATIVE_CALL:
                return "-> [" + libName + "] " + funcName + "()";
            case NATIVE_RETURN:
                return "<- [" + libName + "] " + funcName + " = 0x" + Long.toHexString(retval);
            case NATIVE_LOAD:
                return "[LOAD] " + libName;
            case SYSTEM:
                return "[SYS] " + message;
            case ERROR:
                return "[ERR] " + message;
            default:
                return "?";
        }
    }

    public String getDisplayDetail() {
        StringBuilder sb = new StringBuilder();
        if (args != null && !args.isEmpty()) {
            sb.append("args: ").append(args);
        }
        if (result != null && !result.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("ret: ").append(result);
        }
        if (address != 0) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("@0x").append(Long.toHexString(address));
        }
        if (elapsedUs > 0) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(elapsedUs).append("us");
        }
        return sb.toString();
    }

    private static String simpleClassName(String full) {
        if (full == null) return "?";
        int idx = full.lastIndexOf('.');
        return idx >= 0 ? full.substring(idx + 1) : full;
    }
}
