package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.telegram.TdlibForumTopicManager.Key

class ForumTopicActionsTest {
  private val backend = ForumTopicTestBackend()
  private val store = ForumTopicStore(backend)
  private val actions = ForumTopicActions(store)
  private val key = Key(100, 17)
  private val ok = ForumTopicActions.Callback<TdApi.Ok> { _, _ -> }

  @Test fun allOperationsUseExactChatAndForumTopicFields() {
    actions.create(100, "Synthetic", true, TdApi.ForumTopicIcon(123, 456)) { _, _ -> }
    actions.edit(key, "Renamed", true, 987, ok)
    actions.setClosed(key, true, ok)
    actions.setGeneralHidden(100, true, ok)
    actions.setPinned(key, true, ok)
    actions.setPinnedOrder(100, intArrayOf(18, 17), ok)
    actions.delete(key, ok)
    actions.setNotifications(key, TdApi.ChatNotificationSettings(), ok)
    actions.readAllMentions(key, ok)
    actions.readAllReactions(key, ok)
    actions.readAllPollVotes(key, ok)
    actions.unpinAllMessages(key, ok)
    actions.getLink(key) { _, _ -> }
    actions.setViewAsTopics(100, true, ok)
    val expected = listOf("CreateForumTopic", "EditForumTopic", "ToggleForumTopicIsClosed", "ToggleGeneralForumTopicIsHidden",
      "ToggleForumTopicIsPinned", "SetPinnedForumTopics", "DeleteForumTopic", "SetForumTopicNotificationSettings",
      "ReadAllForumTopicMentions", "ReadAllForumTopicReactions", "ReadAllForumTopicPollVotes", "UnpinAllForumTopicMessages",
      "GetForumTopicLink", "ToggleChatViewAsTopics")
    assertEquals(expected, backend.calls.map { it.request.javaClass.simpleName })
    for (call in backend.calls) {
      val request = call.request
      assertEquals(100L, request.javaClass.getField("chatId").getLong(request))
      request.javaClass.fields.firstOrNull { it.name == "forumTopicId" }?.let { assertEquals(17, it.getInt(request)) }
    }
    val create = backend.calls[0].request as TdApi.CreateForumTopic
    assertEquals("Synthetic", create.name)
    assertTrue(create.isNameImplicit)
    assertEquals(456L, create.icon.customEmojiId)
    val edit = backend.calls[1].request as TdApi.EditForumTopic
    assertEquals("Renamed", edit.name)
    assertTrue(edit.editIconCustomEmoji)
    assertEquals(987L, edit.iconCustomEmojiId)
    assertTrue((backend.calls[2].request as TdApi.ToggleForumTopicIsClosed).isClosed)
    assertTrue((backend.calls[3].request as TdApi.ToggleGeneralForumTopicIsHidden).isHidden)
    assertTrue((backend.calls[4].request as TdApi.ToggleForumTopicIsPinned).isPinned)
    assertArrayEquals(intArrayOf(18, 17), (backend.calls[5].request as TdApi.SetPinnedForumTopics).forumTopicIds)
    assertTrue((backend.calls[13].request as TdApi.ToggleChatViewAsTopics).viewAsTopics)
  }

  @Test fun pinnedOrderDefensivelyCopiesInputArray() {
    val ids = intArrayOf(17, 18)
    actions.setPinnedOrder(100, ids, ok)
    ids[0] = 19
    assertArrayEquals(intArrayOf(17, 18), (backend.calls.single().request as TdApi.SetPinnedForumTopics).forumTopicIds)
  }

  @Test fun serverErrorsAreDeliveredOnPublisherWithoutOptimisticMutation() {
    val session = store.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    var error: TdApi.Error? = null
    actions.setClosed(key, true) { value, failure -> assertNull(value); error = failure }
    val denied = TdApi.Error(400, "Synthetic permission error")
    backend.next<TdApi.ToggleForumTopicIsClosed>().reply(denied)
    assertNull(error)
    backend.publish()
    assertSame(denied, error)
    assertFalse(session.snapshot.topics.single().info.isClosed)
    backend.advance(60000)
    assertEquals(2, backend.calls.size)
  }

  @Test fun successfulMutationReconcilesInsteadOfInventingServerState() {
    val session = store.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    var completed = false
    actions.setClosed(key, true) { value, error -> completed = value != null && error == null }
    backend.next<TdApi.ToggleForumTopicIsClosed>().reply(TdApi.Ok())
    assertFalse(session.snapshot.topics.single().info.isClosed)
    assertTrue(session.snapshot.stale)
    backend.publish()
    assertTrue(completed)
    backend.advance()
    backend.next<TdApi.GetForumTopics>().reply(page(topic().apply { info.isClosed = true }))
    backend.next<TdApi.GetForumTopic>().reply(topic().apply { info.isClosed = true })
    assertTrue(session.snapshot.topics.single().info.isClosed)
    assertFalse(session.snapshot.stale)
  }

  @Test fun mutationSuccessPreventsOlderFullReplyFromUndoingCurrentRow() {
    val session = store.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(topic(name = "Current")))
    session.refresh()
    val old = backend.next<TdApi.GetForumTopics>()
    actions.edit(key, "Renamed", false, 0, ok)
    backend.next<TdApi.EditForumTopic>().reply(TdApi.Ok())
    old.reply(page(topic(name = "Obsolete")))
    assertEquals("Current", session.snapshot.topics.single().info.name)
    assertTrue(session.snapshot.stale)
  }

  @Test fun latePreflightReadCannotUndoSuccessfulMutationOrConsumeItsInvalidation() {
    val session = store.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    store.perform(TdApi.GetForumTopic(key.chatId, key.forumTopicId), key.chatId, null,
      TdApi.ForumTopic.CONSTRUCTOR, changesState = false) { _, error -> assertNull(error) }
    val oldRead = backend.next<TdApi.GetForumTopic>()
    actions.setClosed(key, true, ok)
    store.updateInfo(topic().info.apply { isClosed = true })
    backend.next<TdApi.ToggleForumTopicIsClosed>().reply(TdApi.Ok())
    oldRead.reply(topic())
    backend.publish()
    assertTrue(store.cachedTopic(key)!!.info.isClosed)
    assertTrue(session.snapshot.stale)

    backend.advance()
    backend.next<TdApi.GetForumTopics>().reply(page(topic().apply { info.isClosed = true }))
    backend.next<TdApi.GetForumTopic>().reply(topic().apply { info.isClosed = true })
    assertTrue(session.snapshot.topics.single().info.isClosed)
    assertFalse(session.snapshot.stale)
    backend.advance(60000)
    assertEquals(1, backend.count<TdApi.ToggleForumTopicIsClosed>())
    assertEquals(2, backend.count<TdApi.GetForumTopic>())
  }

  @Test fun createReturnsInfoAndFetchesFullRow() {
    val session = store.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page())
    var created = 0
    actions.create(100, "Alpha", false, TdApi.ForumTopicIcon()) { value, error ->
      assertNull(error)
      created = value!!.forumTopicId
    }
    backend.next<TdApi.CreateForumTopic>().reply(topic().info)
    backend.publish()
    assertEquals(17, created)
    assertTrue(session.snapshot.stale)
    backend.advance()
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    backend.advance()
    assertEquals(1, session.snapshot.topics.size)
    assertEquals(0, backend.count<TdApi.GetForumTopic>())
  }

  @Test fun clearingGeneralDoesNotAssumeTopicIsDeleted() {
    val session = store.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(topic(1)))
    actions.delete(Key(100, 1), ok)
    backend.next<TdApi.DeleteForumTopic>().reply(TdApi.Ok())
    assertEquals(1, session.snapshot.topics.single().info.forumTopicId)
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(topic(1).apply { lastMessage = null })
    backend.next<TdApi.GetForumTopics>().reply(page(topic(1).apply { lastMessage = null }))
    assertEquals(1, session.snapshot.topics.single().info.forumTopicId)
    assertNull(session.snapshot.topics.single().lastMessage)
  }

  @Test fun linkLookupDoesNotInvalidateList() {
    val session = store.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    var link: String? = null
    actions.getLink(key) { value, error -> assertNull(error); link = value!!.link }
    backend.next<TdApi.GetForumTopicLink>().reply(TdApi.MessageLink("https://example.invalid/synthetic", false))
    backend.publish()
    assertEquals("https://example.invalid/synthetic", link)
    assertFalse(session.snapshot.stale)
    backend.advance(60000)
    assertEquals(2, backend.calls.size)
  }

  @Test fun unexpectedResponseHasUnifiedError() {
    var code = 0
    actions.setClosed(key, true) { value, error -> assertNull(value); code = error!!.code }
    backend.next<TdApi.ToggleForumTopicIsClosed>().reply(topic())
    backend.publish()
    assertEquals(502, code)
  }

  @Test fun timedOutMutationIsNeverAutomaticallyRetriedOrCompletedTwice() {
    val codes = ArrayList<Int>()
    actions.setClosed(key, true) { _, error -> codes.add(error?.code ?: 0) }
    val call = backend.next<TdApi.ToggleForumTopicIsClosed>()
    backend.advance(60000)
    backend.publish()
    assertEquals(listOf(408), codes)
    call.reply(TdApi.Ok())
    backend.publish()
    assertEquals(listOf(408), codes)
    assertEquals(1, backend.calls.size)
  }

  @Test fun resetInvalidatesOperationCallbackAndQueuedUiResult() {
    var count = 0
    actions.setClosed(key, true) { _, _ -> count++ }
    backend.next<TdApi.ToggleForumTopicIsClosed>().reply(TdApi.Ok())
    store.reset()
    backend.publish()
    backend.advance(60000)
    assertEquals(0, count)
    assertNull(store.cachedTopic(key))
  }

  @Test fun resetAlsoCancelsUnsentOwnerQueuedOperation() {
    backend.deferOwner = true
    actions.setClosed(key, true, ok)
    store.reset()
    backend.execute()
    assertTrue(backend.calls.isEmpty())
  }
}
