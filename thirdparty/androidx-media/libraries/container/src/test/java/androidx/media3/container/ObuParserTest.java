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
package androidx.media3.container;

import static androidx.media3.container.ObuParser.OBU_FRAME;
import static androidx.media3.container.ObuParser.OBU_FRAME_HEADER;
import static androidx.media3.container.ObuParser.OBU_PADDING;
import static androidx.media3.container.ObuParser.OBU_SEQUENCE_HEADER;
import static androidx.media3.container.ObuParser.OBU_TEMPORAL_DELIMITER;
import static androidx.media3.test.utils.TestUtil.createByteArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ObuParser} */
@RunWith(AndroidJUnit4.class)
public class ObuParserTest {
  private static final ByteBuffer SEQUENCE_HEADER_AND_FRAME =
      ByteBuffer.wrap(
          createByteArray(
              0x0A, 0x0E, 0x00, 0x00, 0x00, 0x24, 0xC6, 0xAB, 0xDF, 0x3E, 0xFE, 0x24, 0x04, 0x04,
              0x04, 0x10, 0x32, 0x32, 0x10, 0x00, 0xC8, 0xC6, 0x00, 0x00, 0x0C, 0x00, 0x00, 0x00,
              0x12, 0x03, 0xCE, 0x0A, 0x5C, 0x9B, 0xB6, 0x7C, 0x34, 0x88, 0x82, 0x3E, 0x0D, 0x3E,
              0xC2, 0x98, 0x91, 0x6A, 0x5C, 0x80, 0x03, 0xCE, 0x0A, 0x5C, 0x9B, 0xB6, 0x7C, 0x48,
              0x35, 0x54, 0xD8, 0x9D, 0x6C, 0x37, 0xD3, 0x4C, 0x4E, 0xD4, 0x6F, 0xF4));

  private static final ByteBuffer DELIMITER_AND_HEADER_AND_PADDING_WITH_EXTENSION_AND_MISSING_SIZE =
      ByteBuffer.wrap(createByteArray(0x16, 0x00, 0x00, 0x1A, 0x01, 0xC8, 0x78, 0xFF, 0xFF, 0xFF));

  private static final ByteBuffer NON_REFERENCE_FRAME =
      ByteBuffer.wrap(
          createByteArray(
              0x32, 0x1A, 0x30, 0xC0, 0x00, 0x1D, 0x66, 0x68, 0x46, 0xC9, 0x38, 0x00, 0x60, 0x10,
              0x20, 0x80, 0x20, 0x00, 0x00, 0x01, 0x8B, 0x7A, 0x87, 0xF9, 0xAA, 0x2D, 0x0F, 0x2C));

  @Test
  public void split_sequenceHeaderAndFrame_parsesCorrectTypesAndSizes() {
    List<ObuParser.Obu> obuList = ObuParser.split(SEQUENCE_HEADER_AND_FRAME);

    assertThat(obuList).hasSize(2);
    assertThat(obuList.get(0).type).isEqualTo(OBU_SEQUENCE_HEADER);
    assertThat(obuList.get(0).payload.remaining()).isEqualTo(14);
    assertThat(obuList.get(1).type).isEqualTo(OBU_FRAME);
    assertThat(obuList.get(1).payload.remaining()).isEqualTo(50);
  }

  @Test
  public void split_delimiterAndHeaderAndPadding_parsesCorrectTypesAndSizes() {
    List<ObuParser.Obu> obuList =
        ObuParser.split(DELIMITER_AND_HEADER_AND_PADDING_WITH_EXTENSION_AND_MISSING_SIZE);

    assertThat(obuList).hasSize(3);
    assertThat(obuList.get(0).type).isEqualTo(OBU_TEMPORAL_DELIMITER);
    assertThat(obuList.get(0).payload.remaining()).isEqualTo(0);
    assertThat(obuList.get(1).type).isEqualTo(OBU_FRAME_HEADER);
    assertThat(obuList.get(1).payload.remaining()).isEqualTo(1);
    assertThat(obuList.get(2).type).isEqualTo(OBU_PADDING);
    assertThat(obuList.get(2).payload.remaining()).isEqualTo(3);
  }

  @Test
  public void sequenceHeader_parses() {
    ObuParser.Obu sequenceHeaderObu = ObuParser.split(SEQUENCE_HEADER_AND_FRAME).get(0);

    ObuParser.SequenceHeader sequenceHeader = ObuParser.SequenceHeader.parse(sequenceHeaderObu);

    assertThat(sequenceHeader.reducedStillPictureHeader).isFalse();
    assertThat(sequenceHeader.decoderModelInfoPresentFlag).isFalse();
    assertThat(sequenceHeader.frameIdNumbersPresentFlag).isFalse();
    assertThat(sequenceHeader.seqForceScreenContentTools).isTrue();
    assertThat(sequenceHeader.seqForceIntegerMv).isTrue();
    assertThat(sequenceHeader.orderHintBits).isEqualTo(7);
  }

  @Test
  public void parseFrameHeader_fromFrame_returnsIsDependedOn() {
    List<ObuParser.Obu> obuList = ObuParser.split(SEQUENCE_HEADER_AND_FRAME);
    ObuParser.Obu sequenceHeaderObu = obuList.get(0);
    ObuParser.SequenceHeader sequenceHeader = ObuParser.SequenceHeader.parse(sequenceHeaderObu);
    ObuParser.Obu frameObu = obuList.get(1);

    ObuParser.FrameHeader frameHeader = ObuParser.FrameHeader.parse(sequenceHeader, frameObu);

    assertThat(frameHeader.isDependedOn()).isTrue();
  }

  @Test
  public void parseFrameHeader_fromShowExistingFrameHeader_returnsIsNotDependedOn() {
    ObuParser.Obu sequenceHeaderObu = ObuParser.split(SEQUENCE_HEADER_AND_FRAME).get(0);
    ObuParser.SequenceHeader sequenceHeader = ObuParser.SequenceHeader.parse(sequenceHeaderObu);
    ObuParser.Obu frameHeaderObu =
        ObuParser.split(DELIMITER_AND_HEADER_AND_PADDING_WITH_EXTENSION_AND_MISSING_SIZE).get(1);

    ObuParser.FrameHeader frameHeader = ObuParser.FrameHeader.parse(sequenceHeader, frameHeaderObu);

    assertThat(frameHeader.isDependedOn()).isFalse();
  }

  @Test
  public void parseFrameHeader_fromNonReferenceFrame_returnsNotDependedOn() {
    ObuParser.Obu sequenceHeaderObu = ObuParser.split(SEQUENCE_HEADER_AND_FRAME).get(0);
    ObuParser.SequenceHeader sequenceHeader = ObuParser.SequenceHeader.parse(sequenceHeaderObu);
    ObuParser.Obu frameObu = ObuParser.split(NON_REFERENCE_FRAME).get(0);

    ObuParser.FrameHeader frameHeader = ObuParser.FrameHeader.parse(sequenceHeader, frameObu);

    assertThat(frameHeader.isDependedOn()).isFalse();
  }
}
