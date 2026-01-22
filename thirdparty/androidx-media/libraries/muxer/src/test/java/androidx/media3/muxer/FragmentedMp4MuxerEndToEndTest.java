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
package androidx.media3.muxer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.muxer.MuxerTestUtil.MP4_FILE_ASSET_DIRECTORY;

import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.container.Mp4TimestampData;
import androidx.media3.exoplayer.MediaExtractorCompat;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.DumpableMp4Box;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** End to end instrumentation tests for {@link FragmentedMp4Muxer}. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class FragmentedMp4MuxerEndToEndTest {
  private static final String H264_WITH_PYRAMID_B_FRAMES_MP4 =
      "bbb_800x640_768kbps_30fps_avc_pyramid_3b.mp4";
  private static final String H265_HDR10_MP4 = "hdr10-720p.mp4";
  private static final String AUDIO_ONLY_MP4 = "sample_audio_only_15s.mp4";

  @Parameters(name = "{0}")
  public static ImmutableList<String> mediaSamples() {
    return ImmutableList.of(H264_WITH_PYRAMID_B_FRAMES_MP4, H265_HDR10_MP4);
  }

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Parameter public @MonotonicNonNull String inputFile;

  private final Context context = ApplicationProvider.getApplicationContext();
  private @MonotonicNonNull String outputPath;
  private @MonotonicNonNull FileOutputStream outputStream;

  @Before
  public void setUp() throws Exception {
    outputPath = temporaryFolder.newFile("muxeroutput.mp4").getPath();
    outputStream = new FileOutputStream(outputPath);
  }

  @After
  public void tearDown() throws IOException {
    checkNotNull(outputStream).close();
  }

  @Test
  public void createFragmentedMp4File_fromInputFileSampleData_matchesExpected() throws Exception {
    @Nullable FragmentedMp4Muxer fragmentedMp4Muxer = null;

    try {
      fragmentedMp4Muxer = new FragmentedMp4Muxer.Builder(checkNotNull(outputStream)).build();
      fragmentedMp4Muxer.addMetadataEntry(
          new Mp4TimestampData(
              /* creationTimestampSeconds= */ 100_000_000L,
              /* modificationTimestampSeconds= */ 500_000_000L));
      feedInputDataToMuxer(context, fragmentedMp4Muxer, checkNotNull(inputFile));
    } finally {
      if (fragmentedMp4Muxer != null) {
        fragmentedMp4Muxer.close();
      }
    }

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(
            new FragmentedMp4Extractor(), checkNotNull(outputPath));
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath(inputFile + "_fragmented"));
  }

  @Test
  public void createFragmentedMp4File_fromInputFileSampleData_matchesExpectedBoxStructure()
      throws Exception {
    @Nullable FragmentedMp4Muxer fragmentedMp4Muxer = null;

    try {
      fragmentedMp4Muxer = new FragmentedMp4Muxer.Builder(checkNotNull(outputStream)).build();
      fragmentedMp4Muxer.addMetadataEntry(
          new Mp4TimestampData(
              /* creationTimestampSeconds= */ 100_000_000L,
              /* modificationTimestampSeconds= */ 500_000_000L));
      feedInputDataToMuxer(context, fragmentedMp4Muxer, H265_HDR10_MP4);
    } finally {
      if (fragmentedMp4Muxer != null) {
        fragmentedMp4Muxer.close();
      }
    }

    DumpableMp4Box dumpableMp4Box =
        new DumpableMp4Box(
            ByteBuffer.wrap(TestUtil.getByteArrayFromFilePath(checkNotNull(outputPath))));
    DumpFileAsserts.assertOutput(
        context,
        dumpableMp4Box,
        MuxerTestUtil.getExpectedDumpFilePath(H265_HDR10_MP4 + "_fragmented_box_structure"));
  }

  @Test
  public void createFragmentedMp4File_fromAudioOnlyInputFile_writesExpectedFragments()
      throws Exception {
    @Nullable FragmentedMp4Muxer fragmentedMp4Muxer = null;

    try {
      fragmentedMp4Muxer = new FragmentedMp4Muxer.Builder(checkNotNull(outputStream)).build();
      fragmentedMp4Muxer.addMetadataEntry(
          new Mp4TimestampData(
              /* creationTimestampSeconds= */ 100_000_000L,
              /* modificationTimestampSeconds= */ 500_000_000L));
      feedInputDataToMuxer(context, fragmentedMp4Muxer, AUDIO_ONLY_MP4);
    } finally {
      if (fragmentedMp4Muxer != null) {
        fragmentedMp4Muxer.close();
      }
    }

    DumpableMp4Box dumpableMp4Box =
        new DumpableMp4Box(
            ByteBuffer.wrap(TestUtil.getByteArrayFromFilePath(checkNotNull(outputPath))));
    // For a 15 sec audio, there should be 8 fragments (2 sec fragment duration).
    DumpFileAsserts.assertOutput(
        context,
        dumpableMp4Box,
        MuxerTestUtil.getExpectedDumpFilePath(AUDIO_ONLY_MP4 + "_fragmented_box_structure"));
  }

  private static void feedInputDataToMuxer(
      Context context, FragmentedMp4Muxer muxer, String inputFileName)
      throws IOException, MuxerException {
    MediaExtractorCompat extractor = new MediaExtractorCompat(context);
    Uri fileUri = Uri.parse(MP4_FILE_ASSET_DIRECTORY + inputFileName);
    extractor.setDataSource(fileUri, /* offset= */ 0);

    List<Integer> addedTracks = new ArrayList<>();
    for (int i = 0; i < extractor.getTrackCount(); i++) {
      int trackId =
          muxer.addTrack(MediaFormatUtil.createFormatFromMediaFormat(extractor.getTrackFormat(i)));
      addedTracks.add(trackId);
      extractor.selectTrack(i);
    }

    do {
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      bufferInfo.flags = extractor.getSampleFlags();
      bufferInfo.offset = 0;
      bufferInfo.presentationTimeUs = extractor.getSampleTime();
      int sampleSize = (int) extractor.getSampleSize();
      bufferInfo.size = sampleSize;

      ByteBuffer sampleBuffer = ByteBuffer.allocateDirect(sampleSize);
      extractor.readSampleData(sampleBuffer, /* offset= */ 0);

      sampleBuffer.rewind();

      muxer.writeSampleData(
          addedTracks.get(extractor.getSampleTrackIndex()), sampleBuffer, bufferInfo);
    } while (extractor.advance());

    extractor.release();
  }
}
