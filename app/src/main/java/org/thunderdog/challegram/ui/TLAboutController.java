/*
 * This file is a part of TradeLens Connect, an unofficial Telegram client derived from Telegram X
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

import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

/**
 * About TradeLens Connect: what the app is, that it is unofficial and built on Telegram technology, the licence, where the
 * source code is, and the privacy, terms and risk information.
 */
public class TLAboutController extends RecyclerViewController<Void> implements View.OnClickListener {
  private static final String SITE = "https://tradelens360ai.live";
  private static final String SOURCE = "https://github.com/Driverline/tradelens-connect-android";

  public TLAboutController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tlAbout;
  }

  @Override
  public CharSequence getName () {
    return "About TradeLens Connect";
  }

  private static ListItem info (String title, String value, String link) {
    ListItem item = new ListItem(ListItem.TYPE_INFO_SETTING, link != null ? R.id.btn_tlAboutLink : 0, R.drawable.baseline_tradelens_24, title).setStringValue(value);
    if (link != null) {
      item.setData(link);
    }
    return item;
  }

  private static ListItem text (String text) {
    return new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, text, false);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    SettingsAdapter adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setData(item.getStringValue());
      }
    };
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(info("Version", BuildConfig.VERSION_NAME, null));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(info("Privacy notice", "How TradeLens handles your data", SITE + "/privacy.html"));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(info("Terms", "TradeLens terms of use", SITE + "/terms.html"));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(info("Source code", "Open source, GPL-3.0 licence", SOURCE));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(info("Download page", "Latest version and install help", SITE + "/connect.html"));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(text("TradeLens Connect is an unofficial Telegram app. It is built with Telegram's open-source technology (TDLib) "
      + "and is based on the open-source Telegram X app, under the GNU General Public License v3. "
      + "It is not made, owned or endorsed by Telegram. Telegram is a trademark of its owner."));
    items.add(text("TradeLens is a separate service, run by TradeLens and not by Telegram. Linking your TradeLens account is optional. "
      + "Your Telegram messages, contacts, password and login codes are never sent to TradeLens."));
    items.add(text("Risk notice: TradeLens provides education and risk analysis only, not financial advice. "
      + "Trading carries a high risk of loss, and you stay responsible for every trade you take."));
    adapter.setItems(items.toArray(new ListItem[0]), false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_tlAboutLink) {
      Object data = ((ListItem) v.getTag()).getData();
      if (data instanceof String) {
        tdlib.ui().openUrl(this, (String) data, null);
      }
    }
  }
}
