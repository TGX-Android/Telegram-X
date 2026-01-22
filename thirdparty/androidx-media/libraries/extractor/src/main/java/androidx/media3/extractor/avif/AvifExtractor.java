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
package androidx.media3.extractor.avif;

import androidx.media3.common.C;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SingleSampleExtractor;
import java.io.IOException;

/** Extracts data from the AVIF (.avif) container format. */
@UnstableApi
public final class AvifExtractor implements Extractor {

  // Specification reference: ISO/IEC 23008-12:2022
  private static final int AVIF_FILE_SIGNATURE_PART_1 = 0x66747970;
  private static final int AVIF_FILE_SIGNATURE_PART_2 = 0x61766966;
  private static final int FILE_SIGNATURE_SEGMENT_LENGTH = 4;

  private final ParsableByteArray scratch;
  private final SingleSampleExtractor imageExtractor;

  /** Creates an instance. */
  public AvifExtractor() {
    scratch = new ParsableByteArray(FILE_SIGNATURE_SEGMENT_LENGTH);
    imageExtractor =
        new SingleSampleExtractor(
            /* fileSignature= */ C.INDEX_UNSET,
            /* fileSignatureLength= */ C.LENGTH_UNSET,
            MimeTypes.IMAGE_AVIF);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    input.advancePeekPosition(4);
    return readAndCompareFourBytes(input, AVIF_FILE_SIGNATURE_PART_1)
        && readAndCompareFourBytes(input, AVIF_FILE_SIGNATURE_PART_2);
  }

  @Override
  public void init(ExtractorOutput output) {
    imageExtractor.init(output);
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    return imageExtractor.read(input, seekPosition);
  }

  @Override
  public void seek(long position, long timeUs) {
    imageExtractor.seek(position, timeUs);
  }

  @Override
  public void release() {
    // Do nothing.
  }

  private boolean readAndCompareFourBytes(ExtractorInput input, int bytesToCompare)
      throws IOException {
    scratch.reset(/* limit= */ FILE_SIGNATURE_SEGMENT_LENGTH);
    input.peekFully(scratch.getData(), /* offset= */ 0, FILE_SIGNATURE_SEGMENT_LENGTH);
    return scratch.readUnsignedInt() == bytesToCompare;
  }
}
