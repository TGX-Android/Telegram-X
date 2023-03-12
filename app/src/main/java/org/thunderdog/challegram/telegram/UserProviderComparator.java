/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 17/02/2018
 */
package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.util.UserProvider;

import java.util.Comparator;

public class UserProviderComparator implements Comparator<UserProvider> {
  private final Comparator<TdApi.User> defaultComparator;

  public UserProviderComparator (Comparator<TdApi.User> defaultComparator) {
    this.defaultComparator = defaultComparator;
  }

  @Override
  public int compare (UserProvider left, UserProvider right) {
    return left == null && right == null ? 0 : left == null ? -1 : right == null ? 1 : defaultComparator.compare(left.getTdUser(), right.getTdUser());
  }
}
