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
package androidx.media3.exoplayer;

import static androidx.annotation.VisibleForTesting.NONE;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.exoplayer.source.SampleStream.FLAG_PEEK;
import static androidx.media3.exoplayer.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static java.lang.Math.max;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.metrics.LogSessionId;
import android.net.Uri;
import android.os.PersistableBundle;
import android.util.SparseArray;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.Format;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceUtil;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.FileDescriptorDataSource;
import androidx.media3.datasource.MediaDataSourceAdapter;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.source.SampleQueue;
import androidx.media3.exoplayer.source.SampleStream.ReadDataResult;
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.extractor.DefaultExtractorInput;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.DiscardingTrackOutput;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.Extractor.ReadResult;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekMap.SeekPoints;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.mp4.PsshAtomUtil;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;

/**
 * A drop-in replacement for {@link MediaExtractor} that provides similar functionality, based on
 * the {@code media3.extractor} logic.
 */
@UnstableApi
public final class MediaExtractorCompat {

  /**
   * The seeking mode. One of {@link #SEEK_TO_PREVIOUS_SYNC}, {@link #SEEK_TO_NEXT_SYNC}, or {@link
   * #SEEK_TO_CLOSEST_SYNC}.
   */
  @IntDef({
    SEEK_TO_PREVIOUS_SYNC,
    SEEK_TO_NEXT_SYNC,
    SEEK_TO_CLOSEST_SYNC,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface SeekMode {}

  /** See {@link MediaExtractor#SEEK_TO_PREVIOUS_SYNC}. */
  public static final int SEEK_TO_PREVIOUS_SYNC = MediaExtractor.SEEK_TO_PREVIOUS_SYNC;

  /** See {@link MediaExtractor#SEEK_TO_NEXT_SYNC}. */
  public static final int SEEK_TO_NEXT_SYNC = MediaExtractor.SEEK_TO_NEXT_SYNC;

  /** See {@link MediaExtractor#SEEK_TO_CLOSEST_SYNC}. */
  public static final int SEEK_TO_CLOSEST_SYNC = MediaExtractor.SEEK_TO_CLOSEST_SYNC;

  /**
   * A default duration added to the largest queued sample timestamp to provide a more realistic
   * estimate of the cached duration. Since the duration of the last sample is unknown, this value
   * prevents the duration of the last sample from being assumed as zero, which would otherwise make
   * the estimated duration appear shorter than it actually is.
   */
  private static final long DEFAULT_LAST_SAMPLE_DURATION_US = 10_000;

  private static final String TAG = "MediaExtractorCompat";

  private final ExtractorsFactory extractorsFactory;
  private final DataSource.Factory dataSourceFactory;
  private final PositionHolder positionHolder;
  private final Allocator allocator;
  private final ArrayList<MediaExtractorTrack> tracks;
  private final SparseArray<MediaExtractorSampleQueue> sampleQueues;
  private final SampleMetadataQueue sampleMetadataQueue;
  private final FormatHolder formatHolder;
  private final DecoderInputBuffer sampleHolderWithBufferReplacementDisabled;
  private final DecoderInputBuffer sampleHolderWithBufferReplacementEnabled;
  private final Set<Integer> selectedTrackIndices;

  private boolean hasBeenPrepared;
  private long offsetInCurrentFile;
  @Nullable private Extractor currentExtractor;
  @Nullable private ExtractorInput currentExtractorInput;
  @Nullable private DataSource currentDataSource;
  @Nullable private SeekPoint pendingSeek;

  @Nullable private SeekMap seekMap;
  private boolean tracksEnded;
  private int upstreamFormatsCount;
  @Nullable private Map<String, String> httpRequestHeaders;
  @Nullable private LogSessionId logSessionId;

  /** Creates a new instance. */
  public MediaExtractorCompat(Context context) {
    this(new DefaultExtractorsFactory(), new DefaultDataSource.Factory(context));
  }

  /**
   * Creates a new instance using the given {@link ExtractorsFactory} to create the {@linkplain
   * Extractor extractors} to use for obtaining media samples from a {@link DataSource} generated by
   * the given {@link DataSource.Factory}.
   *
   * <p>Note: The {@link DataSource.Factory} provided will not be used to generate {@link
   * DataSource} when setting data source using methods:
   *
   * <ul>
   *   <li>{@link #setDataSource(AssetFileDescriptor)}
   *   <li>{@link #setDataSource(FileDescriptor)}
   *   <li>{@link #setDataSource(FileDescriptor, long, long)}
   * </ul>
   *
   * <p>Note: The {@link DataSource.Factory} provided may not be used to generate {@link DataSource}
   * when setting data source using method {@link #setDataSource(Context, Uri, Map)} as the behavior
   * depends on the fallthrough logic related to the scheme of the provided URI.
   */
  public MediaExtractorCompat(
      ExtractorsFactory extractorsFactory, DataSource.Factory dataSourceFactory) {
    this.extractorsFactory = extractorsFactory;
    this.dataSourceFactory = dataSourceFactory;
    positionHolder = new PositionHolder();
    allocator = new DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
    tracks = new ArrayList<>();
    sampleQueues = new SparseArray<>();
    sampleMetadataQueue = new SampleMetadataQueue();
    formatHolder = new FormatHolder();
    sampleHolderWithBufferReplacementDisabled = DecoderInputBuffer.newNoDataInstance();
    sampleHolderWithBufferReplacementEnabled =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    selectedTrackIndices = new HashSet<>();
  }

  /**
   * Sets the data source using the media stream obtained from the given {@link Uri} at the given
   * {@code offset}.
   *
   * @param uri The content {@link Uri} to extract from.
   * @param offset The offset into the file where the data to be extracted starts, in bytes.
   * @throws IOException If an error occurs while extracting the media.
   * @throws UnrecognizedInputFormatException If none of the available extractors successfully
   *     sniffs the input.
   * @throws IllegalStateException If this method is called twice on the same instance.
   */
  public void setDataSource(Uri uri, long offset) throws IOException {
    prepareDataSource(
        dataSourceFactory.createDataSource(), buildDataSpec(uri, /* position= */ offset));
  }

  /**
   * Sets the data source using the media stream obtained from the provided {@link
   * AssetFileDescriptor}.
   *
   * <p>Note: The caller is responsible for closing the {@link AssetFileDescriptor}. It is safe to
   * do so immediately after this method returns.
   *
   * @param assetFileDescriptor The {@link AssetFileDescriptor} for the file to extract from.
   * @throws IOException If an error occurs while extracting the media.
   * @throws UnrecognizedInputFormatException If none of the available extractors successfully
   *     sniffs the input.
   * @throws IllegalStateException If this method is called twice on the same instance.
   */
  public void setDataSource(AssetFileDescriptor assetFileDescriptor) throws IOException {
    // Using getDeclaredLength() to match platform behavior, even though it may result in reading
    // across multiple logical files when they share the same physical file.
    // Reference implementation in platform MediaExtractor:
    // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/media/java/android/media/MediaExtractor.java;l=219-226;drc=3181772b27c6bf3e9625677d1d8afc89d938a60e
    if (assetFileDescriptor.getDeclaredLength() == AssetFileDescriptor.UNKNOWN_LENGTH) {
      setDataSource(assetFileDescriptor.getFileDescriptor());
    } else {
      setDataSource(
          assetFileDescriptor.getFileDescriptor(),
          assetFileDescriptor.getStartOffset(),
          assetFileDescriptor.getDeclaredLength());
    }
  }

  /**
   * Sets the data source using the media stream obtained from the provided {@link FileDescriptor}.
   *
   * @param fileDescriptor The {@link FileDescriptor} for the file to extract from.
   * @throws IOException If an error occurs while extracting the media.
   * @throws UnrecognizedInputFormatException If none of the available extractors successfully
   *     sniffs the input.
   * @throws IllegalStateException If this method is called twice on the same instance.
   */
  public void setDataSource(FileDescriptor fileDescriptor) throws IOException {
    setDataSource(fileDescriptor, /* offset= */ 0, /* length= */ C.LENGTH_UNSET);
  }

  /**
   * Sets the data source using the media stream obtained from the provided {@link FileDescriptor},
   * with a specified {@code offset} and {@code length}.
   *
   * @param fileDescriptor The {@link FileDescriptor} for the file to extract from.
   * @param offset The offset into the file where the data to be extracted starts, in bytes.
   * @param length The length of the data to be extracted, in bytes, or {@link C#LENGTH_UNSET} if it
   *     is unknown.
   * @throws IOException If an error occurs while extracting the media.
   * @throws UnrecognizedInputFormatException If none of the available extractors successfully
   *     sniffs the input.
   * @throws IllegalStateException If this method is called twice on the same instance.
   */
  public void setDataSource(FileDescriptor fileDescriptor, long offset, long length)
      throws IOException {
    FileDescriptorDataSource fileDescriptorDataSource =
        new FileDescriptorDataSource(fileDescriptor, offset, length);
    prepareDataSource(fileDescriptorDataSource, buildDataSpec(Uri.EMPTY, /* position= */ 0));
  }

  /**
   * Sets the data source using the media stream obtained from the given {@linkplain Uri content
   * URI} and optional HTTP request headers.
   *
   * @param context The {@link Context} used to resolve the {@link Uri}.
   * @param uri The {@linkplain Uri content URI} of the media to extract from.
   * @param headers An optional {@link Map} of HTTP request headers to include when fetching the
   *     data, or {@code null} if no headers are to be included.
   * @throws IOException If an error occurs while extracting the media.
   * @throws UnrecognizedInputFormatException If none of the available extractors successfully
   *     sniffs the input.
   * @throws IllegalStateException If this method is called twice on the same instance.
   */
  public void setDataSource(Context context, Uri uri, @Nullable Map<String, String> headers)
      throws IOException {
    String scheme = uri.getScheme();
    String path = uri.getPath();
    if ((scheme == null || scheme.equals("file")) && path != null) {
      // If the URI scheme is null or file, treat it as a local file path
      setDataSource(path);
      return;
    }

    ContentResolver resolver = context.getContentResolver();
    try (AssetFileDescriptor assetFileDescriptor = resolver.openAssetFileDescriptor(uri, "r")) {
      if (assetFileDescriptor != null) {
        // If the URI points to a content provider resource, use the AssetFileDescriptor
        setDataSource(assetFileDescriptor);
        return;
      }
    } catch (SecurityException | FileNotFoundException e) {
      // Fall back to using the URI as a string if the file is not found or the mode is invalid
    }

    // Assume the URI is an HTTP URL and use it with optional headers
    setDataSource(uri.toString(), headers);
  }

  /**
   * Sets the data source using the media stream obtained from the given file path or HTTP URL.
   *
   * @param path The path of the file, or the HTTP URL, to extract media from.
   * @throws IOException If an error occurs while extracting the media.
   * @throws UnrecognizedInputFormatException If none of the available extractors successfully
   *     sniffs the input.
   * @throws IllegalStateException If this method is called twice on the same instance.
   */
  public void setDataSource(String path) throws IOException {
    setDataSource(path, /* headers= */ null);
  }

  /**
   * Sets the data source using the media stream obtained from the given file path or HTTP URL, with
   * optional HTTP request headers.
   *
   * @param path The path of the file, or the HTTP URL, to extract media from.
   * @param headers An optional {@link Map} of HTTP request headers to include when fetching the
   *     data, or {@code null} if no headers are to be included.
   * @throws IOException If an error occurs while extracting the media.
   * @throws UnrecognizedInputFormatException If none of the available extractors successfully
   *     sniffs the input.
   * @throws IllegalStateException If this method is called twice on the same instance.
   */
  public void setDataSource(String path, @Nullable Map<String, String> headers) throws IOException {
    httpRequestHeaders = headers;
    prepareDataSource(
        dataSourceFactory.createDataSource(), buildDataSpec(Uri.parse(path), /* position= */ 0));
  }

  /**
   * Sets the data source using the media stream obtained from the given {@link MediaDataSource}.
   *
   * @param mediaDataSource The {@link MediaDataSource} to extract media from.
   * @throws IOException If an error occurs while extracting the media.
   * @throws UnrecognizedInputFormatException If none of the available extractors successfully
   *     sniffs the input.
   * @throws IllegalStateException If this method is called twice on the same instance.
   */
  @RequiresApi(23)
  public void setDataSource(MediaDataSource mediaDataSource) throws IOException {
    // MediaDataSourceAdapter is created privately here, so TransferListeners cannot be registered.
    // Therefore, the isNetwork parameter is hardcoded to false as it has no effect.
    MediaDataSourceAdapter mediaDataSourceAdapter =
        new MediaDataSourceAdapter(mediaDataSource, /* isNetwork= */ false);
    prepareDataSource(mediaDataSourceAdapter, buildDataSpec(Uri.EMPTY, /* position= */ 0));
  }

  private void prepareDataSource(DataSource dataSource, DataSpec dataSpec) throws IOException {
    // Assert that this instance is not being re-prepared, which is not currently supported.
    Assertions.checkState(!hasBeenPrepared);
    hasBeenPrepared = true;
    offsetInCurrentFile = dataSpec.position;
    currentDataSource = dataSource;

    long length = currentDataSource.open(dataSpec);
    ExtractorInput currentExtractorInput =
        new DefaultExtractorInput(currentDataSource, /* position= */ 0, length);
    Extractor currentExtractor = selectExtractor(currentExtractorInput);
    currentExtractor.init(new ExtractorOutputImpl());

    boolean preparing = true;
    Throwable error = null;
    while (preparing) {
      int result;
      try {
        result = currentExtractor.read(currentExtractorInput, positionHolder);
      } catch (Exception | OutOfMemoryError e) {
        // This value is ignored but initializes result to avoid static analysis errors.
        result = Extractor.RESULT_END_OF_INPUT;
        error = e;
      }
      preparing = !tracksEnded || upstreamFormatsCount < sampleQueues.size() || seekMap == null;
      if (error != null || (preparing && result == Extractor.RESULT_END_OF_INPUT)) {
        // TODO(b/178501820): Support files with incomplete track information.
        release(); // Release resources as soon as possible, in case we are low on memory.
        String message =
            error != null
                ? "Exception encountered while parsing input media."
                : "Reached end of input before preparation completed.";
        throw ParserException.createForMalformedContainer(message, /* cause= */ error);
      } else if (result == Extractor.RESULT_SEEK) {
        currentExtractorInput = reopenCurrentDataSource(positionHolder.position);
      }
    }
    this.currentExtractorInput = currentExtractorInput;
    this.currentExtractor = currentExtractor;
    // At this point, we know how many tracks we have, and their format.
  }

  /**
   * Releases any resources held by this instance.
   *
   * <p>Note: Make sure you call this when you're done to free up any resources instead of relying
   * on the garbage collector to do this for you at some point in the future.
   */
  public void release() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).release();
    }
    sampleQueues.clear();
    if (currentExtractor != null) {
      currentExtractor.release();
      currentExtractor = null;
    }
    currentExtractorInput = null;
    pendingSeek = null;
    DataSourceUtil.closeQuietly(currentDataSource);
    currentDataSource = null;
  }

  /** Returns the number of tracks found in the data source. */
  public int getTrackCount() {
    return tracks.size();
  }

  /** Returns the track {@link MediaFormat} at the specified {@code trackIndex}. */
  public MediaFormat getTrackFormat(int trackIndex) {
    MediaExtractorTrack track = tracks.get(trackIndex);
    MediaFormat mediaFormat =
        track.createDownstreamMediaFormat(formatHolder, sampleHolderWithBufferReplacementDisabled);
    long trackDurationUs = track.sampleQueue.trackDurationUs;
    if (trackDurationUs != C.TIME_UNSET) {
      mediaFormat.setLong(MediaFormat.KEY_DURATION, trackDurationUs);
    } else if (seekMap != null && seekMap.getDurationUs() != C.TIME_UNSET) {
      mediaFormat.setLong(MediaFormat.KEY_DURATION, seekMap.getDurationUs());
    }
    return mediaFormat;
  }

  /**
   * Selects a track at the specified {@code trackIndex}.
   *
   * <p>Subsequent calls to {@link #readSampleData}, {@link #getSampleTrackIndex} and {@link
   * #getSampleTime} only retrieve information for the subset of tracks selected.
   *
   * <p>Note: Selecting the same track multiple times has no effect, the track is only selected
   * once.
   */
  public void selectTrack(int trackIndex) {
    selectedTrackIndices.add(trackIndex);
  }

  /**
   * Unselects the track at the specified {@code trackIndex}.
   *
   * <p>Subsequent calls to {@link #readSampleData}, {@link #getSampleTrackIndex} and {@link
   * #getSampleTime} only retrieve information for the subset of tracks selected.
   */
  public void unselectTrack(int trackIndex) {
    selectedTrackIndices.remove(trackIndex);
  }

  /**
   * All selected tracks seek near the requested {@code timeUs} according to the specified {@code
   * mode}.
   */
  public void seekTo(long timeUs, @SeekMode int mode) {
    if (seekMap == null) {
      return;
    }

    SeekPoints seekPoints;
    if (selectedTrackIndices.size() == 1 && currentExtractor instanceof Mp4Extractor) {
      // Mp4Extractor supports seeking within a specific track. This helps with poorly interleaved
      // tracks. See b/223910395.
      seekPoints =
          ((Mp4Extractor) currentExtractor)
              .getSeekPoints(
                  timeUs, tracks.get(selectedTrackIndices.iterator().next()).getIdOfBackingTrack());
    } else {
      seekPoints = seekMap.getSeekPoints(timeUs);
    }
    SeekPoint seekPoint;
    switch (mode) {
      case SEEK_TO_CLOSEST_SYNC:
        seekPoint =
            Math.abs(timeUs - seekPoints.second.timeUs) < Math.abs(timeUs - seekPoints.first.timeUs)
                ? seekPoints.second
                : seekPoints.first;
        break;
      case SEEK_TO_NEXT_SYNC:
        seekPoint = seekPoints.second;
        break;
      case SEEK_TO_PREVIOUS_SYNC:
        seekPoint = seekPoints.first;
        break;
      default:
        // Should never happen.
        throw new IllegalArgumentException();
    }
    sampleMetadataQueue.clear();
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).reset();
    }
    pendingSeek = seekPoint;
  }

  /**
   * Advances to the next sample. Returns {@code false} if no more sample data is available (i.e.,
   * end of stream), or {@code true} otherwise.
   *
   * <p>Note: When extracting from a local file, the behavior of {@link #advance} and {@link
   * #readSampleData} is undefined if there are concurrent writes to the same file. This may result
   * in an unexpected end of stream being signaled.
   */
  public boolean advance() {
    // Ensure there is a sample to discard.
    if (!advanceToSampleOrEndOfInput()) {
      // The end of input has been reached.
      return false;
    }
    skipOneSample();
    return advanceToSampleOrEndOfInput();
  }

  /**
   * Retrieves the current encoded sample and stores it in the byte {@code buffer} starting at the
   * given {@code offset}.
   *
   * <p><b>Note:</b>On success, the position and limit of {@code buffer} is updated to point to the
   * data just read.
   *
   * @param buffer the destination byte buffer.
   * @param offset The offset into the byte buffer at which to write.
   * @return the sample size, or -1 if no more samples are available.
   */
  public int readSampleData(ByteBuffer buffer, int offset) {
    if (!advanceToSampleOrEndOfInput()) {
      return -1;
    }
    // The platform media extractor implementation ignores the buffer's input position and limit.
    buffer.position(offset);
    buffer.limit(buffer.capacity());
    sampleHolderWithBufferReplacementDisabled.data = buffer;
    peekNextSelectedTrackSample(sampleHolderWithBufferReplacementDisabled);
    buffer.flip();
    buffer.position(offset);
    sampleHolderWithBufferReplacementDisabled.data = null;
    return buffer.remaining();
  }

  /**
   * Returns the track index the current sample originates from, or -1 if no more samples are
   * available.
   */
  public int getSampleTrackIndex() {
    if (!advanceToSampleOrEndOfInput()) {
      return -1;
    }
    return sampleMetadataQueue.peekFirst().trackIndex;
  }

  /** Returns the current sample's size in bytes, or -1 if no more samples are available. */
  public long getSampleSize() {
    if (!advanceToSampleOrEndOfInput()) {
      return -1;
    }
    peekNextSelectedTrackSample(sampleHolderWithBufferReplacementEnabled);
    ByteBuffer buffer = checkNotNull(sampleHolderWithBufferReplacementEnabled.data);
    int sampleSize = buffer.position();
    buffer.position(0);
    return sampleSize;
  }

  /**
   * Returns the current sample's presentation time in microseconds, or -1 if no more samples are
   * available.
   */
  public long getSampleTime() {
    if (!advanceToSampleOrEndOfInput()) {
      return -1;
    }
    return sampleMetadataQueue.peekFirst().timeUs;
  }

  /** Returns the current sample's flags. */
  public int getSampleFlags() {
    if (!advanceToSampleOrEndOfInput()) {
      return -1;
    }
    return sampleMetadataQueue.peekFirst().flags;
  }

  /**
   * Returns {@code true} if the current sample is at least partially encrypted and fills the
   * provided {@link MediaCodec.CryptoInfo} structure with relevant decryption information.
   *
   * @param info The {@link MediaCodec.CryptoInfo} structure to be filled with decryption data.
   * @return {@code true} if the sample is at least partially encrypted, {@code false} otherwise.
   */
  public boolean getSampleCryptoInfo(MediaCodec.CryptoInfo info) {
    if (!advanceToSampleOrEndOfInput()) {
      return false;
    }
    boolean isEncrypted =
        (sampleMetadataQueue.peekFirst().flags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0;
    if (!isEncrypted) {
      return false;
    }
    peekNextSelectedTrackSample(sampleHolderWithBufferReplacementEnabled);
    populatePlatformCryptoInfoParameters(info);
    return true;
  }

  private void populatePlatformCryptoInfoParameters(MediaCodec.CryptoInfo info) {
    MediaCodec.CryptoInfo platformCryptoInfo =
        checkNotNull(sampleHolderWithBufferReplacementEnabled.cryptoInfo).getFrameworkCryptoInfo();
    info.numSubSamples = platformCryptoInfo.numSubSamples;
    info.numBytesOfClearData = platformCryptoInfo.numBytesOfClearData;
    info.numBytesOfEncryptedData = platformCryptoInfo.numBytesOfEncryptedData;
    info.key = platformCryptoInfo.key;
    info.iv = platformCryptoInfo.iv;
    info.mode = platformCryptoInfo.mode;
  }

  /** Sets the {@link LogSessionId} for MediaExtractorCompat. */
  @RequiresApi(31)
  public void setLogSessionId(LogSessionId logSessionId) {
    if (!logSessionId.equals(LogSessionId.LOG_SESSION_ID_NONE)) {
      this.logSessionId = logSessionId;
    }
  }

  /** Returns the {@link LogSessionId} for MediaExtractorCompat. */
  @RequiresApi(31)
  public LogSessionId getLogSessionId() {
    return logSessionId != null ? logSessionId : LogSessionId.LOG_SESSION_ID_NONE;
  }

  /**
   * Extracts the DRM initialization data from the available tracks, if it exists.
   *
   * @return The {@link DrmInitData} in the content, or {@code null} if no recognizable DRM format
   *     is found.
   */
  @Nullable
  public DrmInitData getDrmInitData() {
    for (int i = 0; i < tracks.size(); i++) {
      Format format =
          tracks.get(i).getFormat(formatHolder, sampleHolderWithBufferReplacementDisabled);
      if (format.drmInitData == null) {
        continue;
      }
      return format.drmInitData;
    }
    return null;
  }

  /**
   * Returns an estimate of how much data is presently cached in memory, expressed in microseconds,
   * or -1 if this information is unavailable or not applicable (i.e., no cache exists).
   */
  public long getCachedDuration() {
    if (!advanceToSampleOrEndOfInput()) {
      return 0;
    }

    long largestReadTimestampUs = Long.MIN_VALUE;
    long largestQueuedTimestampUs = Long.MIN_VALUE;
    for (int i = 0; i < tracks.size(); i++) {
      MediaExtractorSampleQueue mediaExtractorSampleQueue = tracks.get(i).sampleQueue;

      largestReadTimestampUs =
          max(largestReadTimestampUs, mediaExtractorSampleQueue.getLargestReadTimestampUs());
      largestQueuedTimestampUs =
          max(largestQueuedTimestampUs, mediaExtractorSampleQueue.getLargestQueuedTimestampUs());
    }

    checkState(largestQueuedTimestampUs != Long.MIN_VALUE);
    if (largestReadTimestampUs == largestQueuedTimestampUs) {
      return 0;
    }

    if (largestReadTimestampUs == Long.MIN_VALUE) {
      largestReadTimestampUs = 0;
    }
    return largestQueuedTimestampUs - largestReadTimestampUs + DEFAULT_LAST_SAMPLE_DURATION_US;
  }

  /**
   * Returns {@code true} if data is being cached and the cache has reached the end of the data
   * stream. This indicates that no additional data is currently available for caching, although a
   * future seek may restart data fetching. This method only returns a meaningful result if {@link
   * #getCachedDuration} indicates the presence of a cache (i.e., does not return -1).
   */
  public boolean hasCacheReachedEndOfStream() {
    return getCachedDuration() == 0;
  }

  /**
   * Returns a {@link PersistableBundle} containing metrics data for the current media container.
   *
   * <p>The bundle includes attributes and values for the media container, as described in {@link
   * MediaExtractor.MetricsConstants}.
   */
  @RequiresApi(26)
  public PersistableBundle getMetrics() {
    PersistableBundle bundle = new PersistableBundle();
    if (currentExtractor != null) {
      bundle.putString(
          MediaExtractor.MetricsConstants.FORMAT,
          currentExtractor.getUnderlyingImplementation().getClass().getSimpleName());
    }
    if (!tracks.isEmpty()) {
      Format format =
          tracks.get(0).getFormat(formatHolder, sampleHolderWithBufferReplacementDisabled);
      if (format.containerMimeType != null) {
        bundle.putString(MediaExtractor.MetricsConstants.MIME_TYPE, format.containerMimeType);
      }
    }
    bundle.putInt(MediaExtractor.MetricsConstants.TRACKS, tracks.size());
    return bundle;
  }

  /**
   * Extracts PSSH data from the media, if present.
   *
   * @return A {@link Map} of UUID-to-byte[] pairs, where the {@link UUID} identifies the crypto
   *     scheme, and the byte array contains the scheme-specific data. Returns {@code null} if no
   *     PSSH data exists.
   */
  @Nullable
  public Map<UUID, byte[]> getPsshInfo() {
    @Nullable DrmInitData drmInitData = getDrmInitData();
    if (drmInitData == null) {
      return null;
    }

    Map<UUID, byte[]> psshDataMap = new HashMap<>();
    for (int i = 0; i < drmInitData.schemeDataCount; i++) {
      DrmInitData.SchemeData schemeData = drmInitData.get(i);
      if (schemeData.data == null) {
        continue;
      }

      @Nullable PsshAtomUtil.PsshAtom parsedPsshAtom = PsshAtomUtil.parsePsshAtom(schemeData.data);
      if (parsedPsshAtom != null) {
        psshDataMap.put(parsedPsshAtom.uuid, parsedPsshAtom.schemeData);
      }
    }

    return psshDataMap.isEmpty() ? null : psshDataMap;
  }

  @VisibleForTesting(otherwise = NONE)
  public Allocator getAllocator() {
    return allocator;
  }

  /**
   * Peeks a sample from the front of the given {@link SampleQueue}, discarding the downstream
   * {@link Format} first, if necessary.
   *
   * @param decoderInputBuffer The buffer to populate.
   * @throws IllegalStateException If a sample is not peeked as a result of calling this method.
   */
  private void peekNextSelectedTrackSample(DecoderInputBuffer decoderInputBuffer) {
    MediaExtractorTrack trackOfSample =
        tracks.get(checkNotNull(sampleMetadataQueue.peekFirst()).trackIndex);
    SampleQueue sampleQueue = trackOfSample.sampleQueue;
    @ReadDataResult
    int result =
        sampleQueue.read(formatHolder, decoderInputBuffer, FLAG_PEEK, /* loadingFinished= */ false);
    if (result == C.RESULT_FORMAT_READ) {
      // We've consumed a downstream format. Now consume the actual sample.
      result =
          sampleQueue.read(
              formatHolder, decoderInputBuffer, FLAG_PEEK, /* loadingFinished= */ false);
    }
    formatHolder.clear();
    // This method must only be called when there is a sample available for reading.
    checkState(result == C.RESULT_BUFFER_READ);
  }

  /**
   * Returns the extractor to use for extracting samples from the given {@code input}.
   *
   * @throws IOException If an error occurs while extracting the media.
   * @throws UnrecognizedInputFormatException If none of the available extractors successfully
   *     sniffs the input.
   */
  private Extractor selectExtractor(ExtractorInput input) throws IOException {
    Extractor[] extractors = extractorsFactory.createExtractors();
    Extractor result = null;
    for (Extractor extractor : extractors) {
      try {
        if (extractor.sniff(input)) {
          result = extractor;
          break;
        }
      } catch (EOFException e) {
        // We reached the end of input without recognizing the input format. Do nothing to let the
        // next extractor sniff the content.
      } finally {
        input.resetPeekPosition();
      }
    }
    if (result == null) {
      throw new UnrecognizedInputFormatException(
          "None of the available extractors ("
              + Joiner.on(", ")
                  .join(
                      Lists.transform(
                          ImmutableList.copyOf(extractors),
                          extractor ->
                              extractor.getUnderlyingImplementation().getClass().getSimpleName()))
              + ") could read the stream.",
          checkNotNull(checkNotNull(currentDataSource).getUri()),
          ImmutableList.of());
    }
    return result;
  }

  /**
   * Advances extraction until there is a queued sample from a selected track, or the end of the
   * input is found.
   *
   * <p>Handles I/O errors (for example, network connection loss) and parsing errors (for example, a
   * truncated file) in the same way as {@link MediaExtractor}, treating them as the end of input.
   *
   * @return Whether a sample from a selected track is available.
   */
  @EnsuresNonNullIf(expression = "sampleMetadataQueue.peekFirst()", result = true)
  private boolean advanceToSampleOrEndOfInput() {
    try {
      maybeResolvePendingSeek();
    } catch (IOException e) {
      Log.w(TAG, "Treating exception as the end of input.", e);
      return false;
    }

    boolean seenEndOfInput = false;
    while (true) {
      if (!sampleMetadataQueue.isEmpty()) {
        // By default, tracks are unselected.
        if (selectedTrackIndices.contains(
            checkNotNull(sampleMetadataQueue.peekFirst()).trackIndex)) {
          return true;
        } else {
          // There is a queued sample, but its track is unselected. We skip the sample.
          skipOneSample();
        }
      } else if (!seenEndOfInput) {
        // There are no queued samples for the selected tracks, but we can feed more data to the
        // extractor and see if more samples are produced.
        try {
          @ReadResult
          int result =
              checkNotNull(currentExtractor)
                  .read(checkNotNull(currentExtractorInput), positionHolder);
          if (result == Extractor.RESULT_END_OF_INPUT) {
            seenEndOfInput = true;
          } else if (result == Extractor.RESULT_SEEK) {
            this.currentExtractorInput = reopenCurrentDataSource(positionHolder.position);
          }
        } catch (Exception | OutOfMemoryError e) {
          Log.w(TAG, "Treating exception as the end of input.", e);
          seenEndOfInput = true;
        }
      } else {
        // No queued samples for selected tracks, and we've parsed all the file. Nothing else to do.
        return false;
      }
    }
  }

  private void skipOneSample() {
    int trackIndex = sampleMetadataQueue.removeFirst().trackIndex;
    MediaExtractorTrack track = tracks.get(trackIndex);
    if (!track.isCompatibilityTrack) {
      // We also need to skip the sample data.
      track.discardFrontSample();
    }
  }

  private ExtractorInput reopenCurrentDataSource(long newPositionInStream) throws IOException {
    DataSource currentDataSource = checkNotNull(this.currentDataSource);
    Uri currentUri = checkNotNull(currentDataSource.getUri());
    DataSourceUtil.closeQuietly(currentDataSource);
    long length =
        currentDataSource.open(
            buildDataSpec(currentUri, offsetInCurrentFile + newPositionInStream));
    if (length != C.LENGTH_UNSET) {
      length += newPositionInStream;
    }
    return new DefaultExtractorInput(currentDataSource, newPositionInStream, length);
  }

  private void onSampleQueueFormatInitialized(
      MediaExtractorSampleQueue mediaExtractorSampleQueue, Format newUpstreamFormat) {
    upstreamFormatsCount++;
    mediaExtractorSampleQueue.setMainTrackIndex(tracks.size());
    tracks.add(
        new MediaExtractorTrack(
            mediaExtractorSampleQueue,
            /* isCompatibilityTrack= */ false,
            /* compatibilityTrackMimeType= */ null));
    @Nullable
    String compatibilityTrackMimeType =
        MediaCodecUtil.getAlternativeCodecMimeType(newUpstreamFormat);
    if (compatibilityTrackMimeType != null) {
      mediaExtractorSampleQueue.setCompatibilityTrackIndex(tracks.size());
      tracks.add(
          new MediaExtractorTrack(
              mediaExtractorSampleQueue,
              /* isCompatibilityTrack= */ true,
              compatibilityTrackMimeType));
    }
  }

  private void maybeResolvePendingSeek() throws IOException {
    if (this.pendingSeek == null) {
      return; // Nothing to do.
    }
    SeekPoint pendingSeek = checkNotNull(this.pendingSeek);
    checkNotNull(currentExtractor).seek(pendingSeek.position, pendingSeek.timeUs);
    this.currentExtractorInput = reopenCurrentDataSource(pendingSeek.position);
    this.pendingSeek = null;
  }

  /**
   * Create a new {@link DataSpec} with the given data.
   *
   * <p>The created {@link DataSpec} disables caching if the content length cannot be resolved,
   * since this is indicative of a progressive live stream.
   */
  private DataSpec buildDataSpec(Uri uri, long position) {
    DataSpec.Builder dataSpec =
        new DataSpec.Builder()
            .setUri(uri)
            .setPosition(position)
            .setFlags(
                DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN
                    | DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION);
    if (httpRequestHeaders != null) {
      dataSpec.setHttpRequestHeaders(httpRequestHeaders);
    }
    return dataSpec.build();
  }

  private final class ExtractorOutputImpl implements ExtractorOutput {

    @Override
    public TrackOutput track(int id, int type) {
      MediaExtractorSampleQueue sampleQueue = sampleQueues.get(id);
      if (sampleQueue != null) {
        // This track has already been declared. We return the sample queue that corresponds to this
        // id.
        return sampleQueue;
      }
      if (tracksEnded) {
        // The id is new and the extractor has ended the tracks. Discard.
        return new DiscardingTrackOutput();
      }

      sampleQueue = new MediaExtractorSampleQueue(allocator, id);
      sampleQueues.put(id, sampleQueue);
      return sampleQueue;
    }

    @Override
    public void endTracks() {
      tracksEnded = true;
    }

    @Override
    public void seekMap(SeekMap seekMap) {
      MediaExtractorCompat.this.seekMap = seekMap;
    }
  }

  private static final class MediaExtractorTrack {

    public final MediaExtractorSampleQueue sampleQueue;
    public final boolean isCompatibilityTrack;
    @Nullable public final String compatibilityTrackMimeType;

    private MediaExtractorTrack(
        MediaExtractorSampleQueue sampleQueue,
        boolean isCompatibilityTrack,
        @Nullable String compatibilityTrackMimeType) {
      this.sampleQueue = sampleQueue;
      this.isCompatibilityTrack = isCompatibilityTrack;
      this.compatibilityTrackMimeType = compatibilityTrackMimeType;
    }

    public MediaFormat createDownstreamMediaFormat(
        FormatHolder scratchFormatHolder, DecoderInputBuffer scratchNoDataDecoderInputBuffer) {
      Format format = getFormat(scratchFormatHolder, scratchNoDataDecoderInputBuffer);
      MediaFormat mediaFormatResult = MediaFormatUtil.createMediaFormatFromFormat(format);
      if (compatibilityTrackMimeType != null) {
        if (Util.SDK_INT >= 29) {
          mediaFormatResult.removeKey(MediaFormat.KEY_CODECS_STRING);
        }
        mediaFormatResult.setString(MediaFormat.KEY_MIME, compatibilityTrackMimeType);
      }
      return mediaFormatResult;
    }

    private Format getFormat(
        FormatHolder scratchFormatHolder, DecoderInputBuffer scratchNoDataDecoderInputBuffer) {
      scratchFormatHolder.clear();
      sampleQueue.read(
          scratchFormatHolder,
          scratchNoDataDecoderInputBuffer,
          FLAG_REQUIRE_FORMAT,
          /* loadingFinished= */ false);
      Format format = checkNotNull(scratchFormatHolder.format);
      scratchFormatHolder.clear();
      return format;
    }

    public void discardFrontSample() {
      sampleQueue.skip(/* count= */ 1);
      sampleQueue.discardToRead();
    }

    public int getIdOfBackingTrack() {
      return sampleQueue.trackId;
    }

    @Override
    public String toString() {
      return String.format(
          "MediaExtractorSampleQueue: %s, isCompatibilityTrack: %s, compatibilityTrackMimeType: %s",
          sampleQueue, isCompatibilityTrack, compatibilityTrackMimeType);
    }
  }

  private final class MediaExtractorSampleQueue extends SampleQueue {

    public final int trackId;
    public long trackDurationUs;
    private int mainTrackIndex;
    private int compatibilityTrackIndex;

    public MediaExtractorSampleQueue(Allocator allocator, int trackId) {
      // We do not need the sample queue to acquire keys for encrypted samples, so we pass null
      // values for DRM-related arguments.
      super(allocator, /* drmSessionManager= */ null, /* drmEventDispatcher= */ null);
      this.trackId = trackId;
      trackDurationUs = C.TIME_UNSET;
      mainTrackIndex = C.INDEX_UNSET;
      compatibilityTrackIndex = C.INDEX_UNSET;
    }

    public void setMainTrackIndex(int mainTrackIndex) {
      this.mainTrackIndex = mainTrackIndex;
    }

    public void setCompatibilityTrackIndex(int compatibilityTrackIndex) {
      this.compatibilityTrackIndex = compatibilityTrackIndex;
    }

    // SampleQueue implementation.

    @Override
    public void durationUs(long durationUs) {
      this.trackDurationUs = durationUs;
      super.durationUs(durationUs);
    }

    @Override
    public Format getAdjustedUpstreamFormat(Format format) {
      if (getUpstreamFormat() == null) {
        onSampleQueueFormatInitialized(this, format);
      }
      return super.getAdjustedUpstreamFormat(format);
    }

    @Override
    public void sampleMetadata(
        long timeUs,
        @C.BufferFlags int flags,
        int size,
        int offset,
        @Nullable CryptoData cryptoData) {
      // Disable BUFFER_FLAG_LAST_SAMPLE to prevent the sample queue from ignoring
      // FLAG_REQUIRE_FORMAT. See b/191518632.
      flags &= ~C.BUFFER_FLAG_LAST_SAMPLE;
      Assertions.checkState(mainTrackIndex != C.INDEX_UNSET);
      int writeIndexBeforeCommitting = this.getWriteIndex();
      super.sampleMetadata(timeUs, flags, size, offset, cryptoData);
      // Add the sample metadata if the sample was committed
      if (this.getWriteIndex() == writeIndexBeforeCommitting + 1) {
        queueSampleMetadata(timeUs, flags);
      }
    }

    @Override
    public String toString() {
      return String.format(
          "trackId: %s, mainTrackIndex: %s, compatibilityTrackIndex: %s",
          trackId, mainTrackIndex, compatibilityTrackIndex);
    }

    private void queueSampleMetadata(long timeUs, @C.BufferFlags int flags) {
      int mediaExtractorFlags = 0;
      mediaExtractorFlags |=
          (flags & C.BUFFER_FLAG_ENCRYPTED) != 0 ? MediaExtractor.SAMPLE_FLAG_ENCRYPTED : 0;
      mediaExtractorFlags |=
          (flags & C.BUFFER_FLAG_KEY_FRAME) != 0 ? MediaExtractor.SAMPLE_FLAG_SYNC : 0;

      if (compatibilityTrackIndex != C.INDEX_UNSET) {
        sampleMetadataQueue.addLast(
            timeUs, /* flags= */ mediaExtractorFlags, compatibilityTrackIndex);
      }
      sampleMetadataQueue.addLast(timeUs, /* flags= */ mediaExtractorFlags, mainTrackIndex);
    }
  }

  /**
   * A queue for managing {@link SampleMetadata} instances in FIFO order with an internal pool for
   * recycling.
   */
  private static final class SampleMetadataQueue {

    private final ArrayDeque<SampleMetadata> sampleMetadataPool;
    private final ArrayDeque<SampleMetadata> sampleMetadataQueue;

    /** Creates an instance. */
    public SampleMetadataQueue() {
      sampleMetadataPool = new ArrayDeque<>();
      sampleMetadataQueue = new ArrayDeque<>();
    }

    /**
     * Adds a sample to the end of the queue, reusing a pooled {@link SampleMetadata} instance if
     * available.
     *
     * @param timeUs The media timestamp associated with the sample, in microseconds.
     * @param flags Flags associated with the sample. See {@code MediaExtractor.SAMPLE_FLAG_*}.
     * @param trackIndex Track index of the sample.
     */
    public void addLast(long timeUs, int flags, int trackIndex) {
      SampleMetadata metadata = obtainSampleMetadata(timeUs, flags, trackIndex);
      sampleMetadataQueue.addLast(metadata);
    }

    /**
     * Removes and returns the first {@link SampleMetadata} in the queue, releasing it back to the
     * pool.
     */
    public SampleMetadata removeFirst() {
      SampleMetadata metadata = sampleMetadataQueue.removeFirst();
      sampleMetadataPool.push(metadata);
      return metadata;
    }

    /**
     * Peeks at the first {@link SampleMetadata} in the queue without removing it.
     *
     * @return The first {@link SampleMetadata} in the queue, or {@code null} if empty.
     */
    @Nullable
    public SampleMetadata peekFirst() {
      return sampleMetadataQueue.peekFirst();
    }

    /** Clears the queue, releasing all {@link SampleMetadata} back to the pool. */
    public void clear() {
      for (SampleMetadata metadata : sampleMetadataQueue) {
        sampleMetadataPool.push(metadata);
      }
      sampleMetadataQueue.clear();
    }

    /** Returns whether the queue is empty. */
    public boolean isEmpty() {
      return sampleMetadataQueue.isEmpty();
    }

    private SampleMetadata obtainSampleMetadata(long timeUs, int flags, int trackIndex) {
      SampleMetadata metadata =
          sampleMetadataPool.isEmpty()
              ? new SampleMetadata(timeUs, flags, trackIndex)
              : sampleMetadataPool.pop();
      metadata.set(timeUs, flags, trackIndex);
      return metadata;
    }

    private static final class SampleMetadata {
      public int flags;
      public long timeUs;
      public int trackIndex;

      public SampleMetadata(long timeUs, int flags, int trackIndex) {
        set(timeUs, flags, trackIndex);
      }

      public void set(long timeUs, int flags, int trackIndex) {
        this.timeUs = timeUs;
        this.flags = flags;
        this.trackIndex = trackIndex;
      }
    }
  }
}
