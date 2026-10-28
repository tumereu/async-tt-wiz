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
