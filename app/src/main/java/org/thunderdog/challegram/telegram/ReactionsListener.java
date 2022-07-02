package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface ReactionsListener {
  default void onSupportedReactionsUpdated(TdApi.Reaction[] supportedReactions) { }
}
