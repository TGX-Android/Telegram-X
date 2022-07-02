package org.thunderdog.challegram.component.popups;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.CustomItemAnimator;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.ScrollToTopDelegate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.android.AnimatorUtils;

public class MessageViewersComponent extends CustomRecyclerView implements Client.ResultHandler, ScrollToTopDelegate {
  private final TGMessage message;
  private final ViewController<?> controller;

  private final SettingsAdapter adapter;

  public MessageViewersComponent (@NonNull ViewController<?> controller, @NonNull TGMessage message) {
    super(controller.context());

    ViewSupport.setThemedBackground(this, R.id.theme_color_background, controller);

    this.controller = controller;
    this.message = message;

    adapter = new SettingsAdapter(controller) {
      @Override
      protected void setUser (@NonNull ListItem item, int position, @NonNull UserView userView, boolean isUpdate) {
        TdApi.User chatUser = tdlib().chatUser(item.getLongId());
        if (chatUser != null) {
          TGUser user = new TGUser(tdlib(), chatUser);
          user.setForceNeedSeparator(item.getBoolValue());
          userView.setUser(user);
        }
      }

      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        Object data = item.getData();
        if (data instanceof Integer) {
          int totalCount = (Integer) data;
          if (totalCount > 0) {
            infoView.showInfo(MessageSeenController.getViewString(message, totalCount));
          } else {
            infoView.showInfo(MessageSeenController.getNobodyString(message));
          }
        } else {
          infoView.showProgress();
        }
      }
    };
    setLayoutManager(new LinearLayoutManager(controller.context()));
    setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, 180L));
    setOverScrollMode(View.OVER_SCROLL_NEVER);
    setAdapter(adapter);

    adapter.setItems(new ListItem[]{
      new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)),
      new ListItem(ListItem.TYPE_LIST_INFO_VIEW),
    }, false);

    loadViewers();
  }

  public void loadViewers () {
    tdlib().send(new TdApi.GetMessageViewers(message.getChatId(), message.getId()), this);
  }

  private Tdlib tdlib () {
    return controller.tdlib();
  }

  @Override
  public void onResult (TdApi.Object result) {
    if (result.getConstructor() != TdApi.Users.CONSTRUCTOR) {
      setItems(Collections.emptyList());
      return;
    }
    TdApi.Users users = (TdApi.Users) result;
    List<ListItem> items = new ArrayList<>(users.totalCount);
    long[] userIds = users.userIds;
    for (int index = 0; index < userIds.length; index++) {
      long userId = userIds[index];
      boolean hasDivider = index < userIds.length - 1;
      items.add(new ListItem(ListItem.TYPE_USER, R.id.user).setLongId(userId).setBoolValue(!hasDivider));
    }
    if (items.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)));
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }
    items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW).setData(users.totalCount));
    postItems(items);
  }

  private void postItems (List<ListItem> items) {
    tdlib().ui().post(() -> setItems(items));
  }

  private void setItems (List<ListItem> items) {
    if (controller.isDestroyed()) {
      return;
    }
    if (items.isEmpty()) {
      adapter.setItems(new ListItem[]{
        new ListItem(ListItem.TYPE_PADDING).setHeight(Screen.dp(12f)),
        new ListItem(ListItem.TYPE_LIST_INFO_VIEW).setData(0)
      }, false);
    } else {
      adapter.setItems(items, false);
    }
  }

  @Override
  public void onScrollToTopRequested () {
    // TODO implement onScrollToTopRequested
  }
}
