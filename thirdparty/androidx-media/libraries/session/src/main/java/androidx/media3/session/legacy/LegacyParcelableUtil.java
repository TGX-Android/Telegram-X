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
package androidx.media3.session.legacy;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.RestrictTo;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.PolyNull;

/**
 * Utilities to convert {@link android.os.Parcelable} instances to and from legacy package names
 * when writing to or reading them from a {@link android.os.Bundle}.
 */
@UnstableApi
@RestrictTo(LIBRARY)
public final class LegacyParcelableUtil {

  private LegacyParcelableUtil() {}

  /**
   * Converts one {@link Parcelable} to another assuming they both share the same parcel structure.
   *
   * @param value The input {@link Parcelable}.
   * @param creator The {@link Parcelable.Creator} of the output type.
   * @return The output {@link Parcelable}.
   * @param <T> The output type.
   * @param <U> The input type.
   */
  public static <T extends Parcelable, U extends Parcelable> @PolyNull T convert(
      @PolyNull U value, Parcelable.Creator<T> creator) {
    if (value == null) {
      return null;
    }
    value = maybeApplyMediaDescriptionParcelableBugWorkaround(value);
    Parcel parcel = Parcel.obtain();
    try {
      value.writeToParcel(parcel, /* flags= */ 0);
      parcel.setDataPosition(0);
      T result = creator.createFromParcel(parcel);
      result = maybeApplyMediaDescriptionParcelableBugWorkaround(result);
      return result;
    } finally {
      parcel.recycle();
    }
  }

  /**
   * Converts one {@link Parcelable} {@link List} to another assuming they both share the same
   * parcel structure.
   *
   * @param value The input {@link Parcelable} {@link List}.
   * @param creator The {@link Parcelable.Creator} of the output type.
   * @return The output {@link Parcelable} {@link ArrayList}.
   * @param <T> The output type.
   * @param <U> The input type.
   */
  public static <T extends Parcelable, U extends Parcelable> @PolyNull ArrayList<T> convertList(
      @PolyNull List<U> value, Parcelable.Creator<T> creator) {
    if (value == null) {
      return null;
    }
    ArrayList<T> output = new ArrayList<>();
    for (int i = 0; i < value.size(); i++) {
      output.add(convert(value.get(i), creator));
    }
    return output;
  }

  // TODO: b/335804969 - Remove this workaround once the bug fix is in the androidx.media dependency
  @SuppressWarnings("unchecked")
  private static <T> T maybeApplyMediaDescriptionParcelableBugWorkaround(T value) {
    if (Util.SDK_INT < 21 || Util.SDK_INT >= 23) {
      return value;
    }
    if (value instanceof android.support.v4.media.MediaBrowserCompat.MediaItem) {
      android.support.v4.media.MediaBrowserCompat.MediaItem mediaItem =
          (android.support.v4.media.MediaBrowserCompat.MediaItem) value;
      return (T)
          new android.support.v4.media.MediaBrowserCompat.MediaItem(
              rebuildMediaDescriptionCompat(mediaItem.getDescription()), mediaItem.getFlags());
    } else if (value instanceof android.support.v4.media.MediaDescriptionCompat) {
      android.support.v4.media.MediaDescriptionCompat description =
          (android.support.v4.media.MediaDescriptionCompat) value;
      return (T) rebuildMediaDescriptionCompat(description);
    } else {
      return value;
    }
  }

  private static android.support.v4.media.MediaDescriptionCompat rebuildMediaDescriptionCompat(
      android.support.v4.media.MediaDescriptionCompat value) {
    return new android.support.v4.media.MediaDescriptionCompat.Builder()
        .setMediaId(value.getMediaId())
        .setTitle(value.getTitle())
        .setSubtitle(value.getSubtitle())
        .setDescription(value.getDescription())
        .setIconBitmap(value.getIconBitmap())
        .setIconUri(value.getIconUri())
        .setExtras(value.getExtras())
        .setMediaUri(value.getMediaUri())
        .build();
  }
}
