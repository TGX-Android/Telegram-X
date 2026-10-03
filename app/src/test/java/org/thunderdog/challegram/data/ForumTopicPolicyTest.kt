package org.thunderdog.challegram.data

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class ForumTopicPolicyTest {
  private fun info(outgoing: Boolean = false, general: Boolean = false) = TdApi.ForumTopicInfo().apply {
    isOutgoing = outgoing; isGeneral = general
  }
  private fun admin(manage: Boolean = false, pin: Boolean = false, delete: Boolean = false) = TdApi.ChatMemberStatusAdministrator().apply {
    rights = TdApi.ChatAdministratorRights().apply { canManageTopics = manage; canPinMessages = pin; canDeleteMessages = delete }
  }
  private fun permissions(create: Boolean = false, pin: Boolean = false) = TdApi.ChatPermissions().apply { canCreateTopics = create; canPinMessages = pin }
  private val member = TdApi.ChatMemberStatusMember()
  private val creator = TdApi.ChatMemberStatusCreator().apply { isMember = true }

  @Test fun ownerCanManageCreateEditAndDelete() {
    assertTrue(ForumTopicPolicy.canManage(creator))
    assertTrue(ForumTopicPolicy.canCreate(creator, permissions()))
    assertTrue(ForumTopicPolicy.canEdit(creator, info()))
    assertTrue(ForumTopicPolicy.canDeleteAny(creator))
  }
  @Test fun pinMessageRightDoesNotGrantTopicManagement() {
    assertFalse(ForumTopicPolicy.canManage(admin(pin = true)))
    assertFalse(ForumTopicPolicy.canEdit(admin(pin = true), info()))
    assertTrue(ForumTopicPolicy.canPinMessages(admin(pin = true), permissions()))
  }
  @Test fun manageTopicRightDoesNotGrantDeleteOrPinMessageRights() {
    assertTrue(ForumTopicPolicy.canManage(admin(manage = true)))
    assertFalse(ForumTopicPolicy.canDeleteAny(admin(manage = true)))
    assertFalse(ForumTopicPolicy.canPinMessages(admin(manage = true), permissions(pin = true)))
  }
  @Test fun deleteRightIsIndependent() {
    assertTrue(ForumTopicPolicy.canOfferDelete(admin(delete = true), info()))
    assertFalse(ForumTopicPolicy.canEdit(admin(delete = true), info()))
  }
  @Test fun membersNeedCreatePermission() {
    assertFalse(ForumTopicPolicy.canCreate(member, permissions()))
    assertTrue(ForumTopicPolicy.canCreate(member, permissions(create = true)))
    assertFalse(ForumTopicPolicy.canCreate(member, null))
  }
  @Test fun restrictedMembersNeedBothGroupAndIndividualPermission() {
    val restricted = TdApi.ChatMemberStatusRestricted().apply { isMember = true; permissions = permissions(create = true) }
    assertFalse(ForumTopicPolicy.canCreate(restricted, permissions()))
    assertTrue(ForumTopicPolicy.canCreate(restricted, permissions(create = true)))
    restricted.permissions.canCreateTopics = false
    assertFalse(ForumTopicPolicy.canCreate(restricted, permissions(create = true)))
  }
  @Test fun leftBannedUnknownAndNonmemberCreatorCannotManage() {
    val statuses = listOf(null, TdApi.ChatMemberStatusLeft(), TdApi.ChatMemberStatusBanned(),
      TdApi.ChatMemberStatusCreator(), TdApi.ChatMemberStatusRestricted().apply { permissions = permissions(true, true) })
    for (status in statuses) {
      assertFalse(ForumTopicPolicy.canManage(status))
      assertFalse(ForumTopicPolicy.canCreate(status, permissions(true, true)))
      assertFalse(ForumTopicPolicy.canEdit(status, info(outgoing = true)))
      assertFalse(ForumTopicPolicy.canOfferDelete(status, info(outgoing = true)))
      assertFalse(ForumTopicPolicy.canPinMessages(status, permissions(true, true)))
    }
  }
  @Test fun topicCreatorCanEditAndRequestServerCheckedDelete() {
    assertTrue(ForumTopicPolicy.canEdit(member, info(outgoing = true)))
    assertTrue(ForumTopicPolicy.canOfferDelete(member, info(outgoing = true)))
    assertFalse(ForumTopicPolicy.canDeleteAny(member))
    assertFalse(ForumTopicPolicy.canOfferDelete(member, info(outgoing = true, general = true)))
  }
  @Test fun anotherMembersTopicCannotBeEditedOrDeleted() {
    assertFalse(ForumTopicPolicy.canEdit(member, info()))
    assertFalse(ForumTopicPolicy.canOfferDelete(member, info()))
    assertFalse(ForumTopicPolicy.canEdit(member, null))
  }
  @Test fun roleChangeTakesEffectWithoutReusingOldPermission() {
    val status = admin(manage = true)
    assertTrue(ForumTopicPolicy.canEdit(status, info()))
    status.rights.canManageTopics = false
    assertFalse(ForumTopicPolicy.canEdit(status, info()))
  }
  @Test fun namesUseUnicodeCodePointsAndRejectBlank() {
    assertFalse(ForumTopicPolicy.validName(null))
    assertFalse(ForumTopicPolicy.validName("  "))
    assertTrue(ForumTopicPolicy.validName("A".repeat(128)))
    assertFalse(ForumTopicPolicy.validName("A".repeat(129)))
    assertTrue(ForumTopicPolicy.validName("\uD83D\uDE80".repeat(128)))
    assertFalse(ForumTopicPolicy.validName("\uD83D\uDE80".repeat(129)))
  }
  @Test fun onlySixAllowedColors() {
    assertEquals(6, ForumTopicPolicy.ICON_COLORS.toSet().size)
    ForumTopicPolicy.ICON_COLORS.forEach { assertTrue(ForumTopicPolicy.validColor(it)) }
    assertFalse(ForumTopicPolicy.validColor(0))
    assertFalse(ForumTopicPolicy.validColor(0xffffff))
  }
  @Test fun premiumMayChooseAnyIconOthersOnlyDefaultIconsOrRegular() {
    val sticker = TdApi.Sticker().apply { fullType = TdApi.StickerFullTypeCustomEmoji().apply { customEmojiId = 123 } }
    assertTrue(ForumTopicPolicy.allowedIcon(0, false, null))
    assertTrue(ForumTopicPolicy.allowedIcon(987, true, null))
    assertFalse(ForumTopicPolicy.allowedIcon(123, false, null))
    assertTrue(ForumTopicPolicy.allowedIcon(123, false, arrayOf(sticker)))
    assertFalse(ForumTopicPolicy.allowedIcon(987, false, arrayOf(sticker)))
  }
  private fun topic(id: Int, pinned: Boolean = true) = TdApi.ForumTopic().apply {
    info = info().apply { forumTopicId = id }; isPinned = pinned
  }
  @Test fun pinPrefixMustBeComplete() {
    assertFalse(ForumTopicPolicy.completePinnedPrefix(listOf(topic(17)), false))
    assertTrue(ForumTopicPolicy.completePinnedPrefix(listOf(topic(17)), true))
    assertTrue(ForumTopicPolicy.completePinnedPrefix(listOf(topic(17), topic(18, false)), false))
    assertFalse(ForumTopicPolicy.completePinnedPrefix(emptyList(), false))
  }
  @Test fun reorderPreservesAllPinsAndDoesNotMutateInput() {
    val topics = listOf(topic(17), topic(18), topic(19), topic(20, false))
    assertArrayEquals(intArrayOf(18, 17, 19), ForumTopicPolicy.movePin(topics, 18, -1))
    assertArrayEquals(intArrayOf(17, 19, 18), ForumTopicPolicy.movePin(topics, 18, 1))
    assertEquals(listOf(17, 18, 19, 20), topics.map { it.info.forumTopicId })
  }
  @Test fun reorderRejectsUnpinnedMissingInvalidDirectionAndEdges() {
    val topics = listOf(topic(17), topic(18), topic(19, false))
    assertNull(ForumTopicPolicy.movePin(topics, 17, -1))
    assertNull(ForumTopicPolicy.movePin(topics, 18, 1))
    assertNull(ForumTopicPolicy.movePin(topics, 19, -1))
    assertNull(ForumTopicPolicy.movePin(topics, 99, -1))
    assertNull(ForumTopicPolicy.movePin(topics, 18, 0))
    assertNull(ForumTopicPolicy.movePin(topics, 18, -2))
  }
  @Test fun notificationOverrideCopiesAllOtherFieldsWithoutMutatingCache() {
    val source = TdApi.ChatNotificationSettings(true, 42, false, 123, false, true, false, true, false, 456, false, true, false, true, false, true)
    val changed = ForumTopicPolicy.notifications(source, false, 3600)
    assertTrue(source.useDefaultMuteFor); assertEquals(42, source.muteFor)
    assertFalse(changed.useDefaultMuteFor); assertEquals(3600, changed.muteFor)
    for (field in TdApi.ChatNotificationSettings::class.java.fields) {
      if (!java.lang.reflect.Modifier.isStatic(field.modifiers) && field.name !in listOf("useDefaultMuteFor", "muteFor")) assertEquals(field.get(source), field.get(changed))
    }
  }
  @Test fun inheritedNotificationsClearOverrideDuration() {
    val changed = ForumTopicPolicy.notifications(TdApi.ChatNotificationSettings(), true, 3600)
    assertTrue(changed.useDefaultMuteFor); assertEquals(0, changed.muteFor)
  }
}
