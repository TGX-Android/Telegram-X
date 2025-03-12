package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.util.ReactionModifier;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.List;

import tgx.td.Td;

public class MessageOptionsReactedController extends BottomSheetViewController.BottomSheetBaseRecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private final PopupLayout popupLayout;
  private final TGMessage message;
  @Nullable
  private final TdApi.ReactionType reactionType;
  private String offset = "";

  private boolean canLoadMore = true;
  private boolean isLoadingMore = false;
  private int totalCount = 0;

  public MessageOptionsReactedController (Context context, Tdlib tdlib, PopupLayout popupLayout, TGMessage message, @Nullable TdApi.ReactionType reactionType) {
    super(context, tdlib);
    this.popupLayout = popupLayout;
    this.message = message;
    this.reactionType = reactionType;
  }


  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        TdApi.MessageSender senderId = (TdApi.MessageSender) item.getData();       
        TGUser user;
        if (senderId.getConstructor() == TdApi.MessageSenderUser.CONSTRUCTOR) {
          user = new TGUser(tdlib, tdlib.cache().userStrict(((TdApi.MessageSenderUser) senderId).userId));
        } else {
          user = new TGUser(tdlib, tdlib.chatStrict(((TdApi.MessageSenderChat) senderId).chatId));
        }
        user.setActionDateStatus(item.getIntValue(), R.string.reacted);
        userView.setUser(user);
        if (item.getSliderValues() != null && item.getSliderValues().length > 0 && reactionType == null) {
          userView.setDrawModifier(new ReactionModifier(tdlib, item.getSliderValues()).setMode(ReactionModifier.MODE_INLINE).setOffset(8).requestFiles(userView.getComplexReceiver()));
        } else {
          userView.setDrawModifier(null);
        }
      }

      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        infoView.showInfo(Lang.pluralBold(R.string.xReacted, totalCount));
      }
    };
    recyclerView.setAdapter(adapter);
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (RecyclerView recyclerView, int dx, int dy) {
        if (canLoadMore && !isLoadingMore /*&& senders != null && !senders.isEmpty() && loadOffset != 0*/) {
          int lastVisiblePosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
          if (lastVisiblePosition + 10 >= adapter.getItemCount()) {
            loadMore();
          }
        }
      }
    });
    ViewSupport.setThemedBackground(recyclerView, ColorId.background);
    addThemeInvalidateListener(recyclerView);
    loadMore();
  }

  private void loadMore () {
    if (isLoadingMore || !canLoadMore) {
      return;
    }
    isLoadingMore = true;
    tdlib.client().send(new TdApi.GetMessageAddedReactions(message.getChatId(), message.getSmallestId(), reactionType, offset, 50), (obj) -> {
      if (obj.getConstructor() != TdApi.AddedReactions.CONSTRUCTOR) return;
      runOnUiThreadOptional(() -> {
        TdApi.AddedReactions reactions = (TdApi.AddedReactions) obj;
        offset = reactions.nextOffset;
        isLoadingMore = false;
        canLoadMore = offset.length() > 0;
        totalCount = reactions.totalCount;
        processNewAddedReactions(reactions);
      });
    });
  }

  private void processNewAddedReactions (TdApi.AddedReactions addedReactions) {
    final TdApi.AddedReaction[] reactions = addedReactions.reactions;
    final List<ListItem> items = adapter.getItems();
    final int itemsCount = items.size();

    StringList reactionKeys = new StringList(3);
    TdApi.AddedReaction lastReaction = null;
    for (TdApi.AddedReaction reaction : reactions) {
      if (lastReaction != null && !Td.equalsTo(reaction.senderId, lastReaction.senderId)) {
        if (!items.isEmpty()) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        ListItem item = new ListItem(ListItem.TYPE_USER_SMALL, R.id.sender)
          .setData(lastReaction.senderId)
          .setIntValue(lastReaction.date)
          .setSliderInfo(reactionKeys.get(), 0);
        items.add(item);
        reactionKeys.clear();
      }
      lastReaction = reaction;
      reactionKeys.append(TD.makeReactionKey(lastReaction.type));
    }
    if (lastReaction != null) {
      if (!items.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      ListItem item = new ListItem(ListItem.TYPE_USER_SMALL, R.id.sender)
        .setData(lastReaction.senderId)
        .setIntValue(lastReaction.date)
        .setSliderInfo(reactionKeys.get(), 0);
      items.add(item);
    }

    if (addedReactions.nextOffset.length() == 0) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW));
    }

    adapter.notifyItemRangeChanged(itemsCount, items.size() - itemsCount);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.sender) {
      popupLayout.hideWindow(true);
      ListItem item = (ListItem) v.getTag();
      TdApi.MessageSender senderId = (TdApi.MessageSender) item.getData();
      tdlib.ui().openSenderProfile(this, senderId, new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
    }
  }

  @Override
  public int getId () {
    return R.id.controller_messageOptionsReacted;
  }

  @Override
  public int getItemsHeight (RecyclerView recyclerView) {
    if (adapter.getItems().size() == 0) {
      return 0;
    }
    return adapter.measureHeight(-1);
  }
}
