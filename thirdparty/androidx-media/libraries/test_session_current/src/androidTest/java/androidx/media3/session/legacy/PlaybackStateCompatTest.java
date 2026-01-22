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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.os.Bundle;
import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test {@link PlaybackStateCompat}. */
@RunWith(AndroidJUnit4.class)
public class PlaybackStateCompatTest {

  private static final long TEST_POSITION = 20000L;
  private static final long TEST_BUFFERED_POSITION = 15000L;
  private static final long TEST_UPDATE_TIME = 100000L;
  private static final long TEST_ACTIONS =
      PlaybackStateCompat.ACTION_PLAY
          | PlaybackStateCompat.ACTION_STOP
          | PlaybackStateCompat.ACTION_SEEK_TO;
  private static final long TEST_QUEUE_ITEM_ID = 23L;
  private static final float TEST_PLAYBACK_SPEED = 3.0f;
  private static final float TEST_PLAYBACK_SPEED_ON_REWIND = -2.0f;
  private static final float DELTA = 1e-7f;

  private static final int TEST_ERROR_CODE = PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED;
  private static final String TEST_ERROR_MSG = "test-error-msg";
  private static final String TEST_CUSTOM_ACTION = "test-custom-action";
  private static final String TEST_CUSTOM_ACTION_NAME = "test-custom-action-name";
  private static final int TEST_ICON_RESOURCE_ID = android.R.drawable.ic_media_next;

  private static final String EXTRAS_KEY = "test-key";
  private static final String EXTRAS_VALUE = "test-value";

  /** Test default values of {@link PlaybackStateCompat}. */
  @Test
  public void testBuilder() {
    PlaybackStateCompat state = new PlaybackStateCompat.Builder().build();

    assertEquals(new ArrayList<PlaybackStateCompat.CustomAction>(), state.getCustomActions());
    assertEquals(0, state.getState());
    assertEquals(0L, state.getPosition());
    assertEquals(0L, state.getBufferedPosition());
    assertEquals(0.0f, state.getPlaybackSpeed(), DELTA);
    assertEquals(0L, state.getActions());
    assertEquals(0, state.getErrorCode());
    assertNull(state.getErrorMessage());
    assertEquals(0L, state.getLastPositionUpdateTime());
    assertEquals(MediaSessionCompat.QueueItem.UNKNOWN_ID, state.getActiveQueueItemId());
    assertNull(state.getExtras());
  }

  /**
   * Test following setter methods of {@link PlaybackStateCompat.Builder}: {@link
   * PlaybackStateCompat.Builder#setState(int, long, float)} {@link
   * PlaybackStateCompat.Builder#setActions(long)} {@link
   * PlaybackStateCompat.Builder#setActiveQueueItemId(long)} {@link
   * PlaybackStateCompat.Builder#setBufferedPosition(long)} {@link
   * PlaybackStateCompat.Builder#setErrorMessage(CharSequence)} {@link
   * PlaybackStateCompat.Builder#setExtras(Bundle)}
   */
  @Test
  @SuppressWarnings("deprecation")
  public void testBuilder_setterMethods() {
    Bundle extras = new Bundle();
    extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, TEST_POSITION, TEST_PLAYBACK_SPEED)
            .setActions(TEST_ACTIONS)
            .setActiveQueueItemId(TEST_QUEUE_ITEM_ID)
            .setBufferedPosition(TEST_BUFFERED_POSITION)
            .setErrorMessage(TEST_ERROR_CODE, TEST_ERROR_MSG)
            .setExtras(extras)
            .build();
    assertEquals(PlaybackStateCompat.STATE_PLAYING, state.getState());
    assertEquals(TEST_POSITION, state.getPosition());
    assertEquals(TEST_PLAYBACK_SPEED, state.getPlaybackSpeed(), DELTA);
    assertEquals(TEST_ACTIONS, state.getActions());
    assertEquals(TEST_QUEUE_ITEM_ID, state.getActiveQueueItemId());
    assertEquals(TEST_BUFFERED_POSITION, state.getBufferedPosition());
    assertEquals(TEST_ERROR_CODE, state.getErrorCode());
    assertEquals(TEST_ERROR_MSG, state.getErrorMessage().toString());
    assertNotNull(state.getExtras());
    assertEquals(EXTRAS_VALUE, state.getExtras().get(EXTRAS_KEY));
  }

  /** Test {@link PlaybackStateCompat.Builder#setState(int, long, float, long)}. */
  @Test
  public void testBuilder_setStateWithUpdateTime() {
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_REWINDING,
                TEST_POSITION,
                TEST_PLAYBACK_SPEED_ON_REWIND,
                TEST_UPDATE_TIME)
            .build();
    assertEquals(PlaybackStateCompat.STATE_REWINDING, state.getState());
    assertEquals(TEST_POSITION, state.getPosition());
    assertEquals(TEST_PLAYBACK_SPEED_ON_REWIND, state.getPlaybackSpeed(), DELTA);
    assertEquals(TEST_UPDATE_TIME, state.getLastPositionUpdateTime());
  }

  /** Test {@link PlaybackStateCompat.Builder#addCustomAction(String, String, int)}. */
  @Test
  public void testBuilder_addCustomAction() {
    ArrayList<PlaybackStateCompat.CustomAction> actions = new ArrayList<>();
    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();

    for (int i = 0; i < 5; i++) {
      actions.add(
          new PlaybackStateCompat.CustomAction.Builder(
                  TEST_CUSTOM_ACTION + i, TEST_CUSTOM_ACTION_NAME + i, TEST_ICON_RESOURCE_ID + i)
              .build());
      builder.addCustomAction(
          TEST_CUSTOM_ACTION + i, TEST_CUSTOM_ACTION_NAME + i, TEST_ICON_RESOURCE_ID + i);
    }

    PlaybackStateCompat state = builder.build();
    assertEquals(actions.size(), state.getCustomActions().size());
    for (int i = 0; i < actions.size(); i++) {
      assertCustomActionEquals(actions.get(i), state.getCustomActions().get(i));
    }
  }

  /** Test {@link PlaybackStateCompat.Builder#addCustomAction(PlaybackStateCompat.CustomAction)}. */
  @Test
  public void testBuilder_addCustomActionWithCustomActionObject() {
    Bundle extras = new Bundle();
    extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

    ArrayList<PlaybackStateCompat.CustomAction> actions = new ArrayList<>();
    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();

    for (int i = 0; i < 5; i++) {
      actions.add(
          new PlaybackStateCompat.CustomAction.Builder(
                  TEST_CUSTOM_ACTION + i, TEST_CUSTOM_ACTION_NAME + i, TEST_ICON_RESOURCE_ID + i)
              .setExtras(extras)
              .build());
      builder.addCustomAction(
          new PlaybackStateCompat.CustomAction.Builder(
                  TEST_CUSTOM_ACTION + i, TEST_CUSTOM_ACTION_NAME + i, TEST_ICON_RESOURCE_ID + i)
              .setExtras(extras)
              .build());
    }

    PlaybackStateCompat state = builder.build();
    assertEquals(actions.size(), state.getCustomActions().size());
    for (int i = 0; i < actions.size(); i++) {
      assertCustomActionEquals(actions.get(i), state.getCustomActions().get(i));
    }
  }

  /** Test {@link PlaybackStateCompat#writeToParcel(Parcel, int)}. */
  @Test
  @SuppressWarnings("deprecation")
  public void testWriteToParcel() {
    Bundle extras = new Bundle();
    extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

    PlaybackStateCompat.Builder builder =
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_CONNECTING,
                TEST_POSITION,
                TEST_PLAYBACK_SPEED,
                TEST_UPDATE_TIME)
            .setActions(TEST_ACTIONS)
            .setActiveQueueItemId(TEST_QUEUE_ITEM_ID)
            .setBufferedPosition(TEST_BUFFERED_POSITION)
            .setErrorMessage(TEST_ERROR_CODE, TEST_ERROR_MSG)
            .setExtras(extras);

    for (int i = 0; i < 5; i++) {
      builder.addCustomAction(
          new PlaybackStateCompat.CustomAction.Builder(
                  TEST_CUSTOM_ACTION + i, TEST_CUSTOM_ACTION_NAME + i, TEST_ICON_RESOURCE_ID + i)
              .setExtras(extras)
              .build());
    }
    PlaybackStateCompat state = builder.build();

    Parcel parcel = Parcel.obtain();
    state.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    PlaybackStateCompat stateOut = PlaybackStateCompat.CREATOR.createFromParcel(parcel);
    assertEquals(PlaybackStateCompat.STATE_CONNECTING, stateOut.getState());
    assertEquals(TEST_POSITION, stateOut.getPosition());
    assertEquals(TEST_PLAYBACK_SPEED, stateOut.getPlaybackSpeed(), DELTA);
    assertEquals(TEST_UPDATE_TIME, stateOut.getLastPositionUpdateTime());
    assertEquals(TEST_BUFFERED_POSITION, stateOut.getBufferedPosition());
    assertEquals(TEST_ACTIONS, stateOut.getActions());
    assertEquals(TEST_QUEUE_ITEM_ID, stateOut.getActiveQueueItemId());
    assertEquals(TEST_ERROR_CODE, stateOut.getErrorCode());
    assertEquals(TEST_ERROR_MSG, stateOut.getErrorMessage());
    assertNotNull(stateOut.getExtras());
    assertEquals(EXTRAS_VALUE, stateOut.getExtras().get(EXTRAS_KEY));

    assertEquals(state.getCustomActions().size(), stateOut.getCustomActions().size());
    for (int i = 0; i < state.getCustomActions().size(); i++) {
      assertCustomActionEquals(state.getCustomActions().get(i), stateOut.getCustomActions().get(i));
    }
    parcel.recycle();
  }

  /** Test {@link PlaybackStateCompat#describeContents()}. */
  @Test
  public void testDescribeContents() {
    assertEquals(0, new PlaybackStateCompat.Builder().build().describeContents());
  }

  /** Test {@link PlaybackStateCompat.CustomAction}. */
  @Test
  @SuppressWarnings("deprecation")
  public void testCustomAction() {
    Bundle extras = new Bundle();
    extras.putString(EXTRAS_KEY, EXTRAS_VALUE);

    // Test Builder/Getters
    PlaybackStateCompat.CustomAction customAction =
        new PlaybackStateCompat.CustomAction.Builder(
                TEST_CUSTOM_ACTION, TEST_CUSTOM_ACTION_NAME, TEST_ICON_RESOURCE_ID)
            .setExtras(extras)
            .build();
    assertEquals(TEST_CUSTOM_ACTION, customAction.getAction());
    assertEquals(TEST_CUSTOM_ACTION_NAME, customAction.getName().toString());
    assertEquals(TEST_ICON_RESOURCE_ID, customAction.getIcon());
    assertEquals(EXTRAS_VALUE, customAction.getExtras().get(EXTRAS_KEY));

    // Test describeContents
    assertEquals(0, customAction.describeContents());

    // Test writeToParcel
    Parcel parcel = Parcel.obtain();
    customAction.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    assertCustomActionEquals(
        customAction, PlaybackStateCompat.CustomAction.CREATOR.createFromParcel(parcel));
    parcel.recycle();
  }

  /** Tests that each ACTION_* constant does not overlap. */
  @Test
  public void testActionConstantDoesNotOverlap() {
    long[] actionConstants =
        new long[] {
          PlaybackStateCompat.ACTION_STOP,
          PlaybackStateCompat.ACTION_PAUSE,
          PlaybackStateCompat.ACTION_PLAY,
          PlaybackStateCompat.ACTION_REWIND,
          PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
          PlaybackStateCompat.ACTION_SKIP_TO_NEXT,
          PlaybackStateCompat.ACTION_FAST_FORWARD,
          PlaybackStateCompat.ACTION_SET_RATING,
          PlaybackStateCompat.ACTION_SEEK_TO,
          PlaybackStateCompat.ACTION_PLAY_PAUSE,
          PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID,
          PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH,
          PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM,
          PlaybackStateCompat.ACTION_PLAY_FROM_URI,
          PlaybackStateCompat.ACTION_PREPARE,
          PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID,
          PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH,
          PlaybackStateCompat.ACTION_PREPARE_FROM_URI,
          PlaybackStateCompat.ACTION_SET_REPEAT_MODE,
          PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE,
          PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED,
          PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED
        };

    // Check that the values are not overlapped.
    for (int i = 0; i < actionConstants.length; i++) {
      for (int j = i + 1; j < actionConstants.length; j++) {
        assertEquals(0, actionConstants[i] & actionConstants[j]);
      }
    }
  }

  @SuppressWarnings("deprecation")
  private void assertCustomActionEquals(
      PlaybackStateCompat.CustomAction action1, PlaybackStateCompat.CustomAction action2) {
    assertEquals(action1.getAction(), action2.getAction());
    assertEquals(action1.getName(), action2.getName());
    assertEquals(action1.getIcon(), action2.getIcon());

    // To be the same, two extras should be both null or both not null.
    assertEquals(action1.getExtras() != null, action2.getExtras() != null);
    if (action1.getExtras() != null) {
      assertEquals(action1.getExtras().get(EXTRAS_KEY), action2.getExtras().get(EXTRAS_KEY));
    }
  }
}
