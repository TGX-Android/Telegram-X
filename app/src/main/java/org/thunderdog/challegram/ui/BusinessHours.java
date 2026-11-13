/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

import org.drinkless.tdlib.TdApi;

final class BusinessHours {
  static final int DAY = 1440, WEEK = 7 * DAY;
  private BusinessHours () { }

  static TdApi.BusinessOpeningHoursInterval[] parse (String text) {
    ArrayList<TdApi.BusinessOpeningHoursInterval> intervals = new ArrayList<>();
    for (String line : text.split("\n")) {
      if (line.trim().isEmpty()) {
        continue;
      }
      String[] fields = line.trim().split("\\s+");
      if (fields.length != 2 || !fields[0].matches("[1-7]")) {
        throw new IllegalArgumentException();
      }
      String[] range = fields[1].split("-", -1);
      if (range.length != 2) {
        throw new IllegalArgumentException();
      }
      int day = Integer.parseInt(fields[0]) - 1;
      int start = parseTime(range[0], false);
      int end = parseTime(range[1], true);
      if (start == end || intervals.size() >= 28) {
        throw new IllegalArgumentException();
      }
      intervals.add(new TdApi.BusinessOpeningHoursInterval(day * DAY + start, day * DAY + end + (end < start ? DAY : 0)));
    }
    intervals.sort(Comparator.comparingInt(interval -> interval.startMinute));
    for (int i = 0; i < intervals.size(); i++) {
      TdApi.BusinessOpeningHoursInterval current = intervals.get(i);
      int nextStart = i + 1 < intervals.size() ? intervals.get(i + 1).startMinute :
        intervals.get(0).startMinute + WEEK;
      if (current.endMinute > nextStart) {
        throw new IllegalArgumentException();
      }
    }
    return intervals.toArray(new TdApi.BusinessOpeningHoursInterval[0]);
  }

  private static int parseTime (String input, boolean end) {
    if (!input.matches("[0-2][0-9]:[0-5][0-9]")) {
      throw new IllegalArgumentException();
    }
    int hours = Integer.parseInt(input.substring(0, 2));
    int minutes = Integer.parseInt(input.substring(3));
    if (hours > 23 && !(end && hours == 24 && minutes == 0)) {
      throw new IllegalArgumentException();
    }
    return hours * 60 + minutes;
  }

  private static String time (int minute) {
    return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
  }

  private static void appendRange (StringBuilder result, int day, int start, int end) {
    if (result.length() > 0) {
      result.append('\n');
    }
    result.append(day % 7 + 1).append(' ').append(time(start)).append('-').append(time(end));
  }

  static String format (TdApi.BusinessOpeningHours hours) {
    if (hours == null) {
      return "";
    }
    StringBuilder result = new StringBuilder();
    for (TdApi.BusinessOpeningHoursInterval interval : hours.openingHours) {
      int start = interval.startMinute;
      int end = interval.endMinute;
      int day = start / DAY;
      int endOfDay = (day + 1) * DAY;
      if (end <= endOfDay + start % DAY && end - start < DAY) {
        appendRange(result, day, start % DAY, end == endOfDay ? DAY : end % DAY);
      } else {
        while (start < end) {
          day = start / DAY;
          int to = Math.min(end, (day + 1) * DAY);
          appendRange(result, day, start - day * DAY, to - day * DAY);
          start = to;
        }
      }
    }
    return result.toString();
  }
}
