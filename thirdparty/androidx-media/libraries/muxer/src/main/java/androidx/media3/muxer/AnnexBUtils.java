/*
 * Copyright 2022 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkState;

import androidx.media3.common.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** NAL unit utilities for start codes and emulation prevention. */
/* package */ final class AnnexBUtils {
  private static final int THREE_BYTE_NAL_START_CODE_SIZE = 3;

  private AnnexBUtils() {}

  /**
   * Splits a {@link ByteBuffer} into individual NAL units (0x000001 or 0x00000001 start code).
   *
   * <p>An {@link IllegalStateException} is thrown if the NAL units are invalid. The NAL units are
   * identified as per ITU-T H264 spec:Annex B.2.
   *
   * <p>The input buffer must have position set to 0 and the position remains unchanged after
   * calling this method.
   */
  public static ImmutableList<ByteBuffer> findNalUnits(ByteBuffer input) {
    if (input.remaining() == 0) {
      return ImmutableList.of();
    }
    input = input.asReadOnlyBuffer();
    input.order(ByteOrder.BIG_ENDIAN);

    // The algorithm always searches for 0x000001 start code but it will work for 0x00000001 start
    // code as well because the first 0 will be considered as a leading 0 and will be skipped.

    int nalStartCodeIndex = skipLeadingZerosAndFindNalStartCodeIndex(input, input.position());

    int nalStartIndex = nalStartCodeIndex + THREE_BYTE_NAL_START_CODE_SIZE;
    boolean readingNalUnit = true;

    ImmutableList.Builder<ByteBuffer> nalUnits = new ImmutableList.Builder<>();
    int i = nalStartIndex;
    while (i < input.limit()) {
      if (readingNalUnit) {
        int nalEndIndex = findNalEndIndex(input, i);
        nalUnits.add(getBytes(input, nalStartIndex, nalEndIndex - nalStartIndex));
        i = nalEndIndex;
        readingNalUnit = false;
      } else {
        int nextNalStartCodeIndex = skipLeadingZerosAndFindNalStartCodeIndex(input, i);
        if (nextNalStartCodeIndex != input.limit()) {
          nalStartIndex = nextNalStartCodeIndex + THREE_BYTE_NAL_START_CODE_SIZE;
          i = nalStartIndex;
          readingNalUnit = true;
        } else {
          break;
        }
      }
    }

    return nalUnits.build();
  }

  /** Removes Annex-B emulation prevention bytes from a buffer. */
  public static ByteBuffer stripEmulationPrevention(ByteBuffer input) {
    // For simplicity, we allocate the same number of bytes (although the eventual number might be
    // smaller).
    ByteBuffer output = ByteBuffer.allocate(input.limit());
    int zerosSeen = 0;
    for (int i = 0; i < input.limit(); i++) {
      boolean lookingAtEmulationPreventionByte = input.get(i) == 0x03 && zerosSeen >= 2;

      // Only copy bytes if they aren't emulation prevention bytes.
      if (!lookingAtEmulationPreventionByte) {
        output.put(input.get(i));
      }

      if (input.get(i) == 0) {
        zerosSeen++;
      } else {
        zerosSeen = 0;
      }
    }

    output.flip();

    return output;
  }

  /**
   * Returns whether the sample of the given MIME type will contain NAL units in Annex-B format
   * (ISO/IEC 14496-10 Annex B, which uses start codes to delineate NAL units).
   */
  public static boolean doesSampleContainAnnexBNalUnits(String sampleMimeType) {
    return sampleMimeType.equals(MimeTypes.VIDEO_H264)
        || sampleMimeType.equals(MimeTypes.VIDEO_H265);
  }

  /**
   * Returns the end position (exclusive) of the current NAL unit within the input.
   *
   * <p>A NAL unit is terminated by one of the following sequences:
   *
   * <ul>
   *   <li>0x000000
   *   <li>0x000001
   *   <li>The end of the input data.
   * </ul>
   *
   * @param input The {@link ByteBuffer} containing NAL units.
   * @param currentIndex The starting position for the search.
   * @return The NAL unit end index (exclusive).
   */
  private static int findNalEndIndex(ByteBuffer input, int currentIndex) {
    while (currentIndex <= input.limit() - 4) {
      int fourBytes = input.getInt(currentIndex);
      // Check if the first 3 bytes are 0x000000 or 0x000001.
      if ((fourBytes & 0xFFFFFF00) == 0 || (fourBytes & 0xFFFFFF00) == 0x00000100) {
        return currentIndex;
      }

      // Check if the last 3 bytes are 0x000000 or 0x000001.
      if ((fourBytes & 0x00FFFFFF) == 0 || (fourBytes & 0x00FFFFFF) == 0x00000001) {
        return currentIndex + 1;
      }

      // Check if the last 2 bytes are prefix of 0x000000 or 0x000001.
      if ((fourBytes & 0x0000FFFF) == 0) {
        currentIndex = currentIndex + 2;
      } else if ((fourBytes & 0x000000FF)
          == 0) { // Check if the last byte is prefix of 0x000000 or 0x000001.
        currentIndex = currentIndex + 3;
      } else {
        currentIndex = currentIndex + 4;
      }
    }

    // Handle remaining bytes if any (less than 4).
    // Last 3 bytes could be 0x000000 or 0x000001.
    if (currentIndex == input.limit() - THREE_BYTE_NAL_START_CODE_SIZE) {
      short firstTwoBytes = input.getShort(currentIndex);
      byte lastByte = input.get(currentIndex + 2);
      if (firstTwoBytes == 0 && (lastByte == 0 || lastByte == 1)) {
        return currentIndex;
      }
    }
    return input.limit();
  }

  /**
   * Skips leading zeros and locates the start of the next NAL unit (0x000001).
   *
   * @param input The {@link ByteBuffer} containing NAL units.
   * @param currentIndex The starting position for the search.
   * @return The index of the NAL start code, or the end of the input if NAL start code is not
   *     found.
   */
  private static int skipLeadingZerosAndFindNalStartCodeIndex(ByteBuffer input, int currentIndex) {
    while (currentIndex <= input.limit() - 4) {
      int fourBytes = input.getInt(currentIndex);

      // Check if the first 3 bytes is 0x000001.
      if ((fourBytes & 0xFFFFFF00) == 0x00000100) {
        return currentIndex;
      }

      // Otherwise the first 3 bytes must be 0.
      checkState((fourBytes & 0xFFFFFF00) == 0, "Invalid Nal units");

      // Check if the last byte is 1. It then makes last three bytes 0x000001.
      if ((fourBytes & 0x000000FF) == 1) {
        return currentIndex + 1;
      }

      // Otherwise the last byte must be 0;
      checkState((fourBytes & 0x000000FF) == 0, "Invalid Nal units");

      // Last three zeroes can be a prefix of the NAL start code 0x000001.
      currentIndex = currentIndex + 1;
    }

    // Handle remaining bytes if any (less than 4).
    // Last 3 bytes could be 0x000001.
    if (currentIndex <= input.limit() - THREE_BYTE_NAL_START_CODE_SIZE) {
      short firstTwoBytes = input.getShort(currentIndex);
      checkState(firstTwoBytes == 0, "Invalid NAL units");
      byte lastByte = input.get(currentIndex + 2);
      if (lastByte == 1) {
        return currentIndex;
      }
      checkState(lastByte == 0, "Invalid NAL units");
    } else {
      // Remaining bytes must be 0.
      while (currentIndex < input.limit()) {
        checkState(input.get(currentIndex) == 0, "Invalid NAL units");
        currentIndex++;
      }
    }
    return input.limit();
  }

  private static ByteBuffer getBytes(ByteBuffer buf, int offset, int length) {
    ByteBuffer result = buf.duplicate();
    result.position(offset);
    result.limit(offset + length);
    return result.slice();
  }
}
