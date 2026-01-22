/*
 * Copyright 2022 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.TransformerUtil.isImage;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.transformer.AssetLoader.CompositionSettings;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.Executors;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** The default {@link AssetLoader.Factory} implementation. */
@UnstableApi
public final class DefaultAssetLoaderFactory implements AssetLoader.Factory {

  private static final String TAG = "DefaultAssetLoaderFact";

  // Limit decoded images to 4096x4096 - should be large enough for most image to video
  // transcode operations, and smaller than GL_MAX_TEXTURE_SIZE for most devices.
  // TODO: b/356072337 - Consider reading this from GL_MAX_TEXTURE_SIZE. This requires an
  //   active OpenGL context.
  private static final int MAXIMUM_BITMAP_OUTPUT_DIMENSION = 4096;

  private final Context context;
  private final Codec.DecoderFactory decoderFactory;
  private final Clock clock;
  @Nullable private final MediaSource.Factory mediaSourceFactory;
  private final BitmapLoader bitmapLoader;
  @Nullable private final TrackSelector.Factory trackSelectorFactory;

  private AssetLoader.@MonotonicNonNull Factory imageAssetLoaderFactory;
  private AssetLoader.@MonotonicNonNull Factory exoPlayerAssetLoaderFactory;

  /**
   * Creates an instance.
   *
   * <p>Uses {@link DataSourceBitmapLoader} to load images, setting the {@link
   * android.graphics.BitmapFactory.Options#inPreferredColorSpace} to {@link
   * android.graphics.ColorSpace.Named#SRGB} when possible.
   *
   * @param context The {@link Context}.
   * @param decoderFactory The {@link Codec.DecoderFactory} to use to decode the samples (if
   *     necessary).
   * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
   *     testing.
   */
  public DefaultAssetLoaderFactory(
      Context context, Codec.DecoderFactory decoderFactory, Clock clock) {
    // TODO: b/381519379 - Deprecate this constructor and replace with a builder.
    this.context = context.getApplicationContext();
    this.decoderFactory = decoderFactory;
    this.clock = clock;
    this.mediaSourceFactory = null;
    this.trackSelectorFactory = null;
    @Nullable BitmapFactory.Options options = null;
    if (Util.SDK_INT >= 26) {
      options = new BitmapFactory.Options();
      options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
    }
    this.bitmapLoader =
        new DataSourceBitmapLoader(
            MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
            new DefaultDataSource.Factory(context),
            options,
            MAXIMUM_BITMAP_OUTPUT_DIMENSION);
  }

  /**
   * Creates an instance with the default {@link Clock} and {@link Codec.DecoderFactory}.
   *
   * <p>For multi-picture formats (e.g. gifs), a single image frame from the container is loaded.
   * The frame loaded is determined by the {@link BitmapLoader} implementation.
   *
   * @param context The {@link Context}.
   * @param bitmapLoader The {@link BitmapLoader} to use to load and decode images.
   */
  public DefaultAssetLoaderFactory(Context context, BitmapLoader bitmapLoader) {
    // TODO: b/381519379 - Deprecate this constructor and replace with a builder.
    this.context = context.getApplicationContext();
    this.bitmapLoader = bitmapLoader;
    decoderFactory = new DefaultDecoderFactory.Builder(context).build();
    clock = Clock.DEFAULT;
    mediaSourceFactory = null;
    trackSelectorFactory = null;
  }

  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   * @param decoderFactory The {@link Codec.DecoderFactory} to use to decode the samples (if
   *     necessary).
   * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
   *     testing.
   * @param mediaSourceFactory The {@link MediaSource.Factory} to use to retrieve the samples to
   *     transform when an {@link ExoPlayerAssetLoader} is used.
   * @param bitmapLoader The {@link BitmapLoader} to use to load and decode images.
   */
  public DefaultAssetLoaderFactory(
      Context context,
      Codec.DecoderFactory decoderFactory,
      Clock clock,
      @Nullable MediaSource.Factory mediaSourceFactory,
      BitmapLoader bitmapLoader) {
    // TODO: b/381519379 - Deprecate this constructor and replace with a builder.
    this.context = context.getApplicationContext();
    this.decoderFactory = decoderFactory;
    this.clock = clock;
    this.mediaSourceFactory = mediaSourceFactory;
    this.bitmapLoader = bitmapLoader;
    this.trackSelectorFactory = null;
  }

  /**
   * Creates an instance.
   *
   * @param context The {@link Context}.
   * @param decoderFactory The {@link Codec.DecoderFactory} to use to decode the samples (if
   *     necessary).
   * @param clock The {@link Clock} to use. It should always be {@link Clock#DEFAULT}, except for
   *     testing.
   * @param mediaSourceFactory The {@link MediaSource.Factory} to use to retrieve the samples to
   *     transform when an {@link ExoPlayerAssetLoader} is used.
   * @param bitmapLoader The {@link BitmapLoader} to use to load and decode images.
   * @param trackSelectorFactory The {@link TrackSelector.Factory} to use when selecting the track
   *     to transform.
   */
  public DefaultAssetLoaderFactory(
      Context context,
      Codec.DecoderFactory decoderFactory,
      Clock clock,
      @Nullable MediaSource.Factory mediaSourceFactory,
      BitmapLoader bitmapLoader,
      TrackSelector.Factory trackSelectorFactory) {
    // TODO: b/381519379 - Deprecate this constructor and replace with a builder.
    this.context = context.getApplicationContext();
    this.decoderFactory = decoderFactory;
    this.clock = clock;
    this.mediaSourceFactory = mediaSourceFactory;
    this.bitmapLoader = bitmapLoader;
    this.trackSelectorFactory = trackSelectorFactory;
  }

  @Override
  public AssetLoader createAssetLoader(
      EditedMediaItem editedMediaItem,
      Looper looper,
      AssetLoader.Listener listener,
      CompositionSettings compositionSettings) {
    MediaItem mediaItem = editedMediaItem.mediaItem;
    boolean isImage = isImage(context, mediaItem);
    // TODO: b/350499931 - Use the MediaItem's imageDurationMs instead of the EditedMediaItem's
    //  durationUs to export motion photos as video
    boolean exportVideoFromMotionPhoto = isImage && editedMediaItem.durationUs == C.TIME_UNSET;
    if (isImage && !exportVideoFromMotionPhoto) {
      if (checkNotNull(mediaItem.localConfiguration).imageDurationMs == C.TIME_UNSET) {
        Log.w(TAG, "The imageDurationMs field must be set on image MediaItems.");
      }
      if (imageAssetLoaderFactory == null) {
        imageAssetLoaderFactory = new ImageAssetLoader.Factory(context, bitmapLoader);
      }
      return imageAssetLoaderFactory.createAssetLoader(
          editedMediaItem, looper, listener, compositionSettings);
    }
    if (exoPlayerAssetLoaderFactory == null) {
      exoPlayerAssetLoaderFactory =
          new ExoPlayerAssetLoader.Factory(
              context, decoderFactory, clock, mediaSourceFactory, trackSelectorFactory);
    }
    return exoPlayerAssetLoaderFactory.createAssetLoader(
        editedMediaItem, looper, listener, compositionSettings);
  }
}
