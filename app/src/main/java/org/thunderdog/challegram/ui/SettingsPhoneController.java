/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 21/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.v.CustomRecyclerView;

public class SettingsPhoneController extends RecyclerViewController<Void> implements View.OnClickListener {
  public SettingsPhoneController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_editPhone;
  }

  @Override
  public CharSequence getName () {
    TdApi.User user = tdlib.myUser();
    return user != null ? Strings.formatPhone(user.phoneNumber) : Lang.getString(R.string.PhoneNumberChange);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    SettingsAdapter adapter = new SettingsAdapter(this);
    adapter.setItems(new ListItem[] {
      new ListItem(ListItem.TYPE_ICONIZED_EMPTY, R.id.changePhoneText, R.drawable.baseline_sim_card_96, Strings.replaceBoldTokens(Lang.getString(R.string.PhoneNumberHelp), R.id.theme_color_background_textLight), false),
      new ListItem(ListItem.TYPE_SHADOW_TOP),
      new ListItem(ListItem.TYPE_BUTTON, R.id.btn_changePhoneNumber, 0, R.string.PhoneNumberChange),
      new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
    }, false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_changePhoneNumber) {
      showOptions(Lang.getString(R.string.PhoneNumberAlert), new int[] {R.id.btn_edit, R.id.btn_cancel}, new String[] {Lang.getString(R.string.PhoneNumberChangeDone), Lang.getString(R.string.Cancel)}, null, new int[] {R.drawable.baseline_check_circle_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
        if (id == R.id.btn_edit) {
          PhoneController c = new PhoneController(context, tdlib);
          c.setMode(PhoneController.MODE_CHANGE_NUMBER);
          navigateTo(c);
        }
        return true;
      });
    }
  }
}
