# NewPipeExtractor (JavaScript engine + reflection)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# WebView JavaScript bridge used to obtain YouTube's PoToken
-keepclassmembers class com.gr4nt.itube.net.potoken.PoTokenWebView {
    @android.webkit.JavascriptInterface <methods>;
}

# Optional classes referenced by libraries but absent on Android
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-ignorewarnings

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
