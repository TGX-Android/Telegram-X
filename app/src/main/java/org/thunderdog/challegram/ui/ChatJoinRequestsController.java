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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.popups.JoinRequestsComponent;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.v.CustomRecyclerView;

public class ChatJoinRequestsController extends RecyclerViewController<ChatJoinRequestsController.Args> implements View.OnClickListener, Menu {
  private JoinRequestsComponent component;

  public ChatJoinRequestsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    component = new JoinRequestsComponent(this, args.chatId, args.inviteLink);
  }

  @Override
  public void onClick (View v) {
    component.onClick(v);
  }

  @Override
  public int getId () {
    return R.id.controller_chatJoinRequests;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.InviteLinkRequests);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    component.onCreateView(context, recyclerView);
  }

  @Override
  public void destroy () {
    super.destroy();
    component.destroy();
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return component.needAsynchronousAnimation();
  }

  public void onRequestDecided () {
    ViewController<?> parent = getArgumentsStrict().parentController;
    if (parent instanceof ChatLinksController) {
      ((ChatLinksController) parent).onChatLinkPendingDecisionMade(getArgumentsStrict().inviteLink);
    }
  }

  public static class Args {
    private final long chatId;
    private final String inviteLink;
    private final ViewController<?> parentController;

    public Args (long chatId, String inviteLink, ViewController<?> parentController) {
      this.chatId = chatId;
      this.inviteLink = inviteLink;
      this.parentController = parentController;
    }
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
        if (getArgumentsStrict().inviteLink == null || getArgumentsStrict().inviteLink.isEmpty()) {
          header.addSearchButton(menu, this);
        }
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
        if (headerView != null) {
          headerView.openSearchMode();
        }
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
  protected void onSearchInputChanged (final String query) {
    super.onSearchInputChanged(query);
    component.search(Strings.clean(query.trim()));
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (inSearchMode()) {
      closeSearchMode(null);
      return true;
    }

    return super.onBackPressed(fromTop);
  }
}
