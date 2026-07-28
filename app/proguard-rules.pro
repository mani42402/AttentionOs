# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }

# ONNX Runtime is reached through JNI, so its classes must keep their names.
-keep class ai.onnxruntime.** { *; }

# SQLCipher likewise calls back into Java from native code. The AAR ships consumer rules, but
# keeping these explicitly means a release build cannot silently lose the database layer — a
# failure that would appear only in release, after shipping.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**
