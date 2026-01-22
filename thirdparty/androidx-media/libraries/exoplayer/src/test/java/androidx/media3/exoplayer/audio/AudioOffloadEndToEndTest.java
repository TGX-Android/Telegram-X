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
package androidx.media3.exoplayer.audio;

import static androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ExoPlayer.AudioOffloadListener;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAudioSystem;

@RunWith(AndroidJUnit4.class)
public class AudioOffloadEndToEndTest {
  private static final AudioFormat AUDIO_FORMAT =
      new AudioFormat.Builder()
          .setSampleRate(48_000)
          .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
          .setEncoding(AudioFormat.ENCODING_OPUS)
          .build();
  private static final AudioAttributes AUDIO_ATTRIBUTES =
      new AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
          .build();
  private static final String OPUS_FILE_URI = "asset:///media/ogg/bear.opus";

  private final Context applicationContext = getApplicationContext();

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Before
  public void setup() {
    ShadowAudioSystem.setOffloadSupported(AUDIO_FORMAT, AUDIO_ATTRIBUTES, true);
    ShadowAudioSystem.setOffloadPlaybackSupport(
        AUDIO_FORMAT, AUDIO_ATTRIBUTES, AudioManager.PLAYBACK_OFFLOAD_SUPPORTED);
    ShadowAudioSystem.setDirectPlaybackSupport(
        AUDIO_FORMAT, AUDIO_ATTRIBUTES, AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED);
  }

  @Test
  @Config(minSdk = 30)
  public void testOffloadPlayback_offloadEnabledAndPlayToEnd() throws Exception {
    AtomicBoolean isOffloadModeSet = new AtomicBoolean(false);
    DefaultRenderersFactory renderersFactory =
        new DefaultRenderersFactory(applicationContext) {
          @Override
          protected AudioSink buildAudioSink(
              Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams) {
            AudioOffloadListener audioOffloadListener =
                new AudioOffloadListener() {
                  @Override
                  public void onOffloadedPlayback(boolean offloadedPlayback) {
                    isOffloadModeSet.set(offloadedPlayback);
                  }
                };
            return new DefaultAudioSink.Builder(applicationContext)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setExperimentalAudioOffloadListener(audioOffloadListener)
                .build();
          }
        };
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setTrackSelectionParameters(
        player
            .getTrackSelectionParameters()
            .buildUpon()
            .setAudioOffloadPreferences(
                new AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(AUDIO_OFFLOAD_MODE_ENABLED)
                    .build())
            .build());
    player.setMediaItem(MediaItem.fromUri(OPUS_FILE_URI));
    player.prepare();

    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    assertThat(isOffloadModeSet.get()).isTrue();

    player.release();
  }
}
