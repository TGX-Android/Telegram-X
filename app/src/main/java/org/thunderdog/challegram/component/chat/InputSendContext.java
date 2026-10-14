package org.thunderdog.challegram.component.chat;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import tgx.td.Td;

/** Pure identity snapshot for a delayed IME send; its destination need not be the visible topic. */
final class InputSendContext {
  private final Object controller, account, arguments;
  private final long chatId;
  private final String viewTopicKey;
  private final boolean scheduled;
  final @Nullable TdApi.MessageTopic outgoingTopic;

  InputSendContext (Object controller, Object account, @Nullable Object arguments,
                    long chatId, @Nullable TdApi.MessageTopic viewTopic, boolean scheduled,
                    @Nullable TdApi.MessageTopic outgoingTopic) {
    this.controller = controller;
    this.account = account;
    this.arguments = arguments;
    this.chatId = chatId;
    // Snapshot the value: TDLib topic objects are mutable, and constructors are part of identity.
    this.viewTopicKey = Td.cacheKey(viewTopic);
    this.scheduled = scheduled;
    this.outgoingTopic = outgoingTopic;
  }

  boolean matches (@Nullable Object controller, @Nullable Object account, @Nullable Object arguments,
                   boolean destroyed, long chatId, @Nullable TdApi.MessageTopic viewTopic, boolean scheduled) {
    return !destroyed && this.controller != null && this.controller == controller &&
      this.account != null && this.account == account && this.arguments == arguments &&
      this.chatId != 0 && this.chatId == chatId && this.scheduled == scheduled &&
      viewTopicKey.equals(Td.cacheKey(viewTopic));
  }
}
