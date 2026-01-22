/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.video;

import static androidx.media3.test.utils.TestUtil.createByteArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link Av1SampleDependencyParser} */
@RunWith(AndroidJUnit4.class)
public class Av1SampleDependencyParserTest {

  private static final byte[] sequenceHeader =
      createByteArray(
          0x0A, 0x0E, 0x00, 0x00, 0x00, 0x24, 0xC6, 0xAB, 0xDF, 0x3E, 0xFE, 0x24, 0x04, 0x04, 0x04,
          0x10);
  private static final byte[] dependedOnFrame =
      createByteArray(
          0x32, 0x32, 0x10, 0x00, 0xC8, 0xC6, 0x00, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x12, 0x03, 0xCE,
          0x0A, 0x5C, 0x9B, 0xB6, 0x7C, 0x34, 0x88, 0x82, 0x3E, 0x0D, 0x3E, 0xC2, 0x98, 0x91, 0x6A,
          0x5C, 0x80, 0x03, 0xCE, 0x0A, 0x5C, 0x9B, 0xB6, 0x7C, 0x48, 0x35, 0x54, 0xD8, 0x9D, 0x6C,
          0x37, 0xD3, 0x4C, 0x4E, 0xD4, 0x6F, 0xF4);

  private static final byte[] notDependedOnFrame =
      createByteArray(
          0x32, 0x1A, 0x30, 0xC0, 0x00, 0x1D, 0x66, 0x68, 0x46, 0xC9, 0x38, 0x00, 0x60, 0x10, 0x20,
          0x80, 0x20, 0x00, 0x00, 0x01, 0x8B, 0x7A, 0x87, 0xF9, 0xAA, 0x2D, 0x0F, 0x2C);

  private static final byte[] frameHeader = createByteArray(0x1A, 0x01, 0xC8);

  private static final byte[] temporalDelimiter = createByteArray(0x12, 0x00);

  private static final byte[] padding = createByteArray(0x7a, 0x02, 0xFF, 0xFF);

  @Test
  public void sampleLimitAfterSkippingNonReferenceFrame_sampleIsDependedOn_returnsFullSample() {
    ByteBuffer sample = ByteBuffer.allocate(128);
    sample.put(sequenceHeader);
    sample.put(dependedOnFrame);
    sample.flip();
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    int sampleLimitAfterSkippingNonReferenceFrames =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            sample, /* skipFrameHeaders= */ true);

    assertThat(sampleLimitAfterSkippingNonReferenceFrames)
        .isEqualTo(sequenceHeader.length + dependedOnFrame.length);
  }

  @Test
  public void
      sampleLimitAfterSkippingNonReferenceFrame_sampleIsNotDependedOn_returnsClippedSample() {
    ByteBuffer sample = ByteBuffer.allocate(128);
    sample.put(sequenceHeader);
    sample.put(notDependedOnFrame);
    sample.flip();
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    int sampleLimitAfterSkippingNonReferenceFrames =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            sample, /* skipFrameHeaders= */ true);

    assertThat(sampleLimitAfterSkippingNonReferenceFrames).isEqualTo(sequenceHeader.length);
  }

  @Test
  public void
      sampleLimitAfterSkippingNonReferenceFrame_queueSequenceHeaderSeparately_returnsEmptySample() {
    ByteBuffer header = ByteBuffer.wrap(sequenceHeader);
    ByteBuffer frame = ByteBuffer.wrap(notDependedOnFrame);
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    av1SampleDependencyParser.queueInputBuffer(header);
    int sampleLimitAfterSkippingNonReferenceFrames =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            frame, /* skipFrameHeaders= */ true);

    assertThat(sampleLimitAfterSkippingNonReferenceFrames).isEqualTo(0);
  }

  @Test
  public void
      sampleLimitAfterSkippingNonReferenceFrame_withTemporalDelimiterAndPadding_returnsEmptySample() {
    ByteBuffer header = ByteBuffer.wrap(sequenceHeader);
    ByteBuffer sample = ByteBuffer.allocate(128);
    sample.put(temporalDelimiter);
    sample.put(notDependedOnFrame);
    sample.put(padding);
    sample.flip();
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    av1SampleDependencyParser.queueInputBuffer(header);
    int sampleLimitAfterSkippingNonReferenceFrames =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            sample, /* skipFrameHeaders= */ true);

    assertThat(sampleLimitAfterSkippingNonReferenceFrames).isEqualTo(0);
  }

  @Test
  public void sampleLimitAfterSkippingNonReferenceFrame_withMultipleFrames_returnsClippedSample() {
    ByteBuffer header = ByteBuffer.wrap(sequenceHeader);
    ByteBuffer sample = ByteBuffer.allocate(128);
    sample.put(temporalDelimiter);
    sample.put(padding);
    sample.put(dependedOnFrame);
    sample.put(notDependedOnFrame);
    sample.put(padding);
    sample.flip();
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    av1SampleDependencyParser.queueInputBuffer(header);
    int sampleLimitAfterSkippingNonReferenceFrames =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            sample, /* skipFrameHeaders= */ true);

    assertThat(sampleLimitAfterSkippingNonReferenceFrames)
        .isEqualTo(temporalDelimiter.length + padding.length + dependedOnFrame.length);
  }

  @Test
  public void sampleLimitAfterSkippingNonReferenceFrame_withMissingHeader_returnsFullSample() {
    ByteBuffer frame = ByteBuffer.wrap(notDependedOnFrame);
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    int sampleLimitAfterSkippingNonReferenceFrames =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            frame, /* skipFrameHeaders= */ true);

    assertThat(sampleLimitAfterSkippingNonReferenceFrames).isEqualTo(notDependedOnFrame.length);
  }

  @Test
  public void
      sampleLimitAfterSkippingNonReferenceFrame_withTwoNonDependedOnFrames_returnsFullSample() {
    ByteBuffer header = ByteBuffer.wrap(sequenceHeader);
    ByteBuffer sample = ByteBuffer.allocate(128);
    sample.put(notDependedOnFrame);
    sample.put(notDependedOnFrame);
    sample.flip();
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    av1SampleDependencyParser.queueInputBuffer(header);
    int sampleLimitAfterSkippingNonReferenceFrames =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            sample, /* skipFrameHeaders= */ true);

    assertThat(sampleLimitAfterSkippingNonReferenceFrames)
        .isEqualTo(notDependedOnFrame.length + notDependedOnFrame.length);
  }

  @Test
  public void sampleLimitAfterSkippingNonReferenceFrame_withEightDelayedObus_returnsFullSample() {
    ByteBuffer sample = ByteBuffer.allocate(256);
    sample.put(sequenceHeader);
    sample.put(temporalDelimiter);
    sample.put(dependedOnFrame);
    sample.put(temporalDelimiter);
    sample.put(dependedOnFrame);
    sample.put(temporalDelimiter);
    sample.put(padding);
    sample.put(dependedOnFrame);
    sample.put(notDependedOnFrame);
    sample.flip();
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    int sampleLimitAfterSkippingNonReferenceFrames =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            sample, /* skipFrameHeaders= */ true);

    assertThat(sampleLimitAfterSkippingNonReferenceFrames).isEqualTo(sample.limit());
  }

  @Test
  public void
      sampleLimitAfterSkippingNonReferenceFrame_queueSampleHeaderAndReset_returnsFullSample() {
    ByteBuffer header = ByteBuffer.wrap(sequenceHeader);
    ByteBuffer frame = ByteBuffer.wrap(notDependedOnFrame);
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    av1SampleDependencyParser.queueInputBuffer(header);
    av1SampleDependencyParser.reset();
    int sampleLimitAfterSkippingNonReferenceFrames =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            frame, /* skipFrameHeaders= */ true);

    assertThat(sampleLimitAfterSkippingNonReferenceFrames).isEqualTo(notDependedOnFrame.length);
  }

  @Test
  public void
      sampleLimitAfterSkippingNonReferenceFrame_withSkipFrameHeadersTrue_returnsEmptySample() {
    ByteBuffer header = ByteBuffer.wrap(sequenceHeader);
    ByteBuffer sample = ByteBuffer.wrap(frameHeader);
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    av1SampleDependencyParser.queueInputBuffer(header);
    int sampleLimitAfterSkippingNonReferenceFrame =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            sample, /* skipFrameHeaders= */ true);

    assertThat(sampleLimitAfterSkippingNonReferenceFrame).isEqualTo(0);
  }

  @Test
  public void
      sampleLimitAfterSkippingNonReferenceFrame_withSkipFrameHeadersFalse_returnsFullSample() {
    ByteBuffer header = ByteBuffer.wrap(sequenceHeader);
    ByteBuffer sample = ByteBuffer.wrap(frameHeader);
    Av1SampleDependencyParser av1SampleDependencyParser = new Av1SampleDependencyParser();

    av1SampleDependencyParser.queueInputBuffer(header);
    int sampleLimitAfterSkippingNonReferenceFrame =
        av1SampleDependencyParser.sampleLimitAfterSkippingNonReferenceFrame(
            sample, /* skipFrameHeaders= */ false);

    assertThat(sampleLimitAfterSkippingNonReferenceFrame).isEqualTo(frameHeader.length);
  }
}
