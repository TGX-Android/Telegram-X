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
package androidx.media3.session.legacy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test {@link AudioAttributesCompat}. */
@RunWith(AndroidJUnit4.class)
public class AudioAttributesCompatTest {
  // some macros for conciseness
  static AudioAttributesCompat.Builder mkBuilder(
      @AudioAttributesCompat.AttributeContentType int type,
      @AudioAttributesCompat.AttributeUsage int usage) {
    return new AudioAttributesCompat.Builder().setContentType(type).setUsage(usage);
  }

  static AudioAttributesCompat.Builder mkBuilder(int legacyStream) {
    return new AudioAttributesCompat.Builder().setLegacyStreamType(legacyStream);
  }

  // some objects we'll toss around
  Object mMediaAA;
  AudioAttributesCompat mMediaAAC;
  AudioAttributesCompat mMediaLegacyAAC;
  AudioAttributesCompat mMediaAACFromAA;
  AudioAttributesCompat mNotificationAAC;
  AudioAttributesCompat mNotificationLegacyAAC;

  @Before
  @SdkSuppress(minSdkVersion = 21)
  public void setUpApi21() {
    if (Build.VERSION.SDK_INT < 21) {
      return;
    }
    mMediaAA =
        new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build();
    mMediaAACFromAA = AudioAttributesCompat.wrap((AudioAttributes) mMediaAA);
  }

  @Before
  public void setUp() {
    mMediaAAC =
        mkBuilder(AudioAttributesCompat.CONTENT_TYPE_MUSIC, AudioAttributesCompat.USAGE_MEDIA)
            .build();
    mMediaLegacyAAC = mkBuilder(AudioManager.STREAM_MUSIC).build();
    mNotificationAAC =
        mkBuilder(
                AudioAttributesCompat.CONTENT_TYPE_SONIFICATION,
                AudioAttributesCompat.USAGE_NOTIFICATION)
            .build();
    mNotificationLegacyAAC = mkBuilder(AudioManager.STREAM_NOTIFICATION).build();
  }

  @Test
  @SdkSuppress(minSdkVersion = 21)
  public void testCreateWithAudioAttributesApi21() {
    assertThat(mMediaAACFromAA, not(equalTo(null)));
    assertThat((AudioAttributes) mMediaAACFromAA.unwrap(), equalTo(mMediaAA));
    assertThat(
        (AudioAttributes) mMediaAACFromAA.unwrap(),
        equalTo(new AudioAttributes.Builder((AudioAttributes) mMediaAA).build()));
  }

  @Test
  @SdkSuppress(minSdkVersion = 21)
  public void testEqualityApi21() {
    assertThat("self equality", mMediaAACFromAA, equalTo(mMediaAACFromAA));
    assertThat("different things", mMediaAACFromAA, not(equalTo(mNotificationAAC)));
  }

  @Test
  public void testEquality() {
    assertThat("self equality", mMediaAAC, equalTo(mMediaAAC));
    assertThat(
        "equal to clone", mMediaAAC, equalTo(new AudioAttributesCompat.Builder(mMediaAAC).build()));
    assertThat("different things are different", mMediaAAC, not(equalTo(mNotificationAAC)));
    assertThat("different things are different 2", mNotificationAAC, not(equalTo(mMediaAAC)));
    assertThat(
        "equal to clone 2",
        mNotificationAAC,
        equalTo(new AudioAttributesCompat.Builder(mNotificationAAC).build()));
  }

  @Test
  public void testGetters() {
    assertThat(mMediaAAC.getContentType(), equalTo(AudioAttributesCompat.CONTENT_TYPE_MUSIC));
    assertThat(mMediaAAC.getUsage(), equalTo(AudioAttributesCompat.USAGE_MEDIA));
    assertThat(mMediaAAC.getFlags(), equalTo(0));
  }

  @Test
  public void testLegacyStreamTypeInference() {
    assertThat(mMediaAAC.getLegacyStreamType(), equalTo(AudioManager.STREAM_MUSIC));
    assertThat(mMediaLegacyAAC.getLegacyStreamType(), equalTo(AudioManager.STREAM_MUSIC));
    assertThat(mNotificationAAC.getLegacyStreamType(), equalTo(AudioManager.STREAM_NOTIFICATION));
    assertThat(
        mNotificationLegacyAAC.getLegacyStreamType(), equalTo(AudioManager.STREAM_NOTIFICATION));
  }

  @Test
  @SdkSuppress(minSdkVersion = 21)
  public void testLegacyStreamTypeInferenceApi21() {
    assertThat(mMediaAACFromAA.getLegacyStreamType(), equalTo(AudioManager.STREAM_MUSIC));
  }

  @Test
  public void testLegacyStreamTypeInferenceInLegacyMode() {
    // the builders behave differently based on the value of this only-for-testing global
    // so we need our very own objects inside this method
    AudioAttributesCompat.setForceLegacyBehavior(true);

    AudioAttributesCompat mediaAAC =
        mkBuilder(AudioAttributesCompat.CONTENT_TYPE_MUSIC, AudioAttributesCompat.USAGE_MEDIA)
            .build();
    AudioAttributesCompat mediaLegacyAAC = mkBuilder(AudioManager.STREAM_MUSIC).build();

    AudioAttributesCompat notificationAAC =
        mkBuilder(
                AudioAttributesCompat.CONTENT_TYPE_SONIFICATION,
                AudioAttributesCompat.USAGE_NOTIFICATION)
            .build();
    AudioAttributesCompat notificationLegacyAAC =
        mkBuilder(AudioManager.STREAM_NOTIFICATION).build();

    assertThat(mediaAAC.getLegacyStreamType(), equalTo(AudioManager.STREAM_MUSIC));
    assertThat(mediaLegacyAAC.getLegacyStreamType(), equalTo(AudioManager.STREAM_MUSIC));
    assertThat(notificationAAC.getLegacyStreamType(), equalTo(AudioManager.STREAM_NOTIFICATION));
    assertThat(
        notificationLegacyAAC.getLegacyStreamType(), equalTo(AudioManager.STREAM_NOTIFICATION));
  }

  @Test
  public void testUsageAndContentTypeInferredFromLegacyStreamType() {
    AudioAttributesCompat alarmAAC = mkBuilder(AudioManager.STREAM_ALARM).build();
    assertThat(alarmAAC.getUsage(), equalTo(AudioAttributesCompat.USAGE_ALARM));
    assertThat(alarmAAC.getContentType(), equalTo(AudioAttributesCompat.CONTENT_TYPE_SONIFICATION));

    AudioAttributesCompat musicAAC = mkBuilder(AudioManager.STREAM_MUSIC).build();
    assertThat(musicAAC.getUsage(), equalTo(AudioAttributesCompat.USAGE_MEDIA));
    assertThat(musicAAC.getContentType(), equalTo(AudioAttributesCompat.CONTENT_TYPE_MUSIC));

    AudioAttributesCompat notificationAAC = mkBuilder(AudioManager.STREAM_NOTIFICATION).build();
    assertThat(notificationAAC.getUsage(), equalTo(AudioAttributesCompat.USAGE_NOTIFICATION));
    assertThat(
        notificationAAC.getContentType(), equalTo(AudioAttributesCompat.CONTENT_TYPE_SONIFICATION));

    AudioAttributesCompat voiceCallAAC = mkBuilder(AudioManager.STREAM_VOICE_CALL).build();
    assertThat(voiceCallAAC.getUsage(), equalTo(AudioAttributesCompat.USAGE_VOICE_COMMUNICATION));
    assertThat(voiceCallAAC.getContentType(), equalTo(AudioAttributesCompat.CONTENT_TYPE_SPEECH));
  }

  @After
  public void cleanUp() {
    AudioAttributesCompat.setForceLegacyBehavior(false);
  }
}
