package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface ChatFiltersListener {
  default void onChatFiltersChanged(TdApi.ChatFilterInfo[] chatFilters, int mainChatListPosition) { }
}
