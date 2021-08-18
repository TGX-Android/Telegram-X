package org.thunderdog.challegram.telegram;

import org.drinkless.td.libcore.telegram.TdApi;

public interface CallsListener {
  void onCallUpdated (TdApi.Call call);
  void onNewCallSignallingDataArrived (int callId, byte[] data);
  void onGroupCallUpdated (TdApi.GroupCall groupCall);
  void onGroupCallParticipantUpdated (int groupCallId, TdApi.GroupCallParticipant groupCallParticipant);
}
