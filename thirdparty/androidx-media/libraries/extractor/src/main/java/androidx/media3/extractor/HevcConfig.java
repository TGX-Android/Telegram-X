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
package androidx.media3.extractor;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.CodecSpecificDataUtil;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.NalUnitUtil;
import java.util.Collections;
import java.util.List;

/** HEVC configuration data. */
@UnstableApi
public final class HevcConfig {

  /**
   * Parses HEVC configuration data.
   *
   * @param data A {@link ParsableByteArray}, whose position is set to the start of the HEVC
   *     configuration data to parse.
   * @return A parsed representation of the HEVC configuration data.
   * @throws ParserException If an error occurred parsing the data.
   */
  public static HevcConfig parse(ParsableByteArray data) throws ParserException {
    return parseImpl(data, /* layered= */ false, /* vpsData= */ null);
  }

  /**
   * Parses L-HEVC configuration data.
   *
   * @param data A {@link ParsableByteArray}, whose position is set to the start of the L-HEVC
   *     configuration data to parse.
   * @param vpsData A parsed representation of VPS data.
   * @return A parsed representation of the L-HEVC configuration data.
   * @throws ParserException If an error occurred parsing the data.
   */
  public static HevcConfig parseLayered(ParsableByteArray data, NalUnitUtil.H265VpsData vpsData)
      throws ParserException {
    return parseImpl(data, /* layered= */ true, vpsData);
  }

  /**
   * Parses HEVC or L-HEVC configuration data.
   *
   * @param data A {@link ParsableByteArray}, whose position is set to the start of the HEVC/L-HEVC
   *     configuration data to parse.
   * @param layered A flag indicating whether layered HEVC (L-HEVC) is being parsed or not.
   * @param vpsData A parsed representation of VPS data or {@code null} if not available.
   * @return A parsed representation of the HEVC/L-HEVC configuration data.
   * @throws ParserException If an error occurred parsing the data.
   */
  private static HevcConfig parseImpl(
      ParsableByteArray data, boolean layered, @Nullable NalUnitUtil.H265VpsData vpsData)
      throws ParserException {
    try {
      // Skip to the NAL unit length size field.
      if (layered) {
        data.skipBytes(4);
      } else {
        data.skipBytes(21);
      }
      int lengthSizeMinusOne = data.readUnsignedByte() & 0x03;

      // Calculate the combined size of all VPS/SPS/PPS bitstreams.
      int numberOfArrays = data.readUnsignedByte();
      int csdLength = 0;
      int csdStartPosition = data.getPosition();
      for (int i = 0; i < numberOfArrays; i++) {
        data.skipBytes(1); // completeness (1), reserved (1), nal_unit_type (6)
        int numberOfNalUnits = data.readUnsignedShort();
        for (int j = 0; j < numberOfNalUnits; j++) {
          int nalUnitLength = data.readUnsignedShort();
          csdLength += 4 + nalUnitLength; // Start code and NAL unit.
          data.skipBytes(nalUnitLength);
        }
      }

      // Concatenate the codec-specific data into a single buffer.
      data.setPosition(csdStartPosition);
      byte[] buffer = new byte[csdLength];
      int maxSubLayers = Format.NO_VALUE;
      int bufferPosition = 0;
      int width = Format.NO_VALUE;
      int height = Format.NO_VALUE;
      int bitdepthLuma = Format.NO_VALUE;
      int bitdepthChroma = Format.NO_VALUE;
      @C.ColorSpace int colorSpace = Format.NO_VALUE;
      @C.ColorRange int colorRange = Format.NO_VALUE;
      @C.ColorTransfer int colorTransfer = Format.NO_VALUE;
      @C.StereoMode int stereoMode = Format.NO_VALUE;
      float pixelWidthHeightRatio = 1;
      int maxNumReorderPics = Format.NO_VALUE;
      @Nullable String codecs = null;
      @Nullable NalUnitUtil.H265VpsData currentVpsData = vpsData;
      for (int i = 0; i < numberOfArrays; i++) {
        int nalUnitType =
            data.readUnsignedByte() & 0x3F; // completeness (1), reserved (1), nal_unit_type (6)
        int numberOfNalUnits = data.readUnsignedShort();
        for (int j = 0; j < numberOfNalUnits; j++) {
          int nalUnitLength = data.readUnsignedShort();
          System.arraycopy(
              NalUnitUtil.NAL_START_CODE,
              0,
              buffer,
              bufferPosition,
              NalUnitUtil.NAL_START_CODE.length);
          bufferPosition += NalUnitUtil.NAL_START_CODE.length;
          System.arraycopy(
              data.getData(), data.getPosition(), buffer, bufferPosition, nalUnitLength);
          if (nalUnitType == NalUnitUtil.H265_NAL_UNIT_TYPE_VPS && j == 0) {
            currentVpsData =
                NalUnitUtil.parseH265VpsNalUnit(
                    buffer, bufferPosition, bufferPosition + nalUnitLength);
          } else if (nalUnitType == NalUnitUtil.H265_NAL_UNIT_TYPE_SPS && j == 0) {
            NalUnitUtil.H265SpsData spsData =
                NalUnitUtil.parseH265SpsNalUnit(
                    buffer, bufferPosition, bufferPosition + nalUnitLength, currentVpsData);
            maxSubLayers = spsData.maxSubLayersMinus1 + 1;
            width = spsData.width;
            height = spsData.height;
            bitdepthLuma = spsData.bitDepthLumaMinus8 + 8;
            bitdepthChroma = spsData.bitDepthChromaMinus8 + 8;
            colorSpace = spsData.colorSpace;
            colorRange = spsData.colorRange;
            colorTransfer = spsData.colorTransfer;
            pixelWidthHeightRatio = spsData.pixelWidthHeightRatio;
            maxNumReorderPics = spsData.maxNumReorderPics;

            if (spsData.profileTierLevel != null) {
              codecs =
                  CodecSpecificDataUtil.buildHevcCodecString(
                      spsData.profileTierLevel.generalProfileSpace,
                      spsData.profileTierLevel.generalTierFlag,
                      spsData.profileTierLevel.generalProfileIdc,
                      spsData.profileTierLevel.generalProfileCompatibilityFlags,
                      spsData.profileTierLevel.constraintBytes,
                      spsData.profileTierLevel.generalLevelIdc);
            }
          } else if (nalUnitType == NalUnitUtil.H265_NAL_UNIT_TYPE_PREFIX_SEI && j == 0) {
            NalUnitUtil.H265Sei3dRefDisplayInfoData seiData =
                NalUnitUtil.parseH265Sei3dRefDisplayInfo(
                    buffer, bufferPosition, bufferPosition + nalUnitLength);
            if (seiData != null && currentVpsData != null) {
              stereoMode =
                  (seiData.leftViewId == currentVpsData.layerInfos.get(0).viewId)
                      ? C.STEREO_MODE_INTERLEAVED_LEFT_PRIMARY
                      : C.STEREO_MODE_INTERLEAVED_RIGHT_PRIMARY;
            }
          }
          bufferPosition += nalUnitLength;
          data.skipBytes(nalUnitLength);
        }
      }

      List<byte[]> initializationData =
          csdLength == 0 ? Collections.emptyList() : Collections.singletonList(buffer);
      return new HevcConfig(
          initializationData,
          lengthSizeMinusOne + 1,
          maxSubLayers,
          width,
          height,
          bitdepthLuma,
          bitdepthChroma,
          colorSpace,
          colorRange,
          colorTransfer,
          stereoMode,
          pixelWidthHeightRatio,
          maxNumReorderPics,
          codecs,
          currentVpsData);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw ParserException.createForMalformedContainer(
          "Error parsing" + (layered ? "L-HEVC config" : "HEVC config"), e);
    }
  }

  /**
   * List of buffers containing the codec-specific data to be provided to the decoder.
   *
   * <p>See {@link Format#initializationData}.
   */
  public final List<byte[]> initializationData;

  /** The length of the NAL unit length field in the bitstream's container, in bytes. */
  public final int nalUnitLengthFieldLength;

  /** The {@code sps_max_sub_layers_minus1 + 1} value: the number of temporal sub-layers. */
  public final int maxSubLayers;

  /** The width of each decoded frame, or {@link Format#NO_VALUE} if unknown. */
  public final int width;

  /** The height of each decoded frame, or {@link Format#NO_VALUE} if unknown. */
  public final int height;

  /** The bit depth of the luma samples, or {@link Format#NO_VALUE} if unknown. */
  public final int bitdepthLuma;

  /** The bit depth of the chroma samples, or {@link Format#NO_VALUE} if unknown. */
  public final int bitdepthChroma;

  /**
   * The {@link C.ColorSpace} of the video or {@link Format#NO_VALUE} if unknown or not applicable.
   */
  public final @C.ColorSpace int colorSpace;

  /**
   * The {@link C.ColorRange} of the video or {@link Format#NO_VALUE} if unknown or not applicable.
   */
  public final @C.ColorRange int colorRange;

  /**
   * The {@link C.ColorTransfer} of the video or {@link Format#NO_VALUE} if unknown or not
   * applicable.
   */
  public final @C.ColorTransfer int colorTransfer;

  /**
   * The {@link C.StereoMode} of the video or {@link Format#NO_VALUE} if unknown or not applicable.
   */
  public final @C.StereoMode int stereoMode;

  /** The pixel width to height ratio. */
  public final float pixelWidthHeightRatio;

  /**
   * The {@code sps_max_num_reorder_pics} value.
   *
   * <p>If a different value is present for each layer (due to {@code
   * sps_sub_layer_ordering_info_present_flag}), this value is the max of the values for all the
   * layers.
   */
  public final int maxNumReorderPics;

  /**
   * An RFC 6381 codecs string representing the video format, or {@code null} if not known.
   *
   * <p>See {@link Format#codecs}.
   */
  @Nullable public final String codecs;

  /** The parsed representation of VPS data or {@code null} if not available. */
  @Nullable public final NalUnitUtil.H265VpsData vpsData;

  private HevcConfig(
      List<byte[]> initializationData,
      int nalUnitLengthFieldLength,
      int maxSubLayers,
      int width,
      int height,
      int bitdepthLuma,
      int bitdepthChroma,
      @C.ColorSpace int colorSpace,
      @C.ColorRange int colorRange,
      @C.ColorTransfer int colorTransfer,
      @C.StereoMode int stereoMode,
      float pixelWidthHeightRatio,
      int maxNumReorderPics,
      @Nullable String codecs,
      @Nullable NalUnitUtil.H265VpsData vpsData) {
    this.initializationData = initializationData;
    this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
    this.maxSubLayers = maxSubLayers;
    this.width = width;
    this.height = height;
    this.bitdepthLuma = bitdepthLuma;
    this.bitdepthChroma = bitdepthChroma;
    this.colorSpace = colorSpace;
    this.colorRange = colorRange;
    this.colorTransfer = colorTransfer;
    this.stereoMode = stereoMode;
    this.pixelWidthHeightRatio = pixelWidthHeightRatio;
    this.maxNumReorderPics = maxNumReorderPics;
    this.codecs = codecs;
    this.vpsData = vpsData;
  }
}
