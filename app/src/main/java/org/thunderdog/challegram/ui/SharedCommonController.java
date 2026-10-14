/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 25/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.InlineResultMultiline;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.PollListener;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.v.MediaRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import tgx.td.MessageId;
import tgx.td.Td;

public class SharedCommonController extends SharedBaseController<InlineResult<?>> implements View.OnClickListener, TGPlayerController.TrackChangeListener, TGPlayerController.PlayListBuilder, PollListener {
  public SharedCommonController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.SearchMessagesFilter filter;
  private final Set<Long> observedPolls = new HashSet<>();

  public SharedCommonController setFilter (TdApi.SearchMessagesFilter filter) {
    this.filter = filter;
    return this;
  }

  @Override
  protected void onCreateView (Context context, MediaRecyclerView recyclerView, SettingsAdapter adapter) {
    super.onCreateView(context, recyclerView, adapter);
    if (filter != null && filter.getConstructor() == TdApi.SearchMessagesFilterAudio.CONSTRUCTOR) {
      tdlib.context().player().addTrackChangeListener(this);
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    onScopeChanged();
    if (filter != null && filter.getConstructor() == TdApi.SearchMessagesFilterAudio.CONSTRUCTOR) {
      tdlib.context().player().removeTrackChangeListener(this);
    }
  }

  @Override protected void onScopeChanged () {
    synchronized (observedPolls) {
      for (long id : observedPolls) tdlib.listeners().removePollListener(id, this);
      observedPolls.clear();
    }
  }

  @Override
  public CharSequence getName () {
    switch (filter.getConstructor()) {
      case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR:
        return Lang.getString(R.string.TabDocs);
      case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR:
        return Lang.getString(R.string.TabAudio);
      case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR:
        return Lang.getString(R.string.TabLinks);
      case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR:
        return Lang.getString(R.string.TabVoiceMessages);
      case TdApi.SearchMessagesFilterPoll.CONSTRUCTOR:
        return Lang.getString(R.string.ForumProfilePolls);
    }
    return "";
  }

  @Override
  public int getIcon () {
    switch (filter.getConstructor()) {
      case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR:
        return R.drawable.baseline_insert_drive_file_24;
      case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR:
        return R.drawable.baseline_music_note_24;
      case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR:
        return R.drawable.baseline_language_24;
      case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR:
        return R.drawable.baseline_mic_24;
      case TdApi.SearchMessagesFilterPoll.CONSTRUCTOR:
        return R.drawable.baseline_poll_24;
    }
    return 0;
  }

  @Override
  protected boolean canSearch () {
    return filter != null;
  }

  @Override
  protected boolean supportsMessageContent () {
    return true;
  }

  @Override
  protected boolean probablyHasEmoji () {
    return true;
  }

  @Override
  public void onClick (View v) {
    if (retryLoad(v)) return;
    ListItem item = (ListItem) v.getTag();
    if (item != null && item.getViewType() == ListItem.TYPE_CUSTOM_INLINE) {
      if (adapter.isInSelectMode()) {
        toggleSelected(item);
        return;
      }
      
      InlineResult<?> result = (InlineResult<?>) item.getData();
      switch (result.getType()) {
        case InlineResult.TYPE_AUDIO:
        case InlineResult.TYPE_VOICE: {
          tdlib.context().player().playPauseMessage(tdlib, result.getMessage(), this);
          break;
        }
        case InlineResult.TYPE_DOCUMENT: {
          ((InlineResultCommon) result).performClick(v);
          break;
        }
        case InlineResult.TYPE_ARTICLE: {
          if (topicId != null) openScopedMessage(result.getMessage());
          else tdlib.ui().openMessage(this, chatId, new MessageId(chatId, result.getQueryId()), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
          break;
        }
      }
    }
  }

  // Impl

  @Override
  protected TdApi.SearchMessagesFilter provideSearchFilter () {
    return filter;
  }

  @Override
  protected InlineResult<?> parseObject (TdApi.Object object) {
    TdApi.Message message = (TdApi.Message) object;
    if (!acceptsMessage(message)) return null;
    InlineResult<?> result;

    if (filter instanceof TdApi.SearchMessagesFilterPoll) {
      if (!(message.content instanceof TdApi.MessagePoll)) return null;
      TdApi.Poll poll = ((TdApi.MessagePoll) message.content).poll;
      synchronized (observedPolls) {
        if (!isDestroyed() && observedPolls.add(poll.id)) tdlib.listeners().addPollListener(poll.id, this);
      }
      result = new InlineResultMultiline(context, tdlib, message, poll);
    } else if (filter != null && filter.getConstructor() == TdApi.SearchMessagesFilterUrl.CONSTRUCTOR) {
      result = new InlineResultMultiline(context, tdlib, message);
    } else {
      result = InlineResult.valueOf(context, tdlib, message);
    }
    if (result != null) {
      result.setQueryId(message.id);
      result.setDate(message.date);
      if (result instanceof InlineResultCommon && Td.isAudio(message.content)) {
        ((InlineResultCommon) result).setIsTrack(false);
      }
    }
    return result;
  }

  @Override
  protected CharSequence buildTotalCount (ArrayList<InlineResult<?>> data) {
    switch (filter.getConstructor()) {
      case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR: {
        return Lang.pluralBold(R.string.xAudios, data.size());
      }
      case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR: {
        return Lang.pluralBold(R.string.xFiles, data.size());
      }
      case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR: {
        return Lang.pluralBold(R.string.xLinks, data.size());
      }
      case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR: {
        return Lang.pluralBold(R.string.xVoiceMessages, data.size());
      }
      case TdApi.SearchMessagesFilterPoll.CONSTRUCTOR:
        return Lang.getString(R.string.ForumProfilePollCount, data.size());
    }
    return null;
  }

  @Override
  protected int provideViewType () {
    return ListItem.TYPE_CUSTOM_INLINE;
  }

  @Override public void onUpdatePoll (TdApi.Poll poll) {
    tdlib.ui().post(() -> {
      if (isDestroyed()) return;
      Map<Long, TdApi.MessagePoll> ids = new HashMap<>();
      collectPollMessages(data, poll.id, ids);
      collectPollMessages(searchData, poll.id, ids);
      for (Map.Entry<Long, TdApi.MessagePoll> entry : ids.entrySet()) {
        TdApi.MessagePoll old = entry.getValue();
        editMessage(entry.getKey(), new TdApi.MessagePoll(poll, old.description, old.media, old.canAddOption));
      }
    });
  }

  private static void collectPollMessages (ArrayList<InlineResult<?>> items, long pollId, Map<Long, TdApi.MessagePoll> ids) {
    if (items == null) return;
    for (InlineResult<?> item : items) {
      TdApi.Message message = item.getMessage();
      if (message != null && message.content instanceof TdApi.MessagePoll && ((TdApi.MessagePoll) message.content).poll.id == pollId) ids.put(message.id, (TdApi.MessagePoll) message.content);
    }
  }

  // Playback

  @Override
  public void onTrackChanged (Tdlib tdlib, @Nullable TdApi.Message newTrack, int fileId, int state, float progress, boolean byUser) {
    setCurrentTrack(data, newTrack);
    if (isSearching()) {
      setCurrentTrack(searchData, newTrack);
    }
  }

  private static void setCurrentTrack (ArrayList<InlineResult<?>> results, TdApi.Message newTrack) {
    if (results == null || results.isEmpty()) {
      return;
    }
    if (newTrack == null) {
      for (InlineResult<?> result : results) {
        if (result instanceof InlineResultCommon) {
          ((InlineResultCommon) result).setIsTrackCurrent(false);
        }
      }
    } else {
      for (InlineResult<?> result : results) {
        if (result instanceof InlineResultCommon) {
          ((InlineResultCommon) result).setIsTrackCurrent(TGPlayerController.compareTracks(result.getMessage(), newTrack));
        }
      }
    }
  }

  @Nullable
  @Override
  public TGPlayerController.PlayList buildPlayList (TdApi.Message fromMessage) {
    if (!acceptsMessage(fromMessage)) return null;
    String query;
    ArrayList<InlineResult<?>> data;
    if (isSearching()) {
      query = getCurrentQuery();
      data = this.searchData;
    } else {
      query = null;
      data = this.data;
    }
    if (data == null || data.isEmpty()) {
      throw new IllegalStateException();
    }
    ArrayList<TdApi.Message> out = new ArrayList<>(data.size());

    int foundIndex = -1;

    int desiredType;
    //noinspection SwitchIntDef
    switch (fromMessage.content.getConstructor()) {
      case TdApi.MessageAudio.CONSTRUCTOR:
        desiredType = InlineResult.TYPE_AUDIO;
        break;
      case TdApi.MessageVoiceNote.CONSTRUCTOR:
        desiredType = InlineResult.TYPE_VOICE;
        break;
      default:
        return null;
    }
    final int count = data.size();
    for (int i = count - 1; i >= 0; i--) {
      InlineResult<?> result = data.get(i);
      if (result.getType() != desiredType) {
        continue;
      }
      if (result instanceof InlineResultCommon) {
        TdApi.Message msg = result.getMessage();
        if (!acceptsMessage(msg)) continue;
        if (TGPlayerController.compareTracks(fromMessage, msg)) {
          if (foundIndex != -1) {
            throw new IllegalStateException();
          }
          foundIndex = out.size();
        }
        out.add(msg);
      }
    }

    if (foundIndex == -1) {
      throw new IllegalArgumentException();
    }

    return new TGPlayerController.PlayList(out, foundIndex).setPlayListFlags(TGPlayerController.PLAYLIST_FLAG_REVERSE).setSearchQuery(query).setTopicId(topicId);
  }

  @Override
  public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
    // A same-chat list may have been built by another topic or by the common stream.
    // Scope includes account, typed topic and query, not just the visible track list.
    return playListChatId != 0 && playListChatId == fromMessage.chatId && isReverse &&
      tdlib.context().player().hasPlayListScope(tdlib, chatId, topicId, getCurrentQuery());
  }

  @Override
  protected boolean needsCustomLongClickListener () {
    return alternateParent != null && alternateParent.inSearchMode();
  }

  @Override
  protected boolean onLongClick (View v, ListItem item) {
    final InlineResult<?> c = (InlineResult<?>) item.getData();

    alternateParent.showOptions(null, new int[]{R.id.btn_showInChat, R.id.btn_share, R.id.btn_delete}, new String[]{Lang.getString(R.string.ShowInChat), Lang.getString(R.string.Share), Lang.getString(R.string.Delete)}, new int[]{OptionColor.NORMAL, OptionColor.NORMAL, OptionColor.RED}, new int[] {R.drawable.baseline_visibility_24, R.drawable.baseline_forward_24, R.drawable.baseline_delete_24}, (itemView, id) -> {
      if (id == R.id.btn_showInChat) {
        alternateParent.closeSearchModeByBackPress(false, true);
        tdlib.ui().openMessage(SharedCommonController.this, c.getMessage(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
      } else if (id == R.id.btn_share) {
        ShareController share = new ShareController(context, tdlib);
        share.setArguments(new ShareController.Args(c.getMessage()).setAllowCopyLink(true));
        share.show();
      } else if (id == R.id.btn_delete) {
        TdApi.Message message = c.getMessage();
        tdlib.ui().showDeleteOptions(alternateParent, message);
      }
      return true;
    });

    return true;
  }

  // Media viewer

  @Override
  protected MediaItem toMediaItem (int index, InlineResult<?> item, @Nullable TdApi.SearchMessagesFilter filter) {
    TdApi.Message message = item.getMessage();
    if (message != null && Td.matchesFilter(message, filter)) {
      return MediaItem.valueOf(context, tdlib, message);
    }
    return null;
  }

  @Override
  protected boolean setThumbLocation (MediaViewThumbLocation location, View view, MediaItem mediaItem) {
    int index = indexOfMessage(mediaItem.getSourceMessageId());
    if (index == -1) {
      return false;
    }
    InlineResult<?> item = data.get(index);
    return item.setThumbLocation(location, view, index, mediaItem);
  }
}
