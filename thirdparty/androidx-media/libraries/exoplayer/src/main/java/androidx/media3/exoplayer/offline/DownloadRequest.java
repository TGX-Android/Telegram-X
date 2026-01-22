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
package androidx.media3.exoplayer.offline;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Util.castNonNull;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.StreamKey;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Defines content to be downloaded. */
@UnstableApi
public final class DownloadRequest implements Parcelable {

  /** Thrown when the encoded request data belongs to an unsupported request type. */
  public static class UnsupportedRequestException extends IOException {}

  /** A builder for download requests. */
  public static class Builder {
    private final String id;
    private final Uri uri;
    @Nullable private String mimeType;
    @Nullable private List<StreamKey> streamKeys;
    @Nullable private byte[] keySetId;
    @Nullable private String customCacheKey;
    @Nullable private byte[] data;
    @Nullable private ByteRange byteRange;

    /** Creates a new instance with the specified id and uri. */
    public Builder(String id, Uri uri) {
      this.id = id;
      this.uri = uri;
      this.byteRange = null;
    }

    /** Sets the {@link DownloadRequest#mimeType}. */
    @CanIgnoreReturnValue
    public Builder setMimeType(@Nullable String mimeType) {
      this.mimeType = MimeTypes.normalizeMimeType(mimeType);
      return this;
    }

    /** Sets the {@link DownloadRequest#streamKeys}. */
    @CanIgnoreReturnValue
    public Builder setStreamKeys(@Nullable List<StreamKey> streamKeys) {
      this.streamKeys = streamKeys;
      return this;
    }

    /** Sets the {@link DownloadRequest#keySetId}. */
    @CanIgnoreReturnValue
    public Builder setKeySetId(@Nullable byte[] keySetId) {
      this.keySetId = keySetId;
      return this;
    }

    /** Sets the {@link DownloadRequest#customCacheKey}. */
    @CanIgnoreReturnValue
    public Builder setCustomCacheKey(@Nullable String customCacheKey) {
      this.customCacheKey = customCacheKey;
      return this;
    }

    /** Sets the {@link DownloadRequest#data}. */
    @CanIgnoreReturnValue
    public Builder setData(@Nullable byte[] data) {
      this.data = data;
      return this;
    }

    /**
     * Sets the byte range to be downloaded.
     *
     * <p>This will be ignored for DASH, HLS and SmoothStreaming downloads.
     *
     * @param offset The offset that the download should start from.
     * @param length The length from the {@code offset} to be downloaded, or @link C#LENGTH_UNSET}
     *     if the media should be downloaded to the end.
     */
    @CanIgnoreReturnValue
    public Builder setByteRange(long offset, long length) {
      this.byteRange = new ByteRange(offset, length);
      return this;
    }

    public DownloadRequest build() {
      return new DownloadRequest(
          id,
          uri,
          mimeType,
          streamKeys != null ? streamKeys : ImmutableList.of(),
          keySetId,
          customCacheKey,
          data,
          byteRange);
    }
  }

  /** The unique content id. */
  public final String id;

  /** The uri being downloaded. */
  public final Uri uri;

  /**
   * The MIME type of this content. Used as a hint to infer the content's type (DASH, HLS,
   * SmoothStreaming). If null, a {@code DownloadService} will infer the content type from the
   * {@link #uri}.
   */
  @Nullable public final String mimeType;

  /** Stream keys to be downloaded. If empty, all streams will be downloaded. */
  public final List<StreamKey> streamKeys;

  /** The key set id of the offline licence if the content is protected with DRM. */
  @Nullable public final byte[] keySetId;

  /**
   * Custom key for cache indexing, or null. Must be null for DASH, HLS and SmoothStreaming
   * downloads.
   */
  @Nullable public final String customCacheKey;

  /** Application defined data associated with the download. May be empty. */
  public final byte[] data;

  /** The byte range to be downloaded. Must be null for DASH, HLS and SmoothStreaming downloads. */
  @Nullable public final ByteRange byteRange;

  /**
   * @param id See {@link #id}.
   * @param uri See {@link #uri}.
   * @param mimeType See {@link #mimeType}
   * @param streamKeys See {@link #streamKeys}.
   * @param customCacheKey See {@link #customCacheKey}.
   * @param data See {@link #data}.
   */
  private DownloadRequest(
      String id,
      Uri uri,
      @Nullable String mimeType,
      List<StreamKey> streamKeys,
      @Nullable byte[] keySetId,
      @Nullable String customCacheKey,
      @Nullable byte[] data,
      @Nullable ByteRange byteRange) {
    @C.ContentType int contentType = Util.inferContentTypeForUriAndMimeType(uri, mimeType);
    if (contentType == C.CONTENT_TYPE_DASH
        || contentType == C.CONTENT_TYPE_HLS
        || contentType == C.CONTENT_TYPE_SS) {
      checkArgument(customCacheKey == null, "customCacheKey must be null for type: " + contentType);
      this.byteRange = null;
    } else {
      this.byteRange = byteRange;
    }
    this.id = id;
    this.uri = uri;
    this.mimeType = mimeType;
    ArrayList<StreamKey> mutableKeys = new ArrayList<>(streamKeys);
    Collections.sort(mutableKeys);
    this.streamKeys = Collections.unmodifiableList(mutableKeys);
    this.keySetId = keySetId != null ? Arrays.copyOf(keySetId, keySetId.length) : null;
    this.customCacheKey = customCacheKey;
    this.data = data != null ? Arrays.copyOf(data, data.length) : Util.EMPTY_BYTE_ARRAY;
  }

  /* package */ DownloadRequest(Parcel in) {
    id = castNonNull(in.readString());
    uri = Uri.parse(castNonNull(in.readString()));
    mimeType = in.readString();
    int streamKeyCount = in.readInt();
    ArrayList<StreamKey> mutableStreamKeys = new ArrayList<>(streamKeyCount);
    for (int i = 0; i < streamKeyCount; i++) {
      mutableStreamKeys.add(in.readParcelable(StreamKey.class.getClassLoader()));
    }
    streamKeys = Collections.unmodifiableList(mutableStreamKeys);
    keySetId = in.createByteArray();
    customCacheKey = in.readString();
    data = castNonNull(in.createByteArray());
    byteRange = in.readParcelable(ByteRange.class.getClassLoader());
  }

  /**
   * Returns a copy with the specified ID.
   *
   * @param id The ID of the copy.
   * @return The copy with the specified ID.
   */
  public DownloadRequest copyWithId(String id) {
    return new DownloadRequest(
        id, uri, mimeType, streamKeys, keySetId, customCacheKey, data, byteRange);
  }

  /**
   * Returns a copy with the specified key set ID.
   *
   * @param keySetId The key set ID of the copy.
   * @return The copy with the specified key set ID.
   */
  public DownloadRequest copyWithKeySetId(@Nullable byte[] keySetId) {
    return new DownloadRequest(
        id, uri, mimeType, streamKeys, keySetId, customCacheKey, data, byteRange);
  }

  /**
   * Returns the result of merging {@code newRequest} into this request. The requests must have the
   * same {@link #id}.
   *
   * <p>The resulting request contains the stream keys from both requests. For all other member
   * variables, those in {@code newRequest} are preferred.
   *
   * @param newRequest The request being merged.
   * @return The merged result.
   * @throws IllegalArgumentException If the requests do not have the same {@link #id}.
   */
  public DownloadRequest copyWithMergedRequest(DownloadRequest newRequest) {
    checkArgument(id.equals(newRequest.id));
    List<StreamKey> mergedKeys;
    if (streamKeys.isEmpty() || newRequest.streamKeys.isEmpty()) {
      // If either streamKeys is empty then all streams should be downloaded.
      mergedKeys = Collections.emptyList();
    } else {
      mergedKeys = new ArrayList<>(streamKeys);
      for (int i = 0; i < newRequest.streamKeys.size(); i++) {
        StreamKey newKey = newRequest.streamKeys.get(i);
        if (!mergedKeys.contains(newKey)) {
          mergedKeys.add(newKey);
        }
      }
    }
    return new DownloadRequest(
        id,
        newRequest.uri,
        newRequest.mimeType,
        mergedKeys,
        newRequest.keySetId,
        newRequest.customCacheKey,
        newRequest.data,
        newRequest.byteRange);
  }

  /** Returns a {@link MediaItem} for the content defined by the request. */
  public MediaItem toMediaItem() {
    return new MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setCustomCacheKey(customCacheKey)
        .setMimeType(mimeType)
        .setStreamKeys(streamKeys)
        .build();
  }

  @Override
  public String toString() {
    return mimeType + ":" + id;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof DownloadRequest)) {
      return false;
    }
    DownloadRequest that = (DownloadRequest) o;
    return id.equals(that.id)
        && uri.equals(that.uri)
        && Objects.equals(mimeType, that.mimeType)
        && streamKeys.equals(that.streamKeys)
        && Arrays.equals(keySetId, that.keySetId)
        && Objects.equals(customCacheKey, that.customCacheKey)
        && Arrays.equals(data, that.data)
        && Objects.equals(byteRange, that.byteRange);
  }

  @Override
  public int hashCode() {
    int result = 31 * id.hashCode();
    result = 31 * result + uri.hashCode();
    result = 31 * result + (mimeType != null ? mimeType.hashCode() : 0);
    result = 31 * result + streamKeys.hashCode();
    result = 31 * result + Arrays.hashCode(keySetId);
    result = 31 * result + (customCacheKey != null ? customCacheKey.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(data);
    result = 31 * result + (byteRange != null ? byteRange.hashCode() : 0);
    return result;
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(uri.toString());
    dest.writeString(mimeType);
    dest.writeInt(streamKeys.size());
    for (int i = 0; i < streamKeys.size(); i++) {
      dest.writeParcelable(streamKeys.get(i), /* parcelableFlags= */ 0);
    }
    dest.writeByteArray(keySetId);
    dest.writeString(customCacheKey);
    dest.writeByteArray(data);
    dest.writeParcelable(byteRange, /* parcelableFlags= */ 0);
  }

  public static final Parcelable.Creator<DownloadRequest> CREATOR =
      new Parcelable.Creator<DownloadRequest>() {

        @Override
        public DownloadRequest createFromParcel(Parcel in) {
          return new DownloadRequest(in);
        }

        @Override
        public DownloadRequest[] newArray(int size) {
          return new DownloadRequest[size];
        }
      };

  /** Defines the byte range. */
  public static final class ByteRange implements Parcelable {

    /** The offset of the byte range. */
    public final long offset;

    /** The length of the byte range. */
    public final long length;

    /* package */ ByteRange(long offset, long length) {
      checkArgument(offset >= 0);
      checkArgument(length >= 0 || length == C.LENGTH_UNSET);
      this.offset = offset;
      this.length = length;
    }

    /* package */ ByteRange(Parcel in) {
      this(in.readLong(), in.readLong());
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (!(o instanceof ByteRange)) {
        return false;
      }
      ByteRange that = (ByteRange) o;
      return offset == that.offset && length == that.length;
    }

    @Override
    public int hashCode() {
      int result = 31 * (int) offset;
      result = 31 * result + (int) length;
      return result;
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeLong(offset);
      dest.writeLong(length);
    }

    public static final Parcelable.Creator<ByteRange> CREATOR =
        new Parcelable.Creator<ByteRange>() {

          @Override
          public ByteRange createFromParcel(Parcel in) {
            return new ByteRange(in);
          }

          @Override
          public ByteRange[] newArray(int size) {
            return new ByteRange[size];
          }
        };
  }
}
