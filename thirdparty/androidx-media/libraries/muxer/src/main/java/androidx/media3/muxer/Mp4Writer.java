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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.muxer.AnnexBUtils.doesSampleContainAnnexBNalUnits;
import static androidx.media3.muxer.Boxes.BOX_HEADER_SIZE;
import static androidx.media3.muxer.Boxes.LARGE_SIZE_BOX_HEADER_SIZE;
import static androidx.media3.muxer.Boxes.getAxteBoxHeader;
import static androidx.media3.muxer.MuxerUtil.getAuxiliaryTracksLengthMetadata;
import static androidx.media3.muxer.MuxerUtil.getAuxiliaryTracksOffsetMetadata;
import static androidx.media3.muxer.MuxerUtil.populateAuxiliaryTracksMetadata;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.media.MediaCodec.BufferInfo;
import androidx.media3.common.Format;
import androidx.media3.common.util.Util;
import androidx.media3.container.MdtaMetadataEntry;
import com.google.common.collect.Range;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Writes all media samples into a single mdat box. */
/* package */ final class Mp4Writer {
  private static final long INTERLEAVE_DURATION_US = 1_000_000L;
  // Used for updating the moov box periodically when sample batching is disabled.
  private static final long MOOV_BOX_UPDATE_INTERVAL_US = 1_000_000L;
  private static final int DEFAULT_MOOV_BOX_SIZE_BYTES = 400_000;
  private static final String FREE_BOX_TYPE = "free";

  private final FileChannel outputFileChannel;
  private final MetadataCollector metadataCollector;
  private final AnnexBToAvccConverter annexBToAvccConverter;
  private final @Mp4Muxer.LastSampleDurationBehavior int lastSampleDurationBehavior;
  private final boolean sampleCopyEnabled;
  private final boolean sampleBatchingEnabled;
  private final List<Track> tracks;
  private final List<Track> auxiliaryTracks;
  private final AtomicBoolean hasWrittenSamples;
  private final LinearByteBufferAllocator linearByteBufferAllocator;

  // Stores location of the space reserved for the moov box at the beginning of the file (after ftyp
  // box)
  private long reservedMoovSpaceStart;
  private long reservedMoovSpaceEnd;
  private boolean canWriteMoovAtStart;
  private long mdatStart;
  private long mdatEnd;
  private long mdatDataEnd; // Always <= mdatEnd
  // Typically written from the end of the mdat box to the end of the file.
  private Range<Long> lastMoovWritten;
  // Used for writing moov box periodically when sample batching is disabled.
  private long lastMoovWrittenAtSampleTimestampUs;

  /**
   * Creates an instance.
   *
   * @param fileChannel The {@link FileChannel} to write the data to. The {@link FileChannel} can be
   *     closed after {@linkplain #finishWritingSamplesAndFinalizeMoovBox() finishing writing
   *     samples}.
   * @param metadataCollector A {@link MetadataCollector}.
   * @param annexBToAvccConverter The {@link AnnexBToAvccConverter} to be used to convert H.264 and
   *     H.265 NAL units from the Annex-B format (using start codes to delineate NAL units) to the
   *     AVCC format (which uses length prefixes).
   * @param lastSampleDurationBehavior The {@link Mp4Muxer.LastSampleDurationBehavior}.
   * @param sampleCopyEnabled Whether sample copying is enabled.
   * @param sampleBatchingEnabled Whether sample batching is enabled.
   * @param attemptStreamableOutputEnabled Whether to attempt to write a streamable output.
   */
  public Mp4Writer(
      FileChannel fileChannel,
      MetadataCollector metadataCollector,
      AnnexBToAvccConverter annexBToAvccConverter,
      @Mp4Muxer.LastSampleDurationBehavior int lastSampleDurationBehavior,
      boolean sampleCopyEnabled,
      boolean sampleBatchingEnabled,
      boolean attemptStreamableOutputEnabled) {
    this.outputFileChannel = fileChannel;
    this.metadataCollector = metadataCollector;
    this.annexBToAvccConverter = annexBToAvccConverter;
    this.lastSampleDurationBehavior = lastSampleDurationBehavior;
    this.sampleCopyEnabled = sampleCopyEnabled;
    this.sampleBatchingEnabled = sampleBatchingEnabled;
    tracks = new ArrayList<>();
    auxiliaryTracks = new ArrayList<>();
    hasWrittenSamples = new AtomicBoolean(false);
    canWriteMoovAtStart = attemptStreamableOutputEnabled;
    lastMoovWritten = Range.closed(0L, 0L);
    lastMoovWrittenAtSampleTimestampUs = 0L;
    linearByteBufferAllocator = new LinearByteBufferAllocator(/* initialCapacity= */ 0);
  }

  /**
   * Adds a track of the given {@link Format}.
   *
   * @param trackId The track id for the track.
   * @param sortKey The key used for sorting the track list.
   * @param format The {@link Format} for the track.
   * @return A unique {@link Track}. It should be used in {@link #writeSampleData}.
   */
  public Track addTrack(int trackId, int sortKey, Format format) {
    Track track = new Track(trackId, format, sortKey, sampleCopyEnabled);
    tracks.add(track);
    Collections.sort(tracks, (a, b) -> Integer.compare(a.sortKey, b.sortKey));
    return track;
  }

  /**
   * Adds an auxiliary track of the given {@link Format}.
   *
   * <p>See {@link MuxerUtil#isAuxiliaryTrack(Format)} for auxiliary tracks.
   *
   * @param trackId The track id for the track.
   * @param sortKey The key used for sorting the track list.
   * @param format The {@link Format} for the track.
   * @return A unique {@link Track}. It should be used in {@link #writeSampleData}.
   */
  public Track addAuxiliaryTrack(int trackId, int sortKey, Format format) {
    Track track = new Track(trackId, format, sortKey, sampleCopyEnabled);
    auxiliaryTracks.add(track);
    Collections.sort(auxiliaryTracks, (a, b) -> Integer.compare(a.sortKey, b.sortKey));
    return track;
  }

  /**
   * Writes encoded sample data.
   *
   * @param track The {@link Track} for which this sample is being written.
   * @param byteBuffer The encoded sample.
   * @param bufferInfo The {@link BufferInfo} related to this sample.
   * @throws IOException If there is any error while writing data to the output {@link FileChannel}.
   */
  public void writeSampleData(Track track, ByteBuffer byteBuffer, BufferInfo bufferInfo)
      throws IOException {
    track.writeSampleData(byteBuffer, bufferInfo);
    if (sampleBatchingEnabled) {
      doInterleave();
    } else {
      writePendingTrackSamples(track);
      boolean primaryTrackSampleWritten = tracks.contains(track);
      long currentSampleTimestampUs = bufferInfo.presentationTimeUs;
      if (primaryTrackSampleWritten
          && canWriteMoovAtStart
          && (currentSampleTimestampUs - lastMoovWrittenAtSampleTimestampUs
              >= MOOV_BOX_UPDATE_INTERVAL_US)) {
        maybeWriteMoovAtStart();
        lastMoovWrittenAtSampleTimestampUs = currentSampleTimestampUs;
      }
    }
  }

  /**
   * Writes all the pending samples and the final moov box to the output {@link FileChannel}.
   *
   * <p>This should be done before closing the file. The output {@link FileChannel} can be closed
   * after calling this method.
   */
  public void finishWritingSamplesAndFinalizeMoovBox() throws IOException {
    for (int i = 0; i < tracks.size(); i++) {
      writePendingTrackSamples(tracks.get(i));
    }
    for (int i = 0; i < auxiliaryTracks.size(); i++) {
      writePendingTrackSamples(auxiliaryTracks.get(i));
    }

    // Leave the file empty if no samples are written.
    if (!hasWrittenSamples.get()) {
      return;
    }

    finalizeMoovBox();

    if (!auxiliaryTracks.isEmpty()) {
      writeAxteBox();
    }
  }

  private void writeAxteBox() throws IOException {
    // The exact offset is known after writing primary track data.
    MdtaMetadataEntry placeholderAuxiliaryTrackOffset =
        getAuxiliaryTracksOffsetMetadata(/* offset= */ 0L);
    metadataCollector.addMetadata(placeholderAuxiliaryTrackOffset);
    ByteBuffer axteBox = getAxteBox();
    metadataCollector.addMetadata(getAuxiliaryTracksLengthMetadata(axteBox.remaining()));
    finalizeMoovBox();
    // Once final moov is written, update the actual offset.
    metadataCollector.removeMdtaMetadataEntry(placeholderAuxiliaryTrackOffset);
    metadataCollector.addMetadata(getAuxiliaryTracksOffsetMetadata(outputFileChannel.size()));
    long fileSizeBefore = outputFileChannel.size();
    finalizeMoovBox();
    checkState(fileSizeBefore == outputFileChannel.size());
    // After writing primary track data, write the axte box.
    outputFileChannel.position(outputFileChannel.size());
    outputFileChannel.write(axteBox);
  }

  private ByteBuffer getAxteBox() {
    // The axte box will have one ftyp and one moov box.
    ByteBuffer ftypBox = Boxes.ftyp();
    MetadataCollector auxiliaryTracksMetadataCollector = new MetadataCollector();
    populateAuxiliaryTracksMetadata(
        auxiliaryTracksMetadataCollector,
        metadataCollector.timestampData,
        /* samplesInterleaved= */ true,
        auxiliaryTracks);
    ByteBuffer moovBox =
        Boxes.moov(
            auxiliaryTracks,
            auxiliaryTracksMetadataCollector,
            /* isFragmentedMp4= */ false,
            lastSampleDurationBehavior);
    ByteBuffer axteBoxHeader =
        getAxteBoxHeader(/* payloadSize= */ ftypBox.remaining() + moovBox.remaining());
    return BoxUtils.concatenateBuffers(axteBoxHeader, ftypBox, moovBox);
  }

  /**
   * Writes the updated moov box to the output {@link FileChannel}.
   *
   * <p>It also trims any extra spaces from the file.
   *
   * @throws IOException If there is any error while writing data to the output {@link FileChannel}.
   */
  public void finalizeMoovBox() throws IOException {
    if (canWriteMoovAtStart) {
      maybeWriteMoovAtStart();
      return;
    }

    // The current state is:
    // | ftyp | mdat .. .. .. (00 00 00) | moov |

    // To keep the trimming safe, first write the final moov box into the gap at the end of the mdat
    // box, and only then trim the extra space.
    ByteBuffer currentMoovData = assembleCurrentMoovData();

    int moovBytesNeeded = currentMoovData.remaining();

    // Write a temporary free box wrapping the new moov box.
    int moovAndFreeBytesNeeded = moovBytesNeeded + 8;

    if (mdatEnd - mdatDataEnd < moovAndFreeBytesNeeded) {
      // If the gap is not big enough for the moov box, then extend the mdat box once again. This
      // involves writing moov box farther away one more time.
      safelyReplaceMoovAtEnd(
          lastMoovWritten.upperEndpoint() + moovAndFreeBytesNeeded, currentMoovData);
      checkState(mdatEnd - mdatDataEnd >= moovAndFreeBytesNeeded);
    }

    // Write out the new moov box into the gap.
    long newMoovLocation = mdatDataEnd;
    outputFileChannel.position(mdatDataEnd);
    outputFileChannel.write(currentMoovData);

    // Add a free box to account for the actual remaining length of the file.
    long remainingLength = lastMoovWritten.upperEndpoint() - (newMoovLocation + moovBytesNeeded);

    // Moov boxes shouldn't be too long; they can fit into a free box with a 32-bit length field.
    checkState(remainingLength < Integer.MAX_VALUE);

    ByteBuffer freeHeader = ByteBuffer.allocate(4 + 4);
    freeHeader.putInt((int) remainingLength);
    freeHeader.put(Util.getUtf8Bytes(FREE_BOX_TYPE));
    freeHeader.flip();
    outputFileChannel.write(freeHeader);

    // The moov box is actually written inside mdat box so the current state is:
    // | ftyp | mdat .. .. .. (new moov) (free header ) (00 00 00) | old moov |

    // Now change this to:
    // | ftyp | mdat .. .. .. | new moov | free (00 00 00) (old moov) |
    mdatEnd = newMoovLocation;
    updateMdatSize(mdatEnd - mdatStart);
    lastMoovWritten = Range.closed(newMoovLocation, newMoovLocation + currentMoovData.limit());

    // Remove the free box.
    outputFileChannel.truncate(newMoovLocation + moovBytesNeeded);
  }

  private void writeHeader() throws IOException {
    outputFileChannel.position(0L);
    outputFileChannel.write(Boxes.ftyp());

    if (canWriteMoovAtStart) {
      // Reserve some space for moov box by adding a free box.
      reservedMoovSpaceStart = outputFileChannel.position();
      outputFileChannel.write(
          BoxUtils.wrapIntoBox(FREE_BOX_TYPE, ByteBuffer.allocate(DEFAULT_MOOV_BOX_SIZE_BYTES)));
      reservedMoovSpaceEnd = outputFileChannel.position();
    }

    // Start with an empty mdat box.
    mdatStart = outputFileChannel.position();
    ByteBuffer header = ByteBuffer.allocate(LARGE_SIZE_BOX_HEADER_SIZE);
    header.putInt(1); // 4 bytes, indicating a 64-bit length field
    header.put(Util.getUtf8Bytes("mdat")); // 4 bytes
    header.putLong(16); // 8 bytes (the actual length)
    header.flip();
    outputFileChannel.write(header);

    // The box includes only its type and length.
    mdatDataEnd = mdatStart + 16;
    mdatEnd = canWriteMoovAtStart ? Long.MAX_VALUE : mdatDataEnd;
  }

  private ByteBuffer assembleCurrentMoovData() {

    return Boxes.moov(
        tracks, metadataCollector, /* isFragmentedMp4= */ false, lastSampleDurationBehavior);
  }

  /**
   * Replaces old moov box with the new one.
   *
   * <p>It doesn't really replace the existing moov box, rather it adds a new moov box at the end of
   * the file. Even if this operation fails, the output MP4 file still has a valid moov box.
   *
   * <p>After this operation, the mdat box might have some extra space containing garbage value of
   * the old moov box. This extra space is trimmed {@linkplain
   * #finishWritingSamplesAndFinalizeMoovBox() eventually} (in {@link #finalizeMoovBox()}).
   *
   * @param newMoovBoxPosition The new position for the moov box.
   * @param newMoovBoxData The new moov box data.
   * @throws IOException If there is any error while writing data to the disk.
   */
  private void safelyReplaceMoovAtEnd(long newMoovBoxPosition, ByteBuffer newMoovBoxData)
      throws IOException {
    checkState(newMoovBoxPosition >= lastMoovWritten.upperEndpoint());
    checkState(newMoovBoxPosition >= mdatEnd);

    // Write a free box to the end of the file, with the new moov box wrapped into it.
    outputFileChannel.position(newMoovBoxPosition);
    outputFileChannel.write(BoxUtils.wrapIntoBox(FREE_BOX_TYPE, newMoovBoxData.duplicate()));

    // The current state is:
    // | ftyp | mdat .. .. .. | previous moov | free (new moov)|

    // Increase the length of the mdat box so that it now extends to
    // the previous moov box and the header of the free box.
    mdatEnd = newMoovBoxPosition + 8;
    updateMdatSize(mdatEnd - mdatStart);

    lastMoovWritten =
        Range.closed(newMoovBoxPosition, newMoovBoxPosition + newMoovBoxData.remaining());
  }

  /**
   * Attempts to write moov box at the start (after the ftyp box). If this is not possible, the moov
   * box is written at the end of the file (after the mdat box).
   */
  private void maybeWriteMoovAtStart() throws IOException {
    ByteBuffer moovBox = assembleCurrentMoovData();
    int moovBoxSize = moovBox.remaining();
    // Keep some space for free box to fill the remaining space.
    if (moovBox.remaining() + BOX_HEADER_SIZE <= reservedMoovSpaceEnd - reservedMoovSpaceStart) {
      outputFileChannel.position(reservedMoovSpaceStart);
      outputFileChannel.write(moovBox);
      // Write free box in the remaining space.
      int freeSpace = (int) (reservedMoovSpaceEnd - outputFileChannel.position() - BOX_HEADER_SIZE);
      outputFileChannel.write(BoxUtils.wrapIntoBox(FREE_BOX_TYPE, ByteBuffer.allocate(freeSpace)));
    } else {
      // Write moov at the end (after mdat).
      canWriteMoovAtStart = false;
      mdatEnd = mdatDataEnd;
      outputFileChannel.position(mdatEnd);
      outputFileChannel.write(moovBox);
      lastMoovWritten = Range.closed(mdatEnd, mdatEnd + moovBoxSize);
      // Replace previously written moov box (after ftyp box) with a free box.
      int freeSpace = (int) (reservedMoovSpaceEnd - reservedMoovSpaceStart - BOX_HEADER_SIZE);
      ByteBuffer freeBox = BoxUtils.wrapIntoBox(FREE_BOX_TYPE, ByteBuffer.allocate(freeSpace));
      outputFileChannel.write(freeBox, reservedMoovSpaceStart);
    }
    updateMdatSize(mdatDataEnd - mdatStart);
  }

  /**
   * Rewrites the moov box after accommodating extra bytes needed for the mdat box.
   *
   * @param bytesNeeded The extra bytes needed for the mdat box.
   * @throws IOException If there is any error while writing data to the output {@link FileChannel}.
   */
  private void rewriteMoovWithMdatEmptySpace(long bytesNeeded) throws IOException {
    long newMoovStart = Math.max(mdatEnd + bytesNeeded, lastMoovWritten.upperEndpoint());

    ByteBuffer currentMoovData = assembleCurrentMoovData();

    safelyReplaceMoovAtEnd(newMoovStart, currentMoovData);
  }

  /**
   * Writes pending samples of given {@link Track tracks} if there are enough samples to write.
   *
   * @param tracks A list of {@link Track} containing the pending samples to be potentially written.
   * @return {@code true} if any new sample is written, {@code false} otherwise.
   */
  private boolean maybeWritePendingTrackSamples(List<Track> tracks) throws IOException {
    boolean newSamplesWritten = false;
    for (int i = 0; i < tracks.size(); i++) {
      Track track = tracks.get(i);
      // TODO: b/270583563 - Check if we need to consider the global timestamp instead.
      if (track.pendingSamplesBufferInfo.size() > 2) {
        BufferInfo firstSampleInfo = checkNotNull(track.pendingSamplesBufferInfo.peekFirst());
        BufferInfo lastSampleInfo = checkNotNull(track.pendingSamplesBufferInfo.peekLast());

        if (lastSampleInfo.presentationTimeUs - firstSampleInfo.presentationTimeUs
            > INTERLEAVE_DURATION_US) {
          newSamplesWritten = true;
          writePendingTrackSamples(track);
        }
      }
    }
    return newSamplesWritten;
  }

  /** Writes out any pending samples of the given {@link Track}. */
  private void writePendingTrackSamples(Track track) throws IOException {
    checkState(track.pendingSamplesByteBuffer.size() == track.pendingSamplesBufferInfo.size());
    if (track.pendingSamplesBufferInfo.isEmpty()) {
      return;
    }

    if (!hasWrittenSamples.getAndSet(true)) {
      writeHeader();
    }

    // Calculate the additional space required.
    long bytesNeededInMdat = 0L;
    for (ByteBuffer sample : track.pendingSamplesByteBuffer) {
      bytesNeededInMdat += sample.limit();
    }

    maybeExtendMdatAndRewriteMoov(bytesNeededInMdat);

    track.writtenChunkOffsets.add(mdatDataEnd);
    track.writtenChunkSampleCounts.add(track.pendingSamplesBufferInfo.size());

    do {
      BufferInfo currentSampleBufferInfo = track.pendingSamplesBufferInfo.removeFirst();
      ByteBuffer currentSampleByteBuffer = track.pendingSamplesByteBuffer.removeFirst();

      // Convert the H.264/H.265 samples from Annex-B format (output by MediaCodec) to
      // Avcc format (required by MP4 container).
      if (doesSampleContainAnnexBNalUnits(checkNotNull(track.format.sampleMimeType))) {
        currentSampleByteBuffer =
            annexBToAvccConverter.process(currentSampleByteBuffer, linearByteBufferAllocator);
        currentSampleBufferInfo.set(
            currentSampleByteBuffer.position(),
            currentSampleByteBuffer.remaining(),
            currentSampleBufferInfo.presentationTimeUs,
            currentSampleBufferInfo.flags);
      }

      // If the original sample had 3 bytes NAL start code instead of 4 bytes, then after AnnexB to
      // Avcc conversion it will have 1 additional byte.
      maybeExtendMdatAndRewriteMoov(currentSampleByteBuffer.remaining());

      mdatDataEnd += outputFileChannel.write(currentSampleByteBuffer, mdatDataEnd);
      linearByteBufferAllocator.reset();
      track.writtenSamples.add(currentSampleBufferInfo);
    } while (!track.pendingSamplesBufferInfo.isEmpty());
    checkState(mdatDataEnd <= mdatEnd);
  }

  private void maybeExtendMdatAndRewriteMoov(long additionalBytesNeeded) throws IOException {
    // The mdat box can be written till the end of the file.
    if (canWriteMoovAtStart) {
      return;
    }
    // If the required number of bytes doesn't fit in the gap between the actual data and the moov
    // box, extend the file and write out the moov box to the end again.
    if (mdatDataEnd + additionalBytesNeeded >= mdatEnd) {
      // Reserve some extra space than required, so that mdat box extension is less frequent.
      rewriteMoovWithMdatEmptySpace(
          /* bytesNeeded= */ getMdatExtensionAmount(mdatDataEnd) + additionalBytesNeeded);
    }
  }

  private void updateMdatSize(long mdatSize) throws IOException {
    // The mdat box has a 64-bit length, so skip the box type (4 bytes) and the default box length
    // (4 bytes).
    outputFileChannel.position(mdatStart + BOX_HEADER_SIZE);
    ByteBuffer mdatSizeBuffer = ByteBuffer.allocate(8); // One long
    mdatSizeBuffer.putLong(mdatSize);
    mdatSizeBuffer.flip();
    outputFileChannel.write(mdatSizeBuffer);
  }

  private void doInterleave() throws IOException {
    boolean primaryTrackSampleWritten = maybeWritePendingTrackSamples(tracks);
    maybeWritePendingTrackSamples(auxiliaryTracks);

    if (primaryTrackSampleWritten && canWriteMoovAtStart) {
      maybeWriteMoovAtStart();
    }
  }

  /**
   * Returns the number of bytes by which to extend the mdat box.
   *
   * @param currentFileLength The length of current file in bytes (except moov box).
   * @return The mdat box extension amount in bytes.
   */
  private long getMdatExtensionAmount(long currentFileLength) {
    // Don't extend by more than 1 GB at a time because the final trimming creates a "free" box that
    // can be as big as this extension + the old "moov" box, but should be less than 2**31 - 1 bytes
    // (because it is a compact "free" box and for simplicity its size is written as a signed
    // integer). Therefore, to be conservative, a max extension of 1 GB was chosen.
    long minBytesToExtend = 500_000L;
    long maxBytesToExtend = 1_000_000_000L;
    float extensionRatio = 0.2f;

    return min(
        maxBytesToExtend, max(minBytesToExtend, (long) (extensionRatio * currentFileLength)));
  }
}
