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

package androidx.media3.transformer;

import android.media.MediaCodec;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;

/** A {@link Muxer} implementation that does nothing. */
/* package */ final class NoWriteMuxer implements Muxer {
  public static final class Factory implements Muxer.Factory {

    private final ImmutableList<String> audioMimeTypes;
    private final ImmutableList<String> videoMimeTypes;

    /**
     * Creates an instance.
     *
     * @param audioMimeTypes The audio {@linkplain MimeTypes mime types} to return in {@link
     *     #getSupportedSampleMimeTypes(int)}.
     * @param videoMimeTypes The video {@linkplain MimeTypes mime types} to return in {@link
     *     #getSupportedSampleMimeTypes(int)}.
     */
    public Factory(ImmutableList<String> audioMimeTypes, ImmutableList<String> videoMimeTypes) {
      this.audioMimeTypes = audioMimeTypes;
      this.videoMimeTypes = videoMimeTypes;
    }

    @Override
    public Muxer create(String path) {
      return new NoWriteMuxer();
    }

    @Override
    public ImmutableList<String> getSupportedSampleMimeTypes(@C.TrackType int trackType) {
      if (trackType == C.TRACK_TYPE_AUDIO) {
        return audioMimeTypes;
      }
      if (trackType == C.TRACK_TYPE_VIDEO) {
        return videoMimeTypes;
      }
      return ImmutableList.of();
    }
  }

  @Override
  public int addTrack(Format format) {
    return 0;
  }

  @Override
  public void writeSampleData(int trackId, ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {}

  @Override
  public void addMetadataEntry(Metadata.Entry metadataEntry) {}

  @Override
  public void close() {}
}
