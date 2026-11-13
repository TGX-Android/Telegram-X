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
package org.thunderdog.challegram.data;

import java.util.TreeSet;

public final class AutosaveVideoSize {
  private AutosaveVideoSize () { }

  public static final long MIN = 512L * 1024;
  public static final long MAX = 4000L * 1024 * 1024;

  public static boolean isValid (long size) {
    return size >= MIN && size <= MAX;
  }

  public static long[] options (long current) {
    TreeSet<Long> values = new TreeSet<>();
    values.add(MIN);
    for (int mb : new int[] {1, 5, 10, 50, 100, 500, 1000, 2000, 4000}) {
      values.add(mb * 1024L * 1024);
    }
    // Do not round a size previously configured in another client.
    if (isValid(current)) {
      values.add(current);
    }
    long[] result = new long[values.size()];
    int index = 0;
    for (long value : values) {
      result[index++] = value;
    }
    return result;
  }
}
