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

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.lang.Math.min;

import androidx.annotation.Nullable;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.UnstableApi;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for parsing AV1 Open Bitstream Units or OBUs.
 *
 * <p><a href=https://aomediacodec.github.io/av1-spec/>AV1 Bitstream and Decoding Process
 * Specification</a>
 */
@UnstableApi
public final class ObuParser {

  /** OBU type sequence header. */
  public static final int OBU_SEQUENCE_HEADER = 1;

  /** OBU type temporal delimiter. */
  public static final int OBU_TEMPORAL_DELIMITER = 2;

  /** OBU type frame header. */
  public static final int OBU_FRAME_HEADER = 3;

  /** OBU type frame. */
  public static final int OBU_FRAME = 6;

  /** OBU type padding. */
  public static final int OBU_PADDING = 15;

  /** Open bitstream unit. */
  public static final class Obu {

    /** The OBU type. See {@code obu_type} in the AV1 spec. */
    public final int type;

    /** The OBU data, excluding the header. */
    public final ByteBuffer payload;

    private Obu(int type, ByteBuffer payload) {
      this.type = type;
      this.payload = payload;
    }
  }

  /**
   * Splits the input sample into a list of OBUs.
   *
   * <p>Expects the AV1 sample format specified by the <a
   * href=https://aomediacodec.github.io/av1-isobmff/#sampleformat>AV1 Codec ISO Media File Format
   * Binding</a>. That is, each OBU has the {@code obu_has_size_field} set to 1 except for the last
   * OBU in the sample, for which {@code obu_has_size_field} may be set to 0.
   *
   * @param sample The sample data.
   * @return The list of OBUs contained within the sample data.
   */
  public static List<Obu> split(ByteBuffer sample) {
    // Create a read-only buffer that shares content with the sample to avoid modifying the
    // input buffer's position, mark or limit.
    ByteBuffer readOnlySample = sample.asReadOnlyBuffer();
    List<Obu> obuList = new ArrayList<>();
    while (readOnlySample.hasRemaining()) {
      int headerByte = readOnlySample.get();
      int obuType = (headerByte >> 3) & 0xF;
      int extensionFlag = (headerByte >> 2) & 0x1;
      if (extensionFlag != 0) {
        readOnlySample.get(); // skip obu_extension_header()
      }
      int obuHasSizeField = (headerByte >> 1) & 0x1;
      int obuSize;
      if (obuHasSizeField != 0) {
        obuSize = leb128(readOnlySample);
      } else {
        // Only the last sample is allowed to have obu_has_size_field == 0, and the size is assumed
        // to fill the remainder of the sample.
        obuSize = readOnlySample.remaining();
      }
      ByteBuffer payload = readOnlySample.duplicate();
      payload.limit(readOnlySample.position() + obuSize);
      obuList.add(new Obu(obuType, payload));
      readOnlySample.position(readOnlySample.position() + obuSize);
    }
    return obuList;
  }

  private static int leb128(ByteBuffer data) {
    int value = 0;
    for (int i = 0; i < 8; i++) {
      int leb128Byte = data.get();
      value |= ((leb128Byte & 0x7F) << (i * 7));
      if ((leb128Byte & 0x80) == 0) {
        break;
      }
    }
    return value;
  }

  /** An AV1 Sequence header. */
  public static final class SequenceHeader {
    /** See {@code reduced_still_picture_header}. */
    public final boolean reducedStillPictureHeader;

    /** See {@code decoder_model_info_present_flag}. */
    public final boolean decoderModelInfoPresentFlag;

    /** See {@code frame_id_numbers_present_flag}. */
    public final boolean frameIdNumbersPresentFlag;

    /** See {@code seq_force_screen_content_tools}. */
    public final boolean seqForceScreenContentTools;

    /** See {@code seq_force_integer_mv}. */
    public final boolean seqForceIntegerMv;

    /** See {@code OrderHintBits}. */
    public final int orderHintBits;

    /**
     * Returns a {@link SequenceHeader} parsed from the input OBU, or {@code null} if the AV1
     * bitstream is not yet supported.
     *
     * @param obu The input OBU with type {@link #OBU_SEQUENCE_HEADER}.
     */
    @Nullable
    public static SequenceHeader parse(Obu obu) {
      try {
        return new SequenceHeader(obu);
      } catch (NotYetImplementedException ignored) {
        return null;
      }
    }

    /** Parses a {@link #OBU_SEQUENCE_HEADER} and creates an instance. */
    private SequenceHeader(Obu obu) throws NotYetImplementedException {
      checkArgument(obu.type == OBU_SEQUENCE_HEADER);
      byte[] data = new byte[obu.payload.remaining()];
      // Do not modify obu.payload while reading it.
      obu.payload.asReadOnlyBuffer().get(data);
      ParsableBitArray obuData = new ParsableBitArray(data);
      obuData.skipBits(4); // seq_profile and still_picture
      reducedStillPictureHeader = obuData.readBit();
      throwWhenFeatureRequired(reducedStillPictureHeader);
      boolean timingInfoPresentFlag = obuData.readBit();
      if (timingInfoPresentFlag) {
        skipTimingInfo(obuData);
        decoderModelInfoPresentFlag = obuData.readBit();
        if (decoderModelInfoPresentFlag) {
          // skip decoder_model_info()
          obuData.skipBits(47);
        }
      } else {
        decoderModelInfoPresentFlag = false;
      }
      boolean initialDisplayDelayPresentFlag = obuData.readBit();
      int operatingPointsCntMinus1 = obuData.readBits(5);
      for (int i = 0; i <= operatingPointsCntMinus1; i++) {
        obuData.skipBits(12); // operating_point_idc[ i ]
        int seqLevelIdx = obuData.readBits(5);
        if (seqLevelIdx > 7) {
          obuData.skipBit(); // seq_tier[ i ]
        }
        throwWhenFeatureRequired(decoderModelInfoPresentFlag);
        if (initialDisplayDelayPresentFlag) {
          boolean initialDisplayDelayPresentForThisOpFlag = obuData.readBit();
          if (initialDisplayDelayPresentForThisOpFlag) {
            obuData.skipBits(4); // initial_display_delay_minus_1[ i ]
          }
        }
      }
      int frameWidthBitsMinus1 = obuData.readBits(4);
      int frameHeightBitsMinus1 = obuData.readBits(4);
      obuData.skipBits(frameWidthBitsMinus1 + 1); // max_frame_width_minus_1
      obuData.skipBits(frameHeightBitsMinus1 + 1); // max_frame_height_minus_1
      frameIdNumbersPresentFlag = obuData.readBit();
      throwWhenFeatureRequired(frameIdNumbersPresentFlag);
      // use_128x128_superblock, enable_filter_intra, and enable_intra_edge_filter
      obuData.skipBits(3);
      // enable_interintra_compound, enable_masked_compound, enable_warped_motion, and
      // enable_dual_filter
      obuData.skipBits(4);
      boolean enableOrderHint = obuData.readBit();
      if (enableOrderHint) {
        obuData.skipBits(2); // enable_jnt_comp and enable_ref_frame_mvs
      }
      boolean seqChooseScreenContentTools = obuData.readBit();
      if (seqChooseScreenContentTools) {
        seqForceScreenContentTools = true;
      } else {
        seqForceScreenContentTools = obuData.readBit();
      }
      if (seqForceScreenContentTools) {
        boolean seqChooseIntegerMv = obuData.readBit();
        if (seqChooseIntegerMv) {
          seqForceIntegerMv = true;
        } else {
          seqForceIntegerMv = obuData.readBit();
        }
      } else {
        seqForceIntegerMv = true;
      }
      if (enableOrderHint) {
        int orderHintBitsMinus1 = obuData.readBits(3);
        orderHintBits = orderHintBitsMinus1 + 1;
      } else {
        orderHintBits = 0;
      }
    }

    /** Advances the bit array by skipping the {@code timing_info()} syntax element. */
    private static void skipTimingInfo(ParsableBitArray parsableBitArray) {
      parsableBitArray.skipBits(64); // num_units_in_display_tick and time_scale
      boolean equalPictureInterval = parsableBitArray.readBit();
      if (equalPictureInterval) {
        skipUvlc(parsableBitArray);
      }
    }
  }

  /** Advances the bit array by skipping the {@code uvlc()} process. */
  private static void skipUvlc(ParsableBitArray parsableBitArray) {
    int leadingZeros = 0;
    while (true) {
      boolean done = parsableBitArray.readBit();
      if (done) {
        break;
      }
      leadingZeros++;
    }
    // 32 or more leading zeros returns (1 << 32) - 1 from the uvlc() process without reading more
    // bits.
    if (leadingZeros < 32) {
      parsableBitArray.skipBits(leadingZeros);
    }
  }

  /** An AV1 Frame Header. */
  public static final class FrameHeader {
    private static final int PROBE_BYTES = 4;

    private static final int FRAME_TYPE_KEY_FRAME = 0;
    private static final int FRAME_TYPE_INTRA_ONLY_FRAME = 2;
    private static final int FRAME_TYPE_SWITCH_FRAME = 3;

    private final boolean isDependedOn;

    /** Returns whether the frame header is depended on by subsequent frames. */
    public boolean isDependedOn() {
      return isDependedOn;
    }

    /**
     * Returns a {@link FrameHeader} parsed from the input OBU, or {@code null} if the AV1 bitstream
     * is not yet supported.
     *
     * @param sequenceHeader The most recent sequence header before the frame header.
     * @param obu The input OBU with type {@link #OBU_FRAME} or {@link #OBU_FRAME_HEADER}.
     */
    @Nullable
    public static FrameHeader parse(SequenceHeader sequenceHeader, Obu obu) {
      try {
        return new FrameHeader(sequenceHeader, obu);
      } catch (NotYetImplementedException ignored) {
        return null;
      }
    }

    private FrameHeader(SequenceHeader sequenceHeader, Obu obu) throws NotYetImplementedException {
      checkArgument(obu.type == OBU_FRAME || obu.type == OBU_FRAME_HEADER);
      byte[] bytes = new byte[min(PROBE_BYTES, obu.payload.remaining())];
      // Do not modify obu.payload while reading it.
      obu.payload.asReadOnlyBuffer().get(bytes);
      ParsableBitArray obuData = new ParsableBitArray(bytes);
      throwWhenFeatureRequired(sequenceHeader.reducedStillPictureHeader);
      boolean showExistingFrame = obuData.readBit();
      if (showExistingFrame) {
        isDependedOn = false;
        return;
      }
      int frameType = obuData.readBits(2);
      boolean showFrame = obuData.readBit();
      throwWhenFeatureRequired(sequenceHeader.decoderModelInfoPresentFlag);
      if (!showFrame) {
        // show_frame equal to 0 specifies that this frame should not be immediately output.
        // If a frame is output later, then it is depended on.
        isDependedOn = true;
        return;
      }
      boolean errorResilientMode;
      if (frameType == FRAME_TYPE_SWITCH_FRAME || (frameType == FRAME_TYPE_KEY_FRAME)) {
        errorResilientMode = true;
      } else {
        errorResilientMode = obuData.readBit();
      }
      obuData.skipBit(); // disable_cdf_update
      throwWhenFeatureRequired(!sequenceHeader.seqForceScreenContentTools);
      boolean allowScreenContentTools = obuData.readBit();
      if (allowScreenContentTools) {
        throwWhenFeatureRequired(!sequenceHeader.seqForceIntegerMv);
        obuData.skipBit(); // force_integer_mv
      }
      throwWhenFeatureRequired(sequenceHeader.frameIdNumbersPresentFlag);
      if (frameType != FRAME_TYPE_SWITCH_FRAME) {
        obuData.skipBit(); // frame_size_override_flag
      }
      obuData.skipBits(sequenceHeader.orderHintBits); // order_hint
      if (frameType != FRAME_TYPE_INTRA_ONLY_FRAME
          && frameType != FRAME_TYPE_KEY_FRAME
          && !errorResilientMode) {
        obuData.skipBits(3); // primary_ref_frame
      }
      int refreshFrameFlags;
      if (frameType == FRAME_TYPE_SWITCH_FRAME || (frameType == FRAME_TYPE_KEY_FRAME)) {
        refreshFrameFlags = (1 << 8) - 1;
      } else {
        refreshFrameFlags = obuData.readBits(8);
      }
      isDependedOn = refreshFrameFlags != 0;
    }
  }

  /** Full AV1 bitstream parsing is not yet implemented. */
  private static void throwWhenFeatureRequired(boolean expression)
      throws NotYetImplementedException {
    if (expression) {
      throw new NotYetImplementedException();
    }
  }

  private static class NotYetImplementedException extends Exception {}

  private ObuParser() {
    // Prevent instantiation.
  }
}
