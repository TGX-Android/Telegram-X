/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.muxer;

import static androidx.media3.container.MdtaMetadataEntry.AUXILIARY_TRACKS_SAMPLES_INTERLEAVED;
import static androidx.media3.container.MdtaMetadataEntry.AUXILIARY_TRACKS_SAMPLES_NOT_INTERLEAVED;
import static androidx.media3.container.MdtaMetadataEntry.TYPE_INDICATOR_8_BIT_UNSIGNED_INT;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.container.Mp4OrientationData;
import androidx.media3.container.Mp4TimestampData;
import androidx.media3.container.XmpData;
import com.google.common.primitives.Longs;
import java.util.List;

/** Utility methods for muxer. */
@UnstableApi
public final class MuxerUtil {
  /** The maximum value of a 32-bit unsigned int. */
  public static final long UNSIGNED_INT_MAX_VALUE = 4_294_967_295L;

  private MuxerUtil() {}

  /** Returns whether a given {@link Metadata.Entry metadata} is supported. */
  public static boolean isMetadataSupported(Metadata.Entry metadata) {
    return metadata instanceof Mp4OrientationData
        || metadata instanceof Mp4LocationData
        || (metadata instanceof Mp4TimestampData
            && isMp4TimestampDataSupported((Mp4TimestampData) metadata))
        || (metadata instanceof MdtaMetadataEntry
            && isMdtaMetadataEntrySupported((MdtaMetadataEntry) metadata))
        || metadata instanceof XmpData;
  }

  /**
   * Returns whether the given {@linkplain Format track format} is an auxiliary track.
   *
   * <p>The {@linkplain Format track format} with {@link C#ROLE_FLAG_AUXILIARY} and the {@code
   * auxiliaryTrackType} from the following are considered as an auxiliary track.
   *
   * <ul>
   *   <li>{@link C#AUXILIARY_TRACK_TYPE_ORIGINAL}
   *   <li>{@link C#AUXILIARY_TRACK_TYPE_DEPTH_LINEAR}
   *   <li>{@link C#AUXILIARY_TRACK_TYPE_DEPTH_INVERSE}
   *   <li>{@link C#AUXILIARY_TRACK_TYPE_DEPTH_METADATA}
   * </ul>
   */
  /* package */ static boolean isAuxiliaryTrack(Format format) {
    return (format.roleFlags & C.ROLE_FLAG_AUXILIARY) > 0
        && (format.auxiliaryTrackType == C.AUXILIARY_TRACK_TYPE_ORIGINAL
            || format.auxiliaryTrackType == C.AUXILIARY_TRACK_TYPE_DEPTH_LINEAR
            || format.auxiliaryTrackType == C.AUXILIARY_TRACK_TYPE_DEPTH_INVERSE
            || format.auxiliaryTrackType == C.AUXILIARY_TRACK_TYPE_DEPTH_METADATA);
  }

  /** Returns a {@link MdtaMetadataEntry} for the auxiliary tracks offset metadata. */
  /* package */ static MdtaMetadataEntry getAuxiliaryTracksOffsetMetadata(long offset) {
    return new MdtaMetadataEntry(
        MdtaMetadataEntry.KEY_AUXILIARY_TRACKS_OFFSET,
        Longs.toByteArray(offset),
        MdtaMetadataEntry.TYPE_INDICATOR_UNSIGNED_INT64);
  }

  /** Returns a {@link MdtaMetadataEntry} for the auxiliary tracks length metadata. */
  /* package */ static MdtaMetadataEntry getAuxiliaryTracksLengthMetadata(long length) {
    return new MdtaMetadataEntry(
        MdtaMetadataEntry.KEY_AUXILIARY_TRACKS_LENGTH,
        Longs.toByteArray(length),
        MdtaMetadataEntry.TYPE_INDICATOR_UNSIGNED_INT64);
  }

  /**
   * Populates auxiliary tracks metadata.
   *
   * @param metadataCollector The {@link MetadataCollector} to add the metadata to.
   * @param timestampData The {@link Mp4TimestampData}.
   * @param samplesInterleaved Whether auxiliary track samples are interleaved with the primary
   *     track samples.
   * @param auxiliaryTracks The auxiliary tracks.
   */
  /* package */ static void populateAuxiliaryTracksMetadata(
      MetadataCollector metadataCollector,
      Mp4TimestampData timestampData,
      boolean samplesInterleaved,
      List<Track> auxiliaryTracks) {
    metadataCollector.addMetadata(timestampData);
    metadataCollector.addMetadata(getAuxiliaryTracksSamplesLocationMetadata(samplesInterleaved));
    metadataCollector.addMetadata(getAuxiliaryTracksMapMetadata(auxiliaryTracks));
  }

  private static MdtaMetadataEntry getAuxiliaryTracksSamplesLocationMetadata(
      boolean samplesInterleaved) {
    return new MdtaMetadataEntry(
        MdtaMetadataEntry.KEY_AUXILIARY_TRACKS_INTERLEAVED,
        new byte[] {
          samplesInterleaved
              ? AUXILIARY_TRACKS_SAMPLES_INTERLEAVED
              : AUXILIARY_TRACKS_SAMPLES_NOT_INTERLEAVED
        },
        TYPE_INDICATOR_8_BIT_UNSIGNED_INT);
  }

  private static MdtaMetadataEntry getAuxiliaryTracksMapMetadata(List<Track> auxiliaryTracks) {
    // 1 byte version + 1 byte track count (n) + n bytes track types.
    int totalTracks = auxiliaryTracks.size();
    int dataSize = 2 + totalTracks;
    byte[] data = new byte[dataSize];
    data[0] = 1; // version
    data[1] = (byte) totalTracks; // track count
    for (int i = 0; i < totalTracks; i++) {
      Track track = auxiliaryTracks.get(i);
      int trackType;
      switch (track.format.auxiliaryTrackType) {
        case C.AUXILIARY_TRACK_TYPE_ORIGINAL:
          trackType = 0;
          break;
        case C.AUXILIARY_TRACK_TYPE_DEPTH_LINEAR:
          trackType = 1;
          break;
        case C.AUXILIARY_TRACK_TYPE_DEPTH_INVERSE:
          trackType = 2;
          break;
        case C.AUXILIARY_TRACK_TYPE_DEPTH_METADATA:
          trackType = 3;
          break;
        default:
          throw new IllegalArgumentException(
              "Unsupported auxiliary track type " + track.format.auxiliaryTrackType);
      }
      data[i + 2] = (byte) trackType;
    }
    return new MdtaMetadataEntry(
        MdtaMetadataEntry.KEY_AUXILIARY_TRACKS_MAP,
        data,
        MdtaMetadataEntry.TYPE_INDICATOR_RESERVED);
  }

  private static boolean isMdtaMetadataEntrySupported(MdtaMetadataEntry mdtaMetadataEntry) {
    return mdtaMetadataEntry.typeIndicator == MdtaMetadataEntry.TYPE_INDICATOR_STRING
        || mdtaMetadataEntry.typeIndicator == MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32;
  }

  private static boolean isMp4TimestampDataSupported(Mp4TimestampData timestampData) {
    return timestampData.creationTimestampSeconds <= UNSIGNED_INT_MAX_VALUE
        && timestampData.modificationTimestampSeconds <= UNSIGNED_INT_MAX_VALUE;
  }
}
