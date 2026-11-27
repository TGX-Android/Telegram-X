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

import org.json.JSONArray;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tradelens.TLFormat;

import java.util.ArrayList;

/**
 * AutoTrading status: availability, eligibility, current status and recent activity, read from TradeLens.
 * This screen cannot switch AutoTrading on or off, change a setting or start a trade; those stay on the TradeLens website,
 * where the existing confirmations and safeguards apply.
 */
public class TradeLensAutoController extends TLListController {
  public TradeLensAutoController (Context context, Tdlib tdlib) {
    super(context, tdlib, "auto");
  }

  @Override
  public int getId () {
    return R.id.controller_tlAuto;
  }

  @Override
  public CharSequence getName () {
    return "AutoTrading";
  }

  @Override
  protected void build (JSONObject json, ArrayList<ListItem> items) {
    if (!json.optBoolean("available", false)) {
      items.add(plain("AutoTrading", "AutoTrading isn't available for your account right now."));
      return;
    }
    JSONArray accounts = json.optJSONArray("accounts");
    if (accounts == null || accounts.length() == 0) {
      items.add(plain("AutoTrading", "Connect an MT5 account on the TradeLens website to use AutoTrading."));
    } else {
      for (int i = 0; i < accounts.length(); i++) {
        JSONObject a = accounts.optJSONObject(i);
        if (a == null) {
          continue;
        }
        if (i > 0) {
          section(items, "Another account");
        }
        String reason = a.optString("paused_reason", "");
        boolean on = a.optBoolean("enabled", false);
        items.add(plain(a.optString("label", "MT5 account"), (on ? "AutoTrading is ON" : "AutoTrading is OFF") + (reason.isEmpty() || "null".equals(reason) ? "" : " · paused: " + reason)));
        JSONObject el = a.optJSONObject("eligibility");
        if (el != null) {
          separator(items);
          boolean ok = el.optBoolean("ok", false);
          String why = el.optString("why", "");
          items.add(plain("Eligibility", ok ? "Eligible" : "Not eligible"));
          if (!ok && !why.isEmpty() && !"null".equals(why)) {
            items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, why, false));
          }
          separator(items);
          items.add(plain("Account equity", TLFormat.money(el, "equity") + " " + el.optString("currency", "")));
          JSONArray keys = el.optJSONArray("keys");
          separator(items);
          items.add(plain("Instruments you can trade", keys == null ? "0" : String.valueOf(keys.length())));
          String tier = el.optString("tier", "");
          if (!tier.isEmpty() && !"null".equals(tier)) {
            separator(items);
            items.add(plain("Account-size category", "Tier " + tier));
          }
        }
        JSONObject prot = a.optJSONObject("protection");
        if (prot != null) {
          separator(items);
          JSONArray changed = prot.optJSONArray("changed");
          items.add(plain("Protection settings", prot.optBoolean("is_default", true) ? "Recommended settings"
            : (changed == null ? 0 : changed.length()) + " changed from recommended · mode " + prot.optString("prot_mode", "")));
        }
        JSONObject pending = a.optJSONObject("pending");
        if (pending != null && pending.length() > 0) {
          separator(items);
          items.add(plain("Pending changes", pending.length() + " setting change(s) waiting for the safety delay"));
        }
      }
    }
    JSONArray jobs = json.optJSONArray("recent");
    int n = jobs == null ? 0 : jobs.length();
    section(items, "Recent activity");
    if (n == 0) {
      items.add(plain("Activity", "No AutoTrading activity yet."));
    }
    for (int i = 0; i < n; i++) {
      JSONObject j = jobs.optJSONObject(i);
      if (j == null) {
        continue;
      }
      if (i > 0) {
        separator(items);
      }
      String profit = j.isNull("profit") ? "" : " · " + TLFormat.signed(j, "profit");
      String note = j.optString("note", "");
      items.add(plain(TLFormat.dateTime(j, "at") + (note.isEmpty() ? "" : " · " + note),
        j.optString("status", "") + " · " + (j.optInt("dir", 1) > 0 ? "BUY " : "SELL ") + j.optString("symbol", "") + " " + TLFormat.num(j, "lots") + " lots" + profit));
    }
  }
}
