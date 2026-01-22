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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.TestUtil.extractAllSamplesFromFilePath;
import static androidx.media3.transformer.TestUtil.ASSET_URI_PREFIX;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.container.Mp4TimestampData;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** End to end parameterized tests for {@link Transformer} with {@link InAppMp4Muxer}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class TransformerWithInAppMp4MuxerEndToEndParameterizedTest {

  private static final String H263_3GP = "mp4/bbb_176x144_128kbps_15fps_h263.3gp";
  private static final String H264_MP4 = "mp4/sample_no_bframes.mp4";
  private static final String H265_MP4 = "mp4/h265_with_metadata_track.mp4";
  private static final String AV1_MP4 = "mp4/sample_av1.mp4";
  private static final String MPEG4_MP4 = "mp4/bbb_176x144_192kbps_15fps_mpeg4.mp4";
  private static final String AMR_NB_3GP = "mp4/bbb_mono_8kHz_12.2kbps_amrnb.3gp";
  private static final String AMR_WB_3GP = "mp4/bbb_mono_16kHz_23.05kbps_amrwb.3gp";
  private static final String OPUS_OGG = "mp4/bbb_6ch_8kHz_opus.ogg";
  private static final String VORBIS_OGG = "mp4/bbb_1ch_16kHz_q10_vorbis.ogg";
  private static final String DOLBY_VISION_MOV = "mp4/dolbyVision-hdr.MOV";

  @Parameters(name = "{0}")
  public static ImmutableList<String> mediaFiles() {
    return ImmutableList.of(
        H263_3GP,
        H264_MP4,
        H265_MP4,
        AV1_MP4,
        MPEG4_MP4,
        AMR_NB_3GP,
        AMR_WB_3GP,
        OPUS_OGG,
        VORBIS_OGG,
        DOLBY_VISION_MOV);
  }

  @Parameter public @MonotonicNonNull String inputFile;

  @Rule public final TemporaryFolder outputDir = new TemporaryFolder();

  private final Context context = ApplicationProvider.getApplicationContext();
  private @MonotonicNonNull String outputPath;

  @Before
  public void setup() throws Exception {
    outputPath = outputDir.newFile("muxeroutput.mp4").getPath();
  }

  @Test
  public void transmux_mp4File_outputMatchesExpected() throws Exception {
    Muxer.Factory inAppMuxerFactory =
        new InAppMp4Muxer.Factory(
            metadataEntries ->
                // Add timestamp to make output file deterministic.
                metadataEntries.add(
                    new Mp4TimestampData(
                        /* creationTimestampSeconds= */ 3_000_000_000L,
                        /* modificationTimestampSeconds= */ 4_000_000_000L)));

    Transformer transformer =
        new TestTransformerBuilder(context).setMuxerFactory(inAppMuxerFactory).build();
    MediaItem mediaItem = MediaItem.fromUri(ASSET_URI_PREFIX + checkNotNull(inputFile));

    transformer.start(mediaItem, outputPath);
    TransformerTestRunner.runLooper(transformer);

    FakeExtractorOutput fakeExtractorOutput =
        extractAllSamplesFromFilePath(
            new Mp4Extractor(new DefaultSubtitleParserFactory()), outputPath);
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        TestUtil.getDumpFileName(
            /* originalFileName= */ checkNotNull(inputFile),
            /* modifications...= */ "transmuxed_with_inappmuxer"));
  }
}
