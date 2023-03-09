/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaBottomBaseController;
import org.thunderdog.challegram.component.attach.MediaLayout;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.tool.Strings;

import me.vkryl.android.widget.FrameLayoutFix;

public class JoinRequestsController extends MediaBottomBaseController<Void> implements View.OnClickListener, Menu {
  private boolean allowExpand;

  private final TdApi.ChatJoinRequestsInfo requestsInfo;
  private final JoinRequestsComponent component;
  private int reqCount;

  protected JoinRequestsController (MediaLayout context, long chatId, TdApi.ChatJoinRequestsInfo requestsInfo) {
    super(context, Lang.plural(R.string.xJoinRequests, requestsInfo.totalCount));
    this.component = new JoinRequestsComponent(this, chatId, null);
    this.requestsInfo = requestsInfo;
    this.reqCount = requestsInfo.totalCount;
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

  public void close () {
    mediaLayout.hide(false);
  }

  public void onRequestDecided () {
    reqCount--;

    if (!mediaLayout.getHeaderView().inSearchMode()) {
      setName(Lang.plural(R.string.xJoinRequests, reqCount));
    }

    if (reqCount == 0) {
      close();
    }
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
    if (mediaLayout.getHeaderView().inSearchMode()) {
      mediaLayout.getHeaderView().closeSearchMode(true, null);
      headerView = mediaLayout.getHeaderView();
      return true;
    }

    close();
    return false;
  }

  @Override
  public void destroy () {
    super.destroy();
    component.destroy();
  }

  // Search

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_search: {
        header.addSearchButton(menu, this);
        break;
      }
      case R.id.menu_clear: {
        header.addClearButton(menu, this);
        break;
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_search: {
        mediaLayout.getHeaderView().openSearchMode();
        headerView = mediaLayout.getHeaderView();
        break;
      }
      case R.id.menu_btn_clear: {
        clearSearchInput();
        break;
      }
    }
  }

  @Override
  protected void onLeaveSearchMode () {
    component.search(null);
  }

  @Override
  protected void onAfterLeaveSearchMode () {
    runOnUiThread(() -> setName(Lang.plural(R.string.xJoinRequests, reqCount)), 100);
  }

  @Override
  protected void onSearchInputChanged (final String query) {
    component.search(Strings.clean(query.trim()));
  }
}
