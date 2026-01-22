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
package androidx.media3.test.utils;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser;
import com.google.common.base.Supplier;

/**
 * A {@link SubtitleParser.Factory} for {@link SubtitleParser} instances that throw an exception on
 * every call to {@link SubtitleParser#parse}.
 *
 * <p>It claims support for all subtitle formats (returns the result of {@link MimeTypes#isText}
 * from {@link SubtitleParser.Factory#supportsFormat}).
 */
@UnstableApi
public class ThrowingSubtitleParserFactory implements SubtitleParser.Factory {

  public static final @Format.CueReplacementBehavior int REPLACEMENT_BEHAVIOR =
      Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE;
  private final Supplier<RuntimeException> exceptionSupplier;

  public ThrowingSubtitleParserFactory(Supplier<RuntimeException> exceptionSupplier) {
    this.exceptionSupplier = exceptionSupplier;
  }

  @Override
  public boolean supportsFormat(Format format) {
    return MimeTypes.isText(format.sampleMimeType);
  }

  @Override
  public @Format.CueReplacementBehavior int getCueReplacementBehavior(Format format) {
    return REPLACEMENT_BEHAVIOR;
  }

  @Override
  public SubtitleParser create(Format format) {
    return new SubtitleParser() {
      @Override
      public void parse(
          byte[] data,
          int offset,
          int length,
          OutputOptions outputOptions,
          Consumer<CuesWithTiming> output) {
        throw exceptionSupplier.get();
      }

      @Override
      public @Format.CueReplacementBehavior int getCueReplacementBehavior() {
        return REPLACEMENT_BEHAVIOR;
      }
    };
  }
}
