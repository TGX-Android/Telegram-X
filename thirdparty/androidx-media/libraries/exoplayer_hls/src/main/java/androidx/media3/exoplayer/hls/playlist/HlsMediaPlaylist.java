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
package androidx.media3.exoplayer.hls.playlist;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.net.Uri;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.media3.common.C;
import androidx.media3.common.DrmInitData;
import androidx.media3.common.StreamKey;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Represents an HLS media playlist. */
@UnstableApi
public final class HlsMediaPlaylist extends HlsPlaylist {

  /** Server control attributes. */
  public static final class ServerControl {

    /**
     * The skip boundary for delta updates in microseconds, or {@link C#TIME_UNSET} if delta updates
     * are not supported.
     */
    public final long skipUntilUs;

    /**
     * Whether the playlist can produce delta updates that skip older #EXT-X-DATERANGE tags in
     * addition to media segments.
     */
    public final boolean canSkipDateRanges;

    /**
     * The server-recommended live offset in microseconds, or {@link C#TIME_UNSET} if none defined.
     */
    public final long holdBackUs;

    /**
     * The server-recommended live offset in microseconds in low-latency mode, or {@link
     * C#TIME_UNSET} if none defined.
     */
    public final long partHoldBackUs;

    /** Whether the server supports blocking playlist reload. */
    public final boolean canBlockReload;

    /**
     * Creates a new instance.
     *
     * @param skipUntilUs See {@link #skipUntilUs}.
     * @param canSkipDateRanges See {@link #canSkipDateRanges}.
     * @param holdBackUs See {@link #holdBackUs}.
     * @param partHoldBackUs See {@link #partHoldBackUs}.
     * @param canBlockReload See {@link #canBlockReload}.
     */
    public ServerControl(
        long skipUntilUs,
        boolean canSkipDateRanges,
        long holdBackUs,
        long partHoldBackUs,
        boolean canBlockReload) {
      this.skipUntilUs = skipUntilUs;
      this.canSkipDateRanges = canSkipDateRanges;
      this.holdBackUs = holdBackUs;
      this.partHoldBackUs = partHoldBackUs;
      this.canBlockReload = canBlockReload;
    }
  }

  /** Media segment reference. */
  @SuppressWarnings("ComparableType")
  public static final class Segment extends SegmentBase {

    /** The human readable title of the segment. */
    public final String title;

    /** The parts belonging to this segment. */
    public final List<Part> parts;

    /**
     * Creates an instance to be used as init segment.
     *
     * @param uri See {@link #url}.
     * @param byteRangeOffset See {@link #byteRangeOffset}.
     * @param byteRangeLength See {@link #byteRangeLength}.
     * @param fullSegmentEncryptionKeyUri See {@link #fullSegmentEncryptionKeyUri}.
     * @param encryptionIV See {@link #encryptionIV}.
     */
    public Segment(
        String uri,
        long byteRangeOffset,
        long byteRangeLength,
        @Nullable String fullSegmentEncryptionKeyUri,
        @Nullable String encryptionIV) {
      this(
          uri,
          /* initializationSegment= */ null,
          /* title= */ "",
          /* durationUs= */ 0,
          /* relativeDiscontinuitySequence= */ -1,
          /* relativeStartTimeUs= */ C.TIME_UNSET,
          /* drmInitData= */ null,
          fullSegmentEncryptionKeyUri,
          encryptionIV,
          byteRangeOffset,
          byteRangeLength,
          /* hasGapTag= */ false,
          /* parts= */ ImmutableList.of());
    }

    /**
     * Creates an instance.
     *
     * @param url See {@link #url}.
     * @param initializationSegment See {@link #initializationSegment}.
     * @param title See {@link #title}.
     * @param durationUs See {@link #durationUs}.
     * @param relativeDiscontinuitySequence See {@link #relativeDiscontinuitySequence}.
     * @param relativeStartTimeUs See {@link #relativeStartTimeUs}.
     * @param drmInitData See {@link #drmInitData}.
     * @param fullSegmentEncryptionKeyUri See {@link #fullSegmentEncryptionKeyUri}.
     * @param encryptionIV See {@link #encryptionIV}.
     * @param byteRangeOffset See {@link #byteRangeOffset}.
     * @param byteRangeLength See {@link #byteRangeLength}.
     * @param hasGapTag See {@link #hasGapTag}.
     * @param parts See {@link #parts}.
     */
    public Segment(
        String url,
        @Nullable Segment initializationSegment,
        String title,
        long durationUs,
        int relativeDiscontinuitySequence,
        long relativeStartTimeUs,
        @Nullable DrmInitData drmInitData,
        @Nullable String fullSegmentEncryptionKeyUri,
        @Nullable String encryptionIV,
        long byteRangeOffset,
        long byteRangeLength,
        boolean hasGapTag,
        List<Part> parts) {
      super(
          url,
          initializationSegment,
          durationUs,
          relativeDiscontinuitySequence,
          relativeStartTimeUs,
          drmInitData,
          fullSegmentEncryptionKeyUri,
          encryptionIV,
          byteRangeOffset,
          byteRangeLength,
          hasGapTag);
      this.title = title;
      this.parts = ImmutableList.copyOf(parts);
    }

    public Segment copyWith(long relativeStartTimeUs, int relativeDiscontinuitySequence) {
      List<Part> updatedParts = new ArrayList<>();
      long relativePartStartTimeUs = relativeStartTimeUs;
      for (int i = 0; i < parts.size(); i++) {
        Part part = parts.get(i);
        updatedParts.add(part.copyWith(relativePartStartTimeUs, relativeDiscontinuitySequence));
        relativePartStartTimeUs += part.durationUs;
      }
      return new Segment(
          url,
          initializationSegment,
          title,
          durationUs,
          relativeDiscontinuitySequence,
          relativeStartTimeUs,
          drmInitData,
          fullSegmentEncryptionKeyUri,
          encryptionIV,
          byteRangeOffset,
          byteRangeLength,
          hasGapTag,
          updatedParts);
    }
  }

  /** A media part. */
  public static final class Part extends SegmentBase {

    /** Whether the part is independent. */
    public final boolean isIndependent;

    /** Whether the part is a preloading part. */
    public final boolean isPreload;

    /**
     * Creates an instance.
     *
     * @param url See {@link #url}.
     * @param initializationSegment See {@link #initializationSegment}.
     * @param durationUs See {@link #durationUs}.
     * @param relativeDiscontinuitySequence See {@link #relativeDiscontinuitySequence}.
     * @param relativeStartTimeUs See {@link #relativeStartTimeUs}.
     * @param drmInitData See {@link #drmInitData}.
     * @param fullSegmentEncryptionKeyUri See {@link #fullSegmentEncryptionKeyUri}.
     * @param encryptionIV See {@link #encryptionIV}.
     * @param byteRangeOffset See {@link #byteRangeOffset}.
     * @param byteRangeLength See {@link #byteRangeLength}.
     * @param hasGapTag See {@link #hasGapTag}.
     * @param isIndependent See {@link #isIndependent}.
     * @param isPreload See {@link #isPreload}.
     */
    public Part(
        String url,
        @Nullable Segment initializationSegment,
        long durationUs,
        int relativeDiscontinuitySequence,
        long relativeStartTimeUs,
        @Nullable DrmInitData drmInitData,
        @Nullable String fullSegmentEncryptionKeyUri,
        @Nullable String encryptionIV,
        long byteRangeOffset,
        long byteRangeLength,
        boolean hasGapTag,
        boolean isIndependent,
        boolean isPreload) {
      super(
          url,
          initializationSegment,
          durationUs,
          relativeDiscontinuitySequence,
          relativeStartTimeUs,
          drmInitData,
          fullSegmentEncryptionKeyUri,
          encryptionIV,
          byteRangeOffset,
          byteRangeLength,
          hasGapTag);
      this.isIndependent = isIndependent;
      this.isPreload = isPreload;
    }

    public Part copyWith(long relativeStartTimeUs, int relativeDiscontinuitySequence) {
      return new Part(
          url,
          initializationSegment,
          durationUs,
          relativeDiscontinuitySequence,
          relativeStartTimeUs,
          drmInitData,
          fullSegmentEncryptionKeyUri,
          encryptionIV,
          byteRangeOffset,
          byteRangeLength,
          hasGapTag,
          isIndependent,
          isPreload);
    }
  }

  /** The base for a {@link Segment} or a {@link Part} required for playback. */
  @SuppressWarnings("ComparableType")
  public static class SegmentBase implements Comparable<Long> {
    /** The url of the segment. */
    public final String url;

    /**
     * The media initialization section for this segment, as defined by #EXT-X-MAP. May be null if
     * the media playlist does not define a media initialization section for this segment. The same
     * instance is used for all segments that share an EXT-X-MAP tag.
     */
    @Nullable public final Segment initializationSegment;

    /** The duration of the segment in microseconds, as defined by #EXTINF or #EXT-X-PART. */
    public final long durationUs;

    /** The number of #EXT-X-DISCONTINUITY tags in the playlist before the segment. */
    public final int relativeDiscontinuitySequence;

    /** The start time of the segment in microseconds, relative to the start of the playlist. */
    public final long relativeStartTimeUs;

    /**
     * DRM initialization data for sample decryption, or null if the segment does not use CDM-DRM
     * protection.
     */
    @Nullable public final DrmInitData drmInitData;

    /**
     * The encryption identity key uri as defined by #EXT-X-KEY, or null if the segment does not use
     * full segment encryption with identity key.
     */
    @Nullable public final String fullSegmentEncryptionKeyUri;

    /**
     * The encryption initialization vector as defined by #EXT-X-KEY, or null if the segment is not
     * encrypted.
     */
    @Nullable public final String encryptionIV;

    /**
     * The segment's byte range offset, as defined by #EXT-X-BYTERANGE, #EXT-X-PART or
     * #EXT-X-PRELOAD-HINT.
     */
    public final long byteRangeOffset;

    /**
     * The segment's byte range length, as defined by #EXT-X-BYTERANGE, #EXT-X-PART or
     * #EXT-X-PRELOAD-HINT, or {@link C#LENGTH_UNSET} if no byte range is specified or the byte
     * range is open-ended.
     */
    public final long byteRangeLength;

    /** Whether the segment is marked as a gap. */
    public final boolean hasGapTag;

    private SegmentBase(
        String url,
        @Nullable Segment initializationSegment,
        long durationUs,
        int relativeDiscontinuitySequence,
        long relativeStartTimeUs,
        @Nullable DrmInitData drmInitData,
        @Nullable String fullSegmentEncryptionKeyUri,
        @Nullable String encryptionIV,
        long byteRangeOffset,
        long byteRangeLength,
        boolean hasGapTag) {
      this.url = url;
      this.initializationSegment = initializationSegment;
      this.durationUs = durationUs;
      this.relativeDiscontinuitySequence = relativeDiscontinuitySequence;
      this.relativeStartTimeUs = relativeStartTimeUs;
      this.drmInitData = drmInitData;
      this.fullSegmentEncryptionKeyUri = fullSegmentEncryptionKeyUri;
      this.encryptionIV = encryptionIV;
      this.byteRangeOffset = byteRangeOffset;
      this.byteRangeLength = byteRangeLength;
      this.hasGapTag = hasGapTag;
    }

    @Override
    public int compareTo(Long relativeStartTimeUs) {
      return this.relativeStartTimeUs > relativeStartTimeUs
          ? 1
          : (this.relativeStartTimeUs < relativeStartTimeUs ? -1 : 0);
    }
  }

  /**
   * A rendition report for an alternative rendition defined in another media playlist.
   *
   * <p>See RFC 8216bis, section 4.4.5.1.4.
   */
  public static final class RenditionReport {
    /** The URI of the media playlist of the reported rendition. */
    public final Uri playlistUri;

    /** The last media sequence that is in the playlist of the reported rendition. */
    public final long lastMediaSequence;

    /**
     * The last part index that is in the playlist of the reported rendition, or {@link
     * C#INDEX_UNSET} if the rendition does not contain partial segments.
     */
    public final int lastPartIndex;

    /**
     * Creates a new instance.
     *
     * @param playlistUri See {@link #playlistUri}.
     * @param lastMediaSequence See {@link #lastMediaSequence}.
     * @param lastPartIndex See {@link #lastPartIndex}.
     */
    public RenditionReport(Uri playlistUri, long lastMediaSequence, int lastPartIndex) {
      this.playlistUri = playlistUri;
      this.lastMediaSequence = lastMediaSequence;
      this.lastPartIndex = lastPartIndex;
    }
  }

  /**
   * An interstitial data range.
   *
   * <p>See RFC 8216bis, appendix D.2.
   */
  public static final class Interstitial {

    /**
     * The cue trigger type. One of {@link #CUE_TRIGGER_PRE}, {@link #CUE_TRIGGER_POST} or {@link
     * #CUE_TRIGGER_ONCE}.
     *
     * <p>See RFC 8216bis, section 4.4.5.1.
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({CUE_TRIGGER_PRE, CUE_TRIGGER_POST, CUE_TRIGGER_ONCE})
    @Documented
    @Target(TYPE_USE)
    public @interface CueTriggerType {}

    /**
     * Cue trigger type indicating to trigger the interstitial before playback of the primary asset.
     */
    public static final String CUE_TRIGGER_PRE = "PRE";

    /**
     * Cue trigger type indicating to trigger the interstitial after playback of the primary asset.
     */
    public static final String CUE_TRIGGER_POST = "POST";

    /** Cue trigger type indicating to trigger the interstitial only once. */
    public static final String CUE_TRIGGER_ONCE = "ONCE";

    /**
     * The snap identifier. One of {@link #SNAP_TYPE_IN} or {@link #SNAP_TYPE_OUT}.
     *
     * <p>See RFC 8216bis, appendix D.2.
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({SNAP_TYPE_IN, SNAP_TYPE_OUT})
    @Documented
    @Target(TYPE_USE)
    public @interface SnapType {}

    /**
     * Snap identifier indicating to locate the segment boundary closest to the scheduled resumption
     * point of the interstitial.
     */
    public static final String SNAP_TYPE_IN = "IN";

    /**
     * Snap identifier indicating to locate the segment boundary closest to the {@link
     * Interstitial#startDateUnixUs}.
     */
    public static final String SNAP_TYPE_OUT = "OUT";

    /**
     * The navigation restriction identifier. One of {@link #NAVIGATION_RESTRICTION_JUMP} or {@link
     * #NAVIGATION_RESTRICTION_SKIP}.
     *
     * <p>See RFC 8216bis, appendix D.2.
     */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({NAVIGATION_RESTRICTION_SKIP, NAVIGATION_RESTRICTION_JUMP})
    @Documented
    @Target(TYPE_USE)
    public @interface NavigationRestriction {}

    /**
     * Navigation restriction identifier indicating to prevent seeking or changing the playback
     * speed during the interstitial being played.
     */
    public static final String NAVIGATION_RESTRICTION_SKIP = "SKIP";

    /**
     * Navigation restriction identifier indicating to enforce playback of the interstitial if the
     * user attempts to seek beyond the interstitial start position.
     */
    public static final String NAVIGATION_RESTRICTION_JUMP = "JUMP";

    /** The required ID. */
    public final String id;

    /** The asset URI. Required if {@link #assetListUri} is null. */
    @Nullable public final Uri assetUri;

    /** The asset list URI. Required if {@link #assetUri} is null. */
    @Nullable public final Uri assetListUri;

    /** The required start time, in microseconds. */
    public final long startDateUnixUs;

    /** The optional end time, in microseconds. {@link C#TIME_UNSET} if not present. */
    public final long endDateUnixUs;

    /** The optional duration, in microseconds. {@link C#TIME_UNSET} if not present. */
    public final long durationUs;

    /** The optional planned duration, in microseconds. {@link C#TIME_UNSET} if not present. */
    public final long plannedDurationUs;

    /** The trigger cue types. */
    public final List<@CueTriggerType String> cue;

    /**
     * Whether the {@link #endDateUnixUs} of the interstitial is equal to the start {@link
     * #startTimeUs} of the following interstitial. {@code false} if not present.
     */
    public final boolean endOnNext;

    /**
     * The offset from {@link #startTimeUs} indicating where in the primary asset to resume playback
     * after completing playback of the interstitial. {@link C#TIME_UNSET} if not present. If not
     * present, the value is considered to be the duration of the interstitial.
     */
    public final long resumeOffsetUs;

    /** The playout limit indicating the limit of the playback time of the interstitial. */
    public final long playoutLimitUs;

    /** The snap types. */
    public final ImmutableList<@SnapType String> snapTypes;

    /** The navigation restrictions. */
    public final ImmutableList<@NavigationRestriction String> restrictions;

    /** The attributes defined by a client. For informational purpose only. */
    public final ImmutableList<ClientDefinedAttribute> clientDefinedAttributes;

    /** Creates an instance. */
    public Interstitial(
        String id,
        @Nullable Uri assetUri,
        @Nullable Uri assetListUri,
        long startDateUnixUs,
        long endDateUnixUs,
        long durationUs,
        long plannedDurationUs,
        List<@CueTriggerType String> cue,
        boolean endOnNext,
        long resumeOffsetUs,
        long playoutLimitUs,
        List<@SnapType String> snapTypes,
        List<@NavigationRestriction String> restrictions,
        List<ClientDefinedAttribute> clientDefinedAttributes) {
      checkArgument(
          (assetUri == null || assetListUri == null) && (assetUri != null || assetListUri != null));
      this.id = id;
      this.assetUri = assetUri;
      this.assetListUri = assetListUri;
      this.startDateUnixUs = startDateUnixUs;
      this.endDateUnixUs = endDateUnixUs;
      this.durationUs = durationUs;
      this.plannedDurationUs = plannedDurationUs;
      this.cue = cue;
      this.endOnNext = endOnNext;
      this.resumeOffsetUs = resumeOffsetUs;
      this.playoutLimitUs = playoutLimitUs;
      this.snapTypes = ImmutableList.copyOf(snapTypes);
      this.restrictions = ImmutableList.copyOf(restrictions);
      this.clientDefinedAttributes = ImmutableList.copyOf(clientDefinedAttributes);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Interstitial)) {
        return false;
      }
      Interstitial that = (Interstitial) o;
      return startDateUnixUs == that.startDateUnixUs
          && endDateUnixUs == that.endDateUnixUs
          && durationUs == that.durationUs
          && plannedDurationUs == that.plannedDurationUs
          && endOnNext == that.endOnNext
          && resumeOffsetUs == that.resumeOffsetUs
          && playoutLimitUs == that.playoutLimitUs
          && Objects.equals(id, that.id)
          && Objects.equals(assetUri, that.assetUri)
          && Objects.equals(assetListUri, that.assetListUri)
          && Objects.equals(cue, that.cue)
          && Objects.equals(snapTypes, that.snapTypes)
          && Objects.equals(restrictions, that.restrictions)
          && Objects.equals(clientDefinedAttributes, that.clientDefinedAttributes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          id,
          assetUri,
          assetListUri,
          startDateUnixUs,
          endDateUnixUs,
          durationUs,
          plannedDurationUs,
          cue,
          endOnNext,
          resumeOffsetUs,
          playoutLimitUs,
          snapTypes,
          restrictions,
          clientDefinedAttributes);
    }
  }

  /** A client defined attribute. See RFC 8216bis, section 4.4.5.1. */
  public static class ClientDefinedAttribute {

    /**
     * The type of the client defined attribute. One of {@link #TYPE_TEXT}, {@link #TYPE_HEX_TEXT}
     * or {@link #TYPE_DOUBLE}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_DOUBLE, TYPE_HEX_TEXT, TYPE_TEXT})
    @Documented
    @Target(TYPE_USE)
    public @interface Type {}

    /** Type text. See RFC 8216bis, section 4.2, quoted-string. */
    public static final int TYPE_TEXT = 0;

    /** Type hex text. See RFC 8216bis, section 4.2, hexadecimal-sequence. */
    public static final int TYPE_HEX_TEXT = 1;

    /** Type double. See RFC 8216bis, section 4.2, decimal-floating-point. */
    public static final int TYPE_DOUBLE = 2;

    /** The name of the client defined attribute. */
    public final String name;

    /** The type of the client defined attribute. */
    public final int type;

    private final double doubleValue;
    @Nullable private final String textValue;

    /** Creates an instance of type {@link #TYPE_DOUBLE}. */
    public ClientDefinedAttribute(String name, double value) {
      this.name = name;
      this.type = TYPE_DOUBLE;
      this.doubleValue = value;
      textValue = null;
    }

    /** Creates an instance of type {@link #TYPE_TEXT} or {@link #TYPE_HEX_TEXT}. */
    public ClientDefinedAttribute(String name, String value, @Type int type) {
      checkState(type != TYPE_HEX_TEXT || value.startsWith("0x") || value.startsWith("0X"));
      this.name = name;
      this.type = type;
      this.textValue = value;
      doubleValue = 0.0d;
    }

    /**
     * Returns the value if the attribute is of {@link #TYPE_DOUBLE}.
     *
     * @throws IllegalStateException if the attribute is not of type {@link #TYPE_TEXT} or {@link
     *     #TYPE_HEX_TEXT}.
     */
    public double getDoubleValue() {
      checkState(type == TYPE_DOUBLE);
      return doubleValue;
    }

    /**
     * Returns the text value if the attribute is of {@link #TYPE_TEXT} or {@link #TYPE_HEX_TEXT}.
     *
     * @throws IllegalStateException if the attribute is not of type {@link #TYPE_DOUBLE}.
     */
    public String getTextValue() {
      checkState(type != TYPE_DOUBLE);
      return checkNotNull(textValue);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ClientDefinedAttribute)) {
        return false;
      }
      ClientDefinedAttribute that = (ClientDefinedAttribute) o;
      return type == that.type
          && Double.compare(doubleValue, that.doubleValue) == 0
          && Objects.equals(name, that.name)
          && Objects.equals(textValue, that.textValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, type, doubleValue, textValue);
    }
  }

  /**
   * Type of the playlist, as defined by #EXT-X-PLAYLIST-TYPE. One of {@link
   * #PLAYLIST_TYPE_UNKNOWN}, {@link #PLAYLIST_TYPE_VOD} or {@link #PLAYLIST_TYPE_EVENT}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({PLAYLIST_TYPE_UNKNOWN, PLAYLIST_TYPE_VOD, PLAYLIST_TYPE_EVENT})
  public @interface PlaylistType {}

  public static final int PLAYLIST_TYPE_UNKNOWN = 0;
  public static final int PLAYLIST_TYPE_VOD = 1;
  public static final int PLAYLIST_TYPE_EVENT = 2;

  /** The type of the playlist. See {@link PlaylistType}. */
  public final @PlaylistType int playlistType;

  /**
   * The start offset in microseconds from the beginning of the playlist, as defined by
   * #EXT-X-START, or {@link C#TIME_UNSET} if undefined. The value is guaranteed to be between 0 and
   * {@link #durationUs}, inclusive.
   */
  public final long startOffsetUs;

  /**
   * Whether the {@link #startOffsetUs} was explicitly defined by #EXT-X-START as a positive value
   * or zero.
   */
  public final boolean hasPositiveStartOffset;

  /** Whether the start position should be precise, as defined by #EXT-X-START. */
  public final boolean preciseStart;

  /**
   * If {@link #hasProgramDateTime} is true, contains the datetime as microseconds since epoch.
   * Otherwise, contains the aggregated duration of removed segments up to this snapshot of the
   * playlist.
   */
  public final long startTimeUs;

  /** Whether the playlist contains the #EXT-X-DISCONTINUITY-SEQUENCE tag. */
  public final boolean hasDiscontinuitySequence;

  /**
   * The discontinuity sequence number of the first media segment in the playlist, as defined by
   * #EXT-X-DISCONTINUITY-SEQUENCE.
   */
  public final int discontinuitySequence;

  /**
   * The media sequence number of the first media segment in the playlist, as defined by
   * #EXT-X-MEDIA-SEQUENCE.
   */
  public final long mediaSequence;

  /** The compatibility version, as defined by #EXT-X-VERSION. */
  public final int version;

  /** The target duration in microseconds, as defined by #EXT-X-TARGETDURATION. */
  public final long targetDurationUs;

  /**
   * The target duration for segment parts, as defined by #EXT-X-PART-INF, or {@link C#TIME_UNSET}
   * if undefined.
   */
  public final long partTargetDurationUs;

  /** Whether the playlist contains the #EXT-X-ENDLIST tag. */
  public final boolean hasEndTag;

  /** Whether the playlist contains a #EXT-X-PROGRAM-DATE-TIME tag. */
  public final boolean hasProgramDateTime;

  /**
   * Contains the CDM protection schemes used by segments in this playlist. Does not contain any key
   * acquisition data. Null if none of the segments in the playlist is CDM-encrypted.
   */
  @Nullable public final DrmInitData protectionSchemes;

  /** The list of segments in the playlist. */
  public final List<Segment> segments;

  /**
   * The list of parts at the end of the playlist for which the segment is not in the playlist yet.
   */
  public final List<Part> trailingParts;

  /** The rendition reports of alternative rendition playlists. */
  public final Map<Uri, RenditionReport> renditionReports;

  /** The total duration of the playlist in microseconds. */
  public final long durationUs;

  /** The attributes of the #EXT-X-SERVER-CONTROL header. */
  public final ServerControl serverControl;

  /**
   * The interstitials declared as {@code #EXT-X-DATERANGE} with {@code
   * CLASS="com.apple.hls.interstitial"}
   */
  public final ImmutableList<Interstitial> interstitials;

  /**
   * Constructs an instance.
   *
   * @param playlistType See {@link #playlistType}.
   * @param baseUri See {@link #baseUri}.
   * @param tags See {@link #tags}.
   * @param startOffsetUs See {@link #startOffsetUs}.
   * @param preciseStart See {@link #preciseStart}.
   * @param startTimeUs See {@link #startTimeUs}.
   * @param hasDiscontinuitySequence See {@link #hasDiscontinuitySequence}.
   * @param discontinuitySequence See {@link #discontinuitySequence}.
   * @param mediaSequence See {@link #mediaSequence}.
   * @param version See {@link #version}.
   * @param targetDurationUs See {@link #targetDurationUs}.
   * @param partTargetDurationUs See {@link #partTargetDurationUs}.
   * @param hasIndependentSegments See {@link #hasIndependentSegments}.
   * @param hasEndTag See {@link #hasEndTag}.
   * @param hasProgramDateTime See {@link #hasProgramDateTime}.
   * @param protectionSchemes See {@link #protectionSchemes}.
   * @param segments See {@link #segments}.
   * @param trailingParts See {@link #trailingParts}.
   * @param serverControl See {@link #serverControl}
   * @param renditionReports See {@link #renditionReports}.
   * @param interstitials See {@link #interstitials}.
   */
  public HlsMediaPlaylist(
      @PlaylistType int playlistType,
      String baseUri,
      List<String> tags,
      long startOffsetUs,
      boolean preciseStart,
      long startTimeUs,
      boolean hasDiscontinuitySequence,
      int discontinuitySequence,
      long mediaSequence,
      int version,
      long targetDurationUs,
      long partTargetDurationUs,
      boolean hasIndependentSegments,
      boolean hasEndTag,
      boolean hasProgramDateTime,
      @Nullable DrmInitData protectionSchemes,
      List<Segment> segments,
      List<Part> trailingParts,
      ServerControl serverControl,
      Map<Uri, RenditionReport> renditionReports,
      List<Interstitial> interstitials) {
    super(baseUri, tags, hasIndependentSegments);
    this.playlistType = playlistType;
    this.startTimeUs = startTimeUs;
    this.preciseStart = preciseStart;
    this.hasDiscontinuitySequence = hasDiscontinuitySequence;
    this.discontinuitySequence = discontinuitySequence;
    this.mediaSequence = mediaSequence;
    this.version = version;
    this.targetDurationUs = targetDurationUs;
    this.partTargetDurationUs = partTargetDurationUs;
    this.hasEndTag = hasEndTag;
    this.hasProgramDateTime = hasProgramDateTime;
    this.protectionSchemes = protectionSchemes;
    this.segments = ImmutableList.copyOf(segments);
    this.trailingParts = ImmutableList.copyOf(trailingParts);
    this.renditionReports = ImmutableMap.copyOf(renditionReports);
    this.interstitials = ImmutableList.copyOf(interstitials);
    if (!trailingParts.isEmpty()) {
      Part lastPart = Iterables.getLast(trailingParts);
      durationUs = lastPart.relativeStartTimeUs + lastPart.durationUs;
    } else if (!segments.isEmpty()) {
      Segment lastSegment = Iterables.getLast(segments);
      durationUs = lastSegment.relativeStartTimeUs + lastSegment.durationUs;
    } else {
      durationUs = 0;
    }
    // From RFC 8216bis, section 4.4.2.2: If startOffsetUs is negative, it indicates the offset from
    // the end of the playlist. If the absolute value exceeds the duration of the playlist, it
    // indicates the beginning (if negative) or the end (if positive) of the playlist.
    this.startOffsetUs =
        startOffsetUs == C.TIME_UNSET
            ? C.TIME_UNSET
            : startOffsetUs >= 0
                ? min(durationUs, startOffsetUs)
                : max(0, durationUs + startOffsetUs);
    this.hasPositiveStartOffset = startOffsetUs >= 0;
    this.serverControl = serverControl;
  }

  @Override
  public HlsMediaPlaylist copy(List<StreamKey> streamKeys) {
    return this;
  }

  /**
   * Returns whether this playlist is newer than {@code other}.
   *
   * @param other The playlist to compare.
   * @return Whether this playlist is newer than {@code other}.
   */
  public boolean isNewerThan(@Nullable HlsMediaPlaylist other) {
    if (other == null || mediaSequence > other.mediaSequence) {
      return true;
    }
    if (mediaSequence < other.mediaSequence) {
      return false;
    }
    // The media sequences are equal.
    int segmentCountDifference = segments.size() - other.segments.size();
    if (segmentCountDifference != 0) {
      return segmentCountDifference > 0;
    }
    int partCount = trailingParts.size();
    int otherPartCount = other.trailingParts.size();
    return partCount > otherPartCount
        || (partCount == otherPartCount && hasEndTag && !other.hasEndTag);
  }

  /** Returns the result of adding the duration of the playlist to its start time. */
  public long getEndTimeUs() {
    return startTimeUs + durationUs;
  }

  /**
   * Returns a playlist identical to this one except for the start time, the discontinuity sequence
   * and {@code hasDiscontinuitySequence} values. The first two are set to the specified values,
   * {@code hasDiscontinuitySequence} is set to true.
   *
   * @param startTimeUs The start time for the returned playlist.
   * @param discontinuitySequence The discontinuity sequence for the returned playlist.
   * @return An identical playlist including the provided discontinuity and timing information.
   */
  public HlsMediaPlaylist copyWith(long startTimeUs, int discontinuitySequence) {
    return new HlsMediaPlaylist(
        playlistType,
        baseUri,
        tags,
        startOffsetUs,
        preciseStart,
        startTimeUs,
        /* hasDiscontinuitySequence= */ true,
        discontinuitySequence,
        mediaSequence,
        version,
        targetDurationUs,
        partTargetDurationUs,
        hasIndependentSegments,
        hasEndTag,
        hasProgramDateTime,
        protectionSchemes,
        segments,
        trailingParts,
        serverControl,
        renditionReports,
        interstitials);
  }

  /**
   * Returns a playlist identical to this one except that an end tag is added. If an end tag is
   * already present then the playlist will return itself.
   */
  public HlsMediaPlaylist copyWithEndTag() {
    if (this.hasEndTag) {
      return this;
    }
    return new HlsMediaPlaylist(
        playlistType,
        baseUri,
        tags,
        startOffsetUs,
        preciseStart,
        startTimeUs,
        hasDiscontinuitySequence,
        discontinuitySequence,
        mediaSequence,
        version,
        targetDurationUs,
        partTargetDurationUs,
        hasIndependentSegments,
        /* hasEndTag= */ true,
        hasProgramDateTime,
        protectionSchemes,
        segments,
        trailingParts,
        serverControl,
        renditionReports,
        interstitials);
  }
}
