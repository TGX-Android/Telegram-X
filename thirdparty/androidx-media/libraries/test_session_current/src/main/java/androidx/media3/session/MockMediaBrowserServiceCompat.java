/*
 * Copyright 2018 The Android Open Source Project
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

import static androidx.media3.session.MediaConstants.EXTRAS_KEY_COMPLETION_STATUS;
import static androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED;
import static androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED;
import static androidx.media3.session.MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED;
import static androidx.media3.session.legacy.MediaConstants.BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT;
import static androidx.media3.session.legacy.MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST;
import static androidx.media3.session.legacy.MediaConstants.DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST;
import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_EXTRAS;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID;
import static androidx.media3.test.session.common.MediaBrowserConstants.ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_CONNECT_REJECTED;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_CHILDREN;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_CHILDREN_FATAL_AUTHENTICATION_ERROR;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_CHILDREN_INCREASE_NUMBER_OF_CHILDREN_WITH_EACH_CALL;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_CHILDREN_NON_FATAL_AUTHENTICATION_ERROR;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_CHILDREN_WITH_NULL_LIST;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_GET_LIBRARY_ROOT;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_MEDIA_ITEMS_WITH_BROWSE_ACTIONS;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_SEND_CUSTOM_COMMAND;
import static androidx.media3.test.session.common.MediaBrowserServiceCompatConstants.TEST_SUBSCRIBE_THEN_REJECT_ON_LOAD_CHILDREN;
import static java.lang.Math.min;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media3.test.session.common.IRemoteMediaBrowserServiceCompat;
import androidx.media3.test.session.common.MediaBrowserConstants;
import androidx.media3.test.session.common.MediaBrowserServiceCompatConstants;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Mock implementation of the media browser service. */
public class MockMediaBrowserServiceCompat extends MediaBrowserServiceCompat {

  /**
   * Immutable list of media items sent to controllers for {@link
   * MediaBrowserServiceCompatConstants#TEST_GET_CHILDREN}.
   */
  public static final ImmutableList<MediaItem> MEDIA_ITEMS = createMediaItems();

  /**
   * Key in the browser root hints to request a confirmation of the call to {@link
   * #onGetRoot(String, int, Bundle)}.
   */
  public static final String EXTRAS_KEY_SEND_ROOT_HINTS_AS_SESSION_EXTRAS =
      "confirm_on_get_root_with_custom_action";

  private static final String TAG = "MockMBSCompat";
  private static final Object lock = new Object();

  @GuardedBy("lock")
  private static volatile MockMediaBrowserServiceCompat instance;

  @GuardedBy("lock")
  private static volatile Proxy serviceProxy;

  private MediaSessionCompat sessionCompat;

  private RemoteMediaBrowserServiceCompatStub testBinder;

  @Override
  public void onCreate() {
    super.onCreate();
    synchronized (lock) {
      instance = this;
    }
    sessionCompat = new MediaSessionCompat(this, TAG);
    sessionCompat.setCallback(new MediaSessionCompat.Callback() {});
    sessionCompat.setActive(true);
    setSessionToken(sessionCompat.getSessionToken());
    testBinder = new RemoteMediaBrowserServiceCompatStub(sessionCompat);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    sessionCompat.release();
    synchronized (lock) {
      instance = null;
      // Note: Don't reset serviceProxy.
      //       When a test is finished and its next test is running, this service will be
      //       destroyed and re-created for the next test. When it happens, onDestroy() may be
      //       called after the next test's proxy has set because onDestroy() and tests run on
      //       the different threads.
      //       So keep serviceProxy for the next test.
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    String action = intent.getAction();
    if (SERVICE_INTERFACE.equals(action)) {
      // for MediaBrowser
      return super.onBind(intent);
    }
    // for RemoteMediaBrowserServiceCompat
    return testBinder;
  }

  public static MockMediaBrowserServiceCompat getInstance() {
    synchronized (lock) {
      return instance;
    }
  }

  public static void setMediaBrowserServiceProxy(Proxy proxy) {
    synchronized (lock) {
      serviceProxy = proxy;
    }
  }

  private static boolean isProxyOverridesMethod(String methodName) {
    return isProxyOverridesMethod(methodName, -1);
  }

  private static boolean isProxyOverridesMethod(String methodName, int paramCount) {
    synchronized (lock) {
      if (serviceProxy == null) {
        return false;
      }
      Method[] methods = serviceProxy.getClass().getMethods();
      if (methods == null) {
        return false;
      }
      for (int i = 0; i < methods.length; i++) {
        if (methods[i].getName().equals(methodName)) {
          if (paramCount < 0
              || (methods[i].getParameterTypes() != null
                  && methods[i].getParameterTypes().length == paramCount)) {
            // Found method. Check if it overrides
            return methods[i].getDeclaringClass() != Proxy.class;
          }
        }
      }
      return false;
    }
  }

  @Override
  public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
    if (!SUPPORT_APP_PACKAGE_NAME.equals(clientPackageName)) {
      // Test only -- reject any other request.
      return null;
    }
    if (rootHints.getBoolean(EXTRAS_KEY_SEND_ROOT_HINTS_AS_SESSION_EXTRAS, false)) {
      // Send delayed because the Media3 browser is in the process of connecting at this point and
      // won't receive listener callbacks before being connected.
      new Handler(Looper.myLooper())
          .postDelayed(() -> sessionCompat.setExtras(rootHints), /* delayMillis= */ 100L);
    }
    synchronized (lock) {
      if (isProxyOverridesMethod("onGetRoot")) {
        return serviceProxy.onGetRoot(clientPackageName, clientUid, rootHints);
      }
    }
    return new BrowserRoot("stub", /* extras= */ rootHints);
  }

  @Override
  public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {
    synchronized (lock) {
      if (isProxyOverridesMethod("onLoadChildren", 2)) {
        serviceProxy.onLoadChildren(parentId, result);
        return;
      }
    }
    super.onLoadChildren(parentId, result, Bundle.EMPTY);
  }

  @Override
  public void onLoadChildren(String parentId, Result<List<MediaItem>> result, Bundle options) {
    synchronized (lock) {
      if (isProxyOverridesMethod("onLoadChildren", 3)) {
        serviceProxy.onLoadChildren(parentId, result, options);
        return;
      }
    }
    super.onLoadChildren(parentId, result, options);
  }

  @Override
  public void onLoadItem(String itemId, Result<MediaItem> result) {
    synchronized (lock) {
      if (isProxyOverridesMethod("onLoadItem")) {
        serviceProxy.onLoadItem(itemId, result);
        return;
      }
    }
    super.onLoadItem(itemId, result);
  }

  @Override
  public void onSearch(String query, Bundle extras, Result<List<MediaItem>> result) {
    synchronized (lock) {
      if (isProxyOverridesMethod("onSearch")) {
        serviceProxy.onSearch(query, extras, result);
        return;
      }
    }
    super.onSearch(query, extras, result);
  }

  @Override
  public void onCustomAction(String action, Bundle extras, Result<Bundle> result) {
    synchronized (lock) {
      if (isProxyOverridesMethod("onCustomAction")) {
        serviceProxy.onCustomAction(action, extras, result);
        return;
      }
    }
    super.onCustomAction(action, extras, result);
  }

  /** Proxy for MediaBrowserServiceCompat callbacks */
  public static class Proxy {
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
      return new BrowserRoot("stub", null);
    }

    public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {}

    public void onLoadChildren(String parentId, Result<List<MediaItem>> result, Bundle options) {}

    public void onLoadItem(String itemId, Result<MediaItem> result) {}

    public void onSearch(String query, Bundle extras, Result<List<MediaItem>> result) {}

    public void onCustomAction(String action, Bundle extras, Result<Bundle> result) {}
  }

  private static class RemoteMediaBrowserServiceCompatStub
      extends IRemoteMediaBrowserServiceCompat.Stub {

    private final MediaSessionCompat session;

    public RemoteMediaBrowserServiceCompatStub(MediaSessionCompat sessionCompat) {
      session = sessionCompat;
    }

    @Override
    public void setProxyForTest(String testName) throws RemoteException {
      switch (testName) {
        case TEST_CONNECT_REJECTED:
          setProxyForTestConnectRejected();
          break;
        case TEST_ON_CHILDREN_CHANGED_SUBSCRIBE_AND_UNSUBSCRIBE:
          setProxyForTestOnChildrenChanged_subscribeAndUnsubscribe();
          break;
        case TEST_GET_LIBRARY_ROOT:
          setProxyForTestGetLibraryRoot_correctExtraKeyAndValue();
          break;
        case TEST_GET_CHILDREN:
          setProxyForTestGetChildren_correctMetadataExtras();
          break;
        case TEST_GET_CHILDREN_WITH_NULL_LIST:
          setProxyForTestOnChildrenChanged_withNullChildrenListInLegacyService_convertedToSessionError();
          break;
        case TEST_GET_CHILDREN_FATAL_AUTHENTICATION_ERROR:
          getChildren_authenticationError_receivesPlaybackException(session, /* isFatal= */ true);
          break;
        case TEST_GET_CHILDREN_NON_FATAL_AUTHENTICATION_ERROR:
          getChildren_authenticationError_receivesPlaybackException(session, /* isFatal= */ false);
          break;
        case TEST_SEND_CUSTOM_COMMAND:
          setProxyForTestSendCustomCommand();
          break;
        case TEST_MEDIA_ITEMS_WITH_BROWSE_ACTIONS:
          setProxyForMediaItemsWithBrowseActions(session);
          break;
        case TEST_SUBSCRIBE_THEN_REJECT_ON_LOAD_CHILDREN:
          setProxyForSubscribeAndRejectGetChildren();
          break;
        case TEST_GET_CHILDREN_INCREASE_NUMBER_OF_CHILDREN_WITH_EACH_CALL:
          setProxyForGetChildrenIncreaseNumberOfChildrenWithEachCall();
          break;
        default:
          throw new IllegalArgumentException("Unknown testName: " + testName);
      }
    }

    @Override
    public void notifyChildrenChanged(String parentId) throws RemoteException {
      getInstance().notifyChildrenChanged(parentId);
    }

    private void setProxyForTestConnectRejected() {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            @Override
            public BrowserRoot onGetRoot(
                String clientPackageName, int clientUid, Bundle rootHints) {
              return null;
            }
          });
    }

    private void setProxyForTestOnChildrenChanged_subscribeAndUnsubscribe() {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            @Override
            public void onLoadChildren(
                String parentId, Result<List<MediaItem>> result, Bundle options) {
              result.sendResult(Collections.emptyList());
            }
          });
    }

    private void setProxyForTestGetChildren_correctMetadataExtras() {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            @Override
            public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {
              onLoadChildren(parentId, result, new Bundle());
            }

            @Override
            public void onLoadChildren(
                String parentId, Result<List<MediaItem>> result, Bundle bundle) {
              result.sendResult(MEDIA_ITEMS);
            }
          });
    }

    private void
        setProxyForTestOnChildrenChanged_withNullChildrenListInLegacyService_convertedToSessionError() {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            @Override
            public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {
              onLoadChildren(parentId, result, new Bundle());
            }

            @Override
            public void onLoadChildren(
                String parentId, Result<List<MediaItem>> result, Bundle bundle) {
              result.sendResult(null);
            }
          });
    }

    private void setProxyForMediaItemsWithBrowseActions(MediaSessionCompat session) {
      // See https://developer.android.com/training/cars/media#custom_browse_actions

      Bundle playlistAddBrowseAction = new Bundle();
      Bundle playlistAddExtras = new Bundle();
      playlistAddExtras.putString("key-1", "playlist_add");
      playlistAddBrowseAction.putString(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID,
          MediaBrowserConstants.COMMAND_PLAYLIST_ADD);
      playlistAddBrowseAction.putString(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL,
          "Add to playlist");
      playlistAddBrowseAction.putString(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI,
          "content://playlist_add");
      playlistAddBrowseAction.putBundle(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_EXTRAS,
          playlistAddExtras);
      Bundle radioBrowseAction = new Bundle();
      Bundle radioExtras = new Bundle();
      radioExtras.putString("key-1", "radio");
      radioBrowseAction.putString(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID,
          MediaBrowserConstants.COMMAND_RADIO);
      radioBrowseAction.putString(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_LABEL,
          "Radio station");
      radioBrowseAction.putString(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ICON_URI,
          "content://radio");
      radioBrowseAction.putBundle(
          androidx.media3.session.legacy.MediaConstants.EXTRAS_KEY_CUSTOM_BROWSER_ACTION_EXTRAS,
          radioExtras);

      ImmutableList<Bundle> browseActions =
          ImmutableList.of(playlistAddBrowseAction, radioBrowseAction);
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            @Override
            public BrowserRoot onGetRoot(
                String clientPackageName, int clientUid, Bundle rootHints) {
              int actionLimit =
                  rootHints.getInt(
                      BROWSER_ROOT_HINTS_KEY_CUSTOM_BROWSER_ACTION_LIMIT, /* defaultValue= */ 0);
              Bundle extras = new Bundle(rootHints);
              ArrayList<Bundle> browseActionList = new ArrayList<>();
              for (int i = 0; i < min(actionLimit, browseActions.size()); i++) {
                browseActionList.add(browseActions.get(i));
              }
              extras.putParcelableArrayList(
                  BROWSER_SERVICE_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ROOT_LIST, browseActionList);
              return new BrowserRoot(ROOT_ID, extras);
            }

            @Override
            public void onLoadItem(String itemId, Result<MediaItem> result) {
              Bundle extras = new Bundle();
              ArrayList<String> supportedActions = new ArrayList<>();
              supportedActions.add(MediaBrowserConstants.COMMAND_PLAYLIST_ADD);
              supportedActions.add(MediaBrowserConstants.COMMAND_RADIO);
              extras.putStringArrayList(
                  DESCRIPTION_EXTRAS_KEY_CUSTOM_BROWSER_ACTION_ID_LIST, supportedActions);
              MediaDescriptionCompat description =
                  new MediaDescriptionCompat.Builder()
                      .setMediaId(itemId)
                      .setExtras(extras)
                      .setTitle("title of " + itemId)
                      .build();
              result.sendResult(new MediaItem(description, MediaItem.FLAG_PLAYABLE));
            }

            @Override
            public void onCustomAction(String action, Bundle extras, Result<Bundle> result) {
              if (action.equals(MediaBrowserConstants.COMMAND_PLAYLIST_ADD)
                  || action.equals(MediaBrowserConstants.COMMAND_RADIO)) {
                Bundle resultBundle = new Bundle();
                if (extras.containsKey(
                    androidx.media3.session.legacy.MediaConstants
                        .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_MEDIA_ITEM_ID)) {
                  resultBundle.putString(
                      MediaConstants.EXTRA_KEY_MEDIA_ID,
                      extras.getString(
                          androidx.media3.session.legacy.MediaConstants
                              .EXTRAS_KEY_CUSTOM_BROWSER_ACTION_MEDIA_ITEM_ID));
                }
                session.setExtras(resultBundle);
                result.sendResult(resultBundle);
              }
            }
          });
    }

    private void setProxyForSubscribeAndRejectGetChildren() {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {

            private boolean isSubscribed;

            @Override
            public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {
              onLoadChildren(parentId, result, new Bundle());
            }

            @Override
            public void onLoadChildren(
                String parentId, Result<List<MediaItem>> result, Bundle options) {
              if (isSubscribed) {
                // Accept the first call that a Media3 browser interprets as a successful
                // subscription. Then reject any further access to onLoadChildren().
                result.sendResult(null);
                return;
              }
              isSubscribed = true;
              result.sendResult(
                  ImmutableList.of(
                      new MediaItem(
                          new MediaDescriptionCompat.Builder()
                              .setMediaUri(Uri.parse("http://www.example.com/1"))
                              .setMediaId("mediaId1")
                              .build(),
                          MediaItem.FLAG_PLAYABLE),
                      new MediaItem(
                          new MediaDescriptionCompat.Builder()
                              .setMediaUri(Uri.parse("http://www.example.com/2"))
                              .setMediaId("mediaId2")
                              .build(),
                          MediaItem.FLAG_PLAYABLE)));
            }
          });
    }

    private void getChildren_authenticationError_receivesPlaybackException(
        MediaSessionCompat session, boolean isFatal) {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            @Override
            public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {
              onLoadChildren(parentId, result, new Bundle());
            }

            @Override
            public void onLoadChildren(
                String parentId, Result<List<MediaItem>> result, Bundle bundle) {
              result.sendResult(
                  isFatal
                      ? null
                      : ImmutableList.of(
                          new MediaItem(
                              new MediaDescriptionCompat.Builder()
                                  .setMediaUri(Uri.parse("http://www.example.com"))
                                  .setMediaId("mediaId")
                                  .build(),
                              MediaItem.FLAG_PLAYABLE)));
              session.setPlaybackState(
                  new PlaybackStateCompat.Builder()
                      .setState(
                          isFatal
                              ? PlaybackStateCompat.STATE_ERROR
                              : PlaybackStateCompat.STATE_PLAYING,
                          isFatal ? PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN : 123L,
                          /* playbackSpeed= */ isFatal ? 0f : 1.0f)
                      .setErrorMessage(
                          PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED,
                          "authentication expired")
                      .build());
            }
          });
    }

    private void setProxyForTestSendCustomCommand() {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            @Override
            public BrowserRoot onGetRoot(
                String clientPackageName, int clientUid, Bundle rootHints) {
              session.setPlaybackState(
                  new PlaybackStateCompat.Builder()
                      .setState(
                          PlaybackStateCompat.STATE_PLAYING,
                          /* position= */ 123L,
                          /* playbackSpeed= */ 1.0f)
                      .addCustomAction(
                          new PlaybackStateCompat.CustomAction.Builder(
                                  MediaBrowserConstants.COMMAND_PLAYLIST_ADD,
                                  "Add to playlist",
                                  CommandButton.ICON_PLAYLIST_ADD)
                              .build())
                      .build());

              return new BrowserRoot(ROOT_ID, Bundle.EMPTY);
            }

            @Override
            public void onCustomAction(String action, Bundle extras, Result<Bundle> result) {
              Bundle resultBundle = new Bundle();
              if (action.equals(MediaBrowserConstants.COMMAND_PLAYLIST_ADD)) {
                if (extras.getBoolean("request_error", /* defaultValue= */ false)) {
                  resultBundle.putString("key-1", "error-from-service");
                  result.sendError(resultBundle);
                } else {
                  resultBundle.putString("key-1", "success-from-service");
                  result.sendResult(resultBundle);
                }
              } else {
                result.sendError(resultBundle);
              }
            }
          });
    }

    private void setProxyForTestGetLibraryRoot_correctExtraKeyAndValue() {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            @Override
            public BrowserRoot onGetRoot(
                String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
              if (rootHints != null) {
                // On API levels lower than 21 root hints are null.
                int supportedRootChildrenFlags =
                    rootHints.getInt(
                        androidx.media.utils.MediaConstants
                            .BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_SUPPORTED_FLAGS,
                        /* defaultValue= */ 0);
                if ((supportedRootChildrenFlags == MediaItem.FLAG_BROWSABLE)) {
                  return new BrowserRoot(ROOT_ID_SUPPORTS_BROWSABLE_CHILDREN_ONLY, ROOT_EXTRAS);
                }
              }
              return new BrowserRoot(ROOT_ID, ROOT_EXTRAS);
            }
          });
    }

    private void setProxyForGetChildrenIncreaseNumberOfChildrenWithEachCall() {
      setMediaBrowserServiceProxy(
          new MockMediaBrowserServiceCompat.Proxy() {
            private int callCount;

            @Override
            public BrowserRoot onGetRoot(
                String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
              return new BrowserRoot(ROOT_ID, ROOT_EXTRAS);
            }

            @Override
            public void onLoadChildren(String parentId, Result<List<MediaItem>> result) {
              super.onLoadChildren(parentId, result, Bundle.EMPTY);
            }

            @Override
            public void onLoadChildren(
                String parentId, Result<List<MediaItem>> result, Bundle options) {
              result.sendResult(MediaTestUtils.createBrowserItems(callCount + 1));
              callCount++;
            }
          });
    }
  }

  private static ImmutableList<MediaItem> createMediaItems() {
    int[] completionStates =
        new int[] {
          EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED,
          EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED,
          EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
        };
    ImmutableList.Builder<MediaItem> builder = new ImmutableList.Builder<>();
    for (int i = 0; i < 3; i++) {
      Bundle extras = new Bundle();
      extras.putInt(EXTRAS_KEY_COMPLETION_STATUS, completionStates[i]);
      builder.add(
          new MediaBrowserCompat.MediaItem(
              new MediaDescriptionCompat.Builder()
                  .setMediaId("media-id-" + i)
                  .setExtras(extras)
                  .build(),
              /* flags= */ 0));
    }
    return builder.build();
  }
}
