/*
 * Copyright 2021 The Android Open Source Project
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

import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.getEventsAsList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media.VolumeProviderCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.FlagSet;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Util;
import androidx.media3.session.legacy.MediaMetadataCompat;
import androidx.media3.test.session.R;
import androidx.media3.test.session.common.CommonConstants;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.ImmutableIntArray;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for {@link MediaController.Listener} with {@link MediaSessionCompat}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerListenerWithMediaSessionCompatTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private static final int EVENT_ON_EVENTS = C.INDEX_UNSET;

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaControllerListenerWithMediaSessionCompatTest");
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private Context context;
  private RemoteMediaSessionCompat session;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    session = new RemoteMediaSessionCompat(CommonConstants.DEFAULT_TEST_NAME, context);
  }

  @After
  public void cleanUp() throws RemoteException {
    session.cleanUp();
  }

  @Test
  public void onEvents_whenOnRepeatModeChanges_isCalledAfterOtherListenerMethods()
      throws Exception {
    Player.Events testEvents =
        new Player.Events(new FlagSet.Builder().add(Player.EVENT_REPEAT_MODE_CHANGED).build());
    CopyOnWriteArrayList<Integer> listenerEventCodes = new CopyOnWriteArrayList<>();

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            listenerEventCodes.add(Player.EVENT_REPEAT_MODE_CHANGED);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            listenerEventCodes.add(EVENT_ON_EVENTS);
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_GROUP);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(listenerEventCodes)
        .containsExactly(Player.EVENT_REPEAT_MODE_CHANGED, EVENT_ON_EVENTS);
    assertThat(eventsRef.get()).isEqualTo(testEvents);
  }

  @Test
  public void setPlaybackState_withCustomActions_onSetCustomLayoutCalled() throws Exception {
    Bundle extras1 = new Bundle();
    extras1.putString("key", "value-1");
    PlaybackStateCompat.CustomAction customAction1 =
        new PlaybackStateCompat.CustomAction.Builder("action1", "actionName1", /* icon= */ 1)
            .setExtras(extras1)
            .build();
    Bundle extras2 = new Bundle();
    extras2.putString("key", "value-2");
    extras2.putInt(
        MediaConstants.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT, CommandButton.ICON_FAST_FORWARD);
    PlaybackStateCompat.CustomAction customAction2 =
        new PlaybackStateCompat.CustomAction.Builder("action2", "actionName2", /* icon= */ 2)
            .setExtras(extras2)
            .build();
    PlaybackStateCompat.Builder builder =
        new PlaybackStateCompat.Builder()
            .addCustomAction(customAction1)
            .addCustomAction(customAction2);
    List<String> receivedActions = new ArrayList<>();
    List<String> receivedDisplayNames = new ArrayList<>();
    List<String> receivedBundleValues = new ArrayList<>();
    List<Integer> receivedIconResIds = new ArrayList<>();
    List<Integer> receivedCommandCodes = new ArrayList<>();
    List<Integer> receivedIcons = new ArrayList<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public ListenableFuture<SessionResult> onSetCustomLayout(
              MediaController controller, List<CommandButton> layout) {
            for (CommandButton button : layout) {
              receivedActions.add(button.sessionCommand.customAction);
              receivedDisplayNames.add(String.valueOf(button.displayName));
              receivedBundleValues.add(button.sessionCommand.customExtras.getString("key"));
              receivedCommandCodes.add(button.sessionCommand.commandCode);
              receivedIconResIds.add(button.iconResId);
              receivedIcons.add(button.icon);
            }
            countDownLatch.countDown();
            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
          }
        });

    session.setPlaybackState(builder.build());

    assertThat(countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedActions).containsExactly("action1", "action2").inOrder();
    assertThat(receivedCommandCodes)
        .containsExactly(SessionCommand.COMMAND_CODE_CUSTOM, SessionCommand.COMMAND_CODE_CUSTOM)
        .inOrder();
    assertThat(receivedDisplayNames).containsExactly("actionName1", "actionName2").inOrder();
    assertThat(receivedIconResIds).containsExactly(1, 2).inOrder();
    assertThat(receivedBundleValues).containsExactly("value-1", "value-2").inOrder();
    assertThat(receivedIcons)
        .containsExactly(CommandButton.ICON_UNDEFINED, CommandButton.ICON_FAST_FORWARD)
        .inOrder();
  }

  @Test
  public void setPlaybackState_fatalError_callsOnPlayerErrorWithCodeMessageAndExtras()
      throws Exception {
    MediaController controller =
        controllerTestRule.createController(session.getSessionToken(), /* listener= */ null);
    CountDownLatch fatalErrorLatch = new CountDownLatch(/* count= */ 1);
    List<PlaybackException> fatalErrorExceptions = new ArrayList<>();
    Bundle fatalErrorExtras = new Bundle();
    fatalErrorExtras.putString("key-2", "value-2");
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onPlayerError(PlaybackException error) {
            fatalErrorExceptions.add(error);
            fatalErrorLatch.countDown();
          }
        });

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_ERROR, /* position= */ 0L, /* playbackSpeed= */ 1.0f)
            .setErrorMessage(
                PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED,
                ApplicationProvider.getApplicationContext()
                    .getString(R.string.error_message_authentication_expired))
            .setExtras(fatalErrorExtras)
            .build());

    assertThat(fatalErrorLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(fatalErrorExceptions).hasSize(1);
    assertThat(fatalErrorExceptions.get(0))
        .hasMessageThat()
        .isEqualTo(context.getString(R.string.error_message_authentication_expired));
    assertThat(fatalErrorExceptions.get(0).errorCode)
        .isEqualTo(PlaybackException.ERROR_CODE_AUTHENTICATION_EXPIRED);
    assertThat(TestUtils.equals(fatalErrorExceptions.get(0).extras, fatalErrorExtras)).isTrue();
  }

  @Test
  public void setPlaybackState_nonFatalError_callsOnErrorWithCodeMessageAndExtras()
      throws Exception {
    CountDownLatch nonFatalErrorLatch = new CountDownLatch(/* count= */ 1);
    List<SessionError> sessionErrors = new ArrayList<>();
    Bundle nonFatalErrorExtra = new Bundle();
    nonFatalErrorExtra.putString("key-1", "value-1");
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public void onError(MediaController controller, SessionError sessionError) {
            sessionErrors.add(sessionError);
            nonFatalErrorLatch.countDown();
          }
        });

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PLAYING,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                /* playbackSpeed= */ .0f)
            .setErrorMessage(
                PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                ApplicationProvider.getApplicationContext()
                    .getString(R.string.default_notification_channel_name))
            .setExtras(nonFatalErrorExtra)
            .build());

    assertThat(nonFatalErrorLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(sessionErrors).hasSize(1);
    assertThat(sessionErrors.get(0).message)
        .isEqualTo(context.getString(R.string.default_notification_channel_name));
    assertThat(TestUtils.equals(sessionErrors.get(0).extras, nonFatalErrorExtra)).isTrue();
  }

  @Test
  public void setSessionExtras_onExtrasChangedCalled() throws Exception {
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("key-1", "value-1");
    CountDownLatch countDownLatch = new CountDownLatch(1);
    List<Bundle> receivedSessionExtras = new ArrayList<>();
    List<Bundle> getterSessionExtras = new ArrayList<>();
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public void onExtrasChanged(MediaController controller, Bundle extras) {
            receivedSessionExtras.add(extras);
            getterSessionExtras.add(controller.getSessionExtras());
            countDownLatch.countDown();
          }
        });

    session.setExtras(sessionExtras);

    assertThat(countDownLatch.await(1_000, MILLISECONDS)).isTrue();
    assertThat(TestUtils.equals(receivedSessionExtras.get(0), sessionExtras)).isTrue();
    assertThat(TestUtils.equals(getterSessionExtras.get(0), sessionExtras)).isTrue();
  }

  @Test
  public void setSessionExtras_includedWhenConnecting() throws Exception {
    Bundle sessionExtras = new Bundle();
    sessionExtras.putString("key-1", "value-1");
    session.setExtras(sessionExtras);

    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    assertThat(
            TestUtils.equals(
                threadTestRule.getHandler().postAndSync(controller::getSessionExtras),
                sessionExtras))
        .isTrue();
  }

  @Test
  public void onPlaylistMetadataChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<MediaMetadata> playlistMetadataParamRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> playlistMetadataGetterRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> playlistMetadataOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEvents = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaylistMetadataChanged(MediaMetadata mediaMetadata) {
            playlistMetadataParamRef.set(mediaMetadata);
            playlistMetadataGetterRef.set(controller.getPlaylistMetadata());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEvents.set(events);
            playlistMetadataOnEventsRef.set(player.getPlaylistMetadata());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setQueueTitle("queue-title");

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playlistMetadataParamRef.get().title.toString()).isEqualTo("queue-title");
    assertThat(playlistMetadataGetterRef.get()).isEqualTo(playlistMetadataParamRef.get());
    assertThat(playlistMetadataOnEventsRef.get()).isEqualTo(playlistMetadataParamRef.get());
    assertThat(getEventsAsList(onEvents.get()))
        .containsExactly(Player.EVENT_PLAYLIST_METADATA_CHANGED);
  }

  @Test
  public void onAudioAttributesChanged() throws Exception {
    // We need to trigger MediaControllerCompat.Callback.onAudioInfoChanged in order to raise the
    // onAudioAttributesChanged() callback. In API 21 and 22, onAudioInfoChanged is not called when
    // playback is changed to local.
    assumeTrue(Util.SDK_INT > 22);

    session.setPlaybackToRemote(
        /* volumeControl= */ VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
        /* maxVolume= */ 100,
        /* currentVolume= */ 50,
        /* routingControllerId= */ "route");
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<AudioAttributes> audioAttributesParamRef = new AtomicReference<>();
    AtomicReference<AudioAttributes> audioAttributesGetterRef = new AtomicReference<>();
    AtomicReference<AudioAttributes> audioAttributesOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEvents = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
            audioAttributesParamRef.set(audioAttributes);
            audioAttributesGetterRef.set(controller.getAudioAttributes());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEvents.set(events);
            audioAttributesOnEventsRef.set(player.getAudioAttributes());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackToLocal(AudioManager.STREAM_ALARM);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(audioAttributesGetterRef.get().contentType).isEqualTo(AudioManager.STREAM_ALARM);
    assertThat(audioAttributesGetterRef.get()).isEqualTo(audioAttributesParamRef.get());
    assertThat(audioAttributesOnEventsRef.get()).isEqualTo(audioAttributesParamRef.get());
    assertThat(getEventsAsList(onEvents.get())).contains(Player.EVENT_AUDIO_ATTRIBUTES_CHANGED);
  }

  @Test
  public void onDeviceInfoChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<DeviceInfo> deviceInfoParamRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoGetterRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEvents = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
            deviceInfoParamRef.set(deviceInfo);
            deviceInfoGetterRef.set(controller.getDeviceInfo());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            deviceInfoOnEventsRef.set(player.getDeviceInfo());
            onEvents.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    String testRoutingSessionId = Util.SDK_INT >= 30 ? "route" : null;

    session.setPlaybackToRemote(
        /* volumeControl= */ VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
        /* maxVolume= */ 100,
        /* currentVolume= */ 50,
        testRoutingSessionId);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceInfoParamRef.get().playbackType).isEqualTo(DeviceInfo.PLAYBACK_TYPE_REMOTE);
    assertThat(deviceInfoParamRef.get().maxVolume).isEqualTo(100);
    assertThat(deviceInfoParamRef.get().routingControllerId).isEqualTo(testRoutingSessionId);
    assertThat(deviceInfoGetterRef.get()).isEqualTo(deviceInfoParamRef.get());
    assertThat(deviceInfoOnEventsRef.get()).isEqualTo(deviceInfoGetterRef.get());
    assertThat(getEventsAsList(onEvents.get())).contains(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void onDeviceVolumeChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger deviceVolumeParam = new AtomicInteger();
    AtomicInteger deviceVolumeGetter = new AtomicInteger();
    AtomicInteger deviceVolumeOnEvents = new AtomicInteger();
    AtomicReference<Player.Events> onEvents = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeParam.set(volume);
            deviceVolumeGetter.set(controller.getDeviceVolume());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            deviceVolumeOnEvents.set(player.getDeviceVolume());
            onEvents.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    session.setPlaybackToRemote(
        /* volumeControl= */ VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
        /* maxVolume= */ 100,
        /* currentVolume= */ 50,
        /* routingControllerId= */ "route");

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeParam.get()).isEqualTo(50);
    assertThat(deviceVolumeGetter.get()).isEqualTo(50);
    assertThat(deviceVolumeOnEvents.get()).isEqualTo(50);
    assertThat(getEventsAsList(onEvents.get())).contains(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void getCustomLayout() throws Exception {
    CommandButton button1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button1")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(new SessionCommand("command1", Bundle.EMPTY))
            .setEnabled(true)
            .setSlots(
                CommandButton.SLOT_BACK, CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
            .build();
    CommandButton button2 =
        new CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
            .setDisplayName("button2")
            .setSessionCommand(new SessionCommand("command2", Bundle.EMPTY))
            .setEnabled(true)
            .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
            .build();
    ConditionVariable onSetCustomLayoutCalled = new ConditionVariable();
    ConditionVariable onCustomLayoutChangedCalled = new ConditionVariable();
    List<List<CommandButton>> setCustomLayoutArguments = new ArrayList<>();
    List<List<CommandButton>> customLayoutChangedArguments = new ArrayList<>();
    List<List<CommandButton>> customLayoutFromGetter = new ArrayList<>();
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public ListenableFuture<SessionResult> onSetCustomLayout(
              MediaController controller, List<CommandButton> layout) {
            setCustomLayoutArguments.add(layout);
            onSetCustomLayoutCalled.open();
            return MediaController.Listener.super.onSetCustomLayout(controller, layout);
          }

          @Override
          public void onCustomLayoutChanged(
              MediaController controller, List<CommandButton> layout) {
            customLayoutChangedArguments.add(layout);
            customLayoutFromGetter.add(controller.getCustomLayout());
            onCustomLayoutChangedCalled.open();
          }
        });
    Bundle extras1 = new Bundle();
    extras1.putString("key", "value-1");
    PlaybackStateCompat.CustomAction customAction1 =
        new PlaybackStateCompat.CustomAction.Builder(
                "command1", "button1", /* icon= */ R.drawable.media3_notification_small_icon)
            .setExtras(extras1)
            .build();
    Bundle extras2 = new Bundle();
    extras2.putString("key", "value-2");
    extras2.putInt(
        MediaConstants.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT, CommandButton.ICON_FAST_FORWARD);
    PlaybackStateCompat.CustomAction customAction2 =
        new PlaybackStateCompat.CustomAction.Builder(
                "command2", "button2", /* icon= */ R.drawable.media3_icon_fast_forward)
            .setExtras(extras2)
            .build();
    PlaybackStateCompat.Builder playbackState1 =
        new PlaybackStateCompat.Builder()
            .addCustomAction(customAction1)
            .addCustomAction(customAction2);
    PlaybackStateCompat.Builder playbackState2 =
        new PlaybackStateCompat.Builder().addCustomAction(customAction1);

    session.setPlaybackState(playbackState1.build());
    assertThat(onSetCustomLayoutCalled.block(TIMEOUT_MS)).isTrue();
    assertThat(onCustomLayoutChangedCalled.block(TIMEOUT_MS)).isTrue();
    onSetCustomLayoutCalled.close();
    onCustomLayoutChangedCalled.close();
    session.setPlaybackState(playbackState2.build());
    assertThat(onSetCustomLayoutCalled.block(TIMEOUT_MS)).isTrue();
    assertThat(onCustomLayoutChangedCalled.block(TIMEOUT_MS)).isTrue();

    ImmutableList<CommandButton> expectedFirstCustomLayout = ImmutableList.of(button1, button2);
    ImmutableList<CommandButton> expectedSecondCustomLayout = ImmutableList.of(button1);
    assertThat(setCustomLayoutArguments)
        .containsExactly(expectedFirstCustomLayout, expectedSecondCustomLayout)
        .inOrder();
    assertThat(customLayoutChangedArguments)
        .containsExactly(expectedFirstCustomLayout, expectedSecondCustomLayout)
        .inOrder();
    assertThat(customLayoutFromGetter)
        .containsExactly(expectedFirstCustomLayout, expectedSecondCustomLayout)
        .inOrder();
  }

  @Test
  public void getMediaButtonPreferences() throws Exception {
    CommandButton button1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button1")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(new SessionCommand("command1", Bundle.EMPTY))
            .setEnabled(true)
            .setSlots(
                CommandButton.SLOT_BACK, CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
            .build();
    CommandButton button2 =
        new CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
            .setDisplayName("button2")
            .setSessionCommand(new SessionCommand("command2", Bundle.EMPTY))
            .setEnabled(true)
            .setSlots(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
            .build();
    ConditionVariable onMediaButtonPreferencesChangedCalled = new ConditionVariable();
    List<List<CommandButton>> onMediaButtonPreferencesChangedArguments = new ArrayList<>();
    List<List<CommandButton>> mediaButtonPreferencesFromGetter = new ArrayList<>();
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public void onMediaButtonPreferencesChanged(
              MediaController controller, List<CommandButton> mediaButtonPreferences) {
            onMediaButtonPreferencesChangedArguments.add(mediaButtonPreferences);
            mediaButtonPreferencesFromGetter.add(controller.getMediaButtonPreferences());
            onMediaButtonPreferencesChangedCalled.open();
          }
        });
    Bundle extras1 = new Bundle();
    extras1.putString("key", "value-1");
    PlaybackStateCompat.CustomAction customAction1 =
        new PlaybackStateCompat.CustomAction.Builder(
                "command1", "button1", /* icon= */ R.drawable.media3_notification_small_icon)
            .setExtras(extras1)
            .build();
    Bundle extras2 = new Bundle();
    extras2.putString("key", "value-2");
    extras2.putInt(
        MediaConstants.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT, CommandButton.ICON_FAST_FORWARD);
    PlaybackStateCompat.CustomAction customAction2 =
        new PlaybackStateCompat.CustomAction.Builder(
                "command2", "button2", /* icon= */ R.drawable.media3_icon_fast_forward)
            .setExtras(extras2)
            .build();
    PlaybackStateCompat.Builder playbackState1 =
        new PlaybackStateCompat.Builder()
            .addCustomAction(customAction1)
            .addCustomAction(customAction2);
    PlaybackStateCompat.Builder playbackState2 =
        new PlaybackStateCompat.Builder().addCustomAction(customAction1);

    session.setPlaybackState(playbackState1.build());
    assertThat(onMediaButtonPreferencesChangedCalled.block(TIMEOUT_MS)).isTrue();
    onMediaButtonPreferencesChangedCalled.close();
    session.setPlaybackState(playbackState2.build());
    assertThat(onMediaButtonPreferencesChangedCalled.block(TIMEOUT_MS)).isTrue();

    ImmutableList<CommandButton> expectedFirstMediaButtonPreferences =
        ImmutableList.of(button1, button2);
    ImmutableList<CommandButton> expectedSecondMediaButtonPreferences = ImmutableList.of(button1);
    assertThat(onMediaButtonPreferencesChangedArguments)
        .containsExactly(expectedFirstMediaButtonPreferences, expectedSecondMediaButtonPreferences)
        .inOrder();
    assertThat(mediaButtonPreferencesFromGetter)
        .containsExactly(expectedFirstMediaButtonPreferences, expectedSecondMediaButtonPreferences)
        .inOrder();
  }

  @Test
  public void getMediaButtonPreferences_withPrevNextActions() throws Exception {
    CommandButton button1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button1")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(new SessionCommand("command1", Bundle.EMPTY))
            .build();
    CommandButton button2 =
        new CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
            .setDisplayName("button2")
            .setSessionCommand(new SessionCommand("command2", Bundle.EMPTY))
            .build();
    ConditionVariable onMediaButtonPreferencesChangedCalled = new ConditionVariable();
    List<List<CommandButton>> reportedMediaButtonPreferences = new ArrayList<>();
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public void onMediaButtonPreferencesChanged(
              MediaController controller, List<CommandButton> mediaButtonPreferences) {
            reportedMediaButtonPreferences.add(mediaButtonPreferences);
            onMediaButtonPreferencesChangedCalled.open();
          }
        });
    Bundle extras1 = new Bundle();
    extras1.putString("key", "value-1");
    PlaybackStateCompat.CustomAction customAction1 =
        new PlaybackStateCompat.CustomAction.Builder(
                "command1", "button1", /* icon= */ R.drawable.media3_notification_small_icon)
            .setExtras(extras1)
            .build();
    Bundle extras2 = new Bundle();
    extras2.putString("key", "value-2");
    extras2.putInt(
        MediaConstants.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT, CommandButton.ICON_FAST_FORWARD);
    PlaybackStateCompat.CustomAction customAction2 =
        new PlaybackStateCompat.CustomAction.Builder(
                "command2", "button2", /* icon= */ R.drawable.media3_icon_fast_forward)
            .setExtras(extras2)
            .build();
    PlaybackStateCompat playbackStatePrev =
        new PlaybackStateCompat.Builder()
            .addCustomAction(customAction1)
            .addCustomAction(customAction2)
            .setActions(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .build();
    PlaybackStateCompat playbackStateNext =
        new PlaybackStateCompat.Builder()
            .addCustomAction(customAction1)
            .addCustomAction(customAction2)
            .setActions(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            .build();
    PlaybackStateCompat playbackStatePrevNext =
        new PlaybackStateCompat.Builder()
            .addCustomAction(customAction1)
            .addCustomAction(customAction2)
            .setActions(
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            .build();

    session.setPlaybackState(playbackStatePrev);
    assertThat(onMediaButtonPreferencesChangedCalled.block(TIMEOUT_MS)).isTrue();
    onMediaButtonPreferencesChangedCalled.close();
    session.setPlaybackState(playbackStateNext);
    assertThat(onMediaButtonPreferencesChangedCalled.block(TIMEOUT_MS)).isTrue();
    onMediaButtonPreferencesChangedCalled.close();
    session.setPlaybackState(playbackStatePrevNext);
    assertThat(onMediaButtonPreferencesChangedCalled.block(TIMEOUT_MS)).isTrue();

    assertThat(reportedMediaButtonPreferences)
        .containsExactly(
            ImmutableList.of(
                button1.copyWithSlots(
                    ImmutableIntArray.of(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)),
                button2.copyWithSlots(ImmutableIntArray.of(CommandButton.SLOT_OVERFLOW))),
            ImmutableList.of(
                button1.copyWithSlots(
                    ImmutableIntArray.of(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)),
                button2.copyWithSlots(ImmutableIntArray.of(CommandButton.SLOT_OVERFLOW))),
            ImmutableList.of(
                button1.copyWithSlots(ImmutableIntArray.of(CommandButton.SLOT_OVERFLOW)),
                button2.copyWithSlots(ImmutableIntArray.of(CommandButton.SLOT_OVERFLOW))))
        .inOrder();
  }

  @Test
  public void getMediaButtonPreferences_withSlotReservations() throws Exception {
    CommandButton button1 =
        new CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("button1")
            .setCustomIconResId(R.drawable.media3_notification_small_icon)
            .setSessionCommand(new SessionCommand("command1", Bundle.EMPTY))
            .build();
    CommandButton button2 =
        new CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
            .setDisplayName("button2")
            .setSessionCommand(new SessionCommand("command2", Bundle.EMPTY))
            .build();
    ConditionVariable onMediaButtonPreferencesChangedCalled = new ConditionVariable();
    List<List<CommandButton>> reportedMediaButtonPreferences = new ArrayList<>();
    controllerTestRule.createController(
        session.getSessionToken(),
        new MediaController.Listener() {
          @Override
          public void onMediaButtonPreferencesChanged(
              MediaController controller, List<CommandButton> mediaButtonPreferences) {
            reportedMediaButtonPreferences.add(mediaButtonPreferences);
            onMediaButtonPreferencesChangedCalled.open();
          }
        });
    Bundle extras1 = new Bundle();
    extras1.putString("key", "value-1");
    PlaybackStateCompat.CustomAction customAction1 =
        new PlaybackStateCompat.CustomAction.Builder(
                "command1", "button1", /* icon= */ R.drawable.media3_notification_small_icon)
            .setExtras(extras1)
            .build();
    Bundle extras2 = new Bundle();
    extras2.putString("key", "value-2");
    extras2.putInt(
        MediaConstants.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT, CommandButton.ICON_FAST_FORWARD);
    PlaybackStateCompat.CustomAction customAction2 =
        new PlaybackStateCompat.CustomAction.Builder(
                "command2", "button2", /* icon= */ R.drawable.media3_icon_fast_forward)
            .setExtras(extras2)
            .build();
    PlaybackStateCompat playbackState =
        new PlaybackStateCompat.Builder()
            .addCustomAction(customAction1)
            .addCustomAction(customAction2)
            .build();
    Bundle extrasPrevSlotReservation = new Bundle();
    extrasPrevSlotReservation.putBoolean(
        androidx.media.utils.MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_PREV, true);
    Bundle extrasNextSlotReservation = new Bundle();
    extrasNextSlotReservation.putBoolean(
        androidx.media.utils.MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_NEXT, true);
    Bundle extrasPrevNextSlotReservation = new Bundle();
    extrasPrevNextSlotReservation.putBoolean(
        androidx.media.utils.MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_PREV, true);
    extrasPrevNextSlotReservation.putBoolean(
        androidx.media.utils.MediaConstants.SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_NEXT, true);

    session.setExtras(extrasPrevSlotReservation);
    session.setPlaybackState(playbackState);
    assertThat(onMediaButtonPreferencesChangedCalled.block(TIMEOUT_MS)).isTrue();
    onMediaButtonPreferencesChangedCalled.close();
    session.setExtras(extrasNextSlotReservation);
    assertThat(onMediaButtonPreferencesChangedCalled.block(TIMEOUT_MS)).isTrue();
    onMediaButtonPreferencesChangedCalled.close();
    session.setExtras(extrasPrevNextSlotReservation);
    assertThat(onMediaButtonPreferencesChangedCalled.block(TIMEOUT_MS)).isTrue();

    assertThat(reportedMediaButtonPreferences)
        .containsExactly(
            ImmutableList.of(
                button1.copyWithSlots(
                    ImmutableIntArray.of(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)),
                button2.copyWithSlots(ImmutableIntArray.of(CommandButton.SLOT_OVERFLOW))),
            ImmutableList.of(
                button1.copyWithSlots(
                    ImmutableIntArray.of(CommandButton.SLOT_BACK, CommandButton.SLOT_OVERFLOW)),
                button2.copyWithSlots(ImmutableIntArray.of(CommandButton.SLOT_OVERFLOW))),
            ImmutableList.of(
                button1.copyWithSlots(ImmutableIntArray.of(CommandButton.SLOT_OVERFLOW)),
                button2.copyWithSlots(ImmutableIntArray.of(CommandButton.SLOT_OVERFLOW))))
        .inOrder();
  }

  @Test
  public void getCurrentPosition_unknownPlaybackPosition_convertedToZero() throws Exception {
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_NONE,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                /* playbackSpeed= */ 1.0f)
            .build());
    MediaControllerCompat legacyController =
        new MediaControllerCompat(
            ApplicationProvider.getApplicationContext(), session.getSessionToken());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());

    assertThat(legacyController.getPlaybackState().getPosition())
        .isEqualTo(PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN);
    assertThat(threadTestRule.getHandler().postAndSync(controller::getCurrentPosition))
        .isEqualTo(0);
  }

  @SuppressWarnings("deprecation") // Testing interoperability and backwards compatibility.
  @Test
  public void setDeviceVolume_whenWaitingForPendingUpdates_maskingDoesNotOverridePendingUpdate()
      throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    List<Integer> reportedPlaybackStates = new ArrayList<>();
    List<MediaMetadata> reportedMediaMetadata = new ArrayList<>();
    ConditionVariable playbackStateChanged = new ConditionVariable();
    ConditionVariable mediaMetadataChanged = new ConditionVariable();
    playbackStateChanged.close();
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            reportedPlaybackStates.add(playbackState);
            playbackStateChanged.open();
          }

          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            reportedMediaMetadata.add(mediaMetadata);
            mediaMetadataChanged.open();
          }
        });

    session.setMetadata(
        new android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "artist-0")
            .build());
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PLAYING, /* position= */ 1001L, /* playbackSpeed= */ 1.0f)
            .build());
    synchronized (this) {
      // Wait 200ms to make playback state and metadata arrive.
      Thread.sleep(200);
      // Trigger masking than must not drop the pending legacy info.
      threadTestRule.getHandler().postAndSync(() -> controller.setDeviceVolume(1, 0));
    }

    assertThat(playbackStateChanged.block(TIMEOUT_MS)).isTrue();
    assertThat(mediaMetadataChanged.block(TIMEOUT_MS)).isTrue();
    assertThat(reportedPlaybackStates).containsExactly(3);
    assertThat(reportedMediaMetadata.stream().map((m) -> m.artist)).containsExactly("artist-0");
  }
}
