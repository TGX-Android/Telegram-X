/*
 * Copyright (C) 2018 The Android Open Source Project
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

/** MPEG location lookup table frame. */
@UnstableApi
public final class MlltFrame extends Id3Frame {

  public static final String ID = "MLLT";

  public final int mpegFramesBetweenReference;
  public final int bytesBetweenReference;
  public final int millisecondsBetweenReference;
  public final int[] bytesDeviations;
  public final int[] millisecondsDeviations;

  public MlltFrame(
      int mpegFramesBetweenReference,
      int bytesBetweenReference,
      int millisecondsBetweenReference,
      int[] bytesDeviations,
      int[] millisecondsDeviations) {
    super(ID);
    this.mpegFramesBetweenReference = mpegFramesBetweenReference;
    this.bytesBetweenReference = bytesBetweenReference;
    this.millisecondsBetweenReference = millisecondsBetweenReference;
    this.bytesDeviations = bytesDeviations;
    this.millisecondsDeviations = millisecondsDeviations;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MlltFrame other = (MlltFrame) obj;
    return mpegFramesBetweenReference == other.mpegFramesBetweenReference
        && bytesBetweenReference == other.bytesBetweenReference
        && millisecondsBetweenReference == other.millisecondsBetweenReference
        && Arrays.equals(bytesDeviations, other.bytesDeviations)
        && Arrays.equals(millisecondsDeviations, other.millisecondsDeviations);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + mpegFramesBetweenReference;
    result = 31 * result + bytesBetweenReference;
    result = 31 * result + millisecondsBetweenReference;
    result = 31 * result + Arrays.hashCode(bytesDeviations);
    result = 31 * result + Arrays.hashCode(millisecondsDeviations);
    return result;
  }
}
