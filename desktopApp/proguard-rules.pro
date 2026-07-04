# Optional runtime integrations referenced by transitive libraries.
# These are not required for DroidShow desktop packaging/runtime.
-dontwarn org.objectweb.asm.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.slf4j.impl.**
-dontwarn sun.font.**
-dontwarn sun.misc.**

# JNA resolves native methods and library interface proxies by name at runtime.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * implements com.sun.jna.Library {
    *;
}
-dontwarn com.sun.jna.**
