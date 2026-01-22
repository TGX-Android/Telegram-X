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
package androidx.media3.extractor.metadata.scte35;

import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;

/** Represents a private command as defined in SCTE35, Section 9.3.6. */
@UnstableApi
public final class PrivateCommand extends SpliceCommand {

  /** The {@code pts_adjustment} as defined in SCTE35, Section 9.2. */
  public final long ptsAdjustment;

  /** The identifier as defined in SCTE35, Section 9.3.6. */
  public final long identifier;

  /** The private bytes as defined in SCTE35, Section 9.3.6. */
  public final byte[] commandBytes;

  private PrivateCommand(long identifier, byte[] commandBytes, long ptsAdjustment) {
    this.ptsAdjustment = ptsAdjustment;
    this.identifier = identifier;
    this.commandBytes = commandBytes;
  }

  /* package */ static PrivateCommand parseFromSection(
      ParsableByteArray sectionData, int commandLength, long ptsAdjustment) {
    long identifier = sectionData.readUnsignedInt();
    byte[] privateBytes = new byte[commandLength - 4 /* identifier size */];
    sectionData.readBytes(privateBytes, 0, privateBytes.length);
    return new PrivateCommand(identifier, privateBytes, ptsAdjustment);
  }

  @Override
  public String toString() {
    return "SCTE-35 PrivateCommand { ptsAdjustment="
        + ptsAdjustment
        + ", identifier= "
        + identifier
        + " }";
  }
}
