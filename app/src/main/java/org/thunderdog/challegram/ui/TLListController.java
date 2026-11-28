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

import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tradelens.ConnectClient;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

/**
 * A read-only list screen fed by one TradeLens Connect endpoint. The TradeLens server stays the single source of truth;
 * these screens only display what it returns and have no way to change anything.
 */
abstract class TLListController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;
  protected String path;

  TLListController (Context context, Tdlib tdlib, String path) {
    super(context, tdlib);
    this.path = path;
  }

  /** Fills the rows for a successful answer. */
  protected abstract void build (JSONObject json, ArrayList<ListItem> items);

  /** A tap on a row other than Refresh. */
  protected void onRowClick (View v) { }

  protected static ListItem row (int id, String caption, String value) {
    return new ListItem(ListItem.TYPE_INFO_SETTING, id, R.drawable.baseline_tradelens_24, caption).setStringValue(value);
  }

  protected static ListItem plain (String caption, String value) {
    return row(R.id.btn_tlRow, caption, value);
  }

  protected static void separator (ArrayList<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
  }

  protected static void section (ArrayList<ListItem> items, String title) {
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, title, false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setData(item.getStringValue());
      }
    };
    recyclerView.setAdapter(adapter);
    message("Loading…");
    load();
  }

  protected void load () {
    ConnectClient.get(tdlib, path, (json, error) -> UI.post(() -> {
      if (isDestroyed()) {
        return;
      }
      if (json == null) {
        message("not_linked".equals(error) ? "Link your TradeLens account first. Open the TradeLens screen to connect."
          : "Couldn't reach TradeLens. Tap Refresh to retry.");
        return;
      }
      ArrayList<ListItem> items = new ArrayList<>();
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      build(json, items);
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(row(R.id.btn_tlRefresh, "Refresh", "Reload from TradeLens"));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      adapter.replaceItems(items);
    }));
  }

  private void message (String text) {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(plain("TradeLens", text));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(row(R.id.btn_tlRefresh, "Refresh", "Reload from TradeLens"));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.replaceItems(items);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_tlRefresh) {
      message("Loading…");
      load();
    } else {
      onRowClick(v);
    }
  }
}
