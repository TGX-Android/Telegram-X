# WebRTC

# Keep items annotated with @CalledByNative
-keep @org.webrtc.CalledByNative public class *
-keepclassmembers class * {
    @org.webrtc.CalledByNative *;
}

# Keep items annotated with @CalledByNativeUnchecked
-keep @org.webrtc.CalledByNativeUnchecked public class *
-keepclassmembers class * {
    @org.webrtc.CalledByNativeUnchecked *;
}

-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }