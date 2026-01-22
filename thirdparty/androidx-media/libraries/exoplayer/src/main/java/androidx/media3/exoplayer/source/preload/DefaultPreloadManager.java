/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.source.preload;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.Math.abs;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRendererCapabilitiesList;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.PlaybackLooperProvider;
import androidx.media3.exoplayer.RendererCapabilitiesList;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.SampleQueue;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.BandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Comparator;

/**
 * A preload manager that preloads with the {@link PreloadMediaSource} to load the media data into
 * the {@link SampleQueue}.
 */
@UnstableApi
public final class DefaultPreloadManager extends BasePreloadManager<Integer> {

  /** A builder for {@link DefaultPreloadManager} instances. */
  public static final class Builder extends BuilderBase<Integer> {

    private final Context context;
    private PlaybackLooperProvider preloadLooperProvider;
    private TrackSelector.Factory trackSelectorFactory;
    private Supplier<BandwidthMeter> bandwidthMeterSupplier;
    private Supplier<RenderersFactory> renderersFactorySupplier;
    private Supplier<LoadControl> loadControlSupplier;
    private boolean buildCalled;
    private boolean buildExoPlayerCalled;

    /**
     * Creates a builder.
     *
     * @param context A {@link Context}.
     * @param targetPreloadStatusControl A {@link TargetPreloadStatusControl<Integer>}.
     */
    public Builder(
        Context context, TargetPreloadStatusControl<Integer> targetPreloadStatusControl) {
      super(
          new RankingDataComparator(),
          targetPreloadStatusControl,
          Suppliers.memoize(() -> new DefaultMediaSourceFactory(context)));
      this.context = context;
      this.preloadLooperProvider = new PlaybackLooperProvider();
      this.trackSelectorFactory = DefaultTrackSelector::new;
      this.bandwidthMeterSupplier = () -> DefaultBandwidthMeter.getSingletonInstance(context);
      this.renderersFactorySupplier = Suppliers.memoize(() -> new DefaultRenderersFactory(context));
      this.loadControlSupplier = Suppliers.memoize(DefaultLoadControl::new);
    }

    /**
     * Sets the {@link MediaSource.Factory} that will be used by the built {@link
     * DefaultPreloadManager} and {@link ExoPlayer}.
     *
     * <p>The default is a {@link DefaultMediaSourceFactory}.
     *
     * @param mediaSourceFactory A {@link MediaSource.Factory}
     * @return This builder.
     * @throws IllegalStateException If {@link #build()}, {@link #buildExoPlayer()} or {@link
     *     #buildExoPlayer(ExoPlayer.Builder)} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setMediaSourceFactory(MediaSource.Factory mediaSourceFactory) {
      checkState(!buildCalled && !buildExoPlayerCalled);
      this.mediaSourceFactorySupplier = () -> mediaSourceFactory;
      return this;
    }

    /**
     * Sets the {@link RenderersFactory} that will be used by the built {@link
     * DefaultPreloadManager} and {@link ExoPlayer}.
     *
     * <p>The default is a {@link DefaultRenderersFactory}.
     *
     * @param renderersFactory A {@link RenderersFactory}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()}, {@link #buildExoPlayer()} or {@link
     *     #buildExoPlayer(ExoPlayer.Builder)} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setRenderersFactory(RenderersFactory renderersFactory) {
      checkState(!buildCalled && !buildExoPlayerCalled);
      this.renderersFactorySupplier = () -> renderersFactory;
      return this;
    }

    /**
     * Sets the {@link TrackSelector.Factory} that will be used by the built {@link
     * DefaultPreloadManager} and {@link ExoPlayer}.
     *
     * <p>The default is a {@link TrackSelector.Factory} that always creates a new {@link
     * DefaultTrackSelector}.
     *
     * @param trackSelectorFactory A {@link TrackSelector.Factory}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()}, {@link #buildExoPlayer()} or {@link
     *     #buildExoPlayer(ExoPlayer.Builder)} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setTrackSelectorFactory(TrackSelector.Factory trackSelectorFactory) {
      checkState(!buildCalled && !buildExoPlayerCalled);
      this.trackSelectorFactory = trackSelectorFactory;
      return this;
    }

    /**
     * Sets the {@link LoadControl} that will be used by the built {@link DefaultPreloadManager} and
     * {@link ExoPlayer}.
     *
     * <p>The default is a {@link DefaultLoadControl}.
     *
     * @param loadControl A {@link LoadControl}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()}, {@link #buildExoPlayer()} or {@link
     *     #buildExoPlayer(ExoPlayer.Builder)} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setLoadControl(LoadControl loadControl) {
      checkState(!buildCalled && !buildExoPlayerCalled);
      this.loadControlSupplier = () -> loadControl;
      return this;
    }

    /**
     * Sets the {@link BandwidthMeter} that will be used by the built {@link DefaultPreloadManager}
     * and {@link ExoPlayer}.
     *
     * <p>The default is a {@link DefaultBandwidthMeter}.
     *
     * @param bandwidthMeter A {@link BandwidthMeter}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()}, {@link #buildExoPlayer()} or {@link
     *     #buildExoPlayer(ExoPlayer.Builder)} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setBandwidthMeter(BandwidthMeter bandwidthMeter) {
      checkState(!buildCalled && !buildExoPlayerCalled);
      this.bandwidthMeterSupplier = () -> bandwidthMeter;
      return this;
    }

    /**
     * Sets the {@link Looper} that will be used for preload and playback.
     *
     * <p>The backing thread should run with priority {@link Process#THREAD_PRIORITY_AUDIO} and
     * should handle messages within 10ms.
     *
     * <p>The default is a looper that is associated with a new thread created internally.
     *
     * @param preloadLooper A {@link Looper}.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()}, {@link #buildExoPlayer()} or {@link
     *     #buildExoPlayer(ExoPlayer.Builder)} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder setPreloadLooper(Looper preloadLooper) {
      checkState(!buildCalled && !buildExoPlayerCalled);
      this.preloadLooperProvider = new PlaybackLooperProvider(preloadLooper);
      return this;
    }

    /**
     * Builds an {@link ExoPlayer}.
     *
     * <p>See {@link #buildExoPlayer(ExoPlayer.Builder)} for the list of values populated on and
     * resulting from this builder that the built {@link ExoPlayer} uses.
     *
     * <p>For the other configurations than above, the built {@link ExoPlayer} uses the default
     * values, see {@link ExoPlayer.Builder#Builder(Context)} for the list of default values.
     *
     * @return An {@link ExoPlayer} instance.
     */
    public ExoPlayer buildExoPlayer() {
      return buildExoPlayer(new ExoPlayer.Builder(context));
    }

    /**
     * Builds an {@link ExoPlayer} with an {@link ExoPlayer.Builder} passed in.
     *
     * <p>The built {@link ExoPlayer} uses the following values populated on and resulting from this
     * builder:
     *
     * <ul>
     *   <li>{@link #setMediaSourceFactory(MediaSource.Factory) MediaSource.Factory}
     *   <li>{@link #setRenderersFactory(RenderersFactory) RenderersFactory}
     *   <li>{@link #setTrackSelectorFactory(TrackSelector.Factory) TrackSelector.Factory}
     *   <li>{@link #setLoadControl(LoadControl) LoadControl}
     *   <li>{@link #setBandwidthMeter(BandwidthMeter) BandwidthMeter}
     *   <li>{@linkplain #setPreloadLooper(Looper)} preload looper}
     * </ul>
     *
     * <p>For the other configurations than above, the built {@link ExoPlayer} uses the values from
     * the passed {@link ExoPlayer.Builder}.
     *
     * @param exoPlayerBuilder An {@link ExoPlayer.Builder} that is used to build the {@link
     *     ExoPlayer}.
     * @return An {@link ExoPlayer} instance.
     */
    public ExoPlayer buildExoPlayer(ExoPlayer.Builder exoPlayerBuilder) {
      buildExoPlayerCalled = true;
      return exoPlayerBuilder
          .setMediaSourceFactory(mediaSourceFactorySupplier.get())
          .setBandwidthMeter(bandwidthMeterSupplier.get())
          .setRenderersFactory(renderersFactorySupplier.get())
          .setLoadControl(loadControlSupplier.get())
          .setPlaybackLooperProvider(preloadLooperProvider)
          .setTrackSelector(trackSelectorFactory.createTrackSelector(context))
          .build();
    }

    /**
     * Builds a {@link DefaultPreloadManager} instance.
     *
     * @throws IllegalStateException If this method has already been called.
     */
    @Override
    public DefaultPreloadManager build() {
      checkState(!buildCalled);
      buildCalled = true;
      return new DefaultPreloadManager(this);
    }
  }

  /**
   * An implementation of {@link TargetPreloadStatusControl.PreloadStatus} that describes the
   * preload status of the {@link PreloadMediaSource}.
   */
  public static class Status implements TargetPreloadStatusControl.PreloadStatus {

    /**
     * Stages for the preload status. One of {@link #STAGE_SOURCE_PREPARED}, {@link
     * #STAGE_TRACKS_SELECTED} or {@link #STAGE_LOADED_FOR_DURATION_MS}.
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef(
        value = {
          STAGE_SOURCE_PREPARED,
          STAGE_TRACKS_SELECTED,
          STAGE_LOADED_FOR_DURATION_MS,
        })
    public @interface Stage {}

    /** The {@link PreloadMediaSource} has completed preparation. */
    public static final int STAGE_SOURCE_PREPARED = 0;

    /** The {@link PreloadMediaSource} has tracks selected. */
    public static final int STAGE_TRACKS_SELECTED = 1;

    /**
     * The {@link PreloadMediaSource} is loaded for a specific duration from the default start
     * position, in milliseconds.
     */
    public static final int STAGE_LOADED_FOR_DURATION_MS = 2;

    private final @Stage int stage;
    private final long value;

    public Status(@Stage int stage, long value) {
      this.stage = stage;
      this.value = value;
    }

    public Status(@Stage int stage) {
      this(stage, C.TIME_UNSET);
    }

    @Override
    public @Stage int getStage() {
      return stage;
    }

    @Override
    public long getValue() {
      return value;
    }
  }

  private final RendererCapabilitiesList rendererCapabilitiesList;
  private final TrackSelector trackSelector;
  private final PlaybackLooperProvider preloadLooperProvider;
  private final PreloadMediaSource.Factory preloadMediaSourceFactory;
  private final Handler preloadHandler;
  private final boolean deprecatedConstructorCalled;

  private DefaultPreloadManager(Builder builder) {
    super(
        new RankingDataComparator(),
        builder.targetPreloadStatusControl,
        builder.mediaSourceFactorySupplier.get());
    rendererCapabilitiesList =
        new DefaultRendererCapabilitiesList.Factory(builder.renderersFactorySupplier.get())
            .createRendererCapabilitiesList();
    preloadLooperProvider = builder.preloadLooperProvider;
    trackSelector = builder.trackSelectorFactory.createTrackSelector(builder.context);
    BandwidthMeter bandwidthMeter = builder.bandwidthMeterSupplier.get();
    trackSelector.init(() -> {}, bandwidthMeter);
    Looper preloadLooper = preloadLooperProvider.obtainLooper();
    preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            builder.mediaSourceFactorySupplier.get(),
            new SourcePreloadControl(),
            trackSelector,
            bandwidthMeter,
            rendererCapabilitiesList.getRendererCapabilities(),
            builder.loadControlSupplier.get().getAllocator(),
            preloadLooper);
    preloadHandler = Util.createHandler(preloadLooper, /* callback= */ null);
    deprecatedConstructorCalled = false;
  }

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public DefaultPreloadManager(
      TargetPreloadStatusControl<Integer> targetPreloadStatusControl,
      MediaSource.Factory mediaSourceFactory,
      TrackSelector trackSelector,
      BandwidthMeter bandwidthMeter,
      RendererCapabilitiesList.Factory rendererCapabilitiesListFactory,
      Allocator allocator,
      Looper preloadLooper) {
    super(new RankingDataComparator(), targetPreloadStatusControl, mediaSourceFactory);
    this.rendererCapabilitiesList =
        rendererCapabilitiesListFactory.createRendererCapabilitiesList();
    this.preloadLooperProvider = new PlaybackLooperProvider(preloadLooper);
    this.trackSelector = trackSelector;
    Looper obtainedPreloadLooper = preloadLooperProvider.obtainLooper();
    preloadMediaSourceFactory =
        new PreloadMediaSource.Factory(
            mediaSourceFactory,
            new SourcePreloadControl(),
            trackSelector,
            bandwidthMeter,
            rendererCapabilitiesList.getRendererCapabilities(),
            allocator,
            obtainedPreloadLooper);
    preloadHandler = Util.createHandler(obtainedPreloadLooper, /* callback= */ null);
    deprecatedConstructorCalled = true;
  }

  /**
   * Sets the index of the current playing media.
   *
   * @param currentPlayingIndex The index of current playing media.
   */
  public void setCurrentPlayingIndex(int currentPlayingIndex) {
    RankingDataComparator rankingDataComparator =
        (RankingDataComparator) this.rankingDataComparator;
    rankingDataComparator.currentPlayingIndex = currentPlayingIndex;
  }

  @Override
  public MediaSource createMediaSourceForPreloading(MediaSource mediaSource) {
    return preloadMediaSourceFactory.createMediaSource(mediaSource);
  }

  @Override
  protected void preloadSourceInternal(MediaSource mediaSource, long startPositionsUs) {
    checkArgument(mediaSource instanceof PreloadMediaSource);
    ((PreloadMediaSource) mediaSource).preload(startPositionsUs);
  }

  @Override
  protected void clearSourceInternal(MediaSource mediaSource) {
    checkArgument(mediaSource instanceof PreloadMediaSource);
    ((PreloadMediaSource) mediaSource).clear();
  }

  @Override
  protected void releaseSourceInternal(MediaSource mediaSource) {
    checkArgument(mediaSource instanceof PreloadMediaSource);
    ((PreloadMediaSource) mediaSource).releasePreloadMediaSource();
  }

  @Override
  protected void releaseInternal() {
    preloadHandler.post(
        () -> {
          rendererCapabilitiesList.release();
          if (!deprecatedConstructorCalled) {
            // TODO: Remove the property deprecatedConstructorCalled and release the TrackSelector
            // anyway after the deprecated constructor is removed.
            trackSelector.release();
          }
          preloadLooperProvider.releaseLooper();
        });
  }

  private static final class RankingDataComparator implements Comparator<Integer> {

    public int currentPlayingIndex;

    public RankingDataComparator() {
      this.currentPlayingIndex = C.INDEX_UNSET;
    }

    @Override
    public int compare(Integer o1, Integer o2) {
      return Integer.compare(abs(o1 - currentPlayingIndex), abs(o2 - currentPlayingIndex));
    }
  }

  private final class SourcePreloadControl implements PreloadMediaSource.PreloadControl {
    @Override
    public boolean onSourcePrepared(PreloadMediaSource mediaSource) {
      // The PreloadMediaSource may have more data preloaded than the target preload status if it
      // has been preloaded before, thus we set `clearExceededDataFromTargetPreloadStatus` to
      // `true` to clear the exceeded data.
      return continueOrCompletePreloading(
          mediaSource,
          /* continueLoadingPredicate= */ status ->
              status.getStage() > Status.STAGE_SOURCE_PREPARED,
          /* clearExceededDataFromTargetPreloadStatus= */ true);
    }

    @Override
    public boolean onTracksSelected(PreloadMediaSource mediaSource) {
      // Set `clearExceededDataFromTargetPreloadStatus` to `false` as clearing the exceeded data
      // from the status STAGE_TRACKS_SELECTED is not supported.
      return continueOrCompletePreloading(
          mediaSource,
          /* continueLoadingPredicate= */ status ->
              status.getStage() > Status.STAGE_TRACKS_SELECTED,
          /* clearExceededDataFromTargetPreloadStatus= */ false);
    }

    @Override
    public boolean onContinueLoadingRequested(
        PreloadMediaSource mediaSource, long bufferedDurationUs) {
      // Set `clearExceededDataFromTargetPreloadStatus` to `false` as clearing the exceeded data
      // from the status STAGE_LOADED_FOR_DURATION_MS is not supported.
      return continueOrCompletePreloading(
          mediaSource,
          /* continueLoadingPredicate= */ status ->
              status.getStage() == Status.STAGE_LOADED_FOR_DURATION_MS
                  && status.getValue() > Util.usToMs(bufferedDurationUs),
          /* clearExceededDataFromTargetPreloadStatus= */ false);
    }

    @Override
    public void onUsedByPlayer(PreloadMediaSource mediaSource) {
      DefaultPreloadManager.this.onPreloadSkipped(mediaSource);
    }

    @Override
    public void onLoadedToTheEndOfSource(PreloadMediaSource mediaSource) {
      DefaultPreloadManager.this.onPreloadCompleted(mediaSource);
    }

    @Override
    public void onPreloadError(PreloadException error, PreloadMediaSource mediaSource) {
      DefaultPreloadManager.this.onPreloadError(error, mediaSource);
    }

    private boolean continueOrCompletePreloading(
        PreloadMediaSource mediaSource,
        Predicate<Status> continueLoadingPredicate,
        boolean clearExceededDataFromTargetPreloadStatus) {
      @Nullable
      TargetPreloadStatusControl.PreloadStatus targetPreloadStatus =
          getTargetPreloadStatus(mediaSource);
      if (targetPreloadStatus != null) {
        Status status = (Status) targetPreloadStatus;
        if (continueLoadingPredicate.apply(checkNotNull(status))) {
          return true;
        }
        if (clearExceededDataFromTargetPreloadStatus) {
          clearSourceInternal(mediaSource);
        }
        DefaultPreloadManager.this.onPreloadCompleted(mediaSource);
      } else {
        DefaultPreloadManager.this.onPreloadSkipped(mediaSource);
      }
      return false;
    }
  }
}
