/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.decoder.iamf;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test IAMF native functions. */
@RunWith(AndroidJUnit4.class)
public final class IamfDecoderTest {
  private static final int DEFAULT_BINAURAL_LAYOUT_CHANNEL_COUNT = 2;

  // Sample configOBUs data from sample_iamf.mp4 file.
  private static final byte[] IACB_OBUS = {
    -8, 6, 105, 97, 109, 102, 0, 0, 0, 15, -56, 1, 105, 112, 99, 109, 64, 0, 0, 1, 16, 0, 0, 62,
    -128, 8, 12, -84, 2, 0, -56, 1, 1, 0, 0, 32, 16, 1, 1, 16, 78, 42, 1, 101, 110, 45, 117, 115, 0,
    116, 101, 115, 116, 95, 109, 105, 120, 95, 112, 114, 101, 115, 0, 1, 1, -84, 2, 116, 101, 115,
    116, 95, 115, 117, 98, 95, 109, 105, 120, 95, 48, 95, 97, 117, 100, 105, 111, 95, 101, 108, 101,
    109, 101, 110, 116, 95, 48, 0, 0, 0, 100, -128, 125, -128, 0, 0, 100, -128, 125, -128, 0, 0, 1,
    -128, 0, -54, 81, -51, -79
  };

  @Before
  public void setUp() {
    assumeTrue(IamfLibrary.isAvailable());
  }

  @Test
  public void iamfBinauralLayoutChannelsCount_equalsTwo() throws Exception {
    IamfDecoder iamf =
        new IamfDecoder(ImmutableList.of(IACB_OBUS), /* spatializationSupported= */ false);

    assertThat(iamf.getBinauralLayoutChannelCount())
        .isEqualTo(DEFAULT_BINAURAL_LAYOUT_CHANNEL_COUNT);
  }
}
