/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.thunderdog.challegram.data.article;

import java.util.Locale;

public final class ArticleListLabels {
  private ArticleListLabels () { }
  public static String label (int value, String type) {
    if (value <= 0) return "";
    if ("a".equals(type) || "A".equals(type)) {
      StringBuilder text = new StringBuilder();
      for (int number = value; number > 0; number = (number - 1) / 26) text.append((char) ('a' + (number - 1) % 26));
      String result = text.reverse().toString();
      return ("A".equals(type) ? result.toUpperCase(Locale.US) : result) + ".";
    }
    if (("i".equals(type) || "I".equals(type)) && value < 4000) {
      int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
      String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
      StringBuilder text = new StringBuilder();
      for (int i = 0; i < values.length; i++) while (value >= values[i]) { text.append(symbols[i]); value -= values[i]; }
      String result = text.toString(); return ("i".equals(type) ? result.toLowerCase(Locale.US) : result) + ".";
    }
    return value + ".";
  }
}
