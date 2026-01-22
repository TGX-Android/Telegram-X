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
package androidx.media3.muxer;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AnnexBToAvccConverter#DEFAULT}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultAnnexBToAvccConverterTest {
  @Test
  public void convertAnnexBToAvcc_singleNalUnit() {
    ByteBuffer input = generateFakeNalUnitData(1000);
    // Add start code for the NAL unit.
    input.put(0, (byte) 0);
    input.put(1, (byte) 0);
    input.put(2, (byte) 0);
    input.put(3, (byte) 1);

    AnnexBToAvccConverter annexBToAvccConverter = AnnexBToAvccConverter.DEFAULT;
    ByteBuffer output = annexBToAvccConverter.process(input);

    // The start code should get replaced with the length of the NAL unit.
    assertThat(output.getInt(0)).isEqualTo(996);
  }

  @Test
  public void convertAnnexBToAvcc_twoNalUnits() {
    ByteBuffer input = generateFakeNalUnitData(1000);
    // Add start code for the first NAL unit.
    input.put(0, (byte) 0);
    input.put(1, (byte) 0);
    input.put(2, (byte) 0);
    input.put(3, (byte) 1);

    // Add start code for the second NAL unit.
    input.put(600, (byte) 0);
    input.put(601, (byte) 0);
    input.put(602, (byte) 0);
    input.put(603, (byte) 1);

    AnnexBToAvccConverter annexBToAvccConverter = AnnexBToAvccConverter.DEFAULT;
    ByteBuffer output = annexBToAvccConverter.process(input);

    // Both the NAL units should have length headers.
    assertThat(output.getInt(0)).isEqualTo(596);
    assertThat(output.getInt(600)).isEqualTo(396);
  }

  @Test
  public void convertAnnexBToAvcc_noNalUnit_throws() {
    ByteBuffer input = generateFakeNalUnitData(1000);

    AnnexBToAvccConverter annexBToAvccConverter = AnnexBToAvccConverter.DEFAULT;
    assertThrows(IllegalStateException.class, () -> annexBToAvccConverter.process(input));
  }

  @Test
  public void convertAnnexBToAvcc_withExtraZeroesAtTheEnd_removesExtraZeroes() {
    ByteBuffer input = ByteBuffer.allocate(10);
    // Add 3 byte start code for the NAL unit.
    input.put(0, (byte) 0);
    input.put(1, (byte) 0);
    input.put(2, (byte) 1);
    // Add fake NAL unit data;
    input.put(3, (byte) 300);
    input.put(4, (byte) 300);
    input.put(5, (byte) 300);
    input.put(6, (byte) 300);
    // Add extra zeroes at the end.
    input.put(7, (byte) 0);
    input.put(8, (byte) 0);
    input.put(9, (byte) 0);

    AnnexBToAvccConverter annexBToAvccConverter = AnnexBToAvccConverter.DEFAULT;
    ByteBuffer output = annexBToAvccConverter.process(input);

    ByteBuffer expectedOutput = ByteBuffer.allocate(8);
    // First 4 bytes for NAL unit length.
    expectedOutput.putInt(4);
    // Fake NAL unit data;
    expectedOutput.put(4, (byte) 300);
    expectedOutput.put(5, (byte) 300);
    expectedOutput.put(6, (byte) 300);
    expectedOutput.put(7, (byte) 300);
    expectedOutput.rewind();
    assertThat(output).isEqualTo(expectedOutput);
  }

  /** Returns {@link ByteBuffer} filled with random NAL unit data without start code. */
  private static ByteBuffer generateFakeNalUnitData(int length) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(length);
    for (int i = 0; i < length; i++) {
      // Avoid anything resembling start codes (0x00000001) or emulation prevention byte (0x03).
      buffer.put((byte) ((i % 250) + 5));
    }

    buffer.rewind();
    return buffer;
  }
}
