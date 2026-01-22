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
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.exoplayer.audio.AudioCapabilities.ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS;
import static androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioProfile;
import android.provider.Settings.Global;
import android.util.Pair;
import androidx.annotation.RequiresApi;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.AudioDeviceInfoBuilder;
import org.robolectric.shadows.AudioProfileBuilder;
import org.robolectric.shadows.ShadowAudioTrack;
import org.robolectric.shadows.ShadowBuild;
import org.robolectric.shadows.ShadowUIModeManager;

/** Unit tests for {@link AudioCapabilities}. */
@RunWith(AndroidJUnit4.class)
public class AudioCapabilitiesTest {

  private AudioManager audioManager;
  private UiModeManager uiModeManager;

  @Before
  public void setUp() {
    audioManager =
        (AudioManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    shadowOf(audioManager).setOutputDevices(ImmutableList.of());
    uiModeManager =
        (UiModeManager)
            ApplicationProvider.getApplicationContext().getSystemService(Context.UI_MODE_SERVICE);
  }

  @Test
  @Config(minSdk = 33)
  public void getCapabilities_returnsCapabilitiesFromDirectProfiles_onTvV33() {
    // Set UI mode to TV.
    ShadowUIModeManager shadowUiModeManager =
        shadowOf(
            (UiModeManager)
                ApplicationProvider.getApplicationContext()
                    .getSystemService(Context.UI_MODE_SERVICE));
    shadowUiModeManager.currentModeType = Configuration.UI_MODE_TYPE_TELEVISION;
    int[] channelMasks =
        new int[] {
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.CHANNEL_OUT_STEREO,
          AudioFormat.CHANNEL_OUT_STEREO | AudioFormat.CHANNEL_OUT_FRONT_CENTER,
          AudioFormat.CHANNEL_OUT_QUAD,
          AudioFormat.CHANNEL_OUT_QUAD | AudioFormat.CHANNEL_OUT_FRONT_CENTER,
          AudioFormat.CHANNEL_OUT_5POINT1
        };
    ImmutableList<AudioProfile> expectedProfiles =
        ImmutableList.of(
            AudioProfileBuilder.newBuilder()
                .setFormat(AudioFormat.ENCODING_DTS)
                .setSamplingRates(new int[] {44_100, 48_000})
                .setChannelMasks(channelMasks)
                .setChannelIndexMasks(new int[] {})
                .setEncapsulationType(AudioProfile.AUDIO_ENCAPSULATION_TYPE_NONE)
                .build(),
            AudioProfileBuilder.newBuilder()
                .setFormat(AudioFormat.ENCODING_PCM_16BIT)
                .setSamplingRates(new int[] {48_000})
                .setChannelMasks(new int[] {AudioFormat.CHANNEL_OUT_STEREO})
                .setChannelIndexMasks(new int[] {})
                .setEncapsulationType(AudioProfile.AUDIO_ENCAPSULATION_TYPE_NONE)
                .build());
    AudioDeviceInfo device =
        AudioDeviceInfoBuilder.newBuilder()
            .setType(AudioDeviceInfo.TYPE_HDMI)
            .setProfiles(expectedProfiles)
            .build();
    shadowOf(audioManager).addOutputDeviceWithDirectProfiles(device);

    AudioCapabilities audioCapabilities =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(),
            AudioAttributes.DEFAULT,
            /* routedDevice= */ null);

    assertThat(audioCapabilities.supportsEncoding(C.ENCODING_PCM_16BIT)).isTrue();
    for (int encoding : ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS.keySet()) {
      if (encoding == AudioFormat.ENCODING_DTS) {
        assertThat(audioCapabilities.supportsEncoding(encoding)).isTrue();
      } else {
        // Should not support all the other encodings.
        assertThat(audioCapabilities.supportsEncoding(encoding)).isFalse();
      }
    }
    // Should not support the format whose channel count is not reported in the profile.
    assertThat(
            audioCapabilities.isPassthroughPlaybackSupported(
                new Format.Builder()
                    .setSampleMimeType(MimeTypes.AUDIO_DTS)
                    .setChannelCount(8)
                    .build(),
                AudioAttributes.DEFAULT))
        .isFalse();
  }

  /** {@link AudioDeviceInfo#TYPE_BLUETOOTH_A2DP} is only supported from API 23. */
  @Test
  @Config(minSdk = 23)
  public void getCapabilities_withBluetoothA2dpAndHdmiConnectedApi23_returnsDefaultCapabilities() {
    setOutputDevices(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_HDMI);
    configureHdmiConnection(/* maxChannelCount= */ 6, /* encodings...= */ AudioFormat.ENCODING_AC3);

    AudioDeviceInfo[] audioDeviceInfos =
        shadowOf(audioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
    AudioCapabilities audioCapabilities =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(),
            AudioAttributes.DEFAULT,
            /* routedDevice= */ null);

    assertThat(getDeviceTypes(audioDeviceInfos))
        .containsAtLeast(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_HDMI);
    assertThat(audioCapabilities).isEqualTo(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES);
  }

  /** {@link AudioDeviceInfo#TYPE_BLE_HEADSET} is only supported from API 31. */
  @Test
  @Config(minSdk = 31)
  public void
      getCapabilities_withBluetoothHeadsetAndHmdiConnectedApi31_returnsDefaultCapabilities() {
    setOutputDevices(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_HDMI);
    configureHdmiConnection(/* maxChannelCount= */ 6, /* encodings...= */ AudioFormat.ENCODING_AC3);

    AudioDeviceInfo[] audioDeviceInfos =
        shadowOf(audioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
    AudioCapabilities audioCapabilities =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(),
            AudioAttributes.DEFAULT,
            /* routedDevice= */ null);

    assertThat(getDeviceTypes(audioDeviceInfos))
        .containsAtLeast(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_HDMI);
    assertThat(audioCapabilities).isEqualTo(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES);
  }

  /** {@link AudioDeviceInfo#TYPE_BLE_BROADCAST} is only supported from API 33. */
  @Test
  @Config(minSdk = 33)
  public void
      getCapabilities_withBluetoothBroadcastAndHdmiConnectedApi33_returnsDefaultCapabilities() {
    setOutputDevices(AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HDMI);
    configureHdmiConnection(/* maxChannelCount= */ 6, /* encodings...= */ AudioFormat.ENCODING_AC3);

    AudioDeviceInfo[] audioDeviceInfos =
        shadowOf(audioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
    AudioCapabilities audioCapabilities =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(),
            AudioAttributes.DEFAULT,
            /* routedDevice= */ null);

    assertThat(getDeviceTypes(audioDeviceInfos))
        .containsAtLeast(AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HDMI);
    assertThat(audioCapabilities).isEqualTo(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES);
  }

  @Test
  public void getCapabilities_noBluetoothButHdmiConnected_returnsHdmiCapabilities() {
    setOutputDevices(AudioDeviceInfo.TYPE_HDMI);
    configureHdmiConnection(
        /* maxChannelCount= */ 6,
        /* encodings...= */ AudioFormat.ENCODING_AC3,
        AudioFormat.ENCODING_DTS,
        AudioFormat.ENCODING_E_AC3);

    AudioDeviceInfo[] audioDeviceInfos =
        shadowOf(audioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
    AudioCapabilities audioCapabilities =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(),
            AudioAttributes.DEFAULT,
            /* routedDevice= */ null);

    assertThat(getDeviceTypes(audioDeviceInfos)).contains(AudioDeviceInfo.TYPE_HDMI);
    assertThat(getDeviceTypes(audioDeviceInfos))
        .doesNotContain(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
    assertThat(audioCapabilities)
        .isEqualTo(
            new AudioCapabilities(
                new int[] {
                  AudioFormat.ENCODING_PCM_16BIT,
                  AudioFormat.ENCODING_AC3,
                  AudioFormat.ENCODING_DTS,
                  AudioFormat.ENCODING_E_AC3
                },
                /* maxChannelCount= */ 6));
  }

  @Config(maxSdk = 32) // Fallback test for APIs before 33
  @Test
  public void
      getCapabilities_noBluetoothButGlobalSurroundSettingOnAmazon_returnsExternalSurroundCapabilities() {
    Global.putInt(
        ApplicationProvider.getApplicationContext().getContentResolver(),
        "external_surround_sound_enabled",
        1);
    ShadowBuild.setManufacturer("Amazon");

    AudioCapabilities audioCapabilities =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(),
            AudioAttributes.DEFAULT,
            /* routedDevice= */ null);

    assertThat(audioCapabilities.supportsEncoding(C.ENCODING_PCM_16BIT)).isTrue();
    assertThat(audioCapabilities.supportsEncoding(C.ENCODING_AC3)).isTrue();
    assertThat(audioCapabilities.supportsEncoding(C.ENCODING_E_AC3)).isTrue();
  }

  // Fallback test for APIs before 33, TYPE_HDMI is only supported from API 23
  @Config(minSdk = 23, maxSdk = 32)
  @Test
  public void
      getCapabilities_noBluetoothButGlobalSurroundSettingForced_returnsExternalSurroundCapabilitiesAndIgnoresHdmi() {
    setOutputDevices(AudioDeviceInfo.TYPE_HDMI);
    configureHdmiConnection(/* maxChannelCount= */ 6, /* encodings...= */ AudioFormat.ENCODING_DTS);
    Global.putInt(
        ApplicationProvider.getApplicationContext().getContentResolver(),
        "use_external_surround_sound_flag",
        1);

    AudioCapabilities audioCapabilitiesWithoutFlag =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(),
            AudioAttributes.DEFAULT,
            /* routedDevice= */ null);
    Global.putInt(
        ApplicationProvider.getApplicationContext().getContentResolver(),
        "external_surround_sound_enabled",
        1);
    AudioCapabilities audioCapabilitiesWithFlag =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(),
            AudioAttributes.DEFAULT,
            /* routedDevice= */ null);

    assertThat(audioCapabilitiesWithoutFlag.supportsEncoding(C.ENCODING_PCM_16BIT)).isTrue();
    assertThat(audioCapabilitiesWithoutFlag.supportsEncoding(C.ENCODING_AC3)).isFalse();
    assertThat(audioCapabilitiesWithoutFlag.supportsEncoding(C.ENCODING_E_AC3)).isFalse();
    assertThat(audioCapabilitiesWithoutFlag.supportsEncoding(C.ENCODING_DTS)).isFalse();
    assertThat(audioCapabilitiesWithFlag.supportsEncoding(C.ENCODING_PCM_16BIT)).isTrue();
    assertThat(audioCapabilitiesWithFlag.supportsEncoding(C.ENCODING_AC3)).isTrue();
    assertThat(audioCapabilitiesWithFlag.supportsEncoding(C.ENCODING_E_AC3)).isTrue();
    assertThat(audioCapabilitiesWithFlag.supportsEncoding(C.ENCODING_DTS)).isFalse();
  }

  @Test
  @Config(minSdk = 23) // TYPE_BLUETOOTH_A2DP detection is supported from API 23.
  public void
      getCapabilities_withBluetoothA2dpConnectedAndHdmiAsRoutedDeviceHintApi23_returnsHdmiCapabilities() {
    setOutputDevices(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_HDMI);
    configureHdmiConnection(
        /* maxChannelCount= */ 10,
        /* encodings...= */ AudioFormat.ENCODING_AC3,
        AudioFormat.ENCODING_DTS,
        AudioFormat.ENCODING_E_AC3_JOC);

    AudioDeviceInfo[] audioDeviceInfos =
        shadowOf(audioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
    AudioDeviceInfo routedDevice = null;
    for (AudioDeviceInfo deviceInfo : audioDeviceInfos) {
      if (deviceInfo.getType() == AudioDeviceInfo.TYPE_HDMI) {
        routedDevice = deviceInfo;
      }
    }
    AudioCapabilities audioCapabilities =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(), AudioAttributes.DEFAULT, routedDevice);

    assertThat(routedDevice).isNotNull();
    assertThat(getDeviceTypes(audioDeviceInfos))
        .containsAtLeast(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_HDMI);
    assertThat(audioCapabilities)
        .isEqualTo(
            new AudioCapabilities(
                new int[] {
                  AudioFormat.ENCODING_PCM_16BIT,
                  AudioFormat.ENCODING_AC3,
                  AudioFormat.ENCODING_DTS,
                  AudioFormat.ENCODING_E_AC3_JOC
                },
                /* maxChannelCount= */ 10));
  }

  @Test
  @Config(minSdk = 33) // getAudioDevicesForAttributes only works from API33
  public void
      getCapabilities_withBluetoothA2dpAndHdmiConnectedAndHdmiAsDefaultRoutedDeviceApi33_returnsHdmiCapabilities() {
    setOutputDevices(AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
    setDefaultRoutedDevice(AudioAttributes.DEFAULT, AudioDeviceInfo.TYPE_HDMI);
    configureHdmiConnection(
        /* maxChannelCount= */ 10,
        /* encodings...= */ AudioFormat.ENCODING_AC3,
        AudioFormat.ENCODING_DTS,
        AudioFormat.ENCODING_E_AC3_JOC);

    AudioDeviceInfo[] audioDeviceInfos =
        shadowOf(audioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
    AudioCapabilities audioCapabilities =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(),
            AudioAttributes.DEFAULT,
            /* routedDevice= */ null);

    assertThat(getDeviceTypes(audioDeviceInfos))
        .containsAtLeast(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_HDMI);
    assertThat(audioCapabilities)
        .isEqualTo(
            new AudioCapabilities(
                new int[] {
                  AudioFormat.ENCODING_PCM_16BIT,
                  AudioFormat.ENCODING_AC3,
                  AudioFormat.ENCODING_DTS,
                  AudioFormat.ENCODING_E_AC3_JOC
                },
                /* maxChannelCount= */ 10));
  }

  @Test
  public void getCapabilities_noExternalOutputs_notTvNorAutomotive_returnsDefaultCapabilities() {
    AudioCapabilities audioCapabilities =
        AudioCapabilities.getCapabilities(
            ApplicationProvider.getApplicationContext(),
            AudioAttributes.DEFAULT,
            /* routedDevice= */ null);

    assertThat(audioCapabilities).isEqualTo(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES);
  }

  @Config(minSdk = 29)
  @Test
  public void
      getEncodingAndChannelConfigForPassthrough_forEAc3JocAndSingleSupportedConfig_returnsCorrectEncodingAndChannelConfig() {
    // Set UI mode to TV.
    shadowOf(uiModeManager).currentModeType = Configuration.UI_MODE_TYPE_TELEVISION;
    Format format =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC)
            .setSampleRate(48_000)
            .build();
    AudioAttributes directPlaybackAudioAttributes =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build();
    addDirectPlaybackSupport(
        AudioFormat.ENCODING_E_AC3_JOC, CHANNEL_OUT_5POINT1, directPlaybackAudioAttributes);
    AudioCapabilities audioCapabilities =
        getCapabilities(
            ApplicationProvider.getApplicationContext(),
            directPlaybackAudioAttributes,
            /* routedDevice= */ null);

    Pair<Integer, Integer> encodingAndChannelConfig =
        audioCapabilities.getEncodingAndChannelConfigForPassthrough(
            format, directPlaybackAudioAttributes);

    assertThat(encodingAndChannelConfig.first).isEqualTo(AudioFormat.ENCODING_E_AC3_JOC);
    assertThat(encodingAndChannelConfig.second).isEqualTo(AudioFormat.CHANNEL_OUT_5POINT1);
  }

  // TODO: b/320191198 - Disable the test for API 33, as the
  // ShadowAudioManager.getDirectProfilesForAttributes(AudioAttributes) hasn't really considered
  // the AudioAttributes yet.
  @Config(minSdk = 29, maxSdk = 32)
  @Test
  public void
      getEncodingAndChannelConfigForPassthrough_forDifferentAudioAttributes_returnsUnsupported() {
    // Set UI mode to TV.
    shadowOf(uiModeManager).currentModeType = Configuration.UI_MODE_TYPE_TELEVISION;
    Format format =
        new Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC)
            .setSampleRate(48_000)
            .build();
    AudioAttributes directPlaybackAudioAttributes =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build();
    addDirectPlaybackSupport(
        AudioFormat.ENCODING_E_AC3_JOC, CHANNEL_OUT_5POINT1, directPlaybackAudioAttributes);
    AudioCapabilities audioCapabilities =
        getCapabilities(
            ApplicationProvider.getApplicationContext(),
            directPlaybackAudioAttributes,
            /* routedDevice= */ null);
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build();

    Pair<Integer, Integer> encodingAndChannelConfig =
        audioCapabilities.getEncodingAndChannelConfigForPassthrough(format, audioAttributes);

    assertThat(encodingAndChannelConfig).isNull();
  }

  /**
   * Sets all the available output devices and uses the first as the default routed device for the
   * given {@link AudioAttributes}
   */
  private void setOutputDevices(int... types) {
    ImmutableList.Builder<AudioDeviceInfo> audioDeviceInfos = ImmutableList.builder();
    for (int type : types) {
      audioDeviceInfos.add(AudioDeviceInfoBuilder.newBuilder().setType(type).build());
    }
    shadowOf(audioManager).setOutputDevices(audioDeviceInfos.build());
  }

  @SuppressWarnings("UseSdkSuppress") // https://issuetracker.google.com/382253664
  @RequiresApi(33)
  private void setDefaultRoutedDevice(AudioAttributes audioAttributes, int type) {
    shadowOf(audioManager)
        .setAudioDevicesForAttributes(
            audioAttributes.getAudioAttributesV21().audioAttributes,
            ImmutableList.of(AudioDeviceInfoBuilder.newBuilder().setType(type).build()));
  }

  @SuppressWarnings("UseSdkSuppress") // https://issuetracker.google.com/382253664
  @RequiresApi(23)
  private void addDirectPlaybackSupport(
      int encoding, int channelMask, AudioAttributes audioAttributes) {
    ShadowAudioTrack.addAllowedNonPcmEncoding(AudioFormat.ENCODING_E_AC3_JOC);
    // We have to add a support for STEREO channel mask as
    // Api29.getDirectPlaybackSupportedEncodings in AudioCapabilities uses CHANNEL_OUT_STEREO
    // to query the support from the platform.
    ShadowAudioTrack.addDirectPlaybackSupport(
        new AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(48_000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build(),
        audioAttributes.getAudioAttributesV21().audioAttributes);
    ShadowAudioTrack.addDirectPlaybackSupport(
        new AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(48_000)
            .setChannelMask(channelMask)
            .build(),
        audioAttributes.getAudioAttributesV21().audioAttributes);
    AudioDeviceInfoBuilder deviceInfoBuilder =
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
                  .build());
      deviceInfoBuilder.setProfiles(expectedProfiles);
    }
    AudioDeviceInfo directPlaybackDevice = deviceInfoBuilder.build();
    shadowOf(audioManager)
        .addOutputDevice(directPlaybackDevice, /* notifyAudioDeviceCallbacks= */ true);
    shadowOf(audioManager).addOutputDeviceWithDirectProfiles(checkNotNull(directPlaybackDevice));
  }

  // Adding the permission to the test AndroidManifest.xml doesn't work to appease lint.
  @SuppressWarnings({"StickyBroadcast", "MissingPermission"})
  private void configureHdmiConnection(int maxChannelCount, int... encodings) {
    Intent intent = new Intent(AudioManager.ACTION_HDMI_AUDIO_PLUG);
    intent.putExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 1);
    intent.putExtra(AudioManager.EXTRA_ENCODINGS, encodings);
    intent.putExtra(AudioManager.EXTRA_MAX_CHANNEL_COUNT, maxChannelCount);
    ApplicationProvider.getApplicationContext().sendStickyBroadcast(intent);
  }

  @SuppressWarnings("UseSdkSuppress") // https://issuetracker.google.com/382253664
  @RequiresApi(23)
  private List<Integer> getDeviceTypes(AudioDeviceInfo[] audioDeviceInfos) {
    List<Integer> deviceTypes = new ArrayList<>();
    for (AudioDeviceInfo audioDeviceInfo : audioDeviceInfos) {
      deviceTypes.add(audioDeviceInfo.getType());
    }
    return deviceTypes;
  }
}
