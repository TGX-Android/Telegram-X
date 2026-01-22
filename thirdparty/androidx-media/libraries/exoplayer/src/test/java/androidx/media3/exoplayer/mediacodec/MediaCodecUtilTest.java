/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.media.MediaCodecInfo;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MediaCodecUtil}. */
@RunWith(AndroidJUnit4.class)
public final class MediaCodecUtilTest {

  private static final byte[] CSD0 =
      new byte[] {
        // Start code
        0,
        0,
        0,
        1,
        // VPS
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
        // Start code
        0,
        0,
        0,
        1,
        // SPS for layer 0
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
        // Start code
        0,
        0,
        0,
        1,
        // PPS for layer 0
        68,
        1,
        -64,
        44,
        -68,
        20,
        -55,
        // Start code
        0,
        0,
        0,
        1,
        // SEI
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

  private static final byte[] CSD1 =
      new byte[] {
        // Start code
        0,
        0,
        0,
        1,
        // SPS for layer 1
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
        // Start code
        0,
        0,
        0,
        1,
        // PPS for layer 1
        68,
        9,
        72,
        2,
        -53,
        -63,
        77,
        -88,
        5
      };

  @Test
  public void getHevcBaseLayerCodecProfileAndLevel_handlesFallbackFromMvHevc() {
    Format format =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_MV_HEVC)
            .setCodecs("hvc1.6.40.L120.BF.80")
            .setInitializationData(ImmutableList.of(CSD0, CSD1))
            .build();
    assertHevcBaseLayerCodecProfileAndLevelForFormat(
        format,
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain,
        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4);
  }

  @Test
  public void getHevcBaseLayerCodecProfileAndLevel_rejectsFormatWithNoInitializationData() {
    Format format =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_MV_HEVC)
            .setCodecs("hvc1.6.40.L120.BF.80")
            .build();
    assertThat(MediaCodecUtil.getHevcBaseLayerCodecProfileAndLevel(format)).isNull();
  }

  private static void assertHevcBaseLayerCodecProfileAndLevelForFormat(
      Format format, int profile, int level) {
    @Nullable
    Pair<Integer, Integer> codecProfileAndLevel =
        MediaCodecUtil.getHevcBaseLayerCodecProfileAndLevel(format);
    assertThat(codecProfileAndLevel).isNotNull();
    assertThat(codecProfileAndLevel.first).isEqualTo(profile);
    assertThat(codecProfileAndLevel.second).isEqualTo(level);
  }
}
