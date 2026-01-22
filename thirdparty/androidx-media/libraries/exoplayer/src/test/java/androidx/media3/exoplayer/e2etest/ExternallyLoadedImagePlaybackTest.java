/*
 * Copyright (C) 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.e2etest;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.annotation.GraphicsMode.Mode.NATIVE;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.datasource.AssetDataSource;
import androidx.media3.datasource.DataSourceUtil;
import androidx.media3.datasource.DataSpec;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.image.ExternallyLoadedImageDecoder;
import androidx.media3.exoplayer.image.ImageDecoderException;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.test.utils.CapturingRenderersFactory;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.robolectric.PlaybackOutput;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.GraphicsMode;

/** End-to-end tests using image content loaded from an injected image management framework. */
@RunWith(AndroidJUnit4.class)
@GraphicsMode(value = NATIVE)
public final class ExternallyLoadedImagePlaybackTest {

  private static final String INPUT_FILE = "png/non-motion-photo-shortened.png";

  @Test
  public void imagePlayback_validExternalLoader_callsLoadOnceAndPlaysSuccessfully()
      throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    ListeningExecutorService listeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    CapturingRenderersFactory renderersFactory =
        new CapturingRenderersFactory(applicationContext)
            .setImageDecoderFactory(
                new ExternallyLoadedImageDecoder.Factory(
                    request -> listeningExecutorService.submit(() -> decode(request.uri))));
    Clock clock = new FakeClock(/* isAutoAdvancing= */ true);
    ArrayList<Uri> externalLoaderUris = new ArrayList<>();
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(applicationContext)
            .setExternalImageLoader(
                request ->
                    listeningExecutorService.submit(() -> externalLoaderUris.add(request.uri)));
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(clock)
            .setMediaSourceFactory(mediaSourceFactory)
            .build();
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, renderersFactory);
    long durationMs = 5 * C.MILLIS_PER_SECOND;
    Uri uri = Uri.parse("asset:///media/" + INPUT_FILE);
    player.setMediaItem(
        new MediaItem.Builder()
            .setUri(uri)
            .setImageDurationMs(durationMs)
            .setMimeType(MimeTypes.APPLICATION_EXTERNALLY_LOADED_IMAGE)
            .build());
    player.prepare();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    long playerStartedMs = clock.elapsedRealtime();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    long playbackDurationMs = clock.elapsedRealtime() - playerStartedMs;
    player.release();

    assertThat(externalLoaderUris).containsExactly(uri);
    assertThat(playbackDurationMs).isAtLeast(durationMs);
    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/" + INPUT_FILE + ".dump");
  }

  @Test
  public void imagePlayback_externalLoaderFutureFails_propagatesFailure() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    ListeningExecutorService listeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    CapturingRenderersFactory renderersFactory =
        new CapturingRenderersFactory(applicationContext)
            .setImageDecoderFactory(
                new ExternallyLoadedImageDecoder.Factory(
                    request -> listeningExecutorService.submit(() -> decode(request.uri))));
    RuntimeException exception = new RuntimeException("My Exception");
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(applicationContext)
            .setExternalImageLoader(
                unused ->
                    listeningExecutorService.submit(
                        () -> {
                          throw exception;
                        }));
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMediaSourceFactory(mediaSourceFactory)
            .build();
    player.setMediaItem(
        new MediaItem.Builder()
            .setUri("asset:///media/" + INPUT_FILE)
            .setImageDurationMs(5 * C.MILLIS_PER_SECOND)
            .setMimeType(MimeTypes.APPLICATION_EXTERNALLY_LOADED_IMAGE)
            .build());
    player.prepare();

    ExoPlaybackException error = TestPlayerRunHelper.runUntilError(player);

    assertThat(error.errorCode).isEqualTo(PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
    assertThat(error.getSourceException()).hasCauseThat().isEqualTo(exception);
  }

  @Test
  public void imagePlayback_loadingCompletedWhenFutureCompletes() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    ListeningExecutorService listeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
    CapturingRenderersFactory renderersFactory =
        new CapturingRenderersFactory(applicationContext)
            .setImageDecoderFactory(
                new ExternallyLoadedImageDecoder.Factory(
                    request -> listeningExecutorService.submit(() -> decode(request.uri))));
    ConditionVariable loadingComplete = new ConditionVariable();
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory(applicationContext)
            .setExternalImageLoader(
                unused -> listeningExecutorService.submit(loadingComplete::blockUninterruptible));
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setMediaSourceFactory(mediaSourceFactory)
            .build();
    player.setMediaItem(
        new MediaItem.Builder()
            .setUri("asset:///media/" + INPUT_FILE)
            .setImageDurationMs(5 * C.MILLIS_PER_SECOND)
            .setMimeType(MimeTypes.APPLICATION_EXTERNALLY_LOADED_IMAGE)
            .build());
    player.prepare();

    TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player);

    assertThat(player.isLoading()).isTrue();

    loadingComplete.open();
    // Assert the player stops loading.
    TestPlayerRunHelper.runUntilIsLoading(player, /* expectedIsLoading= */ false);
  }

  private static Bitmap decode(Uri uri) throws ImageDecoderException {
    AssetDataSource assetDataSource =
        new AssetDataSource(ApplicationProvider.getApplicationContext());
    DataSpec dataSpec = new DataSpec(uri);
    @Nullable Bitmap bitmap;

    try {
      assetDataSource.open(dataSpec);
      byte[] imageData = DataSourceUtil.readToEnd(assetDataSource);
      bitmap = BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, imageData.length);
    } catch (IOException e) {
      throw new ImageDecoderException(e);
    }
    if (bitmap == null) {
      throw new ImageDecoderException(
          "Could not decode image data with BitmapFactory. uriString decoded from data = " + uri);
    }
    return bitmap;
  }
}
