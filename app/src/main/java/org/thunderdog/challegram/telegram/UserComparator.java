package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.data.TD;

import java.util.Comparator;

/**
 * Date: 2/17/18
 * Author: default
 */

public class UserComparator implements Comparator<TdApi.User> {
  private final Tdlib tdlib;

  public UserComparator (Tdlib tdlib) {
    this.tdlib = tdlib;
  }

  @Override
  public int compare (TdApi.User left, TdApi.User right) {
    if (left == null && right == null) {
      return 0;
    }
    if (left == null) {
      return 1;
    }
    if (right == null) {
      return -1;
    }
    final int type1 = left.type.getConstructor();
    final int type2 = right.type.getConstructor();
    if (type1 != type2) {
      final boolean deleted1 = type1 == TdApi.UserTypeDeleted.CONSTRUCTOR;
      final boolean deleted2 = type2 == TdApi.UserTypeDeleted.CONSTRUCTOR;
      if (deleted1 != deleted2) {
        return deleted1 ? 1 : -1;
      }
      final boolean bot1 = type1 == TdApi.UserTypeBot.CONSTRUCTOR;
      final boolean bot2 = type2 == TdApi.UserTypeBot.CONSTRUCTOR;
      if (bot1 != bot2) {
        return bot1 ? 1 : -1;
      }
    }
    final int status1 = left.status.getConstructor();
    final int status2 = right.status.getConstructor();
    if (status1 == TdApi.UserStatusOnline.CONSTRUCTOR && status2 != TdApi.UserStatusOnline.CONSTRUCTOR) {
      return -1;
    }
    if (status2 == TdApi.UserStatusOnline.CONSTRUCTOR && status1 != TdApi.UserStatusOnline.CONSTRUCTOR) {
      return 1;
    }
    if (status1 == status2) {
      boolean needRegular = false;
      if (status1 == TdApi.UserStatusOffline.CONSTRUCTOR) {
        if (((TdApi.UserStatusOffline) left.status).wasOnline == 0 && ((TdApi.UserStatusOffline) right.status).wasOnline == 0) {
          needRegular = true;
        }
      } else {
        switch (status1) {
          case TdApi.UserStatusLastMonth.CONSTRUCTOR:
          case TdApi.UserStatusLastWeek.CONSTRUCTOR:
          case TdApi.UserStatusRecently.CONSTRUCTOR:
          case TdApi.UserStatusEmpty.CONSTRUCTOR: {
            needRegular = true;
            break;
          }
        }
      }

      if (needRegular) {
        return TD.defaultCompare(left, right);
      }
    }

    int myUserId = tdlib.myUserId();
    int x, y;
    if (left.id == myUserId) {
      x = Integer.MAX_VALUE;
      y = TD.getLastSeen(right);
    } else if (right.id == myUserId) {
      x = TD.getLastSeen(left);
      y = Integer.MAX_VALUE;
    } else {
      x = TD.getLastSeen(left);
      y = TD.getLastSeen(right);
    }

      /*if (x == y) {
        int j1 = o1.getJoinDate();
        int j2 = o2.getJoinDate();

        return j1 > j2 ? -1 : j1 < j2 ? 1 : left.id > right.id ? -1 : left.id < right.id ? 1 : 0;
      }*/

    if (x > y) {
      return -1;
    } else if (y > x) {
      return 1;
    }

    return TD.defaultCompare(left, right);
  }
}
