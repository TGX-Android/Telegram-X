/*
 * Copyright 2020 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media3.session.MediaConstants.EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_COMPLETION_STATUS;
import static androidx.media3.session.MediaConstants.EXTRAS_KEY_MEDIA_TYPE_COMPAT;
import static androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED;
import static androidx.media3.session.MediaConstants.EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY;
import static androidx.media3.session.legacy.MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
import static androidx.media3.session.legacy.MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
import static androidx.media3.session.legacy.MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS;
import static androidx.media3.session.legacy.MediaMetadataCompat.METADATA_KEY_DURATION;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import android.text.SpannedString;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.HeartRating;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PercentageRating;
import androidx.media3.common.Player;
import androidx.media3.common.Rating;
import androidx.media3.common.StarRating;
import androidx.media3.common.ThumbRating;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.session.legacy.AudioAttributesCompat;
import androidx.media3.session.legacy.MediaBrowserCompat;
import androidx.media3.session.legacy.MediaControllerCompat;
import androidx.media3.session.legacy.MediaDescriptionCompat;
import androidx.media3.session.legacy.MediaMetadataCompat;
import androidx.media3.session.legacy.MediaSessionCompat;
import androidx.media3.session.legacy.PlaybackStateCompat;
import androidx.media3.session.legacy.RatingCompat;
import androidx.media3.session.legacy.VolumeProviderCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/** Tests for {@link LegacyConversions}. */
@RunWith(AndroidJUnit4.class)
public final class LegacyConversionsTest {

  private Context context;
  private BitmapLoader bitmapLoader;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    bitmapLoader = new CacheBitmapLoader(new DataSourceBitmapLoader(context));
  }

  @Test
  public void convertToMediaItem_browserItemToMediaItem() {
    String mediaId = "testId";
    String title = "testTitle";
    MediaDescriptionCompat descriptionCompat =
        new MediaDescriptionCompat.Builder().setMediaId(mediaId).setTitle(title).build();
    MediaBrowserCompat.MediaItem browserItem =
        new MediaBrowserCompat.MediaItem(descriptionCompat, /* flags= */ 0);

    MediaItem mediaItem = LegacyConversions.convertToMediaItem(browserItem);
    assertThat(mediaItem.mediaId).isEqualTo(mediaId);
    assertThat(mediaItem.mediaMetadata.title.toString()).isEqualTo(title);
  }

  @Test
  public void convertToMediaItem_queueItemToMediaItem() {
    String mediaId = "testMediaId";
    String title = "testTitle";
    MediaDescriptionCompat descriptionCompat =
        new MediaDescriptionCompat.Builder().setMediaId(mediaId).setTitle(title).build();
    MediaSessionCompat.QueueItem queueItem =
        new MediaSessionCompat.QueueItem(descriptionCompat, /* id= */ 1);
    MediaItem mediaItem = LegacyConversions.convertToMediaItem(queueItem);
    assertThat(mediaItem.mediaId).isEqualTo(mediaId);
    assertThat(mediaItem.mediaMetadata.title.toString()).isEqualTo(title);
  }

  @Test
  public void convertBrowserItemListToMediaItemList() {
    ImmutableList<MediaBrowserCompat.MediaItem> browserItems =
        ImmutableList.of(
            new MediaBrowserCompat.MediaItem(
                new MediaDescriptionCompat.Builder().setMediaId("browserItem_1").build(),
                /* flags= */ 0),
            new MediaBrowserCompat.MediaItem(
                new MediaDescriptionCompat.Builder().setMediaId("browserItem_2").build(),
                /* flags= */ 0),
            new MediaBrowserCompat.MediaItem(
                new MediaDescriptionCompat.Builder().setMediaId("browserItem_3").build(),
                /* flags= */ 0));

    List<MediaItem> mediaItems =
        LegacyConversions.convertBrowserItemListToMediaItemList(browserItems);

    assertThat(mediaItems).hasSize(3);
    assertThat(mediaItems.get(0).mediaId).isEqualTo(browserItems.get(0).getMediaId());
    assertThat(mediaItems.get(1).mediaId).isEqualTo(browserItems.get(1).getMediaId());
    assertThat(mediaItems.get(2).mediaId).isEqualTo(browserItems.get(2).getMediaId());
  }

  @Test
  public void convertToQueueItem_withArtworkData() throws Exception {
    MediaItem mediaItem = createMediaItemWithArtworkData("testId", /* durationMs= */ 10_000L);
    MediaMetadata mediaMetadata = mediaItem.mediaMetadata;
    ListenableFuture<Bitmap> bitmapFuture = bitmapLoader.decodeBitmap(mediaMetadata.artworkData);
    @Nullable Bitmap bitmap = bitmapFuture.get(10, SECONDS);

    MediaSessionCompat.QueueItem queueItem =
        LegacyConversions.convertToQueueItem(
            mediaItem,
            /** mediaItemIndex= */
            100,
            bitmap);

    assertThat(queueItem.getQueueId()).isEqualTo(100);
    assertThat(queueItem.getDescription().getIconBitmap()).isNotNull();
  }

  @Test
  public void convertToMediaDescriptionCompat_setsExpectedValues() {
    Bundle extras = new Bundle();
    extras.putInt(EXTRAS_KEY_COMPLETION_STATUS, EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED);
    MediaMetadata metadata =
        new MediaMetadata.Builder()
            .setTitle("testTitle")
            .setArtist("testArtist")
            .setAlbumTitle("testAlbumTitle")
            .setWriter("testWriter")
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setDurationMs(10_000L)
            .setExtras(extras)
            .build();
    MediaItem mediaItem =
        new MediaItem.Builder().setMediaId("testId").setMediaMetadata(metadata).build();

    MediaDescriptionCompat descriptionCompat =
        LegacyConversions.convertToMediaDescriptionCompat(mediaItem, /* artworkBitmap= */ null);

    assertThat(descriptionCompat.getMediaId()).isEqualTo("testId");
    assertThat(descriptionCompat.getTitle().toString()).isEqualTo("testTitle");
    assertThat(descriptionCompat.getSubtitle().toString()).isEqualTo("testArtist");
    assertThat(descriptionCompat.getDescription().toString()).isEqualTo("testAlbumTitle");
    assertThat(descriptionCompat.getExtras().getLong(EXTRAS_KEY_MEDIA_TYPE_COMPAT))
        .isEqualTo(MediaMetadata.MEDIA_TYPE_MUSIC);
    assertThat(descriptionCompat.getExtras().getInt(EXTRAS_KEY_COMPLETION_STATUS))
        .isEqualTo(EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED);
  }

  @Test
  public void convertToMediaDescriptionCompat_displayTitleAndTitleHandledCorrectly() {
    MediaMetadata metadataWithTitleOnly =
        new MediaMetadata.Builder()
            .setTitle("title")
            .setSubtitle("subtitle")
            .setDescription("description")
            .setArtist("artist")
            .setAlbumTitle("albumTitle")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build();
    MediaItem mediaItemWithTitleOnly =
        new MediaItem.Builder().setMediaMetadata(metadataWithTitleOnly).build();
    MediaMetadata metadataWithDisplayTitleOnly =
        new MediaMetadata.Builder()
            .setDisplayTitle("displayTitle")
            .setSubtitle("subtitle")
            .setDescription("description")
            .setArtist("artist")
            .setAlbumTitle("albumTitle")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build();
    MediaItem mediaItemWithDisplayTitleOnly =
        new MediaItem.Builder().setMediaMetadata(metadataWithDisplayTitleOnly).build();
    MediaMetadata metadataWithDisplayTitleAndTitle =
        new MediaMetadata.Builder()
            .setDisplayTitle("displayTitle")
            .setTitle("title")
            .setSubtitle("subtitle")
            .setDescription("description")
            .setArtist("artist")
            .setAlbumTitle("albumTitle")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build();
    MediaItem mediaItemWithDisplayTitleAndTitle =
        new MediaItem.Builder().setMediaMetadata(metadataWithDisplayTitleAndTitle).build();

    MediaDescriptionCompat descriptionCompatWithTitleOnly =
        LegacyConversions.convertToMediaDescriptionCompat(
            mediaItemWithTitleOnly, /* artworkBitmap= */ null);
    MediaDescriptionCompat descriptionCompatWithDisplayTitleOnly =
        LegacyConversions.convertToMediaDescriptionCompat(
            mediaItemWithDisplayTitleOnly, /* artworkBitmap= */ null);
    MediaDescriptionCompat descriptionCompatWithDisplayTitleAndTitle =
        LegacyConversions.convertToMediaDescriptionCompat(
            mediaItemWithDisplayTitleAndTitle, /* artworkBitmap= */ null);

    MediaItem convertedMediaItemWithTitleOnly =
        LegacyConversions.convertToMediaItem(descriptionCompatWithTitleOnly);
    MediaItem convertedMediaItemWithDisplayTitleOnly =
        LegacyConversions.convertToMediaItem(descriptionCompatWithDisplayTitleOnly);
    MediaItem convertedMediaItemWithDisplayTitleAndTitle =
        LegacyConversions.convertToMediaItem(descriptionCompatWithDisplayTitleAndTitle);

    assertThat(convertedMediaItemWithTitleOnly.mediaMetadata.title.toString()).isEqualTo("title");
    assertThat(convertedMediaItemWithTitleOnly.mediaMetadata.subtitle.toString())
        .isEqualTo("artist");
    assertThat(convertedMediaItemWithTitleOnly.mediaMetadata.description.toString())
        .isEqualTo("albumTitle");
    assertThat(convertedMediaItemWithTitleOnly.mediaMetadata.displayTitle).isNull();
    assertThat(convertedMediaItemWithTitleOnly.mediaMetadata.artist).isNull();
    assertThat(convertedMediaItemWithTitleOnly.mediaMetadata.albumTitle).isNull();
    assertThat(convertedMediaItemWithDisplayTitleOnly.mediaMetadata.title).isNull();
    assertThat(convertedMediaItemWithDisplayTitleOnly.mediaMetadata.subtitle.toString())
        .isEqualTo("subtitle");
    assertThat(convertedMediaItemWithDisplayTitleOnly.mediaMetadata.description.toString())
        .isEqualTo("description");
    assertThat(convertedMediaItemWithDisplayTitleOnly.mediaMetadata.displayTitle.toString())
        .isEqualTo("displayTitle");
    assertThat(convertedMediaItemWithDisplayTitleOnly.mediaMetadata.artist).isNull();
    assertThat(convertedMediaItemWithDisplayTitleOnly.mediaMetadata.albumTitle).isNull();
    assertThat(convertedMediaItemWithDisplayTitleAndTitle.mediaMetadata.title.toString())
        .isEqualTo("title");
    assertThat(convertedMediaItemWithDisplayTitleAndTitle.mediaMetadata.subtitle.toString())
        .isEqualTo("subtitle");
    assertThat(convertedMediaItemWithDisplayTitleAndTitle.mediaMetadata.description.toString())
        .isEqualTo("description");
    assertThat(convertedMediaItemWithDisplayTitleAndTitle.mediaMetadata.displayTitle.toString())
        .isEqualTo("displayTitle");
    assertThat(convertedMediaItemWithDisplayTitleAndTitle.mediaMetadata.artist).isNull();
    assertThat(convertedMediaItemWithDisplayTitleAndTitle.mediaMetadata.albumTitle).isNull();
  }

  @Test
  public void
      convertToMediaDescriptionCompat_withoutDisplayTitleWithSubtitle_subtitleUsedAsSubtitle() {
    MediaMetadata metadata =
        new MediaMetadata.Builder().setTitle("a_title").setSubtitle("a_subtitle").build();
    MediaItem mediaItem =
        new MediaItem.Builder().setMediaId("testId").setMediaMetadata(metadata).build();

    MediaDescriptionCompat descriptionCompat =
        LegacyConversions.convertToMediaDescriptionCompat(mediaItem, /* artworkBitmap= */ null);

    assertThat(descriptionCompat.getTitle().toString()).isEqualTo("a_title");
    assertThat(descriptionCompat.getSubtitle().toString()).isEqualTo("a_subtitle");
  }

  @Test
  public void convertToMediaDescriptionCompat_withDisplayTitleAndSubtitle_subtitleUsedAsSubtitle() {
    MediaMetadata metadata =
        new MediaMetadata.Builder()
            .setDisplayTitle("a_display_title")
            .setSubtitle("a_subtitle")
            .build();
    MediaItem mediaItem =
        new MediaItem.Builder().setMediaId("testId").setMediaMetadata(metadata).build();

    MediaDescriptionCompat descriptionCompat =
        LegacyConversions.convertToMediaDescriptionCompat(mediaItem, /* artworkBitmap= */ null);

    assertThat(descriptionCompat.getTitle().toString()).isEqualTo("a_display_title");
    assertThat(descriptionCompat.getSubtitle().toString()).isEqualTo("a_subtitle");
  }

  @Test
  public void convertToQueueItemId() {
    assertThat(LegacyConversions.convertToQueueItemId(C.INDEX_UNSET))
        .isEqualTo(MediaSessionCompat.QueueItem.UNKNOWN_ID);
    assertThat(LegacyConversions.convertToQueueItemId(100)).isEqualTo(100);
  }

  @Test
  public void convertToMediaMetadata_withoutTitle() {
    assertThat(LegacyConversions.convertToMediaMetadata((CharSequence) null))
        .isEqualTo(MediaMetadata.EMPTY);
  }

  @Test
  public void convertToMediaMetadata_withTitle() {
    String title = "title";
    assertThat(LegacyConversions.convertToMediaMetadata(title).title.toString()).isEqualTo(title);
  }

  @Test
  public void convertToMediaMetadata_withCustomKey() {
    MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
    builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "title");
    builder.putLong(EXTRAS_KEY_MEDIA_TYPE_COMPAT, (long) MediaMetadata.MEDIA_TYPE_MUSIC);
    builder.putString("custom_key", "value");
    MediaMetadataCompat testMediaMetadataCompat = builder.build();

    MediaMetadata mediaMetadata =
        LegacyConversions.convertToMediaMetadata(testMediaMetadataCompat, RatingCompat.RATING_NONE);

    assertThat(mediaMetadata.title.toString()).isEqualTo("title");
    assertThat(mediaMetadata.mediaType).isEqualTo(MediaMetadata.MEDIA_TYPE_MUSIC);
    assertThat(mediaMetadata.extras).isNotNull();
    assertThat(mediaMetadata.extras.getString("custom_key")).isEqualTo("value");
    assertThat(mediaMetadata.extras.containsKey(MediaMetadataCompat.METADATA_KEY_TITLE)).isFalse();
    assertThat(mediaMetadata.extras.containsKey(EXTRAS_KEY_MEDIA_TYPE_COMPAT)).isFalse();
  }

  @Test
  public void convertToMediaMetadata_roundTripViaMediaMetadataCompat_returnsEqualMediaItemMetadata()
      throws Exception {
    MediaItem testMediaItem = createMediaItemWithArtworkData("testZZZ", /* durationMs= */ 10_000L);
    MediaMetadata testMediaMetadata = testMediaItem.mediaMetadata;
    @Nullable Bitmap testArtworkBitmap = null;
    @Nullable
    ListenableFuture<Bitmap> bitmapFuture = bitmapLoader.loadBitmapFromMetadata(testMediaMetadata);
    if (bitmapFuture != null) {
      testArtworkBitmap = bitmapFuture.get(10, SECONDS);
    }
    MediaMetadataCompat testMediaMetadataCompat =
        LegacyConversions.convertToMediaMetadataCompat(
            testMediaMetadata,
            "mediaId",
            Uri.parse("http://example.com"),
            /* durationMs= */ C.TIME_UNSET,
            testArtworkBitmap);

    MediaMetadata mediaMetadata =
        LegacyConversions.convertToMediaMetadata(testMediaMetadataCompat, RatingCompat.RATING_NONE);

    assertThat(mediaMetadata).isEqualTo(testMediaMetadata);
    assertThat(mediaMetadata.artworkData).isNotNull();
  }

  @Test
  public void convertToMediaMetadata_displayTitleKeyOnly_movedToTitle() {
    MediaMetadataCompat testMediaMetadataCompat =
        new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "displayTitle")
            .build();

    MediaMetadata mediaMetadata =
        LegacyConversions.convertToMediaMetadata(testMediaMetadataCompat, RatingCompat.RATING_NONE);

    assertThat(mediaMetadata.title.toString()).isEqualTo("displayTitle");
    assertThat(mediaMetadata.displayTitle).isNull();
  }

  @Test
  public void
      convertToMediaMetadata_roundTripViaMediaDescriptionCompat_returnsEqualMediaItemMetadata()
          throws Exception {
    MediaItem testMediaItem =
        createMediaItemWithArtworkData("testZZZ", /* durationMs= */ C.TIME_UNSET);
    MediaMetadata testMediaMetadata = testMediaItem.mediaMetadata;
    @Nullable Bitmap testArtworkBitmap = null;
    @Nullable
    ListenableFuture<Bitmap> bitmapFuture = bitmapLoader.loadBitmapFromMetadata(testMediaMetadata);
    if (bitmapFuture != null) {
      testArtworkBitmap = bitmapFuture.get(10, SECONDS);
    }
    MediaDescriptionCompat mediaDescriptionCompat =
        LegacyConversions.convertToMediaDescriptionCompat(testMediaItem, testArtworkBitmap);

    MediaMetadata mediaMetadata =
        LegacyConversions.convertToMediaMetadata(mediaDescriptionCompat, RatingCompat.RATING_NONE);

    assertThat(mediaMetadata).isEqualTo(testMediaMetadata);
    assertThat(mediaMetadata.artworkData).isNotNull();
  }

  @Test
  public void convertToMediaMetadataCompat_withMediaType_setsMediaType() {
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setMediaMetadata(
                new MediaMetadata.Builder().setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build())
            .build();

    MediaMetadataCompat mediaMetadataCompat =
        LegacyConversions.convertToMediaMetadataCompat(
            mediaItem.mediaMetadata,
            "mediaId",
            Uri.parse("http://www.example.com"),
            /* durationMs= */ C.TIME_UNSET,
            /* artworkBitmap= */ null);

    assertThat(mediaMetadataCompat.getLong(EXTRAS_KEY_MEDIA_TYPE_COMPAT))
        .isEqualTo(MediaMetadata.MEDIA_TYPE_MUSIC);
  }

  @Test
  public void convertToMediaMetadataCompat_populatesExtrasFromMediaMetadata() {
    Bundle extras = new Bundle();
    extras.putString("customNullValueKey", null);
    extras.putString(null, "customNullKeyValue");
    extras.putString("customStringKey", "customStringValue");
    extras.putCharSequence("customCharSequenceKey", new SpannedString("customCharSequenceValue"));
    extras.putByte("customByteKey", (byte) 1);
    extras.putShort("customShortKey", (short) 5);
    extras.putInt("customIntegerKey", 10);
    extras.putLong("customLongKey", 20L);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setMediaMetadata(new MediaMetadata.Builder().setExtras(extras).build())
            .build();

    MediaMetadataCompat mediaMetadataCompat =
        LegacyConversions.convertToMediaMetadataCompat(
            mediaItem.mediaMetadata,
            "mediadId",
            Uri.parse("http://www.example.test"),
            /* durationMs= */ C.TIME_UNSET,
            /* artworkBitmap= */ null);

    assertThat(mediaMetadataCompat.getString("customNullValueKey")).isNull();
    assertThat(mediaMetadataCompat.getString(null)).isEqualTo("customNullKeyValue");
    assertThat(mediaMetadataCompat.getString("customStringKey")).isEqualTo("customStringValue");
    CharSequence customCharSequence = mediaMetadataCompat.getText("customCharSequenceKey");
    assertThat(customCharSequence).isInstanceOf(SpannedString.class);
    assertThat(customCharSequence.toString()).isEqualTo("customCharSequenceValue");
    assertThat(mediaMetadataCompat.getLong("customByteKey")).isEqualTo(1);
    assertThat(mediaMetadataCompat.getLong("customShortKey")).isEqualTo(5);
    assertThat(mediaMetadataCompat.getLong("customIntegerKey")).isEqualTo(10);
    assertThat(mediaMetadataCompat.getLong("customLongKey")).isEqualTo(20);
  }

  @Test
  public void convertBetweenRatingAndRatingCompat() {
    assertRatingEquals(
        LegacyConversions.convertToRating(null), LegacyConversions.convertToRatingCompat(null));
    assertRatingEquals(
        LegacyConversions.convertToRating(RatingCompat.newUnratedRating(RatingCompat.RATING_NONE)),
        LegacyConversions.convertToRatingCompat(null));
    assertRatingEquals(
        LegacyConversions.convertToRating(RatingCompat.newUnratedRating(RatingCompat.RATING_HEART)),
        LegacyConversions.convertToRatingCompat(new HeartRating()));
    assertRatingEquals(
        LegacyConversions.convertToRating(RatingCompat.newHeartRating(true)),
        LegacyConversions.convertToRatingCompat(new HeartRating(true)));
    assertRatingEquals(
        LegacyConversions.convertToRating(RatingCompat.newThumbRating(false)),
        LegacyConversions.convertToRatingCompat(new ThumbRating(false)));
    assertRatingEquals(
        LegacyConversions.convertToRating(RatingCompat.newThumbRating(false)),
        LegacyConversions.convertToRatingCompat(new ThumbRating(false)));
    assertRatingEquals(
        LegacyConversions.convertToRating(
            RatingCompat.newStarRating(RatingCompat.RATING_3_STARS, 1f)),
        LegacyConversions.convertToRatingCompat(new StarRating(3, 1f)));
    assertRatingEquals(
        LegacyConversions.convertToRating(
            RatingCompat.newStarRating(RatingCompat.RATING_4_STARS, 0f)),
        LegacyConversions.convertToRatingCompat(new StarRating(4, 0f)));
    assertRatingEquals(
        LegacyConversions.convertToRating(
            RatingCompat.newStarRating(RatingCompat.RATING_5_STARS, 5f)),
        LegacyConversions.convertToRatingCompat(new StarRating(5, 5f)));
    assertRatingEquals(
        LegacyConversions.convertToRating(RatingCompat.newPercentageRating(80f)),
        LegacyConversions.convertToRatingCompat(new PercentageRating(80f)));
  }

  void assertRatingEquals(Rating rating, RatingCompat ratingCompat) {
    if (rating == null && ratingCompat == null) {
      return;
    }
    assertThat(rating.isRated()).isEqualTo(ratingCompat.isRated());
    if (rating instanceof HeartRating) {
      assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_HEART);
      assertThat(((HeartRating) rating).isHeart()).isEqualTo(ratingCompat.hasHeart());
    } else if (rating instanceof ThumbRating) {
      assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_THUMB_UP_DOWN);
      assertThat(((ThumbRating) rating).isThumbsUp()).isEqualTo(ratingCompat.isThumbUp());
    } else if (rating instanceof StarRating) {
      StarRating starRating = (StarRating) rating;
      switch (starRating.getMaxStars()) {
        case 3:
          assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_3_STARS);
          break;
        case 4:
          assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_4_STARS);
          break;
        case 5:
          assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_5_STARS);
          break;
        default: // fall out
      }
      assertThat(starRating.getStarRating()).isEqualTo(ratingCompat.getStarRating());
    } else if (rating instanceof PercentageRating) {
      assertThat(ratingCompat.getRatingStyle()).isEqualTo(RatingCompat.RATING_PERCENTAGE);
      assertThat(((PercentageRating) rating).getPercent())
          .isEqualTo(ratingCompat.getPercentRating());
    }
  }

  @Test
  public void convertToLibraryParams() {
    assertThat(LegacyConversions.convertToLibraryParams(context, null)).isNull();
    Bundle rootHints = new Bundle();
    rootHints.putString("key", "value");
    rootHints.putInt(BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, FLAG_BROWSABLE);
    rootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_OFFLINE, true);
    rootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true);
    rootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_SUGGESTED, true);

    MediaLibraryService.LibraryParams params =
        LegacyConversions.convertToLibraryParams(context, rootHints);

    assertThat(params.extras.getString("key")).isEqualTo("value");
    assertThat(params.extras.getBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isTrue();
    assertThat(params.extras.containsKey(BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS))
        .isFalse();
    assertThat(params.isOffline).isTrue();
    assertThat(params.isRecent).isTrue();
    assertThat(params.isSuggested).isTrue();
  }

  @Test
  public void convertToLibraryParams_rootHintsBrowsableNoFlagSet_browsableOnlyFalse() {
    Bundle rootHints = new Bundle();
    rootHints.putInt(BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, 0);

    MediaLibraryService.LibraryParams params =
        LegacyConversions.convertToLibraryParams(context, rootHints);

    assertThat(params.extras.getBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isFalse();
  }

  @Test
  public void convertToLibraryParams_rootHintsPlayableFlagSet_browsableOnlyFalse() {
    Bundle rootHints = new Bundle();
    rootHints.putInt(
        BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, FLAG_PLAYABLE | FLAG_BROWSABLE);

    MediaLibraryService.LibraryParams params =
        LegacyConversions.convertToLibraryParams(context, rootHints);

    assertThat(params.extras.getBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isFalse();
  }

  @Test
  public void convertToLibraryParams_rootHintsBrowsableAbsentKey_browsableOnlyFalse() {
    MediaLibraryService.LibraryParams params =
        LegacyConversions.convertToLibraryParams(context, /* legacyBundle= */ Bundle.EMPTY);

    assertThat(params.extras.getBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isFalse();
  }

  @Test
  public void convertToRootHints() {
    assertThat(LegacyConversions.convertToRootHints(null)).isNull();
    Bundle extras = new Bundle();
    extras.putString("key", "value");
    extras.putBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY, true);
    MediaLibraryService.LibraryParams param =
        new MediaLibraryService.LibraryParams.Builder()
            .setOffline(true)
            .setRecent(true)
            .setSuggested(true)
            .setExtras(extras)
            .build();

    Bundle rootHints = LegacyConversions.convertToRootHints(param);

    assertThat(
            rootHints.getInt(
                BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, /* defaultValue= */ 0))
        .isEqualTo(FLAG_BROWSABLE);
    assertThat(rootHints.getString("key")).isEqualTo("value");
    assertThat(rootHints.get(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isNull();
    assertThat(rootHints.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_OFFLINE)).isTrue();
    assertThat(rootHints.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT)).isTrue();
    assertThat(rootHints.getBoolean(MediaBrowserService.BrowserRoot.EXTRA_SUGGESTED)).isTrue();
  }

  @Test
  public void convertToRootHints_browsableOnlyFalse_correctLegacyBrowsableFlags() {
    Bundle extras = new Bundle();
    extras.putBoolean(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY, false);
    MediaLibraryService.LibraryParams param =
        new MediaLibraryService.LibraryParams.Builder().setExtras(extras).build();

    Bundle rootHints = LegacyConversions.convertToRootHints(param);

    assertThat(
            rootHints.getInt(
                BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS, /* defaultValue= */ -1))
        .isEqualTo(FLAG_BROWSABLE | FLAG_PLAYABLE);
    assertThat(rootHints.get(EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY)).isNull();
  }

  @Test
  public void convertToRootHints_browsableAbsentKey_noLegacyKeyAdded() {
    MediaLibraryService.LibraryParams param =
        new MediaLibraryService.LibraryParams.Builder().build();

    Bundle rootHints = LegacyConversions.convertToRootHints(param);

    assertThat(rootHints.get(BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS)).isNull();
  }

  @Test
  public void convertToSessionCommands_withCustomAction_containsCustomAction() {
    PlaybackStateCompat playbackState =
        new PlaybackStateCompat.Builder()
            .addCustomAction("action", "name", /* icon= */ 100)
            .build();
    SessionCommands sessionCommands =
        LegacyConversions.convertToSessionCommands(playbackState, /* isSessionReady= */ true);
    assertThat(sessionCommands.contains(new SessionCommand("action", /* extras= */ Bundle.EMPTY)))
        .isTrue();
  }

  @Config(minSdk = 21)
  @Test
  public void convertToSessionCommands_whenSessionIsNotReadyOnSdk21_disallowsRating() {
    SessionCommands sessionCommands =
        LegacyConversions.convertToSessionCommands(/* state= */ null, /* isSessionReady= */ false);
    assertThat(sessionCommands.contains(SessionCommand.COMMAND_CODE_SESSION_SET_RATING)).isFalse();
  }

  @Test
  public void convertToPlayerCommands_withNoActions_onlyDefaultCommandsAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(/* capabilities= */ 0).build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsExactly(
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            Player.COMMAND_RELEASE);
  }

  @Test
  public void convertToPlayerCommands_withJustPlayActionWhileNotReady_playPauseCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_ERROR, /* position= */ 0, /* playbackSpeed= */ 1f)
            .setActions(PlaybackStateCompat.ACTION_PLAY)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void convertToPlayerCommands_withJustPlayActionWhileReady_playPauseCommandNotAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_BUFFERING, /* position= */ 0, /* playbackSpeed= */ 1f)
            .setActions(PlaybackStateCompat.ACTION_PLAY)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).doesNotContain(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void
      convertToPlayerCommands_withJustPauseActionWhileNotReady_playPauseCommandNotAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_ERROR, /* position= */ 0, /* playbackSpeed= */ 1f)
            .setActions(PlaybackStateCompat.ACTION_PAUSE)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).doesNotContain(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void convertToPlayerCommands_withJustPauseActionWhileReady_playPauseCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_BUFFERING, /* position= */ 0, /* playbackSpeed= */ 1f)
            .setActions(PlaybackStateCompat.ACTION_PAUSE)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void convertToPlayerCommands_withPlayAndPauseAction_playPauseCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void convertToPlayerCommands_withPlayPauseAction_playPauseCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE).build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_PLAY_PAUSE);
  }

  @Test
  public void convertToPlayerCommands_withPrepareAction_prepareCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_PREPARE).build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_PREPARE);
  }

  @Test
  public void convertToPlayerCommands_withRewindAction_seekBackCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_REWIND).build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_SEEK_BACK);
  }

  @Test
  public void convertToPlayerCommands_withFastForwardAction_seekForwardCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_FAST_FORWARD)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_SEEK_FORWARD);
  }

  @Test
  public void convertToPlayerCommands_withSeekToAction_seekInCurrentMediaItemCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_SEEK_TO).build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
  }

  @Test
  public void convertToPlayerCommands_withSkipToNextAction_seekToNextCommandsAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM);
  }

  @Test
  public void convertToPlayerCommands_withSkipToPreviousAction_seekToPreviousCommandsAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
  }

  @Test
  public void
      convertToPlayerCommands_withPlayFromActionsWithoutPrepareFromAction_setMediaItemCommandNotAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                    | PlaybackStateCompat.ACTION_PLAY_FROM_URI)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsNoneOf(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE);
  }

  @Test
  public void
      convertToPlayerCommands_withPrepareFromActionsWithoutPlayFromAction_setMediaItemCommandNotAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_URI)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsNoneOf(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE);
  }

  @Test
  public void
      convertToPlayerCommands_withPlayFromAndPrepareFromMediaId_setMediaItemPrepareAndPlayAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE);
  }

  @Test
  public void
      convertToPlayerCommands_withPlayFromAndPrepareFromSearch_setMediaItemPrepareAndPlayAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE);
  }

  @Test
  public void
      convertToPlayerCommands_withPlayFromAndPrepareFromUri_setMediaItemPrepareAndPlayAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_FROM_URI
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_URI)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_PREPARE);
  }

  @Test
  public void convertToPlayerCommands_withSetPlaybackSpeedAction_setSpeedCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_SET_SPEED_AND_PITCH);
  }

  @Test
  public void convertToPlayerCommands_withStopAction_stopCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(PlaybackStateCompat.ACTION_STOP).build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_STOP);
  }

  @Test
  public void convertToPlayerCommands_withRelativeVolumeControl_adjustVolumeCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(/* capabilities= */ 0).build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_RELATIVE,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands)).contains(Player.COMMAND_ADJUST_DEVICE_VOLUME);
    assertThat(getCommandsAsList(playerCommands)).doesNotContain(Player.COMMAND_SET_DEVICE_VOLUME);
  }

  @Test
  public void convertToPlayerCommands_withAbsoluteVolumeControl_adjustVolumeCommandAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder().setActions(/* capabilities= */ 0).build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_ADJUST_DEVICE_VOLUME, Player.COMMAND_SET_DEVICE_VOLUME);
  }

  @Test
  public void
      convertToPlayerCommands_withShuffleRepeatActionsAndSessionReady_shuffleAndRepeatCommandsAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                    | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ true);

    assertThat(getCommandsAsList(playerCommands))
        .containsAtLeast(Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE);
  }

  @Test
  public void
      convertToPlayerCommands_withShuffleRepeatActionsAndSessionNotReady_shuffleAndRepeatCommandsNotAvailable() {
    PlaybackStateCompat playbackStateCompat =
        new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                    | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)
            .build();

    Player.Commands playerCommands =
        LegacyConversions.convertToPlayerCommands(
            playbackStateCompat,
            /* volumeControlType= */ VolumeProviderCompat.VOLUME_CONTROL_FIXED,
            /* sessionFlags= */ 0,
            /* isSessionReady= */ false);

    assertThat(getCommandsAsList(playerCommands))
        .containsNoneOf(Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_SET_SHUFFLE_MODE);
  }

  @Test
  public void convertToMediaButtonPreferences_withNull_returnsEmptyList() {
    assertThat(
            LegacyConversions.convertToMediaButtonPreferences(
                null, Player.Commands.EMPTY, Bundle.EMPTY))
        .isEmpty();
  }

  @Test
  public void convertToMediaButtonPreferences_withoutIconConstantInExtras() {
    String extraKey = "key";
    String extraValue = "value";
    String actionStr = "action";
    String displayName = "display_name";
    int iconRes = 21;
    Bundle extras = new Bundle();
    extras.putString(extraKey, extraValue);
    PlaybackStateCompat.CustomAction action =
        new PlaybackStateCompat.CustomAction.Builder(actionStr, displayName, iconRes)
            .setExtras(extras)
            .build();
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_NONE,
                /* position= */ 0,
                /* playbackSpeed= */ 1,
                /* updateTime= */ 100)
            .addCustomAction(action)
            .build();

    ImmutableList<CommandButton> buttons =
        LegacyConversions.convertToMediaButtonPreferences(
            state, Player.Commands.EMPTY, Bundle.EMPTY);

    assertThat(buttons).hasSize(1);
    CommandButton button = buttons.get(0);
    assertThat(button.displayName.toString()).isEqualTo(displayName);
    assertThat(button.isEnabled).isTrue();
    assertThat(button.iconResId).isEqualTo(iconRes);
    assertThat(button.sessionCommand.customAction).isEqualTo(actionStr);
    assertThat(button.sessionCommand.customExtras.getString(extraKey)).isEqualTo(extraValue);
    assertThat(button.icon).isEqualTo(CommandButton.ICON_UNDEFINED);
  }

  @Test
  public void convertToMediaButtonPreferences_withIconConstantInExtras() {
    String actionStr = "action";
    String displayName = "display_name";
    int iconRes = 21;
    Bundle extras = new Bundle();
    extras.putInt(EXTRAS_KEY_COMMAND_BUTTON_ICON_COMPAT, CommandButton.ICON_FAST_FORWARD);
    PlaybackStateCompat.CustomAction action =
        new PlaybackStateCompat.CustomAction.Builder(actionStr, displayName, iconRes)
            .setExtras(extras)
            .build();
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_NONE,
                /* position= */ 0,
                /* playbackSpeed= */ 1,
                /* updateTime= */ 100)
            .addCustomAction(action)
            .build();

    ImmutableList<CommandButton> buttons =
        LegacyConversions.convertToMediaButtonPreferences(
            state, Player.Commands.EMPTY, Bundle.EMPTY);

    assertThat(buttons).hasSize(1);
    CommandButton button = buttons.get(0);
    assertThat(button.displayName.toString()).isEqualTo(displayName);
    assertThat(button.isEnabled).isTrue();
    assertThat(button.iconResId).isEqualTo(iconRes);
    assertThat(button.sessionCommand.customAction).isEqualTo(actionStr);
    assertThat(button.icon).isEqualTo(CommandButton.ICON_FAST_FORWARD);
  }

  @Test
  public void convertToAudioAttributes() {
    assertThat(LegacyConversions.convertToAudioAttributes((AudioAttributesCompat) null))
        .isSameInstanceAs(AudioAttributes.DEFAULT);
    assertThat(
            LegacyConversions.convertToAudioAttributes((MediaControllerCompat.PlaybackInfo) null))
        .isSameInstanceAs(AudioAttributes.DEFAULT);

    AudioAttributesCompat aaCompat =
        new AudioAttributesCompat.Builder()
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .setFlags(AudioAttributesCompat.FLAG_AUDIBILITY_ENFORCED)
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .build();
    AudioAttributes aa =
        new AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setFlags(C.FLAG_AUDIBILITY_ENFORCED)
            .setUsage(C.USAGE_MEDIA)
            .build();
    assertThat(LegacyConversions.convertToAudioAttributes(aaCompat)).isEqualTo(aa);
    assertThat(LegacyConversions.convertToAudioAttributesCompat(aa)).isEqualTo(aaCompat);
  }

  @Test
  public void convertToCurrentPosition_byDefault_returnsZero() {
    long currentPositionMs =
        LegacyConversions.convertToCurrentPositionMs(
            /* playbackStateCompat= */ null,
            /* metadataCompat= */ null,
            /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(currentPositionMs).isEqualTo(0);
  }

  @Test
  public void convertToCurrentPositionMs_withNegativePosition_adjustsToZero() {
    long testPositionMs = -100L;
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .build();
    long currentPositionMs =
        LegacyConversions.convertToCurrentPositionMs(
            state, /* metadataCompat= */ null, /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(currentPositionMs).isEqualTo(0);
  }

  @Test
  public void convertToCurrentPositionMs_withGreaterThanDuration_adjustsToDuration() {
    long testDurationMs = 100L;
    long testPositionMs = 200L;
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDurationMs).build();
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .build();
    long currentPositionMs =
        LegacyConversions.convertToCurrentPositionMs(
            state, metadata, /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(currentPositionMs).isEqualTo(testDurationMs);
  }

  @Test
  public void convertToDurationMs() {
    long testDurationMs = 100L;
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDurationMs).build();
    long currentPositionMs = LegacyConversions.convertToDurationMs(metadata);
    assertThat(currentPositionMs).isEqualTo(testDurationMs);
  }

  @Test
  public void convertToDurationMs_withNegativeDuration_returnsTimeUnset() {
    long testDurationMs = -100L;
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDurationMs).build();
    long currentPositionMs = LegacyConversions.convertToDurationMs(metadata);
    assertThat(currentPositionMs).isEqualTo(C.TIME_UNSET);
  }

  @Test
  public void convertToBufferedPositionMs() {
    long testPositionMs = 300L;
    long testBufferedPositionMs = 331L;
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(testBufferedPositionMs)
            .build();

    long bufferedPositionMs =
        LegacyConversions.convertToBufferedPositionMs(
            state, /* metadataCompat= */ null, /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(bufferedPositionMs).isEqualTo(testBufferedPositionMs);
  }

  @Test
  public void convertToBufferedPositionMs_withLessThanPosition_adjustsToPosition() {
    long testPositionMs = 300L;
    long testBufferedPositionMs = 100L;
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPositionMs, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(testBufferedPositionMs)
            .build();
    long bufferedPositionMs =
        LegacyConversions.convertToBufferedPositionMs(
            state, /* metadataCompat= */ null, /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(bufferedPositionMs).isEqualTo(testPositionMs);
  }

  @Test
  public void convertToBufferedPositionMs_withLessThanPositionAndWithTimeDiff_adjustsToPosition() {
    long testPositionMs = 200L;
    long testBufferedPositionMs = 100L;
    long testTimeDiffMs = 100;
    float testPlaybackSpeed = 1.0f;
    long expectedPositionMs = testPositionMs + (long) (testPlaybackSpeed * testTimeDiffMs);
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PLAYING, testPositionMs, testPlaybackSpeed)
            .setBufferedPosition(testBufferedPositionMs)
            .build();
    long bufferedPositionMs =
        LegacyConversions.convertToBufferedPositionMs(
            state, /* metadataCompat= */ null, testTimeDiffMs);
    assertThat(bufferedPositionMs).isEqualTo(expectedPositionMs);
  }

  @Test
  public void convertToBufferedPositionMs_withGreaterThanDuration_adjustsToDuration() {
    long testDurationMs = 100L;
    long testBufferedPositionMs = 200L;
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder().putLong(METADATA_KEY_DURATION, testDurationMs).build();
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder().setBufferedPosition(testBufferedPositionMs).build();
    long bufferedPositionMs =
        LegacyConversions.convertToBufferedPositionMs(
            state, metadata, /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(bufferedPositionMs).isEqualTo(testDurationMs);
  }

  @Test
  public void convertToTotalBufferedDurationMs() {
    long testCurrentPositionMs = 224L;
    long testBufferedPositionMs = 331L;
    long testTotalBufferedDurationMs = testBufferedPositionMs - testCurrentPositionMs;
    PlaybackStateCompat state =
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED, testCurrentPositionMs, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(testBufferedPositionMs)
            .build();

    long totalBufferedDurationMs =
        LegacyConversions.convertToTotalBufferedDurationMs(
            state, /* metadataCompat= */ null, /* timeDiffMs= */ C.INDEX_UNSET);
    assertThat(totalBufferedDurationMs).isEqualTo(testTotalBufferedDurationMs);
  }

  @Test
  public void convertToSessionError_unknownError_returnsNull() {
    SessionError sessionError =
        LegacyConversions.convertToSessionError(
            PlaybackStateCompat.STATE_PLAYING,
            PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR,
            "err message",
            Bundle.EMPTY,
            ApplicationProvider.getApplicationContext());

    assertThat(sessionError).isNull();
  }

  @Test
  public void convertToSessionError_stateError_returnsNull() {
    SessionError sessionError =
        LegacyConversions.convertToSessionError(
            PlaybackStateCompat.STATE_ERROR,
            PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED,
            "err message",
            Bundle.EMPTY,
            ApplicationProvider.getApplicationContext());

    assertThat(sessionError).isNull();
  }

  @Test
  public void convertToSessionError_errorMessageNull_useLocalizedStringResourceAsFallback() {
    SessionError sessionError =
        LegacyConversions.convertToSessionError(
            PlaybackStateCompat.STATE_PLAYING,
            PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED,
            /* errorMessage= */ null,
            Bundle.EMPTY,
            ApplicationProvider.getApplicationContext());

    assertThat(sessionError.message)
        .isEqualTo(
            ApplicationProvider.getApplicationContext()
                .getString(R.string.error_message_authentication_expired));
  }

  // TODO(b/254265256): Move this method to a central place.
  private static ImmutableList<@Player.Command Integer> getCommandsAsList(
      Player.Commands commands) {
    ImmutableList.Builder<@Player.Command Integer> list = new ImmutableList.Builder<>();
    for (int i = 0; i < commands.size(); i++) {
      list.add(commands.get(i));
    }
    return list.build();
  }

  private static MediaItem createMediaItemWithArtworkData(String mediaId, long durationMs) {
    Bundle extras = new Bundle();
    extras.putLong(
        MediaConstants.EXTRAS_KEY_IS_EXPLICIT, MediaConstants.EXTRAS_VALUE_ATTRIBUTE_PRESENT);
    MediaMetadata.Builder mediaMetadataBuilder =
        new MediaMetadata.Builder()
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .setTitle("title")
            .setDisplayTitle("displayTitle")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(extras);

    if (durationMs != C.TIME_UNSET) {
      mediaMetadataBuilder.setDurationMs(durationMs);
    }

    try {
      byte[] artworkData;
      Bitmap bitmap =
          BitmapFactory.decodeStream(
              ApplicationProvider.getApplicationContext()
                  .getResources()
                  .getAssets()
                  .open("media/png/non-motion-photo-shortened.png"));
      try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
        bitmap.compress(Bitmap.CompressFormat.PNG, /* ignored */ 0, stream);
        artworkData = stream.toByteArray();
      }
      mediaMetadataBuilder.setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER);
    } catch (IOException e) {
      fail(e.getMessage());
    }
    MediaMetadata mediaMetadata = mediaMetadataBuilder.build();
    return new MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(mediaMetadata).build();
  }
}
