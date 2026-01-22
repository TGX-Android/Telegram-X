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
package androidx.media3.session.legacy;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.media.MediaBrowserService;
import androidx.annotation.RestrictTo;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.legacy.MediaBrowserCompat.ConnectionCallback;
import java.util.ArrayList;

/** Media constants for sharing constants between media provider and consumer apps */
@UnstableApi
@RestrictTo(LIBRARY)
public final class MediaConstants {
  /**
   * Bundle key used for the account name in {@link MediaSessionCompat session} extras.
   *
   * <p>TYPE: String
   *
   * @see MediaControllerCompat#getExtras
   * @see MediaSessionCompat#setExtras
   */
  @SuppressLint("IntentName")
  public static final String SESSION_EXTRAS_KEY_ACCOUNT_NAME =
      "androidx.media.MediaSessionCompat.Extras.KEY_ACCOUNT_NAME";

  /**
   * Bundle key used for the account type in {@link MediaSessionCompat session} extras. The value
   * would vary across media applications.
   *
   * <p>TYPE: String
   *
   * @see MediaControllerCompat#getExtras
   * @see MediaSessionCompat#setExtras
   */
  @SuppressLint("IntentName")
  public static final String SESSION_EXTRAS_KEY_ACCOUNT_TYPE =
      "androidx.media.MediaSessionCompat.Extras.KEY_ACCOUNT_TYPE";

  /**
   * Bundle key used for the account auth token value in {@link MediaSessionCompat session} extras.
   * The value would vary across media applications.
   *
   * <p>TYPE: byte[]
   *
   * @see MediaControllerCompat#getExtras
   * @see MediaSessionCompat#setExtras
   */
  @SuppressLint("IntentName")
  public static final String SESSION_EXTRAS_KEY_AUTHTOKEN =
      "androidx.media.MediaSessionCompat.Extras.KEY_AUTHTOKEN";

  /**
   * Bundle key passed from {@link MediaSessionCompat} to the hosting {@link MediaControllerCompat}
   * to indicate a preference that a region of space for the skip to next control should always be
   * blocked out in the UI, even when the {@link PlaybackStateCompat#ACTION_SKIP_TO_NEXT skip to
   * next standard action} is not supported. This may be used when the session temporarily hides
   * skip to next by design.
   *
   * <p>TYPE: boolean
   *
   * @see MediaControllerCompat#getExtras()
   * @see MediaSessionCompat#setExtras(Bundle)
   */
  @SuppressLint("IntentName")
  public static final String SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_NEXT =
      "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT";

  /**
   * Bundle key passed from {@link MediaSessionCompat} to the hosting {@link MediaControllerCompat}
   * to indicate a preference that a region of space for the skip to previous control should always
   * be blocked out in the UI, even when the {@link PlaybackStateCompat#ACTION_SKIP_TO_PREVIOUS skip
   * to previous standard action} is not supported. This may be used when the session temporarily
   * hides skip to previous by design.
   *
   * <p>TYPE: boolean
   *
   * @see MediaControllerCompat#getExtras()
   * @see MediaSessionCompat#setExtras(Bundle)
   */
  @SuppressLint("IntentName")
  public static final String SESSION_EXTRAS_KEY_SLOT_RESERVATION_SKIP_TO_PREV =
      "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS";

  /**
   * Bundle key used for media content id in {@link MediaMetadataCompat metadata}, should contain
   * the same ID provided to <a href="https://developers.google.com/actions/media">Media Actions
   * Catalog</a> in reference to this title (e.g., episode, movie). This key can contain the content
   * ID of the currently playing episode or movie and can be used to help users continue watching
   * after this session is paused or stopped.
   *
   * <p>TYPE: String
   *
   * @see MediaMetadataCompat
   */
  @SuppressLint("IntentName")
  public static final String METADATA_KEY_CONTENT_ID =
      "androidx.media.MediaMetadatCompat.METADATA_KEY_CONTENT_ID";

  /**
   * Bundle key used for next episode's media content ID in {@link MediaMetadataCompat metadata},
   * following the same ID and format provided to <a
   * href="https://developers.google.com/actions/media">Media Actions Catalog</a> in reference to
   * the next episode of the current title episode. This key can contain the content ID of the
   * episode immediately following the currently playing episode and can be used to help users
   * continue watching after this episode is over. This value is only valid for TV Episode content
   * type and should be left blank for other content.
   *
   * <p>TYPE: String
   *
   * @see MediaMetadataCompat
   */
  @SuppressLint("IntentName")
  public static final String METADATA_KEY_NEXT_EPISODE_CONTENT_ID =
      "androidx.media.MediaMetadatCompat.METADATA_KEY_NEXT_EPISODE_CONTENT_ID";

  /**
   * Bundle key used for the TV series's media content ID in {@link MediaMetadataCompat metadata},
   * following the same ID and format provided to <a
   * href="https://developers.google.com/actions/media">Media Actions Catalog</a> in reference to
   * the TV series of the current title episode. This value is only valid for TV Episode content
   * type and should be left blank for other content.
   *
   * <p>TYPE: String
   *
   * @see MediaMetadataCompat
   */
  @SuppressLint("IntentName")
  public static final String METADATA_KEY_SERIES_CONTENT_ID =
      "androidx.media.MediaMetadatCompat.METADATA_KEY_SERIES_CONTENT_ID";

  /**
   * Key sent through a key-value mapping in {@link MediaMetadataCompat#getLong(String)} or in the
   * {@link MediaDescriptionCompat#getExtras()} bundle to the hosting {@link MediaBrowserCompat} to
   * indicate that the corresponding {@link MediaMetadataCompat} or {@link
   * MediaBrowserCompat.MediaItem} has explicit content (i.e. user discretion is advised when
   * viewing or listening to this content).
   *
   * <p>TYPE: long (to enable, use value {@link #METADATA_VALUE_ATTRIBUTE_PRESENT})
   *
   * @see MediaMetadataCompat#getLong(String)
   * @see MediaMetadataCompat.Builder#putLong(String, long)
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   */
  @SuppressLint("IntentName")
  public static final String METADATA_KEY_IS_EXPLICIT = "android.media.IS_EXPLICIT";

  /**
   * Key sent through a key-value mapping in {@link MediaMetadataCompat#getLong(String)} or in the
   * {@link MediaDescriptionCompat#getExtras()} bundle to the hosting {@link MediaBrowserCompat} to
   * indicate that the corresponding {@link MediaMetadataCompat} or {@link
   * MediaBrowserCompat.MediaItem} is an advertisement.
   *
   * <p>TYPE: long (to enable, use value {@link #METADATA_VALUE_ATTRIBUTE_PRESENT})
   *
   * @see MediaMetadataCompat#getLong(String)
   * @see MediaMetadataCompat.Builder#putLong(String, long)
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   */
  @SuppressLint("IntentName")
  public static final String METADATA_KEY_IS_ADVERTISEMENT = "android.media.metadata.ADVERTISEMENT";

  /**
   * Value sent through a key-value mapping of {@link MediaMetadataCompat}, or through {@link
   * Bundle} extras on a different data type, to indicate the presence of an attribute described by
   * its corresponding key.
   *
   * @see MediaMetadataCompat#getLong(String)
   * @see MediaMetadataCompat.Builder#putLong(String, long)
   */
  public static final long METADATA_VALUE_ATTRIBUTE_PRESENT = 1L;

  /**
   * Bundle key passed through root hints to the {@link MediaBrowserServiceCompat} to indicate the
   * maximum number of children of the root node that can be supported by the hosting {@link
   * MediaBrowserCompat}. Excess root children may be omitted or made less discoverable by the host.
   *
   * <p>TYPE: int
   *
   * @see MediaBrowserServiceCompat#onGetRoot(String, int, Bundle)
   * @see MediaBrowserServiceCompat#getBrowserRootHints()
   * @see MediaBrowserCompat#MediaBrowserCompat(Context,ComponentName,ConnectionCallback,Bundle)
   */
  @SuppressLint("IntentName")
  public static final String BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_LIMIT =
      "androidx.media.MediaBrowserCompat.Extras.KEY_ROOT_CHILDREN_LIMIT";

  /**
   * Bundle key passed through root hints to the {@link MediaBrowserServiceCompat} to indicate which
   * flags exposed by {@link MediaBrowserCompat.MediaItem#getFlags()} from children of the root node
   * are supported by the hosting {@link MediaBrowserCompat}. Root children with unsupported flags
   * may be omitted or made less discoverable by the host.
   *
   * <p>TYPE: int, a bit field which can be used as a mask. For example, if the value masked (using
   * bitwise AND) with {@link MediaBrowserCompat.MediaItem#FLAG_BROWSABLE} is nonzero, then the host
   * supports browsable root children. Conversely, if the masked result is zero, then the host does
   * not support them.
   *
   * @see MediaBrowserServiceCompat#onGetRoot(String, int, Bundle)
   * @see MediaBrowserServiceCompat#getBrowserRootHints()
   * @see MediaBrowserCompat#MediaBrowserCompat(Context,ComponentName,ConnectionCallback,Bundle)
   */
  @SuppressLint("IntentName")
  public static final String BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS =
      "androidx.media.MediaBrowserCompat.Extras.KEY_ROOT_CHILDREN_SUPPORTED_FLAGS";

  /**
   * Bundle key passed through root hints to the {@link MediaBrowserServiceCompat} to indicate the
   * recommended size, in pixels, for media art bitmaps. Much smaller images may not render well,
   * and much larger images may cause inefficient resource consumption.
   *
   * @see MediaBrowserServiceCompat#onGetRoot(String, int, Bundle)
   * @see MediaBrowserServiceCompat#getBrowserRootHints()
   * @see MediaBrowserCompat#MediaBrowserCompat(Context,ComponentName,ConnectionCallback,Bundle)
   * @see MediaDescriptionCompat#getIconUri()
   * @see MediaDescriptionCompat.Builder#setIconUri(Uri)
   * @see MediaDescriptionCompat#getIconBitmap()
   * @see MediaDescriptionCompat.Builder#setIconBitmap(Bitmap)
   */
  @SuppressLint("IntentName")
  public static final String BROWSER_ROOT_HINTS_KEY_MEDIA_ART_SIZE_PIXELS =
      "android.media.extras.MEDIA_ART_SIZE_HINT_PIXELS";

  /**
   * Bundle key used to indicate that the {@link MediaBrowserServiceCompat} supports showing a
   * settings page.
   *
   * <p>Use this key to populate the {@link Bundle} that you pass to the constructor of the {@link
   * MediaBrowserServiceCompat.BrowserRoot} returned by {@link MediaBrowserServiceCompat#onGetRoot}.
   * Use {@link Bundle#putParcelable(String, Parcelable)} to set a {@link PendingIntent} for this
   * key. The {@link PendingIntent} is created using the {@code CarPendingIntent#getCarApp()}
   * method.
   *
   * <p>The {@link Intent} carried by the pending intent needs to have the component name set to a
   * <a href="http://developer.android.com/training/cars/apps#create-carappservice">Car App Library
   * service</a> that needs to exist in the same application package as the media browser service.
   *
   * <p>TYPE: {@link PendingIntent}.
   *
   * @see MediaBrowserCompat#getExtras()
   * @see MediaBrowserServiceCompat#onGetRoot(String, int, Bundle)
   * @see MediaBrowserServiceCompat.BrowserRoot#BrowserRoot(String, Bundle)
   */
  @SuppressLint("IntentName")
  public static final String
      BROWSER_SERVICE_EXTRAS_KEY_APPLICATION_PREFERENCES_USING_CAR_APP_LIBRARY_INTENT =
          "androidx.media.BrowserRoot.Extras"
              + ".APPLICATION_PREFERENCES_USING_CAR_APP_LIBRARY_INTENT";

  /**
   * Bundle key sent through {@link MediaBrowserCompat#getExtras()} to the hosting {@link
   * MediaBrowserCompat} to indicate that the {@link MediaBrowserServiceCompat} supports the method
   * {@link MediaBrowserServiceCompat#onSearch(String, Bundle, MediaBrowserServiceCompat.Result)}.
   * If sent as {@code true}, the host may expose affordances which call the search method.
   *
   * <p>TYPE: boolean
   *
   * @see MediaBrowserCompat#getExtras()
   * @see MediaBrowserServiceCompat.BrowserRoot#BrowserRoot(String, Bundle)
   */
  @SuppressLint("IntentName")
  public static final String BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED =
      "android.media.browse.SEARCH_SUPPORTED";

  /**
   * Bundle key used to pass a browseable {@link android.media.browse.MediaBrowser.MediaItem} that
   * represents 'Favorite' content or some other notion of preset/pinned content.
   *
   * <p>Use this key to indicate to consumers (e.g. Auto and Automotive) that they can display
   * and/or subscribe to this item.
   *
   * <p>When this item is subscribed to, it is expected that the {@link MediaBrowserService} or
   * {@link MediaBrowserServiceCompat} loads content that the user has marked for easy or quick
   * access - e.g. favorite radio stations, pinned playlists, etc.
   *
   * <p>TYPE: MediaBrowser.MediaItem - note this should not be a {@link
   * MediaBrowserCompat.MediaItem}
   *
   * @see MediaBrowserCompat#getExtras()
   * @see MediaBrowserServiceCompat#onGetRoot(String, int, Bundle)
   * @see MediaBrowserServiceCompat.BrowserRoot#BrowserRoot(String, Bundle)
   */
  @SuppressLint("IntentName")
  public static final String BROWSER_SERVICE_EXTRAS_KEY_FAVORITES_MEDIA_ITEM =
      "androidx.media.BrowserRoot.Extras.FAVORITES_MEDIA_ITEM";

  /**
   * Bundle key passed from the {@link MediaBrowserServiceCompat} to the hosting {@link
   * MediaBrowserCompat} to indicate a preference about how playable instances of {@link
   * MediaBrowserCompat.MediaItem} are presented.
   *
   * <p>If exposed through {@link MediaBrowserCompat#getExtras()}, the preference applies to all
   * playable items within the browse tree.
   *
   * <p>If exposed through {@link MediaDescriptionCompat#getExtras()}, the preference applies to
   * only the immediate playable children of the corresponding browsable item. It takes precedence
   * over preferences sent through {@link MediaBrowserCompat#getExtras()}.
   *
   * <p>TYPE: int. Possible values are separate constants.
   *
   * @see MediaBrowserCompat#getExtras()
   * @see MediaBrowserServiceCompat.BrowserRoot#BrowserRoot(String, Bundle)
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   * @see #DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
   * @see #DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
   */
  @SuppressLint("IntentName")
  public static final String DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE =
      "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT";

  /**
   * Bundle key passed from the {@link MediaBrowserServiceCompat} to the hosting {@link
   * MediaBrowserCompat} to indicate a preference about how browsable instances of {@link
   * MediaBrowserCompat.MediaItem} are presented.
   *
   * <p>If exposed through {@link MediaBrowserCompat#getExtras()}, the preference applies to all
   * browsable items within the browse tree.
   *
   * <p>If exposed through {@link MediaDescriptionCompat#getExtras()}, the preference applies to
   * only the immediate browsable children of the corresponding browsable item. It takes precedence
   * over preferences sent through {@link MediaBrowserCompat#getExtras()}.
   *
   * <p>TYPE: int. Possible values are separate constants.
   *
   * @see MediaBrowserCompat#getExtras()
   * @see MediaBrowserServiceCompat.BrowserRoot#BrowserRoot(String, Bundle)
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   * @see #DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
   * @see #DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
   * @see #DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
   * @see #DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM
   */
  @SuppressLint("IntentName")
  public static final String DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE =
      "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT";

  /**
   * Bundle key sent through {@link MediaDescriptionCompat#getExtras()} to the hosting {@link
   * MediaBrowserCompat} to indicate a preference about how the corresponding {@link
   * MediaBrowserCompat.MediaItem} is presented.
   *
   * <p>This preference takes precedence over those expressed by {@link
   * #DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE} and {@link
   * #DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE}.
   *
   * <p>TYPE: int. Possible values are separate constants.
   *
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   * @see #DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
   * @see #DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
   * @see #DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
   * @see #DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM
   */
  @SuppressLint("IntentName")
  public static final String DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM =
      "android.media.browse.CONTENT_STYLE_SINGLE_ITEM_HINT";

  /**
   * Bundle value passed from the {@link MediaBrowserServiceCompat} to the hosting {@link
   * MediaBrowserCompat} to indicate a preference that certain instances of {@link
   * MediaBrowserCompat.MediaItem} should be presented as list items.
   *
   * @see #DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE
   * @see #DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE
   */
  public static final int DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM = 1;

  /**
   * Bundle value passed from the {@link MediaBrowserServiceCompat} to the hosting {@link
   * MediaBrowserCompat} to indicate a preference that certain instances of {@link
   * MediaBrowserCompat.MediaItem} should be presented as grid items.
   *
   * @see #DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE
   * @see #DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE
   */
  public static final int DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM = 2;

  /**
   * Bundle value passed from the {@link MediaBrowserServiceCompat} to the hosting {@link
   * MediaBrowserCompat} to indicate a preference that browsable instances of {@link
   * MediaBrowserCompat.MediaItem} should be presented as "category" list items. This means the
   * items must provide tintable vector drawable icons that render well when they:
   *
   * <ul>
   *   <li>do <strong>not</strong> fill all of the available area
   *   <li>are tinted by the system to provide sufficient contrast against the background
   * </ul>
   *
   * @see #DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE
   */
  public static final int DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM = 3;

  /**
   * Bundle value passed from the {@link MediaBrowserServiceCompat} to the hosting {@link
   * MediaBrowserCompat} to indicate a preference that browsable instances of {@link
   * MediaBrowserCompat.MediaItem} should be presented as "category" grid items. This means the
   * items must provide tintable vector drawable icons that render well when they:
   *
   * <ul>
   *   <li>do <strong>not</strong> fill all of the available area
   *   <li>are tinted by the system to provide sufficient contrast against the background
   * </ul>
   *
   * @see #DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE
   */
  public static final int DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM = 4;

  /**
   * Bundle key sent through {@link MediaDescriptionCompat#getExtras()} to the hosting {@link
   * MediaBrowserCompat} to indicate that certain instances of {@link MediaBrowserCompat.MediaItem}
   * are related as a group, with a title that is specified through the bundle value. Items that are
   * children of the same browsable node and have the same title are members of the same group. The
   * host may present a group's items as a contiguous block and display the title alongside the
   * group.
   *
   * <p>TYPE: String. Should be human readable and localized.
   *
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   */
  @SuppressLint("IntentName")
  public static final String DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE =
      "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT";

  /**
   * Bundle key sent through {@link MediaDescriptionCompat#getExtras()} to the hosting {@link
   * MediaBrowserCompat} to indicate the playback completion status of the corresponding {@link
   * MediaBrowserCompat.MediaItem}.
   *
   * <p>TYPE: int. Possible values are separate constants.
   *
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   * @see #DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
   * @see #DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
   * @see #DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
   */
  @SuppressLint("IntentName")
  public static final String DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS =
      "android.media.extra.PLAYBACK_STATUS";

  /**
   * Bundle value sent through {@link MediaDescriptionCompat#getExtras()} to the hosting {@link
   * MediaBrowserCompat} to indicate that the corresponding {@link MediaBrowserCompat.MediaItem} has
   * not been played by the user.
   *
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   * @see #DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS
   */
  public static final int DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED = 0;

  /**
   * Bundle value sent through {@link MediaDescriptionCompat#getExtras()} to the hosting {@link
   * MediaBrowserCompat} to indicate that the corresponding {@link MediaBrowserCompat.MediaItem} has
   * been partially played by the user.
   *
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   * @see #DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS
   */
  public static final int DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED = 1;

  /**
   * Bundle value sent through {@link MediaDescriptionCompat#getExtras()} to the hosting {@link
   * MediaBrowserCompat} to indicate that the corresponding {@link MediaBrowserCompat.MediaItem} has
   * been fully played by the user.
   *
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   * @see #DESCRIPTION_EXTRAS_KEY_COMPLETION_STATUS
   */
  public static final int DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED = 2;

  /**
   * Bundle key sent through {@link MediaDescriptionCompat#getExtras()} to the hosting {@link
   * MediaBrowserCompat} to indicate an amount of completion progress for the corresponding {@link
   * MediaBrowserCompat.MediaItem}. This extra augments {@link
   * #DESCRIPTION_EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED the partially played status} by
   * indicating how much has been played by the user.
   *
   * <p>TYPE: double, a value between 0.0 and 1.0, inclusive. 0.0 indicates no completion progress
   * (item is not started) and 1.0 indicates full completion progress (item is fully played). Values
   * in between indicate partial progress (for example, 0.75 indicates the item is 75% complete).
   *
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   */
  @SuppressLint("IntentName")
  public static final String DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE =
      "androidx.media.MediaItem.Extras.COMPLETION_PERCENTAGE";

  /**
   * {@link Bundle} key used to store supported custom browser actions for {@link MediaBrowserCompat
   * media browsers} that support custom browser actions.
   *
   * <p>The browser indicates support for custom browser actions by including the key {@link
   * MediaConstants#BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT} with a non-zero value in the
   * root hints bundle passed to {@link MediaBrowserServiceCompat#onGetRoot}.
   *
   * <p>Use this key to add an {@link ArrayList} to the {@link Bundle} passed in {@link
   * MediaBrowserServiceCompat.BrowserRoot}. {@link MediaBrowserServiceCompat} should add this
   * bundle to the {@link MediaBrowserServiceCompat.BrowserRoot} when {@link
   * MediaBrowserServiceCompat#onGetRoot(String, int, Bundle)} is called. Use {@link
   * Bundle#putParcelableArrayList(String, ArrayList)} with a list of bundles, each defining a
   * custom browser action, to set supported custom browser actions.
   *
   * <p>TYPE: arraylist, an ArrayList of {@link Bundle}s, with each bundle defining a browse custom
   * action.
   *
   * <p>A custom browser action is defined by an {@linkplain
   * MediaConstants#EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID action ID}, an {@linkplain
   * MediaConstants#EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL action label}, an {@linkplain
   * MediaConstants#EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI action icon URI}, and optionally an
   * {@linkplain MediaConstants#EXTRAS_KEY_CUSTOM_BROWSER_ACTION_EXTRAS action extras bundle}.
   *
   * <p>Custom browser action example:
   *
   * <ul>
   *   <li>Action ID: "com.example.audioapp.download"
   *       <ul>
   *         <li>Key: {@link MediaConstants#EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID}
   *       </ul>
   *   <li>Action label: "Download Song"
   *       <ul>
   *         <li>Key: {@link MediaConstants#EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL}
   *         <li>Localized String label for action
   *       </ul>
   *   <li>Action Icon URI: "content://com.example.public/download"
   *       <ul>
   *         <li>Key: {@link MediaConstants#EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI}
   *         <li>Tintable vector drawable
   *       </ul>
   *   <li>Action extras: {bundle}
   *       <ul>
   *         <li>Key: {@link MediaConstants#EXTRAS_KEY_CUSTOM_BROWSER_ACTION_EXTRAS}
   *         <li>Bundle extras
   *       </ul>
   * </ul>
   */
  public static final String BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST =
      "androidx.media.utils.extras.CUSTOM_BROWSER_ACTION_ROOT_LIST";

  /**
   * {@link Bundle} key used to define a string list of custom browser actions for a {@link
   * MediaBrowserCompat.MediaItem}. e.g. "download","favorite","add to queue"
   *
   * <p>Supported {@link MediaBrowserCompat media browsers} use this {@link Bundle} key to build a
   * list of custom browser actions for each {@link MediaBrowserCompat.MediaItem}.
   *
   * <p>This key is sent through {@link MediaDescriptionCompat#getExtras()} to the {@link
   * MediaBrowserCompat} to indicate supported custom browser actions for the corresponding {@link
   * MediaBrowserCompat.MediaItem}.
   *
   * <p>Use {@linkplain Bundle#putStringArrayList(String, ArrayList) a string array list} with a
   * list of custom browser action IDs. Set this bundle in the {@link MediaBrowserCompat.MediaItem}
   * using {@link MediaDescriptionCompat.Builder#setExtras(Bundle)} to set the supported browse
   * custom actions for the {@link MediaBrowserCompat.MediaItem}.
   *
   * <p>Each value action in this list must be an action ID defined in {@linkplain
   * MediaBrowserServiceCompat.BrowserRoot browser root} with {@link Bundle} key {@link
   * MediaConstants#BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST}.
   *
   * <p>TYPE: ArrayList&lt;String&gt;, list of String custom browser action IDs.
   *
   * @see MediaDescriptionCompat#getExtras()
   * @see MediaDescriptionCompat.Builder#setExtras(Bundle)
   * @see MediaConstants#BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST
   */
  public static final String DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST =
      "androidx.media.utils.extras.CUSTOM_BROWSER_ACTION_ID_LIST";

  /**
   * {@link Bundle} key used to define the ID for a custom browser action.
   *
   * <p>TYPE: String, String ID for a custom browser action.
   *
   * @see MediaConstants#BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST
   * @see MediaConstants#DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST
   */
  public static final String EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID =
      "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_ID";

  /**
   * {@link Bundle} key used to define the label for a custom browser action. Label is a localized
   * string that labels the action for the user.
   *
   * <p>TYPE: String, String label for a custom browser action. This must be localized.
   *
   * @see MediaConstants#BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST
   * @see MediaConstants#DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST
   */
  public static final String EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL =
      "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_LABEL";

  /**
   * {@link Bundle} key used to define the icon URI for a custom browser action.
   *
   * <p>TYPE: String, String content provider URI for a tintable vector drawable icon.
   *
   * @see MediaConstants#BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST
   * @see MediaConstants#DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST
   */
  public static final String EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI =
      "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_ICON_URI";

  /**
   * {@link Bundle} key used to define an extras bundle for a custom browser action.
   *
   * <p>Use {@link Bundle#putBundle(String, Bundle)} on the custom browser action bundle to add this
   * extras bundle to the custom browser action.
   *
   * <p>TYPE: Bundle.
   *
   * @see MediaConstants#BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST
   * @see MediaConstants#DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST
   */
  public static final String EXTRAS_KEY_CUSTOM_BROWSER_ACTION_EXTRAS =
      "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_EXTRAS";

  /**
   * {@link Bundle} key used to define the total number of actions allowed per item. Passed to
   * {@link MediaBrowserServiceCompat} using {@link MediaBrowserServiceCompat#onGetRoot(String, int,
   * Bundle)} in root hints bundle.
   *
   * <p>Presence of this key and positive value in the root hints indicates that custom browse
   * actions feature is supported. Actions beyond this limit will be truncated.
   *
   * <p>TYPE: int, number of actions each item is limited to.
   *
   * @see MediaBrowserServiceCompat#onGetRoot(String, int, Bundle)
   * @see MediaConstants#BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST
   * @see MediaConstants#DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST
   */
  public static final String BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT =
      "androidx.media.utils.MediaBrowserCompat.extras.CUSTOM_BROWSER_ACTION_LIMIT";

  /**
   * {@link Bundle} key used to define the ID of the {@link MediaBrowserCompat.MediaItem} associated
   * with the invoked action.
   *
   * <p>A {@link MediaBrowserCompat} that supports custom browser actions can set this key in the
   * parameter extra bundle when using {@link MediaBrowserCompat#sendCustomAction(String, Bundle,
   * MediaBrowserCompat.CustomActionCallback)}.
   *
   * <p>A {@link MediaBrowserServiceCompat} that supports custom browser actions should override
   * {@link MediaBrowserServiceCompat#onCustomAction( String, Bundle,
   * MediaBrowserServiceCompat.Result)} to receive extras bundle set by {@link MediaBrowserCompat
   * media browsers}.
   *
   * <p>TYPE: string, string ID of the {@link MediaBrowserCompat.MediaItem} on which the custom
   * action was invoked.
   *
   * @see MediaBrowserServiceCompat#onCustomAction(String, Bundle, MediaBrowserServiceCompat.Result)
   * @see MediaBrowserCompat#sendCustomAction(String, Bundle,
   *     MediaBrowserCompat.CustomActionCallback)
   */
  public static final String EXTRAS_KEY_CUSTOM_BROWSER_ACTION_MEDIA_ITEM_ID =
      "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_MEDIA_ITEM_ID";

  /**
   * {@link Bundle} key set in {@link MediaBrowserServiceCompat.Result} to indicate which browse
   * node should be displayed next.
   *
   * <p>A {@link MediaBrowserServiceCompat} that supports custom browser actions can set this key in
   * the {@link MediaBrowserServiceCompat.Result} passed in {@link
   * MediaBrowserServiceCompat#onCustomAction(String, Bundle, MediaBrowserServiceCompat.Result)}.
   *
   * <p>If this key is present in a {@link MediaBrowserCompat.CustomActionCallback} data {@link
   * Bundle} the {@link MediaBrowserCompat} will update the current browse node when {@link
   * MediaBrowserCompat.CustomActionCallback#onResult(String, Bundle, Bundle)} is called by the
   * {@link MediaBrowserServiceCompat}. The new browse node will be fetched by {@link
   * MediaBrowserCompat#getItem(String, MediaBrowserCompat.ItemCallback)}.
   *
   * <p>A {@link MediaBrowserServiceCompat} that supports custom browser actions must implement
   * {@link MediaBrowserServiceCompat#onLoadItem(String, MediaBrowserServiceCompat.Result)} to use
   * this feature.
   *
   * <p>TYPE: string, string {@link MediaBrowserCompat.MediaItem} ID to set as new browse node.
   *
   * @see MediaBrowserCompat#sendCustomAction(String, Bundle,
   *     MediaBrowserCompat.CustomActionCallback)
   * @see MediaBrowserCompat.CustomActionCallback
   * @see MediaBrowserServiceCompat#onCustomAction(String, Bundle, MediaBrowserServiceCompat.Result)
   */
  public static final String EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_BROWSE_NODE =
      "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_BROWSE_NODE";

  /**
   * {@link Bundle} key set in {@link MediaBrowserServiceCompat.Result} to show the currently
   * playing item.
   *
   * <p>A {@link MediaBrowserServiceCompat} that supports custom browser actions can set this key in
   * the {@link MediaBrowserServiceCompat.Result} passed in {@link
   * MediaBrowserServiceCompat#onCustomAction(String, Bundle, MediaBrowserServiceCompat.Result)}.
   *
   * <p>If this key is present and the value is true in {@link
   * MediaBrowserCompat.CustomActionCallback} {@link MediaBrowserServiceCompat.Result}, the
   * currently playing item will be shown when {@link
   * MediaBrowserCompat.CustomActionCallback#onResult(String, Bundle, Bundle)} is called by the
   * {@link MediaBrowserServiceCompat}.
   *
   * <p>TYPE: boolean, boolean value of true will show currently playing item.
   *
   * @see MediaBrowserCompat#sendCustomAction(String, Bundle,
   *     MediaBrowserCompat.CustomActionCallback)
   * @see MediaBrowserCompat.CustomActionCallback
   * @see MediaBrowserServiceCompat#onCustomAction(String, Bundle, MediaBrowserServiceCompat.Result)
   */
  public static final String EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_SHOW_PLAYING_ITEM =
      "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_SHOW_PLAYING_ITEM";

  /**
   * {@link Bundle} key set in {@link MediaBrowserServiceCompat.Result} to refresh a {@link
   * MediaBrowserCompat.MediaItem} in the browse tree.
   *
   * <p>A {@link MediaBrowserServiceCompat} that supports custom browser actions can set this key in
   * the {@link MediaBrowserServiceCompat.Result} passed in {@link
   * MediaBrowserServiceCompat#onCustomAction(String, Bundle, MediaBrowserServiceCompat.Result)}.
   *
   * <p>If this key is present in {@link MediaBrowserCompat.CustomActionCallback} {@link
   * MediaBrowserServiceCompat.Result}, the item will be refreshed with {@link
   * MediaBrowserCompat#getItem(String, MediaBrowserCompat.ItemCallback)} when {@link
   * MediaBrowserCompat.CustomActionCallback#onProgressUpdate(String, Bundle, Bundle)} or {@link
   * MediaBrowserCompat.CustomActionCallback#onResult(String, Bundle, Bundle)} is called by the
   * {@link MediaBrowserServiceCompat}.
   *
   * <p>A {@link MediaBrowserServiceCompat} that supports custom browser actions must implement
   * {@link MediaBrowserServiceCompat#onLoadItem(String, MediaBrowserServiceCompat.Result)} in order
   * to update the state of the item.
   *
   * <p>TYPE: string, string {@link MediaBrowserCompat.MediaItem} ID to refresh.
   *
   * @see MediaBrowserCompat#sendCustomAction(String, Bundle,
   *     MediaBrowserCompat.CustomActionCallback)
   * @see MediaBrowserCompat.CustomActionCallback
   * @see MediaBrowserServiceCompat#onCustomAction(String, Bundle, MediaBrowserServiceCompat.Result)
   */
  public static final String EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM =
      "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM";

  /**
   * {@link Bundle} key set in {@link MediaBrowserServiceCompat.Result} to set a message for the
   * user.
   *
   * <p>A {@link MediaBrowserServiceCompat} that supports custom browser actions can set this key in
   * the {@link MediaBrowserServiceCompat.Result} passed in {@link
   * MediaBrowserServiceCompat#onCustomAction(String, Bundle, MediaBrowserServiceCompat.Result)}.
   *
   * <p>If this key is present in {@link MediaBrowserCompat.CustomActionCallback} {@link
   * MediaBrowserServiceCompat.Result}, the message will be shown to the user when {@link
   * MediaBrowserCompat.CustomActionCallback#onProgressUpdate(String, Bundle, Bundle)} or {@link
   * MediaBrowserCompat.CustomActionCallback#onResult(String, Bundle, Bundle)} is called by the
   * {@link MediaBrowserServiceCompat}.
   *
   * <p>TYPE: string, localized message string to show the user.
   *
   * @see MediaBrowserCompat#sendCustomAction(String, Bundle,
   *     MediaBrowserCompat.CustomActionCallback)
   * @see MediaBrowserCompat.CustomActionCallback
   * @see MediaBrowserServiceCompat#onCustomAction(String, Bundle, MediaBrowserServiceCompat.Result)
   */
  public static final String EXTRAS_KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE =
      "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE";

  /**
   * Bundle key used for the media ID in {@link PlaybackStateCompat playback state} extras. It's for
   * associating the playback state with the media being played so the value is expected to be same
   * with {@link MediaMetadataCompat#METADATA_KEY_MEDIA_ID media id} of the current metadata.
   *
   * <p>TYPE: String
   *
   * @see PlaybackStateCompat#getExtras
   * @see PlaybackStateCompat.Builder#setExtras
   */
  @SuppressLint("IntentName")
  public static final String PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID =
      "androidx.media.PlaybackStateCompat.Extras.KEY_MEDIA_ID";

  /**
   * Bundle key passed through {@link PlaybackStateCompat#getExtras()} to the hosting {@link
   * MediaControllerCompat} which maps to a label. The label is associated with {@link
   * #PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT the action} that allow users to
   * resolve the current playback state error.
   *
   * <p>The label should be short; a more detailed explanation can be provided to the user via
   * {@link PlaybackStateCompat#getErrorMessage()}.
   *
   * <p>TYPE: String. Should be human readable and localized.
   *
   * @see PlaybackStateCompat#getExtras()
   * @see PlaybackStateCompat.Builder#setExtras(Bundle)
   * @see #PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT
   */
  @SuppressLint("IntentName")
  public static final String PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL =
      "android.media.extras.ERROR_RESOLUTION_ACTION_LABEL";

  /**
   * Bundle key passed through {@link PlaybackStateCompat#getExtras()} to the hosting {@link
   * MediaControllerCompat} which maps to a pending intent. When launched, the intent should allow
   * users to resolve the current playback state error. {@link
   * #PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL A label} should be included in the
   * same Bundle. The key {@link
   * #BROWSER_SERVICE_EXTRAS_KEY_APPLICATION_PREFERENCES_USING_CAR_APP_LIBRARY_INTENT} should be
   * used instead if the intent points to a Car App Library service.
   *
   * <p>The intent is NOT auto launched and the user first sees an actionable button with label set
   * to {@link #PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL}. Clicking that button
   * launches the intent.
   *
   * <p>TYPE: PendingIntent. Should be inserted into the Bundle {@link Bundle#putParcelable(String,
   * Parcelable) as a Parcelable}.
   *
   * @see PlaybackStateCompat#getExtras()
   * @see PlaybackStateCompat.Builder#setExtras(Bundle)
   * @see #PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL
   * @see #BROWSER_SERVICE_EXTRAS_KEY_APPLICATION_PREFERENCES_USING_CAR_APP_LIBRARY_INTENT
   */
  @SuppressLint("IntentName")
  public static final String PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT =
      "android.media.extras.ERROR_RESOLUTION_ACTION_INTENT";

  /**
   * Bundle key passed through {@link PlaybackStateCompat#getExtras()} to the {@link
   * MediaControllerCompat} which maps to a {@link PendingIntent}. When launched, the {@link
   * PendingIntent} should allow users to resolve the current playback state error. The intent
   * should have the component name set to a Car App Library service which exists in the same
   * application package as the media browser service. The intent may be launched directly unlike
   * the behavior when using {@link #PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT}.
   *
   * <p>Applications must also set the error message and {@link
   * #PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL} for cases in which the intent cannot
   * be auto launched.
   *
   * <p>TYPE: {@link PendingIntent}. Should be inserted into the Bundle {@link
   * Bundle#putParcelable(String, Parcelable) as a Parcelable}.
   *
   * @see PlaybackStateCompat#getExtras()
   * @see PlaybackStateCompat.Builder#setExtras(Bundle)
   */
  @SuppressLint("IntentName")
  public static final String
      PLAYBACK_STATE_EXTRAS_KEY_ERROR_RESOLUTION_USING_CAR_APP_LIBRARY_INTENT =
          "androidx.media.PlaybackStateCompat.Extras"
              + ".ERROR_RESOLUTION_USING_CAR_APP_LIBRARY_INTENT";

  /**
   * Bundle key passed through the {@code extras} of {@link
   * MediaControllerCompat.TransportControls#prepareFromMediaId(String, Bundle)}, {@link
   * MediaControllerCompat.TransportControls#prepareFromSearch(String, Bundle)}, {@link
   * MediaControllerCompat.TransportControls#prepareFromUri(Uri, Bundle)}, {@link
   * MediaControllerCompat.TransportControls#playFromMediaId(String, Bundle)}, {@link
   * MediaControllerCompat.TransportControls#playFromSearch(String, Bundle)}, or {@link
   * MediaControllerCompat.TransportControls#playFromUri(Uri, Bundle)} to indicate the stream type
   * to be used by the session when playing or preparing the media.
   *
   * <p>TYPE: int
   *
   * @see MediaControllerCompat.TransportControls#prepareFromMediaId(String, Bundle)
   * @see MediaControllerCompat.TransportControls#prepareFromSearch(String, Bundle)
   * @see MediaControllerCompat.TransportControls#prepareFromUri(Uri, Bundle)
   * @see MediaControllerCompat.TransportControls#playFromMediaId(String, Bundle)
   * @see MediaControllerCompat.TransportControls#playFromSearch(String, Bundle)
   * @see MediaControllerCompat.TransportControls#playFromUri(Uri, Bundle)
   */
  @SuppressLint("IntentName")
  public static final String TRANSPORT_CONTROLS_EXTRAS_KEY_LEGACY_STREAM_TYPE =
      "android.media.session.extra.LEGACY_STREAM_TYPE";

  /**
   * Bundle key passed through the {@code extras} of {@link
   * MediaControllerCompat.TransportControls#prepareFromMediaId(String, Bundle)}, {@link
   * MediaControllerCompat.TransportControls#prepareFromSearch(String, Bundle)}, {@link
   * MediaControllerCompat.TransportControls#prepareFromUri(Uri, Bundle)}, {@link
   * MediaControllerCompat.TransportControls#playFromMediaId(String, Bundle)}, {@link
   * MediaControllerCompat.TransportControls#playFromSearch(String, Bundle)}, or {@link
   * MediaControllerCompat.TransportControls#playFromUri(Uri, Bundle)} to indicate whether the
   * session should shuffle the media to be played or not. The extra parameter is limited to the
   * current request and doesn't affect the {@link MediaSessionCompat#setShuffleMode(int) shuffle
   * mode}.
   *
   * <p>TYPE: boolean
   *
   * @see MediaControllerCompat.TransportControls#prepareFromMediaId(String, Bundle)
   * @see MediaControllerCompat.TransportControls#prepareFromSearch(String, Bundle)
   * @see MediaControllerCompat.TransportControls#prepareFromUri(Uri, Bundle)
   * @see MediaControllerCompat.TransportControls#playFromMediaId(String, Bundle)
   * @see MediaControllerCompat.TransportControls#playFromSearch(String, Bundle)
   * @see MediaControllerCompat.TransportControls#playFromUri(Uri, Bundle)
   */
  @SuppressLint("IntentName")
  public static final String TRANSPORT_CONTROLS_EXTRAS_KEY_SHUFFLE =
      "androidx.media.MediaControllerCompat.TransportControls.extras.KEY_SHUFFLE";

  private MediaConstants() {}
}
