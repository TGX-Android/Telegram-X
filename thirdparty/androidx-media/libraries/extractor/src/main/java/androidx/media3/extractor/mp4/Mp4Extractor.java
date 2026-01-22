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

import static androidx.media3.common.C.AUXILIARY_TRACK_TYPE_DEPTH_INVERSE;
import static androidx.media3.common.C.AUXILIARY_TRACK_TYPE_DEPTH_LINEAR;
import static androidx.media3.common.C.AUXILIARY_TRACK_TYPE_ORIGINAL;
import static androidx.media3.common.C.AUXILIARY_TRACK_TYPE_UNDEFINED;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.container.MdtaMetadataEntry.AUXILIARY_TRACKS_SAMPLES_NOT_INTERLEAVED;
import static androidx.media3.extractor.mp4.BoxParser.parseTraks;
import static androidx.media3.extractor.mp4.MetadataUtil.findMdtaMetadataEntryWithKey;
import static androidx.media3.extractor.mp4.MimeTypeResolver.getContainerMimeType;
import static androidx.media3.extractor.mp4.Sniffer.BRAND_HEIC;
import static androidx.media3.extractor.mp4.Sniffer.BRAND_QUICKTIME;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Metadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.container.Mp4Box;
import androidx.media3.container.Mp4Box.ContainerBox;
import androidx.media3.container.NalUnitUtil;
import androidx.media3.extractor.Ac3Util;
import androidx.media3.extractor.Ac4Util;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.GaplessInfoHolder;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.SniffFailure;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.TrueHdSampleRechunker;
import androidx.media3.extractor.metadata.mp4.MotionPhotoMetadata;
import androidx.media3.extractor.metadata.mp4.SlowMotionData;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.SubtitleTranscodingExtractorOutput;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Extracts data from the MP4 container format. */
@UnstableApi
public final class Mp4Extractor implements Extractor, SeekMap {

  /**
   * Creates a factory for {@link Mp4Extractor} instances with the provided {@link
   * SubtitleParser.Factory}.
   */
  public static ExtractorsFactory newFactory(SubtitleParser.Factory subtitleParserFactory) {
    return () -> new Extractor[] {new Mp4Extractor(subtitleParserFactory)};
  }

  /**
   * Flags controlling the behavior of the extractor. Possible flag values are {@link
   * #FLAG_WORKAROUND_IGNORE_EDIT_LISTS}, {@link #FLAG_READ_MOTION_PHOTO_METADATA}, {@link
   * #FLAG_READ_SEF_DATA}, {@link #FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES}, {@link
   * #FLAG_READ_AUXILIARY_TRACKS} and {@link #FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES_H265}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef(
      flag = true,
      value = {
        FLAG_WORKAROUND_IGNORE_EDIT_LISTS,
        FLAG_READ_MOTION_PHOTO_METADATA,
        FLAG_READ_SEF_DATA,
        FLAG_MARK_FIRST_VIDEO_TRACK_WITH_MAIN_ROLE,
        FLAG_EMIT_RAW_SUBTITLE_DATA,
        FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES,
        FLAG_READ_AUXILIARY_TRACKS,
        FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES_H265
      })
  public @interface Flags {}

  /** Flag to ignore any edit lists in the stream. */
  public static final int FLAG_WORKAROUND_IGNORE_EDIT_LISTS = 1;

  /**
   * Flag to extract {@link MotionPhotoMetadata} from HEIC motion photos following the Google Photos
   * Motion Photo File Format V1.1.
   *
   * <p>As playback is not supported for motion photos, this flag should only be used for metadata
   * retrieval use cases.
   */
  public static final int FLAG_READ_MOTION_PHOTO_METADATA = 1 << 1;

  /**
   * Flag to extract {@link SlowMotionData} metadata from Samsung Extension Format (SEF) slow motion
   * videos.
   */
  public static final int FLAG_READ_SEF_DATA = 1 << 2;

  /**
   * Flag to mark the first video track encountered as {@link C#ROLE_FLAG_MAIN} and all subsequent
   * video tracks as {@link C#ROLE_FLAG_ALTERNATE}.
   */
  public static final int FLAG_MARK_FIRST_VIDEO_TRACK_WITH_MAIN_ROLE = 1 << 3;

  public static final int FLAG_EMIT_RAW_SUBTITLE_DATA = 1 << 4;

  /**
   * Flag to extract additional sample dependency information, and mark output buffers with {@link
   * C#BUFFER_FLAG_NOT_DEPENDED_ON} for {@linkplain MimeTypes#VIDEO_H264 H.264} video.
   *
   * <p>This class always marks the samples at the start of each group of picture (GOP) with {@link
   * C#BUFFER_FLAG_KEY_FRAME}. Usually, key frames can be decoded independently, without depending
   * on other samples.
   *
   * <p>Setting this flag enables elementary stream parsing to identify disposable samples that are
   * not depended on by other samples. Any disposable sample can be safely omitted, and the rest of
   * the track will remain valid.
   */
  public static final int FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES = 1 << 5;

  /**
   * Flag to extract the auxiliary tracks from the MP4 With Auxiliary Tracks Extension (MP4-AT) file
   * format.
   *
   * <p>Either primary video tracks or auxiliary tracks (but not both) will be extracted based on
   * the flag.
   *
   * <p>If the flag is set but the auxiliary tracks are not present, then it fallbacks to extract
   * primary tracks instead.
   *
   * <p>See the file format at https://developer.android.com/media/platform/mp4-at-file-format.
   */
  public static final int FLAG_READ_AUXILIARY_TRACKS = 1 << 6;

  /**
   * Flag to extract additional sample dependency information, and mark output buffers with {@link
   * C#BUFFER_FLAG_NOT_DEPENDED_ON} for {@linkplain MimeTypes#VIDEO_H265 H.265} video.
   *
   * <p>See {@link #FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES}.
   */
  public static final int FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES_H265 = 1 << 7;

  /**
   * @deprecated Use {@link #newFactory(SubtitleParser.Factory)} instead.
   */
  @Deprecated
  public static final ExtractorsFactory FACTORY =
      () ->
          new Extractor[] {
            new Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED, FLAG_EMIT_RAW_SUBTITLE_DATA)
          };

  /** Parser states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_READING_ATOM_HEADER,
    STATE_READING_ATOM_PAYLOAD,
    STATE_READING_SAMPLE,
    STATE_READING_SEF,
  })
  private @interface State {}

  private static final int STATE_READING_ATOM_HEADER = 0;
  private static final int STATE_READING_ATOM_PAYLOAD = 1;
  private static final int STATE_READING_SAMPLE = 2;
  private static final int STATE_READING_SEF = 3;

  /** Supported file types. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({FILE_TYPE_MP4, FILE_TYPE_QUICKTIME, FILE_TYPE_HEIC})
  private @interface FileType {}

  private static final int FILE_TYPE_MP4 = 0;
  private static final int FILE_TYPE_QUICKTIME = 1;
  private static final int FILE_TYPE_HEIC = 2;

  /**
   * When seeking within the source, if the offset is greater than or equal to this value (or the
   * offset is negative), the source will be reloaded.
   */
  private static final long RELOAD_MINIMUM_SEEK_DISTANCE = 256 * 1024;

  /**
   * For poorly interleaved streams, the maximum byte difference one track is allowed to be read
   * ahead before the source will be reloaded at a new position to read another track.
   */
  private static final long MAXIMUM_READ_AHEAD_BYTES_STREAM = 10 * 1024 * 1024;

  private final SubtitleParser.Factory subtitleParserFactory;
  private final @Flags int flags;

  // Temporary arrays.
  private final ParsableByteArray nalStartCode;
  private final ParsableByteArray nalPrefix;
  private final ParsableByteArray scratch;

  private final ParsableByteArray atomHeader;
  private final ArrayDeque<ContainerBox> containerAtoms;
  private final SefReader sefReader;
  private final List<Metadata.Entry> slowMotionMetadataEntries;

  private ImmutableList<SniffFailure> lastSniffFailures;
  private @State int parserState;
  private int atomType;
  private long atomSize;
  private int atomHeaderBytesRead;
  @Nullable private ParsableByteArray atomData;

  private int sampleTrackIndex;
  private int sampleBytesRead;
  private int sampleBytesWritten;
  private int sampleCurrentNalBytesRemaining;
  private boolean isSampleDependedOn;
  private boolean seenFtypAtom;
  private boolean seekToAxteAtom;
  private long axteAtomOffset;
  private boolean readingAuxiliaryTracks;

  // Used when auxiliary tracks samples are in the auxiliary tracks MP4 (inside axte atom).
  private long sampleOffsetForAuxiliaryTracks;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;
  private Mp4Track[] tracks;

  private long @MonotonicNonNull [][] accumulatedSampleSizes;
  private int firstVideoTrackIndex;
  private long durationUs;
  private @FileType int fileType;
  @Nullable private MotionPhotoMetadata motionPhotoMetadata;

  /**
   * @deprecated Use {@link #Mp4Extractor(SubtitleParser.Factory)} instead
   */
  @Deprecated
  public Mp4Extractor() {
    this(SubtitleParser.Factory.UNSUPPORTED, /* flags= */ FLAG_EMIT_RAW_SUBTITLE_DATA);
  }

  /**
   * Creates a new extractor for unfragmented MP4 streams.
   *
   * @param subtitleParserFactory The {@link SubtitleParser.Factory} for parsing subtitles during
   *     extraction.
   */
  public Mp4Extractor(SubtitleParser.Factory subtitleParserFactory) {
    this(subtitleParserFactory, /* flags= */ 0);
  }

  /**
   * @deprecated Use {@link #Mp4Extractor(SubtitleParser.Factory, int)} instead
   */
  @Deprecated
  public Mp4Extractor(@Flags int flags) {
    this(SubtitleParser.Factory.UNSUPPORTED, flags);
  }

  /**
   * Creates a new extractor for unfragmented MP4 streams, using the specified flags to control the
   * extractor's behavior.
   *
   * @param subtitleParserFactory The {@link SubtitleParser.Factory} for parsing subtitles during
   *     extraction.
   * @param flags Flags that control the extractor's behavior.
   */
  public Mp4Extractor(SubtitleParser.Factory subtitleParserFactory, @Flags int flags) {
    this.subtitleParserFactory = subtitleParserFactory;
    this.flags = flags;
    lastSniffFailures = ImmutableList.of();
    parserState =
        ((flags & FLAG_READ_SEF_DATA) != 0) ? STATE_READING_SEF : STATE_READING_ATOM_HEADER;
    sefReader = new SefReader();
    slowMotionMetadataEntries = new ArrayList<>();
    atomHeader = new ParsableByteArray(Mp4Box.LONG_HEADER_SIZE);
    containerAtoms = new ArrayDeque<>();
    nalStartCode = new ParsableByteArray(NalUnitUtil.NAL_START_CODE);
    nalPrefix = new ParsableByteArray(6);
    scratch = new ParsableByteArray();
    sampleTrackIndex = C.INDEX_UNSET;
    extractorOutput = ExtractorOutput.PLACEHOLDER;
    tracks = new Mp4Track[0];
  }

  /**
   * Returns {@link Flags} denoting if an extractor should parse within GOP sample dependencies.
   *
   * @param videoCodecFlags The set of codecs for which to parse within GOP sample dependencies.
   */
  public static @Flags int codecsToParseWithinGopSampleDependenciesAsFlags(
      @C.VideoCodecFlags int videoCodecFlags) {
    @Flags int flags = 0;
    if ((videoCodecFlags & C.VIDEO_CODEC_FLAG_H264) != 0) {
      flags |= FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES;
    }
    if ((videoCodecFlags & C.VIDEO_CODEC_FLAG_H265) != 0) {
      flags |= FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES_H265;
    }
    return flags;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    @Nullable
    SniffFailure sniffFailure =
        Sniffer.sniffUnfragmented(
            input, /* acceptHeic= */ (flags & FLAG_READ_MOTION_PHOTO_METADATA) != 0);
    lastSniffFailures = sniffFailure != null ? ImmutableList.of(sniffFailure) : ImmutableList.of();
    return sniffFailure == null;
  }

  @Override
  public ImmutableList<SniffFailure> getSniffFailureDetails() {
    return lastSniffFailures;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput =
        (flags & FLAG_EMIT_RAW_SUBTITLE_DATA) == 0
            ? new SubtitleTranscodingExtractorOutput(output, subtitleParserFactory)
            : output;
  }

  @Override
  public void seek(long position, long timeUs) {
    containerAtoms.clear();
    atomHeaderBytesRead = 0;
    sampleTrackIndex = C.INDEX_UNSET;
    sampleBytesRead = 0;
    sampleBytesWritten = 0;
    sampleCurrentNalBytesRemaining = 0;
    isSampleDependedOn = false;
    if (position == 0) {
      // Reading the SEF data occurs before normal MP4 parsing. Therefore we can not transition to
      // reading the atom header until that has completed.
      if (parserState != STATE_READING_SEF) {
        enterReadingAtomHeaderState();
      } else {
        sefReader.reset();
        slowMotionMetadataEntries.clear();
      }
    } else {
      for (Mp4Track track : tracks) {
        updateSampleIndex(track, timeUs);
        if (track.trueHdSampleRechunker != null) {
          track.trueHdSampleRechunker.reset();
        }
      }
    }
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    while (true) {
      switch (parserState) {
        case STATE_READING_ATOM_HEADER:
          if (!readAtomHeader(input)) {
            return RESULT_END_OF_INPUT;
          }
          break;
        case STATE_READING_ATOM_PAYLOAD:
          if (readAtomPayload(input, seekPosition)) {
            return RESULT_SEEK;
          }
          break;
        case STATE_READING_SAMPLE:
          return readSample(input, seekPosition);
        case STATE_READING_SEF:
          return readSefData(input, seekPosition);
        default:
          throw new IllegalStateException();
      }
    }
  }

  // SeekMap implementation.

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    return getSeekPoints(timeUs, /* trackId= */ C.INDEX_UNSET);
  }

  // Non-inherited public methods.

  /**
   * Equivalent to {@link SeekMap#getSeekPoints(long)}, except it adds the {@code trackId}
   * parameter.
   *
   * @param timeUs A seek time in microseconds.
   * @param trackId The id of the track on which to seek for {@link SeekPoints}. May be {@link
   *     C#INDEX_UNSET} if the extractor is expected to define the strategy for generating {@link
   *     SeekPoints}.
   * @return The corresponding seek points.
   */
  public SeekPoints getSeekPoints(long timeUs, int trackId) {
    if (tracks.length == 0) {
      return new SeekPoints(SeekPoint.START);
    }

    long firstTimeUs;
    long firstOffset;
    long secondTimeUs = C.TIME_UNSET;
    long secondOffset = C.INDEX_UNSET;

    // Note that the id matches the index in tracks.
    int mainTrackIndex = trackId != C.INDEX_UNSET ? trackId : firstVideoTrackIndex;
    // If we have a video track, use it to establish one or two seek points.
    if (mainTrackIndex != C.INDEX_UNSET) {
      TrackSampleTable sampleTable = tracks[mainTrackIndex].sampleTable;
      int sampleIndex = getSynchronizationSampleIndex(sampleTable, timeUs);
      if (sampleIndex == C.INDEX_UNSET) {
        return new SeekPoints(SeekPoint.START);
      }
      long sampleTimeUs = sampleTable.timestampsUs[sampleIndex];
      firstTimeUs = sampleTimeUs;
      firstOffset = sampleTable.offsets[sampleIndex];
      if (sampleTimeUs < timeUs && sampleIndex < sampleTable.sampleCount - 1) {
        int secondSampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
        if (secondSampleIndex != C.INDEX_UNSET && secondSampleIndex != sampleIndex) {
          secondTimeUs = sampleTable.timestampsUs[secondSampleIndex];
          secondOffset = sampleTable.offsets[secondSampleIndex];
        }
      }
    } else {
      firstTimeUs = timeUs;
      firstOffset = Long.MAX_VALUE;
    }

    if (trackId == C.INDEX_UNSET) {
      // Take into account other tracks, but only if the caller has not specified a trackId.
      for (int i = 0; i < tracks.length; i++) {
        if (i != firstVideoTrackIndex) {
          TrackSampleTable sampleTable = tracks[i].sampleTable;
          firstOffset = maybeAdjustSeekOffset(sampleTable, firstTimeUs, firstOffset);
          if (secondTimeUs != C.TIME_UNSET) {
            secondOffset = maybeAdjustSeekOffset(sampleTable, secondTimeUs, secondOffset);
          }
        }
      }
    }

    SeekPoint firstSeekPoint = new SeekPoint(firstTimeUs, firstOffset);
    if (secondTimeUs == C.TIME_UNSET) {
      return new SeekPoints(firstSeekPoint);
    } else {
      SeekPoint secondSeekPoint = new SeekPoint(secondTimeUs, secondOffset);
      return new SeekPoints(firstSeekPoint, secondSeekPoint);
    }
  }

  /**
   * Returns the list of sample timestamps of a {@code trackId}, in microseconds.
   *
   * @param trackId The id of the track to get the sample timestamps.
   * @return The corresponding sample timestmaps of the track.
   */
  public long[] getSampleTimestampsUs(int trackId) {
    if (tracks.length <= trackId) {
      return new long[0];
    }
    return tracks[trackId].sampleTable.timestampsUs;
  }

  // Private methods.

  private void enterReadingAtomHeaderState() {
    parserState = STATE_READING_ATOM_HEADER;
    atomHeaderBytesRead = 0;
  }

  private boolean readAtomHeader(ExtractorInput input) throws IOException {
    if (atomHeaderBytesRead == 0) {
      // Read the standard length atom header.
      if (!input.readFully(atomHeader.getData(), 0, Mp4Box.HEADER_SIZE, true)) {
        processEndOfStreamReadingAtomHeader();
        return false;
      }
      atomHeaderBytesRead = Mp4Box.HEADER_SIZE;
      atomHeader.setPosition(0);
      atomSize = atomHeader.readUnsignedInt();
      atomType = atomHeader.readInt();
    }

    if (atomSize == Mp4Box.DEFINES_LARGE_SIZE) {
      // Read the large size.
      int headerBytesRemaining = Mp4Box.LONG_HEADER_SIZE - Mp4Box.HEADER_SIZE;
      input.readFully(atomHeader.getData(), Mp4Box.HEADER_SIZE, headerBytesRemaining);
      atomHeaderBytesRead += headerBytesRemaining;
      atomSize = atomHeader.readUnsignedLongToLong();
    } else if (atomSize == Mp4Box.EXTENDS_TO_END_SIZE) {
      // The atom extends to the end of the file. Note that if the atom is within a container we can
      // work out its size even if the input length is unknown.
      long endPosition = input.getLength();
      if (endPosition == C.LENGTH_UNSET) {
        @Nullable ContainerBox containerAtom = containerAtoms.peek();
        if (containerAtom != null) {
          endPosition = containerAtom.endPosition;
        }
      }
      if (endPosition != C.LENGTH_UNSET) {
        atomSize = endPosition - input.getPosition() + atomHeaderBytesRead;
      }
    }

    if (atomSize < atomHeaderBytesRead) {
      throw ParserException.createForUnsupportedContainerFeature(
          "Atom size less than header length (unsupported).");
    }

    if (shouldParseContainerAtom(atomType)) {
      long endPosition = input.getPosition() + atomSize - atomHeaderBytesRead;
      if (atomSize != atomHeaderBytesRead && atomType == Mp4Box.TYPE_meta) {
        maybeSkipRemainingMetaAtomHeaderBytes(input);
      }
      containerAtoms.push(new ContainerBox(atomType, endPosition));
      if (atomSize == atomHeaderBytesRead) {
        processAtomEnded(endPosition);
      } else {
        // Start reading the first child atom.
        enterReadingAtomHeaderState();
      }
    } else if (shouldParseLeafAtom(atomType)) {
      // We don't support parsing of leaf atoms that define extended atom sizes, or that have
      // lengths greater than Integer.MAX_VALUE.
      Assertions.checkState(atomHeaderBytesRead == Mp4Box.HEADER_SIZE);
      Assertions.checkState(atomSize <= Integer.MAX_VALUE);
      ParsableByteArray atomData = new ParsableByteArray((int) atomSize);
      System.arraycopy(atomHeader.getData(), 0, atomData.getData(), 0, Mp4Box.HEADER_SIZE);
      this.atomData = atomData;
      parserState = STATE_READING_ATOM_PAYLOAD;
    } else {
      processUnparsedAtom(input.getPosition() - atomHeaderBytesRead);
      atomData = null;
      parserState = STATE_READING_ATOM_PAYLOAD;
    }

    return true;
  }

  /**
   * Processes the atom payload.
   *
   * <p>If seek is required, {@code true} is returned and the caller should restart loading at the
   * position in {@code positionHolder}. Otherwise the atom is read/skipped.
   */
  private boolean readAtomPayload(ExtractorInput input, PositionHolder positionHolder)
      throws IOException {
    long atomPayloadSize = atomSize - atomHeaderBytesRead;
    long atomEndPosition = input.getPosition() + atomPayloadSize;
    boolean seekRequired = false;
    @Nullable ParsableByteArray atomData = this.atomData;
    if (atomData != null) {
      input.readFully(atomData.getData(), atomHeaderBytesRead, (int) atomPayloadSize);
      if (atomType == Mp4Box.TYPE_ftyp) {
        seenFtypAtom = true;
        fileType = processFtypAtom(atomData);
      } else if (!containerAtoms.isEmpty()) {
        containerAtoms.peek().add(new Mp4Box.LeafBox(atomType, atomData));
      }
    } else {
      if (!seenFtypAtom && atomType == Mp4Box.TYPE_mdat) {
        // The original QuickTime specification did not require files to begin with the ftyp atom.
        // See https://developer.apple.com/standards/qtff-2001.pdf.
        fileType = FILE_TYPE_QUICKTIME;
      }
      // We don't need the data. Skip or seek, depending on how large the atom is.
      if (atomPayloadSize < RELOAD_MINIMUM_SEEK_DISTANCE) {
        input.skipFully((int) atomPayloadSize);
      } else {
        positionHolder.position = input.getPosition() + atomPayloadSize;
        seekRequired = true;
      }
    }
    processAtomEnded(atomEndPosition);
    if (seekToAxteAtom) {
      readingAuxiliaryTracks = true;
      positionHolder.position = axteAtomOffset;
      seekRequired = true;
      seekToAxteAtom = false;
    }
    return seekRequired && parserState != STATE_READING_SAMPLE;
  }

  private @ReadResult int readSefData(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    @ReadResult int result = sefReader.read(input, seekPosition, slowMotionMetadataEntries);
    if (result == RESULT_SEEK && seekPosition.position == 0) {
      enterReadingAtomHeaderState();
    }
    return result;
  }

  private void processAtomEnded(long atomEndPosition) throws ParserException {
    while (!containerAtoms.isEmpty() && containerAtoms.peek().endPosition == atomEndPosition) {
      ContainerBox containerAtom = containerAtoms.pop();
      if (containerAtom.type == Mp4Box.TYPE_moov) {
        // We've reached the end of the moov atom. Process it and prepare to read samples.
        processMoovAtom(containerAtom);
        containerAtoms.clear();
        if (!seekToAxteAtom) {
          parserState = STATE_READING_SAMPLE;
        }
      } else if (!containerAtoms.isEmpty()) {
        containerAtoms.peek().add(containerAtom);
      }
    }
    if (parserState != STATE_READING_SAMPLE) {
      enterReadingAtomHeaderState();
    }
  }

  /**
   * Processes moov atom and updates the stored track metadata.
   *
   * <p>The processing is aborted if the axte.moov atom needs to be processed instead.
   */
  private void processMoovAtom(ContainerBox moov) throws ParserException {
    // Process metadata first to determine whether to abort processing and seek to the axte atom.
    @Nullable Metadata mdtaMetadata = null;
    @Nullable Mp4Box.ContainerBox meta = moov.getContainerBoxOfType(Mp4Box.TYPE_meta);
    List<@C.AuxiliaryTrackType Integer> auxiliaryTrackTypesForAuxiliaryTracks = new ArrayList<>();
    if (meta != null) {
      mdtaMetadata = BoxParser.parseMdtaFromMeta(meta);
      if (readingAuxiliaryTracks) {
        checkStateNotNull(mdtaMetadata);
        maybeSetDefaultSampleOffsetForAuxiliaryTracks(mdtaMetadata);
        auxiliaryTrackTypesForAuxiliaryTracks =
            getAuxiliaryTrackTypesForAuxiliaryTracks(mdtaMetadata);
      } else if (shouldSeekToAxteAtom(mdtaMetadata)) {
        seekToAxteAtom = true;
        return;
      }
    }
    int firstVideoTrackIndex = C.INDEX_UNSET;
    long durationUs = C.TIME_UNSET;
    List<Mp4Track> tracks = new ArrayList<>();

    // Process remaining metadata.
    boolean isQuickTime = fileType == FILE_TYPE_QUICKTIME;
    GaplessInfoHolder gaplessInfoHolder = new GaplessInfoHolder();
    @Nullable Metadata udtaMetadata = null;
    @Nullable Mp4Box.LeafBox udta = moov.getLeafBoxOfType(Mp4Box.TYPE_udta);
    if (udta != null) {
      udtaMetadata = BoxParser.parseUdta(udta);
      gaplessInfoHolder.setFromMetadata(udtaMetadata);
    }

    Metadata mvhdMetadata =
        new Metadata(
            BoxParser.parseMvhd(checkNotNull(moov.getLeafBoxOfType(Mp4Box.TYPE_mvhd)).data));

    boolean ignoreEditLists = (flags & FLAG_WORKAROUND_IGNORE_EDIT_LISTS) != 0;
    List<TrackSampleTable> trackSampleTables =
        parseTraks(
            moov,
            gaplessInfoHolder,
            /* duration= */ C.TIME_UNSET,
            /* drmInitData= */ null,
            ignoreEditLists,
            isQuickTime,
            /* modifyTrackFunction= */ track -> track);

    if (readingAuxiliaryTracks) {
      checkState(
          auxiliaryTrackTypesForAuxiliaryTracks.size() == trackSampleTables.size(),
          String.format(
              Locale.US,
              "The number of auxiliary track types from metadata (%d) is not same as the number of"
                  + " auxiliary tracks (%d)",
              auxiliaryTrackTypesForAuxiliaryTracks.size(),
              trackSampleTables.size()));
    }
    int trackIndex = 0;
    String containerMimeType = getContainerMimeType(trackSampleTables);
    for (int i = 0; i < trackSampleTables.size(); i++) {
      TrackSampleTable trackSampleTable = trackSampleTables.get(i);
      if (trackSampleTable.sampleCount == 0) {
        continue;
      }
      Track track = trackSampleTable.track;
      Mp4Track mp4Track =
          new Mp4Track(track, trackSampleTable, extractorOutput.track(trackIndex++, track.type));

      long trackDurationUs =
          track.durationUs != C.TIME_UNSET ? track.durationUs : trackSampleTable.durationUs;
      mp4Track.trackOutput.durationUs(trackDurationUs);
      durationUs = max(durationUs, trackDurationUs);

      int maxInputSize;
      if (MimeTypes.AUDIO_TRUEHD.equals(track.format.sampleMimeType)) {
        // TrueHD groups samples per chunks of TRUEHD_RECHUNK_SAMPLE_COUNT samples.
        maxInputSize = trackSampleTable.maximumSize * Ac3Util.TRUEHD_RECHUNK_SAMPLE_COUNT;
      } else {
        // Each sample has up to three bytes of overhead for the start code that replaces its
        // length. Allow ten source samples per output sample, like the platform extractor.
        maxInputSize = trackSampleTable.maximumSize + 3 * 10;
      }

      Format.Builder formatBuilder = track.format.buildUpon();
      formatBuilder.setMaxInputSize(maxInputSize);
      if (track.type == C.TRACK_TYPE_VIDEO) {
        @C.RoleFlags int roleFlags = track.format.roleFlags;
        if ((flags & FLAG_MARK_FIRST_VIDEO_TRACK_WITH_MAIN_ROLE) != 0) {
          roleFlags |=
              firstVideoTrackIndex == C.INDEX_UNSET ? C.ROLE_FLAG_MAIN : C.ROLE_FLAG_ALTERNATE;
        }
        if (readingAuxiliaryTracks) {
          roleFlags |= C.ROLE_FLAG_AUXILIARY;
          formatBuilder.setAuxiliaryTrackType(auxiliaryTrackTypesForAuxiliaryTracks.get(i));
        }
        formatBuilder.setRoleFlags(roleFlags);
      }

      MetadataUtil.setFormatGaplessInfo(track.type, gaplessInfoHolder, formatBuilder);
      MetadataUtil.setFormatMetadata(
          track.type,
          mdtaMetadata,
          formatBuilder,
          track.format.metadata,
          slowMotionMetadataEntries.isEmpty() ? null : new Metadata(slowMotionMetadataEntries),
          udtaMetadata,
          mvhdMetadata);
      formatBuilder.setContainerMimeType(containerMimeType);
      mp4Track.trackOutput.format(formatBuilder.build());

      if (track.type == C.TRACK_TYPE_VIDEO && firstVideoTrackIndex == C.INDEX_UNSET) {
        firstVideoTrackIndex = tracks.size();
      }
      tracks.add(mp4Track);
    }
    this.firstVideoTrackIndex = firstVideoTrackIndex;
    this.durationUs = durationUs;
    this.tracks = tracks.toArray(new Mp4Track[0]);
    accumulatedSampleSizes = calculateAccumulatedSampleSizes(this.tracks);

    extractorOutput.endTracks();
    extractorOutput.seekMap(this);
  }

  private boolean shouldSeekToAxteAtom(@Nullable Metadata mdtaMetadata) {
    if (mdtaMetadata == null) {
      return false;
    }
    if ((flags & FLAG_READ_AUXILIARY_TRACKS) != 0) {
      @Nullable
      MdtaMetadataEntry axteAtomOffsetMetadata =
          findMdtaMetadataEntryWithKey(mdtaMetadata, MdtaMetadataEntry.KEY_AUXILIARY_TRACKS_OFFSET);
      if (axteAtomOffsetMetadata != null) {
        long offset = new ParsableByteArray(axteAtomOffsetMetadata.value).readUnsignedLongToLong();
        if (offset > 0) {
          axteAtomOffset = offset;
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Sets the sample offset for the auxiliary tracks, if the samples are in the auxiliary tracks MP4
   * (inside axte atom).
   */
  private void maybeSetDefaultSampleOffsetForAuxiliaryTracks(Metadata metadata) {
    @Nullable
    MdtaMetadataEntry samplesInterleavedMetadata =
        findMdtaMetadataEntryWithKey(metadata, MdtaMetadataEntry.KEY_AUXILIARY_TRACKS_INTERLEAVED);
    if (samplesInterleavedMetadata != null) {
      if (samplesInterleavedMetadata.value[0] == AUXILIARY_TRACKS_SAMPLES_NOT_INTERLEAVED) {
        sampleOffsetForAuxiliaryTracks = axteAtomOffset + 16; // 16 bits for axte atom header
      }
    }
  }

  private List<@C.AuxiliaryTrackType Integer> getAuxiliaryTrackTypesForAuxiliaryTracks(
      Metadata metadata) {
    MdtaMetadataEntry trackTypesMetadata =
        checkStateNotNull(
            findMdtaMetadataEntryWithKey(metadata, MdtaMetadataEntry.KEY_AUXILIARY_TRACKS_MAP));
    List<Integer> auxiliaryTrackTypesFromMap = trackTypesMetadata.getAuxiliaryTrackTypesFromMap();
    List<@C.AuxiliaryTrackType Integer> auxiliaryTrackTypes =
        new ArrayList<>(auxiliaryTrackTypesFromMap.size());
    for (int i = 0; i < auxiliaryTrackTypesFromMap.size(); i++) {
      @C.AuxiliaryTrackType int auxiliaryTrackType;
      switch (auxiliaryTrackTypesFromMap.get(i)) {
        case 0:
          auxiliaryTrackType = AUXILIARY_TRACK_TYPE_ORIGINAL;
          break;
        case 1:
          auxiliaryTrackType = AUXILIARY_TRACK_TYPE_DEPTH_LINEAR;
          break;
        case 2:
          auxiliaryTrackType = AUXILIARY_TRACK_TYPE_DEPTH_INVERSE;
          break;
        case 3:
          auxiliaryTrackType = C.AUXILIARY_TRACK_TYPE_DEPTH_METADATA;
          break;
        default:
          auxiliaryTrackType = AUXILIARY_TRACK_TYPE_UNDEFINED;
      }
      auxiliaryTrackTypes.add(auxiliaryTrackType);
    }
    return auxiliaryTrackTypes;
  }

  /**
   * Attempts to extract the next sample in the current mdat atom for the specified track.
   *
   * <p>Returns {@link #RESULT_SEEK} if the source should be reloaded from the position in {@code
   * positionHolder}.
   *
   * <p>Returns {@link #RESULT_END_OF_INPUT} if no samples are left. Otherwise, returns {@link
   * #RESULT_CONTINUE}.
   *
   * @param input The {@link ExtractorInput} from which to read data.
   * @param positionHolder If {@link #RESULT_SEEK} is returned, this holder is updated to hold the
   *     position of the required data.
   * @return One of the {@code RESULT_*} flags in {@link Extractor}.
   * @throws IOException If an error occurs reading from the input.
   */
  private int readSample(ExtractorInput input, PositionHolder positionHolder) throws IOException {
    long inputPosition = input.getPosition();
    if (sampleTrackIndex == C.INDEX_UNSET) {
      sampleTrackIndex = getTrackIndexOfNextReadSample(inputPosition);
      if (sampleTrackIndex == C.INDEX_UNSET) {
        return RESULT_END_OF_INPUT;
      }
    }
    Mp4Track track = tracks[sampleTrackIndex];
    TrackOutput trackOutput = track.trackOutput;
    int sampleIndex = track.sampleIndex;
    long position = track.sampleTable.offsets[sampleIndex] + sampleOffsetForAuxiliaryTracks;
    int sampleSize = track.sampleTable.sizes[sampleIndex];
    @Nullable TrueHdSampleRechunker trueHdSampleRechunker = track.trueHdSampleRechunker;
    long skipAmount = position - inputPosition + sampleBytesRead;
    if (skipAmount < 0 || skipAmount >= RELOAD_MINIMUM_SEEK_DISTANCE) {
      positionHolder.position = position;
      return RESULT_SEEK;
    }
    if (track.track.sampleTransformation == Track.TRANSFORMATION_CEA608_CDAT) {
      // The sample information is contained in a cdat atom. The header must be discarded for
      // committing.
      skipAmount += Mp4Box.HEADER_SIZE;
      sampleSize -= Mp4Box.HEADER_SIZE;
    }
    input.skipFully((int) skipAmount);
    if (!canReadWithinGopSampleDependencies(track.track.format)) {
      isSampleDependedOn = true;
    }
    if (track.track.nalUnitLengthFieldLength != 0) {
      // Zero the top three bytes of the array that we'll use to decode nal unit lengths, in case
      // they're only 1 or 2 bytes long.
      byte[] nalPrefixData = nalPrefix.getData();
      nalPrefixData[0] = 0;
      nalPrefixData[1] = 0;
      nalPrefixData[2] = 0;
      int nalUnitLengthFieldLengthDiff = 4 - track.track.nalUnitLengthFieldLength;
      sampleSize += nalUnitLengthFieldLengthDiff;
      // NAL units are length delimited, but the decoder requires start code delimited units.
      // Loop until we've written the sample to the track output, replacing length delimiters with
      // start codes as we encounter them.
      while (sampleBytesWritten < sampleSize) {
        if (sampleCurrentNalBytesRemaining == 0) {
          int nalUnitPrefixLength = track.track.nalUnitLengthFieldLength;
          int numberOfBytesToDetermineSampleDependencies = 0;
          if (!isSampleDependedOn
              && nalUnitPrefixLength + NalUnitUtil.numberOfBytesInNalUnitHeader(track.track.format)
                  <= track.sampleTable.sizes[sampleIndex] - sampleBytesRead) {
            // Parsing sample dependencies needs the first few NAL unit bytes. Read them in the same
            // readFully call that reads the NAL length. This ensures sampleBytesRead,
            // sampleBytesWritten and isSampleDependedOn remain in a consistent state if we have
            // read failures.
            numberOfBytesToDetermineSampleDependencies =
                NalUnitUtil.numberOfBytesInNalUnitHeader(track.track.format);
            nalUnitPrefixLength =
                track.track.nalUnitLengthFieldLength + numberOfBytesToDetermineSampleDependencies;
          }
          // Read the NAL length so that we know where we find the next one.
          input.readFully(nalPrefixData, nalUnitLengthFieldLengthDiff, nalUnitPrefixLength);
          sampleBytesRead += nalUnitPrefixLength;
          nalPrefix.setPosition(0);
          int nalLengthInt = nalPrefix.readInt();
          if (nalLengthInt < 0) {
            throw ParserException.createForMalformedContainer(
                "Invalid NAL length", /* cause= */ null);
          }
          sampleCurrentNalBytesRemaining =
              nalLengthInt - numberOfBytesToDetermineSampleDependencies;
          // Write a start code for the current NAL unit.
          nalStartCode.setPosition(0);
          trackOutput.sampleData(nalStartCode, 4);
          sampleBytesWritten += 4;
          if (numberOfBytesToDetermineSampleDependencies > 0) {
            // Write the first NAL unit bytes that were read.
            trackOutput.sampleData(nalPrefix, numberOfBytesToDetermineSampleDependencies);
            sampleBytesWritten += numberOfBytesToDetermineSampleDependencies;
            // If any NAL unit that's part of this sample can be depended on, treat the entire
            // sample as depended on.
            if (NalUnitUtil.isDependedOn(
                nalPrefixData,
                /* offset= */ 4,
                /* length= */ numberOfBytesToDetermineSampleDependencies,
                track.track.format)) {
              isSampleDependedOn = true;
            }
          }
        } else {
          // Write the payload of the NAL unit.
          int writtenBytes = trackOutput.sampleData(input, sampleCurrentNalBytesRemaining, false);
          sampleBytesRead += writtenBytes;
          sampleBytesWritten += writtenBytes;
          sampleCurrentNalBytesRemaining -= writtenBytes;
        }
      }
    } else {
      if (MimeTypes.AUDIO_AC4.equals(track.track.format.sampleMimeType)) {
        if (sampleBytesWritten == 0) {
          Ac4Util.getAc4SampleHeader(sampleSize, scratch);
          trackOutput.sampleData(scratch, Ac4Util.SAMPLE_HEADER_SIZE);
          sampleBytesWritten += Ac4Util.SAMPLE_HEADER_SIZE;
        }
        sampleSize += Ac4Util.SAMPLE_HEADER_SIZE;
      } else if (trueHdSampleRechunker != null) {
        trueHdSampleRechunker.startSample(input);
      }

      while (sampleBytesWritten < sampleSize) {
        int writtenBytes = trackOutput.sampleData(input, sampleSize - sampleBytesWritten, false);
        sampleBytesRead += writtenBytes;
        sampleBytesWritten += writtenBytes;
        sampleCurrentNalBytesRemaining -= writtenBytes;
      }
    }

    long timeUs = track.sampleTable.timestampsUs[sampleIndex];
    @C.BufferFlags int sampleFlags = track.sampleTable.flags[sampleIndex];
    if (!isSampleDependedOn) {
      sampleFlags |= C.BUFFER_FLAG_NOT_DEPENDED_ON;
    }
    if (trueHdSampleRechunker != null) {
      trueHdSampleRechunker.sampleMetadata(
          trackOutput, timeUs, sampleFlags, sampleSize, /* offset= */ 0, /* cryptoData= */ null);
      if (sampleIndex + 1 == track.sampleTable.sampleCount) {
        trueHdSampleRechunker.outputPendingSampleMetadata(trackOutput, /* cryptoData= */ null);
      }
    } else {
      trackOutput.sampleMetadata(
          timeUs, sampleFlags, sampleSize, /* offset= */ 0, /* cryptoData= */ null);
    }

    track.sampleIndex++;
    sampleTrackIndex = C.INDEX_UNSET;
    sampleBytesRead = 0;
    sampleBytesWritten = 0;
    sampleCurrentNalBytesRemaining = 0;
    isSampleDependedOn = false;
    return RESULT_CONTINUE;
  }

  /**
   * Returns the index of the track that contains the next sample to be read, or {@link
   * C#INDEX_UNSET} if no samples remain.
   *
   * <p>The preferred choice is the sample with the smallest offset not requiring a source reload,
   * or if not available the sample with the smallest overall offset to avoid subsequent source
   * reloads.
   *
   * <p>To deal with poor sample interleaving, we also check whether the required memory to catch up
   * with the next logical sample (based on sample time) exceeds {@link
   * #MAXIMUM_READ_AHEAD_BYTES_STREAM}. If this is the case, we continue with this sample even
   * though it may require a source reload.
   */
  private int getTrackIndexOfNextReadSample(long inputPosition) {
    long preferredSkipAmount = Long.MAX_VALUE;
    boolean preferredRequiresReload = true;
    int preferredTrackIndex = C.INDEX_UNSET;
    long preferredAccumulatedBytes = Long.MAX_VALUE;
    long minAccumulatedBytes = Long.MAX_VALUE;
    boolean minAccumulatedBytesRequiresReload = true;
    int minAccumulatedBytesTrackIndex = C.INDEX_UNSET;
    for (int trackIndex = 0; trackIndex < tracks.length; trackIndex++) {
      Mp4Track track = tracks[trackIndex];
      int sampleIndex = track.sampleIndex;
      if (sampleIndex == track.sampleTable.sampleCount) {
        continue;
      }
      long sampleOffset = track.sampleTable.offsets[sampleIndex];
      long sampleAccumulatedBytes = castNonNull(accumulatedSampleSizes)[trackIndex][sampleIndex];
      long skipAmount = sampleOffset - inputPosition;
      boolean requiresReload = skipAmount < 0 || skipAmount >= RELOAD_MINIMUM_SEEK_DISTANCE;
      if ((!requiresReload && preferredRequiresReload)
          || (requiresReload == preferredRequiresReload && skipAmount < preferredSkipAmount)) {
        preferredRequiresReload = requiresReload;
        preferredSkipAmount = skipAmount;
        preferredTrackIndex = trackIndex;
        preferredAccumulatedBytes = sampleAccumulatedBytes;
      }
      if (sampleAccumulatedBytes < minAccumulatedBytes) {
        minAccumulatedBytes = sampleAccumulatedBytes;
        minAccumulatedBytesRequiresReload = requiresReload;
        minAccumulatedBytesTrackIndex = trackIndex;
      }
    }
    return minAccumulatedBytes == Long.MAX_VALUE
            || !minAccumulatedBytesRequiresReload
            || preferredAccumulatedBytes < minAccumulatedBytes + MAXIMUM_READ_AHEAD_BYTES_STREAM
        ? preferredTrackIndex
        : minAccumulatedBytesTrackIndex;
  }

  /** Updates a track's sample index to point its latest sync sample before/at {@code timeUs}. */
  private void updateSampleIndex(Mp4Track track, long timeUs) {
    TrackSampleTable sampleTable = track.sampleTable;
    int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
    if (sampleIndex == C.INDEX_UNSET) {
      // Handle the case where the requested time is before the first synchronization sample.
      sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
    }
    track.sampleIndex = sampleIndex;
  }

  /** Processes the end of stream in case there is not atom left to read. */
  private void processEndOfStreamReadingAtomHeader() {
    if (fileType == FILE_TYPE_HEIC && (flags & FLAG_READ_MOTION_PHOTO_METADATA) != 0) {
      // Add image track and prepare media.
      TrackOutput trackOutput = extractorOutput.track(/* id= */ 0, C.TRACK_TYPE_IMAGE);
      @Nullable
      Metadata metadata = motionPhotoMetadata == null ? null : new Metadata(motionPhotoMetadata);
      trackOutput.format(new Format.Builder().setMetadata(metadata).build());
      extractorOutput.endTracks();
      extractorOutput.seekMap(new SeekMap.Unseekable(/* durationUs= */ C.TIME_UNSET));
    }
  }

  private void maybeSkipRemainingMetaAtomHeaderBytes(ExtractorInput input) throws IOException {
    scratch.reset(8);
    input.peekFully(scratch.getData(), 0, 8);
    BoxParser.maybeSkipRemainingMetaBoxHeaderBytes(scratch);
    input.skipFully(scratch.getPosition());
    input.resetPeekPosition();
  }

  /** Processes an atom whose payload does not need to be parsed. */
  private void processUnparsedAtom(long atomStartPosition) {
    if (atomType == Mp4Box.TYPE_mpvd) {
      // The input is an HEIC motion photo following the Google Photos Motion Photo File Format
      // V1.1.
      motionPhotoMetadata =
          new MotionPhotoMetadata(
              /* photoStartPosition= */ 0,
              /* photoSize= */ atomStartPosition,
              /* photoPresentationTimestampUs= */ C.TIME_UNSET,
              /* videoStartPosition= */ atomStartPosition + atomHeaderBytesRead,
              /* videoSize= */ atomSize - atomHeaderBytesRead);
    }
  }

  /**
   * Returns whether reading within GOP sample dependencies is enabled for the sample {@link
   * Format}.
   */
  private boolean canReadWithinGopSampleDependencies(Format format) {
    if (Objects.equals(format.sampleMimeType, MimeTypes.VIDEO_H264)) {
      return (flags & FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES) != 0;
    }
    if (Objects.equals(format.sampleMimeType, MimeTypes.VIDEO_H265)) {
      return (flags & FLAG_READ_WITHIN_GOP_SAMPLE_DEPENDENCIES_H265) != 0;
    }
    return false;
  }

  /**
   * For each sample of each track, calculates accumulated size of all samples which need to be read
   * before this sample can be used.
   */
  private static long[][] calculateAccumulatedSampleSizes(Mp4Track[] tracks) {
    long[][] accumulatedSampleSizes = new long[tracks.length][];
    int[] nextSampleIndex = new int[tracks.length];
    long[] nextSampleTimesUs = new long[tracks.length];
    boolean[] tracksFinished = new boolean[tracks.length];
    for (int i = 0; i < tracks.length; i++) {
      accumulatedSampleSizes[i] = new long[tracks[i].sampleTable.sampleCount];
      nextSampleTimesUs[i] = tracks[i].sampleTable.timestampsUs[0];
    }
    long accumulatedSampleSize = 0;
    int finishedTracks = 0;
    while (finishedTracks < tracks.length) {
      long minTimeUs = Long.MAX_VALUE;
      int minTimeTrackIndex = -1;
      for (int i = 0; i < tracks.length; i++) {
        if (!tracksFinished[i] && nextSampleTimesUs[i] <= minTimeUs) {
          minTimeTrackIndex = i;
          minTimeUs = nextSampleTimesUs[i];
        }
      }
      int trackSampleIndex = nextSampleIndex[minTimeTrackIndex];
      accumulatedSampleSizes[minTimeTrackIndex][trackSampleIndex] = accumulatedSampleSize;
      accumulatedSampleSize += tracks[minTimeTrackIndex].sampleTable.sizes[trackSampleIndex];
      nextSampleIndex[minTimeTrackIndex] = ++trackSampleIndex;
      if (trackSampleIndex < accumulatedSampleSizes[minTimeTrackIndex].length) {
        nextSampleTimesUs[minTimeTrackIndex] =
            tracks[minTimeTrackIndex].sampleTable.timestampsUs[trackSampleIndex];
      } else {
        tracksFinished[minTimeTrackIndex] = true;
        finishedTracks++;
      }
    }
    return accumulatedSampleSizes;
  }

  /**
   * Adjusts a seek point offset to take into account the track with the given {@code sampleTable},
   * for a given {@code seekTimeUs}.
   *
   * @param sampleTable The sample table to use.
   * @param seekTimeUs The seek time in microseconds.
   * @param offset The current offset.
   * @return The adjusted offset.
   */
  private static long maybeAdjustSeekOffset(
      TrackSampleTable sampleTable, long seekTimeUs, long offset) {
    int sampleIndex = getSynchronizationSampleIndex(sampleTable, seekTimeUs);
    if (sampleIndex == C.INDEX_UNSET) {
      return offset;
    }
    long sampleOffset = sampleTable.offsets[sampleIndex];
    return min(sampleOffset, offset);
  }

  /**
   * Returns the index of the synchronization sample before or at {@code timeUs}, or the index of
   * the first synchronization sample if located after {@code timeUs}, or {@link C#INDEX_UNSET} if
   * there are no synchronization samples in the table.
   *
   * @param sampleTable The sample table in which to locate a synchronization sample.
   * @param timeUs A time in microseconds.
   * @return The index of the synchronization sample before or at {@code timeUs}, or the index of
   *     the first synchronization sample if located after {@code timeUs}, or {@link C#INDEX_UNSET}
   *     if there are no synchronization samples in the table.
   */
  private static int getSynchronizationSampleIndex(TrackSampleTable sampleTable, long timeUs) {
    int sampleIndex = sampleTable.getIndexOfEarlierOrEqualSynchronizationSample(timeUs);
    if (sampleIndex == C.INDEX_UNSET) {
      // Handle the case where the requested time is before the first synchronization sample.
      sampleIndex = sampleTable.getIndexOfLaterOrEqualSynchronizationSample(timeUs);
    }
    return sampleIndex;
  }

  /**
   * Process an ftyp atom to determine the corresponding {@link FileType}.
   *
   * @param atomData The ftyp atom data.
   * @return The {@link FileType}.
   */
  private static @FileType int processFtypAtom(ParsableByteArray atomData) {
    atomData.setPosition(Mp4Box.HEADER_SIZE);
    int majorBrand = atomData.readInt();
    @FileType int fileType = brandToFileType(majorBrand);
    if (fileType != FILE_TYPE_MP4) {
      return fileType;
    }
    atomData.skipBytes(4); // minor_version
    while (atomData.bytesLeft() > 0) {
      fileType = brandToFileType(atomData.readInt());
      if (fileType != FILE_TYPE_MP4) {
        return fileType;
      }
    }
    return FILE_TYPE_MP4;
  }

  private static @FileType int brandToFileType(int brand) {
    switch (brand) {
      case BRAND_QUICKTIME:
        return FILE_TYPE_QUICKTIME;
      case BRAND_HEIC:
        return FILE_TYPE_HEIC;
      default:
        return FILE_TYPE_MP4;
    }
  }

  /** Returns whether the extractor should decode a leaf atom with type {@code atom}. */
  private static boolean shouldParseLeafAtom(int atom) {
    return atom == Mp4Box.TYPE_mdhd
        || atom == Mp4Box.TYPE_mvhd
        || atom == Mp4Box.TYPE_hdlr
        || atom == Mp4Box.TYPE_stsd
        || atom == Mp4Box.TYPE_stts
        || atom == Mp4Box.TYPE_stss
        || atom == Mp4Box.TYPE_ctts
        || atom == Mp4Box.TYPE_elst
        || atom == Mp4Box.TYPE_stsc
        || atom == Mp4Box.TYPE_stsz
        || atom == Mp4Box.TYPE_stz2
        || atom == Mp4Box.TYPE_stco
        || atom == Mp4Box.TYPE_co64
        || atom == Mp4Box.TYPE_tkhd
        || atom == Mp4Box.TYPE_ftyp
        || atom == Mp4Box.TYPE_udta
        || atom == Mp4Box.TYPE_keys
        || atom == Mp4Box.TYPE_ilst;
  }

  /** Returns whether the extractor should decode a container atom with type {@code atom}. */
  private static boolean shouldParseContainerAtom(int atom) {
    return atom == Mp4Box.TYPE_moov
        || atom == Mp4Box.TYPE_trak
        || atom == Mp4Box.TYPE_mdia
        || atom == Mp4Box.TYPE_minf
        || atom == Mp4Box.TYPE_stbl
        || atom == Mp4Box.TYPE_edts
        || atom == Mp4Box.TYPE_meta
        || atom == Mp4Box.TYPE_axte;
  }

  private static final class Mp4Track {

    public final Track track;
    public final TrackSampleTable sampleTable;
    public final TrackOutput trackOutput;
    @Nullable public final TrueHdSampleRechunker trueHdSampleRechunker;

    public int sampleIndex;

    public Mp4Track(Track track, TrackSampleTable sampleTable, TrackOutput trackOutput) {
      this.track = track;
      this.sampleTable = sampleTable;
      this.trackOutput = trackOutput;
      trueHdSampleRechunker =
          MimeTypes.AUDIO_TRUEHD.equals(track.format.sampleMimeType)
              ? new TrueHdSampleRechunker()
              : null;
    }
  }
}
