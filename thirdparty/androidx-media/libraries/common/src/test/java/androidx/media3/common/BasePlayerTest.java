/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.common;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.test.utils.FakeTimeline;
import androidx.media3.test.utils.StubPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link BasePlayer}. */
@RunWith(AndroidJUnit4.class)
public class BasePlayerTest {

  @Test
  public void seekTo_withIndexAndPosition_usesCommandSeekToMediaItem() {
    TestBasePlayer player = new TestBasePlayer();

    player.seekTo(/* mediaItemIndex= */ 2, /* positionMs= */ 4000);

    player.assertSeekToCall(
        /* mediaItemIndex= */ 2,
        /* positionMs= */ 4000,
        Player.COMMAND_SEEK_TO_MEDIA_ITEM,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekTo_withPosition_usesCommandSeekInCurrentMediaItem() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public int getCurrentMediaItemIndex() {
            return 1;
          }
        };

    player.seekTo(/* positionMs= */ 4000);

    player.assertSeekToCall(
        /* mediaItemIndex= */ 1,
        /* positionMs= */ 4000,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToDefaultPosition_withIndex_usesCommandSeekToMediaItem() {
    TestBasePlayer player = new TestBasePlayer();

    player.seekToDefaultPosition(/* mediaItemIndex= */ 2);

    player.assertSeekToCall(
        /* mediaItemIndex= */ 2,
        /* positionMs= */ C.TIME_UNSET,
        Player.COMMAND_SEEK_TO_MEDIA_ITEM,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToDefaultPosition_withoutIndex_usesCommandSeekToDefaultPosition() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public int getCurrentMediaItemIndex() {
            return 1;
          }
        };

    player.seekToDefaultPosition();

    player.assertSeekToCall(
        /* mediaItemIndex= */ 1,
        /* positionMs= */ C.TIME_UNSET,
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToNext_usesCommandSeekToNext() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public int getCurrentMediaItemIndex() {
            return 1;
          }
        };

    player.seekToNext();

    player.assertSeekToCall(
        /* mediaItemIndex= */ 2,
        /* positionMs= */ C.TIME_UNSET,
        Player.COMMAND_SEEK_TO_NEXT,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToNext_withoutNextItem_forwardsCallWithUnsetMediaItemIndex() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public Timeline getCurrentTimeline() {
            return new FakeTimeline(/* windowCount= */ 3);
          }

          @Override
          public int getCurrentMediaItemIndex() {
            return 2;
          }
        };

    player.seekToNext();

    player.assertSeekToCall(
        /* mediaItemIndex= */ C.INDEX_UNSET,
        /* positionMs= */ C.TIME_UNSET,
        Player.COMMAND_SEEK_TO_NEXT,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToNextMediaItem_usesCommandSeekToNextMediaItem() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public int getCurrentMediaItemIndex() {
            return 1;
          }
        };

    player.seekToNextMediaItem();

    player.assertSeekToCall(
        /* mediaItemIndex= */ 2,
        /* positionMs= */ C.TIME_UNSET,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToNextMediaItem_withoutNextItem_forwardsCallWithUnsetMediaItemIndex() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public Timeline getCurrentTimeline() {
            return new FakeTimeline(/* windowCount= */ 3);
          }

          @Override
          public int getCurrentMediaItemIndex() {
            return 2;
          }
        };

    player.seekToNextMediaItem();

    player.assertSeekToCall(
        /* mediaItemIndex= */ C.INDEX_UNSET,
        /* positionMs= */ C.TIME_UNSET,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekForward_usesCommandSeekForward() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public long getSeekForwardIncrement() {
            return 2000;
          }

          @Override
          public int getCurrentMediaItemIndex() {
            return 1;
          }

          @Override
          public long getCurrentPosition() {
            return 5000;
          }
        };

    player.seekForward();

    player.assertSeekToCall(
        /* mediaItemIndex= */ 1,
        /* positionMs= */ 7000,
        Player.COMMAND_SEEK_FORWARD,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToPrevious_usesCommandSeekToPrevious() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public int getCurrentMediaItemIndex() {
            return 1;
          }

          @Override
          public long getMaxSeekToPreviousPosition() {
            return 4000;
          }

          @Override
          public long getCurrentPosition() {
            return 2000;
          }
        };

    player.seekToPrevious();

    player.assertSeekToCall(
        /* mediaItemIndex= */ 0,
        /* positionMs= */ C.TIME_UNSET,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToPreviousMediaItem_usesCommandSeekToPreviousMediaItem() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public int getCurrentMediaItemIndex() {
            return 1;
          }
        };

    player.seekToPreviousMediaItem();

    player.assertSeekToCall(
        /* mediaItemIndex= */ 0,
        /* positionMs= */ C.TIME_UNSET,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekToPreviousMediaItem_withoutPreviousItem_forwardsCallWithUnsetMediaItemIndex() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public int getCurrentMediaItemIndex() {
            return 0;
          }
        };

    player.seekToPreviousMediaItem();

    player.assertSeekToCall(
        /* mediaItemIndex= */ C.INDEX_UNSET,
        /* positionMs= */ C.TIME_UNSET,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        /* isRepeatingCurrentItem= */ false);
  }

  @Test
  public void seekBack_usesCommandSeekBack() {
    TestBasePlayer player =
        new TestBasePlayer() {
          @Override
          public long getSeekBackIncrement() {
            return 2000;
          }

          @Override
          public int getCurrentMediaItemIndex() {
            return 1;
          }

          @Override
          public long getCurrentPosition() {
            return 5000;
          }
        };

    player.seekBack();

    player.assertSeekToCall(
        /* mediaItemIndex= */ 1,
        /* positionMs= */ 3000,
        Player.COMMAND_SEEK_BACK,
        /* isRepeatingCurrentItem= */ false);
  }

  private static class TestBasePlayer extends StubPlayer {

    private int mediaItemIndex;
    private long positionMs;
    private @Player.Command int seekCommand;
    private boolean isRepeatingCurrentItem;

    public TestBasePlayer() {
      mediaItemIndex = C.INDEX_UNSET;
      positionMs = C.TIME_UNSET;
      seekCommand = Player.COMMAND_INVALID;
      isRepeatingCurrentItem = false;
    }

    public void assertSeekToCall(
        int mediaItemIndex,
        long positionMs,
        @Player.Command int seekCommand,
        boolean isRepeatingCurrentItem) {
      assertThat(this.mediaItemIndex).isEqualTo(mediaItemIndex);
      assertThat(this.positionMs).isEqualTo(positionMs);
      assertThat(this.seekCommand).isEqualTo(seekCommand);
      assertThat(this.isRepeatingCurrentItem).isEqualTo(isRepeatingCurrentItem);
    }

    @Override
    protected void seekTo(
        int mediaItemIndex,
        long positionMs,
        @Player.Command int seekCommand,
        boolean isRepeatingCurrentItem) {
      this.mediaItemIndex = mediaItemIndex;
      this.positionMs = positionMs;
      this.seekCommand = seekCommand;
      this.isRepeatingCurrentItem = isRepeatingCurrentItem;
    }

    @Override
    public long getSeekBackIncrement() {
      return 2000;
    }

    @Override
    public long getSeekForwardIncrement() {
      return 2000;
    }

    @Override
    public long getMaxSeekToPreviousPosition() {
      return 2000;
    }

    @Override
    public Timeline getCurrentTimeline() {
      return new FakeTimeline(/* windowCount= */ 3);
    }

    @Override
    public int getCurrentMediaItemIndex() {
      return 1;
    }

    @Override
    public long getCurrentPosition() {
      return 5000;
    }

    @Override
    public long getDuration() {
      return 20000;
    }

    @Override
    public boolean isPlayingAd() {
      return false;
    }

    @Override
    public int getRepeatMode() {
      return Player.REPEAT_MODE_OFF;
    }

    @Override
    public boolean getShuffleModeEnabled() {
      return false;
    }
  }
}
