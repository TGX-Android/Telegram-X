package org.thunderdog.challegram.util;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 5/28/17
 * Author: default
 */

public interface UserProvider {
  TdApi.User getTdUser ();
}
