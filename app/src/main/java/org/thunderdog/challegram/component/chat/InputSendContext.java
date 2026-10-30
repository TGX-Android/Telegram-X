/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.component.chat;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import tgx.td.Td;

/** Pure identity snapshot for a delayed IME send; its destination need not be the visible topic. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
final class InputSendContext {
  private final Object controller, account, arguments;
  private final long chatId;
  private final String viewTopicKey;
  private final boolean scheduled;
  final @Nullable TdApi.MessageTopic outgoingTopic;

  InputSendContext (
    Object controller,
    Object account,
    @Nullable Object arguments,
    long chatId,
    @Nullable TdApi.MessageTopic viewTopic,
    boolean scheduled,
    @Nullable TdApi.MessageTopic outgoingTopic
  ) {
    this.controller = controller;
    this.account = account;
    this.arguments = arguments;
    this.chatId = chatId;
    // Snapshot the value: TDLib topic objects are mutable, and constructors are part of identity.
    this.viewTopicKey = Td.cacheKey(viewTopic);
    this.scheduled = scheduled;
    this.outgoingTopic = outgoingTopic;
  }

  boolean matches (
    @Nullable Object controller,
    @Nullable Object account,
    @Nullable Object arguments,
    boolean destroyed,
    long chatId,
    @Nullable TdApi.MessageTopic viewTopic,
    boolean scheduled
  ) {
    return !destroyed && this.controller != null && this.controller == controller &&
      this.account != null && this.account == account && this.arguments == arguments &&
      this.chatId != 0 && this.chatId == chatId && this.scheduled == scheduled &&
      viewTopicKey.equals(Td.cacheKey(viewTopic));
  }
}