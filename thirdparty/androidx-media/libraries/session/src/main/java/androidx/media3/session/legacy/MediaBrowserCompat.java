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
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.session.legacy.MediaBrowserProtocol.CLIENT_MSG_ADD_SUBSCRIPTION;
import static androidx.media3.session.legacy.MediaBrowserProtocol.CLIENT_MSG_CONNECT;
import static androidx.media3.session.legacy.MediaBrowserProtocol.CLIENT_MSG_DISCONNECT;
import static androidx.media3.session.legacy.MediaBrowserProtocol.CLIENT_MSG_GET_MEDIA_ITEM;
import static androidx.media3.session.legacy.MediaBrowserProtocol.CLIENT_MSG_REGISTER_CALLBACK_MESSENGER;
import static androidx.media3.session.legacy.MediaBrowserProtocol.CLIENT_MSG_REMOVE_SUBSCRIPTION;
import static androidx.media3.session.legacy.MediaBrowserProtocol.CLIENT_MSG_SEARCH;
import static androidx.media3.session.legacy.MediaBrowserProtocol.CLIENT_MSG_SEND_CUSTOM_ACTION;
import static androidx.media3.session.legacy.MediaBrowserProtocol.CLIENT_MSG_UNREGISTER_CALLBACK_MESSENGER;
import static androidx.media3.session.legacy.MediaBrowserProtocol.CLIENT_VERSION_CURRENT;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_CALLBACK_TOKEN;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_CALLING_PID;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_CUSTOM_ACTION;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_CUSTOM_ACTION_EXTRAS;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_MEDIA_ITEM_ID;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_MEDIA_ITEM_LIST;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_MEDIA_SESSION_TOKEN;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_NOTIFY_CHILDREN_CHANGED_OPTIONS;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_OPTIONS;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_PACKAGE_NAME;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_RESULT_RECEIVER;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_ROOT_HINTS;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_SEARCH_EXTRAS;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_SEARCH_QUERY;
import static androidx.media3.session.legacy.MediaBrowserProtocol.EXTRA_CALLING_PID;
import static androidx.media3.session.legacy.MediaBrowserProtocol.EXTRA_CLIENT_VERSION;
import static androidx.media3.session.legacy.MediaBrowserProtocol.EXTRA_MESSENGER_BINDER;
import static androidx.media3.session.legacy.MediaBrowserProtocol.EXTRA_SERVICE_VERSION;
import static androidx.media3.session.legacy.MediaBrowserProtocol.EXTRA_SESSION_BINDER;
import static androidx.media3.session.legacy.MediaBrowserProtocol.SERVICE_MSG_ON_CONNECT;
import static androidx.media3.session.legacy.MediaBrowserProtocol.SERVICE_MSG_ON_CONNECT_FAILED;
import static androidx.media3.session.legacy.MediaBrowserProtocol.SERVICE_MSG_ON_LOAD_CHILDREN;
import static androidx.media3.session.legacy.MediaBrowserProtocol.SERVICE_VERSION_2;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.os.BadParcelableException;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteException;
import android.support.v4.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.collection.ArrayMap;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.legacy.MediaControllerCompat.TransportControls;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Browses media content offered by a {@link MediaBrowserServiceCompat}.
 *
 * <p>The app targeting API level 30 or higher must include a {@code <queries>} element in their
 * manifest to connect to a media browser service in another app. See the following example and <a
 * href="{@docRoot}training/package-visibility">this guide</a> for more information.
 *
 * <pre>{@code
 * <!-- As an intent action -->
 * <intent>
 *   <action android:name="android.media.browse.MediaBrowserService" />
 * </intent>
 * <!-- Or, as a package name -->
 * <package android:name="package_name_of_the_other_app" />
 * }</pre>
 *
 * <p>This object is not thread-safe. All calls should happen on the thread on which the browser was
 * constructed. All callback methods will be called from the thread on which the browser was
 * constructed. <div class="special reference">
 *
 * <h2>Developer Guides</h2>
 *
 * <p>For information about building your media application, read the <a
 * href="{@docRoot}guide/topics/media-apps/index.html">Media Apps</a> developer guide. </div>
 */
@UnstableApi
@RestrictTo(LIBRARY)
public final class MediaBrowserCompat {
  static final String TAG = "MediaBrowserCompat";
  static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

  /**
   * Used as an int extra field to denote the page number to subscribe. The value of {@code
   * EXTRA_PAGE} should be greater than or equal to 0.
   *
   * @see android.service.media.MediaBrowserService.BrowserRoot
   * @see #EXTRA_PAGE_SIZE
   */
  public static final String EXTRA_PAGE = "android.media.browse.extra.PAGE";

  /**
   * Used as an int extra field to denote the number of media items in a page. The value of {@code
   * EXTRA_PAGE_SIZE} should be greater than or equal to 1.
   *
   * @see android.service.media.MediaBrowserService.BrowserRoot
   * @see #EXTRA_PAGE
   */
  public static final String EXTRA_PAGE_SIZE = "android.media.browse.extra.PAGE_SIZE";

  /**
   * Used as a string extra field to denote the target {@link MediaItem}.
   *
   * @see #CUSTOM_ACTION_DOWNLOAD
   * @see #CUSTOM_ACTION_REMOVE_DOWNLOADED_FILE
   */
  public static final String EXTRA_MEDIA_ID = "android.media.browse.extra.MEDIA_ID";

  /**
   * Used as a float extra field to denote the current progress during download. The value of this
   * field must be a float number within [0.0, 1.0].
   *
   * @see #CUSTOM_ACTION_DOWNLOAD
   * @see CustomActionCallback#onProgressUpdate
   */
  public static final String EXTRA_DOWNLOAD_PROGRESS =
      "android.media.browse.extra.DOWNLOAD_PROGRESS";

  /**
   * Predefined custom action to ask the connected service to download a specific {@link MediaItem}
   * for offline playback. The id of the media item must be passed in an extra bundle. The download
   * progress might be delivered to the browser via {@link CustomActionCallback#onProgressUpdate}.
   *
   * @see #EXTRA_MEDIA_ID
   * @see #EXTRA_DOWNLOAD_PROGRESS
   * @see #CUSTOM_ACTION_REMOVE_DOWNLOADED_FILE
   */
  public static final String CUSTOM_ACTION_DOWNLOAD = "android.support.v4.media.action.DOWNLOAD";

  /**
   * Predefined custom action to ask the connected service to remove the downloaded file of {@link
   * MediaItem} by the {@link #CUSTOM_ACTION_DOWNLOAD download} action. The id of the media item
   * must be passed in an extra bundle.
   *
   * @see #EXTRA_MEDIA_ID
   * @see #CUSTOM_ACTION_DOWNLOAD
   */
  public static final String CUSTOM_ACTION_REMOVE_DOWNLOADED_FILE =
      "android.support.v4.media.action.REMOVE_DOWNLOADED_FILE";

  private final MediaBrowserImpl mImpl;

  /**
   * Creates a media browser for the specified media browse service.
   *
   * @param context The context.
   * @param serviceComponent The component name of the media browse service.
   * @param callback The connection callback.
   * @param rootHints An optional bundle of service-specific arguments to send to the media browse
   *     service when connecting and retrieving the root id for browsing, or null if none. The
   *     contents of this bundle may affect the information returned when browsing.
   * @see MediaBrowserServiceCompat.BrowserRoot#EXTRA_RECENT
   * @see MediaBrowserServiceCompat.BrowserRoot#EXTRA_OFFLINE
   * @see MediaBrowserServiceCompat.BrowserRoot#EXTRA_SUGGESTED
   */
  public MediaBrowserCompat(
      Context context,
      ComponentName serviceComponent,
      ConnectionCallback callback,
      @Nullable Bundle rootHints) {
    // To workaround an issue of {@link #unsubscribe(String, SubscriptionCallback)} on API 24
    // and 25 devices, use the support library version of implementation on those devices.
    if (Build.VERSION.SDK_INT >= 26) {
      mImpl = new MediaBrowserImplApi26(context, serviceComponent, callback, rootHints);
    } else if (Build.VERSION.SDK_INT >= 23) {
      mImpl = new MediaBrowserImplApi23(context, serviceComponent, callback, rootHints);
    } else if (Build.VERSION.SDK_INT >= 21) {
      mImpl = new MediaBrowserImplApi21(context, serviceComponent, callback, rootHints);
    } else {
      mImpl = new MediaBrowserImplBase(context, serviceComponent, callback, rootHints);
    }
  }

  /**
   * Connects to the media browse service. Internally, it binds to the service.
   *
   * <p>The connection callback specified in the constructor will be invoked when the connection
   * completes or fails.
   */
  public void connect() {
    Log.d(TAG, "Connecting to a MediaBrowserService.");
    mImpl.connect();
  }

  /** Disconnects from the media browse service. After this, no more callbacks will be received. */
  public void disconnect() {
    mImpl.disconnect();
  }

  /** Returns whether the browser is connected to the service. */
  public boolean isConnected() {
    return mImpl.isConnected();
  }

  /** Gets the service component that the media browser is connected to. */
  public ComponentName getServiceComponent() {
    return mImpl.getServiceComponent();
  }

  /**
   * Gets the root id.
   *
   * <p>Note that the root id may become invalid or change when when the browser is disconnected.
   *
   * @throws IllegalStateException if not connected.
   */
  public String getRoot() {
    return mImpl.getRoot();
  }

  /**
   * Gets any extras for the media service.
   *
   * @return The extra bundle if it is connected and set, and {@code null} otherwise.
   * @throws IllegalStateException if not connected.
   */
  @Nullable
  public Bundle getExtras() {
    return mImpl.getExtras();
  }

  /**
   * Gets the media session token associated with the media browser.
   *
   * <p>Note that the session token may become invalid or change when when the browser is
   * disconnected.
   *
   * @return The session token for the browser, never null.
   * @throws IllegalStateException if not connected.
   */
  public MediaSessionCompat.Token getSessionToken() {
    return mImpl.getSessionToken();
  }

  /**
   * Queries for information about the media items that are contained within the specified id and
   * subscribes to receive updates when they change.
   *
   * <p>The list of subscriptions is maintained even when not connected and is restored after the
   * reconnection. It is ok to subscribe while not connected but the results will not be returned
   * until the connection completes.
   *
   * <p>If the id is already subscribed with a different callback then the new callback will replace
   * the previous one and the child data will be reloaded.
   *
   * @param parentId The id of the parent media item whose list of children will be subscribed.
   * @param callback The callback to receive the list of children.
   */
  public void subscribe(String parentId, SubscriptionCallback callback) {
    // Check arguments.
    if (TextUtils.isEmpty(parentId)) {
      throw new IllegalArgumentException("parentId is empty");
    }
    if (callback == null) {
      throw new IllegalArgumentException("callback is null");
    }
    mImpl.subscribe(parentId, null, callback);
  }

  /**
   * Queries with service-specific arguments for information about the media items that are
   * contained within the specified id and subscribes to receive updates when they change.
   *
   * <p>The list of subscriptions is maintained even when not connected and is restored after the
   * reconnection. It is ok to subscribe while not connected but the results will not be returned
   * until the connection completes.
   *
   * <p>If the id is already subscribed with a different callback then the new callback will replace
   * the previous one and the child data will be reloaded.
   *
   * @param parentId The id of the parent media item whose list of children will be subscribed.
   * @param options A bundle of service-specific arguments to send to the media browse service. The
   *     contents of this bundle may affect the information returned when browsing.
   * @param callback The callback to receive the list of children.
   */
  public void subscribe(String parentId, Bundle options, SubscriptionCallback callback) {
    // Check arguments.
    if (TextUtils.isEmpty(parentId)) {
      throw new IllegalArgumentException("parentId is empty");
    }
    if (callback == null) {
      throw new IllegalArgumentException("callback is null");
    }
    if (options == null) {
      throw new IllegalArgumentException("options are null");
    }
    mImpl.subscribe(parentId, options, callback);
  }

  /**
   * Unsubscribes for changes to the children of the specified media id.
   *
   * <p>The query callback will no longer be invoked for results associated with this id once this
   * method returns.
   *
   * @param parentId The id of the parent media item whose list of children will be unsubscribed.
   */
  public void unsubscribe(String parentId) {
    // Check arguments.
    if (TextUtils.isEmpty(parentId)) {
      throw new IllegalArgumentException("parentId is empty");
    }
    mImpl.unsubscribe(parentId, null);
  }

  /**
   * Unsubscribes for changes to the children of the specified media id.
   *
   * <p>The query callback will no longer be invoked for results associated with this id once this
   * method returns.
   *
   * @param parentId The id of the parent media item whose list of children will be unsubscribed.
   * @param callback A callback sent to the media browse service to subscribe.
   */
  public void unsubscribe(String parentId, SubscriptionCallback callback) {
    // Check arguments.
    if (TextUtils.isEmpty(parentId)) {
      throw new IllegalArgumentException("parentId is empty");
    }
    if (callback == null) {
      throw new IllegalArgumentException("callback is null");
    }
    mImpl.unsubscribe(parentId, callback);
  }

  /**
   * Retrieves a specific {@link MediaItem} from the connected service. Not all services may support
   * this, so falling back to subscribing to the parent's id should be used when unavailable.
   *
   * @param mediaId The id of the item to retrieve.
   * @param cb The callback to receive the result on.
   */
  public void getItem(final String mediaId, final ItemCallback cb) {
    mImpl.getItem(mediaId, cb);
  }

  /**
   * Searches {@link MediaItem media items} from the connected service. Not all services may support
   * this, and {@link SearchCallback#onError} will be called if not implemented.
   *
   * @param query The search query that contains keywords separated by space. Should not be an empty
   *     string.
   * @param extras The bundle of service-specific arguments to send to the media browser service.
   *     The contents of this bundle may affect the search result.
   * @param callback The callback to receive the search result. Must be non-null.
   * @throws IllegalStateException if the browser is not connected to the media browser service.
   */
  public void search(final String query, @Nullable Bundle extras, SearchCallback callback) {
    if (TextUtils.isEmpty(query)) {
      throw new IllegalArgumentException("query cannot be empty");
    }
    if (callback == null) {
      throw new IllegalArgumentException("callback cannot be null");
    }
    mImpl.search(query, extras, callback);
  }

  /**
   * Sends a custom action to the connected service. If the service doesn't support the given
   * action, {@link CustomActionCallback#onError} will be called.
   *
   * @param action The custom action that will be sent to the connected service. Should not be an
   *     empty string.
   * @param extras The bundle of service-specific arguments to send to the media browser service.
   * @param callback The callback to receive the result of the custom action.
   * @see #CUSTOM_ACTION_DOWNLOAD
   * @see #CUSTOM_ACTION_REMOVE_DOWNLOADED_FILE
   */
  public void sendCustomAction(
      String action, @Nullable Bundle extras, @Nullable CustomActionCallback callback) {
    if (TextUtils.isEmpty(action)) {
      throw new IllegalArgumentException("action cannot be empty");
    }
    mImpl.sendCustomAction(action, extras, callback);
  }

  /**
   * Gets the options which is passed to {@link MediaBrowserServiceCompat#notifyChildrenChanged(
   * String, Bundle)} call that triggered {@link SubscriptionCallback#onChildrenLoaded}. This should
   * be called inside of {@link SubscriptionCallback#onChildrenLoaded}.
   *
   * @return A bundle which is passed to {@link MediaBrowserServiceCompat#notifyChildrenChanged(
   *     String, Bundle)}
   */
  @Nullable
  public Bundle getNotifyChildrenChangedOptions() {
    return mImpl.getNotifyChildrenChangedOptions();
  }

  /**
   * A class with information on a single media item for use in browsing/searching media. MediaItems
   * are application dependent so we cannot guarantee that they contain the right values.
   */
  @SuppressLint("BanParcelableUsage")
  public static class MediaItem implements Parcelable {
    private final int mFlags;
    private final MediaDescriptionCompat mDescription;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
        flag = true,
        value = {FLAG_BROWSABLE, FLAG_PLAYABLE})
    private @interface Flags {}

    /** Flag: Indicates that the item has children of its own. */
    public static final int FLAG_BROWSABLE = 1 << 0;

    /**
     * Flag: Indicates that the item is playable.
     *
     * <p>The id of this item may be passed to {@link TransportControls#playFromMediaId(String,
     * Bundle)} to start playing it.
     */
    public static final int FLAG_PLAYABLE = 1 << 1;

    /**
     * Creates an instance from a framework {@link android.media.browse.MediaBrowser.MediaItem}
     * object.
     *
     * <p>This method is only supported on API 21+. On API 20 and below, it returns null.
     *
     * @param itemObj A {@link android.media.browse.MediaBrowser.MediaItem} object.
     * @return An equivalent {@link MediaItem} object, or null if none.
     */
    @Nullable
    public static MediaItem fromMediaItem(@Nullable Object itemObj) {
      if (itemObj == null || Build.VERSION.SDK_INT < 21) {
        return null;
      }
      MediaBrowser.MediaItem itemFwk = (MediaBrowser.MediaItem) itemObj;
      int flags = Api21Impl.getFlags(itemFwk);
      MediaDescriptionCompat descriptionCompat =
          MediaDescriptionCompat.fromMediaDescription(Api21Impl.getDescription(itemFwk));
      return new MediaItem(descriptionCompat, flags);
    }

    /**
     * Creates a list of {@link MediaItem} objects from a framework {@link
     * android.media.browse.MediaBrowser.MediaItem} object list.
     *
     * <p>This method is only supported on API 21+. On API 20 and below, it returns null.
     *
     * @param itemList A list of {@link android.media.browse.MediaBrowser.MediaItem} objects.
     * @return An equivalent list of {@link MediaItem} objects, or null if none.
     */
    @Nullable
    public static List<MediaItem> fromMediaItemList(@Nullable List<?> itemList) {
      if (itemList == null || Build.VERSION.SDK_INT < 21) {
        return null;
      }
      List<MediaItem> items = new ArrayList<>(itemList.size());
      for (Object itemObj : itemList) {
        MediaItem item = fromMediaItem(itemObj);
        if (item != null) {
          items.add(item);
        }
      }
      return items;
    }

    /**
     * Create a new MediaItem for use in browsing media.
     *
     * @param description The description of the media, which must include a media id.
     * @param flags The flags for this item.
     */
    public MediaItem(@Nullable MediaDescriptionCompat description, @Flags int flags) {
      if (description == null) {
        throw new IllegalArgumentException("description cannot be null");
      }
      if (TextUtils.isEmpty(description.getMediaId())) {
        throw new IllegalArgumentException("description must have a non-empty media id");
      }
      mFlags = flags;
      mDescription = description;
    }

    /** Private constructor. */
    MediaItem(Parcel in) {
      mFlags = in.readInt();
      mDescription = MediaDescriptionCompat.CREATOR.createFromParcel(in);
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
      out.writeInt(mFlags);
      mDescription.writeToParcel(out, flags);
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("MediaItem{");
      sb.append("mFlags=").append(mFlags);
      sb.append(", mDescription=").append(mDescription);
      sb.append('}');
      return sb.toString();
    }

    public static final Parcelable.Creator<MediaItem> CREATOR =
        new Parcelable.Creator<MediaItem>() {
          @Override
          public MediaItem createFromParcel(Parcel in) {
            return new MediaItem(in);
          }

          @Override
          public MediaItem[] newArray(int size) {
            return new MediaItem[size];
          }
        };

    /** Gets the flags of the item. */
    @Flags
    public int getFlags() {
      return mFlags;
    }

    /**
     * Returns whether this item is browsable.
     *
     * @see #FLAG_BROWSABLE
     */
    public boolean isBrowsable() {
      return (mFlags & FLAG_BROWSABLE) != 0;
    }

    /**
     * Returns whether this item is playable.
     *
     * @see #FLAG_PLAYABLE
     */
    public boolean isPlayable() {
      return (mFlags & FLAG_PLAYABLE) != 0;
    }

    /** Returns the description of the media. */
    public MediaDescriptionCompat getDescription() {
      return mDescription;
    }

    /**
     * Returns the media id in the {@link MediaDescriptionCompat} for this item.
     *
     * @see MediaMetadataCompat#METADATA_KEY_MEDIA_ID
     */
    @Nullable
    public String getMediaId() {
      return mDescription.getMediaId();
    }
  }

  /** Callbacks for connection related events. */
  public static class ConnectionCallback {
    @Nullable final MediaBrowser.ConnectionCallback mConnectionCallbackFwk;
    @Nullable ConnectionCallbackInternal mConnectionCallbackInternal;

    public ConnectionCallback() {
      if (Build.VERSION.SDK_INT >= 21) {
        mConnectionCallbackFwk = new ConnectionCallbackApi21();
      } else {
        mConnectionCallbackFwk = null;
      }
    }

    /**
     * Invoked after {@link MediaBrowserCompat#connect()} when the request has successfully
     * completed. This can also be called when the service is next running after it crashed or has
     * been killed.
     *
     * @see ServiceConnection#onServiceConnected(ComponentName, IBinder)
     * @see ServiceConnection#onServiceDisconnected(ComponentName)
     */
    public void onConnected() {}

    /**
     * Invoked when a connection to the browser service has been lost. This typically happens when
     * the process hosting the service has crashed or been killed. This does not remove the
     * connection itself -- this binding to the service will remain active, and {@link
     * #onConnected()} will be called when the service is next running.
     *
     * @see ServiceConnection#onServiceDisconnected(ComponentName)
     */
    public void onConnectionSuspended() {}

    /**
     * Invoked when the connection to the media browser service failed. Connection failures can
     * happen when the browser failed to bind to the service, or when it is rejected from the
     * service.
     */
    public void onConnectionFailed() {}

    void setInternalConnectionCallback(ConnectionCallbackInternal connectionCallbackInternal) {
      mConnectionCallbackInternal = connectionCallbackInternal;
    }

    interface ConnectionCallbackInternal {
      void onConnected();

      void onConnectionSuspended();

      void onConnectionFailed();
    }

    @RequiresApi(21)
    private class ConnectionCallbackApi21 extends MediaBrowser.ConnectionCallback {
      ConnectionCallbackApi21() {}

      @Override
      public void onConnected() {
        if (mConnectionCallbackInternal != null) {
          mConnectionCallbackInternal.onConnected();
        }
        ConnectionCallback.this.onConnected();
      }

      @Override
      public void onConnectionSuspended() {
        if (mConnectionCallbackInternal != null) {
          mConnectionCallbackInternal.onConnectionSuspended();
        }
        ConnectionCallback.this.onConnectionSuspended();
      }

      @Override
      public void onConnectionFailed() {
        if (mConnectionCallbackInternal != null) {
          mConnectionCallbackInternal.onConnectionFailed();
        }
        ConnectionCallback.this.onConnectionFailed();
      }
    }
  }

  /** Callbacks for subscription related events. */
  public abstract static class SubscriptionCallback {
    @Nullable final MediaBrowser.SubscriptionCallback mSubscriptionCallbackFwk;
    final IBinder mToken;
    @Nullable WeakReference<Subscription> mSubscriptionRef;

    public SubscriptionCallback() {
      mToken = new Binder();
      if (Build.VERSION.SDK_INT >= 26) {
        mSubscriptionCallbackFwk = new SubscriptionCallbackApi26();
      } else if (Build.VERSION.SDK_INT >= 21) {
        mSubscriptionCallbackFwk = new SubscriptionCallbackApi21();
      } else {
        mSubscriptionCallbackFwk = null;
      }
    }

    /**
     * Called when the list of children is loaded or updated.
     *
     * @param parentId The media id of the parent media item.
     * @param children The children which were loaded.
     */
    public void onChildrenLoaded(@Nullable String parentId, @Nullable List<MediaItem> children) {}

    /**
     * Called when the list of children is loaded or updated.
     *
     * @param parentId The media id of the parent media item.
     * @param children The children which were loaded.
     * @param options A bundle of service-specific arguments to send to the media browse service.
     *     The contents of this bundle may affect the information returned when browsing.
     */
    public void onChildrenLoaded(
        @Nullable String parentId, @Nullable List<MediaItem> children, @Nullable Bundle options) {}

    /**
     * Called when the id doesn't exist or other errors in subscribing.
     *
     * <p>If this is called, the subscription remains until {@link MediaBrowserCompat#unsubscribe}
     * called, because some errors may heal themselves.
     *
     * @param parentId The media id of the parent media item whose children could not be loaded.
     */
    public void onError(@Nullable String parentId) {}

    /**
     * Called when the id doesn't exist or other errors in subscribing.
     *
     * <p>If this is called, the subscription remains until {@link MediaBrowserCompat#unsubscribe}
     * called, because some errors may heal themselves.
     *
     * @param parentId The media id of the parent media item whose children could not be loaded.
     * @param options A bundle of service-specific arguments sent to the media browse service.
     */
    public void onError(@Nullable String parentId, @Nullable Bundle options) {}

    void setSubscription(Subscription subscription) {
      mSubscriptionRef = new WeakReference<>(subscription);
    }

    @RequiresApi(21)
    private class SubscriptionCallbackApi21 extends MediaBrowser.SubscriptionCallback {
      SubscriptionCallbackApi21() {}

      @Override
      public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
        Subscription sub = mSubscriptionRef == null ? null : mSubscriptionRef.get();
        if (sub == null) {
          SubscriptionCallback.this.onChildrenLoaded(
              parentId, MediaItem.fromMediaItemList(children));
        } else {
          List<MediaBrowserCompat.MediaItem> itemList =
              checkNotNull(MediaItem.fromMediaItemList(children));
          final List<SubscriptionCallback> callbacks = sub.getCallbacks();
          final List<@NullableType Bundle> optionsList = sub.getOptionsList();
          for (int i = 0; i < callbacks.size(); ++i) {
            Bundle options = optionsList.get(i);
            if (options == null) {
              SubscriptionCallback.this.onChildrenLoaded(parentId, itemList);
            } else {
              SubscriptionCallback.this.onChildrenLoaded(
                  parentId, applyOptions(itemList, options), options);
            }
          }
        }
      }

      @Override
      public void onError(String parentId) {
        SubscriptionCallback.this.onError(parentId);
      }

      @Nullable
      List<MediaBrowserCompat.MediaItem> applyOptions(
          List<MediaBrowserCompat.MediaItem> list, final Bundle options) {
        if (list == null) {
          return null;
        }
        int page = options.getInt(MediaBrowserCompat.EXTRA_PAGE, -1);
        int pageSize = options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, -1);
        if (page == -1 && pageSize == -1) {
          return list;
        }
        int fromIndex = pageSize * page;
        int toIndex = fromIndex + pageSize;
        if (page < 0 || pageSize < 1 || fromIndex >= list.size()) {
          return Collections.emptyList();
        }
        if (toIndex > list.size()) {
          toIndex = list.size();
        }
        return list.subList(fromIndex, toIndex);
      }
    }

    @RequiresApi(26)
    private class SubscriptionCallbackApi26 extends SubscriptionCallbackApi21 {
      SubscriptionCallbackApi26() {}

      @Override
      public void onChildrenLoaded(
          String parentId, List<MediaBrowser.MediaItem> children, Bundle options) {
        MediaSessionCompat.ensureClassLoader(options);
        SubscriptionCallback.this.onChildrenLoaded(
            parentId, MediaItem.fromMediaItemList(children), options);
      }

      @Override
      public void onError(String parentId, Bundle options) {
        MediaSessionCompat.ensureClassLoader(options);
        SubscriptionCallback.this.onError(parentId, options);
      }
    }
  }

  /** Callback for receiving the result of {@link #getItem}. */
  public abstract static class ItemCallback {
    @Nullable final MediaBrowser.ItemCallback mItemCallbackFwk;

    public ItemCallback() {
      if (Build.VERSION.SDK_INT >= 23) {
        mItemCallbackFwk = new ItemCallbackApi23();
      } else {
        mItemCallbackFwk = null;
      }
    }

    /**
     * Called when the item has been returned by the browser service.
     *
     * @param item The item that was returned or null if it doesn't exist.
     */
    public void onItemLoaded(@Nullable MediaItem item) {}

    /**
     * Called when the item doesn't exist or there was an error retrieving it.
     *
     * @param itemId The media id of the media item which could not be loaded.
     */
    public void onError(String itemId) {}

    @RequiresApi(23)
    private class ItemCallbackApi23 extends MediaBrowser.ItemCallback {
      ItemCallbackApi23() {}

      @Override
      public void onItemLoaded(MediaBrowser.MediaItem item) {
        ItemCallback.this.onItemLoaded(MediaItem.fromMediaItem(item));
      }

      @Override
      public void onError(String itemId) {
        ItemCallback.this.onError(itemId);
      }
    }
  }

  /** Callback for receiving the result of {@link #search}. */
  public abstract static class SearchCallback {
    /**
     * Called when the {@link #search} finished successfully.
     *
     * @param query The search query sent for the search request to the connected service.
     * @param extras The bundle of service-specific arguments sent to the connected service.
     * @param items The list of media items which contains the search result.
     */
    public void onSearchResult(String query, @Nullable Bundle extras, List<MediaItem> items) {}

    /**
     * Called when an error happens while {@link #search} or the connected service doesn't support
     * {@link #search}.
     *
     * @param query The search query sent for the search request to the connected service.
     * @param extras The bundle of service-specific arguments sent to the connected service.
     */
    public void onError(String query, @Nullable Bundle extras) {}
  }

  /** Callback for receiving the result of {@link #sendCustomAction}. */
  public abstract static class CustomActionCallback {
    /**
     * Called when an interim update was delivered from the connected service while performing the
     * custom action.
     *
     * @param action The custom action sent to the connected service.
     * @param extras The bundle of service-specific arguments sent to the connected service.
     * @param data The additional data delivered from the connected service.
     */
    public void onProgressUpdate(String action, @Nullable Bundle extras, @Nullable Bundle data) {}

    /**
     * Called when the custom action finished successfully.
     *
     * @param action The custom action sent to the connected service.
     * @param extras The bundle of service-specific arguments sent to the connected service.
     * @param resultData The additional data delivered from the connected service.
     */
    public void onResult(String action, @Nullable Bundle extras, @Nullable Bundle resultData) {}

    /**
     * Called when an error happens while performing the custom action or the connected service
     * doesn't support the requested custom action.
     *
     * @param action The custom action sent to the connected service.
     * @param extras The bundle of service-specific arguments sent to the connected service.
     * @param data The additional data delivered from the connected service.
     */
    public void onError(String action, @Nullable Bundle extras, @Nullable Bundle data) {}
  }

  interface MediaBrowserImpl {
    void connect();

    void disconnect();

    boolean isConnected();

    ComponentName getServiceComponent();

    String getRoot();

    @Nullable
    Bundle getExtras();

    MediaSessionCompat.Token getSessionToken();

    void subscribe(String parentId, @Nullable Bundle options, SubscriptionCallback callback);

    void unsubscribe(String parentId, @Nullable SubscriptionCallback callback);

    void getItem(String mediaId, ItemCallback cb);

    void search(String query, @Nullable Bundle extras, SearchCallback callback);

    void sendCustomAction(
        String action, @Nullable Bundle extras, @Nullable CustomActionCallback callback);

    @Nullable
    Bundle getNotifyChildrenChangedOptions();
  }

  interface MediaBrowserServiceCallbackImpl {
    void onServiceConnected(
        Messenger callback,
        @Nullable String root,
        @Nullable MediaSessionCompat.Token session,
        @Nullable Bundle extra);

    void onConnectionFailed(Messenger callback);

    void onLoadChildren(
        Messenger callback,
        @Nullable String parentId,
        @Nullable List<MediaItem> list,
        @Nullable Bundle options,
        @Nullable Bundle notifyChildrenChangedOptions);
  }

  static class MediaBrowserImplBase implements MediaBrowserImpl, MediaBrowserServiceCallbackImpl {
    static final int CONNECT_STATE_DISCONNECTING = 0;
    static final int CONNECT_STATE_DISCONNECTED = 1;
    static final int CONNECT_STATE_CONNECTING = 2;
    static final int CONNECT_STATE_CONNECTED = 3;
    static final int CONNECT_STATE_SUSPENDED = 4;

    final Context mContext;
    final ComponentName mServiceComponent;
    final ConnectionCallback mCallback;
    @Nullable final Bundle mRootHints;

    @SuppressWarnings({
      "argument.type.incompatible",
      "assignment.type.incompatible"
    }) // Using this before constructor finishes
    final CallbackHandler mHandler = new CallbackHandler(this);

    private final ArrayMap<String, Subscription> mSubscriptions = new ArrayMap<>();

    int mState = CONNECT_STATE_DISCONNECTED;
    @Nullable MediaServiceConnection mServiceConnection;
    @Nullable ServiceBinderWrapper mServiceBinderWrapper;
    @Nullable Messenger mCallbacksMessenger;
    @Nullable private String mRootId;
    @Nullable private MediaSessionCompat.Token mMediaSessionToken;
    @Nullable private Bundle mExtras;
    @Nullable private Bundle mNotifyChildrenChangedOptions;

    public MediaBrowserImplBase(
        Context context,
        ComponentName serviceComponent,
        ConnectionCallback callback,
        @Nullable Bundle rootHints) {
      if (context == null) {
        throw new IllegalArgumentException("context must not be null");
      }
      if (serviceComponent == null) {
        throw new IllegalArgumentException("service component must not be null");
      }
      if (callback == null) {
        throw new IllegalArgumentException("connection callback must not be null");
      }
      mContext = context;
      mServiceComponent = serviceComponent;
      mCallback = callback;
      mRootHints = rootHints == null ? null : new Bundle(rootHints);
    }

    @Override
    @SuppressWarnings("ObjectToString")
    public void connect() {
      if (mState != CONNECT_STATE_DISCONNECTING && mState != CONNECT_STATE_DISCONNECTED) {
        throw new IllegalStateException(
            "connect() called while neigther disconnecting nor "
                + "disconnected (state="
                + getStateLabel(mState)
                + ")");
      }

      mState = CONNECT_STATE_CONNECTING;
      mHandler.post(
          new Runnable() {
            @Override
            public void run() {
              // mState could be changed by the Runnable of disconnect()
              if (mState == CONNECT_STATE_DISCONNECTING) {
                return;
              }
              mState = CONNECT_STATE_CONNECTING;
              // TODO: remove this extra check.
              if (DEBUG) {
                if (mServiceConnection != null) {
                  throw new RuntimeException(
                      "mServiceConnection should be null. Instead "
                          + "it is "
                          + mServiceConnection);
                }
              }
              if (mServiceBinderWrapper != null) {
                throw new RuntimeException(
                    "mServiceBinderWrapper should be null. Instead "
                        + "it is "
                        + mServiceBinderWrapper);
              }
              if (mCallbacksMessenger != null) {
                throw new RuntimeException(
                    "mCallbacksMessenger should be null. Instead "
                        + "it is "
                        + mCallbacksMessenger);
              }

              final Intent intent = new Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE);
              intent.setComponent(mServiceComponent);

              mServiceConnection = new MediaServiceConnection();
              boolean bound = false;
              try {
                bound = mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
              } catch (Exception ex) {
                Log.e(TAG, "Failed binding to service " + mServiceComponent);
              }

              if (!bound) {
                // Tell them that it didn't work.
                forceCloseConnection();
                mCallback.onConnectionFailed();
              }

              if (DEBUG) {
                Log.d(TAG, "connect...");
                dump();
              }
            }
          });
    }

    @Override
    public void disconnect() {
      // It's ok to call this any state, because allowing this lets apps not have
      // to check isConnected() unnecessarily. They won't appreciate the extra
      // assertions for this. We do everything we can here to go back to a valid state.
      mState = CONNECT_STATE_DISCONNECTING;
      mHandler.post(
          new Runnable() {
            @Override
            public void run() {
              // connect() could be called before this. Then we will disconnect and reconnect.
              if (mCallbacksMessenger != null) {
                try {
                  checkNotNull(mServiceBinderWrapper).disconnect(mCallbacksMessenger);
                } catch (RemoteException ex) {
                  // We are disconnecting anyway. Log, just for posterity but it's not
                  // a big problem.
                  Log.w(TAG, "RemoteException during connect for " + mServiceComponent);
                }
              }
              int state = mState;
              forceCloseConnection();
              // If the state was not CONNECT_STATE_DISCONNECTING, keep the state so that
              // the operation came after disconnect() can be handled properly.
              if (state != CONNECT_STATE_DISCONNECTING) {
                mState = state;
              }
              if (DEBUG) {
                Log.d(TAG, "disconnect...");
                dump();
              }
            }
          });
    }

    /**
     * Null out the variables and unbind from the service. This doesn't include calling disconnect
     * on the service, because we only try to do that in the clean shutdown cases.
     *
     * <p>Everywhere that calls this EXCEPT for disconnect() should follow it with a call to
     * mCallback.onConnectionFailed(). Disconnect doesn't do that callback for a clean shutdown, but
     * everywhere else is a dirty shutdown and should notify the app.
     */
    void forceCloseConnection() {
      if (mServiceConnection != null) {
        mContext.unbindService(mServiceConnection);
      }
      mState = CONNECT_STATE_DISCONNECTED;
      mServiceConnection = null;
      mServiceBinderWrapper = null;
      mCallbacksMessenger = null;
      mHandler.setCallbacksMessenger(null);
      mRootId = null;
      mMediaSessionToken = null;
    }

    @Override
    public boolean isConnected() {
      return mState == CONNECT_STATE_CONNECTED;
    }

    @Override
    public ComponentName getServiceComponent() {
      if (!isConnected()) {
        throw new IllegalStateException(
            "getServiceComponent() called while not connected" + " (state=" + mState + ")");
      }
      return mServiceComponent;
    }

    @Override
    public String getRoot() {
      if (!isConnected()) {
        throw new IllegalStateException(
            "getRoot() called while not connected" + "(state=" + getStateLabel(mState) + ")");
      }
      return checkNotNull(mRootId);
    }

    @Override
    @Nullable
    public Bundle getExtras() {
      if (!isConnected()) {
        throw new IllegalStateException(
            "getExtras() called while not connected (state=" + getStateLabel(mState) + ")");
      }
      return mExtras;
    }

    @Override
    public MediaSessionCompat.Token getSessionToken() {
      if (!isConnected()) {
        throw new IllegalStateException(
            "getSessionToken() called while not connected" + "(state=" + mState + ")");
      }
      return checkNotNull(mMediaSessionToken);
    }

    @Override
    public void subscribe(
        String parentId, @Nullable Bundle options, SubscriptionCallback callback) {
      // Update or create the subscription.
      Subscription sub = mSubscriptions.get(parentId);
      if (sub == null) {
        sub = new Subscription();
        mSubscriptions.put(parentId, sub);
      }
      Bundle copiedOptions = options == null ? null : new Bundle(options);
      sub.putCallback(copiedOptions, callback);

      // If we are connected, tell the service that we are watching. If we aren't
      // connected, the service will be told when we connect.
      if (isConnected()) {
        try {
          checkNotNull(mServiceBinderWrapper)
              .addSubscription(
                  parentId, callback.mToken, copiedOptions, checkNotNull(mCallbacksMessenger));
        } catch (RemoteException e) {
          // Process is crashing. We will disconnect, and upon reconnect we will
          // automatically reregister. So nothing to do here.
          Log.d(TAG, "addSubscription failed with RemoteException parentId=" + parentId);
        }
      }
    }

    @Override
    public void unsubscribe(String parentId, @Nullable SubscriptionCallback callback) {
      Subscription sub = mSubscriptions.get(parentId);
      if (sub == null) {
        return;
      }

      // Tell the service if necessary.
      try {
        if (callback == null) {
          if (isConnected()) {
            checkNotNull(mServiceBinderWrapper)
                .removeSubscription(parentId, null, checkNotNull(mCallbacksMessenger));
          }
        } else {
          final List<SubscriptionCallback> callbacks = sub.getCallbacks();
          final List<@NullableType Bundle> optionsList = sub.getOptionsList();
          for (int i = callbacks.size() - 1; i >= 0; --i) {
            if (callbacks.get(i) == callback) {
              if (isConnected()) {
                checkNotNull(mServiceBinderWrapper)
                    .removeSubscription(
                        parentId, callback.mToken, checkNotNull(mCallbacksMessenger));
              }
              callbacks.remove(i);
              optionsList.remove(i);
            }
          }
        }
      } catch (RemoteException ex) {
        // Process is crashing. We will disconnect, and upon reconnect we will
        // automatically reregister. So nothing to do here.
        Log.d(TAG, "removeSubscription failed with RemoteException parentId=" + parentId);
      }

      if (sub.isEmpty() || callback == null) {
        mSubscriptions.remove(parentId);
      }
    }

    @Override
    public void getItem(final String mediaId, final ItemCallback cb) {
      if (TextUtils.isEmpty(mediaId)) {
        throw new IllegalArgumentException("mediaId is empty");
      }
      if (cb == null) {
        throw new IllegalArgumentException("cb is null");
      }
      if (!isConnected()) {
        Log.i(TAG, "Not connected, unable to retrieve the MediaItem.");
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                cb.onError(mediaId);
              }
            });
        return;
      }
      ResultReceiver receiver = new ItemReceiver(mediaId, cb, mHandler);
      try {
        checkNotNull(mServiceBinderWrapper)
            .getMediaItem(mediaId, receiver, checkNotNull(mCallbacksMessenger));
      } catch (RemoteException e) {
        Log.i(TAG, "Remote error getting media item: " + mediaId);
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                cb.onError(mediaId);
              }
            });
      }
    }

    @Override
    public void search(final String query, @Nullable Bundle extras, final SearchCallback callback) {
      if (!isConnected()) {
        throw new IllegalStateException(
            "search() called while not connected" + " (state=" + getStateLabel(mState) + ")");
      }

      ResultReceiver receiver = new SearchResultReceiver(query, extras, callback, mHandler);
      try {
        checkNotNull(mServiceBinderWrapper)
            .search(query, extras, receiver, checkNotNull(mCallbacksMessenger));
      } catch (RemoteException e) {
        Log.i(TAG, "Remote error searching items with query: " + query, e);
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                callback.onError(query, extras);
              }
            });
      }
    }

    @Override
    public void sendCustomAction(
        final String action,
        @Nullable Bundle extras,
        @Nullable final CustomActionCallback callback) {
      if (!isConnected()) {
        throw new IllegalStateException(
            "Cannot send a custom action ("
                + action
                + ") with "
                + "extras "
                + extras
                + " because the browser is not connected to the "
                + "service.");
      }

      ResultReceiver receiver = new CustomActionResultReceiver(action, extras, callback, mHandler);
      try {
        checkNotNull(mServiceBinderWrapper)
            .sendCustomAction(action, extras, receiver, checkNotNull(mCallbacksMessenger));
      } catch (RemoteException e) {
        Log.i(
            TAG,
            "Remote error sending a custom action: action=" + action + ", extras=" + extras,
            e);
        if (callback != null) {
          mHandler.post(
              new Runnable() {
                @Override
                public void run() {
                  callback.onError(action, extras, null);
                }
              });
        }
      }
    }

    @Override
    public void onServiceConnected(
        final Messenger callback,
        @Nullable String root,
        @Nullable MediaSessionCompat.Token session,
        @Nullable Bundle extra) {
      // Check to make sure there hasn't been a disconnect or a different ServiceConnection.
      if (!isCurrent(callback, "onConnect")) {
        return;
      }
      // Don't allow them to call us twice.
      if (mState != CONNECT_STATE_CONNECTING) {
        Log.w(TAG, "onConnect from service while mState=" + getStateLabel(mState) + "... ignoring");
        return;
      }
      mRootId = root;
      mMediaSessionToken = session;
      mExtras = extra;
      mState = CONNECT_STATE_CONNECTED;

      if (DEBUG) {
        Log.d(TAG, "ServiceCallbacks.onConnect...");
        dump();
      }
      mCallback.onConnected();

      // we may receive some subscriptions before we are connected, so re-subscribe
      // everything now
      try {
        for (Map.Entry<String, Subscription> subscriptionEntry : mSubscriptions.entrySet()) {
          String id = subscriptionEntry.getKey();
          Subscription sub = subscriptionEntry.getValue();
          List<SubscriptionCallback> callbackList = sub.getCallbacks();
          List<@NullableType Bundle> optionsList = sub.getOptionsList();
          for (int i = 0; i < callbackList.size(); ++i) {
            checkNotNull(mServiceBinderWrapper)
                .addSubscription(
                    id,
                    callbackList.get(i).mToken,
                    optionsList.get(i),
                    checkNotNull(mCallbacksMessenger));
          }
        }
      } catch (RemoteException ex) {
        // Process is crashing. We will disconnect, and upon reconnect we will
        // automatically reregister. So nothing to do here.
        Log.d(TAG, "addSubscription failed with RemoteException.");
      }
    }

    @Override
    public void onConnectionFailed(final Messenger callback) {
      Log.e(TAG, "onConnectFailed for " + mServiceComponent);

      // Check to make sure there hasn't been a disconnect or a different ServiceConnection.
      if (!isCurrent(callback, "onConnectFailed")) {
        return;
      }
      // Don't allow them to call us twice.
      if (mState != CONNECT_STATE_CONNECTING) {
        Log.w(TAG, "onConnect from service while mState=" + getStateLabel(mState) + "... ignoring");
        return;
      }

      // Clean up
      forceCloseConnection();

      // Tell the app.
      mCallback.onConnectionFailed();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onLoadChildren(
        final Messenger callback,
        @Nullable String parentId,
        @Nullable List<MediaItem> list,
        @Nullable Bundle options,
        @Nullable Bundle notifyChildrenChangedOptions) {
      // Check that there hasn't been a disconnect or a different ServiceConnection.
      if (!isCurrent(callback, "onLoadChildren")) {
        return;
      }

      if (DEBUG) {
        Log.d(TAG, "onLoadChildren for " + mServiceComponent + " id=" + parentId);
      }

      // Check that the subscription is still subscribed.
      Subscription subscription = parentId == null ? null : mSubscriptions.get(parentId);
      if (subscription == null) {
        if (DEBUG) {
          Log.d(TAG, "onLoadChildren for id that isn't subscribed id=" + parentId);
        }
        return;
      }

      // Tell the app.
      SubscriptionCallback subscriptionCallback = subscription.getCallback(options);
      if (subscriptionCallback != null) {
        if (options == null) {
          if (list == null) {
            subscriptionCallback.onError(parentId);
          } else {
            mNotifyChildrenChangedOptions = notifyChildrenChangedOptions;
            subscriptionCallback.onChildrenLoaded(parentId, list);
            mNotifyChildrenChangedOptions = null;
          }
        } else {
          if (list == null) {
            subscriptionCallback.onError(parentId, options);
          } else {
            mNotifyChildrenChangedOptions = notifyChildrenChangedOptions;
            subscriptionCallback.onChildrenLoaded(parentId, list, options);
            mNotifyChildrenChangedOptions = null;
          }
        }
      }
    }

    @Nullable
    @Override
    public Bundle getNotifyChildrenChangedOptions() {
      return mNotifyChildrenChangedOptions;
    }

    /** For debugging. */
    private static String getStateLabel(int state) {
      switch (state) {
        case CONNECT_STATE_DISCONNECTING:
          return "CONNECT_STATE_DISCONNECTING";
        case CONNECT_STATE_DISCONNECTED:
          return "CONNECT_STATE_DISCONNECTED";
        case CONNECT_STATE_CONNECTING:
          return "CONNECT_STATE_CONNECTING";
        case CONNECT_STATE_CONNECTED:
          return "CONNECT_STATE_CONNECTED";
        case CONNECT_STATE_SUSPENDED:
          return "CONNECT_STATE_SUSPENDED";
        default:
          return "UNKNOWN/" + state;
      }
    }

    /** Return true if {@code callback} is the current ServiceCallbacks. Also logs if it's not. */
    @SuppressWarnings({"ReferenceEquality", "ObjectToString"})
    private boolean isCurrent(Messenger callback, String funcName) {
      if (mCallbacksMessenger != callback
          || mState == CONNECT_STATE_DISCONNECTING
          || mState == CONNECT_STATE_DISCONNECTED) {
        if (mState != CONNECT_STATE_DISCONNECTING && mState != CONNECT_STATE_DISCONNECTED) {
          Log.i(
              TAG,
              funcName
                  + " for "
                  + mServiceComponent
                  + " with mCallbacksMessenger="
                  + mCallbacksMessenger
                  + " this="
                  + this);
        }
        return false;
      }
      return true;
    }

    /** Log internal state. */
    @SuppressWarnings("ObjectToString")
    void dump() {
      Log.d(TAG, "MediaBrowserCompat...");
      Log.d(TAG, "  mServiceComponent=" + mServiceComponent);
      Log.d(TAG, "  mCallback=" + mCallback);
      Log.d(TAG, "  mRootHints=" + mRootHints);
      Log.d(TAG, "  mState=" + getStateLabel(mState));
      Log.d(TAG, "  mServiceConnection=" + mServiceConnection);
      Log.d(TAG, "  mServiceBinderWrapper=" + mServiceBinderWrapper);
      Log.d(TAG, "  mCallbacksMessenger=" + mCallbacksMessenger);
      Log.d(TAG, "  mRootId=" + mRootId);
      Log.d(TAG, "  mMediaSessionToken=" + mMediaSessionToken);
    }

    /** ServiceConnection to the other app. */
    private class MediaServiceConnection implements ServiceConnection {
      MediaServiceConnection() {}

      @Override
      public void onServiceConnected(final ComponentName name, final IBinder binder) {
        postOrRun(
            new Runnable() {
              @Override
              public void run() {
                if (DEBUG) {
                  Log.d(
                      TAG,
                      "MediaServiceConnection.onServiceConnected name="
                          + name
                          + " binder="
                          + binder);
                  dump();
                }

                // Make sure we are still the current connection, and that they haven't
                // called disconnect().
                if (!isCurrent("onServiceConnected")) {
                  return;
                }

                // Save their binder
                ServiceBinderWrapper wrapper = new ServiceBinderWrapper(binder, mRootHints);
                mServiceBinderWrapper = wrapper;

                // We make a new mServiceCallbacks each time we connect so that we can drop
                // responses from previous connections.
                Messenger messenger = new Messenger(mHandler);
                mCallbacksMessenger = messenger;
                mHandler.setCallbacksMessenger(mCallbacksMessenger);

                mState = CONNECT_STATE_CONNECTING;

                // Call connect, which is async. When we get a response from that we will
                // say that we're connected.
                try {
                  if (DEBUG) {
                    Log.d(TAG, "ServiceCallbacks.onConnect...");
                    dump();
                  }
                  wrapper.connect(mContext, messenger);
                } catch (RemoteException ex) {
                  // Connect failed, which isn't good. But the auto-reconnect on the
                  // service will take over and we will come back. We will also get the
                  // onServiceDisconnected, which has all the cleanup code. So let that
                  // do it.
                  Log.w(TAG, "RemoteException during connect for " + mServiceComponent);
                  if (DEBUG) {
                    Log.d(TAG, "ServiceCallbacks.onConnect...");
                    dump();
                  }
                }
              }
            });
      }

      @Override
      public void onServiceDisconnected(final ComponentName name) {
        postOrRun(
            new Runnable() {
              @Override
              public void run() {
                if (DEBUG) {
                  Log.d(
                      TAG,
                      "MediaServiceConnection.onServiceDisconnected name="
                          + name
                          + " this="
                          + this
                          + " mServiceConnection="
                          + mServiceConnection);
                  dump();
                }

                // Make sure we are still the current connection, and that they haven't
                // called disconnect().
                if (!isCurrent("onServiceDisconnected")) {
                  return;
                }

                // Clear out what we set in onServiceConnected
                mServiceBinderWrapper = null;
                mCallbacksMessenger = null;
                mHandler.setCallbacksMessenger(null);

                // And tell the app that it's suspended.
                mState = CONNECT_STATE_SUSPENDED;
                mCallback.onConnectionSuspended();
              }
            });
      }

      private void postOrRun(Runnable r) {
        if (Thread.currentThread() == mHandler.getLooper().getThread()) {
          r.run();
        } else {
          mHandler.post(r);
        }
      }

      /** Return true if this is the current ServiceConnection. Also logs if it's not. */
      boolean isCurrent(String funcName) {
        if (mServiceConnection != this
            || mState == CONNECT_STATE_DISCONNECTING
            || mState == CONNECT_STATE_DISCONNECTED) {
          if (mState != CONNECT_STATE_DISCONNECTING && mState != CONNECT_STATE_DISCONNECTED) {
            // Check mState, because otherwise this log is noisy.
            Log.i(
                TAG,
                funcName
                    + " for "
                    + mServiceComponent
                    + " with mServiceConnection="
                    + mServiceConnection
                    + " this="
                    + this);
          }
          return false;
        }
        return true;
      }
    }
  }

  @RequiresApi(21)
  static class MediaBrowserImplApi21
      implements MediaBrowserImpl,
          MediaBrowserServiceCallbackImpl,
          ConnectionCallback.ConnectionCallbackInternal {
    final Context mContext;
    protected final MediaBrowser mBrowserFwk;
    protected final Bundle mRootHints;

    @SuppressWarnings({
      "argument.type.incompatible",
      "assignment.type.incompatible"
    }) // Using this before constructor finishes
    protected final CallbackHandler mHandler = new CallbackHandler(this);

    private final ArrayMap<String, Subscription> mSubscriptions = new ArrayMap<>();

    protected int mServiceVersion;
    @Nullable protected ServiceBinderWrapper mServiceBinderWrapper;
    @Nullable protected Messenger mCallbacksMessenger;
    @Nullable private MediaSessionCompat.Token mMediaSessionToken;
    @Nullable private Bundle mNotifyChildrenChangedOptions;

    @SuppressWarnings("argument.type.incompatible") // Using this before constructor finishes
    MediaBrowserImplApi21(
        Context context,
        ComponentName serviceComponent,
        ConnectionCallback callback,
        @Nullable Bundle rootHints) {
      mContext = context;
      mRootHints = (rootHints != null ? new Bundle(rootHints) : new Bundle());
      mRootHints.putInt(EXTRA_CLIENT_VERSION, CLIENT_VERSION_CURRENT);
      mRootHints.putInt(EXTRA_CALLING_PID, Process.myPid());
      callback.setInternalConnectionCallback(this);
      mBrowserFwk =
          new MediaBrowser(
              context, serviceComponent, checkNotNull(callback.mConnectionCallbackFwk), mRootHints);
    }

    @Override
    public void connect() {
      mBrowserFwk.connect();
    }

    @Override
    public void disconnect() {
      if (mServiceBinderWrapper != null && mCallbacksMessenger != null) {
        try {
          mServiceBinderWrapper.unregisterCallbackMessenger(mCallbacksMessenger);
        } catch (RemoteException e) {
          Log.i(TAG, "Remote error unregistering client messenger.");
        }
      }
      mBrowserFwk.disconnect();
    }

    @Override
    public boolean isConnected() {
      return mBrowserFwk.isConnected();
    }

    @Override
    public ComponentName getServiceComponent() {
      return mBrowserFwk.getServiceComponent();
    }

    @Override
    public String getRoot() {
      return mBrowserFwk.getRoot();
    }

    @Override
    @Nullable
    public Bundle getExtras() {
      return mBrowserFwk.getExtras();
    }

    @Override
    public MediaSessionCompat.Token getSessionToken() {
      if (mMediaSessionToken == null) {
        mMediaSessionToken = MediaSessionCompat.Token.fromToken(mBrowserFwk.getSessionToken());
      }
      return mMediaSessionToken;
    }

    @Override
    public void subscribe(
        final String parentId, @Nullable Bundle options, final SubscriptionCallback callback) {
      // Update or create the subscription.
      Subscription sub = mSubscriptions.get(parentId);
      if (sub == null) {
        sub = new Subscription();
        mSubscriptions.put(parentId, sub);
      }
      callback.setSubscription(sub);
      Bundle copiedOptions = options == null ? null : new Bundle(options);
      sub.putCallback(copiedOptions, callback);

      if (mServiceBinderWrapper == null) {
        // TODO: When MediaBrowser is connected to framework's MediaBrowserService,
        // subscribe with options won't work properly.
        mBrowserFwk.subscribe(parentId, checkNotNull(callback.mSubscriptionCallbackFwk));
      } else {
        try {
          mServiceBinderWrapper.addSubscription(
              parentId, callback.mToken, copiedOptions, checkNotNull(mCallbacksMessenger));
        } catch (RemoteException e) {
          // Process is crashing. We will disconnect, and upon reconnect we will
          // automatically reregister. So nothing to do here.
          Log.i(TAG, "Remote error subscribing media item: " + parentId);
        }
      }
    }

    @Override
    public void unsubscribe(String parentId, @Nullable SubscriptionCallback callback) {
      Subscription sub = mSubscriptions.get(parentId);
      if (sub == null) {
        return;
      }

      ServiceBinderWrapper serviceBinderWrapper = this.mServiceBinderWrapper;
      if (serviceBinderWrapper == null) {
        if (callback == null) {
          mBrowserFwk.unsubscribe(parentId);
        } else {
          final List<SubscriptionCallback> callbacks = sub.getCallbacks();
          final List<@NullableType Bundle> optionsList = sub.getOptionsList();
          for (int i = callbacks.size() - 1; i >= 0; --i) {
            if (callbacks.get(i) == callback) {
              callbacks.remove(i);
              optionsList.remove(i);
            }
          }
          if (callbacks.size() == 0) {
            mBrowserFwk.unsubscribe(parentId);
          }
        }
      } else {
        // Tell the service if necessary.
        try {
          if (callback == null) {
            serviceBinderWrapper.removeSubscription(
                parentId, null, checkNotNull(mCallbacksMessenger));
          } else {
            final List<SubscriptionCallback> callbacks = sub.getCallbacks();
            final List<@NullableType Bundle> optionsList = sub.getOptionsList();
            for (int i = callbacks.size() - 1; i >= 0; --i) {
              if (callbacks.get(i) == callback) {
                serviceBinderWrapper.removeSubscription(
                    parentId, callback.mToken, checkNotNull(mCallbacksMessenger));
                callbacks.remove(i);
                optionsList.remove(i);
              }
            }
          }
        } catch (RemoteException ex) {
          // Process is crashing. We will disconnect, and upon reconnect we will
          // automatically reregister. So nothing to do here.
          Log.d(TAG, "removeSubscription failed with RemoteException parentId=" + parentId);
        }
      }

      if (sub.isEmpty() || callback == null) {
        mSubscriptions.remove(parentId);
      }
    }

    @Override
    public void getItem(final String mediaId, final ItemCallback cb) {
      if (TextUtils.isEmpty(mediaId)) {
        throw new IllegalArgumentException("mediaId is empty");
      }
      if (cb == null) {
        throw new IllegalArgumentException("cb is null");
      }
      if (!mBrowserFwk.isConnected()) {
        Log.i(TAG, "Not connected, unable to retrieve the MediaItem.");
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                cb.onError(mediaId);
              }
            });
        return;
      }
      if (mServiceBinderWrapper == null) {
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                // Default framework implementation.
                cb.onError(mediaId);
              }
            });
        return;
      }
      ResultReceiver receiver = new ItemReceiver(mediaId, cb, mHandler);
      try {
        mServiceBinderWrapper.getMediaItem(mediaId, receiver, checkNotNull(mCallbacksMessenger));
      } catch (RemoteException e) {
        Log.i(TAG, "Remote error getting media item: " + mediaId);
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                cb.onError(mediaId);
              }
            });
      }
    }

    @Override
    public void search(final String query, @Nullable Bundle extras, final SearchCallback callback) {
      if (!isConnected()) {
        throw new IllegalStateException("search() called while not connected");
      }
      if (mServiceBinderWrapper == null) {
        Log.i(TAG, "The connected service doesn't support search.");
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                // Default framework implementation.
                callback.onError(query, extras);
              }
            });
        return;
      }

      ResultReceiver receiver = new SearchResultReceiver(query, extras, callback, mHandler);
      try {
        mServiceBinderWrapper.search(query, extras, receiver, checkNotNull(mCallbacksMessenger));
      } catch (RemoteException e) {
        Log.i(TAG, "Remote error searching items with query: " + query, e);
        mHandler.post(
            new Runnable() {
              @Override
              public void run() {
                callback.onError(query, extras);
              }
            });
      }
    }

    @Override
    public void sendCustomAction(
        final String action,
        @Nullable Bundle extras,
        @Nullable final CustomActionCallback callback) {
      if (!isConnected()) {
        throw new IllegalStateException(
            "Cannot send a custom action ("
                + action
                + ") with "
                + "extras "
                + extras
                + " because the browser is not connected to the "
                + "service.");
      }
      ServiceBinderWrapper serviceBinderWrapper = this.mServiceBinderWrapper;
      if (serviceBinderWrapper == null) {
        Log.i(TAG, "The connected service doesn't support sendCustomAction.");
        if (callback != null) {
          mHandler.post(
              new Runnable() {
                @Override
                public void run() {
                  callback.onError(action, extras, null);
                }
              });
        }
        return;
      }

      ResultReceiver receiver = new CustomActionResultReceiver(action, extras, callback, mHandler);
      try {
        serviceBinderWrapper.sendCustomAction(
            action, extras, receiver, checkNotNull(mCallbacksMessenger));
      } catch (RemoteException e) {
        Log.i(
            TAG,
            "Remote error sending a custom action: action=" + action + ", extras=" + extras,
            e);
        if (callback != null) {
          mHandler.post(
              new Runnable() {
                @Override
                public void run() {
                  callback.onError(action, extras, null);
                }
              });
        }
      }
    }

    @Override
    public void onConnected() {
      Bundle extras;
      try {
        extras = mBrowserFwk.getExtras();
      } catch (IllegalStateException e) {
        // Should not be here since onConnected() will be called in a connected state.
        Log.e(TAG, "Unexpected IllegalStateException", e);
        return;
      }
      if (extras == null) {
        return;
      }
      mServiceVersion = extras.getInt(EXTRA_SERVICE_VERSION, 0);
      IBinder serviceBinder = extras.getBinder(EXTRA_MESSENGER_BINDER);
      if (serviceBinder != null) {
        ServiceBinderWrapper serviceBinderWrapper =
            new ServiceBinderWrapper(serviceBinder, mRootHints);
        this.mServiceBinderWrapper = serviceBinderWrapper;
        Messenger messenger = new Messenger(mHandler);
        ;
        this.mCallbacksMessenger = messenger;
        mHandler.setCallbacksMessenger(messenger);
        try {
          serviceBinderWrapper.registerCallbackMessenger(mContext, messenger);
        } catch (RemoteException e) {
          Log.i(TAG, "Remote error registering client messenger.");
        }
      }
      IMediaSession sessionToken =
          IMediaSession.Stub.asInterface(extras.getBinder(EXTRA_SESSION_BINDER));
      if (sessionToken != null) {
        mMediaSessionToken =
            MediaSessionCompat.Token.fromToken(mBrowserFwk.getSessionToken(), sessionToken);
      }
    }

    @Override
    public void onConnectionSuspended() {
      mServiceBinderWrapper = null;
      mCallbacksMessenger = null;
      mMediaSessionToken = null;
      mHandler.setCallbacksMessenger(null);
    }

    @Override
    public void onConnectionFailed() {
      // Do noting
    }

    @Override
    public void onServiceConnected(
        final Messenger callback,
        @Nullable final String root,
        @Nullable final MediaSessionCompat.Token session,
        @Nullable Bundle extra) {
      // This method will not be called.
    }

    @Override
    public void onConnectionFailed(Messenger callback) {
      // This method will not be called.
    }

    @Override
    @SuppressWarnings({"ReferenceEquality", "unchecked"})
    public void onLoadChildren(
        Messenger callback,
        @Nullable String parentId,
        @Nullable List<MediaItem> list,
        @Nullable Bundle options,
        @Nullable Bundle notifyChildrenChangedOptions) {
      if (mCallbacksMessenger != callback) {
        return;
      }

      // Check that the subscription is still subscribed.
      Subscription subscription = parentId == null ? null : mSubscriptions.get(parentId);
      if (subscription == null) {
        if (DEBUG) {
          Log.d(TAG, "onLoadChildren for id that isn't subscribed id=" + parentId);
        }
        return;
      }

      // Tell the app.
      SubscriptionCallback subscriptionCallback = subscription.getCallback(options);
      if (subscriptionCallback != null) {
        if (options == null) {
          if (list == null) {
            subscriptionCallback.onError(parentId);
          } else {
            mNotifyChildrenChangedOptions = notifyChildrenChangedOptions;
            subscriptionCallback.onChildrenLoaded(parentId, list);
            mNotifyChildrenChangedOptions = null;
          }
        } else {
          if (list == null) {
            subscriptionCallback.onError(parentId, options);
          } else {
            mNotifyChildrenChangedOptions = notifyChildrenChangedOptions;
            subscriptionCallback.onChildrenLoaded(parentId, list, options);
            mNotifyChildrenChangedOptions = null;
          }
        }
      }
    }

    @Nullable
    @Override
    public Bundle getNotifyChildrenChangedOptions() {
      return mNotifyChildrenChangedOptions;
    }
  }

  @RequiresApi(23)
  static class MediaBrowserImplApi23 extends MediaBrowserImplApi21 {
    MediaBrowserImplApi23(
        Context context,
        ComponentName serviceComponent,
        ConnectionCallback callback,
        @Nullable Bundle rootHints) {
      super(context, serviceComponent, callback, rootHints);
    }

    @Override
    public void getItem(final String mediaId, final ItemCallback cb) {
      if (mServiceBinderWrapper == null) {
        mBrowserFwk.getItem(mediaId, checkNotNull(cb.mItemCallbackFwk));
      } else {
        super.getItem(mediaId, cb);
      }
    }
  }

  @RequiresApi(26)
  static class MediaBrowserImplApi26 extends MediaBrowserImplApi23 {
    MediaBrowserImplApi26(
        Context context,
        ComponentName serviceComponent,
        ConnectionCallback callback,
        @Nullable Bundle rootHints) {
      super(context, serviceComponent, callback, rootHints);
    }

    @Override
    public void subscribe(
        String parentId, @Nullable Bundle options, SubscriptionCallback callback) {
      // From service v2, we use compat code when subscribing.
      // This is to prevent ClassNotFoundException when options has Parcelable in it.
      if (mServiceBinderWrapper == null || mServiceVersion < SERVICE_VERSION_2) {
        if (options == null) {
          mBrowserFwk.subscribe(parentId, checkNotNull(callback.mSubscriptionCallbackFwk));
        } else {
          mBrowserFwk.subscribe(parentId, options, checkNotNull(callback.mSubscriptionCallbackFwk));
        }
      } else {
        super.subscribe(parentId, options, callback);
      }
    }

    @Override
    public void unsubscribe(String parentId, @Nullable SubscriptionCallback callback) {
      // From service v2, we use compat code when subscribing.
      // This is to prevent ClassNotFoundException when options has Parcelable in it.
      if (mServiceBinderWrapper == null || mServiceVersion < SERVICE_VERSION_2) {
        if (callback == null) {
          mBrowserFwk.unsubscribe(parentId);
        } else {
          mBrowserFwk.unsubscribe(parentId, checkNotNull(callback.mSubscriptionCallbackFwk));
        }
      } else {
        super.unsubscribe(parentId, callback);
      }
    }
  }

  private static class Subscription {
    private final List<SubscriptionCallback> mCallbacks;
    private final List<@NullableType Bundle> mOptionsList;

    public Subscription() {
      mCallbacks = new ArrayList<>();
      mOptionsList = new ArrayList<>();
    }

    public boolean isEmpty() {
      return mCallbacks.isEmpty();
    }

    public List<@NullableType Bundle> getOptionsList() {
      return mOptionsList;
    }

    public List<SubscriptionCallback> getCallbacks() {
      return mCallbacks;
    }

    @Nullable
    public SubscriptionCallback getCallback(@Nullable Bundle options) {
      for (int i = 0; i < mOptionsList.size(); ++i) {
        if (MediaBrowserCompatUtils.areSameOptions(mOptionsList.get(i), options)) {
          return mCallbacks.get(i);
        }
      }
      return null;
    }

    public void putCallback(@Nullable Bundle options, SubscriptionCallback callback) {
      for (int i = 0; i < mOptionsList.size(); ++i) {
        if (MediaBrowserCompatUtils.areSameOptions(mOptionsList.get(i), options)) {
          mCallbacks.set(i, callback);
          return;
        }
      }
      mCallbacks.add(callback);
      mOptionsList.add(options);
    }
  }

  private static class CallbackHandler extends Handler {
    private final WeakReference<MediaBrowserServiceCallbackImpl> mCallbackImplRef;
    @Nullable private WeakReference<Messenger> mCallbacksMessengerRef;

    CallbackHandler(MediaBrowserServiceCallbackImpl callbackImpl) {
      super();
      mCallbackImplRef = new WeakReference<>(callbackImpl);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void handleMessage(Message msg) {
      if (mCallbacksMessengerRef == null) {
        return;
      }
      Messenger callbacksMessenger = mCallbacksMessengerRef.get();
      MediaBrowserServiceCallbackImpl serviceCallback = mCallbackImplRef.get();
      if (callbacksMessenger == null || serviceCallback == null) {
        return;
      }
      Bundle data = msg.getData();
      MediaSessionCompat.ensureClassLoader(data);

      try {
        switch (msg.what) {
          case SERVICE_MSG_ON_CONNECT:
            {
              Bundle rootHints = data.getBundle(DATA_ROOT_HINTS);
              MediaSessionCompat.ensureClassLoader(rootHints);

              serviceCallback.onServiceConnected(
                  callbacksMessenger,
                  data.getString(DATA_MEDIA_ITEM_ID),
                  LegacyParcelableUtil.convert(
                      data.getParcelable(DATA_MEDIA_SESSION_TOKEN),
                      MediaSessionCompat.Token.CREATOR),
                  rootHints);
              break;
            }
          case SERVICE_MSG_ON_CONNECT_FAILED:
            serviceCallback.onConnectionFailed(callbacksMessenger);
            break;
          case SERVICE_MSG_ON_LOAD_CHILDREN:
            {
              Bundle options = data.getBundle(DATA_OPTIONS);
              MediaSessionCompat.ensureClassLoader(options);

              Bundle notifyChildrenChangedOptions =
                  data.getBundle(DATA_NOTIFY_CHILDREN_CHANGED_OPTIONS);
              MediaSessionCompat.ensureClassLoader(notifyChildrenChangedOptions);

              serviceCallback.onLoadChildren(
                  callbacksMessenger,
                  data.getString(DATA_MEDIA_ITEM_ID),
                  LegacyParcelableUtil.convertList(
                      data.getParcelableArrayList(DATA_MEDIA_ITEM_LIST), MediaItem.CREATOR),
                  options,
                  notifyChildrenChangedOptions);
              break;
            }
          default:
            Log.w(
                TAG,
                "Unhandled message: "
                    + msg
                    + "\n  Client version: "
                    + CLIENT_VERSION_CURRENT
                    + "\n  Service version: "
                    + msg.arg1);
        }
      } catch (BadParcelableException e) {
        // Do not print the exception here, since it is already done by the Parcel class.
        Log.e(TAG, "Could not unparcel the data.");
        // If an error happened while connecting, disconnect from the service.
        if (msg.what == SERVICE_MSG_ON_CONNECT) {
          serviceCallback.onConnectionFailed(callbacksMessenger);
        }
      }
    }

    void setCallbacksMessenger(@Nullable Messenger callbacksMessenger) {
      mCallbacksMessengerRef = new WeakReference<>(callbacksMessenger);
    }
  }

  private static class ServiceBinderWrapper {
    private Messenger mMessenger;
    @Nullable private Bundle mRootHints;

    public ServiceBinderWrapper(IBinder target, @Nullable Bundle rootHints) {
      mMessenger = new Messenger(target);
      mRootHints = rootHints;
    }

    void connect(Context context, Messenger callbacksMessenger) throws RemoteException {
      Bundle data = new Bundle();
      data.putString(DATA_PACKAGE_NAME, context.getPackageName());
      data.putInt(DATA_CALLING_PID, Process.myPid());
      data.putBundle(DATA_ROOT_HINTS, mRootHints);
      sendRequest(CLIENT_MSG_CONNECT, data, callbacksMessenger);
    }

    void disconnect(Messenger callbacksMessenger) throws RemoteException {
      sendRequest(CLIENT_MSG_DISCONNECT, null, callbacksMessenger);
    }

    void addSubscription(
        String parentId,
        IBinder callbackToken,
        @Nullable Bundle options,
        Messenger callbacksMessenger)
        throws RemoteException {
      Bundle data = new Bundle();
      data.putString(DATA_MEDIA_ITEM_ID, parentId);
      data.putBinder(DATA_CALLBACK_TOKEN, callbackToken);
      data.putBundle(DATA_OPTIONS, options);
      sendRequest(CLIENT_MSG_ADD_SUBSCRIPTION, data, callbacksMessenger);
    }

    void removeSubscription(
        String parentId, @Nullable IBinder callbackToken, Messenger callbacksMessenger)
        throws RemoteException {
      Bundle data = new Bundle();
      data.putString(DATA_MEDIA_ITEM_ID, parentId);
      data.putBinder(DATA_CALLBACK_TOKEN, callbackToken);
      sendRequest(CLIENT_MSG_REMOVE_SUBSCRIPTION, data, callbacksMessenger);
    }

    void getMediaItem(String mediaId, ResultReceiver receiver, Messenger callbacksMessenger)
        throws RemoteException {
      Bundle data = new Bundle();
      data.putString(DATA_MEDIA_ITEM_ID, mediaId);
      data.putParcelable(DATA_RESULT_RECEIVER, receiver);
      sendRequest(CLIENT_MSG_GET_MEDIA_ITEM, data, callbacksMessenger);
    }

    void registerCallbackMessenger(Context context, Messenger callbackMessenger)
        throws RemoteException {
      Bundle data = new Bundle();
      data.putString(DATA_PACKAGE_NAME, context.getPackageName());
      data.putInt(DATA_CALLING_PID, Process.myPid());
      data.putBundle(DATA_ROOT_HINTS, mRootHints);
      sendRequest(CLIENT_MSG_REGISTER_CALLBACK_MESSENGER, data, callbackMessenger);
    }

    void unregisterCallbackMessenger(Messenger callbackMessenger) throws RemoteException {
      sendRequest(CLIENT_MSG_UNREGISTER_CALLBACK_MESSENGER, null, callbackMessenger);
    }

    void search(
        String query,
        @Nullable Bundle extras,
        ResultReceiver receiver,
        Messenger callbacksMessenger)
        throws RemoteException {
      Bundle data = new Bundle();
      data.putString(DATA_SEARCH_QUERY, query);
      data.putBundle(DATA_SEARCH_EXTRAS, extras);
      data.putParcelable(DATA_RESULT_RECEIVER, receiver);
      sendRequest(CLIENT_MSG_SEARCH, data, callbacksMessenger);
    }

    void sendCustomAction(
        String action,
        @Nullable Bundle extras,
        ResultReceiver receiver,
        Messenger callbacksMessenger)
        throws RemoteException {
      Bundle data = new Bundle();
      data.putString(DATA_CUSTOM_ACTION, action);
      data.putBundle(DATA_CUSTOM_ACTION_EXTRAS, extras);
      data.putParcelable(DATA_RESULT_RECEIVER, receiver);
      sendRequest(CLIENT_MSG_SEND_CUSTOM_ACTION, data, callbacksMessenger);
    }

    private void sendRequest(int what, @Nullable Bundle data, Messenger cbMessenger)
        throws RemoteException {
      Message msg = Message.obtain();
      msg.what = what;
      msg.arg1 = CLIENT_VERSION_CURRENT;
      if (data != null) {
        msg.setData(data);
      }
      msg.replyTo = cbMessenger;
      mMessenger.send(msg);
    }
  }

  @SuppressLint("RestrictedApi")
  private static class ItemReceiver extends ResultReceiver {
    private final String mMediaId;
    private final ItemCallback mCallback;

    ItemReceiver(String mediaId, ItemCallback callback, Handler handler) {
      super(handler);
      mMediaId = mediaId;
      mCallback = callback;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onReceiveResult(int resultCode, @Nullable Bundle resultData) {
      if (resultData != null) {
        resultData = MediaSessionCompat.unparcelWithClassLoader(resultData);
      }
      if (resultCode != MediaBrowserServiceCompat.RESULT_OK
          || resultData == null
          || !resultData.containsKey(MediaBrowserServiceCompat.KEY_MEDIA_ITEM)) {
        mCallback.onError(mMediaId);
        return;
      }
      MediaItem item =
          LegacyParcelableUtil.convert(
              resultData.getParcelable(MediaBrowserServiceCompat.KEY_MEDIA_ITEM),
              MediaItem.CREATOR);
      mCallback.onItemLoaded(item);
    }
  }

  @SuppressLint("RestrictedApi")
  private static class SearchResultReceiver extends ResultReceiver {
    private final String mQuery;
    @Nullable private final Bundle mExtras;
    private final SearchCallback mCallback;

    SearchResultReceiver(
        String query, @Nullable Bundle extras, SearchCallback callback, Handler handler) {
      super(handler);
      mQuery = query;
      mExtras = extras;
      mCallback = callback;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onReceiveResult(int resultCode, @Nullable Bundle resultData) {
      if (resultData != null) {
        resultData = MediaSessionCompat.unparcelWithClassLoader(resultData);
      }
      if (resultCode != MediaBrowserServiceCompat.RESULT_OK
          || resultData == null
          || !resultData.containsKey(MediaBrowserServiceCompat.KEY_SEARCH_RESULTS)) {
        mCallback.onError(mQuery, mExtras);
        return;
      }
      Parcelable[] items =
          resultData.getParcelableArray(MediaBrowserServiceCompat.KEY_SEARCH_RESULTS);
      if (items != null) {
        List<MediaItem> results = new ArrayList<>(items.length);
        for (Parcelable item : items) {
          results.add(LegacyParcelableUtil.convert(item, MediaItem.CREATOR));
        }
        mCallback.onSearchResult(mQuery, mExtras, results);
      } else {
        mCallback.onError(mQuery, mExtras);
      }
    }
  }

  @SuppressLint("RestrictedApi")
  private static class CustomActionResultReceiver extends ResultReceiver {
    private final String mAction;
    @Nullable private final Bundle mExtras;
    @Nullable private final CustomActionCallback mCallback;

    CustomActionResultReceiver(
        String action,
        @Nullable Bundle extras,
        @Nullable CustomActionCallback callback,
        Handler handler) {
      super(handler);
      mAction = action;
      mExtras = extras;
      mCallback = callback;
    }

    @Override
    protected void onReceiveResult(int resultCode, @Nullable Bundle resultData) {
      if (mCallback == null) {
        return;
      }
      MediaSessionCompat.ensureClassLoader(resultData);
      switch (resultCode) {
        case MediaBrowserServiceCompat.RESULT_PROGRESS_UPDATE:
          mCallback.onProgressUpdate(mAction, mExtras, resultData);
          break;
        case MediaBrowserServiceCompat.RESULT_OK:
          mCallback.onResult(mAction, mExtras, resultData);
          break;
        case MediaBrowserServiceCompat.RESULT_ERROR:
          mCallback.onError(mAction, mExtras, resultData);
          break;
        default:
          Log.w(
              TAG,
              "Unknown result code: "
                  + resultCode
                  + " (extras="
                  + mExtras
                  + ", resultData="
                  + resultData
                  + ")");
          break;
      }
    }
  }

  @RequiresApi(21)
  private static class Api21Impl {
    private Api21Impl() {}

    static MediaDescription getDescription(MediaBrowser.MediaItem item) {
      return item.getDescription();
    }

    static int getFlags(MediaBrowser.MediaItem item) {
      return item.getFlags();
    }
  }
}
