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
package androidx.media3.extractor;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;

/**
 * @deprecated Use {@link DiscardingTrackOutput} instead.
 */
@UnstableApi
@Deprecated
public final class DummyTrackOutput implements TrackOutput {

  private final DiscardingTrackOutput discardingTrackOutput;

  public DummyTrackOutput() {
    discardingTrackOutput = new DiscardingTrackOutput();
  }

  @Override
  public void format(Format format) {
    discardingTrackOutput.format(format);
  }

  @Override
  public int sampleData(DataReader input, int length, boolean allowEndOfInput) throws IOException {
    return discardingTrackOutput.sampleData(input, length, allowEndOfInput);
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    discardingTrackOutput.sampleData(data, length);
  }

  @Override
  public int sampleData(
      DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
      throws IOException {
    return discardingTrackOutput.sampleData(input, length, allowEndOfInput, sampleDataPart);
  }

  @Override
  public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
    discardingTrackOutput.sampleData(data, length, sampleDataPart);
  }

  @Override
  public void sampleMetadata(
      long timeUs,
      @C.BufferFlags int flags,
      int size,
      int offset,
      @Nullable CryptoData cryptoData) {
    discardingTrackOutput.sampleMetadata(timeUs, flags, size, offset, cryptoData);
  }
}
