/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.media3.session;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_USER_RATING;
import static androidx.media3.common.Player.STATE_ENDED;
import static androidx.media3.common.Player.STATE_READY;
import static androidx.media3.test.session.common.MediaSessionConstants.NOTIFICATION_CONTROLLER_KEY;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_MEDIA_CONTROLLER_COMPAT_CALLBACK_WITH_MEDIA_SESSION_TEST;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_SET_SHOW_PLAY_BUTTON_IF_SUPPRESSED_TO_FALSE;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static androidx.test.ext.truth.os.BundleSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.HeartRating;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Player.State;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.PollingCheck;
import androidx.media3.test.session.common.SurfaceActivity;
import androidx.media3.test.session.common.TestHandler;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaControllerCompat.Callback} with {@link MediaSession}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerCompatCallbackWithMediaSessionTest {

  private static final String SESSION_ID =
      TEST_MEDIA_CONTROLLER_COMPAT_CALLBACK_WITH_MEDIA_SESSION_TEST;

  @Rule public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(SESSION_ID);

  private Context context;
  private TestHandler handler;
  private RemoteMediaSession session;
  private MediaControllerCompat controllerCompat;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    handler = threadTestRule.getHandler();
    Bundle tokenExtras = new Bundle();
    tokenExtras.putBoolean(
        MediaSessionProviderService.KEY_ENABLE_FAKE_MEDIA_NOTIFICATION_MANAGER_CONTROLLER, true);
    session = new RemoteMediaSession(SESSION_ID, context, tokenExtras);
    controllerCompat = new MediaControllerCompat(context, session.getCompatToken());
  }

  @After
  public void cleanUp() throws Exception {
    session.release();
  }

  @Test
  public void gettersAfterConnected() throws Exception {
    @State int testState = STATE_READY;
    int testBufferingPosition = 1500;
    float testSpeed = 1.5f;
    int testItemIndex = 0;
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    testMediaItems.set(
        testItemIndex,
        new MediaItem.Builder()
            .setMediaId(testMediaItems.get(testItemIndex).mediaId)
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setUserRating(new HeartRating(/* isHeart= */ true))
                    .build())
            .build());
    Timeline testTimeline = new PlaylistTimeline(testMediaItems);
    String testPlaylistTitle = "testPlaylistTitle";
    MediaMetadata testPlaylistMetadata =
        new MediaMetadata.Builder().setTitle(testPlaylistTitle).build();
    boolean testShuffleModeEnabled = true;
    @RepeatMode int testRepeatMode = Player.REPEAT_MODE_ONE;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(testState)
            .setPlayWhenReady(true)
            .setBufferedPosition(testBufferingPosition)
            .setPlaybackParameters(new PlaybackParameters(testSpeed))
            .setTimeline(testTimeline)
            .setMediaMetadata(testMediaItems.get(testItemIndex).mediaMetadata)
            .setPlaylistMetadata(testPlaylistMetadata)
            .setCurrentMediaItemIndex(testItemIndex)
            .setShuffleModeEnabled(testShuffleModeEnabled)
            .setRepeatMode(testRepeatMode)
            .build();
    session.setPlayer(playerConfig);

    MediaControllerCompat controller = new MediaControllerCompat(context, session.getCompatToken());
    CountDownLatch latch = new CountDownLatch(1);
    controller.registerCallback(
        new MediaControllerCompat.Callback() {
          @Override
          public void onSessionReady() {
            latch.countDown();
          }
        },
        handler);

    assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(controller.getPlaybackState().getState())
        .isEqualTo(PlaybackStateCompat.STATE_PLAYING);
    assertThat(controller.getPlaybackState().getBufferedPosition())
        .isEqualTo(testBufferingPosition);
    assertThat(controller.getPlaybackState().getPlaybackSpeed()).isEqualTo(testSpeed);

    assertThat(controller.getMetadata().getString(METADATA_KEY_MEDIA_ID))
        .isEqualTo(testMediaItems.get(testItemIndex).mediaId);
    assertThat(controller.getRatingType()).isEqualTo(RatingCompat.RATING_HEART);

    List<QueueItem> queue = controller.getQueue();
    assertThat(queue).isNotNull();
    assertThat(queue).hasSize(testTimeline.getWindowCount());
    for (int i = 0; i < testTimeline.getWindowCount(); i++) {
      assertThat(queue.get(i).getDescription().getMediaId())
          .isEqualTo(testMediaItems.get(i).mediaId);
    }
    assertThat(testPlaylistTitle).isEqualTo(controller.getQueueTitle().toString());
    assertThat(PlaybackStateCompat.SHUFFLE_MODE_ALL).isEqualTo(controller.getShuffleMode());
    assertThat(PlaybackStateCompat.REPEAT_MODE_ONE).isEqualTo(controller.getRepeatMode());
  }

  @Test
  public void getError_withPlayerErrorAfterConnected_returnsError() throws Exception {
    PlaybackException testPlayerError =
        new PlaybackException(
            /* message= */ "testremote",
            /* cause= */ null,
            PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setPlayerError(testPlayerError).build();
    session.setPlayer(playerConfig);

    MediaControllerCompat controller = new MediaControllerCompat(context, session.getCompatToken());
    CountDownLatch latch = new CountDownLatch(1);
    controller.registerCallback(
        new MediaControllerCompat.Callback() {
          @Override
          public void onSessionReady() {
            latch.countDown();
          }
        },
        handler);

    assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    PlaybackStateCompat state = controller.getPlaybackState();
    assertThat(state.getState()).isEqualTo(PlaybackStateCompat.STATE_ERROR);
    assertThat(state.getErrorCode())
        .isEqualTo(PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED);
    assertThat(state.getErrorMessage().toString()).isEqualTo(testPlayerError.getMessage());
  }

  @Test
  public void playerError_notified() throws Exception {
    PlaybackException testPlayerError =
        new PlaybackException(
            /* message= */ "player error",
            /* cause= */ null,
            PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateCompatRef.set(state);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().notifyPlayerError(testPlayerError);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    PlaybackStateCompat state = playbackStateCompatRef.get();
    assertThat(state.getState()).isEqualTo(PlaybackStateCompat.STATE_ERROR);
    assertThat(state.getErrorCode())
        .isEqualTo(PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED);
    assertThat(state.getErrorMessage().toString()).isEqualTo(testPlayerError.getMessage());
  }

  @Test
  public void sendError_toAllControllers_onPlaybackStateChangedToErrorStateAndWithCorrectErrorData()
      throws Exception {
    CountDownLatch latch = new CountDownLatch(3);
    List<PlaybackStateCompat> playbackStates = new ArrayList<>();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStates.add(state);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("initialKey", "initialValue");
    session.setSessionExtras(sessionExtras);
    PlaybackStateCompat initialPlaybackStateCompat = controllerCompat.getPlaybackState();
    Bundle errorBundle = new Bundle();
    errorBundle.putInt("errorKey", 99);

    session.sendError(
        /* controllerKey= */ null,
        new SessionError(
            /* code= */ SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
            /* message= */ ApplicationProvider.getApplicationContext()
                .getString(R.string.error_message_authentication_expired),
            errorBundle));

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStates).hasSize(3);

    // Skip the playback state from the first setSessionExtras() call.
    PlaybackStateCompat errorPlaybackStateCompat = playbackStates.get(1);
    assertThat(errorPlaybackStateCompat.getState()).isNotEqualTo(PlaybackStateCompat.STATE_ERROR);
    assertThat(errorPlaybackStateCompat.getState())
        .isEqualTo(initialPlaybackStateCompat.getState());
    assertThat(errorPlaybackStateCompat.getErrorCode())
        .isEqualTo(PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED);
    assertThat(errorPlaybackStateCompat.getErrorMessage().toString())
        .isEqualTo(context.getString(R.string.error_message_authentication_expired));
    assertThat(errorPlaybackStateCompat.getExtras()).hasSize(3);
    assertThat(errorPlaybackStateCompat.getExtras()).integer("errorKey").isEqualTo(99);
    assertThat(errorPlaybackStateCompat.getExtras()).string("initialKey").isEqualTo("initialValue");
    assertThat(errorPlaybackStateCompat.getExtras()).containsKey("EXO_SPEED");

    PlaybackStateCompat resolvedPlaybackStateCompat = playbackStates.get(2);
    assertThat(resolvedPlaybackStateCompat.getState())
        .isEqualTo(initialPlaybackStateCompat.getState());
    assertThat(resolvedPlaybackStateCompat.getErrorCode())
        .isEqualTo(initialPlaybackStateCompat.getErrorCode());
    assertThat(resolvedPlaybackStateCompat.getErrorMessage()).isNull();
    assertThat(resolvedPlaybackStateCompat.getActions())
        .isEqualTo(initialPlaybackStateCompat.getActions());
    assertThat(
            TestUtils.equals(
                resolvedPlaybackStateCompat.getExtras(), initialPlaybackStateCompat.getExtras()))
        .isTrue();
  }

  @Test
  public void
      sendError_toMediaNotificationController_onPlaybackStateChangedToErrorStateAndWithCorrectErrorData()
          throws Exception {
    CountDownLatch latch = new CountDownLatch(3);
    List<PlaybackStateCompat> playbackStates = new ArrayList<>();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStates.add(state);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("initialKey", "initialValue");
    session.setSessionExtras(/* controllerKey= */ NOTIFICATION_CONTROLLER_KEY, sessionExtras);
    PlaybackStateCompat initialPlaybackStateCompat = controllerCompat.getPlaybackState();
    Bundle errorBundle = new Bundle();
    errorBundle.putInt("errorKey", 99);

    session.sendError(
        /* controllerKey= */ NOTIFICATION_CONTROLLER_KEY,
        new SessionError(
            SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
            /* message= */ ApplicationProvider.getApplicationContext()
                .getString(R.string.error_message_authentication_expired),
            errorBundle));

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStates).hasSize(3);

    // Skip the playback state from the first setSessionExtras() call.
    PlaybackStateCompat errorPlaybackStateCompat = playbackStates.get(1);
    assertThat(errorPlaybackStateCompat.getState())
        .isEqualTo(initialPlaybackStateCompat.getState());
    assertThat(errorPlaybackStateCompat.getState()).isNotEqualTo(PlaybackStateCompat.STATE_ERROR);
    assertThat(errorPlaybackStateCompat.getErrorCode())
        .isEqualTo(PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED);
    assertThat(errorPlaybackStateCompat.getErrorMessage().toString())
        .isEqualTo(context.getString(R.string.error_message_authentication_expired));
    assertThat(errorPlaybackStateCompat.getActions())
        .isEqualTo(initialPlaybackStateCompat.getActions());
    assertThat(errorPlaybackStateCompat.getExtras()).hasSize(3);
    assertThat(errorPlaybackStateCompat.getExtras()).string("initialKey").isEqualTo("initialValue");
    assertThat(errorPlaybackStateCompat.getExtras()).integer("errorKey").isEqualTo(99);
    assertThat(errorPlaybackStateCompat.getExtras()).containsKey("EXO_SPEED");

    PlaybackStateCompat resolvedPlaybackStateCompat = playbackStates.get(2);
    assertThat(resolvedPlaybackStateCompat.getState())
        .isEqualTo(initialPlaybackStateCompat.getState());
    assertThat(resolvedPlaybackStateCompat.getErrorCode())
        .isEqualTo(initialPlaybackStateCompat.getErrorCode());
    assertThat(resolvedPlaybackStateCompat.getErrorMessage()).isNull();
    assertThat(resolvedPlaybackStateCompat.getActions())
        .isEqualTo(initialPlaybackStateCompat.getActions());
    assertThat(
            TestUtils.equals(
                resolvedPlaybackStateCompat.getExtras(), initialPlaybackStateCompat.getExtras()))
        .isTrue();
  }

  @Test
  public void repeatModeChange() throws Exception {
    @PlaybackStateCompat.RepeatMode int testRepeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger repeatModeRef = new AtomicInteger();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onRepeatModeChanged(@PlaybackStateCompat.RepeatMode int repeatMode) {
            repeatModeRef.set(repeatMode);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().setRepeatMode(Player.REPEAT_MODE_ALL);
    session.getMockPlayer().notifyRepeatModeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(repeatModeRef.get()).isEqualTo(testRepeatMode);
    assertThat(controllerCompat.getRepeatMode()).isEqualTo(testRepeatMode);
  }

  @Test
  public void shuffleModeChange() throws Exception {
    @PlaybackStateCompat.ShuffleMode
    int testShuffleModeEnabled = PlaybackStateCompat.SHUFFLE_MODE_ALL;
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger shuffleModeRef = new AtomicInteger();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onShuffleModeChanged(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
            shuffleModeRef.set(shuffleMode);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().setShuffleModeEnabled(/* shuffleModeEnabled= */ true);
    session.getMockPlayer().notifyShuffleModeEnabledChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(shuffleModeRef.get()).isEqualTo(testShuffleModeEnabled);
    assertThat(controllerCompat.getShuffleMode()).isEqualTo(testShuffleModeEnabled);
  }

  @Test
  public void release() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onSessionDestroyed() {
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.release();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void setPlayer_isNotified() throws Exception {
    @State int testState = STATE_READY;
    boolean testPlayWhenReady = true;
    long testDurationMs = 200;
    long testCurrentPositionMs = 11;
    long testBufferedPositionMs = 100;
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 1.5f);
    int testItemIndex = 0;
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    testMediaItems.set(
        testItemIndex,
        new MediaItem.Builder()
            .setMediaId(testMediaItems.get(testItemIndex).mediaId)
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setUserRating(new HeartRating(/* isHeart= */ true))
                    .build())
            .build());
    Timeline testTimeline = new PlaylistTimeline(testMediaItems);
    String testPlaylistTitle = "testPlaylistTitle";
    MediaMetadata testPlaylistMetadata =
        new MediaMetadata.Builder().setTitle(testPlaylistTitle).build();
    boolean testShuffleModeEnabled = true;
    @RepeatMode int testRepeatMode = Player.REPEAT_MODE_ONE;
    AtomicReference<PlaybackStateCompat> playbackStateRef = new AtomicReference<>();
    AtomicReference<MediaMetadataCompat> metadataRef = new AtomicReference<>();
    AtomicReference<CharSequence> queueTitleRef = new AtomicReference<>();
    AtomicInteger shuffleModeRef = new AtomicInteger();
    AtomicInteger repeatModeRef = new AtomicInteger();
    CountDownLatch latchForPlaybackState = new CountDownLatch(1);
    CountDownLatch latchForMetadata = new CountDownLatch(1);
    CountDownLatch latchForQueue = new CountDownLatch(2);
    CountDownLatch latchForShuffleMode = new CountDownLatch(1);
    CountDownLatch latchForRepeatMode = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateRef.set(state);
            latchForPlaybackState.countDown();
          }

          @Override
          public void onMetadataChanged(MediaMetadataCompat metadata) {
            metadataRef.set(metadata);
            latchForMetadata.countDown();
          }

          @Override
          public void onQueueChanged(List<QueueItem> queue) {
            latchForQueue.countDown();
          }

          @Override
          public void onQueueTitleChanged(CharSequence title) {
            queueTitleRef.set(title);
            latchForQueue.countDown();
          }

          @Override
          public void onRepeatModeChanged(int repeatMode) {
            repeatModeRef.set(repeatMode);
            latchForRepeatMode.countDown();
          }

          @Override
          public void onShuffleModeChanged(int shuffleMode) {
            shuffleModeRef.set(shuffleMode);
            latchForShuffleMode.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(testState)
            .setPlayWhenReady(testPlayWhenReady)
            .setCurrentPosition(testCurrentPositionMs)
            .setBufferedPosition(testBufferedPositionMs)
            .setDuration(testDurationMs)
            .setPlaybackParameters(playbackParameters)
            .setTimeline(testTimeline)
            .setMediaMetadata(testMediaItems.get(testItemIndex).mediaMetadata)
            .setPlaylistMetadata(testPlaylistMetadata)
            .setCurrentMediaItemIndex(testItemIndex)
            .setShuffleModeEnabled(testShuffleModeEnabled)
            .setRepeatMode(testRepeatMode)
            .build();

    session.setPlayer(playerConfig);

    assertThat(latchForPlaybackState.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateRef.get().getBufferedPosition()).isEqualTo(testBufferedPositionMs);
    assertThat(playbackStateRef.get().getPosition()).isEqualTo(testCurrentPositionMs);
    assertThat(playbackStateRef.get().getPlaybackSpeed()).isEqualTo(playbackParameters.speed);
    assertThat(latchForMetadata.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(metadataRef.get().getString(METADATA_KEY_MEDIA_ID))
        .isEqualTo(testMediaItems.get(testItemIndex).mediaId);
    assertThat(metadataRef.get().getLong(METADATA_KEY_DURATION)).isEqualTo(testDurationMs);
    assertThat(playbackStateRef.get().getState()).isEqualTo(PlaybackStateCompat.STATE_PLAYING);
    assertThat(metadataRef.get().getRating(METADATA_KEY_USER_RATING).hasHeart()).isTrue();
    assertThat(latchForQueue.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    List<QueueItem> queue = controllerCompat.getQueue();
    assertThat(queue).hasSize(testTimeline.getWindowCount());
    for (int i = 0; i < testTimeline.getWindowCount(); i++) {
      assertThat(queue.get(i).getDescription().getMediaId())
          .isEqualTo(testMediaItems.get(i).mediaId);
    }
    assertThat(queueTitleRef.get().toString()).isEqualTo(testPlaylistTitle);
    assertThat(latchForShuffleMode.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(shuffleModeRef.get()).isEqualTo(PlaybackStateCompat.SHUFFLE_MODE_ALL);
    assertThat(latchForRepeatMode.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(repeatModeRef.get()).isEqualTo(PlaybackStateCompat.REPEAT_MODE_ONE);
  }

  @Test
  public void setPlayer_playbackTypeChangedToRemote() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(25).build();
    int legacyPlaybackType = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE;
    int deviceVolume = 10;
    CountDownLatch playbackInfoNotified = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            if (info.getPlaybackType() == legacyPlaybackType
                && info.getMaxVolume() == deviceInfo.maxVolume
                && info.getCurrentVolume() == deviceVolume) {
              playbackInfoNotified.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(deviceVolume)
            .build();

    session.setPlayer(playerConfig);

    assertThat(playbackInfoNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
    assertThat(info.getPlaybackType()).isEqualTo(legacyPlaybackType);
    assertThat(info.getMaxVolume()).isEqualTo(deviceInfo.maxVolume);
    assertThat(info.getCurrentVolume()).isEqualTo(deviceVolume);
  }

  @Test
  public void setPlayer_playbackTypeChangedToLocal() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(10).build();
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceInfo(deviceInfo).build();
    session.setPlayer(playerConfig);
    DeviceInfo deviceInfoToUpdate =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).setMaxVolume(10).build();
    int legacyPlaybackTypeToUpdate = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL;
    int legacyStream = AudioManager.STREAM_RING;
    AudioAttributes attrs =
        new AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
            .setUsage(C.USAGE_NOTIFICATION_RINGTONE)
            .build();
    CountDownLatch playbackInfoNotified = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            if (info.getPlaybackType() == legacyPlaybackTypeToUpdate
                && info.getAudioAttributes().getLegacyStreamType() == legacyStream) {
              playbackInfoNotified.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);
    Bundle playerConfigToUpdate =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfoToUpdate)
            .setAudioAttributes(attrs)
            .build();

    session.setPlayer(playerConfigToUpdate);

    // In API 21 and 22, onAudioInfoChanged is not called when playback is changed to local.
    if (Util.SDK_INT <= 22) {
      PollingCheck.waitFor(
          TIMEOUT_MS,
          () -> {
            MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
            return info.getPlaybackType() == legacyPlaybackTypeToUpdate
                && info.getAudioAttributes().getLegacyStreamType() == legacyStream;
          });
    } else {
      assertThat(playbackInfoNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
      MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
      assertThat(info.getPlaybackType()).isEqualTo(legacyPlaybackTypeToUpdate);
      assertThat(info.getAudioAttributes().getLegacyStreamType()).isEqualTo(legacyStream);
    }
  }

  @Test
  public void setPlayer_playbackTypeNotChanged_local() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).setMaxVolume(10).build();
    int legacyPlaybackType = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL;
    int legacyStream = AudioManager.STREAM_RING;
    AudioAttributes attrs =
        new AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
            .setUsage(C.USAGE_NOTIFICATION_RINGTONE)
            .build();
    CountDownLatch playbackInfoNotified = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            if (info.getPlaybackType() == legacyPlaybackType
                && info.getAudioAttributes().getLegacyStreamType() == legacyStream) {
              playbackInfoNotified.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfo)
            .setAudioAttributes(attrs)
            .build();

    session.setPlayer(playerConfig);

    PollingCheck.waitFor(
        TIMEOUT_MS,
        () -> {
          MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
          return info.getPlaybackType() == legacyPlaybackType
              && info.getAudioAttributes().getLegacyStreamType() == legacyStream;
        });
  }

  @Test
  public void setPlayer_playbackTypeNotChanged_remote() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(10).build();
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(1)
            .build();
    session.setPlayer(playerConfig);
    DeviceInfo deviceInfoToUpdate =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(25).build();
    int legacyPlaybackTypeToUpdate = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE;
    int deviceVolumeToUpdate = 10;
    CountDownLatch playbackInfoNotified = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            if (info.getPlaybackType() == legacyPlaybackTypeToUpdate
                && info.getMaxVolume() == deviceInfoToUpdate.maxVolume
                && info.getCurrentVolume() == deviceVolumeToUpdate) {
              playbackInfoNotified.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);
    Bundle playerConfigToUpdate =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfoToUpdate)
            .setDeviceVolume(deviceVolumeToUpdate)
            .build();

    session.setPlayer(playerConfigToUpdate);

    PollingCheck.waitFor(
        TIMEOUT_MS,
        () -> {
          MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
          return info.getPlaybackType() == legacyPlaybackTypeToUpdate
              && info.getMaxVolume() == deviceInfoToUpdate.maxVolume
              && info.getCurrentVolume() == deviceVolumeToUpdate;
        });
  }

  @Test
  public void onPlaybackParametersChanged_notifiesPlaybackStateCompatChanges() throws Exception {
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 1.5f);
    session.getMockPlayer().setPlaybackState(Player.STATE_READY);
    session
        .getMockPlayer()
        .setPlayWhenReady(/* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    AtomicReference<PlaybackStateCompat> playbackStateRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateRef.set(state);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().notifyPlaybackParametersChanged(playbackParameters);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateRef.get().getPlaybackSpeed()).isEqualTo(playbackParameters.speed);
    assertThat(
            playbackStateRef
                .get()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(playbackParameters.speed);
    assertThat(controllerCompat.getPlaybackState().getPlaybackSpeed())
        .isEqualTo(playbackParameters.speed);
    assertThat(
            controllerCompat
                .getPlaybackState()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(playbackParameters.speed);
  }

  @Test
  public void playbackStateChange_playWhenReadyBecomesFalseWhenReady_notifiesPaused()
      throws Exception {
    session
        .getMockPlayer()
        .setPlayWhenReady(/* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    session.getMockPlayer().notifyPlaybackStateChanged(STATE_READY);
    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            playbackStateCompatRef.set(playbackStateCompat);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session
        .getMockPlayer()
        .notifyPlayWhenReadyChanged(
            /* playWhenReady= */ false,
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateCompatRef.get().getState()).isEqualTo(PlaybackStateCompat.STATE_PAUSED);
    assertThat(playbackStateCompatRef.get().getPlaybackSpeed()).isEqualTo(0f);
    assertThat(
            playbackStateCompatRef
                .get()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
    assertThat(controllerCompat.getPlaybackState().getState())
        .isEqualTo(PlaybackStateCompat.STATE_PAUSED);
    assertThat(controllerCompat.getPlaybackState().getPlaybackSpeed()).isEqualTo(0f);
    assertThat(
            controllerCompat
                .getPlaybackState()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
  }

  @Test
  public void playbackStateChange_playWhenReadyBecomesTrueWhenBuffering_notifiesBuffering()
      throws Exception {
    session
        .getMockPlayer()
        .setPlayWhenReady(/* playWhenReady= */ false, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    session.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_BUFFERING);
    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            playbackStateCompatRef.set(playbackStateCompat);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session
        .getMockPlayer()
        .notifyPlayWhenReadyChanged(
            /* playWhenReady= */ true,
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateCompatRef.get().getState())
        .isEqualTo(PlaybackStateCompat.STATE_BUFFERING);
    assertThat(playbackStateCompatRef.get().getPlaybackSpeed()).isEqualTo(0f);
    assertThat(
            playbackStateCompatRef
                .get()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
    assertThat(controllerCompat.getPlaybackState().getState())
        .isEqualTo(PlaybackStateCompat.STATE_BUFFERING);
    assertThat(controllerCompat.getPlaybackState().getPlaybackSpeed()).isEqualTo(0f);
    assertThat(
            controllerCompat
                .getPlaybackState()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
  }

  @Test
  public void playbackStateChange_playbackStateBecomesEnded_notifiesStopped() throws Exception {
    session
        .getMockPlayer()
        .setPlayWhenReady(/* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    session.getMockPlayer().notifyPlaybackStateChanged(STATE_READY);
    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            playbackStateCompatRef.set(playbackStateCompat);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().notifyPlaybackStateChanged(STATE_ENDED);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateCompatRef.get().getState())
        .isEqualTo(PlaybackStateCompat.STATE_STOPPED);
    assertThat(playbackStateCompatRef.get().getPlaybackSpeed()).isEqualTo(0f);
    assertThat(
            playbackStateCompatRef
                .get()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
    assertThat(controllerCompat.getPlaybackState().getState())
        .isEqualTo(PlaybackStateCompat.STATE_STOPPED);
    assertThat(controllerCompat.getPlaybackState().getPlaybackSpeed()).isEqualTo(0f);
    assertThat(
            controllerCompat
                .getPlaybackState()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
  }

  @Test
  public void playbackStateChange_withPlaybackSuppression_notifiesPaused() throws Exception {
    session.getMockPlayer().setPlaybackState(Player.STATE_READY);
    session
        .getMockPlayer()
        .setPlayWhenReady(/* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            playbackStateCompatRef.set(playbackStateCompat);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session
        .getMockPlayer()
        .notifyPlayWhenReadyChanged(
            /* playWhenReady= */ true,
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateCompatRef.get().getState()).isEqualTo(PlaybackStateCompat.STATE_PAUSED);
    assertThat(playbackStateCompatRef.get().getPlaybackSpeed()).isEqualTo(0f);
    assertThat(
            playbackStateCompatRef
                .get()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
    assertThat(controllerCompat.getPlaybackState().getState())
        .isEqualTo(PlaybackStateCompat.STATE_PAUSED);
    assertThat(controllerCompat.getPlaybackState().getPlaybackSpeed()).isEqualTo(0f);
    assertThat(
            controllerCompat
                .getPlaybackState()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
  }

  @Test
  public void
      playbackStateChange_withPlaybackSuppressionWithoutShowPauseIfSuppressed_notifiesPlayingWithSpeedZero()
          throws Exception {
    RemoteMediaSession session =
        new RemoteMediaSession(TEST_SET_SHOW_PLAY_BUTTON_IF_SUPPRESSED_TO_FALSE, context, null);
    MediaControllerCompat controllerCompat =
        new MediaControllerCompat(context, session.getCompatToken());
    session.getMockPlayer().setPlaybackState(Player.STATE_READY);
    session
        .getMockPlayer()
        .setPlayWhenReady(/* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            playbackStateCompatRef.set(playbackStateCompat);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session
        .getMockPlayer()
        .notifyPlayWhenReadyChanged(
            /* playWhenReady= */ true,
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateCompatRef.get().getState())
        .isEqualTo(PlaybackStateCompat.STATE_PLAYING);
    assertThat(playbackStateCompatRef.get().getPlaybackSpeed()).isEqualTo(0f);
    assertThat(
            playbackStateCompatRef
                .get()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
    assertThat(controllerCompat.getPlaybackState().getState())
        .isEqualTo(PlaybackStateCompat.STATE_PLAYING);
    assertThat(controllerCompat.getPlaybackState().getPlaybackSpeed()).isEqualTo(0f);
    assertThat(
            controllerCompat
                .getPlaybackState()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
    session.release();
  }

  @Test
  public void playbackStateChange_playWhenReadyBecomesTrueWhenReady_notifiesPlaying()
      throws Exception {
    session.getMockPlayer().setPlaybackState(Player.STATE_READY);
    session
        .getMockPlayer()
        .setPlayWhenReady(/* playWhenReady= */ false, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            playbackStateCompatRef.set(playbackStateCompat);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session
        .getMockPlayer()
        .notifyPlayWhenReadyChanged(
            /* playWhenReady= */ true,
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateCompatRef.get().getState())
        .isEqualTo(PlaybackStateCompat.STATE_PLAYING);
    assertThat(playbackStateCompatRef.get().getPlaybackSpeed()).isEqualTo(1f);
    assertThat(
            playbackStateCompatRef
                .get()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
    assertThat(controllerCompat.getPlaybackState().getState())
        .isEqualTo(PlaybackStateCompat.STATE_PLAYING);
    assertThat(controllerCompat.getPlaybackState().getPlaybackSpeed()).isEqualTo(1f);
    assertThat(
            controllerCompat
                .getPlaybackState()
                .getExtras()
                .getFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT))
        .isEqualTo(1f);
  }

  @Test
  public void playbackStateChange_positionDiscontinuityNotifies_updatesPosition() throws Exception {
    long testSeekPosition = 1300;
    AtomicReference<PlaybackStateCompat> playbackStateRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateRef.set(state);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);
    session.getMockPlayer().setCurrentPosition(testSeekPosition);

    session
        .getMockPlayer()
        .notifyPositionDiscontinuity(
            /* oldPosition= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
            /* newPosition= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
            Player.DISCONTINUITY_REASON_SEEK);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateRef.get().getPosition()).isEqualTo(testSeekPosition);
    assertThat(controllerCompat.getPlaybackState().getPosition()).isEqualTo(testSeekPosition);
  }

  @Test
  public void setCustomLayout_onPlaybackStateCompatChangedCalled() throws Exception {
    Bundle extras1 = new Bundle();
    extras1.putString("key", "value-1");
    SessionCommand command1 = new SessionCommand("command1", extras1);
    Bundle extras2 = new Bundle();
    extras2.putString("key", "value-2");
    SessionCommand command2 = new SessionCommand("command2", extras2);
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setSessionCommand(command1)
                .setDisplayName("command1")
                .setCustomIconResId(1)
                .build()
                .copyWithIsEnabled(true),
            new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setSessionCommand(command2)
                .setDisplayName("command2")
                .setCustomIconResId(2)
                .build()
                .copyWithIsEnabled(true));
    List<PlaybackStateCompat> reportedPlaybackStates = new ArrayList<>();
    CountDownLatch latch1 = new CountDownLatch(2);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            reportedPlaybackStates.add(state);
            latch1.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.setCustomLayout(customLayout);
    session.setAvailableCommands(
        SessionCommands.EMPTY.buildUpon().add(command1).add(command2).build(),
        Player.Commands.EMPTY);

    assertThat(latch1.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(reportedPlaybackStates).hasSize(2);
    assertThat(reportedPlaybackStates.get(0).getCustomActions()).hasSize(1);
    PlaybackStateCompat.CustomAction action0 =
        reportedPlaybackStates.get(0).getCustomActions().get(0);
    assertThat(action0.getAction()).isEqualTo("command1");
    assertThat(action0.getExtras().getString("key")).isEqualTo("value-1");
    assertThat(action0.getIcon()).isEqualTo(1);
    assertThat(action0.getName().toString()).isEqualTo("command1");
    assertThat(reportedPlaybackStates.get(1).getCustomActions()).hasSize(2);
    PlaybackStateCompat.CustomAction action1 =
        reportedPlaybackStates.get(1).getCustomActions().get(0);
    assertThat(action1.getAction()).isEqualTo("command1");
    assertThat(action1.getExtras().getString("key")).isEqualTo("value-1");
    assertThat(action1.getIcon()).isEqualTo(1);
    assertThat(action1.getName().toString()).isEqualTo("command1");
    PlaybackStateCompat.CustomAction action2 =
        reportedPlaybackStates.get(1).getCustomActions().get(1);
    assertThat(action2.getAction()).isEqualTo("command2");
    assertThat(action2.getExtras().getString("key")).isEqualTo("value-2");
    assertThat(action2.getIcon()).isEqualTo(2);
    assertThat(action2.getName().toString()).isEqualTo("command2");
  }

  @Test
  public void setSessionExtras_toAllControllers_extrasAndStateCallbacks() throws Exception {
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("key-0", "value-0");
    CountDownLatch onExtrasChangedCalled = new CountDownLatch(1);
    CountDownLatch onPlaybackStateChangedCalled = new CountDownLatch(1);
    AtomicReference<Bundle> sessionExtrasFromCallback = new AtomicReference<>();
    AtomicReference<Bundle> sessionExtrasFromController = new AtomicReference<>();
    AtomicReference<Bundle> playbackStateExtrasFromCallback = new AtomicReference<>();
    AtomicReference<Bundle> playbackStateExtrasFromController = new AtomicReference<>();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onExtrasChanged(Bundle extras) {
            sessionExtrasFromCallback.set(extras);
            sessionExtrasFromController.set(controllerCompat.getExtras());
            onExtrasChangedCalled.countDown();
          }

          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateExtrasFromCallback.set(state.getExtras());
            playbackStateExtrasFromController.set(controllerCompat.getPlaybackState().getExtras());
            onPlaybackStateChangedCalled.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.setSessionExtras(sessionExtras);

    assertThat(onExtrasChangedCalled.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(onPlaybackStateChangedCalled.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(sessionExtrasFromCallback.get()).hasSize(1);
    assertThat(sessionExtrasFromCallback.get()).string("key-0").isEqualTo("value-0");
    assertThat(sessionExtrasFromController.get()).hasSize(1);
    assertThat(sessionExtrasFromController.get()).string("key-0").isEqualTo("value-0");
    assertThat(playbackStateExtrasFromCallback.get()).hasSize(2);
    assertThat(playbackStateExtrasFromCallback.get())
        .doubleFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT)
        .isEqualTo(0.0);
    assertThat(playbackStateExtrasFromCallback.get()).string("key-0").isEqualTo("value-0");
    assertThat(playbackStateExtrasFromController.get()).hasSize(2);
    assertThat(playbackStateExtrasFromController.get())
        .doubleFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT)
        .isEqualTo(0.0);
    assertThat(playbackStateExtrasFromController.get()).string("key-0").isEqualTo("value-0");
  }

  @Test
  public void setSessionExtras_toMediaNotificationControllers_extrasAndStateCallbacks()
      throws Exception {
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("key-0", "value-0");
    CountDownLatch onExtrasChangedCalled = new CountDownLatch(1);
    CountDownLatch onPlaybackStateChangedCalled = new CountDownLatch(1);
    AtomicReference<Bundle> sessionExtrasFromCallback = new AtomicReference<>();
    AtomicReference<Bundle> sessionExtrasFromController = new AtomicReference<>();
    AtomicReference<Bundle> playbackStateExtrasFromCallback = new AtomicReference<>();
    AtomicReference<Bundle> playbackStateExtrasFromController = new AtomicReference<>();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onExtrasChanged(Bundle extras) {
            sessionExtrasFromCallback.set(extras);
            sessionExtrasFromController.set(controllerCompat.getExtras());
            onExtrasChangedCalled.countDown();
          }

          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateExtrasFromCallback.set(state.getExtras());
            playbackStateExtrasFromController.set(controllerCompat.getPlaybackState().getExtras());
            onPlaybackStateChangedCalled.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.setSessionExtras(/* controllerKey= */ NOTIFICATION_CONTROLLER_KEY, sessionExtras);

    assertThat(onExtrasChangedCalled.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(onPlaybackStateChangedCalled.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(sessionExtrasFromCallback.get()).hasSize(1);
    assertThat(sessionExtrasFromCallback.get()).string("key-0").isEqualTo("value-0");
    assertThat(sessionExtrasFromController.get()).hasSize(1);
    assertThat(sessionExtrasFromController.get()).string("key-0").isEqualTo("value-0");
    assertThat(playbackStateExtrasFromCallback.get()).hasSize(2);
    assertThat(playbackStateExtrasFromCallback.get())
        .doubleFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT)
        .isEqualTo(0.0);
    assertThat(playbackStateExtrasFromCallback.get()).string("key-0").isEqualTo("value-0");
    assertThat(playbackStateExtrasFromController.get()).hasSize(2);
    assertThat(playbackStateExtrasFromController.get())
        .doubleFloat(MediaConstants.EXTRAS_KEY_PLAYBACK_SPEED_COMPAT)
        .isEqualTo(0.0);
    assertThat(playbackStateExtrasFromController.get()).string("key-0").isEqualTo("value-0");
  }

  @SuppressWarnings("deprecation") // Testing access through deprecated androidx.media library
  @Test
  public void setSessionActivity_forAllControllers_changedWhenReceivedWithSetter()
      throws Exception {
    Intent intent = new Intent(context, SurfaceActivity.class);
    PendingIntent sessionActivity =
        PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    CountDownLatch playingLatch = new CountDownLatch(1);
    CountDownLatch bufferingLatch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            if (state.getState() == PlaybackStateCompat.STATE_BUFFERING) {
              bufferingLatch.countDown();
            } else if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
              playingLatch.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);
    assertThat(controllerCompat.getSessionActivity()).isNull();

    session.setSessionActivity(/* controllerKey= */ null, sessionActivity);
    // The legacy API has no change listener for the session activity. Changing the state to
    // trigger a callback.
    session
        .getMockPlayer()
        .notifyPlayWhenReadyChanged(
            true,
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    session.getMockPlayer().notifyPlaybackStateChanged(STATE_READY);

    assertThat(playingLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerCompat.getSessionActivity()).isEqualTo(sessionActivity);

    session.setSessionActivity(/* controllerKey= */ null, null);
    session.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_BUFFERING);

    assertThat(bufferingLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerCompat.getSessionActivity()).isNull();
  }

  @SuppressWarnings("deprecation") // Testing access through deprecated androidx.media library
  @Test
  public void setSessionActivity_setToNotificationController_changedWhenReceivedWithSetter()
      throws Exception {
    Intent intent = new Intent(context, SurfaceActivity.class);
    PendingIntent sessionActivity =
        PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    CountDownLatch playingLatch = new CountDownLatch(1);
    CountDownLatch bufferingLatch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            if (state.getState() == PlaybackStateCompat.STATE_BUFFERING) {
              bufferingLatch.countDown();
            } else if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
              playingLatch.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);
    assertThat(controllerCompat.getSessionActivity()).isNull();

    session.setSessionActivity(NOTIFICATION_CONTROLLER_KEY, sessionActivity);
    session
        .getMockPlayer()
        .notifyPlayWhenReadyChanged(
            true,
            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    // The legacy API has no change listener for the session activity. Changing the state to
    // trigger a callback.
    session.getMockPlayer().notifyPlaybackStateChanged(STATE_READY);

    assertThat(playingLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerCompat.getSessionActivity()).isEqualTo(sessionActivity);

    session.setSessionActivity(NOTIFICATION_CONTROLLER_KEY, null);
    session.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_BUFFERING);

    assertThat(bufferingLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerCompat.getSessionActivity()).isNull();
  }

  @Test
  public void broadcastCustomCommand_cnSessionEventCalled() throws Exception {
    Bundle commandCallExtras = new Bundle();
    commandCallExtras.putString("key-0", "value-0");
    // Specify session command extras to see that they are NOT used.
    Bundle sessionCommandExtras = new Bundle();
    sessionCommandExtras.putString("key-0", "value-1");
    SessionCommand sessionCommand = new SessionCommand("custom_action", sessionCommandExtras);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> receivedCommand = new AtomicReference<>();
    AtomicReference<Bundle> receivedCommandExtras = new AtomicReference<>();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onSessionEvent(String event, Bundle extras) {
            receivedCommand.set(event);
            receivedCommandExtras.set(extras);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.broadcastCustomCommand(sessionCommand, commandCallExtras);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedCommand.get()).isEqualTo("custom_action");
    assertThat(TestUtils.equals(receivedCommandExtras.get(), commandCallExtras)).isTrue();
  }

  @Test
  public void onMediaItemTransition_updatesLegacyMetadataAndPlaybackState_correctModelConversion()
      throws Exception {
    int testItemIndex = 3;
    long testPosition = 1234;
    String testTitle = "title";
    String testDisplayTitle = "displayTitle";
    long testDurationMs = 30_000;
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(/* size= */ 5);
    String testCurrentMediaId = testMediaItems.get(testItemIndex).mediaId;
    MediaMetadata testMediaMetadata =
        new MediaMetadata.Builder().setTitle(testTitle).setDisplayTitle(testDisplayTitle).build();
    testMediaItems.set(
        testItemIndex,
        new MediaItem.Builder()
            .setMediaId(testMediaItems.get(testItemIndex).mediaId)
            .setMediaMetadata(testMediaMetadata)
            .build());
    session.getMockPlayer().setTimeline(new PlaylistTimeline(testMediaItems));
    session.getMockPlayer().setCurrentMediaItemIndex(testItemIndex);
    session.getMockPlayer().setCurrentPosition(testPosition);
    session.getMockPlayer().setDuration(testDurationMs);
    session.getMockPlayer().setMediaMetadata(testMediaMetadata);
    AtomicReference<MediaMetadataCompat> metadataRef = new AtomicReference<>();
    AtomicReference<PlaybackStateCompat> playbackStateRef = new AtomicReference<>();
    CountDownLatch latchForMetadata = new CountDownLatch(1);
    CountDownLatch latchForPlaybackState = new CountDownLatch(1);
    List<String> callbackOrder = new ArrayList<>();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onMetadataChanged(MediaMetadataCompat metadata) {
            metadataRef.set(metadata);
            callbackOrder.add("onMetadataChanged");
            latchForMetadata.countDown();
          }

          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateRef.set(state);
            callbackOrder.add("onPlaybackStateChanged");
            latchForPlaybackState.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session
        .getMockPlayer()
        .notifyMediaItemTransition(testItemIndex, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);

    // Assert metadata.
    assertThat(latchForMetadata.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaMetadataCompat parameterMetadataCompat = metadataRef.get();
    MediaMetadataCompat getterMetadataCompat = controllerCompat.getMetadata();
    assertThat(parameterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        .isEqualTo(testTitle);
    assertThat(getterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        .isEqualTo(testTitle);
    assertThat(parameterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
        .isEqualTo(testDisplayTitle);
    assertThat(getterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
        .isEqualTo(testDisplayTitle);
    assertThat(parameterMetadataCompat.getLong(METADATA_KEY_DURATION)).isEqualTo(testDurationMs);
    assertThat(getterMetadataCompat.getLong(METADATA_KEY_DURATION)).isEqualTo(testDurationMs);
    assertThat(parameterMetadataCompat.getString(METADATA_KEY_MEDIA_ID))
        .isEqualTo(testCurrentMediaId);
    assertThat(getterMetadataCompat.getString(METADATA_KEY_MEDIA_ID)).isEqualTo(testCurrentMediaId);
    // Assert the playback state.
    assertThat(latchForPlaybackState.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateRef.get().getPosition()).isEqualTo(testPosition);
    assertThat(controllerCompat.getPlaybackState().getPosition()).isEqualTo(testPosition);
    assertThat(playbackStateRef.get().getActiveQueueItemId()).isEqualTo(testItemIndex);
    assertThat(controllerCompat.getPlaybackState().getActiveQueueItemId()).isEqualTo(testItemIndex);
    assertThat(callbackOrder)
        .containsExactly("onMetadataChanged", "onPlaybackStateChanged")
        .inOrder();
  }

  @Test
  public void
      onMediaMetadataChanged_withGetMetadataAndGetCurrentMediaItemCommand_updatesLegacyMetadata()
          throws Exception {
    int testItemIndex = 3;
    String testTitle = "title";
    String testDisplayTitle = "displayTitle";
    long testDurationMs = 30_000;
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(/* size= */ 5);
    String testCurrentMediaId = testMediaItems.get(testItemIndex).mediaId;
    MediaMetadata testMediaMetadata =
        new MediaMetadata.Builder().setTitle(testTitle).setDisplayTitle(testDisplayTitle).build();
    testMediaItems.set(
        testItemIndex,
        new MediaItem.Builder()
            .setMediaId(testMediaItems.get(testItemIndex).mediaId)
            .setMediaMetadata(testMediaMetadata)
            .build());
    session
        .getMockPlayer()
        .notifyAvailableCommandsChanged(
            new Player.Commands.Builder()
                .addAll(Player.COMMAND_GET_METADATA, Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .build());
    session.getMockPlayer().setTimeline(new PlaylistTimeline(testMediaItems));
    session.getMockPlayer().setCurrentMediaItemIndex(testItemIndex);
    session.getMockPlayer().setDuration(testDurationMs);
    AtomicReference<MediaMetadataCompat> metadataRef = new AtomicReference<>();
    CountDownLatch latchForMetadata = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onMetadataChanged(MediaMetadataCompat metadata) {
            metadataRef.set(metadata);
            latchForMetadata.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().notifyMediaMetadataChanged(testMediaMetadata);

    assertThat(latchForMetadata.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaMetadataCompat parameterMetadataCompat = metadataRef.get();
    MediaMetadataCompat getterMetadataCompat = controllerCompat.getMetadata();
    assertThat(parameterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        .isEqualTo(testTitle);
    assertThat(getterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        .isEqualTo(testTitle);
    assertThat(parameterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
        .isEqualTo(testDisplayTitle);
    assertThat(getterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
        .isEqualTo(testDisplayTitle);
    assertThat(parameterMetadataCompat.getLong(METADATA_KEY_DURATION)).isEqualTo(testDurationMs);
    assertThat(getterMetadataCompat.getLong(METADATA_KEY_DURATION)).isEqualTo(testDurationMs);
    assertThat(parameterMetadataCompat.getString(METADATA_KEY_MEDIA_ID))
        .isEqualTo(testCurrentMediaId);
    assertThat(getterMetadataCompat.getString(METADATA_KEY_MEDIA_ID)).isEqualTo(testCurrentMediaId);
  }

  @Test
  public void onMediaMetadataChanged_withGetMetadataCommandOnly_updatesLegacyMetadata()
      throws Exception {
    int testItemIndex = 3;
    String testTitle = "title";
    String testDisplayTitle = "title";
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(/* size= */ 5);
    MediaMetadata testMediaMetadata =
        new MediaMetadata.Builder().setTitle(testTitle).setDisplayTitle(testDisplayTitle).build();
    testMediaItems.set(
        testItemIndex,
        new MediaItem.Builder()
            .setMediaId(testMediaItems.get(testItemIndex).mediaId)
            .setMediaMetadata(testMediaMetadata)
            .build());
    session
        .getMockPlayer()
        .notifyAvailableCommandsChanged(
            new Player.Commands.Builder().add(Player.COMMAND_GET_METADATA).build());
    session.getMockPlayer().setTimeline(new PlaylistTimeline(testMediaItems));
    session.getMockPlayer().setCurrentMediaItemIndex(testItemIndex);
    AtomicReference<MediaMetadataCompat> metadataRef = new AtomicReference<>();
    CountDownLatch latchForMetadata = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onMetadataChanged(MediaMetadataCompat metadata) {
            metadataRef.set(metadata);
            latchForMetadata.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().notifyMediaMetadataChanged(testMediaMetadata);

    assertThat(latchForMetadata.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaMetadataCompat parameterMetadataCompat = metadataRef.get();
    MediaMetadataCompat getterMetadataCompat = controllerCompat.getMetadata();
    assertThat(parameterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        .isEqualTo(testTitle);
    assertThat(getterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        .isEqualTo(testTitle);
    assertThat(parameterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
        .isEqualTo(testDisplayTitle);
    assertThat(getterMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
        .isEqualTo(testDisplayTitle);
  }

  @Test
  public void playlistChange() throws Exception {
    AtomicReference<List<QueueItem>> queueRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onQueueChanged(List<QueueItem> queue) {
            queueRef.set(queue);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 5);

    session.getMockPlayer().setTimeline(timeline);
    session.getMockPlayer().notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    List<QueueItem> queueFromParam = queueRef.get();
    List<QueueItem> queueFromGetter = controllerCompat.getQueue();
    assertThat(queueFromParam).hasSize(timeline.getWindowCount());
    assertThat(queueFromGetter).hasSize(timeline.getWindowCount());
    Timeline.Window window = new Timeline.Window();
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      assertThat(queueFromParam.get(i).getDescription().getMediaId())
          .isEqualTo(timeline.getWindow(i, window).mediaItem.mediaId);
      assertThat(queueFromGetter.get(i).getDescription().getMediaId())
          .isEqualTo(timeline.getWindow(i, window).mediaItem.mediaId);
    }
  }

  @Test
  public void playlistChange_longList() throws Exception {
    AtomicReference<List<QueueItem>> queueRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onQueueChanged(List<QueueItem> queue) {
            queueRef.set(queue);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);
    int listSize = 5_000;

    session.getMockPlayer().createAndSetFakeTimeline(listSize);
    session.getMockPlayer().notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
    List<QueueItem> queueFromParam = queueRef.get();
    List<QueueItem> queueFromGetter = controllerCompat.getQueue();
    assertThat(queueFromParam).hasSize(listSize);
    assertThat(queueFromGetter).hasSize(listSize);
    for (int i = 0; i < queueFromParam.size(); i++) {
      assertThat(queueFromParam.get(i).getDescription().getMediaId())
          .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
      assertThat(queueFromGetter.get(i).getDescription().getMediaId())
          .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  public void playlistChange_withMetadata() throws Exception {
    AtomicReference<List<QueueItem>> queueRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onQueueChanged(List<QueueItem> queue) {
            queueRef.set(queue);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setMediaId("mediaItem_withSampleMediaMetadata")
            .setMediaMetadata(MediaTestUtils.createMediaMetadataWithArtworkData())
            .build();
    Timeline timeline = new PlaylistTimeline(ImmutableList.of(mediaItem));

    session.getMockPlayer().setTimeline(timeline);
    session.getMockPlayer().notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    List<QueueItem> queueFromParam = queueRef.get();
    assertThat(queueFromParam).hasSize(1);
    MediaDescriptionCompat description = queueFromParam.get(0).getDescription();
    assertThat(description.getMediaId()).isEqualTo(mediaItem.mediaId);
    assertThat(TextUtils.equals(description.getTitle(), mediaItem.mediaMetadata.title)).isTrue();
    assertThat(TextUtils.equals(description.getSubtitle(), mediaItem.mediaMetadata.artist))
        .isTrue();
    assertThat(TextUtils.equals(description.getDescription(), mediaItem.mediaMetadata.albumTitle))
        .isTrue();
    assertThat(description.getIconUri()).isEqualTo(mediaItem.mediaMetadata.artworkUri);
    assertThat(description.getMediaUri()).isEqualTo(mediaItem.requestMetadata.mediaUri);
    assertThat(description.getIconBitmap()).isNotNull();
    assertThat(TestUtils.equals(description.getExtras(), mediaItem.mediaMetadata.extras)).isTrue();
  }

  @Test
  public void playlistMetadataChange() throws Exception {
    AtomicReference<CharSequence> queueTitleRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onQueueTitleChanged(CharSequence title) {
            queueTitleRef.set(title);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);
    String playlistTitle = "playlistTitle";
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setTitle(playlistTitle).build();

    session.getMockPlayer().setPlaylistMetadata(playlistMetadata);
    session.getMockPlayer().notifyPlaylistMetadataChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(queueTitleRef.get().toString()).isEqualTo(playlistTitle);
  }

  @Test
  public void onAudioInfoChanged_isCalledByVolumeChange() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(10).build();
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(1)
            .build();
    session.setPlayer(playerConfig);
    int targetVolume = 3;
    CountDownLatch targetVolumeNotified = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            if (info.getCurrentVolume() == targetVolume) {
              targetVolumeNotified.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().setDeviceVolume(targetVolume, /* flags= */ 0);
    session.getMockPlayer().notifyDeviceVolumeChanged();

    assertThat(targetVolumeNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerCompat.getPlaybackInfo().getCurrentVolume()).isEqualTo(targetVolume);
  }
}
