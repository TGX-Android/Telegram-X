/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.extractor;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;

/**
 * An overridable {@link TrackOutput} implementation forwarding all methods to another track output.
 */
@UnstableApi
public class ForwardingTrackOutput implements TrackOutput {

  private final TrackOutput trackOutput;

  /** Creates a new instance that forwards all operations to {@code trackOutput}. */
  public ForwardingTrackOutput(TrackOutput trackOutput) {
    this.trackOutput = trackOutput;
  }

  @Override
  public void durationUs(long durationUs) {
    trackOutput.durationUs(durationUs);
  }

  @Override
  public void format(Format format) {
    trackOutput.format(format);
  }

  @Override
  public int sampleData(DataReader input, int length, boolean allowEndOfInput) throws IOException {
    return trackOutput.sampleData(input, length, allowEndOfInput);
  }

  @Override
  public void sampleData(ParsableByteArray data, int length) {
    trackOutput.sampleData(data, length);
  }

  @Override
  public int sampleData(
      DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
      throws IOException {
    return trackOutput.sampleData(input, length, allowEndOfInput, sampleDataPart);
  }

  @Override
  public void sampleData(ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
    trackOutput.sampleData(data, length, sampleDataPart);
  }

  @Override
  public void sampleMetadata(
      long timeUs,
      @C.BufferFlags int flags,
      int size,
      int offset,
      @Nullable CryptoData cryptoData) {
    trackOutput.sampleMetadata(timeUs, flags, size, offset, cryptoData);
  }
}
