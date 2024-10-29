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
 * File created on 17/08/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.MediaRecyclerView;
import org.thunderdog.challegram.widget.CheckBoxView;
import org.thunderdog.challegram.widget.EmptySmartView;
import org.thunderdog.challegram.widget.SmallChatView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

import tgx.td.ChatId;

public class SharedChatsController extends SharedBaseController<DoubleTextWrapper> implements TdlibCache.SupergroupDataChangeListener, TdlibCache.BasicGroupDataChangeListener, ChatListener {
  @IntDef({
    Mode.GROUPS_IN_COMMON,
    Mode.SIMILAR_CHANNELS
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface Mode {
    int
      GROUPS_IN_COMMON = 0,
      SIMILAR_CHANNELS = 1;
  }

  private @Mode int mode = Mode.GROUPS_IN_COMMON;

  public SharedChatsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public void setMode (@Mode int mode) {
    this.mode = mode;
  }

  public @Mode int getMode () {
    return mode;
  }

  private int totalCountWithPremium;

  public void setTotalCountWithPremium (int totalCountWithPremium) {
    this.totalCountWithPremium = totalCountWithPremium;
  }

  /*@Override
  public int getIcon () {
    return R.drawable.baseline_group_20;
  }*/

  @Override
  public CharSequence getName () {
    switch (mode) {
      case Mode.GROUPS_IN_COMMON:
        return Lang.getString(R.string.TabSharedGroups);
      case Mode.SIMILAR_CHANNELS:
        return Lang.getString(R.string.TabSimilarChannels);
    }
    throw new IllegalStateException();
  }

  @Override
  public int getIcon () {
    switch (mode) {
      case Mode.GROUPS_IN_COMMON:
        return R.drawable.baseline_group_24;
      case Mode.SIMILAR_CHANNELS:
        return R.drawable.baseline_bullhorn_24;
    }
    throw new IllegalStateException();
  }

  @Override
  protected TdApi.Function<?> buildRequest (long chatId, long messageThreadId, String query, long offset, String secretOffset, int limit) {
    switch (mode) {
      case Mode.GROUPS_IN_COMMON:
        return new TdApi.GetGroupsInCommon(tdlib.chatUserId(chatId), offset, limit);
      case Mode.SIMILAR_CHANNELS:
        return new TdApi.GetChatSimilarChats(chatId);
    }
    throw new IllegalStateException();
  }

  @Override
  protected boolean supportsLoadingMore (boolean isMore) {
    return mode != Mode.SIMILAR_CHANNELS;
  }

  @Override
  protected String getExplainedTitle () {
    switch (mode) {
      case Mode.GROUPS_IN_COMMON:
        return Lang.getString(R.string.GroupsInCommon);
      case Mode.SIMILAR_CHANNELS:
        return Lang.getString(R.string.SimilarChannels);
    }
    throw new IllegalStateException();
  }

  protected boolean supportsMessageContent () {
    return false;
  }

  @Override
  protected boolean needDateSectionSplitting () {
    return false;
  }

  @Override
  protected boolean canSearch () {
    return false;
  }

  @Override
  protected CharSequence buildTotalCount (ArrayList<DoubleTextWrapper> data) {
    switch (mode) {
      case Mode.GROUPS_IN_COMMON:
        return Lang.pluralBold(R.string.xGroups, data.size());
      case Mode.SIMILAR_CHANNELS: {
        CharSequence cs = Lang.pluralBold(R.string.xChannels, data.size());
        int remaining = totalCountWithPremium - data.size();
        if (remaining > 0 && !tdlib.hasPremium()) {
          return Lang.getCharSequence(R.string.format_xChatsAndMoreWithPremium,
            cs,
            Lang.getMarkdownPlural(this, R.string.xMoreWithPremium, remaining, Lang.boldCreator())
          );
        }
        return cs;
      }
    }
    throw new IllegalStateException();
  }

  @Override
  protected int getEmptySmartMode () {
    return EmptySmartView.MODE_EMPTY_GROUPS;
  }

  @Override
  protected DoubleTextWrapper parseObject (TdApi.Object object) {
    return new DoubleTextWrapper(tdlib, (TdApi.Chat) object);
  }

  @Override
  protected int provideViewType () {
    return ListItem.TYPE_CHAT_SMALL;
  }

  @Override
  protected void modifyChatViewIfNeeded (ListItem item, SmallChatView chatView, @Nullable CheckBoxView checkBox, boolean isUpdate) {
    switch (mode) {
      case Mode.GROUPS_IN_COMMON:
        // Do nothing
        break;
      case Mode.SIMILAR_CHANNELS:
        chatView.setMaximizedChatModifier(messagesController -> {
          long openedChatId = messagesController.getChatId();
          tdlib.send(new TdApi.OpenChatSimilarChat(getChatId(), openedChatId), tdlib.typedOkHandler());
        });
        break;
    }
  }

  @Override
  public void onClick (View view) {
    ListItem item = (ListItem) view.getTag();
    if (item != null && item.getViewType() == ListItem.TYPE_CHAT_SMALL) {
      long chatId = ((DoubleTextWrapper) item.getData()).getChatId();
      switch (mode) {
        case Mode.GROUPS_IN_COMMON:
          tdlib.ui().openChat(this, chatId,null);
          break;
        case Mode.SIMILAR_CHANNELS:
          tdlib.send(new TdApi.OpenChatSimilarChat(getChatId(), chatId), tdlib.typedOkHandler());
          tdlib.ui().openChat(this, chatId, null);
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  protected long getCurrentOffset (ArrayList<DoubleTextWrapper> data, long emptyValue) {
    return data == null || data.isEmpty() ? emptyValue : data.get(data.size() - 1).getChatId();
  }

  @Override
  protected boolean needsDefaultLongPress () {
    return false;
  }

  @Override
  protected boolean supportsMessageClearing () {
    return false;
  }

  @Override
  protected int getItemCellHeight () {
    return Screen.dp(62f);
  }

  @Override
  protected boolean probablyHasEmoji () {
    return true;
  }

  @Override
  protected void onCreateView (Context context, MediaRecyclerView recyclerView, SettingsAdapter adapter) {
    super.onCreateView(context, recyclerView, adapter);
    tdlib.cache().subscribeForGlobalUpdates(this);
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().unsubscribeFromGlobalUpdates(this);
  }

  // Updates for texts

  private void updateChatById (final long chatId) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && data != null && !data.isEmpty()) {
        for (DoubleTextWrapper wrapper : data) {
          if (chatId == wrapper.getChatId()) {
            wrapper.updateTitleAndPhoto();
            break;
          }
        }
      }
    });
  }

  private void updateChatSubtitle (final long chatId) {
    runOnUiThreadOptional(() -> {
      if (data != null && !data.isEmpty()) {
        for (DoubleTextWrapper wrapper : data) {
          if (chatId == wrapper.getChatId()) {
            wrapper.updateSubtitle();
            break;
          }
        }
      }
    });
  }

  @Override
  public void onChatTitleChanged (long chatId, String title) {
    updateChatById(chatId);
  }

  @Override
  public void onChatPhotoChanged (long chatId, @Nullable TdApi.ChatPhotoInfo photo) {
    updateChatById(chatId);
  }

  @Override
  public void onChatOnlineMemberCountChanged (long chatId, int onlineMemberCount) {
    updateChatSubtitle(chatId);
  }

  @Override
  public void onBasicGroupUpdated (final TdApi.BasicGroup basicGroup, boolean migratedToSupergroup) {
    updateChatSubtitle(ChatId.fromBasicGroupId(basicGroup.id));
  }

  @Override
  public void onBasicGroupFullUpdated (long basicGroupId, TdApi.BasicGroupFullInfo basicGroupFull) {
    updateChatSubtitle(ChatId.fromBasicGroupId(basicGroupId));
  }

  @Override
  public void onSupergroupUpdated (TdApi.Supergroup supergroup) {
    updateChatSubtitle(ChatId.fromSupergroupId(supergroup.id));
  }

  @Override
  public void onSupergroupFullUpdated (long supergroupId, TdApi.SupergroupFullInfo newSupergroupFull) {
    updateChatSubtitle(ChatId.fromSupergroupId(supergroupId));
  }
}
