package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;

public interface TdlibEntitySpan {
  void setTextEntityType (TdApi.TextEntityType type);
  TdApi.TextEntityType getTextEntityType ();
}
