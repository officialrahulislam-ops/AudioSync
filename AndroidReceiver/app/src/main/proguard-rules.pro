# AudioSync ProGuard rules
# Keep all classes for the receiver package
-keep public class com.audiosync.receiver.** { *; }
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View
-keep public class * extends android.widget.Adapter
-keep public class * extends android.widget.AdapterView
-keepclassmembers public class * extends android.view.View {
    <init>(android.content.Context, android.util.AttributeSet);
    <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Opus decoder classes
-keep class org.restcomm.media.core.codec.opus.** { *; }

# Keep Kotlin classes
-keep class kotlin.** { *; }

# Keep AudioTrack
-keep class android.media.AudioTrack { *; }
-keep class android.media.AudioManager { *; }

# Don't obfuscate
-dontobfuscate