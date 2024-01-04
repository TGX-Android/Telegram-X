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
 *
 * File created on 19/02/2019
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;

import java.util.ArrayList;
import java.util.List;

public class SettingsLogOutController extends RecyclerViewController<Integer> implements View.OnClickListener {
  @IntDef({
    Type.LOG_OUT, Type.DELETE_ACCOUNT
  })
  public @interface Type {
    int LOG_OUT = 0, DELETE_ACCOUNT = 1;
  }

  private @Type int getType () {
    return getArguments() == null ? Type.LOG_OUT : getArgumentsStrict();
  }

  public SettingsLogOutController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_logOut;
  }

  @Override
  public CharSequence getName () {
    switch (getType()) {
      case Type.LOG_OUT:
        return Lang.getString(R.string.LogOut);
      case Type.DELETE_ACCOUNT:
        return Lang.getString(R.string.DeleteAccount);
    }
    throw new UnsupportedOperationException();
  }

  private SettingsAdapter adapter;
  private DoubleHeaderView headerCell;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    DoubleHeaderView headerCell = new DoubleHeaderView(context);
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(0, true);
    headerCell.setTitle(getName());
    headerCell.setSubtitle(Lang.getString(R.string.SignOutAlt));
    this.headerCell = headerCell;


    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        @IdRes int itemId = item.getId();
        view.setIconColorId(itemId == R.id.btn_logout || itemId == R.id.btn_deleteAccount ? ColorId.iconNegative : ColorId.NONE);
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setEnabled(false);
        chatView.setChat((TGFoundChat) item.getData());
      }
    };

    List<ListItem> items = new ArrayList<>();

    @Type int type = getType();

    TGFoundChat chat = new TGFoundChat(tdlib, tdlib.mySender(), true);
    chat.setForcedSubtitle(Strings.formatPhone(tdlib.account().getPhoneNumber()));
    items.add(new ListItem(ListItem.TYPE_CHAT_BETTER).setData(chat));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_addAccount, R.drawable.baseline_person_add_24, R.string.SignOutAltAddAccount));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SignOutAltAddAccountHint));

    if (!Passcode.instance().isEnabled()) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_passcode, R.drawable.baseline_lock_24, R.string.SignOutAltPasscode));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SignOutAltPasscodeHint));
    }

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_storageUsage, R.drawable.templarian_baseline_broom_24, R.string.SignOutAltClearCache));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SignOutAltClearCacheHint));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_changePhoneNumber, R.drawable.baseline_sim_card_24, R.string.SignOutAltChangeNumber));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SignOutAltChangeNumberHint));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_help, R.drawable.baseline_help_24, R.string.SignOutAltHelp));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, type == Type.DELETE_ACCOUNT ? R.string.DeleteAccountHelpHint : R.string.SignOutAltHelpHint));

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_logout, R.drawable.baseline_logout_24, R.string.LogOut).setTextColorId(ColorId.textNegative));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, type == Type.DELETE_ACCOUNT ? R.string.DeleteAccountSignOutAltHint2 : R.string.SignOutAltHint2));

    if (type == Type.DELETE_ACCOUNT) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_deleteAccount, R.drawable.baseline_delete_alert_24, R.string.DeleteAccountBtn).setTextColorId(ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.DeleteAccountInfo));
    }

    adapter.setItems(items, false);

    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_addAccount) {
      tdlib.ui().addAccount(context, true, false);
    } else if (viewId == R.id.btn_passcode) {
      if (!Passcode.instance().isEnabled()) {
        navigateTo(new PasscodeSetupController(context, tdlib));
      }
    } else if (viewId == R.id.btn_storageUsage) {
      navigateTo(new SettingsCacheController(context, tdlib));
    } else if (viewId == R.id.btn_changePhoneNumber) {
      navigateTo(new SettingsPhoneController(context, tdlib));
    } else if (viewId == R.id.btn_help) {
      tdlib.ui().openSupport(this);
    } else if (viewId == R.id.btn_logout) {
      tdlib.ui().logOut(this, false);
    } else if (viewId == R.id.btn_deleteAccount) {
      tdlib.ui().permanentlyDeleteAccount(this, false);
    }
  }
}
