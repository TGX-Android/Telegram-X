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
package androidx.media3.extractor.mp4;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import java.util.List;
import java.util.Objects;

/** A helper class for resolving the MIME type of an MP4 container based on its tracks. */
/* package */ final class MimeTypeResolver {

  /**
   * Determines the container MIME type based on a single track's {@link Format}.
   *
   * <p>This method should only be used when there is a single track available to determine the
   * container MIME type. If multiple tracks are present, use {@link #getContainerMimeType(List)}
   * instead.
   *
   * <p>The container MIME type is determined by the guidelines specified in:
   *
   * <ul>
   *   <li><a href="https://www.rfc-editor.org/rfc/rfc4337.html#section-2">RFC 4337, Section 2</a>.
   *   <li>HEIC as specified in ISO/IEC 23008-12, E.2, often treated as HEIF for compatibility.
   *   <li><a href="https://aomediacodec.github.io/av1-avif/#mime-registration">AVIF spec</a>.
   * </ul>
   *
   * @param format The {@link Format} instance representing a single track's format.
   * @return The inferred container MIME type for the track's format.
   */
  public static String getContainerMimeType(Format format) {
    @Nullable String sampleMimeType = format.sampleMimeType;

    if (MimeTypes.isVideo(sampleMimeType)) {
      return MimeTypes.VIDEO_MP4;
    }

    if (MimeTypes.isAudio(sampleMimeType)) {
      return MimeTypes.AUDIO_MP4;
    }

    if (MimeTypes.isImage(sampleMimeType)) {
      if (Objects.equals(sampleMimeType, MimeTypes.IMAGE_HEIC)) {
        return MimeTypes.IMAGE_HEIF;
      } else if (Objects.equals(sampleMimeType, MimeTypes.IMAGE_AVIF)) {
        return MimeTypes.IMAGE_AVIF;
      }
    }

    return MimeTypes.APPLICATION_MP4;
  }

  /**
   * Determines the container MIME type for an MP4 file based on its tracks.
   *
   * <p>The container MIME type is determined by the guidelines specified in:
   *
   * <ul>
   *   <li><a href="https://www.rfc-editor.org/rfc/rfc4337.html#section-2">RFC 4337, Section 2</a>.
   *   <li>HEIC as specified in ISO/IEC 23008-12, E.2, often treated as HEIF for compatibility.
   *   <li><a href="https://aomediacodec.github.io/av1-avif/#mime-registration">AVIF spec</a>.
   * </ul>
   *
   * @param trackSampleTables A list of {@link TrackSampleTable} instances, each representing a
   *     track with {@link Format} information for an MP4 file.
   * @return The inferred container MIME type for the MP4 file.
   */
  public static String getContainerMimeType(List<TrackSampleTable> trackSampleTables) {
    boolean hasAudio = false;
    @Nullable String imageMimeType = null;

    for (TrackSampleTable trackSampleTable : trackSampleTables) {
      @Nullable String sampleMimeType = trackSampleTable.track.format.sampleMimeType;

      if (MimeTypes.isVideo(sampleMimeType)) {
        return MimeTypes.VIDEO_MP4;
      }

      if (MimeTypes.isAudio(sampleMimeType)) {
        hasAudio = true;
      } else if (MimeTypes.isImage(sampleMimeType)) {
        if (Objects.equals(sampleMimeType, MimeTypes.IMAGE_HEIC)) {
          imageMimeType = MimeTypes.IMAGE_HEIF;
        } else if (Objects.equals(sampleMimeType, MimeTypes.IMAGE_AVIF)) {
          imageMimeType = MimeTypes.IMAGE_AVIF;
        }
      }
    }

    if (hasAudio) {
      return MimeTypes.AUDIO_MP4;
    } else if (imageMimeType != null) {
      return imageMimeType;
    }

    return MimeTypes.APPLICATION_MP4;
  }

  private MimeTypeResolver() {}
}
