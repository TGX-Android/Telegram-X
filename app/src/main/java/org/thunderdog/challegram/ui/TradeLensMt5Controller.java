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
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tradelens.TLFormat;

import java.util.ArrayList;

/**
 * MT5 account monitor and equity protection status. Read from TradeLens (the same data as the website's MT5 page);
 * this screen cannot change rules, close a position or send any command to MT5.
 */
public class TradeLensMt5Controller extends TLListController {
  public TradeLensMt5Controller (Context context, Tdlib tdlib) {
    super(context, tdlib, "mt5");
  }

  @Override
  public int getId () {
    return R.id.controller_tlMt5;
  }

  @Override
  public CharSequence getName () {
    return "MT5 account";
  }

  private static String onOff (JSONObject o, String key) {
    return o != null && o.optBoolean(key, false) ? "On" : "Off";
  }

  @Override
  protected void build (JSONObject json, ArrayList<ListItem> items) {
    JSONArray accounts = json.optJSONArray("accounts");
    if (accounts == null || accounts.length() == 0 || !json.optBoolean("has_key", false)) {
      items.add(plain("MT5", json.optBoolean("mt5_allowed", false)
        ? "No MT5 account is connected yet. Connect one on the TradeLens website."
        : "MT5 isn't included in your current plan."));
      return;
    }
    boolean connected = json.optBoolean("connected", false);
    long seen = json.optLong("last_seen_ago", -1);
    items.add(plain(json.optString("label", "MT5 account"),
      connected ? "Connected" : "Not connected" + (seen >= 0 ? " · last seen " + TLFormat.ago(seen) : "")));
    JSONObject acc = json.optJSONObject("account");
    if (acc != null) {
      String cur = acc.optString("currency", "");
      separator(items);
      items.add(plain("Account", (acc.optBoolean("demo", false) ? "Demo" : "Live") + " · " + acc.optString("login", "") + " · " + acc.optString("server", "")));
      separator(items);
      items.add(plain("Balance", TLFormat.money(acc, "balance") + " " + cur));
      separator(items);
      items.add(plain("Equity", TLFormat.money(acc, "equity") + " " + cur));
      if (acc.has("margin_free") && !acc.isNull("margin_free")) {
        separator(items);
        items.add(plain("Free margin", TLFormat.money(acc, "margin_free") + " " + cur));
      }
    }
    JSONObject check = json.optJSONObject("check");
    if (check != null) {
      separator(items);
      items.add(plain("Today", TLFormat.signed(check, "today_pnl") + " (" + TLFormat.signed(check, "today_pct") + "%)"));
      separator(items);
      items.add(plain("Risk check · " + check.optString("status", ""), check.optString("line3", "")));
    }

    JSONArray positions = json.optJSONArray("positions");
    int n = positions == null ? 0 : positions.length();
    section(items, "Open positions (" + n + ")");
    if (n == 0) {
      items.add(plain("Positions", "None open."));
    }
    for (int i = 0; i < n; i++) {
      JSONObject p = positions.optJSONObject(i);
      if (p == null) {
        continue;
      }
      if (i > 0) {
        separator(items);
      }
      items.add(plain("Open " + TLFormat.num(p, "open") + " · SL " + TLFormat.num(p, "sl") + " · TP " + TLFormat.num(p, "tp"),
        p.optString("type", "") + " " + p.optString("symbol", "") + " " + TLFormat.num(p, "volume") + " · " + TLFormat.signed(p, "profit")));
    }

    JSONObject rules = json.optJSONObject("rules");
    JSONObject manage = json.optJSONObject("manage");
    section(items, "Your limits and equity protection");
    if (rules != null) {
      items.add(plain("Maximum risk per trade", TLFormat.num(rules, "max_risk_pct") + "%"));
      separator(items);
      items.add(plain("Daily loss limit", TLFormat.num(rules, "daily_loss_pct") + "%"));
      separator(items);
      items.add(plain("Maximum open positions", TLFormat.num(rules, "max_positions")));
    }
    if (manage != null) {
      if (rules != null) {
        separator(items);
      }
      items.add(plain("Equity protection mode", manage.optString("prot_mode", "off")));
      separator(items);
      items.add(plain("Equity protection levels", "risk " + TLFormat.num(manage, "prot_risk_pct") + "% · cap " + TLFormat.num(manage, "prot_cap_pct") + "%"));
      separator(items);
      items.add(plain("Breakeven", onOff(manage, "be_on")));
      separator(items);
      items.add(plain("Trailing stop loss", onOff(manage, "tsl_on")));
      separator(items);
      items.add(plain("Trailing take profit", onOff(manage, "ttp_on")));
    }
    if (accounts.length() > 1) {
      section(items, "Your MT5 accounts");
      for (int i = 0; i < accounts.length(); i++) {
        JSONObject a = accounts.optJSONObject(i);
        if (a == null) {
          continue;
        }
        if (i > 0) {
          separator(items);
        }
        items.add(row(R.id.btn_tlAccount, "Tap to view", a.optString("label", "") + " · " + (a.optBoolean("connected", false) ? "connected" : "offline"))
          .setData(a.optInt("id", 0)));
      }
    }
  }

  @Override
  protected void onRowClick (View v) {
    if (v.getId() == R.id.btn_tlAccount) {
      Object data = ((ListItem) v.getTag()).getData();
      if (data instanceof Integer && (Integer) data > 0) {
        path = "mt5?account=" + data;
        load();
      }
    }
  }
}
