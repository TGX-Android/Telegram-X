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

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

/**
 * The "TL Comm" tab: one place for the official TradeLens bot, channels and groups.
 * Each row opens the Telegram link in the usual way (joining through the invite link when needed).
 */
public class TLCommController extends RecyclerViewController<Void> implements View.OnClickListener {
  private static final String[][] ENTRIES = {
    // name, description, link
    {"TradeLens", "Official TradeLens bot", "https://t.me/TradeLensagent"},
    {"TradeLens Market Intelligence", "Official TradeLens channel", "https://t.me/TradeLensMarketIntelligence"},
    {"TradeLens Traders Hub", "Official TradeLens group", "https://t.me/+e-cf-gPTZG0xNjk0"},
    {"TradeLens Resources", "Official TradeLens channel", "https://t.me/TradeLensResources"},
  };

  public TLCommController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tlComm;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.TLComm);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    SettingsAdapter adapter = new SettingsAdapter(this);
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int i = 0; i < ENTRIES.length; i++) {
      if (i > 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      String[] entry = ENTRIES[i];
      items.add(new ListItem(ListItem.TYPE_INFO_SETTING, R.id.btn_tlCommLink, R.drawable.baseline_tradelens_24, entry[1])
        .setStringValue(entry[0])
        .setData(entry[2]));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items.toArray(new ListItem[0]), false);
    recyclerView.setAdapter(adapter);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_tlCommLink) {
      Object data = ((ListItem) v.getTag()).getData();
      if (data instanceof String) {
        tdlib.ui().openUrl(this, (String) data, null);
      }
    }
  }
}
