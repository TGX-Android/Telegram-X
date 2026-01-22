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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.transformer.ExportException.ERROR_CODE_UNSPECIFIED;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableMap;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Asset loader that outputs video data passed to its input {@link Surface}.
 *
 * <p>To use this asset loader, pass a callback to the {@linkplain SurfaceAssetLoader.Factory
 * factory's} constructor to get access to the underlying asset loader and {@link Surface} to write
 * to once they are ready. Then pass the factory to {@link
 * Transformer.Builder#setAssetLoaderFactory(AssetLoader.Factory)}.
 *
 * <p>The media item passed to transformer must have a URI starting with the scheme {@link
 * #MEDIA_ITEM_URI_SCHEME}.
 *
 * <p>Call {@link #signalEndOfInput()} when the input stream ends, which will cause the
 * transformation to complete.
 */
@UnstableApi
public final class SurfaceAssetLoader implements AssetLoader {

  /**
   * URI scheme for creating a {@link MediaItem} that signals that the media is provided from this
   * asset loader.
   */
  public static final String MEDIA_ITEM_URI_SCHEME = "transformer_surface_asset";

  /** Callbacks for {@link SurfaceAssetLoader} events. */
  public interface Callback {
    /**
     * Called when the asset loader has been created. Pass the {@linkplain #setContentFormat(Format)
     * content format} to the provided asset loader to trigger surface creation. May be called on
     * any thread.
     */
    void onSurfaceAssetLoaderCreated(SurfaceAssetLoader surfaceAssetLoader);

    /**
     * Called when the input surface is ready to write to. May be called on any thread.
     *
     * @param surface The {@link Surface} to write to.
     * @param editedMediaItem The {@link EditedMediaItem} used to create the associated {@link
     *     SurfaceAssetLoader}.
     */
    void onSurfaceReady(Surface surface, EditedMediaItem editedMediaItem);
  }

  /** Factory for {@link SurfaceAssetLoader} instances. */
  public static final class Factory implements AssetLoader.Factory {

    private final Callback callback;

    /** Creates a factory with the specified callback. */
    public Factory(Callback callback) {
      this.callback = callback;
    }

    @Override
    public SurfaceAssetLoader createAssetLoader(
        EditedMediaItem editedMediaItem,
        Looper looper,
        AssetLoader.Listener listener,
        CompositionSettings compositionSettings) {
      Uri uri = checkNotNull(editedMediaItem.mediaItem.localConfiguration).uri;
      checkState(checkNotNull(uri.getScheme()).equals(MEDIA_ITEM_URI_SCHEME));
      SurfaceAssetLoader surfaceAssetLoader =
          new SurfaceAssetLoader(editedMediaItem, looper, listener, callback);
      callback.onSurfaceAssetLoaderCreated(surfaceAssetLoader);
      return surfaceAssetLoader;
    }
  }

  private final EditedMediaItem editedMediaItem;
  private final AssetLoader.Listener listener;
  private final Handler handler;
  private final Callback callback;

  private @Transformer.ProgressState int progressState;

  private boolean isStarted;
  private boolean isVideoEndOfStreamSignaled;
  private @MonotonicNonNull SampleConsumer sampleConsumer;
  private @MonotonicNonNull Format contentFormat;

  private SurfaceAssetLoader(
      EditedMediaItem editedMediaItem,
      Looper looper,
      AssetLoader.Listener listener,
      Callback callback) {
    this.editedMediaItem = editedMediaItem;
    this.listener = listener;
    this.callback = callback;
    handler = new Handler(looper);
    progressState = PROGRESS_STATE_NOT_STARTED;
  }

  /**
   * Sets the video content format, which must have a raw video sample MIME type, width, height and
   * color info. May be called on any thread.
   */
  public void setContentFormat(Format contentFormat) {
    checkArgument(Objects.equals(contentFormat.sampleMimeType, MimeTypes.VIDEO_RAW));
    checkArgument(contentFormat.width != Format.NO_VALUE);
    checkArgument(contentFormat.height != Format.NO_VALUE);
    checkArgument(checkNotNull(contentFormat.colorInfo).isDataSpaceValid());
    handler.post(
        () -> {
          this.contentFormat = contentFormat;
          try {
            maybeFinishPreparation();
          } catch (RuntimeException e) {
            listener.onError(ExportException.createForAssetLoader(e, ERROR_CODE_UNSPECIFIED));
          }
        });
  }

  /** Returns the {@link EditedMediaItem} being loaded by this instance. */
  public EditedMediaItem getEditedMediaItem() {
    return editedMediaItem;
  }

  /** Signals that no further input frames will be rendered. May be called on any thread. */
  public void signalEndOfInput() {
    handler.post(
        () -> {
          try {
            if (!isVideoEndOfStreamSignaled && sampleConsumer != null) {
              isVideoEndOfStreamSignaled = true;
              sampleConsumer.signalEndOfVideoInput();
            }
          } catch (RuntimeException e) {
            listener.onError(ExportException.createForAssetLoader(e, ERROR_CODE_UNSPECIFIED));
          }
        });
  }

  // AssetLoader implementation.

  @Override
  public void start() {
    isStarted = true;
    maybeFinishPreparation();
  }

  @Override
  public @Transformer.ProgressState int getProgress(ProgressHolder progressHolder) {
    return progressState;
  }

  @Override
  public ImmutableMap<Integer, String> getDecoderNames() {
    return ImmutableMap.of();
  }

  @Override
  public void release() {
    // Do nothing.
  }

  private void maybeFinishPreparation() {
    if (!isStarted || contentFormat == null) {
      return;
    }
    listener.onTrackCount(1);
    listener.onDurationUs(C.TIME_UNSET);
    listener.onTrackAdded(contentFormat, SUPPORTED_OUTPUT_TYPE_DECODED);
    try {
      sampleConsumer = checkNotNull(listener.onOutputFormat(contentFormat));
      sampleConsumer.setOnInputSurfaceReadyListener(
          () ->
              callback.onSurfaceReady(
                  checkNotNull(sampleConsumer).getInputSurface(), editedMediaItem));
    } catch (ExportException e) {
      listener.onError(e);
    }
    progressState = Transformer.PROGRESS_STATE_UNAVAILABLE;
  }
}
