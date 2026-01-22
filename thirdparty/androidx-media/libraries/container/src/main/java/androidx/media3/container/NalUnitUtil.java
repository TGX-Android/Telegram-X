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
package androidx.media3.container;

import static androidx.media3.common.MimeTypes.containsCodecsCorrespondingToMimeType;
import static com.google.common.math.DoubleMath.log2;
import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.CodecSpecificDataUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Utility methods for handling H.264/AVC and H.265/HEVC NAL units. */
@UnstableApi
public final class NalUnitUtil {

  private static final String TAG = "NalUnitUtil";

  /** H.264 coded slice of a non-IDR picture. */
  public static final int H264_NAL_UNIT_TYPE_NON_IDR = 1;

  /**
   * @deprecated Use {@link #H264_NAL_UNIT_TYPE_NON_IDR}
   */
  @Deprecated public static final int NAL_UNIT_TYPE_NON_IDR = H264_NAL_UNIT_TYPE_NON_IDR;

  /** H.264 coded slice data partition A. */
  public static final int H264_NAL_UNIT_TYPE_PARTITION_A = 2;

  /**
   * @deprecated Use {@link #H264_NAL_UNIT_TYPE_PARTITION_A}
   */
  @Deprecated public static final int NAL_UNIT_TYPE_PARTITION_A = H264_NAL_UNIT_TYPE_PARTITION_A;

  /** H.264 coded slice of an IDR picture. */
  public static final int H264_NAL_UNIT_TYPE_IDR = 5;

  /**
   * @deprecated Use {@link #H264_NAL_UNIT_TYPE_IDR}
   */
  @Deprecated public static final int NAL_UNIT_TYPE_IDR = H264_NAL_UNIT_TYPE_IDR;

  /** H.264 supplemental enhancement information. */
  public static final int H264_NAL_UNIT_TYPE_SEI = 6;

  /**
   * @deprecated Use {@link #H264_NAL_UNIT_TYPE_SEI}
   */
  @Deprecated public static final int NAL_UNIT_TYPE_SEI = H264_NAL_UNIT_TYPE_SEI;

  /** H.264 sequence parameter set. */
  public static final int H264_NAL_UNIT_TYPE_SPS = 7;

  /**
   * @deprecated Use {@link #H264_NAL_UNIT_TYPE_SPS}
   */
  @Deprecated public static final int NAL_UNIT_TYPE_SPS = H264_NAL_UNIT_TYPE_SPS;

  /** H.264 picture parameter set. */
  public static final int H264_NAL_UNIT_TYPE_PPS = 8;

  /**
   * @deprecated Use {@link #H264_NAL_UNIT_TYPE_PPS}
   */
  @Deprecated public static final int NAL_UNIT_TYPE_PPS = H264_NAL_UNIT_TYPE_PPS;

  /** H.264 access unit delimiter. */
  public static final int H264_NAL_UNIT_TYPE_AUD = 9;

  /**
   * @deprecated Use {@link #H264_NAL_UNIT_TYPE_AUD}
   */
  @Deprecated public static final int NAL_UNIT_TYPE_AUD = H264_NAL_UNIT_TYPE_AUD;

  /** H.264 prefix NAL unit. */
  public static final int H264_NAL_UNIT_TYPE_PREFIX = 14;

  /**
   * @deprecated Use {@link #H264_NAL_UNIT_TYPE_PREFIX}
   */
  @Deprecated public static final int NAL_UNIT_TYPE_PREFIX = H264_NAL_UNIT_TYPE_PREFIX;

  /** H.264 unspecified NAL unit. */
  public static final int H264_NAL_UNIT_TYPE_UNSPECIFIED = 24;

  /** H.265 coded slice segment of a random access skipped leading picture (RASL_R). */
  public static final int H265_NAL_UNIT_TYPE_RASL_R = 9;

  /** H.265 coded slice segment of a broken link access picture (BLA_W_LP). */
  public static final int H265_NAL_UNIT_TYPE_BLA_W_LP = 16;

  /** H.265 coded slice segment of a clean random access picture (CRA_NUT). */
  public static final int H265_NAL_UNIT_TYPE_CRA = 21;

  /** H.265 video parameter set (VPS_NUT). */
  public static final int H265_NAL_UNIT_TYPE_VPS = 32;

  /** H.265 sequence parameter set (SPS_NUT). */
  public static final int H265_NAL_UNIT_TYPE_SPS = 33;

  /** H.265 picture parameter set (PPS_NUT). */
  public static final int H265_NAL_UNIT_TYPE_PPS = 34;

  /** H.265 access unit delimiter (AUD_NUT). */
  public static final int H265_NAL_UNIT_TYPE_AUD = 35;

  /** H.265 prefixed supplemental enhancement information (PREFIX_SEI_NUT). */
  public static final int H265_NAL_UNIT_TYPE_PREFIX_SEI = 39;

  /** H.265 suffixed supplemental enhancement information (SUFFIX_SEI_NUT). */
  public static final int H265_NAL_UNIT_TYPE_SUFFIX_SEI = 40;

  /** H.265 unspecified NAL unit. */
  public static final int H265_NAL_UNIT_TYPE_UNSPECIFIED = 48;

  /** Holds data parsed from a H.264 sequence parameter set NAL unit. */
  public static final class SpsData {

    public final int profileIdc;
    public final int constraintsFlagsAndReservedZero2Bits;
    public final int levelIdc;
    public final int seqParameterSetId;
    public final int maxNumRefFrames;
    public final int width;
    public final int height;
    public final float pixelWidthHeightRatio;
    public final int bitDepthLumaMinus8;
    public final int bitDepthChromaMinus8;
    public final boolean separateColorPlaneFlag;
    public final boolean frameMbsOnlyFlag;
    public final int frameNumLength;
    public final int picOrderCountType;
    public final int picOrderCntLsbLength;
    public final boolean deltaPicOrderAlwaysZeroFlag;
    public final @C.ColorSpace int colorSpace;
    public final @C.ColorRange int colorRange;
    public final @C.ColorTransfer int colorTransfer;
    public final int maxNumReorderFrames;

    public SpsData(
        int profileIdc,
        int constraintsFlagsAndReservedZero2Bits,
        int levelIdc,
        int seqParameterSetId,
        int maxNumRefFrames,
        int width,
        int height,
        float pixelWidthHeightRatio,
        int bitDepthLumaMinus8,
        int bitDepthChromaMinus8,
        boolean separateColorPlaneFlag,
        boolean frameMbsOnlyFlag,
        int frameNumLength,
        int picOrderCountType,
        int picOrderCntLsbLength,
        boolean deltaPicOrderAlwaysZeroFlag,
        @C.ColorSpace int colorSpace,
        @C.ColorRange int colorRange,
        @C.ColorTransfer int colorTransfer,
        int maxNumReorderFrames) {
      this.profileIdc = profileIdc;
      this.constraintsFlagsAndReservedZero2Bits = constraintsFlagsAndReservedZero2Bits;
      this.levelIdc = levelIdc;
      this.seqParameterSetId = seqParameterSetId;
      this.maxNumRefFrames = maxNumRefFrames;
      this.width = width;
      this.height = height;
      this.pixelWidthHeightRatio = pixelWidthHeightRatio;
      this.bitDepthLumaMinus8 = bitDepthLumaMinus8;
      this.bitDepthChromaMinus8 = bitDepthChromaMinus8;
      this.separateColorPlaneFlag = separateColorPlaneFlag;
      this.frameMbsOnlyFlag = frameMbsOnlyFlag;
      this.frameNumLength = frameNumLength;
      this.picOrderCountType = picOrderCountType;
      this.picOrderCntLsbLength = picOrderCntLsbLength;
      this.deltaPicOrderAlwaysZeroFlag = deltaPicOrderAlwaysZeroFlag;
      this.colorSpace = colorSpace;
      this.colorRange = colorRange;
      this.colorTransfer = colorTransfer;
      this.maxNumReorderFrames = maxNumReorderFrames;
    }
  }

  /** Holds data parsed from a H.265 NAL unit header. */
  public static final class H265NalHeader {

    public final int nalUnitType;
    public final int layerId;
    public final int temporalId;

    public H265NalHeader(int nalUnitType, int layerId, int temporalId) {
      this.nalUnitType = nalUnitType;
      this.layerId = layerId;
      this.temporalId = temporalId;
    }
  }

  /** Holds data that maps NAL unit header layer ID to the internal layer info specified in VPS. */
  public static final class H265LayerInfo {
    public final int layerIdInVps;

    /** Internal layer ID used within VPS. */
    public final int viewId;

    /** Indicates the view ID (>= 0) for the multiview case. */
    public H265LayerInfo(int layerIdInVps, int viewId) {
      this.layerIdInVps = layerIdInVps;
      this.viewId = viewId;
    }
  }

  /** Holds data parsed from a H.265 profile_tier_level() of either VPS or SPS. */
  public static final class H265ProfileTierLevel {

    public final int generalProfileSpace;
    public final boolean generalTierFlag;
    public final int generalProfileIdc;
    public final int generalProfileCompatibilityFlags;
    public final int[] constraintBytes;
    public final int generalLevelIdc;

    public H265ProfileTierLevel(
        int generalProfileSpace,
        boolean generalTierFlag,
        int generalProfileIdc,
        int generalProfileCompatibilityFlags,
        int[] constraintBytes,
        int generalLevelIdc) {
      this.generalProfileSpace = generalProfileSpace;
      this.generalTierFlag = generalTierFlag;
      this.generalProfileIdc = generalProfileIdc;
      this.generalProfileCompatibilityFlags = generalProfileCompatibilityFlags;
      this.constraintBytes = constraintBytes;
      this.generalLevelIdc = generalLevelIdc;
    }
  }

  /** Holds a list of H.265 profile_tier_level()s and a corresponding list of indices. */
  public static final class H265ProfileTierLevelsAndIndices {
    /** The list of profile_tier_level()s that can be referenced by each layer. */
    public final ImmutableList<H265ProfileTierLevel> profileTierLevels;

    /**
     * The list of indices to the {@code profileTierLevels}. For each layer available in the L-HEVC
     * bitstream (identified by the layer ID in VPS), an index to the profileTierLevels list is set.
     */
    public final int[] indices;

    public H265ProfileTierLevelsAndIndices(
        List<H265ProfileTierLevel> profileTierLevels, int[] indices) {
      this.profileTierLevels = ImmutableList.copyOf(profileTierLevels);
      this.indices = indices;
    }
  }

  /** Holds data parsed from a H.265 rep_format() of VPS extension - vps_extension(). */
  public static final class H265RepFormat {

    public final int chromaFormatIdc;
    public final int bitDepthLumaMinus8;
    public final int bitDepthChromaMinus8;
    public final int width;
    public final int height;

    public H265RepFormat(
        int chromaFormatIdc,
        int bitDepthLumaMinus8,
        int bitDepthChromaMinus8,
        int width,
        int height) {
      this.chromaFormatIdc = chromaFormatIdc;
      this.bitDepthLumaMinus8 = bitDepthLumaMinus8;
      this.bitDepthChromaMinus8 = bitDepthChromaMinus8;
      this.width = width;
      this.height = height;
    }
  }

  /** Holds a list of H.265 rep_format()s and a corresponding list of indices. */
  public static final class H265RepFormatsAndIndices {

    /** The list of rep_format()s that can be referenced by each layer. */
    public final ImmutableList<H265RepFormat> repFormats;

    /**
     * The list of indices to the {@code repFormats}; for each layer available in the L-HEVC
     * bitstream (identified by the layer ID in VPS), an index to the repFormats list is set.
     */
    public final int[] indices;

    public H265RepFormatsAndIndices(List<H265RepFormat> repFormats, int[] indices) {
      this.repFormats = ImmutableList.copyOf(repFormats);
      this.indices = indices;
    }
  }

  /** Holds data parsed from a H.265 video_signal_info() of vps_vui() of vps_extension(). */
  public static final class H265VideoSignalInfo {

    public final @C.ColorSpace int colorSpace;
    public final @C.ColorRange int colorRange;
    public final @C.ColorTransfer int colorTransfer;

    public H265VideoSignalInfo(
        @C.ColorSpace int colorSpace,
        @C.ColorRange int colorRange,
        @C.ColorTransfer int colorTransfer) {
      this.colorSpace = colorSpace;
      this.colorRange = colorRange;
      this.colorTransfer = colorTransfer;
    }
  }

  /** Holds a list of H.265 video_signal_info()s and a corresponding list of indices. */
  public static final class H265VideoSignalInfosAndIndices {

    /** The list of video_signal_info()s that can be referenced by each layer. */
    public final ImmutableList<H265VideoSignalInfo> videoSignalInfos;

    /**
     * The list of indices to the {@code videoSignalInfos}; for each layer available in the L-HEVC
     * bitstream (identified by the layer ID in VPS), an index to the videoSignalInfos list is set.
     */
    public final int[] indices;

    public H265VideoSignalInfosAndIndices(
        List<H265VideoSignalInfo> videoSignalInfos, int[] indices) {
      this.videoSignalInfos = ImmutableList.copyOf(videoSignalInfos);
      this.indices = indices;
    }
  }

  /** Holds data parsed from a H.265 video parameter set NAL unit. */
  public static final class H265VpsData {

    public final H265NalHeader nalHeader;

    public final ImmutableList<H265LayerInfo> layerInfos;

    /** The list of profile_tier_level()s and corresponding lis of indices. */
    public final H265ProfileTierLevelsAndIndices profileTierLevelsAndIndices;

    /** The list of rep_format()s and corresponding list of indices. */
    @Nullable public final H265RepFormatsAndIndices repFormatsAndIndices;

    /** The list of video_signal_info()s and corresponding list of indices. */
    @Nullable public final H265VideoSignalInfosAndIndices videoSignalInfosAndIndices;

    public H265VpsData(
        H265NalHeader nalHeader,
        @Nullable List<H265LayerInfo> layerInfos,
        H265ProfileTierLevelsAndIndices profileTierLevelsAndIndices,
        @Nullable H265RepFormatsAndIndices repFormatsAndIndices,
        @Nullable H265VideoSignalInfosAndIndices videoSignalInfosAndIndices) {
      this.nalHeader = nalHeader;
      this.layerInfos = layerInfos != null ? ImmutableList.copyOf(layerInfos) : ImmutableList.of();
      this.profileTierLevelsAndIndices = profileTierLevelsAndIndices;
      this.repFormatsAndIndices = repFormatsAndIndices;
      this.videoSignalInfosAndIndices = videoSignalInfosAndIndices;
    }
  }

  /** Holds data parsed from a H.265 sequence parameter set NAL unit. */
  public static final class H265SpsData {

    public final H265NalHeader nalHeader;
    public final int maxSubLayersMinus1;
    @Nullable public final H265ProfileTierLevel profileTierLevel;
    public final int chromaFormatIdc;
    public final int bitDepthLumaMinus8;
    public final int bitDepthChromaMinus8;
    public final int seqParameterSetId;
    public final int width;
    public final int height;
    public final float pixelWidthHeightRatio;
    public final int maxNumReorderPics;
    public final @C.ColorSpace int colorSpace;
    public final @C.ColorRange int colorRange;
    public final @C.ColorTransfer int colorTransfer;

    public H265SpsData(
        H265NalHeader nalHeader,
        int maxSubLayersMinus1,
        @Nullable H265ProfileTierLevel profileTierLevel,
        int chromaFormatIdc,
        int bitDepthLumaMinus8,
        int bitDepthChromaMinus8,
        int seqParameterSetId,
        int width,
        int height,
        float pixelWidthHeightRatio,
        int maxNumReorderPics,
        @C.ColorSpace int colorSpace,
        @C.ColorRange int colorRange,
        @C.ColorTransfer int colorTransfer) {
      this.nalHeader = nalHeader;
      this.maxSubLayersMinus1 = maxSubLayersMinus1;
      this.profileTierLevel = profileTierLevel;
      this.chromaFormatIdc = chromaFormatIdc;
      this.bitDepthLumaMinus8 = bitDepthLumaMinus8;
      this.bitDepthChromaMinus8 = bitDepthChromaMinus8;
      this.seqParameterSetId = seqParameterSetId;
      this.width = width;
      this.height = height;
      this.pixelWidthHeightRatio = pixelWidthHeightRatio;
      this.maxNumReorderPics = maxNumReorderPics;
      this.colorSpace = colorSpace;
      this.colorRange = colorRange;
      this.colorTransfer = colorTransfer;
    }
  }

  /** Holds data parsed from a picture parameter set NAL unit. */
  public static final class PpsData {

    public final int picParameterSetId;
    public final int seqParameterSetId;
    public final boolean bottomFieldPicOrderInFramePresentFlag;

    public PpsData(
        int picParameterSetId,
        int seqParameterSetId,
        boolean bottomFieldPicOrderInFramePresentFlag) {
      this.picParameterSetId = picParameterSetId;
      this.seqParameterSetId = seqParameterSetId;
      this.bottomFieldPicOrderInFramePresentFlag = bottomFieldPicOrderInFramePresentFlag;
    }
  }

  /** Holds data parsed from a H.265 3D reference displays information SEI message. */
  public static final class H265Sei3dRefDisplayInfoData {
    public final int precRefDisplayWidth;
    public final int precRefViewingDist;
    public final int numRefDisplays;
    public final int leftViewId;
    public final int rightViewId;
    public final int exponentRefDisplayWidth;
    public final int mantissaRefDisplayWidth;
    public final int exponentRefViewingDist;
    public final int mantissaRefViewingDist;

    public H265Sei3dRefDisplayInfoData(
        int precRefDisplayWidth,
        int precRefViewingDist,
        int numRefDisplays,
        int leftViewId,
        int rightViewId,
        int exponentRefDisplayWidth,
        int mantissaRefDisplayWidth,
        int exponentRefViewingDist,
        int mantissaRefViewingDist) {
      this.precRefDisplayWidth = precRefDisplayWidth;
      this.precRefViewingDist = precRefViewingDist;
      this.numRefDisplays = numRefDisplays;
      this.leftViewId = leftViewId;
      this.rightViewId = rightViewId;
      this.exponentRefDisplayWidth = exponentRefDisplayWidth;
      this.mantissaRefDisplayWidth = mantissaRefDisplayWidth;
      this.exponentRefViewingDist = exponentRefViewingDist;
      this.mantissaRefViewingDist = mantissaRefViewingDist;
    }
  }

  /** Four initial bytes that must prefix NAL units for decoding. */
  public static final byte[] NAL_START_CODE = new byte[] {0, 0, 0, 1};

  /** Value for aspect_ratio_idc indicating an extended aspect ratio, in H.264 and H.265 SPSs. */
  public static final int EXTENDED_SAR = 0xFF;

  /** Aspect ratios indexed by aspect_ratio_idc, in H.264 and H.265 SPSs. */
  public static final float[] ASPECT_RATIO_IDC_VALUES =
      new float[] {
        1f /* Unspecified. Assume square */,
        1f,
        12f / 11f,
        10f / 11f,
        16f / 11f,
        40f / 33f,
        24f / 11f,
        20f / 11f,
        32f / 11f,
        80f / 33f,
        18f / 11f,
        15f / 11f,
        64f / 33f,
        160f / 99f,
        4f / 3f,
        3f / 2f,
        2f
      };

  private static final int INVALID_ID = -1; // Invalid ID.

  private static final Object scratchEscapePositionsLock = new Object();

  /**
   * Temporary store for positions of escape codes in {@link #unescapeStream(byte[], int)}. Guarded
   * by {@link #scratchEscapePositionsLock}.
   */
  private static int[] scratchEscapePositions = new int[10];

  /**
   * Unescapes {@code data} up to the specified limit, replacing occurrences of [0, 0, 3] with [0,
   * 0]. The unescaped data is returned in-place, with the return value indicating its length.
   *
   * <p>Executions of this method are mutually exclusive, so it should not be called with very large
   * buffers.
   *
   * @param data The data to unescape.
   * @param limit The limit (exclusive) of the data to unescape.
   * @return The length of the unescaped data.
   */
  public static int unescapeStream(byte[] data, int limit) {
    synchronized (scratchEscapePositionsLock) {
      int position = 0;
      int scratchEscapeCount = 0;
      while (position < limit) {
        position = findNextUnescapeIndex(data, position, limit);
        if (position < limit) {
          if (scratchEscapePositions.length <= scratchEscapeCount) {
            // Grow scratchEscapePositions to hold a larger number of positions.
            scratchEscapePositions =
                Arrays.copyOf(scratchEscapePositions, scratchEscapePositions.length * 2);
          }
          scratchEscapePositions[scratchEscapeCount++] = position;
          position += 3;
        }
      }

      int unescapedLength = limit - scratchEscapeCount;
      int escapedPosition = 0; // The position being read from.
      int unescapedPosition = 0; // The position being written to.
      for (int i = 0; i < scratchEscapeCount; i++) {
        int nextEscapePosition = scratchEscapePositions[i];
        int copyLength = nextEscapePosition - escapedPosition;
        System.arraycopy(data, escapedPosition, data, unescapedPosition, copyLength);
        unescapedPosition += copyLength;
        data[unescapedPosition++] = 0;
        data[unescapedPosition++] = 0;
        escapedPosition += copyLength + 3;
      }

      int remainingLength = unescapedLength - unescapedPosition;
      System.arraycopy(data, escapedPosition, data, unescapedPosition, remainingLength);
      return unescapedLength;
    }
  }

  /**
   * Discards data from the buffer up to the first SPS, where {@code data.position()} is interpreted
   * as the length of the buffer.
   *
   * <p>When the method returns, {@code data.position()} will contain the new length of the buffer.
   * If the buffer is not empty it is guaranteed to start with an SPS.
   *
   * @param data Buffer containing start code delimited NAL units.
   */
  public static void discardToSps(ByteBuffer data) {
    int length = data.position();
    int consecutiveZeros = 0;
    int offset = 0;
    while (offset + 1 < length) {
      int value = data.get(offset) & 0xFF;
      if (consecutiveZeros == 3) {
        if (value == 1 && (data.get(offset + 1) & 0x1F) == H264_NAL_UNIT_TYPE_SPS) {
          // Copy from this NAL unit onwards to the start of the buffer.
          ByteBuffer offsetData = data.duplicate();
          offsetData.position(offset - 3);
          offsetData.limit(length);
          data.position(0);
          data.put(offsetData);
          return;
        }
      } else if (value == 0) {
        consecutiveZeros++;
      }
      if (value != 0) {
        consecutiveZeros = 0;
      }
      offset++;
    }
    // Empty the buffer if the SPS NAL unit was not found.
    data.clear();
  }

  /**
   * @deprecated Use {@link #isNalUnitSei(Format, byte)} in order to support {@link
   *     MimeTypes#VIDEO_DOLBY_VISION} tracks with backwards compatible {@link MimeTypes#VIDEO_H264}
   *     or {@link MimeTypes#VIDEO_H265} data.
   */
  @Deprecated
  public static boolean isNalUnitSei(@Nullable String mimeType, byte nalUnitHeaderFirstByte) {
    return (MimeTypes.VIDEO_H264.equals(mimeType)
            && (nalUnitHeaderFirstByte & 0x1F) == H264_NAL_UNIT_TYPE_SEI)
        || (MimeTypes.VIDEO_H265.equals(mimeType)
            && ((nalUnitHeaderFirstByte & 0x7E) >> 1) == H265_NAL_UNIT_TYPE_PREFIX_SEI);
  }

  /**
   * Returns whether the NAL unit with the specified header contains supplemental enhancement
   * information.
   *
   * @param format The sample {@link Format}.
   * @param nalUnitHeaderFirstByte The first byte of nal_unit().
   * @return Whether the NAL unit with the specified header is an SEI NAL unit. False is returned if
   *     the {@code MimeType} is {@code null}.
   */
  public static boolean isNalUnitSei(Format format, byte nalUnitHeaderFirstByte) {
    return ((Objects.equals(format.sampleMimeType, MimeTypes.VIDEO_H264)
                || containsCodecsCorrespondingToMimeType(format.codecs, MimeTypes.VIDEO_H264))
            && (nalUnitHeaderFirstByte & 0x1F) == H264_NAL_UNIT_TYPE_SEI)
        || ((Objects.equals(format.sampleMimeType, MimeTypes.VIDEO_H265)
                || containsCodecsCorrespondingToMimeType(format.codecs, MimeTypes.VIDEO_H265))
            && ((nalUnitHeaderFirstByte & 0x7E) >> 1) == H265_NAL_UNIT_TYPE_PREFIX_SEI);
  }

  /**
   * Returns the type of the NAL unit in {@code data} that starts at {@code offset}.
   *
   * @param data The data to search.
   * @param offset The start offset of a NAL unit. Must lie between {@code -3} (inclusive) and
   *     {@code data.length - 3} (exclusive).
   * @return The type of the unit.
   */
  public static int getNalUnitType(byte[] data, int offset) {
    return data[offset + 3] & 0x1F;
  }

  /**
   * Returns whether the H.264 NAL unit can be depended on by subsequent NAL units in decoding
   * order.
   *
   * @param nalUnitHeaderFirstByte The first byte of nal_unit().
   */
  public static boolean isH264NalUnitDependedOn(byte nalUnitHeaderFirstByte) {
    int nalRefIdc = ((nalUnitHeaderFirstByte & 0x60) >> 5);
    if (nalRefIdc != 0) {
      // A picture with nal_ref_idc not equal to 0 is a reference picture, which contains
      // samples that may be used for inter prediction in the decoding process of subsequent
      // pictures in decoding order.
      return true;
    }

    int nalUnitType = nalUnitHeaderFirstByte & 0x1F;
    if (nalUnitType == H264_NAL_UNIT_TYPE_NON_IDR) {
      // For pictures (Video Coding Layer NAL units), we can rely on nal_ref_idc to determine
      // whether future NAL units depend on it.
      return false;
    }
    if (nalUnitType == H264_NAL_UNIT_TYPE_AUD) {
      // NAL unit delimiters are not depended on.
      return false;
    }
    if (nalUnitType == H264_NAL_UNIT_TYPE_PREFIX) {
      // Prefix NAL units are only used by Annex G scalable video coding to mark temporal layers.
      // Rely on nal_ref_idc to identify sample dependencies.
      return false;
    }
    // Treat any other NAL unit type as depended on. This might be too restrictive, but reduces
    // risks around closed captions, HDR metadata in SEI messages.
    return true;
  }

  /**
   * Returns the number of bytes in the NAL unit header.
   *
   * <p>The NAL unit header can be used to determine the NAL unit type and whether subsequent NAL
   * units can depend on the current NAL unit.
   *
   * <p>This is {@code nalUnitHeaderBytes} from the H.264 spec, or the size of {@code
   * nal_unit_header()} in H.265.
   *
   * @param format The sample {@link Format}.
   */
  public static int numberOfBytesInNalUnitHeader(Format format) {
    if (Objects.equals(format.sampleMimeType, MimeTypes.VIDEO_H264)) {
      return 1;
    }
    if (Objects.equals(format.sampleMimeType, MimeTypes.VIDEO_H265)
        || MimeTypes.containsCodecsCorrespondingToMimeType(format.codecs, MimeTypes.VIDEO_H265)) {
      return 2;
    }
    return 0;
  }

  /**
   * Returns whether the NAL unit starting with the given bytes can be depended on by subsequent NAL
   * units in decoding order.
   *
   * @param data The array holding the first {@code length} bytes of the NAL unit.
   * @param offset The offset in {@code data} at which the NAL unit starts.
   * @param length The number of bytes available.
   * @param format The sample {@link Format}.
   */
  public static boolean isDependedOn(byte[] data, int offset, int length, Format format) {
    if (Objects.equals(format.sampleMimeType, MimeTypes.VIDEO_H264)) {
      return isH264NalUnitDependedOn(data[offset]);
    }
    if (Objects.equals(format.sampleMimeType, MimeTypes.VIDEO_H265)) {
      return isH265NalUnitDependedOn(data, offset, length, format);
    }
    return true;
  }

  private static boolean isH265NalUnitDependedOn(
      byte[] data, int offset, int length, Format format) {
    H265NalHeader header =
        parseH265NalHeader(new ParsableNalUnitBitArray(data, offset, /* limit= */ offset + length));
    if (header.nalUnitType == H265_NAL_UNIT_TYPE_AUD) {
      // NAL unit delimiters are not depended on.
      return false;
    }
    boolean isSubLayerNonReferencePicture = header.nalUnitType <= 14 && header.nalUnitType % 2 == 0;
    if (isSubLayerNonReferencePicture && header.temporalId == format.maxSubLayers - 1) {
      // Sub-layer non-reference (SLNR) pictures cannot be used for inter prediction in the same
      // temporal layer. That is, SLNR pictures are not depended on if they are part of the highest
      // temporal layer.
      return false;
    }
    return true;
  }

  /**
   * Returns the type of the H.265 NAL unit in {@code data} that starts at {@code offset}.
   *
   * @param data The data to search.
   * @param offset The start offset of a NAL unit. Must lie between {@code -3} (inclusive) and
   *     {@code data.length - 3} (exclusive).
   * @return The type of the unit.
   */
  public static int getH265NalUnitType(byte[] data, int offset) {
    return (data[offset + 3] & 0x7E) >> 1;
  }

  /**
   * Parses a SPS NAL unit using the syntax defined in ITU-T Recommendation H.264 (2013) subsection
   * 7.3.2.1.1.
   *
   * @param nalData A buffer containing escaped SPS data.
   * @param nalOffset The offset of the NAL unit header in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the SPS data.
   */
  public static SpsData parseSpsNalUnit(byte[] nalData, int nalOffset, int nalLimit) {
    return parseSpsNalUnitPayload(nalData, nalOffset + 1, nalLimit);
  }

  /**
   * Parses a SPS NAL unit payload (excluding the NAL unit header) using the syntax defined in ITU-T
   * Recommendation H.264 (2013) subsection 7.3.2.1.1.
   *
   * @param nalData A buffer containing escaped SPS data.
   * @param nalOffset The offset of the NAL unit payload in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the SPS data.
   */
  public static SpsData parseSpsNalUnitPayload(byte[] nalData, int nalOffset, int nalLimit) {
    ParsableNalUnitBitArray data = new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit);
    int profileIdc = data.readBits(8);
    int constraintsFlagsAndReservedZero2Bits = data.readBits(8);
    int levelIdc = data.readBits(8);
    int seqParameterSetId = data.readUnsignedExpGolombCodedInt();

    int chromaFormatIdc = 1; // Default is 4:2:0
    boolean separateColorPlaneFlag = false;
    int bitDepthLumaMinus8 = 0;
    int bitDepthChromaMinus8 = 0;
    if (profileIdc == 100
        || profileIdc == 110
        || profileIdc == 122
        || profileIdc == 244
        || profileIdc == 44
        || profileIdc == 83
        || profileIdc == 86
        || profileIdc == 118
        || profileIdc == 128
        || profileIdc == 138) {
      chromaFormatIdc = data.readUnsignedExpGolombCodedInt();
      if (chromaFormatIdc == 3) {
        separateColorPlaneFlag = data.readBit();
      }
      bitDepthLumaMinus8 = data.readUnsignedExpGolombCodedInt();
      bitDepthChromaMinus8 = data.readUnsignedExpGolombCodedInt();
      data.skipBit(); // qpprime_y_zero_transform_bypass_flag
      boolean seqScalingMatrixPresentFlag = data.readBit();
      if (seqScalingMatrixPresentFlag) {
        int limit = (chromaFormatIdc != 3) ? 8 : 12;
        for (int i = 0; i < limit; i++) {
          boolean seqScalingListPresentFlag = data.readBit();
          if (seqScalingListPresentFlag) {
            skipScalingList(data, i < 6 ? 16 : 64);
          }
        }
      }
    }

    int frameNumLength = data.readUnsignedExpGolombCodedInt() + 4; // log2_max_frame_num_minus4 + 4
    int picOrderCntType = data.readUnsignedExpGolombCodedInt();
    int picOrderCntLsbLength = 0;
    boolean deltaPicOrderAlwaysZeroFlag = false;
    if (picOrderCntType == 0) {
      // log2_max_pic_order_cnt_lsb_minus4 + 4
      picOrderCntLsbLength = data.readUnsignedExpGolombCodedInt() + 4;
    } else if (picOrderCntType == 1) {
      deltaPicOrderAlwaysZeroFlag = data.readBit(); // delta_pic_order_always_zero_flag
      data.readSignedExpGolombCodedInt(); // offset_for_non_ref_pic
      data.readSignedExpGolombCodedInt(); // offset_for_top_to_bottom_field
      long numRefFramesInPicOrderCntCycle = data.readUnsignedExpGolombCodedInt();
      for (int i = 0; i < numRefFramesInPicOrderCntCycle; i++) {
        data.readUnsignedExpGolombCodedInt(); // offset_for_ref_frame[i]
      }
    }
    int maxNumRefFrames = data.readUnsignedExpGolombCodedInt(); // max_num_ref_frames
    data.skipBit(); // gaps_in_frame_num_value_allowed_flag

    int picWidthInMbs = data.readUnsignedExpGolombCodedInt() + 1;
    int picHeightInMapUnits = data.readUnsignedExpGolombCodedInt() + 1;
    boolean frameMbsOnlyFlag = data.readBit();
    int frameHeightInMbs = (2 - (frameMbsOnlyFlag ? 1 : 0)) * picHeightInMapUnits;
    if (!frameMbsOnlyFlag) {
      data.skipBit(); // mb_adaptive_frame_field_flag
    }

    data.skipBit(); // direct_8x8_inference_flag
    int frameWidth = picWidthInMbs * 16;
    int frameHeight = frameHeightInMbs * 16;
    boolean frameCroppingFlag = data.readBit();
    if (frameCroppingFlag) {
      int frameCropLeftOffset = data.readUnsignedExpGolombCodedInt();
      int frameCropRightOffset = data.readUnsignedExpGolombCodedInt();
      int frameCropTopOffset = data.readUnsignedExpGolombCodedInt();
      int frameCropBottomOffset = data.readUnsignedExpGolombCodedInt();
      int cropUnitX;
      int cropUnitY;
      if (chromaFormatIdc == 0) {
        cropUnitX = 1;
        cropUnitY = 2 - (frameMbsOnlyFlag ? 1 : 0);
      } else {
        int subWidthC = (chromaFormatIdc == 3) ? 1 : 2;
        int subHeightC = (chromaFormatIdc == 1) ? 2 : 1;
        cropUnitX = subWidthC;
        cropUnitY = subHeightC * (2 - (frameMbsOnlyFlag ? 1 : 0));
      }
      frameWidth -= (frameCropLeftOffset + frameCropRightOffset) * cropUnitX;
      frameHeight -= (frameCropTopOffset + frameCropBottomOffset) * cropUnitY;
    }

    @C.ColorSpace int colorSpace = Format.NO_VALUE;
    @C.ColorRange int colorRange = Format.NO_VALUE;
    @C.ColorTransfer int colorTransfer = Format.NO_VALUE;
    float pixelWidthHeightRatio = 1;
    // Initialize to the default value defined in section E.2.1 of the H.264 spec. Precisely
    // calculating MaxDpbFrames is complicated, so we short-circuit to the max value of 16 here
    // instead.
    int maxNumReorderFrames =
        (profileIdc == 44
                    || profileIdc == 86
                    || profileIdc == 100
                    || profileIdc == 110
                    || profileIdc == 122
                    || profileIdc == 244)
                && ((constraintsFlagsAndReservedZero2Bits & 0x10) != 0)
            ? 0
            : 16;
    if (data.readBit()) { // vui_parameters_present_flag
      // Section E.1.1: VUI parameters syntax
      boolean aspectRatioInfoPresentFlag = data.readBit();
      if (aspectRatioInfoPresentFlag) {
        int aspectRatioIdc = data.readBits(8);
        if (aspectRatioIdc == NalUnitUtil.EXTENDED_SAR) {
          int sarWidth = data.readBits(16);
          int sarHeight = data.readBits(16);
          if (sarWidth != 0 && sarHeight != 0) {
            pixelWidthHeightRatio = (float) sarWidth / sarHeight;
          }
        } else if (aspectRatioIdc < NalUnitUtil.ASPECT_RATIO_IDC_VALUES.length) {
          pixelWidthHeightRatio = NalUnitUtil.ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
        } else {
          Log.w(TAG, "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
        }
      }
      if (data.readBit()) { // overscan_info_present_flag
        data.skipBit(); // overscan_appropriate_flag
      }
      if (data.readBit()) { // video_signal_type_present_flag
        data.skipBits(3); // video_format
        colorRange =
            data.readBit() ? C.COLOR_RANGE_FULL : C.COLOR_RANGE_LIMITED; // video_full_range_flag
        if (data.readBit()) { // colour_description_present_flag
          int colorPrimaries = data.readBits(8); // colour_primaries
          int transferCharacteristics = data.readBits(8); // transfer_characteristics
          data.skipBits(8); // matrix_coeffs

          colorSpace = ColorInfo.isoColorPrimariesToColorSpace(colorPrimaries);
          colorTransfer =
              ColorInfo.isoTransferCharacteristicsToColorTransfer(transferCharacteristics);
        }
      }
      if (data.readBit()) { // chroma_loc_info_present_flag
        data.readUnsignedExpGolombCodedInt(); // chroma_sample_loc_type_top_field
        data.readUnsignedExpGolombCodedInt(); // chroma_sample_loc_type_bottom_field
      }
      if (data.readBit()) { // timing_info_present_flag
        data.skipBits(65); // num_units_in_tick (32), time_scale (32), fixed_frame_rate_flag (1)
      }
      boolean nalHrdParametersPresent = data.readBit(); // nal_hrd_parameters_present_flag
      if (nalHrdParametersPresent) {
        skipHrdParameters(data);
      }
      boolean vclHrdParametersPresent = data.readBit(); // vcl_hrd_parameters_present_flag
      if (vclHrdParametersPresent) {
        skipHrdParameters(data);
      }
      if (nalHrdParametersPresent || vclHrdParametersPresent) {
        data.skipBit(); // low_delay_hrd_flag
      }
      data.skipBit(); // pic_struct_present_flag
      if (data.readBit()) { // bitstream_restriction_flag
        data.skipBit(); // motion_vectors_over_pic_boundaries_flag
        data.readUnsignedExpGolombCodedInt(); // max_bytes_per_pic_denom
        data.readUnsignedExpGolombCodedInt(); // max_bits_per_mb_denom
        data.readUnsignedExpGolombCodedInt(); // log2_max_mv_length_horizontal
        data.readUnsignedExpGolombCodedInt(); // log2_max_mv_length_vertical
        maxNumReorderFrames = data.readUnsignedExpGolombCodedInt(); // max_num_reorder_frames
        data.readUnsignedExpGolombCodedInt(); // max_dec_frame_buffering
      }
    }

    return new SpsData(
        profileIdc,
        constraintsFlagsAndReservedZero2Bits,
        levelIdc,
        seqParameterSetId,
        maxNumRefFrames,
        frameWidth,
        frameHeight,
        pixelWidthHeightRatio,
        bitDepthLumaMinus8,
        bitDepthChromaMinus8,
        separateColorPlaneFlag,
        frameMbsOnlyFlag,
        frameNumLength,
        picOrderCntType,
        picOrderCntLsbLength,
        deltaPicOrderAlwaysZeroFlag,
        colorSpace,
        colorRange,
        colorTransfer,
        maxNumReorderFrames);
  }

  /**
   * Parses a H.265 VPS NAL unit using the syntax defined in ITU-T Recommendation H.265 (2019)
   * subsections 7.3.2.1 and F.7.3.2.1.
   *
   * @param nalData A buffer containing escaped VPS data.
   * @param nalOffset The offset of the NAL unit header in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the VPS data.
   */
  public static H265VpsData parseH265VpsNalUnit(byte[] nalData, int nalOffset, int nalLimit) {
    ParsableNalUnitBitArray data = new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit);
    H265NalHeader nalHeader = parseH265NalHeader(data);
    return parseH265VpsNalUnitPayload(data, nalHeader);
  }

  /**
   * Parses and returns a H.265 NAL unit header using the syntax defined in ITU-T Recommendation
   * H.265 (2019) subsection 7.3.1.2. This function updates the ParsableNalUnitBitArray data with
   * new byte and bit offsets that point to the end of the nal_unit_header().
   */
  private static H265NalHeader parseH265NalHeader(ParsableNalUnitBitArray data) {
    // For HEVC and L-HEVC, the layer ID and temporal layer ID can be extracted from the 2 byte
    // nal_unit_header().
    data.skipBit(); // forbidden_zero_bit
    int nalUnitType = data.readBits(6); // nal_unit_type
    int layerId = data.readBits(6); // nuh_layer_id
    int temporalId = data.readBits(3) - 1; // nuh_temporal_id_plus1
    return new H265NalHeader(nalUnitType, layerId, temporalId);
  }

  /**
   * Parses a H.265 VPS NAL unit payload (excluding the NAL unit header) using the syntax defined in
   * ITU-T Recommendation H.265 (2019) subsections 7.3.2.1 and F.7.3.2.1.
   */
  private static H265VpsData parseH265VpsNalUnitPayload(
      ParsableNalUnitBitArray data, H265NalHeader nalHeader) {
    data.skipBits(4); // vps_video_parameter_set_id
    boolean baseLayerInternalFlag = data.readBit(); // vps_base_layer_internal_flag
    boolean baseLayerAvailableFlag = data.readBit(); // vps_base_layer_available_flag
    int maxLayers = data.readBits(6) + 1; // vps_max_layers_minus1

    int maxSubLayersMinus1 = data.readBits(3); // vps_max_sub_layers_minus1
    data.skipBits(17); // vps_temporal_id_nesting_flag, vps_reserved_0xffff_16bits

    H265ProfileTierLevel profileTierLevel =
        parseH265ProfileTierLevel(
            data,
            /* profilePresentFlag= */ true,
            maxSubLayersMinus1,
            /* prevProfileTierLevel= */ null);

    // for (i = vps_sub_layer_ordering_info_present_flag ? 0 : vps_max_sub_layers_minus1; ...)
    for (int i = data.readBit() ? 0 : maxSubLayersMinus1; i <= maxSubLayersMinus1; i++) {
      data.readUnsignedExpGolombCodedInt(); // vps_max_dec_pic_buffering_minus1[i]
      data.readUnsignedExpGolombCodedInt(); // vps_max_num_reorder_pics[i]
      data.readUnsignedExpGolombCodedInt(); // vps_max_latency_increase_plus1[i]
    }

    int maxLayerId = data.readBits(6); // vps_max_layer_id
    int numLayerSets = data.readUnsignedExpGolombCodedInt() + 1; // vps_num_layer_sets_minus1

    ImmutableList<H265ProfileTierLevel> profileTierLevels = ImmutableList.of(profileTierLevel);
    H265ProfileTierLevelsAndIndices baseLayerProfileTierLevelsAndIndices =
        new H265ProfileTierLevelsAndIndices(profileTierLevels, new int[1]);

    // Given that the first layer set is dedicated for the base layer, numLayerSets must be greater
    // than 1 for the L-HEVC case.
    boolean haveEnoughLayerSets = maxLayers >= 2 && numLayerSets >= 2;
    // This implementation only supports the case where the base layer is included within the L-HEVC
    // bitstream.
    boolean baseLayerIncluded = baseLayerInternalFlag && baseLayerAvailableFlag;
    // The NAL unit header's layer ID, nuh_layer_id, (which doesn't need to be sequential) better
    // have enough range to cover the specified max number of layers.
    boolean haveLargeEnoughMaxLayerIdInNuh = maxLayerId + 1 >= maxLayers;
    if (!haveEnoughLayerSets || !baseLayerIncluded || !haveLargeEnoughMaxLayerIdInNuh) {
      // Fallback to single layer HEVC.
      return new H265VpsData(
          nalHeader,
          /* layerInfos= */ null,
          baseLayerProfileTierLevelsAndIndices,
          /* repFormatsAndIndices= */ null,
          /* videoSignalInfosAndIndices= */ null);
    }

    // Define each layer set information: list of layer IDs, number of layers, and max layer ID.
    int[][] layerSetLayerIdList = new int[numLayerSets][maxLayerId + 1];
    int[] numLayersInIdList = new int[numLayerSets];
    int[] layerSetMaxLayerId = new int[numLayerSets];
    // The first layer set is comprised of only the base layer.
    layerSetLayerIdList[0][0] = 0;
    numLayersInIdList[0] = 1;
    layerSetMaxLayerId[0] = 0;
    // Define other layer sets.
    for (int i = 1; i < numLayerSets; i++) {
      int n = 0;
      for (int j = 0; j <= maxLayerId; j++) {
        if (data.readBit()) { // layer_id_included_flag[i][j]
          layerSetLayerIdList[i][n++] = j;
          layerSetMaxLayerId[i] = j;
        }
        numLayersInIdList[i] = n;
      }
    }

    if (data.readBit()) { // vps_timing_info_present_flag
      data.skipBits(64); // vps_num_units_in_tick, vps_time_scale
      if (data.readBit()) { // vps_poc_proportional_to_timing_flag
        data.readUnsignedExpGolombCodedInt(); // vps_num_ticks_poc_diff_one_minus1
      }
      int numHrdParameters = data.readUnsignedExpGolombCodedInt(); // vps_num_hrd_parameters
      for (int i = 0; i < numHrdParameters; i++) {
        data.readUnsignedExpGolombCodedInt(); // hrd_layer_set_idx[i]
        skipH265HrdParameters(
            data, /* commonInfPresentFlag= */ i == 0 || data.readBit(), maxSubLayersMinus1);
      }
    }

    // For L-HEVC, vps_extension() needs to be parsed.
    if (!data.readBit()) { // vps_extension_flag
      // If no vps_extension(), then fallback to single layer HEVC.
      return new H265VpsData(
          nalHeader,
          /* layerInfos= */ null,
          baseLayerProfileTierLevelsAndIndices,
          /* repFormatsAndIndices= */ null,
          /* videoSignalInfosAndIndices= */ null);
    }

    data.byteAlign();

    // Parsing vps_extension().
    H265ProfileTierLevel baseLayerProfileTierLevel =
        parseH265ProfileTierLevel(
            data, /* profilePresentFlag= */ false, maxSubLayersMinus1, profileTierLevel);

    boolean splittingFlag = data.readBit(); // splitting_flag
    boolean[] scalabilityMaskFlag = new boolean[16];
    int numScalabilityTypes = 0;
    for (int i = 0; i < 16; i++) {
      scalabilityMaskFlag[i] = data.readBit(); // scalability_mask_flag[i]
      if (scalabilityMaskFlag[i]) {
        numScalabilityTypes++;
      }
    }
    // As listed in Table F.1 of the spec, numScalabilityTypes indicates the number of different
    // scalability dimensions.  If there is no scalability dimension, then we simply have a
    // single-layer HEVC.  Of the 16 scalability dimensions, dimension 1 is used for multiview;
    // currently only the multiview case is supported.
    if (numScalabilityTypes == 0 || !scalabilityMaskFlag[1]) {
      return new H265VpsData(
          nalHeader,
          /* layerInfos= */ null,
          baseLayerProfileTierLevelsAndIndices,
          /* repFormatsAndIndices= */ null,
          /* videoSignalInfosAndIndices= */ null);
    }

    int[] dimensionIdLenMinus1 = new int[numScalabilityTypes];
    for (int i = 0; i < numScalabilityTypes - (splittingFlag ? 1 : 0); i++) {
      dimensionIdLenMinus1[i] = data.readBits(3); // dimension_id_len_minus1[i]
    }
    int[] dimBitOffset = new int[numScalabilityTypes + 1];
    if (splittingFlag) {
      for (int i = 1; i < numScalabilityTypes; i++) {
        for (int j = 0; j < i; j++) {
          dimBitOffset[i] += dimensionIdLenMinus1[j] + 1;
        }
      }
      dimBitOffset[numScalabilityTypes] = 6;
    }

    int[][] dimensionId = new int[maxLayers][numScalabilityTypes];
    // Get layerIdInNuh that maps the layer ID used in this VPS to the NAL unit header's parsed
    // layer ID - nuh_layer_id.
    int[] layerIdInNuh = new int[maxLayers];
    layerIdInNuh[0] = 0;
    boolean nuhLayerIdPresentFlag = data.readBit(); // vps_nuh_layer_id_present_flag
    for (int i = 1; i < maxLayers; i++) {
      if (nuhLayerIdPresentFlag) {
        layerIdInNuh[i] = data.readBits(6); // layer_id_in_nuh[i]
      } else {
        layerIdInNuh[i] = i;
      }
      if (!splittingFlag) {
        for (int j = 0; j < numScalabilityTypes; j++) {
          dimensionId[i][j] = data.readBits(dimensionIdLenMinus1[j] + 1); // dimension_id[i][j]
        }
      } else {
        for (int j = 0; j < numScalabilityTypes; j++) {
          dimensionId[i][j] =
              (layerIdInNuh[i] & ((1 << dimBitOffset[j + 1]) - 1)) >> dimBitOffset[j];
        }
      }
    }

    // Derive viewOrderIdx[] and numViews following (F-3) in subsection F.7.4.3.1.1.
    int[] viewOrderIdx = new int[maxLayerId + 1];
    int numViews = 1;
    for (int i = 0; i < maxLayers; i++) {
      viewOrderIdx[layerIdInNuh[i]] = C.INDEX_UNSET;
      // for (smId x= 0, j = 0; smIdx < 16; ...)
      for (int scalabilityMaskFlagIndex = 0, j = 0;
          scalabilityMaskFlagIndex < 16;
          scalabilityMaskFlagIndex++) {
        if (scalabilityMaskFlag[scalabilityMaskFlagIndex]) {
          if (scalabilityMaskFlagIndex == 1) { // multiview
            // Note that viewOrderIdx is expected to be an index as it is used to access
            // view_id_val[]; however, dimensionId[i][j] is not expected to follow the index
            // constraint.  It is up to the encoder to ensure that the dimensionId[i][j] is
            // consistent with the use of viewOrderIdx.
            viewOrderIdx[layerIdInNuh[i]] = dimensionId[i][j];
          }
          j++;
        }
      }
      if (i > 0) {
        boolean newView = true;
        for (int j = 0; j < i; j++) {
          if (viewOrderIdx[layerIdInNuh[i]] == viewOrderIdx[layerIdInNuh[j]]) {
            newView = false;
            break;
          }
        }
        if (newView) {
          numViews++;
        }
      }
    }

    int viewIdLen = data.readBits(4); // view_id_len
    if (numViews < 2 || viewIdLen == 0) {
      // This means all views have viewId of 0, so no multiview effect.
      return new H265VpsData(
          nalHeader,
          /* layerInfos= */ null,
          baseLayerProfileTierLevelsAndIndices,
          /* repFormatsAndIndices= */ null,
          /* videoSignalInfosAndIndices= */ null);
    }
    int[] viewIdVals = new int[numViews];
    for (int i = 0; i < numViews; i++) {
      viewIdVals[i] = data.readBits(viewIdLen); // view_id_val[i]
    }

    int[] layerIdInVps = new int[maxLayerId + 1];
    for (int i = 0; i < maxLayers; i++) {
      layerIdInVps[min(layerIdInNuh[i], maxLayerId)] = i;
    }
    ImmutableList.Builder<H265LayerInfo> layerInfosBuilder = ImmutableList.builder();
    for (int i = 0; i <= maxLayerId; i++) {
      int viewIdValIdx = min(viewOrderIdx[i], numViews - 1);
      int viewIdVal = viewIdValIdx >= 0 ? viewIdVals[viewIdValIdx] : INVALID_ID;
      layerInfosBuilder.add(new H265LayerInfo(layerIdInVps[i], viewIdVal));
    }
    ImmutableList<H265LayerInfo> layerInfos = layerInfosBuilder.build();

    if (layerInfos.get(0).viewId == INVALID_ID) {
      // The base layer must be the primary view; fallback to single layer HEVC.
      return new H265VpsData(
          nalHeader,
          /* layerInfos= */ null,
          baseLayerProfileTierLevelsAndIndices,
          /* repFormatsAndIndices= */ null,
          /* videoSignalInfosAndIndices= */ null);
    }
    int secondaryViewLayerId = INVALID_ID;
    for (int i = 1; i <= maxLayerId; i++) {
      if (layerInfos.get(i).viewId != INVALID_ID) {
        secondaryViewLayerId = i;
        break;
      }
    }
    if (secondaryViewLayerId == INVALID_ID) {
      // No secondary view defined; fallback to single layer HEVC.
      return new H265VpsData(
          nalHeader,
          /* layerInfos= */ null,
          baseLayerProfileTierLevelsAndIndices,
          /* repFormatsAndIndices= */ null,
          /* videoSignalInfosAndIndices= */ null);
    }

    // Derive H.265 layer dependency structure following (F-4) in subsection F.7.4.3.1.1.
    boolean[][] directDependencyFlag = new boolean[maxLayers][maxLayers];
    boolean[][] dependencyFlag = new boolean[maxLayers][maxLayers];
    for (int i = 1; i < maxLayers; i++) {
      for (int j = 0; j < i; j++) {
        directDependencyFlag[i][j] =
            dependencyFlag[i][j] = data.readBit(); // direct_dependency_flag[i][j]
      }
    }
    for (int i = 1; i < maxLayers; i++) {
      for (int j = 0; j < maxLayers - 1; j++) {
        for (int k = 0; k < i; k++) {
          if (dependencyFlag[i][k] && dependencyFlag[k][j]) {
            dependencyFlag[i][j] = true;
            break;
          }
        }
      }
    }

    // Derive numDirectRefLayers following (F-5) in subsection F.7.4.3.1.1.
    int[] numDirectRefLayers = new int[maxLayerId + 1];
    for (int i = 0; i < maxLayers; i++) {
      int d = 0;
      for (int j = 0; j < i; j++) {
        d += directDependencyFlag[i][j] ? 1 : 0;
      }
      numDirectRefLayers[layerIdInNuh[i]] = d;
    }
    // Derive numIndependentLayers following (F-6) in subsection F.7.4.3.1.1.
    int numIndependentLayers = 0;
    for (int i = 0; i < maxLayers; i++) {
      if (numDirectRefLayers[layerIdInNuh[i]] == 0) {
        numIndependentLayers++;
      }
    }
    if (numIndependentLayers > 1) {
      // Current implementation only supports one independent layer - the base layer.
      return new H265VpsData(
          nalHeader,
          /* layerInfos= */ null,
          baseLayerProfileTierLevelsAndIndices,
          /* repFormatsAndIndices= */ null,
          /* videoSignalInfosAndIndices= */ null);
    }

    // Since only one independent layer is expected, num_add_layer_sets is implied to be 0.

    int[] subLayersVpsMaxMinus1 = new int[maxLayers];
    int[] maxSubLayersInLayerSet = new int[numLayerSets];
    if (data.readBit()) { // vps_sub_layers_max_minus1_present_flag
      for (int i = 0; i < maxLayers; i++) {
        subLayersVpsMaxMinus1[i] = data.readBits(3); // sub_layers_vps_max_minus1[i]
      }
    } else {
      Arrays.fill(subLayersVpsMaxMinus1, 0, maxLayers, maxSubLayersMinus1);
    }
    for (int i = 0; i < numLayerSets; i++) {
      int layerSetMaxSubLayersMinus1 = 0; // maxSlMinus1
      for (int k = 0; k < numLayersInIdList[i]; k++) {
        int layerId = layerSetLayerIdList[i][k];
        layerSetMaxSubLayersMinus1 =
            max(
                layerSetMaxSubLayersMinus1,
                subLayersVpsMaxMinus1[layerInfos.get(layerId).layerIdInVps]);
      }
      maxSubLayersInLayerSet[i] = layerSetMaxSubLayersMinus1 + 1;
    }

    if (data.readBit()) { // max_tid_ref_present_flag
      for (int i = 0; i < maxLayers - 1; i++) {
        for (int j = i + 1; j < maxLayers; j++) {
          if (directDependencyFlag[j][i]) {
            data.skipBits(3); // max_tid_il_ref_pics_plus1[i][j]
          }
        }
      }
    }
    data.skipBit(); // default_ref_layers_active_flag

    // Get profile_tier_level()s needed for non-base layer.
    int numProfileTierLevels = data.readUnsignedExpGolombCodedInt() + 1;
    ImmutableList.Builder<H265ProfileTierLevel> profileTierLevelsBuilder = ImmutableList.builder();
    profileTierLevelsBuilder.add(profileTierLevel);
    if (numProfileTierLevels > 1) {
      profileTierLevelsBuilder.add(baseLayerProfileTierLevel);
      H265ProfileTierLevel prevProfileTierLevel = baseLayerProfileTierLevel;
      for (int i = 2; i < numProfileTierLevels; i++) {
        H265ProfileTierLevel nextProfileTierLevel =
            parseH265ProfileTierLevel(
                data,
                /* profilePresentFlag= */ data.readBit(),
                maxSubLayersMinus1,
                prevProfileTierLevel);
        profileTierLevelsBuilder.add(nextProfileTierLevel);
        prevProfileTierLevel = nextProfileTierLevel;
      }
    }
    profileTierLevels = profileTierLevelsBuilder.build();

    // Define output layer sets.

    int numOutputLayerSets = numLayerSets + data.readUnsignedExpGolombCodedInt(); // num_add_olss
    if (numOutputLayerSets > numLayerSets) {
      // Current implementation only supports the output layer set being the same as the layer set.
      // Fallback to single layer HEVC if the constraint is not met.
      return new H265VpsData(
          nalHeader,
          /* layerInfos= */ null,
          baseLayerProfileTierLevelsAndIndices,
          /* repFormatsAndIndices= */ null,
          /* videoSignalInfosAndIndices= */ null);
    }

    int defaultOutputLayerIdc = data.readBits(2); // default_output_layer_idc

    boolean[][] outputLayerFlag = new boolean[numOutputLayerSets][maxLayerId + 1];
    int[] numOutputLayersInOutputLayerSet = new int[numOutputLayerSets];
    int[] olsHighestOutputLayerId = new int[numOutputLayerSets];
    for (int i = 0; i < numLayerSets; i++) {
      numOutputLayersInOutputLayerSet[i] = 0;
      olsHighestOutputLayerId[i] = layerSetMaxLayerId[i];
      if (defaultOutputLayerIdc == 0) {
        Arrays.fill(outputLayerFlag[i], 0, numLayersInIdList[i], true);
        numOutputLayersInOutputLayerSet[i] = numLayersInIdList[i];
      } else if (defaultOutputLayerIdc == 1) {
        int highestLayerId = layerSetMaxLayerId[i];
        for (int j = 0; j < numLayersInIdList[i]; j++) {
          outputLayerFlag[i][j] = layerSetLayerIdList[i][j] == highestLayerId;
        }
        numOutputLayersInOutputLayerSet[i] = 1;
      } else {
        outputLayerFlag[0][0] = true;
        numOutputLayersInOutputLayerSet[0] = 1;
      }
    }

    int[] profileTierLevelIndices = new int[maxLayerId + 1];
    boolean[][] necessaryLayerFlag = new boolean[numOutputLayerSets][maxLayerId + 1];
    int targetOutputLayerSetIdx = 0;

    for (int i = 1; i < numOutputLayerSets; i++) {
      if (defaultOutputLayerIdc == 2) {
        for (int j = 0; j < numLayersInIdList[i]; j++) {
          outputLayerFlag[i][j] = data.readBit(); // output_layer_flag[i][j]
          numOutputLayersInOutputLayerSet[i] += outputLayerFlag[i][j] ? 1 : 0;
          if (outputLayerFlag[i][j]) {
            olsHighestOutputLayerId[i] = layerSetLayerIdList[i][j];
          }
        }
      }

      // Look for the first output layer set that includes the base layer (primary view) and the
      // layer with the secondary view.
      if (targetOutputLayerSetIdx == 0 && layerSetLayerIdList[i][0] == 0 && outputLayerFlag[i][0]) {
        for (int j = 1; j < numLayersInIdList[i]; j++) {
          if (layerSetLayerIdList[i][j] == secondaryViewLayerId
              && outputLayerFlag[i][secondaryViewLayerId]) {
            targetOutputLayerSetIdx = i;
          }
        }
      }

      for (int j = 0; j < numLayersInIdList[i]; j++) {
        if (numProfileTierLevels > 1) {
          necessaryLayerFlag[i][j] = outputLayerFlag[i][j];
          int bitLen = log2(numProfileTierLevels, RoundingMode.CEILING);
          if (!necessaryLayerFlag[i][j]) {
            int currLayerIdInVps = layerInfos.get(layerSetLayerIdList[i][j]).layerIdInVps;
            for (int k = 0; k < j; k++) {
              int refLayerIdInVps = layerInfos.get(layerSetLayerIdList[i][k]).layerIdInVps;
              if (dependencyFlag[currLayerIdInVps][refLayerIdInVps]) {
                necessaryLayerFlag[i][j] = true;
                break;
              }
            }
          }
          if (necessaryLayerFlag[i][j]) {
            if (targetOutputLayerSetIdx > 0 && i == targetOutputLayerSetIdx) {
              // Only store the information needed for the target output layer set.
              profileTierLevelIndices[j] = data.readBits(bitLen); // profile_tier_level_idx[i][j]
            } else {
              data.skipBits(bitLen); // profile_tier_level_idx[i][j]
            }
          }
        }
      }
      if (numOutputLayersInOutputLayerSet[i] == 1
          && numDirectRefLayers[olsHighestOutputLayerId[i]] > 0) {
        data.skipBit(); // alt_output_layer_flag[i]
      }
    }

    if (targetOutputLayerSetIdx == 0) {
      // The selected target output layer set only contains the base layer.
      return new H265VpsData(
          nalHeader,
          /* layerInfos= */ null,
          baseLayerProfileTierLevelsAndIndices,
          /* repFormatsAndIndices= */ null,
          /* videoSignalInfosAndIndices= */ null);
    }

    H265RepFormatsAndIndices repFormatsAndIndices = parseH265RepFormatsAndIndices(data, maxLayers);

    data.skipBits(2); // max_one_active_ref_layer_flag, vps_poc_lsb_aligned_flag
    for (int i = 1; i < maxLayers; i++) {
      if (numDirectRefLayers[layerIdInNuh[i]] == 0) {
        data.skipBit(); // poc_lsb_not_present_flag[i]
      }
    }

    skipH265DpbSize(
        data, numOutputLayerSets, maxSubLayersInLayerSet, numLayersInIdList, necessaryLayerFlag);

    skipToH265VuiPresentFlagAfterDpbSize(data, maxLayers, directDependencyFlag);

    H265VideoSignalInfosAndIndices videoSignalInfosAndIndices = null;
    if (data.readBit()) { // vps_vui_present_flag
      data.byteAlign();
      videoSignalInfosAndIndices =
          parseH265VideoSignalInfosAndIndices(
              data, maxLayers, numLayerSets, maxSubLayersInLayerSet);
    }

    return new H265VpsData(
        nalHeader,
        layerInfos,
        new H265ProfileTierLevelsAndIndices(profileTierLevels, profileTierLevelIndices),
        repFormatsAndIndices,
        videoSignalInfosAndIndices);
  }

  /**
   * Parses a H.265 SPS NAL unit using the syntax defined in ITU-T Recommendation H.265 (2019)
   * subsections 7.3.2.2.1 and F.7.3.2.2.1.
   *
   * @param nalData A buffer containing escaped SPS data.
   * @param nalOffset The offset of the NAL unit header in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @param vpsData The VPS that the SPS refers to or {@code null} if unavailable.
   * @return A parsed representation of the SPS data.
   */
  public static H265SpsData parseH265SpsNalUnit(
      byte[] nalData, int nalOffset, int nalLimit, @Nullable H265VpsData vpsData) {
    H265NalHeader nalHeader =
        parseH265NalHeader(new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit));
    // The nal_unit_header() - nalHeader - is comprised of 2 bytes.
    return parseH265SpsNalUnitPayload(nalData, nalOffset + 2, nalLimit, nalHeader, vpsData);
  }

  /**
   * Parses a H.265 SPS NAL unit payload (excluding the NAL unit header) using the syntax defined in
   * ITU-T Recommendation H.265 (2019) subsections 7.3.2.2.1 and F.7.3.2.2.1.
   *
   * @param nalData A buffer containing escaped SPS data.
   * @param nalOffset The offset of the NAL unit payload in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @param nalHeader The parsed representation of the NAL header.
   * @param vpsData The VPS that the SPS refers to or {@code null} if unavailable.
   * @return A parsed representation of the SPS data.
   */
  public static H265SpsData parseH265SpsNalUnitPayload(
      byte[] nalData,
      int nalOffset,
      int nalLimit,
      H265NalHeader nalHeader,
      @Nullable H265VpsData vpsData) {
    ParsableNalUnitBitArray data = new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit);
    data.skipBits(4); // sps_video_parameter_set_id
    // Represents sps_max_sub_layers_minus1 (when nuh_layer_id == 0) or
    // sps_ext_or_max_sub_layers_minus1 (when nuh_layer_id != 0).
    int maxSubLayersMinus1 = data.readBits(3);
    boolean multiLayerExtSpsFlag = nalHeader.layerId != 0 && maxSubLayersMinus1 == 7;

    int layerIdInVps = 0;
    if (vpsData != null && !vpsData.layerInfos.isEmpty()) {
      int layerId = min(nalHeader.layerId, vpsData.layerInfos.size() - 1);
      layerIdInVps = vpsData.layerInfos.get(layerId).layerIdInVps;
    }
    @Nullable H265ProfileTierLevel profileTierLevel = null;
    if (!multiLayerExtSpsFlag) {
      data.skipBit(); // sps_temporal_id_nesting_flag
      profileTierLevel =
          parseH265ProfileTierLevel(
              data,
              /* profilePresentFlag= */ true,
              maxSubLayersMinus1,
              /* prevProfileTierLevel= */ null);
    } else if (vpsData != null) {
      int profileTierLevelIdx = vpsData.profileTierLevelsAndIndices.indices[layerIdInVps];
      if (vpsData.profileTierLevelsAndIndices.profileTierLevels.size() > profileTierLevelIdx) {
        profileTierLevel =
            vpsData.profileTierLevelsAndIndices.profileTierLevels.get(profileTierLevelIdx);
      }
    }

    int seqParameterSetId = data.readUnsignedExpGolombCodedInt();
    int chromaFormatIdc = 0;
    int frameWidth = 0;
    int frameHeight = 0;
    int bitDepthLumaMinus8 = 0;
    int bitDepthChromaMinus8 = 0;
    int spsRepFormatIdx = C.INDEX_UNSET;
    if (multiLayerExtSpsFlag) {
      if (data.readBit()) { // update_rep_format_flag
        spsRepFormatIdx = data.readBits(8); // sps_rep_format_idx
      }
      if (vpsData != null && vpsData.repFormatsAndIndices != null) {
        if (spsRepFormatIdx == C.INDEX_UNSET) {
          spsRepFormatIdx = vpsData.repFormatsAndIndices.indices[layerIdInVps];
        }
        if (spsRepFormatIdx != C.INDEX_UNSET
            && vpsData.repFormatsAndIndices.repFormats.size() > spsRepFormatIdx) {
          H265RepFormat repFormat = vpsData.repFormatsAndIndices.repFormats.get(spsRepFormatIdx);
          chromaFormatIdc = repFormat.chromaFormatIdc;
          frameWidth = repFormat.width;
          frameHeight = repFormat.height;
          bitDepthLumaMinus8 = repFormat.bitDepthLumaMinus8;
          bitDepthChromaMinus8 = repFormat.bitDepthChromaMinus8;
        }
      }
    } else {
      chromaFormatIdc = data.readUnsignedExpGolombCodedInt();
      if (chromaFormatIdc == 3) {
        data.skipBit(); // separate_colour_plane_flag
      }
      frameWidth = data.readUnsignedExpGolombCodedInt();
      frameHeight = data.readUnsignedExpGolombCodedInt();
      if (data.readBit()) { // conformance_window_flag
        int confWinLeftOffset = data.readUnsignedExpGolombCodedInt();
        int confWinRightOffset = data.readUnsignedExpGolombCodedInt();
        int confWinTopOffset = data.readUnsignedExpGolombCodedInt();
        int confWinBottomOffset = data.readUnsignedExpGolombCodedInt();
        frameWidth =
            applyConformanceWindowToWidth(
                frameWidth, chromaFormatIdc, confWinLeftOffset, confWinRightOffset);
        frameHeight =
            applyConformanceWindowToHeight(
                frameHeight, chromaFormatIdc, confWinTopOffset, confWinBottomOffset);
      }
      bitDepthLumaMinus8 = data.readUnsignedExpGolombCodedInt();
      bitDepthChromaMinus8 = data.readUnsignedExpGolombCodedInt();
    }
    int log2MaxPicOrderCntLsbMinus4 = data.readUnsignedExpGolombCodedInt();
    int maxNumReorderPics = -1;
    if (!multiLayerExtSpsFlag) {
      // for (i = sps_sub_layer_ordering_info_present_flag ? 0 : sps_max_sub_layers_minus1; ...)
      for (int i = data.readBit() ? 0 : maxSubLayersMinus1; i <= maxSubLayersMinus1; i++) {
        data.readUnsignedExpGolombCodedInt(); // sps_max_dec_pic_buffering_minus1[i]
        // sps_max_num_reorder_pics[i]
        maxNumReorderPics = max(data.readUnsignedExpGolombCodedInt(), maxNumReorderPics);
        data.readUnsignedExpGolombCodedInt(); // sps_max_latency_increase_plus1[i]
      }
    }
    data.readUnsignedExpGolombCodedInt(); // log2_min_luma_coding_block_size_minus3
    data.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_luma_coding_block_size
    data.readUnsignedExpGolombCodedInt(); // log2_min_luma_transform_block_size_minus2
    data.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_luma_transform_block_size
    data.readUnsignedExpGolombCodedInt(); // max_transform_hierarchy_depth_inter
    data.readUnsignedExpGolombCodedInt(); // max_transform_hierarchy_depth_intra
    if (data.readBit()) { // scaling_list_enabled_flag
      boolean inferScalingListFlag = false;
      if (multiLayerExtSpsFlag) {
        inferScalingListFlag = data.readBit(); // sps_infer_scaling_list_flag
      }
      if (inferScalingListFlag) {
        data.skipBits(6); // sps_scaling_list_ref_layer_id
      } else if (data.readBit()) { // sps_scaling_list_data_present_flag
        skipH265ScalingList(data);
      }
    }
    data.skipBits(2); // amp_enabled_flag (1), sample_adaptive_offset_enabled_flag (1)
    if (data.readBit()) { // pcm_enabled_flag
      // pcm_sample_bit_depth_luma_minus1 (4), pcm_sample_bit_depth_chroma_minus1 (4)
      data.skipBits(8);
      data.readUnsignedExpGolombCodedInt(); // log2_min_pcm_luma_coding_block_size_minus3
      data.readUnsignedExpGolombCodedInt(); // log2_diff_max_min_pcm_luma_coding_block_size
      data.skipBit(); // pcm_loop_filter_disabled_flag
    }
    skipH265ShortTermReferencePictureSets(data);
    if (data.readBit()) { // long_term_ref_pics_present_flag
      int numLongTermRefPicsSps = data.readUnsignedExpGolombCodedInt();
      for (int i = 0; i < numLongTermRefPicsSps; i++) {
        int ltRefPicPocLsbSpsLength = log2MaxPicOrderCntLsbMinus4 + 4;
        // lt_ref_pic_poc_lsb_sps[i], used_by_curr_pic_lt_sps_flag[i]
        data.skipBits(ltRefPicPocLsbSpsLength + 1);
      }
    }
    data.skipBits(2); // sps_temporal_mvp_enabled_flag, strong_intra_smoothing_enabled_flag
    @C.ColorSpace int colorSpace = Format.NO_VALUE;
    @C.ColorRange int colorRange = Format.NO_VALUE;
    @C.ColorTransfer int colorTransfer = Format.NO_VALUE;
    float pixelWidthHeightRatio = 1;
    if (data.readBit()) { // vui_parameters_present_flag
      if (data.readBit()) { // aspect_ratio_info_present_flag
        int aspectRatioIdc = data.readBits(8);
        if (aspectRatioIdc == NalUnitUtil.EXTENDED_SAR) {
          int sarWidth = data.readBits(16);
          int sarHeight = data.readBits(16);
          if (sarWidth != 0 && sarHeight != 0) {
            pixelWidthHeightRatio = (float) sarWidth / sarHeight;
          }
        } else if (aspectRatioIdc < NalUnitUtil.ASPECT_RATIO_IDC_VALUES.length) {
          pixelWidthHeightRatio = NalUnitUtil.ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
        } else {
          Log.w(TAG, "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
        }
      }
      if (data.readBit()) { // overscan_info_present_flag
        data.skipBit(); // overscan_appropriate_flag
      }
      if (data.readBit()) { // video_signal_type_present_flag
        data.skipBits(3); // video_format
        colorRange =
            data.readBit() ? C.COLOR_RANGE_FULL : C.COLOR_RANGE_LIMITED; // video_full_range_flag
        if (data.readBit()) { // colour_description_present_flag
          int colorPrimaries = data.readBits(8); // colour_primaries
          int transferCharacteristics = data.readBits(8); // transfer_characteristics
          data.skipBits(8); // matrix_coeffs

          colorSpace = ColorInfo.isoColorPrimariesToColorSpace(colorPrimaries);
          colorTransfer =
              ColorInfo.isoTransferCharacteristicsToColorTransfer(transferCharacteristics);
        }
      } else if (vpsData != null && vpsData.videoSignalInfosAndIndices != null) {
        int videoSignalInfoIdx = vpsData.videoSignalInfosAndIndices.indices[layerIdInVps];
        if (vpsData.videoSignalInfosAndIndices.videoSignalInfos.size() > videoSignalInfoIdx) {
          H265VideoSignalInfo videoSignalInfo =
              vpsData.videoSignalInfosAndIndices.videoSignalInfos.get(videoSignalInfoIdx);
          colorSpace = videoSignalInfo.colorSpace;
          colorRange = videoSignalInfo.colorRange;
          colorTransfer = videoSignalInfo.colorTransfer;
        }
      }
      if (data.readBit()) { // chroma_loc_info_present_flag
        data.readUnsignedExpGolombCodedInt(); // chroma_sample_loc_type_top_field
        data.readUnsignedExpGolombCodedInt(); // chroma_sample_loc_type_bottom_field
      }
      data.skipBit(); // neutral_chroma_indication_flag
      if (data.readBit()) { // field_seq_flag
        // field_seq_flag equal to 1 indicates that the coded video sequence conveys pictures that
        // represent fields, which means that frame height is double the picture height.
        frameHeight *= 2;
      }
    }

    return new H265SpsData(
        nalHeader,
        maxSubLayersMinus1,
        profileTierLevel,
        chromaFormatIdc,
        bitDepthLumaMinus8,
        bitDepthChromaMinus8,
        seqParameterSetId,
        frameWidth,
        frameHeight,
        pixelWidthHeightRatio,
        maxNumReorderPics,
        colorSpace,
        colorRange,
        colorTransfer);
  }

  /**
   * Parses a PPS NAL unit using the syntax defined in ITU-T Recommendation H.264 (2013) subsection
   * 7.3.2.2.
   *
   * @param nalData A buffer containing escaped PPS data.
   * @param nalOffset The offset of the NAL unit header in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the PPS data.
   */
  public static PpsData parsePpsNalUnit(byte[] nalData, int nalOffset, int nalLimit) {
    return parsePpsNalUnitPayload(nalData, nalOffset + 1, nalLimit);
  }

  /**
   * Parses a PPS NAL unit payload (excluding the NAL unit header) using the syntax defined in ITU-T
   * Recommendation H.264 (2013) subsection 7.3.2.2.
   *
   * @param nalData A buffer containing escaped PPS data.
   * @param nalOffset The offset of the NAL unit payload in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the PPS data.
   */
  public static PpsData parsePpsNalUnitPayload(byte[] nalData, int nalOffset, int nalLimit) {
    ParsableNalUnitBitArray data = new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit);
    int picParameterSetId = data.readUnsignedExpGolombCodedInt();
    int seqParameterSetId = data.readUnsignedExpGolombCodedInt();
    data.skipBit(); // entropy_coding_mode_flag
    boolean bottomFieldPicOrderInFramePresentFlag = data.readBit();
    return new PpsData(picParameterSetId, seqParameterSetId, bottomFieldPicOrderInFramePresentFlag);
  }

  /**
   * Parses a H.265 3D reference displays information SEI message syntax defined in ITU-T
   * Recommendation H.265 (2019) subsection G.14.2.3. Given a generic PREFIX_SEI NAL unit, only 3D
   * reference displays information SEI is parsed, if exists.
   *
   * @param nalData A buffer containing escaped prefix SEI data.
   * @param nalOffset The offset of the NAL unit header in {@code nalData}.
   * @param nalLimit The limit of the NAL unit in {@code nalData}.
   * @return A parsed representation of the PPS data.
   */
  @Nullable
  public static H265Sei3dRefDisplayInfoData parseH265Sei3dRefDisplayInfo(
      byte[] nalData, int nalOffset, int nalLimit) {

    int seiRbspPos = nalOffset + 2;
    int last1BitBytePos = nalLimit - 1;
    while (nalData[last1BitBytePos] == 0 && last1BitBytePos > seiRbspPos) {
      last1BitBytePos--;
    }
    if (nalData[last1BitBytePos] == 0 || last1BitBytePos <= seiRbspPos) {
      return null;
    }

    ParsableNalUnitBitArray data =
        new ParsableNalUnitBitArray(nalData, seiRbspPos, last1BitBytePos + 1);
    // Every SEI message must have at least 2 bytes for the payload type and size.
    while (data.canReadBits(16)) {
      // Parsing sei_message() in subsection 7.3.5.
      int payloadType = 0;
      int nextByte = data.readBits(8);
      while (nextByte == 255) {
        payloadType += 255;
        nextByte = data.readBits(8);
      }
      payloadType += nextByte;

      int payloadSize = 0;
      nextByte = data.readBits(8);
      while (nextByte == 255) {
        payloadSize += 255;
        nextByte = data.readBits(8);
      }
      payloadSize += nextByte;
      if (payloadSize == 0 || !data.canReadBits(payloadSize)) {
        return null;
      }

      if (payloadType == 176) { // three_dimensional_reference_displays_info()
        int precRefDisplayWidth = data.readUnsignedExpGolombCodedInt(); // prec_ref_display_width
        boolean refViewingDistanceFlag = data.readBit(); // ref_viewing_distance_flag
        int precRefViewingDist = 0;
        if (refViewingDistanceFlag) {
          precRefViewingDist = data.readUnsignedExpGolombCodedInt(); // prec_ref_viewing_dist
        }
        int numRefDisplaysMinus1 = data.readUnsignedExpGolombCodedInt(); // num_ref_displays_minus1
        int leftViewId = -1;
        int rightViewId = -1;
        int exponentRefDisplayWidth = -1;
        int mantissaRefDisplayWidth = -1;
        int exponentRefViewingDist = -1;
        int mantissaRefViewingDist = -1;
        for (int i = 0; i <= numRefDisplaysMinus1; i++) {
          leftViewId = data.readUnsignedExpGolombCodedInt(); // left_view_id[i]
          rightViewId = data.readUnsignedExpGolombCodedInt(); // right_view_id[i]
          exponentRefDisplayWidth = data.readBits(6); // exponent_ref_display_width[i]
          if (exponentRefDisplayWidth == 63) {
            return null;
          }
          int refDispWidthBits =
              exponentRefDisplayWidth == 0
                  ? max(0, precRefDisplayWidth - 30)
                  : max(0, exponentRefDisplayWidth + precRefDisplayWidth - 31);
          mantissaRefDisplayWidth =
              data.readBits(refDispWidthBits); // mantissa_ref_display_width[i]
          if (refViewingDistanceFlag) {
            exponentRefViewingDist = data.readBits(6); // exponent_ref_viewing_distance[i]
            if (exponentRefViewingDist == 63) {
              return null;
            }
            int refViewDistBits =
                exponentRefViewingDist == 0
                    ? max(0, precRefViewingDist - 30)
                    : max(0, exponentRefViewingDist + precRefViewingDist - 31);
            mantissaRefViewingDist =
                data.readBits(refViewDistBits); // mantissa_ref_viewing_distance[i]
          }
          if (data.readBit()) { // additional_shift_present_flag[i]
            data.skipBits(10); // num_sample_shift_plus512[i]
          }
        }

        return new H265Sei3dRefDisplayInfoData(
            precRefDisplayWidth,
            precRefViewingDist,
            numRefDisplaysMinus1 + 1,
            leftViewId,
            rightViewId,
            exponentRefDisplayWidth,
            mantissaRefDisplayWidth,
            exponentRefViewingDist,
            mantissaRefViewingDist);
      }
    }
    return null;
  }

  /**
   * Finds the first NAL unit in {@code data}.
   *
   * <p>If {@code prefixFlags} is null then the first three bytes of a NAL unit must be entirely
   * contained within the part of the array being searched in order for it to be found.
   *
   * <p>When {@code prefixFlags} is non-null, this method supports finding NAL units whose first
   * four bytes span {@code data} arrays passed to successive calls. To use this feature, pass the
   * same {@code prefixFlags} parameter to successive calls. State maintained in this parameter
   * enables the detection of such NAL units. Note that when using this feature, the return value
   * may be 3, 2 or 1 less than {@code startOffset}, to indicate a NAL unit starting 3, 2 or 1 bytes
   * before the first byte in the current array.
   *
   * @param data The data to search.
   * @param startOffset The offset (inclusive) in the data to start the search.
   * @param endOffset The offset (exclusive) in the data to end the search.
   * @param prefixFlags A boolean array whose first three elements are used to store the state
   *     required to detect NAL units where the NAL unit prefix spans array boundaries. The array
   *     must be at least 3 elements long.
   * @return The offset of the NAL unit, or {@code endOffset} if a NAL unit was not found.
   */
  public static int findNalUnit(
      byte[] data, int startOffset, int endOffset, boolean[] prefixFlags) {
    int length = endOffset - startOffset;

    Assertions.checkState(length >= 0);
    if (length == 0) {
      return endOffset;
    }

    if (prefixFlags[0]) {
      clearPrefixFlags(prefixFlags);
      return startOffset - 3;
    } else if (length > 1 && prefixFlags[1] && data[startOffset] == 1) {
      clearPrefixFlags(prefixFlags);
      return startOffset - 2;
    } else if (length > 2
        && prefixFlags[2]
        && data[startOffset] == 0
        && data[startOffset + 1] == 1) {
      clearPrefixFlags(prefixFlags);
      return startOffset - 1;
    }

    int limit = endOffset - 1;
    // We're looking for the NAL unit start code prefix 0x000001. The value of i tracks the index of
    // the third byte.
    for (int i = startOffset + 2; i < limit; i += 3) {
      if ((data[i] & 0xFE) != 0) {
        // There isn't a NAL prefix here, or at the next two positions. Do nothing and let the
        // loop advance the index by three.
      } else if (data[i - 2] == 0 && data[i - 1] == 0 && data[i] == 1) {
        clearPrefixFlags(prefixFlags);
        return i - 2;
      } else {
        // There isn't a NAL prefix here, but there might be at the next position. We should
        // only skip forward by one. The loop will skip forward by three, so subtract two here.
        i -= 2;
      }
    }

    // True if the last three bytes in the data seen so far are {0,0,1}.
    prefixFlags[0] =
        length > 2
            ? (data[endOffset - 3] == 0 && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
            : length == 2
                ? (prefixFlags[2] && data[endOffset - 2] == 0 && data[endOffset - 1] == 1)
                : (prefixFlags[1] && data[endOffset - 1] == 1);
    // True if the last two bytes in the data seen so far are {0,0}.
    prefixFlags[1] =
        length > 1
            ? data[endOffset - 2] == 0 && data[endOffset - 1] == 0
            : prefixFlags[2] && data[endOffset - 1] == 0;
    // True if the last byte in the data seen so far is {0}.
    prefixFlags[2] = data[endOffset - 1] == 0;

    return endOffset;
  }

  /**
   * Clears prefix flags, as used by {@link #findNalUnit(byte[], int, int, boolean[])}.
   *
   * @param prefixFlags The flags to clear.
   */
  public static void clearPrefixFlags(boolean[] prefixFlags) {
    prefixFlags[0] = false;
    prefixFlags[1] = false;
    prefixFlags[2] = false;
  }

  /**
   * Returns a new RFC 6381 codecs description string specifically for the single-layer HEVC case.
   * When falling back to single-layer HEVC from L-HEVC, both profile and level should be adjusted
   * for the base layer case and the codecs description string should represent that. For the
   * single-layer HEVC case, the string is derived from the SPS of the base layer.
   *
   * @param csdBuffers The CSD buffers that include the SPS of the base layer.
   * @return A RFC 6381 codecs string derived from the SPS of the base layer if such information is
   *     available, or null otherwise.
   */
  @Nullable
  public static String getH265BaseLayerCodecsString(List<byte[]> csdBuffers) {
    for (int i = 0; i < csdBuffers.size(); i++) {
      byte[] buffer = csdBuffers.get(i);
      int limit = buffer.length;
      if (limit > 3) {
        ImmutableList<Integer> nalUnitPositions = findNalUnitPositions(buffer);
        for (int j = 0; j < nalUnitPositions.size(); j++) {
          // Start code prefix of 3 bytes is included in the nalUnitPositions.
          if (nalUnitPositions.get(j) + 3 < limit) {
            // Use the base layer (layerId == 0) SPS to derive new codecs string.
            ParsableNalUnitBitArray data =
                new ParsableNalUnitBitArray(buffer, nalUnitPositions.get(j) + 3, limit);
            H265NalHeader nalHeader = parseH265NalHeader(data);
            if (nalHeader.nalUnitType == H265_NAL_UNIT_TYPE_SPS && nalHeader.layerId == 0) {
              return createCodecStringFromH265SpsPalyoad(data);
            }
          }
        }
      }
    }
    return null;
  }

  /** Finds all NAL unit positions from a given bitstream buffer. */
  private static ImmutableList<Integer> findNalUnitPositions(byte[] data) {
    int offset = 0;
    boolean[] prefixFlags = new boolean[3];
    ImmutableList.Builder<Integer> nalUnitPositions = ImmutableList.builder();
    while (offset < data.length) {
      int nalUnitOffset = findNalUnit(data, offset, data.length, prefixFlags);
      if (nalUnitOffset != data.length) {
        nalUnitPositions.add(nalUnitOffset);
      }
      offset = nalUnitOffset + 3;
    }
    return nalUnitPositions.build();
  }

  /** Creates a RFC 6381 HEVC codec string from a given SPS NAL unit payload. */
  @Nullable
  private static String createCodecStringFromH265SpsPalyoad(ParsableNalUnitBitArray data) {
    data.skipBits(4); // sps_video_parameter_set_id
    int maxSubLayersMinus1 = data.readBits(3);
    data.skipBit(); // sps_temporal_id_nesting_flag
    H265ProfileTierLevel profileTierLevel =
        parseH265ProfileTierLevel(
            data,
            /* profilePresentFlag= */ true,
            maxSubLayersMinus1,
            /* prevProfileTierLevel= */ null);
    return CodecSpecificDataUtil.buildHevcCodecString(
        profileTierLevel.generalProfileSpace,
        profileTierLevel.generalTierFlag,
        profileTierLevel.generalProfileIdc,
        profileTierLevel.generalProfileCompatibilityFlags,
        profileTierLevel.constraintBytes,
        profileTierLevel.generalLevelIdc);
  }

  private static int findNextUnescapeIndex(byte[] bytes, int offset, int limit) {
    for (int i = offset; i < limit - 2; i++) {
      if (bytes[i] == 0x00 && bytes[i + 1] == 0x00 && bytes[i + 2] == 0x03) {
        return i;
      }
    }
    return limit;
  }

  /**
   * Skips all bits in hrd_parameters() defined in ITU-T Recommendation H.265 (2019) subsection
   * E.2.2. This function updates the ParsableNalUnitBitArray data with new byte and bit offsets
   * that point to the end of the hrd_parameters().
   */
  private static void skipH265HrdParameters(
      ParsableNalUnitBitArray data, boolean commonInfPresentFlag, int maxSubLayersMinus1) {
    boolean nalHrdParametersPresentFlag = false;
    boolean vclHrdParametersPresentFlag = false;
    boolean subPicHrdParametersPresentFlag = false;
    if (commonInfPresentFlag) {
      nalHrdParametersPresentFlag = data.readBit(); // nal_hrd_parameters_present_flag
      vclHrdParametersPresentFlag = data.readBit(); // vcl_hrd_parameters_present_flag
      if (nalHrdParametersPresentFlag || vclHrdParametersPresentFlag) {
        subPicHrdParametersPresentFlag = data.readBit(); // sub_pic_hrd_params_present_flag
        if (subPicHrdParametersPresentFlag) {
          // tick_divisor_minus2, du_cpb_removal_delay_increment_length_minus1,
          // sub_pic_cpb_params_in_pic_timing_sei_flag, dpb_output_delay_du_length_minus1
          data.skipBits(19);
        }
        data.skipBits(8); // bit_rate_scale, cpb_size_scale
        if (subPicHrdParametersPresentFlag) {
          data.skipBits(4); // cpb_size_du_scale
        }
        // initial_cpb_removal_delay_length_minus1, au_cpb_removal_delay_length_minus1,
        // dpb_output_delay_length_minus1
        data.skipBits(15);
      }
    }
    for (int i = 0; i <= maxSubLayersMinus1; i++) {
      boolean fixedPicRateGeneralFlag = data.readBit(); // fixed_pic_rate_general_flag[i]
      boolean fixedPicRateWithinCvsFlag = fixedPicRateGeneralFlag;
      boolean lowDelayHrdFlag = false;
      int cpbCntMinus1 = 0;
      if (!fixedPicRateGeneralFlag) {
        fixedPicRateWithinCvsFlag = data.readBit(); // fixed_pic_rate_within_cvs_flag[i]
      }
      if (fixedPicRateWithinCvsFlag) {
        data.readUnsignedExpGolombCodedInt(); // elemental_duration_in_tc_minus1[i]
      } else {
        lowDelayHrdFlag = data.readBit(); // low_delay_hrd_flag[i]
      }
      if (!lowDelayHrdFlag) {
        cpbCntMinus1 = data.readUnsignedExpGolombCodedInt(); // cpb_cnt_minus1[i]
      }
      int numSubLayerHrdParameters = 0;
      numSubLayerHrdParameters += nalHrdParametersPresentFlag ? 1 : 0;
      numSubLayerHrdParameters += vclHrdParametersPresentFlag ? 1 : 0;
      for (int j = 0; j < numSubLayerHrdParameters; j++) {
        for (int k = 0; k <= cpbCntMinus1; k++) {
          data.readUnsignedExpGolombCodedInt(); // bit_rate_value_minus1[k]
          data.readUnsignedExpGolombCodedInt(); // cpb_size_value_minus1[k]
          if (subPicHrdParametersPresentFlag) {
            data.readUnsignedExpGolombCodedInt(); // cpb_size_du_value_minus1[k]
            data.readUnsignedExpGolombCodedInt(); // bit_rate_du_value_minus1[k]
          }
          data.skipBit(); // cbr_flag[k]
        }
      }
    }
  }

  /**
   * Parses a H.265 profile_tier_level() using the syntax defined in ITU-T Recommendation H.265
   * (2019) subsection 7.3.3. This function updates the ParsableNalUnitBitArray data with new byte
   * and bit offsets that point to the end of parsing the profile_tier_level().
   */
  private static H265ProfileTierLevel parseH265ProfileTierLevel(
      ParsableNalUnitBitArray data,
      boolean profilePresentFlag,
      int maxSubLayersMinus1,
      @Nullable H265ProfileTierLevel prevProfileTierLevel) {
    int generalProfileSpace = 0;
    boolean generalTierFlag = false;
    int generalProfileIdc = 0;
    int generalProfileCompatibilityFlags = 0;
    int[] constraintBytes = new int[6];
    if (profilePresentFlag) {
      generalProfileSpace = data.readBits(2); // general_profile_space
      generalTierFlag = data.readBit(); // general_tier_flag
      generalProfileIdc = data.readBits(5); // general_profile_idc
      generalProfileCompatibilityFlags = 0;
      for (int i = 0; i < 32; i++) {
        if (data.readBit()) {
          generalProfileCompatibilityFlags |= (1 << i); // general_profile_compatibility_flag[i]
        }
      }
      for (int i = 0; i < constraintBytes.length; ++i) {
        constraintBytes[i] = data.readBits(8);
      }
    } else if (prevProfileTierLevel != null) {
      generalProfileSpace = prevProfileTierLevel.generalProfileSpace;
      generalTierFlag = prevProfileTierLevel.generalTierFlag;
      generalProfileIdc = prevProfileTierLevel.generalProfileIdc;
      generalProfileCompatibilityFlags = prevProfileTierLevel.generalProfileCompatibilityFlags;
      constraintBytes = prevProfileTierLevel.constraintBytes;
    }
    int generalLevelIdc = data.readBits(8); // general_level_idc

    // Skip to the end of profile_tier_level().
    int toSkip = 0;
    for (int i = 0; i < maxSubLayersMinus1; i++) {
      if (data.readBit()) { // sub_layer_profile_present_flag[i]
        toSkip += 88;
      }
      if (data.readBit()) { // sub_layer_level_present_flag[i]
        toSkip += 8;
      }
    }
    data.skipBits(toSkip);
    if (maxSubLayersMinus1 > 0) {
      data.skipBits(2 * (8 - maxSubLayersMinus1)); // reserved_zero_2bits
    }

    return new H265ProfileTierLevel(
        generalProfileSpace,
        generalTierFlag,
        generalProfileIdc,
        generalProfileCompatibilityFlags,
        constraintBytes,
        generalLevelIdc);
  }

  // Applies the conformance window offsets to the width following H.265/HEVC (2014) Table 6-1.
  private static int applyConformanceWindowToWidth(
      int width, int chromaFormatIdc, int offsetLeft, int offsetRight) {
    int subWidthC = chromaFormatIdc == 1 || chromaFormatIdc == 2 ? 2 : 1;
    return width - subWidthC * (offsetLeft + offsetRight);
  }

  // Applies the conformance window offsets to the height following H.265/HEVC (2014) Table 6-1.
  private static int applyConformanceWindowToHeight(
      int height, int chromaFormatIdc, int offsetTop, int offsetBottom) {
    int subHeightC = chromaFormatIdc == 1 ? 2 : 1;
    return height - subHeightC * (offsetTop + offsetBottom);
  }

  /**
   * Parses H.265 rep_format()s and corresponding indices (vps_rep_format_idx[]) within
   * vps_extension(). This function updates the ParsableNalUnitBitArray data with new byte and bit
   * offsets that point to the end of parsing the rep_format()s and indices.
   */
  private static H265RepFormatsAndIndices parseH265RepFormatsAndIndices(
      ParsableNalUnitBitArray data, int maxLayers) {
    int numRepFormats = data.readUnsignedExpGolombCodedInt() + 1;
    ImmutableList.Builder<H265RepFormat> repFormats =
        ImmutableList.builderWithExpectedSize(numRepFormats);
    int[] repFormatIndices = new int[maxLayers];
    for (int i = 0; i < numRepFormats; i++) {
      // rep_format()
      repFormats.add(parseH265RepFormat(data));
    }
    if (numRepFormats > 1 && data.readBit()) { // rep_format_idx_present_flag
      int bitLen = log2(numRepFormats, RoundingMode.CEILING);
      // Here, vps_base_layer_internal_flag == true as we only support the case where the base layer
      // is included within the L-HEVC bitstream; hence the index i starts from 1.
      for (int i = 1; i < maxLayers; i++) {
        repFormatIndices[i] = data.readBits(bitLen); // vps_rep_format_idx[i]
      }
    } else {
      for (int i = 1; i < maxLayers; i++) {
        repFormatIndices[i] = min(i, numRepFormats - 1);
      }
    }
    return new H265RepFormatsAndIndices(repFormats.build(), repFormatIndices);
  }

  /**
   * Parses a H.265 rep_format() using the syntax defined in ITU-T Recommendation H.265 (2019)
   * subsection F.7.3.2.1.2. This function updates the ParsableNalUnitBitArray data with new byte
   * and bit offsets that point to the end of the parsing of the rep_format().
   */
  private static H265RepFormat parseH265RepFormat(ParsableNalUnitBitArray data) {
    int frameWidth = data.readBits(16); // pic_width_vps_in_luma_samples
    int frameHeight = data.readBits(16); // pic_height_vps_in_luma_samples
    int chromaFormatIdc = 0;
    int bitDepthLumaMinus8 = 0;
    int bitDepthChromaMinus8 = 0;
    if (data.readBit()) { // chroma_and_bit_depth_vps_present_flag
      chromaFormatIdc = data.readBits(2); // chroma_format_vps_idc
      if (chromaFormatIdc == 3) {
        data.skipBit(); // separate_colour_plane_vps_flag
      }
      bitDepthLumaMinus8 = data.readBits(4); // bit_depth_vps_luma_minus8
      bitDepthChromaMinus8 = data.readBits(4); // bit_depth_vps_chroma_minus8
    }
    if (data.readBit()) { // conformance_window_vps_flag
      int confWinLeftOffset = data.readUnsignedExpGolombCodedInt(); // conf_win_vps_left_offset
      int confWinRightOffset = data.readUnsignedExpGolombCodedInt(); // conf_win_vps_right_offset
      int confWinTopOffset = data.readUnsignedExpGolombCodedInt(); // conf_win_vps_top_offset
      int confWinBottomOffset = data.readUnsignedExpGolombCodedInt(); // conf_win_vps_bottom_offset
      frameWidth =
          applyConformanceWindowToWidth(
              frameWidth, chromaFormatIdc, confWinLeftOffset, confWinRightOffset);
      frameHeight =
          applyConformanceWindowToHeight(
              frameHeight, chromaFormatIdc, confWinTopOffset, confWinBottomOffset);
    }
    return new H265RepFormat(
        chromaFormatIdc, bitDepthLumaMinus8, bitDepthChromaMinus8, frameWidth, frameHeight);
  }

  /**
   * Skips H.265 dpb_size() within vps_extension(). This function updates the
   * ParsableNalUnitBitArray data with new byte and bit offsets that point to the end of the parsing
   * of the dpb_size().
   */
  private static void skipH265DpbSize(
      ParsableNalUnitBitArray data,
      int numOutputLayerSets,
      int[] maxSubLayersInLayerSet,
      int[] numLayersInIdList,
      boolean[][] necessaryLayerFlag) {
    for (int i = 1; i < numOutputLayerSets; i++) {
      boolean subLayerFlagInfoPresentFlag = data.readBit(); // sub_layer_flag_info_present_flag[i]
      for (int j = 0; j < maxSubLayersInLayerSet[i]; j++) {
        boolean subLayerDpbInfoPresentFlag;
        if (j > 0 && subLayerFlagInfoPresentFlag) {
          subLayerDpbInfoPresentFlag = data.readBit(); // sub_layer_dpb_info_present_flag[i][j]
        } else {
          subLayerDpbInfoPresentFlag = j == 0;
        }
        if (subLayerDpbInfoPresentFlag) {
          for (int k = 0; k < numLayersInIdList[i]; k++) {
            // Note that here we assume that vps_base_layer_internal_flag is always true.
            if (necessaryLayerFlag[i][k]) {
              data.readUnsignedExpGolombCodedInt(); // max_vps_dec_pic_buffering_minus1[i][k][j]
            }
          }
          data.readUnsignedExpGolombCodedInt(); // max_vps_num_reorder_pics[i][j]
          data.readUnsignedExpGolombCodedInt(); // max_vps_latency_increase_plus1[i][j]
        }
      }
    }
  }

  /**
   * Skips up to (not including) vps_vui_present_flag starting right after dbp_size() within H.265
   * vps_extension(). This function updates the ParsableNalUnitBitArray data with new byte and bit
   * offsets that point to vps_vui_present_flag.
   */
  private static void skipToH265VuiPresentFlagAfterDpbSize(
      ParsableNalUnitBitArray data, int maxLayers, boolean[][] directDependencyFlag) {
    int directDepTypeLen = data.readUnsignedExpGolombCodedInt() + 2; // direct_dep_type_len_minus2
    if (data.readBit()) { // direct_dependency_all_layers_flag
      data.skipBits(directDepTypeLen); // direct_dependency_all_layers_type
    } else {
      // Here, vps_base_layer_internal_flag == true as we only support the case where the base layer
      // is included within the L-HEVC bitstream; hence the index i starts from 1 and j from 0.
      for (int i = 1; i < maxLayers; i++) {
        for (int j = 0; j < i; j++) {
          if (directDependencyFlag[i][j]) {
            data.skipBits(directDepTypeLen); // direct_dependency_type[i][j]
          }
        }
      }
    }
    int nonVuiExtensionLen = data.readUnsignedExpGolombCodedInt(); // vps_non_vui_extension_length
    for (int i = 1; i <= nonVuiExtensionLen; i++) {
      data.skipBits(8); // vps_non_vui_extension_data_byte
    }
  }

  /**
   * Parses H.265 video_signal_info()s and corresponding indices (vps_video_signal_info_idx[])
   * within vps_vui() that is within vps_extension(). This function updates the
   * ParsableNalUnitBitArray data with new byte and bit offsets that point to the end of the parsing
   * of the video_signal_info()s and indices.
   */
  private static H265VideoSignalInfosAndIndices parseH265VideoSignalInfosAndIndices(
      ParsableNalUnitBitArray data, int maxLayers, int numLayerSets, int[] maxSubLayersInLayerSet) {
    boolean crossLayerIrapAlignedFlag = true;
    if (!data.readBit()) { // cross_layer_pic_type_aligned_flag
      crossLayerIrapAlignedFlag = data.readBit(); // cross_layer_irap_aligned_flag
    }
    if (crossLayerIrapAlignedFlag) {
      data.skipBit(); // all_layers_idr_aligned_flag
    }

    boolean bitRatePresentVpsFlag = data.readBit(); // bit_rate_present_vps_flag
    boolean picRatePresentVpsFlag = data.readBit(); // pic_rate_present_vps_flag
    if (bitRatePresentVpsFlag || picRatePresentVpsFlag) {
      // Here, vps_base_layer_internal_flag == true as we only support the case where the base layer
      // is included within the L-HEVC bitstream; hence the index i starts from 0.
      for (int i = 0; i < numLayerSets; i++) {
        for (int j = 0; j < maxSubLayersInLayerSet[i]; j++) {
          boolean bitRatePresentFlag = false;
          boolean picRatePresentFlag = false;
          if (bitRatePresentVpsFlag) {
            bitRatePresentFlag = data.readBit(); // bit_rate_present_flag[i][j]
          }
          if (picRatePresentVpsFlag) {
            picRatePresentFlag = data.readBit(); // pic_rate_present_flag[i][j]
          }
          if (bitRatePresentFlag) {
            data.skipBits(32); // avg_bit_rate[i][j], max_bit_rate[i][j]
          }
          if (picRatePresentFlag) {
            data.skipBits(18); // constant_pic_rate_idc[i][j], avg_pic_rate[i][j]
          }
        }
      }
    }
    int numVideoSignalInfos = maxLayers;
    boolean videoSignalInfoIdxPresentFlag = data.readBit(); // video_signal_info_idx_present_flag
    if (videoSignalInfoIdxPresentFlag) {
      numVideoSignalInfos = data.readBits(4) + 1; // vps_num_video_signal_info_minus1
    }
    ImmutableList.Builder<H265VideoSignalInfo> videoSignalInfos =
        ImmutableList.builderWithExpectedSize(numVideoSignalInfos);
    int[] videoSignalInfoIdices = new int[maxLayers];
    for (int i = 0; i < numVideoSignalInfos; i++) {
      // video_signal_info()
      videoSignalInfos.add(parseH265VideoSignalInfo(data));
    }
    if (videoSignalInfoIdxPresentFlag && numVideoSignalInfos > 1) {
      // Here, vps_base_layer_internal_flag == true as we only support the case where the base layer
      // is included within the L-HEVC bitstream; hence the index i starts from 0.
      for (int i = 0; i < maxLayers; i++) {
        videoSignalInfoIdices[i] = data.readBits(4); // vps_video_signal_info_idx[i]
      }
    }
    return new H265VideoSignalInfosAndIndices(videoSignalInfos.build(), videoSignalInfoIdices);
  }

  /**
   * Parses a H.265 video_signal_info() using the syntax defined in ITU-T Recommendation H.265
   * (2019) subsection F.7.3.2.1.5. This function updates the ParsableNalUnitBitArray data with new
   * byte and bit offsets that point to the end of parsing the video_signal_info().
   */
  private static H265VideoSignalInfo parseH265VideoSignalInfo(ParsableNalUnitBitArray data) {
    data.skipBits(3); // video_vps_format
    @C.ColorRange
    int colorRange =
        data.readBit() ? C.COLOR_RANGE_FULL : C.COLOR_RANGE_LIMITED; // video_full_range_vps_flag
    @C.ColorSpace
    int colorSpace =
        ColorInfo.isoColorPrimariesToColorSpace(data.readBits(8)); // colour_primaries_vps
    @C.ColorTransfer
    int colorTransfer =
        ColorInfo.isoTransferCharacteristicsToColorTransfer(
            data.readBits(8)); // transfer_characteristics_vps
    data.skipBits(8); // matrix_coeffs_vps

    return new H265VideoSignalInfo(colorSpace, colorRange, colorTransfer);
  }

  private static void skipScalingList(ParsableNalUnitBitArray bitArray, int size) {
    int lastScale = 8;
    int nextScale = 8;
    for (int i = 0; i < size; i++) {
      if (nextScale != 0) {
        int deltaScale = bitArray.readSignedExpGolombCodedInt();
        nextScale = (lastScale + deltaScale + 256) % 256;
      }
      lastScale = (nextScale == 0) ? lastScale : nextScale;
    }
  }

  /** Skip HRD parameters in {@code data}, as defined in E.1.2 of the H.264 spec. */
  private static void skipHrdParameters(ParsableNalUnitBitArray data) {
    int codedPictureBufferCount = data.readUnsignedExpGolombCodedInt() + 1; // cpb_cnt_minus1
    data.skipBits(8); // bit_rate_scale (4), cpb_size_scale (4)
    for (int i = 0; i < codedPictureBufferCount; i++) {
      data.readUnsignedExpGolombCodedInt(); // bit_rate_value_minus1[i]
      data.readUnsignedExpGolombCodedInt(); // cpb_size_value_minus1[i]
      data.skipBit(); // cbr_flag[i]
    }
    // initial_cpb_removal_delay_length_minus1 (5)
    // cpb_removal_delay_length_minus1 (5)
    // dpb_output_delay_length_minus1 (5)
    // time_offset_length (5)
    data.skipBits(20);
  }

  private static void skipH265ScalingList(ParsableNalUnitBitArray bitArray) {
    for (int sizeId = 0; sizeId < 4; sizeId++) {
      for (int matrixId = 0; matrixId < 6; matrixId += sizeId == 3 ? 3 : 1) {
        if (!bitArray.readBit()) { // scaling_list_pred_mode_flag[sizeId][matrixId]
          // scaling_list_pred_matrix_id_delta[sizeId][matrixId]
          bitArray.readUnsignedExpGolombCodedInt();
        } else {
          int coefNum = min(64, 1 << (4 + (sizeId << 1)));
          if (sizeId > 1) {
            // scaling_list_dc_coef_minus8[sizeId - 2][matrixId]
            bitArray.readSignedExpGolombCodedInt();
          }
          for (int i = 0; i < coefNum; i++) {
            bitArray.readSignedExpGolombCodedInt(); // scaling_list_delta_coef
          }
        }
      }
    }
  }

  /**
   * Skips any short term reference picture sets contained in a H.265 SPS.
   *
   * <p>Note: The st_ref_pic_set parsing in this method is simplified for the case where they're
   * contained in a SPS, and would need generalizing for use elsewhere.
   */
  private static void skipH265ShortTermReferencePictureSets(ParsableNalUnitBitArray bitArray) {
    int numShortTermRefPicSets = bitArray.readUnsignedExpGolombCodedInt();
    // As this method applies in a SPS, each short term reference picture set only accesses data
    // from the previous one. This is because RefRpsIdx = stRpsIdx - (delta_idx_minus1 + 1), and
    // delta_idx_minus1 is always zero in a SPS. Hence we just keep track of variables from the
    // previous one as we iterate.
    int previousNumNegativePics = C.INDEX_UNSET;
    int previousNumPositivePics = C.INDEX_UNSET;
    int[] previousDeltaPocS0 = new int[0];
    int[] previousDeltaPocS1 = new int[0];
    for (int stRpsIdx = 0; stRpsIdx < numShortTermRefPicSets; stRpsIdx++) {
      int numNegativePics;
      int numPositivePics;
      int[] deltaPocS0;
      int[] deltaPocS1;

      boolean interRefPicSetPredictionFlag = stRpsIdx != 0 && bitArray.readBit();
      if (interRefPicSetPredictionFlag) {
        int previousNumDeltaPocs = previousNumNegativePics + previousNumPositivePics;

        int deltaRpsSign = bitArray.readBit() ? 1 : 0;
        int absDeltaRps = bitArray.readUnsignedExpGolombCodedInt() + 1;
        int deltaRps = (1 - 2 * deltaRpsSign) * absDeltaRps;

        boolean[] useDeltaFlags = new boolean[previousNumDeltaPocs + 1];
        for (int j = 0; j <= previousNumDeltaPocs; j++) {
          if (!bitArray.readBit()) { // used_by_curr_pic_flag[j]
            useDeltaFlags[j] = bitArray.readBit();
          } else {
            // When use_delta_flag[j] is not present, its value is 1.
            useDeltaFlags[j] = true;
          }
        }

        // Derive numNegativePics, numPositivePics, deltaPocS0 and deltaPocS1 as per Rec. ITU-T
        // H.265 v6 (06/2019) Section 7.4.8
        int i = 0;
        deltaPocS0 = new int[previousNumDeltaPocs + 1];
        deltaPocS1 = new int[previousNumDeltaPocs + 1];
        for (int j = previousNumPositivePics - 1; j >= 0; j--) {
          int dPoc = previousDeltaPocS1[j] + deltaRps;
          if (dPoc < 0 && useDeltaFlags[previousNumNegativePics + j]) {
            deltaPocS0[i++] = dPoc;
          }
        }
        if (deltaRps < 0 && useDeltaFlags[previousNumDeltaPocs]) {
          deltaPocS0[i++] = deltaRps;
        }
        for (int j = 0; j < previousNumNegativePics; j++) {
          int dPoc = previousDeltaPocS0[j] + deltaRps;
          if (dPoc < 0 && useDeltaFlags[j]) {
            deltaPocS0[i++] = dPoc;
          }
        }
        numNegativePics = i;
        deltaPocS0 = Arrays.copyOf(deltaPocS0, numNegativePics);

        i = 0;
        for (int j = previousNumNegativePics - 1; j >= 0; j--) {
          int dPoc = previousDeltaPocS0[j] + deltaRps;
          if (dPoc > 0 && useDeltaFlags[j]) {
            deltaPocS1[i++] = dPoc;
          }
        }
        if (deltaRps > 0 && useDeltaFlags[previousNumDeltaPocs]) {
          deltaPocS1[i++] = deltaRps;
        }
        for (int j = 0; j < previousNumPositivePics; j++) {
          int dPoc = previousDeltaPocS1[j] + deltaRps;
          if (dPoc > 0 && useDeltaFlags[previousNumNegativePics + j]) {
            deltaPocS1[i++] = dPoc;
          }
        }
        numPositivePics = i;
        deltaPocS1 = Arrays.copyOf(deltaPocS1, numPositivePics);
      } else {
        numNegativePics = bitArray.readUnsignedExpGolombCodedInt();
        numPositivePics = bitArray.readUnsignedExpGolombCodedInt();
        deltaPocS0 = new int[numNegativePics];
        for (int i = 0; i < numNegativePics; i++) {
          deltaPocS0[i] =
              (i > 0 ? deltaPocS0[i - 1] : 0) - (bitArray.readUnsignedExpGolombCodedInt() + 1);
          bitArray.skipBit(); // used_by_curr_pic_s0_flag[i]
        }
        deltaPocS1 = new int[numPositivePics];
        for (int i = 0; i < numPositivePics; i++) {
          deltaPocS1[i] =
              (i > 0 ? deltaPocS1[i - 1] : 0) + (bitArray.readUnsignedExpGolombCodedInt() + 1);
          bitArray.skipBit(); // used_by_curr_pic_s1_flag[i]
        }
      }
      previousNumNegativePics = numNegativePics;
      previousNumPositivePics = numPositivePics;
      previousDeltaPocS0 = deltaPocS0;
      previousDeltaPocS1 = deltaPocS1;
    }
  }

  private NalUnitUtil() {
    // Prevent instantiation.
  }
}
