package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.component.chat.MessagesManager
import org.thunderdog.challegram.data.ForumHistory
import org.thunderdog.challegram.data.ForumTopicContext
import org.thunderdog.challegram.ui.MessagesController
import tgx.td.MessageId

class ForumHistoryTest {
  private val forum = TdApi.MessageTopicForum(17)
  private fun message(id: Long = 100, topicId: TdApi.MessageTopic? = forum, chat: Long = 100, scheduled: Boolean = false) = TdApi.Message().apply {
    this.id = id
    chatId = chat
    this.topicId = topicId
    if (scheduled) schedulingState = TdApi.MessageSchedulingStateSendAtDate(123, 0)
  }
  private fun draft(text: String) = TdApi.DraftMessage(null, 123, TdApi.DraftMessageContentText(TdApi.FormattedText(text, emptyArray()), null), 0, null)
  private fun context() = ForumTopicContext(100, 17)

  @Test fun initialHistoryUsesForumApiEvenForLocalPass() {
    val request = ForumHistory.request(100, forum, 0, 0, 19, true) as TdApi.GetForumTopicHistory
    assertEquals(100L, request.chatId)
    assertEquals(17, request.forumTopicId)
    assertEquals(0L, request.fromMessageId)
    assertEquals(19, request.limit)
  }
  @Test fun olderPageUsesForumCursor() {
    val request = ForumHistory.request(100, forum, 400, 0, 50, false) as TdApi.GetForumTopicHistory
    assertEquals(400L, request.fromMessageId)
    assertEquals(0, request.offset)
  }
  @Test fun newerPageUsesNegativeOffset() {
    val request = ForumHistory.request(100, forum, 400, -30, 31, false) as TdApi.GetForumTopicHistory
    assertEquals(-30, request.offset)
    assertEquals(31, request.limit)
  }
  @Test fun anchorUsesForumHistory() {
    val request = ForumHistory.request(100, forum, 700, -19, 33, false) as TdApi.GetForumTopicHistory
    assertEquals(700L, request.fromMessageId)
    assertEquals(-19, request.offset)
    assertEquals(33, request.limit)
  }
  @Test fun generalIsAnExplicitForum() {
    val request = ForumHistory.request(100, TdApi.MessageTopicForum(1), 0, 0, 19, true) as TdApi.GetForumTopicHistory
    assertEquals(1, request.forumTopicId)
    assertFalse(ForumHistory.matches(message(), 100, TdApi.MessageTopicForum(1), false))
  }
  @Test fun wholeChatKeepsLocalHistory() {
    val request = ForumHistory.request(100, null, 99, 0, 19, true) as TdApi.GetChatHistory
    assertTrue(request.onlyLocal)
    assertEquals(99L, request.fromMessageId)
  }
  @Test fun historyFiltersBeforeAlbumMerging() {
    val own = message()
    val array = arrayOf(message(topicId = null), message(topicId = TdApi.MessageTopicForum(18)), message(chat = 101), own, message(scheduled = true), message(topicId = TdApi.MessageTopicThread(17)))
    val result = ForumHistory.filter(array, 100, forum, false)
    assertEquals(1, result.size)
    assertTrue(own === result[0])
  }
  @Test fun scheduledHistoryIsAlsoTopicScoped() {
    val own = message(scheduled = true)
    val result = ForumHistory.filter(arrayOf(message(), own, message(topicId = TdApi.MessageTopicForum(18), scheduled = true)), 100, forum, true)
    assertEquals(1, result.size)
    assertTrue(own === result[0])
  }
  @Test fun emptyHistoryIsValid() { assertEquals(0, ForumHistory.filter(emptyArray(), 100, forum, false).size) }
  @Test fun wholeChatStillIncludesTopics() { assertTrue(ForumHistory.matches(message(), 100, null, false)) }
  @Test fun liveMessagesRequireSameChatTopicAndMode() {
    assertTrue(ForumHistory.matches(message(), 100, forum, false))
    assertFalse(ForumHistory.matches(message(), 101, forum, false))
    assertFalse(ForumHistory.matches(message(), 100, TdApi.MessageTopicForum(18), false))
    assertFalse(ForumHistory.matches(message(), 100, forum, true))
  }
  @Test fun albumCannotCrossTopicChatOrScheduledMode() {
    val anchor = message().apply { mediaAlbumId = 9 }
    assertTrue(ForumHistory.sameAlbum(anchor, message(101).apply { mediaAlbumId = 9 }))
    assertFalse(ForumHistory.sameAlbum(anchor, message(topicId = TdApi.MessageTopicForum(18)).apply { mediaAlbumId = 9 }))
    assertFalse(ForumHistory.sameAlbum(anchor, message(chat = 101).apply { mediaAlbumId = 9 }))
    assertFalse(ForumHistory.sameAlbum(anchor, message(scheduled = true).apply { mediaAlbumId = 9 }))
    assertFalse(ForumHistory.sameAlbum(anchor, message()))
    assertFalse(ForumHistory.sameAlbum(message(), message()))
  }
  @Test fun albumRequestsUseForumHistory() {
    val request = ForumHistory.request(100, forum, 1000, -9, 10, true) as TdApi.GetForumTopicHistory
    assertEquals(-9, request.offset)
    assertTrue(request.limit > -request.offset)
  }
  @Test fun externalReplyDoesNotRedirectForumSend() {
    assertTrue(forum === ForumHistory.outgoingTopic(forum, TdApi.MessageTopicForum(18)))
    assertTrue(forum === ForumHistory.outgoingTopic(forum, TdApi.MessageTopicThread(18)))
    assertTrue(forum === ForumHistory.outgoingTopic(forum, null))
  }
  @Test fun wholeChatReplyStillSelectsItsTopic() { assertTrue(forum === ForumHistory.outgoingTopic(null, forum)) }
  @Test fun unknownForumHasNoDraftOrReadState() {
    val state = context()
    assertNull(state.draft())
    assertEquals(0L, state.lastReadInbox())
    assertEquals(0L, state.lastReadOutbox())
    assertEquals(0L, state.lastMessageId())
    assertTrue(state.isLoading)
    assertFalse(state.canSend(TdApi.ChatMemberStatusCreator(), true))
  }
  @Test fun emptyRemoteDraftStaysEmpty() {
    val state = context()
    state.update(topic(), null)
    assertNull(state.draft())
  }
  @Test fun remoteDraftAndReadStateComeFromThisTopic() {
    val value = topic().apply { draftMessage = draft("remote"); lastReadInboxMessageId = 71; lastReadOutboxMessageId = 72 }
    val state = context()
    state.update(value, null)
    assertTrue(value.draftMessage === state.draft())
    assertEquals(71L, state.lastReadInbox())
    assertEquals(72L, state.lastReadOutbox())
    assertEquals(value.lastMessage!!.id, state.lastMessageId())
  }
  @Test fun wrongTopicMetadataIsRejected() {
    val state = context()
    assertFalse(state.update(topic(id = 18), null))
    assertFalse(state.update(topic(chat = 101), null))
    assertNull(state.topic())
  }
  @Test fun localDraftWinsOverRemoteUpdatesAndDeletion() {
    val local = draft("local")
    val state = context()
    state.setLocalDraft(local)
    state.update(topic().apply { draftMessage = draft("remote") }, null)
    assertTrue(local === state.draft())
    state.update(null, TdApi.Error(404, "Synthetic missing topic"))
    assertTrue(local === state.draft())
    assertFalse(state.canSend(TdApi.ChatMemberStatusCreator(), true))
  }
  @Test fun explicitLocalClearDoesNotFallBackToRemote() {
    val state = context()
    state.setLocalDraft(null)
    state.update(topic().apply { draftMessage = draft("remote") }, null)
    assertTrue(state.hasLocalDraft())
    assertNull(state.draft())
  }
  @Test fun independentTopicsHaveIndependentDrafts() {
    val a = context()
    val b = ForumTopicContext(100, 18)
    a.setLocalDraft(draft("A"))
    b.update(topic(id = 18), null)
    assertNull(b.draft())
    assertNotNull(a.draft())
  }
  @Test fun lateSaveAcknowledgementDoesNotClearANewerDraft() {
    val state = context()
    val old = draft("old")
    val current = draft("new")
    state.setLocalDraft(old)
    state.setLocalDraft(current)
    state.acknowledgeDraft(old)
    assertTrue(state.hasLocalDraft())
    assertTrue(current === state.draft())
    state.acknowledgeDraft(current)
    assertFalse(state.hasLocalDraft())
  }
  @Test fun remoteClearIsAppliedWhenThereIsNoLocalEdit() {
    val state = context()
    state.update(topic().apply { draftMessage = draft("remote") }, null)
    state.update(topic(), null)
    assertNull(state.draft())
  }
  @Test fun closeAndReopenDoNotChangeDraft() {
    val state = context()
    val local = draft("keep")
    state.setLocalDraft(local)
    state.update(topic().apply { info.isClosed = true }, null)
    assertFalse(state.canSend(TdApi.ChatMemberStatusMember(), true))
    state.update(topic(), null)
    assertTrue(state.canSend(TdApi.ChatMemberStatusMember(), true))
    assertTrue(local === state.draft())
  }
  @Test fun closedTopicAllowsCreatorAndTopicManagers() {
    val state = context()
    state.update(topic().apply { info.isClosed = true }, null)
    assertTrue(state.canSend(TdApi.ChatMemberStatusCreator(), true))
    val admin = TdApi.ChatMemberStatusAdministrator().apply { rights = TdApi.ChatAdministratorRights() }
    assertFalse(state.canSend(admin, true))
    admin.rights.canManageTopics = true
    assertTrue(state.canSend(admin, true))
    state.update(topic().apply { info.isClosed = true; info.isOutgoing = true }, null)
    assertTrue(state.canSend(TdApi.ChatMemberStatusMember(), true))
  }
  @Test fun unavailableTopicIsNotMistakenForAnEmptyTopic() {
    val state = context()
    state.update(null, TdApi.Error(403, "Synthetic inaccessible topic"))
    assertFalse(state.isLoading)
    assertEquals(403, state.error()!!.code)
    assertFalse(state.canSend(TdApi.ChatMemberStatusCreator(), true))
  }
  @Test fun transientErrorCanRecover() {
    val state = context()
    state.update(topic(), TdApi.Error(500, "Synthetic timeout"))
    assertFalse(state.canSend(TdApi.ChatMemberStatusMember(), true))
    state.update(topic(), null)
    assertTrue(state.canSend(TdApi.ChatMemberStatusMember(), true))
  }
  @Test fun unreadAnchorNeverBorrowsGroupState() {
    val chat = TdApi.Chat().apply { id = 100; unreadCount = 20; lastReadInboxMessageId = 999 }
    assertFalse(MessagesManager.canGoUnread(chat, null, forum))
    assertNull(MessagesManager.getAnchorMessageId(0, chat, null, forum, MessagesManager.HIGHLIGHT_MODE_UNREAD))
    val value = topic().apply { unreadCount = 2; lastReadInboxMessageId = 71 }
    assertTrue(MessagesManager.canGoUnread(chat, null, forum, value))
    assertEquals(71L, MessagesManager.getAnchorMessageId(0, chat, null, forum, MessagesManager.HIGHLIGHT_MODE_UNREAD, value).messageId)
  }
  @Test fun entirelyUnreadTopicStartsAtFirstMessage() {
    val value = topic().apply { unreadCount = 2 }
    val chat = TdApi.Chat().apply { id = 100 }
    assertTrue(ForumTopicContext.canGoUnread(value))
    assertEquals(MessageId.MIN_VALID_ID, MessagesManager.getAnchorMessageId(0, chat, null, forum, MessagesManager.HIGHLIGHT_MODE_UNREAD, value).messageId)
  }
  @Test fun readTopicDoesNotHaveUnreadAnchor() { assertFalse(ForumTopicContext.canGoUnread(topic())) }
  @Test fun searchArgumentsRetainTopic() {
    val args = MessagesController.Arguments(null, TdApi.Chat(), forum, "synthetic", null, TdApi.SearchMessagesFilterPinned())
    assertTrue(forum === args.messageTopicId)
  }
  @Test fun anchoredSearchArgumentsRetainTopic() {
    val args = MessagesController.Arguments(null, TdApi.Chat(), forum, "synthetic", null, null, MessageId(100, 5), MessagesManager.HIGHLIGHT_MODE_NORMAL)
    assertTrue(forum === args.messageTopicId)
  }
}
