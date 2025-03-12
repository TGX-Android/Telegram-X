# There is a bug in R8, which causes app to crash
# during launch on Android 4.x with VerifyError.
#
# Last reproduced on:
# com.android.tools.build:gradle:8.3.0
# build tools version 34.0.0
# gradle-6.1.1
#
# Therefore all optimizations must be disabled for now.

-dontoptimize

# Below is an unsuccessful attempt to eliminate errors one by one.
# Even if the rules below would work, it's a bad idea,
# as new malformed class might appear at any time.

#-keep class androidx.media3.exoplayer.drm.DrmSession { *; }
#-keepclassmembers class androidx.media3.exoplayer.drm.DrmSession { *; }
#-keep class androidx.media3.effect.MatrixTransformation { *; }
#-keepclassmembers class androidx.media3.effect.MatrixTransformation { *; }
#-keep class androidx.media3.extractor.text.SubtitleParser { *; }
#-keepclassmembers class androidx.media3.extractor.text.SubtitleParser { *; }
#-keep class androidx.media3.exoplayer.RendererCapabilities { *; }
#-keepclassmembers class androidx.media3.exoplayer.RendererCapabilities { *; }
#-keep class androidx.work.NetworkType { *; }
#-keepclassmembers class androidx.work.NetworkType { *; }
#-keep class androidx.work.BackoffPolicy { *; }
#-keepclassmembers class androidx.work.BackoffPolicy { *; }
#-keep class androidx.work.NetworkType$* { *; }
#-keepclassmembers class androidx.work.NetworkType$* { *; }
#-keep class androidx.work.WorkInfo$State { *; }
#-keepclassmembers class androidx.work.WorkInfo$State { *; }
#-keep class androidx.media3.transformer.Transformer$Listener { *; }
#-keepclassmembers class androidx.media3.transformer.Transformer$Listener { *; }
#-keep class androidx.core.view.ViewKt$* { *; }
#-keepclassmembers class androidx.core.view.ViewKt$* { *; }
#-keep class androidx.core.os.BundleCompat$* { *; }
#-keepclassmembers class androidx.core.os.BundleCompat$* { *; }
## java.lang.VerifyError: *$$ExternalSyntheticOutline0
#-keep class androidx.collection.ArraySetKt$* { *; }
#-keepclassmembers class androidx.collection.ArraySetKt$* { *; }
#-keep class androidx.collection.ArraySetKt { *; }
#-keepclassmembers class androidx.collection.ArraySetKt { *; }
#-keep class androidx.core.net.UriCompat { *; }
#-keepclassmembers class androidx.core.net.UriCompat { *; }
#-keep class androidx.core.view.ViewGroupKt { *; }
#-keepclassmembers class androidx.core.view.ViewGroupKt { *; }
#-keep class androidx.core.math.MathUtils { *; }
#-keepclassmembers class androidx.core.math.MathUtils { *; }
#-keep class com.coremedia.iso.boxes.AlbumBox { *; }
#-keepclassmembers class com.coremedia.iso.boxes.AlbumBox { *; }
#-keep class com.coremedia.iso.boxes.AlbumBox$* { *; }
#-keepclassmembers class com.coremedia.iso.boxes.AlbumBox$* { *; }