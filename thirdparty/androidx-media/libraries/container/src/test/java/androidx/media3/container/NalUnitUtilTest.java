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

import static androidx.media3.container.NalUnitUtil.isDependedOn;
import static androidx.media3.container.NalUnitUtil.numberOfBytesInNalUnitHeader;
import static androidx.media3.test.utils.TestUtil.createByteArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link NalUnitUtil}. */
@RunWith(AndroidJUnit4.class)
public final class NalUnitUtilTest {

  private static final int TEST_PARTIAL_NAL_POSITION = 4;
  private static final int TEST_NAL_POSITION = 10;
  private static final byte[] SPS_TEST_DATA =
      createByteArray(
          0x00, 0x00, 0x01, 0x67, 0x4D, 0x40, 0x16, 0xEC, 0xA0, 0x50, 0x17, 0xFC, 0xB8, 0x0A, 0x90,
          0x91, 0x00, 0x00, 0x7E, 0xA0);
  private static final int SPS_TEST_DATA_OFFSET = 3;

  // Below are H.265 VPS and SPS samples obtained from the "24-bit big endian raw audio LPCM
  // (MP4,H265,raw)" clip in the ExoPlayer sample.
  private static final byte[] H265_VPS_TEST_DATA =
      createByteArray(
          0x40, 0x01, 0x0C, 0x01, 0xFF, 0xFF, 0x22, 0x20, 0x00, 0x00, 0x03, 0x00, 0xB0, 0x00, 0x00,
          0x03, 0x00, 0x00, 0x03, 0x00, 0x99, 0x2C, 0x09);
  private static final byte[] H265_SPS_TEST_DATA =
      createByteArray(
          0x42, 0x01, 0x01, 0x22, 0x20, 0x00, 0x00, 0x03, 0x00, 0xB0, 0x00, 0x00, 0x03, 0x00, 0x00,
          0x03, 0x00, 0x99, 0xA0, 0x01, 0xE0, 0x20, 0x02, 0x1C, 0x4D, 0x94, 0xBB, 0xB4, 0xA3, 0x32,
          0xAA, 0xC0, 0x5A, 0x84, 0x89, 0x04, 0x8A, 0x00, 0x00, 0x07, 0xD2, 0x00, 0x00, 0xBB, 0x80,
          0xE4, 0x68, 0x7C, 0x9C, 0x00, 0x01, 0x2E, 0x1F, 0x80, 0x00, 0x21, 0xFD, 0x30, 0x00, 0x02,
          0x5C, 0x3F, 0x00, 0x00, 0x43, 0xFA, 0x62);

  // Below are MV-HEVC VPS and SPS samples obtained from the two sample clips in b/40937818.
  private static final byte[] H265_VPS_TEST_DATA_2VIEWS =
      createByteArray(
          0x40, 0x01, 0x0C, 0x11, 0xFF, 0xFF, 0x01, 0x60, 0x00, 0x00, 0x03, 0x00, 0xB0, 0x00, 0x00,
          0x03, 0x00, 0x00, 0x03, 0x00, 0x78, 0x15, 0xC1, 0x5B, 0x00, 0x20, 0x00, 0x28, 0x24, 0xC1,
          0x97, 0x06, 0x02, 0x00, 0x00, 0x03, 0x00, 0xBF, 0x80, 0x00, 0x00, 0x03, 0x00, 0x00, 0x78,
          0x8D, 0x07, 0x80, 0x04, 0x40, 0xA0, 0x1E, 0x5C, 0x52, 0xBF, 0x48);
  private static final byte[] H265_SPS_TEST_DATA_2VIEWS_VIEW_0 =
      createByteArray(
          0x42, 0x01, 0x01, 0x01, 0x60, 0x00, 0x00, 0x03, 0x00, 0xB0, 0x00, 0x00, 0x03, 0x00, 0x00,
          0x03, 0x00, 0x78, 0xA0, 0x03, 0xC0, 0x80, 0x11, 0x07, 0xCB, 0x88, 0x15, 0xEE, 0x45, 0x95,
          0x4D, 0x40, 0x40, 0x40, 0x40, 0x20);
  private static final byte[] H265_SPS_TEST_DATA_2VIEWS_VIEW_1 =
      createByteArray(0x42, 0x09, 0x0E, 0x82, 0x2E, 0x45, 0x8A, 0xA0, 0x05, 0x01);
  private static final byte[] H265_SPS_TEST_DATA_2VIEWS_SEI =
      createByteArray(0x4E, 0x01, 0xB0, 0x04, 0x04, 0x0A, 0x80, 0x20, 0x80);

  private static final byte[] H265_VPS_TEST_DATA_2VIEWS_HDR =
      createByteArray(
          0x40, 0x01, 0x0C, 0x11, 0xFF, 0xFF, 0x02, 0x20, 0x00, 0x00, 0x03, 0x00, 0xB0, 0x00, 0x00,
          0x03, 0x00, 0x00, 0x03, 0x00, 0x99, 0x98, 0xA3, 0x41, 0x5C, 0x00, 0x00, 0x0F, 0xA4, 0x00,
          0x03, 0xA9, 0x83, 0xFF, 0x99, 0x20, 0x00, 0x21, 0x16, 0x93, 0x93, 0x11, 0x00, 0x00, 0x03,
          0x00, 0x5E, 0xC4, 0x00, 0x00, 0x03, 0x00, 0x00, 0x4C, 0xC6, 0x87, 0x80, 0x04, 0x38, 0x52,
          0x24, 0x31, 0x8A, 0x3B, 0xA4, 0x80);
  private static final byte[] H265_SPS_TEST_DATA_2VIEWS_HDR_VIEW_0 =
      createByteArray(
          0x42, 0x01, 0x01, 0x02, 0x20, 0x00, 0x00, 0x03, 0x00, 0xB0, 0x00, 0x00, 0x03, 0x00, 0x00,
          0x03, 0x00, 0x99, 0xA0, 0x01, 0xE0, 0x20, 0x02, 0x1C, 0x4D, 0x94, 0x62, 0x8D, 0x92, 0x42,
          0x97, 0x55, 0x58, 0x43, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xF9, 0x18, 0x82, 0x8D,
          0x08, 0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x91, 0x88, 0x28, 0xD0, 0x87, 0xFF,
          0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xF9, 0x2B, 0xC0, 0x41, 0x40, 0x00, 0x00, 0xFA, 0x40,
          0x00, 0x3A, 0x98, 0x3C, 0x24, 0x82, 0x4D, 0xC0, 0x00, 0x26, 0x25, 0xA0, 0x00, 0x13, 0x12,
          0xDF, 0xC4, 0xC7, 0x8F, 0x40);
  private static final byte[] H265_SPS_TEST_DATA_2VIEWS_HDR_VIEW_1 =
      createByteArray(
          0x42, 0x09, 0x0E, 0x85, 0x92, 0x42, 0x96, 0xAA, 0xAC, 0x21, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
          0xFF, 0xFF, 0xFC, 0x8C, 0x41, 0x46, 0x84, 0x3F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
          0xC8, 0xC4, 0x14, 0x68, 0x43, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFC, 0x95, 0xA8,
          0x18);
  private static final byte[] H265_SPS_TEST_DATA_2VIEWS_HDR_SEI =
      createByteArray(0x4E, 0x01, 0xB0, 0x04, 0x04, 0x0D, 0x00, 0x20, 0x80);

  @Test
  public void findNalUnit() {
    byte[] data = buildTestData();

    // Should find NAL unit.
    int result = NalUnitUtil.findNalUnit(data, 0, data.length, new boolean[3]);
    assertThat(result).isEqualTo(TEST_NAL_POSITION);
    // Should find NAL unit whose prefix ends one byte before the limit.
    result = NalUnitUtil.findNalUnit(data, 0, TEST_NAL_POSITION + 4, new boolean[3]);
    assertThat(result).isEqualTo(TEST_NAL_POSITION);
    // Shouldn't find NAL unit whose prefix ends at the limit (since the limit is exclusive).
    result = NalUnitUtil.findNalUnit(data, 0, TEST_NAL_POSITION + 3, new boolean[3]);
    assertThat(result).isEqualTo(TEST_NAL_POSITION + 3);
    // Should find NAL unit whose prefix starts at the offset.
    result = NalUnitUtil.findNalUnit(data, TEST_NAL_POSITION, data.length, new boolean[3]);
    assertThat(result).isEqualTo(TEST_NAL_POSITION);
    // Shouldn't find NAL unit whose prefix starts one byte past the offset.
    result = NalUnitUtil.findNalUnit(data, TEST_NAL_POSITION + 1, data.length, new boolean[3]);
    assertThat(result).isEqualTo(data.length);
  }

  @Test
  public void findNalUnitWithPrefix() {
    byte[] data = buildTestData();

    // First byte of NAL unit in data1, rest in data2.
    boolean[] prefixFlags = new boolean[3];
    byte[] data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 1);
    byte[] data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 1, data.length);
    int result = NalUnitUtil.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertThat(result).isEqualTo(data1.length);
    result = NalUnitUtil.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertThat(result).isEqualTo(-1);
    assertPrefixFlagsCleared(prefixFlags);

    // First three bytes of NAL unit in data1, rest in data2.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 3);
    data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 3, data.length);
    result = NalUnitUtil.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertThat(result).isEqualTo(data1.length);
    result = NalUnitUtil.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertThat(result).isEqualTo(-3);
    assertPrefixFlagsCleared(prefixFlags);

    // First byte of NAL unit in data1, second byte in data2, rest in data3.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 1);
    data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 1, TEST_NAL_POSITION + 2);
    byte[] data3 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 2, data.length);
    result = NalUnitUtil.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertThat(result).isEqualTo(data1.length);
    result = NalUnitUtil.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertThat(result).isEqualTo(data2.length);
    result = NalUnitUtil.findNalUnit(data3, 0, data3.length, prefixFlags);
    assertThat(result).isEqualTo(-2);
    assertPrefixFlagsCleared(prefixFlags);

    // NAL unit split with one byte in four arrays.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_NAL_POSITION + 1);
    data2 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 1, TEST_NAL_POSITION + 2);
    data3 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 2, TEST_NAL_POSITION + 3);
    byte[] data4 = Arrays.copyOfRange(data, TEST_NAL_POSITION + 2, data.length);
    result = NalUnitUtil.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertThat(result).isEqualTo(data1.length);
    result = NalUnitUtil.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertThat(result).isEqualTo(data2.length);
    result = NalUnitUtil.findNalUnit(data3, 0, data3.length, prefixFlags);
    assertThat(result).isEqualTo(data3.length);
    result = NalUnitUtil.findNalUnit(data4, 0, data4.length, prefixFlags);
    assertThat(result).isEqualTo(-3);
    assertPrefixFlagsCleared(prefixFlags);

    // NAL unit entirely in data2. data1 ends with partial prefix.
    prefixFlags = new boolean[3];
    data1 = Arrays.copyOfRange(data, 0, TEST_PARTIAL_NAL_POSITION + 2);
    data2 = Arrays.copyOfRange(data, TEST_PARTIAL_NAL_POSITION + 2, data.length);
    result = NalUnitUtil.findNalUnit(data1, 0, data1.length, prefixFlags);
    assertThat(result).isEqualTo(data1.length);
    result = NalUnitUtil.findNalUnit(data2, 0, data2.length, prefixFlags);
    assertThat(result).isEqualTo(4);
    assertPrefixFlagsCleared(prefixFlags);
  }

  @Test
  public void parseSpsNalUnit() {
    NalUnitUtil.SpsData data =
        NalUnitUtil.parseSpsNalUnit(SPS_TEST_DATA, SPS_TEST_DATA_OFFSET, SPS_TEST_DATA.length);
    assertThat(data.maxNumRefFrames).isEqualTo(4);
    assertThat(data.width).isEqualTo(640);
    assertThat(data.height).isEqualTo(360);
    assertThat(data.deltaPicOrderAlwaysZeroFlag).isFalse();
    assertThat(data.frameMbsOnlyFlag).isTrue();
    assertThat(data.frameNumLength).isEqualTo(4);
    assertThat(data.picOrderCntLsbLength).isEqualTo(6);
    assertThat(data.seqParameterSetId).isEqualTo(0);
    assertThat(data.pixelWidthHeightRatio).isEqualTo(1.0f);
    assertThat(data.picOrderCountType).isEqualTo(0);
    assertThat(data.separateColorPlaneFlag).isFalse();
    assertThat(data.colorSpace).isEqualTo(6);
    assertThat(data.colorRange).isEqualTo(2);
    assertThat(data.colorTransfer).isEqualTo(6);
    assertThat(data.maxNumReorderFrames).isEqualTo(1);
  }

  @Test
  public void parseH265VpsAndSpsNalUnits() {
    NalUnitUtil.H265VpsData vpsData =
        NalUnitUtil.parseH265VpsNalUnit(
            H265_VPS_TEST_DATA, /* nalOffset= */ 0, H265_VPS_TEST_DATA.length);
    assertThat(vpsData.nalHeader.layerId).isEqualTo(0);
    assertThat(vpsData.layerInfos).isEmpty();
    ImmutableList<NalUnitUtil.H265ProfileTierLevel> profileTierLevels =
        vpsData.profileTierLevelsAndIndices.profileTierLevels;
    assertThat(profileTierLevels).hasSize(1);
    assertThat(profileTierLevels.get(0).generalProfileIdc).isEqualTo(2);
    assertThat(profileTierLevels.get(0).generalProfileCompatibilityFlags).isEqualTo(4);
    assertThat(profileTierLevels.get(0).generalLevelIdc).isEqualTo(153);

    NalUnitUtil.H265SpsData spsData =
        NalUnitUtil.parseH265SpsNalUnit(
            H265_SPS_TEST_DATA, /* nalOffset= */ 0, H265_SPS_TEST_DATA.length, vpsData);
    assertThat(spsData.nalHeader.layerId).isEqualTo(0);
    assertThat(spsData.maxSubLayersMinus1).isEqualTo(0);
    assertThat(spsData.profileTierLevel.generalProfileIdc).isEqualTo(2);
    assertThat(spsData.profileTierLevel.generalProfileCompatibilityFlags).isEqualTo(4);
    assertThat(spsData.profileTierLevel.generalLevelIdc).isEqualTo(153);
    assertThat(spsData.chromaFormatIdc).isEqualTo(1);
    assertThat(spsData.width).isEqualTo(3840);
    assertThat(spsData.height).isEqualTo(2160);
    assertThat(spsData.bitDepthLumaMinus8).isEqualTo(2);
    assertThat(spsData.bitDepthChromaMinus8).isEqualTo(2);
    assertThat(spsData.colorSpace).isEqualTo(6);
    assertThat(spsData.colorRange).isEqualTo(2);
    assertThat(spsData.colorTransfer).isEqualTo(7);
  }

  @Test
  public void parseH265VpsAndSpsNalUnits2Views() {
    NalUnitUtil.H265VpsData vpsData =
        NalUnitUtil.parseH265VpsNalUnit(
            H265_VPS_TEST_DATA_2VIEWS, /* nalOffset= */ 0, H265_VPS_TEST_DATA_2VIEWS.length);
    assertThat(vpsData.nalHeader.layerId).isEqualTo(0);
    ImmutableList<NalUnitUtil.H265LayerInfo> layerInfos = vpsData.layerInfos;
    assertThat(layerInfos).hasSize(2);
    assertThat(layerInfos.get(0).layerIdInVps).isEqualTo(0);
    assertThat(layerInfos.get(0).viewId).isEqualTo(0);
    assertThat(layerInfos.get(1).layerIdInVps).isEqualTo(1);
    assertThat(layerInfos.get(1).viewId).isEqualTo(1);
    ImmutableList<NalUnitUtil.H265ProfileTierLevel> profileTierLevels =
        vpsData.profileTierLevelsAndIndices.profileTierLevels;
    assertThat(profileTierLevels).hasSize(3);
    assertThat(profileTierLevels.get(0).generalProfileIdc).isEqualTo(1);
    assertThat(profileTierLevels.get(0).generalProfileCompatibilityFlags).isEqualTo(6);
    assertThat(profileTierLevels.get(0).generalLevelIdc).isEqualTo(120);
    assertThat(profileTierLevels.get(1).generalProfileIdc).isEqualTo(1);
    assertThat(profileTierLevels.get(1).generalProfileCompatibilityFlags).isEqualTo(6);
    assertThat(profileTierLevels.get(1).generalLevelIdc).isEqualTo(0);
    assertThat(profileTierLevels.get(2).generalProfileIdc).isEqualTo(6);
    assertThat(profileTierLevels.get(2).generalProfileCompatibilityFlags).isEqualTo(64);
    assertThat(profileTierLevels.get(2).generalLevelIdc).isEqualTo(120);
    ImmutableList<NalUnitUtil.H265RepFormat> repFormats = vpsData.repFormatsAndIndices.repFormats;
    assertThat(repFormats).hasSize(1);
    assertThat(repFormats.get(0).chromaFormatIdc).isEqualTo(1);
    assertThat(repFormats.get(0).width).isEqualTo(1920);
    assertThat(repFormats.get(0).height).isEqualTo(1080);
    assertThat(repFormats.get(0).bitDepthLumaMinus8).isEqualTo(0);
    assertThat(repFormats.get(0).bitDepthChromaMinus8).isEqualTo(0);

    NalUnitUtil.H265SpsData spsDataView0 =
        NalUnitUtil.parseH265SpsNalUnit(
            H265_SPS_TEST_DATA_2VIEWS_VIEW_0,
            /* nalOffset= */ 0,
            H265_SPS_TEST_DATA_2VIEWS_VIEW_0.length,
            vpsData);
    assertThat(spsDataView0.nalHeader.layerId).isEqualTo(0);
    assertThat(spsDataView0.maxSubLayersMinus1).isEqualTo(0);
    assertThat(spsDataView0.profileTierLevel.generalProfileIdc).isEqualTo(1);
    assertThat(spsDataView0.profileTierLevel.generalProfileCompatibilityFlags).isEqualTo(6);
    assertThat(spsDataView0.profileTierLevel.generalLevelIdc).isEqualTo(120);
    assertThat(spsDataView0.chromaFormatIdc).isEqualTo(1);
    assertThat(spsDataView0.width).isEqualTo(1920);
    assertThat(spsDataView0.height).isEqualTo(1080);
    assertThat(spsDataView0.bitDepthLumaMinus8).isEqualTo(0);
    assertThat(spsDataView0.bitDepthChromaMinus8).isEqualTo(0);
    assertThat(spsDataView0.colorSpace).isEqualTo(1);
    assertThat(spsDataView0.colorRange).isEqualTo(2);
    assertThat(spsDataView0.colorTransfer).isEqualTo(3);

    NalUnitUtil.H265SpsData spsDataView1 =
        NalUnitUtil.parseH265SpsNalUnit(
            H265_SPS_TEST_DATA_2VIEWS_VIEW_1,
            /* nalOffset= */ 0,
            H265_SPS_TEST_DATA_2VIEWS_VIEW_1.length,
            vpsData);
    assertThat(spsDataView1.nalHeader.layerId).isEqualTo(1);
    assertThat(spsDataView1.maxSubLayersMinus1).isEqualTo(7);
    assertThat(spsDataView1.profileTierLevel.generalProfileIdc).isEqualTo(6);
    assertThat(spsDataView1.profileTierLevel.generalProfileCompatibilityFlags).isEqualTo(64);
    assertThat(spsDataView1.profileTierLevel.generalLevelIdc).isEqualTo(120);
    assertThat(spsDataView1.chromaFormatIdc).isEqualTo(1);
    assertThat(spsDataView1.width).isEqualTo(1920);
    assertThat(spsDataView1.height).isEqualTo(1080);
    assertThat(spsDataView1.bitDepthLumaMinus8).isEqualTo(0);
    assertThat(spsDataView1.bitDepthChromaMinus8).isEqualTo(0);

    NalUnitUtil.H265Sei3dRefDisplayInfoData seiData =
        NalUnitUtil.parseH265Sei3dRefDisplayInfo(
            H265_SPS_TEST_DATA_2VIEWS_SEI, 0, H265_SPS_TEST_DATA_2VIEWS_SEI.length);
    assertThat(seiData.precRefDisplayWidth).isEqualTo(31);
    assertThat(seiData.precRefViewingDist).isEqualTo(0);
    assertThat(seiData.numRefDisplays).isEqualTo(1);
    assertThat(seiData.leftViewId).isEqualTo(1);
    assertThat(seiData.rightViewId).isEqualTo(0);
    assertThat(seiData.exponentRefDisplayWidth).isEqualTo(0);
    assertThat(seiData.mantissaRefDisplayWidth).isEqualTo(0);
  }

  @Test
  public void parseH265VpsAndSpsNalUnits2ViewsHdr() {
    NalUnitUtil.H265VpsData vpsData =
        NalUnitUtil.parseH265VpsNalUnit(
            H265_VPS_TEST_DATA_2VIEWS_HDR,
            /* nalOffset= */ 0,
            H265_VPS_TEST_DATA_2VIEWS_HDR.length);
    assertThat(vpsData.nalHeader.layerId).isEqualTo(0);
    ImmutableList<NalUnitUtil.H265LayerInfo> layerInfos = vpsData.layerInfos;
    assertThat(layerInfos).hasSize(2);
    assertThat(layerInfos.get(0).layerIdInVps).isEqualTo(0);
    assertThat(layerInfos.get(0).viewId).isEqualTo(0);
    assertThat(layerInfos.get(1).layerIdInVps).isEqualTo(1);
    assertThat(layerInfos.get(1).viewId).isEqualTo(1);
    ImmutableList<NalUnitUtil.H265ProfileTierLevel> profileTierLevels =
        vpsData.profileTierLevelsAndIndices.profileTierLevels;
    assertThat(profileTierLevels).hasSize(3);
    assertThat(profileTierLevels.get(0).generalProfileIdc).isEqualTo(2);
    assertThat(profileTierLevels.get(0).generalProfileCompatibilityFlags).isEqualTo(4);
    assertThat(profileTierLevels.get(0).generalLevelIdc).isEqualTo(153);
    assertThat(profileTierLevels.get(1).generalProfileIdc).isEqualTo(2);
    assertThat(profileTierLevels.get(1).generalProfileCompatibilityFlags).isEqualTo(4);
    assertThat(profileTierLevels.get(1).generalLevelIdc).isEqualTo(153);
    assertThat(profileTierLevels.get(2).generalProfileIdc).isEqualTo(6);
    assertThat(profileTierLevels.get(2).generalProfileCompatibilityFlags).isEqualTo(68);
    assertThat(profileTierLevels.get(2).generalLevelIdc).isEqualTo(153);
    ImmutableList<NalUnitUtil.H265RepFormat> repFormats = vpsData.repFormatsAndIndices.repFormats;
    assertThat(repFormats).hasSize(1);
    assertThat(repFormats.get(0).chromaFormatIdc).isEqualTo(1);
    assertThat(repFormats.get(0).width).isEqualTo(3840);
    assertThat(repFormats.get(0).height).isEqualTo(2160);
    assertThat(repFormats.get(0).bitDepthLumaMinus8).isEqualTo(2);
    assertThat(repFormats.get(0).bitDepthChromaMinus8).isEqualTo(2);

    NalUnitUtil.H265SpsData spsDataView0 =
        NalUnitUtil.parseH265SpsNalUnit(
            H265_SPS_TEST_DATA_2VIEWS_HDR_VIEW_0,
            /* nalOffset= */ 0,
            H265_SPS_TEST_DATA_2VIEWS_HDR_VIEW_0.length,
            vpsData);
    assertThat(spsDataView0.nalHeader.layerId).isEqualTo(0);
    assertThat(spsDataView0.maxSubLayersMinus1).isEqualTo(0);
    assertThat(spsDataView0.profileTierLevel.generalProfileIdc).isEqualTo(2);
    assertThat(spsDataView0.profileTierLevel.generalProfileCompatibilityFlags).isEqualTo(4);
    assertThat(spsDataView0.profileTierLevel.generalLevelIdc).isEqualTo(153);
    assertThat(spsDataView0.chromaFormatIdc).isEqualTo(1);
    assertThat(spsDataView0.width).isEqualTo(3840);
    assertThat(spsDataView0.height).isEqualTo(2160);
    assertThat(spsDataView0.bitDepthLumaMinus8).isEqualTo(2);
    assertThat(spsDataView0.bitDepthChromaMinus8).isEqualTo(2);

    NalUnitUtil.H265SpsData spsDataView1 =
        NalUnitUtil.parseH265SpsNalUnit(
            H265_SPS_TEST_DATA_2VIEWS_HDR_VIEW_1,
            /* nalOffset= */ 0,
            H265_SPS_TEST_DATA_2VIEWS_HDR_VIEW_1.length,
            vpsData);
    assertThat(spsDataView1.nalHeader.layerId).isEqualTo(1);
    assertThat(spsDataView1.maxSubLayersMinus1).isEqualTo(7);
    assertThat(spsDataView1.profileTierLevel.generalProfileIdc).isEqualTo(6);
    assertThat(spsDataView1.profileTierLevel.generalProfileCompatibilityFlags).isEqualTo(68);
    assertThat(spsDataView1.profileTierLevel.generalLevelIdc).isEqualTo(153);
    assertThat(spsDataView1.chromaFormatIdc).isEqualTo(1);
    assertThat(spsDataView1.width).isEqualTo(3840);
    assertThat(spsDataView1.height).isEqualTo(2160);
    assertThat(spsDataView1.bitDepthLumaMinus8).isEqualTo(2);
    assertThat(spsDataView1.bitDepthChromaMinus8).isEqualTo(2);

    NalUnitUtil.H265Sei3dRefDisplayInfoData seiData =
        NalUnitUtil.parseH265Sei3dRefDisplayInfo(
            H265_SPS_TEST_DATA_2VIEWS_HDR_SEI, 0, H265_SPS_TEST_DATA_2VIEWS_HDR_SEI.length);
    assertThat(seiData.precRefDisplayWidth).isEqualTo(31);
    assertThat(seiData.precRefViewingDist).isEqualTo(0);
    assertThat(seiData.numRefDisplays).isEqualTo(1);
    assertThat(seiData.leftViewId).isEqualTo(0);
    assertThat(seiData.rightViewId).isEqualTo(1);
    assertThat(seiData.exponentRefDisplayWidth).isEqualTo(0);
    assertThat(seiData.mantissaRefDisplayWidth).isEqualTo(0);
  }

  @Test
  public void unescapeDoesNotModifyBuffersWithoutStartCodes() {
    assertUnescapeDoesNotModify("");
    assertUnescapeDoesNotModify("0000");
    assertUnescapeDoesNotModify("172BF38A3C");
    assertUnescapeDoesNotModify("000004");
  }

  @Test
  public void unescapeModifiesBuffersWithStartCodes() {
    assertUnescapeMatchesExpected("00000301", "000001");
    assertUnescapeMatchesExpected("0000030200000300", "000002000000");
  }

  @Test
  public void discardToSps() {
    assertDiscardToSpsMatchesExpected("", "");
    assertDiscardToSpsMatchesExpected("00", "");
    assertDiscardToSpsMatchesExpected("FFFF000001", "");
    assertDiscardToSpsMatchesExpected("00000001", "");
    assertDiscardToSpsMatchesExpected("00000001FF67", "");
    assertDiscardToSpsMatchesExpected("00000001000167", "");
    assertDiscardToSpsMatchesExpected("0000000167", "0000000167");
    assertDiscardToSpsMatchesExpected("0000000167FF", "0000000167FF");
    assertDiscardToSpsMatchesExpected("0000000167FF", "0000000167FF");
    assertDiscardToSpsMatchesExpected("0000000167FF000000016700", "0000000167FF000000016700");
    assertDiscardToSpsMatchesExpected("000000000167FF", "0000000167FF");
    assertDiscardToSpsMatchesExpected("0001670000000167FF", "0000000167FF");
    assertDiscardToSpsMatchesExpected("FF00000001660000000167FF", "0000000167FF");
  }

  /** Regression test for https://github.com/google/ExoPlayer/issues/10316. */
  @Test
  public void parseH265SpsNalUnitPayload_exoghi_10316() {
    byte[] spsNalUnitPayload =
        new byte[] {
          1, 2, 32, 0, 0, 3, 0, -112, 0, 0, 3, 0, 0, 3, 0, -106, -96, 1, -32, 32, 2, 28, 77, -98,
          87, -110, 66, -111, -123, 22, 74, -86, -53, -101, -98, -68, -28, 9, 119, -21, -103, 120,
          -16, 22, -95, 34, 1, 54, -62, 0, 0, 7, -46, 0, 0, -69, -127, -12, 85, -17, 126, 0, -29,
          -128, 28, 120, 1, -57, 0, 56, -15
        };

    NalUnitUtil.H265NalHeader nalHeader =
        new NalUnitUtil.H265NalHeader(NalUnitUtil.H265_NAL_UNIT_TYPE_SPS, 0, 0);
    NalUnitUtil.H265SpsData spsData =
        NalUnitUtil.parseH265SpsNalUnitPayload(
            spsNalUnitPayload, 0, spsNalUnitPayload.length, nalHeader, null);

    assertThat(spsData.maxSubLayersMinus1).isEqualTo(0);
    assertThat(spsData.profileTierLevel.constraintBytes).isEqualTo(new int[] {144, 0, 0, 0, 0, 0});
    assertThat(spsData.profileTierLevel.generalLevelIdc).isEqualTo(150);
    assertThat(spsData.profileTierLevel.generalProfileCompatibilityFlags).isEqualTo(4);
    assertThat(spsData.profileTierLevel.generalProfileIdc).isEqualTo(2);
    assertThat(spsData.profileTierLevel.generalProfileSpace).isEqualTo(0);
    assertThat(spsData.profileTierLevel.generalTierFlag).isFalse();
    assertThat(spsData.height).isEqualTo(2160);
    assertThat(spsData.pixelWidthHeightRatio).isEqualTo(1);
    assertThat(spsData.seqParameterSetId).isEqualTo(0);
    assertThat(spsData.width).isEqualTo(3840);
    assertThat(spsData.colorSpace).isEqualTo(6);
    assertThat(spsData.colorRange).isEqualTo(2);
    assertThat(spsData.colorTransfer).isEqualTo(6);
    assertThat(spsData.maxNumReorderPics).isEqualTo(2);
  }

  /** Regression test for [Internal: b/292170736]. */
  @Test
  public void parseH265SpsNalUnitPayload_withShortTermRefPicSets() {
    byte[] spsNalUnitPayload =
        new byte[] {
          1, 2, 96, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, 0, 3, 0, -106, -96, 2, 28, -128, 30, 4, -39, 111,
          -110, 76, -114, -65, -7, -13, 101, 33, -51, 66, 68, 2, 65, 0, 0, 3, 0, 1, 0, 0, 3, 0, 29,
          8
        };

    NalUnitUtil.H265NalHeader nalHeader =
        new NalUnitUtil.H265NalHeader(NalUnitUtil.H265_NAL_UNIT_TYPE_SPS, 0, 0);
    NalUnitUtil.H265SpsData spsData =
        NalUnitUtil.parseH265SpsNalUnitPayload(
            spsNalUnitPayload, 0, spsNalUnitPayload.length, nalHeader, null);

    assertThat(spsData.maxSubLayersMinus1).isEqualTo(0);
    assertThat(spsData.profileTierLevel.constraintBytes).isEqualTo(new int[] {0, 0, 0, 0, 0, 0});
    assertThat(spsData.profileTierLevel.generalLevelIdc).isEqualTo(150);
    assertThat(spsData.profileTierLevel.generalProfileCompatibilityFlags).isEqualTo(6);
    assertThat(spsData.profileTierLevel.generalProfileIdc).isEqualTo(2);
    assertThat(spsData.profileTierLevel.generalProfileSpace).isEqualTo(0);
    assertThat(spsData.profileTierLevel.generalTierFlag).isFalse();
    assertThat(spsData.width).isEqualTo(1080);
    assertThat(spsData.height).isEqualTo(1920);
    assertThat(spsData.pixelWidthHeightRatio).isEqualTo(1);
    assertThat(spsData.seqParameterSetId).isEqualTo(0);
    assertThat(spsData.chromaFormatIdc).isEqualTo(1);
    assertThat(spsData.bitDepthLumaMinus8).isEqualTo(2);
    assertThat(spsData.bitDepthChromaMinus8).isEqualTo(2);
    assertThat(spsData.colorSpace).isEqualTo(6);
    assertThat(spsData.colorRange).isEqualTo(2);
    assertThat(spsData.colorTransfer).isEqualTo(6);
    assertThat(spsData.maxNumReorderPics).isEqualTo(0);
  }

  @Test
  public void numberOfBytesInNalUnitHeader_vp8_returnsZero() {
    Format vp8Video = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_VP8).build();

    assertThat(numberOfBytesInNalUnitHeader(vp8Video)).isEqualTo(0);
  }

  @Test
  public void isDependedOn_vp8_returnsTrue() {
    Format vp8Video = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_VP8).build();

    assertThat(isDependedOn(new byte[0], /* offset= */ 0, /* length= */ 0, vp8Video)).isTrue();
  }

  private static byte[] buildTestData() {
    byte[] data = new byte[20];
    Arrays.fill(data, (byte) 0xFF);
    // Insert an incomplete NAL unit start code.
    data[TEST_PARTIAL_NAL_POSITION] = 0;
    data[TEST_PARTIAL_NAL_POSITION + 1] = 0;
    // Insert a complete NAL unit start code.
    data[TEST_NAL_POSITION] = 0;
    data[TEST_NAL_POSITION + 1] = 0;
    data[TEST_NAL_POSITION + 2] = 1;
    data[TEST_NAL_POSITION + 3] = 5;
    return data;
  }

  private static void assertPrefixFlagsCleared(boolean[] flags) {
    assertThat(flags[0] || flags[1] || flags[2]).isEqualTo(false);
  }

  private static void assertUnescapeDoesNotModify(String input) {
    assertUnescapeMatchesExpected(input, input);
  }

  private static void assertUnescapeMatchesExpected(String input, String expectedOutput) {
    byte[] bitstream = Util.getBytesFromHexString(input);
    byte[] expectedOutputBitstream = Util.getBytesFromHexString(expectedOutput);
    int count = NalUnitUtil.unescapeStream(bitstream, bitstream.length);
    assertThat(count).isEqualTo(expectedOutputBitstream.length);
    byte[] outputBitstream = new byte[count];
    System.arraycopy(bitstream, 0, outputBitstream, 0, count);
    assertThat(outputBitstream).isEqualTo(expectedOutputBitstream);
  }

  private static void assertDiscardToSpsMatchesExpected(String input, String expectedOutput) {
    byte[] bitstream = Util.getBytesFromHexString(input);
    byte[] expectedOutputBitstream = Util.getBytesFromHexString(expectedOutput);
    ByteBuffer buffer = ByteBuffer.wrap(bitstream);
    buffer.position(buffer.limit());
    NalUnitUtil.discardToSps(buffer);
    assertThat(Arrays.copyOf(buffer.array(), buffer.position())).isEqualTo(expectedOutputBitstream);
  }
}
