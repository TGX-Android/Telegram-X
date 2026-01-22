/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.session;

import static androidx.media3.common.Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS;
import static androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS;
import static androidx.media3.common.Player.COMMAND_GET_AUDIO_ATTRIBUTES;
import static androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_GET_DEVICE_VOLUME;
import static androidx.media3.common.Player.COMMAND_GET_METADATA;
import static androidx.media3.common.Player.COMMAND_GET_TIMELINE;
import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_PREPARE;
import static androidx.media3.common.Player.COMMAND_RELEASE;
import static androidx.media3.common.Player.COMMAND_SEEK_BACK;
import static androidx.media3.common.Player.COMMAND_SEEK_FORWARD;
import static androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_DEFAULT_POSITION;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS;
import static androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SET_REPEAT_MODE;
import static androidx.media3.common.Player.COMMAND_SET_SHUFFLE_MODE;
import static androidx.media3.common.Player.COMMAND_SET_SPEED_AND_PITCH;
import static androidx.media3.common.Player.COMMAND_STOP;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.constrainValue;
import static androidx.media3.session.MediaConstants.EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY;
import static androidx.media3.session.legacy.MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS;
import static androidx.media3.session.legacy.MediaConstants.DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST;
import static androidx.media3.session.legacy.MediaMetadataCompat.PREFERRED_DESCRIPTION_ORDER;
import static androidx.media3.session.legacy.MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS;
import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.AdPlaybackState;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.HeartRating;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PercentageRating;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Rating;
import androidx.media3.common.StarRating;
import androidx.media3.common.ThumbRating;
import androidx.media3.common.Timeline;
import androidx.media3.common.Timeline.Period;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.util.Log;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.legacy.AudioAttributesCompat;
import androidx.media3.session.legacy.MediaBrowserCompat;
import androidx.media3.session.legacy.MediaBrowserServiceCompat.BrowserRoot;
import androidx.media3.session.legacy.MediaControllerCompat;
import androidx.media3.session.legacy.MediaDescriptionCompat;
import androidx.media3.session.legacy.MediaMetadataCompat;
import androidx.media3.session.legacy.MediaSessionCompat.QueueItem;
import androidx.media3.session.legacy.PlaybackStateCompat;
import androidx.media3.session.legacy.PlaybackStateCompat.CustomAction;
import androidx.media3.session.legacy.RatingCompat;
import androidx.media3.session.legacy.VolumeProviderCompat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/** Util methods for converting between {@code androidx.media3} and {@code androidx.media} types. */
/* package*/ class LegacyConversions {

  private static final String TAG = "LegacyConversions";

  // Stub BrowserRoot for accepting any connection here.
  public static final BrowserRoot defaultBrowserRoot =
      new BrowserRoot(MediaLibraryService.SERVICE_INTERFACE, null);

  public static final ImmutableSet<String> KNOWN_METADATA_COMPAT_KEYS =
      ImmutableSet.of(
          MediaMetadataCompat.METADATA_KEY_TITLE,
          MediaMetadataCompat.METADATA_KEY_ARTIST,
          MediaMetadataCompat.METADATA_KEY_DURATION,
          MediaMetadataCompat.METADATA_KEY_ALBUM,
          MediaMetadataCompat.METADATA_KEY_AUTHOR,
          MediaMetadataCompat.METADATA_KEY_WRITER,
          MediaMetadataCompat.METADATA_KEY_COMPOSER,
          MediaMetadataCompat.METADATA_KEY_COMPILATION,
          MediaMetadataCompat.METADATA_KEY_DATE,
          MediaMetadataCompat.METADATA_KEY_YEAR,
          MediaMetadataCompat.METADATA_KEY_GENRE,
          MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER,
          MediaMetadataCompat.METADATA_KEY_NUM_TRACKS,
          MediaMetadataCompat.METADATA_KEY_DISC_NUMBER,
          MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST,
          MediaMetadataCompat.METADATA_KEY_ART,
          MediaMetadataCompat.METADATA_KEY_ART_URI,
          MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
          MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
          MediaMetadataCompat.METADATA_KEY_USER_RATING,
          MediaMetadataCompat.METADATA_KEY_RATING,
          MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
          MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
          MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
          MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON,
          MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
          MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
          MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
          MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE,
          MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT,
          MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS,
          MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT);

  /** Exception thrown when the conversion between legacy and Media3 states fails. */
  public static class ConversionException extends Exception {
    private ConversionException(String message) {
      super(message);
    }
  }

  /** Converts {@link PlaybackStateCompat} to {@link PlaybackException}. */
  @Nullable
  public static PlaybackException convertToPlaybackException(
      @Nullable PlaybackStateCompat playbackStateCompat) {
    if (playbackStateCompat == null
        || playbackStateCompat.getState() != PlaybackStateCompat.STATE_ERROR) {
      return null;
    }
    @Nullable CharSequence errorMessage = playbackStateCompat.getErrorMessage();
    @Nullable Bundle playbackStateCompatExtras = playbackStateCompat.getExtras();
    return new PlaybackException(
        errorMessage != null ? errorMessage.toString() : null,
        /* cause= */ null,
        convertToPlaybackExceptionErrorCode(playbackStateCompat.getErrorCode()),
        playbackStateCompatExtras != null ? playbackStateCompatExtras : Bundle.EMPTY);
  }

  /**
   * Converts {@link PlaybackStateCompat} to {@link SessionError}.
   *
   * @param playbackStateCompat The {@link PlaybackStateCompat} to convert.
   * @param context The context to read string resources to be used as fallback error messages.
   * @return The {@link SessionError}.
   */
  @Nullable
  public static SessionError convertToSessionError(
      @Nullable PlaybackStateCompat playbackStateCompat, Context context) {
    if (playbackStateCompat == null) {
      return null;
    }
    return convertToSessionError(
        playbackStateCompat.getState(),
        playbackStateCompat.getErrorCode(),
        playbackStateCompat.getErrorMessage(),
        playbackStateCompat.getExtras(),
        context);
  }

  @VisibleForTesting
  @Nullable
  /* package */ static SessionError convertToSessionError(
      @PlaybackStateCompat.State int state,
      @PlaybackStateCompat.ErrorCode int errorCode,
      @Nullable CharSequence errorMessage,
      @Nullable Bundle extras,
      Context context) {
    if (state == PlaybackStateCompat.STATE_ERROR
        || errorCode == PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR) {
      return null;
    }
    int sessionErrorCode = convertToSessionErrorCode(errorCode);
    return new SessionError(
        sessionErrorCode,
        errorMessage != null
            ? errorMessage.toString()
            : getSessionErrorMessage(sessionErrorCode, context),
        extras != null ? extras : Bundle.EMPTY);
  }

  private static String getSessionErrorMessage(
      @SessionError.Code int sessionErrorCode, Context context) {
    switch (sessionErrorCode) {
      case SessionError.INFO_CANCELLED:
        return context.getString(R.string.error_message_info_cancelled);
      case SessionError.ERROR_BAD_VALUE:
        return context.getString(R.string.error_message_bad_value);
      case SessionError.ERROR_INVALID_STATE:
        return context.getString(R.string.error_message_invalid_state);
      case SessionError.ERROR_IO:
        return context.getString(R.string.error_message_io);
      case SessionError.ERROR_NOT_SUPPORTED:
        return context.getString(R.string.error_message_not_supported);
      case SessionError.ERROR_PERMISSION_DENIED:
        return context.getString(R.string.error_message_permission_denied);
      case SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED:
        return context.getString(R.string.error_message_authentication_expired);
      case SessionError.ERROR_SESSION_CONTENT_ALREADY_PLAYING:
        return context.getString(R.string.error_message_content_already_playing);
      case SessionError.ERROR_SESSION_CONCURRENT_STREAM_LIMIT:
        return context.getString(R.string.error_message_concurrent_stream_limit);
      case SessionError.ERROR_SESSION_DISCONNECTED:
        return context.getString(R.string.error_message_disconnected);
      case SessionError.ERROR_SESSION_END_OF_PLAYLIST:
        return context.getString(R.string.error_message_end_of_playlist);
      case SessionError.ERROR_SESSION_NOT_AVAILABLE_IN_REGION:
        return context.getString(R.string.error_message_not_available_in_region);
      case SessionError.ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED:
        return context.getString(R.string.error_message_parental_control_restricted);
      case SessionError.ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED:
        return context.getString(R.string.error_message_premium_account_required);
      case SessionError.ERROR_SESSION_SETUP_REQUIRED:
        return context.getString(R.string.error_message_setup_required);
      case SessionError.ERROR_SESSION_SKIP_LIMIT_REACHED:
        return context.getString(R.string.error_message_skip_limit_reached);
      case SessionError.ERROR_UNKNOWN: // fall through
      default:
        return context.getString(R.string.error_message_fallback);
    }
  }

  private static @SessionError.Code int convertToSessionErrorCode(
      @PlaybackStateCompat.ErrorCode int errorCode) {
    switch (errorCode) {
      case PlaybackStateCompat.ERROR_CODE_ACTION_ABORTED:
        return SessionError.INFO_CANCELLED;
      case PlaybackStateCompat.ERROR_CODE_APP_ERROR:
        return SessionError.ERROR_INVALID_STATE;
      case PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED:
        return SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED;
      case PlaybackStateCompat.ERROR_CODE_CONTENT_ALREADY_PLAYING:
        return SessionError.ERROR_SESSION_CONTENT_ALREADY_PLAYING;
      case PlaybackStateCompat.ERROR_CODE_CONCURRENT_STREAM_LIMIT:
        return SessionError.ERROR_SESSION_CONCURRENT_STREAM_LIMIT;
      case PlaybackStateCompat.ERROR_CODE_END_OF_QUEUE:
        return SessionError.ERROR_SESSION_END_OF_PLAYLIST;
      case PlaybackStateCompat.ERROR_CODE_NOT_AVAILABLE_IN_REGION:
        return SessionError.ERROR_SESSION_NOT_AVAILABLE_IN_REGION;
      case PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED:
        return SessionError.ERROR_NOT_SUPPORTED;
      case PlaybackStateCompat.ERROR_CODE_PARENTAL_CONTROL_RESTRICTED:
        return SessionError.ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED;
      case PlaybackStateCompat.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED:
        return SessionError.ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED;
      case PlaybackStateCompat.ERROR_CODE_SKIP_LIMIT_REACHED:
        return SessionError.ERROR_SESSION_SKIP_LIMIT_REACHED;
      default:
        return SessionError.ERROR_UNKNOWN;
    }
  }

  private static @PlaybackException.ErrorCode int convertToPlaybackExceptionErrorCode(
      @PlaybackStateCompat.ErrorCode int errorCode) {
    @PlaybackException.ErrorCode
    int playbackExceptionErrorCode = convertToSessionErrorCode(errorCode);
    switch (playbackExceptionErrorCode) {
      case SessionError.ERROR_UNKNOWN:
        return PlaybackException.ERROR_CODE_UNSPECIFIED;
      case SessionError.ERROR_IO:
        return PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
      default:
        return playbackExceptionErrorCode;
    }
  }

  /** Converts {@link SessionError.Code} to {@link PlaybackStateCompat.ErrorCode}. */
  @PlaybackStateCompat.ErrorCode
  public static int convertToLegacyErrorCode(@SessionError.Code int errorCode) {
    switch (errorCode) {
      case SessionError.INFO_CANCELLED:
        return PlaybackStateCompat.ERROR_CODE_ACTION_ABORTED;
      case SessionError.ERROR_INVALID_STATE:
        return PlaybackStateCompat.ERROR_CODE_APP_ERROR;
      case SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED:
        return PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED;
      case SessionError.ERROR_SESSION_CONTENT_ALREADY_PLAYING:
        return PlaybackStateCompat.ERROR_CODE_CONTENT_ALREADY_PLAYING;
      case SessionError.ERROR_SESSION_CONCURRENT_STREAM_LIMIT:
        return PlaybackStateCompat.ERROR_CODE_CONCURRENT_STREAM_LIMIT;
      case SessionError.ERROR_SESSION_END_OF_PLAYLIST:
        return PlaybackStateCompat.ERROR_CODE_END_OF_QUEUE;
      case SessionError.ERROR_SESSION_NOT_AVAILABLE_IN_REGION:
        return PlaybackStateCompat.ERROR_CODE_NOT_AVAILABLE_IN_REGION;
      case SessionError.ERROR_NOT_SUPPORTED:
        return PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED;
      case SessionError.ERROR_SESSION_PARENTAL_CONTROL_RESTRICTED:
        return PlaybackStateCompat.ERROR_CODE_PARENTAL_CONTROL_RESTRICTED;
      case SessionError.ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED:
        return PlaybackStateCompat.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED;
      case SessionError.ERROR_SESSION_SKIP_LIMIT_REACHED:
        return PlaybackStateCompat.ERROR_CODE_SKIP_LIMIT_REACHED;
      case SessionError.ERROR_UNKNOWN: // fall through
      default:
        return PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR;
    }
  }

  /** Converts {@link PlaybackException} to {@link PlaybackStateCompat.ErrorCode}. */
  @PlaybackStateCompat.ErrorCode
  public static int convertToLegacyErrorCode(PlaybackException playbackException) {
    return convertToLegacyErrorCode(playbackException.errorCode);
  }

  public static MediaBrowserCompat.MediaItem convertToBrowserItem(
      MediaItem item, @Nullable Bitmap artworkBitmap) {
    MediaDescriptionCompat description = convertToMediaDescriptionCompat(item, artworkBitmap);
    MediaMetadata metadata = item.mediaMetadata;
    int flags = 0;
    if (metadata.isBrowsable != null && metadata.isBrowsable) {
      flags |= MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
    }
    if (metadata.isPlayable != null && metadata.isPlayable) {
      flags |= MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
    }
    return new MediaBrowserCompat.MediaItem(description, flags);
  }

  /** Converts a {@link MediaBrowserCompat.MediaItem} to a {@link MediaItem}. */
  public static MediaItem convertToMediaItem(MediaBrowserCompat.MediaItem item) {
    return convertToMediaItem(item.getDescription(), item.isBrowsable(), item.isPlayable());
  }

  /** Converts a {@link QueueItem} to a {@link MediaItem}. */
  public static MediaItem convertToMediaItem(QueueItem item) {
    return convertToMediaItem(item.getDescription());
  }

  /** Converts a {@link QueueItem} to a {@link MediaItem}. */
  public static MediaItem convertToMediaItem(MediaDescriptionCompat description) {
    checkNotNull(description);
    return convertToMediaItem(description, /* browsable= */ false, /* playable= */ true);
  }

  /** Converts a {@link MediaMetadataCompat} to a {@link MediaItem}. */
  public static MediaItem convertToMediaItem(
      MediaMetadataCompat metadataCompat, @RatingCompat.Style int ratingType) {
    return convertToMediaItem(
        metadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID),
        metadataCompat,
        ratingType);
  }

  /** Converts a {@code mediaId} and {@link MediaMetadataCompat} to a {@link MediaItem}. */
  public static MediaItem convertToMediaItem(
      @Nullable String mediaId,
      MediaMetadataCompat metadataCompat,
      @RatingCompat.Style int ratingType) {
    MediaItem.Builder builder = new MediaItem.Builder();
    if (mediaId != null) {
      builder.setMediaId(mediaId);
    }
    @Nullable
    String mediaUriString = metadataCompat.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI);
    if (mediaUriString != null) {
      builder.setRequestMetadata(
          new MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(mediaUriString)).build());
    }
    builder.setMediaMetadata(convertToMediaMetadata(metadataCompat, ratingType));
    return builder.build();
  }

  private static MediaItem convertToMediaItem(
      MediaDescriptionCompat descriptionCompat, boolean browsable, boolean playable) {
    @Nullable String mediaId = descriptionCompat.getMediaId();
    return new MediaItem.Builder()
        .setMediaId(mediaId == null ? MediaItem.DEFAULT_MEDIA_ID : mediaId)
        .setRequestMetadata(
            new MediaItem.RequestMetadata.Builder()
                .setMediaUri(descriptionCompat.getMediaUri())
                .build())
        .setMediaMetadata(
            convertToMediaMetadata(
                descriptionCompat, RatingCompat.RATING_NONE, browsable, playable))
        .build();
  }

  /** Converts a list of {@link MediaBrowserCompat.MediaItem} to a list of {@link MediaItem}. */
  public static ImmutableList<MediaItem> convertBrowserItemListToMediaItemList(
      List<MediaBrowserCompat.MediaItem> items) {
    ImmutableList.Builder<MediaItem> builder = new ImmutableList.Builder<>();
    for (int i = 0; i < items.size(); i++) {
      builder.add(convertToMediaItem(items.get(i)));
    }
    return builder.build();
  }

  /** Converts a {@link Timeline} to a list of {@link MediaItem MediaItems}. */
  public static List<MediaItem> convertToMediaItemList(Timeline timeline) {
    List<MediaItem> mediaItems = new ArrayList<>();
    Window window = new Window();
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      mediaItems.add(timeline.getWindow(i, window).mediaItem);
    }
    return mediaItems;
  }

  /**
   * Converts a {@link MediaItem} to a {@link QueueItem}. The index of the item in the playlist
   * would be used as the queue ID to match the behavior of {@link MediaController}.
   */
  public static QueueItem convertToQueueItem(
      MediaItem item, int mediaItemIndex, @Nullable Bitmap artworkBitmap) {
    MediaDescriptionCompat description = convertToMediaDescriptionCompat(item, artworkBitmap);
    long id = convertToQueueItemId(mediaItemIndex);
    return new QueueItem(description, id);
  }

  /** Converts the index of a {@link MediaItem} in a playlist into id of {@link QueueItem}. */
  public static long convertToQueueItemId(int mediaItemIndex) {
    if (mediaItemIndex == C.INDEX_UNSET) {
      return QueueItem.UNKNOWN_ID;
    }
    return mediaItemIndex;
  }

  public static Window convertToWindow(MediaItem mediaItem, int periodIndex) {
    Window window = new Window();
    window.set(
        /* uid= */ 0,
        mediaItem,
        /* manifest= */ null,
        /* presentationStartTimeMs= */ 0,
        /* windowStartTimeMs= */ 0,
        /* elapsedRealtimeEpochOffsetMs= */ 0,
        /* isSeekable= */ true,
        /* isDynamic= */ false,
        /* liveConfiguration= */ null,
        /* defaultPositionUs= */ 0,
        /* durationUs= */ C.TIME_UNSET,
        /* firstPeriodIndex= */ periodIndex,
        /* lastPeriodIndex= */ periodIndex,
        /* positionInFirstPeriodUs= */ 0);
    return window;
  }

  public static Period convertToPeriod(int windowIndex) {
    Period period = new Period();
    period.set(
        /* id= */ null,
        /* uid= */ null,
        windowIndex,
        /* durationUs= */ C.TIME_UNSET,
        /* positionInWindowUs= */ 0,
        /* adPlaybackState= */ AdPlaybackState.NONE,
        /* isPlaceholder= */ true);
    return period;
  }

  /** Creates {@link MediaMetadata} from the {@link CharSequence queue title}. */
  public static MediaMetadata convertToMediaMetadata(@Nullable CharSequence queueTitle) {
    if (queueTitle == null) {
      return MediaMetadata.EMPTY;
    }
    return new MediaMetadata.Builder().setTitle(queueTitle).build();
  }

  public static MediaMetadata convertToMediaMetadata(
      @Nullable MediaDescriptionCompat descriptionCompat, @RatingCompat.Style int ratingType) {
    return convertToMediaMetadata(
        descriptionCompat, ratingType, /* browsable= */ false, /* playable= */ true);
  }

  @SuppressWarnings("deprecation") // Populating deprecated fields.
  private static MediaMetadata convertToMediaMetadata(
      @Nullable MediaDescriptionCompat descriptionCompat,
      @RatingCompat.Style int ratingType,
      boolean browsable,
      boolean playable) {
    if (descriptionCompat == null) {
      return MediaMetadata.EMPTY;
    }

    MediaMetadata.Builder builder = new MediaMetadata.Builder();

    builder
        .setSubtitle(descriptionCompat.getSubtitle())
        .setDescription(descriptionCompat.getDescription())
        .setArtworkUri(descriptionCompat.getIconUri())
        .setUserRating(convertToRating(RatingCompat.newUnratedRating(ratingType)));

    @Nullable Bitmap iconBitmap = descriptionCompat.getIconBitmap();
    if (iconBitmap != null) {
      @Nullable byte[] artworkData = null;
      try {
        artworkData = convertToByteArray(iconBitmap);
      } catch (IOException e) {
        Log.w(TAG, "Failed to convert iconBitmap to artworkData", e);
      }
      builder.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
    }

    @Nullable Bundle compatExtras = descriptionCompat.getExtras();
    @Nullable Bundle extras = compatExtras == null ? null : new Bundle(compatExtras);

    if (extras != null && extras.containsKey(MediaDescriptionCompat.EXTRA_BT_FOLDER_TYPE)) {
      builder.setFolderType(
          convertToFolderType(extras.getLong(MediaDescriptionCompat.EXTRA_BT_FOLDER_TYPE)));
      extras.remove(MediaDescriptionCompat.EXTRA_BT_FOLDER_TYPE);
    }
    builder.setIsBrowsable(browsable);

    if (extras != null && extras.containsKey(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT)) {
      builder.setMediaType((int) extras.getLong(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT));
      extras.remove(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT);
    }

    if (extras != null
        && extras.containsKey(DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST)) {
      builder.setSupportedCommands(
          ImmutableList.copyOf(
              checkNotNull(
                  extras.getStringArrayList(
                      DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST))));
    }

    if (extras != null
        && extras.containsKey(MediaConstants.EXTRAS_KEY_MEDIA_DESCRIPTION_COMPAT_TITLE)) {
      builder.setTitle(
          extras.getCharSequence(MediaConstants.EXTRAS_KEY_MEDIA_DESCRIPTION_COMPAT_TITLE));
      builder.setDisplayTitle(descriptionCompat.getTitle());
      extras.remove(MediaConstants.EXTRAS_KEY_MEDIA_DESCRIPTION_COMPAT_TITLE);
    } else {
      builder.setTitle(descriptionCompat.getTitle());
    }

    if (extras != null && !extras.isEmpty()) {
      builder.setExtras(extras);
    }

    builder.setIsPlayable(playable);

    return builder.build();
  }

  /** Creates {@link MediaMetadata} from the {@link MediaMetadataCompat} and rating type. */
  @SuppressWarnings("deprecation") // Populating deprecated fields.
  public static MediaMetadata convertToMediaMetadata(
      @Nullable MediaMetadataCompat metadataCompat, @RatingCompat.Style int ratingType) {
    if (metadataCompat == null) {
      return MediaMetadata.EMPTY;
    }

    MediaMetadata.Builder builder = new MediaMetadata.Builder();

    CharSequence title = metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_TITLE);
    CharSequence displayTitle =
        metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
    builder
        .setTitle(title != null ? title : displayTitle)
        .setDisplayTitle(title != null ? displayTitle : null)
        .setSubtitle(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE))
        .setDescription(
            metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION))
        .setArtist(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_ARTIST))
        .setAlbumTitle(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_ALBUM))
        .setAlbumArtist(metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST))
        .setOverallRating(
            convertToRating(metadataCompat.getRating(MediaMetadataCompat.METADATA_KEY_RATING)));

    if (metadataCompat.containsKey(MediaMetadataCompat.METADATA_KEY_DURATION)) {
      long durationMs = metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
      if (durationMs >= 0) {
        // Only set duration if a non-negative is set. Do not assert because we don't want the app
        // to crash because an external app sends a negative value that is valid in media1.
        builder.setDurationMs(durationMs);
      }
    }

    @Nullable
    Rating userRating =
        convertToRating(metadataCompat.getRating(MediaMetadataCompat.METADATA_KEY_USER_RATING));
    if (userRating != null) {
      builder.setUserRating(userRating);
    } else {
      builder.setUserRating(convertToRating(RatingCompat.newUnratedRating(ratingType)));
    }

    if (metadataCompat.containsKey(MediaMetadataCompat.METADATA_KEY_YEAR)) {
      long year = metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_YEAR);
      builder.setRecordingYear((int) year);
    }

    @Nullable
    String artworkUriString =
        getFirstString(
            metadataCompat,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadataCompat.METADATA_KEY_ART_URI);
    if (artworkUriString != null) {
      builder.setArtworkUri(Uri.parse(artworkUriString));
    }

    @Nullable
    Bitmap artworkBitmap =
        getFirstBitmap(
            metadataCompat,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON,
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
            MediaMetadataCompat.METADATA_KEY_ART);
    if (artworkBitmap != null) {
      try {
        byte[] artworkData = convertToByteArray(artworkBitmap);
        builder.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
      } catch (IOException e) {
        Log.w(TAG, "Failed to convert artworkBitmap to artworkData", e);
      }
    }

    boolean isBrowsable =
        metadataCompat.containsKey(MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE);
    builder.setIsBrowsable(isBrowsable);
    if (isBrowsable) {
      builder.setFolderType(
          convertToFolderType(
              metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE)));
    }

    if (metadataCompat.containsKey(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT)) {
      builder.setMediaType(
          (int) metadataCompat.getLong(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT));
    }

    builder.setIsPlayable(true);

    Bundle extras = metadataCompat.getBundle();
    for (String key : KNOWN_METADATA_COMPAT_KEYS) {
      extras.remove(key);
    }
    if (!extras.isEmpty()) {
      builder.setExtras(extras);
    }

    return builder.build();
  }

  @Nullable
  private static Bitmap getFirstBitmap(MediaMetadataCompat mediaMetadataCompat, String... keys) {
    for (String key : keys) {
      if (mediaMetadataCompat.containsKey(key)) {
        return mediaMetadataCompat.getBitmap(key);
      }
    }
    return null;
  }

  @Nullable
  private static String getFirstString(MediaMetadataCompat mediaMetadataCompat, String... keys) {
    for (String key : keys) {
      if (mediaMetadataCompat.containsKey(key)) {
        return mediaMetadataCompat.getString(key);
      }
    }
    return null;
  }

  /**
   * Converts a {@link MediaMetadata} to a {@link MediaMetadataCompat}.
   *
   * @param metadata The {@link MediaMetadata} instance to convert.
   * @param mediaId The corresponding media ID.
   * @param mediaUri The corresponding media URI, or null if unknown.
   * @param durationMs The duration of the media, in milliseconds or {@link C#TIME_UNSET}, if no
   *     duration should be included.
   * @return An instance of the legacy {@link MediaMetadataCompat}.
   */
  // Converting deprecated fields and suppressing nullness.
  // TODO: b/311689564 - Add @Nullable annotations to setters of MediaMetadataCompat.Builder
  @SuppressWarnings({"deprecation", "nullness:argument"})
  public static MediaMetadataCompat convertToMediaMetadataCompat(
      MediaMetadata metadata,
      String mediaId,
      @Nullable Uri mediaUri,
      long durationMs,
      @Nullable Bitmap artworkBitmap) {
    MediaMetadataCompat.Builder builder =
        new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId);

    if (metadata.title != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.title);
    }

    if (metadata.displayTitle != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, metadata.displayTitle);
    }

    if (metadata.subtitle != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, metadata.subtitle);
    }

    if (metadata.description != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, metadata.description);
    }

    if (metadata.artist != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.artist);
    }

    if (metadata.albumTitle != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_ALBUM, metadata.albumTitle);
    }

    if (metadata.albumArtist != null) {
      builder.putText(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, metadata.albumArtist);
    }

    if (metadata.recordingYear != null) {
      builder.putLong(MediaMetadataCompat.METADATA_KEY_YEAR, metadata.recordingYear);
    }

    if (mediaUri != null) {
      builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, mediaUri.toString());
    }

    if (metadata.artworkUri != null) {
      builder.putString(
          MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, metadata.artworkUri.toString());
      builder.putString(
          MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, metadata.artworkUri.toString());
      builder.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, metadata.artworkUri.toString());
    }

    if (artworkBitmap != null) {
      builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artworkBitmap);
      builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap);
    }

    if (metadata.folderType != null && metadata.folderType != MediaMetadata.FOLDER_TYPE_NONE) {
      builder.putLong(
          MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE,
          convertToExtraBtFolderType(metadata.folderType));
    }

    if (durationMs == C.TIME_UNSET && metadata.durationMs != null) {
      // If the actual media duration is unknown, use the manually declared value if available.
      durationMs = metadata.durationMs;
    }
    if (durationMs != C.TIME_UNSET) {
      builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
    }

    @Nullable RatingCompat userRatingCompat = convertToRatingCompat(metadata.userRating);
    if (userRatingCompat != null) {
      builder.putRating(MediaMetadataCompat.METADATA_KEY_USER_RATING, userRatingCompat);
    }

    @Nullable RatingCompat overallRatingCompat = convertToRatingCompat(metadata.overallRating);
    if (overallRatingCompat != null) {
      builder.putRating(MediaMetadataCompat.METADATA_KEY_RATING, overallRatingCompat);
    }

    if (metadata.mediaType != null) {
      builder.putLong(MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT, metadata.mediaType);
    }

    if (metadata.extras != null) {
      for (@Nullable String customKey : metadata.extras.keySet()) {
        @Nullable Object customValue = metadata.extras.get(customKey);
        if (customValue == null || customValue instanceof CharSequence) {
          builder.putText(customKey, (CharSequence) customValue);
        } else if (customValue instanceof Byte
            || customValue instanceof Short
            || customValue instanceof Integer
            || customValue instanceof Long) {
          builder.putLong(customKey, ((Number) customValue).longValue());
        }
      }
    }

    return builder.build();
  }

  /** Converts a {@link MediaItem} to a {@link MediaDescriptionCompat} */
  @SuppressWarnings("deprecation") // Converting deprecated fields.
  public static MediaDescriptionCompat convertToMediaDescriptionCompat(
      MediaItem item, @Nullable Bitmap artworkBitmap) {
    MediaDescriptionCompat.Builder builder =
        new MediaDescriptionCompat.Builder()
            .setMediaId(item.mediaId.equals(MediaItem.DEFAULT_MEDIA_ID) ? null : item.mediaId);
    MediaMetadata metadata = item.mediaMetadata;
    if (artworkBitmap != null) {
      builder.setIconBitmap(artworkBitmap);
    }
    @Nullable Bundle extras = metadata.extras;
    if (extras != null) {
      extras = new Bundle(extras);
    }
    boolean hasFolderType =
        metadata.folderType != null && metadata.folderType != MediaMetadata.FOLDER_TYPE_NONE;
    boolean hasMediaType = metadata.mediaType != null;
    if (hasFolderType || hasMediaType) {
      if (extras == null) {
        extras = new Bundle();
      }
      if (hasFolderType) {
        extras.putLong(
            MediaDescriptionCompat.EXTRA_BT_FOLDER_TYPE,
            convertToExtraBtFolderType(checkNotNull(metadata.folderType)));
      }
      if (hasMediaType) {
        extras.putLong(
            MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT, checkNotNull(metadata.mediaType));
      }
    }
    if (!metadata.supportedCommands.isEmpty()) {
      if (extras == null) {
        extras = new Bundle();
      }
      extras.putStringArrayList(
          DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST,
          new ArrayList<>(metadata.supportedCommands));
    }
    CharSequence title;
    CharSequence subtitle;
    CharSequence description;
    if (metadata.displayTitle != null) {
      title = metadata.displayTitle;
      subtitle = metadata.subtitle;
      description = metadata.description;
      if (extras == null) {
        extras = new Bundle();
      }
      extras.putCharSequence(
          MediaConstants.EXTRAS_KEY_MEDIA_DESCRIPTION_COMPAT_TITLE, metadata.title);
    } else {
      // The BT AVRPC service expects the subtitle of the media description to be the artist
      // (see https://github.com/androidx/media/issues/148). This can be achieved by NOT setting the
      // `displayTitle` when setting the `artist`, or by setting the `displayTitle` and writing the
      // artist into the `subtitle`. When `displayTitle` is set, the artist is always ignored.
      CharSequence[] texts = new CharSequence[3];
      int textIndex = 0;
      int keyIndex = 0;
      while (textIndex < texts.length && keyIndex < PREFERRED_DESCRIPTION_ORDER.length) {
        CharSequence next = getText(PREFERRED_DESCRIPTION_ORDER[keyIndex++], metadata);
        if (!TextUtils.isEmpty(next)) {
          // Fill in the next empty bit of text
          texts[textIndex++] = next;
        }
      }
      title = texts[0];
      subtitle = texts[1];
      description = texts[2];
    }
    return builder
        .setTitle(title)
        .setSubtitle(subtitle)
        .setDescription(description)
        .setIconUri(metadata.artworkUri)
        .setMediaUri(item.requestMetadata.mediaUri)
        .setExtras(extras)
        .build();
  }

  @Nullable
  private static CharSequence getText(String key, MediaMetadata metadata) {
    switch (key) {
      case MediaMetadataCompat.METADATA_KEY_TITLE:
        return metadata.title;
      case MediaMetadataCompat.METADATA_KEY_ARTIST:
        return metadata.artist;
      case MediaMetadataCompat.METADATA_KEY_ALBUM:
        return metadata.albumTitle;
      case MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST:
        return metadata.albumArtist;
      case MediaMetadataCompat.METADATA_KEY_WRITER:
        return metadata.writer;
      case MediaMetadataCompat.METADATA_KEY_COMPOSER:
        return metadata.composer;
      case MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE:
        return metadata.subtitle;
      default:
        return null;
    }
  }

  @SuppressWarnings("deprecation") // Converting to deprecated constants.
  @MediaMetadata.FolderType
  private static int convertToFolderType(long extraBtFolderType) {
    if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_MIXED) {
      return MediaMetadata.FOLDER_TYPE_MIXED;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_TITLES) {
      return MediaMetadata.FOLDER_TYPE_TITLES;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_ALBUMS) {
      return MediaMetadata.FOLDER_TYPE_ALBUMS;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_ARTISTS) {
      return MediaMetadata.FOLDER_TYPE_ARTISTS;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_GENRES) {
      return MediaMetadata.FOLDER_TYPE_GENRES;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_PLAYLISTS) {
      return MediaMetadata.FOLDER_TYPE_PLAYLISTS;
    } else if (extraBtFolderType == MediaDescriptionCompat.BT_FOLDER_TYPE_YEARS) {
      return MediaMetadata.FOLDER_TYPE_YEARS;
    } else {
      return MediaMetadata.FOLDER_TYPE_MIXED;
    }
  }

  @SuppressWarnings("deprecation") // Converting from deprecated constants.
  private static long convertToExtraBtFolderType(@MediaMetadata.FolderType int folderType) {
    switch (folderType) {
      case MediaMetadata.FOLDER_TYPE_MIXED:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_MIXED;
      case MediaMetadata.FOLDER_TYPE_TITLES:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_TITLES;
      case MediaMetadata.FOLDER_TYPE_ALBUMS:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_ALBUMS;
      case MediaMetadata.FOLDER_TYPE_ARTISTS:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_ARTISTS;
      case MediaMetadata.FOLDER_TYPE_GENRES:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_GENRES;
      case MediaMetadata.FOLDER_TYPE_PLAYLISTS:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_PLAYLISTS;
      case MediaMetadata.FOLDER_TYPE_YEARS:
        return MediaDescriptionCompat.BT_FOLDER_TYPE_YEARS;
      case MediaMetadata.FOLDER_TYPE_NONE:
      default:
        throw new IllegalArgumentException("Unrecognized FolderType: " + folderType);
    }
  }

  /**
   * Creates a {@link Rating} from the {@link RatingCompat}.
   *
   * @param ratingCompat A {@link RatingCompat} object.
   * @return The newly created {@link Rating} object.
   */
  @Nullable
  public static Rating convertToRating(@Nullable RatingCompat ratingCompat) {
    if (ratingCompat == null) {
      return null;
    }
    switch (ratingCompat.getRatingStyle()) {
      case RatingCompat.RATING_3_STARS:
        return ratingCompat.isRated()
            ? new StarRating(3, ratingCompat.getStarRating())
            : new StarRating(3);
      case RatingCompat.RATING_4_STARS:
        return ratingCompat.isRated()
            ? new StarRating(4, ratingCompat.getStarRating())
            : new StarRating(4);
      case RatingCompat.RATING_5_STARS:
        return ratingCompat.isRated()
            ? new StarRating(5, ratingCompat.getStarRating())
            : new StarRating(5);
      case RatingCompat.RATING_HEART:
        return ratingCompat.isRated()
            ? new HeartRating(ratingCompat.hasHeart())
            : new HeartRating();
      case RatingCompat.RATING_THUMB_UP_DOWN:
        return ratingCompat.isRated()
            ? new ThumbRating(ratingCompat.isThumbUp())
            : new ThumbRating();
      case RatingCompat.RATING_PERCENTAGE:
        return ratingCompat.isRated()
            ? new PercentageRating(ratingCompat.getPercentRating())
            : new PercentageRating();
      case RatingCompat.RATING_NONE:
      default:
        return null;
    }
  }

  /**
   * Creates a {@link RatingCompat} from the {@link Rating}.
   *
   * @param rating A {@link Rating} object.
   * @return The newly created {@link RatingCompat} object.
   */
  @SuppressLint("WrongConstant") // for @StarStyle
  @Nullable
  public static RatingCompat convertToRatingCompat(@Nullable Rating rating) {
    if (rating == null) {
      return null;
    }
    int ratingCompatStyle = getRatingCompatStyle(rating);
    if (!rating.isRated()) {
      return RatingCompat.newUnratedRating(ratingCompatStyle);
    }

    switch (ratingCompatStyle) {
      case RatingCompat.RATING_3_STARS:
      case RatingCompat.RATING_4_STARS:
      case RatingCompat.RATING_5_STARS:
        return RatingCompat.newStarRating(ratingCompatStyle, ((StarRating) rating).getStarRating());
      case RatingCompat.RATING_HEART:
        return RatingCompat.newHeartRating(((HeartRating) rating).isHeart());
      case RatingCompat.RATING_THUMB_UP_DOWN:
        return RatingCompat.newThumbRating(((ThumbRating) rating).isThumbsUp());
      case RatingCompat.RATING_PERCENTAGE:
        return RatingCompat.newPercentageRating(((PercentageRating) rating).getPercent());
      case RatingCompat.RATING_NONE:
      default:
        return null;
    }
  }

  /** Converts {@link Player}' states to state of {@link PlaybackStateCompat}. */
  @PlaybackStateCompat.State
  public static int convertToPlaybackStateCompatState(Player player, boolean shouldShowPlayButton) {
    if (player.getPlayerError() != null) {
      return PlaybackStateCompat.STATE_ERROR;
    }
    @Player.State int playbackState = player.getPlaybackState();
    switch (playbackState) {
      case Player.STATE_IDLE:
        return PlaybackStateCompat.STATE_NONE;
      case Player.STATE_READY:
        return shouldShowPlayButton
            ? PlaybackStateCompat.STATE_PAUSED
            : PlaybackStateCompat.STATE_PLAYING;
      case Player.STATE_ENDED:
        return PlaybackStateCompat.STATE_STOPPED;
      case Player.STATE_BUFFERING:
        return shouldShowPlayButton
            ? PlaybackStateCompat.STATE_PAUSED
            : PlaybackStateCompat.STATE_BUFFERING;
      default:
        throw new IllegalArgumentException("Unrecognized State: " + playbackState);
    }
  }

  /** Converts a {@link PlaybackStateCompat} to {@link PlaybackParameters}. */
  public static PlaybackParameters convertToPlaybackParameters(
      @Nullable PlaybackStateCompat playbackStateCompat) {
    return playbackStateCompat == null
        ? PlaybackParameters.DEFAULT
        : new PlaybackParameters(playbackStateCompat.getPlaybackSpeed());
  }

  /** Converts a {@link PlaybackStateCompat} to {@link Player}'s play when ready. */
  public static boolean convertToPlayWhenReady(@Nullable PlaybackStateCompat playbackState) {
    if (playbackState == null) {
      return false;
    }
    switch (playbackState.getState()) {
      case PlaybackStateCompat.STATE_BUFFERING:
      case PlaybackStateCompat.STATE_FAST_FORWARDING:
      case PlaybackStateCompat.STATE_PLAYING:
      case PlaybackStateCompat.STATE_REWINDING:
      case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
      case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
      case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
        return true;
      case PlaybackStateCompat.STATE_CONNECTING:
      case PlaybackStateCompat.STATE_ERROR:
      case PlaybackStateCompat.STATE_NONE:
      case PlaybackStateCompat.STATE_PAUSED:
      case PlaybackStateCompat.STATE_STOPPED:
        return false;
    }
    return false;
  }

  /**
   * Converts a {@link PlaybackStateCompat} to {@link Player.State}
   *
   * @throws ConversionException if the legacy state of the remote session is invalid
   */
  public static @Player.State int convertToPlaybackState(
      @Nullable PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat currentMediaMetadata,
      long timeDiffMs)
      throws ConversionException {
    if (playbackStateCompat == null) {
      return Player.STATE_IDLE;
    }
    boolean isEnded = convertToIsEnded(playbackStateCompat, currentMediaMetadata, timeDiffMs);
    switch (playbackStateCompat.getState()) {
      case PlaybackStateCompat.STATE_CONNECTING:
      case PlaybackStateCompat.STATE_ERROR:
      case PlaybackStateCompat.STATE_NONE:
        return Player.STATE_IDLE;
      case PlaybackStateCompat.STATE_STOPPED:
        return isEnded ? Player.STATE_ENDED : Player.STATE_IDLE;
      case PlaybackStateCompat.STATE_BUFFERING:
      case PlaybackStateCompat.STATE_FAST_FORWARDING:
      case PlaybackStateCompat.STATE_REWINDING:
      case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
      case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
      case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
        return Player.STATE_BUFFERING;
      case PlaybackStateCompat.STATE_PLAYING:
        return Player.STATE_READY;
      case PlaybackStateCompat.STATE_PAUSED:
        return isEnded ? Player.STATE_ENDED : Player.STATE_READY;
      default:
        throw new ConversionException(
            "Invalid state of PlaybackStateCompat: " + playbackStateCompat.getState());
    }
  }

  /** Converts a {@link PlaybackStateCompat} to isPlaying, defined by {@link Player#isPlaying()}. */
  public static boolean convertToIsPlaying(@Nullable PlaybackStateCompat playbackStateCompat) {
    if (playbackStateCompat == null) {
      return false;
    }
    return playbackStateCompat.getState() == PlaybackStateCompat.STATE_PLAYING;
  }

  /** Converts a {@link PlaybackStateCompat} to isPlaying, defined by {@link Player#isPlaying()}. */
  public static boolean convertToIsPlayingAd(@Nullable MediaMetadataCompat metadataCompat) {
    if (metadataCompat == null) {
      return false;
    }
    return metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT) != 0;
  }

  /** Gets the current position. {@code 0} will be returned if unknown. */
  public static long convertToCurrentPositionMs(
      @Nullable PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat metadataCompat,
      long timeDiffMs) {
    if (playbackStateCompat == null) {
      return 0;
    }
    long positionMs =
        playbackStateCompat.getState() == PlaybackStateCompat.STATE_PLAYING
            ? getCurrentPosition(playbackStateCompat, timeDiffMs)
            : playbackStateCompat.getPosition();
    long durationMs = convertToDurationMs(metadataCompat);
    return durationMs == C.TIME_UNSET
        ? max(0, positionMs)
        : constrainValue(positionMs, /* min= */ 0, durationMs);
  }

  @SuppressWarnings("nullness:argument") // PlaybackStateCompat#getCurrentPosition can take null.
  private static long getCurrentPosition(PlaybackStateCompat playbackStateCompat, long timeDiffMs) {
    return playbackStateCompat.getCurrentPosition(timeDiffMs == C.TIME_UNSET ? null : timeDiffMs);
  }

  /** Gets the duration. {@link C#TIME_UNSET} will be returned if unknown. */
  public static long convertToDurationMs(@Nullable MediaMetadataCompat metadataCompat) {
    if (metadataCompat == null
        || !metadataCompat.containsKey(MediaMetadataCompat.METADATA_KEY_DURATION)) {
      return C.TIME_UNSET;
    }
    long legacyDurationMs = metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
    return legacyDurationMs <= 0 ? C.TIME_UNSET : legacyDurationMs;
  }

  /** Gets the buffered position. {@code 0} will be returned if unknown. */
  public static long convertToBufferedPositionMs(
      @Nullable PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat metadataCompat,
      long timeDiffMs) {
    long legacyBufferedPositionMs =
        (playbackStateCompat == null) ? 0 : playbackStateCompat.getBufferedPosition();
    long currentPositionMs =
        convertToCurrentPositionMs(playbackStateCompat, metadataCompat, timeDiffMs);
    long durationMs = convertToDurationMs(metadataCompat);
    return (durationMs == C.TIME_UNSET)
        ? max(currentPositionMs, legacyBufferedPositionMs)
        : constrainValue(legacyBufferedPositionMs, currentPositionMs, durationMs);
  }

  /** Gets the total buffered duration. {@code 0} will be returned if unknown. */
  public static long convertToTotalBufferedDurationMs(
      @Nullable PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat metadataCompat,
      long timeDiffMs) {
    long bufferedPositionMs =
        convertToBufferedPositionMs(playbackStateCompat, metadataCompat, timeDiffMs);
    long currentPositionMs =
        convertToCurrentPositionMs(playbackStateCompat, metadataCompat, timeDiffMs);
    return bufferedPositionMs - currentPositionMs;
  }

  /** Gets the buffered percentage. {@code 0} will be returned if unknown. */
  public static int convertToBufferedPercentage(
      @Nullable PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat mediaMetadataCompat,
      long timeDiffMs) {
    long bufferedPositionMs =
        convertToBufferedPositionMs(playbackStateCompat, mediaMetadataCompat, timeDiffMs);
    long durationMs = convertToDurationMs(mediaMetadataCompat);
    return MediaUtils.calculateBufferedPercentage(bufferedPositionMs, durationMs);
  }

  public static @RatingCompat.Style int getRatingCompatStyle(@Nullable Rating rating) {
    if (rating instanceof HeartRating) {
      return RatingCompat.RATING_HEART;
    } else if (rating instanceof ThumbRating) {
      return RatingCompat.RATING_THUMB_UP_DOWN;
    } else if (rating instanceof StarRating) {
      switch (((StarRating) rating).getMaxStars()) {
        case 3:
          return RatingCompat.RATING_3_STARS;
        case 4:
          return RatingCompat.RATING_4_STARS;
        case 5:
          return RatingCompat.RATING_5_STARS;
      }
    } else if (rating instanceof PercentageRating) {
      return RatingCompat.RATING_PERCENTAGE;
    }
    return RatingCompat.RATING_NONE;
  }

  /** Converts {@link PlaybackStateCompat.RepeatMode} to {@link Player.RepeatMode}. */
  public static @Player.RepeatMode int convertToRepeatMode(
      @PlaybackStateCompat.RepeatMode int playbackStateCompatRepeatMode) {
    switch (playbackStateCompatRepeatMode) {
      case PlaybackStateCompat.REPEAT_MODE_INVALID:
      case PlaybackStateCompat.REPEAT_MODE_NONE:
        return Player.REPEAT_MODE_OFF;
      case PlaybackStateCompat.REPEAT_MODE_ONE:
        return Player.REPEAT_MODE_ONE;
      case PlaybackStateCompat.REPEAT_MODE_ALL:
      case PlaybackStateCompat.REPEAT_MODE_GROUP:
        return Player.REPEAT_MODE_ALL;
      default:
        Log.w(
            TAG,
            "Unrecognized PlaybackStateCompat.RepeatMode: "
                + playbackStateCompatRepeatMode
                + " was converted to `Player.REPEAT_MODE_OFF`");
        return Player.REPEAT_MODE_OFF;
    }
  }

  /** Converts {@link Player.RepeatMode} to {@link PlaybackStateCompat.RepeatMode} */
  @PlaybackStateCompat.RepeatMode
  public static int convertToPlaybackStateCompatRepeatMode(@Player.RepeatMode int repeatMode) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return PlaybackStateCompat.REPEAT_MODE_NONE;
      case Player.REPEAT_MODE_ONE:
        return PlaybackStateCompat.REPEAT_MODE_ONE;
      case Player.REPEAT_MODE_ALL:
        return PlaybackStateCompat.REPEAT_MODE_ALL;
      default:
        Log.w(
            TAG,
            "Unrecognized RepeatMode: "
                + repeatMode
                + " was converted to `PlaybackStateCompat.REPEAT_MODE_NONE`");
        return PlaybackStateCompat.REPEAT_MODE_NONE;
    }
  }

  /** Converts {@link PlaybackStateCompat.ShuffleMode} to shuffle mode enabled. */
  public static boolean convertToShuffleModeEnabled(
      @PlaybackStateCompat.ShuffleMode int playbackStateCompatShuffleMode) {
    switch (playbackStateCompatShuffleMode) {
      case PlaybackStateCompat.SHUFFLE_MODE_INVALID:
      case PlaybackStateCompat.SHUFFLE_MODE_NONE:
        return false;
      case PlaybackStateCompat.SHUFFLE_MODE_ALL:
      case PlaybackStateCompat.SHUFFLE_MODE_GROUP:
        return true;
      default:
        throw new IllegalArgumentException(
            "Unrecognized ShuffleMode: " + playbackStateCompatShuffleMode);
    }
  }

  /** Converts shuffle mode enabled to {@link PlaybackStateCompat.ShuffleMode} */
  @PlaybackStateCompat.ShuffleMode
  public static int convertToPlaybackStateCompatShuffleMode(boolean shuffleModeEnabled) {
    return shuffleModeEnabled
        ? PlaybackStateCompat.SHUFFLE_MODE_ALL
        : PlaybackStateCompat.SHUFFLE_MODE_NONE;
  }

  /** Converts the rootHints, option, and extra to the {@link LibraryParams}. */
  @Nullable
  public static LibraryParams convertToLibraryParams(
      Context context, @Nullable Bundle legacyBundle) {
    if (legacyBundle == null) {
      return null;
    }
    try {
      legacyBundle.setClassLoader(context.getClassLoader());
      int supportedChildrenFlags =
          legacyBundle.getInt(
              BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, /* defaultValue= */ -1);
      if (supportedChildrenFlags >= 0) {
        legacyBundle.remove(BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS);
        legacyBundle.putBoolean(
            EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY,
            supportedChildrenFlags == MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
      }
      return new LibraryParams.Builder()
          .setExtras(legacyBundle)
          .setRecent(legacyBundle.getBoolean(BrowserRoot.EXTRA_RECENT))
          .setOffline(legacyBundle.getBoolean(BrowserRoot.EXTRA_OFFLINE))
          .setSuggested(legacyBundle.getBoolean(BrowserRoot.EXTRA_SUGGESTED))
          .build();
    } catch (Exception e) {
      // Failure when unpacking the legacy bundle.
      return new LibraryParams.Builder().setExtras(legacyBundle).build();
    }
  }

  /** Converts {@link LibraryParams} to the root hints. */
  @Nullable
  public static Bundle convertToRootHints(@Nullable LibraryParams params) {
    if (params == null) {
      return null;
    }
    Bundle rootHints = new Bundle(params.extras);
    if (params.extras.containsKey(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)) {
      boolean browsableChildrenSupported =
          params.extras.getBoolean(
              EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY, /* defaultValue= */ false);
      rootHints.remove(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY);
      rootHints.putInt(
          BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS,
          browsableChildrenSupported
              ? MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
              : MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                  | MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }
    rootHints.putBoolean(BrowserRoot.EXTRA_RECENT, params.isRecent);
    rootHints.putBoolean(BrowserRoot.EXTRA_OFFLINE, params.isOffline);
    rootHints.putBoolean(BrowserRoot.EXTRA_SUGGESTED, params.isSuggested);
    return rootHints;
  }

  /**
   * Converts {@link PlaybackStateCompat}, {@link
   * MediaControllerCompat.PlaybackInfo#getVolumeControl() volume control type}, {@link
   * MediaControllerCompat#getFlags() session flags} and {@link MediaControllerCompat#isSessionReady
   * whether the session is ready} to {@link Player.Commands}.
   *
   * @param playbackStateCompat The {@link PlaybackStateCompat}.
   * @param volumeControlType The {@link MediaControllerCompat.PlaybackInfo#getVolumeControl()
   *     volume control type}.
   * @param sessionFlags The session flags.
   * @param isSessionReady Whether the session compat is ready.
   * @return The converted player commands.
   */
  @SuppressWarnings("deprecation") // Backwards compatibility with old volume commands
  public static Player.Commands convertToPlayerCommands(
      @Nullable PlaybackStateCompat playbackStateCompat,
      int volumeControlType,
      long sessionFlags,
      boolean isSessionReady) {
    Player.Commands.Builder playerCommandsBuilder = new Player.Commands.Builder();
    long actions = playbackStateCompat == null ? 0 : playbackStateCompat.getActions();
    boolean playWhenReady = convertToPlayWhenReady(playbackStateCompat);
    if ((hasAction(actions, PlaybackStateCompat.ACTION_PLAY) && !playWhenReady)
        || (hasAction(actions, PlaybackStateCompat.ACTION_PAUSE) && playWhenReady)
        || hasAction(actions, PlaybackStateCompat.ACTION_PLAY_PAUSE)) {
      playerCommandsBuilder.add(COMMAND_PLAY_PAUSE);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_PREPARE)) {
      playerCommandsBuilder.add(COMMAND_PREPARE);
    }
    if ((hasAction(actions, PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID)
            && hasAction(actions, PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID))
        || (hasAction(actions, PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH)
            && hasAction(actions, PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH))
        || (hasAction(actions, PlaybackStateCompat.ACTION_PREPARE_FROM_URI)
            && hasAction(actions, PlaybackStateCompat.ACTION_PLAY_FROM_URI))) {
      // Require both PREPARE and PLAY actions as we have no logic to handle having just one action.
      playerCommandsBuilder.addAll(COMMAND_SET_MEDIA_ITEM, COMMAND_PREPARE);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_REWIND)) {
      playerCommandsBuilder.add(COMMAND_SEEK_BACK);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_FAST_FORWARD)) {
      playerCommandsBuilder.add(COMMAND_SEEK_FORWARD);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_SEEK_TO)) {
      playerCommandsBuilder.addAll(
          COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_DEFAULT_POSITION);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)) {
      playerCommandsBuilder.addAll(COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)) {
      playerCommandsBuilder.addAll(COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED)) {
      playerCommandsBuilder.add(COMMAND_SET_SPEED_AND_PITCH);
    }
    if (hasAction(actions, PlaybackStateCompat.ACTION_STOP)) {
      playerCommandsBuilder.add(COMMAND_STOP);
    }
    if (volumeControlType == VolumeProviderCompat.VOLUME_CONTROL_RELATIVE) {
      playerCommandsBuilder.addAll(
          Player.COMMAND_ADJUST_DEVICE_VOLUME, COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS);
    } else if (volumeControlType == VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE) {
      playerCommandsBuilder.addAll(
          Player.COMMAND_ADJUST_DEVICE_VOLUME,
          COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
          Player.COMMAND_SET_DEVICE_VOLUME,
          COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS);
    }
    playerCommandsBuilder.addAll(
        COMMAND_GET_DEVICE_VOLUME,
        COMMAND_GET_TIMELINE,
        COMMAND_GET_METADATA,
        COMMAND_GET_CURRENT_MEDIA_ITEM,
        COMMAND_GET_AUDIO_ATTRIBUTES,
        COMMAND_RELEASE);
    if ((sessionFlags & FLAG_HANDLES_QUEUE_COMMANDS) != 0) {
      playerCommandsBuilder.add(COMMAND_CHANGE_MEDIA_ITEMS);
      if (hasAction(actions, PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)) {
        playerCommandsBuilder.add(Player.COMMAND_SEEK_TO_MEDIA_ITEM);
      }
    }
    if (isSessionReady) {
      if (hasAction(actions, PlaybackStateCompat.ACTION_SET_REPEAT_MODE)) {
        playerCommandsBuilder.add(COMMAND_SET_REPEAT_MODE);
      }
      if (hasAction(actions, PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)) {
        playerCommandsBuilder.add(COMMAND_SET_SHUFFLE_MODE);
      }
    }
    return playerCommandsBuilder.build();
  }

  /**
   * Checks if the set of actions contains the specified action.
   *
   * @param actions A bit set of actions.
   * @param action The action to check.
   * @return Whether the action is contained in the set.
   */
  private static boolean hasAction(long actions, @PlaybackStateCompat.Actions long action) {
    return (actions & action) != 0;
  }

  /**
   * Converts {@link PlaybackStateCompat} to {@link SessionCommands}.
   *
   * <p>This ignores {@link PlaybackStateCompat#getActions() actions} in the {@link
   * PlaybackStateCompat} to workaround media apps' issues that they don't set playback state
   * correctly.
   *
   * @param state playback state
   * @param isSessionReady Whether the session compat is ready.
   * @return the converted session commands
   */
  public static SessionCommands convertToSessionCommands(
      @Nullable PlaybackStateCompat state, boolean isSessionReady) {
    SessionCommands.Builder sessionCommandsBuilder = new SessionCommands.Builder();
    sessionCommandsBuilder.addAllSessionCommands();
    if (!isSessionReady) {
      // Disables rating function when session isn't ready because of the
      // MediaController#setRating(RatingCompat, Bundle) and MediaController#getRatingType().
      sessionCommandsBuilder.remove(SessionCommand.COMMAND_CODE_SESSION_SET_RATING);
    }

    if (state != null) {
      List<PlaybackStateCompat.CustomAction> customActions = state.getCustomActions();
      if (customActions != null) {
        for (CustomAction customAction : customActions) {
          String action = customAction.getAction();
          @Nullable Bundle extras = customAction.getExtras();
          sessionCommandsBuilder.add(
              new SessionCommand(action, extras == null ? Bundle.EMPTY : extras));
        }
      }
    }
    return sessionCommandsBuilder.build();
  }

  /**
   * Converts {@link CustomAction} in the {@link PlaybackStateCompat} to media button preferences.
   *
   * @param state The {@link PlaybackStateCompat}.
   * @param availablePlayerCommands The available {@link Player.Commands}.
   * @param sessionExtras The {@linkplain MediaControllerCompat#getExtras session-level extras}.
   * @return The media button preferences.
   */
  public static ImmutableList<CommandButton> convertToMediaButtonPreferences(
      @Nullable PlaybackStateCompat state,
      Player.Commands availablePlayerCommands,
      Bundle sessionExtras) {
    if (state == null) {
      return ImmutableList.of();
    }
    List<PlaybackStateCompat.CustomAction> customActions = state.getCustomActions();
    if (customActions == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<CommandButton> customLayout = new ImmutableList.Builder<>();
    for (CustomAction customAction : customActions) {
      String action = customAction.getAction();
      @Nullable Bundle extras = customAction.getExtras();
      @CommandButton.Icon
      int icon =
          extras != null
              ? extras.getInt(
                  MediaConstants.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT,
                  /* defaultValue= */ CommandButton.ICON_UNDEFINED)
              : CommandButton.ICON_UNDEFINED;
      CommandButton button =
          new CommandButton.Builder(icon, customAction.getIcon())
              .setSessionCommand(new SessionCommand(action, extras == null ? Bundle.EMPTY : extras))
              .setDisplayName(customAction.getName())
              .setEnabled(true)
              .build();
      customLayout.add(button);
    }
    return CommandButton.getMediaButtonPreferencesFromCustomLayout(
        customLayout.build(), availablePlayerCommands, sessionExtras);
  }

  /** Converts {@link AudioAttributesCompat} into {@link AudioAttributes}. */
  /*
   * @AudioAttributesCompat.AttributeUsage and @C.AudioUsage both use the same constant values,
   * defined by AudioAttributes in the platform.
   */
  @SuppressLint("WrongConstant")
  public static AudioAttributes convertToAudioAttributes(
      @Nullable AudioAttributesCompat audioAttributesCompat) {
    if (audioAttributesCompat == null) {
      return AudioAttributes.DEFAULT;
    }
    return new AudioAttributes.Builder()
        .setContentType(audioAttributesCompat.getContentType())
        .setFlags(audioAttributesCompat.getFlags())
        .setUsage(audioAttributesCompat.getUsage())
        .build();
  }

  /** Converts {@link MediaControllerCompat.PlaybackInfo} to {@link AudioAttributes}. */
  public static AudioAttributes convertToAudioAttributes(
      @Nullable MediaControllerCompat.PlaybackInfo playbackInfoCompat) {
    if (playbackInfoCompat == null) {
      return AudioAttributes.DEFAULT;
    }
    return convertToAudioAttributes(playbackInfoCompat.getAudioAttributes());
  }

  /** Converts {@link AudioAttributes} into {@link AudioAttributesCompat}. */
  public static AudioAttributesCompat convertToAudioAttributesCompat(
      AudioAttributes audioAttributes) {
    return new AudioAttributesCompat.Builder()
        .setContentType(audioAttributes.contentType)
        .setFlags(audioAttributes.flags)
        .setUsage(audioAttributes.usage)
        .build();
  }

  /**
   * Gets the legacy stream type from {@link AudioAttributes}.
   *
   * @param audioAttributes audio attributes
   * @return int legacy stream type from {@link AudioManager}
   */
  public static int getLegacyStreamType(AudioAttributes audioAttributes) {
    int legacyStreamType = convertToAudioAttributesCompat(audioAttributes).getLegacyStreamType();
    if (legacyStreamType == AudioManager.USE_DEFAULT_STREAM_TYPE) {
      // Usually, AudioAttributesCompat#getLegacyStreamType() does not return
      // USE_DEFAULT_STREAM_TYPE unless the developer sets it with
      // AudioAttributesCompat.Builder#setLegacyStreamType().
      // But for safety, let's convert USE_DEFAULT_STREAM_TYPE to STREAM_MUSIC here.
      return AudioManager.STREAM_MUSIC;
    }
    return legacyStreamType;
  }

  public static <T> T getFutureResult(Future<T> future, long timeoutMs)
      throws ExecutionException, TimeoutException {
    long initialTimeMs = SystemClock.elapsedRealtime();
    long remainingTimeMs = timeoutMs;
    boolean interrupted = false;
    try {
      while (true) {
        try {
          return future.get(remainingTimeMs, MILLISECONDS);
        } catch (InterruptedException e) {
          interrupted = true;
          long elapsedTimeMs = SystemClock.elapsedRealtime() - initialTimeMs;
          if (elapsedTimeMs >= timeoutMs) {
            throw new TimeoutException();
          }
          remainingTimeMs = timeoutMs - elapsedTimeMs;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** Converts {@link MediaControllerCompat.PlaybackInfo} to {@link DeviceInfo}. */
  public static DeviceInfo convertToDeviceInfo(
      @Nullable MediaControllerCompat.PlaybackInfo playbackInfoCompat,
      @Nullable String routingControllerId) {
    if (playbackInfoCompat == null) {
      return DeviceInfo.UNKNOWN;
    }
    return new DeviceInfo.Builder(
            playbackInfoCompat.getPlaybackType()
                    == MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE
                ? DeviceInfo.PLAYBACK_TYPE_REMOTE
                : DeviceInfo.PLAYBACK_TYPE_LOCAL)
        .setMaxVolume(playbackInfoCompat.getMaxVolume())
        .setRoutingControllerId(routingControllerId)
        .build();
  }

  /** Converts {@link MediaControllerCompat.PlaybackInfo} to device volume. */
  public static int convertToDeviceVolume(
      @Nullable MediaControllerCompat.PlaybackInfo playbackInfoCompat) {
    if (playbackInfoCompat == null) {
      return 0;
    }
    return playbackInfoCompat.getCurrentVolume();
  }

  /** Converts {@link MediaControllerCompat.PlaybackInfo} to device muted. */
  public static boolean convertToIsDeviceMuted(
      @Nullable MediaControllerCompat.PlaybackInfo playbackInfoCompat) {
    if (playbackInfoCompat == null) {
      return false;
    }
    return playbackInfoCompat.getCurrentVolume() == 0;
  }

  /**
   * Converts a {@linkplain Bundle custom browse action} to a {@link CommandButton}. Returns null if
   * the bundle doesn't contain sufficient information to build a command button.
   *
   * <p>See <a href="https://developer.android.com/training/cars/media#custom_browse_actions">Custom
   * Browse Actions for Automotive OS</a>.
   *
   * @param browseActionBundle The bundle containing the information of a browse action.
   * @return The resulting {@link CommandButton} or null.
   */
  @Nullable
  public static CommandButton convertCustomBrowseActionToCommandButton(Bundle browseActionBundle) {
    String commandAction =
        browseActionBundle.getString(
            androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID);
    if (commandAction == null) {
      return null;
    }
    @Nullable
    CommandButton.Builder commandButton =
        new CommandButton.Builder()
            .setSessionCommand(new SessionCommand(commandAction, Bundle.EMPTY));
    String label =
        browseActionBundle.getString(
            androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL);
    if (label != null) {
      commandButton.setDisplayName(label);
    }
    String iconUri =
        browseActionBundle.getString(
            androidx.media3.session.legacy.MediaConstants
                .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI);
    if (iconUri != null) {
      try {
        commandButton.setIconUri(Uri.parse(iconUri));
      } catch (Throwable t) {
        Log.e(TAG, "error parsing icon URI of legacy browser action " + commandAction, t);
      }
    }
    Bundle actionExtras =
        browseActionBundle.getBundle(
            androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_EXTRAS);
    if (actionExtras != null) {
      commandButton.setExtras(actionExtras);
    }
    return commandButton.build();
  }

  /**
   * Converts a {@link CommandButton} to a {@link Bundle} according to the browse action
   * specification of Automotive OS.
   *
   * <p>See <a href="https://developer.android.com/training/cars/media#custom_browse_actions">Custom
   * Browse Actions for Automotive OS</a>.
   *
   * @param commandButton The {@link CommandButton} to convert.
   * @return The resulting {@link Bundle}.
   */
  public static Bundle convertToBundle(CommandButton commandButton) {
    Bundle buttonBundle = new Bundle();
    if (commandButton.sessionCommand != null) {
      buttonBundle.putString(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID,
          commandButton.sessionCommand.customAction);
    }
    buttonBundle.putString(
        androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL,
        commandButton.displayName.toString());
    if (commandButton.iconUri != null) {
      buttonBundle.putString(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI,
          commandButton.iconUri.toString());
    }
    if (!commandButton.extras.isEmpty()) {
      buttonBundle.putBundle(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_EXTRAS,
          commandButton.extras);
    }
    return buttonBundle;
  }

  /**
   * Gets the max number of commands for media items from the {@linkplain
   * androidx.media3.session.legacy.MediaBrowserServiceCompat#onGetRoot(String, int, Bundle) root
   * hints} of a legacy {@link MediaBrowserCompat} that connects.
   *
   * @param rootHints The root hints passed by the legacy browser when connecting.
   * @return The specified max number of commands per media items, or 0 if not specified.
   */
  public static int extractMaxCommandsForMediaItemFromRootHints(Bundle rootHints) {
    return max(
        0,
        rootHints.getInt(
            androidx.media3.session.legacy.MediaConstants
                .BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT,
            /* defaultValue= */ 0));
  }

  private static boolean convertToIsEnded(
      PlaybackStateCompat playbackStateCompat,
      @Nullable MediaMetadataCompat currentMediaMetadata,
      long timeDiffMs) {
    long durationMs = convertToDurationMs(currentMediaMetadata);
    if (durationMs == C.TIME_UNSET) {
      return false;
    }
    long currentPositionMs =
        convertToCurrentPositionMs(playbackStateCompat, currentMediaMetadata, timeDiffMs);
    return currentPositionMs >= durationMs;
  }

  private static byte[] convertToByteArray(Bitmap bitmap) throws IOException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      bitmap.compress(Bitmap.CompressFormat.PNG, /* ignored */ 0, stream);
      return stream.toByteArray();
    }
  }

  private LegacyConversions() {}
}
