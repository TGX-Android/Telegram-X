package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.data.ForumTopicSelection.Action
import org.thunderdog.challegram.telegram.TdlibForumTopicManager.Key

/** Bounded synthetic transport; real groups/accounts are never contacted. */
class ForumTopicBatchTest {
  private val backend = ForumTopicTestBackend()
  private val actions = ForumTopicActions(ForumTopicStore(backend))
  private val topics = linkedMapOf(17 to topic(17), 18 to topic(18), 19 to topic(19))
  private var status: TdApi.ChatMemberStatus = TdApi.ChatMemberStatusCreator().apply { isMember = true }
  private var pinLimit = 5L
  private var groupMuted = false
  private var result: ForumTopicActions.BatchResult? = null
  private val progress = ArrayList<Int>()
  private val callback = object : ForumTopicActions.BatchCallback {
    override fun onProgress(completed: Int, total: Int) { progress.add(completed) }
    override fun onComplete(result: ForumTopicActions.BatchResult) { this@ForumTopicBatchTest.result = result }
  }
  private fun start(action: Action, ids: List<Int> = listOf(17, 18)) = actions.startBatch(100, 77, action,
    ids.map { ForumTopicActions.BatchTarget(it, "Synthetic $it") }, callback)

  private fun pump(override: (TdApi.Function<*>) -> TdApi.Object? = { null }) {
    var count = 0
    while (true) {
      val pending = backend.calls.filter { !it.answered }
      if (pending.isEmpty()) break
      assertEquals("Only one batch request in flight", 1, pending.size)
      assertTrue("Bounded batch", ++count < 500)
      val call = pending.single()
      val request = call.request
      val reply = override(request) ?: when (request) {
        is TdApi.GetChatMember -> TdApi.ChatMember().apply { status = this@ForumTopicBatchTest.status }
        is TdApi.GetForumTopic -> topics[request.forumTopicId] ?: TdApi.Error(404, "Synthetic missing topic")
        is TdApi.GetChat -> TdApi.Chat().apply { notificationSettings = TdApi.ChatNotificationSettings().apply { useDefaultMuteFor = true } }
        is TdApi.GetScopeNotificationSettings -> TdApi.ScopeNotificationSettings().apply { muteFor = if (groupMuted) 100 else 0 }
        is TdApi.GetOption -> TdApi.OptionValueInteger(pinLimit)
        is TdApi.GetForumTopics -> page(*topics.values.toTypedArray(), cursor = ForumTopicStore.Cursor())
        is TdApi.ToggleForumTopicIsPinned -> TdApi.Ok().also { topics[request.forumTopicId]!!.isPinned = request.isPinned }
        is TdApi.ToggleForumTopicIsClosed -> TdApi.Ok().also { topics[request.forumTopicId]!!.info.isClosed = request.isClosed }
        is TdApi.SetForumTopicNotificationSettings -> TdApi.Ok().also { topics[request.forumTopicId]!!.notificationSettings = request.notificationSettings }
        is TdApi.DeleteForumTopic -> TdApi.Ok().also { topics.remove(request.forumTopicId) }
        else -> error("Unexpected request ${request.javaClass.simpleName}")
      }
      call.reply(reply); backend.publish()
    }
  }

  @Test fun pinLimitStopsEntireBatchBeforeFirstWrite() {
    pinLimit = 1
    start(Action.PIN); pump()
    assertEquals(0, backend.count<TdApi.ToggleForumTopicIsPinned>())
    assertEquals(2, result!!.outcomes.size)
    assertTrue(result!!.outcomes.all { it.error?.code == ForumTopicActions.BATCH_PIN_LIMIT })
  }
  @Test fun mixedPinStateFailsPreflightWithoutWrites() {
    topics[18]!!.isPinned = true
    start(Action.PIN); pump()
    assertEquals(0, backend.count<TdApi.ToggleForumTopicIsPinned>())
    assertTrue(result!!.outcomes.none { it.successful })
  }
  @Test fun partialFailureContinuesAndRetryContainsOnlyFailedTargets() {
    start(Action.CLOSE, listOf(17, 18, 19))
    pump { if (it is TdApi.ToggleForumTopicIsClosed && it.forumTopicId == 18) TdApi.Error(400, "Synthetic denied") else null }
    assertEquals(listOf(true, false, true), result!!.outcomes.map { it.successful })
    assertEquals(listOf(0, 1, 2, 3), progress)
    val retry = result!!.failedTargets()
    assertEquals(listOf(18), retry.map { it.topicId })
    actions.startBatch(100, 77, Action.CLOSE, retry, callback); pump()
    assertEquals(listOf(17, 18, 19, 18), backend.calls.mapNotNull { (it.request as? TdApi.ToggleForumTopicIsClosed)?.forumTopicId })
    assertTrue(result!!.outcomes.single().successful)
  }
  @Test fun rightsRevocationBetweenWritesIsRechecked() {
    start(Action.CLOSE)
    pump {
      if (it is TdApi.ToggleForumTopicIsClosed) { status = TdApi.ChatMemberStatusMember(); TdApi.Ok() } else null
    }
    assertEquals(1, backend.count<TdApi.ToggleForumTopicIsClosed>())
    assertEquals(listOf(true, false), result!!.outcomes.map { it.successful })
  }
  @Test fun unmuteOverridesMutedDefaultAndKeepsUnrelatedSettings() {
    groupMuted = true
    topics.values.forEach { it.notificationSettings = TdApi.ChatNotificationSettings().apply { useDefaultMuteFor = true; soundId = 123; showPreview = true } }
    start(Action.UNMUTE); pump()
    val settings = backend.calls.mapNotNull { (it.request as? TdApi.SetForumTopicNotificationSettings)?.notificationSettings }
    assertEquals(2, settings.size)
    settings.forEach { assertFalse(it.useDefaultMuteFor); assertEquals(0, it.muteFor); assertEquals(123L, it.soundId); assertTrue(it.showPreview) }
  }
  @Test fun repeatedShortPinnedCursorIsNotAnEndOfList() {
    val pinned = topic(90).apply { isPinned = true }
    start(Action.PIN)
    pump { if (it is TdApi.GetForumTopics) page(pinned, cursor = ForumTopicStore.Cursor(1, 2, 90)) else null }
    assertEquals(0, backend.count<TdApi.ToggleForumTopicIsPinned>())
    assertEquals(2, backend.count<TdApi.GetForumTopics>())
    assertTrue(result!!.outcomes.all { it.error?.code == ForumTopicActions.BATCH_PIN_PREFIX })
  }
  @Test fun doubleStartDoesNotSendAnotherRequest() {
    assertNotNull(start(Action.CLOSE))
    assertNull(start(Action.CLOSE))
    assertEquals(1, backend.calls.size)
    pump(); assertEquals(2, backend.count<TdApi.ToggleForumTopicIsClosed>())
  }
  @Test fun uncertainDestructiveOutcomeIsNotRetryable() {
    start(Action.DELETE)
    pump { if (it is TdApi.DeleteForumTopic && it.forumTopicId == 17) TdApi.Error(408, "Synthetic uncertain outcome") else null }
    assertTrue(result!!.outcomes[0].uncertain)
    assertFalse(result!!.outcomes[0].retryable)
    assertTrue(result!!.failedTargets().isEmpty())
  }
  @Test fun cancellationPreventsFurtherRequests() {
    val batch = start(Action.CLOSE)!!
    batch.cancel()
    backend.next<TdApi.GetChatMember>().reply(TdApi.ChatMember().apply { status = this@ForumTopicBatchTest.status })
    backend.publish()
    assertEquals(1, backend.calls.size); assertNull(result)
  }
  @Test fun duplicateIdentitiesAndGeneralMixedClearAreRejectedBeforeNetwork() {
    start(Action.DELETE, listOf(17, 17))
    assertTrue(backend.calls.isEmpty())
    start(Action.CLEAR_GENERAL, listOf(1, 17))
    assertTrue(backend.calls.isEmpty())
  }
  @Test fun preflightErrorIsAttributedToItsTargetAndOthersAreExplicitlyNotSent() {
    topics.remove(18)
    start(Action.CLOSE); pump()
    assertEquals(ForumTopicActions.BATCH_PREFLIGHT_ABORTED, result!!.outcomes[0].error!!.code)
    assertEquals(404, result!!.outcomes[1].error!!.code)
    assertEquals(0, backend.count<TdApi.ToggleForumTopicIsClosed>())
  }
  @Test fun retryOfAlreadyAppliedNonDestructiveDirectionDoesNotWriteAgain() {
    start(Action.CLOSE)
    pump {
      if (it is TdApi.ToggleForumTopicIsClosed && it.forumTopicId == 17) {
        topics[17]!!.info.isClosed = true
        TdApi.Error(408, "Synthetic lost acknowledgement")
      } else null
    }
    actions.startBatch(100, 77, Action.CLOSE, result!!.failedTargets(), callback); pump()
    assertEquals(2, backend.count<TdApi.ToggleForumTopicIsClosed>())
    assertTrue(result!!.outcomes.single().successful)
  }
  @Test fun failedDeleteWhichIsNowMissingIsNotSentAgain() {
    start(Action.DELETE)
    pump { if (it is TdApi.DeleteForumTopic && it.forumTopicId == 17) TdApi.Error(400, "Synthetic denial") else null }
    topics.remove(17)
    actions.startBatch(100, 77, Action.DELETE, result!!.failedTargets(), callback); pump()
    assertEquals(2, backend.count<TdApi.DeleteForumTopic>())
    assertTrue(result!!.outcomes.single().successful)
  }

  @Test fun lastAlreadyAppliedTargetPublishesItsReadInBothDirections() {
    for (closed in listOf(true, false)) {
      val transport = ForumTopicTestBackend()
      val store = ForumTopicStore(transport)
      val runner = ForumTopicActions(store)
      fun original(id: Int) = topic(id).apply { info.isClosed = !closed }
      fun changed(id: Int) = topic(id).apply {
        info.isClosed = closed
        lastMessage!!.content = TdApi.MessageForumTopicIsClosedToggled(closed)
      }
      fun rights() {
        transport.next<TdApi.GetChatMember>().reply(TdApi.ChatMember().apply {
          status = TdApi.ChatMemberStatusCreator().apply { isMember = true }
        })
        transport.publish()
      }
      fun read(id: Int, value: TdApi.ForumTopic) {
        val call = transport.next<TdApi.GetForumTopic>()
        assertEquals(id, (call.request as TdApi.GetForumTopic).forumTopicId)
        call.reply(value)
        transport.publish()
      }
      val session = store.openList(100, "") { }
      transport.next<TdApi.GetForumTopics>().reply(page(original(17), original(18)))
      var completed: ForumTopicActions.BatchResult? = null
      runner.startBatch(100, 77, if (closed) Action.CLOSE else Action.OPEN,
        listOf(17, 18).map { ForumTopicActions.BatchTarget(it, "Synthetic $it") },
        object : ForumTopicActions.BatchCallback {
          override fun onProgress(completed: Int, total: Int) { }
          override fun onComplete(result: ForumTopicActions.BatchResult) { completed = result }
        })
      rights()
      read(17, original(17))
      read(18, original(18))
      rights()
      read(17, original(17))
      transport.next<TdApi.ToggleForumTopicIsClosed>().reply(TdApi.Ok())
      transport.publish()

      // A's reconciliation finishes before B's final validation. Do not share mutable
      // mock server objects with cached rows: that would hide a missing store merge.
      transport.advance()
      transport.next<TdApi.GetForumTopics>().reply(page(changed(17), original(18)))
      read(17, changed(17))
      rights()
      // Another client has already applied B; the runner correctly skips its write.
      read(18, changed(18))

      assertTrue(completed!!.outcomes.all { it.successful })
      assertEquals(1, transport.count<TdApi.ToggleForumTopicIsClosed>())
      assertEquals(closed, store.cachedTopic(Key(100, 18))!!.info.isClosed)
      assertEquals(closed, session.snapshot.topics.first { it.info.forumTopicId == 18 }.info.isClosed)
      assertFalse(session.snapshot.stale)
      val requests = transport.calls.size
      transport.advance(60000)
      assertEquals("No follow-up read or write is needed", requests, transport.calls.size)
      assertTrue(transport.calls.all { it.answered })
    }
  }
}
