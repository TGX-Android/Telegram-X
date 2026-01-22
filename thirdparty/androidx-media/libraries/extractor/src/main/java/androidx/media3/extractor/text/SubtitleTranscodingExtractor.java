/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.media3.extractor.text;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * @deprecated Use {@link SubtitleTranscodingExtractorOutput} directly from within an existing
 *     {@link Extractor} implementation instead.
 */
@Deprecated
@UnstableApi
public class SubtitleTranscodingExtractor implements Extractor {

  private final Extractor delegate;
  private final SubtitleParser.Factory subtitleParserFactory;

  private @MonotonicNonNull SubtitleTranscodingExtractorOutput transcodingExtractorOutput;

  public SubtitleTranscodingExtractor(
      Extractor delegate, SubtitleParser.Factory subtitleParserFactory) {
    this.delegate = delegate;
    this.subtitleParserFactory = subtitleParserFactory;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return delegate.sniff(input);
  }

  @Override
  public void init(ExtractorOutput output) {
    transcodingExtractorOutput =
        new SubtitleTranscodingExtractorOutput(output, subtitleParserFactory);
    delegate.init(transcodingExtractorOutput);
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    return delegate.read(input, seekPosition);
  }

  @Override
  public void seek(long position, long timeUs) {
    if (transcodingExtractorOutput != null) {
      transcodingExtractorOutput.resetSubtitleParsers();
    }
    delegate.seek(position, timeUs);
  }

  @Override
  public void release() {
    delegate.release();
  }

  @Override
  public Extractor getUnderlyingImplementation() {
    return delegate;
  }
}
