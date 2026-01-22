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

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageWriter;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** End to end instrumentation test for {@link SurfaceAssetLoader} using {@link Transformer}. */
@RunWith(AndroidJUnit4.class)
public class SurfaceAssetLoaderTest {

  // TODO: b/351776005 - Add HDR-based test case(s).

  private static final String TEST_BITMAP_PATH = "media/jpeg/london-512.jpg";
  private static final long TIMEOUT_MS = 10_000L; // Set to avoid timing out on slow emulators.

  @Rule public final TestName testName = new TestName();

  private final Context context = ApplicationProvider.getApplicationContext();

  private String testId;

  @Before
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @Test
  public void encodingFromSurface_succeeds() throws Exception {
    assumeTrue("ImageWriter with pixel format set requires API 29", Util.SDK_INT >= 29);

    SettableFuture<SurfaceAssetLoader> surfaceAssetLoaderSettableFuture = SettableFuture.create();
    SettableFuture<Surface> surfaceSettableFuture = SettableFuture.create();
    Transformer transformer =
        new Transformer.Builder(context)
            .setAssetLoaderFactory(
                new SurfaceAssetLoader.Factory(
                    new SurfaceAssetLoader.Callback() {
                      @Override
                      public void onSurfaceAssetLoaderCreated(
                          SurfaceAssetLoader surfaceAssetLoader) {
                        surfaceAssetLoaderSettableFuture.set(surfaceAssetLoader);
                      }

                      @Override
                      public void onSurfaceReady(Surface surface, EditedMediaItem editedMediaItem) {
                        surfaceSettableFuture.set(surface);
                      }
                    }))
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(SurfaceAssetLoader.MEDIA_ITEM_URI_SCHEME + ":"))
            .build();
    ListenableFuture<ExportResult> exportCompletionFuture =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .runAsync(testId, editedMediaItem);
    SurfaceAssetLoader surfaceAssetLoader =
        surfaceAssetLoaderSettableFuture.get(TIMEOUT_MS, MILLISECONDS);
    Bitmap bitmap = BitmapPixelTestUtil.readBitmap(TEST_BITMAP_PATH);
    surfaceAssetLoader.setContentFormat(
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_RAW)
            .setWidth(bitmap.getWidth())
            .setHeight(bitmap.getHeight())
            .setColorInfo(ColorInfo.SRGB_BT709_FULL)
            .build());
    Surface surface = surfaceSettableFuture.get(TIMEOUT_MS, MILLISECONDS);

    int inputFrameCount = 10;
    try (ImageWriter imageWriter =
        ImageWriter.newInstance(surface, /* maxImages= */ inputFrameCount, PixelFormat.RGBA_8888)) {
      ConditionVariable readyForInputCondition = new ConditionVariable();
      imageWriter.setOnImageReleasedListener(
          unusedImageWriter -> readyForInputCondition.open(), new Handler(Looper.getMainLooper()));
      for (int i = 0; i < inputFrameCount; i++) {
        Image image = imageWriter.dequeueInputImage();
        image.setTimestamp(i * C.NANOS_PER_SECOND / 30);
        BitmapPixelTestUtil.copyRbga8888BitmapToImage(bitmap, image);
        readyForInputCondition.close();
        imageWriter.queueInputImage(image);
        // When frames are queued as fast as possible some can be dropped, so throttle input by
        // blocking until the previous frame has been released by the downstream pipeline.
        if (i > 0) {
          assertThat(readyForInputCondition.block(TIMEOUT_MS)).isTrue();
        }
      }
    }
    surfaceAssetLoader.signalEndOfInput();

    ExportResult exportResult = exportCompletionFuture.get();
    assertThat(exportResult.videoFrameCount).isEqualTo(inputFrameCount);
    assertThat(exportResult.width).isEqualTo(bitmap.getWidth());
    assertThat(exportResult.height).isEqualTo(bitmap.getHeight());
    assertThat(exportResult.durationMs).isEqualTo(300);
  }

  @Test
  public void encodingFromSurface_withLargeTimestamps_succeeds() throws Exception {
    assumeTrue("ImageWriter with pixel format set requires API 29", Util.SDK_INT >= 29);

    SettableFuture<SurfaceAssetLoader> surfaceAssetLoaderSettableFuture = SettableFuture.create();
    SettableFuture<Surface> surfaceSettableFuture = SettableFuture.create();
    Transformer transformer =
        new Transformer.Builder(context)
            .setAssetLoaderFactory(
                new SurfaceAssetLoader.Factory(
                    new SurfaceAssetLoader.Callback() {
                      @Override
                      public void onSurfaceAssetLoaderCreated(
                          SurfaceAssetLoader surfaceAssetLoader) {
                        surfaceAssetLoaderSettableFuture.set(surfaceAssetLoader);
                      }

                      @Override
                      public void onSurfaceReady(Surface surface, EditedMediaItem editedMediaItem) {
                        surfaceSettableFuture.set(surface);
                      }
                    }))
            .build();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(SurfaceAssetLoader.MEDIA_ITEM_URI_SCHEME + ":"))
            .build();
    ListenableFuture<ExportResult> exportCompletionFuture =
        new TransformerAndroidTestRunner.Builder(context, transformer)
            .build()
            .runAsync(testId, editedMediaItem);
    SurfaceAssetLoader surfaceAssetLoader =
        surfaceAssetLoaderSettableFuture.get(TIMEOUT_MS, MILLISECONDS);
    Bitmap bitmap = BitmapPixelTestUtil.readBitmap(TEST_BITMAP_PATH);
    surfaceAssetLoader.setContentFormat(
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_RAW)
            .setWidth(bitmap.getWidth())
            .setHeight(bitmap.getHeight())
            .setColorInfo(ColorInfo.SRGB_BT709_FULL)
            .build());
    Surface surface = surfaceSettableFuture.get(TIMEOUT_MS, MILLISECONDS);

    int inputFrameCount = 10;
    try (ImageWriter imageWriter =
        ImageWriter.newInstance(surface, /* maxImages= */ inputFrameCount, PixelFormat.RGBA_8888)) {
      ConditionVariable readyForInputCondition = new ConditionVariable();
      imageWriter.setOnImageReleasedListener(
          unusedImageWriter -> readyForInputCondition.open(), new Handler(Looper.getMainLooper()));
      for (int i = 0; i < inputFrameCount; i++) {
        Image image = imageWriter.dequeueInputImage();

        // Add a large base offset in nanoseconds.
        image.setTimestamp(3_020_642_044_930_642L + i * C.NANOS_PER_SECOND / 30);
        BitmapPixelTestUtil.copyRbga8888BitmapToImage(bitmap, image);
        readyForInputCondition.close();
        imageWriter.queueInputImage(image);
        // When frames are queued as fast as possible some can be dropped, so throttle input by
        // blocking until the previous frame has been released by the downstream pipeline.
        if (i > 0) {
          assertThat(readyForInputCondition.block(TIMEOUT_MS)).isTrue();
        }
      }
    }
    surfaceAssetLoader.signalEndOfInput();

    ExportResult exportResult = exportCompletionFuture.get();
    assertThat(exportResult.videoFrameCount).isEqualTo(inputFrameCount);
    assertThat(exportResult.width).isEqualTo(bitmap.getWidth());
    assertThat(exportResult.height).isEqualTo(bitmap.getHeight());
    assertThat(exportResult.durationMs).isEqualTo(300);
  }
}
