/*
 * Copyright 2025 The Android Open Source Project
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

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
import static androidx.media3.common.util.Assertions.checkNotNull;

import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableMap;

/** Asset loader that provides video from {@link MediaProjection}. */
@UnstableApi
public final class MediaProjectionAssetLoader implements AssetLoader {

  /** Factory for {@link MediaProjectionAssetLoader} instances. */
  public static final class Factory implements AssetLoader.Factory {

    private final MediaProjection mediaProjection;
    private final Rect bounds;
    private final int densityDpi;

    /**
     * Creates a new instance.
     *
     * @param mediaProjection The media projection that will provide media.
     * @param bounds The bounds of captured video frames.
     * @param densityDpi The density of the virtual display, in dots per inch.
     */
    public Factory(MediaProjection mediaProjection, Rect bounds, int densityDpi) {
      this.mediaProjection = mediaProjection;
      this.bounds = bounds;
      this.densityDpi = densityDpi;
    }

    @Override
    public MediaProjectionAssetLoader createAssetLoader(
        EditedMediaItem editedMediaItem,
        Looper looper,
        AssetLoader.Listener listener,
        CompositionSettings compositionSettings) {
      return new MediaProjectionAssetLoader(
          mediaProjection,
          bounds,
          densityDpi,
          editedMediaItem,
          looper,
          listener,
          compositionSettings);
    }
  }

  private static final String VIRTUAL_DISPLAY_NAME = "MediaProjectionAssetLoader";

  private final MediaProjection mediaProjection;
  private final Format screenCaptureFormat;
  private final int densityDpi;
  private final Listener listener;
  private final Handler handler;
  private final SurfaceAssetLoader surfaceAssetLoader;

  @Nullable private Format videoFormat;
  @Nullable private VirtualDisplay virtualDisplay;

  /**
   * Creates a new asset that provides video from {@link MediaProjection}.
   *
   * @param mediaProjection The media projection that will provide media.
   * @param bounds The bounds of captured video frames.
   * @param densityDpi The density of the virtual display, in dots per inch.
   * @param editedMediaItem The media item to load.
   * @param looper Looper on which to call {@code listener} methods.
   * @param listener Listener for events.
   * @param compositionSettings Settings for the composition.
   */
  private MediaProjectionAssetLoader(
      MediaProjection mediaProjection,
      Rect bounds,
      int densityDpi,
      EditedMediaItem editedMediaItem,
      Looper looper,
      Listener listener,
      CompositionSettings compositionSettings) {
    this.mediaProjection = mediaProjection;
    screenCaptureFormat =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_RAW)
            .setWidth(bounds.width())
            .setHeight(bounds.height())
            .setColorInfo(ColorInfo.SRGB_BT709_FULL) // TODO: b/345751897 - Check the color format.
            .build();
    this.densityDpi = densityDpi;
    this.listener = listener;
    this.handler = new Handler(Looper.getMainLooper());

    ComponentListener componentListener = new ComponentListener();
    SurfaceAssetLoader.Factory surfaceAssetLoaderFactory =
        new SurfaceAssetLoader.Factory(componentListener);
    surfaceAssetLoader =
        surfaceAssetLoaderFactory.createAssetLoader(
            editedMediaItem, looper, componentListener, compositionSettings);
    surfaceAssetLoader.setContentFormat(screenCaptureFormat);
  }

  private void startCapture(Surface surface) {
    mediaProjection.registerCallback(
        new MediaProjection.Callback() {
          @Override
          public void onStop() {
            super.onStop();
            if (virtualDisplay != null) {
              virtualDisplay.setSurface(null);
              virtualDisplay.release();
            }
            surfaceAssetLoader.signalEndOfInput();
          }
        },
        new Handler());

    virtualDisplay =
        checkNotNull(mediaProjection)
            .createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                checkNotNull(screenCaptureFormat).width,
                screenCaptureFormat.height,
                densityDpi,
                VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                /* callback= */ null,
                /* handler= */ null);
  }

  @Override
  public void start() {
    surfaceAssetLoader.start();
  }

  @Override
  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    return surfaceAssetLoader.getProgress(progressHolder);
  }

  @Override
  public ImmutableMap<Integer, String> getDecoderNames() {
    return surfaceAssetLoader.getDecoderNames();
  }

  @Override
  public void release() {
    surfaceAssetLoader.release();
  }

  private final class ComponentListener
      implements SurfaceAssetLoader.Callback, AssetLoader.Listener {

    // SurfaceAssetLoader.Callback

    @Override
    public void onSurfaceAssetLoaderCreated(SurfaceAssetLoader surfaceAssetLoader) {}

    @Override
    public void onSurfaceReady(Surface surface, EditedMediaItem editedMediaItem) {
      handler.post(() -> startCapture(surface));
    }

    // AssetLoader.Listener

    @Override
    public void onDurationUs(long durationUs) {}

    @Override
    public void onTrackCount(int trackCount) {}

    @Override
    public boolean onTrackAdded(
        Format inputFormat, @SupportedOutputTypes int supportedOutputTypes) {
      if (MimeTypes.isVideo(inputFormat.sampleMimeType)) {
        videoFormat = inputFormat;
        listener.onDurationUs(C.TIME_UNSET);
        listener.onTrackCount(1);
        listener.onTrackAdded(inputFormat, SUPPORTED_OUTPUT_TYPE_DECODED);
      }
      return true;
    }

    @Nullable
    @Override
    public SampleConsumer onOutputFormat(Format format) throws ExportException {
      if (videoFormat == null) {
        return null;
      }
      return listener.onOutputFormat(format);
    }

    @Override
    public void onError(ExportException exportException) {
      listener.onError(exportException);
    }
  }
}
