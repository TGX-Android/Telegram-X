package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.popups.MessageSeenController;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.data.TGMessage;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.PopupLayout;

import java.util.ArrayList;

public class MessageOptionsSeenController extends BottomSheetViewController.BottomSheetBaseRecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private PopupLayout popupLayout;
  private TGMessage message;
  private TdApi.MessageViewers viewers;

  public MessageOptionsSeenController (Context context, Tdlib tdlib, PopupLayout popupLayout, TGMessage msg) {
    super(context, tdlib);
    this.popupLayout = popupLayout;
    this.message = msg;
  }

  @Override
  public int getId () {
    return R.id.controller_messageOptionsSeen;
  }

  @Override
  public boolean supportsBottomInset () {
    return true;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        TGUser user = new TGUser(tdlib, tdlib.chatUser(item.getLongId()));
        user.setActionDateStatus(item.getIntValue(), message.getMessage());
        userView.setUser(user);
      }

      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        if (viewers != null && message != null) {
          infoView.showInfo(MessageSeenController.getViewString(message, viewers.viewers.length));
        }
      }
    };
    recyclerView.setAdapter(adapter);
    ViewSupport.setThemedBackground(recyclerView, ColorId.background);
    addThemeInvalidateListener(recyclerView);
    tdlib.client().send(new TdApi.GetMessageViewers(message.getChatId(), message.getId()), (obj) -> {
      if (obj.getConstructor() != TdApi.MessageViewers.CONSTRUCTOR) return;
      runOnUiThreadOptional(() -> {
        setUsers(message, (TdApi.MessageViewers) obj);
      });
    });
  }

  @Override
  public boolean needsTempUpdates () {
    return true;
  }

  public void setUsers (TGMessage msg, TdApi.MessageViewers viewers) {
    this.message = msg;
    this.viewers = viewers;

    boolean first = true;
    ArrayList<ListItem> items = new ArrayList<>();
    for (TdApi.MessageViewer viewer : viewers.viewers) {
      if (first) {
        first = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(new ListItem(ListItem.TYPE_USER_SMALL, R.id.user).setLongId(viewer.userId).setIntValue(viewer.viewDate));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    //items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, R.string.MessageSeenPrivacy));
    items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW));
    adapter.setItems(items.toArray(new ListItem[0]), false);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.user) {
      popupLayout.hideWindow(true);
      tdlib.ui().openPrivateProfile(this, ((ListItem) v.getTag()).getLongId(), new TdlibUi.UrlOpenParameters().tooltip(context().tooltipManager().builder(v)));
    }
  }

  @Override
  public int getItemsHeight (RecyclerView recyclerView) {
    if (adapter.getItems().size() == 0) {
      return 0;
    }
    return adapter.measureHeight(-1);
  }
}
