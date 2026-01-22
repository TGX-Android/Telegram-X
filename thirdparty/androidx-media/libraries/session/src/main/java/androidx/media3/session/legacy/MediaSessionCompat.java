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
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.session.legacy.MediaSessionManager.RemoteUserInfo.LEGACY_CONTROLLER;
import static androidx.media3.session.legacy.MediaSessionManager.RemoteUserInfo.UNKNOWN_PID;
import static androidx.media3.session.legacy.MediaSessionManager.RemoteUserInfo.UNKNOWN_UID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.MediaMetadataEditor;
import android.media.MediaMetadataRetriever;
import android.media.Rating;
import android.media.RemoteControlClient;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.media3.common.audio.AudioManagerCompat;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.legacy.MediaSessionManager.RemoteUserInfo;
import androidx.versionedparcelable.ParcelUtils;
import androidx.versionedparcelable.VersionedParcelable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Allows interaction with media controllers, volume keys, media buttons, and transport controls.
 *
 * <p>A MediaSession should be created when an app wants to publish media playback information or
 * handle media keys. In general an app only needs one session for all playback, though multiple
 * sessions can be created to provide finer grain controls of media.
 *
 * <p>Once a session is created the owner of the session may pass its {@link #getSessionToken()
 * session token} to other processes to allow them to create a {@link MediaControllerCompat} to
 * interact with the session.
 *
 * <p>To receive commands, media keys, and other events a {@link Callback} must be set with {@link
 * #setCallback(Callback)}.
 *
 * <p>When an app is finished performing playback it must call {@link #release()} to clean up the
 * session and notify any controllers.
 *
 * <p>MediaSessionCompat objects are not thread safe and all calls should be made from the same
 * thread.
 *
 * <p>This is a helper for accessing features in {@link android.media.session.MediaSession}
 * introduced after API level 4 in a backwards compatible fashion.
 *
 * <h2>Developer Guides</h2>
 *
 * <p>For information about building your media application, read the <a
 * href="{@docRoot}guide/topics/media-apps/index.html">Media Apps</a> developer guide.
 */
@UnstableApi
@RestrictTo(LIBRARY)
public class MediaSessionCompat {
  static final String TAG = "MediaSessionCompat";

  private final MediaSessionImpl mImpl;
  private final MediaControllerCompat mController;
  private final ArrayList<OnActiveChangeListener> mActiveListeners = new ArrayList<>();

  @IntDef(
      flag = true,
      value = {
        FLAG_HANDLES_MEDIA_BUTTONS,
        FLAG_HANDLES_TRANSPORT_CONTROLS,
        FLAG_HANDLES_QUEUE_COMMANDS
      })
  @Retention(RetentionPolicy.SOURCE)
  private @interface SessionFlags {}

  /**
   * Sets this flag on the session to indicate that it can handle media button events.
   *
   * @deprecated This flag is no longer used. All media sessions are expected to handle media button
   *     events now. For backward compatibility, this flag will be always set.
   */
  @Deprecated public static final int FLAG_HANDLES_MEDIA_BUTTONS = 1 << 0;

  /**
   * Sets this flag on the session to indicate that it handles transport control commands through
   * its {@link Callback}.
   *
   * @deprecated This flag is no longer used. All media sessions are expected to handle transport
   *     controls now. For backward compatibility, this flag will be always set.
   */
  @Deprecated public static final int FLAG_HANDLES_TRANSPORT_CONTROLS = 1 << 1;

  /**
   * Sets this flag on the session to indicate that it handles queue management commands through its
   * {@link Callback}.
   */
  public static final int FLAG_HANDLES_QUEUE_COMMANDS = 1 << 2;

  /**
   * Predefined custom action to flag the media that is currently playing as inappropriate.
   *
   * @see Callback#onCustomAction
   */
  public static final String ACTION_FLAG_AS_INAPPROPRIATE =
      "android.support.v4.media.session.action.FLAG_AS_INAPPROPRIATE";

  /**
   * Predefined custom action to skip the advertisement that is currently playing.
   *
   * @see Callback#onCustomAction
   */
  public static final String ACTION_SKIP_AD = "android.support.v4.media.session.action.SKIP_AD";

  /**
   * Predefined custom action to follow an artist, album, or playlist. The extra bundle must have
   * {@link #ARGUMENT_MEDIA_ATTRIBUTE} to indicate the type of the follow action. The bundle can
   * also have an optional string argument, {@link #ARGUMENT_MEDIA_ATTRIBUTE_VALUE}, to specify the
   * target to follow (e.g., the name of the artist to follow). If this argument is omitted, the
   * currently playing media will be the target of the action. Thus, the session must perform the
   * follow action with the current metadata. If there's no specified attribute in the current
   * metadata, the controller must not omit this argument.
   *
   * @see #ARGUMENT_MEDIA_ATTRIBUTE
   * @see #ARGUMENT_MEDIA_ATTRIBUTE_VALUE
   * @see Callback#onCustomAction
   */
  public static final String ACTION_FOLLOW = "android.support.v4.media.session.action.FOLLOW";

  /**
   * Predefined custom action to unfollow an artist, album, or playlist. The extra bundle must have
   * {@link #ARGUMENT_MEDIA_ATTRIBUTE} to indicate the type of the unfollow action. The bundle can
   * also have an optional string argument, {@link #ARGUMENT_MEDIA_ATTRIBUTE_VALUE}, to specify the
   * target to unfollow (e.g., the name of the artist to unfollow). If this argument is omitted, the
   * currently playing media will be the target of the action. Thus, the session must perform the
   * unfollow action with the current metadata. If there's no specified attribute in the current
   * metadata, the controller must not omit this argument.
   *
   * @see #ARGUMENT_MEDIA_ATTRIBUTE
   * @see #ARGUMENT_MEDIA_ATTRIBUTE_VALUE
   * @see Callback#onCustomAction
   */
  public static final String ACTION_UNFOLLOW = "android.support.v4.media.session.action.UNFOLLOW";

  /**
   * Argument to indicate the media attribute. It should be one of the following:
   *
   * <ul>
   *   <li>{@link #MEDIA_ATTRIBUTE_ARTIST}
   *   <li>{@link #MEDIA_ATTRIBUTE_PLAYLIST}
   *   <li>{@link #MEDIA_ATTRIBUTE_ALBUM}
   * </ul>
   */
  public static final String ARGUMENT_MEDIA_ATTRIBUTE =
      "android.support.v4.media.session.ARGUMENT_MEDIA_ATTRIBUTE";

  /**
   * String argument to indicate the value of the media attribute (e.g., the name of the artist).
   */
  public static final String ARGUMENT_MEDIA_ATTRIBUTE_VALUE =
      "android.support.v4.media.session.ARGUMENT_MEDIA_ATTRIBUTE_VALUE";

  /**
   * The value of {@link #ARGUMENT_MEDIA_ATTRIBUTE} indicating the artist.
   *
   * @see #ARGUMENT_MEDIA_ATTRIBUTE
   */
  public static final int MEDIA_ATTRIBUTE_ARTIST = 0;

  /**
   * The value of {@link #ARGUMENT_MEDIA_ATTRIBUTE} indicating the album.
   *
   * @see #ARGUMENT_MEDIA_ATTRIBUTE
   */
  public static final int MEDIA_ATTRIBUTE_ALBUM = 1;

  /**
   * The value of {@link #ARGUMENT_MEDIA_ATTRIBUTE} indicating the playlist.
   *
   * @see #ARGUMENT_MEDIA_ATTRIBUTE
   */
  public static final int MEDIA_ATTRIBUTE_PLAYLIST = 2;

  /** Custom action to invoke playFromUri() for the forward compatibility. */
  public static final String ACTION_PLAY_FROM_URI =
      "android.support.v4.media.session.action.PLAY_FROM_URI";

  /** Custom action to invoke prepare() for the forward compatibility. */
  public static final String ACTION_PREPARE = "android.support.v4.media.session.action.PREPARE";

  /** Custom action to invoke prepareFromMediaId() for the forward compatibility. */
  public static final String ACTION_PREPARE_FROM_MEDIA_ID =
      "android.support.v4.media.session.action.PREPARE_FROM_MEDIA_ID";

  /** Custom action to invoke prepareFromSearch() for the forward compatibility. */
  public static final String ACTION_PREPARE_FROM_SEARCH =
      "android.support.v4.media.session.action.PREPARE_FROM_SEARCH";

  /** Custom action to invoke prepareFromUri() for the forward compatibility. */
  public static final String ACTION_PREPARE_FROM_URI =
      "android.support.v4.media.session.action.PREPARE_FROM_URI";

  /** Custom action to invoke setCaptioningEnabled() for the forward compatibility. */
  public static final String ACTION_SET_CAPTIONING_ENABLED =
      "android.support.v4.media.session.action.SET_CAPTIONING_ENABLED";

  /** Custom action to invoke setRepeatMode() for the forward compatibility. */
  public static final String ACTION_SET_REPEAT_MODE =
      "android.support.v4.media.session.action.SET_REPEAT_MODE";

  /** Custom action to invoke setShuffleMode() for the forward compatibility. */
  public static final String ACTION_SET_SHUFFLE_MODE =
      "android.support.v4.media.session.action.SET_SHUFFLE_MODE";

  /** Custom action to invoke setRating() with extra fields. */
  public static final String ACTION_SET_RATING =
      "android.support.v4.media.session.action.SET_RATING";

  /** Custom action to invoke setPlaybackSpeed() with extra fields. */
  public static final String ACTION_SET_PLAYBACK_SPEED =
      "android.support.v4.media.session.action.SET_PLAYBACK_SPEED";

  /** Argument for use with {@link #ACTION_PREPARE_FROM_MEDIA_ID} indicating media id to play. */
  public static final String ACTION_ARGUMENT_MEDIA_ID =
      "android.support.v4.media.session.action.ARGUMENT_MEDIA_ID";

  /** Argument for use with {@link #ACTION_PREPARE_FROM_SEARCH} indicating search query. */
  public static final String ACTION_ARGUMENT_QUERY =
      "android.support.v4.media.session.action.ARGUMENT_QUERY";

  /**
   * Argument for use with {@link #ACTION_PREPARE_FROM_URI} and {@link #ACTION_PLAY_FROM_URI}
   * indicating URI to play.
   */
  public static final String ACTION_ARGUMENT_URI =
      "android.support.v4.media.session.action.ARGUMENT_URI";

  /** Argument for use with {@link #ACTION_SET_RATING} indicating the rate to be set. */
  public static final String ACTION_ARGUMENT_RATING =
      "android.support.v4.media.session.action.ARGUMENT_RATING";

  /** Argument for use with {@link #ACTION_SET_PLAYBACK_SPEED} indicating the speed to be set. */
  public static final String ACTION_ARGUMENT_PLAYBACK_SPEED =
      "android.support.v4.media.session.action.ARGUMENT_PLAYBACK_SPEED";

  /** Argument for use with various actions indicating extra bundle. */
  public static final String ACTION_ARGUMENT_EXTRAS =
      "android.support.v4.media.session.action.ARGUMENT_EXTRAS";

  /**
   * Argument for use with {@link #ACTION_SET_CAPTIONING_ENABLED} indicating whether captioning is
   * enabled.
   */
  public static final String ACTION_ARGUMENT_CAPTIONING_ENABLED =
      "android.support.v4.media.session.action.ARGUMENT_CAPTIONING_ENABLED";

  /** Argument for use with {@link #ACTION_SET_REPEAT_MODE} indicating repeat mode. */
  public static final String ACTION_ARGUMENT_REPEAT_MODE =
      "android.support.v4.media.session.action.ARGUMENT_REPEAT_MODE";

  /** Argument for use with {@link #ACTION_SET_SHUFFLE_MODE} indicating shuffle mode. */
  public static final String ACTION_ARGUMENT_SHUFFLE_MODE =
      "android.support.v4.media.session.action.ARGUMENT_SHUFFLE_MODE";

  /** */
  public static final String KEY_TOKEN = "android.support.v4.media.session.TOKEN";

  /** */
  public static final String KEY_EXTRA_BINDER = "android.support.v4.media.session.EXTRA_BINDER";

  /** */
  public static final String KEY_SESSION2_TOKEN = "android.support.v4.media.session.SESSION_TOKEN2";

  // Maximum size of the bitmap in dp.
  private static final int MAX_BITMAP_SIZE_IN_DP = 320;

  private static final String DATA_CALLING_PACKAGE = "data_calling_pkg";
  private static final String DATA_CALLING_PID = "data_calling_pid";
  private static final String DATA_CALLING_UID = "data_calling_uid";
  private static final String DATA_EXTRAS = "data_extras";

  // Maximum size of the bitmap in px. It shouldn't be changed.
  static int sMaxBitmapSize;

  /**
   * Creates a new session. You must call {@link #release()} when finished with the session.
   *
   * <p>The session will automatically be registered with the system but will not be published until
   * {@link #setActive(boolean) setActive(true)} is called.
   *
   * <p>For API 20 or earlier, note that a media button receiver is required for handling {@link
   * Intent#ACTION_MEDIA_BUTTON}. This constructor will attempt to find an appropriate {@link
   * BroadcastReceiver} from your manifest. See {@link MediaButtonReceiver} for more details.
   *
   * @param context The context to use to create the session.
   * @param tag A short name for debugging purposes.
   */
  public MediaSessionCompat(Context context, String tag) {
    this(context, tag, null, null);
  }

  /**
   * Creates a new session with a specified media button receiver (a component name and/or a pending
   * intent). You must call {@link #release()} when finished with the session.
   *
   * <p>The session will automatically be registered with the system but will not be published until
   * {@link #setActive(boolean) setActive(true)} is called.
   *
   * <p>For API 20 or earlier, note that a media button receiver is required for handling {@link
   * Intent#ACTION_MEDIA_BUTTON}. This constructor will attempt to find an appropriate {@link
   * BroadcastReceiver} from your manifest if it's not specified. See {@link MediaButtonReceiver}
   * for more details.
   *
   * @param context The context to use to create the session.
   * @param tag A short name for debugging purposes.
   * @param mbrComponent The component name for your media button receiver.
   * @param mbrIntent The PendingIntent for your receiver component that handles media button
   *     events. This is optional and will be used on between {@link
   *     android.os.Build.VERSION_CODES#JELLY_BEAN_MR2} and {@link
   *     android.os.Build.VERSION_CODES#KITKAT_WATCH} instead of the component name.
   */
  public MediaSessionCompat(
      Context context,
      String tag,
      @Nullable ComponentName mbrComponent,
      @Nullable PendingIntent mbrIntent) {
    this(context, tag, mbrComponent, mbrIntent, null);
  }

  /**
   * Creates a new session with a specified media button receiver (a component name and/or a pending
   * intent). You must call {@link #release()} when finished with the session.
   *
   * <p>The session will automatically be registered with the system but will not be published until
   * {@link #setActive(boolean) setActive(true)} is called.
   *
   * <p>For API 20 or earlier, note that a media button receiver is required for handling {@link
   * Intent#ACTION_MEDIA_BUTTON}. This constructor will attempt to find an appropriate {@link
   * BroadcastReceiver} from your manifest if it's not specified. See {@link MediaButtonReceiver}
   * for more details. The {@code sessionInfo} can include additional unchanging information about
   * this session. For example, it can include the version of the application, or other app-specific
   * unchanging information.
   *
   * @param context The context to use to create the session.
   * @param tag A short name for debugging purposes.
   * @param mbrComponent The component name for your media button receiver.
   * @param mbrIntent The PendingIntent for your receiver component that handles media button
   *     events. This is optional and will be used on between {@link
   *     android.os.Build.VERSION_CODES#JELLY_BEAN_MR2} and {@link
   *     android.os.Build.VERSION_CODES#KITKAT_WATCH} instead of the component name.
   * @param sessionInfo A bundle for additional information about this session, or {@link
   *     Bundle#EMPTY} if none. Controllers can get this information by calling {@link
   *     MediaControllerCompat#getSessionInfo()}. An {@link IllegalArgumentException} will be thrown
   *     if this contains any non-framework Parcelable objects.
   */
  public MediaSessionCompat(
      Context context,
      String tag,
      @Nullable ComponentName mbrComponent,
      @Nullable PendingIntent mbrIntent,
      @Nullable Bundle sessionInfo) {
    this(context, tag, mbrComponent, mbrIntent, sessionInfo, null /* session2Token */);
  }

  /** */
  @SuppressWarnings({
    "method.invocation.invalid",
    "argument.type.incompatible",
    "assignment.type.incompatible"
  }) // registering listener from constructor
  public MediaSessionCompat(
      Context context,
      String tag,
      @Nullable ComponentName mbrComponent,
      @Nullable PendingIntent mbrIntent,
      @Nullable Bundle sessionInfo,
      @Nullable VersionedParcelable session2Token) {
    if (context == null) {
      throw new IllegalArgumentException("context must not be null");
    }
    if (TextUtils.isEmpty(tag)) {
      throw new IllegalArgumentException("tag must not be null or empty");
    }

    if (mbrComponent == null) {
      mbrComponent = MediaButtonReceiver.getMediaButtonReceiverComponent(context);
      if (mbrComponent == null) {
        Log.i(TAG, "Couldn't find a unique registered media button receiver in the given context.");
      }
    }
    if (mbrComponent != null && mbrIntent == null) {
      // construct a PendingIntent for the media button
      Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
      // the associated intent will be handled by the component being registered
      mediaButtonIntent.setComponent(mbrComponent);
      mbrIntent =
          PendingIntent.getBroadcast(
              context,
              0 /* requestCode, ignored */,
              mediaButtonIntent,
              Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
    }

    if (android.os.Build.VERSION.SDK_INT >= 21) {
      if (android.os.Build.VERSION.SDK_INT >= 29) {
        mImpl = new MediaSessionImplApi29(context, tag, session2Token, sessionInfo);
      } else if (android.os.Build.VERSION.SDK_INT >= 28) {
        mImpl = new MediaSessionImplApi28(context, tag, session2Token, sessionInfo);
      } else if (android.os.Build.VERSION.SDK_INT >= 22) {
        mImpl = new MediaSessionImplApi22(context, tag, session2Token, sessionInfo);
      } else {
        mImpl = new MediaSessionImplApi21(context, tag, session2Token, sessionInfo);
      }
      // Set default callback to respond to controllers' extra binder requests.
      Looper myLooper = Looper.myLooper();
      Handler handler = new Handler(myLooper != null ? myLooper : Looper.getMainLooper());
      setCallback(new Callback() {}, handler);
      mImpl.setMediaButtonReceiver(mbrIntent);
    } else {
      mImpl =
          new MediaSessionImplApi19(
              context, tag, mbrComponent, mbrIntent, session2Token, sessionInfo);
    }
    mController = new MediaControllerCompat(context, this);

    if (sMaxBitmapSize == 0) {
      sMaxBitmapSize =
          (int)
              (TypedValue.applyDimension(
                      TypedValue.COMPLEX_UNIT_DIP,
                      MAX_BITMAP_SIZE_IN_DP,
                      context.getResources().getDisplayMetrics())
                  + 0.5f);
    }
  }

  @SuppressWarnings({
    "method.invocation.invalid",
    "argument.type.incompatible",
    "assignment.type.incompatible"
  }) // registering listener from constructor
  private MediaSessionCompat(Context context, MediaSessionImpl impl) {
    mImpl = impl;
    mController = new MediaControllerCompat(context, this);
  }

  /**
   * Adds a callback to receive updates on for the MediaSession. This includes media button and
   * volume events. The caller's thread will be used to post events. Set the callback to null to
   * stop receiving events.
   *
   * <p>Don't reuse the callback among the sessions. Callbacks keep internal reference to the
   * session when it's set, so it may misbehave.
   *
   * @param callback The callback object
   */
  public void setCallback(Callback callback) {
    setCallback(callback, null);
  }

  /**
   * Sets the callback to receive updates for the MediaSession. This includes media button and
   * volume events. Set the callback to null to stop receiving events.
   *
   * <p>Don't reuse the callback among the sessions. Callbacks keep internal reference to the
   * session when it's set, so it may misbehave.
   *
   * @param callback The callback to receive updates on.
   * @param handler The handler that events should be posted on.
   */
  @SuppressWarnings("deprecation")
  public void setCallback(Callback callback, @Nullable Handler handler) {
    if (callback == null) {
      mImpl.setCallback(null, null);
    } else {
      mImpl.setCallback(callback, handler != null ? handler : new Handler());
    }
  }

  /**
   * Sets the {@link RegistrationCallback}.
   *
   * @param callback callback to listener callback registration. Can be null to stop.
   * @param handler handler
   */
  public void setRegistrationCallback(@Nullable RegistrationCallback callback, Handler handler) {
    mImpl.setRegistrationCallback(callback, handler);
  }

  /**
   * Sets an intent for launching UI for this Session. This can be used as a quick link to an
   * ongoing media screen. The intent should be for an activity that may be started using {@link
   * Activity#startActivity(Intent)}.
   *
   * @param pi The intent to launch to show UI for this Session.
   */
  public void setSessionActivity(@Nullable PendingIntent pi) {
    mImpl.setSessionActivity(pi);
  }

  /**
   * Sets a pending intent for your media button receiver to allow restarting playback after the
   * session has been stopped. If your app is started in this way an {@link
   * Intent#ACTION_MEDIA_BUTTON} intent will be sent via the pending intent.
   *
   * <p>This method will only work on {@link android.os.Build.VERSION_CODES#LOLLIPOP} and later.
   * Earlier platform versions must include the media button receiver in the constructor.
   *
   * @param mbr The {@link PendingIntent} to send the media button event to.
   */
  public void setMediaButtonReceiver(PendingIntent mbr) {
    mImpl.setMediaButtonReceiver(mbr);
  }

  /**
   * Sets any flags for the session.
   *
   * @param flags The flags to set for this session.
   */
  public void setFlags(@SessionFlags int flags) {
    mImpl.setFlags(flags);
  }

  /**
   * Sets the stream this session is playing on. This will affect the system's volume handling for
   * this session. If {@link #setPlaybackToRemote} was previously called it will stop receiving
   * volume commands and the system will begin sending volume changes to the appropriate stream.
   *
   * <p>By default sessions are on {@link AudioManager#STREAM_MUSIC}.
   *
   * @param stream The {@link AudioManager} stream this session is playing on.
   */
  public void setPlaybackToLocal(int stream) {
    mImpl.setPlaybackToLocal(stream);
  }

  /**
   * Configures this session to use remote volume handling. This must be called to receive volume
   * button events, otherwise the system will adjust the current stream volume for this session. If
   * {@link #setPlaybackToLocal} was previously called that stream will stop receiving volume
   * changes for this session.
   *
   * <p>On platforms earlier than {@link android.os.Build.VERSION_CODES#LOLLIPOP} this will only
   * allow an app to handle volume commands sent directly to the session by a {@link
   * MediaControllerCompat}. System routing of volume keys will not use the volume provider.
   *
   * @param volumeProvider The provider that will handle volume changes. May not be null.
   */
  public void setPlaybackToRemote(VolumeProviderCompat volumeProvider) {
    if (volumeProvider == null) {
      throw new IllegalArgumentException("volumeProvider may not be null!");
    }
    mImpl.setPlaybackToRemote(volumeProvider);
  }

  /**
   * Sets if this session is currently active and ready to receive commands. If set to false your
   * session's controller may not be discoverable. You must set the session to active before it can
   * start receiving media button events or transport commands.
   *
   * <p>On platforms earlier than {@link android.os.Build.VERSION_CODES#LOLLIPOP}, a media button
   * event receiver should be set via the constructor to receive media button events.
   *
   * @param active Whether this session is active or not.
   */
  public void setActive(boolean active) {
    mImpl.setActive(active);
    for (OnActiveChangeListener listener : mActiveListeners) {
      listener.onActiveChanged();
    }
  }

  /**
   * Gets the current active state of this session.
   *
   * @return True if the session is active, false otherwise.
   */
  public boolean isActive() {
    return mImpl.isActive();
  }

  /**
   * Sends a proprietary event to all MediaControllers listening to this Session. It's up to the
   * Controller/Session owner to determine the meaning of any events.
   *
   * @param event The name of the event to send
   * @param extras Any extras included with the event
   */
  public void sendSessionEvent(String event, @Nullable Bundle extras) {
    if (TextUtils.isEmpty(event)) {
      throw new IllegalArgumentException("event cannot be null or empty");
    }
    mImpl.sendSessionEvent(event, extras);
  }

  /**
   * This must be called when an app has finished performing playback. If playback is expected to
   * start again shortly the session can be left open, but it must be released if your activity or
   * service is being destroyed.
   */
  public void release() {
    mImpl.release();
  }

  /**
   * Retrieves a token object that can be used by apps to create a {@link MediaControllerCompat} for
   * interacting with this session. The owner of the session is responsible for deciding how to
   * distribute these tokens.
   *
   * <p>On platform versions before {@link android.os.Build.VERSION_CODES#LOLLIPOP} this token may
   * only be used within your app as there is no way to guarantee other apps are using the same
   * version of the support library.
   *
   * @return A token that can be used to create a media controller for this session.
   */
  public Token getSessionToken() {
    return mImpl.getSessionToken();
  }

  /**
   * Gets a controller for this session. This is a convenience method to avoid having to cache your
   * own controller in process.
   *
   * @return A controller for this session.
   */
  public MediaControllerCompat getController() {
    return mController;
  }

  /**
   * Updates the current playback state.
   *
   * @param state The current state of playback
   */
  public void setPlaybackState(PlaybackStateCompat state) {
    mImpl.setPlaybackState(state);
  }

  /**
   * Updates the current metadata. New metadata can be created using {@link
   * androidx.media3.session.legacy.MediaMetadataCompat.Builder}. This operation may take time
   * proportional to the size of the bitmap to replace large bitmaps with a scaled down copy.
   *
   * @param metadata The new metadata
   * @see androidx.media3.session.legacy.MediaMetadataCompat.Builder#putBitmap
   */
  public void setMetadata(@Nullable MediaMetadataCompat metadata) {
    mImpl.setMetadata(metadata);
  }

  /**
   * Updates the list of items in the play queue. It is an ordered list and should contain the
   * current item, and previous or upcoming items if they exist. The id of each item should be
   * unique within the play queue. Specify null if there is no current play queue.
   *
   * <p>The queue should be of reasonable size. If the play queue is unbounded within your app, it
   * is better to send a reasonable amount in a sliding window instead.
   *
   * @param queue A list of items in the play queue.
   */
  public void setQueue(@Nullable List<QueueItem> queue) {
    if (queue != null) {
      Set<Long> set = new HashSet<>();
      for (QueueItem item : queue) {
        if (item == null) {
          throw new IllegalArgumentException("queue shouldn't have null items");
        }
        if (set.contains(item.getQueueId())) {
          Log.e(
              TAG,
              "Found duplicate queue id: " + item.getQueueId(),
              new IllegalArgumentException("id of each queue item should be unique"));
        }
        set.add(item.getQueueId());
      }
    }
    mImpl.setQueue(queue);
  }

  /**
   * Sets the title of the play queue. The UI should display this title along with the play queue
   * itself. e.g. "Play Queue", "Now Playing", or an album name.
   *
   * @param title The title of the play queue.
   */
  public void setQueueTitle(CharSequence title) {
    mImpl.setQueueTitle(title);
  }

  /**
   * Sets the style of rating used by this session. Apps trying to set the rating should use this
   * style. Must be one of the following:
   *
   * <ul>
   *   <li>{@link RatingCompat#RATING_NONE}
   *   <li>{@link RatingCompat#RATING_3_STARS}
   *   <li>{@link RatingCompat#RATING_4_STARS}
   *   <li>{@link RatingCompat#RATING_5_STARS}
   *   <li>{@link RatingCompat#RATING_HEART}
   *   <li>{@link RatingCompat#RATING_PERCENTAGE}
   *   <li>{@link RatingCompat#RATING_THUMB_UP_DOWN}
   * </ul>
   */
  public void setRatingType(@RatingCompat.Style int type) {
    mImpl.setRatingType(type);
  }

  /**
   * Enables/disables captioning for this session.
   *
   * @param enabled {@code true} to enable captioning, {@code false} to disable.
   */
  public void setCaptioningEnabled(boolean enabled) {
    mImpl.setCaptioningEnabled(enabled);
  }

  /**
   * Sets the repeat mode for this session.
   *
   * <p>Note that if this method is not called before, {@link MediaControllerCompat#getRepeatMode}
   * will return {@link PlaybackStateCompat#REPEAT_MODE_NONE}.
   *
   * @param repeatMode The repeat mode. Must be one of the following: {@link
   *     PlaybackStateCompat#REPEAT_MODE_NONE}, {@link PlaybackStateCompat#REPEAT_MODE_ONE}, {@link
   *     PlaybackStateCompat#REPEAT_MODE_ALL}, {@link PlaybackStateCompat#REPEAT_MODE_GROUP}
   */
  public void setRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode) {
    mImpl.setRepeatMode(repeatMode);
  }

  /**
   * Sets the shuffle mode for this session.
   *
   * <p>Note that if this method is not called before, {@link MediaControllerCompat#getShuffleMode}
   * will return {@link PlaybackStateCompat#SHUFFLE_MODE_NONE}.
   *
   * @param shuffleMode The shuffle mode. Must be one of the following: {@link
   *     PlaybackStateCompat#SHUFFLE_MODE_NONE}, {@link PlaybackStateCompat#SHUFFLE_MODE_ALL},
   *     {@link PlaybackStateCompat#SHUFFLE_MODE_GROUP}
   */
  public void setShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
    mImpl.setShuffleMode(shuffleMode);
  }

  /**
   * Sets some extras that can be associated with the {@link MediaSessionCompat}. No assumptions
   * should be made as to how a {@link MediaControllerCompat} will handle these extras. Keys should
   * be fully qualified (e.g. com.example.MY_EXTRA) to avoid conflicts.
   *
   * @param extras The extras associated with the session.
   */
  public void setExtras(@Nullable Bundle extras) {
    mImpl.setExtras(extras);
  }

  /**
   * Gets the underlying framework {@link android.media.session.MediaSession} object.
   *
   * <p>This method is only supported on API 21+.
   *
   * @return The underlying {@link android.media.session.MediaSession} object, or null if none.
   */
  @Nullable
  public Object getMediaSession() {
    return mImpl.getMediaSession();
  }

  /**
   * Gets the underlying framework {@link android.media.RemoteControlClient} object.
   *
   * <p>This method is only supported on APIs 14-20. On API 21+ {@link #getMediaSession()} should be
   * used instead.
   *
   * @return The underlying {@link android.media.RemoteControlClient} object, or null if none.
   */
  @Nullable
  public Object getRemoteControlClient() {
    return mImpl.getRemoteControlClient();
  }

  /**
   * Gets the controller information who sent the current request.
   *
   * <p>Note: This is only valid while in a request callback, such as {@link Callback#onPlay}.
   *
   * <p>Note: From API 21 to 23, this method returns a fake {@link RemoteUserInfo} which has
   * following values:
   *
   * <ul>
   *   <li>Package name is {@link MediaSessionManager.RemoteUserInfo#LEGACY_CONTROLLER}.
   *   <li>PID and UID will have negative values.
   * </ul>
   *
   * <p>Note: From API 24 to 27, the {@link RemoteUserInfo} returned from this method will have
   * negative uid and pid. Most of the cases it will have the correct package name, but sometimes it
   * will fail to get the right one.
   *
   * @see MediaSessionManager.RemoteUserInfo#LEGACY_CONTROLLER
   * @see MediaSessionManager#isTrustedForMediaControl(RemoteUserInfo)
   */
  @Nullable
  public final RemoteUserInfo getCurrentControllerInfo() {
    return mImpl.getCurrentControllerInfo();
  }

  /**
   * Returns the name of the package that sent the last media button, transport control, or command
   * from controllers and the system. This is only valid while in a request callback, such as {@link
   * Callback#onPlay}. This method is not available and returns null on pre-N devices.
   */
  @Nullable
  public String getCallingPackage() {
    return mImpl.getCallingPackage();
  }

  /**
   * Adds a listener to be notified when the active status of this session changes. This is
   * primarily used by the support library and should not be needed by apps.
   *
   * @param listener The listener to add.
   */
  public void addOnActiveChangeListener(OnActiveChangeListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("Listener may not be null");
    }
    mActiveListeners.add(listener);
  }

  /**
   * Stops the listener from being notified when the active status of this session changes.
   *
   * @param listener The listener to remove.
   */
  public void removeOnActiveChangeListener(OnActiveChangeListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("Listener may not be null");
    }
    mActiveListeners.remove(listener);
  }

  /**
   * Creates an instance from a framework {@link android.media.session.MediaSession} object.
   *
   * <p>This method is only supported on API 21+. On API 20 and below, it returns null.
   *
   * <p>Note: A {@link MediaSessionCompat} object returned from this method may not provide the full
   * functionality of {@link MediaSessionCompat} until setting a new {@link
   * MediaSessionCompat.Callback}. To avoid this, when both a {@link MediaSessionCompat} and a
   * framework {@link android.media.session.MediaSession} are needed, it is recommended to create a
   * {@link MediaSessionCompat} first and get the framework session through {@link
   * #getMediaSession()}.
   *
   * @param context The context to use to create the session.
   * @param mediaSession A {@link android.media.session.MediaSession} object.
   * @return An equivalent {@link MediaSessionCompat} object, or null if none.
   */
  @Nullable
  public static MediaSessionCompat fromMediaSession(
      @Nullable Context context, @Nullable Object mediaSession) {
    if (Build.VERSION.SDK_INT < 21 || context == null || mediaSession == null) {
      return null;
    }
    MediaSessionImpl impl;
    if (Build.VERSION.SDK_INT >= 29) {
      impl = new MediaSessionImplApi29(mediaSession);
    } else if (Build.VERSION.SDK_INT >= 28) {
      impl = new MediaSessionImplApi28(mediaSession);
    } else {
      // API 21+
      impl = new MediaSessionImplApi21(mediaSession);
    }
    return new MediaSessionCompat(context, impl);
  }

  /** A helper method for setting the application class loader to the given {@link Bundle}. */
  public static void ensureClassLoader(@Nullable Bundle bundle) {
    if (bundle != null) {
      bundle.setClassLoader(checkNotNull(MediaSessionCompat.class.getClassLoader()));
    }
  }

  /**
   * Tries to unparcel the given {@link Bundle} with the application class loader and returns {@code
   * null} if a {@link BadParcelableException} is thrown while unparcelling, otherwise the given
   * bundle in which the application class loader is set.
   */
  @Nullable
  public static Bundle unparcelWithClassLoader(@Nullable Bundle bundle) {
    if (bundle == null) {
      return null;
    }
    ensureClassLoader(bundle);
    try {
      bundle.isEmpty(); // to call unparcel()
      return bundle;
    } catch (BadParcelableException e) {
      // The exception details will be logged by Parcel class.
      Log.e(TAG, "Could not unparcel the data.");
      return null;
    }
  }

  @Nullable
  @SuppressWarnings("WeakerAccess") /* synthetic access */
  static PlaybackStateCompat getStateWithUpdatedPosition(
      @Nullable PlaybackStateCompat state, @Nullable MediaMetadataCompat metadata) {
    if (state == null || state.getPosition() == PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN) {
      return state;
    }

    if (state.getState() == PlaybackStateCompat.STATE_PLAYING
        || state.getState() == PlaybackStateCompat.STATE_FAST_FORWARDING
        || state.getState() == PlaybackStateCompat.STATE_REWINDING) {
      long updateTime = state.getLastPositionUpdateTime();
      if (updateTime > 0) {
        long currentTime = SystemClock.elapsedRealtime();
        long position =
            (long) (state.getPlaybackSpeed() * (currentTime - updateTime)) + state.getPosition();
        long duration = -1;
        if (metadata != null && metadata.containsKey(MediaMetadataCompat.METADATA_KEY_DURATION)) {
          duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        }

        if (duration >= 0 && position > duration) {
          position = duration;
        } else if (position < 0) {
          position = 0;
        }
        return new PlaybackStateCompat.Builder(state)
            .setState(state.getState(), position, state.getPlaybackSpeed(), currentTime)
            .build();
      }
    }
    return state;
  }

  /**
   * Receives transport controls, media buttons, and commands from controllers and the system. The
   * callback may be set using {@link #setCallback}.
   *
   * <p>Don't reuse the callback among the sessions. Callbacks keep internal reference to the
   * session when it's set, so it may misbehave.
   */
  public abstract static class Callback {
    final Object mLock = new Object();
    @Nullable final MediaSession.Callback mCallbackFwk;
    private boolean mMediaPlayPausePendingOnHandler;

    @GuardedBy("mLock")
    WeakReference<MediaSessionImpl> mSessionImpl;

    @Nullable
    @GuardedBy("mLock")
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    CallbackHandler mCallbackHandler;

    public Callback() {
      if (android.os.Build.VERSION.SDK_INT >= 21) {
        mCallbackFwk = new MediaSessionCallbackApi21();
      } else {
        mCallbackFwk = null;
      }
      mSessionImpl = new WeakReference<>(null);
    }

    void setSessionImpl(@Nullable MediaSessionImpl impl, @Nullable Handler handler) {
      synchronized (mLock) {
        mSessionImpl = new WeakReference<MediaSessionImpl>(impl);
        if (mCallbackHandler != null) {
          mCallbackHandler.removeCallbacksAndMessages(null);
        }
        mCallbackHandler =
            impl == null || handler == null ? null : new CallbackHandler(handler.getLooper());
      }
    }

    /**
     * Called when a controller has sent a custom command to this session. The owner of the session
     * may handle custom commands but is not required to.
     *
     * @param command The command name.
     * @param extras Optional parameters for the command, may be null.
     * @param cb A result receiver to which a result may be sent by the command, may be null.
     */
    public void onCommand(String command, @Nullable Bundle extras, @Nullable ResultReceiver cb) {}

    /**
     * Override to handle media button events.
     *
     * <p>The double tap of {@link KeyEvent#KEYCODE_MEDIA_PLAY_PAUSE} or {@link
     * KeyEvent#KEYCODE_HEADSETHOOK} will call the {@link #onSkipToNext} by default. If the current
     * SDK level is 27 or higher, the default double tap handling is done by framework so this
     * method would do nothing for it.
     *
     * @param mediaButtonEvent The media button event intent.
     * @return True if the event was handled, false otherwise.
     */
    @SuppressWarnings("deprecation")
    public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
      if (android.os.Build.VERSION.SDK_INT >= 27) {
        // Double tap of play/pause as skipping to next is already handled by framework,
        // so we don't need to repeat again here.
        // Note: Double tap would be handled twice for OC-DR1 whose SDK version 26 and
        //       framework handles the double tap.
        return false;
      }
      MediaSessionImpl impl;
      Handler callbackHandler;
      synchronized (mLock) {
        impl = mSessionImpl.get();
        callbackHandler = mCallbackHandler;
      }
      if (impl == null || callbackHandler == null) {
        return false;
      }
      KeyEvent keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
      if (keyEvent == null || keyEvent.getAction() != KeyEvent.ACTION_DOWN) {
        return false;
      }
      RemoteUserInfo remoteUserInfo = impl.getCurrentControllerInfo();
      int keyCode = keyEvent.getKeyCode();
      switch (keyCode) {
        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
        case KeyEvent.KEYCODE_HEADSETHOOK:
          if (keyEvent.getRepeatCount() == 0) {
            if (mMediaPlayPausePendingOnHandler) {
              callbackHandler.removeMessages(
                  CallbackHandler.MSG_MEDIA_PLAY_PAUSE_KEY_DOUBLE_TAP_TIMEOUT);
              mMediaPlayPausePendingOnHandler = false;
              PlaybackStateCompat state = impl.getPlaybackState();
              long validActions = state == null ? 0 : state.getActions();
              // Consider double tap as the next.
              if ((validActions & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
                onSkipToNext();
              }
            } else {
              mMediaPlayPausePendingOnHandler = true;
              callbackHandler.sendMessageDelayed(
                  callbackHandler.obtainMessage(
                      CallbackHandler.MSG_MEDIA_PLAY_PAUSE_KEY_DOUBLE_TAP_TIMEOUT, remoteUserInfo),
                  ViewConfiguration.getDoubleTapTimeout());
            }
          } else {
            // Consider long-press as a single tap.
            handleMediaPlayPauseIfPendingOnHandler(impl, callbackHandler);
          }
          return true;
        default:
          // If another key is pressed within double tap timeout, consider the pending
          // pending play/pause as a single tap to handle media keys in order.
          handleMediaPlayPauseIfPendingOnHandler(impl, callbackHandler);
          break;
      }
      return false;
    }

    void handleMediaPlayPauseIfPendingOnHandler(MediaSessionImpl impl, Handler callbackHandler) {
      if (!mMediaPlayPausePendingOnHandler) {
        return;
      }
      mMediaPlayPausePendingOnHandler = false;
      callbackHandler.removeMessages(CallbackHandler.MSG_MEDIA_PLAY_PAUSE_KEY_DOUBLE_TAP_TIMEOUT);
      PlaybackStateCompat state = impl.getPlaybackState();
      long validActions = state == null ? 0 : state.getActions();
      boolean isPlaying = state != null && state.getState() == PlaybackStateCompat.STATE_PLAYING;
      boolean canPlay =
          (validActions & (PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY))
              != 0;
      boolean canPause =
          (validActions
                  & (PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PAUSE))
              != 0;
      if (isPlaying && canPause) {
        onPause();
      } else if (!isPlaying && canPlay) {
        onPlay();
      }
    }

    /**
     * Override to handle requests to prepare playback. Override {@link #onPlay} to handle requests
     * for starting playback.
     */
    public void onPrepare() {}

    /**
     * Override to handle requests to prepare for playing a specific mediaId that was provided by
     * your app. Override {@link #onPlayFromMediaId} to handle requests for starting playback.
     */
    public void onPrepareFromMediaId(@Nullable String mediaId, @Nullable Bundle extras) {}

    /**
     * Override to handle requests to prepare playback from a search query. An empty query indicates
     * that the app may prepare any music. The implementation should attempt to make a smart choice
     * about what to play. Override {@link #onPlayFromSearch} to handle requests for starting
     * playback.
     */
    public void onPrepareFromSearch(@Nullable String query, @Nullable Bundle extras) {}

    /**
     * Override to handle requests to prepare a specific media item represented by a URI. Override
     * {@link #onPlayFromUri} to handle requests for starting playback.
     */
    public void onPrepareFromUri(@Nullable Uri uri, @Nullable Bundle extras) {}

    /** Override to handle requests to begin playback. */
    public void onPlay() {}

    /** Override to handle requests to play a specific mediaId that was provided by your app. */
    public void onPlayFromMediaId(@Nullable String mediaId, @Nullable Bundle extras) {}

    /**
     * Override to handle requests to begin playback from a search query. An empty query indicates
     * that the app may play any music. The implementation should attempt to make a smart choice
     * about what to play.
     */
    public void onPlayFromSearch(@Nullable String query, @Nullable Bundle extras) {}

    /** Override to handle requests to play a specific media item represented by a URI. */
    public void onPlayFromUri(@Nullable Uri uri, @Nullable Bundle extras) {}

    /** Override to handle requests to play an item with a given id from the play queue. */
    public void onSkipToQueueItem(long id) {}

    /** Override to handle requests to pause playback. */
    public void onPause() {}

    /** Override to handle requests to skip to the next media item. */
    public void onSkipToNext() {}

    /** Override to handle requests to skip to the previous media item. */
    public void onSkipToPrevious() {}

    /** Override to handle requests to fast forward. */
    public void onFastForward() {}

    /** Override to handle requests to rewind. */
    public void onRewind() {}

    /** Override to handle requests to stop playback. */
    public void onStop() {}

    /**
     * Override to handle requests to seek to a specific position in ms.
     *
     * @param pos New position to move to, in milliseconds.
     */
    public void onSeekTo(long pos) {}

    /**
     * Override to handle the item being rated.
     *
     * @param rating The rating being set.
     */
    public void onSetRating(@Nullable RatingCompat rating) {}

    /**
     * Override to handle the item being rated.
     *
     * @param rating The rating being set.
     * @param extras The extras can include information about the media item being rated.
     */
    public void onSetRating(@Nullable RatingCompat rating, @Nullable Bundle extras) {}

    /**
     * Override to handle the playback speed change. To update the new playback speed, create a new
     * {@link PlaybackStateCompat} by using {@link PlaybackStateCompat.Builder#setState(int, long,
     * float)}, and set it with {@link #setPlaybackState(PlaybackStateCompat)}.
     *
     * <p>A value of {@code 1.0f} is the default playback value, and a negative value indicates
     * reverse playback. The {@code speed} will not be equal to zero.
     *
     * @param speed the playback speed
     * @see #setPlaybackState(PlaybackStateCompat)
     * @see PlaybackStateCompat.Builder#setState(int, long, float)
     */
    public void onSetPlaybackSpeed(float speed) {}

    /**
     * Override to handle requests to enable/disable captioning.
     *
     * @param enabled {@code true} to enable captioning, {@code false} to disable.
     */
    public void onSetCaptioningEnabled(boolean enabled) {}

    /**
     * Override to handle the setting of the repeat mode.
     *
     * <p>You should call {@link #setRepeatMode} before end of this method in order to notify the
     * change to the {@link MediaControllerCompat}, or {@link MediaControllerCompat#getRepeatMode}
     * could return an invalid value.
     *
     * @param repeatMode The repeat mode which is one of followings: {@link
     *     PlaybackStateCompat#REPEAT_MODE_NONE}, {@link PlaybackStateCompat#REPEAT_MODE_ONE},
     *     {@link PlaybackStateCompat#REPEAT_MODE_ALL}, {@link
     *     PlaybackStateCompat#REPEAT_MODE_GROUP}
     */
    public void onSetRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode) {}

    /**
     * Override to handle the setting of the shuffle mode.
     *
     * <p>You should call {@link #setShuffleMode} before the end of this method in order to notify
     * the change to the {@link MediaControllerCompat}, or {@link
     * MediaControllerCompat#getShuffleMode} could return an invalid value.
     *
     * @param shuffleMode The shuffle mode which is one of followings: {@link
     *     PlaybackStateCompat#SHUFFLE_MODE_NONE}, {@link PlaybackStateCompat#SHUFFLE_MODE_ALL},
     *     {@link PlaybackStateCompat#SHUFFLE_MODE_GROUP}
     */
    public void onSetShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {}

    /**
     * Called when a {@link MediaControllerCompat} wants a {@link PlaybackStateCompat.CustomAction}
     * to be performed.
     *
     * @param action The action that was originally sent in the {@link
     *     PlaybackStateCompat.CustomAction}.
     * @param extras Optional extras specified by the {@link MediaControllerCompat}.
     * @see #ACTION_FLAG_AS_INAPPROPRIATE
     * @see #ACTION_SKIP_AD
     * @see #ACTION_FOLLOW
     * @see #ACTION_UNFOLLOW
     */
    public void onCustomAction(String action, @Nullable Bundle extras) {}

    /**
     * Called when a {@link MediaControllerCompat} wants to add a {@link QueueItem} with the given
     * {@link MediaDescriptionCompat description} at the end of the play queue.
     *
     * @param description The {@link MediaDescriptionCompat} for creating the {@link QueueItem} to
     *     be inserted.
     */
    public void onAddQueueItem(@Nullable MediaDescriptionCompat description) {}

    /**
     * Called when a {@link MediaControllerCompat} wants to add a {@link QueueItem} with the given
     * {@link MediaDescriptionCompat description} at the specified position in the play queue.
     *
     * @param description The {@link MediaDescriptionCompat} for creating the {@link QueueItem} to
     *     be inserted.
     * @param index The index at which the created {@link QueueItem} is to be inserted.
     */
    public void onAddQueueItem(@Nullable MediaDescriptionCompat description, int index) {}

    /**
     * Called when a {@link MediaControllerCompat} wants to remove the first occurrence of the
     * specified {@link QueueItem} with the given {@link MediaDescriptionCompat description} in the
     * play queue.
     *
     * @param description The {@link MediaDescriptionCompat} for denoting the {@link QueueItem} to
     *     be removed.
     */
    public void onRemoveQueueItem(@Nullable MediaDescriptionCompat description) {}

    /**
     * Called when a {@link MediaControllerCompat} wants to remove a {@link QueueItem} at the
     * specified position in the play queue.
     *
     * @param index The index of the element to be removed.
     * @deprecated {@link #onRemoveQueueItem} will be called instead.
     */
    @Deprecated
    public void onRemoveQueueItemAt(int index) {}

    private class CallbackHandler extends Handler {
      private static final int MSG_MEDIA_PLAY_PAUSE_KEY_DOUBLE_TAP_TIMEOUT = 1;

      CallbackHandler(Looper looper) {
        super(looper);
      }

      @Override
      public void handleMessage(Message msg) {
        if (msg.what == MSG_MEDIA_PLAY_PAUSE_KEY_DOUBLE_TAP_TIMEOUT) {
          // Here we manually set the caller info, since this is not directly called from
          // the session callback. This is triggered by timeout.
          MediaSessionImpl impl;
          Handler callbackHandler;
          synchronized (mLock) {
            impl = mSessionImpl.get();
            callbackHandler = mCallbackHandler;
          }
          if (impl == null
              || MediaSessionCompat.Callback.this != impl.getCallback()
              || callbackHandler == null) {
            return;
          }
          RemoteUserInfo info = (RemoteUserInfo) msg.obj;
          impl.setCurrentControllerInfo(info);
          handleMediaPlayPauseIfPendingOnHandler(impl, callbackHandler);
          impl.setCurrentControllerInfo(null);
        }
      }
    }

    @RequiresApi(21)
    private class MediaSessionCallbackApi21 extends MediaSession.Callback {
      MediaSessionCallbackApi21() {}

      @Override
      @SuppressWarnings("deprecation")
      public void onCommand(String command, @Nullable Bundle extras, @Nullable ResultReceiver cb) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        ensureClassLoader(extras);
        setCurrentControllerInfo(sessionImpl);
        try {
          if (command.equals(MediaControllerCompat.COMMAND_GET_EXTRA_BINDER)) {
            if (cb != null) {
              Bundle result = new Bundle();
              Token token = sessionImpl.getSessionToken();
              IMediaSession extraBinder = token.getExtraBinder();
              result.putBinder(
                  KEY_EXTRA_BINDER, extraBinder == null ? null : extraBinder.asBinder());
              ParcelUtils.putVersionedParcelable(
                  result, KEY_SESSION2_TOKEN, token.getSession2Token());
              cb.send(0, result);
            }
          } else if (command.equals(MediaControllerCompat.COMMAND_ADD_QUEUE_ITEM)) {
            if (extras != null) {
              Callback.this.onAddQueueItem(
                  LegacyParcelableUtil.convert(
                      extras.getParcelable(
                          MediaControllerCompat.COMMAND_ARGUMENT_MEDIA_DESCRIPTION),
                      MediaDescriptionCompat.CREATOR));
            }
          } else if (command.equals(MediaControllerCompat.COMMAND_ADD_QUEUE_ITEM_AT)) {
            if (extras != null) {
              Callback.this.onAddQueueItem(
                  LegacyParcelableUtil.convert(
                      extras.getParcelable(
                          MediaControllerCompat.COMMAND_ARGUMENT_MEDIA_DESCRIPTION),
                      MediaDescriptionCompat.CREATOR),
                  extras.getInt(MediaControllerCompat.COMMAND_ARGUMENT_INDEX));
            }
          } else if (command.equals(MediaControllerCompat.COMMAND_REMOVE_QUEUE_ITEM)) {
            if (extras != null) {
              Callback.this.onRemoveQueueItem(
                  LegacyParcelableUtil.convert(
                      extras.getParcelable(
                          MediaControllerCompat.COMMAND_ARGUMENT_MEDIA_DESCRIPTION),
                      MediaDescriptionCompat.CREATOR));
            }
          } else if (command.equals(MediaControllerCompat.COMMAND_REMOVE_QUEUE_ITEM_AT)) {
            List<MediaSessionCompat.QueueItem> queue = sessionImpl.mQueue;
            if (queue != null && extras != null) {
              int index = extras.getInt(MediaControllerCompat.COMMAND_ARGUMENT_INDEX, -1);
              QueueItem item = (index >= 0 && index < queue.size()) ? queue.get(index) : null;
              if (item != null) {
                Callback.this.onRemoveQueueItem(item.getDescription());
              }
            }
          } else {
            Callback.this.onCommand(command, extras, cb);
          }
        } catch (BadParcelableException e) {
          // Do not print the exception here, since it is already done by the Parcel
          // class.
          Log.e(TAG, "Could not unparcel the extra data.");
        }
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return false;
        }
        setCurrentControllerInfo(sessionImpl);
        boolean result = Callback.this.onMediaButtonEvent(mediaButtonIntent);
        clearCurrentControllerInfo(sessionImpl);
        return result || super.onMediaButtonEvent(mediaButtonIntent);
      }

      @Override
      public void onPlay() {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onPlay();
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onPlayFromMediaId(String mediaId, @Nullable Bundle extras) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        ensureClassLoader(extras);
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onPlayFromMediaId(mediaId, extras);
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onPlayFromSearch(String search, @Nullable Bundle extras) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        ensureClassLoader(extras);
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onPlayFromSearch(search, extras);
        clearCurrentControllerInfo(sessionImpl);
      }

      @RequiresApi(23)
      @Override
      public void onPlayFromUri(Uri uri, @Nullable Bundle extras) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        ensureClassLoader(extras);
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onPlayFromUri(uri, extras);
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onSkipToQueueItem(long id) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onSkipToQueueItem(id);
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onPause() {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onPause();
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onSkipToNext() {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onSkipToNext();
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onSkipToPrevious() {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onSkipToPrevious();
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onFastForward() {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onFastForward();
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onRewind() {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onRewind();
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onStop() {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onStop();
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onSeekTo(long pos) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onSeekTo(pos);
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      public void onSetRating(Rating ratingFwk) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onSetRating(RatingCompat.fromRating(ratingFwk));
        clearCurrentControllerInfo(sessionImpl);
      }

      @Override
      @SuppressWarnings("deprecation")
      public void onCustomAction(String action, @Nullable Bundle extras) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        ensureClassLoader(extras);
        setCurrentControllerInfo(sessionImpl);

        try {
          if (action.equals(ACTION_PLAY_FROM_URI)) {
            if (extras != null) {
              Uri uri = extras.getParcelable(ACTION_ARGUMENT_URI);
              Bundle bundle = extras.getBundle(ACTION_ARGUMENT_EXTRAS);
              ensureClassLoader(bundle);
              Callback.this.onPlayFromUri(uri, bundle);
            }
          } else if (action.equals(ACTION_PREPARE)) {
            Callback.this.onPrepare();
          } else if (action.equals(ACTION_PREPARE_FROM_MEDIA_ID)) {
            if (extras != null) {
              String mediaId = extras.getString(ACTION_ARGUMENT_MEDIA_ID);
              Bundle bundle = extras.getBundle(ACTION_ARGUMENT_EXTRAS);
              ensureClassLoader(bundle);
              Callback.this.onPrepareFromMediaId(mediaId, bundle);
            }
          } else if (action.equals(ACTION_PREPARE_FROM_SEARCH)) {
            if (extras != null) {
              String query = extras.getString(ACTION_ARGUMENT_QUERY);
              Bundle bundle = extras.getBundle(ACTION_ARGUMENT_EXTRAS);
              ensureClassLoader(bundle);
              Callback.this.onPrepareFromSearch(query, bundle);
            }
          } else if (action.equals(ACTION_PREPARE_FROM_URI)) {
            if (extras != null) {
              Uri uri = extras.getParcelable(ACTION_ARGUMENT_URI);
              Bundle bundle = extras.getBundle(ACTION_ARGUMENT_EXTRAS);
              ensureClassLoader(bundle);
              Callback.this.onPrepareFromUri(uri, bundle);
            }
          } else if (action.equals(ACTION_SET_CAPTIONING_ENABLED)) {
            if (extras != null) {
              boolean enabled = extras.getBoolean(ACTION_ARGUMENT_CAPTIONING_ENABLED);
              Callback.this.onSetCaptioningEnabled(enabled);
            }
          } else if (action.equals(ACTION_SET_REPEAT_MODE)) {
            if (extras != null) {
              int repeatMode = extras.getInt(ACTION_ARGUMENT_REPEAT_MODE);
              Callback.this.onSetRepeatMode(repeatMode);
            }
          } else if (action.equals(ACTION_SET_SHUFFLE_MODE)) {
            if (extras != null) {
              int shuffleMode = extras.getInt(ACTION_ARGUMENT_SHUFFLE_MODE);
              Callback.this.onSetShuffleMode(shuffleMode);
            }
          } else if (action.equals(ACTION_SET_RATING)) {
            if (extras != null) {
              RatingCompat rating =
                  LegacyParcelableUtil.convert(
                      extras.getParcelable(ACTION_ARGUMENT_RATING), RatingCompat.CREATOR);
              Bundle bundle = extras.getBundle(ACTION_ARGUMENT_EXTRAS);
              ensureClassLoader(bundle);
              Callback.this.onSetRating(rating, bundle);
            }
          } else if (action.equals(ACTION_SET_PLAYBACK_SPEED)) {
            if (extras != null) {
              float speed = extras.getFloat(ACTION_ARGUMENT_PLAYBACK_SPEED, 1.0f);
              Callback.this.onSetPlaybackSpeed(speed);
            }
          } else {
            Callback.this.onCustomAction(action, extras);
          }
        } catch (BadParcelableException e) {
          // The exception details will be logged by Parcel class.
          Log.e(TAG, "Could not unparcel the data.");
        }
        clearCurrentControllerInfo(sessionImpl);
      }

      @RequiresApi(24)
      @Override
      public void onPrepare() {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onPrepare();
        clearCurrentControllerInfo(sessionImpl);
      }

      @RequiresApi(24)
      @Override
      public void onPrepareFromMediaId(@Nullable String mediaId, @Nullable Bundle extras) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        ensureClassLoader(extras);
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onPrepareFromMediaId(mediaId, extras);
        clearCurrentControllerInfo(sessionImpl);
      }

      @RequiresApi(24)
      @Override
      public void onPrepareFromSearch(@Nullable String query, @Nullable Bundle extras) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        ensureClassLoader(extras);
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onPrepareFromSearch(query, extras);
        clearCurrentControllerInfo(sessionImpl);
      }

      @RequiresApi(24)
      @Override
      public void onPrepareFromUri(@Nullable Uri uri, @Nullable Bundle extras) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        ensureClassLoader(extras);
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onPrepareFromUri(uri, extras);
        clearCurrentControllerInfo(sessionImpl);
      }

      @RequiresApi(29)
      @Override
      public void onSetPlaybackSpeed(float speed) {
        MediaSessionImplApi21 sessionImpl = getSessionImplIfCallbackIsSet();
        if (sessionImpl == null) {
          return;
        }
        setCurrentControllerInfo(sessionImpl);
        Callback.this.onSetPlaybackSpeed(speed);
        clearCurrentControllerInfo(sessionImpl);
      }

      private void setCurrentControllerInfo(MediaSessionImpl sessionImpl) {
        if (Build.VERSION.SDK_INT >= 28) {
          // From API 28, this method has no effect since
          // MediaSessionImplApi28#getCurrentControllerInfo() returns controller info from
          // framework.
          return;
        }
        String packageName = sessionImpl.getCallingPackage();
        if (TextUtils.isEmpty(packageName)) {
          packageName = LEGACY_CONTROLLER;
        }
        sessionImpl.setCurrentControllerInfo(
            new RemoteUserInfo(packageName, UNKNOWN_PID, UNKNOWN_UID));
      }

      private void clearCurrentControllerInfo(MediaSessionImpl sessionImpl) {
        sessionImpl.setCurrentControllerInfo(null);
      }

      // Returns the MediaSessionImplApi21 if this callback is still set by the session.
      // This prevent callback methods to be called after session is release() or
      // callback is changed.
      @Nullable
      private MediaSessionImplApi21 getSessionImplIfCallbackIsSet() {
        MediaSessionImplApi21 sessionImpl;
        synchronized (mLock) {
          sessionImpl = (MediaSessionImplApi21) mSessionImpl.get();
        }
        return sessionImpl != null && MediaSessionCompat.Callback.this == sessionImpl.getCallback()
            ? sessionImpl
            : null;
      }
    }
  }

  /** Callback to be called when a controller has registered or unregistered controller callback. */
  public interface RegistrationCallback {
    /**
     * Called when a {@link MediaControllerCompat} registered callback.
     *
     * @param callingPid PID from Binder#getCallingPid()
     * @param callingUid UID from Binder#getCallingUid()
     */
    void onCallbackRegistered(int callingPid, int callingUid);

    /**
     * Called when a {@link MediaControllerCompat} unregistered callback.
     *
     * @param callingPid PID from Binder#getCallingPid()
     * @param callingUid UID from Binder#getCallingUid()
     */
    void onCallbackUnregistered(int callingPid, int callingUid);
  }

  /**
   * Represents an ongoing session. This may be passed to apps by the session owner to allow them to
   * create a {@link MediaControllerCompat} to communicate with the session.
   */
  @SuppressLint("BanParcelableUsage")
  public static final class Token implements Parcelable {
    private final Object mLock = new Object();
    private final Object mInner;

    @Nullable
    @GuardedBy("mLock")
    private IMediaSession mExtraBinder;

    @Nullable
    @GuardedBy("mLock")
    private VersionedParcelable mSession2Token;

    Token(Object inner) {
      this(inner, null, null);
    }

    Token(Object inner, @Nullable IMediaSession extraBinder) {
      this(inner, extraBinder, null);
    }

    Token(
        Object inner,
        @Nullable IMediaSession extraBinder,
        @Nullable VersionedParcelable session2Token) {
      mInner = inner;
      mExtraBinder = extraBinder;
      mSession2Token = session2Token;
    }

    /**
     * Creates a compat Token from a framework {@link android.media.session.MediaSession.Token}
     * object.
     *
     * <p>This method is only supported on {@link android.os.Build.VERSION_CODES#LOLLIPOP} and
     * later.
     *
     * @param token The framework token object.
     * @return A compat Token for use with {@link MediaControllerCompat}.
     */
    @RequiresApi(21)
    public static Token fromToken(Object token) {
      return fromToken(token, null);
    }

    /**
     * Creates a compat Token from a framework {@link android.media.session.MediaSession.Token}
     * object, and the extra binder.
     *
     * <p>This method is only supported on {@link android.os.Build.VERSION_CODES#LOLLIPOP} and
     * later.
     *
     * @param token The framework token object.
     * @param extraBinder The extra binder.
     * @return A compat Token for use with {@link MediaControllerCompat}.
     */
    @RequiresApi(21)
    /* package */ static Token fromToken(Object token, @Nullable IMediaSession extraBinder) {
      checkState(token != null);
      if (!(token instanceof MediaSession.Token)) {
        throw new IllegalArgumentException("token is not a valid MediaSession.Token object");
      }
      return new Token(token, extraBinder);
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      if (android.os.Build.VERSION.SDK_INT >= 21) {
        dest.writeParcelable((Parcelable) mInner, flags);
      } else {
        dest.writeStrongBinder((IBinder) mInner);
      }
    }

    @Override
    public int hashCode() {
      if (mInner == null) {
        return 0;
      }
      return mInner.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Token)) {
        return false;
      }

      Token other = (Token) obj;
      if (mInner == null) {
        return other.mInner == null;
      }
      if (other.mInner == null) {
        return false;
      }
      return mInner.equals(other.mInner);
    }

    /**
     * Gets the underlying framework {@link android.media.session.MediaSession.Token} object.
     *
     * <p>This method is only supported on API 21+.
     *
     * @return The underlying {@link android.media.session.MediaSession.Token} object, or null if
     *     none.
     */
    public Object getToken() {
      return mInner;
    }

    @Nullable
    /* package */ IMediaSession getExtraBinder() {
      synchronized (mLock) {
        return mExtraBinder;
      }
    }

    /* package */ void setExtraBinder(@Nullable IMediaSession extraBinder) {
      synchronized (mLock) {
        mExtraBinder = extraBinder;
      }
    }

    /** */
    @Nullable
    public VersionedParcelable getSession2Token() {
      synchronized (mLock) {
        return mSession2Token;
      }
    }

    /** */
    public void setSession2Token(@Nullable VersionedParcelable session2Token) {
      synchronized (mLock) {
        mSession2Token = session2Token;
      }
    }

    /** */
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      bundle.putParcelable(
          KEY_TOKEN,
          LegacyParcelableUtil.convert(
              this, android.support.v4.media.session.MediaSessionCompat.Token.CREATOR));
      synchronized (mLock) {
        if (mExtraBinder != null) {
          bundle.putBinder(KEY_EXTRA_BINDER, mExtraBinder.asBinder());
        }
        if (mSession2Token != null) {
          ParcelUtils.putVersionedParcelable(bundle, KEY_SESSION2_TOKEN, mSession2Token);
        }
      }
      return bundle;
    }

    /**
     * Creates a compat Token from a bundle object.
     *
     * @param tokenBundle
     * @return A compat Token for use with {@link MediaControllerCompat}.
     */
    @SuppressWarnings("deprecation")
    @Nullable
    public static Token fromBundle(@Nullable Bundle tokenBundle) {
      if (tokenBundle == null) {
        return null;
      }
      ensureClassLoader(tokenBundle);
      IMediaSession extraSession =
          IMediaSession.Stub.asInterface(tokenBundle.getBinder(KEY_EXTRA_BINDER));
      VersionedParcelable session2Token =
          ParcelUtils.getVersionedParcelable(tokenBundle, KEY_SESSION2_TOKEN);
      Token token = LegacyParcelableUtil.convert(tokenBundle.getParcelable(KEY_TOKEN), CREATOR);
      return token == null ? null : new Token(token.mInner, extraSession, session2Token);
    }

    public static final Parcelable.Creator<Token> CREATOR =
        new Parcelable.Creator<Token>() {
          @SuppressWarnings("deprecation")
          @Override
          public Token createFromParcel(Parcel in) {
            Object inner;
            if (android.os.Build.VERSION.SDK_INT >= 21) {
              inner = in.readParcelable(null);
            } else {
              inner = in.readStrongBinder();
            }
            return new Token(checkNotNull(inner));
          }

          @Override
          public Token[] newArray(int size) {
            return new Token[size];
          }
        };
  }

  /**
   * A single item that is part of the play queue. It contains a description of the item and its id
   * in the queue.
   */
  @SuppressLint("BanParcelableUsage")
  public static final class QueueItem implements Parcelable {
    /** This id is reserved. No items can be explicitly assigned this id. */
    public static final int UNKNOWN_ID = -1;

    private final MediaDescriptionCompat mDescription;
    private final long mId;

    @Nullable private MediaSession.QueueItem mItemFwk;

    /**
     * Creates a new {@link MediaSessionCompat.QueueItem}.
     *
     * @param description The {@link MediaDescriptionCompat} for this item.
     * @param id An identifier for this item. It must be unique within the play queue and cannot be
     *     {@link #UNKNOWN_ID}.
     */
    public QueueItem(MediaDescriptionCompat description, long id) {
      this(null, description, id);
    }

    private QueueItem(
        @Nullable MediaSession.QueueItem queueItem,
        @Nullable MediaDescriptionCompat description,
        long id) {
      if (description == null) {
        throw new IllegalArgumentException("Description cannot be null");
      }
      if (id == UNKNOWN_ID) {
        throw new IllegalArgumentException("Id cannot be QueueItem.UNKNOWN_ID");
      }
      mDescription = description;
      mId = id;
      mItemFwk = queueItem;
    }

    QueueItem(Parcel in) {
      mDescription = MediaDescriptionCompat.CREATOR.createFromParcel(in);
      mId = in.readLong();
    }

    /** Gets the description for this item. */
    public MediaDescriptionCompat getDescription() {
      return mDescription;
    }

    /** Gets the queue id for this item. */
    public long getQueueId() {
      return mId;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      mDescription.writeToParcel(dest, flags);
      dest.writeLong(mId);
    }

    @Override
    public int describeContents() {
      return 0;
    }

    /**
     * Gets the underlying {@link android.media.session.MediaSession.QueueItem}.
     *
     * <p>On builds before {@link android.os.Build.VERSION_CODES#LOLLIPOP} null is returned.
     *
     * @return The underlying {@link android.media.session.MediaSession.QueueItem} or null.
     */
    @Nullable
    public Object getQueueItem() {
      if (mItemFwk != null || android.os.Build.VERSION.SDK_INT < 21) {
        return mItemFwk;
      }
      mItemFwk =
          Api21Impl.createQueueItem((MediaDescription) mDescription.getMediaDescription(), mId);
      return mItemFwk;
    }

    /**
     * Creates an instance from a framework {@link android.media.session.MediaSession.QueueItem}
     * object.
     *
     * <p>This method is only supported on API 21+.
     *
     * @param queueItem A {@link android.media.session.MediaSession.QueueItem} object.
     * @return An equivalent {@link QueueItem} object.
     */
    @RequiresApi(21)
    public static QueueItem fromQueueItem(Object queueItem) {
      MediaSession.QueueItem queueItemObj = (MediaSession.QueueItem) queueItem;
      Object descriptionObj = Api21Impl.getDescription(queueItemObj);
      MediaDescriptionCompat description =
          MediaDescriptionCompat.fromMediaDescription(descriptionObj);
      long id = Api21Impl.getQueueId(queueItemObj);
      return new QueueItem(queueItemObj, description, id);
    }

    /**
     * Creates a list of {@link QueueItem} objects from a framework {@link
     * android.media.session.MediaSession.QueueItem} object list.
     *
     * <p>This method is only supported on API 21+. On API 20 and below, it returns null.
     *
     * @param itemList A list of {@link android.media.session.MediaSession.QueueItem} objects.
     * @return An equivalent list of {@link QueueItem} objects, or null if none.
     */
    @Nullable
    public static List<QueueItem> fromQueueItemList(
        @Nullable List<? extends @NonNull Object> itemList) {
      if (itemList == null || Build.VERSION.SDK_INT < 21) {
        return null;
      }
      List<QueueItem> items = new ArrayList<>(itemList.size());
      for (Object itemObj : itemList) {
        items.add(fromQueueItem(itemObj));
      }
      return items;
    }

    public static final Creator<MediaSessionCompat.QueueItem> CREATOR =
        new Creator<MediaSessionCompat.QueueItem>() {

          @Override
          public MediaSessionCompat.QueueItem createFromParcel(Parcel p) {
            return new MediaSessionCompat.QueueItem(p);
          }

          @Override
          public MediaSessionCompat.QueueItem[] newArray(int size) {
            return new MediaSessionCompat.QueueItem[size];
          }
        };

    @Override
    public String toString() {
      return "MediaSession.QueueItem { Description=" + mDescription + ", Id=" + mId + " }";
    }

    @RequiresApi(21)
    private static class Api21Impl {
      private Api21Impl() {}

      static MediaSession.QueueItem createQueueItem(MediaDescription description, long id) {
        return new MediaSession.QueueItem(description, id);
      }

      static MediaDescription getDescription(MediaSession.QueueItem queueItem) {
        return queueItem.getDescription();
      }

      static long getQueueId(MediaSession.QueueItem queueItem) {
        return queueItem.getQueueId();
      }
    }
  }

  /**
   * This is a wrapper for {@link ResultReceiver} for sending over aidl interfaces. The framework
   * version was not exposed to aidls until {@link android.os.Build.VERSION_CODES#LOLLIPOP}.
   */
  @SuppressLint("BanParcelableUsage")
  /* package */ static final class ResultReceiverWrapper implements Parcelable {
    ResultReceiver mResultReceiver;

    public ResultReceiverWrapper(ResultReceiver resultReceiver) {
      mResultReceiver = resultReceiver;
    }

    ResultReceiverWrapper(Parcel in) {
      mResultReceiver = ResultReceiver.CREATOR.createFromParcel(in);
    }

    public static final Creator<ResultReceiverWrapper> CREATOR =
        new Creator<ResultReceiverWrapper>() {
          @Override
          public ResultReceiverWrapper createFromParcel(Parcel p) {
            return new ResultReceiverWrapper(p);
          }

          @Override
          public ResultReceiverWrapper[] newArray(int size) {
            return new ResultReceiverWrapper[size];
          }
        };

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      mResultReceiver.writeToParcel(dest, flags);
    }
  }

  public interface OnActiveChangeListener {
    void onActiveChanged();
  }

  interface MediaSessionImpl {
    void setCallback(@Nullable Callback callback, @Nullable Handler handler);

    void setRegistrationCallback(@Nullable RegistrationCallback callback, Handler handler);

    void setFlags(@SessionFlags int flags);

    void setPlaybackToLocal(int stream);

    void setPlaybackToRemote(VolumeProviderCompat volumeProvider);

    void setActive(boolean active);

    boolean isActive();

    void sendSessionEvent(String event, @Nullable Bundle extras);

    void release();

    Token getSessionToken();

    void setPlaybackState(PlaybackStateCompat state);

    @Nullable
    PlaybackStateCompat getPlaybackState();

    void setMetadata(@Nullable MediaMetadataCompat metadata);

    void setSessionActivity(@Nullable PendingIntent pi);

    void setMediaButtonReceiver(@Nullable PendingIntent mbr);

    void setQueue(@Nullable List<QueueItem> queue);

    void setQueueTitle(CharSequence title);

    void setRatingType(@RatingCompat.Style int type);

    void setCaptioningEnabled(boolean enabled);

    void setRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode);

    void setShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode);

    void setExtras(@Nullable Bundle extras);

    @Nullable
    Object getMediaSession();

    @Nullable
    Object getRemoteControlClient();

    @Nullable
    String getCallingPackage();

    @Nullable
    RemoteUserInfo getCurrentControllerInfo();

    void setCurrentControllerInfo(@Nullable RemoteUserInfo remoteUserInfo);

    @Nullable
    Callback getCallback();
  }

  static class MediaSessionImplBase implements MediaSessionImpl {
    /***** RemoteControlClient States, we only need none as the others were public *******/
    static final int RCC_PLAYSTATE_NONE = 0;

    private final Context mContext;
    private final ComponentName mMediaButtonReceiverComponentName;
    private final PendingIntent mMediaButtonReceiverIntent;
    private final MediaSessionStub mStub;
    private final Token mToken;
    @Nullable final Bundle mSessionInfo;
    final AudioManager mAudioManager;
    final RemoteControlClient mRcc;

    final Object mLock = new Object();
    final RemoteCallbackList<IMediaControllerCallback> mControllerCallbacks =
        new RemoteCallbackList<>();

    @Nullable private MessageHandler mHandler;
    boolean mDestroyed = false;
    boolean mIsActive = false;
    @Nullable volatile Callback mCallback;
    @Nullable private RemoteUserInfo mRemoteUserInfo;

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    @Nullable
    RegistrationCallbackHandler mRegistrationCallbackHandler;

    // For backward compatibility, these flags are always set.
    @SessionFlags int mFlags = FLAG_HANDLES_MEDIA_BUTTONS | FLAG_HANDLES_TRANSPORT_CONTROLS;

    @Nullable MediaMetadataCompat mMetadata;
    @Nullable PlaybackStateCompat mState;
    @Nullable PendingIntent mSessionActivity;
    @Nullable List<QueueItem> mQueue;
    @Nullable CharSequence mQueueTitle;
    @RatingCompat.Style int mRatingType;
    boolean mCaptioningEnabled;
    @PlaybackStateCompat.RepeatMode int mRepeatMode;
    @PlaybackStateCompat.ShuffleMode int mShuffleMode;
    @Nullable Bundle mExtras;

    int mVolumeType;
    int mLocalStream;
    @Nullable VolumeProviderCompat mVolumeProvider;

    private VolumeProviderCompat.Callback mVolumeCallback =
        new VolumeProviderCompat.Callback() {
          @SuppressWarnings("method.invocation.invalid") // referencing method from constructor
          @Override
          public void onVolumeChanged(VolumeProviderCompat volumeProvider) {
            if (mVolumeProvider != volumeProvider) {
              return;
            }
            ParcelableVolumeInfo info =
                new ParcelableVolumeInfo(
                    mVolumeType,
                    mLocalStream,
                    volumeProvider.getVolumeControl(),
                    volumeProvider.getMaxVolume(),
                    volumeProvider.getCurrentVolume());
            sendVolumeInfoChanged(info);
          }
        };

    @SuppressWarnings({
      "assignment.type.incompatible",
      "argument.type.incompatible"
    }) // Sharing this in constructor
    public MediaSessionImplBase(
        Context context,
        String tag,
        ComponentName mbrComponent,
        @Nullable PendingIntent mbrIntent,
        @Nullable VersionedParcelable session2Token,
        @Nullable Bundle sessionInfo) {
      if (mbrComponent == null) {
        throw new IllegalArgumentException("MediaButtonReceiver component may not be null");
      }
      mContext = context;
      mSessionInfo = sessionInfo;
      mAudioManager = AudioManagerCompat.getAudioManager(context);
      mMediaButtonReceiverComponentName = mbrComponent;
      mMediaButtonReceiverIntent = mbrIntent;
      mStub = new MediaSessionStub(/* mediaSessionImpl= */ this, context.getPackageName(), tag);
      mToken = new Token(mStub, /* extraBinder= */ null, session2Token);

      mRatingType = RatingCompat.RATING_NONE;
      mVolumeType = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL;
      mLocalStream = AudioManager.STREAM_MUSIC;
      mRcc = new RemoteControlClient(mbrIntent);
    }

    @Override
    public void setCallback(@Nullable Callback callback, @Nullable Handler handler) {
      synchronized (mLock) {
        if (mHandler != null) {
          mHandler.removeCallbacksAndMessages(null);
        }
        mHandler =
            callback == null || handler == null ? null : new MessageHandler(handler.getLooper());
        if (mCallback != callback && mCallback != null) {
          mCallback.setSessionImpl(null, null);
        }
        mCallback = callback;
        if (mCallback != null) {
          mCallback.setSessionImpl(this, handler);
        }
      }
    }

    @Override
    public void setRegistrationCallback(@Nullable RegistrationCallback callback, Handler handler) {
      synchronized (mLock) {
        if (mRegistrationCallbackHandler != null) {
          mRegistrationCallbackHandler.removeCallbacksAndMessages(null);
        }
        if (callback != null) {
          mRegistrationCallbackHandler =
              new RegistrationCallbackHandler(handler.getLooper(), callback);
        } else {
          mRegistrationCallbackHandler = null;
        }
      }
    }

    void postToHandler(
        int what, int arg1, int arg2, @Nullable Object obj, @Nullable Bundle extras) {
      synchronized (mLock) {
        if (mHandler != null) {
          Message msg = mHandler.obtainMessage(what, arg1, arg2, obj);
          Bundle data = new Bundle();

          int uid = Binder.getCallingUid();
          data.putInt(DATA_CALLING_UID, uid);
          // Note: Different apps can have same uid, but only when they are signed with
          // the same private key. This means those apps are from the same developer.
          // Session apps can allow/reject controller by reading one of their names.
          data.putString(DATA_CALLING_PACKAGE, getPackageNameForUid(uid));
          int pid = Binder.getCallingPid();
          if (pid > 0) {
            data.putInt(DATA_CALLING_PID, pid);
          } else {
            // This cannot be happen for now, but added for future changes.
            data.putInt(DATA_CALLING_PID, UNKNOWN_PID);
          }
          if (extras != null) {
            data.putBundle(DATA_EXTRAS, extras);
          }
          msg.setData(data);
          msg.sendToTarget();
        }
      }
    }

    String getPackageNameForUid(int uid) {
      String result = mContext.getPackageManager().getNameForUid(uid);
      if (TextUtils.isEmpty(result)) {
        result = LEGACY_CONTROLLER;
      }
      return result;
    }

    @Override
    public void setFlags(@SessionFlags int flags) {
      synchronized (mLock) {
        // For backward compatibility, these flags are always set.
        mFlags = flags | FLAG_HANDLES_MEDIA_BUTTONS | FLAG_HANDLES_TRANSPORT_CONTROLS;
      }
    }

    @Override
    public void setPlaybackToLocal(int stream) {
      if (mVolumeProvider != null) {
        mVolumeProvider.setCallback(null);
      }
      mLocalStream = stream;
      mVolumeType = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL;
      ParcelableVolumeInfo info =
          new ParcelableVolumeInfo(
              mVolumeType,
              mLocalStream,
              VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,
              mAudioManager.getStreamMaxVolume(mLocalStream),
              mAudioManager.getStreamVolume(mLocalStream));
      sendVolumeInfoChanged(info);
    }

    @Override
    public void setPlaybackToRemote(VolumeProviderCompat volumeProvider) {
      if (volumeProvider == null) {
        throw new IllegalArgumentException("volumeProvider may not be null");
      }
      if (mVolumeProvider != null) {
        mVolumeProvider.setCallback(null);
      }
      mVolumeType = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE;
      mVolumeProvider = volumeProvider;
      ParcelableVolumeInfo info =
          new ParcelableVolumeInfo(
              mVolumeType,
              mLocalStream,
              mVolumeProvider.getVolumeControl(),
              mVolumeProvider.getMaxVolume(),
              mVolumeProvider.getCurrentVolume());
      sendVolumeInfoChanged(info);

      volumeProvider.setCallback(mVolumeCallback);
    }

    @Override
    public void setActive(boolean active) {
      if (active == mIsActive) {
        return;
      }
      mIsActive = active;
      updateMbrAndRcc();
    }

    @Override
    public boolean isActive() {
      return mIsActive;
    }

    @Override
    public void sendSessionEvent(String event, @Nullable Bundle extras) {
      sendEvent(event, extras);
    }

    @Override
    public void release() {
      mIsActive = false;
      mDestroyed = true;
      updateMbrAndRcc();
      sendSessionDestroyed();
      setCallback(null, null);
    }

    @Override
    public Token getSessionToken() {
      return mToken;
    }

    @Override
    public void setPlaybackState(@Nullable PlaybackStateCompat state) {
      synchronized (mLock) {
        mState = state;
      }
      sendState(state);
      if (!mIsActive) {
        // Don't set the state until after the RCC is registered
        return;
      }
      if (state == null) {
        mRcc.setPlaybackState(0);
        mRcc.setTransportControlFlags(0);
      } else {
        // Set state
        setRccState(checkNotNull(state));

        // Set transport control flags
        mRcc.setTransportControlFlags(getRccTransportControlFlagsFromActions(state.getActions()));
      }
    }

    @Nullable
    @Override
    public PlaybackStateCompat getPlaybackState() {
      synchronized (mLock) {
        return mState;
      }
    }

    void setRccState(PlaybackStateCompat state) {
      mRcc.setPlaybackState(getRccStateFromState(state.getState()));
    }

    int getRccStateFromState(int state) {
      switch (state) {
        case PlaybackStateCompat.STATE_CONNECTING:
        case PlaybackStateCompat.STATE_BUFFERING:
          return RemoteControlClient.PLAYSTATE_BUFFERING;
        case PlaybackStateCompat.STATE_ERROR:
          return RemoteControlClient.PLAYSTATE_ERROR;
        case PlaybackStateCompat.STATE_FAST_FORWARDING:
          return RemoteControlClient.PLAYSTATE_FAST_FORWARDING;
        case PlaybackStateCompat.STATE_NONE:
          return RCC_PLAYSTATE_NONE;
        case PlaybackStateCompat.STATE_PAUSED:
          return RemoteControlClient.PLAYSTATE_PAUSED;
        case PlaybackStateCompat.STATE_PLAYING:
          return RemoteControlClient.PLAYSTATE_PLAYING;
        case PlaybackStateCompat.STATE_REWINDING:
          return RemoteControlClient.PLAYSTATE_REWINDING;
        case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
          return RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS;
        case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
        case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
          return RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS;
        case PlaybackStateCompat.STATE_STOPPED:
          return RemoteControlClient.PLAYSTATE_STOPPED;
        default:
          return -1;
      }
    }

    int getRccTransportControlFlagsFromActions(long actions) {
      int transportControlFlags = 0;
      if ((actions & PlaybackStateCompat.ACTION_STOP) != 0) {
        transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_STOP;
      }
      if ((actions & PlaybackStateCompat.ACTION_PAUSE) != 0) {
        transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_PAUSE;
      }
      if ((actions & PlaybackStateCompat.ACTION_PLAY) != 0) {
        transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_PLAY;
      }
      if ((actions & PlaybackStateCompat.ACTION_REWIND) != 0) {
        transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_REWIND;
      }
      if ((actions & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
        transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS;
      }
      if ((actions & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
        transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_NEXT;
      }
      if ((actions & PlaybackStateCompat.ACTION_FAST_FORWARD) != 0) {
        transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_FAST_FORWARD;
      }
      if ((actions & PlaybackStateCompat.ACTION_PLAY_PAUSE) != 0) {
        transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE;
      }
      return transportControlFlags;
    }

    @Override
    public void setMetadata(@Nullable MediaMetadataCompat metadata) {
      if (metadata != null) {
        // Clones {@link MediaMetadataCompat} and scales down bitmaps if they are large.
        metadata = new MediaMetadataCompat.Builder(metadata, sMaxBitmapSize).build();
      }

      synchronized (mLock) {
        mMetadata = metadata;
      }
      sendMetadata(metadata);
      if (!mIsActive) {
        // Don't set metadata until after the rcc has been registered
        return;
      }
      RemoteControlClient.MetadataEditor editor =
          buildRccMetadata(metadata == null ? null : metadata.getBundle());
      editor.apply();
    }

    @SuppressWarnings({"deprecation", "argument.type.incompatible"})
    RemoteControlClient.MetadataEditor buildRccMetadata(@Nullable Bundle metadata) {
      RemoteControlClient.MetadataEditor editor = mRcc.editMetadata(true);
      if (metadata == null) {
        return editor;
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_ART)) {
        Bitmap art = metadata.getParcelable(MediaMetadataCompat.METADATA_KEY_ART);
        if (art != null) {
          // Clone the bitmap to prevent it from being recycled by RCC.
          art = art.copy(art.getConfig(), false);
        }
        editor.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, art);
      } else if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)) {
        // Fall back to album art if the track art wasn't available
        Bitmap art = metadata.getParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
        if (art != null) {
          // Clone the bitmap to prevent it from being recycled by RCC.
          art = art.copy(art.getConfig(), false);
        }
        editor.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, art);
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_ALBUM)) {
        editor.putString(
            MediaMetadataRetriever.METADATA_KEY_ALBUM,
            metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)) {
        editor.putString(
            MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST,
            metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_ARTIST)) {
        editor.putString(
            MediaMetadataRetriever.METADATA_KEY_ARTIST,
            metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_AUTHOR)) {
        editor.putString(
            MediaMetadataRetriever.METADATA_KEY_AUTHOR,
            metadata.getString(MediaMetadataCompat.METADATA_KEY_AUTHOR));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_COMPILATION)) {
        editor.putString(
            MediaMetadataRetriever.METADATA_KEY_COMPILATION,
            metadata.getString(MediaMetadataCompat.METADATA_KEY_COMPILATION));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_COMPOSER)) {
        editor.putString(
            MediaMetadataRetriever.METADATA_KEY_COMPOSER,
            metadata.getString(MediaMetadataCompat.METADATA_KEY_COMPOSER));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_DATE)) {
        editor.putString(
            MediaMetadataRetriever.METADATA_KEY_DATE,
            metadata.getString(MediaMetadataCompat.METADATA_KEY_DATE));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER)) {
        editor.putLong(
            MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER,
            metadata.getLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_DURATION)) {
        editor.putLong(
            MediaMetadataRetriever.METADATA_KEY_DURATION,
            metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_GENRE)) {
        editor.putString(
            MediaMetadataRetriever.METADATA_KEY_GENRE,
            metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_TITLE)) {
        editor.putString(
            MediaMetadataRetriever.METADATA_KEY_TITLE,
            metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER)) {
        editor.putLong(
            MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
            metadata.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_WRITER)) {
        editor.putString(
            MediaMetadataRetriever.METADATA_KEY_WRITER,
            metadata.getString(MediaMetadataCompat.METADATA_KEY_WRITER));
      }
      return editor;
    }

    @Override
    public void setSessionActivity(@Nullable PendingIntent pi) {
      synchronized (mLock) {
        mSessionActivity = pi;
      }
    }

    @Override
    public void setMediaButtonReceiver(@Nullable PendingIntent mbr) {
      // Do nothing, changing this is not supported before API 21.
    }

    @Override
    public void setQueue(@Nullable List<QueueItem> queue) {
      mQueue = queue;
      sendQueue(queue);
    }

    @Override
    public void setQueueTitle(CharSequence title) {
      mQueueTitle = title;
      sendQueueTitle(title);
    }

    @Nullable
    @Override
    public Object getMediaSession() {
      return null;
    }

    @Nullable
    @Override
    public Object getRemoteControlClient() {
      return null;
    }

    @Nullable
    @Override
    public String getCallingPackage() {
      return null;
    }

    @Override
    public void setRatingType(@RatingCompat.Style int type) {
      mRatingType = type;
    }

    @Override
    public void setCaptioningEnabled(boolean enabled) {
      if (mCaptioningEnabled != enabled) {
        mCaptioningEnabled = enabled;
        sendCaptioningEnabled(enabled);
      }
    }

    @Override
    public void setRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode) {
      if (mRepeatMode != repeatMode) {
        mRepeatMode = repeatMode;
        sendRepeatMode(repeatMode);
      }
    }

    @Override
    public void setShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
      if (mShuffleMode != shuffleMode) {
        mShuffleMode = shuffleMode;
        sendShuffleMode(shuffleMode);
      }
    }

    @Override
    public void setExtras(@Nullable Bundle extras) {
      mExtras = extras;
      sendExtras(extras);
    }

    @Nullable
    @Override
    public RemoteUserInfo getCurrentControllerInfo() {
      synchronized (mLock) {
        return mRemoteUserInfo;
      }
    }

    @Override
    public void setCurrentControllerInfo(@Nullable RemoteUserInfo remoteUserInfo) {
      synchronized (mLock) {
        mRemoteUserInfo = remoteUserInfo;
      }
    }

    @Nullable
    @Override
    public Callback getCallback() {
      synchronized (mLock) {
        return mCallback;
      }
    }

    // Registers/unregisters components as needed.
    void updateMbrAndRcc() {
      if (mIsActive) {
        // When session becomes active, register MBR and RCC.
        registerMediaButtonEventReceiver(
            mMediaButtonReceiverIntent, mMediaButtonReceiverComponentName);
        mAudioManager.registerRemoteControlClient(mRcc);

        setMetadata(mMetadata);
        setPlaybackState(mState);
      } else {
        // When inactive remove any registered components.
        unregisterMediaButtonEventReceiver(
            mMediaButtonReceiverIntent, mMediaButtonReceiverComponentName);
        // RCC keeps the state while the system resets its state internally when
        // we register RCC. Reset the state so that the states in RCC and the system
        // are in sync when we re-register the RCC.
        mRcc.setPlaybackState(0);
        mAudioManager.unregisterRemoteControlClient(mRcc);
      }
    }

    void registerMediaButtonEventReceiver(PendingIntent mbrIntent, ComponentName mbrComponent) {
      mAudioManager.registerMediaButtonEventReceiver(mbrComponent);
    }

    void unregisterMediaButtonEventReceiver(PendingIntent mbrIntent, ComponentName mbrComponent) {
      mAudioManager.unregisterMediaButtonEventReceiver(mbrComponent);
    }

    void adjustVolume(int direction, int flags) {
      if (mVolumeType == MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
        if (mVolumeProvider != null) {
          mVolumeProvider.onAdjustVolume(direction);
        }
      } else {
        mAudioManager.adjustStreamVolume(mLocalStream, direction, flags);
      }
    }

    void setVolumeTo(int value, int flags) {
      if (mVolumeType == MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
        if (mVolumeProvider != null) {
          mVolumeProvider.onSetVolumeTo(value);
        }
      } else {
        mAudioManager.setStreamVolume(mLocalStream, value, flags);
      }
    }

    void sendVolumeInfoChanged(ParcelableVolumeInfo info) {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onVolumeInfoChanged(info);
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
      }
    }

    private void sendSessionDestroyed() {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onSessionDestroyed();
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
        mControllerCallbacks.kill();
      }
    }

    private void sendEvent(String event, @Nullable Bundle extras) {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onEvent(event, extras);
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
      }
    }

    private void sendState(@Nullable PlaybackStateCompat state) {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onPlaybackStateChanged(state);
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
      }
    }

    private void sendMetadata(@Nullable MediaMetadataCompat metadata) {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onMetadataChanged(metadata);
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
      }
    }

    private void sendQueue(@Nullable List<QueueItem> queue) {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onQueueChanged(queue);
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
      }
    }

    private void sendQueueTitle(CharSequence queueTitle) {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onQueueTitleChanged(queueTitle);
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
      }
    }

    private void sendCaptioningEnabled(boolean enabled) {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onCaptioningEnabledChanged(enabled);
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
      }
    }

    private void sendRepeatMode(int repeatMode) {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onRepeatModeChanged(repeatMode);
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
      }
    }

    private void sendShuffleMode(int shuffleMode) {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onShuffleModeChanged(shuffleMode);
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
      }
    }

    private void sendExtras(@Nullable Bundle extras) {
      synchronized (mLock) {
        int size = mControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onExtrasChanged(extras);
          } catch (RemoteException e) {
          }
        }
        mControllerCallbacks.finishBroadcast();
      }
    }

    static class MediaSessionStub extends IMediaSession.Stub {

      private final AtomicReference<MediaSessionImplBase> mMediaSessionImplRef;
      private final String mPackageName;
      private final String mTag;

      MediaSessionStub(MediaSessionImplBase mediaSessionImpl, String packageName, String tag) {
        mMediaSessionImplRef = new AtomicReference<>(mediaSessionImpl);
        mPackageName = packageName;
        mTag = tag;
      }

      @Override
      public void sendCommand(
          @Nullable String command, @Nullable Bundle args, @Nullable ResultReceiverWrapper cb) {
        if (command == null) {
          return;
        }
        postToHandler(
            MessageHandler.MSG_COMMAND,
            new Command(command, args, cb == null ? null : cb.mResultReceiver));
      }

      @Override
      public boolean sendMediaButton(@Nullable KeyEvent mediaButton) {
        postToHandler(MessageHandler.MSG_MEDIA_BUTTON, mediaButton);
        return true;
      }

      @Override
      public void registerCallbackListener(@Nullable IMediaControllerCallback cb) {
        if (cb == null) {
          return;
        }
        // If this session is already destroyed tell the caller and
        // don't add them.
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl == null) {
          try {
            cb.onSessionDestroyed();
          } catch (Exception e) {
            // ignored
          }
          return;
        }
        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        RemoteUserInfo info =
            new RemoteUserInfo(
                mediaSessionImpl.getPackageNameForUid(callingUid), callingPid, callingUid);
        mediaSessionImpl.mControllerCallbacks.register(cb, info);

        synchronized (mediaSessionImpl.mLock) {
          if (mediaSessionImpl.mRegistrationCallbackHandler != null) {
            mediaSessionImpl.mRegistrationCallbackHandler.postCallbackRegistered(
                callingPid, callingUid);
          }
        }
      }

      @Override
      public void unregisterCallbackListener(@Nullable IMediaControllerCallback cb) {
        if (cb == null) {
          return;
        }
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl == null) {
          return;
        }
        mediaSessionImpl.mControllerCallbacks.unregister(cb);

        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        synchronized (mediaSessionImpl.mLock) {
          if (mediaSessionImpl.mRegistrationCallbackHandler != null) {
            mediaSessionImpl.mRegistrationCallbackHandler.postCallbackUnregistered(
                callingPid, callingUid);
          }
        }
      }

      @Override
      public String getPackageName() {
        return mPackageName;
      }

      @Nullable
      @Override
      public Bundle getSessionInfo() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        // mSessionInfo is final so doesn't need synchronize block
        return mediaSessionImpl != null && mediaSessionImpl.mSessionInfo != null
            ? new Bundle(mediaSessionImpl.mSessionInfo)
            : null;
      }

      @Override
      public String getTag() {
        // mTag is final so doesn't need synchronize block
        return mTag;
      }

      @Nullable
      @Override
      public PendingIntent getLaunchPendingIntent() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl == null) {
          return null;
        }
        synchronized (mediaSessionImpl.mLock) {
          return mediaSessionImpl.mSessionActivity;
        }
      }

      @Override
      @SessionFlags
      public long getFlags() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl == null) {
          return 0;
        }
        synchronized (mediaSessionImpl.mLock) {
          return mediaSessionImpl.mFlags;
        }
      }

      @Nullable
      @Override
      public ParcelableVolumeInfo getVolumeAttributes() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl == null) {
          return null;
        }
        synchronized (mediaSessionImpl.mLock) {
          int volumeType = mediaSessionImpl.mVolumeType;
          int stream = mediaSessionImpl.mLocalStream;
          VolumeProviderCompat vp = mediaSessionImpl.mVolumeProvider;
          int controlType;
          int max;
          int current;
          if (volumeType == MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            checkNotNull(vp);
            controlType = vp.getVolumeControl();
            max = vp.getMaxVolume();
            current = vp.getCurrentVolume();
          } else {
            controlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
            max = mediaSessionImpl.mAudioManager.getStreamMaxVolume(stream);
            current = mediaSessionImpl.mAudioManager.getStreamVolume(stream);
          }
          return new ParcelableVolumeInfo(volumeType, stream, controlType, max, current);
        }
      }

      @Override
      public void adjustVolume(int direction, int flags, @Nullable String packageName) {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl != null) {
          mediaSessionImpl.adjustVolume(direction, flags);
        }
      }

      @Override
      public void setVolumeTo(int value, int flags, @Nullable String packageName) {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl != null) {
          mediaSessionImpl.setVolumeTo(value, flags);
        }
      }

      @Override
      public void prepare() throws RemoteException {
        postToHandler(MessageHandler.MSG_PREPARE);
      }

      @Override
      public void prepareFromMediaId(@Nullable String mediaId, @Nullable Bundle extras) {
        postToHandler(MessageHandler.MSG_PREPARE_MEDIA_ID, mediaId, extras);
      }

      @Override
      public void prepareFromSearch(@Nullable String query, @Nullable Bundle extras) {
        postToHandler(MessageHandler.MSG_PREPARE_SEARCH, query, extras);
      }

      @Override
      public void prepareFromUri(@Nullable Uri uri, @Nullable Bundle extras) {
        postToHandler(MessageHandler.MSG_PREPARE_URI, uri, extras);
      }

      @Override
      public void play() throws RemoteException {
        postToHandler(MessageHandler.MSG_PLAY);
      }

      @Override
      public void playFromMediaId(@Nullable String mediaId, @Nullable Bundle extras) {
        postToHandler(MessageHandler.MSG_PLAY_MEDIA_ID, mediaId, extras);
      }

      @Override
      public void playFromSearch(@Nullable String query, @Nullable Bundle extras) {
        postToHandler(MessageHandler.MSG_PLAY_SEARCH, query, extras);
      }

      @Override
      public void playFromUri(@Nullable Uri uri, @Nullable Bundle extras) {
        postToHandler(MessageHandler.MSG_PLAY_URI, uri, extras);
      }

      @Override
      public void skipToQueueItem(long id) {
        postToHandler(MessageHandler.MSG_SKIP_TO_ITEM, id);
      }

      @Override
      public void pause() {
        postToHandler(MessageHandler.MSG_PAUSE);
      }

      @Override
      public void stop() {
        postToHandler(MessageHandler.MSG_STOP);
      }

      @Override
      public void next() {
        postToHandler(MessageHandler.MSG_NEXT);
      }

      @Override
      public void previous() {
        postToHandler(MessageHandler.MSG_PREVIOUS);
      }

      @Override
      public void fastForward() {
        postToHandler(MessageHandler.MSG_FAST_FORWARD);
      }

      @Override
      public void rewind() {
        postToHandler(MessageHandler.MSG_REWIND);
      }

      @Override
      public void seekTo(long pos) {
        postToHandler(MessageHandler.MSG_SEEK_TO, pos);
      }

      @Override
      public void rate(@Nullable RatingCompat rating) {
        postToHandler(MessageHandler.MSG_RATE, rating);
      }

      @Override
      public void rateWithExtras(@Nullable RatingCompat rating, @Nullable Bundle extras) {
        postToHandler(MessageHandler.MSG_RATE_EXTRA, rating, extras);
      }

      @Override
      public void setPlaybackSpeed(float speed) {
        postToHandler(MessageHandler.MSG_SET_PLAYBACK_SPEED, speed);
      }

      @Override
      public void setCaptioningEnabled(boolean enabled) {
        postToHandler(MessageHandler.MSG_SET_CAPTIONING_ENABLED, enabled);
      }

      @Override
      public void setRepeatMode(int repeatMode) {
        postToHandler(MessageHandler.MSG_SET_REPEAT_MODE, repeatMode);
      }

      @Override
      public void setShuffleModeEnabledRemoved(boolean enabled) {
        // Do nothing.
      }

      @Override
      public void setShuffleMode(int shuffleMode) {
        postToHandler(MessageHandler.MSG_SET_SHUFFLE_MODE, shuffleMode);
      }

      @Override
      public void sendCustomAction(@Nullable String action, @Nullable Bundle args)
          throws RemoteException {
        postToHandler(MessageHandler.MSG_CUSTOM_ACTION, action, args);
      }

      @Nullable
      @Override
      public MediaMetadataCompat getMetadata() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null ? mediaSessionImpl.mMetadata : null;
      }

      @Nullable
      @Override
      public PlaybackStateCompat getPlaybackState() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl == null) {
          return null;
        }
        PlaybackStateCompat state;
        MediaMetadataCompat metadata;
        synchronized (mediaSessionImpl.mLock) {
          state = mediaSessionImpl.mState;
          metadata = mediaSessionImpl.mMetadata;
        }
        return getStateWithUpdatedPosition(state, metadata);
      }

      @Nullable
      @Override
      public List<QueueItem> getQueue() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl == null) {
          return null;
        }
        synchronized (mediaSessionImpl.mLock) {
          return mediaSessionImpl.mQueue;
        }
      }

      @Override
      public void addQueueItem(@Nullable MediaDescriptionCompat description) {
        postToHandler(MessageHandler.MSG_ADD_QUEUE_ITEM, description);
      }

      @Override
      public void addQueueItemAt(@Nullable MediaDescriptionCompat description, int index) {
        postToHandler(MessageHandler.MSG_ADD_QUEUE_ITEM_AT, description, index, /* extras= */ null);
      }

      @Override
      public void removeQueueItem(@Nullable MediaDescriptionCompat description) {
        postToHandler(MessageHandler.MSG_REMOVE_QUEUE_ITEM, description);
      }

      @Override
      public void removeQueueItemAt(int index) {
        postToHandler(MessageHandler.MSG_REMOVE_QUEUE_ITEM_AT, index);
      }

      @Nullable
      @Override
      public CharSequence getQueueTitle() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null ? mediaSessionImpl.mQueueTitle : null;
      }

      @Nullable
      @Override
      public Bundle getExtras() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl == null) {
          return null;
        }
        synchronized (mediaSessionImpl.mLock) {
          return mediaSessionImpl.mExtras;
        }
      }

      @Override
      @RatingCompat.Style
      public int getRatingType() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null ? mediaSessionImpl.mRatingType : RatingCompat.RATING_NONE;
      }

      @Override
      public boolean isCaptioningEnabled() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null && mediaSessionImpl.mCaptioningEnabled;
      }

      @Override
      @PlaybackStateCompat.RepeatMode
      public int getRepeatMode() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null
            ? mediaSessionImpl.mRepeatMode
            : PlaybackStateCompat.REPEAT_MODE_INVALID;
      }

      @Override
      public boolean isShuffleModeEnabledRemoved() {
        return false;
      }

      @Override
      @PlaybackStateCompat.ShuffleMode
      public int getShuffleMode() {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null
            ? mediaSessionImpl.mShuffleMode
            : PlaybackStateCompat.SHUFFLE_MODE_INVALID;
      }

      @Override
      public boolean isTransportControlEnabled() {
        // All sessions should support transport control commands.
        return true;
      }

      void postToHandler(int what) {
        postToHandler(what, /* obj= */ null, /* arg1= */ 0, /* extras= */ null);
      }

      void postToHandler(int what, int arg1) {
        postToHandler(what, /* obj= */ null, arg1, /* extras= */ null);
      }

      void postToHandler(int what, @Nullable Object obj) {
        postToHandler(what, obj, /* arg1= */ 0, /* extras= */ null);
      }

      void postToHandler(int what, @Nullable Object obj, @Nullable Bundle extras) {
        postToHandler(what, obj, /* arg1= */ 0, extras);
      }

      void postToHandler(int what, @Nullable Object obj, int arg1, @Nullable Bundle extras) {
        MediaSessionImplBase mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl != null) {
          mediaSessionImpl.postToHandler(what, arg1, /* arg2= */ 0, obj, extras);
        }
      }
    }

    private static final class Command {
      public final String command;
      @Nullable public final Bundle extras;
      @Nullable public final ResultReceiver stub;

      public Command(String command, @Nullable Bundle extras, @Nullable ResultReceiver stub) {
        this.command = command;
        this.extras = extras;
        this.stub = stub;
      }
    }

    class MessageHandler extends Handler {
      // Next ID: 33
      private static final int MSG_COMMAND = 1;
      private static final int MSG_ADJUST_VOLUME = 2;
      private static final int MSG_PREPARE = 3;
      private static final int MSG_PREPARE_MEDIA_ID = 4;
      private static final int MSG_PREPARE_SEARCH = 5;
      private static final int MSG_PREPARE_URI = 6;
      private static final int MSG_PLAY = 7;
      private static final int MSG_PLAY_MEDIA_ID = 8;
      private static final int MSG_PLAY_SEARCH = 9;
      private static final int MSG_PLAY_URI = 10;
      private static final int MSG_SKIP_TO_ITEM = 11;
      private static final int MSG_PAUSE = 12;
      private static final int MSG_STOP = 13;
      private static final int MSG_NEXT = 14;
      private static final int MSG_PREVIOUS = 15;
      private static final int MSG_FAST_FORWARD = 16;
      private static final int MSG_REWIND = 17;
      private static final int MSG_SEEK_TO = 18;
      private static final int MSG_RATE = 19;
      private static final int MSG_RATE_EXTRA = 31;
      private static final int MSG_SET_PLAYBACK_SPEED = 32;
      private static final int MSG_CUSTOM_ACTION = 20;
      private static final int MSG_MEDIA_BUTTON = 21;
      private static final int MSG_SET_VOLUME = 22;
      private static final int MSG_SET_REPEAT_MODE = 23;
      private static final int MSG_ADD_QUEUE_ITEM = 25;
      private static final int MSG_ADD_QUEUE_ITEM_AT = 26;
      private static final int MSG_REMOVE_QUEUE_ITEM = 27;
      private static final int MSG_REMOVE_QUEUE_ITEM_AT = 28;
      private static final int MSG_SET_CAPTIONING_ENABLED = 29;
      private static final int MSG_SET_SHUFFLE_MODE = 30;

      // KeyEvent constants only available on API 11+
      private static final int KEYCODE_MEDIA_PAUSE = 127;
      private static final int KEYCODE_MEDIA_PLAY = 126;

      public MessageHandler(Looper looper) {
        super(looper);
      }

      @Override
      public void handleMessage(Message msg) {
        MediaSessionCompat.Callback cb = mCallback;
        if (cb == null) {
          return;
        }

        Bundle data = msg.getData();
        ensureClassLoader(data);
        setCurrentControllerInfo(
            new RemoteUserInfo(
                data.getString(DATA_CALLING_PACKAGE),
                data.getInt(DATA_CALLING_PID),
                data.getInt(DATA_CALLING_UID)));

        Bundle extras = data.getBundle(DATA_EXTRAS);
        ensureClassLoader(extras);

        try {
          switch (msg.what) {
            case MSG_COMMAND:
              Command cmd = (Command) msg.obj;
              cb.onCommand(cmd.command, cmd.extras, cmd.stub);
              break;
            case MSG_MEDIA_BUTTON:
              KeyEvent keyEvent = (KeyEvent) msg.obj;
              Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
              intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
              // Let the Callback handle events first before using the default
              // behavior
              if (!cb.onMediaButtonEvent(intent)) {
                onMediaButtonEvent(keyEvent, cb);
              }
              break;
            case MSG_PREPARE:
              cb.onPrepare();
              break;
            case MSG_PREPARE_MEDIA_ID:
              cb.onPrepareFromMediaId((String) msg.obj, extras);
              break;
            case MSG_PREPARE_SEARCH:
              cb.onPrepareFromSearch((String) msg.obj, extras);
              break;
            case MSG_PREPARE_URI:
              cb.onPrepareFromUri((Uri) msg.obj, extras);
              break;
            case MSG_PLAY:
              cb.onPlay();
              break;
            case MSG_PLAY_MEDIA_ID:
              cb.onPlayFromMediaId((String) msg.obj, extras);
              break;
            case MSG_PLAY_SEARCH:
              cb.onPlayFromSearch((String) msg.obj, extras);
              break;
            case MSG_PLAY_URI:
              cb.onPlayFromUri((Uri) msg.obj, extras);
              break;
            case MSG_SKIP_TO_ITEM:
              cb.onSkipToQueueItem((Long) msg.obj);
              break;
            case MSG_PAUSE:
              cb.onPause();
              break;
            case MSG_STOP:
              cb.onStop();
              break;
            case MSG_NEXT:
              cb.onSkipToNext();
              break;
            case MSG_PREVIOUS:
              cb.onSkipToPrevious();
              break;
            case MSG_FAST_FORWARD:
              cb.onFastForward();
              break;
            case MSG_REWIND:
              cb.onRewind();
              break;
            case MSG_SEEK_TO:
              cb.onSeekTo((Long) msg.obj);
              break;
            case MSG_RATE:
              cb.onSetRating((RatingCompat) msg.obj);
              break;
            case MSG_RATE_EXTRA:
              cb.onSetRating((RatingCompat) msg.obj, extras);
              break;
            case MSG_SET_PLAYBACK_SPEED:
              cb.onSetPlaybackSpeed((Float) msg.obj);
              break;
            case MSG_CUSTOM_ACTION:
              cb.onCustomAction((String) msg.obj, extras);
              break;
            case MSG_ADD_QUEUE_ITEM:
              cb.onAddQueueItem((MediaDescriptionCompat) msg.obj);
              break;
            case MSG_ADD_QUEUE_ITEM_AT:
              cb.onAddQueueItem((MediaDescriptionCompat) msg.obj, msg.arg1);
              break;
            case MSG_REMOVE_QUEUE_ITEM:
              cb.onRemoveQueueItem((MediaDescriptionCompat) msg.obj);
              break;
            case MSG_REMOVE_QUEUE_ITEM_AT:
              if (mQueue != null) {
                QueueItem item =
                    (msg.arg1 >= 0 && msg.arg1 < mQueue.size()) ? mQueue.get(msg.arg1) : null;
                if (item != null) {
                  cb.onRemoveQueueItem(item.getDescription());
                }
              }
              break;
            case MSG_ADJUST_VOLUME:
              adjustVolume(msg.arg1, 0);
              break;
            case MSG_SET_VOLUME:
              setVolumeTo(msg.arg1, 0);
              break;
            case MSG_SET_CAPTIONING_ENABLED:
              cb.onSetCaptioningEnabled((boolean) msg.obj);
              break;
            case MSG_SET_REPEAT_MODE:
              cb.onSetRepeatMode(msg.arg1);
              break;
            case MSG_SET_SHUFFLE_MODE:
              cb.onSetShuffleMode(msg.arg1);
              break;
          }
        } finally {
          setCurrentControllerInfo(null);
        }
      }

      private void onMediaButtonEvent(@Nullable KeyEvent ke, MediaSessionCompat.Callback cb) {
        if (ke == null || ke.getAction() != KeyEvent.ACTION_DOWN) {
          return;
        }
        long validActions = mState == null ? 0 : mState.getActions();
        switch (ke.getKeyCode()) {
          // Note KeyEvent.KEYCODE_MEDIA_PLAY is API 11+
          case KEYCODE_MEDIA_PLAY:
            if ((validActions & PlaybackStateCompat.ACTION_PLAY) != 0) {
              cb.onPlay();
            }
            break;
          // Note KeyEvent.KEYCODE_MEDIA_PAUSE is API 11+
          case KEYCODE_MEDIA_PAUSE:
            if ((validActions & PlaybackStateCompat.ACTION_PAUSE) != 0) {
              cb.onPause();
            }
            break;
          case KeyEvent.KEYCODE_MEDIA_NEXT:
            if ((validActions & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
              cb.onSkipToNext();
            }
            break;
          case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            if ((validActions & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
              cb.onSkipToPrevious();
            }
            break;
          case KeyEvent.KEYCODE_MEDIA_STOP:
            if ((validActions & PlaybackStateCompat.ACTION_STOP) != 0) {
              cb.onStop();
            }
            break;
          case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            if ((validActions & PlaybackStateCompat.ACTION_FAST_FORWARD) != 0) {
              cb.onFastForward();
            }
            break;
          case KeyEvent.KEYCODE_MEDIA_REWIND:
            if ((validActions & PlaybackStateCompat.ACTION_REWIND) != 0) {
              cb.onRewind();
            }
            break;
          case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
          case KeyEvent.KEYCODE_HEADSETHOOK:
            Log.w(TAG, "KEYCODE_MEDIA_PLAY_PAUSE and KEYCODE_HEADSETHOOK are handled already");
            break;
        }
      }
    }
  }

  static class MediaSessionImplApi18 extends MediaSessionImplBase {
    private static boolean sIsMbrPendingIntentSupported = true;

    MediaSessionImplApi18(
        Context context,
        String tag,
        ComponentName mbrComponent,
        @Nullable PendingIntent mbrIntent,
        @Nullable VersionedParcelable session2Token,
        @Nullable Bundle sessionInfo) {
      super(context, tag, mbrComponent, mbrIntent, session2Token, sessionInfo);
    }

    @SuppressWarnings("argument.type.incompatible")
    @Override
    public void setCallback(@Nullable Callback callback, @Nullable Handler handler) {
      super.setCallback(callback, handler);
      if (callback == null) {
        mRcc.setPlaybackPositionUpdateListener(null);
      } else {
        RemoteControlClient.OnPlaybackPositionUpdateListener listener =
            new RemoteControlClient.OnPlaybackPositionUpdateListener() {
              @Override
              public void onPlaybackPositionUpdate(long newPositionMs) {
                postToHandler(MessageHandler.MSG_SEEK_TO, -1, -1, newPositionMs, null);
              }
            };
        mRcc.setPlaybackPositionUpdateListener(listener);
      }
    }

    @Override
    void setRccState(PlaybackStateCompat state) {
      long position = state.getPosition();
      float speed = state.getPlaybackSpeed();
      long updateTime = state.getLastPositionUpdateTime();
      long currTime = SystemClock.elapsedRealtime();
      if (state.getState() == PlaybackStateCompat.STATE_PLAYING && position > 0) {
        long diff = 0;
        if (updateTime > 0) {
          diff = currTime - updateTime;
          if (speed > 0 && speed != 1f) {
            diff = (long) (diff * speed);
          }
        }
        position += diff;
      }
      mRcc.setPlaybackState(getRccStateFromState(state.getState()), position, speed);
    }

    @Override
    int getRccTransportControlFlagsFromActions(long actions) {
      int transportControlFlags = super.getRccTransportControlFlagsFromActions(actions);
      if ((actions & PlaybackStateCompat.ACTION_SEEK_TO) != 0) {
        transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_POSITION_UPDATE;
      }
      return transportControlFlags;
    }

    @Override
    void registerMediaButtonEventReceiver(PendingIntent mbrIntent, ComponentName mbrComponent) {
      // Some Android implementations are not able to register a media button event receiver
      // using a PendingIntent but need a ComponentName instead. These will raise a
      // NullPointerException.
      if (sIsMbrPendingIntentSupported) {
        try {
          mAudioManager.registerMediaButtonEventReceiver(mbrIntent);
        } catch (NullPointerException e) {
          Log.w(
              TAG,
              "Unable to register media button event receiver with "
                  + "PendingIntent, falling back to ComponentName.");
          sIsMbrPendingIntentSupported = false;
        }
      }

      if (!sIsMbrPendingIntentSupported) {
        super.registerMediaButtonEventReceiver(mbrIntent, mbrComponent);
      }
    }

    @Override
    void unregisterMediaButtonEventReceiver(PendingIntent mbrIntent, ComponentName mbrComponent) {
      if (sIsMbrPendingIntentSupported) {
        mAudioManager.unregisterMediaButtonEventReceiver(mbrIntent);
      } else {
        super.unregisterMediaButtonEventReceiver(mbrIntent, mbrComponent);
      }
    }
  }

  static class MediaSessionImplApi19 extends MediaSessionImplApi18 {
    MediaSessionImplApi19(
        Context context,
        String tag,
        ComponentName mbrComponent,
        @Nullable PendingIntent mbrIntent,
        @Nullable VersionedParcelable session2Token,
        @Nullable Bundle sessionInfo) {
      super(context, tag, mbrComponent, mbrIntent, session2Token, sessionInfo);
    }

    @SuppressWarnings("argument.type.incompatible")
    @Override
    public void setCallback(@Nullable Callback callback, @Nullable Handler handler) {
      super.setCallback(callback, handler);
      if (callback == null) {
        mRcc.setMetadataUpdateListener(null);
      } else {
        RemoteControlClient.OnMetadataUpdateListener listener =
            new RemoteControlClient.OnMetadataUpdateListener() {
              @Override
              public void onMetadataUpdate(int key, Object newValue) {
                if (key == MediaMetadataEditor.RATING_KEY_BY_USER && newValue instanceof Rating) {
                  postToHandler(
                      MessageHandler.MSG_RATE, -1, -1, RatingCompat.fromRating(newValue), null);
                }
              }
            };
        mRcc.setMetadataUpdateListener(listener);
      }
    }

    @Override
    int getRccTransportControlFlagsFromActions(long actions) {
      int transportControlFlags = super.getRccTransportControlFlagsFromActions(actions);
      if ((actions & PlaybackStateCompat.ACTION_SET_RATING) != 0) {
        transportControlFlags |= RemoteControlClient.FLAG_KEY_MEDIA_RATING;
      }
      return transportControlFlags;
    }

    @Override
    @SuppressWarnings({"deprecation", "argument.type.incompatible"})
    RemoteControlClient.MetadataEditor buildRccMetadata(@Nullable Bundle metadata) {
      RemoteControlClient.MetadataEditor editor = super.buildRccMetadata(metadata);
      long actions = mState == null ? 0 : mState.getActions();
      if ((actions & PlaybackStateCompat.ACTION_SET_RATING) != 0) {
        editor.addEditableKey(RemoteControlClient.MetadataEditor.RATING_KEY_BY_USER);
      }

      if (metadata == null) {
        return editor;
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_YEAR)) {
        editor.putLong(
            MediaMetadataRetriever.METADATA_KEY_YEAR,
            metadata.getLong(MediaMetadataCompat.METADATA_KEY_YEAR));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_RATING)) {
        // Do not remove casting here. Without this, a crash will happen in API 19.
        ((MediaMetadataEditor) editor)
            .putObject(
                MediaMetadataEditor.RATING_KEY_BY_OTHERS,
                LegacyParcelableUtil.convert(
                    metadata.getParcelable(MediaMetadataCompat.METADATA_KEY_RATING),
                    RatingCompat.CREATOR));
      }
      if (metadata.containsKey(MediaMetadataCompat.METADATA_KEY_USER_RATING)) {
        // Do not remove casting here. Without this, a crash will happen in API 19.
        ((MediaMetadataEditor) editor)
            .putObject(
                MediaMetadataEditor.RATING_KEY_BY_USER,
                LegacyParcelableUtil.convert(
                    metadata.getParcelable(MediaMetadataCompat.METADATA_KEY_USER_RATING),
                    RatingCompat.CREATOR));
      }
      return editor;
    }
  }

  @RequiresApi(21)
  static class MediaSessionImplApi21 implements MediaSessionImpl {
    final MediaSession mSessionFwk;
    final ExtraSession mExtraSession;
    final Token mToken;
    final Object mLock = new Object();
    @Nullable Bundle mSessionInfo;

    boolean mDestroyed = false;
    final RemoteCallbackList<IMediaControllerCallback> mExtraControllerCallbacks =
        new RemoteCallbackList<>();

    @Nullable PlaybackStateCompat mPlaybackState;
    @Nullable List<QueueItem> mQueue;
    @Nullable MediaMetadataCompat mMetadata;
    @RatingCompat.Style int mRatingType;
    boolean mCaptioningEnabled;
    @PlaybackStateCompat.RepeatMode int mRepeatMode;
    @PlaybackStateCompat.ShuffleMode int mShuffleMode;

    @Nullable
    @GuardedBy("mLock")
    Callback mCallback;

    @Nullable
    @GuardedBy("mLock")
    RegistrationCallbackHandler mRegistrationCallbackHandler;

    @Nullable
    @GuardedBy("mLock")
    RemoteUserInfo mRemoteUserInfo;

    // Sharing this in constructor
    @SuppressWarnings({
      "method.invocation.invalid",
      "assignment.type.incompatible",
      "argument.type.incompatible"
    })
    MediaSessionImplApi21(
        Context context,
        String tag,
        @Nullable VersionedParcelable session2Token,
        @Nullable Bundle sessionInfo) {
      mSessionFwk = createFwkMediaSession(context, tag, sessionInfo);
      mExtraSession = new ExtraSession(/* mediaSessionImpl= */ this);
      mToken = new Token(mSessionFwk.getSessionToken(), mExtraSession, session2Token);
      mSessionInfo = sessionInfo;
      // For backward compatibility, these flags are always set.
      setFlags(FLAG_HANDLES_MEDIA_BUTTONS | FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    // Sharing this in constructor
    @SuppressWarnings({
      "method.invocation.invalid",
      "assignment.type.incompatible",
      "argument.type.incompatible"
    })
    MediaSessionImplApi21(Object mediaSession) {
      if (!(mediaSession instanceof MediaSession)) {
        throw new IllegalArgumentException("mediaSession is not a valid MediaSession object");
      }
      mSessionFwk = (MediaSession) mediaSession;
      mExtraSession = new ExtraSession(/* mediaSessionImpl= */ this);
      mToken = new Token(mSessionFwk.getSessionToken(), mExtraSession);
      mSessionInfo = null;
      // For backward compatibility, these flags are always set.
      setFlags(FLAG_HANDLES_MEDIA_BUTTONS | FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    public MediaSession createFwkMediaSession(
        Context context, String tag, @Nullable Bundle sessionInfo) {
      return new MediaSession(context, tag);
    }

    @Override
    public void setCallback(@Nullable Callback callback, @Nullable Handler handler) {
      synchronized (mLock) {
        mCallback = callback;
        mSessionFwk.setCallback(callback == null ? null : callback.mCallbackFwk, handler);
        if (callback != null) {
          callback.setSessionImpl(this, handler);
        }
      }
    }

    @Override
    public void setRegistrationCallback(@Nullable RegistrationCallback callback, Handler handler) {
      synchronized (mLock) {
        if (mRegistrationCallbackHandler != null) {
          mRegistrationCallbackHandler.removeCallbacksAndMessages(null);
        }
        if (callback != null) {
          mRegistrationCallbackHandler =
              new RegistrationCallbackHandler(handler.getLooper(), callback);
        } else {
          mRegistrationCallbackHandler = null;
        }
      }
    }

    @SuppressLint("WrongConstant")
    @Override
    public void setFlags(@SessionFlags int flags) {
      // For backward compatibility, always set these deprecated flags.
      mSessionFwk.setFlags(flags | FLAG_HANDLES_MEDIA_BUTTONS | FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    @Override
    public void setPlaybackToLocal(int stream) {
      // TODO update APIs to use support version of AudioAttributes
      AudioAttributes.Builder bob = new AudioAttributes.Builder();
      bob.setLegacyStreamType(stream);
      mSessionFwk.setPlaybackToLocal(bob.build());
    }

    @Override
    public void setPlaybackToRemote(VolumeProviderCompat volumeProvider) {
      mSessionFwk.setPlaybackToRemote((VolumeProvider) volumeProvider.getVolumeProvider());
    }

    @Override
    public void setActive(boolean active) {
      mSessionFwk.setActive(active);
    }

    @Override
    public boolean isActive() {
      return mSessionFwk.isActive();
    }

    @Override
    public void sendSessionEvent(String event, @Nullable Bundle extras) {
      if (android.os.Build.VERSION.SDK_INT < 23) {
        synchronized (mLock) {
          int size = mExtraControllerCallbacks.beginBroadcast();
          for (int i = size - 1; i >= 0; i--) {
            IMediaControllerCallback cb = mExtraControllerCallbacks.getBroadcastItem(i);
            try {
              cb.onEvent(event, extras);
            } catch (RemoteException e) {
            }
          }
          mExtraControllerCallbacks.finishBroadcast();
        }
      }
      mSessionFwk.sendSessionEvent(event, extras);
    }

    @Override
    public void release() {
      mDestroyed = true;
      mExtraControllerCallbacks.kill();
      if (Build.VERSION.SDK_INT == 27) {
        // This is a workaround for framework MediaSession's bug in API 27.
        try {
          @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
          Field callback = mSessionFwk.getClass().getDeclaredField("mCallback");
          callback.setAccessible(true);
          Handler handler = (Handler) callback.get(mSessionFwk);
          if (handler != null) {
            handler.removeCallbacksAndMessages(null);
          }
        } catch (Exception e) {
          Log.w(TAG, "Exception happened while accessing MediaSession.mCallback.", e);
        }
      }
      // Prevent from receiving callbacks from released session.
      mSessionFwk.setCallback(null);
      mExtraSession.release();
      mSessionFwk.release();
    }

    @Override
    public Token getSessionToken() {
      return mToken;
    }

    @Override
    public void setPlaybackState(PlaybackStateCompat state) {
      mPlaybackState = state;
      synchronized (mLock) {
        int size = mExtraControllerCallbacks.beginBroadcast();
        for (int i = size - 1; i >= 0; i--) {
          IMediaControllerCallback cb = mExtraControllerCallbacks.getBroadcastItem(i);
          try {
            cb.onPlaybackStateChanged(state);
          } catch (RemoteException e) {
          }
        }
        mExtraControllerCallbacks.finishBroadcast();
      }
      mSessionFwk.setPlaybackState(state == null ? null : (PlaybackState) state.getPlaybackState());
    }

    @Nullable
    @Override
    public PlaybackStateCompat getPlaybackState() {
      return mPlaybackState;
    }

    @Override
    public void setMetadata(@Nullable MediaMetadataCompat metadata) {
      mMetadata = metadata;
      mSessionFwk.setMetadata(
          metadata == null ? null : (MediaMetadata) metadata.getMediaMetadata());
    }

    @Override
    public void setSessionActivity(@Nullable PendingIntent pi) {
      mSessionFwk.setSessionActivity(pi);
    }

    @Override
    public void setMediaButtonReceiver(@Nullable PendingIntent mbr) {
      mSessionFwk.setMediaButtonReceiver(mbr);
    }

    @Override
    public void setQueue(@Nullable List<QueueItem> queue) {
      mQueue = queue;
      if (queue == null) {
        mSessionFwk.setQueue(null);
        return;
      }
      ArrayList<MediaSession.QueueItem> queueItemFwks = new ArrayList<>(queue.size());
      for (QueueItem item : queue) {
        queueItemFwks.add((MediaSession.QueueItem) checkNotNull(item.getQueueItem()));
      }
      mSessionFwk.setQueue(queueItemFwks);
    }

    @Override
    public void setQueueTitle(CharSequence title) {
      mSessionFwk.setQueueTitle(title);
    }

    @Override
    public void setRatingType(@RatingCompat.Style int type) {
      mRatingType = type;
    }

    @Override
    public void setCaptioningEnabled(boolean enabled) {
      if (mCaptioningEnabled != enabled) {
        mCaptioningEnabled = enabled;
        synchronized (mLock) {
          int size = mExtraControllerCallbacks.beginBroadcast();
          for (int i = size - 1; i >= 0; i--) {
            IMediaControllerCallback cb = mExtraControllerCallbacks.getBroadcastItem(i);
            try {
              cb.onCaptioningEnabledChanged(enabled);
            } catch (RemoteException e) {
            }
          }
          mExtraControllerCallbacks.finishBroadcast();
        }
      }
    }

    @Override
    public void setRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode) {
      if (mRepeatMode != repeatMode) {
        mRepeatMode = repeatMode;
        synchronized (mLock) {
          int size = mExtraControllerCallbacks.beginBroadcast();
          for (int i = size - 1; i >= 0; i--) {
            IMediaControllerCallback cb = mExtraControllerCallbacks.getBroadcastItem(i);
            try {
              cb.onRepeatModeChanged(repeatMode);
            } catch (RemoteException e) {
            }
          }
          mExtraControllerCallbacks.finishBroadcast();
        }
      }
    }

    @Override
    public void setShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
      if (mShuffleMode != shuffleMode) {
        mShuffleMode = shuffleMode;
        synchronized (mLock) {
          int size = mExtraControllerCallbacks.beginBroadcast();
          for (int i = size - 1; i >= 0; i--) {
            IMediaControllerCallback cb = mExtraControllerCallbacks.getBroadcastItem(i);
            try {
              cb.onShuffleModeChanged(shuffleMode);
            } catch (RemoteException e) {
            }
          }
          mExtraControllerCallbacks.finishBroadcast();
        }
      }
    }

    @Override
    public void setExtras(@Nullable Bundle extras) {
      mSessionFwk.setExtras(extras);
    }

    @Nullable
    @Override
    public Object getMediaSession() {
      return mSessionFwk;
    }

    @Nullable
    @Override
    public Object getRemoteControlClient() {
      // Note: When this returns something, {@link MediaSessionCompatCallbackTest} and
      //       {@link #setCurrentUserInfoOverride} should be also updated.
      return null;
    }

    @Override
    public void setCurrentControllerInfo(@Nullable RemoteUserInfo remoteUserInfo) {
      synchronized (mLock) {
        mRemoteUserInfo = remoteUserInfo;
      }
    }

    @Nullable
    @Override
    public String getCallingPackage() {
      if (android.os.Build.VERSION.SDK_INT < 24) {
        return null;
      } else {
        try {
          Method getCallingPackageMethod = mSessionFwk.getClass().getMethod("getCallingPackage");
          return (String) getCallingPackageMethod.invoke(mSessionFwk);
        } catch (Exception e) {
          Log.e(TAG, "Cannot execute MediaSession.getCallingPackage()", e);
        }
        return null;
      }
    }

    @Nullable
    @Override
    public RemoteUserInfo getCurrentControllerInfo() {
      synchronized (mLock) {
        return mRemoteUserInfo;
      }
    }

    @Nullable
    @Override
    public Callback getCallback() {
      synchronized (mLock) {
        return mCallback;
      }
    }

    private static class ExtraSession extends IMediaSession.Stub {

      private final AtomicReference<@NullableType MediaSessionImplApi21> mMediaSessionImplRef;

      ExtraSession(MediaSessionImplApi21 mediaSessionImpl) {
        mMediaSessionImplRef = new AtomicReference<>(mediaSessionImpl);
      }

      /** Clears the reference to the containing component in order to enable garbage collection. */
      @SuppressWarnings("argument.type.incompatible") // Resetting variable to null
      public void release() {
        mMediaSessionImplRef.set(null);
      }

      @Override
      public void sendCommand(
          @Nullable String command, @Nullable Bundle args, @Nullable ResultReceiverWrapper cb) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public boolean sendMediaButton(@Nullable KeyEvent mediaButton) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void registerCallbackListener(@Nullable IMediaControllerCallback cb) {
        MediaSessionImplApi21 mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl == null || cb == null) {
          return;
        }
        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        RemoteUserInfo info =
            new RemoteUserInfo(RemoteUserInfo.LEGACY_CONTROLLER, callingPid, callingUid);
        mediaSessionImpl.mExtraControllerCallbacks.register(cb, info);
        synchronized (mediaSessionImpl.mLock) {
          if (mediaSessionImpl.mRegistrationCallbackHandler != null) {
            mediaSessionImpl.mRegistrationCallbackHandler.postCallbackRegistered(
                callingPid, callingUid);
          }
        }
      }

      @Override
      public void unregisterCallbackListener(@Nullable IMediaControllerCallback cb) {
        MediaSessionImplApi21 mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl == null || cb == null) {
          return;
        }
        mediaSessionImpl.mExtraControllerCallbacks.unregister(cb);

        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        synchronized (mediaSessionImpl.mLock) {
          if (mediaSessionImpl.mRegistrationCallbackHandler != null) {
            mediaSessionImpl.mRegistrationCallbackHandler.postCallbackUnregistered(
                callingPid, callingUid);
          }
        }
      }

      @Override
      public String getPackageName() {
        // Will not be called.
        throw new AssertionError();
      }

      @Nullable
      @Override
      public Bundle getSessionInfo() {
        MediaSessionImplApi21 mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null && mediaSessionImpl.mSessionInfo != null
            ? new Bundle(mediaSessionImpl.mSessionInfo)
            : null;
      }

      @Override
      public String getTag() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public PendingIntent getLaunchPendingIntent() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      @SessionFlags
      public long getFlags() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public ParcelableVolumeInfo getVolumeAttributes() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void adjustVolume(int direction, int flags, @Nullable String packageName) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void setVolumeTo(int value, int flags, @Nullable String packageName) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void prepare() throws RemoteException {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void prepareFromMediaId(@Nullable String mediaId, @Nullable Bundle extras) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void prepareFromSearch(@Nullable String query, @Nullable Bundle extras) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void prepareFromUri(@Nullable Uri uri, @Nullable Bundle extras) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void play() throws RemoteException {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void playFromMediaId(@Nullable String mediaId, @Nullable Bundle extras) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void playFromSearch(@Nullable String query, @Nullable Bundle extras) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void playFromUri(@Nullable Uri uri, @Nullable Bundle extras) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void skipToQueueItem(long id) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void pause() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void stop() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void next() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void previous() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void fastForward() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void rewind() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void seekTo(long pos) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void rate(@Nullable RatingCompat rating) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void rateWithExtras(@Nullable RatingCompat rating, @Nullable Bundle extras) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void setPlaybackSpeed(float speed) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void setCaptioningEnabled(boolean enabled) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void setRepeatMode(int repeatMode) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void setShuffleModeEnabledRemoved(boolean enabled) {
        // Do nothing.
      }

      @Override
      public void setShuffleMode(int shuffleMode) throws RemoteException {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void sendCustomAction(@Nullable String action, @Nullable Bundle args)
          throws RemoteException {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public MediaMetadataCompat getMetadata() {
        // Will not be called.
        throw new AssertionError();
      }

      @Nullable
      @Override
      public PlaybackStateCompat getPlaybackState() {
        MediaSessionImplApi21 mediaSessionImpl = mMediaSessionImplRef.get();
        if (mediaSessionImpl != null) {
          return getStateWithUpdatedPosition(
              mediaSessionImpl.mPlaybackState, mediaSessionImpl.mMetadata);
        } else {
          return null;
        }
      }

      @Nullable
      @Override
      public List<QueueItem> getQueue() {
        // Will not be called.
        return null;
      }

      @Override
      public void addQueueItem(@Nullable MediaDescriptionCompat descriptionCompat) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void addQueueItemAt(@Nullable MediaDescriptionCompat descriptionCompat, int index) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void removeQueueItem(@Nullable MediaDescriptionCompat description) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void removeQueueItemAt(int index) {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public CharSequence getQueueTitle() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public Bundle getExtras() {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      @RatingCompat.Style
      public int getRatingType() {
        MediaSessionImplApi21 mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null ? mediaSessionImpl.mRatingType : RatingCompat.RATING_NONE;
      }

      @Override
      public boolean isCaptioningEnabled() {
        MediaSessionImplApi21 mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null && mediaSessionImpl.mCaptioningEnabled;
      }

      @Override
      @PlaybackStateCompat.RepeatMode
      public int getRepeatMode() {
        MediaSessionImplApi21 mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null
            ? mediaSessionImpl.mRepeatMode
            : PlaybackStateCompat.REPEAT_MODE_INVALID;
      }

      @Override
      public boolean isShuffleModeEnabledRemoved() {
        return false;
      }

      @Override
      @PlaybackStateCompat.ShuffleMode
      public int getShuffleMode() {
        MediaSessionImplApi21 mediaSessionImpl = mMediaSessionImplRef.get();
        return mediaSessionImpl != null
            ? mediaSessionImpl.mShuffleMode
            : PlaybackStateCompat.SHUFFLE_MODE_INVALID;
      }

      @Override
      public boolean isTransportControlEnabled() {
        // Will not be called.
        throw new AssertionError();
      }
    }
  }

  @RequiresApi(22)
  static class MediaSessionImplApi22 extends MediaSessionImplApi21 {
    MediaSessionImplApi22(
        Context context,
        String tag,
        @Nullable VersionedParcelable session2Token,
        @Nullable Bundle sessionInfo) {
      super(context, tag, session2Token, sessionInfo);
    }

    MediaSessionImplApi22(Object mediaSession) {
      super(mediaSession);
    }

    @Override
    public void setRatingType(@RatingCompat.Style int type) {
      mSessionFwk.setRatingType(type);
    }
  }

  @RequiresApi(28)
  static class MediaSessionImplApi28 extends MediaSessionImplApi22 {
    MediaSessionImplApi28(
        Context context,
        String tag,
        @Nullable VersionedParcelable session2Token,
        @Nullable Bundle sessionInfo) {
      super(context, tag, session2Token, sessionInfo);
    }

    MediaSessionImplApi28(Object mediaSession) {
      super(mediaSession);
    }

    @Override
    public void setCurrentControllerInfo(@Nullable RemoteUserInfo remoteUserInfo) {
      // No-op. {@link MediaSession#getCurrentControllerInfo} would work.
    }

    @Nullable
    @Override
    public final RemoteUserInfo getCurrentControllerInfo() {
      android.media.session.MediaSessionManager.RemoteUserInfo info =
          ((MediaSession) mSessionFwk).getCurrentControllerInfo();
      return new RemoteUserInfo(info);
    }
  }

  @RequiresApi(29)
  static class MediaSessionImplApi29 extends MediaSessionImplApi28 {
    MediaSessionImplApi29(
        Context context,
        String tag,
        @Nullable VersionedParcelable session2Token,
        @Nullable Bundle sessionInfo) {
      super(context, tag, session2Token, sessionInfo);
    }

    MediaSessionImplApi29(Object mediaSession) {
      super(mediaSession);
      mSessionInfo = ((MediaSession) mediaSession).getController().getSessionInfo();
    }

    @Override
    public MediaSession createFwkMediaSession(
        Context context, String tag, @Nullable Bundle sessionInfo) {
      return new MediaSession(context, tag, sessionInfo);
    }
  }

  static final class RegistrationCallbackHandler extends Handler {
    private static final int MSG_CALLBACK_REGISTERED = 1001;
    private static final int MSG_CALLBACK_UNREGISTERED = 1002;

    private final RegistrationCallback mCallback;

    RegistrationCallbackHandler(Looper looper, RegistrationCallback callback) {
      super(looper);
      mCallback = callback;
    }

    @Override
    public void handleMessage(Message msg) {
      super.handleMessage(msg);
      switch (msg.what) {
        case MSG_CALLBACK_REGISTERED:
          mCallback.onCallbackRegistered(msg.arg1, msg.arg2);
          break;
        case MSG_CALLBACK_UNREGISTERED:
          mCallback.onCallbackUnregistered(msg.arg1, msg.arg2);
          break;
      }
    }

    public void postCallbackRegistered(int callingPid, int callingUid) {
      obtainMessage(MSG_CALLBACK_REGISTERED, callingPid, callingUid).sendToTarget();
    }

    public void postCallbackUnregistered(int callingPid, int callingUid) {
      obtainMessage(MSG_CALLBACK_UNREGISTERED, callingPid, callingUid).sendToTarget();
    }
  }
}
