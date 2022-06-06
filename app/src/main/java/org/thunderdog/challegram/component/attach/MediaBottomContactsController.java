/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 19/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.SimpleUsersAdapter;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.ContactsController;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;

public class MediaBottomContactsController extends MediaBottomBaseController<Void> implements Client.ResultHandler, SimpleUsersAdapter.Callback, Menu {
  public MediaBottomContactsController (MediaLayout context) {
    super(context, R.string.AttachContact);
  }

  @Override
  public int getId () {
    return R.id.controller_media_contacts;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_search;
  }

  @Override
  public int getBroadcastingAction () {
    return TdApi.ChatActionChoosingContact.CONSTRUCTOR;
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
        if (users != null && !users.isEmpty()) {
          mediaLayout.getHeaderView().openSearchMode();
          headerView = mediaLayout.getHeaderView();
        }
        break;
      }
      case R.id.menu_btn_clear: {
        clearSearchInput();
        break;
      }
    }
  }

  private static final long ITEM_ANIMATION_DURATION = 180l;

  @Override
  protected View onCreateView (Context context) {
    buildContentView(true);
    setLayoutManager(new LinearLayoutManager(context(), RecyclerView.VERTICAL, false));
    setAdapter(adapter = new SimpleUsersAdapter(this, this, SimpleUsersAdapter.OPTION_CLICKABLE | SimpleUsersAdapter.OPTION_SELECTABLE, this));
    recyclerView.setItemAnimator(new CustomItemAnimator(AnimatorUtils.DECELERATE_INTERPOLATOR, HIDE_PROGRESS_DURATION));
    tdlib.searchContacts(null, ContactsController.DISPLAY_LIMIT, this);
    return contentView;
  }

  private SimpleUsersAdapter adapter;
  private ArrayList<TGUser> users;

  protected void displayContacts (final ArrayList<TGUser> users) {
    if (users.isEmpty()) {
      showError(this.users == null ? R.string.NoContacts : R.string.NothingFound, this.users == null);
      adapter.setUsers(null);
    } else if (this.users == null) {
      hideProgress();
      hideError();
      this.users = users;
      adapter.setUsers(users);
      expandStartHeight(adapter);
    } else {
      adapter.setUsers(users);
      hideError();
    }
  }

  @Override
  public void onUserPicked (TGUser user) {
    mediaLayout.sendContact(user);
  }

  @Override
  public void onUserSelected (int selectedCount, TGUser user, boolean isSelected) {
    mediaLayout.setCounter(selectedCount);
  }

  @Override
  protected void onMultiSendPress (@NonNull TdApi.MessageSendOptions options, boolean disableMarkdown) {
    mediaLayout.sendContacts(adapter.getSelectedUsers(), options);
  }

  @Override
  protected void onCancelMultiSelection () {
    adapter.clearSelectedUsers((LinearLayoutManager) getLayoutManager());
  }

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.Error.CONSTRUCTOR: {
        dispatchError(TD.toErrorString(object), true);
        break;
      }
      case TdApi.Users.CONSTRUCTOR: {
        long[] userIds = ((TdApi.Users) object).userIds;
        ArrayList<TdApi.User> contacts = tdlib.cache().users(userIds);
        final ArrayList<TGUser> users = new ArrayList<>(userIds.length);
        for (TdApi.User user : contacts) {
          if (TD.hasPhoneNumber(user)) {
            users.add(TGUser.createWithPhone(tdlib, user));
          }
        }
        runOnUiThread(() -> displayContacts(users));
        break;
      }
      default: {
        dispatchError("Unknown constructor: " + object.getConstructor(), true);
        break;
      }
    }
  }

  private String lastQuery = "";

  private void searchUsers (final String q) {
    if (users == null) {
      return;
    }
    recyclerView.setItemAnimator(null);
    if (lastQuery.equals(q)) {
      return;
    }
    lastQuery = q;
    if (q.isEmpty()) {
      displayContacts(users);
      return;
    }
    Background.instance().post(() -> {
      final ArrayList<TGUser> foundUsers = new ArrayList<>();
      for (TGUser user : users) {
        TdApi.User raw = user.getUser();
        if (raw == null) {
          continue;
        }

        String firstName = Strings.clean(user.getFirstName().trim()).toLowerCase();
        String lastName = Strings.clean(user.getLastName().trim()).toLowerCase();
        String check = (firstName + " " + lastName).trim();

        if (!firstName.startsWith(q) && !lastName.startsWith(q) && !check.startsWith(q)) {
          continue;
        }

        foundUsers.add(user);
      }
      UI.post(() -> {
        if (!isDestroyed() && lastQuery.equals(q)) {
          displayContacts(foundUsers);
        }
      });
    });
  }

  @Override
  protected void onLeaveSearchMode () {
    searchUsers("");
  }

  @Override
  protected void onSearchInputChanged (final String query) {
    searchUsers(Strings.clean(query.trim().toLowerCase()));
  }
}
