package org.thunderdog.challegram.component.popups;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.support.ViewSupport;

import me.vkryl.android.widget.FrameLayoutFix;

public class JoinRequestsController extends MediaBottomBaseController<Void> implements View.OnClickListener {
  private boolean allowExpand;

  private final TdApi.ChatJoinRequestsInfo requestsInfo;
  private final JoinRequestsComponent component;

  protected JoinRequestsController (MediaLayout context, long chatId, TdApi.ChatJoinRequestsInfo requestsInfo) {
    super(context, "Test");
    this.component = new JoinRequestsComponent(this, chatId, null);
    this.requestsInfo = requestsInfo;
  }

  @Override
  public void onClick (View v) {
    component.onClick(v);
  }

  @Override
  protected View onCreateView (Context context) {
    buildContentView(false);

    this.component.onCreateView(context, recyclerView);
    ViewSupport.setThemedBackground(recyclerView, R.id.theme_color_background);

    initMetrics();
    this.allowExpand = getInitialContentHeight() == super.getInitialContentHeight();

    if (!allowExpand) {
      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams();
      params.height = getInitialContentHeight();
      recyclerView.setLayoutParams(params);
    }

    return contentView;
  }

  @Override
  protected int getInitialContentHeight () {
    if (requestsInfo != null && requestsInfo.totalCount > 0) {
      return Math.min(super.getInitialContentHeight(), component.getHeight(requestsInfo.totalCount));
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

  @Override
  public void destroy () {
    super.destroy();
    component.destroy();
  }
}
