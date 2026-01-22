/*
 * Copyright (C) 2020 The Android Open Source Project
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
package androidx.media3.test.utils.robolectric;

import static java.lang.Math.max;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import androidx.media3.common.HeartRating;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MediaMetadata.MediaType;
import androidx.media3.common.MediaMetadata.PictureType;
import androidx.media3.common.Metadata;
import androidx.media3.common.PercentageRating;
import androidx.media3.common.Player;
import androidx.media3.common.Rating;
import androidx.media3.common.StarRating;
import androidx.media3.common.ThumbRating;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.extractor.metadata.dvbsi.AppInfoTable;
import androidx.media3.extractor.metadata.emsg.EventMessage;
import androidx.media3.extractor.metadata.flac.PictureFrame;
import androidx.media3.extractor.metadata.icy.IcyHeaders;
import androidx.media3.extractor.metadata.icy.IcyInfo;
import androidx.media3.extractor.metadata.id3.Id3Frame;
import androidx.media3.extractor.metadata.mp4.MotionPhotoMetadata;
import androidx.media3.extractor.metadata.mp4.SlowMotionData;
import androidx.media3.extractor.metadata.mp4.SmtaMetadataEntry;
import androidx.media3.extractor.metadata.scte35.SpliceCommand;
import androidx.media3.extractor.metadata.vorbis.VorbisComment;
import androidx.media3.test.utils.CapturingRenderersFactory;
import androidx.media3.test.utils.Dumper;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class to capture output from a playback test.
 *
 * <p>Implements {@link Dumper.Dumpable} so the output can be easily dumped to a string for
 * comparison against previous test runs.
 */
@UnstableApi
public final class PlaybackOutput implements Dumper.Dumpable {

  private final CapturingRenderersFactory capturingRenderersFactory;

  private final List<Metadata> metadatas;
  private final List<MediaMetadata> mediaMetadatas;
  private final List<CueGroup> subtitles;
  private final List<List<Cue>> subtitlesFromDeprecatedTextOutput;

  private PlaybackOutput(ExoPlayer player, CapturingRenderersFactory capturingRenderersFactory) {
    this.capturingRenderersFactory = capturingRenderersFactory;

    metadatas = Collections.synchronizedList(new ArrayList<>());
    mediaMetadatas = Collections.synchronizedList(new ArrayList<>());
    subtitles = Collections.synchronizedList(new ArrayList<>());
    subtitlesFromDeprecatedTextOutput = Collections.synchronizedList(new ArrayList<>());
    // TODO: Consider passing playback position into MetadataOutput. Calling
    // player.getCurrentPosition() inside onMetadata will likely be non-deterministic
    // because renderer-thread != playback-thread.
    player.addListener(
        new Player.Listener() {
          @Override
          public void onMetadata(Metadata metadata) {
            metadatas.add(metadata);
          }

          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            mediaMetadatas.add(mediaMetadata);
          }

          @SuppressWarnings("deprecation") // Intentionally testing deprecated output
          @Override
          public void onCues(List<Cue> cues) {
            subtitlesFromDeprecatedTextOutput.add(cues);
          }

          @Override
          public void onCues(CueGroup cueGroup) {
            subtitles.add(cueGroup);
          }
        });
  }

  /**
   * Create an instance that captures the metadata and text output from {@code player} and the audio
   * and video output via {@code capturingRenderersFactory}.
   *
   * <p>Must be called <b>before</b> playback to ensure metadata and text output is captured
   * correctly.
   *
   * @param player The {@link ExoPlayer} to capture metadata and text output from.
   * @param capturingRenderersFactory The {@link CapturingRenderersFactory} to capture audio and
   *     video output from.
   * @return A new instance that can be used to dump the playback output.
   */
  public static PlaybackOutput register(
      ExoPlayer player, CapturingRenderersFactory capturingRenderersFactory) {
    return new PlaybackOutput(player, capturingRenderersFactory);
  }

  @Override
  public void dump(Dumper dumper) {
    capturingRenderersFactory.dump(dumper);

    dumpMetadata(dumper);
    dumpMediaMetadata(dumper);
    dumpSubtitles(dumper);
  }

  private void dumpMetadata(Dumper dumper) {
    if (metadatas.isEmpty()) {
      return;
    }
    dumper.startBlock("MetadataOutput");
    for (int i = 0; i < metadatas.size(); i++) {
      dumper.startBlock("Metadata[" + i + "]");
      Metadata metadata = metadatas.get(i);
      dumper.addTime("presentationTimeUs", metadata.presentationTimeUs);
      for (int j = 0; j < metadata.length(); j++) {
        dumper.add("entry[" + j + "]", getEntryAsString(metadata.get(j)));
      }
      dumper.endBlock();
    }
    dumper.endBlock();
  }

  /**
   * Returns {@code entry.toString()} if we know the implementation overrides it, otherwise returns
   * the simple class name.
   */
  private static String getEntryAsString(Metadata.Entry entry) {
    if (entry instanceof EventMessage
        || entry instanceof PictureFrame
        || entry instanceof VorbisComment
        || entry instanceof Id3Frame
        || entry instanceof MdtaMetadataEntry
        || entry instanceof MotionPhotoMetadata
        || entry instanceof SlowMotionData
        || entry instanceof SmtaMetadataEntry
        || entry instanceof AppInfoTable
        || entry instanceof IcyHeaders
        || entry instanceof IcyInfo
        || entry instanceof SpliceCommand
        || "androidx.media3.exoplayer.hls.HlsTrackMetadataEntry"
            .equals(entry.getClass().getCanonicalName())) {
      return entry.toString();
    } else {
      return entry.getClass().getSimpleName();
    }
  }

  private void dumpMediaMetadata(Dumper dumper) {
    if (mediaMetadatas.isEmpty()) {
      return;
    }
    dumper.startBlock("Listener.onMediaMetadata");
    for (int i = 0; i < mediaMetadatas.size(); i++) {
      dumper.startBlock("MediaMetadata[" + i + "]");
      MediaMetadata mediaMetadata = mediaMetadatas.get(i);
      dumper.addIfNonDefault("title", mediaMetadata.title, null);
      dumper.addIfNonDefault("artist", mediaMetadata.artist, null);
      dumper.addIfNonDefault("albumTitle", mediaMetadata.albumTitle, null);
      dumper.addIfNonDefault("albumArtist", mediaMetadata.albumArtist, null);
      dumper.addIfNonDefault("displayTitle", mediaMetadata.displayTitle, null);
      dumper.addIfNonDefault("subtitle", mediaMetadata.subtitle, null);
      dumper.addIfNonDefault("description", mediaMetadata.description, null);
      dumper.addIfNonDefault("userRating", ratingString(mediaMetadata.userRating), null);
      dumper.addIfNonDefault("overallRating", ratingString(mediaMetadata.overallRating), null);
      dumper.addIfNonDefault("artworkData", mediaMetadata.artworkData, null);
      dumper.addIfNonDefault(
          "artworkDataType", pictureTypeString(mediaMetadata.artworkDataType), null);
      dumper.addIfNonDefault("artworkUri", mediaMetadata.artworkUri, null);
      dumper.addIfNonDefault("trackNumber", mediaMetadata.trackNumber, null);
      dumper.addIfNonDefault("totalTrackCount", mediaMetadata.totalTrackCount, null);
      dumper.addIfNonDefault("isBrowsable", mediaMetadata.isBrowsable, null);
      dumper.addIfNonDefault("isPlayable", mediaMetadata.isPlayable, null);
      dumper.addIfNonDefault("recordingYear", mediaMetadata.recordingYear, null);
      dumper.addIfNonDefault("recordingMonth", mediaMetadata.recordingMonth, null);
      dumper.addIfNonDefault("recordingDay", mediaMetadata.recordingDay, null);
      dumper.addIfNonDefault("releaseYear", mediaMetadata.releaseYear, null);
      dumper.addIfNonDefault("releaseMonth", mediaMetadata.releaseMonth, null);
      dumper.addIfNonDefault("releaseDay", mediaMetadata.releaseDay, null);
      dumper.addIfNonDefault("writer", mediaMetadata.writer, null);
      dumper.addIfNonDefault("composer", mediaMetadata.composer, null);
      dumper.addIfNonDefault("conductor", mediaMetadata.conductor, null);
      dumper.addIfNonDefault("discNumber", mediaMetadata.discNumber, null);
      dumper.addIfNonDefault("totalDiscCount", mediaMetadata.totalDiscCount, null);
      dumper.addIfNonDefault("genre", mediaMetadata.genre, null);
      dumper.addIfNonDefault("compilation", mediaMetadata.compilation, null);
      dumper.addIfNonDefault("station", mediaMetadata.station, null);
      dumper.addIfNonDefault("mediaType", mediaTypeString(mediaMetadata.mediaType), null);
      dumper.addIfNonDefault("extras", mediaMetadata.extras, null);
      dumper.endBlock();
    }
    dumper.endBlock();
  }

  @Nullable
  private static String pictureTypeString(@Nullable @PictureType Integer pictureType) {
    if (pictureType == null) {
      return null;
    }
    switch (pictureType) {
      case MediaMetadata.PICTURE_TYPE_OTHER:
        return "other";
      case MediaMetadata.PICTURE_TYPE_FILE_ICON:
        return "file icon";
      case MediaMetadata.PICTURE_TYPE_FILE_ICON_OTHER:
        return "file icon (other)";
      case MediaMetadata.PICTURE_TYPE_FRONT_COVER:
        return "front cover";
      case MediaMetadata.PICTURE_TYPE_BACK_COVER:
        return "back cover";
      case MediaMetadata.PICTURE_TYPE_LEAFLET_PAGE:
        return "leaflet page";
      case MediaMetadata.PICTURE_TYPE_MEDIA:
        return "media";
      case MediaMetadata.PICTURE_TYPE_LEAD_ARTIST_PERFORMER:
        return "lead performer";
      case MediaMetadata.PICTURE_TYPE_ARTIST_PERFORMER:
        return "performer";
      case MediaMetadata.PICTURE_TYPE_CONDUCTOR:
        return "conductor";
      case MediaMetadata.PICTURE_TYPE_BAND_ORCHESTRA:
        return "orchestra";
      case MediaMetadata.PICTURE_TYPE_COMPOSER:
        return "composer";
      case MediaMetadata.PICTURE_TYPE_LYRICIST:
        return "lyricist";
      case MediaMetadata.PICTURE_TYPE_RECORDING_LOCATION:
        return "recording location";
      case MediaMetadata.PICTURE_TYPE_DURING_RECORDING:
        return "during recording";
      case MediaMetadata.PICTURE_TYPE_DURING_PERFORMANCE:
        return "during performance";
      case MediaMetadata.PICTURE_TYPE_MOVIE_VIDEO_SCREEN_CAPTURE:
        return "video capture";
      case MediaMetadata.PICTURE_TYPE_A_BRIGHT_COLORED_FISH:
        return "bright colored fish";
      case MediaMetadata.PICTURE_TYPE_ILLUSTRATION:
        return "illustration";
      case MediaMetadata.PICTURE_TYPE_BAND_ARTIST_LOGO:
        return "artist logo";
      case MediaMetadata.PICTURE_TYPE_PUBLISHER_STUDIO_LOGO:
        return "publisher logo";
      default:
        return "unrecognised: " + pictureType;
    }
  }

  @Nullable
  private static String mediaTypeString(@Nullable @MediaType Integer mediaType) {
    if (mediaType == null) {
      return null;
    }
    switch (mediaType) {
      case MediaMetadata.MEDIA_TYPE_MIXED:
        return "mixed";
      case MediaMetadata.MEDIA_TYPE_MUSIC:
        return "music";
      case MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER:
        return "audiobook chapter";
      case MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE:
        return "podcast episode";
      case MediaMetadata.MEDIA_TYPE_RADIO_STATION:
        return "radio station";
      case MediaMetadata.MEDIA_TYPE_NEWS:
        return "news";
      case MediaMetadata.MEDIA_TYPE_VIDEO:
        return "video";
      case MediaMetadata.MEDIA_TYPE_TRAILER:
        return "trailer";
      case MediaMetadata.MEDIA_TYPE_MOVIE:
        return "movie";
      case MediaMetadata.MEDIA_TYPE_TV_SHOW:
        return "tv show";
      case MediaMetadata.MEDIA_TYPE_ALBUM:
        return "album";
      case MediaMetadata.MEDIA_TYPE_ARTIST:
        return "artist";
      case MediaMetadata.MEDIA_TYPE_GENRE:
        return "genre";
      case MediaMetadata.MEDIA_TYPE_PLAYLIST:
        return "playlist";
      case MediaMetadata.MEDIA_TYPE_YEAR:
        return "year";
      case MediaMetadata.MEDIA_TYPE_AUDIO_BOOK:
        return "audiobook";
      case MediaMetadata.MEDIA_TYPE_PODCAST:
        return "podcast";
      case MediaMetadata.MEDIA_TYPE_TV_CHANNEL:
        return "tv channel";
      case MediaMetadata.MEDIA_TYPE_TV_SERIES:
        return "tv series";
      case MediaMetadata.MEDIA_TYPE_TV_SEASON:
        return "tv season";
      case MediaMetadata.MEDIA_TYPE_FOLDER_MIXED:
        return "folder (mixed)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS:
        return "folder (albums)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS:
        return "folder (artists)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_GENRES:
        return "folder (genres)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS:
        return "folder (playlists)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_YEARS:
        return "folder (years)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS:
        return "folder (audiobooks)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS:
        return "folder (podcasts)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_TV_CHANNELS:
        return "folder (tv channels)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_TV_SERIES:
        return "folder (tv series)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_TV_SHOWS:
        return "folder (tv shows)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS:
        return "folder (radio stations)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_NEWS:
        return "folder (news)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_VIDEOS:
        return "folder (videos)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_TRAILERS:
        return "folder (trailers)";
      case MediaMetadata.MEDIA_TYPE_FOLDER_MOVIES:
        return "folder (movies)";
      default:
        return "unrecognised: " + mediaType;
    }
  }

  @Nullable
  private static String ratingString(@Nullable Rating rating) {
    if (rating == null) {
      return null;
    }
    if (rating instanceof StarRating) {
      StarRating starRating = (StarRating) rating;
      return starRating.getStarRating() + "/" + starRating.getMaxStars() + " stars";
    } else if (rating instanceof PercentageRating) {
      return ((PercentageRating) rating).getPercent() + "%";
    } else if (rating instanceof HeartRating) {
      return ((HeartRating) rating).isHeart() ? "❤️" : "\uD83D\uDC94";
    } else if (rating instanceof ThumbRating) {
      return ((ThumbRating) rating).isThumbsUp() ? "\uD83D\uDC4D" : "\uD83D\uDC4E";
    }
    throw new IllegalStateException("Unrecognized Rating subclass: " + rating);
  }

  private void dumpSubtitles(Dumper dumper) {
    if (subtitles.size() != subtitlesFromDeprecatedTextOutput.size()) {
      throw new IllegalStateException(
          "Expected subtitles to be of equal length from both implementations of onCues method.");
    }

    if (subtitles.isEmpty()) {
      return;
    }
    dumper.startBlock("TextOutput");
    for (int i = 0; i < subtitles.size(); i++) {
      dumper.startBlock("Subtitle[" + i + "]");
      // TODO: Solving https://github.com/google/ExoPlayer/issues/9672 will allow us to remove this
      // hack of forcing presentationTimeUs to be >= 0.
      dumper.addTime("presentationTimeUs", max(0, subtitles.get(i).presentationTimeUs));
      ImmutableList<Cue> subtitle = subtitles.get(i).cues;
      if (!subtitle.equals(subtitlesFromDeprecatedTextOutput.get(i))) {
        throw new IllegalStateException(
            "Expected subtitle to be equal from both implementations of onCues method for index "
                + i);
      }
      if (subtitle.isEmpty()) {
        dumper.add("Cues", ImmutableList.of());
      }
      for (int j = 0; j < subtitle.size(); j++) {
        dumper.startBlock("Cue[" + j + "]");
        Cue cue = subtitle.get(j);
        dumper.addIfNonDefault("text", cue.text, null);
        dumper.addIfNonDefault("textAlignment", cue.textAlignment, null);
        dumpBitmap(dumper, cue.bitmap);
        dumper.addIfNonDefault("line", cue.line, Cue.DIMEN_UNSET);
        dumper.addIfNonDefault("lineType", cue.lineType, Cue.TYPE_UNSET);
        dumper.addIfNonDefault("lineAnchor", cue.lineAnchor, Cue.TYPE_UNSET);
        dumper.addIfNonDefault("position", cue.position, Cue.DIMEN_UNSET);
        dumper.addIfNonDefault("positionAnchor", cue.positionAnchor, Cue.TYPE_UNSET);
        dumper.addIfNonDefault("size", cue.size, Cue.DIMEN_UNSET);
        dumper.addIfNonDefault("bitmapHeight", cue.bitmapHeight, Cue.DIMEN_UNSET);
        if (cue.windowColorSet) {
          dumper.add("cue.windowColor", cue.windowColor);
        }
        dumper.addIfNonDefault("textSizeType", cue.textSizeType, Cue.TYPE_UNSET);
        dumper.addIfNonDefault("textSize", cue.textSize, Cue.DIMEN_UNSET);
        dumper.addIfNonDefault("verticalType", cue.verticalType, Cue.TYPE_UNSET);
        dumper.endBlock();
      }
      dumper.endBlock();
    }
    dumper.endBlock();
  }

  private static void dumpBitmap(Dumper dumper, @Nullable Bitmap bitmap) {
    if (bitmap == null) {
      return;
    }
    byte[] bytes = new byte[bitmap.getByteCount()];
    bitmap.copyPixelsToBuffer(ByteBuffer.wrap(bytes));
    dumper.add("bitmap", bytes);
  }
}
