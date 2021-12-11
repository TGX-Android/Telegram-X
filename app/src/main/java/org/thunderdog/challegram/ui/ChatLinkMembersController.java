package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.ForceTouchView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.ArrayUtils;
import me.vkryl.td.ChatId;

public class ChatLinkMembersController extends RecyclerViewController<ChatLinkMembersController.Args> implements View.OnClickListener, Client.ResultHandler, TdlibCache.UserDataChangeListener {
  private ArrayList<TGUser> senders;
  private ArrayList<TdApi.ChatInviteLinkMember> sendersTdlib = new ArrayList<>();

  private DoubleHeaderView headerCell;
  private SettingsAdapter adapter;

  private int loadOffset;
  private boolean canLoadMore;
  private boolean isLoadingMore;

  public ChatLinkMembersController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  public static class Args {
    private final long chatId;
    private final String inviteLink;

    public Args (long chatId, String inviteLink) {
      this.chatId = chatId;
      this.inviteLink = inviteLink;
    }
  }

  @Override
  public int getId () {
    return R.id.controller_chatLinkMembers;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.InviteLinkViewMembersTitle);
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return senders == null;
  }

  @Override
  public void onClick (View v) {
    TGUser user = ((UserView) v).getUser();
    if (user != null) {
      tdlib.ui().openPrivateProfile(this, user.getUserId(), new TdlibUi.UrlOpenParameters());
    }
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    headerCell = new DoubleHeaderView(context());
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(49f), true);
    headerCell.setTitle(getName());
    headerCell.setSubtitle(getArgumentsStrict().inviteLink);

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        if (isUpdate) {
          userView.updateSubtext();
        } else {
          TGUser user = senders.get(position);
          userView.setPreviewChatId(new TdApi.ChatListMain(), user.getChatId(), null);
          userView.setPreviewActionListProvider((v, forceTouchContext, ids, icons, strings, target) -> {
            ids.append(R.id.btn_openChat);
            icons.append(R.drawable.baseline_forum_24);
            strings.append(R.string.OpenChat);

            ids.append(R.id.btn_restrictMember);
            icons.append(R.drawable.baseline_remove_circle_24);
            strings.append(R.string.RestrictUser);

            forceTouchContext.setExcludeHeader(true);

            return new ForceTouchView.ActionListener() {
              @Override
              public void onForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {

              }

              @Override
              public void onAfterForceTouchAction (ForceTouchView.ForceTouchContext context, int actionId, Object arg) {
                switch (actionId) {
                  case R.id.btn_openChat:
                    tdlib.ui().openChat(ChatLinkMembersController.this, user.getChatId(), new TdlibUi.ChatOpenParameters().keepStack());
                    break;
                  case R.id.btn_restrictMember:
                    openRightsScreen(user.getUserId());
                    break;
                }
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
        if (isFocused() && canLoadMore && !isLoadingMore && senders != null && !senders.isEmpty() && loadOffset != 0) {
          int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
          if (lastVisiblePosition + 10 >= senders.size()) {
            loadMore();
          }
        }
      }
    });

    recyclerView.setAdapter(adapter);

    tdlib.client().send(new TdApi.GetChatInviteLinkMembers(getArgumentsStrict().chatId, getArgumentsStrict().inviteLink, null, 20), result -> {
      if (result.getConstructor() == TdApi.ChatInviteLinkMembers.CONSTRUCTOR) {
        TdApi.ChatInviteLinkMembers senders = (TdApi.ChatInviteLinkMembers) result;
        ArrayList<TGUser> list = new ArrayList<>(senders.members.length);

        for (TdApi.ChatInviteLinkMember sender : senders.members) {
          sendersTdlib.add(sender);
          list.add(parseSender(tdlib, sender, list));
        }

        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            this.senders = list;
            this.loadOffset = senders.members.length;
            this.canLoadMore = loadOffset <= senders.totalCount;
            buildCells();
            executeScheduledAnimation();
          }
        });
      }
    });

    tdlib.listeners().subscribeForAnyUpdates(this);
  }

  private void openRightsScreen (long userId) {
    tdlib.client().send(new TdApi.GetChatMember(getArgumentsStrict().chatId, new TdApi.MessageSenderUser(userId)), result -> {
      if (result.getConstructor() != TdApi.ChatMember.CONSTRUCTOR) return;
      runOnUiThreadOptional(() -> {
        TdApi.ChatMember member = (TdApi.ChatMember) result;
        TdApi.ChatMemberStatus myStatus = tdlib.chatStatus(getArgumentsStrict().chatId);
        int mode = TD.canRestrictMember(myStatus, member.status);
        if (mode == TD.RESTRICT_MODE_NEW) {
          member = null;
        }
        EditRightsController c = new EditRightsController(context, tdlib);
        c.setArguments(new EditRightsController.Args(getArgumentsStrict().chatId, new TdApi.MessageSenderUser(userId), true, myStatus, member).noFocusLock());
        navigateTo(c);
      });
    });
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromAnyUpdates(this);
  }

  @Override
  public void onResult (TdApi.Object object) {
    if (object.getConstructor() != TdApi.ChatInviteLinkMembers.CONSTRUCTOR) {
      return;
    }

    final TdApi.ChatInviteLinkMembers senders = (TdApi.ChatInviteLinkMembers) object;
    final ArrayList<TGUser> parsedChats = new ArrayList<>(senders.members.length);
    for (TdApi.ChatInviteLinkMember sender : senders.members) {
      sendersTdlib.add(sender);
      parsedChats.add(parseSender(tdlib, sender, this.senders));
    }

    if (!parsedChats.isEmpty()) {
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          isLoadingMore = false;
          loadOffset += senders.members.length;
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

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();

    if (senders != null) {
      items.ensureCapacity(senders.size());

      for (TGUser chat : senders) {
        items.add(new ListItem(ListItem.TYPE_USER, R.id.user, 0, 0).setLongId(chat.getChatId()));
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

  private static TGUser parseSender (Tdlib tdlib, TdApi.ChatInviteLinkMember sender, ArrayList<TGUser> senders) {
    TGUser parsedUser = new TGUser(tdlib, tdlib.cache().user(sender.userId));
    parsedUser.setNoBotState();
    parsedUser.setCustomStatus(Lang.getString(R.string.MemberSince, Lang.getDate(sender.joinedChatDate, TimeUnit.SECONDS), Lang.time(sender.joinedChatDate, TimeUnit.SECONDS)));
    parsedUser.setBoundList(senders);
    return parsedUser;
  }

  private void loadMore () {
    if (isLoadingMore || !canLoadMore || sendersTdlib.isEmpty()) {
      return;
    }

    isLoadingMore = true;
    tdlib.client().send(new TdApi.GetChatInviteLinkMembers(getArgumentsStrict().chatId, getArgumentsStrict().inviteLink, sendersTdlib.get(sendersTdlib.size() - 1), 50), this);
  }
}
