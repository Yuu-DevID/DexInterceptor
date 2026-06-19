# Add project specific ProGuard rules here.
-keep class com.yuuxi.interceptor.hooks.HookEntry { *; }
-keep class com.yuuxi.interceptor.NativeBridge { *; }
-keep class com.yuuxi.interceptor.model.HookCallEntry { *; }
-keep class com.yuuxi.interceptor.hooks.JavaMethodHook { *; }
-keep class com.yuuxi.interceptor.hooks.NativeHookManager { *; }
-keep class com.yuuxi.interceptor.logger.MethodCallLogger { *; }

-keepattributes Signature
-keepattributes *Annotation*

-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }

-keepclassmembers class * {
    @de.robv.android.xposed.* <methods>;
}
