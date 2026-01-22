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
import java.util.Arrays;
import java.util.Objects;

/** Chapter table of contents ID3 frame. */
@UnstableApi
public final class ChapterTocFrame extends Id3Frame {

  public static final String ID = "CTOC";

  public final String elementId;
  public final boolean isRoot;
  public final boolean isOrdered;
  public final String[] children;
  private final Id3Frame[] subFrames;

  public ChapterTocFrame(
      String elementId,
      boolean isRoot,
      boolean isOrdered,
      String[] children,
      Id3Frame[] subFrames) {
    super(ID);
    this.elementId = elementId;
    this.isRoot = isRoot;
    this.isOrdered = isOrdered;
    this.children = children;
    this.subFrames = subFrames;
  }

  /** Returns the number of sub-frames. */
  public int getSubFrameCount() {
    return subFrames.length;
  }

  /** Returns the sub-frame at {@code index}. */
  public Id3Frame getSubFrame(int index) {
    return subFrames[index];
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ChapterTocFrame other = (ChapterTocFrame) obj;
    return isRoot == other.isRoot
        && isOrdered == other.isOrdered
        && Objects.equals(elementId, other.elementId)
        && Arrays.equals(children, other.children)
        && Arrays.equals(subFrames, other.subFrames);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (isRoot ? 1 : 0);
    result = 31 * result + (isOrdered ? 1 : 0);
    result = 31 * result + (elementId != null ? elementId.hashCode() : 0);
    return result;
  }
}
