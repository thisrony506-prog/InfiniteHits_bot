# Bubble Blast - R8 / ProGuard rules
#
# The game is plain Android UI + a Canvas based engine, so only a handful of
# rules are required on top of proguard-android-optimize.txt.

# Custom views are instantiated by name from XML layouts.
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Entry points referenced from AndroidManifest.xml.
-keep class com.infinitehits.bubbleblast.ui.** { <init>(...); }
-keep class com.infinitehits.bubbleblast.BubbleBlastApp { <init>(...); }
-keep class com.infinitehits.bubbleblast.game.GameView { <init>(...); }
-keep class com.infinitehits.bubbleblast.ui.view.** { <init>(...); }

# Keep enums used through valueOf()/name() serialisation in the save system.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# WebView JS bridge for the bundled legal documents.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep line numbers for readable crash reports, hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
