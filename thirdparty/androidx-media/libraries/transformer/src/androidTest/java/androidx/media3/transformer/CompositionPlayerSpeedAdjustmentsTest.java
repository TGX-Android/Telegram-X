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
package androidx.media3.transformer;

import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET;
import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.util.Pair;
import android.view.SurfaceView;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.effect.GlEffect;
import androidx.media3.test.utils.TestSpeedProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Instrumentation tests for {@link CompositionPlayer} with Speed Adjustments. */
@RunWith(AndroidJUnit4.class)
public class CompositionPlayerSpeedAdjustmentsTest {
  private static final long TEST_TIMEOUT_MS = 10_000;

  @Rule
  public ActivityScenarioRule<SurfaceTestActivity> rule =
      new ActivityScenarioRule<>(SurfaceTestActivity.class);

  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context applicationContext = instrumentation.getContext().getApplicationContext();

  private CompositionPlayer compositionPlayer;
  private SurfaceView surfaceView;

  @Before
  public void setupSurfaces() {
    rule.getScenario().onActivity(activity -> surfaceView = activity.getSurfaceView());
  }

  @After
  public void closeActivity() {
    rule.getScenario().close();
  }

  @Test
  public void videoPreview_withSpeedAdjustment_timestampsAreCorrect() throws Exception {
    Pair<AudioProcessor, Effect> effects =
        Effects.createExperimentalSpeedChangingEffect(
            TestSpeedProvider.createWithStartTimes(
                new long[] {0, 300_000L, 600_000L}, new float[] {2f, 1f, 0.5f}));
    EditedMediaItem video =
        new EditedMediaItem.Builder(MediaItem.fromUri(MP4_ASSET.uri))
            .setDurationUs(1_000_000)
            .setEffects(
                new Effects(ImmutableList.of(effects.first), ImmutableList.of(effects.second)))
            .build();
    ImmutableList<Long> expectedTimestamps =
        ImmutableList.of(
            0L, 16683L, 33366L, 50050L, 66733L, 83416L, 100100L, 116783L, 133466L, 150300L, 183666L,
            217033L, 250400L, 283766L, 317133L, 350500L, 383866L, 417233L, 451200L, 517932L,
            584666L, 651400L, 718132L, 784866L, 851600L, 918332L, 985066L, 1051800L, 1118532L,
            1185266L);

    ImmutableList<Long> timestampsFromCompositionPlayer = getTimestampsFromCompositionPlayer(video);

    assertThat(timestampsFromCompositionPlayer).isEqualTo(expectedTimestamps);
  }

  private ImmutableList<Long> getTimestampsFromCompositionPlayer(EditedMediaItem item)
      throws Exception {
    PlayerTestListener compositionPlayerListener = new PlayerTestListener(TEST_TIMEOUT_MS);
    InputTimestampRecordingShaderProgram timestampRecordingShaderProgram =
        new InputTimestampRecordingShaderProgram();
    ImmutableList<EditedMediaItem> timestampRecordingEditedMediaItems =
        appendVideoEffects(
            item,
            /* effects= */ ImmutableList.of(
                (GlEffect) (context, useHdr) -> timestampRecordingShaderProgram));

    instrumentation.runOnMainSync(
        () -> {
          compositionPlayer = new CompositionPlayer.Builder(applicationContext).build();
          // Set a surface on the player even though there is no UI on this test. We need a surface
          // otherwise the player will skip/drop video frames.
          compositionPlayer.setVideoSurfaceView(surfaceView);
          compositionPlayer.addListener(compositionPlayerListener);
          compositionPlayer.setComposition(
              new Composition.Builder(
                      new EditedMediaItemSequence.Builder(timestampRecordingEditedMediaItems)
                          .build())
                  .experimentalSetForceAudioTrack(true)
                  .build());
          compositionPlayer.prepare();
          compositionPlayer.play();
        });

    compositionPlayerListener.waitUntilPlayerEnded();
    instrumentation.runOnMainSync(() -> compositionPlayer.release());

    return timestampRecordingShaderProgram.getInputTimestampsUs();
  }

  private static ImmutableList<EditedMediaItem> appendVideoEffects(
      EditedMediaItem item, List<Effect> effects) {
    return ImmutableList.of(
        item.buildUpon()
            .setEffects(
                new Effects(
                    item.effects.audioProcessors,
                    new ImmutableList.Builder<Effect>()
                        .addAll(item.effects.videoEffects)
                        .addAll(effects)
                        .build()))
            .build());
  }
}
