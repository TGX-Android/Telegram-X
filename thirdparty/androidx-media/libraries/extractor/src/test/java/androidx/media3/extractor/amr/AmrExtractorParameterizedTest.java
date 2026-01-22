/*
 * Copyright (C) 2018 The Android Open Source Project
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
package androidx.media3.extractor.amr;

import androidx.media3.test.utils.ExtractorAsserts;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/**
 * Unit tests for {@link AmrExtractor} that use parameterization to test a range of behaviours.
 *
 * <p>For non-parameterized tests see {@link AmrExtractorSeekTest} and {@link
 * AmrExtractorNonParameterizedTest}.
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class AmrExtractorParameterizedTest {

  @Parameters(name = "{0}")
  public static ImmutableList<ExtractorAsserts.SimulationConfig> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter public ExtractorAsserts.SimulationConfig simulationConfig;

  @Test
  public void extractingNarrowBandSamples() throws Exception {
    ExtractorAsserts.assertBehavior(AmrExtractor::new, "media/amr/sample_nb.amr", simulationConfig);
  }

  @Test
  public void extractingWideBandSamples() throws Exception {
    ExtractorAsserts.assertBehavior(AmrExtractor::new, "media/amr/sample_wb.amr", simulationConfig);
  }

  @Test
  public void extractingNarrowBandSamples_withSeeking() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
        "media/amr/sample_nb.amr",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/amr/sample_nb_cbr_seeking_enabled.amr")
            .build(),
        simulationConfig);
  }

  @Test
  public void extractingWideBandSamples_withSeeking() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
        "media/amr/sample_wb.amr",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/amr/sample_wb_cbr_seeking_enabled.amr")
            .build(),
        simulationConfig);
  }

  @Test
  public void extractingNarrowBandSamples_withSeekingAlways() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS),
        "media/amr/sample_nb.amr",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/amr/sample_nb_cbr_seeking_always_enabled.amr")
            .build(),
        simulationConfig);
  }

  @Test
  public void extractingWideBandSamples_withSeekingAlways() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS),
        "media/amr/sample_wb.amr",
        new ExtractorAsserts.AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/amr/sample_wb_cbr_seeking_always_enabled.amr")
            .build(),
        simulationConfig);
  }

  @Test
  public void extractingNarrowBandSamples_withIndexSeeking() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new AmrExtractor(AmrExtractor.FLAG_ENABLE_INDEX_SEEKING),
        "media/amr/sample_nb_with_silence_frames.amr",
        simulationConfig);
  }

  @Test
  public void extractingWideBandSamples_withIndexSeeking() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new AmrExtractor(AmrExtractor.FLAG_ENABLE_INDEX_SEEKING),
        "media/amr/sample_wb_with_silence_frames.amr",
        simulationConfig);
  }
}
