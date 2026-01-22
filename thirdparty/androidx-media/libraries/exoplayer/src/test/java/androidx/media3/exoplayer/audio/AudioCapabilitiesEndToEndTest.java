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

import static android.media.AudioFormat.CHANNEL_OUT_5POINT1;
import static android.media.AudioFormat.CHANNEL_OUT_STEREO;
import static android.media.AudioFormat.ENCODING_AC3;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioProfile;
import android.media.MediaCodec;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.Tracks.Group;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.SynchronousMediaCodecAdapter;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.AudioDeviceInfoBuilder;
import org.robolectric.shadows.AudioProfileBuilder;
import org.robolectric.shadows.ShadowAudioTrack;
import org.robolectric.shadows.ShadowUIModeManager;

/** End to end playback test for audio capabilities. */
@RunWith(AndroidJUnit4.class)
public class AudioCapabilitiesEndToEndTest {

  private Context applicationContext;
  private AudioManager audioManager;
  private Parameters defaultParameters;
  @Nullable private AudioDeviceInfo directPlaybackDevice;
  private List<Tracks> selectedTracks;
  private List<String> analyticsListenerReceivedCallbacks;

  @Rule
  public ShadowMediaCodecConfig shadowMediaCodecConfig =
      ShadowMediaCodecConfig.withNoDefaultSupportedMimeTypes();

  @Before
  public void setUp() {
    applicationContext = ApplicationProvider.getApplicationContext();
    audioManager = (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE);
    shadowOf(audioManager).setOutputDevices(ImmutableList.of());
    defaultParameters = Parameters.DEFAULT;
    selectedTracks = new ArrayList<>();
    analyticsListenerReceivedCallbacks = new ArrayList<>();
  }

  @After
  public void tearDown() {
    clearDirectPlaybackSupport();
  }

  /**
   * Tests that ExoPlayer recovers from AudioTrack recoverable error.
   *
   * <p>The test starts playing of AC3 audio via audio passthrough (direct playback). Mid-playback,
   * {@link android.media.AudioTrack} no longer supports direct playback of the format, emulating a
   * TV where the routed audio device moves from TV speakers to a bluetooth headset, and writing to
   * the {@link android.media.AudioTrack} returns {@link
   * android.media.AudioTrack#ERROR_DEAD_OBJECT}. As a result, the player triggers a new track
   * selection and picks the AAC stereo format which is available.
   *
   * <p>For {@code API 33+}, the {@link AudioCapabilities} polls the platform with {@link
   * AudioManager#getDirectProfilesForAttributes(AudioAttributes)}. And for {@code 29 <= API < 33},
   * the {@link AudioCapabilities} polls the platform with {@link
   * android.media.AudioTrack#isDirectPlaybackSupported}. Use the {@link
   * SynchronousMediaCodecAdapter} because {@link MediaCodec} is not fully functional in
   * asynchronous mode with Robolectric.
   */
  @Test
  @Config(minSdk = 29)
  public void playAc3WithDirectPlayback_directPlaybackNotSupportMidPlayback_recoversToAac()
      throws Exception {
    shadowMediaCodecConfig.addSupportedMimeTypes(MimeTypes.AUDIO_AAC);
    setupDefaultPcmSupport();
    addDirectPlaybackSupportForAC3();
    setUiModeToTv();
    RenderersFactory renderersFactory =
        createRenderersFactory(
            // Right after we write the first buffer to audio sink, AudioTrack stops
            // supporting the audio format directly.
            /* onProcessedOutputBufferRunnable= */ this::clearDirectPlaybackSupport);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample_ac3_aac.mp4"));
    player.prepare();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);

    player.play();
    advance(player).ignoringNonFatalErrors().untilState(Player.STATE_ENDED);
    player.release();

    ArgumentCaptor<Tracks> tracks = ArgumentCaptor.forClass(Tracks.class);
    verify(listener, times(2)).onTracksChanged(tracks.capture());
    // First track selection picks AC3 and second track selection picks AAC.
    Tracks firstTrackSelection = tracks.getAllValues().get(0);
    assertThat(firstTrackSelection.getGroups()).hasSize(2);
    assertThat(firstTrackSelection.getGroups().get(0).isSelected()).isTrue();
    assertThat(firstTrackSelection.getGroups().get(0).getTrackFormat(0).sampleMimeType)
        .isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(firstTrackSelection.getGroups().get(1).isSelected()).isFalse();
    assertThat(firstTrackSelection.getGroups().get(1).getTrackFormat(0).sampleMimeType)
        .isEqualTo(MimeTypes.AUDIO_AAC);
    Tracks secondTrackSelection = tracks.getAllValues().get(1);
    assertThat(secondTrackSelection.getGroups()).hasSize(2);
    assertThat(secondTrackSelection.getGroups().get(0).isSelected()).isFalse();
    assertThat(secondTrackSelection.getGroups().get(0).getTrackFormat(0).sampleMimeType)
        .isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(secondTrackSelection.getGroups().get(1).isSelected()).isTrue();
    assertThat(secondTrackSelection.getGroups().get(1).getTrackFormat(0).sampleMimeType)
        .isEqualTo(MimeTypes.AUDIO_AAC);
  }

  /**
   * Tests that ExoPlayer recovers from AudioTrack recoverable error.
   *
   * <p>The test starts playing of AC3 audio via audio passthrough (direct playback). Mid-playback,
   * {@link android.media.AudioTrack} no longer supports direct playback of the format, emulating a
   * TV where the routed audio device moves from TV speakers to a bluetooth headset, and writing to
   * the {@link android.media.AudioTrack} returns {@link
   * android.media.AudioTrack#ERROR_DEAD_OBJECT}. In this test, the device also has an AC3 decoder,
   * so after the recovery, the AC3 audio is chosen again.
   *
   * <p>For {@code API 33+}, the {@link AudioCapabilities} polls the platform with {@link
   * AudioManager#getDirectProfilesForAttributes(AudioAttributes)}. And for {@code 29 <= API < 33},
   * the {@link AudioCapabilities} polls the platform with {@link
   * android.media.AudioTrack#isDirectPlaybackSupported}. Use the {@link
   * SynchronousMediaCodecAdapter} because {@link MediaCodec} is not fully functional in
   * asynchronous mode with Robolectric.
   */
  @Test
  @Config(minSdk = 29)
  public void
      playAc3WithDirectPlayback_directPlaybackNotSupportMidPlaybackButDeviceHasAc3Codec_recoversToAc3()
          throws Throwable {
    shadowMediaCodecConfig.addSupportedMimeTypes(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_AC3);
    setupDefaultPcmSupport();
    addDirectPlaybackSupportForAC3();
    setUiModeToTv();
    RenderersFactory renderersFactory =
        createRenderersFactory(
            // Right after we write the first buffer to audio sink, AudioTrack stops
            // supporting the audio format directly.
            /* onProcessedOutputBufferRunnable= */ this::clearDirectPlaybackSupport);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample_ac3_aac.mp4"));
    player.prepare();
    player.addAnalyticsListener(createAnalyticsListener());

    player.play();
    advance(player).ignoringNonFatalErrors().untilState(Player.STATE_ENDED);
    player.release();

    // We expect to start playing audio via passthrough and mid-playback switch to a local decoder.
    // Hence, the audio renderer is enabled and disabled without an audio decoder initialized,
    // indicating direct audio playback. Then the audio renderer is enabled and an audio decoder is
    // initialized, indicating local decoding of audio. We cannot verify the order of callbacks with
    // Mockito's in-order verification because onAudioEnabled is called twice and we cannot reliably
    // distinguish between the two calls with Mockito.
    assertThat(analyticsListenerReceivedCallbacks)
        .containsExactly(
            "onAudioEnabled", "onAudioDisabled", "onAudioEnabled", "onAudioDecoderInitialized")
        .inOrder();
    // Verify onTracksChanged was called exactly once and the AC3 track was selected.
    ImmutableList<Group> groups = Iterables.getOnlyElement(selectedTracks).getGroups();
    assertThat(groups.get(0).isSelected()).isTrue();
    assertThat(groups.get(0).getTrackFormat(0).sampleMimeType).isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(groups.get(1).isSelected()).isFalse();
  }

  /**
   * Tests that ExoPlayer switches to direct playback mode after audio capabilities change.
   *
   * <p>The test starts with playing AAC stereo audio over a bluetooth headset, and the {@linkplain
   * Parameters.Builder#setAllowInvalidateSelectionsOnRendererCapabilitiesChange(boolean) is turned
   * on}. Mid-playback, the bluetooth headset is disconnected and the default TV speaker supports
   * direct playback for AC3. Then the AC3 audio is selected for direct playback.
   *
   * <p>For {@code API 33+}, the {@link AudioCapabilities} polls the platform with {@link
   * AudioManager#getDirectProfilesForAttributes(AudioAttributes)}. And for {@code 29 <= API < 33},
   * the {@link AudioCapabilities} polls the platform with {@link
   * android.media.AudioTrack#isDirectPlaybackSupported}. Use the {@link
   * SynchronousMediaCodecAdapter} because {@link MediaCodec} is not fully functional in
   * asynchronous mode with Robolectric.
   */
  @Test
  @Config(minSdk = 29)
  public void playAacWithCodec_directPlaybackSupportMidPlayback_changeToAc3DirectPlayback()
      throws Throwable {
    final AtomicBoolean directPlaybackSupportAddedReference = new AtomicBoolean();

    setupDefaultPcmSupport();
    shadowMediaCodecConfig.addSupportedMimeTypes(MimeTypes.AUDIO_AAC);
    setUiModeToTv();
    RenderersFactory renderersFactory =
        createRenderersFactory(
            /* onProcessedOutputBufferRunnable= */ () -> {
              // Right after we write the first buffer to audio sink, direct playback support
              // is added, and AudioTrack begins to support the audio format directly.
              if (!directPlaybackSupportAddedReference.get()) {
                addDirectPlaybackSupportForAC3();
                directPlaybackSupportAddedReference.set(true);
              }
            });
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setTrackSelectionParameters(
        defaultParameters
            .buildUpon()
            .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
            .build());
    player.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample_ac3_aac.mp4"));
    player.prepare();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);

    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    ArgumentCaptor<Tracks> tracks = ArgumentCaptor.forClass(Tracks.class);
    verify(listener, times(2)).onTracksChanged(tracks.capture());
    // First track selection picks AAC and second track selection picks AC3.
    Tracks firstTrackSelection = tracks.getAllValues().get(0);
    assertThat(firstTrackSelection.getGroups()).hasSize(2);
    assertThat(firstTrackSelection.getGroups().get(0).isSelected()).isFalse();
    assertThat(firstTrackSelection.getGroups().get(0).getTrackFormat(0).sampleMimeType)
        .isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(firstTrackSelection.getGroups().get(1).isSelected()).isTrue();
    assertThat(firstTrackSelection.getGroups().get(1).getTrackFormat(0).sampleMimeType)
        .isEqualTo(MimeTypes.AUDIO_AAC);
    Tracks secondTrackSelection = tracks.getAllValues().get(1);
    assertThat(secondTrackSelection.getGroups()).hasSize(2);
    assertThat(secondTrackSelection.getGroups().get(0).isSelected()).isTrue();
    assertThat(secondTrackSelection.getGroups().get(0).getTrackFormat(0).sampleMimeType)
        .isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(secondTrackSelection.getGroups().get(1).isSelected()).isFalse();
    assertThat(secondTrackSelection.getGroups().get(1).getTrackFormat(0).sampleMimeType)
        .isEqualTo(MimeTypes.AUDIO_AAC);
  }

  /**
   * Tests that ExoPlayer switches to direct playback mode after audio capabilities change.
   *
   * <p>The test starts with playing over a bluetooth headset, the AC3 audio is decoded to PCM with
   * a local decoder, and the {@linkplain
   * Parameters.Builder#setAllowInvalidateSelectionsOnRendererCapabilitiesChange(boolean) is turned
   * on}. Mid-playback, the bluetooth headset is disconnected and the default TV speaker supports
   * direct playback for AC3. Then the AC3 audio is selected again for direct playback.
   *
   * <p>For {@code API 33+}, the {@link AudioCapabilities} polls the platform with {@link
   * AudioManager#getDirectProfilesForAttributes(AudioAttributes)}. And for {@code 29 <= API < 33},
   * the {@link AudioCapabilities} polls the platform with {@link
   * android.media.AudioTrack#isDirectPlaybackSupported}. Use the {@link
   * SynchronousMediaCodecAdapter} because {@link MediaCodec} is not fully functional in
   * asynchronous mode with Robolectric.
   */
  @Test
  @Config(minSdk = 29)
  public void playAc3WithCodec_directPlaybackSupportMidPlayback_changeToAc3DirectPlayback()
      throws Throwable {
    final AtomicBoolean directPlaybackSupportAddedReference = new AtomicBoolean();

    setupDefaultPcmSupport();
    shadowMediaCodecConfig.addSupportedMimeTypes(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_AC3);
    setUiModeToTv();
    RenderersFactory renderersFactory =
        createRenderersFactory(
            /* onProcessedOutputBufferRunnable= */ () -> {
              // Right after we write the first buffer to audio sink, direct playback support
              // is added, and AudioTrack begins to support the audio format directly.
              if (!directPlaybackSupportAddedReference.get()) {
                addDirectPlaybackSupportForAC3();
                directPlaybackSupportAddedReference.set(true);
              }
            });
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setTrackSelectionParameters(
        defaultParameters
            .buildUpon()
            .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
            .build());
    player.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample_ac3_aac.mp4"));
    player.prepare();
    player.addAnalyticsListener(createAnalyticsListener());

    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // We expect to start playing audio with a local AC3 decoder and mid-playback switch to the
    // passthrough mode. Hence, the audio renderer is enabled and disabled with an audio decoder
    // initialized, indicating the local decoding of audio. Then the audio renderer is enabled and
    // the audio decoder is released, indicating the direct audio playback. We cannot verify the
    // order of callbacks with Mockito's in-order verification because onAudioEnabled is called
    // twice and we cannot reliably distinguish between the two calls with Mockito.
    assertThat(analyticsListenerReceivedCallbacks)
        .containsExactly(
            "onAudioEnabled",
            "onAudioDecoderInitialized",
            "onAudioDisabled",
            "onAudioEnabled",
            "onAudioDecoderReleased")
        .inOrder();
    // Verify onTracksChanged was called exactly once and the AC3 track was selected.
    ImmutableList<Group> groups = Iterables.getOnlyElement(selectedTracks).getGroups();
    assertThat(groups.get(0).isSelected()).isTrue();
    assertThat(groups.get(0).getTrackFormat(0).sampleMimeType).isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(groups.get(1).isSelected()).isFalse();
  }

  /**
   * Tests that ExoPlayer doesn't switch track after audio capabilities change when the {@linkplain
   * Parameters.Builder#setAllowInvalidateSelectionsOnRendererCapabilitiesChange(boolean) is turned
   * off}.
   *
   * <p>The test starts with playing AAC stereo audio over a bluetooth headset, and the {@linkplain
   * Parameters.Builder#setAllowInvalidateSelectionsOnRendererCapabilitiesChange(boolean) is turned
   * off}. Mid-playback, the bluetooth headset is disconnected and the default TV speaker supports
   * direct playback for AC3. The playback remains with AAC track.
   *
   * <p>For {@code API 33+}, the {@link AudioCapabilities} polls the platform with {@link
   * AudioManager#getDirectProfilesForAttributes(AudioAttributes)}. And for {@code 29 <= API < 33},
   * the {@link AudioCapabilities} polls the platform with {@link
   * android.media.AudioTrack#isDirectPlaybackSupported}. Use the {@link
   * SynchronousMediaCodecAdapter} because {@link MediaCodec} is not fully functional in
   * asynchronous mode with Robolectric.
   */
  @Test
  @Config(minSdk = 29)
  public void playAacWithCodec_rendererCapabilitiesChangedWhenSelectionInvalidationTurnedOff()
      throws Throwable {
    final AtomicBoolean directPlaybackSupportAddedReference = new AtomicBoolean();

    shadowMediaCodecConfig.addSupportedMimeTypes(MimeTypes.AUDIO_AAC);
    setupDefaultPcmSupport();
    setUiModeToTv();
    RenderersFactory renderersFactory =
        createRenderersFactory(
            /* onProcessedOutputBufferRunnable= */ () -> {
              // Right after we write the first buffer to audio sink, direct playback support
              // is added, and AudioTrack begins to support the audio format directly.
              if (!directPlaybackSupportAddedReference.get()) {
                addDirectPlaybackSupportForAC3();
                directPlaybackSupportAddedReference.set(true);
              }
            });
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setMediaItem(MediaItem.fromUri("asset:///media/mp4/sample_ac3_aac.mp4"));
    player.prepare();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);

    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Verify onTracksChanged was called exactly once and the AAC track was selected.
    ArgumentCaptor<Tracks> tracks = ArgumentCaptor.forClass(Tracks.class);
    verify(listener).onTracksChanged(tracks.capture());
    Tracks trackSelection = tracks.getAllValues().get(0);
    assertThat(trackSelection.getGroups()).hasSize(2);
    assertThat(trackSelection.getGroups().get(0).isSelected()).isFalse();
    assertThat(trackSelection.getGroups().get(0).getTrackFormat(0).sampleMimeType)
        .isEqualTo(MimeTypes.AUDIO_AC3);
    assertThat(trackSelection.getGroups().get(1).isSelected()).isTrue();
    assertThat(trackSelection.getGroups().get(1).getTrackFormat(0).sampleMimeType)
        .isEqualTo(MimeTypes.AUDIO_AAC);
  }

  private void setUiModeToTv() {
    ShadowUIModeManager shadowUiModeManager =
        shadowOf(
            (UiModeManager)
                ApplicationProvider.getApplicationContext()
                    .getSystemService(Context.UI_MODE_SERVICE));
    shadowUiModeManager.currentModeType = Configuration.UI_MODE_TYPE_TELEVISION;
  }

  private void addDirectPlaybackSupportForAC3() {
    ShadowAudioTrack.addAllowedNonPcmEncoding(ENCODING_AC3);
    // Set direct playback support for the format and attributes that AudioCapabilities use when
    // querying the platform.
    ShadowAudioTrack.addDirectPlaybackSupport(
        new AudioFormat.Builder()
            .setEncoding(ENCODING_AC3)
            .setSampleRate(AudioCapabilities.DEFAULT_SAMPLE_RATE_HZ)
            .setChannelMask(CHANNEL_OUT_STEREO)
            .build(),
        new android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
            .setFlags(0)
            .build());
    directPlaybackDevice = createDirectPlaybackDevice(ENCODING_AC3, CHANNEL_OUT_5POINT1);
    if (Util.SDK_INT >= 33) {
      shadowOf(audioManager).addOutputDeviceWithDirectProfiles(checkNotNull(directPlaybackDevice));
    }
    shadowOf(audioManager)
        .addOutputDevice(
            checkNotNull(directPlaybackDevice), /* notifyAudioDeviceCallbacks= */ true);
  }

  private void clearDirectPlaybackSupport() {
    ShadowAudioTrack.clearAllowedNonPcmEncodings();
    ShadowAudioTrack.clearDirectPlaybackSupportedFormats();
    if (directPlaybackDevice != null) {
      if (Util.SDK_INT >= 33) {
        shadowOf(audioManager).removeOutputDeviceWithDirectProfiles(directPlaybackDevice);
      }
      shadowOf(audioManager)
          .removeOutputDevice(directPlaybackDevice, /* notifyAudioDeviceCallbacks= */ true);
      directPlaybackDevice = null;
    }
  }

  private void setupDefaultPcmSupport() {
    AudioDeviceInfoBuilder defaultDevice =
        AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    if (Util.SDK_INT >= 33) {
      defaultDevice.setProfiles(ImmutableList.of(createPcmProfile()));
      shadowOf(audioManager).addOutputDeviceWithDirectProfiles(defaultDevice.build());
    } else {
      shadowOf(audioManager)
          .addOutputDevice(defaultDevice.build(), /* notifyAudioDeviceCallbacks= */ true);
    }
  }

  @SuppressWarnings("UseSdkSuppress") // https://issuetracker.google.com/382253664
  @RequiresApi(33)
  private static AudioProfile createPcmProfile() {
    return AudioProfileBuilder.newBuilder()
        .setFormat(AudioFormat.ENCODING_PCM_16BIT)
        .setSamplingRates(new int[] {48_000})
        .setChannelMasks(new int[] {AudioFormat.CHANNEL_OUT_STEREO})
        .setChannelIndexMasks(new int[] {})
        .setEncapsulationType(AudioProfile.AUDIO_ENCAPSULATION_TYPE_NONE)
        .build();
  }

  private static AudioDeviceInfo createDirectPlaybackDevice(int encoding, int channelMask) {
    AudioDeviceInfoBuilder directPlaybackDevice =
        AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_HDMI);
    if (Util.SDK_INT >= 33) {
      ImmutableList<AudioProfile> expectedProfiles =
          ImmutableList.of(
              AudioProfileBuilder.newBuilder()
                  .setFormat(encoding)
                  .setSamplingRates(new int[] {48_000})
                  .setChannelMasks(new int[] {channelMask})
                  .setChannelIndexMasks(new int[] {})
                  .setEncapsulationType(AudioProfile.AUDIO_ENCAPSULATION_TYPE_NONE)
                  .build(),
              createPcmProfile());
      directPlaybackDevice.setProfiles(expectedProfiles);
    }
    return directPlaybackDevice.build();
  }

  private RenderersFactory createRenderersFactory(Runnable onProcessedOutputBufferRunnable) {
    return (eventHandler,
        unusedVideoRendererEventListener,
        audioRendererEventListener,
        unusedTextRendererOutput,
        unusedMetadataRendererOutput) ->
        new Renderer[] {
          new MediaCodecAudioRenderer(
              applicationContext,
              new SynchronousMediaCodecAdapter.Factory(),
              MediaCodecSelector.DEFAULT,
              /* enableDecoderFallback= */ false,
              eventHandler,
              audioRendererEventListener,
              new DefaultAudioSink.Builder(applicationContext).build()) {
            @Override
            protected void onProcessedOutputBuffer(long presentationTimeUs) {
              onProcessedOutputBufferRunnable.run();
              super.onProcessedOutputBuffer(presentationTimeUs);
            }
          }
        };
  }

  private AnalyticsListener createAnalyticsListener() {
    return new AnalyticsListener() {
      @Override
      public void onTracksChanged(EventTime eventTime, Tracks tracks) {
        selectedTracks.add(tracks);
      }

      @Override
      public void onAudioEnabled(EventTime eventTime, DecoderCounters decoderCounters) {
        analyticsListenerReceivedCallbacks.add("onAudioEnabled");
      }

      @Override
      public void onAudioDisabled(EventTime eventTime, DecoderCounters decoderCounters) {
        analyticsListenerReceivedCallbacks.add("onAudioDisabled");
      }

      @Override
      public void onAudioDecoderInitialized(
          EventTime eventTime,
          String decoderName,
          long initializedTimestampMs,
          long initializationDurationMs) {
        analyticsListenerReceivedCallbacks.add("onAudioDecoderInitialized");
      }

      @Override
      public void onAudioDecoderReleased(EventTime eventTime, String decoderName) {
        analyticsListenerReceivedCallbacks.add("onAudioDecoderReleased");
      }
    };
  }
}
