package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.UserView;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.ui.SettingHolder;
import org.thunderdog.challegram.ui.SettingsAdapter;

import me.vkryl.android.widget.FrameLayoutFix;

public class JoinRequestsController extends MediaBottomBaseController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  private final long chatId;
  private final int totalRequestCount;
  private boolean allowExpand;

  protected JoinRequestsController (MediaLayout context, long chatId, int totalRequestCount) {
    super(context, "Test");
    this.chatId = chatId;
    this.totalRequestCount = totalRequestCount;
  }

  @Override
  public void onClick (View v) {

  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));

    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_openLink) {
          view.setIconColorId(R.id.theme_color_textNeutral);
        } else {
          view.setIconColorId(R.id.theme_color_icon);
        }
      }

      @Override
      protected void setUser (ListItem item, int position, UserView userView, boolean isUpdate) {
        userView.setUser(new TGUser(tdlib, tdlib.chatUser(item.getLongId())));
      }

    };

    ViewSupport.setThemedBackground(recyclerView, R.id.theme_color_background);

    initMetrics();
    this.allowExpand = getInitialContentHeight() == super.getInitialContentHeight();
    setAdapter(adapter);

    FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams();
    params.height = getInitialContentHeight();
    recyclerView.setLayoutParams(params);

    adapter.setItems(new ListItem[] {
      new ListItem(ListItem.TYPE_PROGRESS)
    }, false);

    return contentView;
  }

  @Override
  protected int getInitialContentHeight () {
    if (totalRequestCount > 0) {
      int initialContentHeight = SettingHolder.measureHeightForType(ListItem.TYPE_USER) * totalRequestCount;
      for (int i = totalRequestCount; i < adapter.getItemCount(); i++) {
        ListItem item = adapter.getItems().get(i);
        if (item.getViewType() == ListItem.TYPE_DESCRIPTION) {
          initialContentHeight += Screen.dp(24f);
        } else {
          initialContentHeight += SettingHolder.measureHeightForType(item.getViewType());
        }
      }
      return Math.min(super.getInitialContentHeight(), initialContentHeight);
    }

    return super.getInitialContentHeight();
  }

  @Override
  protected boolean canExpandHeight () {
    return allowExpand;
  }

  @Override
  public int getId () {
    return R.id.controller_chatJoinRequests;
  }

  @Override
  protected ViewGroup createCustomBottomBar () {
    return new FrameLayout(context);
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_CLOSE;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    mediaLayout.hide(false);
    return true;
  }
}
