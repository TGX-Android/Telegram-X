package org.thunderdog.challegram.player;

import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.IllegalSeekPositionException;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGAudio;
import org.thunderdog.challegram.service.AudioService;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.MathUtils;

/**
 * Date: 1/13/18
 * Author: default
 */

public class AudioController extends BasePlaybackController implements TGAudio.PlayListener, TGPlayerController.TrackListChangeListener, FactorAnimator.Target {
  private final TdlibManager context;

  public AudioController (TdlibManager context, TGPlayerController controller) {
    this.context = context;
    controller.addTrackChangeListener(this);
    controller.addTrackListChangeListener(this, false);
  }

  // Common entry point.
  // At this step, we decide whether we want to use DynamicConcatenatingMediaSource or not

  private static final int PLAYBACK_MODE_UNSET = 0;
  private static final int PLAYBACK_MODE_LEGACY = 1;
  private static final int PLAYBACK_MODE_EXOPLAYER_LIST = 3;

  private int determineBestPlaybackMode (boolean voiceMessages) {
    int maxSupportedMode;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      maxSupportedMode = PLAYBACK_MODE_EXOPLAYER_LIST;
    } else {
      maxSupportedMode = PLAYBACK_MODE_LEGACY;
    }
    int preferredMode = Settings.instance().getPreferredAudioPlaybackMode();
    if (preferredMode > PLAYBACK_MODE_UNSET && preferredMode <= maxSupportedMode) {
      return preferredMode;
    }
    return maxSupportedMode;
  }

  private int playbackMode;
  private int playFlags;
  private TGAudio legacyAudio;
  @Nullable
  private SimpleExoPlayer exoPlayer;
  // private ConcatenatingMediaSource mediaList;
  private ArrayList<TdApi.Message> playList;
  private boolean isPlaying;
  private int playIndex = -1;

  private float volume = 1.0f;
  private boolean reduceVolume;
  private FactorAnimator volumeAnimator;

  private static final float VOLUME_REDUCED = .035f;

  public void setReduceVolume (boolean reduceVolume) {
    if (this.reduceVolume != reduceVolume) {
      this.reduceVolume = reduceVolume;
      if (volumeAnimator == null) {
        volumeAnimator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 300l, this.volume);
      }
      final float toFactor = reduceVolume ? VOLUME_REDUCED : 1f;
      boolean animated = playbackMode == PLAYBACK_MODE_EXOPLAYER_LIST && exoPlayer != null && isPlaying;
      if (animated) {
        volumeAnimator.animateTo(toFactor);
      } else {
        volumeAnimator.forceFactor(toFactor);
        setVolume(toFactor);
      }
    }
  }

  private void setVolume (float volume) {
    if (this.volume != volume) {
      this.volume = volume;
      if (exoPlayer != null) {
        exoPlayer.setVolume(volume);
      }
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case 0:
        setVolume(factor);
        break;
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private boolean inReverseMode () {
    return (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
  }

  /*private boolean inShuffleMode () {
    return (playFlags & TGPlayerController.PLAY_FLAG_SHUFFLE) != 0;
  }*/

  private void setPlaybackMode (int mode, boolean needForeground) {
    if (this.playbackMode != mode) {
      this.playbackMode = mode;
      if (mode == PLAYBACK_MODE_EXOPLAYER_LIST) {
        UI.startService(new Intent(UI.getAppContext(), AudioService.class), needForeground, false);
      }
    }
  }

  @Override
  protected boolean isSupported (TdApi.Message message) {
    switch (message.content.getConstructor()) {
      case TdApi.MessageAudio.CONSTRUCTOR:
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  protected boolean isPlayingSomething () {
    return playIndex != -1 && playIndex >= 0 && playIndex < playList.size();
  }

  protected boolean isPlayingVoice () {
    return isPlayingSomething() && playList.get(playIndex).content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR;
  }

  @Override
  protected void finishPlayback (Tdlib tdlib, TdApi.Message message, boolean byUserRequest) {
    switch (playbackMode) {
      case PLAYBACK_MODE_LEGACY: {
        Media.instance().stopAudio();
        break;
      }
      case PLAYBACK_MODE_EXOPLAYER_LIST: {
        if (playList != null) {
          playList.clear();
          playIndex = -1;
        }
        if (exoPlayer != null) {
          if (isPlaying) {
            final ExoPlayer finishingExoPlayer = exoPlayer;
            finishingExoPlayer.removeListener(this);
            final AtomicBoolean exoPlayerReleased = new AtomicBoolean(false);
            final Runnable act = () -> {
              if (!exoPlayerReleased.getAndSet(true)) {
                finishingExoPlayer.release();
              }
            };
            finishingExoPlayer.addListener(new Player.EventListener() {
              private void releaseExoPlayer () {
                if (!isReleased) {
                  isReleased = true;
                  UI.post(act, 250);
                }
              }

              private boolean isReleased;

              @Override
              public void onPlayerError (@NonNull PlaybackException error) {
                releaseExoPlayer();
              }

              @Override
              public void onPlayerStateChanged (boolean playWhenReady, int playbackState) {
                if (playbackState == Player.STATE_IDLE || !playWhenReady) {
                  releaseExoPlayer();
                }
              }
            });
            finishingExoPlayer.setPlayWhenReady(false);
            UI.post(act, 1000);
          } else {
            exoPlayer.release();
          }
          exoPlayer = null;
        }
        // TODO setEarpieceMode(Settings.EARPIECE_MODE_NEVER);
        break;
      }
    }
    setPlaybackMode(PLAYBACK_MODE_UNSET, false);
  }

  @Override
  protected void playPause (boolean isPlaying) {
    switch (playbackMode) {
      case PLAYBACK_MODE_LEGACY: {
        if (isPlaying) {
          Media.instance().playAudio(legacyAudio);
        } else {
          Media.instance().pauseAudio(legacyAudio);
        }
        break;
      }
      case PLAYBACK_MODE_EXOPLAYER_LIST: {
        if (exoPlayer != null) {
          exoPlayer.setPlayWhenReady(this.isPlaying = isPlaying);
        }
        break;
      }
    }
  }

  @Override
  protected void startPlayback (Tdlib tdlib, TdApi.Message message, boolean byUserRequest, boolean hadObject, Tdlib previousTdlib, int previousFileId) {
    if (playbackMode == PLAYBACK_MODE_UNSET) {
      setPlaybackMode(determineBestPlaybackMode(message.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR), message.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR);
    }
    Log.i(Log.TAG_PLAYER, "startPlayback mode:%d byUserRequest:%b, hadObject:%b, previousFileId:%d", playbackMode, byUserRequest, hadObject, previousFileId);
    switch (playbackMode) {
      case PLAYBACK_MODE_LEGACY: {
        if (message.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR) {
          legacyAudio = new TGAudio(tdlib, message, ((TdApi.MessageVoiceNote) message.content).voiceNote);
        } else {
          legacyAudio = new TGAudio(tdlib, message, ((TdApi.MessageAudio) message.content).audio);
        }
        legacyAudio.setPlayListener(this);
        Media.instance().playAudio(legacyAudio);
        break;
      }
      case PLAYBACK_MODE_EXOPLAYER_LIST: {
        if (!byUserRequest && hadObject) {
          return;
        }
        this.isPlaying = true;
        break;
      }
    }
  }

  @Override
  protected void displayPlaybackError (PlaybackException e) {
    UI.showToast(U.isUnsupportedFormat(e) ? R.string.AudioPlaybackUnsupported : R.string.AudioPlaybackError, Toast.LENGTH_SHORT);
  }

  // Legacy entry point.

  @Override
  public void onPlayChanged (TGAudio audio, int fileId, boolean isPlaying) { }

  @Override
  public void onPlayProgress (TGAudio audio, int fileId, float progress) {
    if (tdlib != null && audio.tdlib() == tdlib && object != null && TD.getFileId(object) == fileId) {
      context.player().setPlayProgress(tdlib, object.chatId, object.id, fileId, progress, -1, -1, false);
    }
  }

  // List entry point.
  // At this step, we keep information inside DynamicConcatenatingMediaSource, if needed

  // private int earpieceMode = Settings.EARPIECE_MODE_NEVER;

  @UiThread
  @Override
  public void onTrackListReset (Tdlib tdlib, @NonNull TdApi.Message currentTrack, int trackIndex, List<TdApi.Message> trackList, long playListChatId, int playFlags, int playState) {
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST) {
      return;
    }

    this.playFlags = playFlags;
    boolean reverseMode = inReverseMode();

    Log.i(Log.TAG_PLAYER, "trackList reset id:%d, index:%d, totalSize:%d", currentTrack.id, trackIndex, trackList.size());

    if (this.playList == null) {
      this.playList = new ArrayList<>();
    } else {
      this.playList.clear();
    }
    this.playList.ensureCapacity(trackList.size());
    this.playList.addAll(trackList);
    this.playIndex = trackIndex;
    final int count = trackList.size();

    ArrayList<MediaSource> mediaSources = new ArrayList<>(count);
    if (reverseMode) {
      for (int i = count - 1; i >= 0; i--) {
        TdApi.Message track = trackList.get(i);
        MediaSource source = U.newMediaSource(tdlib.id(), track);
        mediaSources.add(source);
      }
    } else {
      for (TdApi.Message track : trackList) {
        MediaSource source = U.newMediaSource(tdlib.id(), track);
        mediaSources.add(source);
      }
    }

    if (exoPlayer != null) {
      exoPlayer.removeListener(this);
      exoPlayer.release();
      exoPlayer = null;
    }

    exoPlayer = U.newExoPlayer(UI.getAppContext(), true);
    exoPlayer.addListener(this);
    setExoPlayerParameters();
    exoPlayer.setVolume(volume);
    switch (TGPlayerController.getPlayRepeatFlag(playFlags)) {
      case TGPlayerController.PLAY_FLAG_REPEAT:
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        break;
      case TGPlayerController.PLAY_FLAG_REPEAT_ONE:
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
        break;
    }
    if ((playFlags & TGPlayerController.PLAY_FLAG_SHUFFLE) != 0) {
      exoPlayer.setShuffleModeEnabled(true);
    }

    exoPlayer.setMediaSources(mediaSources);
    exoPlayer.prepare();
    seekTo(exoPlayer, trackIndex, mediaSources.size(), reverseMode);
    exoPlayer.setPlayWhenReady(isPlaying);
  }

  @Override
  public void onPlayerError (PlaybackException e) {
    displayPlaybackError(e);
    if (playbackMode != PLAYBACK_MODE_UNSET) {
      context.player().playNextMessageInQueue();
    }
  }

  @Override
  public void onTrackListPositionChange (Tdlib tdlib, TdApi.Message newTrack, int newIndex, List<TdApi.Message> trackList, boolean byUserRequest, int playState) {
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST || exoPlayer == null) {
      return;
    }
    if (playIndex == newIndex) {
      return;
    }

    Log.i(Log.TAG_PLAYER, "trackList position change id:%d, newIndex:%d, totalCount:%d, byUserRequest:%b", newTrack.id, newIndex, trackList.size(), byUserRequest);

    playIndex = newIndex;
    if (!ignoreNext) {
      seekTo(exoPlayer, newIndex, playList.size(), (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0);
      exoPlayer.setPlayWhenReady(isPlaying);
    }
  }

  @UiThread
  @Override
  public void onTrackListClose (Tdlib tdlib, long playListChatId, long playListMaxMessageId, long playListMinMessageId, boolean newEndReached, boolean oldEndReached, List<TdApi.Message> removedMessages) {
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST) {
      return;
    }

    clearApic();

    // Nothing to do?

  }

  @UiThread
  @Override
  public void onTrackListItemAdded (Tdlib tdlib, TdApi.Message newTrack, int position) {
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST) {
      return;
    }
    playList.add(position, newTrack);
    if (position <= playIndex) {
      playIndex++;
    }
    MediaSource mediaSource = U.newMediaSource(tdlib.id(), newTrack);
    int currentSize = exoPlayer.getMediaItemCount();
    int atIndex = inReverseMode() ? currentSize - position : position;
    exoPlayer.addMediaSource(atIndex, mediaSource);
  }

  @UiThread
  @Override
  public void onTrackListItemRemoved (Tdlib tdlib, TdApi.Message track, int position, boolean isCurrent) {
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST || exoPlayer == null) {
      return;
    }

    final boolean reverse = inReverseMode();
    int nextPlayIndex = position;
    TdApi.Message nextMessage = null;
    if (isCurrent) {
      int nextIndex = exoPlayer.getNextWindowIndex();
      if (nextIndex == C.INDEX_UNSET) {
        nextMessage = null;
        nextPlayIndex = -1;
      } else if (reverse) {
        nextPlayIndex = playList.size() - nextIndex - 1;
      } else {
        nextPlayIndex = nextIndex;
      }
      if (nextPlayIndex != C.INDEX_UNSET) {
        nextMessage = playList.get(nextPlayIndex);
      }
    }
    playList.remove(position);
    if (position < nextPlayIndex) {
      nextPlayIndex--;
    }
    if (isCurrent) {
      playIndex = nextPlayIndex;
    } else if (position < playIndex) {
      playIndex--;
    }
    int currentSize = exoPlayer.getMediaItemCount();
    if (reverse) {
      exoPlayer.removeMediaItem(currentSize - position - 1);
    } else {
      exoPlayer.removeMediaItem(position);
    }

    if (isCurrent) {
      onNextSongReached(tdlib, nextMessage);
    }

    /*
    exoPlayer.seekToDefaultPosition(nextIndex);*/

    /*if (isCurrent && isPlaying) {
      exoPlayer.setPlayWhenReady(true);
    }*/
  }

  private boolean ignoreNext;

  private void onNextSongReached (Tdlib tdlib, TdApi.Message message) {
    ignoreNext = true;
    context.player().playPauseMessageEventually(tdlib, message, true);
    ignoreNext = false;
  }

  @UiThread
  @Override
  public void onTrackListItemMoved (Tdlib tdlib, TdApi.Message track, int fromPosition, int toPosition) {
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST) {
      return;
    }
    ArrayUtils.move(playList, fromPosition, toPosition);
    if (playIndex == fromPosition) {
      playIndex = toPosition;
    } else {
      if (fromPosition < playIndex) {
        playIndex--;
      }
      if (toPosition <= playIndex) {
        playIndex++;
      }
    }
    int currentSize = exoPlayer.getMediaItemCount();
    if (inReverseMode()) {
      exoPlayer.moveMediaItem(currentSize - fromPosition - 1, currentSize - toPosition - 1);
    } else {
      exoPlayer.moveMediaItem(fromPosition, toPosition);
    }
  }

  @UiThread
  @Override
  public void onTrackListItemRangeAdded (Tdlib tdlib, List<TdApi.Message> addedItems, boolean areNew) {
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST) {
      return;
    }
    if (areNew) {
      playList.addAll(addedItems);
    } else {
      playList.addAll(0, addedItems);
    }

    ArrayList<MediaSource> newItems = new ArrayList<>(addedItems.size());
    int remaining = addedItems.size();
    boolean reverseOrder = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;
    while (--remaining >= 0) {
      TdApi.Message addedTrack = addedItems.get(reverseOrder ? remaining : addedItems.size() - 1 - remaining);
      newItems.add(U.newMediaSource(tdlib.id(), addedTrack));
    }

    boolean addOnBottom = reverseOrder != areNew;
    if (addOnBottom) {
      exoPlayer.addMediaSources(newItems);
    } else {
      exoPlayer.addMediaSources(0, newItems);
    }

    if (!areNew) {
      playIndex += addedItems.size();
    }
  }

  @Override
  public void onPlaybackSpeedChanged (int newSpeed) {
    if (playbackMode == PLAYBACK_MODE_EXOPLAYER_LIST && exoPlayer != null) {
      exoPlayer.setPlaybackParameters(TGPlayerController.newPlaybackParameters(isPlayingVoice(), newSpeed));
    }
  }

  @Override
  public void onTrackListFlagsChanged (int playFlags) {
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST || exoPlayer == null) {
      return;
    }

    boolean wasReverse = inReverseMode();
    boolean nowReverse = (playFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0;

    if (wasReverse != nowReverse) {
      int totalCount = exoPlayer.getMediaItemCount();
      int currentIndex = reversePosition(playIndex, totalCount, wasReverse);
      for (int i = currentIndex - 1; i >= 0; i--) {
        exoPlayer.moveMediaItem(i, i + (currentIndex - i));
      }
      for (int i = currentIndex + 1; i < totalCount; i++) {
        exoPlayer.moveMediaItem(i, 0);
      }
    }

    int prevLoopMode = TGPlayerController.getPlayRepeatFlag(this.playFlags);
    int newLoopMode = TGPlayerController.getPlayRepeatFlag(playFlags);
    if (prevLoopMode != newLoopMode) {
      switch (newLoopMode) {
        case TGPlayerController.PLAY_FLAG_REPEAT:
          exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
          break;
        case TGPlayerController.PLAY_FLAG_REPEAT_ONE:
          exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
          break;
        default:
          exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
          break;
      }
    }

    boolean wasShuffling = (this.playFlags & TGPlayerController.PLAY_FLAG_SHUFFLE) != 0;
    boolean nowShuffling = (playFlags & TGPlayerController.PLAY_FLAG_SHUFFLE) != 0;
    if (wasShuffling != nowShuffling) {
      exoPlayer.setShuffleModeEnabled(nowShuffling);
    }

    this.playFlags = playFlags;
  }

  @Override
  public void onTrackListLoadStateChanged () { }

  // ExoPlayer seek

  private static void seekTo (@NonNull Player player, int windowIndex, int totalSize, boolean reverse) {
    Log.i(Log.TAG_PLAYER, "seekTo windowIndex:%d size:%d, reverse:%b", windowIndex, totalSize, reverse);
    try {
      player.seekToDefaultPosition(reverse ? totalSize - windowIndex - 1 : windowIndex);
    } catch (IllegalSeekPositionException e) {
      Log.e("Cannot complete seekTo because of bug. Please report to the developer. windowIndex:%d, totalSize:%d, reverse:%b, currentWindowIndex:%d, previousWindowIndex:%d, nextWindowIndex:%d", windowIndex, totalSize, reverse, player.getCurrentWindowIndex(), player.getPreviousWindowIndex(), player.getNextWindowIndex());
    }
  }

  public void skip (boolean next) {
    if (playbackMode == PLAYBACK_MODE_UNSET) {
      return;
    }
    switch (playbackMode) {
      case PLAYBACK_MODE_EXOPLAYER_LIST: {
        if (exoPlayer == null) {
          return;
        }

        int position = exoPlayer.getCurrentWindowIndex();
        if (position == C.POSITION_UNSET) {
          Log.i(Log.TAG_PLAYER, "Couldn't skip, because current position is unset");
          return;
        }

        boolean repeatModeLocked = exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ONE;

        if (repeatModeLocked) {
          exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        }

        int desiredPosition = next ? exoPlayer.getNextWindowIndex() : exoPlayer.getPreviousWindowIndex();
        boolean reverseMode = inReverseMode();
        Log.i(Log.TAG_PLAYER, "skipping track position:%d, desiredPosition:%d reverse:%b", position, desiredPosition, reverseMode);
        if (desiredPosition == C.POSITION_UNSET) {
          context.player().stopPlayback(true);
        } else {
          exoPlayer.seekToDefaultPosition(desiredPosition);
          if (repeatModeLocked) {
            exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
          }
          exoPlayer.setPlayWhenReady(isPlaying = true);
          int reversePosition = reversePosition(desiredPosition, exoPlayer.getMediaItemCount(), reverseMode);
          TdApi.Message message = playList.get(reversePosition);
          context.player().playIfPaused(message);
        }

        break;
      }
    }
  }

  private int reversePosition (int position, int totalSize, boolean reverse) {
    return reverse ? totalSize - position - 1 : position;
  }

  public boolean isSeekable () {
    return playbackMode == PLAYBACK_MODE_EXOPLAYER_LIST && exoPlayer != null && (Config.ALLOW_SEEK_ANYTIME || exoPlayer.isCurrentWindowSeekable());
  }

  public float getMaxSeek (long durationMillis, float desiredSeek) {
    if (durationMillis > 0) {
      return Math.min(desiredSeek, (float) (((double) durationMillis - 5) / (double) durationMillis));
    }
    return desiredSeek;
    /*if (playbackMode == PLAYBACK_MODE_EXOPLAYER_LIST && exoPlayer != null) {
      Timeline timeline = exoPlayer.getCurrentTimeline();
      if (timeline != null) {
        int count = timeline.getPeriodCount();
        if (count > 0) {
          if (period == null) {
            period = new Timeline.Period();
          }
          timeline.getPeriod(count - 1, period);

          long position = period.getPositionInWindowMs();
          long duration = period.getDurationMs();
          long totalDuration = exoPlayer.getDuration();
          if (position >= 0 && duration > 0 && duration < totalDuration) {
            return Math.min((float) ((double) (position + duration) / (double) totalDuration), desiredSeek);
          }
        }
      }
    }
    return desiredSeek;*/
  }

  public void seekTo (long positionMillis, long durationMillis) {
    if (playbackMode == PLAYBACK_MODE_UNSET) {
      return;
    }
    switch (playbackMode) {
      case PLAYBACK_MODE_LEGACY: {
        if (durationMillis != -1 && positionMillis >= 0 && durationMillis >= positionMillis) {
          Media.instance().seekAudio(legacyAudio, MathUtils.clamp((float) ((double) positionMillis / (double) durationMillis)));
        }
        break;
      }
      case PLAYBACK_MODE_EXOPLAYER_LIST:
        if (exoPlayer != null) {
          exoPlayer.seekTo(positionMillis);
        }
        break;
    }
  }

  // ExoPlayer

  @Override
  public void onTimelineChanged (Timeline timeline, int reason) {
    Log.d(Log.TAG_PLAYER, "[state] onTimeLineChanged reason:%d", reason);
  }

  private static ApicFrame findApic (TrackGroupArray trackGroups) {
    for (int i = 0; i < trackGroups.length; i++) {
      TrackGroup trackGroup = trackGroups.get(i);
      for (int j = 0; j < trackGroup.length; j++) {
        Metadata trackMetadata = trackGroup.getFormat(j).metadata;
        if (trackMetadata != null) {
          int metadataCount = trackMetadata.length();
          for (int z = 0; z < metadataCount; z++) {
            Metadata.Entry metadata = trackMetadata.get(z);
            if (metadata instanceof ApicFrame) {
              return (ApicFrame) metadata;
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public void onTracksChanged (TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    Log.d(Log.TAG_PLAYER, "[state] onTracksChanged");
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST || playIndex == -1) {
      return;
    }
    ApicFrame frame = findApic(trackGroups);
    if (frame != null) {
      checkApicDelayed();
    }
  }

  private void checkApicDelayed () {
    UI.post(() -> {
      if (playIndex != -1 && playIndex >= 0 && playIndex < playList.size()) {
        TdApi.Message track = playList.get(playIndex);
        if (!TGPlayerController.compareTracks(currentApicMessage, track)) {
          ApicFrame frame = findApic(exoPlayer.getCurrentTrackGroups());
          if (frame != null) {
            dispatchApic(tdlib, track, frame);
          }
        }
      }
    }, 50);

  }

  @Override
  public void onLoadingChanged (boolean isLoading) {
    Log.d(Log.TAG_PLAYER, "[state] onLoadingChanged %b", isLoading);
  }

  private long bufferingStartTime;

  private void checkPlayerPosition () {
    if (playbackMode == PLAYBACK_MODE_EXOPLAYER_LIST && exoPlayer != null && playList != null) {
      int windowIndex = exoPlayer.getCurrentWindowIndex();
      if (windowIndex != -1 && inReverseMode()) {
        windowIndex = reversePosition(windowIndex, playList.size(), true);
      }
      if (windowIndex != playIndex && windowIndex >= 0 && windowIndex < playList.size()) {
        Log.i(Log.TAG_PLAYER, "Next track reached, updating UI only %d -> %d", playIndex, windowIndex);
        playIndex = windowIndex;
        // TODO reset time
        onNextSongReached(tdlib, playList.get(windowIndex));
      }
    }
  }

  @Override
  public void onPlayerStateChanged (boolean playWhenReady, int playbackState) {
    setInProgressLoop(playbackState == Player.STATE_READY);

    boolean isBuffering = playbackState == Player.STATE_BUFFERING;
    boolean wasBuffering = bufferingStartTime != 0;
    if (isBuffering != wasBuffering) {
      if (isBuffering) {
        bufferingStartTime = SystemClock.uptimeMillis();
      } else {
        Log.i(Log.TAG_PLAYER, "[state] buffering finished in %dms", SystemClock.uptimeMillis() - bufferingStartTime);
        bufferingStartTime = 0;
      }
      checkTimes();
    }

    Log.d(Log.TAG_PLAYER, "[state] onPlayerStateChanged mode:%d, playWhenReady:%b, state:%d", playbackMode, playWhenReady, playbackState);

    switch (playbackState) {
      case Player.STATE_BUFFERING: {
        if (playbackMode == PLAYBACK_MODE_EXOPLAYER_LIST && exoPlayer != null && exoPlayer.getRepeatMode() != Player.REPEAT_MODE_ONE) {
          long currentPosition = exoPlayer.getCurrentPosition();
          long currentDuration = exoPlayer.getDuration();
          int currentWindowIndex = exoPlayer.getCurrentWindowIndex();
          int nextWindowIndex = exoPlayer.getNextWindowIndex();

          if (currentPosition != C.TIME_UNSET && currentDuration != C.TIME_UNSET && currentWindowIndex != C.INDEX_UNSET && nextWindowIndex != C.INDEX_UNSET && currentDuration >= 5000 && currentDuration - currentPosition < 500) {
            final int totalSize = exoPlayer.getMediaItemCount();
            TdApi.File file = TD.getFile(playList.get(reversePosition(currentWindowIndex, totalSize, inReverseMode())));
            boolean fileLoaded = file != null && file.local.isDownloadingCompleted;
            Log.i(Log.TAG_PLAYER, "[state] seeking to the next window, because we received buffering at the end of the current file, fileLoaded:%b");
            if (fileLoaded) {
              seekTo(exoPlayer, nextWindowIndex, totalSize, false);
            }
          }
        }
        break;
      }
      case Player.STATE_ENDED: {
        if (playbackMode != PLAYBACK_MODE_UNSET && !(playbackMode == PLAYBACK_MODE_EXOPLAYER_LIST && exoPlayer != null && exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ONE)) {
          context.player().playNextMessageInQueue();
        }
        break;
      }
    }
  }

  @Override
  public void onRepeatModeChanged (int repeatMode) { }

  @Override
  public void onShuffleModeEnabledChanged (boolean shuffleModeEnabled) { }

  @Override
  public void onPositionDiscontinuity (int reason) {
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST) {
      return;
    }
    Log.d(Log.TAG_PLAYER, "[state] onPositionDiscontinuity mode:%d, reason:%d", playbackMode, reason);
    checkPlayerPosition();
    // findApic(exoPlayer.getCurrentTrackGroups());
  }

  @Override
  public void onPlaybackParametersChanged (PlaybackParameters playbackParameters) {
    Log.d(Log.TAG_PLAYER, "[state] onPlaybackParametersChanged");
  }

  @Override
  public void onSeekProcessed () {
    Log.d(Log.TAG_PLAYER, "[state] onSeekProcessed");
  }

  // Progress loop helper

  private static class ProgressHandler extends Handler {
    private final AudioController context;
    public ProgressHandler (AudioController context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      if (context.inProgressLoop) {
        context.checkTimes();
        context.checkTimesDelayed();
      }
    }
  }

  private ProgressHandler progressHandler;
  private boolean inProgressLoop;

  private long calculateProgressUpdateDelayMillis () {
    // TODO calculate better delay?
    long duration = exoPlayer != null ? exoPlayer.getDuration() : C.TIME_UNSET;
    if (duration == C.TIME_UNSET || duration < 1000) {
      return 25;
    } else {
      long delay = (long) (30.0 * Math.max(1.0, (double) duration / 30000.0));
      if (delay >= 1000l && context.player().hasValuableTarget()) {
        delay = 1000l;
      }
      return delay;
    }
  }

  private void setInProgressLoop (boolean inProgressLoop) {
    if (this.inProgressLoop != inProgressLoop) {
      this.inProgressLoop = inProgressLoop;

      if (inProgressLoop) {
        if (progressHandler == null) {
          progressHandler = new ProgressHandler(this);
        }
        checkTimesDelayed();
      } else {
        if (progressHandler != null) {
          progressHandler.removeMessages(0);
        }
        checkTimes();
      }
    } else if (!inProgressLoop) {
      checkTimes();
    }
  }

  private void checkTimesDelayed () {
    progressHandler.sendMessageDelayed(Message.obtain(progressHandler, 0), calculateProgressUpdateDelayMillis());
  }

  public void checkTimesIfNeeded () {
    if (inProgressLoop) {
      boolean removedMessages = false;
      if (progressHandler.hasMessages(0)) {
        progressHandler.removeMessages(0);
        removedMessages = true;
      }
      checkTimes();
      if (removedMessages) {
        checkTimesDelayed();
      }
    }
  }

  private void checkTimes () {
    if (playbackMode != PLAYBACK_MODE_EXOPLAYER_LIST) {
      return;
    }
    if (exoPlayer == null) {
      return;
    }

    long duration = exoPlayer.getDuration();
    long position = exoPlayer.getCurrentPosition();

    if (duration == C.TIME_UNSET && position == C.TIME_UNSET) {
      return;
    }

    // FIXME make it in a proper way when will be needed
    duration = Math.max(-1, duration);
    position = Math.max(-1, position);

    float progress = duration <= 0 ? 0 : position >= duration ? 1 : (float) ((double) position / (double) duration);

    if (object != null) {
      context.player().setPlayProgress(tdlib, object.chatId, object.id, TD.getFileId(object), progress, position, duration, exoPlayer.getPlaybackState() == Player.STATE_BUFFERING);
    }
  }

  // Album art

  private Tdlib currentApicTdlib;
  private TdApi.Message currentApicMessage;
  private ApicFrame currentApic;

  public interface ApicListener {
    void onApicLoaded (Tdlib tdlib, TdApi.Message message, ApicFrame apicFrame);
  }

  private static class ApicTarget {
    private final Tdlib tdlib;
    private final TdApi.Message message;
    private final ArrayList<ApicListener> listeners;

    public ApicTarget (Tdlib tdlib, TdApi.Message message, ApicListener initialListener) {
      this.tdlib = tdlib;
      this.message = message;
      this.listeners = new ArrayList<>();
      this.listeners.add(initialListener);
    }

    public void dispatchAvailable (ApicFrame frame) {
      for (ApicListener listener : listeners) {
        listener.onApicLoaded(tdlib, message, frame);
      }
    }
  }

  private ArrayList<ApicTarget> apicTargets;

  private void clearApic () {
    currentApicTdlib = null;
    currentApic = null;
    currentApicMessage = null;
  }

  private void dispatchApic (Tdlib tdlib, TdApi.Message message, ApicFrame apicFrame) {
    synchronized (this) {
      if (TGPlayerController.compareTracks(this.currentApicMessage, message) && currentApic != null) {
        return;
      }
      currentApicTdlib = tdlib;
      currentApicMessage = message;
      currentApic = apicFrame;
      if (apicTargets != null) {
        int i = 0;
        for (ApicTarget target : apicTargets) {
          if (target.tdlib == tdlib && TGPlayerController.compareTracks(target.message, message)) {
            target.dispatchAvailable(apicFrame);
            target.listeners.clear();
            apicTargets.remove(i);
            break;
          }
          i++;
        }
      }
    }
  }

  public void cancelApic (Tdlib tdlib, TdApi.Message message, ApicListener listener) {
    synchronized (this) {
      if (apicTargets != null) {
        int i = 0;
        for (ApicTarget target : apicTargets) {
          if (TGPlayerController.compareTracks(target.tdlib, tdlib, target.message, message)) {
            if (target.listeners.remove(listener) && target.listeners.isEmpty()) {
              apicTargets.remove(i);
            }
            break;
          }
          i++;
        }
      }
    }
  }

  public ApicFrame requestApic (Tdlib tdlib, TdApi.Message message, ApicListener listener) {
    synchronized (this) {
      if (TGPlayerController.compareTracks(currentApicTdlib, tdlib, currentApicMessage, message)) {
        listener.onApicLoaded(tdlib, message, currentApic);
        return currentApic;
      }
      if (apicTargets != null) {
        for (ApicTarget target : apicTargets) {
          if (target.tdlib == tdlib && TGPlayerController.compareTracks(target.message, message)) {
            target.listeners.add(listener);
            return null;
          }
        }
      } else {
        apicTargets = new ArrayList<>();
      }
      apicTargets.add(new ApicTarget(tdlib, message, listener));
    }
    return null;
  }

  // Raise to Listen

  private void setExoPlayerParameters () {
    if (exoPlayer != null) {
      context.player().proximityManager().modifyExoPlayer(exoPlayer, C.CONTENT_TYPE_MUSIC);
    }
  }

  @Override
  public void onPlaybackParametersChanged (Tdlib tdlib, @NonNull TdApi.Message track) {
    if (comparePlayingObject(tdlib, track)) {
      setExoPlayerParameters();
    }
  }
}
