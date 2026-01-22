/*
 * Copyright (C) 2021 The Android Open Source Project
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
package androidx.media3.datasource;

import static java.lang.Math.max;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.media3.common.C;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.UnstableApi;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Utility methods for {@link Bitmap} instances. */
@UnstableApi
public final class BitmapUtil {

  private BitmapUtil() {}

  /**
   * Decodes a {@link Bitmap} from a byte array using {@link BitmapFactory} and the {@link
   * ExifInterface}.
   *
   * @param data Byte array of compressed image data.
   * @param length The number of bytes to parse.
   * @param options The {@link BitmapFactory.Options} to decode the {@code data} with.
   * @param maximumOutputDimension The largest output Bitmap dimension that can be returned by this
   *     method, or {@link C#LENGTH_UNSET} if no limits are enforced.
   * @throws ParserException if the {@code data} could not be decoded.
   */
  // BitmapFactory's options parameter is null-ok.
  @SuppressWarnings("nullness:argument.type.incompatible")
  public static Bitmap decode(
      byte[] data, int length, @Nullable BitmapFactory.Options options, int maximumOutputDimension)
      throws IOException {
    if (maximumOutputDimension != C.LENGTH_UNSET) {
      if (options == null) {
        options = new BitmapFactory.Options();
      }
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeByteArray(data, /* offset= */ 0, length, options);
      int largerDimensions = max(options.outWidth, options.outHeight);

      options.inJustDecodeBounds = false;
      options.inSampleSize = 1;
      // Only scaling by 2x is supported.
      while (largerDimensions > maximumOutputDimension) {
        options.inSampleSize *= 2;
        largerDimensions /= 2;
      }
    }

    @Nullable Bitmap bitmap = BitmapFactory.decodeByteArray(data, /* offset= */ 0, length, options);
    if (options != null) {
      options.inSampleSize = 1;
    }
    if (bitmap == null) {
      throw ParserException.createForMalformedContainer(
          "Could not decode image data", new IllegalStateException());
    }
    // BitmapFactory doesn't read the exif header, so we use the ExifInterface to this do ensure the
    // bitmap is correctly orientated.
    ExifInterface exifInterface;
    try (InputStream inputStream = new ByteArrayInputStream(data)) {
      exifInterface = new ExifInterface(inputStream);
    }
    int rotationDegrees = exifInterface.getRotationDegrees();
    if (rotationDegrees != 0) {
      Matrix matrix = new Matrix();
      matrix.postRotate(rotationDegrees);
      bitmap =
          Bitmap.createBitmap(
              bitmap,
              /* x= */ 0,
              /* y= */ 0,
              bitmap.getWidth(),
              bitmap.getHeight(),
              matrix,
              /* filter= */ false);
    }
    return bitmap;
  }
}
