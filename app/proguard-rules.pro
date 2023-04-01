# == COMMON ==

# Keep native methods
-keepclasseswithmembers class * {
  native <methods>;
}
# Keep inflated classes
-keepclasseswithmembers class * {
  public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
  public <init>(android.content.Context, android.util.AttributeSet, int);
}
# Keep items annotated with @Keep
-keep @androidx.annotation.Keep public class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
# Keep metadata for crashes
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# == TELEGRAM X ==

# Keep native bridge
-keep class org.thunderdog.challegram.N { *; }
-keep class org.thunderdog.challegram.N$* { *; }
-keepclassmembers class org.thunderdog.challegram.N { *; }
# Keep log
-keep class org.thunderdog.challegram.Log
-keepclassmembers class org.thunderdog.challegram.Log { *; }
# Keep all related to VoIP
-keep class org.thunderdog.challegram.voip.**
-keepclassmembers class org.thunderdog.challegram.voip.** { *; }
# Keep sync services
-keep class org.thunderdog.challegram.sync.**

# == THIRDPARTY ==

# MP4Parser
-keep class * implements com.coremedia.iso.boxes.Box { *; }

# https://github.com/leolin310148/ShortcutBadger/blob/master/ShortcutBadger/proguard-rules.pro
-keep class me.leolin.shortcutbadger.impl.** {
  <init>(...);
}

# https://github.com/square/okhttp/blob/master/okhttp/src/main/resources/META-INF/proguard/okhttp3.pro
# https://github.com/square/okio/blob/master/okio/src/jvmMain/resources/META-INF/proguard/okio.pro
# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*
# OkHttp platform used only on JVM and when Conscrypt dependency is available.
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn org.conscrypt.ConscryptHostnameVerifier

# WebRTC

-keep class org.webrtc.Histogram { *; }
-keepclassmembers class org.webrtc.Histogram { *; }
-keep class org.webrtc.JniCommon { *; }
-keepclassmembers class org.webrtc.JniCommon { *; }
-keep class org.webrtc.NetworkChangeDetector { *; }
-keepclassmembers class org.webrtc.NetworkChangeDetector { *; }
-keep class org.webrtc.audio.WebRtcAudioManager { *; }
-keepclassmembers class org.webrtc.audio.WebRtcAudioManager { *; }
-keep class org.webrtc.NetworkChangeDetector$IPAddress { *; }
-keepclassmembers class org.webrtc.NetworkChangeDetector$IPAddress { *; }
-keep class org.webrtc.NetworkChangeDetector$NetworkInformation { *; }
-keepclassmembers class org.webrtc.NetworkChangeDetector$NetworkInformation { *; }
-keep class org.webrtc.NetworkMonitor { *; }
-keepclassmembers class org.webrtc.NetworkMonitor { *; }
-keep class org.webrtc.RefCounted { *; }
-keepclassmembers class org.webrtc.RefCounted { *; }
-keep class org.webrtc.BuiltinAudioDecoderFactoryFactory { *; }
-keepclassmembers class org.webrtc.BuiltinAudioDecoderFactoryFactory { *; }
-keep class org.webrtc.BuiltinAudioEncoderFactoryFactory { *; }
-keepclassmembers class org.webrtc.BuiltinAudioEncoderFactoryFactory { *; }
-keep class org.webrtc.audio.WebRtcAudioRecord { *; }
-keepclassmembers class org.webrtc.audio.WebRtcAudioRecord { *; }
-keep class org.webrtc.audio.WebRtcAudioTrack { *; }
-keepclassmembers class org.webrtc.audio.WebRtcAudioTrack { *; }
-keep class org.webrtc.audio.JavaAudioDeviceModule { *; }
-keepclassmembers class org.webrtc.audio.JavaAudioDeviceModule { *; }
-keep class org.webrtc.LibvpxVp8Decoder { *; }
-keepclassmembers class org.webrtc.LibvpxVp8Decoder { *; }
-keep class org.webrtc.LibvpxVp8Encoder { *; }
-keepclassmembers class org.webrtc.LibvpxVp8Encoder { *; }
-keep class org.webrtc.LibvpxVp9Decoder { *; }
-keepclassmembers class org.webrtc.LibvpxVp9Decoder { *; }
-keep class org.webrtc.LibvpxVp9Encoder { *; }
-keepclassmembers class org.webrtc.LibvpxVp9Encoder { *; }
-keep class org.webrtc.JNILogging { *; }
-keepclassmembers class org.webrtc.JNILogging { *; }
-keep class org.webrtc.Metrics { *; }
-keepclassmembers class org.webrtc.Metrics { *; }
-keep class org.webrtc.Metrics$HistogramInfo { *; }
-keepclassmembers class org.webrtc.Metrics$HistogramInfo { *; }
-keep class org.webrtc.JniHelper { *; }
-keepclassmembers class org.webrtc.JniHelper { *; }
-keep class org.webrtc.WebRtcClassLoader { *; }
-keepclassmembers class org.webrtc.WebRtcClassLoader { *; }
-keep class org.webrtc.AddIceObserver { *; }
-keepclassmembers class org.webrtc.AddIceObserver { *; }
-keep class org.webrtc.AudioTrack { *; }
-keepclassmembers class org.webrtc.AudioTrack { *; }
-keep class org.webrtc.CallSessionFileRotatingLogSink { *; }
-keepclassmembers class org.webrtc.CallSessionFileRotatingLogSink { *; }
-keep class org.webrtc.CandidatePairChangeEvent { *; }
-keepclassmembers class org.webrtc.CandidatePairChangeEvent { *; }
-keep class org.webrtc.CryptoOptions { *; }
-keepclassmembers class org.webrtc.CryptoOptions { *; }
-keep class org.webrtc.CryptoOptions$Srtp { *; }
-keepclassmembers class org.webrtc.CryptoOptions$Srtp { *; }
-keep class org.webrtc.CryptoOptions$SFrame { *; }
-keepclassmembers class org.webrtc.CryptoOptions$SFrame { *; }
-keep class org.webrtc.DataChannel { *; }
-keepclassmembers class org.webrtc.DataChannel { *; }
-keep class org.webrtc.DataChannel$Init { *; }
-keepclassmembers class org.webrtc.DataChannel$Init { *; }
-keep class org.webrtc.DataChannel$Buffer { *; }
-keepclassmembers class org.webrtc.DataChannel$Buffer { *; }
-keep class org.webrtc.DataChannel$Observer { *; }
-keepclassmembers class org.webrtc.DataChannel$Observer { *; }
-keep class org.webrtc.DataChannel$State { *; }
-keepclassmembers class org.webrtc.DataChannel$State { *; }
-keep class org.webrtc.DtmfSender { *; }
-keepclassmembers class org.webrtc.DtmfSender { *; }
-keep class org.webrtc.IceCandidate { *; }
-keepclassmembers class org.webrtc.IceCandidate { *; }
-keep class org.webrtc.IceCandidateErrorEvent { *; }
-keepclassmembers class org.webrtc.IceCandidateErrorEvent { *; }
-keep class org.webrtc.MediaConstraints { *; }
-keepclassmembers class org.webrtc.MediaConstraints { *; }
-keep class org.webrtc.MediaConstraints$KeyValuePair { *; }
-keepclassmembers class org.webrtc.MediaConstraints$KeyValuePair { *; }
-keep class org.webrtc.MediaSource { *; }
-keepclassmembers class org.webrtc.MediaSource { *; }
-keep class org.webrtc.MediaSource$State { *; }
-keepclassmembers class org.webrtc.MediaSource$State { *; }
-keep class org.webrtc.MediaStream { *; }
-keepclassmembers class org.webrtc.MediaStream { *; }
-keep class org.webrtc.MediaStreamTrack { *; }
-keepclassmembers class org.webrtc.MediaStreamTrack { *; }
-keep class org.webrtc.MediaStreamTrack$State { *; }
-keepclassmembers class org.webrtc.MediaStreamTrack$State { *; }
-keep class org.webrtc.MediaStreamTrack$MediaType { *; }
-keepclassmembers class org.webrtc.MediaStreamTrack$MediaType { *; }
-keep class org.webrtc.PeerConnection { *; }
-keepclassmembers class org.webrtc.PeerConnection { *; }
-keep class org.webrtc.PeerConnection$IceGatheringState { *; }
-keepclassmembers class org.webrtc.PeerConnection$IceGatheringState { *; }
-keep class org.webrtc.PeerConnection$IceConnectionState { *; }
-keepclassmembers class org.webrtc.PeerConnection$IceConnectionState { *; }
-keep class org.webrtc.PeerConnection$PeerConnectionState { *; }
-keepclassmembers class org.webrtc.PeerConnection$PeerConnectionState { *; }
-keep class org.webrtc.PeerConnection$SignalingState { *; }
-keepclassmembers class org.webrtc.PeerConnection$SignalingState { *; }
-keep class org.webrtc.PeerConnection$Observer { *; }
-keepclassmembers class org.webrtc.PeerConnection$Observer { *; }
-keep class org.webrtc.PeerConnection$IceServer { *; }
-keepclassmembers class org.webrtc.PeerConnection$IceServer { *; }
-keep class org.webrtc.PeerConnection$AdapterType { *; }
-keepclassmembers class org.webrtc.PeerConnection$AdapterType { *; }
-keep class org.webrtc.PeerConnection$RTCConfiguration { *; }
-keepclassmembers class org.webrtc.PeerConnection$RTCConfiguration { *; }
-keep class org.webrtc.PeerConnectionFactory { *; }
-keepclassmembers class org.webrtc.PeerConnectionFactory { *; }
-keep class org.webrtc.PeerConnectionFactory$Options { *; }
-keepclassmembers class org.webrtc.PeerConnectionFactory$Options { *; }
-keep class org.webrtc.RtcCertificatePem { *; }
-keepclassmembers class org.webrtc.RtcCertificatePem { *; }
-keep class org.webrtc.RTCStats { *; }
-keepclassmembers class org.webrtc.RTCStats { *; }
-keep class org.webrtc.RTCStatsCollectorCallback { *; }
-keepclassmembers class org.webrtc.RTCStatsCollectorCallback { *; }
-keep class org.webrtc.RTCStatsReport { *; }
-keepclassmembers class org.webrtc.RTCStatsReport { *; }
-keep class org.webrtc.RtpParameters { *; }
-keepclassmembers class org.webrtc.RtpParameters { *; }
-keep class org.webrtc.RtpParameters$DegradationPreference { *; }
-keepclassmembers class org.webrtc.RtpParameters$DegradationPreference { *; }
-keep class org.webrtc.RtpParameters$Encoding { *; }
-keepclassmembers class org.webrtc.RtpParameters$Encoding { *; }
-keep class org.webrtc.RtpParameters$Codec { *; }
-keepclassmembers class org.webrtc.RtpParameters$Codec { *; }
-keep class org.webrtc.RtpParameters$Rtcp { *; }
-keepclassmembers class org.webrtc.RtpParameters$Rtcp { *; }
-keep class org.webrtc.RtpParameters$HeaderExtension { *; }
-keepclassmembers class org.webrtc.RtpParameters$HeaderExtension { *; }
-keep class org.webrtc.RtpReceiver { *; }
-keepclassmembers class org.webrtc.RtpReceiver { *; }
-keep class org.webrtc.RtpReceiver$Observer { *; }
-keepclassmembers class org.webrtc.RtpReceiver$Observer { *; }
-keep class org.webrtc.RtpSender { *; }
-keepclassmembers class org.webrtc.RtpSender { *; }
-keep class org.webrtc.RtpTransceiver { *; }
-keepclassmembers class org.webrtc.RtpTransceiver { *; }
-keep class org.webrtc.RtpTransceiver$RtpTransceiverDirection { *; }
-keepclassmembers class org.webrtc.RtpTransceiver$RtpTransceiverDirection { *; }
-keep class org.webrtc.RtpTransceiver$RtpTransceiverInit { *; }
-keepclassmembers class org.webrtc.RtpTransceiver$RtpTransceiverInit { *; }
-keep class org.webrtc.SdpObserver { *; }
-keepclassmembers class org.webrtc.SdpObserver { *; }
-keep class org.webrtc.SessionDescription { *; }
-keepclassmembers class org.webrtc.SessionDescription { *; }
-keep class org.webrtc.SessionDescription$Type { *; }
-keepclassmembers class org.webrtc.SessionDescription$Type { *; }
-keep class org.webrtc.SSLCertificateVerifier { *; }
-keepclassmembers class org.webrtc.SSLCertificateVerifier { *; }
-keep class org.webrtc.StatsObserver { *; }
-keepclassmembers class org.webrtc.StatsObserver { *; }
-keep class org.webrtc.StatsReport { *; }
-keepclassmembers class org.webrtc.StatsReport { *; }
-keep class org.webrtc.StatsReport$Value { *; }
-keepclassmembers class org.webrtc.StatsReport$Value { *; }
-keep class org.webrtc.TurnCustomizer { *; }
-keepclassmembers class org.webrtc.TurnCustomizer { *; }
-keep class org.webrtc.EglBase10Impl { *; }
-keepclassmembers class org.webrtc.EglBase10Impl { *; }
-keep class org.webrtc.EncodedImage { *; }
-keepclassmembers class org.webrtc.EncodedImage { *; }
-keep class org.webrtc.EncodedImage$FrameType { *; }
-keepclassmembers class org.webrtc.EncodedImage$FrameType { *; }
-keep class org.webrtc.H264Utils { *; }
-keepclassmembers class org.webrtc.H264Utils { *; }
-keep class org.webrtc.JavaI420Buffer { *; }
-keepclassmembers class org.webrtc.JavaI420Buffer { *; }
-keep class org.webrtc.NativeAndroidVideoTrackSource { *; }
-keepclassmembers class org.webrtc.NativeAndroidVideoTrackSource { *; }
-keep class org.webrtc.NativeCapturerObserver { *; }
-keepclassmembers class org.webrtc.NativeCapturerObserver { *; }
-keep class org.webrtc.NV12Buffer { *; }
-keepclassmembers class org.webrtc.NV12Buffer { *; }
-keep class org.webrtc.NV21Buffer { *; }
-keepclassmembers class org.webrtc.NV21Buffer { *; }
-keep class org.webrtc.TimestampAligner { *; }
-keepclassmembers class org.webrtc.TimestampAligner { *; }
-keep class org.webrtc.VideoCodecInfo { *; }
-keepclassmembers class org.webrtc.VideoCodecInfo { *; }
-keep class org.webrtc.VideoCodecStatus { *; }
-keepclassmembers class org.webrtc.VideoCodecStatus { *; }
-keep class org.webrtc.VideoDecoder { *; }
-keepclassmembers class org.webrtc.VideoDecoder { *; }
-keep class org.webrtc.VideoDecoder$Settings { *; }
-keepclassmembers class org.webrtc.VideoDecoder$Settings { *; }
-keep class org.webrtc.VideoDecoderFactory { *; }
-keepclassmembers class org.webrtc.VideoDecoderFactory { *; }
-keep class org.webrtc.VideoDecoderFallback { *; }
-keepclassmembers class org.webrtc.VideoDecoderFallback { *; }
-keep class org.webrtc.VideoDecoderWrapper { *; }
-keepclassmembers class org.webrtc.VideoDecoderWrapper { *; }
-keep class org.webrtc.VideoEncoder { *; }
-keepclassmembers class org.webrtc.VideoEncoder { *; }
-keep class org.webrtc.VideoEncoder$Settings { *; }
-keepclassmembers class org.webrtc.VideoEncoder$Settings { *; }
-keep class org.webrtc.VideoEncoder$Capabilities { *; }
-keepclassmembers class org.webrtc.VideoEncoder$Capabilities { *; }
-keep class org.webrtc.VideoEncoder$EncodeInfo { *; }
-keepclassmembers class org.webrtc.VideoEncoder$EncodeInfo { *; }
-keep class org.webrtc.VideoEncoder$BitrateAllocation { *; }
-keepclassmembers class org.webrtc.VideoEncoder$BitrateAllocation { *; }
-keep class org.webrtc.VideoEncoder$ResolutionBitrateLimits { *; }
-keepclassmembers class org.webrtc.VideoEncoder$ResolutionBitrateLimits { *; }
-keep class org.webrtc.VideoEncoder$RateControlParameters { *; }
-keepclassmembers class org.webrtc.VideoEncoder$RateControlParameters { *; }
-keep class org.webrtc.VideoEncoder$EncoderInfo { *; }
-keepclassmembers class org.webrtc.VideoEncoder$EncoderInfo { *; }
-keep class org.webrtc.VideoEncoderFactory { *; }
-keepclassmembers class org.webrtc.VideoEncoderFactory { *; }
-keep class org.webrtc.VideoEncoderFactory$VideoEncoderSelector { *; }
-keepclassmembers class org.webrtc.VideoEncoderFactory$VideoEncoderSelector { *; }
-keep class org.webrtc.VideoEncoderFallback { *; }
-keepclassmembers class org.webrtc.VideoEncoderFallback { *; }
-keep class org.webrtc.VideoEncoderWrapper { *; }
-keepclassmembers class org.webrtc.VideoEncoderWrapper { *; }
-keep class org.webrtc.VideoFrame { *; }
-keepclassmembers class org.webrtc.VideoFrame { *; }
-keep class org.webrtc.VideoFrame$Buffer { *; }
-keepclassmembers class org.webrtc.VideoFrame$Buffer { *; }
-keep class org.webrtc.VideoFrame$I420Buffer { *; }
-keepclassmembers class org.webrtc.VideoFrame$I420Buffer { *; }
-keep class org.webrtc.VideoSink { *; }
-keepclassmembers class org.webrtc.VideoSink { *; }
-keep class org.webrtc.VideoTrack { *; }
-keepclassmembers class org.webrtc.VideoTrack { *; }
-keep class org.webrtc.WrappedNativeI420Buffer { *; }
-keepclassmembers class org.webrtc.WrappedNativeI420Buffer { *; }
-keep class org.webrtc.YuvHelper { *; }
-keepclassmembers class org.webrtc.YuvHelper { *; }

# WebRTC's native_unittests. Currently unused

#-keep class org.webrtc.ApplicationContextProvider { *; }
#-keepclassmembers class org.webrtc.ApplicationContextProvider { *; }
#-keep class org.webrtc.BuildInfo { *; }
#-keepclassmembers class org.webrtc.BuildInfo { *; }
#-keep class org.webrtc.CodecsWrapperTestHelper { *; }
#-keepclassmembers class org.webrtc.CodecsWrapperTestHelper { *; }
#-keep class org.webrtc.JavaTypesTestHelper { *; }
#-keepclassmembers class org.webrtc.JavaTypesTestHelper { *; }
#-keep class org.webrtc.JavaVideoSourceTestHelper { *; }
#-keepclassmembers class org.webrtc.JavaVideoSourceTestHelper { *; }
#-keep class org.webrtc.PeerConnectionFactoryInitializationHelper { *; }
#-keepclassmembers class org.webrtc.PeerConnectionFactoryInitializationHelper { *; }

# TODO remove once fixed in Android Gradle Plugin
-dontoptimize