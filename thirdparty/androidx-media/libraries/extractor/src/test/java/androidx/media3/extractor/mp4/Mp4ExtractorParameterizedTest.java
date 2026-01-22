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
package androidx.media3.extractor.mp4;

import static androidx.media3.extractor.mp4.Mp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA;

import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.test.utils.ExtractorAsserts;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** Parameterized tests for {@link Mp4Extractor} using {@link ExtractorAsserts}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class Mp4ExtractorParameterizedTest {

  @Parameters(name = "{0},subtitlesParsedDuringExtraction={1},readWithinGopSampleDependencies={2}")
  public static List<Object[]> params() {
    List<Object[]> parameterList = new ArrayList<>();
    for (ExtractorAsserts.SimulationConfig config : ExtractorAsserts.configs()) {
      parameterList.add(
          new Object[] {
            config,
            /* subtitlesParsedDuringExtraction */ true,
            /* readWithinGopSampleDependencies */ false
          });
      parameterList.add(
          new Object[] {
            config,
            /* subtitlesParsedDuringExtraction */ false,
            /* readWithinGopSampleDependencies */ false
          });
      parameterList.add(
          new Object[] {
            config,
            /* subtitlesParsedDuringExtraction */ true,
            /* readWithinGopSampleDependencies */ true
          });
    }
    return parameterList;
  }

  @Parameter(0)
  public ExtractorAsserts.SimulationConfig simulationConfig;

  @Parameter(1)
  public boolean subtitlesParsedDuringExtraction;

  @Parameter(2)
  public boolean readWithinGopSampleDependencies;

  @Test
  public void mp4Sample() throws Exception {
    assertExtractorBehavior("media/mp4/sample.mp4");
  }

  @Test
  public void mp4SampleWithSlowMotionMetadata() throws Exception {
    assertExtractorBehavior("media/mp4/sample_android_slow_motion.mp4");
  }

  @Test
  public void mp4SampleWithMetadata() throws Exception {
    assertExtractorBehavior("media/mp4/sample_with_metadata.mp4");
  }

  @Test
  public void mp4SampleWithNumericGenre() throws Exception {
    assertExtractorBehavior("media/mp4/sample_with_numeric_genre.mp4");
  }

  /**
   * Test case for https://github.com/google/ExoPlayer/issues/6774. The sample file contains an mdat
   * atom whose size indicates that it extends 8 bytes beyond the end of the file.
   */
  @Test
  public void mp4SampleWithMdatTooLong() throws Exception {
    assertExtractorBehavior("media/mp4/sample_mdat_too_long.mp4");
  }

  @Test
  public void mp4SampleWithAc3Track() throws Exception {
    assertExtractorBehavior("media/mp4/sample_ac3.mp4");
  }

  @Test
  public void mp4SampleWithAc4Track() throws Exception {
    assertExtractorBehavior("media/mp4/sample_ac4.mp4");
  }

  @Test
  public void mp4SampleWithAc4Level4Track() throws Exception {
    assertExtractorBehavior("media/mp4/sample_ac4_level4.mp4");
  }

  @Test
  public void mp4SampleWithEac3Track() throws Exception {
    assertExtractorBehavior("media/mp4/sample_eac3.mp4");
  }

  @Test
  public void mp4SampleWithEac3jocTrack() throws Exception {
    assertExtractorBehavior("media/mp4/sample_eac3joc.mp4");
  }

  @Test
  public void mp4SampleWithOpusTrack() throws Exception {
    assertExtractorBehavior("media/mp4/sample_opus.mp4");
  }

  @Test
  public void mp4SampleWithMha1Track() throws Exception {
    assertExtractorBehavior("media/mp4/sample_mpegh_mha1.mp4");
  }

  @Test
  public void mp4SampleWithMhm1Track() throws Exception {
    assertExtractorBehavior("media/mp4/sample_mpegh_mhm1.mp4");
  }

  @Test
  public void mp4SampleWithColorInfo() throws Exception {
    assertExtractorBehavior("media/mp4/sample_with_color_info.mp4");
  }

  /**
   * Test case for https://github.com/google/ExoPlayer/issues/9332. The file contains a colr box
   * with size=18 and type=nclx. This is not valid according to the spec (size must be 19), but
   * files like this exist in the wild.
   */
  @Test
  public void mp4Sample18ByteNclxColr() throws Exception {
    assertExtractorBehavior("media/mp4/sample_18byte_nclx_colr.mp4");
  }

  @Test
  public void mp4SampleWithDolbyTrueHDTrack() throws Exception {
    assertExtractorBehavior("media/mp4/sample_dthd.mp4");
  }

  @Test
  public void mp4SampleWithColrMdcvAndClli() throws Exception {
    assertExtractorBehavior("media/mp4/sample_with_colr_mdcv_and_clli.mp4");
  }

  /** Test case for supporting original QuickTime specification [Internal: b/297137302]. */
  @Test
  public void mp4SampleWithOriginalQuicktimeSpecification() throws Exception {
    assertExtractorBehavior("media/mp4/sample_with_original_quicktime_specification.mov");
  }

  @Test
  public void mp4SampleWithAv1c() throws Exception {
    assertExtractorBehavior("media/mp4/sample_with_av1c.mp4");
  }

  @Test
  public void mp4SampleWithApvC() throws Exception {
    assertExtractorBehavior("media/mp4/sample_with_apvc.mp4");
  }

  @Test
  public void mp4SampleWithMhm1BlCicp1Track() throws Exception {
    assertExtractorBehavior("media/mp4/sample_mhm1_bl_cicp1.mp4");
  }

  @Test
  public void mp4SampleWithMhm1LcBlCicp1Track() throws Exception {
    assertExtractorBehavior("media/mp4/sample_mhm1_lcbl_cicp1.mp4");
  }

  @Test
  public void mp4SampleWithMhm1BlConfigChangeTrack() throws Exception {
    assertExtractorBehavior("media/mp4/sample_mhm1_bl_configchange.mp4");
  }

  @Test
  public void mp4SampleWithMhm1LcBlConfigChangeTrack() throws Exception {
    assertExtractorBehavior("media/mp4/sample_mhm1_lcbl_configchange.mp4");
  }

  @Test
  public void mp4SampleWithEditList() throws Exception {
    assertExtractorBehavior("media/mp4/sample_edit_list.mp4");
  }

  @Test
  public void mp4SampleWithEditListAndNoSyncFrameBeforeEdit() throws Exception {
    assertExtractorBehavior("media/mp4/sample_edit_list_no_sync_frame_before_edit.mp4");
  }

  @Test
  public void mp4SampleWithEmptyTrack() throws Exception {
    assertExtractorBehavior("media/mp4/sample_empty_track.mp4");
  }

  @Test
  public void mp4SampleWithTwoTracksOneWithSingleFrame() throws Exception {
    assertExtractorBehavior("media/mp4/pixel-motion-photo-2-hevc-tracks.mp4");
  }

  @Test
  public void mp4SampleWithNoMaxNumReorderFramesValue() throws Exception {
    assertExtractorBehavior("media/mp4/bt601.mov");
  }

  @Test
  public void mp4sampleWithIamfTrack() throws Exception {
    assertExtractorBehavior("media/mp4/sample_iamf.mp4");
  }

  @Test
  public void mp4SampleWithMvHevc8bit() throws Exception {
    assertExtractorBehavior("media/mp4/water_180_mvhevc_5frames.mov");
  }

  @Test
  public void mp4WithAuxiliaryTracks() throws Exception {
    assertExtractorBehavior("media/mp4/sample_with_fake_auxiliary_tracks.mp4");
  }

  @Test
  public void mp4WithAuxiliaryTracksInterleavedWithPrimaryVideoTracks() throws Exception {
    assertExtractorBehavior(
        "media/mp4/sample_with_fake_auxiliary_tracks_interleaved_with_primary_video_tracks.mp4");
  }

  @Test
  public void mp4SampleWithEmptyNalu() throws Exception {
    assertExtractorBehavior("media/mp4/sample_with_invalid_nalu.mp4");
  }

  @Test
  public void mp4SampleWithNonReferenceH265Frames() throws Exception {
    assertExtractorBehavior("media/mp4/h265_bframes.mp4");
  }

  // b/386847142
  @Test
  public void mp4SampleWithTwoByteNalLength() throws Exception {
    assertExtractorBehavior("media/mp4/sample_2_byte_NAL_length.mp4");
  }

  @Test
  public void mp4SampleWithBtrt() throws Exception {
    assertExtractorBehavior("media/mp4/sample_with_btrt.mp4");
  }

  private void assertExtractorBehavior(String file) throws IOException {
    ExtractorAsserts.AssertionConfig.Builder assertionConfigBuilder =
        new ExtractorAsserts.AssertionConfig.Builder();
    if (readWithinGopSampleDependencies) {
      String dumpFilesPrefix =
          file.replaceFirst("media", "extractordumps") + ".reading_within_gop_sample_dependencies";
      assertionConfigBuilder.setDumpFilesPrefix(dumpFilesPrefix);
    }
    ExtractorAsserts.assertBehavior(
        getExtractorFactory(subtitlesParsedDuringExtraction, readWithinGopSampleDependencies),
        file,
        assertionConfigBuilder.build(),
        simulationConfig);
  }

  private static ExtractorAsserts.ExtractorFactory getExtractorFactory(
      boolean subtitlesParsedDuringExtraction, boolean readWithinGopSampleDependencies) {
    SubtitleParser.Factory subtitleParserFactory;
    @Mp4Extractor.Flags int flags;
    if (subtitlesParsedDuringExtraction) {
      subtitleParserFactory = new DefaultSubtitleParserFactory();
      flags = 0;
    } else {
      subtitleParserFactory = SubtitleParser.Factory.UNSUPPORTED;
      flags = FLAG_EMIT_RAW_SUBTITLE_DATA;
    }
    if (readWithinGopSampleDependencies) {
      flags |= Mp4Extractor.FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES;
      flags |= Mp4Extractor.FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES_H265;
    }

    @Mp4Extractor.Flags int finalFlags = flags;
    return () -> new Mp4Extractor(subtitleParserFactory, finalFlags);
  }
}
