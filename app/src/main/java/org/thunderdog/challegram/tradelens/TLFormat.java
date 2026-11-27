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
package org.thunderdog.challegram.tradelens;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

/** Small helpers for showing values from the Connect API. */
public final class TLFormat {
  private TLFormat () { }

  /** A date from either epoch seconds or a text value, or "" when absent. */
  public static String date (@Nullable JSONObject o, String key) {
    if (o == null || o.isNull(key) || !o.has(key)) {
      return "";
    }
    Object v = o.opt(key);
    if (v instanceof Number) {
      long n = ((Number) v).longValue();
      if (n <= 0) {
        return "";
      }
      if (n < 100000000000L) {
        n *= 1000L;
      }
      return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(n));
    }
    String text = String.valueOf(v);
    return text.length() > 10 ? text.substring(0, 10) : text;
  }

  public static String num (@Nullable JSONObject o, String key) {
    if (o == null || o.isNull(key) || !o.has(key)) {
      return "";
    }
    double d = o.optDouble(key, Double.NaN);
    if (Double.isNaN(d)) {
      return o.optString(key, "");
    }
    return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
  }

  /** Date and time from epoch seconds, or "" when absent. */
  public static String dateTime (@Nullable JSONObject o, String key) {
    if (o == null || o.isNull(key) || !o.has(key)) {
      return "";
    }
    long n = o.optLong(key, 0);
    if (n <= 0) {
      return "";
    }
    if (n < 100000000000L) {
      n *= 1000L;
    }
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(n));
  }

  /** A money amount with two decimals, or "" when absent. */
  public static String money (@Nullable JSONObject o, String key) {
    if (o == null || o.isNull(key) || !o.has(key)) {
      return "";
    }
    double d = o.optDouble(key, Double.NaN);
    return Double.isNaN(d) ? "" : String.format(java.util.Locale.US, "%.2f", d);
  }

  public static String signed (@Nullable JSONObject o, String key) {
    String m = money(o, key);
    return m.isEmpty() ? "" : (o.optDouble(key, 0) > 0 ? "+" : "") + m;
  }

  public static String ago (long seconds) {
    if (seconds < 90) {
      return seconds + " s ago";
    }
    if (seconds < 5400) {
      return (seconds / 60) + " min ago";
    }
    return (seconds / 3600) + " h ago";
  }
}
