/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 02/09/2022, 19:31.
 */

package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.chat.MessagesManager;

public class TGMessageService extends TGMessage {
  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageContactRegistered contactRegistered) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageScreenshotTaken screenshotTaken) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageProximityAlertTriggered proximityAlertTriggered) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessagePinMessage pinMessage) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatUpgradeTo upgradeToSupergroup) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageBasicGroupChatCreate basicGroupCreate) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageSupergroupChatCreate supergroupCreate) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatUpgradeFrom upgradeFromBasicGroup) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatChangeTitle changeTitle) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatChangePhoto changePhoto) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatDeletePhoto deletePhoto) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatJoinByLink joinByLink) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatJoinByRequest joinByRequest) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatAddMembers addMembers) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatDeleteMember deleteMember) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageWebsiteConnected deletePhoto) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageChatSetTtl setTtl) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageExpiredPhoto expiredPhoto) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageExpiredVideo expiredVideo) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageGameScore gameScore) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageVideoChatStarted videoChatStarted) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageVideoChatScheduled videoChatScheduled) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageVideoChatEnded videoChatEnded) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageInviteVideoChatParticipants inviteVideoChatParticipants) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessagePaymentSuccessful paymentSuccessful) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.MessageCustomServiceAction custom) {
    super(context, msg);
  }

  // Chat events

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessageDeleted messageDeleted) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessageEdited messageEdited) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessagePinned messagePinned) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventPollStopped pollStopped) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMessageUnpinned messageUnpinned) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventInvitesToggled invitesToggled) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventSignMessagesToggled signMessagesToggled) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventHasProtectedContentToggled protectedContentToggled) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventUsernameChanged usernameChanged) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventDescriptionChanged descriptionChanged) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMemberJoinedByInviteLink joinedByInviteLink) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventMemberJoinedByRequest joinedByRequest) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventInviteLinkRevoked inviteLinkRevoked) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventInviteLinkDeleted inviteLinkDeleted) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventVideoChatParticipantVolumeLevelChanged videoChatParticipantVolumeLevelChanged) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventVideoChatParticipantIsMutedToggled videoChatParticipantIsMutedToggled) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventIsAllHistoryAvailableToggled isAllHistoryAvailableToggled) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventStickerSetChanged stickerSetChanged) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventSlowModeDelayChanged slowModeDelayChanged) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventVideoChatMuteNewParticipantsToggled newParticipantsToggled) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventLinkedChatChanged linkedChatChanged) {
    super(context, msg);
  }

  public TGMessageService (MessagesManager context, TdApi.Message msg, TdApi.ChatEventLocationChanged locationChanged) {
    super(context, msg);
  }
}
