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
package androidx.media3.exoplayer.audio;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link PcmAudioUtil}. */
@RunWith(AndroidJUnit4.class)
public final class PcmAudioUtilTest {

  @Test
  public void readAs32BitIntPcm_read8Bit_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(5);
    buffer.put(hexToBytes("80" + "AB" + "00" + "12" + "7F"));
    buffer.flip();

    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_8BIT))
        .isEqualTo(Integer.MIN_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_8BIT)).isEqualTo(0xAB000000);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_8BIT)).isEqualTo(0);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_8BIT)).isEqualTo(0x12000000);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_8BIT))
        .isWithin(0xFFFFFF)
        .of(Integer.MAX_VALUE);
  }

  @Test
  public void readAs32BitIntPcm_read16Bit_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(10);
    buffer.put(hexToBytes("0080" + "CDAB" + "0000" + "3412" + "FF7F"));
    buffer.flip();

    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_16BIT))
        .isEqualTo(Integer.MIN_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_16BIT)).isEqualTo(0xABCD0000);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_16BIT)).isEqualTo(0);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_16BIT)).isEqualTo(0x12340000);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_16BIT))
        .isWithin(0xFFFF)
        .of(Integer.MAX_VALUE);
  }

  @Test
  public void readAs32BitIntPcm_read16BitBigEndian_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(10);
    buffer.put(hexToBytes("8000" + "ABCD" + "0000" + "1234" + "7FFF"));
    buffer.flip();

    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_16BIT_BIG_ENDIAN))
        .isEqualTo(Integer.MIN_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_16BIT_BIG_ENDIAN))
        .isEqualTo(0xABCD0000);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_16BIT_BIG_ENDIAN))
        .isEqualTo(0);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_16BIT_BIG_ENDIAN))
        .isEqualTo(0x12340000);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_16BIT_BIG_ENDIAN))
        .isWithin(0xFFFF)
        .of(Integer.MAX_VALUE);
  }

  @Test
  public void readAs32BitIntPcm_read24Bit_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(15);
    buffer.put(hexToBytes("000080" + "EFCDAB" + "000000" + "563412" + "FFFF7F"));
    buffer.flip();

    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_24BIT))
        .isEqualTo(Integer.MIN_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_24BIT)).isEqualTo(0xABCDEF00);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_24BIT)).isEqualTo(0);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_24BIT)).isEqualTo(0x12345600);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_24BIT))
        .isWithin(0xFF)
        .of(Integer.MAX_VALUE);
  }

  @Test
  public void readAs32BitIntPcm_read24BitBigEndian_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(15);
    buffer.put(hexToBytes("800000" + "ABCDEF" + "000000" + "123456" + "7FFFFF"));
    buffer.flip();

    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_24BIT_BIG_ENDIAN))
        .isEqualTo(Integer.MIN_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_24BIT_BIG_ENDIAN))
        .isEqualTo(0xABCDEF00);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_24BIT_BIG_ENDIAN))
        .isEqualTo(0);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_24BIT_BIG_ENDIAN))
        .isEqualTo(0x12345600);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_24BIT_BIG_ENDIAN))
        .isWithin(0xFF)
        .of(Integer.MAX_VALUE);
  }

  @Test
  public void readAs32BitIntPcm_read32Bit_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(20);
    buffer.put(hexToBytes("00000080" + "12EFCDAB" + "00000000" + "78563412" + "FFFFFF7F"));
    buffer.flip();

    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_32BIT))
        .isEqualTo(Integer.MIN_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_32BIT)).isEqualTo(0xABCDEF12);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_32BIT)).isEqualTo(0);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_32BIT)).isEqualTo(0x12345678);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_32BIT))
        .isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  public void readAs32BitIntPcm_read32BitBigEndian_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(20);
    buffer.put(hexToBytes("80000000" + "ABCDEF12" + "00000000" + "12345678" + "7FFFFFFF"));
    buffer.flip();

    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_32BIT_BIG_ENDIAN))
        .isEqualTo(Integer.MIN_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_32BIT_BIG_ENDIAN))
        .isEqualTo(0xABCDEF12);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_32BIT_BIG_ENDIAN))
        .isEqualTo(0);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_32BIT_BIG_ENDIAN))
        .isEqualTo(0x12345678);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_32BIT_BIG_ENDIAN))
        .isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  public void readAs32BitIntPcm_readFloat_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(40);
    buffer.putFloat(Float.NEGATIVE_INFINITY);
    buffer.putFloat(-2f);
    buffer.putFloat(-1f);
    buffer.putFloat(-0.5f);
    buffer.putFloat(0f);
    buffer.putFloat(0.5f);
    buffer.putFloat(1f);
    buffer.putFloat(2f);
    buffer.putFloat(Float.POSITIVE_INFINITY);
    buffer.putFloat(Float.NaN);
    buffer.flip();

    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_FLOAT))
        .isEqualTo(Integer.MIN_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_FLOAT))
        .isEqualTo(Integer.MIN_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_FLOAT))
        .isEqualTo(Integer.MIN_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_FLOAT))
        .isWithin(1)
        .of(Integer.MIN_VALUE / 2);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_FLOAT)).isEqualTo(0);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_FLOAT))
        .isWithin(1)
        .of(Integer.MAX_VALUE / 2);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_FLOAT))
        .isEqualTo(Integer.MAX_VALUE);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_FLOAT))
        .isEqualTo(Integer.MAX_VALUE);
    // Just make sure we don't crash for NaN.
    assertThat(PcmAudioUtil.readAs32BitIntPcm(buffer, C.ENCODING_PCM_FLOAT))
        .isAnyOf(0, Integer.MAX_VALUE, Integer.MIN_VALUE);
  }

  @Test
  public void write32BitIntPcm_write8Bit_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(5);

    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MIN_VALUE, C.ENCODING_PCM_8BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0xABCDEF12, C.ENCODING_PCM_8BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0, C.ENCODING_PCM_8BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0x12345678, C.ENCODING_PCM_8BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MAX_VALUE, C.ENCODING_PCM_8BIT);
    buffer.flip();

    assertThat(byteBufferToHex(buffer)).isEqualTo("80" + "AB" + "00" + "12" + "7F");
  }

  @Test
  public void write32BitIntPcm_write16Bit_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(10);

    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MIN_VALUE, C.ENCODING_PCM_16BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0xABCDEF12, C.ENCODING_PCM_16BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0, C.ENCODING_PCM_16BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0x12345678, C.ENCODING_PCM_16BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MAX_VALUE, C.ENCODING_PCM_16BIT);
    buffer.flip();

    assertThat(byteBufferToHex(buffer)).isEqualTo("0080" + "CDAB" + "0000" + "3412" + "FF7F");
  }

  @Test
  public void write32BitIntPcm_write16BitBigEndian_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(10);

    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MIN_VALUE, C.ENCODING_PCM_16BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, 0xABCDEF12, C.ENCODING_PCM_16BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, 0, C.ENCODING_PCM_16BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, 0x12345678, C.ENCODING_PCM_16BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MAX_VALUE, C.ENCODING_PCM_16BIT_BIG_ENDIAN);
    buffer.flip();

    assertThat(byteBufferToHex(buffer)).isEqualTo("8000" + "ABCD" + "0000" + "1234" + "7FFF");
  }

  @Test
  public void write32BitIntPcm_write24Bit_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(15);

    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MIN_VALUE, C.ENCODING_PCM_24BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0xABCDEF12, C.ENCODING_PCM_24BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0, C.ENCODING_PCM_24BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0x12345678, C.ENCODING_PCM_24BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MAX_VALUE, C.ENCODING_PCM_24BIT);
    buffer.flip();

    assertThat(byteBufferToHex(buffer))
        .isEqualTo("000080" + "EFCDAB" + "000000" + "563412" + "FFFF7F");
  }

  @Test
  public void write32BitIntPcm_write24BitBigEndian_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(15);

    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MIN_VALUE, C.ENCODING_PCM_24BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, 0xABCDEF12, C.ENCODING_PCM_24BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, 0, C.ENCODING_PCM_24BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, 0x12345678, C.ENCODING_PCM_24BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MAX_VALUE, C.ENCODING_PCM_24BIT_BIG_ENDIAN);
    buffer.flip();

    assertThat(byteBufferToHex(buffer))
        .isEqualTo("800000" + "ABCDEF" + "000000" + "123456" + "7FFFFF");
  }

  @Test
  public void write32BitIntPcm_write32Bit_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(20);

    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MIN_VALUE, C.ENCODING_PCM_32BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0xABCDEF12, C.ENCODING_PCM_32BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0, C.ENCODING_PCM_32BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0x12345678, C.ENCODING_PCM_32BIT);
    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MAX_VALUE, C.ENCODING_PCM_32BIT);
    buffer.flip();

    assertThat(byteBufferToHex(buffer))
        .isEqualTo("00000080" + "12EFCDAB" + "00000000" + "78563412" + "FFFFFF7F");
  }

  @Test
  public void write32BitIntPcm_write32BitBigEndian_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(20);

    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MIN_VALUE, C.ENCODING_PCM_32BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, 0xABCDEF12, C.ENCODING_PCM_32BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, 0, C.ENCODING_PCM_32BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, 0x12345678, C.ENCODING_PCM_32BIT_BIG_ENDIAN);
    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MAX_VALUE, C.ENCODING_PCM_32BIT_BIG_ENDIAN);
    buffer.flip();

    assertThat(byteBufferToHex(buffer))
        .isEqualTo("80000000" + "ABCDEF12" + "00000000" + "12345678" + "7FFFFFFF");
  }

  @Test
  public void write32BitIntPcm_writeFloat_returnsExpectedValues() {
    ByteBuffer buffer = ByteBuffer.allocate(20);

    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MIN_VALUE, C.ENCODING_PCM_FLOAT);
    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MIN_VALUE / 2, C.ENCODING_PCM_FLOAT);
    PcmAudioUtil.write32BitIntPcm(buffer, 0, C.ENCODING_PCM_FLOAT);
    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MAX_VALUE / 2, C.ENCODING_PCM_FLOAT);
    PcmAudioUtil.write32BitIntPcm(buffer, Integer.MAX_VALUE, C.ENCODING_PCM_FLOAT);
    buffer.flip();

    assertThat(buffer.getFloat()).isEqualTo(-1f);
    assertThat(buffer.getFloat()).isEqualTo(-0.5f);
    assertThat(buffer.getFloat()).isEqualTo(0f);
    assertThat(buffer.getFloat()).isEqualTo(0.5f);
    assertThat(buffer.getFloat()).isEqualTo(1f);
  }

  @Test
  public void rampUpVolume_fromZeroFullRamp_returnsCorrectlyScaledValues() {
    ByteBuffer buffer = ByteBuffer.allocate(48);
    // Intentionally use large values to check for overflows.
    int a = Integer.MAX_VALUE;
    int b = Integer.MIN_VALUE;
    for (int i = 0; i < 6; i++) {
      PcmAudioUtil.write32BitIntPcm(buffer, a, C.ENCODING_PCM_32BIT);
      PcmAudioUtil.write32BitIntPcm(buffer, b, C.ENCODING_PCM_32BIT);
    }
    buffer.flip();

    ByteBuffer output =
        PcmAudioUtil.rampUpVolume(
            buffer,
            C.ENCODING_PCM_32BIT,
            /* pcmFrameSize= */ 8,
            /* startFrameIndex= */ 0,
            /* rampFrameCount= */ 4);

    ImmutableList.Builder<Integer> outputValues = ImmutableList.builder();
    while (output.hasRemaining()) {
      outputValues.add(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_32BIT));
    }
    int threeQuartersA = (int) ((long) a * 3 / 4);
    int threeQuartersB = (int) ((long) b * 3 / 4);
    assertThat(outputValues.build())
        .containsExactly(
            0, 0, a / 4, b / 4, a / 2, b / 2, threeQuartersA, threeQuartersB, a, b, a, b)
        .inOrder();
  }

  @Test
  public void rampUpVolume_fromNonZeroFullRamp_returnsCorrectlyScaledValues() {
    ByteBuffer buffer = ByteBuffer.allocate(32);
    // Intentionally use large values to check for overflows.
    int a = Integer.MAX_VALUE;
    int b = Integer.MIN_VALUE;
    for (int i = 0; i < 4; i++) {
      PcmAudioUtil.write32BitIntPcm(buffer, a, C.ENCODING_PCM_32BIT);
      PcmAudioUtil.write32BitIntPcm(buffer, b, C.ENCODING_PCM_32BIT);
    }
    buffer.flip();

    ByteBuffer output =
        PcmAudioUtil.rampUpVolume(
            buffer,
            C.ENCODING_PCM_32BIT,
            /* pcmFrameSize= */ 8,
            /* startFrameIndex= */ 2,
            /* rampFrameCount= */ 4);

    ImmutableList.Builder<Integer> outputValues = ImmutableList.builder();
    while (output.hasRemaining()) {
      outputValues.add(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_32BIT));
    }
    int threeQuartersA = (int) ((long) a * 3 / 4);
    int threeQuartersB = (int) ((long) b * 3 / 4);
    assertThat(outputValues.build())
        .containsExactly(a / 2, b / 2, threeQuartersA, threeQuartersB, a, b, a, b)
        .inOrder();
  }

  @Test
  public void rampUpVolume_fromZeroPartialRamp_returnsCorrectlyScaledValues() {
    ByteBuffer buffer = ByteBuffer.allocate(24);
    // Intentionally use large values to check for overflows.
    int a = Integer.MAX_VALUE;
    int b = Integer.MIN_VALUE;
    for (int i = 0; i < 3; i++) {
      PcmAudioUtil.write32BitIntPcm(buffer, a, C.ENCODING_PCM_32BIT);
      PcmAudioUtil.write32BitIntPcm(buffer, b, C.ENCODING_PCM_32BIT);
    }
    buffer.flip();

    ByteBuffer output =
        PcmAudioUtil.rampUpVolume(
            buffer,
            C.ENCODING_PCM_32BIT,
            /* pcmFrameSize= */ 8,
            /* startFrameIndex= */ 0,
            /* rampFrameCount= */ 4);

    ImmutableList.Builder<Integer> outputValues = ImmutableList.builder();
    while (output.hasRemaining()) {
      outputValues.add(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_32BIT));
    }
    assertThat(outputValues.build()).containsExactly(0, 0, a / 4, b / 4, a / 2, b / 2).inOrder();
  }

  @Test
  public void rampUpVolume_fromNonZeroPartialRamp_returnsCorrectlyScaledValues() {
    ByteBuffer buffer = ByteBuffer.allocate(48);
    // Intentionally use large values to check for overflows.
    int a = Integer.MAX_VALUE;
    int b = Integer.MIN_VALUE;
    for (int i = 0; i < 2; i++) {
      PcmAudioUtil.write32BitIntPcm(buffer, a, C.ENCODING_PCM_32BIT);
      PcmAudioUtil.write32BitIntPcm(buffer, b, C.ENCODING_PCM_32BIT);
    }
    buffer.flip();

    ByteBuffer output =
        PcmAudioUtil.rampUpVolume(
            buffer,
            C.ENCODING_PCM_32BIT,
            /* pcmFrameSize= */ 8,
            /* startFrameIndex= */ 1,
            /* rampFrameCount= */ 4);

    ImmutableList.Builder<Integer> outputValues = ImmutableList.builder();
    while (output.hasRemaining()) {
      outputValues.add(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_32BIT));
    }
    assertThat(outputValues.build()).containsExactly(a / 4, b / 4, a / 2, b / 2).inOrder();
  }

  @Test
  public void rampUpVolume_non32Bit_returnsCorrectlyScaledValues() {
    ByteBuffer buffer = ByteBuffer.allocate(48);
    // Intentionally use large values to check for overflows.
    int a = Integer.MAX_VALUE;
    int b = Integer.MIN_VALUE;
    for (int i = 0; i < 6; i++) {
      PcmAudioUtil.write32BitIntPcm(buffer, a, C.ENCODING_PCM_16BIT);
      PcmAudioUtil.write32BitIntPcm(buffer, b, C.ENCODING_PCM_16BIT);
    }
    buffer.flip();

    ByteBuffer output =
        PcmAudioUtil.rampUpVolume(
            buffer,
            C.ENCODING_PCM_16BIT,
            /* pcmFrameSize= */ 4,
            /* startFrameIndex= */ 0,
            /* rampFrameCount= */ 4);

    int threeQuartersA = (int) ((long) a * 3 / 4);
    int threeQuartersB = (int) ((long) b * 3 / 4);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT)).isWithin(0xFFFF).of(0);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT)).isWithin(0xFFFF).of(0);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT))
        .isWithin(0xFFFF)
        .of(a / 4);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT))
        .isWithin(0xFFFF)
        .of(b / 4);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT))
        .isWithin(0xFFFF)
        .of(a / 2);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT))
        .isWithin(0xFFFF)
        .of(b / 2);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT))
        .isWithin(0xFFFF)
        .of(threeQuartersA);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT))
        .isWithin(0xFFFF)
        .of(threeQuartersB);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT)).isWithin(0xFFFF).of(a);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT)).isWithin(0xFFFF).of(b);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT)).isWithin(0xFFFF).of(a);
    assertThat(PcmAudioUtil.readAs32BitIntPcm(output, C.ENCODING_PCM_16BIT)).isWithin(0xFFFF).of(b);
  }

  private byte[] hexToBytes(String hexString) {
    byte[] bytes = new BigInteger(hexString, 16).toByteArray();
    // Remove or add leading zeros to match the expected length.
    int expectedLength = hexString.length() / 2;
    if (bytes.length > expectedLength) {
      bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
    } else if (bytes.length < expectedLength) {
      byte[] newBytes = new byte[expectedLength];
      System.arraycopy(
          bytes,
          /* srcPos= */ 0,
          newBytes,
          /* destPos= */ expectedLength - bytes.length,
          bytes.length);
      bytes = newBytes;
    }
    return bytes;
  }

  private static String byteBufferToHex(ByteBuffer buffer) {
    StringBuilder hexString = new StringBuilder();
    while (buffer.hasRemaining()) {
      hexString.append(String.format("%02X", buffer.get()));
    }
    return hexString.toString();
  }
}
