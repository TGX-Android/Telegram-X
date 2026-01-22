/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.transformer;

import static androidx.media3.common.ColorInfo.SDR_BT709_LIMITED;
import static androidx.media3.common.ColorInfo.isTransferHdr;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getAlternativeCodecMimeType;
import static androidx.media3.transformer.Composition.HDR_MODE_KEEP_HDR;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;
import static androidx.media3.transformer.EncoderUtil.getSupportedEncodersForHdrEditing;
import static java.lang.Math.round;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.extractor.metadata.mp4.SlowMotionData;
import androidx.media3.transformer.Composition.HdrMode;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.util.Objects;

/** Utility methods for Transformer. */
@UnstableApi
public final class TransformerUtil {

  private TransformerUtil() {}

  /**
   * Returns the {@link C.TrackType track type} constant corresponding to how a specified MIME type
   * should be processed, which may be {@link C#TRACK_TYPE_UNKNOWN} if it could not be determined.
   *
   * <p>{@linkplain MimeTypes#isImage Image} MIME types are processed as {@link C#TRACK_TYPE_VIDEO}.
   *
   * <p>See {@link MimeTypes#getTrackType} for more details.
   */
  public static @C.TrackType int getProcessedTrackType(@Nullable String mimeType) {
    @C.TrackType int trackType = MimeTypes.getTrackType(mimeType);
    return trackType == C.TRACK_TYPE_IMAGE ? C.TRACK_TYPE_VIDEO : trackType;
  }

  /** Returns {@link MediaCodec} flags corresponding to {@link C.BufferFlags}. */
  public static int getMediaCodecFlags(@C.BufferFlags int flags) {
    int mediaCodecFlags = 0;
    if ((flags & C.BUFFER_FLAG_KEY_FRAME) == C.BUFFER_FLAG_KEY_FRAME) {
      mediaCodecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
    }
    if ((flags & C.BUFFER_FLAG_END_OF_STREAM) == C.BUFFER_FLAG_END_OF_STREAM) {
      mediaCodecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
    }
    return mediaCodecFlags;
  }

  /** Returns whether the audio track should be transcoded. */
  public static boolean shouldTranscodeAudio(
      Format inputFormat,
      Composition composition,
      int sequenceIndex,
      TransformationRequest transformationRequest,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper) {
    if (composition.sequences.size() > 1
        || composition.sequences.get(sequenceIndex).editedMediaItems.size() > 1) {
      checkArgument(
          !composition.hasGaps() || !composition.transmuxAudio, "Gaps can not be transmuxed.");
      return !composition.transmuxAudio;
    }
    if (composition.hasGaps()) {
      return true;
    }
    if (encoderFactory.audioNeedsEncoding()) {
      return true;
    }
    if (transformationRequest.audioMimeType != null
        && !transformationRequest.audioMimeType.equals(inputFormat.sampleMimeType)) {
      return true;
    }
    if (transformationRequest.audioMimeType == null
        && !muxerWrapper.supportsSampleMimeType(inputFormat.sampleMimeType)) {
      return true;
    }
    EditedMediaItem firstEditedMediaItem =
        composition.sequences.get(sequenceIndex).editedMediaItems.get(0);
    if (firstEditedMediaItem.flattenForSlowMotion && containsSlowMotionData(inputFormat)) {
      return true;
    }
    if (!firstEditedMediaItem.effects.audioProcessors.isEmpty()) {
      return true;
    }
    if (!composition.effects.audioProcessors.isEmpty()) {
      return true;
    }
    return false;
  }

  /**
   * Returns whether the {@link Format} contains {@linkplain SlowMotionData slow motion metadata}.
   */
  private static boolean containsSlowMotionData(Format format) {
    @Nullable Metadata metadata = format.metadata;
    if (metadata == null) {
      return false;
    }
    for (int i = 0; i < metadata.length(); i++) {
      if (metadata.get(i) instanceof SlowMotionData) {
        return true;
      }
    }
    return false;
  }

  /** Returns whether the video track should be transcoded. */
  public static boolean shouldTranscodeVideo(
      Format inputFormat,
      Composition composition,
      int sequenceIndex,
      TransformationRequest transformationRequest,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper) {
    if (composition.sequences.size() > 1
        || composition.sequences.get(sequenceIndex).editedMediaItems.size() > 1) {
      return !composition.transmuxVideo;
    }
    if (encoderFactory.videoNeedsEncoding()) {
      return true;
    }
    if (transformationRequest.hdrMode != HDR_MODE_KEEP_HDR) {
      return true;
    }
    @Nullable String requestedMimeType = transformationRequest.videoMimeType;
    if (requestedMimeType != null) {
      boolean requestedMimeTypeEqualsPrimaryOrAlternativeMimeType =
          requestedMimeType.equals(inputFormat.sampleMimeType)
              || requestedMimeType.equals(getAlternativeCodecMimeType(inputFormat));
      if (!requestedMimeTypeEqualsPrimaryOrAlternativeMimeType) {
        return true;
      }
    }
    if (requestedMimeType == null
        && !muxerWrapper.supportsSampleMimeType(inputFormat.sampleMimeType)
        && !muxerWrapper.supportsSampleMimeType(getAlternativeCodecMimeType(inputFormat))) {
      return true;
    }
    if (inputFormat.pixelWidthHeightRatio != 1f) {
      return true;
    }
    EditedMediaItem firstEditedMediaItem =
        composition.sequences.get(sequenceIndex).editedMediaItems.get(0);
    ImmutableList<Effect> combinedEffects =
        new ImmutableList.Builder<Effect>()
            .addAll(firstEditedMediaItem.effects.videoEffects)
            .addAll(composition.effects.videoEffects)
            .build();
    return !combinedEffects.isEmpty()
        && maybeCalculateTotalRotationDegreesAppliedInEffects(combinedEffects, inputFormat) == -1;
  }

  /**
   * Returns the total rotation degrees of all the rotations in {@code videoEffects}, or {@code -1}
   * if {@code videoEffects} contains any effects that are not no-ops or regular rotations.
   *
   * <p>If all the {@code videoEffects} are either noOps or regular rotations, then the rotations
   * can be applied in the {@linkplain #maybeSetMuxerWrapperAdditionalRotationDegrees(MuxerWrapper,
   * ImmutableList, Format) MuxerWrapper}.
   */
  private static float maybeCalculateTotalRotationDegreesAppliedInEffects(
      ImmutableList<Effect> videoEffects, Format inputFormat) {
    int width = (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.width : inputFormat.height;
    int height = (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;
    float totalRotationDegrees = 0;
    for (int i = 0; i < videoEffects.size(); i++) {
      Effect videoEffect = videoEffects.get(i);
      if (!(videoEffect instanceof GlEffect)) {
        // We cannot confirm whether Effect instances that are not GlEffect instances are
        // no-ops.
        return -1;
      }
      GlEffect glEffect = (GlEffect) videoEffect;
      if (videoEffect instanceof ScaleAndRotateTransformation) {
        ScaleAndRotateTransformation scaleAndRotateTransformation =
            (ScaleAndRotateTransformation) videoEffect;
        if (scaleAndRotateTransformation.scaleX != 1f
            || scaleAndRotateTransformation.scaleY != 1f) {
          return -1;
        }
        float rotationDegrees = scaleAndRotateTransformation.rotationDegrees;
        if (rotationDegrees % 90f != 0) {
          return -1;
        }
        totalRotationDegrees += rotationDegrees;
        width = (totalRotationDegrees % 180 == 0) ? inputFormat.width : inputFormat.height;
        height = (totalRotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;
        continue;
      }
      if (!glEffect.isNoOp(width, height)) {
        return -1;
      }
    }
    totalRotationDegrees %= 360;
    return totalRotationDegrees % 90 == 0 ? totalRotationDegrees : -1;
  }

  /**
   * Sets {@linkplain MuxerWrapper#setAdditionalRotationDegrees(int) the additionalRotationDegrees}
   * on the given {@link MuxerWrapper} if the given {@code videoEffects} only contains a mix of
   * regular rotations and no-ops. A regular rotation is a rotation divisible by 90 degrees.
   */
  public static void maybeSetMuxerWrapperAdditionalRotationDegrees(
      MuxerWrapper muxerWrapper, ImmutableList<Effect> videoEffects, Format inputFormat) {
    float rotationDegrees =
        maybeCalculateTotalRotationDegreesAppliedInEffects(videoEffects, inputFormat);
    if (rotationDegrees == 90f || rotationDegrees == 180f || rotationDegrees == 270f) {
      // The MuxerWrapper rotation is clockwise while the ScaleAndRotateTransformation rotation
      // is counterclockwise.
      muxerWrapper.setAdditionalRotationDegrees(360 - round(rotationDegrees));
    }
  }

  /**
   * Adjust for invalid {@link ColorInfo} values, by defaulting to {@link
   * ColorInfo#SDR_BT709_LIMITED}.
   */
  public static ColorInfo getValidColor(@Nullable ColorInfo colorInfo) {
    if (colorInfo == null || !colorInfo.isDataSpaceValid()) {
      return ColorInfo.SDR_BT709_LIMITED;
    }
    return colorInfo;
  }

  /** Returns the decoder output color taking tone mapping into account. */
  public static ColorInfo getDecoderOutputColor(
      ColorInfo decoderInputColor, boolean isMediaCodecToneMappingRequested) {
    if (isMediaCodecToneMappingRequested && ColorInfo.isTransferHdr(decoderInputColor)) {
      return SDR_BT709_LIMITED;
    }
    return decoderInputColor;
  }

  /**
   * Calculate what the MIME type and {@link HdrMode} to use, applying fallback measure if
   * necessary.
   *
   * @param hdrMode The {@link HdrMode}.
   * @param requestedOutputMimeType The desired output MIME type.
   * @param colorInfo The {@link ColorInfo}.
   * @return a {@link Pair} of the output MIME type and {@link HdrMode}.
   */
  public static Pair<String, Integer> getOutputMimeTypeAndHdrModeAfterFallback(
      @HdrMode int hdrMode, String requestedOutputMimeType, @Nullable ColorInfo colorInfo) {
    // HdrMode fallback is only supported from HDR_MODE_KEEP_HDR to
    // HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL.
    if (hdrMode == HDR_MODE_KEEP_HDR && isTransferHdr(colorInfo)) {
      ImmutableList<MediaCodecInfo> hdrEncoders =
          getSupportedEncodersForHdrEditing(requestedOutputMimeType, colorInfo);
      if (hdrEncoders.isEmpty()) {
        // Fallback H.265/HEVC codecs for HDR content to avoid tonemapping.
        hdrEncoders = getSupportedEncodersForHdrEditing(MimeTypes.VIDEO_H265, colorInfo);
        if (!hdrEncoders.isEmpty()) {
          requestedOutputMimeType = MimeTypes.VIDEO_H265;
        } else {
          hdrMode = HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;
        }
      }
    }
    return Pair.create(requestedOutputMimeType, hdrMode);
  }

  /** Returns whether the provided {@link MediaItem} corresponds to an image. */
  public static boolean isImage(Context context, MediaItem mediaItem) {
    @Nullable String mimeType = getImageMimeType(context, mediaItem);
    return mimeType != null && MimeTypes.isImage(mimeType);
  }

  /**
   * Returns the image MIME type corresponding to a {@link MediaItem}.
   *
   * <p>This method only supports some common image MIME types.
   *
   * @param context The {@link Context}.
   * @param mediaItem The {@link MediaItem} to inspect.
   * @return The MIME type.
   */
  @Nullable
  public static String getImageMimeType(Context context, MediaItem mediaItem) {
    if (mediaItem.localConfiguration == null) {
      return null;
    }
    MediaItem.LocalConfiguration localConfiguration = mediaItem.localConfiguration;
    @Nullable String mimeType = localConfiguration.mimeType;
    if (mimeType == null) {
      if (Objects.equals(localConfiguration.uri.getScheme(), ContentResolver.SCHEME_CONTENT)) {
        ContentResolver cr = context.getContentResolver();
        mimeType = cr.getType(localConfiguration.uri);
      } else {
        @Nullable String uriPath = localConfiguration.uri.getPath();
        if (uriPath == null) {
          return null;
        }
        int fileExtensionStart = uriPath.lastIndexOf(".");
        if (fileExtensionStart >= 0 && fileExtensionStart < uriPath.length() - 1) {
          String extension = Ascii.toLowerCase(uriPath.substring(fileExtensionStart + 1));
          mimeType = getCommonImageMimeTypeFromExtension(extension);
        }
      }
    }
    return mimeType;
  }

  @Nullable
  private static String getCommonImageMimeTypeFromExtension(String extension) {
    switch (extension) {
      case "bmp":
      case "dib":
        return MimeTypes.IMAGE_BMP;
      case "heif":
        return MimeTypes.IMAGE_HEIF;
      case "heic":
        return MimeTypes.IMAGE_HEIC;
      case "jpg":
      case "jpeg":
      case "jpe":
      case "jif":
      case "jfif":
      case "jfi":
        return MimeTypes.IMAGE_JPEG;
      case "png":
        return MimeTypes.IMAGE_PNG;
      case "webp":
        return MimeTypes.IMAGE_WEBP;
      case "gif":
        return "image/gif";
      case "tiff":
      case "tif":
        return "image/tiff";
      case "raw":
      case "arw":
      case "cr2":
      case "k25":
        return "image/raw";
      case "svg":
      case "svgz":
        return "image/svg+xml";
      case "ico":
        return "image/x-icon";
      case "avif":
        return MimeTypes.IMAGE_AVIF;
      default:
        return null;
    }
  }
}
