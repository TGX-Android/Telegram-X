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
package androidx.media3.extractor;

import androidx.media3.common.util.UnstableApi;
import java.io.IOException;
import java.util.List;

/**
 * An overridable {@link Extractor} implementation which forwards all methods to another {@link
 * Extractor}.
 */
@UnstableApi
public class ForwardingExtractor implements Extractor {
  private final Extractor delegate;

  public ForwardingExtractor(Extractor delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    return delegate.sniff(input);
  }

  @Override
  public List<SniffFailure> getSniffFailureDetails() {
    return delegate.getSniffFailureDetails();
  }

  @Override
  public void init(ExtractorOutput output) {
    delegate.init(output);
  }

  @Override
  public @ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    return delegate.read(input, seekPosition);
  }

  @Override
  public void seek(long position, long timeUs) {
    delegate.seek(position, timeUs);
  }

  @Override
  public void release() {
    delegate.release();
  }

  @Override
  public Extractor getUnderlyingImplementation() {
    return delegate.getUnderlyingImplementation();
  }
}
