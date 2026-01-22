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

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;
import androidx.media3.common.util.UnstableApi;

/**
 * Provides support for interacting with {@link MediaSessionCompat media sessions} that applications
 * have published to express their ongoing media playback state.
 *
 * @see MediaSessionCompat
 * @see MediaControllerCompat
 */
@UnstableApi
@RestrictTo(LIBRARY)
public final class MediaSessionManager {
  static final String TAG = "MediaSessionManager";
  static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

  private static final Object sLock = new Object();
  @Nullable private static volatile MediaSessionManager sSessionManager;

  MediaSessionManagerImpl mImpl;

  /**
   * Gets an instance of the media session manager associated with the context.
   *
   * @return The MediaSessionManager instance for this context.
   */
  public static MediaSessionManager getSessionManager(Context context) {
    if (context == null) {
      throw new IllegalArgumentException("context cannot be null");
    }
    synchronized (sLock) {
      if (sSessionManager == null) {
        sSessionManager = new MediaSessionManager(context.getApplicationContext());
      }
      return sSessionManager;
    }
  }

  private MediaSessionManager(Context context) {
    if (Build.VERSION.SDK_INT >= 28) {
      mImpl = new MediaSessionManagerImplApi28(context);
    } else if (Build.VERSION.SDK_INT >= 21) {
      mImpl = new MediaSessionManagerImplApi21(context);
    } else {
      mImpl = new MediaSessionManagerImplBase(context);
    }
  }

  /**
   * Checks whether the remote user is a trusted app.
   *
   * <p>An app is trusted if the app holds the android.Manifest.permission.MEDIA_CONTENT_CONTROL
   * permission or has an enabled notification listener.
   *
   * @param userInfo The remote user info from either {@link
   *     MediaSessionCompat#getCurrentControllerInfo()} and {@link
   *     MediaBrowserServiceCompat#getCurrentBrowserInfo()}.
   * @return {@code true} if the remote user is trusted and its package name matches with the UID.
   *     {@code false} otherwise.
   */
  public boolean isTrustedForMediaControl(RemoteUserInfo userInfo) {
    if (userInfo == null) {
      throw new IllegalArgumentException("userInfo should not be null");
    }
    return mImpl.isTrustedForMediaControl(userInfo.mImpl);
  }

  Context getContext() {
    return mImpl.getContext();
  }

  interface MediaSessionManagerImpl {
    Context getContext();

    boolean isTrustedForMediaControl(RemoteUserInfoImpl userInfo);
  }

  interface RemoteUserInfoImpl {
    String getPackageName();

    int getPid();

    int getUid();
  }

  /**
   * Information of a remote user of {@link MediaSessionCompat} or {@link
   * MediaBrowserServiceCompat}. This can be used to decide whether the remote user is trusted app,
   * and also differentiate caller of {@link MediaSessionCompat} and {@link
   * MediaBrowserServiceCompat} callbacks.
   *
   * <p>See {@link #equals(Object)} to take a look at how it differentiate media controller.
   *
   * @see #isTrustedForMediaControl(RemoteUserInfo)
   */
  public static final class RemoteUserInfo {
    /**
     * Used by {@link #getPackageName()} when the session is connected to the legacy controller
     * whose exact package name cannot be obtained.
     */
    public static final String LEGACY_CONTROLLER = "android.media.session.MediaController";

    /** Represents an unknown pid of an application. */
    public static final int UNKNOWN_PID = -1;

    /** Represents an unknown uid of an application. */
    public static final int UNKNOWN_UID = -1;

    RemoteUserInfoImpl mImpl;

    /**
     * Public constructor.
     *
     * <p>Can be used for {@link MediaSessionManager#isTrustedForMediaControl(RemoteUserInfo)}}.
     *
     * @param packageName package name of the remote user
     * @param pid pid of the remote user
     * @param uid uid of the remote user
     * @throws IllegalArgumentException if package name is empty
     */
    public RemoteUserInfo(@Nullable String packageName, int pid, int uid) {
      if (packageName == null) {
        throw new NullPointerException("package shouldn't be null");
      } else if (TextUtils.isEmpty(packageName)) {
        throw new IllegalArgumentException("packageName should be nonempty");
      }
      if (Build.VERSION.SDK_INT >= 28) {
        mImpl = new MediaSessionManagerImplApi28.RemoteUserInfoImplApi28(packageName, pid, uid);
      } else {
        // Note: We need to include IBinder to distinguish controllers in a process.
        mImpl = new MediaSessionManagerImplBase.RemoteUserInfoImplBase(packageName, pid, uid);
      }
    }

    /**
     * Public constructor for internal uses.
     *
     * <p>Internal code MUST use this on SDK >= 28 to distinguish individual RemoteUserInfos in a
     * process.
     *
     * @param remoteUserInfo Framework RemoteUserInfo
     * @throws IllegalArgumentException if package name is empty
     */
    @RequiresApi(28)
    public RemoteUserInfo(android.media.session.MediaSessionManager.RemoteUserInfo remoteUserInfo) {
      // Framework RemoteUserInfo doesn't ensure non-null nor non-empty package name,
      // so ensure package name here instead.
      String packageName =
          MediaSessionManagerImplApi28.RemoteUserInfoImplApi28.getPackageName(remoteUserInfo);
      if (packageName == null) {
        throw new NullPointerException("package shouldn't be null");
      } else if (TextUtils.isEmpty(packageName)) {
        throw new IllegalArgumentException("packageName should be nonempty");
      }
      mImpl = new MediaSessionManagerImplApi28.RemoteUserInfoImplApi28(remoteUserInfo);
    }

    /**
     * @return package name of the controller. Can be {@link #LEGACY_CONTROLLER} if the package name
     *     cannot be obtained.
     */
    public String getPackageName() {
      return mImpl.getPackageName();
    }

    /**
     * @return pid of the controller. Can be a negative value if the pid cannot be obtained.
     */
    public int getPid() {
      return mImpl.getPid();
    }

    /**
     * @return uid of the controller. Can be a negative value if the uid cannot be obtained.
     */
    public int getUid() {
      return mImpl.getUid();
    }

    /**
     * Returns equality of two RemoteUserInfo by comparing their package name, UID, and PID.
     *
     * <p>On P and before (API &le; 28), two RemoteUserInfo objects equal if following conditions
     * are met:
     *
     * <ol>
     *   <li>UID and package name are the same
     *   <li>One of the RemoteUserInfo's PID is UNKNOWN_PID or both of RemoteUserInfo's PID are the
     *       same
     * </ol>
     *
     * @param obj the reference object with which to compare.
     * @return {@code true} if equals, {@code false} otherwise
     */
    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof RemoteUserInfo)) {
        return false;
      }
      return mImpl.equals(((RemoteUserInfo) obj).mImpl);
    }

    @Override
    public int hashCode() {
      return mImpl.hashCode();
    }
  }

  private static class MediaSessionManagerImplBase
      implements MediaSessionManager.MediaSessionManagerImpl {
    private static final String TAG = MediaSessionManager.TAG;
    private static final boolean DEBUG = MediaSessionManager.DEBUG;

    private static final String PERMISSION_STATUS_BAR_SERVICE =
        "android.permission.STATUS_BAR_SERVICE";
    private static final String PERMISSION_MEDIA_CONTENT_CONTROL =
        "android.permission.MEDIA_CONTENT_CONTROL";
    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";

    Context mContext;
    ContentResolver mContentResolver;

    MediaSessionManagerImplBase(Context context) {
      mContext = context;
      mContentResolver = mContext.getContentResolver();
    }

    @Override
    public Context getContext() {
      return mContext;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isTrustedForMediaControl(MediaSessionManager.RemoteUserInfoImpl userInfo) {
      try {
        ApplicationInfo applicationInfo =
            mContext.getPackageManager().getApplicationInfo(userInfo.getPackageName(), 0);
        if (applicationInfo == null) {
          return false;
        }
      } catch (PackageManager.NameNotFoundException e) {
        if (DEBUG) {
          Log.d(TAG, "Package " + userInfo.getPackageName() + " doesn't exist");
        }
        return false;
      }
      return isPermissionGranted(userInfo, PERMISSION_STATUS_BAR_SERVICE)
          || isPermissionGranted(userInfo, PERMISSION_MEDIA_CONTENT_CONTROL)
          || userInfo.getUid() == Process.SYSTEM_UID
          || isEnabledNotificationListener(userInfo);
    }

    private boolean isPermissionGranted(
        MediaSessionManager.RemoteUserInfoImpl userInfo, String permission) {
      if (userInfo.getPid() < 0) {
        // This may happen for the MediaBrowserServiceCompat#onGetRoot().
        return mContext.getPackageManager().checkPermission(permission, userInfo.getPackageName())
            == PackageManager.PERMISSION_GRANTED;
      }
      return mContext.checkPermission(permission, userInfo.getPid(), userInfo.getUid())
          == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * This checks if the component is an enabled notification listener for the specified user.
     * Enabled components may only operate on behalf of the user they're running as.
     *
     * @return True if the component is enabled, false otherwise
     */
    @SuppressWarnings("StringSplitter")
    boolean isEnabledNotificationListener(MediaSessionManager.RemoteUserInfoImpl userInfo) {
      final String enabledNotifListeners =
          Settings.Secure.getString(mContentResolver, ENABLED_NOTIFICATION_LISTENERS);
      if (enabledNotifListeners != null) {
        final String[] components = enabledNotifListeners.split(":");
        for (int i = 0; i < components.length; i++) {
          final ComponentName component = ComponentName.unflattenFromString(components[i]);
          if (component != null) {
            if (component.getPackageName().equals(userInfo.getPackageName())) {
              return true;
            }
          }
        }
      }
      return false;
    }

    static class RemoteUserInfoImplBase implements MediaSessionManager.RemoteUserInfoImpl {
      private String mPackageName;
      private int mPid;
      private int mUid;

      RemoteUserInfoImplBase(String packageName, int pid, int uid) {
        mPackageName = packageName;
        mPid = pid;
        mUid = uid;
      }

      @Override
      public String getPackageName() {
        return mPackageName;
      }

      @Override
      public int getPid() {
        return mPid;
      }

      @Override
      public int getUid() {
        return mUid;
      }

      @Override
      public boolean equals(@Nullable Object obj) {
        if (this == obj) {
          return true;
        }
        if (!(obj instanceof RemoteUserInfoImplBase)) {
          return false;
        }
        RemoteUserInfoImplBase otherUserInfo = (RemoteUserInfoImplBase) obj;
        if (mPid < 0 || otherUserInfo.mPid < 0) {
          // Only compare package name and UID when PID is unknown.
          return TextUtils.equals(mPackageName, otherUserInfo.mPackageName)
              && mUid == otherUserInfo.mUid;
        }
        return TextUtils.equals(mPackageName, otherUserInfo.mPackageName)
            && mPid == otherUserInfo.mPid
            && mUid == otherUserInfo.mUid;
      }

      @Override
      public int hashCode() {
        return ObjectsCompat.hash(mPackageName, mUid);
      }
    }
  }

  @RequiresApi(21)
  private static class MediaSessionManagerImplApi21 extends MediaSessionManagerImplBase {
    MediaSessionManagerImplApi21(Context context) {
      super(context);
      mContext = context;
    }

    @Override
    public boolean isTrustedForMediaControl(MediaSessionManager.RemoteUserInfoImpl userInfo) {

      return hasMediaControlPermission(userInfo) || super.isTrustedForMediaControl(userInfo);
    }

    /** Checks the caller has android.Manifest.permission.MEDIA_CONTENT_CONTROL permission. */
    private boolean hasMediaControlPermission(MediaSessionManager.RemoteUserInfoImpl userInfo) {
      return getContext()
              .checkPermission(
                  android.Manifest.permission.MEDIA_CONTENT_CONTROL,
                  userInfo.getPid(),
                  userInfo.getUid())
          == PackageManager.PERMISSION_GRANTED;
    }
  }

  @RequiresApi(28)
  private static final class MediaSessionManagerImplApi28 extends MediaSessionManagerImplApi21 {
    @Nullable android.media.session.MediaSessionManager mObject;

    MediaSessionManagerImplApi28(Context context) {
      super(context);
      mObject =
          (android.media.session.MediaSessionManager)
              context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @Override
    public boolean isTrustedForMediaControl(MediaSessionManager.RemoteUserInfoImpl userInfo) {
      // Don't use framework's isTrustedForMediaControl().
      // In P, framework's isTrustedForMediaControl() checks whether the UID, PID,
      // and package name match. In MediaSession/MediaController, Context#getPackageName() is
      // used by MediaController to tell MediaSession the package name.
      // However, UID, PID and Context#getPackageName() may not match if a activity/service runs
      // on the another app's process by specifying android:process in the AndroidManifest.xml.
      // In that case, this check will always fail.
      // Alternative way is to use Context#getOpPackageName() for sending the package name,
      // but it's hidden so we cannot use it.
      return super.isTrustedForMediaControl(userInfo);
    }

    /**
     * This extends {@link RemoteUserInfoImplBase} on purpose not to use frameworks' equals() and
     * hashCode() implementation for two reasons:
     *
     * <p>1. To override PID checks when one of them are unknown. PID can be unknown between
     * MediaBrowserCompat / MediaBrowserServiceCompat 2. To skip checking hidden binder. Framework's
     * {@link android.media.session.MediaSessionManager.RemoteUserInfo} also checks internal binder
     * to distinguish multiple {@link android.media.session.MediaController} and {@link
     * android.media.browse.MediaBrowser} in a process. However, when the binders in both
     * RemoteUserInfos are {@link null}, framework's equal() specially handles the case and returns
     * {@code false}. This cause two issues that we need to workaround. Issue a) RemoteUserInfos
     * created by key events are considered as all different. issue b) RemoteUserInfos created with
     * public constructors are considers as all different.
     */
    @RequiresApi(28)
    private static final class RemoteUserInfoImplApi28 extends RemoteUserInfoImplBase {
      final android.media.session.MediaSessionManager.RemoteUserInfo mObject;

      RemoteUserInfoImplApi28(String packageName, int pid, int uid) {
        super(packageName, pid, uid);
        mObject =
            new android.media.session.MediaSessionManager.RemoteUserInfo(packageName, pid, uid);
      }

      RemoteUserInfoImplApi28(
          android.media.session.MediaSessionManager.RemoteUserInfo remoteUserInfo) {
        super(remoteUserInfo.getPackageName(), remoteUserInfo.getPid(), remoteUserInfo.getUid());
        mObject = remoteUserInfo;
      }

      static String getPackageName(
          android.media.session.MediaSessionManager.RemoteUserInfo remoteUserInfo) {
        return remoteUserInfo.getPackageName();
      }
    }
  }
}
