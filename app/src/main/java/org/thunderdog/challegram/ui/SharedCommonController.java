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
 * File created on 25/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.data.InlineResultCommon;
import org.thunderdog.challegram.data.InlineResultMultiline;
import org.thunderdog.challegram.mediaview.MediaViewThumbLocation;
import org.thunderdog.challegram.mediaview.data.MediaItem;
import org.thunderdog.challegram.player.TGPlayerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.v.MediaRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.td.MessageId;
import me.vkryl.td.Td;

public class SharedCommonController extends SharedBaseController<InlineResult<?>> implements View.OnClickListener, TGPlayerController.TrackChangeListener, TGPlayerController.PlayListBuilder {
  public SharedCommonController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private TdApi.SearchMessagesFilter filter;

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
    if (filter != null && filter.getConstructor() == TdApi.SearchMessagesFilterAudio.CONSTRUCTOR) {
      tdlib.context().player().removeTrackChangeListener(this);
    }
  }

  /*@Override
  public int getIcon () {
    switch (filter.getConstructor()) {
      case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR: {
        return R.drawable.baseline_insert_drive_file_20;
      }
      case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR: {
        return R.drawable.baseline_music_note_20;
      }
      case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR: {
        return R.drawable.baseline_language_20;
      }
      case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR: {
        return R.drawable.baseline_mic_20;
      }
    }
    return 0;
  }*/

  @Override
  public CharSequence getName () {
    switch (filter.getConstructor()) {
      case TdApi.SearchMessagesFilterDocument.CONSTRUCTOR: {
        return Lang.getString(R.string.TabDocs);
      }
      case TdApi.SearchMessagesFilterAudio.CONSTRUCTOR: {
        return Lang.getString(R.string.TabAudio);
      }
      case TdApi.SearchMessagesFilterUrl.CONSTRUCTOR: {
        return Lang.getString(R.string.TabLinks);
      }
      case TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR: {
        return Lang.getString(R.string.TabVoiceMessages);
      }
    }
    return "";
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
          tdlib.ui().openMessage(this, chatId, new MessageId(chatId, result.getQueryId()), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
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
    InlineResult<?> result;

    if (filter != null && filter.getConstructor() == TdApi.SearchMessagesFilterUrl.CONSTRUCTOR) {
      result = new InlineResultMultiline(context, tdlib, message);
    } else {
      result = InlineResult.valueOf(context, tdlib, message);
    }
    if (result != null) {
      result.setQueryId(message.id);
      result.setDate(message.date);
      if (result instanceof InlineResultCommon && message.content.getConstructor() == TdApi.MessageAudio.CONSTRUCTOR) {
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
    }
    return null;
  }

  @Override
  protected int provideViewType () {
    return ListItem.TYPE_CUSTOM_INLINE;
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

    return new TGPlayerController.PlayList(out, foundIndex).setPlayListFlags(TGPlayerController.PLAYLIST_FLAG_REVERSE).setSearchQuery(query);
  }

  @Override
  public boolean wouldReusePlayList (TdApi.Message fromMessage, boolean isReverse, boolean hasAltered, List<TdApi.Message> trackList, long playListChatId) {
    return playListChatId != 0 && playListChatId == fromMessage.chatId && isReverse;
  }

  @Override
  protected boolean needsCustomLongClickListener () {
    return alternateParent != null && alternateParent.inSearchMode();
  }

  @Override
  protected boolean onLongClick (View v, ListItem item) {
    final InlineResult<?> c = (InlineResult<?>) item.getData();

    alternateParent.showOptions(null, new int[]{R.id.btn_showInChat, R.id.btn_share, R.id.btn_delete}, new String[]{Lang.getString(R.string.ShowInChat), Lang.getString(R.string.Share), Lang.getString(R.string.Delete)}, new int[]{OPTION_COLOR_NORMAL, OPTION_COLOR_NORMAL, OPTION_COLOR_RED}, new int[] {R.drawable.baseline_visibility_24, R.drawable.baseline_forward_24, R.drawable.baseline_delete_24}, (itemView, id) -> {
      switch (id) {
        case R.id.btn_showInChat: {
          alternateParent.closeSearchModeByBackPress(false);
          tdlib.ui().openMessage(SharedCommonController.this, c.getMessage(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
          break;
        }
        case R.id.btn_share: {
          ShareController share = new ShareController(context, tdlib);
          share.setArguments(new ShareController.Args(c.getMessage()).setAllowCopyLink(true));
          share.show();
          break;
        }
        case R.id.btn_delete: {
          tdlib.ui().showDeleteOptions(alternateParent, new TdApi.Message[] {c.getMessage()}, () -> {
            // setInMediaSelectMode(false);
          });
          break;
        }
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
