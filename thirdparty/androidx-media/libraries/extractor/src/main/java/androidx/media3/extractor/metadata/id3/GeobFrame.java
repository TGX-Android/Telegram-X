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
package androidx.media3.extractor.metadata.id3;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import java.util.Arrays;
import java.util.Objects;

/** GEOB (General Encapsulated Object) ID3 frame. */
@UnstableApi
public final class GeobFrame extends Id3Frame {

  public static final String ID = "GEOB";

  public final String mimeType;
  public final String filename;
  public final String description;
  public final byte[] data;

  public GeobFrame(String mimeType, String filename, String description, byte[] data) {
    super(ID);
    this.mimeType = mimeType;
    this.filename = filename;
    this.description = description;
    this.data = data;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    GeobFrame other = (GeobFrame) obj;
    return Objects.equals(mimeType, other.mimeType)
        && Objects.equals(filename, other.filename)
        && Objects.equals(description, other.description)
        && Arrays.equals(data, other.data);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
    result = 31 * result + (filename != null ? filename.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(data);
    return result;
  }

  @Override
  public String toString() {
    return id
        + ": mimeType="
        + mimeType
        + ", filename="
        + filename
        + ", description="
        + description;
  }
}
