-dontwarn io.github.libxposed.annotation.**
-dontwarn io.github.libxposed.api.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keep class com.allmightgamebooster.gusdev.xposed.** { *; }
