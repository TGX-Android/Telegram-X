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
package androidx.media3.container;

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.annotation.Nullable;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;

/**
 * Stores the orientation hint for the video playback.
 *
 * <p>The orientation hint is typically read/written in the "tkhd" box (track header box, defined in
 * ISO/IEC 14496-12).
 */
@UnstableApi
public final class Mp4OrientationData implements Metadata.Entry {

  /** The orientation, in degrees. */
  public final int orientation;

  /**
   * Creates an instance.
   *
   * @param orientation The orientation, in degrees. The supported values are 0, 90, 180 and 270
   *     (degrees).
   */
  public Mp4OrientationData(int orientation) {
    checkArgument(
        orientation == 0 || orientation == 90 || orientation == 180 || orientation == 270,
        "Unsupported orientation");
    this.orientation = orientation;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Mp4OrientationData)) {
      return false;
    }
    Mp4OrientationData other = (Mp4OrientationData) obj;
    return orientation == other.orientation;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + Integer.hashCode(orientation);
    return result;
  }

  @Override
  public String toString() {
    return "Orientation= " + orientation;
  }
}
