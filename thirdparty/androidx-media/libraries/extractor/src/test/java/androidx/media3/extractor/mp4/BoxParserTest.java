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
package androidx.media3.extractor.mp4;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.container.Mp4Box;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link BoxParser}. */
@RunWith(AndroidJUnit4.class)
public final class BoxParserTest {

  private static final String ATOM_HEADER = "000000000000000000000000";
  private static final String SAMPLE_COUNT = "00000004";
  private static final byte[] FOUR_BIT_STZ2 =
      Util.getBytesFromHexString(ATOM_HEADER + "00000004" + SAMPLE_COUNT + "1234");
  private static final byte[] EIGHT_BIT_STZ2 =
      Util.getBytesFromHexString(ATOM_HEADER + "00000008" + SAMPLE_COUNT + "01020304");
  private static final byte[] SIXTEEN_BIT_STZ2 =
      Util.getBytesFromHexString(ATOM_HEADER + "00000010" + SAMPLE_COUNT + "0001000200030004");

  // Sample 'vexu' with 'eyes' containing 'stri' along with other optional boxes.
  private static final byte[] VEXU_DATA0 =
      new byte[] {
        // size (101), 'vexu'
        0,
        0,
        0,
        101,
        118,
        101,
        120,
        117,
        // size (69), 'eyes'
        0,
        0,
        0,
        69,
        101,
        121,
        101,
        115,
        // size (13), 'stri'
        0,
        0,
        0,
        13,
        115,
        116,
        114,
        105,
        0,
        0,
        0,
        0,
        3,
        // size (24), 'cams'
        0,
        0,
        0,
        24,
        99,
        97,
        109,
        115,
        0,
        0,
        0,
        16,
        98,
        108,
        105,
        110,
        0,
        0,
        0,
        0,
        0,
        0,
        75,
        40,
        // size (24), 'cmfy'
        0,
        0,
        0,
        24,
        99,
        109,
        102,
        121,
        0,
        0,
        0,
        16,
        100,
        97,
        100,
        106,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        -56,
        // size (24), 'proj'
        0,
        0,
        0,
        24,
        112,
        114,
        111,
        106,
        0,
        0,
        0,
        16,
        112,
        114,
        106,
        105,
        0,
        0,
        0,
        0,
        114,
        101,
        99,
        116,
      };
  // Sample 'vexu' with the use of 'must' to list required boxes.
  private static final byte[] VEXU_DATA1 =
      new byte[] {
        // size (78), 'vexu'
        0,
        0,
        0,
        78,
        118,
        101,
        120,
        117,
        // size (16), 'must' --> requires 'eyes'
        0,
        0,
        0,
        16,
        109,
        117,
        115,
        116,
        0,
        0,
        0,
        0,
        101,
        121,
        101,
        115,
        // size (54), 'eyes'
        0,
        0,
        0,
        54,
        101,
        121,
        101,
        115,
        // size (20), 'must' --> requires 'stri' and 'hero'
        0,
        0,
        0,
        20,
        109,
        117,
        115,
        116,
        0,
        0,
        0,
        0,
        115,
        116,
        114,
        105,
        104,
        101,
        114,
        111,
        // size (13), 'stri'
        0,
        0,
        0,
        13,
        115,
        116,
        114,
        105,
        0,
        0,
        0,
        0,
        3,
        // size (13), 'hero'
        0,
        0,
        0,
        13,
        104,
        101,
        114,
        111,
        0,
        0,
        0,
        0,
        1,
      };

  @Test
  public void parseCommonEncryptionSinfFromParentIgnoresUnknownSchemeType() throws ParserException {
    byte[] cencSinf =
        new byte[] {
          0, 0, 0, 24, 115, 105, 110, 102, // size (4), 'sinf' (4)
          0, 0, 0, 16, 115, 99, 104, 109, // size (4), 'schm' (4)
          0, 0, 0, 0, 88, 88, 88, 88
        }; // version (1), flags (3), 'xxxx' (4)
    assertThat(
            BoxParser.parseCommonEncryptionSinfFromParent(
                new ParsableByteArray(cencSinf), 0, cencSinf.length))
        .isNull();
  }

  @Test
  public void stz2Parsing4BitFieldSize() {
    verifyStz2Parsing(new Mp4Box.LeafBox(Mp4Box.TYPE_stsz, new ParsableByteArray(FOUR_BIT_STZ2)));
  }

  @Test
  public void stz2Parsing8BitFieldSize() {
    verifyStz2Parsing(new Mp4Box.LeafBox(Mp4Box.TYPE_stsz, new ParsableByteArray(EIGHT_BIT_STZ2)));
  }

  @Test
  public void stz2Parsing16BitFieldSize() {
    verifyStz2Parsing(
        new Mp4Box.LeafBox(Mp4Box.TYPE_stsz, new ParsableByteArray(SIXTEEN_BIT_STZ2)));
  }

  @Test
  public void vexuParsings() throws ParserException {
    BoxParser.VexuData vexuData = null;
    assertThat(
            vexuData =
                BoxParser.parseVideoExtendedUsageBox(
                    new ParsableByteArray(VEXU_DATA0), 0, VEXU_DATA0.length))
        .isNotNull();
    assertThat(vexuData).isNotNull();
    assertThat(vexuData.hasBothEyeViews()).isTrue();
    assertThat(
            vexuData =
                BoxParser.parseVideoExtendedUsageBox(
                    new ParsableByteArray(VEXU_DATA1), 0, VEXU_DATA1.length))
        .isNotNull();
    assertThat(vexuData).isNotNull();
    assertThat(vexuData.hasBothEyeViews()).isTrue();
  }

  private static void verifyStz2Parsing(Mp4Box.LeafBox stz2Atom) {
    BoxParser.Stz2SampleSizeBox box = new BoxParser.Stz2SampleSizeBox(stz2Atom);
    assertThat(box.getSampleCount()).isEqualTo(4);
    assertThat(box.getFixedSampleSize()).isEqualTo(C.LENGTH_UNSET);
    for (int i = 0; i < box.getSampleCount(); i++) {
      assertThat(box.readNextSampleSize()).isEqualTo(i + 1);
    }
  }
}
