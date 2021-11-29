package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibContext;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.widget.EmbeddableStickerView;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.ArrayUtils;

public class JoinRequestsComponent implements TGLegacyManager.EmojiLoadListener, Client.ResultHandler {
  private static final String UTYAN_EMOJI = "\uD83D\uDE0E";

  private ArrayList<TGUser> joinRequests;
  private final ArrayList<TdApi.ChatJoinRequest> joinRequestsTdlib = new ArrayList<>();

  private SettingsAdapter adapter;
  private int loadOffset;
  private boolean canLoadMore;
  private boolean isLoadingMore;

  private final ViewController<?> controller;
  private final long chatId;
  private final String inviteLink;
  private final boolean isBottomSheet;
  private final boolean isSeparateLink;

  public JoinRequestsComponent (ViewController<?> controller, long chatId, String inviteLink) {
    this.controller = controller;
    this.chatId = chatId;
    this.inviteLink = inviteLink;
    this.isBottomSheet = controller instanceof JoinRequestsController;
    this.isSeparateLink = inviteLink != null && !inviteLink.isEmpty();
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

  public boolean needAsynchronousAnimation () {
    return joinRequests == null;
  }

  public void onClick (View v) {

  }

  public void destroy () {
    TGLegacyManager.instance().removeEmojiListener(this);
  }

  public void onCreateView (Context context, RecyclerView recyclerView) {
    this.adapter = new SettingsAdapter(controller) {
      @Override
      protected void setEmbedSticker (ListItem item, int position, EmbeddableStickerView userView, boolean isUpdate) {
        userView.setSticker(new TGStickerObj(tdlib(), (TdApi.Sticker) item.getData(), UTYAN_EMOJI, false));
        userView.setCaptionText(getInvitesDescription());
      }

      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        if (isUpdate) {
          userView.updateSubtext();
        } else {
          TGUser user = joinRequests.get((isBottomSheet || isSeparateLink) ? position : position - 3);
          userView.setPreviewChatId(new TdApi.ChatListMain(), user.getChatId(), null);
          userView.setPreviewActionListProvider((v, forceTouchContext, ids, icons, strings, target) -> {
            ids.append(R.id.btn_approveChatRequest);
            icons.append(R.drawable.baseline_person_add_24);
            strings.append(R.string.InviteLinkActionAccept);

            ids.append(R.id.btn_openChat);
            icons.append(R.drawable.baseline_forum_24);
            strings.append(R.string.InviteLinkActionWrite);

            ids.append(R.id.btn_declineChatRequest);
            icons.append(R.drawable.baseline_remove_circle_24);
            strings.append(R.string.InviteLinkActionDecline);

            final ForceTouchView.MaximizeListener maximizeListener = forceTouchContext.getMaximizeListener();
            forceTouchContext.setExcludeHeader(true);
            forceTouchContext.setMaximizeListener((t, animateToWhenReady, arg) -> {
              closeIfAvailable();
              return maximizeListener.onPerformMaximize(t, animateToWhenReady, arg);
            });

            return new ForceTouchView.ActionListener() {
              @Override
              public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
                switch (actionId) {
                  case R.id.btn_approveChatRequest:
                    controller.showOptions(Lang.getString(R.string.AreYouSureAcceptJoinRequest, user.getName(), tdlib().chatTitle(chatId)), new int[]{R.id.btn_approveChatRequest, R.id.btn_cancel}, new String[]{Lang.getString(R.string.InviteLinkActionAccept), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_person_add_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
                      if (id2 == R.id.btn_approveChatRequest) {
                        tdlib().client().send(new TdApi.ApproveChatJoinRequest(chatId, user.getUserId()), obj -> {
                          controller.runOnUiThreadOptional(() -> {
                            if (isBottomSheet) {
                              context().onBackPressed();
                            } else {
                              controller.navigateBack();
                            }
                          });
                        });
                      }

                      return true;
                    });
                    break;
                  case R.id.btn_openChat:
                    closeIfAvailable();
                    tdlib().ui().openChat(controller, user.getUserId(), new TdlibUi.ChatOpenParameters().keepStack());
                    break;
                  case R.id.btn_declineChatRequest:
                    declineRequest(user);
                    break;
                }
              }

              @Override
              public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {

              }
            };
          });

          userView.setUser(user);
        }
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

    TGLegacyManager.instance().addEmojiListener(this);

    if (!isBottomSheet) {
      RemoveHelper.attach(recyclerView, new RemoveHelper.Callback() {
        @Override
        public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
          ListItem item = adapter.getItems().get(position);
          return item != null && item.getId() == R.id.user;
        }

        @Override
        public void onRemove (RecyclerView.ViewHolder viewHolder) {
          declineRequest(joinRequests.get(viewHolder.getBindingAdapterPosition() - (isSeparateLink ? 0 : 3)));
        }
      });
    }

    adapter.setItems(new ListItem[] {
      new ListItem(ListItem.TYPE_PROGRESS)
    }, false);

    tdlib().client().send(new TdApi.GetChatJoinRequests(chatId, inviteLink, null,  null, 20), result -> {
      if (result.getConstructor() == TdApi.ChatJoinRequests.CONSTRUCTOR) {
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
          }
        });
      }
    });
  }

  private void declineRequest (TGUser user) {
    controller.showOptions(Lang.getString(R.string.AreYouSureDeclineJoinRequest, user.getName()), new int[]{R.id.btn_declineChatRequest, R.id.btn_cancel}, new String[]{Lang.getString(R.string.InviteLinkActionDeclineAction), Lang.getString(R.string.Cancel)}, new int[]{ViewController.OPTION_COLOR_RED, ViewController.OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_24, R.drawable.baseline_cancel_24}, (itemView2, id2) -> {
      if (id2 == R.id.btn_declineChatRequest) {
        tdlib().client().send(new TdApi.DeclineChatJoinRequest(chatId, user.getUserId()), obj -> {
          controller.runOnUiThreadOptional(() -> {
            int itemIdx = joinRequests.indexOf(user);
            if (itemIdx == -1) return;
            joinRequests.remove(itemIdx);
            joinRequestsTdlib.remove(itemIdx);
            adapter.removeItem(itemIdx);
          });
        });
      }

      return true;
    });
  }

  private static TGUser parseSender (Tdlib tdlib, TdApi.ChatJoinRequest sender, ArrayList<TGUser> senders) {
    TGUser parsedUser = new TGUser(tdlib, tdlib.cache().user(sender.userId));
    parsedUser.setNoBotState();
    parsedUser.setCustomStatus(!sender.bio.isEmpty() ? sender.bio : Lang.getString(R.string.InviteLinkRequestSince, Lang.getDate(sender.date, TimeUnit.SECONDS), Lang.time(sender.date, TimeUnit.SECONDS)));
    parsedUser.setBoundList(senders);
    return parsedUser;
  }

  private CharSequence getInvitesDescription () {
    return Lang.getMarkdownStringSecure(new TdlibContext(context(), tdlib()), R.string.InviteLinkRequestsHint, "tgx://invites/" + tdlib().myUserId());
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();

    if (!isBottomSheet && !isSeparateLink) {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
      items.add(new ListItem(ListItem.TYPE_EMBED_STICKER).setData(tdlib().findUtyanEmoji(UTYAN_EMOJI)));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    }

    if (joinRequests != null) {
      items.ensureCapacity(joinRequests.size());

      for (TGUser chat : joinRequests) {
        items.add(new ListItem(ListItem.TYPE_USER, R.id.user, 0, 0).setLongId(chat.getChatId()));
      }
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (isBottomSheet && !canLoadMore) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, getInvitesDescription(), false));
    }

    adapter.setItems(items, false);
  }

  private void loadMore () {
    if (isLoadingMore || !canLoadMore || joinRequestsTdlib.isEmpty()) {
      return;
    }

    isLoadingMore = true;
    tdlib().client().send(new TdApi.GetChatJoinRequests(chatId, inviteLink, null, joinRequestsTdlib.get(joinRequestsTdlib.size() - 1), 20), this);
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
    final int startIndex = joinRequests.size() - 1;
    joinRequests.ensureCapacity(joinRequests.size() + newSenders.size());
    joinRequests.addAll(newSenders);
    List<ListItem> out = adapter.getItems();
    ArrayUtils.ensureCapacity(out, out.size() + newSenders.size() + (!canLoadMore ? 2 : 0));
    for (TGUser user : newSenders) {
      out.add(out.size() - 1, new ListItem(ListItem.TYPE_USER, R.id.user, 0, 0).setLongId(user.getUserId()));
    }
    if (!canLoadMore) {
      out.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      out.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, getInvitesDescription(), false));
    }
    adapter.notifyItemRangeInserted(startIndex, newSenders.size() + (!canLoadMore ? 2 : 0));
  }

  // bottomsheet only
  public int getHeight (int predictUserCount) {
    int initialContentHeight = SettingHolder.measureHeightForType(ListItem.TYPE_USER) * predictUserCount;

    initialContentHeight += Screen.dp(48f); // TODO
    initialContentHeight += SettingHolder.measureHeightForType(ListItem.TYPE_SHADOW_TOP);

    return initialContentHeight;
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
  public void onEmojiPartLoaded () {
    adapter.updateAllValuedSettingsById(R.id.user);
  }
}
