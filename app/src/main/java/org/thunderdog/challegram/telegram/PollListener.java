package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface PollListener {
  void onUpdatePoll (TdApi.Poll updatedPoll);
}
