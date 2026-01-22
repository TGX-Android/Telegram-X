/*
 * Copyright 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.util.ParsableByteArray;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link HevcConfig}. */
@RunWith(AndroidJUnit4.class)
public final class HevcConfigTest {

  private static final byte[] HVCC_BOX_PAYLOAD =
      new byte[] {
        // Header
        1,
        1,
        96,
        0,
        0,
        0,
        -80,
        0,
        0,
        0,
        0,
        0,
        -103,
        -16,
        0,
        -4,
        -4,
        -8,
        -8,
        0,
        0,
        15,

        // Number of arrays
        3,

        // NAL unit type = VPS
        32,
        // Number of NAL units
        0,
        1,
        // NAL unit length
        0,
        23,
        // NAL unit
        64,
        1,
        12,
        1,
        -1,
        -1,
        1,
        96,
        0,
        0,
        3,
        0,
        -80,
        0,
        0,
        3,
        0,
        0,
        3,
        0,
        -103,
        -84,
        9,

        // NAL unit type = SPS
        33,
        // Number of NAL units
        0,
        1,
        // NAL unit length
        0,
        39,
        // NAL unit
        66,
        1,
        1,
        1,
        96,
        0,
        0,
        3,
        0,
        -80,
        0,
        0,
        3,
        0,
        0,
        3,
        0,
        -103,
        -96,
        1,
        -32,
        32,
        2,
        32,
        124,
        78,
        90,
        -18,
        76,
        -110,
        -22,
        86,
        10,
        12,
        12,
        5,
        -38,
        20,
        37,

        // NAL unit type = PPS
        34,
        // Number of NAL units
        0,
        1,
        // NAL unit length
        0,
        14,
        // NAL unit
        68,
        1,
        -64,
        -29,
        15,
        8,
        -80,
        96,
        48,
        24,
        12,
        115,
        8,
        64
      };

  private static final byte[] HVCC_BOX_PAYLOAD_WITH_SET_RESERVED_BIT =
      new byte[] {
        // Header
        1,
        1,
        96,
        0,
        0,
        0,
        -80,
        0,
        0,
        0,
        0,
        0,
        -103,
        -16,
        0,
        -4,
        -4,
        -8,
        -8,
        0,
        0,
        15,

        // Number of arrays
        1,

        // NAL unit type = SPS (Ignoring reserved bit)
        // completeness (1), reserved (1), nal_unit_type (6)
        97,
        // Number of NAL units
        0,
        1,
        // NAL unit length
        0,
        39,
        // NAL unit
        66,
        1,
        1,
        1,
        96,
        0,
        0,
        3,
        0,
        -80,
        0,
        0,
        3,
        0,
        0,
        3,
        0,
        -103,
        -96,
        1,
        -32,
        32,
        2,
        32,
        124,
        78,
        90,
        -18,
        76,
        -110,
        -22,
        86,
        10,
        12,
        12,
        5,
        -38,
        20,
        37
      };

  private static final byte[] HVCC_BOX_PAYLOAD_MV_HEVC =
      new byte[] {
        // Header
        1,
        1,
        96,
        0,
        0,
        0,
        -80,
        0,
        0,
        0,
        0,
        0,
        120,
        -16,
        0,
        -4,
        -3,
        -8,
        -8,
        0,
        0,
        11,

        // Number of arrays
        4,

        // NAL unit type = VPS
        -96,
        // Number of NAL units
        0,
        1,
        // NAL unit length
        0,
        56,
        // NAL unit
        64,
        1,
        12,
        17,
        -1,
        -1,
        1,
        96,
        0,
        0,
        3,
        0,
        -80,
        0,
        0,
        3,
        0,
        0,
        3,
        0,
        120,
        21,
        -63,
        91,
        0,
        32,
        0,
        40,
        36,
        -63,
        -105,
        6,
        2,
        0,
        0,
        3,
        0,
        -65,
        -128,
        0,
        0,
        3,
        0,
        0,
        120,
        -115,
        7,
        -128,
        4,
        64,
        -96,
        30,
        92,
        82,
        -65,
        72,

        // NAL unit type = SPS
        -95,
        // Number of NAL units
        0,
        1,
        // NAL unit length
        0,
        36,
        // NAL unit
        66,
        1,
        1,
        1,
        96,
        0,
        0,
        3,
        0,
        -80,
        0,
        0,
        3,
        0,
        0,
        3,
        0,
        120,
        -96,
        3,
        -64,
        -128,
        17,
        7,
        -53,
        -120,
        21,
        -18,
        69,
        -107,
        77,
        64,
        64,
        64,
        64,
        32,

        // NAL unit type = PPS
        -94,
        // Number of NAL units
        0,
        1,
        // NAL unit length
        0,
        7,
        // NAL unit
        68,
        1,
        -64,
        44,
        -68,
        20,
        -55,

        // NAL unit type = SEI
        -89,
        // Number of NAL units
        0,
        1,
        // NAL unit length
        0,
        9,
        // NAL unit
        78,
        1,
        -80,
        4,
        4,
        10,
        -128,
        32,
        -128
      };

  private static final byte[] LHVC_BOX_PAYLOAD_MV_HEVC =
      new byte[] {
        // Header
        1,
        -16,
        0,
        -4,
        -53,

        // Number of arrays
        2,

        // NAL unit type = SPS
        -95,
        // Number of NAL units
        0,
        1,
        // NAL unit length
        0,
        10,
        // NAL unit
        66,
        9,
        14,
        -126,
        46,
        69,
        -118,
        -96,
        5,
        1,

        // NAL unit type = PPS
        -94,
        // Number of NAL units
        0,
        1,
        // NAL unit length
        0,
        9,
        // NAL unit
        68,
        9,
        72,
        2,
        -53,
        -63,
        77,
        -88,
        5,
      };

  @Test
  public void parseHevcDecoderConfigurationRecord() throws Exception {
    ParsableByteArray data = new ParsableByteArray(HVCC_BOX_PAYLOAD);
    HevcConfig hevcConfig = HevcConfig.parse(data);

    assertThat(hevcConfig.maxSubLayers).isEqualTo(1);
    assertThat(hevcConfig.codecs).isEqualTo("hvc1.1.6.L153.B0");
    assertThat(hevcConfig.nalUnitLengthFieldLength).isEqualTo(4);
  }

  /** https://github.com/google/ExoPlayer/issues/10366 */
  @Test
  public void parseHevcDecoderConfigurationRecord_ignoresReservedBit() throws Exception {
    ParsableByteArray data = new ParsableByteArray(HVCC_BOX_PAYLOAD_WITH_SET_RESERVED_BIT);
    HevcConfig hevcConfig = HevcConfig.parse(data);

    assertThat(hevcConfig.maxSubLayers).isEqualTo(1);
    assertThat(hevcConfig.codecs).isEqualTo("hvc1.1.6.L153.B0");
    assertThat(hevcConfig.nalUnitLengthFieldLength).isEqualTo(4);
  }

  @Test
  public void parseLhevcDecoderConfigurationRecord() throws Exception {
    ParsableByteArray hevcData = new ParsableByteArray(HVCC_BOX_PAYLOAD_MV_HEVC);
    HevcConfig hevcConfig = HevcConfig.parse(hevcData);

    assertThat(hevcConfig.maxSubLayers).isEqualTo(1);
    assertThat(hevcConfig.codecs).isEqualTo("hvc1.1.6.L120.B0");
    assertThat(hevcConfig.nalUnitLengthFieldLength).isEqualTo(4);

    ParsableByteArray lhevcData = new ParsableByteArray(LHVC_BOX_PAYLOAD_MV_HEVC);
    HevcConfig lhevcConfig = HevcConfig.parseLayered(lhevcData, hevcConfig.vpsData);

    assertThat(lhevcConfig.maxSubLayers).isEqualTo(8);
    assertThat(lhevcConfig.codecs).isEqualTo("hvc1.6.40.L120.BF.80");
    assertThat(lhevcConfig.nalUnitLengthFieldLength).isEqualTo(4);
  }
}
