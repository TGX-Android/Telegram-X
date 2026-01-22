/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.effect.EffectsTestUtil.generateAndProcessFrames;
import static androidx.media3.effect.EffectsTestUtil.getAndAssertOutputBitmaps;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.Pair;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.util.Consumer;
import androidx.media3.test.utils.TextureBitmapReader;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Tests for {@link QueuingGlShaderProgram}. */
@RunWith(AndroidJUnit4.class)
public class QueuingGlShaderProgramTest {
  @Rule public final TestName testName = new TestName();

  private static final String ASSET_PATH = "test-generated-goldens/QueuingGlShaderProgramTest";

  private static final int BLANK_FRAME_WIDTH = 100;
  private static final int BLANK_FRAME_HEIGHT = 50;
  private static final Consumer<SpannableString> TEXT_SPAN_CONSUMER =
      (text) -> {
        text.setSpan(
            new ForegroundColorSpan(Color.BLACK),
            /* start= */ 0,
            text.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
            new AbsoluteSizeSpan(/* size= */ 24),
            /* start= */ 0,
            text.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
            new TypefaceSpan(/* family= */ "sans-serif"),
            /* start= */ 0,
            text.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      };

  private @MonotonicNonNull TextureBitmapReader textureBitmapReader;
  private String testId;

  @Before
  public void setUp() {
    textureBitmapReader = new TextureBitmapReader();
    testId = testName.getMethodName();
  }

  @Test
  public void queuingGlShaderProgram_withQueueSizeOne_outputsFramesInOrder() throws Exception {
    List<Pair<String, Long>> events = new ArrayList<>();
    ImmutableList<Long> frameTimesUs = ImmutableList.of(0L, 333_333L, 666_667L);
    ImmutableList<Long> actualPresentationTimesUs =
        generateAndProcessFrames(
            BLANK_FRAME_WIDTH,
            BLANK_FRAME_HEIGHT,
            frameTimesUs,
            new TestGlEffect(events, /* queueSize= */ 1),
            textureBitmapReader,
            TEXT_SPAN_CONSUMER);

    assertThat(actualPresentationTimesUs).containsExactlyElementsIn(frameTimesUs).inOrder();
    assertThat(events)
        .containsExactly(
            Pair.create("queueInputFrame", 0L),
            Pair.create("finishProcessingAndBlend", 0L),
            Pair.create("queueInputFrame", 333_333L),
            Pair.create("finishProcessingAndBlend", 333_333L),
            Pair.create("queueInputFrame", 666_667L),
            Pair.create("finishProcessingAndBlend", 666_667L))
        .inOrder();

    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId, ASSET_PATH);
  }

  @Test
  public void queuingGlShaderProgram_withQueueSizeTwo_outputsFramesInOrder() throws Exception {
    List<Pair<String, Long>> events = new ArrayList<>();
    ImmutableList<Long> frameTimesUs = ImmutableList.of(0L, 333_333L, 666_667L);
    ImmutableList<Long> actualPresentationTimesUs =
        generateAndProcessFrames(
            BLANK_FRAME_WIDTH,
            BLANK_FRAME_HEIGHT,
            frameTimesUs,
            new TestGlEffect(events, /* queueSize= */ 2),
            textureBitmapReader,
            TEXT_SPAN_CONSUMER);

    assertThat(actualPresentationTimesUs).containsExactlyElementsIn(frameTimesUs).inOrder();
    assertThat(events)
        .containsExactly(
            Pair.create("queueInputFrame", 0L),
            Pair.create("queueInputFrame", 333_333L),
            Pair.create("finishProcessingAndBlend", 0L),
            Pair.create("queueInputFrame", 666_667L),
            Pair.create("finishProcessingAndBlend", 333_333L),
            Pair.create("finishProcessingAndBlend", 666_667L))
        .inOrder();
    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId, ASSET_PATH);
  }

  private static class TestGlEffect implements GlEffect {

    private final List<Pair<String, Long>> events;
    private final int queueSize;

    TestGlEffect(List<Pair<String, Long>> events, int queueSize) {
      this.events = events;
      this.queueSize = queueSize;
    }

    @Override
    public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr) {
      return new QueuingGlShaderProgram<>(
          /* useHighPrecisionColorComponents= */ useHdr,
          queueSize,
          new NoOpConcurrentEffect(events));
    }
  }

  private static class NoOpConcurrentEffect
      implements QueuingGlShaderProgram.ConcurrentEffect<Long> {
    private final List<Pair<String, Long>> events;

    NoOpConcurrentEffect(List<Pair<String, Long>> events) {
      this.events = events;
    }

    @Override
    public Future<Long> queueInputFrame(
        GlObjectsProvider glObjectsProvider, GlTextureInfo textureInfo, long presentationTimeUs) {
      checkState(textureInfo.width == BLANK_FRAME_WIDTH);
      checkState(textureInfo.height == BLANK_FRAME_HEIGHT);
      events.add(Pair.create("queueInputFrame", presentationTimeUs));
      return immediateFuture(presentationTimeUs);
    }

    @Override
    public void finishProcessingAndBlend(
        GlTextureInfo outputFrame, long presentationTimeUs, Long result) {
      checkState(result == presentationTimeUs);
      events.add(Pair.create("finishProcessingAndBlend", presentationTimeUs));
    }

    @Override
    public void signalEndOfCurrentInputStream() {}

    @Override
    public void flush() {}

    @Override
    public void release() {}
  }
}
