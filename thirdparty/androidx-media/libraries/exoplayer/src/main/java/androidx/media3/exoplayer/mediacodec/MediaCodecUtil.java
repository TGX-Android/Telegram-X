/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.exoplayer.mediacodec;

import static androidx.media3.common.util.CodecSpecificDataUtil.getHevcProfileAndLevel;
import static java.lang.Math.max;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;
import androidx.annotation.CheckResult;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.CodecSpecificDataUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.container.NalUnitUtil;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.InlineMe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** A utility class for querying the available codecs. */
@SuppressLint("InlinedApi")
@UnstableApi
public final class MediaCodecUtil {

  /**
   * Thrown when an error occurs querying the device for its underlying media capabilities.
   *
   * <p>Such failures are not expected in normal operation and are normally temporary (e.g. if the
   * mediaserver process has crashed and is yet to restart).
   */
  public static class DecoderQueryException extends Exception {

    private DecoderQueryException(Throwable cause) {
      super("Failed to query underlying media codecs", cause);
    }
  }

  private static final String TAG = "MediaCodecUtil";

  @GuardedBy("MediaCodecUtil.class")
  private static final HashMap<CodecKey, List<MediaCodecInfo>> decoderInfosCache = new HashMap<>();

  // Lazily initialized.
  private static int maxH264DecodableFrameSize = -1;

  private MediaCodecUtil() {}

  /**
   * Optional call to warm the codec cache for a given MIME type.
   *
   * <p>Calling this method may speed up subsequent calls to {@link #getDecoderInfo(String, boolean,
   * boolean)} and {@link #getDecoderInfos(String, boolean, boolean)}.
   *
   * @param mimeType The MIME type.
   * @param secure Whether the decoder is required to support secure decryption. Always pass false
   *     unless secure decryption really is required.
   * @param tunneling Whether the decoder is required to support tunneling. Always pass false unless
   *     tunneling really is required.
   */
  public static void warmDecoderInfoCache(String mimeType, boolean secure, boolean tunneling) {
    try {
      getDecoderInfos(mimeType, secure, tunneling);
    } catch (DecoderQueryException e) {
      // Codec warming is best effort, so we can swallow the exception.
      Log.e(TAG, "Codec warming failed", e);
    }
  }

  /* Clears the codec cache.*/
  @VisibleForTesting
  public static synchronized void clearDecoderInfoCache() {
    decoderInfosCache.clear();
  }

  /**
   * Returns information about a decoder that will only decrypt data, without decoding it.
   *
   * @return A {@link MediaCodecInfo} describing the decoder, or null if no suitable decoder exists.
   * @throws DecoderQueryException If there was an error querying the available decoders.
   */
  @Nullable
  public static MediaCodecInfo getDecryptOnlyDecoderInfo() throws DecoderQueryException {
    return getDecoderInfo(MimeTypes.AUDIO_RAW, /* secure= */ false, /* tunneling= */ false);
  }

  /**
   * Returns information about the preferred decoder for a given MIME type.
   *
   * @param mimeType The MIME type.
   * @param secure Whether the decoder is required to support secure decryption. Always pass false
   *     unless secure decryption really is required.
   * @param tunneling Whether the decoder is required to support tunneling. Always pass false unless
   *     tunneling really is required.
   * @return A {@link MediaCodecInfo} describing the decoder, or null if no suitable decoder exists.
   * @throws DecoderQueryException If there was an error querying the available decoders.
   */
  @Nullable
  public static MediaCodecInfo getDecoderInfo(String mimeType, boolean secure, boolean tunneling)
      throws DecoderQueryException {
    List<MediaCodecInfo> decoderInfos = getDecoderInfos(mimeType, secure, tunneling);
    return decoderInfos.isEmpty() ? null : decoderInfos.get(0);
  }

  /**
   * Returns all {@link MediaCodecInfo}s for the given MIME type, in the order given by {@link
   * MediaCodecList}.
   *
   * @param mimeType The MIME type.
   * @param secure Whether the decoder is required to support secure decryption. Always pass false
   *     unless secure decryption really is required.
   * @param tunneling Whether the decoder is required to support tunneling. Always pass false unless
   *     tunneling really is required.
   * @return An unmodifiable list of all {@link MediaCodecInfo}s for the given MIME type, in the
   *     order given by {@link MediaCodecList}.
   * @throws DecoderQueryException If there was an error querying the available decoders.
   */
  public static synchronized List<MediaCodecInfo> getDecoderInfos(
      String mimeType, boolean secure, boolean tunneling) throws DecoderQueryException {
    CodecKey key = new CodecKey(mimeType, secure, tunneling);
    @Nullable List<MediaCodecInfo> cachedDecoderInfos = decoderInfosCache.get(key);
    if (cachedDecoderInfos != null) {
      return cachedDecoderInfos;
    }

    // MV-HEVC is handled by a special codec in the media_codecs.xml file.  We need to get
    // ALL_CODECS list to include the special codec.
    boolean specialCodec = mimeType.equals(MimeTypes.VIDEO_MV_HEVC);
    MediaCodecListCompat mediaCodecList =
        new MediaCodecListCompatV21(secure, tunneling, specialCodec);
    ArrayList<MediaCodecInfo> decoderInfos = getDecoderInfosInternal(key, mediaCodecList);
    if (secure && decoderInfos.isEmpty() && Util.SDK_INT <= 23) {
      // Some devices don't list secure decoders on API level 21 [Internal: b/18678462]. Try the
      // legacy path. We also try this path on API levels 22 and 23 as a defensive measure.
      mediaCodecList = new MediaCodecListCompatV16();
      decoderInfos = getDecoderInfosInternal(key, mediaCodecList);
      if (!decoderInfos.isEmpty()) {
        Log.w(
            TAG,
            "MediaCodecList API didn't list secure decoder for: "
                + mimeType
                + ". Assuming: "
                + decoderInfos.get(0).name);
      }
    }
    applyWorkarounds(mimeType, decoderInfos);
    ImmutableList<MediaCodecInfo> immutableDecoderInfos = ImmutableList.copyOf(decoderInfos);
    decoderInfosCache.put(key, immutableDecoderInfos);
    return immutableDecoderInfos;
  }

  /**
   * Returns a list of decoders that can decode media in the specified format, in the priority order
   * specified by the {@link MediaCodecSelector}.
   *
   * <p>Since the {@link MediaCodecSelector} only has access to {@link Format#sampleMimeType}, the
   * list is not ordered to account for whether each decoder supports the details of the format
   * (e.g., taking into account the format's profile, level, resolution and so on). {@link
   * #getDecoderInfosSortedByFormatSupport} can be used to further sort the list into an order where
   * decoders that fully support the format come first.
   *
   * <p>This list is more complete than {@link #getDecoderInfos}, as it also considers alternative
   * MIME types that are a close match using {@link #getAlternativeCodecMimeType}.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param format The {@link Format} for which a decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @param requiresTunnelingDecoder Whether a tunneling decoder is required.
   * @return A list of {@link MediaCodecInfo}s corresponding to decoders. May be empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  @RequiresNonNull("#2.sampleMimeType")
  public static List<MediaCodecInfo> getDecoderInfosSoftMatch(
      MediaCodecSelector mediaCodecSelector,
      Format format,
      boolean requiresSecureDecoder,
      boolean requiresTunnelingDecoder)
      throws DecoderQueryException {
    List<MediaCodecInfo> decoderInfos =
        mediaCodecSelector.getDecoderInfos(
            format.sampleMimeType, requiresSecureDecoder, requiresTunnelingDecoder);
    List<MediaCodecInfo> alternativeDecoderInfos =
        getAlternativeDecoderInfos(
            mediaCodecSelector, format, requiresSecureDecoder, requiresTunnelingDecoder);
    return ImmutableList.<MediaCodecInfo>builder()
        .addAll(decoderInfos)
        .addAll(alternativeDecoderInfos)
        .build();
  }

  /**
   * Returns a list of decoders for {@linkplain #getAlternativeCodecMimeType alternative MIME types}
   * that can decode samples of the provided {@link Format}, in the priority order specified by the
   * {@link MediaCodecSelector}.
   *
   * <p>Since the {@link MediaCodecSelector} only has access to {@link Format#sampleMimeType}, the
   * list is not ordered to account for whether each decoder supports the details of the format
   * (e.g., taking into account the format's profile, level, resolution and so on). {@link
   * #getDecoderInfosSortedByFormatSupport} can be used to further sort the list into an order where
   * decoders that fully support the format come first.
   *
   * @param mediaCodecSelector The decoder selector.
   * @param format The {@link Format} for which an alternative decoder is required.
   * @param requiresSecureDecoder Whether a secure decoder is required.
   * @param requiresTunnelingDecoder Whether a tunneling decoder is required.
   * @return A list of {@link MediaCodecInfo}s corresponding to alternative decoders. May be empty.
   * @throws DecoderQueryException Thrown if there was an error querying decoders.
   */
  public static List<MediaCodecInfo> getAlternativeDecoderInfos(
      MediaCodecSelector mediaCodecSelector,
      Format format,
      boolean requiresSecureDecoder,
      boolean requiresTunnelingDecoder)
      throws DecoderQueryException {
    @Nullable String alternativeMimeType = getAlternativeCodecMimeType(format);
    if (alternativeMimeType == null) {
      return ImmutableList.of();
    }
    return mediaCodecSelector.getDecoderInfos(
        alternativeMimeType, requiresSecureDecoder, requiresTunnelingDecoder);
  }

  /**
   * Returns a copy of the provided decoder list sorted such that decoders with functional format
   * support are listed first. The returned list is modifiable for convenience.
   */
  @CheckResult
  public static List<MediaCodecInfo> getDecoderInfosSortedByFormatSupport(
      List<MediaCodecInfo> decoderInfos, Format format) {
    decoderInfos = new ArrayList<>(decoderInfos);
    sortByScore(
        decoderInfos, decoderInfo -> decoderInfo.isFormatFunctionallySupported(format) ? 1 : 0);
    return decoderInfos;
  }

  /**
   * Returns a copy of the provided decoder list sorted such that decoders with complete format
   * support are listed first. The returned list is modifiable for convenience.
   */
  @CheckResult
  public static List<MediaCodecInfo> getDecoderInfosSortedByFullFormatSupport(
      List<MediaCodecInfo> decoderInfos, Format format) {
    decoderInfos = new ArrayList<>(decoderInfos);
    sortByScore(
        decoderInfos,
        decoderInfo -> {
          try {
            return decoderInfo.isFormatSupported(format) ? 1 : 0;
          } catch (DecoderQueryException e) {
            return -1;
          }
        });
    return decoderInfos;
  }

  /**
   * Returns a copy of the provided decoder list sorted such that software decoders are listed
   * first. Break ties by listing non-{@link MediaCodecInfo#vendor} decoders first, due to issues
   * with decoder reuse with some software vendor codecs. See b/382447848.
   *
   * <p>The returned list is not modifiable.
   */
  @CheckResult
  public static List<MediaCodecInfo> getDecoderInfosSortedBySoftwareOnly(
      List<MediaCodecInfo> decoderInfos) {
    decoderInfos = new ArrayList<>(decoderInfos);
    sortByScore(
        decoderInfos,
        decoderInfo -> (decoderInfo.softwareOnly ? 2 : 0) + (decoderInfo.vendor ? 0 : 1));
    return ImmutableList.copyOf(decoderInfos);
  }

  /**
   * Returns the maximum frame size supported by the default H264 decoder.
   *
   * @return The maximum frame size for an H264 stream that can be decoded on the device.
   */
  public static int maxH264DecodableFrameSize() throws DecoderQueryException {
    if (maxH264DecodableFrameSize == -1) {
      int result = 0;
      @Nullable
      MediaCodecInfo decoderInfo =
          getDecoderInfo(MimeTypes.VIDEO_H264, /* secure= */ false, /* tunneling= */ false);
      if (decoderInfo != null) {
        for (CodecProfileLevel profileLevel : decoderInfo.getProfileLevels()) {
          result = max(avcLevelToMaxFrameSize(profileLevel.level), result);
        }
        // We assume support for at least 480p, which is the level mandated by the Android CDD.
        result = max(result, 720 * 480);
      }
      maxH264DecodableFrameSize = result;
    }
    return maxH264DecodableFrameSize;
  }

  /**
   * @deprecated Use {@link CodecSpecificDataUtil#getCodecProfileAndLevel(Format)}.
   */
  @InlineMe(
      replacement = "CodecSpecificDataUtil.getCodecProfileAndLevel(format)",
      imports = {"androidx.media3.common.util.CodecSpecificDataUtil"})
  @Deprecated
  @Nullable
  public static Pair<Integer, Integer> getCodecProfileAndLevel(Format format) {
    return CodecSpecificDataUtil.getCodecProfileAndLevel(format);
  }

  /**
   * Returns profile and level (as defined by {@link CodecProfileLevel}) corresponding to the base
   * layer (for the case of falling back to single-layer HEVC from L-HEVC).
   *
   * @param format Media format with codec specific initialization data.
   * @return A pair (profile constant, level constant) if the initializationData of the {@code
   *     format} is well-formed and recognized, or null otherwise.
   */
  @Nullable
  public static Pair<Integer, Integer> getHevcBaseLayerCodecProfileAndLevel(Format format) {
    String codecs = NalUnitUtil.getH265BaseLayerCodecsString(format.initializationData);
    if (codecs == null) {
      return null;
    }
    String[] parts = Util.split(codecs.trim(), "\\.");
    return getHevcProfileAndLevel(codecs, parts, format.colorInfo);
  }

  /**
   * Returns an alternative codec MIME type (besides the default {@link Format#sampleMimeType}) that
   * can be used to decode samples of the provided {@link Format}.
   *
   * @param format The media format.
   * @return An alternative MIME type of a codec that be used decode samples of the provided {@code
   *     Format} (besides the default {@link Format#sampleMimeType}), or null if no such alternative
   *     exists.
   */
  @Nullable
  public static String getAlternativeCodecMimeType(Format format) {
    if (MimeTypes.AUDIO_E_AC3_JOC.equals(format.sampleMimeType)) {
      // E-AC3 decoders can decode JOC streams, but in 2-D rather than 3-D.
      return MimeTypes.AUDIO_E_AC3;
    }
    if (MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)) {
      // H.264/AVC, H.265/HEVC or AV1 decoders can decode the base layer of some DV profiles.
      // This can't be done for profile CodecProfileLevel.DolbyVisionProfileDvheStn and profile
      // CodecProfileLevel.DolbyVisionProfileDvheDtb because the first one is not backward
      // compatible and the second one is deprecated and is not always backward compatible.
      @Nullable Pair<Integer, Integer> codecProfileAndLevel = getCodecProfileAndLevel(format);
      if (codecProfileAndLevel != null) {
        int profile = codecProfileAndLevel.first;
        if (profile == CodecProfileLevel.DolbyVisionProfileDvheDtr
            || profile == CodecProfileLevel.DolbyVisionProfileDvheSt) {
          return MimeTypes.VIDEO_H265;
        } else if (profile == CodecProfileLevel.DolbyVisionProfileDvavSe) {
          return MimeTypes.VIDEO_H264;
        } else if (profile == CodecProfileLevel.DolbyVisionProfileDvav110) {
          return MimeTypes.VIDEO_AV1;
        }
      }
    }
    if (MimeTypes.VIDEO_MV_HEVC.equals(format.sampleMimeType)) {
      // Single-layer HEVC decoders can decode the base layer of MV-HEVC streams.
      return MimeTypes.VIDEO_H265;
    }
    return null;
  }

  // Internal methods.

  /**
   * Returns {@link MediaCodecInfo}s for the given codec {@link CodecKey} in the order given by
   * {@code mediaCodecList}.
   *
   * @param key The codec key.
   * @param mediaCodecList The codec list.
   * @return The codec information for usable codecs matching the specified key.
   * @throws DecoderQueryException If there was an error querying the available decoders.
   */
  private static ArrayList<MediaCodecInfo> getDecoderInfosInternal(
      CodecKey key, MediaCodecListCompat mediaCodecList) throws DecoderQueryException {
    try {
      ArrayList<MediaCodecInfo> decoderInfos = new ArrayList<>();
      String mimeType = key.mimeType;
      int numberOfCodecs = mediaCodecList.getCodecCount();
      boolean secureDecodersExplicit = mediaCodecList.secureDecodersExplicit();
      // Note: MediaCodecList is sorted by the framework such that the best decoders come first.
      for (int i = 0; i < numberOfCodecs; i++) {
        android.media.MediaCodecInfo codecInfo = mediaCodecList.getCodecInfoAt(i);
        if (isAlias(codecInfo)) {
          // Skip aliases of other codecs, since they will also be listed under their canonical
          // names.
          continue;
        }
        String name = codecInfo.getName();
        if (!isCodecUsableDecoder(codecInfo, name, secureDecodersExplicit, mimeType)) {
          continue;
        }
        @Nullable String codecMimeType = getCodecMimeType(codecInfo, name, mimeType);
        if (codecMimeType == null) {
          continue;
        }
        try {
          CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(codecMimeType);
          boolean tunnelingSupported =
              mediaCodecList.isFeatureSupported(
                  CodecCapabilities.FEATURE_TunneledPlayback, codecMimeType, capabilities);
          boolean tunnelingRequired =
              mediaCodecList.isFeatureRequired(
                  CodecCapabilities.FEATURE_TunneledPlayback, codecMimeType, capabilities);
          if ((!key.tunneling && tunnelingRequired) || (key.tunneling && !tunnelingSupported)) {
            continue;
          }
          boolean secureSupported =
              mediaCodecList.isFeatureSupported(
                  CodecCapabilities.FEATURE_SecurePlayback, codecMimeType, capabilities);
          boolean secureRequired =
              mediaCodecList.isFeatureRequired(
                  CodecCapabilities.FEATURE_SecurePlayback, codecMimeType, capabilities);
          if ((!key.secure && secureRequired) || (key.secure && !secureSupported)) {
            continue;
          }
          boolean hardwareAccelerated = isHardwareAccelerated(codecInfo, mimeType);
          boolean softwareOnly = isSoftwareOnly(codecInfo, mimeType);
          boolean vendor = isVendor(codecInfo);
          if ((secureDecodersExplicit && key.secure == secureSupported)
              || (!secureDecodersExplicit && !key.secure)) {
            decoderInfos.add(
                MediaCodecInfo.newInstance(
                    name,
                    mimeType,
                    codecMimeType,
                    capabilities,
                    hardwareAccelerated,
                    softwareOnly,
                    vendor,
                    /* forceDisableAdaptive= */ false,
                    /* forceSecure= */ false));
          } else if (!secureDecodersExplicit && secureSupported) {
            decoderInfos.add(
                MediaCodecInfo.newInstance(
                    name + ".secure",
                    mimeType,
                    codecMimeType,
                    capabilities,
                    hardwareAccelerated,
                    softwareOnly,
                    vendor,
                    /* forceDisableAdaptive= */ false,
                    /* forceSecure= */ true));
            // It only makes sense to have one synthesized secure decoder, return immediately.
            return decoderInfos;
          }
        } catch (Exception e) {
          if (Util.SDK_INT <= 23 && !decoderInfos.isEmpty()) {
            // Suppress error querying secondary codec capabilities up to API level 23.
            Log.e(TAG, "Skipping codec " + name + " (failed to query capabilities)");
          } else {
            // Rethrow error querying primary codec capabilities, or secondary codec
            // capabilities if API level is greater than 23.
            Log.e(TAG, "Failed to query codec " + name + " (" + codecMimeType + ")");
            throw e;
          }
        }
      }
      return decoderInfos;
    } catch (Exception e) {
      // If the underlying mediaserver is in a bad state, we may catch an IllegalStateException
      // or an IllegalArgumentException here.
      throw new DecoderQueryException(e);
    }
  }

  /**
   * Returns the codec's supported MIME type for media of type {@code mimeType}, or {@code null} if
   * the codec can't be used.
   *
   * @param info The codec information.
   * @param name The name of the codec
   * @param mimeType The MIME type.
   * @return The codec's supported MIME type for media of type {@code mimeType}, or {@code null} if
   *     the codec can't be used. If non-null, the returned type will be equal to {@code mimeType}
   *     except in cases where the codec is known to use a non-standard MIME type alias.
   */
  @Nullable
  private static String getCodecMimeType(
      android.media.MediaCodecInfo info, String name, String mimeType) {
    String[] supportedTypes = info.getSupportedTypes();
    for (String supportedType : supportedTypes) {
      if (supportedType.equalsIgnoreCase(mimeType)) {
        return supportedType;
      }
    }

    if (mimeType.equals(MimeTypes.VIDEO_DOLBY_VISION)) {
      // Handle decoders that declare support for DV via MIME types that aren't
      // video/dolby-vision.
      if ("OMX.MS.HEVCDV.Decoder".equals(name)) {
        return "video/hevcdv";
      } else if ("OMX.RTK.video.decoder".equals(name)
          || "OMX.realtek.video.decoder.tunneled".equals(name)) {
        return "video/dv_hevc";
      }
    } else if (mimeType.equals(MimeTypes.VIDEO_MV_HEVC)) {
      // Handle decoders that declare support for MV-HEVC via MIME types that aren't video/mv-hevc.
      if ("c2.qti.mvhevc.decoder".equals(name) || "c2.qti.mvhevc.decoder.secure".equals(name)) {
        return "video/x-mvhevc";
      }
    } else if (mimeType.equals(MimeTypes.AUDIO_ALAC) && "OMX.lge.alac.decoder".equals(name)) {
      return "audio/x-lg-alac";
    } else if (mimeType.equals(MimeTypes.AUDIO_FLAC) && "OMX.lge.flac.decoder".equals(name)) {
      return "audio/x-lg-flac";
    } else if (mimeType.equals(MimeTypes.AUDIO_AC3) && "OMX.lge.ac3.decoder".equals(name)) {
      return "audio/lg-ac3";
    }

    return null;
  }

  /**
   * Returns whether the specified codec is usable for decoding on the current device.
   *
   * @param info The codec information.
   * @param name The name of the codec
   * @param secureDecodersExplicit Whether secure decoders were explicitly listed, if present.
   * @param mimeType The MIME type.
   * @return Whether the specified codec is usable for decoding on the current device.
   */
  private static boolean isCodecUsableDecoder(
      android.media.MediaCodecInfo info,
      String name,
      boolean secureDecodersExplicit,
      String mimeType) {
    if (info.isEncoder() || (!secureDecodersExplicit && name.endsWith(".secure"))) {
      return false;
    }

    // Work around https://github.com/google/ExoPlayer/issues/3249.
    if (Util.SDK_INT < 24
        && ("OMX.SEC.aac.dec".equals(name) || "OMX.Exynos.AAC.Decoder".equals(name))
        && "samsung".equals(Build.MANUFACTURER)
        && (Build.DEVICE.startsWith("zeroflte") // Galaxy S6
            || Build.DEVICE.startsWith("zerolte") // Galaxy S6 Edge
            || Build.DEVICE.startsWith("zenlte") // Galaxy S6 Edge+
            || "SC-05G".equals(Build.DEVICE) // Galaxy S6
            || "marinelteatt".equals(Build.DEVICE) // Galaxy S6 Active
            || "404SC".equals(Build.DEVICE) // Galaxy S6 Edge
            || "SC-04G".equals(Build.DEVICE)
            || "SCV31".equals(Build.DEVICE))) {
      return false;
    }

    // MTK AC3 decoder doesn't support decoding JOC streams in 2-D. See [Internal: b/69400041].
    if (Util.SDK_INT <= 23
        && MimeTypes.AUDIO_E_AC3_JOC.equals(mimeType)
        && "OMX.MTK.AUDIO.DECODER.DSPAC3".equals(name)) {
      return false;
    }

    return true;
  }

  /**
   * Modifies a list of {@link MediaCodecInfo}s to apply workarounds where we know better than the
   * platform.
   *
   * @param mimeType The MIME type of input media.
   * @param decoderInfos The list to modify.
   */
  private static void applyWorkarounds(String mimeType, List<MediaCodecInfo> decoderInfos) {
    if (MimeTypes.AUDIO_RAW.equals(mimeType)) {
      if (Util.SDK_INT < 26
          && Build.DEVICE.equals("R9")
          && decoderInfos.size() == 1
          && decoderInfos.get(0).name.equals("OMX.MTK.AUDIO.DECODER.RAW")) {
        // This device does not list a generic raw audio decoder, yet it can be instantiated by
        // name. See <a href="https://github.com/google/ExoPlayer/issues/5782">Issue #5782</a>.
        decoderInfos.add(
            MediaCodecInfo.newInstance(
                /* name= */ "OMX.google.raw.decoder",
                /* mimeType= */ MimeTypes.AUDIO_RAW,
                /* codecMimeType= */ MimeTypes.AUDIO_RAW,
                /* capabilities= */ null,
                /* hardwareAccelerated= */ false,
                /* softwareOnly= */ true,
                /* vendor= */ false,
                /* forceDisableAdaptive= */ false,
                /* forceSecure= */ false));
      }
      // Work around inconsistent raw audio decoding behavior across different devices.
      sortByScore(
          decoderInfos,
          decoderInfo -> {
            String name = decoderInfo.name;
            if (name.startsWith("OMX.google") || name.startsWith("c2.android")) {
              // Prefer generic decoders over ones provided by the device.
              return 1;
            }
            if (Util.SDK_INT < 26 && name.equals("OMX.MTK.AUDIO.DECODER.RAW")) {
              // This decoder may modify the audio, so any other compatible decoders take
              // precedence. See [Internal: b/62337687].
              return -1;
            }
            return 0;
          });
    }

    if (Util.SDK_INT < 32 && decoderInfos.size() > 1) {
      String firstCodecName = decoderInfos.get(0).name;
      // Prefer anything other than OMX.qti.audio.decoder.flac on older devices. See [Internal
      // ref: b/199124812].
      if ("OMX.qti.audio.decoder.flac".equals(firstCodecName)) {
        decoderInfos.add(decoderInfos.remove(0));
      }
    }
  }

  private static boolean isAlias(android.media.MediaCodecInfo info) {
    return Util.SDK_INT >= 29 && isAliasV29(info);
  }

  @RequiresApi(29)
  private static boolean isAliasV29(android.media.MediaCodecInfo info) {
    return info.isAlias();
  }

  /**
   * The result of {@link android.media.MediaCodecInfo#isHardwareAccelerated()} for API levels 29+,
   * or a best-effort approximation for lower levels.
   */
  private static boolean isHardwareAccelerated(
      android.media.MediaCodecInfo codecInfo, String mimeType) {
    if (Util.SDK_INT >= 29) {
      return isHardwareAcceleratedV29(codecInfo);
    }
    // codecInfo.isHardwareAccelerated() != codecInfo.isSoftwareOnly() is not necessarily true.
    // However, we assume this to be true as an approximation.
    return !isSoftwareOnly(codecInfo, mimeType);
  }

  @RequiresApi(29)
  private static boolean isHardwareAcceleratedV29(android.media.MediaCodecInfo codecInfo) {
    return codecInfo.isHardwareAccelerated();
  }

  /**
   * The result of {@link android.media.MediaCodecInfo#isSoftwareOnly()} for API levels 29+, or a
   * best-effort approximation for lower levels.
   */
  private static boolean isSoftwareOnly(android.media.MediaCodecInfo codecInfo, String mimeType) {
    if (Util.SDK_INT >= 29) {
      return isSoftwareOnlyV29(codecInfo);
    }
    if (MimeTypes.isAudio(mimeType)) {
      // Assume audio decoders are software only.
      return true;
    }
    String codecName = Ascii.toLowerCase(codecInfo.getName());
    if (codecName.startsWith("arc.")) {
      // App Runtime for Chrome (ARC) codecs
      return false;
    }
    return codecName.startsWith("omx.google.")
        || codecName.startsWith("omx.ffmpeg.")
        || (codecName.startsWith("omx.sec.") && codecName.contains(".sw."))
        || codecName.equals("omx.qcom.video.decoder.hevcswvdec")
        || codecName.startsWith("c2.android.")
        || codecName.startsWith("c2.google.")
        || (!codecName.startsWith("omx.") && !codecName.startsWith("c2."));
  }

  @RequiresApi(29)
  private static boolean isSoftwareOnlyV29(android.media.MediaCodecInfo codecInfo) {
    return codecInfo.isSoftwareOnly();
  }

  /**
   * The result of {@link android.media.MediaCodecInfo#isVendor()} for API levels 29+, or a
   * best-effort approximation for lower levels.
   */
  private static boolean isVendor(android.media.MediaCodecInfo codecInfo) {
    if (Util.SDK_INT >= 29) {
      return isVendorV29(codecInfo);
    }
    String codecName = Ascii.toLowerCase(codecInfo.getName());
    return !codecName.startsWith("omx.google.")
        && !codecName.startsWith("c2.android.")
        && !codecName.startsWith("c2.google.");
  }

  @RequiresApi(29)
  private static boolean isVendorV29(android.media.MediaCodecInfo codecInfo) {
    return codecInfo.isVendor();
  }

  /**
   * Conversion values taken from ISO 14496-10 Table A-1.
   *
   * @param avcLevel One of the {@link CodecProfileLevel} {@code AVCLevel*} constants.
   * @return The maximum frame size that can be decoded by a decoder with the specified AVC level,
   *     or {@code -1} if the level is not recognized.
   */
  private static int avcLevelToMaxFrameSize(int avcLevel) {
    switch (avcLevel) {
      case CodecProfileLevel.AVCLevel1:
      case CodecProfileLevel.AVCLevel1b:
        return 99 * 16 * 16;
      case CodecProfileLevel.AVCLevel12:
      case CodecProfileLevel.AVCLevel13:
      case CodecProfileLevel.AVCLevel2:
        return 396 * 16 * 16;
      case CodecProfileLevel.AVCLevel21:
        return 792 * 16 * 16;
      case CodecProfileLevel.AVCLevel22:
      case CodecProfileLevel.AVCLevel3:
        return 1620 * 16 * 16;
      case CodecProfileLevel.AVCLevel31:
        return 3600 * 16 * 16;
      case CodecProfileLevel.AVCLevel32:
        return 5120 * 16 * 16;
      case CodecProfileLevel.AVCLevel4:
      case CodecProfileLevel.AVCLevel41:
        return 8192 * 16 * 16;
      case CodecProfileLevel.AVCLevel42:
        return 8704 * 16 * 16;
      case CodecProfileLevel.AVCLevel5:
        return 22080 * 16 * 16;
      case CodecProfileLevel.AVCLevel51:
      case CodecProfileLevel.AVCLevel52:
        return 36864 * 16 * 16;
      case CodecProfileLevel.AVCLevel6:
      case CodecProfileLevel.AVCLevel61:
      case CodecProfileLevel.AVCLevel62:
        return 139264 * 16 * 16;
      default:
        return -1;
    }
  }

  /** Stably sorts the provided {@code list} in-place, in order of decreasing score. */
  private static <T> void sortByScore(List<T> list, ScoreProvider<T> scoreProvider) {
    Collections.sort(list, (a, b) -> scoreProvider.getScore(b) - scoreProvider.getScore(a));
  }

  /** Interface for providers of item scores. */
  private interface ScoreProvider<T> {
    /** Returns the score of the provided item. */
    int getScore(T t);
  }

  private interface MediaCodecListCompat {

    /** The number of codecs in the list. */
    int getCodecCount();

    /**
     * The info at the specified index in the list.
     *
     * @param index The index.
     */
    android.media.MediaCodecInfo getCodecInfoAt(int index);

    /** Returns whether secure decoders are explicitly listed, if present. */
    boolean secureDecodersExplicit();

    /** Whether the specified {@link CodecCapabilities} {@code feature} is supported. */
    boolean isFeatureSupported(String feature, String mimeType, CodecCapabilities capabilities);

    /** Whether the specified {@link CodecCapabilities} {@code feature} is required. */
    boolean isFeatureRequired(String feature, String mimeType, CodecCapabilities capabilities);
  }

  private static final class MediaCodecListCompatV21 implements MediaCodecListCompat {

    private final int codecKind;

    @Nullable private android.media.MediaCodecInfo[] mediaCodecInfos;

    public MediaCodecListCompatV21(
        boolean includeSecure, boolean includeTunneling, boolean includeSpecialCodec) {
      codecKind =
          includeSecure || includeTunneling || includeSpecialCodec
              ? MediaCodecList.ALL_CODECS
              : MediaCodecList.REGULAR_CODECS;
    }

    @Override
    public int getCodecCount() {
      ensureMediaCodecInfosInitialized();
      return mediaCodecInfos.length;
    }

    @Override
    public android.media.MediaCodecInfo getCodecInfoAt(int index) {
      ensureMediaCodecInfosInitialized();
      return mediaCodecInfos[index];
    }

    @Override
    public boolean secureDecodersExplicit() {
      return true;
    }

    @Override
    public boolean isFeatureSupported(
        String feature, String mimeType, CodecCapabilities capabilities) {
      return capabilities.isFeatureSupported(feature);
    }

    @Override
    public boolean isFeatureRequired(
        String feature, String mimeType, CodecCapabilities capabilities) {
      return capabilities.isFeatureRequired(feature);
    }

    @EnsuresNonNull({"mediaCodecInfos"})
    private void ensureMediaCodecInfosInitialized() {
      if (mediaCodecInfos == null) {
        mediaCodecInfos = new MediaCodecList(codecKind).getCodecInfos();
      }
    }
  }

  private static final class MediaCodecListCompatV16 implements MediaCodecListCompat {

    @Override
    public int getCodecCount() {
      return MediaCodecList.getCodecCount();
    }

    @Override
    public android.media.MediaCodecInfo getCodecInfoAt(int index) {
      return MediaCodecList.getCodecInfoAt(index);
    }

    @Override
    public boolean secureDecodersExplicit() {
      return false;
    }

    @Override
    public boolean isFeatureSupported(
        String feature, String mimeType, CodecCapabilities capabilities) {
      // Secure decoders weren't explicitly listed prior to API level 21. We assume that a secure
      // H264 decoder exists.
      return CodecCapabilities.FEATURE_SecurePlayback.equals(feature)
          && MimeTypes.VIDEO_H264.equals(mimeType);
    }

    @Override
    public boolean isFeatureRequired(
        String feature, String mimeType, CodecCapabilities capabilities) {
      return false;
    }
  }

  private static final class CodecKey {

    public final String mimeType;
    public final boolean secure;
    public final boolean tunneling;

    public CodecKey(String mimeType, boolean secure, boolean tunneling) {
      this.mimeType = mimeType;
      this.secure = secure;
      this.tunneling = tunneling;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + mimeType.hashCode();
      result = prime * result + (secure ? 1231 : 1237);
      result = prime * result + (tunneling ? 1231 : 1237);
      return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || obj.getClass() != CodecKey.class) {
        return false;
      }
      CodecKey other = (CodecKey) obj;
      return TextUtils.equals(mimeType, other.mimeType)
          && secure == other.secure
          && tunneling == other.tunneling;
    }
  }
}
