package org.thunderdog.challegram.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;

import org.drinkless.td.libcore.telegram.TdApi;
import org.drinkmore.Tracer;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageReader;
import org.thunderdog.challegram.player.AudioController;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.receiver.AudioMediaReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibNotificationManager;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.ArrayUtils;

/**
 * Date: 1/21/18
 * Author: default
 */

public class AudioService extends Service implements TGPlayerController.TrackListChangeListener, TGPlayerController.TrackListener, AudioController.ApicListener, AudioManager.OnAudioFocusChangeListener {

  @Nullable
  @Override
  public IBinder onBind (Intent intent) {
    return null;
  }

  private static final int MSG_PROCESS_HOOK = 0;
  private static final int MSG_REQUEST_FOCUS = 1;

  private static class EventHandler extends Handler {
    private final AudioService context;

    public EventHandler (AudioService context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      switch (msg.what) {
        case MSG_PROCESS_HOOK: {
          context.onHookTap();
          break;
        }
        case MSG_REQUEST_FOCUS: {
          if (msg.arg1 == 1) {
            context.requestAudioFocusImpl();
          } else {
            context.abandonAudioFocusImpl();
          }
          break;
        }
      }
    }
  }

  private Handler handler;

  @Override
  public void onCreate () {
    super.onCreate();

    UI.initApp(getApplicationContext());

    this.handler = new EventHandler(this);

    Log.i(Log.TAG_PLAYER, "[service] onCreate");
  }

  @Override
  public void onDestroy () {
    super.onDestroy();
    Log.i(Log.TAG_PLAYER, "[service] onDestroy");
    U.stopForeground(this, true, TdlibNotificationManager.ID_MUSIC);
    TdlibManager.instance().player().removeTrackListChangeListener(this);
  }

  @Override
  public int onStartCommand (Intent intent, int flags, int startId) {
    Log.i(Log.TAG_PLAYER, "[service] onStartCommand");
    TdlibManager.instance().player().addTrackListChangeListener(this, true);
    return START_STICKY;
  }

  // List management

  private static boolean isSupportedTrack (TdApi.Message message) {
    return message != null && message.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR;
  }

  private boolean isInitialized () {
    return currentTrack != null;
  }

  private int trackIndex = -1;
  private final ArrayList<TdApi.Message> trackList = new ArrayList<>();

  private int playState;
  private boolean playBuffering;
  private long playPosition = -1, playDuration = -1;

  private int playFlags;
  private boolean inReverseMode () {
    return (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
  }
  private boolean hasNext () {
    return trackIndex != -1 && (inReverseMode() ? trackIndex > 0 : trackIndex + 1 < trackList.size());
  }
  private boolean hasPrevious () {
    return trackIndex != -1 && (!inReverseMode() ? trackIndex > 0 : trackIndex + 1 < trackList.size());
  }

  private boolean hadPrevious, hadNext;
  private void rememberListState () {
    hadPrevious = hasPrevious();
    hadNext = hasNext();
  }
  private void checkListState () {
    if (currentTrack != null && (hadPrevious != hasPrevious() || hadNext != hasNext())) {
      updateNotification();
      setSessionPlayState();
    }
  }

  private void destroyList () {
    if (currentTrack != null) {
      // TODO
      playPosition = playDuration = -1;
      playState = 0;
      playBuffering = false;
      playFlags = 0;
      trackList.clear();
      trackIndex = -1;
      setTrack(null, null);
    }
  }

  private void setPlayState (int playState) {
    if (this.playState != playState) {
      this.playState = playState;
      if (playState == TGPlayerController.STATE_PLAYING) {
        requestAudioFocusIfNeeded();
      }
      if (currentTrack != null) {
        updateNotification();
        setSessionPlayState();
      }
    }
  }

  // current track

  @Override
  public void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state) {
    if (currentTdlib == tdlib && currentTrack != null && TGPlayerController.compareTracks(currentTrack, chatId, messageId, fileId) && (state == TGPlayerController.STATE_PLAYING || state == TGPlayerController.STATE_PAUSED)) {
      setPlayState(state);
    }
  }

  @Override
  public void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long position, long totalDuration, boolean isBuffering) {
    if (currentTdlib == tdlib && currentTrack != null && TGPlayerController.compareTracks(currentTrack, chatId, messageId, fileId)) {
      boolean changedDuration = this.playDuration != totalDuration;
      boolean changedPosition = (this.playPosition <= 0 != position <= 0) || Math.abs(position - playPosition) >= 2000;
      boolean updatedDuration = (this.playDuration <= 0) != (totalDuration <= 0) || (this.playDuration > 0 && totalDuration > 0 && playDuration != totalDuration);
      boolean changedBuffering = this.playBuffering != isBuffering;
      if (changedDuration || this.playPosition != position || changedBuffering) {
        this.playDuration = totalDuration;
        this.playPosition = position;
        this.playBuffering = isBuffering;
        if (changedDuration || changedPosition || (changedBuffering && playState == TGPlayerController.STATE_PLAYING)) {
          updateNotification();
        }
        if (updatedDuration) {
          setSessionMetadata();
        }
        if (updatedDuration || changedDuration || changedPosition || changedBuffering) {
          setSessionPlayState();
        }
      }
    }
  }

  // Track list

  @Override
  public void onTrackListReset (Tdlib tdlib, @NonNull TdApi.Message currentTrack, int trackIndex, List<TdApi.Message> trackList, long playListChatId, int playFlags, int playState) {
    if (!isSupportedTrack(currentTrack)) {
      destroyList();
      return;
    }

    this.trackList.clear();
    this.trackList.addAll(trackList);
    this.trackIndex = trackIndex;
    this.playFlags = playFlags;
    this.playState = playState;

    setTrack(tdlib, currentTrack);
  }

  @Override
  public void onTrackListPositionChange (Tdlib tdlib, TdApi.Message newTrack, int newIndex, List<TdApi.Message> trackList, boolean byUserRequest, int playState) {
    if (isInitialized()) {
      this.trackIndex = newIndex;
      this.playState = playState;
      setTrack(tdlib, newTrack);
    }
  }

  @Override
  public void onTrackListClose (Tdlib tdlib, long playListChatId, long playListMaxMessageId, long playListMinMessageId, boolean newEndReached, boolean oldEndReached, List<TdApi.Message> removedMessages) {
    if (isInitialized()) {
      destroyList();
    }
  }

  @Override
  public void onTrackListItemAdded (Tdlib tdlib, TdApi.Message newTrack, int position) {
    if (!isInitialized()) {
      return;
    }
    rememberListState();
    if (position <= trackIndex) {
      trackIndex++;
    }
    trackList.add(position, newTrack);
    checkListState();
  }

  @Override
  public void onTrackListItemMoved (Tdlib tdlib, TdApi.Message track, int fromPosition, int toPosition) {
    if (!isInitialized()) {
      return;
    }
    rememberListState();
    if (trackIndex == fromPosition) {
      trackIndex = toPosition;
    } else {
      if (fromPosition < trackIndex) {
        trackIndex--;
      }
      if (toPosition <= trackIndex) {
        trackIndex++;
      }
    }
    ArrayUtils.move(trackList, fromPosition, toPosition);
    checkListState();
  }

  @Override
  public void onTrackListItemRemoved (Tdlib tdlib, TdApi.Message track, int position, boolean isCurrent) {
    if (!isInitialized()) {
      return;
    }
    rememberListState();
    if (position < trackIndex) {
      trackIndex--;
    }
    trackList.remove(position);
    checkListState();
  }

  @Override
  public void onTrackListItemRangeAdded (Tdlib tdlib, List<TdApi.Message> addedItems, boolean areNew) {
    if (!isInitialized()) {
      return;
    }
    rememberListState();
    if (!areNew) {
      trackIndex += addedItems.size();
    }
    if (areNew) {
      trackList.addAll(addedItems);
    } else {
      trackList.addAll(0, addedItems);
    }
    checkListState();
  }

  @Override
  public void onTrackListFlagsChanged (int newPlayFlags) {
    if (isInitialized() && this.playFlags != newPlayFlags) {
      rememberListState();
      this.playFlags = newPlayFlags;
      checkListState();
    }
  }

  @Override
  public void onTrackListLoadStateChanged () { }

  // Notification

  private Tdlib currentTdlib;
  private TdApi.Message currentTrack;
  private Bitmap currentCover, oldCover;
  private Bitmap emptyCover;

  private static Bitmap tryCreatePlaceholder () {
    try {
      int size = getCoverSize(true);
      return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    } catch (Throwable ignored) {}
    return null;
  }

  private static Bitmap recycleBitmap (Bitmap bitmap) {
    if (bitmap != null && !bitmap.isRecycled()) {
      bitmap.recycle();
    }
    return null;
  }

  private void setTrack (Tdlib tdlib, TdApi.Message track) {
    if (track == null && currentTrack == null) {
      return;
    }
    if (currentTdlib == tdlib && TGPlayerController.compareTracks(currentTdlib, tdlib, currentTrack, track)) {
      return;
    }

    boolean hadTrack = currentTrack != null;
    boolean hasTrack = track != null;

    if (hadTrack) {
      TdlibManager.instance().player().removeTrackListener(currentTdlib, currentTrack, this);
      TdlibManager.instance().audio().cancelApic(currentTdlib, currentTrack, this);
    }

    this.currentTdlib = tdlib;
    this.currentTrack = track;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (currentCover != null) {
        this.oldCover = recycleBitmap(oldCover);
        this.oldCover = currentCover;
      }
      this.currentCover = null;
    } else {
      this.currentCover = recycleBitmap(currentCover);
    }
    this.playDuration = this.playPosition = -1;

    if (!hasTrack) {
      U.stopForeground(this, true, TdlibNotificationManager.ID_MUSIC);
      destroyResources();
      stopSelf();
      return;
    }

    /*cancelApicClearer();
    scheduleApicClearer(tdlib, track);*/
    ApicFrame apicFrame = TdlibManager.instance().audio().requestApic(tdlib, track, this);
    if (apicFrame != null) {
      // cancelApicClearer();
      processApicAsync(tdlib, track, apicFrame);
    }

    if (!hadTrack) {
      initResources();
    } else {
      requestAudioFocusIfNeeded();
    }

    setSessionMetadata();
    setSessionPlayState();

    TdlibManager.instance().player().addTrackListener(tdlib, track, this);

    if (!hadTrack) {
      U.startForeground(this, TdlibNotificationManager.ID_MUSIC, buildNotification());
    } else {
      updateNotification();
    }
  }

  /*private void cancelApicClearer () {
    if (apicClearer != null) {
      apicClearer.cancel();
      apicClearer = null;
    }
  }

  private CancellableRunnable apicClearer;

  private void scheduleApicClearer (Tdlib tdlib, TdApi.Message message) {
    apicClearer = new CancellableRunnable() {
      @Override
      public void act () {
        setApic(tdlib, message, null);
      }
    };
    apicClearer.removeOnCancel();
    UI.post(apicClearer, 1000);
  }*/

  private @Nullable Object mediaSession;

  private boolean inHookTap;
  private int hookTapScore;

  private void processHookTap () {
    if (hookLock != 0 && SystemClock.uptimeMillis() - hookLock < 500) {
      hookLock = 0;
      return;
    }
    if (inHookTap) {
      if (hookTapScore == 3) {
        return;
      }
      handler.removeMessages(MSG_PROCESS_HOOK);
      hookTapScore++;
      if (hookTapScore == 3) {
        onHookTap();
      } else {
        handler.sendMessageDelayed(Message.obtain(handler, MSG_PROCESS_HOOK), 420);
      }
    } else {
      inHookTap = true;
      hookTapScore = 1;
      handler.sendMessageDelayed(Message.obtain(handler, MSG_PROCESS_HOOK), 370l);
    }
  }

  private long hookLock;

  private void onHookTap () {
    if (inHookTap) {
      inHookTap = false;
      int score = hookTapScore;
      hookTapScore = 0;
      if (score >= 0) {
        hookLock = SystemClock.uptimeMillis();
      }
      if (score <= 1) {
        TdlibManager.instance().player().playPauseCurrent();
      } else if (score == 2) {
        TdlibManager.instance().player().skip(true);
      } else {
        TdlibManager.instance().player().skip(false);
      }
    }
  }

  private void initResources () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      android.media.session.MediaSession session = new android.media.session.MediaSession(this, "MusicService");
      session.setCallback(new android.media.session.MediaSession.Callback() {
        @Override
        public void onPlay () {
          TdlibManager.instance().player().playPauseCurrent(true);
        }

        @Override
        public void onPause () {
          TdlibManager.instance().player().playPauseCurrent(false);
        }

        @Override
        public void onStop () {
          TdlibManager.instance().player().stopPlayback(true);
        }

        @Override
        public void onSkipToNext () {
          TdlibManager.instance().player().skip(true);
        }

        @Override
        public void onSkipToPrevious () {
          TdlibManager.instance().player().skip(false);
        }

        @Override
        public void onSeekTo (long pos) {
          TdlibManager.instance().audio().seekTo(pos, -1);
        }

        @Override
        public boolean onMediaButtonEvent (@NonNull Intent mediaButtonIntent) {
          if (!Intent.ACTION_MEDIA_BUTTON.equals(mediaButtonIntent.getAction())) {
            return super.onMediaButtonEvent(mediaButtonIntent);
          }
          KeyEvent ke = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
          if (ke == null || ke.getAction() != MotionEvent.ACTION_DOWN || ke.getKeyCode() != KeyEvent.KEYCODE_HEADSETHOOK) {
            return super.onMediaButtonEvent(mediaButtonIntent);
          }
          processHookTap();
          return true;
        }
      });
      session.setFlags(android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
      session.setActive(true);
      session.setSessionActivity(valueOfPlayer(currentTdlib.id()));
      session.setExtras(new Bundle());
      this.mediaSession = session;
    }
    requestAudioFocus();
    emptyCover = tryCreatePlaceholder();
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void setSessionMetadata () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
      TdApi.Audio audio = ((TdApi.MessageAudio) currentTrack.content).audio;
      android.media.session.MediaSession session = (android.media.session.MediaSession) mediaSession;
      MediaMetadata.Builder b = new MediaMetadata.Builder();
      b.putString(MediaMetadata.METADATA_KEY_TITLE, TD.getTitle(audio));
      b.putString(MediaMetadata.METADATA_KEY_ARTIST, TD.getSubtitle(audio));
      if (playDuration != C.TIME_UNSET && playDuration > 0) {
        b.putLong(MediaMetadata.METADATA_KEY_DURATION, playDuration);
      }
      if (currentCover != null) {
        b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, currentCover);
      } else if (Config.USE_OLD_COVER && oldCover != null) {
        b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, oldCover);
      }
      session.setMetadata(b.build());
    }
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void setSessionPlayState () {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
      android.media.session.MediaSession session = (android.media.session.MediaSession) mediaSession;

      android.media.session.PlaybackState.Builder stateBuilder = new android.media.session.PlaybackState.Builder();
      long actions = android.media.session.PlaybackState.ACTION_PLAY |
        android.media.session.PlaybackState.ACTION_PLAY_PAUSE |
        android.media.session.PlaybackState.ACTION_PAUSE |
        android.media.session.PlaybackState.ACTION_STOP |
        android.media.session.PlaybackState.ACTION_SEEK_TO;
      if (hasNext()) {
        actions |= android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT;
      }
      if (hasPrevious()) {
        actions |= android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS;
      }
      stateBuilder.setActions(actions);

      stateBuilder.setState(
        playState == TGPlayerController.STATE_PLAYING ?
          (playBuffering ? android.media.session.PlaybackState.STATE_BUFFERING : android.media.session.PlaybackState.STATE_PLAYING) :
          android.media.session.PlaybackState.STATE_PAUSED, playPosition, 1.0f);
      session.setPlaybackState(stateBuilder.build());
      session.setActive(true);
    }
  }

  private void destroyResources () {
    TdlibManager.instance().player().setReduceVolume(false);
    emptyCover = recycleBitmap(emptyCover);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
      android.media.session.MediaSession session = (android.media.session.MediaSession) mediaSession;
      session.setActive(false);
      session.release();
      mediaSession = null;
    }
    this.oldCover = recycleBitmap(oldCover);
    this.currentCover = recycleBitmap(currentCover);
    abandonAudioFocus();
  }

  private void updateNotification () {
    NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    if (manager != null) {
      try {
        manager.notify(TdlibNotificationManager.ID_MUSIC, buildNotification());
      } catch (Throwable t) {
        Log.e("Unable to update music notification", t);
        Tracer.onOtherError(t);
        throw t;
      }
    }
  }

  private Intent valueOf (String action) {
    Intent intent = new Intent(this, AudioMediaReceiver.class);
    intent.setAction(action);
    return intent;
  }

  private PendingIntent valueOfPlayer (int accountId) {
    return PendingIntent.getActivity(this, 0, Intents.valueOfPlayer(accountId), PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private Notification buildNotification () {
    Notification.Builder b;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      b = new Notification.Builder(this, Intents.newSimpleChannel(Intents.CHANNEL_ID_PLAYBACK, R.string.NotificationChannelPlayback));
    } else {
      b = new Notification.Builder(this);
    }

    b.setContentIntent(valueOfPlayer(currentTdlib.id()));

    PendingIntent prevIntent = PendingIntent.getBroadcast(this, 100, valueOf(Intents.ACTION_PLAYBACK_SKIP_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent playPauseIntent = PendingIntent.getBroadcast(this, 100, valueOf(Intents.ACTION_PLAYBACK_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent nextIntent = PendingIntent.getBroadcast(this, 100, valueOf(Intents.ACTION_PLAYBACK_SKIP_NEXT), PendingIntent.FLAG_UPDATE_CURRENT);

    // PendingIntent pIntent = PendingIntent.getActivity(this, (int) SystemClock.uptimeMillis(), new Intent(this, MainActivity.class), 0);

    b.addAction(R.drawable.baseline_skip_previous_24_white, Lang.getString(R.string.PlaySkipPrev), prevIntent);
    if (playState == TGPlayerController.STATE_PLAYING) {
      b.addAction(R.drawable.baseline_pause_36_white, Lang.getString(R.string.PlayPause), playPauseIntent);
    } else {
      b.addAction(R.drawable.baseline_play_arrow_36_white, Lang.getString(playPosition > 0 ? R.string.PlayResume : R.string.PlayPlay), playPauseIntent);
    }
    b.addAction(R.drawable.baseline_skip_next_24_white, Lang.getString(R.string.PlaySkipNext), nextIntent);
    if (playState != TGPlayerController.STATE_PLAYING) {
      PendingIntent stopIntent = PendingIntent.getBroadcast(this, 100, valueOf(Intents.ACTION_PLAYBACK_STOP), PendingIntent.FLAG_UPDATE_CURRENT);
      b.addAction(R.drawable.baseline_stop_24_white, Lang.getString(R.string.PlayStop), stopIntent);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      b.setColor(currentTdlib.accountPlayerColor());
      int[] indexes = new int[] {0,1,2};
      b.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(indexes).setMediaSession(((android.media.session.MediaSession) mediaSession).getSessionToken()));
    }
    b.setSmallIcon(R.drawable.baseline_play_circle_filled_24_white);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      b.setVisibility(Notification.VISIBILITY_PUBLIC);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      boolean isPlaying = playState == TGPlayerController.STATE_PLAYING && !playBuffering && playDuration != C.TIME_UNSET && playPosition != C.TIME_UNSET && trackIndex != -1;
      if (isPlaying) {
        b.setUsesChronometer(true).setWhen(System.currentTimeMillis() - playPosition);
      } else {
        b.setUsesChronometer(false).setWhen(0);
      }
      b.setShowWhen(isPlaying);
    }

    TdApi.Audio audio = ((TdApi.MessageAudio) currentTrack.content).audio;

    b.setContentTitle(TD.getTitle(audio));
    b.setContentText(TD.getSubtitle(audio));
    b.setOngoing(playState == TGPlayerController.STATE_PLAYING);
    if (currentCover != null) {
      b.setLargeIcon(currentCover);
    } else if (Config.USE_OLD_COVER && oldCover != null) {
      b.setLargeIcon(oldCover);
    } else if (emptyCover != null) {
      b.setLargeIcon(emptyCover);
    }

    return b.build();
  }

  // Apic

  private static int getCoverSize (boolean isEmpty) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !isEmpty ? Math.max(1024, Screen.smallestSide()) : Screen.dp(64f);
  }

  private void setApic (Tdlib tdlib, TdApi.Message message, Bitmap bitmap) {
    if (currentTrack != null && TGPlayerController.compareTracks(currentTdlib, tdlib, message, currentTrack)) {
      this.currentCover = bitmap;
      this.oldCover = null;
      updateNotification();
      setSessionMetadata();
    } else if (bitmap != null) {
      bitmap.recycle();
    }
  }

  @Override
  public void onApicLoaded (Tdlib tdlib, TdApi.Message message, ApicFrame apicFrame) {
    processApicAsync(tdlib, message, apicFrame);
  }

  private void processApicAsync (final Tdlib tdlib, final TdApi.Message message, final ApicFrame apicFrame) {
    /*if (currentTrack != null && TGPlayerController.compareTracks(currentTdlib, tdlib, message, currentTrack)) {
      cancelApicClearer();
    }*/
    Background.instance().post(() -> {
      final Bitmap bitmap = ImageReader.readBytes(apicFrame.pictureData, getCoverSize(false), false, false);
      if (bitmap != null) {
        tdlib.ui().post(() -> setApic(tdlib, message, bitmap));
      }
    });
  }

  // Focus

  private int audioFocusState = AudioManager.AUDIOFOCUS_GAIN;

  private boolean hasAudioFocus () {
    return hasFocus(audioFocusState);
  }

  private boolean audioFocusRequested;

  private void requestAudioFocus () {
    audioFocusRequested = true;
    handler.removeMessages(MSG_REQUEST_FOCUS);
    handler.sendMessageDelayed(Message.obtain(handler, MSG_REQUEST_FOCUS, 1, 0), 180l);
  }

  private void abandonAudioFocus () {
    audioFocusRequested = false;
    handler.removeMessages(MSG_REQUEST_FOCUS);
    handler.sendMessageDelayed(Message.obtain(handler, MSG_REQUEST_FOCUS, 0, 0), 50l);
  }

  private void requestAudioFocusImpl () {
    if (audioFocusRequested) {
      AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
      if (am != null) {
        am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        setAudioFocusState(AudioManager.AUDIOFOCUS_GAIN);
      }
    }
  }

  private void abandonAudioFocusImpl () {
    if (!audioFocusRequested) {
      AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
      if (am != null) {
        am.abandonAudioFocus(this);
      }
    }
  }

  private void requestAudioFocusIfNeeded () {
    if (!hasAudioFocus() || !audioFocusRequested) {
      requestAudioFocus();
    }
  }

  private void setAudioFocusState (int state) {
    if (audioFocusState != state) {
      boolean wasSilent = reduceVolume(state);
      this.audioFocusState = state;
      boolean hasFocus = hasFocus(state);
      boolean isSilent = reduceVolume(state);

      TdlibManager.instance().player().setPauseReason(TGPlayerController.PAUSE_REASON_FOCUS, !hasFocus);

      if (wasSilent != isSilent) {
        TdlibManager.instance().player().setReduceVolume(isSilent);
      }
    }
  }

  private static boolean hasFocus (int focus) {
    return focus == AudioManager.AUDIOFOCUS_GAIN || focus == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
  }

  private static boolean reduceVolume (int focus) {
    return focus == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
  }

  @Override
  public void onAudioFocusChange (int focusChange) {
    setAudioFocusState(focusChange);
  }
}
