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
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.ArrayUtils;
import me.vkryl.td.ChatId;

public class ChatLinkMembersController extends RecyclerViewController<ChatLinkMembersController.Args> implements View.OnClickListener, Client.ResultHandler, TdlibCache.UserDataChangeListener {
  private ArrayList<TGUser> senders;
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
      tdlib.ui().openPrivateChat(this, user.getId(), new TdlibUi.ChatOpenParameters().keepStack());
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
          userView.setUser(senders.get(position));
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
          if (parsedSender.getId() == user.id) {
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
      out.add(new ListItem(ListItem.TYPE_USER, R.id.user, 0, 0).setLongId(user.getId()));
    }
    adapter.notifyItemRangeInserted(startIndex, newSenders.size());
  }

  private static TGUser parseSender (Tdlib tdlib, TdApi.ChatInviteLinkMember sender, ArrayList<TGUser> senders) {
    TGUser parsedUser = new TGUser(tdlib, tdlib.cache().user(sender.userId));
    parsedUser.setNoBotState();
    parsedUser.setCustomStatus(Lang.getRelativeDate(sender.joinedChatDate, TimeUnit.SECONDS, System.currentTimeMillis(), TimeUnit.MILLISECONDS, true, 60, R.string.RoleMember, true));
    parsedUser.setBoundList(senders);
    return parsedUser;
  }

  private void loadMore () {
    if (isLoadingMore || !canLoadMore) {
      return;
    }

    isLoadingMore = true;
    tdlib.client().send(new TdApi.GetChatInviteLinkMembers(getArgumentsStrict().chatId, getArgumentsStrict().inviteLink, new TdApi.ChatInviteLinkMember(senders.get(senders.size() - 1).getId(), 0), 50), this);
  }
}
