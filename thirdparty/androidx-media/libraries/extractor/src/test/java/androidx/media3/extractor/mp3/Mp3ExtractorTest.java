/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.extractor.mp3;

import static org.junit.Assume.assumeFalse;

import androidx.media3.test.utils.ExtractorAsserts;
import androidx.media3.test.utils.ExtractorAsserts.AssertionConfig;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Unit test for {@link Mp3Extractor}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class Mp3ExtractorTest {

  @Parameters(name = "{0}")
  public static ImmutableList<ExtractorAsserts.SimulationConfig> params() {
    return ExtractorAsserts.configs();
  }

  @Parameter public ExtractorAsserts.SimulationConfig simulationConfig;

  @Test
  public void mp3SampleWithXingHeader() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new, "media/mp3/bear-vbr-xing-header.mp3", simulationConfig);
  }

  @Test
  public void mp3SampleWithInfoHeader() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new, "media/mp3/test-cbr-info-header.mp3", simulationConfig);
  }

  // https://github.com/androidx/media/issues/1376#issuecomment-2117393653
  @Test
  public void mp3SampleWithInfoHeaderAndPcutFrame() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new, "media/mp3/test-cbr-info-header-pcut-frame.mp3", simulationConfig);
  }

  // https://github.com/androidx/media/issues/1480
  @Test
  public void mp3SampleWithInfoHeaderAndTrailingGarbage() throws Exception {
    // This test file is test-cbr-info-header.mp3 with 150kB of 0xDEADBEEF garbage appended on the
    // end. The test asserts that the extracted samples are the same as for
    // test-cbr-info-header.mp3.
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new,
        "media/mp3/test-cbr-info-header-trailing-garbage.mp3",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/mp3/test-cbr-info-header.mp3")
            .build(),
        simulationConfig);
  }

  @Test
  public void mp3SampleWithVbriHeader() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new, "media/mp3/bear-vbr-vbri-header.mp3", simulationConfig);
  }

  // https://github.com/androidx/media/issues/1904
  @Test
  public void mp3SampleWithVbriHeaderWithTruncatedToC() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new, "media/mp3/bear-vbr-vbri-header-truncated-toc.mp3", simulationConfig);
  }

  @Test
  public void mp3SampleWithCbrSeeker() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new,
        "media/mp3/bear-cbr-variable-frame-size-no-seek-table.mp3",
        simulationConfig);
  }

  @Test
  public void mp3SampleWithCbrSeekingAlwaysEnabled() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new Mp3Extractor(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS),
        "media/mp3/bear-cbr-variable-frame-size-no-seek-table.mp3",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/mp3/bear-cbr_cbr-seeking-always-enabled")
            .build(),
        simulationConfig);
  }

  @Test
  public void mp3SampleWithIndexSeeker() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new Mp3Extractor(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING),
        "media/mp3/bear-vbr-no-seek-table.mp3",
        simulationConfig);
  }

  // https://github.com/androidx/media/issues/1563
  @Test
  public void mp3CbrSampleWithNoSeekTableAndTrailingGarbage() throws Exception {
    assumeFalse(
        "Skipping I/O error testing with unknown length due to b/362727473",
        simulationConfig.simulateIOErrors && simulationConfig.simulateUnknownLength);
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new,
        "media/mp3/bear-cbr-no-seek-table-trailing-garbage.mp3",
        simulationConfig);
  }

  @Test
  public void trimmedMp3Sample() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new, "media/mp3/play-trimmed.mp3", simulationConfig);
  }

  @Test
  public void mp3SampleWithId3Enabled() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new,
        "media/mp3/bear-id3.mp3",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/mp3/bear-id3-enabled")
            .build(),
        simulationConfig);
  }

  @Test
  public void mp3SampleWithId3Disabled() throws Exception {
    ExtractorAsserts.assertBehavior(
        () -> new Mp3Extractor(Mp3Extractor.FLAG_DISABLE_ID3_METADATA),
        "media/mp3/bear-id3.mp3",
        new AssertionConfig.Builder()
            .setDumpFilesPrefix("extractordumps/mp3/bear-id3-disabled")
            .build(),
        simulationConfig);
  }

  @Test
  public void mp3SampleWithId3NumericGenre() throws Exception {
    ExtractorAsserts.assertBehavior(
        Mp3Extractor::new, "media/mp3/bear-id3-numeric-genre.mp3", simulationConfig);
  }
}
