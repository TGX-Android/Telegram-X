/*
 * Copyright 2023 The Android Open Source Project
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

import static androidx.media3.muxer.Mp4Muxer.LAST_SAMPLE_DURATION_BEHAVIOR_SET_FROM_END_OF_STREAM_BUFFER_OR_DUPLICATE_PREVIOUS;
import static androidx.media3.muxer.Mp4Muxer.LAST_SAMPLE_DURATION_BEHAVIOR_SET_TO_ZERO;
import static androidx.media3.muxer.MuxerTestUtil.FAKE_AUDIO_FORMAT;
import static androidx.media3.muxer.MuxerTestUtil.FAKE_CSD_0;
import static androidx.media3.muxer.MuxerTestUtil.FAKE_VIDEO_FORMAT;
import static androidx.media3.muxer.MuxerTestUtil.getExpectedDumpFilePath;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.media.MediaCodec;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4LocationData;
import androidx.media3.muxer.FragmentedMp4Writer.SampleMetadata;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.DumpableMp4Box;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link Boxes}. */
@RunWith(AndroidJUnit4.class)
public class BoxesTest {
  // A typical timescale is ~90_000. We're using 100_000 here to simplify calculations.
  // This makes one time unit equal to 10 microseconds.
  private static final int VU_TIMEBASE = 100_000;

  private Context context;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
  }

  @Test
  public void createTkhdBox_forVideoTrack_matchesExpected() throws IOException {
    ByteBuffer tkhdBox =
        Boxes.tkhd(
            /* trackId= */ 1,
            /* trackDurationUs= */ 500_000_000,
            /* creationTimestampSeconds= */ 1_000_000_000,
            /* modificationTimestampSeconds= */ 2_000_000_000,
            /* orientation= */ 90,
            FAKE_VIDEO_FORMAT);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(tkhdBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("video_track_tkhd_box"));
  }

  @Test
  public void createTkhdBox_forAudioTrack_matchesExpected() throws IOException {
    ByteBuffer tkhdBox =
        Boxes.tkhd(
            /* trackId= */ 1,
            /* trackDurationUs= */ 500_000_000,
            /* creationTimestampSeconds= */ 1_000_000_000,
            /* modificationTimestampSeconds= */ 2_000_000_000,
            /* orientation= */ 90,
            FAKE_AUDIO_FORMAT);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(tkhdBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("audio_track_tkhd_box"));
  }

  @Test
  public void createEdtsBox_forZeroStartTimeTrack_matchesExpected() throws IOException {
    ByteBuffer edtsBox =
        Boxes.edts(
            /* firstInputPtsUs= */ 0L,
            /* minInputPtsUs= */ 0L,
            /* trackDurationUs= */ 1_000_000L,
            /* mvhdTimescale= */ 10_000L,
            /* trackTimescale= */ 90_000L);

    assertThat(edtsBox.limit()).isEqualTo(0);
  }

  @Test
  public void createEdtsBox_forPositiveStartTimeTrack_matchesExpected() throws IOException {
    ByteBuffer edtsBox =
        Boxes.edts(
            /* firstInputPtsUs= */ 10_000L,
            /* minInputPtsUs= */ 0L,
            /* trackDurationUs= */ 1_000_000L,
            /* mvhdTimescale= */ 10_000L,
            /* trackTimescale= */ 90_000L);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(edtsBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("positive_start_time_edts_box"));
  }

  @Test
  public void createEdtsBox_forNegativeStartTimeTrack_matchesExpected() throws IOException {
    ByteBuffer edtsBox =
        Boxes.edts(
            /* firstInputPtsUs= */ -10_000L,
            /* minInputPtsUs= */ -20_000L,
            /* trackDurationUs= */ 1_000_000L,
            /* mvhdTimescale= */ 10_000L,
            /* trackTimescale= */ 90_000L);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(edtsBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("negative_start_time_edts_box"));
  }

  @Test
  public void createMvhdBox_matchesExpected() throws IOException {
    ByteBuffer mvhdBox =
        Boxes.mvhd(
            /* nextEmptyTrackId= */ 3,
            /* creationTimestampSeconds= */ 1_000_000_000,
            /* modificationTimestampSeconds= */ 2_000_000_000,
            /* videoDurationUs= */ 5_000_000);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(mvhdBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("mvhd_box"));
  }

  @Test
  public void createMdhdBox_matchesExpected() throws IOException {
    ByteBuffer mdhdBox =
        Boxes.mdhd(
            /* trackDurationVu= */ 5_000_000,
            VU_TIMEBASE,
            /* creationTimestampSeconds= */ 1_000_000_000,
            /* modificationTimestampSeconds= */ 2_000_000_000,
            /* languageCode= */ "und");

    DumpableMp4Box dumpableBox = new DumpableMp4Box(mdhdBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("mdhd_box"));
  }

  @Test
  public void createVmhdBox_matchesExpected() throws IOException {
    ByteBuffer vmhdBox = Boxes.vmhd();

    DumpableMp4Box dumpableBox = new DumpableMp4Box(vmhdBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("vmhd_box"));
  }

  @Test
  public void createSmhdBox_matchesExpected() throws IOException {
    ByteBuffer smhdBox = Boxes.smhd();

    DumpableMp4Box dumpableBox = new DumpableMp4Box(smhdBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("smhd_box"));
  }

  @Test
  public void createNmhdBox_matchesExpected() throws IOException {
    ByteBuffer nmhdBox = Boxes.nmhd();

    DumpableMp4Box dumpableBox = new DumpableMp4Box(nmhdBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("nmhd_box"));
  }

  @Test
  public void createEmptyDinfBox_matchesExpected() throws IOException {
    ByteBuffer dinfBox = Boxes.dinf(Boxes.dref(Boxes.localUrl()));

    DumpableMp4Box dumpableBox = new DumpableMp4Box(dinfBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("dinf_box_empty"));
  }

  @Test
  public void createHdlrBox_matchesExpected() throws IOException {
    // Create hdlr box for video track.
    ByteBuffer hdlrBox = Boxes.hdlr(/* handlerType= */ "vide", /* handlerName= */ "VideoHandle");

    DumpableMp4Box dumpableBox = new DumpableMp4Box(hdlrBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("hdlr_box"));
  }

  @Test
  public void createUdtaBox_matchesExpected() throws IOException {
    Mp4LocationData mp4Location = new Mp4LocationData(33.0f, -120f);

    ByteBuffer udtaBox = Boxes.udta(mp4Location);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(udtaBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("udta_box"));
  }

  @Test
  public void createKeysBox_matchesExpected() throws Exception {
    List<MdtaMetadataEntry> metadataEntries = new ArrayList<>();
    metadataEntries.add(
        new MdtaMetadataEntry(
            "com.android.version",
            Util.getUtf8Bytes("11"),
            MdtaMetadataEntry.TYPE_INDICATOR_STRING));
    metadataEntries.add(
        new MdtaMetadataEntry(
            "com.android.capture.fps",
            Util.toByteArray(120.0f),
            MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32));

    ByteBuffer keysBox = Boxes.keys(metadataEntries);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(keysBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("keys_box"));
  }

  @Test
  public void createIlstBox_matchesExpected() throws Exception {
    List<MdtaMetadataEntry> metadataEntries = new ArrayList<>();
    metadataEntries.add(
        new MdtaMetadataEntry(
            "com.android.version",
            Util.getUtf8Bytes("11"),
            MdtaMetadataEntry.TYPE_INDICATOR_STRING));
    metadataEntries.add(
        new MdtaMetadataEntry(
            "com.android.capture.fps",
            Util.toByteArray(120.0f),
            MdtaMetadataEntry.TYPE_INDICATOR_FLOAT32));

    ByteBuffer ilstBox = Boxes.ilst(metadataEntries);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(ilstBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("ilst_box"));
  }

  @Test
  public void createUuidBox_forXmpData_matchesExpected() throws Exception {
    ByteBuffer xmpData =
        ByteBuffer.wrap(TestUtil.getByteArray(context, "media/xmp/sample_datetime_xmp.xmp"));

    ByteBuffer xmpUuidBox = Boxes.uuid(Boxes.XMP_UUID, xmpData);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(xmpUuidBox);
    DumpFileAsserts.assertOutput(context, dumpableBox, getExpectedDumpFilePath("uuid_box_XMP"));
  }

  @Test
  public void createuuidBox_withEmptyXmpData_throws() {
    ByteBuffer xmpData = ByteBuffer.allocate(0);

    assertThrows(IllegalArgumentException.class, () -> Boxes.uuid(Boxes.XMP_UUID, xmpData));
  }

  @Test
  public void createAudioSampleEntryBox_forAac_matchesExpected() throws Exception {
    Format format =
        FAKE_AUDIO_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setInitializationData(ImmutableList.of(FAKE_CSD_0))
            .build();

    ByteBuffer audioSampleEntryBox = Boxes.audioSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(audioSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("audio_sample_entry_box_aac"));
  }

  @Test
  public void createAudioSampleEntryBox_forAmrNb_matchesExpected() throws Exception {
    Format format = FAKE_AUDIO_FORMAT.buildUpon().setSampleMimeType(MimeTypes.AUDIO_AMR_NB).build();

    ByteBuffer audioSampleEntryBox = Boxes.audioSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(audioSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("audio_sample_entry_box_amrnb"));
  }

  @Test
  public void createAudioSampleEntryBox_forAmrWb_matchesExpected() throws Exception {
    Format format = FAKE_AUDIO_FORMAT.buildUpon().setSampleMimeType(MimeTypes.AUDIO_AMR_WB).build();

    ByteBuffer audioSampleEntryBox = Boxes.audioSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(audioSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("audio_sample_entry_box_amrwb"));
  }

  @Test
  public void createAudioSampleEntryBox_forOpus_matchesExpected() throws Exception {
    Format format =
        FAKE_AUDIO_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.AUDIO_OPUS)
            .setInitializationData(
                ImmutableList.of(
                    BaseEncoding.base16()
                        .decode("4F7075734865616401063801401F00000000010402000401020305")))
            .build();

    ByteBuffer audioSampleEntryBox = Boxes.audioSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(audioSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("audio_sample_entry_box_opus"));
  }

  @Test
  public void createAudioSampleEntryBox_forVorbis_matchesExpected() throws Exception {
    Format format =
        FAKE_AUDIO_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.AUDIO_VORBIS)
            .setInitializationData(
                ImmutableList.of(
                    BaseEncoding.base16()
                        .decode("01766F726269730000000001803E0000000000009886010000000000A901"),
                    BaseEncoding.base16()
                        .decode("05766F726269732442435601004000001842102A05AD638E3A01")))
            .build();

    ByteBuffer audioSampleEntryBox = Boxes.audioSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(audioSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, getExpectedDumpFilePath("audio_sample_entry_box_vorbis"));
  }

  @Test
  public void createAudioSampleEntryBox_withUnknownAudioFormat_throws() {
    // The audio format contains an unknown MIME type.
    Format format = FAKE_AUDIO_FORMAT.buildUpon().setSampleMimeType("audio/mp4a-unknown").build();

    assertThrows(IllegalArgumentException.class, () -> Boxes.audioSampleEntry(format));
  }

  @Test
  public void createVideoSampleEntryBox_forH265_matchesExpected() throws Exception {
    Format format =
        FAKE_VIDEO_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setInitializationData(
                ImmutableList.of(
                    BaseEncoding.base16()
                        .decode(
                            "0000000140010C01FFFF0408000003009FC800000300001E959809000000014201010408000003009FC800000300001EC1882165959AE4CAE68080000003008000000C84000000014401C173D089")))
            .build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_h265"));
  }

  @Test
  public void createVideoSampleEntryBox_forH265_hdr10_matchesExpected() throws Exception {
    Format format =
        FAKE_VIDEO_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(
                new ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .setColorRange(C.COLOR_RANGE_LIMITED)
                    .build())
            .setInitializationData(
                ImmutableList.of(
                    BaseEncoding.base16()
                        .decode(
                            "0000000140010C01FFFF02200000030090000003000003003C9598090000000142010102200000030090000003000003003CA008080404D96566924CAE69C20000030002000003003210000000014401C172B46240")))
            .build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_h265_hdr10"));
  }

  @Test
  public void createVideoSampleEntryBox_forH263_matchesExpected() throws Exception {
    Format format =
        FAKE_VIDEO_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_H263)
            .setCodecs("s263.1.10")
            .build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_h263"));
  }

  @Test
  public void createVideoSampleEntryBox_forH264_matchesExpected() throws Exception {
    Format format = FAKE_VIDEO_FORMAT.buildUpon().setSampleMimeType(MimeTypes.VIDEO_H264).build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_h264"));
  }

  @Test
  public void createVideoSampleEntryBox_forAv1_matchesExpected() throws IOException {
    Format format = FAKE_VIDEO_FORMAT.buildUpon().setSampleMimeType(MimeTypes.VIDEO_AV1).build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_av1"));
  }

  @Test
  public void createVideoSampleEntryBox_forMPEG4_matchesExpected() throws IOException {
    Format format = FAKE_VIDEO_FORMAT.buildUpon().setSampleMimeType(MimeTypes.VIDEO_MP4V).build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_mpeg4"));
  }

  @Test
  public void createVideoSampleEntryBox_forVp09WithCodecPrivate_matchesExpected()
      throws IOException {
    Format format =
        FAKE_VIDEO_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_VP9)
            .setColorInfo(
                new ColorInfo.Builder()
                    .setColorSpace(C.COLOR_SPACE_BT2020)
                    .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                    .setColorRange(C.COLOR_RANGE_FULL)
                    .build())
            .setInitializationData(
                ImmutableList.of(BaseEncoding.base16().decode("01010102010A030108040100")))
            .build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_vp09_codec_private_as_csd"));
  }

  @Test
  public void createVideoSampleEntryBox_forVp09WithVpcBoxAsCsd_matchesExpected()
      throws IOException {
    Format format =
        FAKE_VIDEO_FORMAT
            .buildUpon()
            .setSampleMimeType(MimeTypes.VIDEO_VP9)
            .setInitializationData(
                ImmutableList.of(BaseEncoding.base16().decode("01000000010A810510060000")))
            .build();

    ByteBuffer videoSampleEntryBox = Boxes.videoSampleEntry(format);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(videoSampleEntryBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("video_sample_entry_box_vp09_vpc_as_csd"));
  }

  @Test
  public void createVideoSampleEntryBox_withUnknownVideoFormat_throws() {
    // The video format contains an unknown MIME type.
    Format format =
        FAKE_VIDEO_FORMAT.buildUpon().setSampleMimeType("video/someweirdvideoformat").build();

    assertThrows(IllegalArgumentException.class, () -> Boxes.videoSampleEntry(format));
  }

  @Test
  public void
      convertPresentationTimestampsToDurationsVu_singleSampleAtZeroTimestamp_returnsSampleLengthEqualsZero() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(0L);

    List<Integer> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            VU_TIMEBASE,
            LAST_SAMPLE_DURATION_BEHAVIOR_SET_TO_ZERO,
            C.TIME_UNSET);

    assertThat(durationsVu).containsExactly(0);
  }

  @Test
  public void
      convertPresentationTimestampsToDurationsVu_singleSampleAtNonZeroTimestamp_returnsSampleLengthEqualsZero() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(5_000L);

    List<Integer> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            VU_TIMEBASE,
            LAST_SAMPLE_DURATION_BEHAVIOR_SET_TO_ZERO,
            C.TIME_UNSET);

    assertThat(durationsVu).containsExactly(0);
  }

  @Test
  public void
      convertPresentationTimestampsToDurationsVu_differentSampleDurations_lastFrameDurationShort_returnsLastSampleOfZeroDuration() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(0L, 30_000L, 80_000L);

    List<Integer> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            VU_TIMEBASE,
            LAST_SAMPLE_DURATION_BEHAVIOR_SET_TO_ZERO,
            C.TIME_UNSET);

    assertThat(durationsVu).containsExactly(3_000, 5_000, 0);
  }

  @Test
  public void
      convertPresentationTimestampsToDurationsVu_differentSampleDurations_lastFrameDurationDuplicate_returnsLastSampleOfDuplicateDuration() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(0L, 30_000L, 80_000L);

    List<Integer> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            VU_TIMEBASE,
            LAST_SAMPLE_DURATION_BEHAVIOR_SET_FROM_END_OF_STREAM_BUFFER_OR_DUPLICATE_PREVIOUS,
            C.TIME_UNSET);

    assertThat(durationsVu).containsExactly(3_000, 5_000, 5_000);
  }

  @Test
  public void
      convertPresentationTimestampsToDurationsVu_withOutOfOrderSampleTimestamps_returnsExpectedDurations() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(0L, 10_000L, 1_000L, 2_000L, 11_000L);

    List<Integer> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            VU_TIMEBASE,
            LAST_SAMPLE_DURATION_BEHAVIOR_SET_TO_ZERO,
            C.TIME_UNSET);

    assertThat(durationsVu).containsExactly(100, 100, 800, 100, 0);
  }

  @Test
  public void
      convertPresentationTimestampsToDurationsVu_withLastSampleDurationBehaviorUsingEndOfStreamFlag_returnsExpectedDurations() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(0L, 1_000L, 2_000L, 3_000L, 4_000L);

    List<Integer> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            VU_TIMEBASE,
            LAST_SAMPLE_DURATION_BEHAVIOR_SET_FROM_END_OF_STREAM_BUFFER_OR_DUPLICATE_PREVIOUS,
            /* endOfStreamTimestampUs= */ 10_000);

    assertThat(durationsVu).containsExactly(100, 100, 100, 100, 600);
  }

  @Test
  public void createSttsBox_withSingleSampleDuration_matchesExpected() throws IOException {
    ImmutableList<Integer> sampleDurations = ImmutableList.of(500);

    ByteBuffer sttsBox = Boxes.stts(sampleDurations);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(sttsBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("stts_box_single_sample_duration"));
  }

  @Test
  public void createSttsBox_withAllDifferentSampleDurations_matchesExpected() throws IOException {
    ImmutableList<Integer> sampleDurations = ImmutableList.of(1_000, 2_000, 3_000, 5_000);

    ByteBuffer sttsBox = Boxes.stts(sampleDurations);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(sttsBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("stts_box_all_different_sample_durations"));
  }

  @Test
  public void createSttsBox_withFewConsecutiveSameSampleDurations_matchesExpected()
      throws IOException {
    ImmutableList<Integer> sampleDurations = ImmutableList.of(1_000, 2_000, 2_000, 2_000);

    ByteBuffer sttsBox = Boxes.stts(sampleDurations);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(sttsBox);
    DumpFileAsserts.assertOutput(
        context,
        dumpableBox,
        MuxerTestUtil.getExpectedDumpFilePath("stts_box_few_same_sample_durations"));
  }

  @Test
  public void createCttsBox_withSingleSampleTimestamp_returnsEmptyBox() {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(400);
    List<Integer> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            VU_TIMEBASE,
            LAST_SAMPLE_DURATION_BEHAVIOR_SET_TO_ZERO,
            C.TIME_UNSET);

    ByteBuffer cttsBox = Boxes.ctts(sampleBufferInfos, durationsVu, VU_TIMEBASE);

    // Create empty box in case of 1 sample.
    assertThat(cttsBox.hasRemaining()).isFalse();
  }

  @Test
  public void createCttsBox_withNoBframesSampleTimestamps_returnsEmptyBox() throws IOException {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(0L, 1000L, 2000L);
    List<Integer> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            VU_TIMEBASE,
            LAST_SAMPLE_DURATION_BEHAVIOR_SET_TO_ZERO,
            C.TIME_UNSET);

    ByteBuffer cttsBox = Boxes.ctts(sampleBufferInfos, durationsVu, VU_TIMEBASE);

    // Create empty ctts box in case samples does not contain B-frames.
    assertThat(cttsBox.hasRemaining()).isFalse();
  }

  @Test
  public void createCttsBox_withBFramesSampleTimestamps_matchesExpected() throws IOException {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(
            0, 400, 200, 100, 300, 800, 600, 500, 700);

    List<Integer> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            VU_TIMEBASE,
            LAST_SAMPLE_DURATION_BEHAVIOR_SET_TO_ZERO,
            C.TIME_UNSET);

    ByteBuffer cttsBox = Boxes.ctts(sampleBufferInfos, durationsVu, VU_TIMEBASE);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(cttsBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("ctts_box"));
  }

  @Test
  public void createCttsBox_withLargeSampleTimestamps_matchesExpected() throws IOException {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSamplePresentationTimestamps(
            23698215060L, 23698248252L, 23698347988L, 23698488968L, 23698547416L);

    List<Integer> durationsVu =
        Boxes.convertPresentationTimestampsToDurationsVu(
            sampleBufferInfos,
            VU_TIMEBASE,
            LAST_SAMPLE_DURATION_BEHAVIOR_SET_TO_ZERO,
            C.TIME_UNSET);

    ByteBuffer cttsBox = Boxes.ctts(sampleBufferInfos, durationsVu, VU_TIMEBASE);

    assertThat(cttsBox.hasRemaining()).isFalse();
  }

  @Test
  public void createStszBox_matchesExpected() throws IOException {
    List<MediaCodec.BufferInfo> sampleBufferInfos =
        createBufferInfoListWithSampleSizes(100, 200, 150, 200);

    ByteBuffer stszBox = Boxes.stsz(sampleBufferInfos);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(stszBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("stsz_box"));
  }

  @Test
  public void createStscBox_withDifferentChunks_matchesExpected() throws IOException {
    ImmutableList<Integer> chunkSampleCounts = ImmutableList.of(100, 500, 200, 100);

    ByteBuffer stscBox = Boxes.stsc(chunkSampleCounts);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(stscBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("stsc_box"));
  }

  @Test
  public void createStscBox_withSameChunks_matchesExpected() throws IOException {
    ImmutableList<Integer> chunkSampleCounts = ImmutableList.of(100, 100, 100, 100);

    ByteBuffer stscBox = Boxes.stsc(chunkSampleCounts);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(stscBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("stsc_box_with_same_chunks"));
  }

  @Test
  public void createStcoBox_matchesExpected() throws IOException {
    ImmutableList<Long> chunkOffsets = ImmutableList.of(1_000L, 5_000L, 7_000L, 10_000L);

    ByteBuffer stcoBox = Boxes.stco(chunkOffsets);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(stcoBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("stco_box"));
  }

  @Test
  public void createCo64Box_matchesExpected() throws IOException {
    ImmutableList<Long> chunkOffsets = ImmutableList.of(1_000L, 5_000L, 7_000L, 10_000L);

    ByteBuffer co64Box = Boxes.co64(chunkOffsets);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(co64Box);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("co64_box"));
  }

  @Test
  public void createStssBox_matchesExpected() throws IOException {
    List<MediaCodec.BufferInfo> sampleBufferInfos = createBufferInfoListWithSomeKeyFrames();

    ByteBuffer stssBox = Boxes.stss(sampleBufferInfos);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(stssBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("stss_box"));
  }

  @Test
  public void createFtypBox_matchesExpected() throws IOException {
    ByteBuffer ftypBox = Boxes.ftyp();

    DumpableMp4Box dumpableBox = new DumpableMp4Box(ftypBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("ftyp_box"));
  }

  @Test
  public void createMfhdBox_matchesExpected() throws IOException {
    ByteBuffer mfhdBox = Boxes.mfhd(/* sequenceNumber= */ 5);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(mfhdBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("mfhd_box"));
  }

  @Test
  public void createTfhdBox_matchesExpected() throws IOException {
    ByteBuffer tfhdBox = Boxes.tfhd(/* trackId= */ 1, /* baseDataOffset= */ 1_000L);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(tfhdBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("tfhd_box"));
  }

  @Test
  public void createTrunBox_matchesExpected() throws IOException {
    int sampleCount = 5;
    List<SampleMetadata> samplesMetadata = new ArrayList<>(sampleCount);
    for (int i = 0; i < sampleCount; i++) {
      samplesMetadata.add(
          new SampleMetadata(
              /* durationsVu= */ 2_000,
              /* size= */ 5_000,
              /* flags= */ i == 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0,
              /* compositionTimeOffsetVu= */ 0));
    }

    ByteBuffer trunBox =
        Boxes.trun(samplesMetadata, /* dataOffset= */ 1_000, /* hasBFrame= */ false);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(trunBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("trun_box"));
  }

  @Test
  public void createTrunBox_withBFrame_matchesExpected() throws IOException {
    int sampleCount = 5;
    List<SampleMetadata> samplesMetadata = new ArrayList<>(sampleCount);
    for (int i = 0; i < sampleCount; i++) {
      samplesMetadata.add(
          new SampleMetadata(
              /* durationsVu= */ 2_000,
              /* size= */ 5_000,
              /* flags= */ i == 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0,
              /* compositionTimeOffsetVu= */ 100));
    }

    ByteBuffer trunBox =
        Boxes.trun(samplesMetadata, /* dataOffset= */ 1_000, /* hasBFrame= */ true);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(trunBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("trun_box_with_b_frame"));
  }

  @Test
  public void createTrexBox_matchesExpected() throws IOException {
    ByteBuffer trexBox = Boxes.trex(/* trackId= */ 2);

    DumpableMp4Box dumpableBox = new DumpableMp4Box(trexBox);
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("trex_box"));
  }

  private static List<MediaCodec.BufferInfo> createBufferInfoListWithSamplePresentationTimestamps(
      long... timestampsUs) {
    List<MediaCodec.BufferInfo> bufferInfoList = new ArrayList<>();
    for (long timestampUs : timestampsUs) {
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      bufferInfo.presentationTimeUs = timestampUs;
      bufferInfoList.add(bufferInfo);
    }

    return bufferInfoList;
  }

  private static List<MediaCodec.BufferInfo> createBufferInfoListWithSampleSizes(int... sizes) {
    List<MediaCodec.BufferInfo> bufferInfoList = new ArrayList<>();
    for (int size : sizes) {
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      bufferInfo.size = size;
      bufferInfoList.add(bufferInfo);
    }

    return bufferInfoList;
  }

  private static List<MediaCodec.BufferInfo> createBufferInfoListWithSomeKeyFrames() {
    List<MediaCodec.BufferInfo> bufferInfoList = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
      if (i % 5 == 0) { // Make every 5th frame as key frame.
        bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
      }
      bufferInfoList.add(bufferInfo);
    }

    return bufferInfoList;
  }
}
