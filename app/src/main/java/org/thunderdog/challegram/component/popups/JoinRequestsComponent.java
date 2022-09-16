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
 */
package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.ui.ChatJoinRequestsController;
import org.thunderdog.challegram.ui.ChatLinksController;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.widget.DoubleTextViewWithIcon;
import org.thunderdog.challegram.widget.EmbeddableStickerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.core.ArrayUtils;

public class JoinRequestsComponent implements TGLegacyManager.EmojiLoadListener, Client.ResultHandler {
  private static final String UTYAN_EMOJI = "\uD83D\uDE0E";

  private ArrayList<TGUser> joinRequests;
  private final ArrayList<TdApi.ChatJoinRequest> joinRequestsTdlib = new ArrayList<>();

  private int loadOffset;
  private boolean canLoadMore;
  private boolean isLoadingMore;
  private String currentQuery;

  private final ViewController<?> controller;
  private final long chatId;
  private final String inviteLink;
  private final boolean isBottomSheet;
  private final boolean isSeparateLink;
  private final boolean isChannel;

  private RecyclerView recyclerView;
  private SettingsAdapter adapter;

  public JoinRequestsComponent (ViewController<?> controller, long chatId, String inviteLink) {
    this.controller = controller;
    this.chatId = chatId;
    this.inviteLink = inviteLink;
    this.isBottomSheet = controller instanceof JoinRequestsController;
    this.isSeparateLink = inviteLink != null && !inviteLink.isEmpty();
    this.isChannel = controller.tdlib().isChannel(chatId);
  }

  private BaseActivity context() {
    return controller.context();
  }

  private Tdlib tdlib() {
    return controller.tdlib();
  }

  private void closeIfAvailable () {
    if (isBottomSheet) {
      ((JoinRequestsController) controller).close();
    }
  }

  private void onRequestDecided () {
    if (isBottomSheet) {
      ((JoinRequestsController) controller).onRequestDecided();
    } else if (controller instanceof ChatJoinRequestsController) {
      ((ChatJoinRequestsController) controller).onRequestDecided();
      if (adapter.indexOfViewById(R.id.user) == -1) {
        controller.navigateBack();
      }
    }
  }

  public boolean needAsynchronousAnimation () {
    return joinRequests == null;
  }

  public boolean inSearchMode () {
    return currentQuery != null && !currentQuery.isEmpty();
  }

  public void onClick (View v) {
    if (v.getId() != R.id.user) {
      return;
    }

    TGUser user = (TGUser) v.getTag();

    if (user == null) {
      return;
    }

    SpannableStringBuilder msg = new SpannableStringBuilder(Lang.wrap(user.getName(), Lang.boldCreator()));
    int idx = joinRequests.indexOf(user);
    if (idx != -1) {
      TdApi.ChatJoinRequest request = joinRequestsTdlib.get(idx);

      if (!request.bio.isEmpty()) {
        msg.append("\n\n").append(Lang.wrap(joinRequestsTdlib.get(joinRequests.indexOf(user)).bio, Lang.italicCreator()));
      }

      msg.append("\n").append(Lang.wrap(Lang.getString(R.string.InviteLinkRequestSince, Lang.getMessageTimestamp(request.date, TimeUnit.SECONDS)), Lang.italicCreator()));
    }

    controller.showOptions(msg, new int[]{R.id.btn_approveChatRequest, R.id.btn_declineChatRequest, R.id.btn_openChat}, new String[]{Lang.getString(isChannel ? R.string.InviteLinkActionAcceptChannel : R.string.InviteLinkActionAccept), Lang.getString(R.string.InviteLinkActionDeclineAction), Lang.getString(R.string.InviteLinkActionWrite)}, new int[] { ViewController.OPTION_COLOR_BLUE, ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL }, new int[]{R.drawable.baseline_person_add_24, R.drawable.baseline_delete_24, R.drawable.baseline_person_24}, (itemView2, id2) -> {
      switch (id2) {
        case R.id.btn_approveChatRequest:
          acceptRequest(user);
          break;
        case R.id.btn_openChat:
          openProfile(user);
          break;
        case R.id.btn_declineChatRequest:
          declineRequest(user);
          break;
      }

      return true;
    });
  }

  public void destroy () {
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  public void onCreateView (Context context, RecyclerView recyclerView) {
    this.adapter = new SettingsAdapter(controller) {
      @Override
      protected void setEmbedSticker (ListItem item, int position, EmbeddableStickerView userView, boolean isUpdate) {
        TdApi.Sticker sticker = (TdApi.Sticker) item.getData();
        userView.setSticker(new TGStickerObj(tdlib(), sticker, UTYAN_EMOJI, sticker.type));
        userView.setCaptionText(Strings.buildMarkdown(controller, Lang.getString(isChannel ? R.string.InviteLinkRequestsHintChannel : R.string.InviteLinkRequestsHint, "tg://need_update_for_some_feature"), (view, span, clickedText) -> {
          ChatLinksController linksController = new ChatLinksController(context(), tdlib());
          linksController.setArguments(new ChatLinksController.Args(chatId, tdlib().myUserId(), null, null, tdlib().chatStatus(chatId).getConstructor() == TdApi.ChatMemberStatusCreator.CONSTRUCTOR));
          controller.navigateTo(linksController);
          return true;
        }));
      }

      @Override
      protected void setJoinRequest (ListItem item, int position, DoubleTextViewWithIcon group, boolean isUpdate) {
        TGUser user = joinRequests.get((isBottomSheet || isSeparateLink || inSearchMode()) ? position : position - 3);
        group.setTag(user);
        group.text().setAvatar(user.getAvatar(), user.getAvatarPlaceholderMetadata());
        group.text().setText(user.getName(), user.getStatus());
        group.text().setIcon(R.drawable.baseline_person_add_16, (v) -> acceptRequest(user));
        group.icon().setImageResource(R.drawable.baseline_close_24);
        group.setIconClickListener((v) -> declineRequest(user));
        group.setTextClickListener((v) -> onClick(group));
      }
    };

    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        if ((isBottomSheet || controller.isFocused()) && canLoadMore && !isLoadingMore && joinRequests != null && !joinRequests.isEmpty() && loadOffset != 0) {
          int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
          if (lastVisiblePosition + 10 >= joinRequests.size()) {
            loadMore();
          }
        }
      }
    });

    recyclerView.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setAdapter(this.adapter);

    this.recyclerView = recyclerView;
    toggleItemAnimator(true);

    TGLegacyManager.instance().addEmojiListener(this);

    adapter.setItems(new ListItem[] {
      new ListItem(ListItem.TYPE_PROGRESS)
    }, false);

    loadInitial();
  }

  private void loadInitial () {
    tdlib().client().send(new TdApi.GetChatJoinRequests(chatId, inviteLink, currentQuery,null, 20), result -> {
      if (result.getConstructor() == TdApi.ChatJoinRequests.CONSTRUCTOR) {
        joinRequestsTdlib.clear();

        TdApi.ChatJoinRequests senders = (TdApi.ChatJoinRequests) result;
        ArrayList<TGUser> list = new ArrayList<>(senders.requests.length);

        for (TdApi.ChatJoinRequest sender : senders.requests) {
          joinRequestsTdlib.add(sender);
          list.add(parseSender(tdlib(), sender, list));
        }

        tdlib().ui().post(() -> {
          if (!controller.isDestroyed()) {
            this.joinRequests = list;
            this.loadOffset = senders.requests.length;
            this.canLoadMore = loadOffset <= senders.totalCount;
            buildCells();
            controller.executeScheduledAnimation();
            toggleItemAnimator(false);
          }
        });
      }
    });
  }

  private void toggleItemAnimator (boolean enabled) {
    recyclerView.setItemAnimator(enabled ? new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180l) : null);
  }

  private void openProfile (TGUser user) {
    closeIfAvailable();
    tdlib().ui().openPrivateProfile(new TdlibContext(context(), tdlib()), user.getUserId(), null);
  }

  private void acceptRequest (TGUser user) {
    controller.showOptions(Lang.getStringBold(R.string.AreYouSureAcceptJoinRequest, user.getName(), tdlib().chatTitle(chatId)), new int[]{R.id.btn_approveChatRequest, R.id.btn_cancel}, new String[]{Lang.getString(isChannel ? R.string.InviteLinkActionAcceptChannel : R.string.InviteLinkActionAccept), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_BLUE, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_person_add_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
      if (id2 == R.id.btn_approveChatRequest) {
        tdlib().client().send(new TdApi.ProcessChatJoinRequest(chatId, user.getUserId(), true), obj -> removeSender(user));
      }

      return true;
    });
  }

  private void declineRequest (TGUser user) {
    controller.showOptions(Lang.getStringBold(R.string.AreYouSureDeclineJoinRequest, user.getName()), new int[]{R.id.btn_declineChatRequest, R.id.btn_cancel}, new String[]{Lang.getString(R.string.InviteLinkActionDeclineAction), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
      if (id2 == R.id.btn_declineChatRequest) {
        tdlib().client().send(new TdApi.ProcessChatJoinRequest(chatId, user.getUserId(), false), obj -> removeSender(user));
      }

      return true;
    });
  }

  private void removeSender (TGUser user) {
    controller.runOnUiThreadOptional(() -> {
      int itemIdx = joinRequests.indexOf(user);
      if (itemIdx == -1) return;
      toggleItemAnimator(true);
      joinRequests.remove(itemIdx);
      joinRequestsTdlib.remove(itemIdx);
      adapter.removeItem((isBottomSheet || isSeparateLink || inSearchMode()) ? itemIdx : itemIdx + 3);
      onRequestDecided();
      tdlib().ui().postDelayed(() -> toggleItemAnimator(false), 500L);
    });
  }

  private static TGUser parseSender (Tdlib tdlib, TdApi.ChatJoinRequest sender, ArrayList<TGUser> senders) {
    TGUser parsedUser = new TGUser(tdlib, tdlib.cache().user(sender.userId));
    parsedUser.setNoBotState();
    parsedUser.setCustomStatus(Lang.getString(R.string.InviteLinkRequestSince, Lang.getMessageTimestamp(sender.date, TimeUnit.SECONDS)));
    parsedUser.setBoundList(senders);
    return parsedUser;
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();

    if (!isBottomSheet && !isSeparateLink && !inSearchMode()) {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_EMBED_STICKER).setData(tdlib().findTgxEmoji(UTYAN_EMOJI)));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    }

    if (joinRequests != null) {
      items.ensureCapacity(joinRequests.size());

      for (TGUser chat : joinRequests) {
        items.add(new ListItem(ListItem.TYPE_JOIN_REQUEST, R.id.user, 0, 0).setLongId(chat.getChatId()));
      }
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (isBottomSheet && !canLoadMore && !inSearchMode()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, isChannel ? R.string.InviteLinkRequestsHintChannel : R.string.InviteLinkRequestsHint));
    }

    adapter.setItems(items, false);
  }

  private void loadMore () {
    if (isLoadingMore || !canLoadMore || joinRequestsTdlib.isEmpty()) {
      return;
    }

    isLoadingMore = true;
    tdlib().client().send(new TdApi.GetChatJoinRequests(chatId, inviteLink, currentQuery, joinRequestsTdlib.get(joinRequestsTdlib.size() - 1), 20), this);
  }

  private int indexOfSender (long chatId) {
    if (joinRequests != null) {
      int i = 0;
      for (TGUser sender : joinRequests) {
        if (sender.getUserId() == chatId) {
          return i;
        }
        i++;
      }
    }
    return -1;
  }

  private void addSenders (ArrayList<TGUser> newSenders) {
    if (newSenders.isEmpty())
      return;
    final int startIndex = joinRequests.size();
    joinRequests.ensureCapacity(joinRequests.size() + newSenders.size());
    joinRequests.addAll(newSenders);
    List<ListItem> out = adapter.getItems();
    ArrayUtils.ensureCapacity(out, out.size() + newSenders.size() + (!canLoadMore ? 2 : 0));
    for (TGUser user : newSenders) {
      out.add(out.size() - 1, new ListItem(ListItem.TYPE_JOIN_REQUEST, R.id.user, 0, 0).setLongId(user.getUserId()));
    }
    if (!canLoadMore) {
      out.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      if (!inSearchMode()) {
        out.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.InviteLinkRequestsHint));
      }
    }
    adapter.notifyItemRangeInserted(startIndex, newSenders.size() + (!canLoadMore ? 2 : 0));
  }

  // bottomsheet only
  public int getHeight (int predictUserCount) {
    int initialContentHeight = SettingHolder.measureHeightForType(ListItem.TYPE_JOIN_REQUEST) * predictUserCount;

    initialContentHeight += Screen.dp(48f); // TODO
    initialContentHeight += SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_TOP);

    return initialContentHeight;
  }

  public void search (String query) {
    if (Objects.equals(currentQuery, query)) {
      return;
    }

    if (query != null && query.isEmpty()) {
      query = null;
    }

    isLoadingMore = false;
    loadOffset = 0;
    currentQuery = query;
    loadInitial();
  }

  @Override
  public void onResult (TdApi.Object object) {
    if (object.getConstructor() != TdApi.ChatJoinRequests.CONSTRUCTOR) {
      return;
    }

    final TdApi.ChatJoinRequests senders = (TdApi.ChatJoinRequests) object;
    final ArrayList<TGUser> parsedChats = new ArrayList<>(senders.requests.length);
    for (TdApi.ChatJoinRequest sender : senders.requests) {
      joinRequestsTdlib.add(sender);
      parsedChats.add(parseSender(tdlib(), sender, this.joinRequests));
    }

    if (!parsedChats.isEmpty()) {
      tdlib().ui().post(() -> {
        if (!controller.isDestroyed()) {
          isLoadingMore = false;
          loadOffset += senders.requests.length;
          canLoadMore = loadOffset <= senders.totalCount;

          for (int i = parsedChats.size() - 1; i >= 0; i--) {
            if (indexOfSender(parsedChats.get(i).getUserId()) != -1) {
              parsedChats.remove(i);
            }
          }

          addSenders(parsedChats);
        }
      });
    }
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    adapter.updateAllValuedSettingsById(R.id.user);
  }
}
