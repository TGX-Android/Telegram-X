/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import static org.junit.Assert.*;

public class MiniAppRequestedPeerPolicyTest {
  @Test public void botAndPremiumRestrictionsAreIndependent () {
    TdApi.User user = new TdApi.User(); user.type = new TdApi.UserTypeRegular();
    TdApi.KeyboardButtonTypeRequestUsers request = new TdApi.KeyboardButtonTypeRequestUsers();
    request.restrictUserIsBot = true; request.userIsBot = false;
    request.restrictUserIsPremium = true; request.userIsPremium = true;
    assertFalse(MiniAppRequestedPeerPolicy.user(request, user));
    user.isPremium = true; assertTrue(MiniAppRequestedPeerPolicy.user(request, user));
    user.type = new TdApi.UserTypeBot(); assertFalse(MiniAppRequestedPeerPolicy.user(request, user));
    request.restrictUserIsBot = false; assertTrue(MiniAppRequestedPeerPolicy.user(request, user));
    user.type = new TdApi.UserTypeDeleted(); assertFalse(MiniAppRequestedPeerPolicy.user(request, user));
  }
  @Test public void requestedGroupPropertiesDoNotAdmitPrivateOrSecretChats () {
    TdApi.KeyboardButtonTypeRequestChat request = new TdApi.KeyboardButtonTypeRequestChat();
    TdApi.Chat chat = new TdApi.Chat();
    for (TdApi.ChatType type : new TdApi.ChatType[] {new TdApi.ChatTypePrivate(1), new TdApi.ChatTypeSecret(), new TdApi.ChatTypeSupergroup(1, true)}) {
      chat.type = type; assertFalse(MiniAppRequestedPeerPolicy.chat(request, chat, null, false, false));
    }
    chat.type = new TdApi.ChatTypeSupergroup(1, false); request.restrictChatIsForum = request.chatIsForum = true;
    request.restrictChatHasUsername = true; request.chatHasUsername = false;
    assertFalse(MiniAppRequestedPeerPolicy.chat(request, chat, null, false, false));
    assertFalse(MiniAppRequestedPeerPolicy.chat(request, chat, null, true, true));
    assertTrue(MiniAppRequestedPeerPolicy.chat(request, chat, null, true, false));
    request.chatIsCreated = true; assertFalse(MiniAppRequestedPeerPolicy.chat(request, chat, new TdApi.ChatMemberStatusAdministrator(), true, false));
    assertTrue(MiniAppRequestedPeerPolicy.chat(request, chat, new TdApi.ChatMemberStatusCreator(), true, false));
  }
  @Test public void everyAdministratorRightIsRequiredIndividually () throws Exception {
    TdApi.ChatMemberStatusAdministrator admin = new TdApi.ChatMemberStatusAdministrator(); admin.rights = new TdApi.ChatAdministratorRights();
    int checked = 0;
    for (java.lang.reflect.Field field : TdApi.ChatAdministratorRights.class.getFields()) {
      if (field.getType() != boolean.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
      TdApi.ChatAdministratorRights required = new TdApi.ChatAdministratorRights(); field.setBoolean(required, true);
      assertFalse(field.getName(), MiniAppRequestedPeerPolicy.rights(admin, required));
      field.setBoolean(admin.rights, true); assertTrue(field.getName(), MiniAppRequestedPeerPolicy.rights(admin, required));
      field.setBoolean(admin.rights, false); checked++;
    }
    assertEquals(18, checked);
  }
  @Test public void creatorCanSatisfyRightsButNonMemberCannotSatisfyMembership () {
    assertTrue(MiniAppRequestedPeerPolicy.rights(new TdApi.ChatMemberStatusCreator(), new TdApi.ChatAdministratorRights()));
    assertFalse(MiniAppRequestedPeerPolicy.rights(new TdApi.ChatMemberStatusMember(), new TdApi.ChatAdministratorRights()));
    assertFalse(MiniAppRequestedPeerPolicy.member(null));
    assertFalse(MiniAppRequestedPeerPolicy.member(new TdApi.ChatMemberStatusLeft()));
    TdApi.ChatMemberStatusRestricted restricted = new TdApi.ChatMemberStatusRestricted();
    assertFalse(MiniAppRequestedPeerPolicy.member(restricted)); restricted.isMember = true; assertTrue(MiniAppRequestedPeerPolicy.member(restricted));
  }
}
