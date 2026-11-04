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

# Class names that end up in game data (problem report N7FU6Y): without the InnerClasses attribute a nested class's
# simpleName is "Outer$Inner" on the phone and "Inner" on the server. R8 full mode keeps these attributes only for
# classes a -keep rule matches, so the engine's and protocol's classes still lose them (checked with dexdump on the
# personalTest build, 2026-11-04): anything a device and a server must agree on (ids, keys, choices) writes its names
# out (Decision.kind, Question.kind, Texts.costName); texts and logs drop the outer names themselves.
-keepattributes InnerClasses,EnclosingMethod
