package org.thunderdog.challegram.util;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 27/12/2016
 * Author: default
 */
public interface MessageSourceProvider {
  int getSourceDate ();
  long getSourceMessageId ();
  TdApi.Message getMessage();
}
