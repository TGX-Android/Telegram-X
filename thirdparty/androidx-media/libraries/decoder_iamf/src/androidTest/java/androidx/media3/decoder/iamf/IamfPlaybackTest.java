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
package androidx.media3.decoder.iamf;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.Spatializer;
import android.net.Uri;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.test.utils.CapturingAudioSink;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Playback tests using {@link LibiamfAudioRenderer}. */
@RunWith(AndroidJUnit4.class)
public class IamfPlaybackTest {
  private static final String IAMF_SAMPLE = "mp4/sample_iamf.mp4";

  @Before
  public void setUp() {
    assertWithMessage("Iamf library not available").that(IamfLibrary.isAvailable()).isTrue();
  }

  @Test
  public void playIamf() throws Exception {
    playAndAssertAudioSinkOutput(IAMF_SAMPLE);
  }

  private static void playAndAssertAudioSinkOutput(String fileName) throws Exception {
    CapturingAudioSink audioSink =
        new CapturingAudioSink(
            new DefaultAudioSink.Builder(ApplicationProvider.getApplicationContext()).build());

    TestPlaybackRunnable testPlaybackRunnable =
        new TestPlaybackRunnable(
            Uri.parse("asset:///media/" + fileName),
            ApplicationProvider.getApplicationContext(),
            audioSink);
    Thread thread = new Thread(testPlaybackRunnable);
    thread.start();
    thread.join();
    if (testPlaybackRunnable.playbackException != null) {
      throw testPlaybackRunnable.playbackException;
    }
    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        audioSink,
        "audiosinkdumps/" + fileName + ".audiosink.dump");
  }

  private static class TestPlaybackRunnable implements Player.Listener, Runnable {

    private final Context context;
    private final Uri uri;
    private final AudioSink audioSink;

    @Nullable private ExoPlayer player;
    @Nullable private PlaybackException playbackException;

    public TestPlaybackRunnable(Uri uri, Context context, AudioSink audioSink) {
      this.uri = uri;
      this.context = context;
      this.audioSink = audioSink;
    }

    @Override
    public void run() {
      Looper.prepare();
      if (Util.SDK_INT >= 32) { // Spatializer is only available on API 32 and above.
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        // Lint can't follow the indirection from AudioFormat.ENCODING_PCM_16BIT to
        // IamfDecoder.OUTPUT_PCM_ENCODING.
        @SuppressWarnings("WrongConstant")
        AudioFormat.Builder audioFormat =
            new AudioFormat.Builder()
                .setEncoding(IamfDecoder.OUTPUT_PCM_ENCODING)
                .setChannelMask(IamfDecoder.SPATIALIZED_OUTPUT_LAYOUT);
        if (audioManager != null) {
          Spatializer spatializer = audioManager.getSpatializer();
          assertWithMessage("Spatializer must be disabled to run this test.")
              .that(
                  spatializer.getImmersiveAudioLevel()
                          != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
                      && spatializer.isAvailable()
                      && spatializer.isEnabled()
                      && spatializer.canBeSpatialized(
                          AudioAttributes.DEFAULT.getAudioAttributesV21().audioAttributes,
                          audioFormat.build()))
              .isFalse();
        }
      }
      RenderersFactory renderersFactory =
          (eventHandler,
              videoRendererEventListener,
              audioRendererEventListener,
              textRendererOutput,
              metadataRendererOutput) ->
              new Renderer[] {
                new LibiamfAudioRenderer(
                    context, eventHandler, audioRendererEventListener, audioSink)
              };
      player = new ExoPlayer.Builder(context, renderersFactory).build();
      player.addListener(this);
      MediaSource mediaSource =
          new ProgressiveMediaSource.Factory(
                  new DefaultDataSource.Factory(context),
                  Mp4Extractor.newFactory(new DefaultSubtitleParserFactory()))
              .createMediaSource(MediaItem.fromUri(uri));
      player.setMediaSource(mediaSource);
      player.prepare();
      player.play();
      Looper.loop();
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      playbackException = error;
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
      if (playbackState == Player.STATE_ENDED
          || (playbackState == Player.STATE_IDLE && playbackException != null)) {
        player.release();
        Looper.myLooper().quit();
      }
    }
  }
}
