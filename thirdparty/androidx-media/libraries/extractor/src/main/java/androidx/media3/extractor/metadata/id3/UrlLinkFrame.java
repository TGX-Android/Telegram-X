/*
 * Copyright (C) 2017 The Android Open Source Project
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
package androidx.media3.extractor.metadata.id3;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import java.util.Objects;

/** Url link ID3 frame. */
@UnstableApi
public final class UrlLinkFrame extends Id3Frame {

  @Nullable public final String description;
  public final String url;

  public UrlLinkFrame(String id, @Nullable String description, String url) {
    super(id);
    this.description = description;
    this.url = url;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    UrlLinkFrame other = (UrlLinkFrame) obj;
    return id.equals(other.id)
        && Objects.equals(description, other.description)
        && Objects.equals(url, other.url);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + id.hashCode();
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (url != null ? url.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return id + ": url=" + url;
  }
}
