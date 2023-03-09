/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 15/02/2018
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;

public class TdlibUtils {
  public static boolean assertChat (long chatId, @Nullable TdApi.Chat chat) {
    if (chat == null) {
      if (Config.CRASH_CHAT_NOT_FOUND) {
        throw new IllegalStateException("updateChat not received for id: " + chatId);
      }
      Log.e("updateChat not received for id: %d", chatId);
      return true;
    }
    return false;
  }

  public static boolean assertChat (long chatId, @Nullable TdApi.Chat chat, final TdApi.Update update) {
    if (chat == null) {
      if (Config.CRASH_CHAT_NOT_FOUND) {
        throw new IllegalStateException("updateChat not received for id: " + chatId + ", cannot process update " + update.getClass().getName());
      }
      Log.e("updateChat not received for id: %d, cannot process update %s", chatId, update.getClass().getName());
      return true;
    }
    return false;
  }

  /*public static boolean assertChat (long chatId, @Nullable TdApi.Chat chat) {
    if (chat == null) {
      if (Config.BETA) {
        throw new IllegalStateException("updateChat not received for id: " + chatId);
      }
      Log.w("updateChat not received for id: %d", chatId);
      return true;
    }
    return false;
  }*/
}
