/*
 * Copyright (C) 2019 The Android Open Source Project
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
package androidx.media3.extractor.metadata.flac;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import com.google.common.base.Ascii;
import com.google.common.primitives.Ints;

/**
 * @deprecated Use {@link androidx.media3.extractor.metadata.vorbis.VorbisComment} instead.
 */
@SuppressWarnings("deprecation") // Internal references to own class
@Deprecated
@UnstableApi
public class VorbisComment implements Metadata.Entry {

  /** The key in upper case, to ease case-insensitive comparisons. */
  public final String key;

  /** The value. */
  public final String value;

  /**
   * Constructs an instance.
   *
   * @param key The key. Must be an ASCII string containing only characters between 0x20 and 0x7D
   *     (inclusive), excluding 0x3D ('=').
   * @param value The value.
   */
  public VorbisComment(String key, String value) {
    this.key = Ascii.toUpperCase(key);
    this.value = value;
  }

  @Override
  public void populateMediaMetadata(MediaMetadata.Builder builder) {
    // Vorbis comments can have duplicate keys, but all these fields are singular on MediaMetadata,
    // so we naively overwrite whatever any previously set value (which results in 'keep last'
    // semantics).
    switch (key) {
      case "TITLE":
        builder.setTitle(value);
        break;
      case "ARTIST":
        builder.setArtist(value);
        break;
      case "ALBUM":
        builder.setAlbumTitle(value);
        break;
      case "ALBUMARTIST":
        builder.setAlbumArtist(value);
        break;
      case "TRACKNUMBER":
        @Nullable Integer trackNumber = Ints.tryParse(value);
        if (trackNumber != null) {
          builder.setTrackNumber(trackNumber);
        }
        break;
      case "TOTALTRACKS":
        @Nullable Integer totalTracks = Ints.tryParse(value);
        if (totalTracks != null) {
          builder.setTotalTrackCount(totalTracks);
        }
        break;
      case "DISCNUMBER":
        @Nullable Integer discNumber = Ints.tryParse(value);
        if (discNumber != null) {
          builder.setDiscNumber(discNumber);
        }
        break;
      case "TOTALDISCS":
        @Nullable Integer totalDiscs = Ints.tryParse(value);
        if (totalDiscs != null) {
          builder.setTotalDiscCount(totalDiscs);
        }
        break;
      case "GENRE":
        builder.setGenre(value);
        break;
      case "DESCRIPTION":
        builder.setDescription(value);
        break;
      default:
        break;
    }
  }

  @Override
  public String toString() {
    return "VC: " + key + "=" + value;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    VorbisComment other = (VorbisComment) obj;
    return key.equals(other.key) && value.equals(other.value);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + key.hashCode();
    result = 31 * result + value.hashCode();
    return result;
  }
}
