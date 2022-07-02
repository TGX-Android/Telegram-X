package org.thunderdog.challegram.component.popups;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.util.ReactionModifier;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.ScrollToTopDelegate;

import me.vkryl.android.AnimatorUtils;

public class MessageAddedReactionsComponent extends CustomRecyclerView implements Client.ResultHandler, ScrollToTopDelegate {
  private static final String NO_OFFSET = "";

  private String loadOffset;
  private boolean isLoadingMore;

  private final String reaction;
  private final TGMessage message;
  private final ViewController<?> controller;

  private final SettingsAdapter adapter;
  private final LinearLayoutManager layoutManager;

  public MessageAddedReactionsComponent (@NonNull ViewController<?> controller, @NonNull TGMessage message, @Nullable String reaction) {
    super(controller.context());
    this.controller = controller;

    this.message = message;
    this.reaction = reaction != null ? reaction : "";

    ViewSupport.setThemedBackground(this, R.id.theme_color_background, controller);

    this.adapter = new SettingsAdapter(controller) {
      @Override
      protected void setUser (@NonNull ListItem item, int position, @NonNull UserView userView, boolean isUpdate) {
        userView.setDrawModifier(item.getDrawModifier());
        userView.setUser((TGUser) item.getData());
      }

      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        Object data = item.getData();
        if (data instanceof Integer) {
          int totalCount = (Integer) data;
          if (totalCount > 0) {
            infoView.showInfo(Lang.pluralBold(R.string.xMessageReactions, totalCount));
          } else {
            infoView.showInfo(Lang.getString(R.string.NobodyReacted));
          }
        } else {
          infoView.showProgress();
        }
      }
    };
    this.layoutManager = new LinearLayoutManager(controller.context());

    addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled (@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (!isLoadingMore && loadOffset != null && !loadOffset.isEmpty()) {
          int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
          if (lastVisiblePosition + 10 >= adapter.getItems().size()) {
            loadMore();
          }
        }
      }
    });

    setOverScrollMode(View.OVER_SCROLL_NEVER);
    setLayoutManager(layoutManager);
    setAdapter(adapter);

    toggleItemAnimator(true);

    adapter.setItems(new ListItem[]{
      new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)),
      new ListItem(ListItem.TYPE_LIST_INFO_VIEW),
    }, false);

    loadInitial();
  }

  @NonNull
  public String getReaction () {
    return reaction;
  }

  private Tdlib tdlib () {
    return controller.tdlib();
  }

  private void loadInitial () {
    tdlib().client().send(new TdApi.GetMessageAddedReactions(message.getChatId(), message.getId(), reaction, NO_OFFSET, 20), result -> {
      if (result.getConstructor() != TdApi.AddedReactions.CONSTRUCTOR) {
        postItems(new ListItem[0], true, null);
        return;
      }

      TdApi.AddedReactions addedReactions = (TdApi.AddedReactions) result;
      boolean isLast = addedReactions.nextOffset == null || addedReactions.nextOffset.isEmpty();
      ListItem[] items = mapToItems(addedReactions.reactions, isLast);
      postItems(items, true, addedReactions.nextOffset);
    });
  }

  private void toggleItemAnimator (boolean enabled) {
    setItemAnimator(enabled ? new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180L) : null);
  }

  private void loadMore () {
    if (isLoadingMore || loadOffset == null || loadOffset.isEmpty()) {
      return;
    }
    isLoadingMore = true;
    tdlib().client().send(new TdApi.GetMessageAddedReactions(message.getChatId(), message.getId(), reaction, loadOffset, 20), this);
  }

  @Override
  public void onResult (@NonNull TdApi.Object object) {
    if (object.getConstructor() != TdApi.AddedReactions.CONSTRUCTOR) {
      postItems(new ListItem[0], false, null);
      return;
    }
    TdApi.AddedReactions addedReactions = (TdApi.AddedReactions) object;
    boolean isLast = addedReactions.nextOffset == null || addedReactions.nextOffset.isEmpty();
    ListItem[] items = mapToItems(addedReactions.reactions, isLast);
    postItems(items, false, addedReactions.nextOffset);
  }

  private void postItems (@NonNull ListItem[] items, boolean isInitial, String nextOffset) {
    tdlib().ui().post(() -> addItems(items, isInitial, nextOffset));
  }

  private void addItems (@NonNull ListItem[] items, boolean isInitial, String nextOffset) {
    if (controller.isDestroyed()) {
      return;
    }
    loadOffset = nextOffset;
    if (isInitial) {
      adapter.setItems(items, false);
      toggleItemAnimator(false);
    } else {
      isLoadingMore = false;
      adapter.addItems(adapter.getItems().size(), items);
    }
    if (nextOffset == null || nextOffset.isEmpty()) {
      int itemCount = adapter.getItems().size();
      adapter.addItems(itemCount,
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_LIST_INFO_VIEW).setData(itemCount));
    }
  }

  @NonNull
  private ListItem[] mapToItems (@Nullable TdApi.AddedReaction[] addedReactions, boolean isLast) {
    if (addedReactions == null || addedReactions.length == 0) {
      return new ListItem[0];
    }
    final ListItem[] items = new ListItem[addedReactions.length];
    for (int i = 0; i < addedReactions.length; i++) {
      TdApi.AddedReaction addedReaction = addedReactions[i];
      ListItem item = new ListItem(ListItem.TYPE_USER, R.id.user);
      TGUser user = TGUser.fromMessageSender(tdlib(), addedReaction.senderId);
      if (user != null) {
        user.setForceNeedSeparator(!isLast || (i < (addedReactions.length - 1)));
      }
      item.setData(user);
      if (reaction == null || reaction.isEmpty()) {
        item.setDrawModifier(new ReactionModifier(tdlib(), addedReaction.reaction));
      }
      items[i] = item;
    }
    return items;
  }

  @Override
  public void onScrollToTopRequested () {
    // TODO implement onScrollToTopRequested
  }
}
