package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.util.UserProvider;

import java.util.Comparator;

/**
 * Date: 2/17/18
 * Author: default
 */

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
