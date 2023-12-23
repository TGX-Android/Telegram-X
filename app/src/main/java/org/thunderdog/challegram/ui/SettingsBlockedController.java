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
 * File created on 16/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.SenderPickerDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class SettingsBlockedController extends RecyclerViewController<TdApi.BlockList> implements View.OnClickListener, Menu, TdlibCache.UserDataChangeListener, TdlibCache.UserStatusChangeListener, SenderPickerDelegate, Client.ResultHandler, ChatListener {
  public SettingsBlockedController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_blocked;
  }

  private ArrayList<TGUser> senders;

  @Override
  public boolean needAsynchronousAnimation () {
    return senders == null;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.BlockedSenders);
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_contacts;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_contacts) {
      header.addButton(menu, R.id.menu_btn_addContact, R.drawable.baseline_person_add_24, getHeaderIconColorId(), this, Screen.dp(49f));
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_addContact) {
      blockContact();
    }
  }

  private void blockContact () {
    ContactsController c = new ContactsController(context, tdlib);
    c.setArguments(new ContactsController.Args(this));
    c.setAllowBots(true);
    c.setAllowChats(true, false);
    navigateTo(c);
  }

  @Override
  public boolean allowGlobalSearch () {
    return true;
  }

  @Override
  public String getUserPickTitle () {
    return Lang.getString(R.string.BlockSender);
  }

  @Override
  public boolean onSenderPick (ContactsController context, View view, TdApi.MessageSender senderId) {
    showOptions(Lang.getStringBold(senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR ? R.string.QBlockUser : R.string.QBlockChat, Strings.wrapRtlLtr(tdlib.senderName(senderId))), new int[] {R.id.btn_blockSender, R.id.btn_cancel}, new String[] {Lang.getString(senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR ? R.string.BlockUserBtn : R.string.BlockChatBtn), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_block_24, R.drawable.baseline_cancel_24});
    return false;
  }

  private TdApi.MessageSender senderToBlock;

  @Override
  public void onSenderConfirm (ContactsController context, TdApi.MessageSender senderId, int option) {
    senderToBlock = senderId;
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (senderToBlock != null) {
      tdlib.blockSender(senderToBlock, getArgumentsStrict(), tdlib.okHandler());
      senderToBlock = null;
    }
  }

  public void unblockSender (TGUser user) {
    showOptions(Lang.getStringBold(R.string.QUnblockX, tdlib.senderName(user.getSenderId())), new int[]{R.id.btn_unblockSender, R.id.btn_cancel}, new String[]{Lang.getString(R.string.Unblock), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_block_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_unblockSender) {
        tdlib.blockSender(user.getSenderId(), null, tdlib.okHandler(() -> {
          runOnUiThreadOptional(() -> {
            removeSender(user.getSenderId());
          });
        }));
      }
      return true;
    });
  }

  private int loadOffset;
  private boolean canLoadMore;

  private boolean isLoadingMore;

  private void loadMore () {
    if (isLoadingMore || !canLoadMore) {
      return;
    }
    isLoadingMore = true;
    tdlib.client().send(new TdApi.GetBlockedMessageSenders(getArgumentsStrict(), loadOffset, 50), this);
  }

  @Override
  public void onResult (TdApi.Object object) {
    if (object.getConstructor() != TdApi.MessageSenders.CONSTRUCTOR) {
      return;
    }
    final TdApi.MessageSenders senders = (TdApi.MessageSenders) object;
    final ArrayList<TGUser> parsedChats = new ArrayList<>(senders.senders.length);
    for (TdApi.MessageSender sender : senders.senders) {
      parsedChats.add(parseSender(tdlib, sender, this.senders));
    }
    if (!parsedChats.isEmpty()) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          isLoadingMore = false;
          loadOffset += senders.senders.length;
          canLoadMore = loadOffset <= senders.totalCount;
          for (int i = parsedChats.size() - 1; i >= 0; i--) {
            if (indexOfSender(parsedChats.get(i).getChatId()) != -1) {
              parsedChats.remove(i);
            }
          }
          addSenders(parsedChats);
        }
      });
    }
  }

  private static TGUser parseSender (Tdlib tdlib, TdApi.MessageSender sender, ArrayList<TGUser> senders) {
    TGUser parsedUser;
    switch (sender.getConstructor()) {
      case TdApi.MessageSenderChat.CONSTRUCTOR: {
        TdApi.Chat chat = tdlib.chatStrict(((TdApi.MessageSenderChat) sender).chatId);
        parsedUser = new TGUser(tdlib, chat);
        break;
      }
      case TdApi.MessageSenderUser.CONSTRUCTOR: {
        TdApi.User user = tdlib.cache().user(((TdApi.MessageSenderUser) sender).userId);
        parsedUser = new TGUser(tdlib, user);
        break;
      }
      default: {
        Td.assertMessageSender_439d4c9c();
        throw Td.unsupported(sender);
      }
    }
    parsedUser.setNoBotState();
    parsedUser.setBoundList(senders);
    return parsedUser;
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        if (isUpdate) {
          userView.updateSubtext();
        } else {
          userView.setUser(senders.get(position));
        }
      }
    };
    buildCells();
    // ViewSupport.setThemedBackground(recyclerView, ColorId.filling, this);
    RemoveHelper.attach(recyclerView, new RemoveHelper.Callback() {
      @Override
      public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
        return viewHolder.getItemViewType() == ListItem.TYPE_USER;
      }

      @Override
      public void onRemove (RecyclerView.ViewHolder viewHolder) {
        unblockSender(((UserView) viewHolder.itemView).getUser());
      }
    });
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        if (isFocused() && canLoadMore && !isLoadingMore && senders != null && !senders.isEmpty() && loadOffset != 0) {
          int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
          if (lastVisiblePosition + 10 >= senders.size()) {
            loadMore();
          }
        }
      }
    });

    recyclerView.setAdapter(adapter);
    tdlib.client().send(new TdApi.GetBlockedMessageSenders(getArgumentsStrict(), 0, 20), result -> {
      if (result.getConstructor() == TdApi.MessageSenders.CONSTRUCTOR) {
        TdApi.MessageSenders senders = (TdApi.MessageSenders) result;
        ArrayList<TGUser> list = new ArrayList<>(senders.senders.length);
        for (TdApi.MessageSender sender : senders.senders) {
          list.add(parseSender(tdlib, sender, list));
        }
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            this.senders = list;
            this.loadOffset = senders.senders.length;
            this.canLoadMore = loadOffset <= senders.totalCount;
            buildCells();
            executeScheduledAnimation();
          }
        });
      }
    });
    tdlib.cache().addGlobalUsersListener(this);
    tdlib.listeners().subscribeForGlobalUpdates(this);
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().removeGlobalUsersListener(this);
    tdlib.listeners().unsubscribeFromGlobalUpdates(this);
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();

    if (senders != null) {
      if (senders.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.BlockListEmpty));
      } else {
        items.ensureCapacity(senders.size());
        for (TGUser chat : senders) {
          items.add(new ListItem(ListItem.TYPE_USER, R.id.user, 0, 0).setLongId(chat.getChatId()));
        }
      }
    }

    adapter.setItems(items, false);
  }

  private void addSenders (ArrayList<TGUser> newSenders) {
    if (newSenders.isEmpty())
      return;
    final int startIndex = senders.size();
    senders.ensureCapacity(senders.size() + newSenders.size());
    senders.addAll(newSenders);
    List<ListItem> out = adapter.getItems();
    ArrayUtils.ensureCapacity(out, out.size() + newSenders.size());
    for (TGUser user : newSenders) {
      out.add(new ListItem(ListItem.TYPE_USER, R.id.user, 0, 0).setLongId(user.getUserId()));
    }
    adapter.notifyItemRangeInserted(startIndex, newSenders.size());
  }

  @Override
  public void onUserUpdated (final TdApi.User user) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && senders != null && !senders.isEmpty()) {
        for (TGUser parsedSender : senders) {
          if (parsedSender.getUserId() == user.id) {
            parsedSender.setUser(user, 0);
            adapter.updateUserViewByLongId(ChatId.fromUserId(user.id), false);
            break;
          }
        }
      }
    });
  }

  private void addSender (TdApi.MessageSender sender) {
    if (senders == null)
      return;
    TGUser parsedSender = parseSender(tdlib, sender, senders);
    if (parsedSender == null) {
      return;
    }
    this.senders.add(0, parsedSender);
    if (senders.size() == 1) {
      buildCells();
    } else {
      int i = findFirstVisiblePosition();
      int top = getViewTop(i);
      adapter.getItems().add(0, new ListItem(ListItem.TYPE_USER, R.id.user, 0, 0).setLongId(parsedSender.getChatId()));
      adapter.notifyItemInserted(0);
      if (i != -1) {
        ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(i, top);
      }
    }
  }

  private void removeSender (TdApi.MessageSender sender) {
    int index = indexOfSender(ChatId.fromSender(sender));
    if (index != -1) {
      removeSender(index);
    }
  }

  private void removeSender (int position) {
    if (senders.size() == 1) {
      senders.clear();
      buildCells();
    } else {
      TGUser sender = senders.remove(position);
      adapter.removeItemByLongId(sender.getChatId());
    }
  }

  private int indexOfSender (long chatId) {
    if (senders != null) {
      int i = 0;
      for (TGUser sender : senders) {
        if (sender.getChatId() == chatId) {
          return i;
        }
        i++;
      }
    }
    return -1;
  }

  @Override
  public void onChatBlockListChanged (long chatId, @Nullable TdApi.BlockList blockList) {
    if (ChatId.isSecret(chatId)) {
      return;
    }
    tdlib.ui().post(() -> {
      if (!isDestroyed() && senders != null) {
        final boolean isBlocked = blockList != null && blockList.getConstructor() == getArgumentsStrict().getConstructor();
        int index = indexOfSender(chatId);
        if (isBlocked && index == -1) {
          long userId = tdlib.chatUserId(chatId);
          if (userId != 0) {
            addSender(new TdApi.MessageSenderUser(userId));
          } else {
            addSender(new TdApi.MessageSenderChat(chatId));
          }
        } else if (!isBlocked && index != -1) {
          removeSender(index);
        }
      }
    });
  }

  @Override
  public boolean needUserStatusUiUpdates () {
    return true;
  }

  @Override
  public void onUserStatusChanged (final long userId, TdApi.UserStatus status, boolean uiOnly) {
    if (!isDestroyed() && senders != null) {
      adapter.updateUserViewByLongId(ChatId.fromUserId(userId), true);
    }
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.user) {
      TGUser user = ((UserView) v).getUser();
      if (user != null) {
        tdlib.ui().openChat(this, user.getSenderId(), new TdlibUi.ChatOpenParameters().keepStack());
      }
    }
  }
}
