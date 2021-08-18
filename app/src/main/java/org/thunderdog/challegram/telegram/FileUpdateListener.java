package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 2/15/18
 * Author: default
 */
public interface FileUpdateListener {
  void onUpdateFile (TdApi.UpdateFile updateFile);
}
