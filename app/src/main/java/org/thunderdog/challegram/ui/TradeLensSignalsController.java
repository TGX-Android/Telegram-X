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

import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tradelens.ConnectClient;
import org.thunderdog.challegram.tradelens.TLFormat;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

/**
 * Read-only list of TradeLens signals. Opening a signal (which can use your daily allowance or balance) and executing trades
 * stay in the full TradeLens screen, where the existing confirmations and safeguards apply.
 */
public class TradeLensSignalsController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  public TradeLensSignalsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tlSignals;
  }

  @Override
  public CharSequence getName () {
    return "Signals";
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
    render(null, "Loading…");
    ConnectClient.get(tdlib, "signals", (json, error) -> UI.post(() -> {
      if (isDestroyed()) {
        return;
      }
      if (json == null) {
        render(null, "not_linked".equals(error) ? "Link your TradeLens account first." : "Couldn't reach TradeLens.");
      } else {
        render(json, null);
      }
    }));
  }

  private static ListItem row (int id, String caption, String value) {
    return new ListItem(ListItem.TYPE_INFO_SETTING, id, R.drawable.baseline_tradelens_24, caption).setStringValue(value);
  }

  private void render (JSONObject json, String message) {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    if (json == null) {
      items.add(row(R.id.btn_tlRow, "Signals", message));
    } else {
      JSONObject budget = json.optJSONObject("budget");
      if (budget != null) {
        String text = TLFormat.num(budget, "used") + " of " + TLFormat.num(budget, "limit") + " used today";
        if (budget.optBoolean("paused", false)) {
          text += " · paused";
        }
        items.add(row(R.id.btn_tlRow, "Your allowance", text));
      }
      JSONArray list = json.optJSONArray("signals");
      if (list == null || list.length() == 0) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(row(R.id.btn_tlRow, "No signals right now", "New setups appear here as they are found."));
      } else {
        for (int i = 0; i < list.length(); i++) {
          JSONObject s = list.optJSONObject(i);
          if (s == null) {
            continue;
          }
          boolean hasLevels = s.has("entry") && !s.isNull("entry");
          String title = s.optString("direction", "") + " " + s.optString("name", s.optString("instrument", ""));
          StringBuilder caption = new StringBuilder();
          caption.append(s.optString("style", "")).append(" · ").append(s.optString("setup", ""))
            .append(" · risk ").append(s.optString("risk", "")).append(" · ").append(s.optString("status", ""));
          if (hasLevels) {
            caption.append("\nEntry ").append(TLFormat.num(s, "entry")).append(" · Stop ").append(TLFormat.num(s, "stop"))
              .append(" · Target ").append(TLFormat.num(s, "target"));
          } else if (s.optBoolean("actionable", false)) {
            caption.append("\nOpen in TradeLens to see the levels");
          }
          if (!s.isNull("result_r")) {
            caption.append("\nResult ").append(TLFormat.num(s, "result_r")).append(" R");
          }
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(row(R.id.btn_tlRow, caption.toString(), title));
        }
      }
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(row(R.id.btn_tlOpenFull, "Open in TradeLens", "Open signals, reveal levels and trade"));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.replaceItems(items);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_tlOpenFull) {
      navigateTo(new TradeLensController(context, tdlib));
    }
  }
}
