/*
 * Copyright 2019 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.session.SessionError.ERROR_BAD_VALUE;
import static androidx.media3.session.SessionError.ERROR_PERMISSION_DENIED;
import static androidx.media3.session.SessionError.ERROR_SESSION_DISCONNECTED;
import static androidx.media3.session.SessionError.ERROR_UNKNOWN;
import static androidx.media3.session.legacy.MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Log;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.legacy.MediaBrowserCompat;
import androidx.media3.session.legacy.MediaBrowserCompat.ItemCallback;
import androidx.media3.session.legacy.MediaBrowserCompat.SubscriptionCallback;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.initialization.qual.UnderInitialization;

/** Implementation of MediaBrowser with the {@link MediaBrowserCompat} for legacy support. */
/* package */ class MediaBrowserImplLegacy extends MediaControllerImplLegacy
    implements MediaBrowser.MediaBrowserImpl {

  private static final String TAG = "MB2ImplLegacy";

  private final HashMap<LibraryParams, MediaBrowserCompat> browserCompats = new HashMap<>();
  private final HashMap<String, List<SubscribeCallback>> subscribeCallbacks = new HashMap<>();
  private final MediaBrowser instance;

  private ImmutableMap<String, CommandButton> commandButtonsForMediaItems;

  MediaBrowserImplLegacy(
      Context context,
      @UnderInitialization MediaBrowser instance,
      SessionToken token,
      Bundle connectionHints,
      Looper applicationLooper,
      BitmapLoader bitmapLoader,
      long platformSessionCallbackAggregationTimeoutMs) {
    super(
        context,
        instance,
        token,
        connectionHints,
        applicationLooper,
        bitmapLoader,
        platformSessionCallbackAggregationTimeoutMs);
    this.instance = instance;
    commandButtonsForMediaItems = ImmutableMap.of();
  }

  @Override
  /* package*/ MediaBrowser getInstance() {
    return instance;
  }

  @Override
  public void release() {
    for (MediaBrowserCompat browserCompat : browserCompats.values()) {
      browserCompat.disconnect();
    }
    browserCompats.clear();
    // Ensure that MediaController.Listener#onDisconnected() is called by super.release().
    super.release();
  }

  @Override
  public SessionCommands getAvailableSessionCommands() {
    @Nullable MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat != null) {
      return super.getAvailableSessionCommands().buildUpon().addAllLibraryCommands().build();
    }
    return super.getAvailableSessionCommands();
  }

  @Override
  public ImmutableList<CommandButton> getCommandButtonsForMediaItem(MediaItem mediaItem) {
    // Do not filter by available commands. When connected to a legacy session, the available
    // session commands are read from the custom actions in PlaybackStateCompat (see
    // LegacyConversion.convertToSessionCommands). Filtering by these commands would force a
    // legacy session to put all commands for media items into the playback state as custom commands
    // which would interfere with the custom commands set for media controls.
    ImmutableList<String> supportedActions = mediaItem.mediaMetadata.supportedCommands;
    ImmutableList.Builder<CommandButton> commandButtonsForMediaItem = new ImmutableList.Builder<>();
    for (int i = 0; i < supportedActions.size(); i++) {
      CommandButton commandButton = commandButtonsForMediaItems.get(supportedActions.get(i));
      if (commandButton != null && commandButton.sessionCommand != null) {
        commandButtonsForMediaItem.add(commandButton);
      }
    }
    return commandButtonsForMediaItem.build();
  }

  @Override
  public ListenableFuture<LibraryResult<MediaItem>> getLibraryRoot(@Nullable LibraryParams params) {
    if (!getInstance()
        .isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT)) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_PERMISSION_DENIED));
    }
    SettableFuture<LibraryResult<MediaItem>> result = SettableFuture.create();
    MediaBrowserCompat browserCompat = getBrowserCompat(params);
    if (browserCompat != null) {
      // Already connected with the given extras.
      result.set(LibraryResult.ofItem(createRootMediaItem(browserCompat), null));
    } else {
      Bundle rootHints = LegacyConversions.convertToRootHints(params);
      rootHints.putInt(
          androidx.media3.session.legacy.MediaConstants
              .BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT,
          getInstance().getMaxCommandsForMediaItems());
      MediaBrowserCompat newBrowser =
          new MediaBrowserCompat(
              getContext(),
              getConnectedToken().getComponentName(),
              new GetLibraryRootCallback(result, params),
              rootHints);
      browserCompats.put(params, newBrowser);
      newBrowser.connect();
    }
    return result;
  }

  @Override
  public ListenableFuture<LibraryResult<Void>> subscribe(
      String parentId, @Nullable LibraryParams params) {
    if (!getInstance().isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE)) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_SESSION_DISCONNECTED));
    }
    Bundle options = createOptionsForSubscription(params);
    SettableFuture<LibraryResult<Void>> future = SettableFuture.create();
    SubscribeCallback callback = new SubscribeCallback(parentId, options, future);
    List<SubscribeCallback> list = subscribeCallbacks.get(parentId);
    if (list == null) {
      list = new ArrayList<>();
      subscribeCallbacks.put(parentId, list);
    }
    list.add(callback);
    browserCompat.subscribe(parentId, options, callback);
    return future;
  }

  @Override
  public ListenableFuture<LibraryResult<Void>> unsubscribe(String parentId) {
    if (!getInstance().isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE)) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_SESSION_DISCONNECTED));
    }
    // Note: don't use MediaBrowserCompat#unsubscribe(String) here, to keep the subscription
    // callback for getChildren.
    List<SubscribeCallback> list = subscribeCallbacks.get(parentId);
    if (list == null) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_BAD_VALUE));
    }
    for (int i = 0; i < list.size(); i++) {
      browserCompat.unsubscribe(parentId, list.get(i));
    }

    // No way to get result. Just return success.
    return Futures.immediateFuture(LibraryResult.ofVoid());
  }

  @Override
  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getChildren(
      String parentId, int page, int pageSize, @Nullable LibraryParams params) {
    if (!getInstance()
        .isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN)) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_SESSION_DISCONNECTED));
    }

    Bundle options = createOptionsWithPagingInfo(params, page, pageSize);
    SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future = SettableFuture.create();
    // Try to get the cached children in case this is the first call right after subscribing.
    List<MediaBrowserCompat.MediaItem> childrenFromCache =
        getChildrenFromSubscription(parentId, page);
    // Always evict to avoid memory leaks. We've done what we can.
    evictChildrenFromSubscription(parentId);
    if (childrenFromCache != null) {
      future.set(
          LibraryResult.ofItemList(
              LegacyConversions.convertBrowserItemListToMediaItemList(childrenFromCache),
              new LibraryParams.Builder().setExtras(options).build()));
    } else {
      GetChildrenCallback getChildrenCallback = new GetChildrenCallback(future, parentId);
      browserCompat.subscribe(parentId, options, getChildrenCallback);
    }
    return future;
  }

  @Nullable
  private List<MediaBrowserCompat.MediaItem> getChildrenFromSubscription(
      String parentId, int page) {
    List<SubscribeCallback> callbacks = subscribeCallbacks.get(parentId);
    if (callbacks == null) {
      return null;
    }
    for (int i = 0; i < callbacks.size(); i++) {
      if (callbacks.get(i).canServeGetChildrenRequest(parentId, page)) {
        return callbacks.get(i).receivedChildren;
      }
    }
    return null;
  }

  private void evictChildrenFromSubscription(String parentId) {
    List<SubscribeCallback> callbacks = subscribeCallbacks.get(parentId);
    if (callbacks == null) {
      return;
    }
    for (int i = 0; i < callbacks.size(); i++) {
      if (callbacks.get(i).receivedChildren != null) {
        // Evict the first cached children we find.
        callbacks.get(i).receivedChildren = null;
        return;
      }
    }
  }

  @Override
  public ListenableFuture<LibraryResult<MediaItem>> getItem(String mediaId) {
    if (!getInstance().isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM)) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_SESSION_DISCONNECTED));
    }
    SettableFuture<LibraryResult<MediaItem>> result = SettableFuture.create();
    browserCompat.getItem(
        mediaId,
        new ItemCallback() {
          @Override
          public void onItemLoaded(MediaBrowserCompat.MediaItem item) {
            if (item != null) {
              result.set(
                  LibraryResult.ofItem(
                      LegacyConversions.convertToMediaItem(item), /* params= */ null));
            } else {
              result.set(LibraryResult.ofError(ERROR_BAD_VALUE));
            }
          }

          @Override
          public void onError(String itemId) {
            result.set(LibraryResult.ofError(ERROR_UNKNOWN));
          }
        });
    return result;
  }

  @Override
  public ListenableFuture<LibraryResult<Void>> search(
      String query, @Nullable LibraryParams params) {
    if (!getInstance().isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_SESSION_DISCONNECTED));
    }
    browserCompat.search(
        query,
        getExtras(params),
        new MediaBrowserCompat.SearchCallback() {
          @Override
          public void onSearchResult(
              String query, @Nullable Bundle extras, List<MediaBrowserCompat.MediaItem> items) {
            getInstance()
                .notifyBrowserListener(
                    listener -> {
                      // Set extra null here, because 'extra' have different meanings between old
                      // API and new API as follows.
                      // - Old API: Extra/Option specified with search().
                      // - New API: Extra from MediaLibraryService to MediaBrowser
                      // TODO(b/193193565): Cache search result for later getSearchResult() calls.
                      listener.onSearchResultChanged(getInstance(), query, items.size(), null);
                    });
          }

          @Override
          public void onError(String query, @Nullable Bundle extras) {
            getInstance()
                .notifyBrowserListener(
                    listener -> {
                      // Set extra null here, because 'extra' have different meanings between old
                      // API and new API as follows.
                      // - Old API: Extra/Option specified with search().
                      // - New API: Extra from MediaLibraryService to MediaBrowser
                      listener.onSearchResultChanged(getInstance(), query, 0, null);
                    });
          }
        });
    // No way to get result. Just return success.
    return Futures.immediateFuture(LibraryResult.ofVoid());
  }

  @Override
  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getSearchResult(
      String query, int page, int pageSize, @Nullable LibraryParams params) {
    if (!getInstance()
        .isSessionCommandAvailable(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT)) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_PERMISSION_DENIED));
    }
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat == null) {
      return Futures.immediateFuture(LibraryResult.ofError(ERROR_SESSION_DISCONNECTED));
    }

    SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future = SettableFuture.create();
    Bundle options = createOptionsWithPagingInfo(params, page, pageSize);
    options.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    options.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    browserCompat.search(
        query,
        options,
        new MediaBrowserCompat.SearchCallback() {
          @Override
          public void onSearchResult(
              String query, @Nullable Bundle extrasSent, List<MediaBrowserCompat.MediaItem> items) {
            future.set(
                LibraryResult.ofItemList(
                    LegacyConversions.convertBrowserItemListToMediaItemList(items),
                    /* params= */ null));
          }

          @Override
          public void onError(String query, @Nullable Bundle extrasSent) {
            future.set(LibraryResult.ofError(ERROR_UNKNOWN));
          }
        });
    return future;
  }

  @Override
  public ListenableFuture<SessionResult> sendCustomCommand(SessionCommand command, Bundle args) {
    MediaBrowserCompat browserCompat = getBrowserCompat();
    if (browserCompat != null
        && (instance.isSessionCommandAvailable(command)
            || isContainedInCommandButtonsForMediaItems(command))) {
      SettableFuture<SessionResult> settable = SettableFuture.create();
      browserCompat.sendCustomAction(
          command.customAction,
          args,
          new MediaBrowserCompat.CustomActionCallback() {
            @Override
            public void onResult(
                String action, @Nullable Bundle extras, @Nullable Bundle resultData) {
              Bundle mergedBundles = new Bundle(extras);
              mergedBundles.putAll(resultData);
              settable.set(new SessionResult(SessionResult.RESULT_SUCCESS, mergedBundles));
            }

            @Override
            public void onError(String action, @Nullable Bundle extras, @Nullable Bundle data) {
              Bundle mergedBundles = new Bundle(extras);
              mergedBundles.putAll(data);
              settable.set(new SessionResult(SessionResult.RESULT_ERROR_UNKNOWN, mergedBundles));
            }
          });
      return settable;
    }
    return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED));
  }

  // Using this method as a proxy whether an browser is allowed to send a custom action can be
  // justified because a MediaBrowserCompat can declare the custom browse actions in onGetRoot()
  // specifically for each browser that connects. This is different to Media3 where the command
  // buttons for media items are declared on the session level, and are constraint by the available
  // session commands granted individually to a controller/browser in onConnect.
  private boolean isContainedInCommandButtonsForMediaItems(SessionCommand command) {
    if (command.commandCode != SessionCommand.COMMAND_CODE_CUSTOM) {
      return false;
    }
    CommandButton commandButton = commandButtonsForMediaItems.get(command.customAction);
    return commandButton != null && Objects.equals(commandButton.sessionCommand, command);
  }

  private MediaBrowserCompat getBrowserCompat(LibraryParams extras) {
    return browserCompats.get(extras);
  }

  private static Bundle createOptionsForSubscription(@Nullable LibraryParams params) {
    return params == null ? new Bundle() : new Bundle(params.extras);
  }

  private static Bundle createOptionsWithPagingInfo(
      @Nullable LibraryParams params, int page, int pageSize) {
    Bundle options = createOptionsForSubscription(params);
    options.putInt(MediaBrowserCompat.EXTRA_PAGE, page);
    options.putInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, pageSize);
    return options;
  }

  private static Bundle getExtras(@Nullable LibraryParams params) {
    return params != null ? params.extras : null;
  }

  private MediaItem createRootMediaItem(MediaBrowserCompat browserCompat) {
    // TODO(b/193193690): Query again with getMediaItem() to get real media item.
    String mediaId = browserCompat.getRoot();
    MediaMetadata mediaMetadata =
        new MediaMetadata.Builder()
            .setIsBrowsable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setIsPlayable(false)
            .setExtras(browserCompat.getExtras())
            .build();
    return new MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(mediaMetadata).build();
  }

  private class GetLibraryRootCallback extends MediaBrowserCompat.ConnectionCallback {
    private final SettableFuture<LibraryResult<MediaItem>> result;
    private final LibraryParams params;

    public GetLibraryRootCallback(
        SettableFuture<LibraryResult<MediaItem>> result, LibraryParams params) {
      super();
      this.result = result;
      this.params = params;
    }

    @Override
    public void onConnected() {
      MediaBrowserCompat browserCompat = browserCompats.get(params);
      if (browserCompat == null) {
        // Shouldn't be happen. Internal error?
        result.set(LibraryResult.ofError(ERROR_UNKNOWN));
      } else {
        Bundle extras = browserCompat.getExtras();
        if (extras != null) {
          ArrayList<Bundle> parcelableArrayList =
              extras.getParcelableArrayList(
                  BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST);
          if (parcelableArrayList != null) {
            @Nullable
            ImmutableMap.Builder<String, CommandButton> commandButtonsForMediaItemsBuilder = null;
            // Converting custom browser action bundles to media item command buttons.
            for (int i = 0; i < parcelableArrayList.size(); i++) {
              CommandButton commandButton =
                  LegacyConversions.convertCustomBrowseActionToCommandButton(
                      parcelableArrayList.get(i));
              if (commandButton != null) {
                if (commandButtonsForMediaItemsBuilder == null) {
                  // Merge all media item command button of different legacy roots into a single
                  // map. Last wins in case of duplicate action names.
                  commandButtonsForMediaItemsBuilder =
                      new ImmutableMap.Builder<String, CommandButton>()
                          .putAll(commandButtonsForMediaItems);
                }
                String customAction = checkNotNull(commandButton.sessionCommand).customAction;
                commandButtonsForMediaItemsBuilder.put(customAction, commandButton);
              }
            }
            if (commandButtonsForMediaItemsBuilder != null) {
              commandButtonsForMediaItems = commandButtonsForMediaItemsBuilder.buildKeepingLast();
            }
          }
        }
        result.set(
            LibraryResult.ofItem(
                createRootMediaItem(browserCompat),
                LegacyConversions.convertToLibraryParams(context, extras)));
      }
    }

    @Override
    public void onConnectionSuspended() {
      onConnectionFailed();
    }

    @Override
    public void onConnectionFailed() {
      // Unknown extra field.
      result.set(LibraryResult.ofError(ERROR_BAD_VALUE));
      release();
    }
  }

  private class SubscribeCallback extends SubscriptionCallback {

    private final String subscriptionParentId;
    private final Bundle subscriptionOptions;
    private final SettableFuture<LibraryResult<Void>> future;

    @Nullable private List<MediaBrowserCompat.MediaItem> receivedChildren;

    public SubscribeCallback(
        String subscriptionParentId,
        Bundle subscriptionOptions,
        SettableFuture<LibraryResult<Void>> future) {
      this.subscriptionParentId = subscriptionParentId;
      this.subscriptionOptions = subscriptionOptions;
      this.future = future;
    }

    @Override
    public void onError(@Nullable String parentId) {
      onError(subscriptionParentId, subscriptionOptions);
    }

    @Override
    public void onError(@Nullable String parentId, @Nullable Bundle options) {
      onErrorInternal(subscriptionParentId, subscriptionOptions);
    }

    @Override
    public void onChildrenLoaded(
        @Nullable String parentId, @Nullable List<MediaBrowserCompat.MediaItem> children) {
      onChildrenLoadedInternal(subscriptionParentId, children);
    }

    @Override
    public void onChildrenLoaded(
        @Nullable String parentId,
        @Nullable List<MediaBrowserCompat.MediaItem> children,
        @Nullable Bundle options) {
      onChildrenLoadedInternal(subscriptionParentId, children);
    }

    private void onErrorInternal(String parentId, Bundle options) {
      if (future.isDone()) {
        // Delegate to the browser by calling `onChildrenChanged` that makes the app call
        // `getChildren()` for which the service can return the appropriate error code. This makes a
        // redundant call to `subscribe` that can be expected to be not expensive as it just returns
        // an exception.
        getInstance()
            .notifyBrowserListener(
                listener ->
                    listener.onChildrenChanged(
                        getInstance(),
                        parentId,
                        Integer.MAX_VALUE,
                        new LibraryParams.Builder().setExtras(options).build()));
      }
      // Don't need to unsubscribe here, because MediaBrowserServiceCompat can notify children
      // changed after the initial failure and MediaBrowserCompat could receive the changes.
      future.set(LibraryResult.ofError(ERROR_UNKNOWN));
    }

    private void onChildrenLoadedInternal(
        String parentId, @Nullable List<MediaBrowserCompat.MediaItem> children) {
      if (TextUtils.isEmpty(parentId)) {
        Log.w(TAG, "SubscribeCallback.onChildrenLoaded(): Ignoring empty parentId");
        return;
      }
      MediaBrowserCompat browserCompat = getBrowserCompat();
      if (browserCompat == null) {
        // Browser is closed.
        return;
      }
      if (children == null) {
        // Note this doesn't happen except possibly when someone is using a very old OS (change
        // landed in Android tree at 2016-02-23). Recent Android versions turn children=null into an
        // error and `onError` of this `SubscriptionCallback` is called instead of
        // `onChildrenLoaded` (see `MediaBrowser.onLoadChildren`). We should do the same here to be
        // consistent again.
        onError(subscriptionParentId, subscriptionOptions);
        return;
      }

      LibraryParams params =
          LegacyConversions.convertToLibraryParams(
              context, browserCompat.getNotifyChildrenChangedOptions());
      receivedChildren = children;
      getInstance()
          .notifyBrowserListener(
              listener -> {
                listener.onChildrenChanged(getInstance(), parentId, children.size(), params);
              });
      future.set(LibraryResult.ofVoid());
    }

    /**
     * Returns true if the cached children can be served for a request for the given parent ID and
     * paging options.
     *
     * @param parentId The media ID of the parent of the requested children.
     * @param pageIndex The requested page index.
     * @return True if the request can be served with the cached children of the subscription
     *     callback.
     */
    public boolean canServeGetChildrenRequest(String parentId, int pageIndex) {
      return subscriptionParentId.equals(parentId)
          && receivedChildren != null
          && pageIndex
              == subscriptionOptions.getInt(MediaBrowserCompat.EXTRA_PAGE, /* defaultValue= */ 0);
    }
  }

  private class GetChildrenCallback extends SubscriptionCallback {

    private final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future;
    private final String parentId;

    public GetChildrenCallback(
        SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future, String parentId) {
      super();
      this.future = future;
      this.parentId = parentId;
    }

    @Override
    public void onError(@Nullable String parentId) {
      onErrorInternal();
    }

    @Override
    public void onError(@Nullable String parentId, @Nullable Bundle options) {
      onErrorInternal();
    }

    @Override
    public void onChildrenLoaded(
        @Nullable String parentId, @Nullable List<MediaBrowserCompat.MediaItem> children) {
      onChildrenLoadedInternal(parentId, children);
    }

    @Override
    public void onChildrenLoaded(
        @Nullable String parentId,
        @Nullable List<MediaBrowserCompat.MediaItem> children,
        Bundle options) {
      onChildrenLoadedInternal(parentId, children);
    }

    private void onErrorInternal() {
      future.set(LibraryResult.ofError(ERROR_UNKNOWN));
    }

    private void onChildrenLoadedInternal(
        @Nullable String parentId, @Nullable List<MediaBrowserCompat.MediaItem> children) {
      if (TextUtils.isEmpty(parentId)) {
        Log.w(TAG, "GetChildrenCallback.onChildrenLoaded(): Ignoring empty parentId");
        return;
      }
      MediaBrowserCompat browserCompat = getBrowserCompat();
      if (browserCompat == null) {
        future.set(LibraryResult.ofError(ERROR_SESSION_DISCONNECTED));
        return;
      }
      browserCompat.unsubscribe(this.parentId, GetChildrenCallback.this);

      if (children == null) {
        // list are non-Null, so it must be internal error.
        future.set(LibraryResult.ofError(ERROR_UNKNOWN));
      } else {
        // Don't set extra here, because 'extra' have different meanings between old
        // API and new API as follows.
        // - Old API: Extra/Option specified with subscribe().
        // - New API: Extra from MediaLibraryService to MediaBrowser
        future.set(
            LibraryResult.ofItemList(
                LegacyConversions.convertBrowserItemListToMediaItemList(children),
                /* params= */ null));
      }
    }
  }
}
