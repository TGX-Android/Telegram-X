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
package androidx.media3.exoplayer.hls;

import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.exoplayer.upstream.ParsingLoadable;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

/** Parses a X-ASSET-LIST JSON object. */
/* package */ final class AssetListParser
    implements ParsingLoadable.Parser<AssetListParser.AssetList> {

  /** Holds assets. */
  public static final class AssetList {

    private static final AssetList EMPTY = new AssetList(ImmutableList.of(), ImmutableList.of());

    /** The list of assets. */
    public final ImmutableList<Asset> assets;

    /** The list of string attributes of the asset list JSON object. */
    public final ImmutableList<StringAttribute> stringAttributes;

    /** Creates an instance. */
    public AssetList(ImmutableList<Asset> assets, ImmutableList<StringAttribute> stringAttributes) {
      this.assets = assets;
      this.stringAttributes = stringAttributes;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof AssetList)) {
        return false;
      }
      AssetList assetList = (AssetList) o;
      return Objects.equals(assets, assetList.assets)
          && Objects.equals(stringAttributes, assetList.stringAttributes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(assets, stringAttributes);
    }
  }

  /**
   * An asset with a URI and a duration.
   *
   * <p>See RFC 8216bis, appendix D.2, X-ASSET-LIST.
   */
  public static final class Asset {

    /** A uri to an HLS source. */
    public final Uri uri;

    /** The duration, in microseconds. */
    public final long durationUs;

    /** Creates an instance. */
    public Asset(Uri uri, long durationUs) {
      this.uri = uri;
      this.durationUs = durationUs;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Asset)) {
        return false;
      }
      Asset asset = (Asset) o;
      return durationUs == asset.durationUs && Objects.equals(uri, asset.uri);
    }

    @Override
    public int hashCode() {
      return Objects.hash(uri, durationUs);
    }
  }

  /** A string attribute with its name and value. */
  public static final class StringAttribute {

    /** The name of the attribute. */
    public final String name;

    /** The value of the attribute. */
    public final String value;

    /** Creates an instance. */
    public StringAttribute(String name, String value) {
      this.name = name;
      this.value = value;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof StringAttribute)) {
        return false;
      }
      StringAttribute that = (StringAttribute) o;
      return Objects.equals(name, that.name) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, value);
    }
  }

  /** The asset name of the assets array in a X-ASSET-LIST JSON object. */
  private static final String ASSET_LIST_JSON_NAME_ASSET_ARRAY = "ASSETS";

  /** The asset URI name in a X-ASSET-LIST JSON object. */
  private static final String ASSET_LIST_JSON_NAME_URI = "URI";

  /** The asset duration name in a X-ASSET-LIST JSON object. */
  private static final String ASSET_LIST_JSON_NAME_DURATION = "DURATION";

  @Override
  public AssetList parse(Uri uri, InputStream inputStream) throws IOException {
    try (JsonReader reader = new JsonReader(new InputStreamReader(inputStream))) {
      if (reader.peek() != JsonToken.BEGIN_OBJECT) {
        return AssetList.EMPTY;
      }
      ImmutableList.Builder<Asset> assets = new ImmutableList.Builder<>();
      ImmutableList.Builder<StringAttribute> stringAttributes = new ImmutableList.Builder<>();
      reader.beginObject();
      while (reader.hasNext()) {
        JsonToken token = reader.peek();
        if (token.equals(JsonToken.NAME)) {
          String name = reader.nextName();
          if (name.equals(ASSET_LIST_JSON_NAME_ASSET_ARRAY)
              && reader.peek() == JsonToken.BEGIN_ARRAY) {
            parseAssetArray(reader, assets);
          } else if (reader.peek() == JsonToken.STRING) {
            stringAttributes.add(new StringAttribute(name, reader.nextString()));
          } else {
            reader.skipValue();
          }
        }
      }
      return new AssetList(assets.build(), stringAttributes.build());
    }
  }

  private static void parseAssetArray(JsonReader reader, ImmutableList.Builder<Asset> assets)
      throws IOException {
    reader.beginArray();
    while (reader.hasNext()) {
      if (reader.peek() == JsonToken.BEGIN_OBJECT) {
        parseAssetObject(reader, assets);
      }
    }
    reader.endArray();
  }

  private static void parseAssetObject(JsonReader reader, ImmutableList.Builder<Asset> assets)
      throws IOException {
    reader.beginObject();
    String uri = null;
    long duration = C.TIME_UNSET;
    String name;
    while (reader.hasNext()) {
      name = reader.nextName();
      if (name.equals(ASSET_LIST_JSON_NAME_URI) && reader.peek() == JsonToken.STRING) {
        uri = reader.nextString();
      } else if (name.equals(ASSET_LIST_JSON_NAME_DURATION) && reader.peek() == JsonToken.NUMBER) {
        duration = (long) (reader.nextDouble() * C.MICROS_PER_SECOND);
      } else {
        reader.skipValue();
      }
    }
    if (uri != null && duration != C.TIME_UNSET) {
      assets.add(new Asset(Uri.parse(uri), duration));
    }
    reader.endObject();
  }
}
