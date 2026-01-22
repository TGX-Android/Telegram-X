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
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_CALLBACK_TOKEN;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_CALLING_PID;
import static androidx.media3.session.legacy.MediaBrowserProtocol.DATA_CALLING_UID;
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
import static androidx.media3.session.legacy.MediaBrowserProtocol.SERVICE_VERSION_CURRENT;
import static androidx.media3.session.legacy.MediaSessionManager.RemoteUserInfo.LEGACY_CONTROLLER;
import static androidx.media3.session.legacy.MediaSessionManager.RemoteUserInfo.UNKNOWN_PID;
import static androidx.media3.session.legacy.MediaSessionManager.RemoteUserInfo.UNKNOWN_UID;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.service.media.MediaBrowserService;
import android.support.v4.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.collection.ArrayMap;
import androidx.core.util.Pair;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.legacy.MediaSessionManager.RemoteUserInfo;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Base class for media browse services.
 *
 * <p>Media browse services enable applications to browse media content provided by an application
 * and ask the application to start playing it. They may also be used to control content that is
 * already playing by way of a {@link MediaSessionCompat}. To extend this class, you must declare
 * the service in your manifest file with an intent filter with the {@link #SERVICE_INTERFACE}
 * action.
 *
 * <p>For example:
 *
 * <pre>
 * &lt;service android:name=".MyMediaBrowserServiceCompat"
 *          android:label="&#64;string/service_name" >
 *     &lt;intent-filter>
 *         &lt;action android:name="android.media.browse.MediaBrowserService" />
 *     &lt;/intent-filter>
 * &lt;/service>
 * </pre>
 *
 * <div class="special reference">
 *
 * <h2>Developer Guides</h2>
 *
 * <p>For information about building your media application, read the <a
 * href="{@docRoot}guide/topics/media-apps/index.html">Media Apps</a> developer guide. </div>
 */
@UnstableApi
@RestrictTo(LIBRARY)
public abstract class MediaBrowserServiceCompat extends Service {
  static final String TAG = "MBServiceCompat";
  static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

  private static final float EPSILON = 0.00001f;

  private @MonotonicNonNull MediaBrowserServiceImpl mImpl;

  /** The {@link Intent} that must be declared as handled by the service. */
  public static final String SERVICE_INTERFACE = "android.media.browse.MediaBrowserService";

  /** A key for passing the MediaItem to the ResultReceiver in getItem. */
  public static final String KEY_MEDIA_ITEM = "media_item";

  /** A key for passing the list of MediaItems to the ResultReceiver in search. */
  public static final String KEY_SEARCH_RESULTS = "search_results";

  static final int RESULT_FLAG_OPTION_NOT_HANDLED = 1 << 0;
  static final int RESULT_FLAG_ON_LOAD_ITEM_NOT_IMPLEMENTED = 1 << 1;
  static final int RESULT_FLAG_ON_SEARCH_NOT_IMPLEMENTED = 1 << 2;

  /** */
  public static final int RESULT_ERROR = -1;

  /** */
  public static final int RESULT_OK = 0;

  /** */
  public static final int RESULT_PROGRESS_UPDATE = 1;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {
        RESULT_FLAG_OPTION_NOT_HANDLED,
        RESULT_FLAG_ON_LOAD_ITEM_NOT_IMPLEMENTED,
        RESULT_FLAG_ON_SEARCH_NOT_IMPLEMENTED
      })
  private @interface ResultFlags {}

  private final ServiceBinderImpl mServiceBinderImpl = new ServiceBinderImpl();
  final ConnectionRecord mConnectionFromFwk =
      new ConnectionRecord(LEGACY_CONTROLLER, UNKNOWN_PID, UNKNOWN_UID, null, null);
  final ArrayList<ConnectionRecord> mPendingConnections = new ArrayList<>();
  final ArrayMap<IBinder, ConnectionRecord> mConnections = new ArrayMap<>();
  @Nullable ConnectionRecord mCurConnection;

  @SuppressWarnings({
    "argument.type.incompatible",
    "assignment.type.incompatible"
  }) // Using this before construtor completes
  final ServiceHandler mHandler = new ServiceHandler(/* service= */ this);

  @Nullable MediaSessionCompat.Token mSession;

  interface MediaBrowserServiceImpl {
    void onCreate();

    @Nullable
    IBinder onBind(Intent intent);

    void setSessionToken(MediaSessionCompat.Token token);

    void notifyChildrenChanged(String parentId, @Nullable Bundle options);

    void notifyChildrenChanged(RemoteUserInfo remoteUserInfo, String parentId, Bundle options);

    @Nullable
    Bundle getBrowserRootHints();

    RemoteUserInfo getCurrentBrowserInfo();
  }

  class MediaBrowserServiceImplBase implements MediaBrowserServiceImpl {
    private @MonotonicNonNull Messenger mMessenger;

    @Override
    public void onCreate() {
      mMessenger = new Messenger(mHandler);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
      if (SERVICE_INTERFACE.equals(intent.getAction())) {
        return checkNotNull(mMessenger).getBinder();
      }
      return null;
    }

    @Override
    public void setSessionToken(final MediaSessionCompat.Token token) {
      mHandler.post(
          new Runnable() {
            @Override
            public void run() {
              Iterator<ConnectionRecord> iter = mConnections.values().iterator();
              while (iter.hasNext()) {
                ConnectionRecord connection = iter.next();
                try {
                  BrowserRoot root = checkNotNull(connection.root);
                  checkNotNull(connection.callbacks)
                      .onConnect(root.getRootId(), token, root.getExtras());
                } catch (RemoteException e) {
                  Log.w(TAG, "Connection for " + connection.pkg + " is no longer valid.");
                  iter.remove();
                }
              }
            }
          });
    }

    @Override
    public void notifyChildrenChanged(String parentId, @Nullable Bundle options) {
      mHandler.post(
          new Runnable() {
            @Override
            public void run() {
              for (IBinder binder : mConnections.keySet()) {
                ConnectionRecord connection = checkNotNull(mConnections.get(binder));
                notifyChildrenChangedOnHandler(connection, parentId, options);
              }
            }
          });
    }

    @Override
    public void notifyChildrenChanged(
        final RemoteUserInfo remoteUserInfo, final String parentId, final Bundle options) {
      mHandler.post(
          new Runnable() {
            @Override
            public void run() {
              for (int i = 0; i < mConnections.size(); i++) {
                ConnectionRecord connection = mConnections.valueAt(i);
                if (connection.browserInfo.equals(remoteUserInfo)) {
                  notifyChildrenChangedOnHandler(connection, parentId, options);
                  break;
                }
              }
            }
          });
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void notifyChildrenChangedOnHandler(
        ConnectionRecord connection, String parentId, @Nullable Bundle options) {
      List<Pair<@NullableType IBinder, @NullableType Bundle>> callbackList =
          connection.subscriptions.get(parentId);
      if (callbackList != null) {
        for (Pair<@NullableType IBinder, @NullableType Bundle> callback : callbackList) {
          if (MediaBrowserCompatUtils.hasDuplicatedItems(options, callback.second)) {
            performLoadChildren(parentId, connection, callback.second, options);
          }
        }
      }
      // Don't break, because multiple remoteUserInfo may match.
    }

    @Nullable
    @Override
    public Bundle getBrowserRootHints() {
      if (mCurConnection == null) {
        throw new IllegalStateException(
            "This should be called inside of onLoadChildren,"
                + " onLoadItem, onSearch, or onCustomAction methods");
      }
      return mCurConnection.rootHints == null ? null : new Bundle(mCurConnection.rootHints);
    }

    @Override
    public RemoteUserInfo getCurrentBrowserInfo() {
      if (mCurConnection == null) {
        throw new IllegalStateException(
            "This should be called inside of onLoadChildren,"
                + " onLoadItem, onSearch, or onCustomAction methods");
      }
      return mCurConnection.browserInfo;
    }
  }

  @RequiresApi(21)
  class MediaBrowserServiceImplApi21 implements MediaBrowserServiceImpl {
    final List<Bundle> mRootExtrasList = new ArrayList<>();
    @MonotonicNonNull MediaBrowserService mServiceFwk;
    @MonotonicNonNull Messenger mMessenger;

    @Override
    public void onCreate() {
      mServiceFwk = new MediaBrowserServiceApi21(MediaBrowserServiceCompat.this);
      mServiceFwk.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
      return checkNotNull(mServiceFwk).onBind(intent);
    }

    @Override
    public void setSessionToken(final MediaSessionCompat.Token token) {
      mHandler.postOrRun(
          new Runnable() {
            @Override
            public void run() {
              setSessionTokenOnHandler(token);
            }
          });
    }

    void setSessionTokenOnHandler(MediaSessionCompat.Token token) {
      if (!mRootExtrasList.isEmpty()) {
        IMediaSession extraBinder = token.getExtraBinder();
        if (extraBinder != null) {
          for (Bundle rootExtras : mRootExtrasList) {
            rootExtras.putBinder(EXTRA_SESSION_BINDER, extraBinder.asBinder());
          }
        }
        mRootExtrasList.clear();
      }
      checkNotNull(mServiceFwk)
          .setSessionToken(checkNotNull((MediaSession.Token) token.getToken()));
    }

    @Override
    public void notifyChildrenChanged(String parentId, @Nullable Bundle options) {
      notifyChildrenChangedForFramework(parentId, options);
      notifyChildrenChangedForCompat(parentId, options);
    }

    @Override
    public void notifyChildrenChanged(
        final RemoteUserInfo remoteUserInfo, final String parentId, final Bundle options) {
      // TODO(Post-P): Need a way to notify to a specific browser in framework.
      notifyChildrenChangedForCompat(remoteUserInfo, parentId, options);
    }

    @Nullable
    public BrowserRoot onGetRoot(
        String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
      Bundle rootExtras = null;
      int clientPid = UNKNOWN_PID;
      if (rootHints != null && rootHints.getInt(EXTRA_CLIENT_VERSION, 0) != 0) {
        rootHints.remove(EXTRA_CLIENT_VERSION);
        mMessenger = new Messenger(mHandler);
        rootExtras = new Bundle();
        rootExtras.putInt(EXTRA_SERVICE_VERSION, SERVICE_VERSION_CURRENT);
        rootExtras.putBinder(EXTRA_MESSENGER_BINDER, mMessenger.getBinder());
        if (mSession != null) {
          IMediaSession extraBinder = mSession.getExtraBinder();
          rootExtras.putBinder(
              EXTRA_SESSION_BINDER, extraBinder == null ? null : extraBinder.asBinder());
        } else {
          mRootExtrasList.add(rootExtras);
        }
        clientPid = rootHints.getInt(EXTRA_CALLING_PID, UNKNOWN_PID);
        rootHints.remove(EXTRA_CALLING_PID);
      }
      ConnectionRecord connection =
          new ConnectionRecord(clientPackageName, clientPid, clientUid, rootHints, null);
      // We aren't sure whether this connection request would be accepted.
      // Temporarily set mCurConnection just to make getCurrentBrowserInfo() working.
      mCurConnection = connection;
      BrowserRoot root =
          MediaBrowserServiceCompat.this.onGetRoot(clientPackageName, clientUid, rootHints);
      mCurConnection = null;
      if (root == null) {
        return null;
      }
      if (mMessenger != null) {
        // Keeps the connection request from the MediaBrowserCompat to reuse the package
        // name here.
        // Note: Connection will be completed after it gets extra binder call with
        // CLIENT_MSG_REGISTER_CALLBACK_MESSENGER.
        mPendingConnections.add(connection);
      }
      @Nullable Bundle existingRootExtras = root.getExtras();
      if (rootExtras == null) {
        rootExtras = existingRootExtras;
      } else if (existingRootExtras != null) {
        rootExtras.putAll(existingRootExtras);
      }
      return new BrowserRoot(root.getRootId(), rootExtras);
    }

    public void onLoadChildren(String parentId, final ResultWrapper<List<Parcel>> resultWrapper) {
      final Result<List<MediaBrowserCompat.MediaItem>> result =
          new Result<List<MediaBrowserCompat.MediaItem>>(parentId) {
            @Override
            void onResultSent(@Nullable List<MediaBrowserCompat.MediaItem> list) {
              List<Parcel> parcelList;
              if (list == null) {
                // A null children list here indicates that the requested parentId
                // is invalid. Unfortunately before API 24 the platform
                // MediaBrowserService's implementation of Result inside
                // performLoadChildren (invoked below) throws an exception if
                // given a null list  (b/19127753). This means there's no clear
                // way to communicate an invalid parentId, so in order to avoid
                // an exception below API 24 we transform null to an empty list
                // here (meaning it looks like parentId is valid but has no
                // children).
                parcelList = Build.VERSION.SDK_INT >= 24 ? null : Collections.emptyList();
              } else {
                parcelList = new ArrayList<>(list.size());
                for (MediaBrowserCompat.MediaItem item : list) {
                  Parcel parcel = Parcel.obtain();
                  item.writeToParcel(parcel, 0);
                  parcelList.add(parcel);
                }
              }
              resultWrapper.sendResult(parcelList);
            }

            @Override
            public void detach() {
              resultWrapper.detach();
            }
          };
      mCurConnection = mConnectionFromFwk;
      MediaBrowserServiceCompat.this.onLoadChildren(parentId, result);
      mCurConnection = null;
    }

    void notifyChildrenChangedForFramework(String parentId, @Nullable Bundle options) {
      checkNotNull(mServiceFwk).notifyChildrenChanged(parentId);
    }

    void notifyChildrenChangedForCompat(String parentId, @Nullable Bundle options) {
      mHandler.post(
          new Runnable() {
            @Override
            public void run() {
              for (IBinder binder : mConnections.keySet()) {
                ConnectionRecord connection = checkNotNull(mConnections.get(binder));
                notifyChildrenChangedForCompatOnHandler(connection, parentId, options);
              }
            }
          });
    }

    void notifyChildrenChangedForCompat(
        final RemoteUserInfo remoteUserInfo, final String parentId, final Bundle options) {
      mHandler.post(
          new Runnable() {
            @Override
            public void run() {
              for (int i = 0; i < mConnections.size(); i++) {
                ConnectionRecord connection = mConnections.valueAt(i);
                if (connection.browserInfo.equals(remoteUserInfo)) {
                  notifyChildrenChangedForCompatOnHandler(connection, parentId, options);
                }
              }
            }
          });
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void notifyChildrenChangedForCompatOnHandler(
        ConnectionRecord connection, String parentId, @Nullable Bundle options) {
      List<Pair<@NullableType IBinder, @NullableType Bundle>> callbackList =
          connection.subscriptions.get(parentId);
      if (callbackList != null) {
        for (Pair<@NullableType IBinder, @NullableType Bundle> callback : callbackList) {
          if (MediaBrowserCompatUtils.hasDuplicatedItems(options, callback.second)) {
            performLoadChildren(parentId, connection, callback.second, options);
          }
        }
      }
    }

    @Nullable
    @Override
    public Bundle getBrowserRootHints() {
      if (mMessenger == null) {
        // TODO: Handle getBrowserRootHints when connected with framework MediaBrowser.
        return null;
      }
      if (mCurConnection == null) {
        throw new IllegalStateException(
            "This should be called inside of onGetRoot,"
                + " onLoadChildren, onLoadItem, onSearch, or onCustomAction methods");
      }
      return mCurConnection.rootHints == null ? null : new Bundle(mCurConnection.rootHints);
    }

    @Override
    public RemoteUserInfo getCurrentBrowserInfo() {
      if (mCurConnection == null) {
        throw new IllegalStateException(
            "This should be called inside of onGetRoot,"
                + " onLoadChildren, onLoadItem, onSearch, or onCustomAction methods");
      }
      return mCurConnection.browserInfo;
    }

    @RequiresApi(21)
    class MediaBrowserServiceApi21 extends MediaBrowserService {
      @SuppressWarnings("method.invocation.invalid") // Calling base method from constructor
      MediaBrowserServiceApi21(Context context) {
        attachBaseContext(context);
      }

      @Nullable
      @Override
      public MediaBrowserService.BrowserRoot onGetRoot(
          String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        MediaSessionCompat.ensureClassLoader(rootHints);
        MediaBrowserServiceCompat.BrowserRoot browserRootCompat =
            MediaBrowserServiceImplApi21.this.onGetRoot(
                clientPackageName, clientUid, rootHints == null ? null : new Bundle(rootHints));
        return browserRootCompat == null
            ? null
            : new MediaBrowserService.BrowserRoot(
                browserRootCompat.mRootId, browserRootCompat.mExtras);
      }

      @Override
      public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        MediaBrowserServiceImplApi21.this.onLoadChildren(
            parentId, new ResultWrapper<List<Parcel>>(result));
      }
    }
  }

  @RequiresApi(23)
  class MediaBrowserServiceImplApi23 extends MediaBrowserServiceImplApi21 {
    @Override
    public void onCreate() {
      mServiceFwk = new MediaBrowserServiceApi23(MediaBrowserServiceCompat.this);
      mServiceFwk.onCreate();
    }

    public void onLoadItem(String itemId, final ResultWrapper<Parcel> resultWrapper) {
      final Result<MediaBrowserCompat.MediaItem> result =
          new Result<MediaBrowserCompat.MediaItem>(itemId) {
            @Override
            void onResultSent(@Nullable MediaBrowserCompat.MediaItem item) {
              if (item == null) {
                resultWrapper.sendResult(null);
              } else {
                Parcel parcelItem = Parcel.obtain();
                item.writeToParcel(parcelItem, 0);
                resultWrapper.sendResult(parcelItem);
              }
            }

            @Override
            public void detach() {
              resultWrapper.detach();
            }
          };
      mCurConnection = mConnectionFromFwk;
      MediaBrowserServiceCompat.this.onLoadItem(itemId, result);
      mCurConnection = null;
    }

    class MediaBrowserServiceApi23 extends MediaBrowserServiceApi21 {
      MediaBrowserServiceApi23(Context context) {
        super(context);
      }

      @Override
      public void onLoadItem(String itemId, Result<MediaBrowser.MediaItem> result) {
        MediaBrowserServiceImplApi23.this.onLoadItem(itemId, new ResultWrapper<Parcel>(result));
      }
    }
  }

  @RequiresApi(26)
  class MediaBrowserServiceImplApi26 extends MediaBrowserServiceImplApi23 {
    @Override
    public void onCreate() {
      mServiceFwk = new MediaBrowserServiceApi26(MediaBrowserServiceCompat.this);
      mServiceFwk.onCreate();
    }

    public void onLoadChildren(
        String parentId, final ResultWrapper<List<Parcel>> resultWrapper, final Bundle options) {
      final Result<List<MediaBrowserCompat.MediaItem>> result =
          new Result<List<MediaBrowserCompat.MediaItem>>(parentId) {
            @Override
            void onResultSent(@Nullable List<MediaBrowserCompat.MediaItem> list) {
              if (list == null) {
                resultWrapper.sendResult(null);
                return;
              }
              if ((getFlags() & RESULT_FLAG_OPTION_NOT_HANDLED) != 0) {
                // If onLoadChildren(options) is not overridden, the list we get
                // here is not paginated. Therefore, we need to manually cut
                // the list. In other words, we need to apply options here.
                list = applyOptions(list, options);
              }
              List<Parcel> parcelList = new ArrayList<>(list == null ? 0 : list.size());
              if (list != null) {
                for (MediaBrowserCompat.MediaItem item : list) {
                  Parcel parcel = Parcel.obtain();
                  item.writeToParcel(parcel, 0);
                  parcelList.add(parcel);
                }
              }
              resultWrapper.sendResult(parcelList);
            }

            @Override
            public void detach() {
              resultWrapper.detach();
            }
          };
      mCurConnection = mConnectionFromFwk;
      MediaBrowserServiceCompat.this.onLoadChildren(parentId, result, options);
      mCurConnection = null;
    }

    @Nullable
    @Override
    public Bundle getBrowserRootHints() {
      if (mCurConnection == null) {
        throw new IllegalStateException(
            "This should be called inside of onGetRoot,"
                + " onLoadChildren, onLoadItem, onSearch, or onCustomAction methods");
      }
      if (mCurConnection == mConnectionFromFwk) {
        return checkNotNull(mServiceFwk).getBrowserRootHints();
      }
      return mCurConnection.rootHints == null ? null : new Bundle(mCurConnection.rootHints);
    }

    @Override
    void notifyChildrenChangedForFramework(final String parentId, @Nullable Bundle options) {
      if (options != null) {
        checkNotNull(mServiceFwk).notifyChildrenChanged(parentId, options);
      } else {
        super.notifyChildrenChangedForFramework(parentId, options);
      }
    }

    class MediaBrowserServiceApi26 extends MediaBrowserServiceApi23 {
      MediaBrowserServiceApi26(Context context) {
        super(context);
      }

      @Override
      public void onLoadChildren(
          String parentId, Result<List<MediaBrowser.MediaItem>> result, Bundle options) {
        MediaSessionCompat.ensureClassLoader(options);
        mCurConnection = mConnectionFromFwk;
        MediaBrowserServiceImplApi26.this.onLoadChildren(
            parentId, new ResultWrapper<List<Parcel>>(result), options);
        mCurConnection = null;
      }
    }
  }

  @RequiresApi(28)
  class MediaBrowserServiceImplApi28 extends MediaBrowserServiceImplApi26 {
    @Override
    public RemoteUserInfo getCurrentBrowserInfo() {
      if (mCurConnection == null) {
        throw new IllegalStateException(
            "This should be called inside of onGetRoot,"
                + " onLoadChildren, onLoadItem, onSearch, or onCustomAction methods");
      }
      if (mCurConnection == mConnectionFromFwk) {
        return new RemoteUserInfo(checkNotNull(mServiceFwk).getCurrentBrowserInfo());
      }
      return mCurConnection.browserInfo;
    }
  }

  private static final class ServiceHandler extends Handler {

    // Must only be accessed on the main thread.
    @Nullable private MediaBrowserServiceCompat mService;

    @MainThread
    ServiceHandler(MediaBrowserServiceCompat service) {
      mService = service;
    }

    @Override
    @MainThread
    public void handleMessage(Message msg) {
      if (mService != null) {
        mService.handleMessageInternal(msg);
      } else {
        removeCallbacksAndMessages(/* token= */ null);
      }
    }

    @MainThread
    public void release() {
      mService = null;
    }

    @Override
    public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
      // Binder.getCallingUid() in handleMessage will return the uid of this process.
      // In order to get the right calling uid, Binder.getCallingUid() should be called here.
      Bundle data = msg.getData();
      data.setClassLoader(checkNotNull(MediaBrowserCompat.class.getClassLoader()));
      data.putInt(DATA_CALLING_UID, Binder.getCallingUid());
      int pid = Binder.getCallingPid();
      if (pid > 0) {
        data.putInt(DATA_CALLING_PID, pid);
      } else if (!data.containsKey(DATA_CALLING_PID)) {
        // If the MediaBrowserCompat didn't send its PID, then put UNKNOWN_PID.
        data.putInt(DATA_CALLING_PID, UNKNOWN_PID);
      }
      return super.sendMessageAtTime(msg, uptimeMillis);
    }

    public void postOrRun(Runnable r) {
      if (Thread.currentThread() == getLooper().getThread()) {
        r.run();
      } else {
        post(r);
      }
    }
  }

  /** All the info about a connection. */
  private class ConnectionRecord implements IBinder.DeathRecipient {
    @Nullable public final String pkg;
    public final int pid;
    public final int uid;
    public final RemoteUserInfo browserInfo;
    @Nullable public final Bundle rootHints;
    @Nullable public final ServiceCallbacks callbacks;
    public final HashMap<
            @NullableType String, List<Pair<@NullableType IBinder, @NullableType Bundle>>>
        subscriptions = new HashMap<>();
    @Nullable public BrowserRoot root;

    ConnectionRecord(
        @Nullable String pkg,
        int pid,
        int uid,
        @Nullable Bundle rootHints,
        @Nullable ServiceCallbacks callback) {
      this.pkg = pkg;
      this.pid = pid;
      this.uid = uid;
      this.browserInfo = new RemoteUserInfo(pkg, pid, uid);
      this.rootHints = rootHints;
      this.callbacks = callback;
    }

    @Override
    public void binderDied() {
      mHandler.post(
          new Runnable() {
            @Override
            public void run() {
              mConnections.remove(checkNotNull(callbacks).asBinder());
            }
          });
    }
  }

  /**
   * Completion handler for asynchronous callback methods in {@link MediaBrowserServiceCompat}.
   *
   * <p>Each of the methods that takes one of these to send the result must call either {@link
   * #sendResult} or {@link #sendError} to respond to the caller with the given results or errors.
   * If those functions return without calling {@link #sendResult} or {@link #sendError}, they must
   * instead call {@link #detach} before returning, and then may call {@link #sendResult} or {@link
   * #sendError} when they are done. If {@link #sendResult}, {@link #sendError}, or {@link #detach}
   * is called twice, an exception will be thrown.
   *
   * <p>Those functions might also want to call {@link #sendProgressUpdate} to send interim updates
   * to the caller. If it is called after calling {@link #sendResult} or {@link #sendError}, an
   * exception will be thrown.
   *
   * @see MediaBrowserServiceCompat#onLoadChildren
   * @see MediaBrowserServiceCompat#onLoadItem
   * @see MediaBrowserServiceCompat#onSearch
   * @see MediaBrowserServiceCompat#onCustomAction
   */
  public static class Result<T> {
    @Nullable private final Object mDebug;
    private boolean mDetachCalled;
    private boolean mSendResultCalled;
    private boolean mSendErrorCalled;
    private int mFlags;

    Result(@Nullable Object debug) {
      mDebug = debug;
    }

    /** Send the result back to the caller. */
    public void sendResult(@Nullable T result) {
      if (mSendResultCalled || mSendErrorCalled) {
        throw new IllegalStateException(
            "sendResult() called when either sendResult() or "
                + "sendError() had already been called for: "
                + mDebug);
      }
      mSendResultCalled = true;
      onResultSent(result);
    }

    /**
     * Send an interim update to the caller. This method is supported only when it is used in {@link
     * #onCustomAction}.
     *
     * @param extras A bundle that contains extra data.
     */
    public void sendProgressUpdate(@Nullable Bundle extras) {
      if (mSendResultCalled || mSendErrorCalled) {
        throw new IllegalStateException(
            "sendProgressUpdate() called when either "
                + "sendResult() or sendError() had already been called for: "
                + mDebug);
      }
      checkExtraFields(extras);
      onProgressUpdateSent(extras);
    }

    /**
     * Notify the caller of a failure. This is supported only when it is used in {@link
     * #onCustomAction}.
     *
     * @param extras A bundle that contains extra data.
     */
    public void sendError(@Nullable Bundle extras) {
      if (mSendResultCalled || mSendErrorCalled) {
        throw new IllegalStateException(
            "sendError() called when either sendResult() or "
                + "sendError() had already been called for: "
                + mDebug);
      }
      mSendErrorCalled = true;
      onErrorSent(extras);
    }

    /**
     * Detach this message from the current thread and allow the {@link #sendResult} call to happen
     * later.
     */
    public void detach() {
      if (mDetachCalled) {
        throw new IllegalStateException(
            "detach() called when detach() had already" + " been called for: " + mDebug);
      }
      if (mSendResultCalled) {
        throw new IllegalStateException(
            "detach() called when sendResult() had already" + " been called for: " + mDebug);
      }
      if (mSendErrorCalled) {
        throw new IllegalStateException(
            "detach() called when sendError() had already" + " been called for: " + mDebug);
      }
      mDetachCalled = true;
    }

    boolean isDone() {
      return mDetachCalled || mSendResultCalled || mSendErrorCalled;
    }

    void setFlags(@ResultFlags int flags) {
      mFlags = flags;
    }

    int getFlags() {
      return mFlags;
    }

    /**
     * Called when the result is sent, after assertions about not being called twice have happened.
     */
    void onResultSent(@Nullable T result) {}

    /** Called when an interim update is sent. */
    void onProgressUpdateSent(@Nullable Bundle extras) {
      throw new UnsupportedOperationException(
          "It is not supported to send an interim update " + "for " + mDebug);
    }

    /**
     * Called when an error is sent, after assertions about not being called twice have happened.
     */
    void onErrorSent(@Nullable Bundle extras) {
      throw new UnsupportedOperationException("It is not supported to send an error for " + mDebug);
    }

    private void checkExtraFields(@Nullable Bundle extras) {
      if (extras == null) {
        return;
      }
      if (extras.containsKey(MediaBrowserCompat.EXTRA_DOWNLOAD_PROGRESS)) {
        float value = extras.getFloat(MediaBrowserCompat.EXTRA_DOWNLOAD_PROGRESS);
        if (value < -EPSILON || value > 1.0f + EPSILON) {
          throw new IllegalArgumentException(
              "The value of the EXTRA_DOWNLOAD_PROGRESS "
                  + "field must be a float number within [0.0, 1.0]");
        }
      }
    }
  }

  private class ServiceBinderImpl {
    ServiceBinderImpl() {}

    public void connect(
        @Nullable String pkg,
        int pid,
        int uid,
        @Nullable Bundle rootHints,
        ServiceCallbacks callbacks) {

      if (!isValidPackage(pkg, uid)) {
        throw new IllegalArgumentException("Package/uid mismatch: uid=" + uid + " package=" + pkg);
      }

      mHandler.postOrRun(
          new Runnable() {
            @Override
            public void run() {
              final IBinder b = callbacks.asBinder();

              // Clear out the old subscriptions. We are getting new ones.
              mConnections.remove(b);

              ConnectionRecord connection =
                  new ConnectionRecord(pkg, pid, uid, rootHints, callbacks);
              mCurConnection = connection;
              BrowserRoot root = MediaBrowserServiceCompat.this.onGetRoot(pkg, uid, rootHints);
              connection.root = root;
              mCurConnection = null;

              // If they didn't return something, don't allow this client.
              if (root == null) {
                Log.i(TAG, "No root for client " + pkg + " from service " + getClass().getName());
                try {
                  callbacks.onConnectFailed();
                } catch (RemoteException ex) {
                  Log.w(TAG, "Calling onConnectFailed() failed. Ignoring. " + "pkg=" + pkg);
                }
              } else {
                try {
                  mConnections.put(b, connection);
                  b.linkToDeath(connection, 0);
                  if (mSession != null) {
                    callbacks.onConnect(root.getRootId(), mSession, root.getExtras());
                  }
                } catch (RemoteException ex) {
                  Log.w(TAG, "Calling onConnect() failed. Dropping client. " + "pkg=" + pkg);
                  mConnections.remove(b);
                }
              }
            }
          });
    }

    public void disconnect(ServiceCallbacks callbacks) {
      mHandler.postOrRun(
          new Runnable() {
            @Override
            public void run() {
              final IBinder b = callbacks.asBinder();

              // Clear out the old subscriptions. We are getting new ones.
              final ConnectionRecord old = mConnections.remove(b);
              if (old != null) {
                // TODO
                checkNotNull(old.callbacks).asBinder().unlinkToDeath(old, 0);
              }
            }
          });
    }

    public void addSubscription(
        @Nullable String id,
        @Nullable IBinder token,
        @Nullable Bundle options,
        ServiceCallbacks callbacks) {
      mHandler.postOrRun(
          new Runnable() {
            @Override
            public void run() {
              IBinder b = callbacks.asBinder();

              // Get the record for the connection
              ConnectionRecord connection = mConnections.get(b);
              if (connection == null) {
                Log.w(TAG, "addSubscription for callback that isn't registered id=" + id);
                return;
              }

              MediaBrowserServiceCompat.this.addSubscription(id, connection, token, options);
            }
          });
    }

    public void removeSubscription(
        @Nullable String id, @Nullable IBinder token, ServiceCallbacks callbacks) {
      mHandler.postOrRun(
          new Runnable() {
            @Override
            public void run() {
              final IBinder b = callbacks.asBinder();

              ConnectionRecord connection = mConnections.get(b);
              if (connection == null) {
                Log.w(TAG, "removeSubscription for callback that isn't registered id=" + id);
                return;
              }
              if (!MediaBrowserServiceCompat.this.removeSubscription(id, connection, token)) {
                Log.w(TAG, "removeSubscription called for " + id + " which is not subscribed");
              }
            }
          });
    }

    public void getMediaItem(
        @Nullable String mediaId,
        @Nullable ResultReceiver receiver,
        final ServiceCallbacks callbacks) {
      if (TextUtils.isEmpty(mediaId) || receiver == null) {
        return;
      }

      mHandler.postOrRun(
          new Runnable() {
            @Override
            public void run() {
              final IBinder b = callbacks.asBinder();

              ConnectionRecord connection = mConnections.get(b);
              if (connection == null) {
                Log.w(TAG, "getMediaItem for callback that isn't registered id=" + mediaId);
                return;
              }
              performLoadItem(mediaId, connection, receiver);
            }
          });
    }

    // Used when {@link MediaBrowserProtocol#EXTRA_MESSENGER_BINDER} is used.
    public void registerCallbacks(
        ServiceCallbacks callbacks,
        @Nullable String pkg,
        int pid,
        int uid,
        @Nullable Bundle rootHints) {
      mHandler.postOrRun(
          new Runnable() {
            @Override
            public void run() {
              final IBinder b = callbacks.asBinder();
              // Clear out the old subscriptions. We are getting new ones.
              mConnections.remove(b);

              ConnectionRecord connection = null;
              Iterator<ConnectionRecord> iter = mPendingConnections.iterator();
              while (iter.hasNext()) {
                ConnectionRecord pendingConnection = iter.next();
                // Note: We cannot use Map/Set for mPendingConnections but List because
                // multiple MediaBrowserCompats with the same UID can request connect.
                if (pendingConnection.uid == uid) {
                  // If caller hasn't set pkg and pid, do the best effort to get it.
                  if (TextUtils.isEmpty(pkg) || pid <= 0) {
                    // Note: Do not assign pendingConnection directly because it doesn't
                    // have callback information.
                    connection =
                        new ConnectionRecord(
                            pendingConnection.pkg,
                            pendingConnection.pid,
                            pendingConnection.uid,
                            rootHints,
                            callbacks);
                  }
                  iter.remove();
                  break;
                }
              }
              if (connection == null) {
                connection = new ConnectionRecord(pkg, pid, uid, rootHints, callbacks);
              }
              mConnections.put(b, connection);
              try {
                b.linkToDeath(connection, 0);
              } catch (RemoteException e) {
                Log.w(TAG, "IBinder is already dead.");
              }
            }
          });
    }

    // Used when {@link MediaBrowserProtocol#EXTRA_MESSENGER_BINDER} is used.
    public void unregisterCallbacks(final ServiceCallbacks callbacks) {
      mHandler.postOrRun(
          new Runnable() {
            @Override
            public void run() {
              final IBinder b = callbacks.asBinder();
              ConnectionRecord old = mConnections.remove(b);
              if (old != null) {
                b.unlinkToDeath(old, 0);
              }
            }
          });
    }

    public void search(
        @Nullable String query,
        @Nullable Bundle extras,
        @Nullable ResultReceiver receiver,
        final ServiceCallbacks callbacks) {
      if (TextUtils.isEmpty(query) || receiver == null) {
        return;
      }

      mHandler.postOrRun(
          new Runnable() {
            @Override
            public void run() {
              final IBinder b = callbacks.asBinder();

              ConnectionRecord connection = mConnections.get(b);
              if (connection == null) {
                Log.w(TAG, "search for callback that isn't registered query=" + query);
                return;
              }
              performSearch(query, extras, connection, receiver);
            }
          });
    }

    public void sendCustomAction(
        @Nullable String action,
        @Nullable Bundle extras,
        @Nullable ResultReceiver receiver,
        ServiceCallbacks callbacks) {
      if (TextUtils.isEmpty(action) || receiver == null) {
        return;
      }

      mHandler.postOrRun(
          new Runnable() {
            @Override
            public void run() {
              final IBinder b = callbacks.asBinder();

              ConnectionRecord connection = mConnections.get(b);
              if (connection == null) {
                Log.w(
                    TAG,
                    "sendCustomAction for callback that isn't registered action="
                        + action
                        + ", extras="
                        + extras);
                return;
              }
              performCustomAction(action, extras, connection, receiver);
            }
          });
    }
  }

  private interface ServiceCallbacks {
    IBinder asBinder();

    void onConnect(String root, @Nullable MediaSessionCompat.Token session, @Nullable Bundle extras)
        throws RemoteException;

    void onConnectFailed() throws RemoteException;

    void onLoadChildren(
        @Nullable String mediaId,
        @Nullable List<MediaBrowserCompat.MediaItem> list,
        @Nullable Bundle options,
        @Nullable Bundle notifyChildrenChangedOptions)
        throws RemoteException;
  }

  private static class ServiceCallbacksCompat implements ServiceCallbacks {
    final Messenger mCallbacks;

    ServiceCallbacksCompat(Messenger callbacks) {
      mCallbacks = callbacks;
    }

    @Override
    public IBinder asBinder() {
      return mCallbacks.getBinder();
    }

    @Override
    public void onConnect(
        String root, @Nullable MediaSessionCompat.Token session, @Nullable Bundle extras)
        throws RemoteException {
      if (extras == null) {
        extras = new Bundle();
      }
      extras.putInt(EXTRA_SERVICE_VERSION, SERVICE_VERSION_CURRENT);
      Bundle data = new Bundle();
      data.putString(DATA_MEDIA_ITEM_ID, root);
      data.putParcelable(
          DATA_MEDIA_SESSION_TOKEN,
          LegacyParcelableUtil.convert(
              session, android.support.v4.media.session.MediaSessionCompat.Token.CREATOR));
      data.putBundle(DATA_ROOT_HINTS, extras);
      sendRequest(SERVICE_MSG_ON_CONNECT, data);
    }

    @Override
    public void onConnectFailed() throws RemoteException {
      sendRequest(SERVICE_MSG_ON_CONNECT_FAILED, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void onLoadChildren(
        @Nullable String mediaId,
        @Nullable List<MediaBrowserCompat.MediaItem> list,
        @Nullable Bundle options,
        @Nullable Bundle notifyChildrenChangedOptions)
        throws RemoteException {
      Bundle data = new Bundle();
      data.putString(DATA_MEDIA_ITEM_ID, mediaId);
      data.putBundle(DATA_OPTIONS, options);
      data.putBundle(DATA_NOTIFY_CHILDREN_CHANGED_OPTIONS, notifyChildrenChangedOptions);
      if (list != null) {
        data.putParcelableArrayList(
            DATA_MEDIA_ITEM_LIST,
            LegacyParcelableUtil.convertList(
                list, android.support.v4.media.MediaBrowserCompat.MediaItem.CREATOR));
      }
      sendRequest(SERVICE_MSG_ON_LOAD_CHILDREN, data);
    }

    private void sendRequest(int what, @Nullable Bundle data) throws RemoteException {
      Message msg = Message.obtain();
      msg.what = what;
      msg.arg1 = SERVICE_VERSION_CURRENT;
      if (data != null) {
        msg.setData(data);
      }
      mCallbacks.send(msg);
    }
  }

  @RequiresApi(21)
  @SuppressWarnings({"rawtypes", "unchecked"})
  static class ResultWrapper<T> {
    MediaBrowserService.Result mResultFwk;

    ResultWrapper(MediaBrowserService.Result result) {
      mResultFwk = result;
    }

    public void sendResult(@Nullable T result) {
      if (result instanceof List) {
        mResultFwk.sendResult(parcelListToItemList((List<Parcel>) result));
      } else if (result instanceof Parcel) {
        Parcel parcel = (Parcel) result;
        parcel.setDataPosition(0);
        mResultFwk.sendResult(MediaBrowser.MediaItem.CREATOR.createFromParcel(parcel));
        parcel.recycle();
      } else {
        // The result is null or an invalid instance.
        mResultFwk.sendResult(null);
      }
    }

    public void detach() {
      mResultFwk.detach();
    }

    @Nullable
    List<MediaBrowser.MediaItem> parcelListToItemList(List<Parcel> parcelList) {
      if (parcelList == null) {
        return null;
      }
      List<MediaBrowser.MediaItem> items = new ArrayList<>(parcelList.size());
      for (Parcel parcel : parcelList) {
        parcel.setDataPosition(0);
        items.add(MediaBrowser.MediaItem.CREATOR.createFromParcel(parcel));
        parcel.recycle();
      }
      return items;
    }
  }

  /**
   * Attaches to the base context. This method is added to change the visibility of {@link
   * Service#attachBaseContext(Context)}.
   *
   * <p>Note that we cannot simply override {@link Service#attachBaseContext(Context)} and hide it
   * because lint checks considers the overridden method as the new public API that needs update of
   * current.txt.
   */
  public void attachToBaseContext(Context base) {
    attachBaseContext(base);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    if (Build.VERSION.SDK_INT >= 28) {
      mImpl = new MediaBrowserServiceImplApi28();
    } else if (Build.VERSION.SDK_INT >= 26) {
      mImpl = new MediaBrowserServiceImplApi26();
    } else if (Build.VERSION.SDK_INT >= 23) {
      mImpl = new MediaBrowserServiceImplApi23();
    } else if (Build.VERSION.SDK_INT >= 21) {
      mImpl = new MediaBrowserServiceImplApi21();
    } else {
      mImpl = new MediaBrowserServiceImplBase();
    }
    mImpl.onCreate();
  }

  @CallSuper
  @MainThread
  @Override
  public void onDestroy() {
    mHandler.release();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return checkNotNull(mImpl).onBind(intent);
  }

  @Override
  public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {}

  /**
   * Called to get the root information for browsing by a particular client.
   *
   * <p>The implementation should verify that the client package has permission to access browse
   * media information before returning the root id; it should return null if the client is not
   * allowed to access this information.
   *
   * @param clientPackageName The package name of the application which is requesting access to
   *     browse media.
   * @param clientUid The uid of the application which is requesting access to browse media.
   * @param rootHints An optional bundle of service-specific arguments to send to the media browse
   *     service when connecting and retrieving the root id for browsing, or null if none. The
   *     contents of this bundle may affect the information returned when browsing.
   * @return The {@link BrowserRoot} for accessing this app's content or null.
   * @see BrowserRoot#EXTRA_RECENT
   * @see BrowserRoot#EXTRA_OFFLINE
   * @see BrowserRoot#EXTRA_SUGGESTED
   */
  public abstract @Nullable BrowserRoot onGetRoot(
      @Nullable String clientPackageName, int clientUid, @Nullable Bundle rootHints);

  /**
   * Called to get information about the children of a media item.
   *
   * <p>Implementations must call {@link Result#sendResult result.sendResult} with the list of
   * children. If loading the children will be an expensive operation that should be performed on
   * another thread, {@link Result#detach result.detach} may be called before returning from this
   * function, and then {@link Result#sendResult result.sendResult} called when the loading is
   * complete.
   *
   * <p>In case the media item does not have any children, call {@link Result#sendResult} with an
   * empty list. When the given {@code parentId} is invalid, implementations must call {@link
   * Result#sendResult result.sendResult} with {@code null}, which will invoke {@link
   * MediaBrowserCompat.SubscriptionCallback#onError}.
   *
   * @param parentId The id of the parent media item whose children are to be queried.
   * @param result The Result to send the list of children to.
   */
  public abstract void onLoadChildren(
      @Nullable String parentId, Result<List<MediaBrowserCompat.MediaItem>> result);

  /**
   * Called to get information about the children of a media item.
   *
   * <p>Implementations must call {@link Result#sendResult result.sendResult} with the list of
   * children. If loading the children will be an expensive operation that should be performed on
   * another thread, {@link Result#detach result.detach} may be called before returning from this
   * function, and then {@link Result#sendResult result.sendResult} called when the loading is
   * complete.
   *
   * <p>In case the media item does not have any children, call {@link Result#sendResult} with an
   * empty list. When the given {@code parentId} is invalid, implementations must call {@link
   * Result#sendResult result.sendResult} with {@code null}, which will invoke {@link
   * MediaBrowserCompat.SubscriptionCallback#onError}.
   *
   * @param parentId The id of the parent media item whose children are to be queried.
   * @param result The Result to send the list of children to.
   * @param options A bundle of service-specific arguments sent from the media browse. The
   *     information returned through the result should be affected by the contents of this bundle.
   */
  public void onLoadChildren(
      @Nullable String parentId,
      Result<List<MediaBrowserCompat.MediaItem>> result,
      Bundle options) {
    // To support backward compatibility, when the implementation of MediaBrowserService doesn't
    // override onLoadChildren() with options, onLoadChildren() without options will be used
    // instead, and the options will be applied in the implementation of result.onResultSent().
    result.setFlags(RESULT_FLAG_OPTION_NOT_HANDLED);
    onLoadChildren(parentId, result);
  }

  /**
   * Called when a {@link MediaBrowserCompat#subscribe} is called.
   *
   * @param id id
   * @param option option
   */
  public void onSubscribe(@Nullable String id, @Nullable Bundle option) {}

  /**
   * Called when a {@link MediaBrowserCompat#unsubscribe} is called.
   *
   * @param id
   */
  public void onUnsubscribe(@Nullable String id) {}

  /**
   * Called to get information about a specific media item.
   *
   * <p>Implementations must call {@link Result#sendResult result.sendResult}. If loading the item
   * will be an expensive operation {@link Result#detach result.detach} may be called before
   * returning from this function, and then {@link Result#sendResult result.sendResult} called when
   * the item has been loaded.
   *
   * <p>When the given {@code itemId} is invalid, implementations must call {@link Result#sendResult
   * result.sendResult} with {@code null}.
   *
   * <p>The default implementation will invoke {@link MediaBrowserCompat.ItemCallback#onError}.
   *
   * @param itemId The id for the specific {@link MediaBrowserCompat.MediaItem}.
   * @param result The Result to send the item to, or null if the id is invalid.
   */
  public void onLoadItem(String itemId, Result<MediaBrowserCompat.MediaItem> result) {
    result.setFlags(RESULT_FLAG_ON_LOAD_ITEM_NOT_IMPLEMENTED);
    result.sendResult(null);
  }

  /**
   * Called to get the search result.
   *
   * <p>Implementations must call {@link Result#sendResult result.sendResult}. If the search will be
   * an expensive operation {@link Result#detach result.detach} may be called before returning from
   * this function, and then {@link Result#sendResult result.sendResult} called when the search has
   * been completed.
   *
   * <p>In case there are no search results, call {@link Result#sendResult result.sendResult} with
   * an empty list. In case there are some errors happened, call {@link Result#sendResult
   * result.sendResult} with {@code null}, which will invoke {@link
   * MediaBrowserCompat.SearchCallback#onError}.
   *
   * <p>The default implementation will invoke {@link MediaBrowserCompat.SearchCallback#onError}.
   *
   * @param query The search query sent from the media browser. It contains keywords separated by
   *     space.
   * @param extras The bundle of service-specific arguments sent from the media browser.
   * @param result The {@link Result} to send the search result.
   */
  public void onSearch(
      String query, @Nullable Bundle extras, Result<List<MediaBrowserCompat.MediaItem>> result) {
    result.setFlags(RESULT_FLAG_ON_SEARCH_NOT_IMPLEMENTED);
    result.sendResult(null);
  }

  /**
   * Called to request a custom action to this service.
   *
   * <p>Implementations must call either {@link Result#sendResult} or {@link Result#sendError}. If
   * the requested custom action will be an expensive operation {@link Result#detach} may be called
   * before returning from this function, and then the service can send the result later when the
   * custom action is completed. Implementation can also call {@link Result#sendProgressUpdate} to
   * send an interim update to the requester.
   *
   * <p>If the requested custom action is not supported by this service, call {@link
   * Result#sendError}. The default implementation will invoke {@link Result#sendError}.
   *
   * @param action The custom action sent from the media browser.
   * @param extras The bundle of service-specific arguments sent from the media browser.
   * @param result The {@link Result} to send the result of the requested custom action.
   * @see MediaBrowserCompat#CUSTOM_ACTION_DOWNLOAD
   * @see MediaBrowserCompat#CUSTOM_ACTION_REMOVE_DOWNLOADED_FILE
   */
  public void onCustomAction(String action, Bundle extras, Result<Bundle> result) {
    result.sendError(null);
  }

  /**
   * Call to set the media session.
   *
   * <p>This should be called as soon as possible during the service's startup. It may only be
   * called once.
   *
   * @param token The token for the service's {@link MediaSessionCompat}.
   */
  public void setSessionToken(MediaSessionCompat.Token token) {
    if (token == null) {
      throw new IllegalArgumentException("Session token may not be null");
    }
    if (mSession != null) {
      throw new IllegalStateException("The session token has already been set");
    }
    mSession = token;
    checkNotNull(mImpl).setSessionToken(token);
  }

  /** Gets the session token, or null if it has not yet been created or if it has been destroyed. */
  @Nullable
  public MediaSessionCompat.Token getSessionToken() {
    return mSession;
  }

  /**
   * Gets the root hints sent from the currently connected {@link MediaBrowserCompat}. The root
   * hints are service-specific arguments included in an optional bundle sent to the media browser
   * service when connecting and retrieving the root id for browsing, or null if none. The contents
   * of this bundle may affect the information returned when browsing.
   *
   * <p>Note that this will return null when connected to {@link android.media.browse.MediaBrowser}
   * and running on API 23 or lower.
   *
   * @throws IllegalStateException If this method is called outside of {@link #onLoadChildren},
   *     {@link #onLoadItem} or {@link #onSearch}.
   * @see MediaBrowserServiceCompat.BrowserRoot#EXTRA_RECENT
   * @see MediaBrowserServiceCompat.BrowserRoot#EXTRA_OFFLINE
   * @see MediaBrowserServiceCompat.BrowserRoot#EXTRA_SUGGESTED
   */
  @Nullable
  public final Bundle getBrowserRootHints() {
    return checkNotNull(mImpl).getBrowserRootHints();
  }

  /**
   * Gets the browser information who sent the current request.
   *
   * @throws IllegalStateException If this method is called outside of {@link #onGetRoot} or {@link
   *     #onLoadChildren} or {@link #onLoadItem}.
   * @see MediaSessionManager#isTrustedForMediaControl(RemoteUserInfo)
   */
  public final RemoteUserInfo getCurrentBrowserInfo() {
    return checkNotNull(mImpl).getCurrentBrowserInfo();
  }

  /**
   * Notifies all connected media browsers that the children of the specified parent id have changed
   * in some way. This will cause browsers to fetch subscribed content again.
   *
   * @param parentId The id of the parent media item whose children changed.
   */
  public void notifyChildrenChanged(String parentId) {
    if (parentId == null) {
      throw new IllegalArgumentException("parentId cannot be null in notifyChildrenChanged");
    }
    checkNotNull(mImpl).notifyChildrenChanged(parentId, null);
  }

  /**
   * Notifies all connected media browsers that the children of the specified parent id have changed
   * in some way. This will cause browsers to fetch subscribed content again.
   *
   * @param parentId The id of the parent media item whose children changed.
   * @param options A bundle of service-specific arguments to send to the media browse. The contents
   *     of this bundle may contain the information about the change.
   */
  public void notifyChildrenChanged(String parentId, Bundle options) {
    if (parentId == null) {
      throw new IllegalArgumentException("parentId cannot be null in notifyChildrenChanged");
    }
    if (options == null) {
      throw new IllegalArgumentException("options cannot be null in notifyChildrenChanged");
    }
    checkNotNull(mImpl).notifyChildrenChanged(parentId, options);
  }

  /**
   * Notifies a connected media browsers that the children of the specified parent id have changed
   * in some way. This will cause browsers to fetch subscribed content again.
   *
   * @param remoteUserInfo to receive this event.
   * @param parentId The id of the parent media item whose children changed.
   * @param options A bundle of service-specific arguments to send to the media browse. The contents
   *     of this bundle may contain the information about the change.
   */
  public void notifyChildrenChanged(
      RemoteUserInfo remoteUserInfo, String parentId, Bundle options) {
    if (remoteUserInfo == null) {
      throw new IllegalArgumentException(
          "remoteUserInfo cannot be null in" + " notifyChildrenChanged");
    }
    if (parentId == null) {
      throw new IllegalArgumentException("parentId cannot be null in notifyChildrenChanged");
    }
    if (options == null) {
      throw new IllegalArgumentException("options cannot be null in notifyChildrenChanged");
    }
    checkNotNull(mImpl).notifyChildrenChanged(remoteUserInfo, parentId, options);
  }

  // Package visibility to avoid synthetic accessor.
  /* package */
  @SuppressLint("RestrictedApi")
  void handleMessageInternal(Message msg) {
    Bundle data = msg.getData();
    switch (msg.what) {
      case CLIENT_MSG_CONNECT:
        {
          Bundle rootHints = data.getBundle(DATA_ROOT_HINTS);
          MediaSessionCompat.ensureClassLoader(rootHints);

          mServiceBinderImpl.connect(
              data.getString(DATA_PACKAGE_NAME),
              data.getInt(DATA_CALLING_PID),
              data.getInt(DATA_CALLING_UID),
              rootHints,
              new ServiceCallbacksCompat(msg.replyTo));
          break;
        }
      case CLIENT_MSG_DISCONNECT:
        mServiceBinderImpl.disconnect(new ServiceCallbacksCompat(msg.replyTo));
        break;
      case CLIENT_MSG_ADD_SUBSCRIPTION:
        {
          Bundle options = data.getBundle(DATA_OPTIONS);
          MediaSessionCompat.ensureClassLoader(options);

          mServiceBinderImpl.addSubscription(
              data.getString(DATA_MEDIA_ITEM_ID),
              data.getBinder(DATA_CALLBACK_TOKEN),
              options,
              new ServiceCallbacksCompat(msg.replyTo));
          break;
        }
      case CLIENT_MSG_REMOVE_SUBSCRIPTION:
        mServiceBinderImpl.removeSubscription(
            data.getString(DATA_MEDIA_ITEM_ID),
            data.getBinder(DATA_CALLBACK_TOKEN),
            new ServiceCallbacksCompat(msg.replyTo));
        break;
      case CLIENT_MSG_GET_MEDIA_ITEM:
        mServiceBinderImpl.getMediaItem(
            data.getString(DATA_MEDIA_ITEM_ID),
            data.getParcelable(DATA_RESULT_RECEIVER),
            new ServiceCallbacksCompat(msg.replyTo));
        break;
      case CLIENT_MSG_REGISTER_CALLBACK_MESSENGER:
        {
          @Nullable Bundle rootHints = data.getBundle(DATA_ROOT_HINTS);
          MediaSessionCompat.ensureClassLoader(rootHints);

          mServiceBinderImpl.registerCallbacks(
              new ServiceCallbacksCompat(msg.replyTo),
              data.getString(DATA_PACKAGE_NAME),
              data.getInt(DATA_CALLING_PID),
              data.getInt(DATA_CALLING_UID),
              rootHints);
          break;
        }
      case CLIENT_MSG_UNREGISTER_CALLBACK_MESSENGER:
        mServiceBinderImpl.unregisterCallbacks(new ServiceCallbacksCompat(msg.replyTo));
        break;
      case CLIENT_MSG_SEARCH:
        {
          @Nullable Bundle searchExtras = data.getBundle(DATA_SEARCH_EXTRAS);
          MediaSessionCompat.ensureClassLoader(searchExtras);

          mServiceBinderImpl.search(
              data.getString(DATA_SEARCH_QUERY),
              searchExtras,
              data.getParcelable(DATA_RESULT_RECEIVER),
              new ServiceCallbacksCompat(msg.replyTo));
          break;
        }
      case CLIENT_MSG_SEND_CUSTOM_ACTION:
        {
          @Nullable Bundle customActionExtras = data.getBundle(DATA_CUSTOM_ACTION_EXTRAS);
          MediaSessionCompat.ensureClassLoader(customActionExtras);

          mServiceBinderImpl.sendCustomAction(
              data.getString(DATA_CUSTOM_ACTION),
              customActionExtras,
              data.getParcelable(DATA_RESULT_RECEIVER),
              new ServiceCallbacksCompat(msg.replyTo));
          break;
        }
      default:
        Log.w(
            TAG,
            "Unhandled message: "
                + msg
                + "\n  Service version: "
                + SERVICE_VERSION_CURRENT
                + "\n  Client version: "
                + msg.arg1);
    }
  }

  /** Return whether the given package is one of the ones that is owned by the uid. */
  @EnsuresNonNullIf(result = true, expression = "#1")
  boolean isValidPackage(@Nullable String pkg, int uid) {
    if (pkg == null) {
      return false;
    }
    final PackageManager pm = getPackageManager();
    final String[] packages = pm.getPackagesForUid(uid);
    if (packages == null) {
      return false;
    }
    final int N = packages.length;
    for (int i = 0; i < N; i++) {
      if (packages[i].equals(pkg)) {
        return true;
      }
    }
    return false;
  }

  /** Save the subscription and if it is a new subscription send the results. */
  void addSubscription(
      @Nullable String id,
      ConnectionRecord connection,
      @Nullable IBinder token,
      @Nullable Bundle options) {
    // Save the subscription
    List<Pair<@NullableType IBinder, @NullableType Bundle>> callbackList =
        connection.subscriptions.get(id);
    if (callbackList == null) {
      callbackList = new ArrayList<>();
    }
    for (Pair<@NullableType IBinder, @NullableType Bundle> callback : callbackList) {
      if (token == callback.first
          && MediaBrowserCompatUtils.areSameOptions(options, callback.second)) {
        return;
      }
    }
    callbackList.add(new Pair<>(token, options));
    connection.subscriptions.put(id, callbackList);
    // send the results
    performLoadChildren(id, connection, options, null);

    mCurConnection = connection;
    onSubscribe(id, options);
    mCurConnection = null;
  }

  /** Remove the subscription. */
  boolean removeSubscription(
      @Nullable String id, ConnectionRecord connection, @Nullable IBinder token) {
    try {
      if (token == null) {
        return connection.subscriptions.remove(id) != null;
      }
      boolean removed = false;
      List<Pair<@NullableType IBinder, @NullableType Bundle>> callbackList =
          connection.subscriptions.get(id);
      if (callbackList != null) {
        Iterator<Pair<@NullableType IBinder, @NullableType Bundle>> iter = callbackList.iterator();
        while (iter.hasNext()) {
          if (token == iter.next().first) {
            removed = true;
            iter.remove();
          }
        }
        if (callbackList.size() == 0) {
          connection.subscriptions.remove(id);
        }
      }
      return removed;
    } finally {
      mCurConnection = connection;
      onUnsubscribe(id);
      mCurConnection = null;
    }
  }

  /**
   * Call onLoadChildren and then send the results back to the connection.
   *
   * <p>Callers must make sure that this connection is still connected.
   */
  void performLoadChildren(
      @Nullable String parentId,
      final ConnectionRecord connection,
      @Nullable Bundle subscribeOptions,
      @Nullable Bundle notifyChildrenChangedOptions) {
    final Result<List<MediaBrowserCompat.MediaItem>> result =
        new Result<List<MediaBrowserCompat.MediaItem>>(parentId) {
          @Override
          void onResultSent(@Nullable List<MediaBrowserCompat.MediaItem> list) {
            if (mConnections.get(checkNotNull(connection.callbacks).asBinder()) != connection) {
              if (DEBUG) {
                Log.d(
                    TAG,
                    "Not sending onLoadChildren result for connection that has"
                        + " been disconnected. pkg="
                        + connection.pkg
                        + " id="
                        + parentId);
              }
              return;
            }

            List<MediaBrowserCompat.MediaItem> filteredList =
                (getFlags() & RESULT_FLAG_OPTION_NOT_HANDLED) != 0
                    ? applyOptions(list, subscribeOptions)
                    : list;
            try {
              connection.callbacks.onLoadChildren(
                  parentId, filteredList, subscribeOptions, notifyChildrenChangedOptions);
            } catch (RemoteException ex) {
              // The other side is in the process of crashing.
              Log.w(
                  TAG,
                  "Calling onLoadChildren() failed for id="
                      + parentId
                      + " package="
                      + connection.pkg);
            }
          }
        };

    mCurConnection = connection;
    if (subscribeOptions == null) {
      onLoadChildren(parentId, result);
    } else {
      onLoadChildren(parentId, result, subscribeOptions);
    }
    mCurConnection = null;

    if (!result.isDone()) {
      throw new IllegalStateException(
          "onLoadChildren must call detach() or sendResult()"
              + " before returning for package="
              + connection.pkg
              + " id="
              + parentId);
    }
  }

  @Nullable
  List<MediaBrowserCompat.MediaItem> applyOptions(
      @Nullable List<MediaBrowserCompat.MediaItem> list, @Nullable Bundle options) {
    if (list == null) {
      return null;
    }
    if (options == null) {
      return list;
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

  void performLoadItem(String itemId, ConnectionRecord connection, final ResultReceiver receiver) {
    final Result<MediaBrowserCompat.MediaItem> result =
        new Result<MediaBrowserCompat.MediaItem>(itemId) {
          @SuppressLint("RestrictedApi")
          @Override
          void onResultSent(@Nullable MediaBrowserCompat.MediaItem item) {
            if ((getFlags() & RESULT_FLAG_ON_LOAD_ITEM_NOT_IMPLEMENTED) != 0) {
              receiver.send(RESULT_ERROR, null);
              return;
            }
            Bundle bundle = new Bundle();
            bundle.putParcelable(
                KEY_MEDIA_ITEM,
                LegacyParcelableUtil.convert(
                    item, android.support.v4.media.MediaBrowserCompat.MediaItem.CREATOR));
            receiver.send(RESULT_OK, bundle);
          }
        };

    mCurConnection = connection;
    onLoadItem(itemId, result);
    mCurConnection = null;

    if (!result.isDone()) {
      throw new IllegalStateException(
          "onLoadItem must call detach() or sendResult()" + " before returning for id=" + itemId);
    }
  }

  void performSearch(
      String query, @Nullable Bundle extras, ConnectionRecord connection, ResultReceiver receiver) {
    final Result<List<MediaBrowserCompat.MediaItem>> result =
        new Result<List<MediaBrowserCompat.MediaItem>>(query) {
          @SuppressLint("RestrictedApi")
          @Override
          void onResultSent(@Nullable List<MediaBrowserCompat.MediaItem> items) {
            if ((getFlags() & RESULT_FLAG_ON_SEARCH_NOT_IMPLEMENTED) != 0 || items == null) {
              receiver.send(RESULT_ERROR, null);
              return;
            }
            Bundle bundle = new Bundle();
            List<android.support.v4.media.MediaBrowserCompat.MediaItem> legacyPackageList =
                LegacyParcelableUtil.convertList(
                    items, android.support.v4.media.MediaBrowserCompat.MediaItem.CREATOR);
            bundle.putParcelableArray(
                KEY_SEARCH_RESULTS,
                legacyPackageList.toArray(
                    new android.support.v4.media.MediaBrowserCompat.MediaItem[0]));
            receiver.send(RESULT_OK, bundle);
          }
        };

    mCurConnection = connection;
    onSearch(query, extras, result);
    mCurConnection = null;

    if (!result.isDone()) {
      throw new IllegalStateException(
          "onSearch must call detach() or sendResult()" + " before returning for query=" + query);
    }
  }

  void performCustomAction(
      String action,
      @Nullable Bundle extras,
      ConnectionRecord connection,
      ResultReceiver receiver) {
    @SuppressLint("RestrictedApi")
    final Result<Bundle> result =
        new Result<Bundle>(action) {
          @Override
          void onResultSent(@Nullable Bundle result) {
            receiver.send(RESULT_OK, result);
          }

          @Override
          void onProgressUpdateSent(@Nullable Bundle data) {
            receiver.send(RESULT_PROGRESS_UPDATE, data);
          }

          @Override
          void onErrorSent(@Nullable Bundle data) {
            receiver.send(RESULT_ERROR, data);
          }
        };

    mCurConnection = connection;
    onCustomAction(action, extras == null ? Bundle.EMPTY : extras, result);
    mCurConnection = null;

    if (!result.isDone()) {
      throw new IllegalStateException(
          "onCustomAction must call detach() or sendResult() or "
              + "sendError() before returning for action="
              + action
              + " extras="
              + extras);
    }
  }

  /**
   * Contains information that the browser service needs to send to the client when first connected.
   */
  public static final class BrowserRoot {
    /**
     * The lookup key for a boolean that indicates whether the browser service should return a
     * browser root for recently played media items.
     *
     * <p>When creating a media browser for a given media browser service, this key can be supplied
     * as a root hint for retrieving media items that are recently played. If the media browser
     * service can provide such media items, the implementation must return the key in the root hint
     * when {@link #onGetRoot(String, int, Bundle)} is called back.
     *
     * <p>The root hint may contain multiple keys.
     *
     * @see #EXTRA_OFFLINE
     * @see #EXTRA_SUGGESTED
     */
    public static final String EXTRA_RECENT = "android.service.media.extra.RECENT";

    /**
     * The lookup key for a boolean that indicates whether the browser service should return a
     * browser root for offline media items.
     *
     * <p>When creating a media browser for a given media browser service, this key can be supplied
     * as a root hint for retrieving media items that are can be played without an internet
     * connection. If the media browser service can provide such media items, the implementation
     * must return the key in the root hint when {@link #onGetRoot(String, int, Bundle)} is called
     * back.
     *
     * <p>The root hint may contain multiple keys.
     *
     * @see #EXTRA_RECENT
     * @see #EXTRA_SUGGESTED
     */
    public static final String EXTRA_OFFLINE = "android.service.media.extra.OFFLINE";

    /**
     * The lookup key for a boolean that indicates whether the browser service should return a
     * browser root for suggested media items.
     *
     * <p>When creating a media browser for a given media browser service, this key can be supplied
     * as a root hint for retrieving the media items suggested by the media browser service. The
     * list of media items passed in {@link
     * MediaBrowserCompat.SubscriptionCallback#onChildrenLoaded(String, List)} is considered ordered
     * by relevance, first being the top suggestion. If the media browser service can provide such
     * media items, the implementation must return the key in the root hint when {@link
     * #onGetRoot(String, int, Bundle)} is called back.
     *
     * <p>The root hint may contain multiple keys.
     *
     * @see #EXTRA_RECENT
     * @see #EXTRA_OFFLINE
     */
    public static final String EXTRA_SUGGESTED = "android.service.media.extra.SUGGESTED";

    /**
     * The lookup key for a string that indicates specific keywords which will be considered when
     * the browser service suggests media items.
     *
     * <p>When creating a media browser for a given media browser service, this key can be supplied
     * as a root hint together with {@link #EXTRA_SUGGESTED} for retrieving suggested media items
     * related with the keywords. The list of media items passed in {@link
     * android.media.browse.MediaBrowser.SubscriptionCallback#onChildrenLoaded(String, List)} is
     * considered ordered by relevance, first being the top suggestion. If the media browser service
     * can provide such media items, the implementation must return the key in the root hint when
     * {@link #onGetRoot(String, int, Bundle)} is called back.
     *
     * <p>The root hint may contain multiple keys.
     *
     * @see #EXTRA_RECENT
     * @see #EXTRA_OFFLINE
     * @see #EXTRA_SUGGESTED
     * @deprecated The search functionality is now supported by the methods {@link
     *     MediaBrowserCompat#search} and {@link #onSearch}. Use those methods instead.
     */
    @Deprecated
    public static final String EXTRA_SUGGESTION_KEYWORDS =
        "android.service.media.extra.SUGGESTION_KEYWORDS";

    private final String mRootId;
    @Nullable private final Bundle mExtras;

    /**
     * Constructs a browser root.
     *
     * @param rootId The root id for browsing.
     * @param extras Any extras about the browser service.
     */
    public BrowserRoot(String rootId, @Nullable Bundle extras) {
      if (rootId == null) {
        throw new IllegalArgumentException(
            "The root id in BrowserRoot cannot be null. " + "Use null for BrowserRoot instead");
      }
      mRootId = rootId;
      mExtras = extras;
    }

    /** Gets the root id for browsing. */
    public String getRootId() {
      return mRootId;
    }

    /** Gets any extras about the browser service. */
    @Nullable
    public Bundle getExtras() {
      return mExtras;
    }
  }
}
