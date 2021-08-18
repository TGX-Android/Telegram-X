package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;

/**
 * Date: 25/03/2019
 * Author: default
 */
public interface TdlibProvider {
  int accountId ();
  @NonNull Tdlib tdlib ();
}
