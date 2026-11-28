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
 * Native TradeLens dashboard. Everything shown here is read from the TradeLens server (the existing entitlement rules are
 * the single source of truth); nothing is calculated or changed in the app.
 */
public class TradeLensDashboardController extends RecyclerViewController<Void> implements View.OnClickListener {
  private SettingsAdapter adapter;

  public TradeLensDashboardController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_tlDashboard;
  }

  @Override
  public CharSequence getName () {
    return "TradeLens";
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
    load();
  }

  private void load () {
    ConnectClient.get(tdlib, "me", (json, error) -> UI.post(() -> {
      if (isDestroyed()) {
        return;
      }
      if (json == null) {
        render(null, "not_linked".equals(error)
          ? "Your Telegram account isn't linked to TradeLens yet. Open the TradeLens screen to connect."
          : "Couldn't reach TradeLens. Tap Refresh to retry.");
      } else {
        render(json, null);
      }
    }));
  }

  private static ListItem row (int id, String caption, String value) {
    return new ListItem(ListItem.TYPE_INFO_SETTING, id, R.drawable.baseline_tradelens_24, caption).setStringValue(value);
  }

  private static String onOff (JSONObject flags, JSONObject until, String key) {
    boolean on = flags != null && flags.optBoolean(key, false);
    String when = TLFormat.date(until, key);
    return on ? (when.isEmpty() ? "On" : "On · until " + when) : "Off";
  }

  private void render (JSONObject me, String message) {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    if (me == null) {
      items.add(row(R.id.btn_tlRow, "TradeLens", message));
    } else {
      JSONObject ent = me.optJSONObject("entitlements");
      JSONObject flags = ent != null ? ent.optJSONObject("flags") : null;
      JSONObject until = ent != null ? ent.optJSONObject("until") : null;
      JSONObject sig = ent != null ? ent.optJSONObject("signals") : null;
      JSONObject analysis = ent != null ? ent.optJSONObject("analysis") : null;
      JSONArray plans = ent != null ? ent.optJSONArray("plans") : null;

      items.add(row(R.id.btn_tlRow, "Account", me.optString("email", "") + " · " + me.optString("tier", "")));
      if (sig != null) {
        String text = TLFormat.num(sig, "used") + " of " + TLFormat.num(sig, "limit") + " used today";
        if ("credits".equals(sig.optString("kind"))) {
          text += " · balance " + TLFormat.num(sig, "balance");
        }
        if (sig.optBoolean("paused", false)) {
          text += " · paused";
        }
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(row(R.id.btn_tlRow, "Signals", text));
      }
      if (analysis != null) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(row(R.id.btn_tlRow, "Analyses today", TLFormat.num(analysis, "used") + " of " + TLFormat.num(analysis, "limit")));
      }
      String[][] features = {{"notifications", "Notifications"}, {"mt5", "MT5"}, {"copytrading", "Copy trading"}, {"autotrading", "AutoTrading"}};
      for (String[] f : features) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(row(R.id.btn_tlRow, f[1], onOff(flags, until, f[0])));
      }
      if (plans != null) {
        for (int i = 0; i < plans.length(); i++) {
          JSONObject plan = plans.optJSONObject(i);
          if (plan == null) {
            continue;
          }
          String when = TLFormat.date(plan, "until");
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          items.add(row(R.id.btn_tlRow, "Plan", plan.optString("label", plan.optString("plan", "")) + (when.isEmpty() ? "" : " · until " + when)));
        }
      }
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(row(R.id.btn_tlSignals, "Signals", "See the latest setups"));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(row(R.id.btn_tlMt5, "MT5 account", "Balance, equity, open positions and protection"));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(row(R.id.btn_tlAuto, "AutoTrading", "Status, eligibility and recent activity"));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(row(R.id.btn_tlOpenFull, "Full TradeLens", "Open analysis, wallet, MT5 and more"));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(row(R.id.btn_tlRefresh, "Refresh", "Reload from TradeLens"));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.replaceItems(items);
  }

  @Override
  public void onClick (View v) {
    int id = v.getId();
    if (id == R.id.btn_tlSignals) {
      navigateTo(new TradeLensSignalsController(context, tdlib));
    } else if (id == R.id.btn_tlMt5) {
      navigateTo(new TradeLensMt5Controller(context, tdlib));
    } else if (id == R.id.btn_tlAuto) {
      navigateTo(new TradeLensAutoController(context, tdlib));
    } else if (id == R.id.btn_tlOpenFull) {
      navigateTo(new TradeLensController(context, tdlib));
    } else if (id == R.id.btn_tlRefresh) {
      render(null, "Loading…");
      load();
    }
  }
}
