/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 28/08/2017
 */
package org.thunderdog.challegram.player;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.PlaybackParameters;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.GlobalMessageListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.ui.PlaybackController;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.reference.ReferenceMap;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class TGPlayerController implements GlobalMessageListener, ProximityManager.Delegate {
  private static final int STATE_SEEK = -1; // This is used when dispatching seek progress
  public static final int STATE_NONE = 0; // Not playing
  public static final int STATE_LOADING = 1; // Loading, unable to play/pause
  public static final int STATE_PAUSED = 2; // Paused, able to play
  public static final int STATE_PLAYING = 3; // Playing, able to pause

  public static final int PLAY_FLAG_SHUFFLE = 1;
  public static final int PLAY_FLAG_REPEAT = 1 << 1;  // go to first item, when the last entry finished
  public static final int PLAY_FLAG_REPEAT_ONE = 1 << 2; // repeat current entry, until battery or user dies
  public static final int PLAY_FLAGS_DEFAULT = PLAY_FLAG_REPEAT;

  public static final int PLAY_SPEED_NORMAL = 0;
  public static final int PLAY_SPEED_2X = 1;
  public static final int PLAY_SPEED_3X = 2;
  public static final int PLAY_SPEED_4X = 2;

  private int speed;

  public static final int PLAYLIST_FLAG_REVERSE = 1 << 15;  // e.g. when starting music playback from the shared media we need to go from newer to older items
  public static final int PLAYLIST_FLAG_ALTERED = 1 << 16; // when we are working with user-customized playlist, it's a good idea to not rebuild it each time we see the same song in different lists

  public static final int PLAYLIST_FLAGS_MASK = PLAYLIST_FLAG_REVERSE | PLAYLIST_FLAG_ALTERED;

  public interface TrackChangeListener {
    void onTrackChanged (Tdlib tdlib, @Nullable TdApi.Message newTrack, int fileId, int state, float progress, boolean byUser);
    default void onPlaybackSpeedChanged (int newSpeed) { }
    default void onPlaybackParametersChanged (Tdlib tdlib, @NonNull TdApi.Message track) { }
  }

  public interface TrackListener {
    void onTrackStateChanged (Tdlib tdlib, long chatId, long messageId, int fileId, int state);
    void onTrackPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long position, long totalDuration, boolean isBuffering);
  }

  public interface TrackListChangeListener {
    void onTrackListReset (Tdlib tdlib, @NonNull TdApi.Message currentTrack, int trackIndex, List<TdApi.Message> trackList, long playListChatId, int playFlags, int playState);
    void onTrackListPositionChange (Tdlib tdlib, TdApi.Message newTrack, int newIndex, List<TdApi.Message> trackList, boolean byUserRequest, int playState);
    void onTrackListClose (Tdlib tdlib, long playListChatId, long playListMaxMessageId, long playListMinMessageId, boolean newEndReached, boolean oldEndReached, List<TdApi.Message> removedMessages);

    void onTrackListItemAdded (Tdlib tdlib, TdApi.Message newTrack, int position);
    void onTrackListItemMoved (Tdlib tdlib, TdApi.Message track, int fromPosition, int toPosition);
    void onTrackListItemRemoved (Tdlib tdlib, TdApi.Message track, int position, boolean isCurrent);
    void onTrackListItemRangeAdded (Tdlib tdlib, List<TdApi.Message> addedItems, boolean areNew);

    void onTrackListFlagsChanged (int newPlayFlags);
    void onTrackListLoadStateChanged ();
  }

  private final ReferenceList<TrackChangeListener> globalListeners = new ReferenceList<>(false, false, null);
  private final ReferenceList<TrackListChangeListener> trackListChangeListeners = new ReferenceList<>(false, false, null);
  private final ReferenceMap<String, TrackListener> targetListeners = new ReferenceMap<>(false, false, null);
  private final PlayHandler handler = new PlayHandler(this);

  private static final int LIST_STATE_LOADING_NEWER = 1;
  private static final int LIST_STATE_LOADING_OLDER = 1 << 1;
  private static final int LIST_STATE_LOADED_NEW = 1 << 2;
  private static final int LIST_STATE_LOADED_OLD = 1 << 3;

  private int messageListStateFlags;
  private final List<TdApi.Message> messageList = new ArrayList<>();
  private final List<TdApi.Message> removedMessageList = new ArrayList<>();
  private final HashMap<Integer, List<TdApi.File>> filesMap = new HashMap<>();
  private int messageListContextId;

  private Tdlib tdlib;
  private @Nullable TdApi.Message message;
  private int playState;

  private static final boolean BUFFERING_DEFAULT = false;

  private float playProgress;
  private long playPosition = -1, playDuration = -1;
  private boolean playBuffering = BUFFERING_DEFAULT;

  public static boolean compareTracks (Tdlib ta, Tdlib tb, TdApi.Message a, TdApi.Message b) {
    return ta == tb && compareTracks(a, b);
  }

  public static boolean compareTracks (TdApi.Message a, TdApi.Message b) {
    return (a == null && b == null) || (a == b) || (a != null && b != null && a.chatId == b.chatId && a.id == b.id && compareTrackFiles(TD.getFile(a), TD.getFile(b)));
  }

  public static boolean compareTracks (Tdlib ta, Tdlib tb, TdApi.Message a, long chatId, long messageId, int fileId) {
    return ta == tb && compareTracks(a, chatId, messageId, fileId);
  }

  public static boolean compareTracks (TdApi.Message a, long chatId, long messageId, int fileId) {
    return (a != null && a.chatId == chatId && a.id == messageId && TD.getFileId(a) == fileId);
  }

  public static boolean compareTrackFiles (TdApi.File a, TdApi.File b) {
    if (a == null && b == null)
      return true;
    if (a == null || b == null || a.id != b.id)
      return false;
    if (a.id == -1)
      return StringUtils.equalsOrBothEmpty(a.remote != null ? a.remote.id : null, b.remote != null ? b.remote.id : null);
    return true;
  }

  private int playbackFlags; // preserved between playlists and controlled by TGPlayerController
  private int playListFlags; // runtime only: controlled by PlayListBuilder or via specific user requests


  private long playlistChatId;
  private long playlistMessageThreadId;
  private long playlistMaxMessageId, playlistMinMessageId;
  private String playlistSearchQuery;
  private TdApi.GetInlineQueryResults playlistInlineQuery;
  private String playlistInlineNextOffset, playlistSecretNextOffset;

  private final ProximityManager proximityManager;
  private final TdlibManager context;

  public TGPlayerController (TdlibManager context) {
    this.context = context;
    context.global().addMessageListener(this);
    this.playbackFlags = Settings.instance().getPlayerFlags();
    this.proximityManager = new ProximityManager(this, this);
  }

  public void onUpdateFile (Tdlib tdlib, TdApi.UpdateFile updateFile) {
    synchronized (this) {
      if (this.tdlib != null && this.tdlib.id() == tdlib.id()) {
        List<TdApi.File> files = filesMap.get(updateFile.file.id);
        if (files != null) {
          for (TdApi.File existingFile : files) {
            if (existingFile.id == updateFile.file.id) {
              Td.copyTo(updateFile.file, existingFile);
            }
          }
        }
      }
    }
  }

  private static boolean isValuableTarget (Object target) {
    return target instanceof PlaybackController;
  }

  /**
   * @return Tells whether there's any listener that requires timestamp updates at least once in 1 second
   */
  public boolean hasValuableTarget () {
    for (TrackListChangeListener listener : trackListChangeListeners) {
      if (isValuableTarget(listener))
        return true;
    }
    return false;
  }

  public void setReduceVolume (boolean reduceVolume) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_REDUCE_VOLUME, reduceVolume ? 1 : 0, 0));
      return;
    }
    synchronized (this) {
      context.audio().setReduceVolume(reduceVolume);
    }
  }

  public void toggleReverseMode () {
    synchronized (this) {
      if (this.message != null && playState != STATE_NONE) {
        playListFlags ^= PLAYLIST_FLAG_REVERSE;
        notifyTrackListFlagsChanged(trackListChangeListeners, getSessionFlags());
        ensureStackAvailability(FORCE_REASON_NONE);
      }
    }
  }

  public ProximityManager proximityManager () {
    return proximityManager;
  }

  public boolean canReverseOrder () {
    synchronized (this) {
      if (this.message != null && playState != STATE_NONE) {
        if (messageList.size() >= 3) {
          return true;
        }
        if ((playListFlags & TGPlayerController.PLAYLIST_FLAG_REVERSE) != 0) {
          return (messageListStateFlags & LIST_STATE_LOADED_NEW) == 0;
        } else {
          return (messageListStateFlags & LIST_STATE_LOADED_OLD) == 0;
        }
      }
    }
    return false;
  }

  public void addToPlayList (TdApi.Message track) {
    synchronized (this) {
      int currentIndex = indexOfCurrentMessage();
      if (currentIndex == -1) {
        return;
      }
      int fromPosition = indexOfMessage(track);
      int addMode = canAddToPlayListImpl(tdlib, track, currentIndex, fromPosition);
      if (addMode == ADD_MODE_NONE) {
        return;
      }

      int insertPosition = (playListFlags & PLAYLIST_FLAG_REVERSE) != 0 ? currentIndex : currentIndex + 1;
      if (addMode == ADD_MODE_MOVE) {
        TdApi.Message existingItem = messageList.remove(fromPosition);
        if (fromPosition <= insertPosition) {
          insertPosition--;
        }
        messageList.add(insertPosition, existingItem);
        notifyTrackListItemMoved(trackListChangeListeners, tdlib, track, fromPosition, insertPosition);
      } else {
        addMessageImpl(insertPosition, track);
        notifyTrackListItemAdded(trackListChangeListeners, tdlib, track, insertPosition);
        if (addMode == ADD_MODE_RESTORE) {
          deleteRemovedTrack(track);
        }
      }

      playListFlags |= PLAYLIST_FLAG_ALTERED;
    }
  }

  private void deleteRemovedTrack (TdApi.Message message) {
    int i = 0;
    for (TdApi.Message removedMessage : removedMessageList) {
      if (compareTracks(removedMessage, message)) {
        removedMessageList.remove(i);
        break;
      }
      i++;
    }
  }

  private void addMessageImpl (int position, TdApi.Message message) {
    messageList.add(position, message);
    addFileImpl(TD.getFile(message));
  }

  private void addMessageImpl (TdApi.Message message) {
    messageList.add(message);
    addFileImpl(TD.getFile(message));
  }

  private void addMessagesImpl (List<TdApi.Message> messages) {
    messageList.addAll(messages);
    for (TdApi.Message message : messages) {
      addFileImpl(TD.getFile(message));
    }
  }

  private void addMessagesImpl (int i, List<TdApi.Message> messages) {
    messageList.addAll(i, messages);
    for (TdApi.Message message : messages) {
      addFileImpl(TD.getFile(message));
    }
  }

  private void clearMessagesImpl () {
    messageList.clear();
    clearFilesImpl();
  }

  private void addFileImpl (@Nullable TdApi.File file) {
    if (file != null && file.id >= 0) {
      List<TdApi.File> files = filesMap.get(file.id);
      if (files == null) {
        files = new ArrayList<>();
        filesMap.put(file.id, files);
      }
      files.add(file);
    }
  }

  private void removeFileImpl (@Nullable TdApi.File file) {
    if (file != null && file.id >= 0) {
      List<TdApi.File> files = filesMap.get(file.id);
      if (files != null) {
        files.remove(file);
        if (files.isEmpty()) {
          filesMap.remove(file.id);
        }
      }
    }
  }

  private void clearFilesImpl () {
    filesMap.clear();
  }

  public boolean isPlayingMusic () {
    synchronized (this) {
      return message != null && message.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR;
    }
  }

  public int canAddToPlayList (Tdlib tdlib, TdApi.Message track) {
    if (track.content.getConstructor() != TdApi.MessageAudio.CONSTRUCTOR) {
      return ADD_MODE_NONE;
    }
    synchronized (this) {
      return canAddToPlayListImpl(tdlib, track, indexOfCurrentMessage(), indexOfMessage(track));
    }
  }

  public static final int ADD_MODE_NONE = 0;
  public static final int ADD_MODE_INSERT = 1;
  public static final int ADD_MODE_MOVE = 2;
  public static final int ADD_MODE_RESTORE = 3;

  private boolean canRestoreImpl (TdApi.Message track) {
    if (message != null && playState != STATE_NONE && message.content.getConstructor() == track.content.getConstructor() && !removedMessageList.isEmpty()) {
      for (TdApi.Message message : removedMessageList) {
        if (compareTracks(message, track)) {
          return true;
        }
      }
    }
    return false;
  }

  private int canAddToPlayListImpl (Tdlib tdlib, TdApi.Message track, int currentPosition, int existingIndex) {
    if (this.tdlib == tdlib && this.message != null && playState != STATE_NONE && this.message.content.getConstructor() == track.content.getConstructor()) {
      if (existingIndex != -1) {
        if ((playListFlags & PLAYLIST_FLAG_REVERSE) != 0) {
          return existingIndex != currentPosition && currentPosition - 1 != existingIndex ? ADD_MODE_MOVE : ADD_MODE_NONE;
        } else {
          return existingIndex != currentPosition && currentPosition + 1 != existingIndex ? ADD_MODE_MOVE : ADD_MODE_NONE;
        }
      }
      if (playlistChatId == 0 || track.chatId != playlistChatId) {
        return ADD_MODE_INSERT;
      } else if (canRestoreImpl(track)) {
        return ADD_MODE_RESTORE;
      }
    }
    return ADD_MODE_NONE;
  }

  public void moveTrack (int fromPosition, int toPosition) {
    synchronized (this) {
      if (this.message != null && playState != STATE_NONE && this.message.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR) {
        TdApi.Message track = messageList.remove(fromPosition);
        messageList.add(toPosition, track);
        notifyTrackListItemMoved(trackListChangeListeners, tdlib, track, fromPosition, toPosition);
        playListFlags |= PLAYLIST_FLAG_ALTERED;
      }
    }
  }

  public void removeTrack (TdApi.Message track, boolean byUserRequest) {
    synchronized (this) {
      if (this.message != null && playState != STATE_NONE && this.message.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR && messageList.size() > 1) {
        int position = indexOfMessage(track);
        removeTrackImpl(track, position, byUserRequest);
      }
    }
  }

  private void removeTrackImpl (TdApi.Message track, int position, boolean byUserRequest) {
    if (position != -1) {
      if (position < 0 || position >= messageList.size()) {
        throw new IllegalStateException();
      }
      if (messageList.size() == 1) {
        stopPlaybackImpl(byUserRequest);
      } else {
        boolean isCurrent = TGPlayerController.compareTracks(track, message);
        messageList.remove(position);
        notifyTrackListItemRemoved(trackListChangeListeners, tdlib, track, position, isCurrent);
        if (byUserRequest) {
          removedMessageList.add(track);
          playListFlags |= PLAYLIST_FLAG_ALTERED;
        }
      }
    }
  }

  public void toggleRepeatMode () {
    int newFlags = playbackFlags;
    int currentMode = getPlayRepeatFlag(newFlags);
    newFlags &= ~currentMode;
    switch (currentMode) {
      case TGPlayerController.PLAY_FLAG_REPEAT:
        newFlags |= TGPlayerController.PLAY_FLAG_REPEAT_ONE;
        break;
      case TGPlayerController.PLAY_FLAG_REPEAT_ONE:
        break;
      default:
        newFlags |= TGPlayerController.PLAY_FLAG_REPEAT;
        break;
    }
    setPlaybackFlags(newFlags);
  }

  public void setPlaybackSpeed (int speed) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_SET_SPEED, speed, 0));
      return;
    }
    if (this.speed != speed) {
      this.speed = speed;
      synchronized (this) {
        notifyTrackListSpeedChanged(globalListeners, speed);
      }
    }
  }

  public static @NonNull PlaybackParameters newPlaybackParameters (boolean isVoice, int speedValue) {
    PlaybackParameters parameters = PlaybackParameters.DEFAULT;
    if (speedValue != TGPlayerController.PLAY_SPEED_NORMAL) {
      float speed;
      float pitch = 1f;
      switch (speedValue) {
        case TGPlayerController.PLAY_SPEED_2X:
          if (isVoice) {
            speed = 1.72f;
            pitch = .98f;
          } else {
            speed = 2f;
          }
          break;
        default:
          speed = 1f;
          break;
      }
      if (speed != 1f) {
        parameters = new PlaybackParameters(speed, pitch);
      }
    }
    return parameters;
  }

  public void togglePlaybackFlag (int flag) {
    int flags = playbackFlags;
    flags ^= flag;
    setPlaybackFlags(flags);
  }

  private void setPlaybackFlags (int flags) {
    if (this.playbackFlags != flags) {
      this.playbackFlags = flags;
      Settings.instance().setPlayerFlags(flags);
      synchronized (this) {
        notifyTrackListFlagsChanged(trackListChangeListeners, getSessionFlags());
      }
    }
  }

  public int getPlaybackSessionFlags () {
    synchronized (this) {
      return playState == STATE_NONE ? playbackFlags : playbackFlags | playListFlags;
    }
  }

  public static int getPlayRepeatFlag (int flags) {
    return (flags & PLAY_FLAG_REPEAT) != 0 ? PLAY_FLAG_REPEAT : (flags & PLAY_FLAG_REPEAT_ONE) != 0 ? PLAY_FLAG_REPEAT_ONE : 0;
  }

  private static boolean supportsPlaybackFlags (TdApi.Message message) {
    return message != null && message.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR;
  }

  private static int getPlaybackFlags (TdApi.Message message, int flags) {
    return message == null || supportsPlaybackFlags(message) ? flags : 0;
  }

  // Getters

  public static String makeTargetKey (Tdlib tdlib, long chatId, long messageId) {
    return tdlib.id() + "_" + chatId + "_" + messageId;
  }

  public int getState () {
    synchronized (this) {
      return playState;
    }
  }

  public long getMessageId () {
    synchronized (this) {
      return message != null ? message.id : 0;
    }
  }

  public long getChatId () {
    synchronized (this) {
      return message != null ? message.chatId : 0;
    }
  }

  public int getPlayState (Tdlib tdlib, TdApi.Message message) {
    synchronized (this) {
      return this.message != null && compareTracks(this.tdlib, tdlib, this.message, message) ? this.playState : STATE_NONE;
    }
  }

  public long getPlayPosition (Tdlib tdlib, TdApi.Message message) {
    synchronized (this) {
      return this.message != null && compareTracks(this.tdlib, tdlib, this.message, message) ? this.playPosition : -1;
    }
  }

  public long getPlayDuration (Tdlib tdlib, TdApi.Message message) {
    synchronized (this) {
      return this.message != null && compareTracks(this.tdlib, tdlib, this.message, message) ? this.playDuration : -1;
    }
  }

  public boolean compare (Tdlib tdlib, long chatId, long messageId) {
    synchronized (this) {
      return this.tdlib == tdlib && message != null && message.chatId == chatId && message.id == messageId;
    }
  }

  public int getContentType () {
    synchronized (this) {
      return message != null ? message.content.getConstructor() : 0;
    }
  }

  /*public @Nullable TdApi.Message getMessage () {
    synchronized (this) {
      return message;
    }
  }*/

  public boolean isPlayingRoundVideo () {
    synchronized (this) {
      return message != null && message.content.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR;
    }
  }

  public boolean isPlayingVoice () {
    synchronized (this) {
      return message != null && message.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR;
    }
  }

  public boolean isPlayingMessage (long chatId, long messageId) {
    synchronized (this){
      return message != null && message.chatId == chatId && message.id == messageId;
    }
  }

  public boolean isPlayingFileId (int fileId) {
    synchronized (this) {
      return message != null && TD.getFileId(message) == fileId;
    }
  }

  // Listener

  public final void addTrackChangeListener (@NonNull TrackChangeListener listener) {
    synchronized (this) {
      globalListeners.add(listener);
      if (message != null) {
        listener.onTrackChanged(tdlib, message, TD.getFileId(message), playState, playProgress, false);
      }
    }
  }

  public final void removeTrackChangeListener (@NonNull TrackChangeListener listener) {
    synchronized (this) {
      globalListeners.add(listener);
    }
  }

  public final void addTrackListChangeListener (@NonNull TrackListChangeListener listener, boolean makeInitialCall) {
    synchronized (this) {
      trackListChangeListeners.add(listener);
      if (makeInitialCall && playState != STATE_NONE && !messageList.isEmpty() && message != null) {
        listener.onTrackListReset(tdlib, message, indexOfCurrentMessage(), messageList, playlistChatId, playListFlags | getPlaybackFlags(message, playbackFlags), playState);
      }
    }
    if (isValuableTarget(listener)) {
      context.audio().checkTimesIfNeeded();
    }
  }

  public final void removeTrackListChangeListener (@NonNull TrackListChangeListener listener) {
    synchronized (this) {
      trackListChangeListeners.remove(listener);
    }
  }

  public final void addTrackListener (Tdlib tdlib, @NonNull TdApi.Message message, @NonNull TrackListener trackListener) {
    synchronized (this) {
      final long currentChatId = this.message != null ? this.message.chatId : 0;
      final long currentMessageId = this.message != null ? this.message.id : 0;
      final int currentFileId = this.message != null ? TD.getFileId(this.message) : 0;
      final boolean isCurrent = currentChatId == message.chatId && currentMessageId == message.id && TD.getFileId(message) == currentFileId;
      targetListeners.add(makeTargetKey(tdlib, message.chatId, message.id), trackListener);
      if (isCurrent) {
        trackListener.onTrackStateChanged(tdlib, message.chatId, message.id, currentFileId, this.playState);
        if (this.playProgress != 0f) {
          trackListener.onTrackPlayProgress(tdlib, message.chatId, message.id, currentFileId, this.playProgress, this.playPosition, this.playDuration, this.playBuffering);
        }
      }
    }
  }

  public final void removeTrackListener (Tdlib tdlib, @NonNull TdApi.Message message, @NonNull TrackListener trackListener) {
    synchronized (this) {
      targetListeners.remove(makeTargetKey(tdlib, message.chatId, message.id), trackListener);
    }
  }

  private int getSessionFlags () {
    return (getPlaybackFlags(message, playbackFlags) | playListFlags);
  }

  private static void notifyTrackListSpeedChanged (ReferenceList<TrackChangeListener> list, int newSpeed) {
    for (TrackChangeListener listener : list) {
      listener.onPlaybackSpeedChanged(newSpeed);
    }
  }

  private static void notifyTrackListParametersChanged (ReferenceList<TrackChangeListener> list, Tdlib tdlib, TdApi.Message track) {
    for (TrackChangeListener listener : list) {
      listener.onPlaybackParametersChanged(tdlib, track);
    }
  }

  private static void notifyTrackListFlagsChanged (ReferenceList<TrackListChangeListener> list, int newFlags) {
    for (TrackListChangeListener listener : list) {
      listener.onTrackListFlagsChanged(newFlags);
    }
  }

  private static void notifyTrackListLoadStateChanged (ReferenceList<TrackListChangeListener> list) {
    for (TrackListChangeListener listener : list) {
      listener.onTrackListLoadStateChanged();
    }
  }

  private static void notifyTrackListReset (ReferenceList<TrackListChangeListener> list, Tdlib tdlib, TdApi.Message currentTrack, int trackIndex, List<TdApi.Message> trackList, long playListChatId, int playFlags, int playState) {
    for (TrackListChangeListener listener : list) {
      listener.onTrackListReset(tdlib, currentTrack, trackIndex, trackList, playListChatId, playFlags, playState);
    }
  }

  private static void notifyTrackListClose (ReferenceList<TrackListChangeListener> list, Tdlib tdlib, List<TdApi.Message> removedMessages, long sourceChatId, long maxMessageId, long minMessageId, boolean newEndReached, boolean oldEndReached) {
    for (TrackListChangeListener listener : list) {
      listener.onTrackListClose(tdlib, sourceChatId, maxMessageId, minMessageId, newEndReached, oldEndReached, removedMessages);
    }
  }

  private static void notifyTrackListItemAdded (ReferenceList<TrackListChangeListener> list, Tdlib tdlib, TdApi.Message newTrack, int position) {
    for (TrackListChangeListener listener : list) {
      listener.onTrackListItemAdded(tdlib, newTrack, position);
    }
  }

  private static void notifyTrackListItemMoved (ReferenceList<TrackListChangeListener> list, Tdlib tdlib, TdApi.Message track, int fromPosition, int toPosition) {
    for (TrackListChangeListener listener : list) {
      listener.onTrackListItemMoved(tdlib, track, fromPosition, toPosition);
    }
  }

  private static void notifyTrackListItemRemoved (ReferenceList<TrackListChangeListener> list, Tdlib tdlib, TdApi.Message track, int position, boolean isCurrent) {
    for (TrackListChangeListener listener : list) {
      listener.onTrackListItemRemoved(tdlib, track, position, isCurrent);
    }
  }

  private static void notifyTrackListItemRangeAdded (ReferenceList<TrackListChangeListener> list, Tdlib tdlib, List<TdApi.Message> newTracks, boolean areNew) {
    for (TrackListChangeListener listener : list) {
      listener.onTrackListItemRangeAdded(tdlib, newTracks, areNew);
    }
  }

  private static void notifyTrackListPositionChange (ReferenceList<TrackListChangeListener> list, Tdlib tdlib, TdApi.Message message, int newIndex, List<TdApi.Message> messages, boolean byUserRequest, int playState) {
    for (TrackListChangeListener listener : list) {
      listener.onTrackListPositionChange(tdlib, message, newIndex, messages, byUserRequest, playState);
    }
  }

  private static void notifyTrackChange (ReferenceList<TrackChangeListener> list, Tdlib tdlib, @Nullable TdApi.Message message, int fileId, int state, float progress, boolean byUser) {
    for (TrackChangeListener listener : list) {
      listener.onTrackChanged(tdlib, message, fileId, state, progress, byUser);
    }
  }

  private void updateProximityMessageImpl () {
    if (message != null && (message.content.getConstructor() == TdApi.MessageVoiceNote.CONSTRUCTOR || message.content.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR)) {
      proximityManager.setPlaybackObject(message);
    } else {
      proximityManager.setPlaybackObject(null);
    }
  }

  public boolean isPlayingThroughEarpiece () {
    synchronized (this) {
      return proximityManager.needPlayThroughEarpiece();
    }
  }

  private void moveListeners (Tdlib tdlib, TdApi.Message newMessage, long oldMessageId) {
    synchronized (this) {
      if (this.tdlib != null && this.tdlib.id() == tdlib.id() && this.message != null && this.message.id == oldMessageId && this.message.chatId == newMessage.chatId) {
        this.message = newMessage;
        updateProximityMessageImpl();
      }
      String oldKey = makeTargetKey(tdlib, newMessage.chatId, oldMessageId);
      String newKey = makeTargetKey(tdlib, newMessage.chatId, newMessage.id);
      targetListeners.move(oldKey, newKey);
    }
  }

  public void setPlayProgress (Tdlib tdlib, long chatId, long messageId, int fileId, float progress, long playPosition, long playDuration, boolean isBuffering) {
    synchronized (this) {
      if (this.tdlib == tdlib && this.message != null && this.message.chatId == chatId && this.message.id == messageId && TD.getFileId(this.message) == fileId && (this.playProgress != progress || this.playPosition != playPosition || this.playDuration != playDuration || this.playBuffering != isBuffering)) {
        this.playProgress = progress;
        this.playDuration = playDuration;
        this.playPosition = playPosition;
        this.playBuffering = isBuffering;
        notifyTrackState(targetListeners, tdlib, chatId, messageId, fileId, STATE_SEEK, progress, playPosition, playDuration, isBuffering);
      }
    }
  }

  private static void notifyTrackState (ReferenceMap<String, TrackListener> targetListeners, Tdlib tdlib, long chatId, long messageId, int fileId, int newState, float progress, long playPosition, long playDuration, boolean isBuffering) {
    final Iterator<TrackListener> iterator = targetListeners.iterator(makeTargetKey(tdlib, chatId, messageId));
    if (iterator != null) {
      while (iterator.hasNext()) {
        TrackListener listener = iterator.next();
        if (newState != STATE_SEEK)
          listener.onTrackStateChanged(tdlib, chatId, messageId, fileId, newState);
        if (progress != -1)
          listener.onTrackPlayProgress(tdlib, chatId, messageId, fileId, progress, playPosition, playDuration, isBuffering);
      }
    }
  }

  private static void notifyTrackState (ReferenceMap<String, TrackListener> targetListeners, Tdlib tdlib, long chatId, long messageId, int fileId, int newState) {
    notifyTrackState(targetListeners, tdlib, chatId, messageId, fileId, newState, -1, -1, -1, BUFFERING_DEFAULT);
  }

  // Playback

  private int indexOfCurrentMessage () {
    return indexOfMessage(message);
  }

  private int indexOfMessage (TdApi.Message message) {
    if (message != null) {
      int i = 0;
      for (TdApi.Message msg : messageList) {
        if (compareTracks(message, msg)) {
          return i;
        }
        i++;
      }
    }
    return -1;
  }

  private int indexOfNextMessage (int i, boolean isNext, boolean byUserRequest) {
    if (i == -1) {
      return -1;
    }
    if ((playListFlags & PLAYLIST_FLAG_REVERSE) != 0) {
      isNext = !isNext;
    }
    int playRepeatMode = getPlayRepeatFlag(getPlaybackFlags(messageList.get(i), playbackFlags));
    if (playRepeatMode == PLAY_FLAG_REPEAT_ONE && !byUserRequest) {
      return i;
    }
    if (isNext) {
      if (++i >= messageList.size()) {
        i = playRepeatMode != 0 || byUserRequest ? 0 : -1;
      }
    } else {
      if (--i < 0) {
        i = playRepeatMode != 0 || byUserRequest ? messageList.size() - 1 : -1;
      }
    }
    return i;
  }

  public void stopPlaybackIfPlayingAnyOf (TdApi.File[] files) {
    boolean stop = false;
    synchronized (this) {
      if (message != null) {
        int fileId = TD.getFileId(message);
        for (TdApi.File file : files) {
          if (fileId == file.id) {
            stop = true;
            break;
          }
        }
      }
    }
    if (stop) {
      stopPlayback(true);
    }
  }

  public void stopPlaybackIfPlaying (int fileId) {
    boolean stop;
    synchronized (this) {
      stop = message != null && TD.getFileId(message) == fileId;
    }
    if (stop) {
      stopPlayback(true);
    }
  }

  private static final int PLAY_STATE_UNSPECIFIED = 0;
  private static final int PLAY_STATE_PLAYING = 1;
  private static final int PLAY_STATE_PAUSED = 2;

  public void playPauseCurrent () {
    playPauseCurrent(PLAY_STATE_UNSPECIFIED);
  }

  public void playPauseCurrent (boolean requiredState) {
    playPauseCurrent(requiredState ? PLAY_STATE_PLAYING : PLAY_STATE_PAUSED);
  }

  private void playPauseCurrent (int requiredState) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_PLAY_PAUSE_CURRENT, requiredState, 0));
      return;
    }
    synchronized (this) {
      if (message == null || playState == STATE_NONE || (requiredState != PLAY_STATE_UNSPECIFIED && (requiredState == PLAY_STATE_PLAYING) == (playState == STATE_PLAYING))) {
        return;
      }
    }
    playPauseMessageImpl(message, true, false, tdlib, null);
  }

  public void stopPlayback (boolean byUserRequest) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_STOP_PLAYBACK, byUserRequest ? 1 : 0, 0));
    } else {
      playPauseMessageImpl(null, byUserRequest, false, tdlib, null);
    }
  }

  public void stopRoundPlayback (boolean byUserRequest) {
    if (message != null && message.content.getConstructor() == TdApi.MessageVideoNote.CONSTRUCTOR) {
      playPauseMessageImpl(null, byUserRequest, false, tdlib, null);
    }
  }

  private void stopPlaybackImpl (boolean byUserRequest) {
    playPauseMessageImpl(null, byUserRequest, false, tdlib, null);
  }

  public void removeMessages (Tdlib tdlib, long chatId, long[] messageIds) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_DELETE_MESSAGES, BitwiseUtils.splitLongToFirstInt(chatId), BitwiseUtils.splitLongToSecondInt(chatId), new Object[] {tdlib, messageIds}));
      return;
    }
    synchronized (this) {
      if (message == null || playState == STATE_NONE) {
        return;
      }
      if (this.tdlib == null || this.tdlib.id() != tdlib.id()) {
        return;
      }
      int currentIndex = indexOfCurrentMessage();
      if (currentIndex == -1) {
        return;
      }
      final int size = messageList.size();
      main: for (int i = size - 1; i >= 0; i--) {
        TdApi.Message track = messageList.get(i);
        if (track.chatId != 0 && track.chatId == chatId && ArrayUtils.indexOf(messageIds, track.id) != -1) {
          if (i == currentIndex) {
            switch (track.content.getConstructor()) {
              case TdApi.MessageAudio.CONSTRUCTOR:
                // Do nothing. Let user finish playback
                break;
              case TdApi.MessageVoiceNote.CONSTRUCTOR:
              case TdApi.MessageVideoNote.CONSTRUCTOR:
                // TODO switch to the next track?
                // removeTrackImpl(track, i, false);
                stopPlaybackImpl(false);
                break main;
            }
          } else {
            removeTrackImpl(track, i, false);
            if (i < currentIndex) {
              currentIndex--;
            }
          }
        }
      }
    }
  }

  public void skip (boolean next) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_SKIP, next ? 1 : 0, 0));
    } else {
      synchronized (this) {
        if (message != null && message.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR) {
          context.audio().skip(next);
        }
      }
    }
  }

  public void playNextMessageInQueue () {
    playNextMessage(true, false);
  }

  public void playNextMessage (boolean isNext, boolean byUserRequest) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_PLAY_NEXT, isNext ? 1 : 0, byUserRequest ? 1 : 0));
    } else {
      int currentIndex = indexOfCurrentMessage();
      int i = indexOfNextMessage(currentIndex, isNext, byUserRequest);
      if (i != -1) {
        playPauseMessageImpl(messageList.get(i), byUserRequest, true, tdlib, null);
      } else {
        playPauseMessageImpl(null, byUserRequest, true, tdlib, null);
      }
    }
  }

  public void playIfPaused (TdApi.Message message) {
    if (Looper.myLooper() != Looper.getMainLooper())
      throw new IllegalStateException();
    if (this.message != null && (!compareTracks(this.message, message) || playState != STATE_PLAYING)) {
      playPauseMessageImpl(message, true, true, tdlib, null);
    }
  }

  /*private void dispatchNewState (int newState, TdApi.Message message) {
    int currentFileId = TD.getFileId(message);
    setPlayState(newState);
    notifyTrackChange(message, currentFileId, newState, 0f, true);
    notifyTrackState(message.chatId, message.id, currentFileId, newState);
  }*/

  public void playPauseMessage (Tdlib tdlib, @Nullable TdApi.Message message, @Nullable PlayListBuilder builder) {
    playPauseMessageImpl(message, true, false, tdlib, builder);
  }

  public void playPauseMessageEventually (Tdlib tdlib, @Nullable TdApi.Message message, boolean isNext) {
    playPauseMessageImpl(message, false, isNext, tdlib, null);
  }

  public boolean canSeekTrack (TdApi.Message message) {
    synchronized (this) {
      if (this.message != null && compareTracks(this.message, message)) {
        return context.audio().isSeekable();
      }
    }
    return false;
  }

  public float normalizeSeekPosition (long durationMillis, float desiredSeek) {
    synchronized (this) {
      return context.audio().getMaxSeek(durationMillis, desiredSeek);
    }
  }

  public void seekTrack (@NonNull TdApi.Message message, long positionMillis) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_SEEK, BitwiseUtils.splitLongToFirstInt(positionMillis), BitwiseUtils.splitLongToSecondInt(positionMillis), message));
      return;
    }
    synchronized (this) {
      if (this.message != null && compareTracks(this.message, message) && (Config.ALLOW_SEEK_ANYTIME || (this.playDuration != -1 && positionMillis >= 0 && playDuration >= positionMillis))) {
        context.audio().seekTo(positionMillis, playDuration);
      }
    }
  }

  private static final int __PLAY_FLAG_BY_USER_REQUEST = 1;
  private static final int __PLAY_FLAG_NEXT = 1 << 1;

  private void playPauseMessageImpl (@Nullable TdApi.Message message, int flags, Tdlib tdlib, @Nullable PlayListBuilder builder) {
    boolean byUserRequest = (flags & __PLAY_FLAG_BY_USER_REQUEST) != 0;
    boolean isNext = (flags & __PLAY_FLAG_NEXT) != 0;
    playPauseMessageImpl(message, byUserRequest, isNext, tdlib, builder);
  }

  private void setPlayState (int newState) {
    this.playState = newState;
    if (newState == STATE_PLAYING) {
      needResume = false;
      pauseReasons = 0;
    }
  }

  private void playPauseMessageImpl (@Nullable TdApi.Message message, boolean byUserRequest, boolean isNext, Tdlib tdlib, @Nullable PlayListBuilder builder) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      int flags = 0;
      if (byUserRequest)
        flags |= __PLAY_FLAG_BY_USER_REQUEST;
      if (byUserRequest)
        flags |= __PLAY_FLAG_NEXT;
      handler.sendMessage(Message.obtain(handler, ACTION_PLAY_PAUSE, flags, 0, new Object[] {message, tdlib, builder}));
      return;
    }
    synchronized (this) {
      if (this.message == null && message == null) {
        return;
      }

      final Tdlib currentTdlib = this.tdlib;
      final TdApi.Message currentMessage = this.message;
      final long currentMessageId = this.message != null ? this.message.id : 0;
      final long currentChatId = this.message != null ? this.message.chatId : 0;
      final int currentFileId = this.message != null ? TD.getFileId(this.message) : 0;
      final boolean isCurrent = currentTdlib == tdlib && message != null && currentChatId == message.chatId && currentMessageId == message.id && TD.getFileId(message) == currentFileId;
      final float currentProgress = this.playProgress;

      if (isCurrent) {
        // TODO seek to beginning when isNext == true
        final int newState;
        if (playState == STATE_PAUSED) {
          newState = STATE_PLAYING;
        } else if (playState == STATE_PLAYING) {
          newState = STATE_PAUSED;
        } else if (playState == STATE_LOADING) {
          TdApi.File file = TD.getFile(message);
          if (TD.isFileLoaded(file)) {
            newState = STATE_PLAYING;
          } else {
            return;
          }
        } else {
          return;
        }
        setPlayState(newState);
        notifyTrackChange(globalListeners, tdlib, message, currentFileId, newState, currentProgress, byUserRequest);
        notifyTrackState(targetListeners, tdlib, message.chatId, message.id, currentFileId, newState);
        return;
      }

      final int newState;
      if (message != null) {
        if (TD.isFileLoaded(message) || Config.useCloudPlayback(message)) {
          newState = STATE_PLAYING;
        } else {
          newState = STATE_LOADING;
        }
      } else {
        newState = STATE_NONE;
      }

      this.tdlib = tdlib;
      this.message = message;
      updateProximityMessageImpl();
      this.playProgress = 0f;
      this.playPosition = this.playDuration = -1;
      this.playBuffering = BUFFERING_DEFAULT;
      setPlayState(newState);

      if (currentTdlib != tdlib) {
        if (currentTdlib != null) {
          currentTdlib.decrementUiReferenceCount();
        }
        if (tdlib != null) {
          tdlib.incrementUiReferenceCount();
        }
      }

      if (newState == STATE_NONE) {
        notifyTrackChange(globalListeners, currentTdlib, currentMessage, currentFileId, newState, 0f, byUserRequest);
      } else {
        notifyTrackChange(globalListeners, tdlib, message, TD.getFileId(message), newState, 0f, byUserRequest);
      }

      if (currentFileId != 0) {
        notifyTrackState(targetListeners, currentTdlib, currentChatId, currentMessageId, currentFileId, STATE_NONE);
      }
      if (newState != STATE_NONE) {
        notifyTrackState(targetListeners, tdlib, message.chatId, message.id, TD.getFileId(message), newState);
        if (message.chatId != 0 && message.id != 0) {
          tdlib.client().send(new TdApi.OpenMessageContent(message.chatId, message.id), tdlib.silentHandler());
        }
      }

      // Message listeners

      if (isNext && newState != STATE_NONE) {
        ensureStackAvailability(FORCE_REASON_NONE);
        int i = indexOfCurrentMessage();
        if (i == -1) {
          throw new IllegalStateException();
        }
        notifyTrackListPositionChange(trackListChangeListeners, tdlib, messageList.get(i), i, messageList, byUserRequest, newState);
        return;
      }

      int messageIndex = -1;
      int newPlayListFlags = 0;
      PlayList playList = null;

      if (message != null) {
        int existingMessageIndex = -1;
        if (!messageList.isEmpty()) {
          int i = 0;
          for (TdApi.Message oldMessage : messageList) {
            if (compareTracks(oldMessage, message)) {
              existingMessageIndex = i;
              break;
            }
            i++;
          }
        }
        if (existingMessageIndex != -1) {
          if (builder == null && (playListFlags & PLAYLIST_FLAG_ALTERED) != 0) { // Keeping custom user playlist
            messageIndex = existingMessageIndex;
            Log.i(Log.TAG_PLAYER, "Using existing message index, because user has customized the playlist");
          } else if (builder != null && builder.wouldReusePlayList(message, (playListFlags & PLAYLIST_FLAG_REVERSE) != 0, (playListFlags & PLAYLIST_FLAG_ALTERED) != 0, messageList, playlistChatId)) {
            Log.i(Log.TAG_PLAYER, "Reusing playlist because it's the same source");
            messageIndex = existingMessageIndex;
          } else {
            Log.i(Log.TAG_PLAYER, "Track has been found in the playlist, but we have to rebuild it, hasBuilder:%b", builder != null);
          }
        }
        if (messageIndex != -1) {
          ensureStackAvailability(FORCE_REASON_NONE);
          notifyTrackListPositionChange(trackListChangeListeners, tdlib, message, messageIndex, messageList, byUserRequest, newState);
          return;
        }
        if (builder != null) {
          playList = builder.buildPlayList(message);
          if (playList != null) {
            newPlayListFlags = playList.playListFlags;
            clearMessagesImpl();
            addMessagesImpl(playList.messages);
            playList.messages.clear();
            messageIndex = playList.originIndex;
          }
        }
      }
      if (messageIndex == -1) {
        clearMessagesImpl();
        if (message != null) {
          addMessageImpl(message);
          messageIndex = 0;
        }
      }
      int oldMessageListStateFlags = messageListStateFlags;
      cancelStackLoader();

      if (messageIndex < 0) {
        notifyTrackListClose(trackListChangeListeners, currentTdlib, removedMessageList, playlistChatId, playlistMaxMessageId, playlistMinMessageId, (oldMessageListStateFlags & LIST_STATE_LOADED_NEW) != 0, (oldMessageListStateFlags & LIST_STATE_LOADED_OLD) != 0);
        removedMessageList.clear();
        return;
      }

      if (!canControlQueue(message)) {
        messageListStateFlags |= LIST_STATE_LOADED_OLD;
        newPlayListFlags = 0;
      }
      this.playListFlags = newPlayListFlags;
      this.playlistChatId = message.chatId;
      if (!messageList.isEmpty()) {
        this.playlistMinMessageId = messageList.get(0).id;
        this.playlistMaxMessageId = messageList.get(messageList.size() - 1).id;
      } else {
        this.playlistMinMessageId = this.playlistMaxMessageId = 0;
      }
      this.playlistSearchQuery = null;
      this.playlistMessageThreadId = 0;
      this.playlistInlineQuery = null;
      this.playlistInlineNextOffset = this.playlistSecretNextOffset = null;
      this.removedMessageList.clear();
      if (playList != null) {
        playlistSearchQuery = playList.searchQuery;
        playlistSecretNextOffset = playList.secretNextOffset;
        playlistMessageThreadId = playList.messageThreadId;
        if (playList.playListInformationSet) {
          playlistChatId = playList.chatId;
          playlistMaxMessageId = playList.maxMessageId;
          playlistMinMessageId = playList.minMessageId;
        }
        if (playList.removedMessages != null && !playList.removedMessages.isEmpty()) {
          removedMessageList.addAll(playList.removedMessages);
        }
        if (playList.newEndReached) {
          messageListStateFlags |= LIST_STATE_LOADED_NEW;
        }
        if (playList.oldEndReached) {
          messageListStateFlags |= LIST_STATE_LOADED_OLD;
        }
        playlistInlineNextOffset = playList.inlineNextOffset;
        playlistInlineQuery = playList.inlineQuery;
      }
      if (TD.isScheduled(message)) {
        messageListStateFlags |= LIST_STATE_LOADED_NEW | LIST_STATE_LOADED_OLD;
      }
      notifyTrackListReset(trackListChangeListeners, tdlib, message, messageIndex, messageList, playlistChatId, newPlayListFlags | getPlaybackFlags(message, playbackFlags), newState);
      prepareStack(true, true, FORCE_REASON_NONE);
    }
  }

  public List<TdApi.Message> getTrackList () {
    synchronized (this) {
      if (playState != STATE_NONE && !messageList.isEmpty()) {
        return messageList;
      }
    }
    return null;
  }

  public long getPlayListChatId () {
    synchronized (this) {
      if (playState != STATE_NONE && !messageList.isEmpty()) {
        return playlistChatId;
      }
    }
    return 0;
  }

  public TdApi.Message getCurrentTrack () {
    synchronized (this) {
      if (playState != STATE_NONE) {
        return message;
      }
    }
    return null;
  }

  private static boolean canControlQueue (TdApi.Message message) {
    return message.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR;
  }

  private static boolean matchesFilter (TdApi.MessageContent content, int contentType) {
    int ctr = content.getConstructor();
    return ctr == contentType || ((ctr == TdApi.MessageVoiceNote.CONSTRUCTOR || ctr == TdApi.MessageVideoNote.CONSTRUCTOR) && (contentType == TdApi.MessageVoiceNote.CONSTRUCTOR || contentType == TdApi.MessageVideoNote.CONSTRUCTOR));
  }

  private Client.ResultHandler newStackHandler (final int contextId, final boolean areNew, final Tdlib tdlib, final long chatId, final int contentType, final long maxMessageId, final long minMessageId) {
    return object -> {
      final List<TdApi.Message> moreMessages;
      switch (object.getConstructor()) {
        case TdApi.FoundMessages.CONSTRUCTOR: {
          TdApi.FoundMessages foundMessages = (TdApi.FoundMessages) object;
          if (foundMessages.messages.length == 0) {
            moreMessages = null;
          } else {
            moreMessages = new ArrayList<>(foundMessages.messages.length);
            for (int i = foundMessages.messages.length - 1; i >= 0; i--) {
              TdApi.Message message = foundMessages.messages[i];
              if (message == null || message.chatId != chatId || !matchesFilter(message.content, contentType)) {
                continue;
              }
              if (areNew) {
                if (message.id <= maxMessageId) {
                  continue;
                }
              } else {
                if (message.id >= minMessageId) {
                  break;
                }
              }
              moreMessages.add(message);
            }
            synchronized (TGPlayerController.this) {
              if (contextId == messageListContextId) {
                playlistSecretNextOffset = foundMessages.nextOffset;
              }
            }
          }
          ArrayUtils.trimToSize(moreMessages);
          break;
        }
        case TdApi.Messages.CONSTRUCTOR: {
          TdApi.Messages messages = (TdApi.Messages) object;
          TdApi.Message[] array = messages.messages;
          if (array.length == 0) {
            moreMessages = null;
          } else {
            moreMessages = new ArrayList<>(array.length);
            for (int i = array.length - 1; i >= 0; i--) {
              TdApi.Message message = array[i];
              if (message == null || message.chatId != chatId || !matchesFilter(message.content, contentType)) {
                continue;
              }
              if (areNew) {
                if (message.id <= maxMessageId) {
                  continue;
                }
              } else {
                if (message.id >= minMessageId) {
                  break;
                }
              }
              moreMessages.add(message);
            }
            ArrayUtils.trimToSize(moreMessages);
          }
          break;
        }
        case TdApi.InlineQueryResults.CONSTRUCTOR: {
          TdApi.InlineQueryResults results = (TdApi.InlineQueryResults) object;
          moreMessages = new ArrayList<>(results.results.length);
          for (TdApi.InlineQueryResult rawResult : results.results) {
            TdApi.Message message = null;
            switch (rawResult.getConstructor()) {
              case TdApi.InlineQueryResultAudio.CONSTRUCTOR: {
                if (contentType == TdApi.MessageAudio.CONSTRUCTOR) {
                  TdApi.Audio audio = ((TdApi.InlineQueryResultAudio) rawResult).audio;
                  message = TD.newFakeMessage(audio);
                }
                break;
              }
              case TdApi.InlineQueryResultDocument.CONSTRUCTOR: {
                if (contentType == TdApi.MessageAudio.CONSTRUCTOR) {
                  TdApi.Document document = ((TdApi.InlineQueryResultDocument) rawResult).document;
                  if (TD.isSupportedMusic(document)) {
                    TdApi.Audio audio = TD.newFakeAudio(document);
                    message = TD.newFakeMessage(audio);
                  }
                }
                break;
              }
              default:
                break;
            }
            if (message != null && message.content.getConstructor() == contentType) {
              moreMessages.add(message);
            }
          }
          synchronized (TGPlayerController.this) {
            if (contextId == messageListContextId) {
              playlistInlineNextOffset = results.nextOffset;
            }
          }
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          Log.w("prepareStack TDLib error:%s, areNew:%b", TD.toErrorString(object), areNew);
          moreMessages = null;
          break;
        }
        default: {
          Log.unexpectedTdlibResponse(object, TdApi.SearchSecretMessages.class, TdApi.SearchChatMessages.class, TdApi.Messages.class, TdApi.Error.class);
          return;
        }
      }
      addMessages(contextId, moreMessages, areNew);
    };
  }

  private static boolean matchesFilter (InlineResult<?> result, @TdApi.MessageContent.Constructors int contentType) {
    switch (result.getType()) {
      case InlineResult.TYPE_AUDIO:
        return contentType == TdApi.MessageAudio.CONSTRUCTOR;
      case InlineResult.TYPE_VOICE:
        return contentType == TdApi.MessageVoiceNote.CONSTRUCTOR;
    }
    return false;
  }

  private void cancelStackLoader () {
    if (messageListContextId == Integer.MAX_VALUE) {
      messageListContextId = Integer.MIN_VALUE;
    } else {
      messageListContextId++;
    }
    messageListStateFlags = 0;
  }

  private static final int FORCE_THRESHOLD = 3;
  private static final int LOAD_THRESHOLD = 25;
  public static final int SCROLL_LOAD_THRESHOLD = 10;

  public void requestMoreTracks () {
    synchronized (this) {
      ensureStackAvailability((playListFlags & PLAYLIST_FLAG_REVERSE) != 0 ? FORCE_REASON_OLDER : FORCE_REASON_NEWER);
    }
  }

  public boolean canRequestMoreTrackReverse () {
    synchronized (this) {
      if (message != null && playlistChatId != 0 && !messageList.isEmpty() && playState != STATE_NONE) {
        if ((playListFlags & PLAYLIST_FLAG_REVERSE) != 0) {
          return (messageListStateFlags & (LIST_STATE_LOADED_OLD | LIST_STATE_LOADING_OLDER)) == 0;
        } else {
          return (messageListStateFlags & (LIST_STATE_LOADED_NEW | LIST_STATE_LOADING_NEWER)) == 0;
        }
      }
    }
    return false;
  }

  private void ensureStackAvailability (int forceReason) {
    if (message == null || (playlistChatId == 0 && playlistInlineQuery == null) || messageList.isEmpty() || playState == STATE_NONE) {
      return;
    }
    int i = indexOfCurrentMessage();
    if (i == -1) {
      return;
    }
    prepareStack(i + LOAD_THRESHOLD >= messageList.size(), i < LOAD_THRESHOLD, forceReason);
  }

  private static final int FORCE_REASON_NONE = 0;
  private static final int FORCE_REASON_NEWER = 1;
  private static final int FORCE_REASON_OLDER = 2;

  private TdApi.GetInlineQueryResults makeNextInlineQuery () {
    return new TdApi.GetInlineQueryResults(playlistInlineQuery.botUserId, playlistInlineQuery.chatId, playlistInlineQuery.userLocation, playlistInlineQuery.query, playlistInlineNextOffset);
  }

  private void prepareStack (boolean allowNewer, boolean allowOlder, int forceReason) {
    if (messageList.isEmpty()) {
      return;
    }

    // final TdApi.Message oldestMessage = messageList.get(0);
    final Tdlib tdlib = this.tdlib;
    final long chatId = playlistChatId;
    if (chatId == 0 && playlistInlineQuery == null) {
      return;
    }

    final TdApi.SearchMessagesFilter filter = TD.makeFilter(message, false);
    if (filter == null) {
      return;
    }

    // final TdApi.Message newestMessage = messageList.get(messageList.size() - 1);

    final long minMessageId = playlistMinMessageId;
    final long maxMessageId = playlistMaxMessageId;

    final int contentType = message.content.getConstructor();

    final int contextId = messageListContextId;
    final boolean reverse = (playListFlags & PLAYLIST_FLAG_REVERSE) != 0;
    final boolean forceOlder = forceReason == FORCE_REASON_OLDER;
    final boolean forceNewer = forceReason == FORCE_REASON_NEWER;
    allowOlder = (allowOlder || forceOlder) && minMessageId != 0 && (messageListStateFlags & LIST_STATE_LOADED_OLD) == 0 && (messageListStateFlags & LIST_STATE_LOADING_OLDER) == 0;
    if (allowOlder) {
      allowOlder = (reverse && !forceNewer) || forceOlder;
    }
    boolean hasNewer = maxMessageId != 0 || (playlistInlineQuery != null && !StringUtils.isEmpty(playlistInlineNextOffset));
    allowNewer = (allowNewer || forceNewer) && hasNewer && (messageListStateFlags & LIST_STATE_LOADED_NEW) == 0 && (messageListStateFlags & LIST_STATE_LOADING_NEWER) == 0;
    if (allowNewer) {
      allowNewer = (!reverse && !forceOlder) || forceNewer;
    }
    int totalCount = 0;
    if (allowOlder) {
      totalCount++;
    }
    if (allowNewer) {
      totalCount++;
    }
    if (totalCount == 0) {
      return;
    }

    final ArrayList<TdApi.Function<?>> functions = new ArrayList<>(totalCount);
    TdApi.Function<?> requestOld, requestNew;
    if (!StringUtils.isEmpty(playlistSearchQuery) && ChatId.isSecret(chatId)) {
      requestOld = allowOlder ? new TdApi.SearchSecretMessages(chatId, playlistSearchQuery, playlistSecretNextOffset, 100, filter) : null;
      requestNew = null;
      messageListStateFlags |= LIST_STATE_LOADED_NEW;
    } else {
      requestOld = allowOlder ? new TdApi.SearchChatMessages(chatId, playlistSearchQuery, null, minMessageId, 0, 100, filter, playlistMessageThreadId) : null;
      requestNew = allowNewer ? playlistInlineQuery != null ? makeNextInlineQuery() : new TdApi.SearchChatMessages(chatId, playlistSearchQuery, null, maxMessageId, -99, 100, filter, playlistMessageThreadId) : null;
    }

    if ((playListFlags & PLAYLIST_FLAG_REVERSE) != 0) {
      if (requestOld != null)
        functions.add(requestOld);
      if (requestNew != null)
        functions.add(requestNew);
    } else {
      if (requestNew != null)
        functions.add(requestNew);
      if (requestOld != null)
        functions.add(requestOld);
    }

    for (TdApi.Function<?> function : functions) {
      boolean areNew = function instanceof TdApi.GetInlineQueryResults || (function instanceof TdApi.SearchChatMessages && ((TdApi.SearchChatMessages) function).offset < 0);
      if (areNew) {
        messageListStateFlags |= LIST_STATE_LOADING_NEWER;
      } else {
        messageListStateFlags |= LIST_STATE_LOADING_OLDER;
      }
      tdlib.client().send(function, newStackHandler(contextId, areNew, tdlib, chatId, contentType, maxMessageId, minMessageId));
    }
  }

  public boolean isTrackListEndReached () {
    synchronized (this) {
      if (message == null || playState == STATE_NONE) {
        return true;
      }
      if ((playListFlags & PLAYLIST_FLAG_REVERSE) != 0) {
        return ((messageListStateFlags) & LIST_STATE_LOADED_OLD) != 0;
      } else {
        return ((messageListStateFlags) & LIST_STATE_LOADED_NEW) != 0;
      }
    }
  }

  private void addMessages (int contextId, @Nullable List<TdApi.Message> messages, boolean areNew) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_ADD_MESSAGES, contextId, areNew ? 1 : 0, messages));
      return;
    }
    synchronized (this) {
      if (messageListContextId != contextId) {
        return;
      }
      if (areNew) {
        messageListStateFlags &= ~LIST_STATE_LOADING_NEWER;
      } else {
        messageListStateFlags &= ~LIST_STATE_LOADING_OLDER;
      }
      boolean endReached = messages == null || messages.isEmpty();
      if (playlistInlineQuery != null) {
        endReached = endReached || StringUtils.isEmpty(playlistInlineNextOffset);
      }
      int forceReason = FORCE_REASON_NONE;
      if (messages != null && !messages.isEmpty()) {
        if (areNew) {
          addMessagesImpl(messages);
          playlistMaxMessageId = messages.get(messages.size() - 1).id;
        } else {
          addMessagesImpl(0, messages);
          playlistMinMessageId = messages.get(0).id;
        }
        notifyTrackListItemRangeAdded(trackListChangeListeners, tdlib, messages, areNew);
      }
      if (endReached) {
        messageListStateFlags |= areNew ? LIST_STATE_LOADED_NEW : LIST_STATE_LOADED_OLD;
        if (messageList.size() <= FORCE_THRESHOLD) {
          forceReason = areNew ? FORCE_REASON_OLDER : FORCE_REASON_NEWER;
        }
        notifyTrackListLoadStateChanged(trackListChangeListeners);
      }
      ensureStackAvailability(forceReason);
    }
  }

  private void addNewMessage (Tdlib tdlib, TdApi.Message message) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      handler.sendMessage(Message.obtain(handler, ACTION_ADD_MESSAGE, new Object[] {tdlib, message}));
      return;
    }
    synchronized (this) {
      if (this.tdlib == tdlib && this.message != null && this.message.chatId == message.chatId && message.chatId != 0 && this.message.content.getConstructor() == message.content.getConstructor() && TD.isScheduled(this.message) == TD.isScheduled(message)) {
        if ((messageListStateFlags & LIST_STATE_LOADED_NEW) != 0) {
          int position = messageList.size();
          addMessageImpl(message);
          notifyTrackListItemAdded(trackListChangeListeners, tdlib, message, position);
        }
      }
      ensureStackAvailability(FORCE_REASON_NONE);
    }
  }

  // Chat listener

  @Override
  public void onNewMessage (Tdlib tdlib, TdApi.Message message) {
    final long chatId = getChatId();
    final int contentType = getContentType();
    if (chatId != 0 && contentType != 0 && message.chatId == chatId && message.content.getConstructor() == contentType && message.sendingState == null) {
      addNewMessage(tdlib, message);
    }
  }

  @Override
  public void onNewMessages (Tdlib tdlib, TdApi.Message[] messages) {
    final long chatId = getChatId();
    final int contentType = getContentType();
    if (chatId != 0 && contentType != 0) {
      for (TdApi.Message message : messages) {
        if (message.chatId == chatId && message.content.getConstructor() == contentType && message.sendingState == null) {
          addNewMessage(tdlib, message);
        }
      }
    }
  }

  @Override
  public void onMessageSendSucceeded (Tdlib tdlib, final TdApi.Message message, final long oldMessageId) {
    moveListeners(tdlib, message, oldMessageId);
    final long chatId = getChatId();
    if (chatId != 0 && message.chatId == chatId) {
      addNewMessage(tdlib, message);
    }
  }

  @Override
  public void onMessageSendFailed (Tdlib tdlib, final TdApi.Message message, final long oldMessageId, int errorCode, String errorMessage) {
    moveListeners(tdlib, message, oldMessageId);
  }

  @Override
  public void onMessagesDeleted (Tdlib tdlib, final long chatId, final long[] messageIds) {
    removeMessages(tdlib, chatId, messageIds);
  }

  // Handler

  private static final int ACTION_DELETE_MESSAGES = 0;
  private static final int ACTION_STOP_PLAYBACK = 1;
  private static final int ACTION_PLAY_NEXT = 2;
  private static final int ACTION_PLAY_PAUSE = 3;
  private static final int ACTION_ADD_MESSAGE = 4;
  private static final int ACTION_ADD_MESSAGES = 5;
  private static final int ACTION_SEEK = 6;
  private static final int ACTION_SKIP = 7;
  private static final int ACTION_PLAY_PAUSE_CURRENT = 8;
  private static final int ACTION_REDUCE_VOLUME = 9;
  private static final int ACTION_SET_SPEED = 10;

  private void processMessage (Message msg) {
    switch (msg.what) {
      case ACTION_DELETE_MESSAGES: {
        long chatId = BitwiseUtils.mergeLong(msg.arg1, msg.arg2);
        Object[] data = (Object[]) msg.obj;
        Tdlib tdlib = (Tdlib) data[0];
        long[] messageIds = (long[]) data[1];
        removeMessages(tdlib, chatId, messageIds);
        data[0] = null;
        data[1] = null;
        break;
      }
      case ACTION_STOP_PLAYBACK: {
        stopPlayback(msg.arg1 == 1);
        break;
      }
      case ACTION_PLAY_NEXT: {
        playNextMessage(msg.arg1 == 1, msg.arg2 == 1);
        break;
      }
      case ACTION_SEEK: {
        seekTrack((TdApi.Message) msg.obj, BitwiseUtils.mergeLong(msg.arg1, msg.arg2));
        break;
      }
      case ACTION_PLAY_PAUSE: {
        Object[] data = (Object[]) msg.obj;
        playPauseMessageImpl((TdApi.Message) data[0], msg.arg1, (Tdlib) data[1], (PlayListBuilder) data[2]);
        data[0] = null;
        data[1] = null;
        data[2] = null;
        break;
      }
      case ACTION_ADD_MESSAGE: {
        Object[] data = (Object[]) msg.obj;
        addNewMessage((Tdlib) data[0], (TdApi.Message) data[1]);
        data[0] = null;
        data[1] = null;
        break;
      }
      case ACTION_ADD_MESSAGES: {
        //noinspection unchecked
        addMessages(msg.arg1, (List<TdApi.Message>) msg.obj, msg.arg2 == 1);
        break;
      }
      case ACTION_SKIP: {
        skip(msg.arg1 == 1);
        break;
      }
      case ACTION_PLAY_PAUSE_CURRENT: {
        playPauseCurrent(msg.arg1);
        break;
      }
      case ACTION_REDUCE_VOLUME: {
        setReduceVolume(msg.arg1 == 1);
        break;
      }
      case ACTION_SET_SPEED: {
        setPlaybackSpeed(msg.arg1);
        break;
      }
    }
  }

  // Etc

  private static class PlayHandler extends Handler {
    private final TGPlayerController context;

    public PlayHandler (TGPlayerController context) {
      super(Looper.getMainLooper());
      this.context = context;
    }

    @Override
    public void handleMessage (Message msg) {
      context.processMessage(msg);
    }
  }

  // External events

  public static final int PAUSE_REASON_CALL = 1 << 1;
  public static final int PAUSE_REASON_NOISY = 1 << 2;
  public static final int PAUSE_REASON_OPEN_VIDEO = 1 << 3;
  public static final int PAUSE_REASON_OPEN_YOUTUBE = 1 << 4;
  public static final int PAUSE_REASON_TELEGRAM_CALL = 1 << 5;
  public static final int PAUSE_REASON_FOCUS = 1 << 6;
  public static final int PAUSE_REASON_RECORD_AUDIO_VIDEO = 1 << 7;
  public static final int PAUSE_REASON_RECORD_VIDEO = 1 << 8;
  public static final int PAUSE_REASON_PROXIMITY = 1 << 9;
  public static final int PAUSE_REASON_OPEN_WEB_VIDEO = 1 << 10;

  private boolean needResume;
  private int pauseReasons;

  public void setPauseReason (int reason, boolean active) {
    synchronized (this) {
      int reasons = BitwiseUtils.setFlag(pauseReasons, reason, active);
      if (this.pauseReasons == reasons) {
        return;
      }
      boolean wasPaused = this.pauseReasons != 0;
      boolean isPaused = reasons != 0;
      this.pauseReasons = reasons;
      if (wasPaused != isPaused && message != null) {
        if (isPaused && playState == STATE_PLAYING) {
          needResume = true;
          playPauseMessage(tdlib, message, null);
        } else if (!isPaused && playState == STATE_PAUSED && needResume) {
          needResume = false;
          playPauseMessage(tdlib, message, null);
        }
      }
    }
  }

  public void pauseWithReason (int reason) {
    synchronized (this) {
      if (message != null && playState == STATE_PLAYING) {
        Log.i(Log.TAG_PLAYER, "pausing message because of external event, reason:%d", reason);
        playPauseMessage(tdlib, message, null);
      }
    }
  }

  // Media collection from the current view controller

  public static class PlayList {
    private final List<TdApi.Message> messages;
    private final int originIndex;

    private int playListFlags;
    private String searchQuery, secretNextOffset;
    private long messageThreadId;

    private List<TdApi.Message> removedMessages;

    private boolean playListInformationSet;
    private long chatId, maxMessageId, minMessageId;

    private boolean newEndReached, oldEndReached;

    private TdApi.GetInlineQueryResults inlineQuery;
    private String inlineNextOffset;

    /**
     * @param messages messages list in the ascending order from older to newer messages
     * @param originIndex index of message requested in {@link PlayListBuilder#buildPlayList(TdApi.Message)}
     */
    public PlayList (List<TdApi.Message> messages, int originIndex) {
      this.messages = messages;
      this.originIndex = originIndex;
    }

    /**
     * Flags to be applied to playback flags (in addition to internal ones)
     * */
    public PlayList setPlayListFlags (int flags) {
      this.playListFlags = flags;
      return this;
    }

    /**
     * Set parameters to be used in GetInlineQueryResults instead of SearchChatMessages
     * */
    public PlayList setInlineQuery (TdApi.GetInlineQueryResults inlineQuery, String inlineNextOffset) {
      this.inlineQuery = inlineQuery;
      this.inlineNextOffset = inlineNextOffset;
      setReachedEnds(StringUtils.isEmpty(inlineNextOffset), true);
      return this;
    }

    /**
     * Set list of messages that we removed and can be re-added to the list.
     * */
    public PlayList setRemovedMessages (List<TdApi.Message> messages) {
      this.removedMessages = messages;
      return this;
    }

    /**
     * Set flags telling the player that there's no need to load more messages in speicific direction.
     * */
    public PlayList setReachedEnds (boolean newEndReached, boolean oldEndReached) {
      this.newEndReached = newEndReached;
      this.oldEndReached = oldEndReached;
      return this;
    }

    /**
     * Set range of messages to be loaded.
     * */
    public PlayList setPlayListSource (long chatId, long maxMessageId, long minMessageId) {
      this.chatId = chatId;
      this.maxMessageId = maxMessageId;
      this.minMessageId = minMessageId;
      this.playListInformationSet = true;
      return this;
    }

    /**
     * Search query to be passed in {@link org.drinkless.td.libcore.telegram.TdApi.SearchChatMessages} query
     * */
    public PlayList setSearchQuery (String query) {
      this.searchQuery = query;
      return this;
    }

    /**
     * Message thread identifier to be passed in {@link org.drinkless.td.libcore.telegram.TdApi.SearchChatMessages} query
     */
    public PlayList setMessageThreadId (long messageThreadId) {
      this.messageThreadId = messageThreadId;
      return this;
    }
  }

  public interface PlayListBuilder {
    /**
     * @param fromMessage message, from which we need to build a playlist
     *
     * @return {@link TdApi.Message fromMessage}
     */
    @Nullable
    PlayList buildPlayList (TdApi.Message fromMessage);

    /**
     *
     * @param fromMessage Requested playback message
     * @param isReverse
     * @param hasAltered
     * @param trackList
     * @param playListChatId
     * @return
     */
    boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId);
  }

  // Raise to speak

  @Override
  public void onUpdateAttributes () {
    synchronized (this) {
      if (tdlib != null && message != null) {
        notifyTrackListParametersChanged(globalListeners, tdlib, message);
      }
    }
  }
}
