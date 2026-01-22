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
package androidx.media3.transformer;

import static androidx.media3.transformer.TestUtil.ASSET_URI_PREFIX;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW;
import static androidx.media3.transformer.TestUtil.FILE_AUDIO_RAW_STEREO_48000KHZ;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.SpeedChangingAudioProcessor;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.TestSpeedProvider;
import androidx.media3.test.utils.robolectric.RobolectricUtil;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** Unit tests for {@link CompositionPlayer}. */
@RunWith(AndroidJUnit4.class)
public class CompositionPlayerTest {
  private static final long TEST_TIMEOUT_MS = 1_000;

  @Test
  public void builder_buildCalledTwice_throws() {
    CompositionPlayer.Builder builder =
        new CompositionPlayer.Builder(ApplicationProvider.getApplicationContext());

    CompositionPlayer player = builder.build();

    assertThrows(IllegalStateException.class, builder::build);

    player.release();
  }

  @Test
  public void builder_buildCalledOnNonHandlerThread_throws() throws InterruptedException {
    AtomicReference<@NullableType Exception> exception = new AtomicReference<>();
    ConditionVariable conditionVariable = new ConditionVariable();

    Thread thread =
        new Thread(
            () -> {
              try {
                new Composition.Builder(ApplicationProvider.getApplicationContext()).build();
              } catch (Exception e) {
                exception.set(e);
              } finally {
                conditionVariable.open();
              }
            });
    thread.start();

    conditionVariable.block();
    thread.join();

    assertThat(exception.get()).isNotNull();
  }

  @Test
  public void instance_accessedByWrongThread_throws() throws InterruptedException {
    CompositionPlayer player = buildCompositionPlayer();
    AtomicReference<@NullableType RuntimeException> exception = new AtomicReference<>();
    ConditionVariable conditionVariable = new ConditionVariable();
    HandlerThread handlerThread = new HandlerThread("test");
    handlerThread.start();

    new Handler(handlerThread.getLooper())
        .post(
            () -> {
              try {
                player.setComposition(buildComposition());
              } catch (RuntimeException e) {
                exception.set(e);
              } finally {
                conditionVariable.open();
              }
            });
    conditionVariable.block();
    player.release();
    handlerThread.quit();
    handlerThread.join();

    assertThat(exception.get()).isInstanceOf(IllegalStateException.class);
    assertThat(exception.get()).hasMessageThat().contains("Player is accessed on the wrong thread");
  }

  @Test
  public void instance_withSpecifiedApplicationLooper_callbacksDispatchedOnSpecifiedThread()
      throws Exception {
    HandlerThread applicationHandlerThread = new HandlerThread("app-thread");
    applicationHandlerThread.start();
    Looper applicationLooper = applicationHandlerThread.getLooper();
    Handler applicationThreadHandler = new Handler(applicationLooper);
    AtomicReference<Thread> callbackThread = new AtomicReference<>();
    ConditionVariable eventsArrived = new ConditionVariable();
    CompositionPlayer player =
        createCompositionPlayerBuilder().setLooper(applicationLooper).build();
    // Listeners can be added by any thread.
    player.addListener(
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            callbackThread.set(Thread.currentThread());
            eventsArrived.open();
          }
        });

    applicationThreadHandler.post(
        () -> {
          player.setComposition(buildComposition());
          player.prepare();
        });
    if (!eventsArrived.block(TEST_TIMEOUT_MS)) {
      throw new TimeoutException();
    }
    // Use a separate condition variable to releasing the player to avoid race conditions
    // with the condition variable used for the callback.
    ConditionVariable released = new ConditionVariable();
    applicationThreadHandler.post(
        () -> {
          player.release();
          released.open();
        });
    if (!released.block(TEST_TIMEOUT_MS)) {
      throw new TimeoutException();
    }
    applicationHandlerThread.quit();
    applicationHandlerThread.join();

    assertThat(eventsArrived.isOpen()).isTrue();
    assertThat(callbackThread.get()).isEqualTo(applicationLooper.getThread());
  }

  @Test
  public void release_onNewlyCreateInstance() {
    CompositionPlayer player = buildCompositionPlayer();

    player.release();
  }

  @Test
  public void release_audioFailsDuringRelease_onlyLogsError() throws Exception {
    Log.Logger logger = mock(Log.Logger.class);
    Log.setLogger(logger);
    AudioSink audioSink =
        new ForwardingAudioSink(
            new DefaultAudioSink.Builder(ApplicationProvider.getApplicationContext()).build()) {
          @Override
          public void release() {
            throw new RuntimeException("AudioSink release error");
          }
        };
    CompositionPlayer player = createCompositionPlayerBuilder().setAudioSink(audioSink).build();
    Player.Listener listener = mock(Player.Listener.class);
    player.addListener(listener);

    player.setComposition(buildComposition());
    player.prepare();
    TestPlayerRunHelper.advance(player).untilState(Player.STATE_READY);

    player.release();

    verify(listener, never()).onPlayerError(any());
    verify(logger)
        .e(
            eq("CompPlayerInternal"),
            eq("error while releasing the player"),
            argThat(
                throwable ->
                    throwable instanceof RuntimeException
                        && throwable.getMessage().contains("AudioSink release error")));
  }

  @Test
  public void getAvailableCommands_returnsSpecificCommands() {
    CompositionPlayer player = buildCompositionPlayer();

    assertThat(getList(player.getAvailableCommands()))
        .containsExactly(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_SET_VIDEO_SURFACE,
            Player.COMMAND_SET_REPEAT_MODE,
            Player.COMMAND_GET_VOLUME,
            Player.COMMAND_SET_VOLUME,
            Player.COMMAND_RELEASE);

    player.release();
  }

  @Test
  public void setComposition_calledTwice_throws() {
    Composition composition = buildComposition();
    CompositionPlayer player = buildCompositionPlayer();

    player.setComposition(composition);

    assertThrows(IllegalStateException.class, () -> player.setComposition(composition));

    player.release();
  }

  @Test
  public void prepare_withoutCompositionSet_throws() {
    CompositionPlayer player = buildCompositionPlayer();

    assertThrows(IllegalStateException.class, player::prepare);

    player.release();
  }

  @Test
  public void playWhenReady_calledBeforePrepare_startsPlayingAfterPrepareCalled() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();

    player.setPlayWhenReady(true);
    player.setComposition(buildComposition());
    player.prepare();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
  }

  @Test
  public void playWhenReady_triggersPlayWhenReadyCallbackWithReason() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    AtomicInteger playWhenReadyReason = new AtomicInteger(-1);
    player.addListener(
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
            playWhenReadyReason.set(reason);
          }
        });

    player.setPlayWhenReady(true);
    RobolectricUtil.runMainLooperUntil(() -> playWhenReadyReason.get() != -1);

    assertThat(playWhenReadyReason.get())
        .isEqualTo(Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
  }

  @Test
  public void setVideoTextureView_throws() {
    Context context = ApplicationProvider.getApplicationContext();
    CompositionPlayer player = buildCompositionPlayer();

    assertThrows(
        UnsupportedOperationException.class,
        () -> player.setVideoTextureView(new TextureView(context)));

    player.release();
  }

  @Test
  public void setVideoSurface_withNonNullSurface_throws() {
    CompositionPlayer player = buildCompositionPlayer();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 0));

    assertThrows(UnsupportedOperationException.class, () -> player.setVideoSurface(surface));

    player.release();
    surface.release();
  }

  @Test
  public void clearVideoSurface_specifiedSurfaceNotPreviouslySet_throws() {
    CompositionPlayer player = buildCompositionPlayer();

    assertThrows(
        IllegalArgumentException.class,
        () -> player.clearVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 0))));

    player.release();
  }

  @Test
  public void getTotalBufferedDuration_playerStillIdle_returnsZero() {
    CompositionPlayer player = buildCompositionPlayer();

    assertThat(player.getTotalBufferedDuration()).isEqualTo(0);

    player.release();
  }

  @Test
  public void getTotalBufferedDuration_setCompositionButNotPrepare_returnsZero() {
    CompositionPlayer player = buildCompositionPlayer();

    player.setComposition(buildComposition());

    assertThat(player.getTotalBufferedDuration()).isEqualTo(0);

    player.release();
  }

  @Test
  public void getTotalBufferedDuration_playerReady_returnsNonZero() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();

    player.setComposition(buildComposition());
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.getTotalBufferedDuration()).isGreaterThan(0);

    player.release();
  }

  @Test
  public void getDuration_withoutComposition_returnsTimeUnset() {
    CompositionPlayer player = buildCompositionPlayer();

    assertThat(player.getDuration()).isEqualTo(C.TIME_UNSET);

    player.release();
  }

  @Test
  public void getDuration_withComposition_returnsDuration() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    Composition composition = buildComposition();

    player.setComposition(composition);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    // Refer to the durations in buildComposition().
    assertThat(player.getDuration()).isEqualTo(1_348);

    player.release();
  }

  @Test
  public void getDuration_withClippedStart_returnsCorrectDuration() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setStartPositionUs(200_000).build())
            .build();
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(mediaItem).setDurationUs(1_000_000L).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder(editedMediaItem1).build();
    Composition composition = new Composition.Builder(sequence).build();

    player.setComposition(composition);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.getDuration()).isEqualTo(800);

    player.release();
  }

  @Test
  public void getDuration_withClippedEnd_returnsCorrectDuration() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionUs(600_000).build())
            .build();
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(mediaItem).setDurationUs(1_000_000L).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder(editedMediaItem1).build();
    Composition composition = new Composition.Builder(sequence).build();

    player.setComposition(composition);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.getDuration()).isEqualTo(600);

    player.release();
  }

  @Test
  public void getDuration_withClippedStartEnd_returnsCorrectDuration() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(100_000)
                    .setEndPositionUs(550_000)
                    .build())
            .build();
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(mediaItem).setDurationUs(1_000_000L).build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder(editedMediaItem1).build();
    Composition composition = new Composition.Builder(sequence).build();

    player.setComposition(composition);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.getDuration()).isEqualTo(450);

    player.release();
  }

  @Test
  public void getDuration_withDurationAdjustingEffectsAndClippedStart_returnsCorrectDuration()
      throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    ImmutableList<AudioProcessor> audioProcessors =
        ImmutableList.of(
            new SpeedChangingAudioProcessor(
                TestSpeedProvider.createWithStartTimes(new long[] {0L}, new float[] {2f})));
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setStartPositionUs(200_000).build())
            .build();
    // Video must be removed because Composition presentation time assumes there is audio and video.
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setDurationUs(1_000_000L)
            .setEffects(new Effects(audioProcessors, /* videoEffects= */ ImmutableList.of()))
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder(editedMediaItem1).build();
    Composition composition = new Composition.Builder(sequence).build();

    player.setComposition(composition);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.getDuration()).isEqualTo(400);

    player.release();
  }

  @Test
  public void getDuration_withDurationAdjustingEffectsAndClippedEnd_returnsCorrectDuration()
      throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    ImmutableList<AudioProcessor> audioProcessors =
        ImmutableList.of(
            new SpeedChangingAudioProcessor(
                TestSpeedProvider.createWithStartTimes(new long[] {0L}, new float[] {2f})));
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder().setEndPositionUs(600_000).build())
            .build();
    // Video must be removed because Composition presentation time assumes there is audio and video.
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setDurationUs(1_000_000L)
            .setEffects(new Effects(audioProcessors, /* videoEffects= */ ImmutableList.of()))
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder(editedMediaItem1).build();
    Composition composition = new Composition.Builder(sequence).build();

    player.setComposition(composition);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.getDuration()).isEqualTo(300);

    player.release();
  }

  @Test
  public void getDuration_withDurationAdjustingEffectsAndClippedStartEnd_returnsCorrectDuration()
      throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    ImmutableList<AudioProcessor> audioProcessors =
        ImmutableList.of(
            new SpeedChangingAudioProcessor(
                TestSpeedProvider.createWithStartTimes(new long[] {0L}, new float[] {0.5f})));
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(100_000)
                    .setEndPositionUs(550_000)
                    .build())
            .build();
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(mediaItem)
            .setDurationUs(1_000_000L)
            .setEffects(new Effects(audioProcessors, /* videoEffects= */ ImmutableList.of()))
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder(editedMediaItem1).build();
    Composition composition = new Composition.Builder(sequence).build();

    player.setComposition(composition);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    assertThat(player.getDuration()).isEqualTo(900);

    player.release();
  }

  @Test
  public void addListener_callsSupportedCallbacks() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    Composition composition = buildComposition();
    List<Integer> playbackStates = new ArrayList<>();
    AtomicBoolean playing = new AtomicBoolean();
    Player.Listener listener =
        spy(
            new Player.Listener() {
              @Override
              public void onPlaybackStateChanged(int playbackState) {
                if (playbackStates.isEmpty()
                    || Iterables.getLast(playbackStates) != playbackState) {
                  playbackStates.add(playbackState);
                }
              }

              @Override
              public void onIsPlayingChanged(boolean isPlaying) {
                playing.set(isPlaying);
              }
            });
    InOrder inOrder = Mockito.inOrder(listener);

    player.setComposition(composition);
    player.addListener(listener);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);

    inOrder
        .verify(listener)
        .onTimelineChanged(any(), eq(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED));
    inOrder.verify(listener).onPlaybackStateChanged(Player.STATE_BUFFERING);
    inOrder.verify(listener).onPlaybackStateChanged(Player.STATE_READY);

    player.setPlayWhenReady(true);

    // Ensure that Player.Listener.onIsPlayingChanged(true) is called.
    RobolectricUtil.runMainLooperUntil(playing::get);
    inOrder
        .verify(listener)
        .onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
    inOrder.verify(listener).onIsPlayingChanged(true);

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    inOrder.verify(listener).onPlaybackStateChanged(Player.STATE_ENDED);

    player.stop();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_IDLE);
    inOrder.verify(listener).onPlaybackStateChanged(Player.STATE_IDLE);
    player.release();

    assertThat(playbackStates)
        .containsExactly(
            Player.STATE_BUFFERING, Player.STATE_READY, Player.STATE_ENDED, Player.STATE_IDLE)
        .inOrder();
  }

  @Test
  public void addListener_callsOnEventsWithSupportedEvents() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    Composition composition = buildComposition();
    Player.Listener mockListener = mock(Player.Listener.class);
    ArgumentCaptor<Player.Events> eventsCaptor = ArgumentCaptor.forClass(Player.Events.class);
    ImmutableSet<Integer> supportedEvents =
        ImmutableSet.of(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_IS_PLAYING_CHANGED);

    player.setComposition(composition);
    player.addListener(mockListener);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    verify(mockListener, atLeastOnce()).onEvents(any(), eventsCaptor.capture());
    List<Player.Events> eventsList = eventsCaptor.getAllValues();
    for (Player.Events events : eventsList) {
      assertThat(events.size()).isNotEqualTo(0);
      for (int j = 0; j < events.size(); j++) {
        assertThat(supportedEvents).contains(events.get(j));
      }
    }
  }

  @Test
  public void play_withCorrectTimelineUpdated() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    Composition composition = buildComposition();
    Player.Listener mockListener = mock(Player.Listener.class);
    ArgumentCaptor<Timeline> timelineCaptor = ArgumentCaptor.forClass(Timeline.class);
    ArgumentCaptor<Integer> timelineChangeReasonCaptor = ArgumentCaptor.forClass(Integer.class);
    player.setComposition(composition);
    player.addListener(mockListener);
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    verify(mockListener)
        .onTimelineChanged(timelineCaptor.capture(), timelineChangeReasonCaptor.capture());
    assertThat(timelineCaptor.getAllValues()).hasSize(1);
    assertThat(timelineChangeReasonCaptor.getAllValues()).hasSize(1);
    Timeline timeline = timelineCaptor.getValue();
    assertThat(timeline.getWindowCount()).isEqualTo(1);
    assertThat(timeline.getPeriodCount()).isEqualTo(1);
    // Refer to the durations in buildComposition().
    assertThat(timeline.getWindow(/* windowIndex= */ 0, new Timeline.Window()).durationUs)
        .isEqualTo(1_348_000L);
    assertThat(timelineChangeReasonCaptor.getValue())
        .isEqualTo(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
  }

  @Test
  public void playSequence_withRepeatModeOff_doesNotReportRepeatMediaItemTransition()
      throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);
    player.setComposition(buildComposition());
    player.prepare();
    player.play();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    verify(mockListener, never())
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT));
    verify(mockListener, never())
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_SEEK));
  }

  @Test
  public void playSequence_withRepeatModeAll_reportsRepeatReasonForMediaItemTransition()
      throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);
    player.setRepeatMode(Player.REPEAT_MODE_ALL);
    player.setComposition(buildComposition());
    player.prepare();
    player.play();

    TestPlayerRunHelper.runUntilPositionDiscontinuity(
        player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    TestPlayerRunHelper.runUntilPositionDiscontinuity(
        player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    TestPlayerRunHelper.runUntilPositionDiscontinuity(
        player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    verify(mockListener, times(3))
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT));
    verify(mockListener, never())
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_SEEK));
  }

  @Test
  public void playComposition_withRepeatModeOff_doesNotReportRepeatMediaItemTransition()
      throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(0)
                            .setEndPositionUs(696_000)
                            .build())
                    .build())
            .setDurationUs(1_000_000L)
            .build();
    EditedMediaItem editedMediaItem2 =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setDurationUs(348_000L)
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem1).build(),
                new EditedMediaItemSequence.Builder(editedMediaItem2, editedMediaItem2).build())
            .build();

    player.setComposition(composition);
    player.prepare();
    player.play();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    verify(mockListener, never())
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT));
    verify(mockListener, never())
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_SEEK));
  }

  @Test
  public void playComposition_withRepeatModeAll_reportsRepeatReasonForMediaItemTransition()
      throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    Player.Listener mockListener = mock(Player.Listener.class);
    player.addListener(mockListener);
    player.setRepeatMode(Player.REPEAT_MODE_ALL);
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(
                new MediaItem.Builder()
                    .setUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW)
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(0)
                            .setEndPositionUs(696_000)
                            .build())
                    .build())
            .setDurationUs(1_000_000L)
            .build();
    EditedMediaItem editedMediaItem2 =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setDurationUs(348_000L)
            .build();
    Composition composition =
        new Composition.Builder(
                new EditedMediaItemSequence.Builder(editedMediaItem1).build(),
                new EditedMediaItemSequence.Builder(editedMediaItem2, editedMediaItem2).build())
            .build();
    player.setComposition(composition);
    player.prepare();
    player.play();

    TestPlayerRunHelper.runUntilPositionDiscontinuity(
        player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    TestPlayerRunHelper.runUntilPositionDiscontinuity(
        player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    TestPlayerRunHelper.runUntilPositionDiscontinuity(
        player, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    verify(mockListener, times(3))
        .onMediaItemTransition(any(), eq(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT));
    verify(mockListener, never())
        .onPositionDiscontinuity(any(), any(), eq(Player.DISCONTINUITY_REASON_SEEK));
  }

  @Test
  public void seekPastDuration_ends() throws Exception {
    CompositionPlayer player = buildCompositionPlayer();
    EditedMediaItem editedMediaItem =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
            .setDurationUs(1_000_000L)
            .build();
    EditedMediaItemSequence sequence = new EditedMediaItemSequence.Builder(editedMediaItem).build();
    Composition composition = new Composition.Builder(sequence).build();
    player.setComposition(composition);
    player.prepare();
    player.play();

    player.seekTo(/* positionMs= */ 1100);
    TestPlayerRunHelper.advance(player).untilState(Player.STATE_ENDED);
    player.release();
  }

  private static CompositionPlayer buildCompositionPlayer() {
    return createCompositionPlayerBuilder().build();
  }

  private static CompositionPlayer.Builder createCompositionPlayerBuilder() {
    return new CompositionPlayer.Builder(ApplicationProvider.getApplicationContext())
        .setClock(new FakeClock(/* isAutoAdvancing= */ true));
  }

  private static Composition buildComposition() {
    // Use raw audio-only assets which can be played in robolectric tests.
    EditedMediaItem editedMediaItem1 =
        new EditedMediaItem.Builder(MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW))
            .setDurationUs(1_000_000L)
            .build();
    EditedMediaItem editedMediaItem2 =
        new EditedMediaItem.Builder(
                MediaItem.fromUri(ASSET_URI_PREFIX + FILE_AUDIO_RAW_STEREO_48000KHZ))
            .setDurationUs(348_000L)
            .build();
    EditedMediaItemSequence sequence =
        new EditedMediaItemSequence.Builder(editedMediaItem1, editedMediaItem2).build();
    return new Composition.Builder(sequence).build();
  }

  private static List<Integer> getList(Player.Commands commands) {
    List<Integer> commandList = new ArrayList<>();
    for (int i = 0; i < commands.size(); i++) {
      commandList.add(commands.get(i));
    }
    return commandList;
  }
}
