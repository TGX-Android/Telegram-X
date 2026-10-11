/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.*;

public class MiniAppChatPolicyTest {
  private static TdApi.Chat chat (TdApi.ChatType type) {
    TdApi.Chat chat = new TdApi.Chat();
    chat.type = type;
    return chat;
  }

  @Test public void secretChatsAreNeverEligible () {
    TdApi.AttachmentMenuBot bot = new TdApi.AttachmentMenuBot();
    bot.supportsSelfChat = bot.supportsBotChats = bot.supportsUserChats = bot.supportsGroupChats = bot.supportsChannelChats = true;
    TdApi.Chat secret = chat(new TdApi.ChatTypeSecret());
    assertFalse(MiniAppChatPolicy.allows(bot, secret, false));
    assertFalse(MiniAppChatPolicy.allows(new TdApi.TargetChatTypes(true, true, true, true), secret, false));
    assertFalse(MiniAppChatPolicy.allows((TdApi.TargetChatTypes) null, secret, false));
  }

  @Test public void ownBotChatRequiresItsOwnPermission () {
    TdApi.AttachmentMenuBot bot = new TdApi.AttachmentMenuBot();
    bot.botUserId = 42;
    bot.supportsBotChats = true;
    assertFalse(MiniAppChatPolicy.allows(bot, chat(new TdApi.ChatTypePrivate(42)), true));
    assertTrue(MiniAppChatPolicy.allows(bot, chat(new TdApi.ChatTypePrivate(43)), true));
    bot.supportsSelfChat = true;
    bot.supportsBotChats = false;
    assertTrue(MiniAppChatPolicy.allows(bot, chat(new TdApi.ChatTypePrivate(42)), true));
    assertFalse(MiniAppChatPolicy.allows(bot, chat(new TdApi.ChatTypePrivate(43)), true));
  }

  @Test public void channelPermissionDoesNotGrantGroups () {
    TdApi.AttachmentMenuBot bot = new TdApi.AttachmentMenuBot();
    bot.supportsChannelChats = true;
    assertTrue(MiniAppChatPolicy.allows(bot, chat(new TdApi.ChatTypeSupergroup(1, true, false)), false));
    assertFalse(MiniAppChatPolicy.allows(bot, chat(new TdApi.ChatTypeSupergroup(1, false, false)), false));
    assertFalse(MiniAppChatPolicy.allows(bot, chat(new TdApi.ChatTypeBasicGroup(1)), false));
  }

  @Test public void chosenTypesRestrictUserAndBotChatsSeparately () {
    TdApi.TargetChatTypes usersOnly = new TdApi.TargetChatTypes(true, false, false, false);
    TdApi.Chat privateChat = chat(new TdApi.ChatTypePrivate(12));
    assertTrue(MiniAppChatPolicy.allows(usersOnly, privateChat, false));
    assertFalse(MiniAppChatPolicy.allows(usersOnly, privateChat, true));
    assertFalse(MiniAppChatPolicy.allows(new TdApi.TargetChatTypes(), privateChat, false));
  }

  @Test public void missingChatCannotBeUsed () {
    assertFalse(MiniAppChatPolicy.allows(new TdApi.AttachmentMenuBot(), null, false));
    assertFalse(MiniAppChatPolicy.allows((TdApi.TargetChatTypes) null, new TdApi.Chat(), false));
  }
}
