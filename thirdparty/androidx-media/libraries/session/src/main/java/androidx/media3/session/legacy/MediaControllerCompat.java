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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.R;
import androidx.media3.session.legacy.MediaSessionCompat.QueueItem;
import androidx.media3.session.legacy.PlaybackStateCompat.CustomAction;
import androidx.versionedparcelable.ParcelUtils;
import androidx.versionedparcelable.VersionedParcelable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Allows an app to interact with an ongoing media session. Media buttons and other commands can be
 * sent to the session. A callback may be registered to receive updates from the session, such as
 * metadata and play state changes.
 *
 * <p>A MediaController can be created if you have a {@link MediaSessionCompat.Token} from the
 * session owner.
 *
 * <p>MediaController objects are thread-safe.
 *
 * <p>This is a helper for accessing features in {@link android.media.session.MediaSession}
 * introduced after API level 4 in a backwards compatible fashion.
 *
 * <p class="note">If MediaControllerCompat is created with a {@link MediaSessionCompat.Token
 * session token} from another process, following methods will not work directly after the creation
 * if the {@link MediaSessionCompat.Token session token} is not passed through a {@link
 * MediaBrowserCompat}:
 *
 * <ul>
 *   <li>{@link #getPlaybackState()}.{@link PlaybackStateCompat#getExtras() getExtras()}
 *   <li>{@link #getRatingType()}
 *   <li>{@link #getRepeatMode()}
 *   <li>{@link #getSessionInfo()}
 *   <li>{@link #getShuffleMode()}
 *   <li>{@link #isCaptioningEnabled()}
 * </ul>
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
public final class MediaControllerCompat {
  static final String TAG = "MediaControllerCompat";

  /** */
  public static final String COMMAND_GET_EXTRA_BINDER =
      "android.support.v4.media.session.command.GET_EXTRA_BINDER";

  /** */
  public static final String COMMAND_ADD_QUEUE_ITEM =
      "android.support.v4.media.session.command.ADD_QUEUE_ITEM";

  /** */
  public static final String COMMAND_ADD_QUEUE_ITEM_AT =
      "android.support.v4.media.session.command.ADD_QUEUE_ITEM_AT";

  /** */
  public static final String COMMAND_REMOVE_QUEUE_ITEM =
      "android.support.v4.media.session.command.REMOVE_QUEUE_ITEM";

  /** */
  public static final String COMMAND_REMOVE_QUEUE_ITEM_AT =
      "android.support.v4.media.session.command.REMOVE_QUEUE_ITEM_AT";

  /** */
  public static final String COMMAND_ARGUMENT_MEDIA_DESCRIPTION =
      "android.support.v4.media.session.command.ARGUMENT_MEDIA_DESCRIPTION";

  /** */
  public static final String COMMAND_ARGUMENT_INDEX =
      "android.support.v4.media.session.command.ARGUMENT_INDEX";

  /**
   * Sets a {@link MediaControllerCompat} in the {@code activity} for later retrieval via {@link
   * #getMediaController(Activity)}.
   *
   * <p>On API 21 and later, {@link Activity#setMediaController(MediaController)} will also be
   * called.
   *
   * @param activity The activity to set the {@code mediaController} in, must not be null.
   * @param mediaController The controller for the session which should receive media keys and
   *     volume changes on API 21 and later.
   * @see #getMediaController(Activity)
   * @see Activity#setMediaController(android.media.session.MediaController)
   */
  public static void setMediaController(Activity activity, MediaControllerCompat mediaController) {
    activity
        .getWindow()
        .getDecorView()
        .setTag(R.id.media_controller_compat_view_tag, mediaController);
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      MediaControllerImplApi21.setMediaController(activity, mediaController);
    }
  }

  /**
   * Retrieves the {@link MediaControllerCompat} set in the activity by {@link
   * #setMediaController(Activity, MediaControllerCompat)} for sending media key and volume events.
   *
   * <p>This is compatible with {@link Activity#getMediaController()}.
   *
   * @param activity The activity to get the media controller from, must not be null.
   * @return The controller which should receive events.
   * @see #setMediaController(Activity, MediaControllerCompat)
   */
  @Nullable
  public static MediaControllerCompat getMediaController(Activity activity) {
    Object tag = activity.getWindow().getDecorView().getTag(R.id.media_controller_compat_view_tag);
    if (tag instanceof MediaControllerCompat) {
      return (MediaControllerCompat) tag;
    } else if (android.os.Build.VERSION.SDK_INT >= 21) {
      return MediaControllerImplApi21.getMediaController(activity);
    }
    return null;
  }

  @SuppressWarnings("WeakerAccess") /* synthetic access */
  static void validateCustomAction(@Nullable String action, @Nullable Bundle args) {
    if (action == null) {
      return;
    }
    switch (action) {
      case MediaSessionCompat.ACTION_FOLLOW:
      case MediaSessionCompat.ACTION_UNFOLLOW:
        if (args == null || !args.containsKey(MediaSessionCompat.ARGUMENT_MEDIA_ATTRIBUTE)) {
          throw new IllegalArgumentException(
              "An extra field "
                  + MediaSessionCompat.ARGUMENT_MEDIA_ATTRIBUTE
                  + " is required "
                  + "for this action "
                  + action
                  + ".");
        }
        break;
    }
  }

  private final MediaControllerImpl mImpl;
  private final MediaSessionCompat.Token mToken;
  // This set is used to keep references to registered callbacks to prevent them being GCed,
  // since we only keep weak references for callbacks in this class and its inner classes.
  private final Set<Callback> mRegisteredCallbacks;

  /**
   * Creates a media controller from a session.
   *
   * @param context A context.
   * @param session The session to be controlled.
   */
  public MediaControllerCompat(Context context, MediaSessionCompat session) {
    this(context, session.getSessionToken());
  }

  /**
   * Creates a media controller from a session token which may have been obtained from another
   * process.
   *
   * @param context A context.
   * @param sessionToken The token of the session to be controlled.
   */
  public MediaControllerCompat(Context context, MediaSessionCompat.Token sessionToken) {
    if (sessionToken == null) {
      throw new IllegalArgumentException("sessionToken must not be null");
    }
    mRegisteredCallbacks = Collections.synchronizedSet(new HashSet<>());
    mToken = sessionToken;

    if (Build.VERSION.SDK_INT >= 29) {
      mImpl = new MediaControllerImplApi29(context, sessionToken);
    } else if (Build.VERSION.SDK_INT >= 21) {
      mImpl = new MediaControllerImplApi21(context, sessionToken);
    } else {
      mImpl = new MediaControllerImplBase(sessionToken);
    }
  }

  /**
   * Gets a {@link TransportControls} instance for this session.
   *
   * @return A controls instance
   */
  public TransportControls getTransportControls() {
    return mImpl.getTransportControls();
  }

  /**
   * Sends the specified media button event to the session. Only media keys can be sent by this
   * method, other keys will be ignored.
   *
   * @param keyEvent The media button event to dispatch.
   * @return true if the event was sent to the session, false otherwise.
   */
  public boolean dispatchMediaButtonEvent(@Nullable KeyEvent keyEvent) {
    if (keyEvent == null) {
      throw new IllegalArgumentException("KeyEvent may not be null");
    }
    return mImpl.dispatchMediaButtonEvent(keyEvent);
  }

  /**
   * Gets the current playback state for this session.
   *
   * <p>If the session is not ready, {@link PlaybackStateCompat#getExtras()} on the result of this
   * method may return null.
   *
   * @return The current PlaybackState or null
   * @see #isSessionReady
   * @see Callback#onSessionReady
   */
  @Nullable
  public PlaybackStateCompat getPlaybackState() {
    return mImpl.getPlaybackState();
  }

  /**
   * Gets the current metadata for this session.
   *
   * @return The current MediaMetadata or null.
   */
  @Nullable
  public MediaMetadataCompat getMetadata() {
    return mImpl.getMetadata();
  }

  /**
   * Gets the current play queue for this session if one is set. If you only care about the current
   * item {@link #getMetadata()} should be used.
   *
   * @return The current play queue or null.
   */
  @Nullable
  public List<QueueItem> getQueue() {
    return mImpl.getQueue();
  }

  /**
   * Adds a queue item from the given {@code description} at the end of the play queue of this
   * session. Not all sessions may support this. To know whether the session supports this, get the
   * session's flags with {@link #getFlags()} and check that the flag {@link
   * MediaSessionCompat#FLAG_HANDLES_QUEUE_COMMANDS} is set.
   *
   * @param description The {@link MediaDescriptionCompat} for creating the {@link
   *     MediaSessionCompat.QueueItem} to be inserted.
   * @throws UnsupportedOperationException If this session doesn't support this.
   * @see #getFlags()
   * @see MediaSessionCompat#FLAG_HANDLES_QUEUE_COMMANDS
   */
  public void addQueueItem(MediaDescriptionCompat description) {
    mImpl.addQueueItem(description);
  }

  /**
   * Adds a queue item from the given {@code description} at the specified position in the play
   * queue of this session. Shifts the queue item currently at that position (if any) and any
   * subsequent queue items to the right (adds one to their indices). Not all sessions may support
   * this. To know whether the session supports this, get the session's flags with {@link
   * #getFlags()} and check that the flag {@link MediaSessionCompat#FLAG_HANDLES_QUEUE_COMMANDS} is
   * set.
   *
   * @param description The {@link MediaDescriptionCompat} for creating the {@link
   *     MediaSessionCompat.QueueItem} to be inserted.
   * @param index The index at which the created {@link MediaSessionCompat.QueueItem} is to be
   *     inserted.
   * @throws UnsupportedOperationException If this session doesn't support this.
   * @see #getFlags()
   * @see MediaSessionCompat#FLAG_HANDLES_QUEUE_COMMANDS
   */
  public void addQueueItem(MediaDescriptionCompat description, int index) {
    mImpl.addQueueItem(description, index);
  }

  /**
   * Removes the first occurrence of the specified {@link MediaSessionCompat.QueueItem} with the
   * given {@link MediaDescriptionCompat description} in the play queue of the associated session.
   * Not all sessions may support this. To know whether the session supports this, get the session's
   * flags with {@link #getFlags()} and check that the flag {@link
   * MediaSessionCompat#FLAG_HANDLES_QUEUE_COMMANDS} is set.
   *
   * @param description The {@link MediaDescriptionCompat} for denoting the {@link
   *     MediaSessionCompat.QueueItem} to be removed.
   * @throws UnsupportedOperationException If this session doesn't support this.
   * @see #getFlags()
   * @see MediaSessionCompat#FLAG_HANDLES_QUEUE_COMMANDS
   */
  public void removeQueueItem(MediaDescriptionCompat description) {
    mImpl.removeQueueItem(description);
  }

  /**
   * Removes a queue item at the specified position in the play queue of this session. Not all
   * sessions may support this. To know whether the session supports this, get the session's flags
   * with {@link #getFlags()} and check that the flag {@link
   * MediaSessionCompat#FLAG_HANDLES_QUEUE_COMMANDS} is set.
   *
   * @param index The index of the element to be removed.
   * @throws UnsupportedOperationException If this session doesn't support this.
   * @see #getFlags()
   * @see MediaSessionCompat#FLAG_HANDLES_QUEUE_COMMANDS
   * @deprecated Use {@link #removeQueueItem(MediaDescriptionCompat)} instead.
   */
  @Deprecated
  public void removeQueueItemAt(int index) {
    List<QueueItem> queue = getQueue();
    if (queue != null && index >= 0 && index < queue.size()) {
      QueueItem item = queue.get(index);
      if (item != null) {
        removeQueueItem(item.getDescription());
      }
    }
  }

  /** Gets the queue title for this session. */
  @Nullable
  public CharSequence getQueueTitle() {
    return mImpl.getQueueTitle();
  }

  /** Gets the extras for this session. */
  @Nullable
  public Bundle getExtras() {
    return mImpl.getExtras();
  }

  /**
   * Gets the rating type supported by the session. One of:
   *
   * <ul>
   *   <li>{@link RatingCompat#RATING_NONE}
   *   <li>{@link RatingCompat#RATING_HEART}
   *   <li>{@link RatingCompat#RATING_THUMB_UP_DOWN}
   *   <li>{@link RatingCompat#RATING_3_STARS}
   *   <li>{@link RatingCompat#RATING_4_STARS}
   *   <li>{@link RatingCompat#RATING_5_STARS}
   *   <li>{@link RatingCompat#RATING_PERCENTAGE}
   * </ul>
   *
   * <p>If the session is not ready, it will return {@link RatingCompat#RATING_NONE}.
   *
   * @return The supported rating type, or {@link RatingCompat#RATING_NONE} if the value is not set
   *     or the session is not ready.
   * @see #isSessionReady
   * @see Callback#onSessionReady
   */
  public int getRatingType() {
    return mImpl.getRatingType();
  }

  /**
   * Returns whether captioning is enabled for this session.
   *
   * <p>If the session is not ready, it will return a {@code false}.
   *
   * @return {@code true} if captioning is enabled, {@code false} if disabled or not set.
   * @see #isSessionReady
   * @see Callback#onSessionReady
   */
  public boolean isCaptioningEnabled() {
    return mImpl.isCaptioningEnabled();
  }

  /**
   * Gets the repeat mode for this session.
   *
   * @return The latest repeat mode set to the session, {@link PlaybackStateCompat#REPEAT_MODE_NONE}
   *     if not set, or {@link PlaybackStateCompat#REPEAT_MODE_INVALID} if the session is not ready
   *     yet.
   * @see #isSessionReady
   * @see Callback#onSessionReady
   */
  public int getRepeatMode() {
    return mImpl.getRepeatMode();
  }

  /**
   * Gets the shuffle mode for this session.
   *
   * @return The latest shuffle mode set to the session, or {@link
   *     PlaybackStateCompat#SHUFFLE_MODE_NONE} if disabled or not set, or {@link
   *     PlaybackStateCompat#SHUFFLE_MODE_INVALID} if the session is not ready yet.
   * @see #isSessionReady
   * @see Callback#onSessionReady
   */
  public int getShuffleMode() {
    return mImpl.getShuffleMode();
  }

  /**
   * Gets the flags for this session. Flags are defined in {@link MediaSessionCompat}.
   *
   * @return The current set of flags for the session.
   */
  public long getFlags() {
    return mImpl.getFlags();
  }

  /**
   * Gets the current playback info for this session.
   *
   * @return The current playback info or null.
   */
  @Nullable
  public PlaybackInfo getPlaybackInfo() {
    return mImpl.getPlaybackInfo();
  }

  /**
   * Gets an intent for launching UI associated with this session if one exists.
   *
   * @return A {@link PendingIntent} to launch UI or null.
   */
  @Nullable
  public PendingIntent getSessionActivity() {
    return mImpl.getSessionActivity();
  }

  /**
   * Gets the token for the session that this controller is connected to.
   *
   * @return The session's token.
   */
  public MediaSessionCompat.Token getSessionToken() {
    return mToken;
  }

  /**
   * Gets the SessionToken in media2 as VersionedParcelable for the session that this controller is
   * connected to.
   *
   * @return The session's token as VersionedParcelable.
   */
  @Nullable
  public VersionedParcelable getSession2Token() {
    return mToken.getSession2Token();
  }

  /**
   * Sets the volume of the output this session is playing on. The command will be ignored if it
   * does not support {@link VolumeProviderCompat#VOLUME_CONTROL_ABSOLUTE}. The flags in {@link
   * AudioManager} may be used to affect the handling.
   *
   * @see #getPlaybackInfo()
   * @param value The value to set it to, between 0 and the reported max.
   * @param flags Flags from {@link AudioManager} to include with the volume request.
   */
  public void setVolumeTo(int value, int flags) {
    mImpl.setVolumeTo(value, flags);
  }

  /**
   * Adjusts the volume of the output this session is playing on. The direction must be one of
   * {@link AudioManager#ADJUST_LOWER}, {@link AudioManager#ADJUST_RAISE}, or {@link
   * AudioManager#ADJUST_SAME}. The command will be ignored if the session does not support {@link
   * VolumeProviderCompat#VOLUME_CONTROL_RELATIVE} or {@link
   * VolumeProviderCompat#VOLUME_CONTROL_ABSOLUTE}. The flags in {@link AudioManager} may be used to
   * affect the handling.
   *
   * @see #getPlaybackInfo()
   * @param direction The direction to adjust the volume in.
   * @param flags Any flags to pass with the command.
   */
  public void adjustVolume(int direction, int flags) {
    mImpl.adjustVolume(direction, flags);
  }

  /**
   * Adds a callback to receive updates from the Session. Updates will be posted on the caller's
   * thread.
   *
   * @param callback The callback object, must not be null.
   */
  public void registerCallback(Callback callback) {
    registerCallback(callback, null);
  }

  /**
   * Adds a callback to receive updates from the session. Updates will be posted on the specified
   * handler's thread.
   *
   * @param callback The callback object, must not be null.
   * @param handler The handler to post updates on. If null the callers thread will be used.
   */
  @SuppressWarnings("deprecation")
  public void registerCallback(Callback callback, @Nullable Handler handler) {
    if (callback == null) {
      throw new IllegalArgumentException("callback must not be null");
    }
    if (!mRegisteredCallbacks.add(callback)) {
      Log.w(TAG, "the callback has already been registered");
      return;
    }
    if (handler == null) {
      handler = new Handler();
    }
    callback.setHandler(handler);
    mImpl.registerCallback(callback, handler);
  }

  /**
   * Stops receiving updates on the specified callback. If an update has already been posted you may
   * still receive it after calling this method.
   *
   * @param callback The callback to remove
   */
  public void unregisterCallback(Callback callback) {
    if (callback == null) {
      throw new IllegalArgumentException("callback must not be null");
    }
    if (!mRegisteredCallbacks.remove(callback)) {
      Log.w(TAG, "the callback has never been registered");
      return;
    }
    try {
      mImpl.unregisterCallback(callback);
    } finally {
      callback.setHandler(null);
    }
  }

  /**
   * Sends a generic command to the session. It is up to the session creator to decide what commands
   * and parameters they will support. As such, commands should only be sent to sessions that the
   * controller owns.
   *
   * @param command The command to send
   * @param params Any parameters to include with the command. Can be {@code null}.
   * @param cb The callback to receive the result on. Can be {@code null}.
   */
  public void sendCommand(String command, @Nullable Bundle params, @Nullable ResultReceiver cb) {
    if (TextUtils.isEmpty(command)) {
      throw new IllegalArgumentException("command must neither be null nor empty");
    }
    mImpl.sendCommand(command, params, cb);
  }

  /**
   * Returns whether the session is ready or not.
   *
   * <p>If the session is not ready, following methods can work incorrectly.
   *
   * <ul>
   *   <li>{@link #getPlaybackState()}
   *   <li>{@link #getRatingType()}
   *   <li>{@link #getRepeatMode()}
   *   <li>{@link #getSessionInfo()}}
   *   <li>{@link #getShuffleMode()}
   *   <li>{@link #isCaptioningEnabled()}
   * </ul>
   *
   * @return true if the session is ready, false otherwise.
   * @see Callback#onSessionReady()
   */
  public boolean isSessionReady() {
    return mImpl.isSessionReady();
  }

  /**
   * Gets the session owner's package name.
   *
   * @return The package name of the session owner.
   */
  @Nullable
  public String getPackageName() {
    return mImpl.getPackageName();
  }

  /**
   * Gets the additional session information which was set when the session was created. The
   * returned {@link Bundle} can include additional unchanging information about the session. For
   * example, it can include the version of the session application, or other app-specific
   * unchanging information.
   *
   * @return The additional session information, or {@link Bundle#EMPTY} if the session didn't set
   *     the information or if the session is not ready.
   * @see #isSessionReady
   * @see Callback#onSessionReady
   */
  public Bundle getSessionInfo() {
    return mImpl.getSessionInfo();
  }

  /**
   * Gets the underlying framework {@link android.media.session.MediaController} object.
   *
   * <p>This method is only supported on API 21+.
   *
   * @return The underlying {@link android.media.session.MediaController} object, or null if none.
   */
  @Nullable
  public Object getMediaController() {
    return mImpl.getMediaController();
  }

  /**
   * Callback for receiving updates on from the session. A Callback can be registered using {@link
   * #registerCallback}
   */
  public abstract static class Callback implements IBinder.DeathRecipient {
    @Nullable final MediaController.Callback mCallbackFwk;
    @Nullable MessageHandler mHandler;
    @Nullable IMediaControllerCallback mIControllerCallback;

    // Sharing this in constructor
    @SuppressWarnings({"assignment.type.incompatible", "argument.type.incompatible"})
    public Callback() {
      if (android.os.Build.VERSION.SDK_INT >= 21) {
        mCallbackFwk = new MediaControllerCallbackApi21(this);
      } else {
        mCallbackFwk = null;
        mIControllerCallback = new StubCompat(this);
      }
    }

    /**
     * Override to handle the session being ready.
     *
     * @see MediaControllerCompat#isSessionReady
     */
    public void onSessionReady() {}

    /**
     * Override to handle the session being destroyed. The session is no longer valid after this
     * call and calls to it will be ignored.
     */
    public void onSessionDestroyed() {}

    /**
     * Override to handle custom events sent by the session owner without a specified interface.
     * Controllers should only handle these for sessions they own.
     *
     * @param event The event from the session.
     * @param extras Optional parameters for the event.
     */
    public void onSessionEvent(@Nullable String event, @Nullable Bundle extras) {}

    /**
     * Override to handle changes in playback state.
     *
     * @param state The new playback state of the session
     */
    public void onPlaybackStateChanged(@Nullable PlaybackStateCompat state) {}

    /**
     * Override to handle changes to the current metadata.
     *
     * @param metadata The current metadata for the session or null if none.
     * @see MediaMetadataCompat
     */
    public void onMetadataChanged(@Nullable MediaMetadataCompat metadata) {}

    /**
     * Override to handle changes to items in the queue.
     *
     * @see MediaSessionCompat.QueueItem
     * @param queue A list of items in the current play queue. It should include the currently
     *     playing item as well as previous and upcoming items if applicable.
     */
    public void onQueueChanged(@Nullable List<QueueItem> queue) {}

    /**
     * Override to handle changes to the queue title.
     *
     * @param title The title that should be displayed along with the play queue such as "Now
     *     Playing". May be null if there is no such title.
     */
    public void onQueueTitleChanged(@Nullable CharSequence title) {}

    /**
     * Override to handle changes to the {@link MediaSessionCompat} extras.
     *
     * @param extras The extras that can include other information associated with the {@link
     *     MediaSessionCompat}.
     */
    public void onExtrasChanged(@Nullable Bundle extras) {}

    /**
     * Override to handle changes to the audio info.
     *
     * @param info The current audio info for this session.
     */
    public void onAudioInfoChanged(@Nullable PlaybackInfo info) {}

    /**
     * Override to handle changes to the captioning enabled status.
     *
     * @param enabled {@code true} if captioning is enabled, {@code false} otherwise.
     */
    public void onCaptioningEnabledChanged(boolean enabled) {}

    /**
     * Override to handle changes to the repeat mode.
     *
     * @param repeatMode The repeat mode. It should be one of followings: {@link
     *     PlaybackStateCompat#REPEAT_MODE_NONE}, {@link PlaybackStateCompat#REPEAT_MODE_ONE},
     *     {@link PlaybackStateCompat#REPEAT_MODE_ALL}, {@link
     *     PlaybackStateCompat#REPEAT_MODE_GROUP}
     */
    public void onRepeatModeChanged(@PlaybackStateCompat.RepeatMode int repeatMode) {}

    /**
     * Override to handle changes to the shuffle mode.
     *
     * @param shuffleMode The shuffle mode. Must be one of the following: {@link
     *     PlaybackStateCompat#SHUFFLE_MODE_NONE}, {@link PlaybackStateCompat#SHUFFLE_MODE_ALL},
     *     {@link PlaybackStateCompat#SHUFFLE_MODE_GROUP}
     */
    public void onShuffleModeChanged(@PlaybackStateCompat.ShuffleMode int shuffleMode) {}

    @Override
    public void binderDied() {
      postToHandler(MessageHandler.MSG_DESTROYED, null, null);
    }

    /** Set the handler to use for callbacks. */
    void setHandler(@Nullable Handler handler) {
      if (handler == null) {
        if (mHandler != null) {
          mHandler.mRegistered = false;
          mHandler.removeCallbacksAndMessages(null);
          mHandler = null;
        }
      } else {
        mHandler = new MessageHandler(handler.getLooper());
        mHandler.mRegistered = true;
      }
    }

    void postToHandler(int what, @Nullable Object obj, @Nullable Bundle data) {
      if (mHandler != null) {
        Message msg = mHandler.obtainMessage(what, obj);
        if (data != null) {
          msg.setData(data);
        }
        msg.sendToTarget();
      }
    }

    // Callback methods in this class are run on handler which was given to registerCallback().
    @RequiresApi(21)
    private static class MediaControllerCallbackApi21 extends MediaController.Callback {
      private final WeakReference<MediaControllerCompat.Callback> mCallback;

      MediaControllerCallbackApi21(MediaControllerCompat.Callback callback) {
        mCallback = new WeakReference<>(callback);
      }

      @Override
      public void onSessionDestroyed() {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.onSessionDestroyed();
        }
      }

      @Override
      public void onSessionEvent(String event, @Nullable Bundle extras) {
        MediaSessionCompat.ensureClassLoader(extras);
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          if (callback.mIControllerCallback != null && android.os.Build.VERSION.SDK_INT < 23) {
            // Ignore. ExtraCallback will handle this.
          } else {
            callback.onSessionEvent(event, extras);
          }
        }
      }

      @Override
      public void onPlaybackStateChanged(@Nullable PlaybackState stateObj) {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          if (callback.mIControllerCallback != null) {
            // Ignore. ExtraCallback will handle this.
          } else {
            callback.onPlaybackStateChanged(PlaybackStateCompat.fromPlaybackState(stateObj));
          }
        }
      }

      @Override
      public void onMetadataChanged(@Nullable MediaMetadata metadataObj) {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.onMetadataChanged(MediaMetadataCompat.fromMediaMetadata(metadataObj));
        }
      }

      @Override
      public void onQueueChanged(@Nullable List<MediaSession.QueueItem> queue) {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.onQueueChanged(QueueItem.fromQueueItemList(queue));
        }
      }

      @Override
      public void onQueueTitleChanged(@Nullable CharSequence title) {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.onQueueTitleChanged(title);
        }
      }

      @Override
      public void onExtrasChanged(@Nullable Bundle extras) {
        MediaSessionCompat.ensureClassLoader(extras);
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.onExtrasChanged(extras);
        }
      }

      @Override
      public void onAudioInfoChanged(@Nullable MediaController.PlaybackInfo info) {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null && info != null) {
          callback.onAudioInfoChanged(
              new PlaybackInfo(
                  info.getPlaybackType(),
                  checkNotNull(AudioAttributesCompat.wrap(info.getAudioAttributes())),
                  info.getVolumeControl(),
                  info.getMaxVolume(),
                  info.getCurrentVolume()));
        }
      }
    }

    private static class StubCompat extends IMediaControllerCallback.Stub {
      private final WeakReference<MediaControllerCompat.Callback> mCallback;

      StubCompat(MediaControllerCompat.Callback callback) {
        mCallback = new WeakReference<>(callback);
      }

      @Override
      public void onEvent(@Nullable String event, @Nullable Bundle extras) throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_EVENT, event, extras);
        }
      }

      @Override
      public void onSessionDestroyed() throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_DESTROYED, null, null);
        }
      }

      @Override
      public void onPlaybackStateChanged(@Nullable PlaybackStateCompat state)
          throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_UPDATE_PLAYBACK_STATE, state, null);
        }
      }

      @Override
      public void onMetadataChanged(@Nullable MediaMetadataCompat metadata) throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_UPDATE_METADATA, metadata, null);
        }
      }

      @Override
      public void onQueueChanged(@Nullable List<QueueItem> queue) throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_UPDATE_QUEUE, queue, null);
        }
      }

      @Override
      public void onQueueTitleChanged(@Nullable CharSequence title) throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_UPDATE_QUEUE_TITLE, title, null);
        }
      }

      @Override
      public void onCaptioningEnabledChanged(boolean enabled) throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_UPDATE_CAPTIONING_ENABLED, enabled, null);
        }
      }

      @Override
      public void onRepeatModeChanged(int repeatMode) throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_UPDATE_REPEAT_MODE, repeatMode, null);
        }
      }

      @Override
      public void onShuffleModeChangedRemoved(boolean enabled) throws RemoteException {
        // Do nothing.
      }

      @Override
      public void onShuffleModeChanged(int shuffleMode) throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_UPDATE_SHUFFLE_MODE, shuffleMode, null);
        }
      }

      @Override
      public void onExtrasChanged(@Nullable Bundle extras) throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_UPDATE_EXTRAS, extras, null);
        }
      }

      @Override
      public void onVolumeInfoChanged(@Nullable ParcelableVolumeInfo info) throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          PlaybackInfo pi = null;
          if (info != null) {
            pi =
                new PlaybackInfo(
                    info.volumeType,
                    info.audioStream,
                    info.controlType,
                    info.maxVolume,
                    info.currentVolume);
          }
          callback.postToHandler(MessageHandler.MSG_UPDATE_VOLUME, pi, null);
        }
      }

      @Override
      public void onSessionReady() throws RemoteException {
        MediaControllerCompat.Callback callback = mCallback.get();
        if (callback != null) {
          callback.postToHandler(MessageHandler.MSG_SESSION_READY, null, null);
        }
      }
    }

    private class MessageHandler extends Handler {
      private static final int MSG_EVENT = 1;
      private static final int MSG_UPDATE_PLAYBACK_STATE = 2;
      private static final int MSG_UPDATE_METADATA = 3;
      private static final int MSG_UPDATE_VOLUME = 4;
      private static final int MSG_UPDATE_QUEUE = 5;
      private static final int MSG_UPDATE_QUEUE_TITLE = 6;
      private static final int MSG_UPDATE_EXTRAS = 7;
      private static final int MSG_DESTROYED = 8;
      private static final int MSG_UPDATE_REPEAT_MODE = 9;
      private static final int MSG_UPDATE_CAPTIONING_ENABLED = 11;
      private static final int MSG_UPDATE_SHUFFLE_MODE = 12;
      private static final int MSG_SESSION_READY = 13;

      boolean mRegistered = false;

      MessageHandler(Looper looper) {
        super(looper);
      }

      @Override
      @SuppressWarnings("unchecked")
      public void handleMessage(Message msg) {
        if (!mRegistered) {
          return;
        }
        switch (msg.what) {
          case MSG_EVENT:
            {
              Bundle extras = msg.getData();
              MediaSessionCompat.ensureClassLoader(extras);
              onSessionEvent((String) msg.obj, extras);
              break;
            }
          case MSG_UPDATE_PLAYBACK_STATE:
            onPlaybackStateChanged((PlaybackStateCompat) msg.obj);
            break;
          case MSG_UPDATE_METADATA:
            onMetadataChanged((MediaMetadataCompat) msg.obj);
            break;
          case MSG_UPDATE_QUEUE:
            onQueueChanged((List<QueueItem>) msg.obj);
            break;
          case MSG_UPDATE_QUEUE_TITLE:
            onQueueTitleChanged((CharSequence) msg.obj);
            break;
          case MSG_UPDATE_CAPTIONING_ENABLED:
            onCaptioningEnabledChanged((boolean) msg.obj);
            break;
          case MSG_UPDATE_REPEAT_MODE:
            onRepeatModeChanged((int) msg.obj);
            break;
          case MSG_UPDATE_SHUFFLE_MODE:
            onShuffleModeChanged((int) msg.obj);
            break;
          case MSG_UPDATE_EXTRAS:
            {
              Bundle extras = (Bundle) msg.obj;
              MediaSessionCompat.ensureClassLoader(extras);
              onExtrasChanged(extras);
              break;
            }
          case MSG_UPDATE_VOLUME:
            onAudioInfoChanged((PlaybackInfo) msg.obj);
            break;
          case MSG_DESTROYED:
            onSessionDestroyed();
            break;
          case MSG_SESSION_READY:
            onSessionReady();
            break;
        }
      }
    }
  }

  /**
   * Interface for controlling media playback on a session. This allows an app to send media
   * transport commands to the session.
   */
  public abstract static class TransportControls {
    /**
     * Used as an integer extra field in {@link #playFromMediaId(String, Bundle)} or {@link
     * #prepareFromMediaId(String, Bundle)} to indicate the stream type to be used by the media
     * player when playing or preparing the specified media id. See {@link AudioManager} for a list
     * of stream types.
     *
     * @deprecated Use {@link MediaConstants#TRANSPORT_CONTROLS_EXTRAS_KEY_LEGACY_STREAM_TYPE}
     *     instead.
     */
    @Deprecated
    public static final String EXTRA_LEGACY_STREAM_TYPE =
        MediaConstants.TRANSPORT_CONTROLS_EXTRAS_KEY_LEGACY_STREAM_TYPE;

    TransportControls() {}

    /**
     * Request that the player prepare for playback. This can decrease the time it takes to start
     * playback when a play command is received. Preparation is not required. You can call {@link
     * #play} without calling this method beforehand.
     */
    public abstract void prepare();

    /**
     * Request that the player prepare playback for a specific media id. This can decrease the time
     * it takes to start playback when a play command is received. Preparation is not required. You
     * can call {@link #playFromMediaId} without calling this method beforehand.
     *
     * @param mediaId The id of the requested media.
     * @param extras Optional extras that can include extra information about the media item to be
     *     prepared.
     */
    public abstract void prepareFromMediaId(String mediaId, @Nullable Bundle extras);

    /**
     * Request that the player prepare playback for a specific search query. This can decrease the
     * time it takes to start playback when a play command is received. An empty or null query
     * should be treated as a request to prepare any music. Preparation is not required. You can
     * call {@link #playFromSearch} without calling this method beforehand.
     *
     * @param query The search query.
     * @param extras Optional extras that can include extra information about the query.
     */
    public abstract void prepareFromSearch(String query, @Nullable Bundle extras);

    /**
     * Request that the player prepare playback for a specific {@link Uri}. This can decrease the
     * time it takes to start playback when a play command is received. Preparation is not required.
     * You can call {@link #playFromUri} without calling this method beforehand.
     *
     * @param uri The URI of the requested media.
     * @param extras Optional extras that can include extra information about the media item to be
     *     prepared.
     */
    public abstract void prepareFromUri(Uri uri, @Nullable Bundle extras);

    /** Request that the player start its playback at its current position. */
    public abstract void play();

    /**
     * Request that the player start playback for a specific media id.
     *
     * @param mediaId The id of the requested media.
     * @param extras Optional extras that can include extra information about the media item to be
     *     played.
     */
    public abstract void playFromMediaId(String mediaId, @Nullable Bundle extras);

    /**
     * Request that the player start playback for a specific search query. An empty or null query
     * should be treated as a request to play any music.
     *
     * @param query The search query.
     * @param extras Optional extras that can include extra information about the query.
     */
    public abstract void playFromSearch(String query, @Nullable Bundle extras);

    /**
     * Request that the player start playback for a specific {@link Uri}.
     *
     * @param uri The URI of the requested media.
     * @param extras Optional extras that can include extra information about the media item to be
     *     played.
     */
    public abstract void playFromUri(Uri uri, @Nullable Bundle extras);

    /**
     * Plays an item with a specific id in the play queue. If you specify an id that is not in the
     * play queue, the behavior is undefined.
     */
    public abstract void skipToQueueItem(long id);

    /** Request that the player pause its playback and stay at its current position. */
    public abstract void pause();

    /**
     * Request that the player stop its playback; it may clear its state in whatever way is
     * appropriate.
     */
    public abstract void stop();

    /**
     * Moves to a new location in the media stream.
     *
     * @param pos Position to move to, in milliseconds.
     */
    public abstract void seekTo(long pos);

    /**
     * Starts fast forwarding. If playback is already fast forwarding this may increase the rate.
     */
    public abstract void fastForward();

    /** Skips to the next item. */
    public abstract void skipToNext();

    /** Starts rewinding. If playback is already rewinding this may increase the rate. */
    public abstract void rewind();

    /** Skips to the previous item. */
    public abstract void skipToPrevious();

    /**
     * Rates the current content. This will cause the rating to be set for the current user. The
     * rating type of the given {@link RatingCompat} must match the type returned by {@link
     * #getRatingType()}.
     *
     * @param rating The rating to set for the current content
     */
    public abstract void setRating(RatingCompat rating);

    /**
     * Rates a media item. This will cause the rating to be set for the specific media item. The
     * rating type of the given {@link RatingCompat} must match the type returned by {@link
     * #getRatingType()}.
     *
     * @param rating The rating to set for the media item.
     * @param extras Optional arguments that can include information about the media item to be
     *     rated.
     * @see MediaSessionCompat#ARGUMENT_MEDIA_ATTRIBUTE
     * @see MediaSessionCompat#ARGUMENT_MEDIA_ATTRIBUTE_VALUE
     */
    public abstract void setRating(RatingCompat rating, @Nullable Bundle extras);

    /**
     * Sets the playback speed. A value of {@code 1.0f} is the default playback value, and a
     * negative value indicates reverse playback. {@code 0.0f} is not allowed.
     *
     * @param speed The playback speed
     * @throws IllegalArgumentException if the {@code speed} is equal to zero.
     */
    public void setPlaybackSpeed(float speed) {}

    /**
     * Enables/disables captioning for this session.
     *
     * @param enabled {@code true} to enable captioning, {@code false} to disable.
     */
    public abstract void setCaptioningEnabled(boolean enabled);

    /**
     * Sets the repeat mode for this session.
     *
     * @param repeatMode The repeat mode. Must be one of the following: {@link
     *     PlaybackStateCompat#REPEAT_MODE_NONE}, {@link PlaybackStateCompat#REPEAT_MODE_ONE},
     *     {@link PlaybackStateCompat#REPEAT_MODE_ALL}, {@link
     *     PlaybackStateCompat#REPEAT_MODE_GROUP}
     */
    public abstract void setRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode);

    /**
     * Sets the shuffle mode for this session.
     *
     * @param shuffleMode The shuffle mode. Must be one of the following: {@link
     *     PlaybackStateCompat#SHUFFLE_MODE_NONE}, {@link PlaybackStateCompat#SHUFFLE_MODE_ALL},
     *     {@link PlaybackStateCompat#SHUFFLE_MODE_GROUP}
     */
    public abstract void setShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode);

    /**
     * Sends a custom action for the {@link MediaSessionCompat} to perform.
     *
     * @param customAction The action to perform.
     * @param args Optional arguments to supply to the {@link MediaSessionCompat} for this custom
     *     action.
     */
    public abstract void sendCustomAction(
        PlaybackStateCompat.CustomAction customAction, @Nullable Bundle args);

    /**
     * Sends the id and args from a custom action for the {@link MediaSessionCompat} to perform.
     *
     * @see #sendCustomAction(PlaybackStateCompat.CustomAction action, Bundle args)
     * @see MediaSessionCompat#ACTION_FLAG_AS_INAPPROPRIATE
     * @see MediaSessionCompat#ACTION_SKIP_AD
     * @see MediaSessionCompat#ACTION_FOLLOW
     * @see MediaSessionCompat#ACTION_UNFOLLOW
     * @param action The action identifier of the {@link PlaybackStateCompat.CustomAction} as
     *     specified by the {@link MediaSessionCompat}.
     * @param args Optional arguments to supply to the {@link MediaSessionCompat} for this custom
     *     action.
     */
    public abstract void sendCustomAction(String action, @Nullable Bundle args);
  }

  /** Holds information about the way volume is handled for this session. */
  public static final class PlaybackInfo {
    /** The session uses local playback. */
    public static final int PLAYBACK_TYPE_LOCAL = 1;

    /** The session uses remote playback. */
    public static final int PLAYBACK_TYPE_REMOTE = 2;

    private final int mPlaybackType;
    private final AudioAttributesCompat mAudioAttrsCompat;
    private final int mVolumeControl;
    private final int mMaxVolume;
    private final int mCurrentVolume;

    PlaybackInfo(int type, int stream, int control, int max, int current) {
      this(
          type,
          new AudioAttributesCompat.Builder().setLegacyStreamType(stream).build(),
          control,
          max,
          current);
    }

    PlaybackInfo(int type, AudioAttributesCompat attrsCompat, int control, int max, int current) {
      mPlaybackType = type;
      mAudioAttrsCompat = attrsCompat;
      mVolumeControl = control;
      mMaxVolume = max;
      mCurrentVolume = current;
    }

    /**
     * Gets the type of volume handling, either local or remote. One of:
     *
     * <ul>
     *   <li>{@link PlaybackInfo#PLAYBACK_TYPE_LOCAL}
     *   <li>{@link PlaybackInfo#PLAYBACK_TYPE_REMOTE}
     * </ul>
     *
     * @return The type of volume handling this session is using.
     */
    public int getPlaybackType() {
      return mPlaybackType;
    }

    /**
     * Gets the stream this is currently controlling volume on. When the volume type is {@link
     * PlaybackInfo#PLAYBACK_TYPE_REMOTE} this value does not have meaning and should be ignored.
     *
     * @deprecated Use {@link PlaybackInfo#getAudioAttributes()} instead.
     * @return The stream this session is playing on.
     */
    @Deprecated
    public int getAudioStream() {
      return mAudioAttrsCompat.getLegacyStreamType();
    }

    /**
     * Get the audio attributes for this session. The attributes will affect volume handling for the
     * session. When the volume type is {@link PlaybackInfo#PLAYBACK_TYPE_REMOTE} these may be
     * ignored by the remote volume handler.
     *
     * @return The attributes for this session.
     */
    public AudioAttributesCompat getAudioAttributes() {
      return mAudioAttrsCompat;
    }

    /**
     * Gets the type of volume control that can be used. One of:
     *
     * <ul>
     *   <li>{@link VolumeProviderCompat#VOLUME_CONTROL_ABSOLUTE}
     *   <li>{@link VolumeProviderCompat#VOLUME_CONTROL_RELATIVE}
     *   <li>{@link VolumeProviderCompat#VOLUME_CONTROL_FIXED}
     * </ul>
     *
     * @return The type of volume control that may be used with this session.
     */
    public int getVolumeControl() {
      return mVolumeControl;
    }

    /**
     * Gets the maximum volume that may be set for this session.
     *
     * @return The maximum allowed volume where this session is playing.
     */
    public int getMaxVolume() {
      return mMaxVolume;
    }

    /**
     * Gets the current volume for this session.
     *
     * @return The current volume where this session is playing.
     */
    public int getCurrentVolume() {
      return mCurrentVolume;
    }
  }

  interface MediaControllerImpl {
    void registerCallback(Callback callback, Handler handler);

    void unregisterCallback(Callback callback);

    boolean dispatchMediaButtonEvent(KeyEvent keyEvent);

    TransportControls getTransportControls();

    @Nullable
    PlaybackStateCompat getPlaybackState();

    @Nullable
    MediaMetadataCompat getMetadata();

    @Nullable
    List<QueueItem> getQueue();

    void addQueueItem(MediaDescriptionCompat description);

    void addQueueItem(MediaDescriptionCompat description, int index);

    void removeQueueItem(MediaDescriptionCompat description);

    @Nullable
    CharSequence getQueueTitle();

    @Nullable
    Bundle getExtras();

    int getRatingType();

    boolean isCaptioningEnabled();

    int getRepeatMode();

    int getShuffleMode();

    long getFlags();

    @Nullable
    PlaybackInfo getPlaybackInfo();

    @Nullable
    PendingIntent getSessionActivity();

    void setVolumeTo(int value, int flags);

    void adjustVolume(int direction, int flags);

    void sendCommand(String command, @Nullable Bundle params, @Nullable ResultReceiver cb);

    boolean isSessionReady();

    @Nullable
    String getPackageName();

    Bundle getSessionInfo();

    @Nullable
    Object getMediaController();
  }

  static class MediaControllerImplBase implements MediaControllerImpl {
    private IMediaSession mBinder;
    @Nullable private TransportControls mTransportControls;
    @Nullable private Bundle mSessionInfo;

    MediaControllerImplBase(MediaSessionCompat.Token token) {
      mBinder = IMediaSession.Stub.asInterface((IBinder) token.getToken());
    }

    @Override
    public void registerCallback(Callback callback, Handler handler) {
      if (callback == null) {
        throw new IllegalArgumentException("callback may not be null.");
      }
      try {
        mBinder.asBinder().linkToDeath(callback, 0);
        mBinder.registerCallbackListener(checkNotNull(callback.mIControllerCallback));
        callback.postToHandler(Callback.MessageHandler.MSG_SESSION_READY, null, null);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in registerCallback.", e);
        callback.postToHandler(Callback.MessageHandler.MSG_DESTROYED, null, null);
      }
    }

    @Override
    public void unregisterCallback(Callback callback) {
      if (callback == null) {
        throw new IllegalArgumentException("callback may not be null.");
      }
      try {
        mBinder.unregisterCallbackListener(checkNotNull(callback.mIControllerCallback));
        mBinder.asBinder().unlinkToDeath(callback, 0);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in unregisterCallback.", e);
      }
    }

    @Override
    public boolean dispatchMediaButtonEvent(KeyEvent event) {
      if (event == null) {
        throw new IllegalArgumentException("event may not be null.");
      }
      try {
        mBinder.sendMediaButton(event);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in dispatchMediaButtonEvent.", e);
      }
      return false;
    }

    @Override
    public TransportControls getTransportControls() {
      if (mTransportControls == null) {
        mTransportControls = new TransportControlsBase(mBinder);
      }

      return mTransportControls;
    }

    @Nullable
    @Override
    public PlaybackStateCompat getPlaybackState() {
      try {
        return mBinder.getPlaybackState();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getPlaybackState.", e);
      }
      return null;
    }

    @Nullable
    @Override
    public MediaMetadataCompat getMetadata() {
      try {
        return mBinder.getMetadata();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getMetadata.", e);
      }
      return null;
    }

    @Nullable
    @Override
    public List<QueueItem> getQueue() {
      try {
        return mBinder.getQueue();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getQueue.", e);
      }
      return null;
    }

    @Override
    public void addQueueItem(MediaDescriptionCompat description) {
      try {
        long flags = mBinder.getFlags();
        if ((flags & MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS) == 0) {
          throw new UnsupportedOperationException(
              "This session doesn't support queue management operations");
        }
        mBinder.addQueueItem(description);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in addQueueItem.", e);
      }
    }

    @Override
    public void addQueueItem(MediaDescriptionCompat description, int index) {
      try {
        long flags = mBinder.getFlags();
        if ((flags & MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS) == 0) {
          throw new UnsupportedOperationException(
              "This session doesn't support queue management operations");
        }
        mBinder.addQueueItemAt(description, index);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in addQueueItemAt.", e);
      }
    }

    @Override
    public void removeQueueItem(MediaDescriptionCompat description) {
      try {
        long flags = mBinder.getFlags();
        if ((flags & MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS) == 0) {
          throw new UnsupportedOperationException(
              "This session doesn't support queue management operations");
        }
        mBinder.removeQueueItem(description);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in removeQueueItem.", e);
      }
    }

    @Nullable
    @Override
    public CharSequence getQueueTitle() {
      try {
        return mBinder.getQueueTitle();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getQueueTitle.", e);
      }
      return null;
    }

    @Nullable
    @Override
    public Bundle getExtras() {
      try {
        return mBinder.getExtras();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getExtras.", e);
      }
      return null;
    }

    @Override
    public int getRatingType() {
      try {
        return mBinder.getRatingType();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getRatingType.", e);
      }
      return 0;
    }

    @Override
    public boolean isCaptioningEnabled() {
      try {
        return mBinder.isCaptioningEnabled();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in isCaptioningEnabled.", e);
      }
      return false;
    }

    @Override
    public int getRepeatMode() {
      try {
        return mBinder.getRepeatMode();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getRepeatMode.", e);
      }
      return PlaybackStateCompat.REPEAT_MODE_INVALID;
    }

    @Override
    public int getShuffleMode() {
      try {
        return mBinder.getShuffleMode();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getShuffleMode.", e);
      }
      return PlaybackStateCompat.SHUFFLE_MODE_INVALID;
    }

    @Override
    public long getFlags() {
      try {
        return mBinder.getFlags();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getFlags.", e);
      }
      return 0;
    }

    @Nullable
    @Override
    public PlaybackInfo getPlaybackInfo() {
      try {
        ParcelableVolumeInfo info = mBinder.getVolumeAttributes();
        if (info == null) {
          return null;
        }
        PlaybackInfo pi =
            new PlaybackInfo(
                info.volumeType,
                info.audioStream,
                info.controlType,
                info.maxVolume,
                info.currentVolume);
        return pi;
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getPlaybackInfo.", e);
      }
      return null;
    }

    @Nullable
    @Override
    public PendingIntent getSessionActivity() {
      try {
        return mBinder.getLaunchPendingIntent();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getSessionActivity.", e);
      }
      return null;
    }

    @Override
    public void setVolumeTo(int value, int flags) {
      try {
        mBinder.setVolumeTo(value, flags, null);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in setVolumeTo.", e);
      }
    }

    @Override
    public void adjustVolume(int direction, int flags) {
      try {
        mBinder.adjustVolume(direction, flags, null);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in adjustVolume.", e);
      }
    }

    @Override
    public void sendCommand(String command, @Nullable Bundle params, @Nullable ResultReceiver cb) {
      try {
        mBinder.sendCommand(
            command, params, cb == null ? null : new MediaSessionCompat.ResultReceiverWrapper(cb));
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in sendCommand.", e);
      }
    }

    @Override
    public boolean isSessionReady() {
      return true;
    }

    @Nullable
    @Override
    public String getPackageName() {
      try {
        return mBinder.getPackageName();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in getPackageName.", e);
      }
      return null;
    }

    @Override
    public Bundle getSessionInfo() {
      try {
        mSessionInfo = mBinder.getSessionInfo();
      } catch (RemoteException e) {
        Log.d(TAG, "Dead object in getSessionInfo.", e);
      }

      mSessionInfo = MediaSessionCompat.unparcelWithClassLoader(mSessionInfo);
      return mSessionInfo == null ? Bundle.EMPTY : new Bundle(mSessionInfo);
    }

    @Nullable
    @Override
    public Object getMediaController() {
      return null;
    }
  }

  static class TransportControlsBase extends TransportControls {
    private IMediaSession mBinder;

    public TransportControlsBase(IMediaSession binder) {
      mBinder = binder;
    }

    @Override
    public void prepare() {
      try {
        mBinder.prepare();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in prepare.", e);
      }
    }

    @Override
    public void prepareFromMediaId(String mediaId, @Nullable Bundle extras) {
      try {
        mBinder.prepareFromMediaId(mediaId, extras);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in prepareFromMediaId.", e);
      }
    }

    @Override
    public void prepareFromSearch(String query, @Nullable Bundle extras) {
      try {
        mBinder.prepareFromSearch(query, extras);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in prepareFromSearch.", e);
      }
    }

    @Override
    public void prepareFromUri(Uri uri, @Nullable Bundle extras) {
      try {
        mBinder.prepareFromUri(uri, extras);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in prepareFromUri.", e);
      }
    }

    @Override
    public void play() {
      try {
        mBinder.play();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in play.", e);
      }
    }

    @Override
    public void playFromMediaId(String mediaId, @Nullable Bundle extras) {
      try {
        mBinder.playFromMediaId(mediaId, extras);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in playFromMediaId.", e);
      }
    }

    @Override
    public void playFromSearch(String query, @Nullable Bundle extras) {
      try {
        mBinder.playFromSearch(query, extras);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in playFromSearch.", e);
      }
    }

    @Override
    public void playFromUri(Uri uri, @Nullable Bundle extras) {
      try {
        mBinder.playFromUri(uri, extras);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in playFromUri.", e);
      }
    }

    @Override
    public void skipToQueueItem(long id) {
      try {
        mBinder.skipToQueueItem(id);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in skipToQueueItem.", e);
      }
    }

    @Override
    public void pause() {
      try {
        mBinder.pause();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in pause.", e);
      }
    }

    @Override
    public void stop() {
      try {
        mBinder.stop();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in stop.", e);
      }
    }

    @Override
    public void seekTo(long pos) {
      try {
        mBinder.seekTo(pos);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in seekTo.", e);
      }
    }

    @Override
    public void fastForward() {
      try {
        mBinder.fastForward();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in fastForward.", e);
      }
    }

    @Override
    public void skipToNext() {
      try {
        mBinder.next();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in skipToNext.", e);
      }
    }

    @Override
    public void rewind() {
      try {
        mBinder.rewind();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in rewind.", e);
      }
    }

    @Override
    public void skipToPrevious() {
      try {
        mBinder.previous();
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in skipToPrevious.", e);
      }
    }

    @Override
    public void setRating(RatingCompat rating) {
      try {
        mBinder.rate(rating);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in setRating.", e);
      }
    }

    @Override
    public void setRating(RatingCompat rating, @Nullable Bundle extras) {
      try {
        mBinder.rateWithExtras(rating, extras);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in setRating.", e);
      }
    }

    @Override
    public void setPlaybackSpeed(float speed) {
      if (speed == 0.0f) {
        throw new IllegalArgumentException("speed must not be zero");
      }
      try {
        mBinder.setPlaybackSpeed(speed);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in setPlaybackSpeed.", e);
      }
    }

    @Override
    public void setCaptioningEnabled(boolean enabled) {
      try {
        mBinder.setCaptioningEnabled(enabled);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in setCaptioningEnabled.", e);
      }
    }

    @Override
    public void setRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode) {
      try {
        mBinder.setRepeatMode(repeatMode);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in setRepeatMode.", e);
      }
    }

    @Override
    public void setShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
      try {
        mBinder.setShuffleMode(shuffleMode);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in setShuffleMode.", e);
      }
    }

    @Override
    public void sendCustomAction(CustomAction customAction, @Nullable Bundle args) {
      sendCustomAction(customAction.getAction(), args);
    }

    @Override
    public void sendCustomAction(String action, @Nullable Bundle args) {
      validateCustomAction(action, args);
      try {
        mBinder.sendCustomAction(action, args);
      } catch (RemoteException e) {
        Log.e(TAG, "Dead object in sendCustomAction.", e);
      }
    }
  }

  @RequiresApi(21)
  static class MediaControllerImplApi21 implements MediaControllerImpl {
    protected final MediaController mControllerFwk;

    final Object mLock = new Object();

    @GuardedBy("mLock")
    private final List<Callback> mPendingCallbacks = new ArrayList<>();

    private HashMap<Callback, ExtraCallback> mCallbackMap = new HashMap<>();

    @Nullable protected Bundle mSessionInfo;

    final MediaSessionCompat.Token mSessionToken;

    // Calling method from constructor
    @SuppressWarnings({"assignment.type.incompatible", "method.invocation.invalid"})
    MediaControllerImplApi21(Context context, MediaSessionCompat.Token sessionToken) {
      mSessionToken = sessionToken;
      mControllerFwk =
          new MediaController(context, (MediaSession.Token) checkNotNull(mSessionToken.getToken()));
      if (mSessionToken.getExtraBinder() == null) {
        requestExtraBinder();
      }
    }

    @Override
    public final void registerCallback(Callback callback, Handler handler) {
      mControllerFwk.registerCallback(checkNotNull(callback.mCallbackFwk), handler);
      synchronized (mLock) {
        IMediaSession extraBinder = mSessionToken.getExtraBinder();
        if (extraBinder != null) {
          ExtraCallback extraCallback = new ExtraCallback(callback);
          mCallbackMap.put(callback, extraCallback);
          callback.mIControllerCallback = extraCallback;
          try {
            extraBinder.registerCallbackListener(extraCallback);
            callback.postToHandler(Callback.MessageHandler.MSG_SESSION_READY, null, null);
          } catch (RemoteException e) {
            Log.e(TAG, "Dead object in registerCallback.", e);
          }
        } else {
          callback.mIControllerCallback = null;
          mPendingCallbacks.add(callback);
        }
      }
    }

    @Override
    public final void unregisterCallback(Callback callback) {
      mControllerFwk.unregisterCallback(checkNotNull(callback.mCallbackFwk));
      synchronized (mLock) {
        IMediaSession extraBinder = mSessionToken.getExtraBinder();
        if (extraBinder != null) {
          try {
            ExtraCallback extraCallback = mCallbackMap.remove(callback);
            if (extraCallback != null) {
              callback.mIControllerCallback = null;
              extraBinder.unregisterCallbackListener(extraCallback);
            }
          } catch (RemoteException e) {
            Log.e(TAG, "Dead object in unregisterCallback.", e);
          }
        } else {
          mPendingCallbacks.remove(callback);
        }
      }
    }

    @Override
    public boolean dispatchMediaButtonEvent(KeyEvent event) {
      return mControllerFwk.dispatchMediaButtonEvent(event);
    }

    @Override
    public TransportControls getTransportControls() {
      MediaController.TransportControls controlsFwk = mControllerFwk.getTransportControls();
      if (Build.VERSION.SDK_INT >= 29) {
        return new TransportControlsApi29(controlsFwk);
      } else if (Build.VERSION.SDK_INT >= 24) {
        return new TransportControlsApi24(controlsFwk);
      } else if (Build.VERSION.SDK_INT >= 23) {
        return new TransportControlsApi23(controlsFwk);
      } else {
        return new TransportControlsApi21(controlsFwk);
      }
    }

    @Nullable
    @Override
    public PlaybackStateCompat getPlaybackState() {
      IMediaSession extraBinder = mSessionToken.getExtraBinder();
      if (extraBinder != null) {
        try {
          return extraBinder.getPlaybackState();
        } catch (RemoteException e) {
          Log.e(TAG, "Dead object in getPlaybackState.", e);
        }
      }
      PlaybackState stateFwk = mControllerFwk.getPlaybackState();
      return stateFwk != null ? PlaybackStateCompat.fromPlaybackState(stateFwk) : null;
    }

    @Nullable
    @Override
    public MediaMetadataCompat getMetadata() {
      MediaMetadata metadataFwk = mControllerFwk.getMetadata();
      return metadataFwk != null ? MediaMetadataCompat.fromMediaMetadata(metadataFwk) : null;
    }

    @Nullable
    @Override
    public List<QueueItem> getQueue() {
      List<MediaSession.QueueItem> queueFwks = mControllerFwk.getQueue();
      return queueFwks != null ? QueueItem.fromQueueItemList(queueFwks) : null;
    }

    @Override
    public void addQueueItem(MediaDescriptionCompat description) {
      long flags = getFlags();
      if ((flags & MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS) == 0) {
        throw new UnsupportedOperationException(
            "This session doesn't support queue management operations");
      }
      Bundle params = new Bundle();
      params.putParcelable(
          COMMAND_ARGUMENT_MEDIA_DESCRIPTION,
          LegacyParcelableUtil.convert(
              description, android.support.v4.media.MediaDescriptionCompat.CREATOR));
      sendCommand(COMMAND_ADD_QUEUE_ITEM, params, null);
    }

    @Override
    public void addQueueItem(MediaDescriptionCompat description, int index) {
      long flags = getFlags();
      if ((flags & MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS) == 0) {
        throw new UnsupportedOperationException(
            "This session doesn't support queue management operations");
      }
      Bundle params = new Bundle();
      params.putParcelable(
          COMMAND_ARGUMENT_MEDIA_DESCRIPTION,
          LegacyParcelableUtil.convert(
              description, android.support.v4.media.MediaDescriptionCompat.CREATOR));
      params.putInt(COMMAND_ARGUMENT_INDEX, index);
      sendCommand(COMMAND_ADD_QUEUE_ITEM_AT, params, null);
    }

    @Override
    public void removeQueueItem(MediaDescriptionCompat description) {
      long flags = getFlags();
      if ((flags & MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS) == 0) {
        throw new UnsupportedOperationException(
            "This session doesn't support queue management operations");
      }
      Bundle params = new Bundle();
      params.putParcelable(
          COMMAND_ARGUMENT_MEDIA_DESCRIPTION,
          LegacyParcelableUtil.convert(
              description, android.support.v4.media.MediaDescriptionCompat.CREATOR));
      sendCommand(COMMAND_REMOVE_QUEUE_ITEM, params, null);
    }

    @Nullable
    @Override
    public CharSequence getQueueTitle() {
      return mControllerFwk.getQueueTitle();
    }

    @Nullable
    @Override
    public Bundle getExtras() {
      return mControllerFwk.getExtras();
    }

    @Override
    public int getRatingType() {
      if (android.os.Build.VERSION.SDK_INT < 22) {
        try {
          IMediaSession extraBinder = mSessionToken.getExtraBinder();
          if (extraBinder != null) {
            return extraBinder.getRatingType();
          }
        } catch (RemoteException e) {
          Log.e(TAG, "Dead object in getRatingType.", e);
        }
      }
      return mControllerFwk.getRatingType();
    }

    @Override
    public boolean isCaptioningEnabled() {
      IMediaSession extraBinder = mSessionToken.getExtraBinder();
      if (extraBinder != null) {
        try {
          return extraBinder.isCaptioningEnabled();
        } catch (RemoteException e) {
          Log.e(TAG, "Dead object in isCaptioningEnabled.", e);
        }
      }
      return false;
    }

    @Override
    public int getRepeatMode() {
      IMediaSession extraBinder = mSessionToken.getExtraBinder();
      if (extraBinder != null) {
        try {
          return extraBinder.getRepeatMode();
        } catch (RemoteException e) {
          Log.e(TAG, "Dead object in getRepeatMode.", e);
        }
      }
      return PlaybackStateCompat.REPEAT_MODE_INVALID;
    }

    @Override
    public int getShuffleMode() {
      IMediaSession extraBinder = mSessionToken.getExtraBinder();
      if (extraBinder != null) {
        try {
          return extraBinder.getShuffleMode();
        } catch (RemoteException e) {
          Log.e(TAG, "Dead object in getShuffleMode.", e);
        }
      }
      return PlaybackStateCompat.SHUFFLE_MODE_INVALID;
    }

    @Override
    public long getFlags() {
      return mControllerFwk.getFlags();
    }

    @Nullable
    @Override
    public PlaybackInfo getPlaybackInfo() {
      MediaController.PlaybackInfo volumeInfoFwk = mControllerFwk.getPlaybackInfo();
      return volumeInfoFwk != null
          ? new PlaybackInfo(
              volumeInfoFwk.getPlaybackType(),
              checkNotNull(AudioAttributesCompat.wrap(volumeInfoFwk.getAudioAttributes())),
              volumeInfoFwk.getVolumeControl(),
              volumeInfoFwk.getMaxVolume(),
              volumeInfoFwk.getCurrentVolume())
          : null;
    }

    @Nullable
    @Override
    public PendingIntent getSessionActivity() {
      return mControllerFwk.getSessionActivity();
    }

    @Override
    public void setVolumeTo(int value, int flags) {
      mControllerFwk.setVolumeTo(value, flags);
    }

    @Override
    public void adjustVolume(int direction, int flags) {
      mControllerFwk.adjustVolume(direction, flags);
    }

    @Override
    public void sendCommand(String command, @Nullable Bundle params, @Nullable ResultReceiver cb) {
      mControllerFwk.sendCommand(command, params, cb);
    }

    @Override
    public boolean isSessionReady() {
      return mSessionToken.getExtraBinder() != null;
    }

    @Override
    public String getPackageName() {
      return mControllerFwk.getPackageName();
    }

    @Override
    public Bundle getSessionInfo() {
      if (mSessionInfo != null) {
        return new Bundle(mSessionInfo);
      }

      IMediaSession extraBinder = mSessionToken.getExtraBinder();
      if (extraBinder != null) {
        try {
          mSessionInfo = extraBinder.getSessionInfo();
        } catch (RemoteException e) {
          Log.e(TAG, "Dead object in getSessionInfo.", e);
          mSessionInfo = Bundle.EMPTY;
        }
      }

      mSessionInfo = MediaSessionCompat.unparcelWithClassLoader(mSessionInfo);
      return mSessionInfo == null ? Bundle.EMPTY : new Bundle(mSessionInfo);
    }

    @Nullable
    @Override
    public Object getMediaController() {
      return mControllerFwk;
    }

    private void requestExtraBinder() {
      sendCommand(COMMAND_GET_EXTRA_BINDER, null, new ExtraBinderRequestResultReceiver(this));
    }

    @GuardedBy("mLock")
    void processPendingCallbacksLocked() {
      IMediaSession extraBinder = mSessionToken.getExtraBinder();
      if (extraBinder == null) {
        return;
      }
      for (Callback callback : mPendingCallbacks) {
        ExtraCallback extraCallback = new ExtraCallback(callback);
        mCallbackMap.put(callback, extraCallback);
        callback.mIControllerCallback = extraCallback;
        try {
          extraBinder.registerCallbackListener(extraCallback);
        } catch (RemoteException e) {
          Log.e(TAG, "Dead object in registerCallback.", e);
          break;
        }
        callback.postToHandler(Callback.MessageHandler.MSG_SESSION_READY, null, null);
      }
      mPendingCallbacks.clear();
    }

    @SuppressWarnings("argument.type.incompatible") // Activity.setMediaController is not annotated
    static void setMediaController(Activity activity, MediaControllerCompat mediaControllerCompat) {
      MediaController controllerFwk = null;
      if (mediaControllerCompat != null) {
        Object sessionTokenObj = mediaControllerCompat.getSessionToken().getToken();
        controllerFwk = new MediaController(activity, (MediaSession.Token) sessionTokenObj);
      }
      activity.setMediaController(controllerFwk);
    }

    @Nullable
    static MediaControllerCompat getMediaController(Activity activity) {
      MediaController controllerFwk = activity.getMediaController();
      if (controllerFwk == null) {
        return null;
      }
      MediaSession.Token sessionTokenFwk = controllerFwk.getSessionToken();
      return new MediaControllerCompat(
          activity, MediaSessionCompat.Token.fromToken(sessionTokenFwk));
    }

    private static class ExtraBinderRequestResultReceiver extends ResultReceiver {
      private WeakReference<MediaControllerImplApi21> mMediaControllerImpl;

      ExtraBinderRequestResultReceiver(MediaControllerImplApi21 mediaControllerImpl) {
        super(null /* handler */);
        mMediaControllerImpl = new WeakReference<>(mediaControllerImpl);
      }

      @Override
      protected void onReceiveResult(int resultCode, Bundle resultData) {
        MediaControllerImplApi21 mediaControllerImpl = mMediaControllerImpl.get();
        if (mediaControllerImpl == null || resultData == null) {
          return;
        }
        synchronized (mediaControllerImpl.mLock) {
          mediaControllerImpl.mSessionToken.setExtraBinder(
              IMediaSession.Stub.asInterface(
                  resultData.getBinder(MediaSessionCompat.KEY_EXTRA_BINDER)));
          mediaControllerImpl.mSessionToken.setSession2Token(
              ParcelUtils.getVersionedParcelable(
                  resultData, MediaSessionCompat.KEY_SESSION2_TOKEN));
          mediaControllerImpl.processPendingCallbacksLocked();
        }
      }
    }

    private static class ExtraCallback extends Callback.StubCompat {
      ExtraCallback(Callback callback) {
        super(callback);
      }

      @Override
      public void onSessionDestroyed() throws RemoteException {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void onMetadataChanged(@Nullable MediaMetadataCompat metadata) throws RemoteException {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void onQueueChanged(@Nullable List<QueueItem> queue) throws RemoteException {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void onQueueTitleChanged(@Nullable CharSequence title) throws RemoteException {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void onExtrasChanged(@Nullable Bundle extras) throws RemoteException {
        // Will not be called.
        throw new AssertionError();
      }

      @Override
      public void onVolumeInfoChanged(@Nullable ParcelableVolumeInfo info) throws RemoteException {
        // Will not be called.
        throw new AssertionError();
      }
    }
  }

  @RequiresApi(29)
  static class MediaControllerImplApi29 extends MediaControllerImplApi21 {
    MediaControllerImplApi29(Context context, MediaSessionCompat.Token sessionToken) {
      super(context, sessionToken);
    }

    @Override
    public Bundle getSessionInfo() {
      if (mSessionInfo != null) {
        return new Bundle(mSessionInfo);
      }
      mSessionInfo = mControllerFwk.getSessionInfo();
      mSessionInfo = MediaSessionCompat.unparcelWithClassLoader(mSessionInfo);
      return mSessionInfo == null ? Bundle.EMPTY : new Bundle(mSessionInfo);
    }
  }

  @RequiresApi(21)
  static class TransportControlsApi21 extends TransportControls {
    protected final MediaController.TransportControls mControlsFwk;

    TransportControlsApi21(MediaController.TransportControls controlsFwk) {
      mControlsFwk = controlsFwk;
    }

    @Override
    public void prepare() {
      sendCustomAction(MediaSessionCompat.ACTION_PREPARE, null);
    }

    @Override
    public void prepareFromMediaId(String mediaId, @Nullable Bundle extras) {
      Bundle bundle = new Bundle();
      bundle.putString(MediaSessionCompat.ACTION_ARGUMENT_MEDIA_ID, mediaId);
      bundle.putBundle(MediaSessionCompat.ACTION_ARGUMENT_EXTRAS, extras);
      sendCustomAction(MediaSessionCompat.ACTION_PREPARE_FROM_MEDIA_ID, bundle);
    }

    @Override
    public void prepareFromSearch(String query, @Nullable Bundle extras) {
      Bundle bundle = new Bundle();
      bundle.putString(MediaSessionCompat.ACTION_ARGUMENT_QUERY, query);
      bundle.putBundle(MediaSessionCompat.ACTION_ARGUMENT_EXTRAS, extras);
      sendCustomAction(MediaSessionCompat.ACTION_PREPARE_FROM_SEARCH, bundle);
    }

    @Override
    public void prepareFromUri(Uri uri, @Nullable Bundle extras) {
      Bundle bundle = new Bundle();
      bundle.putParcelable(MediaSessionCompat.ACTION_ARGUMENT_URI, uri);
      bundle.putBundle(MediaSessionCompat.ACTION_ARGUMENT_EXTRAS, extras);
      sendCustomAction(MediaSessionCompat.ACTION_PREPARE_FROM_URI, bundle);
    }

    @Override
    public void play() {
      mControlsFwk.play();
    }

    @Override
    public void pause() {
      mControlsFwk.pause();
    }

    @Override
    public void stop() {
      mControlsFwk.stop();
    }

    @Override
    public void seekTo(long pos) {
      mControlsFwk.seekTo(pos);
    }

    @Override
    public void fastForward() {
      mControlsFwk.fastForward();
    }

    @Override
    public void rewind() {
      mControlsFwk.rewind();
    }

    @Override
    public void skipToNext() {
      mControlsFwk.skipToNext();
    }

    @Override
    public void skipToPrevious() {
      mControlsFwk.skipToPrevious();
    }

    @SuppressWarnings("argument.type.incompatible") // Platform controller accepts null rating
    @Override
    public void setRating(RatingCompat rating) {
      mControlsFwk.setRating(rating != null ? (Rating) rating.getRating() : null);
    }

    @Override
    public void setRating(RatingCompat rating, @Nullable Bundle extras) {
      Bundle bundle = new Bundle();
      bundle.putParcelable(
          MediaSessionCompat.ACTION_ARGUMENT_RATING,
          LegacyParcelableUtil.convert(rating, android.support.v4.media.RatingCompat.CREATOR));
      bundle.putBundle(MediaSessionCompat.ACTION_ARGUMENT_EXTRAS, extras);
      sendCustomAction(MediaSessionCompat.ACTION_SET_RATING, bundle);
    }

    @Override
    public void setPlaybackSpeed(float speed) {
      if (speed == 0.0f) {
        throw new IllegalArgumentException("speed must not be zero");
      }
      Bundle bundle = new Bundle();
      bundle.putFloat(MediaSessionCompat.ACTION_ARGUMENT_PLAYBACK_SPEED, speed);
      sendCustomAction(MediaSessionCompat.ACTION_SET_PLAYBACK_SPEED, bundle);
    }

    @Override
    public void setCaptioningEnabled(boolean enabled) {
      Bundle bundle = new Bundle();
      bundle.putBoolean(MediaSessionCompat.ACTION_ARGUMENT_CAPTIONING_ENABLED, enabled);
      sendCustomAction(MediaSessionCompat.ACTION_SET_CAPTIONING_ENABLED, bundle);
    }

    @Override
    public void setRepeatMode(@PlaybackStateCompat.RepeatMode int repeatMode) {
      Bundle bundle = new Bundle();
      bundle.putInt(MediaSessionCompat.ACTION_ARGUMENT_REPEAT_MODE, repeatMode);
      sendCustomAction(MediaSessionCompat.ACTION_SET_REPEAT_MODE, bundle);
    }

    @Override
    public void setShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
      Bundle bundle = new Bundle();
      bundle.putInt(MediaSessionCompat.ACTION_ARGUMENT_SHUFFLE_MODE, shuffleMode);
      sendCustomAction(MediaSessionCompat.ACTION_SET_SHUFFLE_MODE, bundle);
    }

    @SuppressWarnings("argument.type.incompatible") // Platform controller accepts null extras
    @Override
    public void playFromMediaId(String mediaId, @Nullable Bundle extras) {
      mControlsFwk.playFromMediaId(mediaId, extras);
    }

    @SuppressWarnings("argument.type.incompatible") // Platform controller accepts null extras
    @Override
    public void playFromSearch(String query, @Nullable Bundle extras) {
      mControlsFwk.playFromSearch(query, extras);
    }

    @Override
    public void playFromUri(Uri uri, @Nullable Bundle extras) {
      if (uri == null || Uri.EMPTY.equals(uri)) {
        throw new IllegalArgumentException("You must specify a non-empty Uri for playFromUri.");
      }
      Bundle bundle = new Bundle();
      bundle.putParcelable(MediaSessionCompat.ACTION_ARGUMENT_URI, uri);
      bundle.putBundle(MediaSessionCompat.ACTION_ARGUMENT_EXTRAS, extras);
      sendCustomAction(MediaSessionCompat.ACTION_PLAY_FROM_URI, bundle);
    }

    @Override
    public void skipToQueueItem(long id) {
      mControlsFwk.skipToQueueItem(id);
    }

    @Override
    public void sendCustomAction(CustomAction customAction, @Nullable Bundle args) {
      validateCustomAction(customAction.getAction(), args);
      mControlsFwk.sendCustomAction(customAction.getAction(), args);
    }

    @Override
    public void sendCustomAction(String action, @Nullable Bundle args) {
      validateCustomAction(action, args);
      mControlsFwk.sendCustomAction(action, args);
    }
  }

  @RequiresApi(23)
  static class TransportControlsApi23 extends TransportControlsApi21 {
    TransportControlsApi23(MediaController.TransportControls controlsFwk) {
      super(controlsFwk);
    }

    @SuppressWarnings("argument.type.incompatible") // Framework controller is missing annotation
    @Override
    public void playFromUri(Uri uri, @Nullable Bundle extras) {
      mControlsFwk.playFromUri(uri, extras);
    }
  }

  @RequiresApi(24)
  static class TransportControlsApi24 extends TransportControlsApi23 {
    TransportControlsApi24(MediaController.TransportControls controlsFwk) {
      super(controlsFwk);
    }

    @Override
    public void prepare() {
      mControlsFwk.prepare();
    }

    @SuppressWarnings("argument.type.incompatible") // Framework controller is missing annotation
    @Override
    public void prepareFromMediaId(String mediaId, @Nullable Bundle extras) {
      mControlsFwk.prepareFromMediaId(mediaId, extras);
    }

    @SuppressWarnings("argument.type.incompatible") // Platform controller accepts null extra
    @Override
    public void prepareFromSearch(String query, @Nullable Bundle extras) {
      mControlsFwk.prepareFromSearch(query, extras);
    }

    @SuppressWarnings("argument.type.incompatible") // Platform controller accepts null extra
    @Override
    public void prepareFromUri(Uri uri, @Nullable Bundle extras) {
      mControlsFwk.prepareFromUri(uri, extras);
    }
  }

  @RequiresApi(29)
  static class TransportControlsApi29 extends TransportControlsApi24 {
    TransportControlsApi29(MediaController.TransportControls controlsFwk) {
      super(controlsFwk);
    }

    @Override
    public void setPlaybackSpeed(float speed) {
      if (speed == 0.0f) {
        throw new IllegalArgumentException("speed must not be zero");
      }
      mControlsFwk.setPlaybackSpeed(speed);
    }
  }
}
