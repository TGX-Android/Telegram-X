/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;

/** Server-declared peer types are applied to both the picker and the final selection. */
public final class MiniAppChatPolicy {
  private MiniAppChatPolicy () { }

  public static boolean allows (TdApi.AttachmentMenuBot bot, TdApi.Chat chat, boolean isBot) {
    if (chat == null || chat.type == null) return false;
    if (chat.type instanceof TdApi.ChatTypePrivate) {
      if (((TdApi.ChatTypePrivate) chat.type).userId == bot.botUserId) return bot.supportsSelfChat;
      return isBot ? bot.supportsBotChats : bot.supportsUserChats;
    }
    if (chat.type instanceof TdApi.ChatTypeBasicGroup) return bot.supportsGroupChats;
    if (chat.type instanceof TdApi.ChatTypeSupergroup) return ((TdApi.ChatTypeSupergroup) chat.type).isChannel ? bot.supportsChannelChats : bot.supportsGroupChats;
    return false;
  }

  public static boolean allows (TdApi.TargetChatTypes types, TdApi.Chat chat, boolean isBot) {
    if (chat == null || chat.type == null || chat.type instanceof TdApi.ChatTypeSecret) return false;
    if (types == null) return true;
    if (chat.type instanceof TdApi.ChatTypePrivate) return isBot ? types.allowBotChats : types.allowUserChats;
    if (chat.type instanceof TdApi.ChatTypeBasicGroup) return types.allowGroupChats;
    if (chat.type instanceof TdApi.ChatTypeSupergroup) return ((TdApi.ChatTypeSupergroup) chat.type).isChannel ? types.allowChannelChats : types.allowGroupChats;
    return false;
  }
}
