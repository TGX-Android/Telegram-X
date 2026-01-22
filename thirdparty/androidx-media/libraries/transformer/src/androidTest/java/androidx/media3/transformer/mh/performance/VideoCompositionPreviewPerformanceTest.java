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

package androidx.media3.transformer.mh.performance;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.SurfaceView;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Util;
import androidx.media3.effect.Contrast;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.CompositionPlayer;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.PlayerTestListener;
import androidx.media3.transformer.SurfaceTestActivity;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Performance tests for the composition previewing pipeline in {@link CompositionPlayer}. */
@RunWith(AndroidJUnit4.class)
public class VideoCompositionPreviewPerformanceTest {

  private static final long TEST_TIMEOUT_MS = 10_000;
  private static final long MEDIA_ITEM_CLIP_DURATION_MS = 500;

  @Rule
  public ActivityScenarioRule<SurfaceTestActivity> rule =
      new ActivityScenarioRule<>(SurfaceTestActivity.class);

  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private SurfaceView surfaceView;
  private @MonotonicNonNull CompositionPlayer player;

  @Before
  public void setUpSurface() {
    rule.getScenario().onActivity(activity -> surfaceView = activity.getSurfaceView());
  }

  @After
  public void tearDown() {
    instrumentation.runOnMainSync(
        () -> {
          if (player != null) {
            player.release();
          }
        });
    rule.getScenario().close();
  }

  /**
   * This test guards against performance regressions in the effects preview pipeline that format
   * switches do not cause the player to stall.
   */
  @Test
  @Ignore("TODO: b/375349144 - Fix this test and re-enable it")
  public void compositionPlayerCompositionPreviewTest() throws PlaybackException, TimeoutException {
    PlayerTestListener listener = new PlayerTestListener(TEST_TIMEOUT_MS);
    instrumentation.runOnMainSync(
        () -> {
          player = new CompositionPlayer.Builder(getApplicationContext()).build();
          player.setVideoSurfaceView(surfaceView);
          player.setPlayWhenReady(false);
          player.addListener(listener);
          player.setComposition(
              new Composition.Builder(
                      new EditedMediaItemSequence.Builder(
                              getClippedEditedMediaItem(MP4_ASSET.uri, new Contrast(.2f)),
                              getClippedEditedMediaItem(MP4_ASSET.uri, new Contrast(-.2f)))
                          .build())
                  .build());
          player.prepare();
        });

    listener.waitUntilPlayerReady();

    AtomicLong playbackStartTimeMs = new AtomicLong();
    instrumentation.runOnMainSync(
        () -> {
          playbackStartTimeMs.set(SystemClock.elapsedRealtime());
          checkNotNull(player).play();
        });

    listener.waitUntilPlayerEnded();
    long compositionDurationMs = MEDIA_ITEM_CLIP_DURATION_MS * 2;
    long playbackDurationMs = SystemClock.elapsedRealtime() - playbackStartTimeMs.get();

    assertThat(playbackDurationMs)
        .isIn(Range.closed(compositionDurationMs, compositionDurationMs + 250));
  }

  private static EditedMediaItem getClippedEditedMediaItem(String uri, Effect effect) {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(MEDIA_ITEM_CLIP_DURATION_MS)
                    .build())
            .build();

    return new EditedMediaItem.Builder(mediaItem)
        .setEffects(
            new Effects(
                /* audioProcessors= */ ImmutableList.of(),
                /* videoEffects= */ ImmutableList.of(effect)))
        .setDurationUs(Util.msToUs(MEDIA_ITEM_CLIP_DURATION_MS))
        .build();
  }
}
