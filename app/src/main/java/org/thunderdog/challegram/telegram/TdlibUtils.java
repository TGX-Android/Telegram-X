package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.config.Config;

/**
 * Date: 2/15/18
 * Author: default
 */

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
