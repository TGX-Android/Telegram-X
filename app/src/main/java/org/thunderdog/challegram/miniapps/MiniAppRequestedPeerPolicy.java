/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;

/** Local filtering complements Share*WithBot(onlyCheck), which remains authoritative. */
public final class MiniAppRequestedPeerPolicy {
  private MiniAppRequestedPeerPolicy () { }

  public static boolean user (TdApi.KeyboardButtonTypeRequestUsers button, TdApi.User user) {
    if (user == null || user.type == null || user.type instanceof TdApi.UserTypeDeleted || user.type instanceof TdApi.UserTypeUnknown) return false;
    return (!button.restrictUserIsBot || button.userIsBot == (user.type instanceof TdApi.UserTypeBot)) &&
      (!button.restrictUserIsPremium || button.userIsPremium == user.isPremium);
  }

  public static boolean chat (TdApi.KeyboardButtonTypeRequestChat button, TdApi.Chat chat, TdApi.ChatMemberStatus status, boolean forum, boolean username) {
    if (chat == null || chat.type == null) return false;
    boolean channel = chat.type instanceof TdApi.ChatTypeSupergroup && ((TdApi.ChatTypeSupergroup) chat.type).isChannel;
    if (!(chat.type instanceof TdApi.ChatTypeBasicGroup) && !(chat.type instanceof TdApi.ChatTypeSupergroup)) return false;
    return button.chatIsChannel == channel && (!button.restrictChatIsForum || button.chatIsForum == forum) &&
      (!button.restrictChatHasUsername || button.chatHasUsername == username) &&
      (!button.chatIsCreated || status instanceof TdApi.ChatMemberStatusCreator) && rights(status, button.userAdministratorRights);
  }

  public static boolean rights (TdApi.ChatMemberStatus status, TdApi.ChatAdministratorRights required) {
    if (required == null || status instanceof TdApi.ChatMemberStatusCreator) return true;
    if (!(status instanceof TdApi.ChatMemberStatusAdministrator)) return false;
    TdApi.ChatAdministratorRights actual = ((TdApi.ChatMemberStatusAdministrator) status).rights;
    if (actual == null) return false;
    return (!required.canManageChat || actual.canManageChat) && (!required.canChangeInfo || actual.canChangeInfo) &&
      (!required.canPostMessages || actual.canPostMessages) && (!required.canEditMessages || actual.canEditMessages) &&
      (!required.canDeleteMessages || actual.canDeleteMessages) && (!required.canInviteUsers || actual.canInviteUsers) &&
      (!required.canRestrictMembers || actual.canRestrictMembers) && (!required.canPinMessages || actual.canPinMessages) &&
      (!required.canManageTopics || actual.canManageTopics) && (!required.canPromoteMembers || actual.canPromoteMembers) &&
      (!required.canManageVideoChats || actual.canManageVideoChats) && (!required.canPostStories || actual.canPostStories) &&
      (!required.canEditStories || actual.canEditStories) && (!required.canDeleteStories || actual.canDeleteStories) &&
      (!required.canManageDirectMessages || actual.canManageDirectMessages) && (!required.canManageTags || actual.canManageTags) &&
      (!required.canSendWelcomeMessages || actual.canSendWelcomeMessages) && (!required.isAnonymous || actual.isAnonymous);
  }

  public static boolean member (TdApi.ChatMemberStatus status) {
    return status instanceof TdApi.ChatMemberStatusMember || status instanceof TdApi.ChatMemberStatusAdministrator ||
      status instanceof TdApi.ChatMemberStatusCreator && ((TdApi.ChatMemberStatusCreator) status).isMember ||
      status instanceof TdApi.ChatMemberStatusRestricted && ((TdApi.ChatMemberStatusRestricted) status).isMember;
  }
}
