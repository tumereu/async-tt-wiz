# Shrink and optimize only: names stay readable in problem-report stack traces.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# ActivityPushConfiguration reaches the optional FCM adapter by name (fcm flavor only).
-keep class org.asyncttwiz.android.ActivityPushService {
    public static void configure(android.content.Context);
}

# WorkManager's Room 2.6.1 only ships "-keep class * extends RoomDatabase"; R8 full mode then drops the
# no-arg constructor Room.getGeneratedImplementation() calls reflectively (WorkDatabase_Impl).
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}

# Class names that end up in game data (problem report N7FU6Y): without these attributes a nested class's simpleName
# is "Outer$Inner" on the phone and "Inner" on the server. Game ids are written out where it matters; this keeps the
# rest (texts, logs) the same as on the JVM.
-keepattributes InnerClasses,EnclosingMethod
