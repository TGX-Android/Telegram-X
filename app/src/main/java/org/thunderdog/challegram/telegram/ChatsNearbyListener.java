package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 2019-12-21
 * Author: default
 */
public interface ChatsNearbyListener {
  void onUsersNearbyUpdated (TdApi.ChatNearby[] usersNearby);
}
