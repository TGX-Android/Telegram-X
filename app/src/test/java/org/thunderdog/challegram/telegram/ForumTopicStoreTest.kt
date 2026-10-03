package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.telegram.TdlibForumTopicManager.Key

class ForumTopicStoreTest {
  private val backend = ForumTopicTestBackend()
  private val store = ForumTopicStore(backend, pageSize = 2)
  private val key = Key(100, 17)
  private fun open(query: String = "", chat: Long = 100) = store.openList(chat, query) { }
  private fun loaded(vararg topics: TdApi.ForumTopic): ForumTopicStore.ListSession {
    val session = open()
    backend.next<TdApi.GetForumTopics>().reply(page(*topics))
    return session
  }
  private fun ids(session: ForumTopicStore.ListSession) = session.snapshot.topics.map { it.info.forumTopicId }

  @Test fun initialLoadingAndQueryNormalization() {
    val session = open("  Alpha  Beta  ")
    val request = backend.next<TdApi.GetForumTopics>().request as TdApi.GetForumTopics
    assertEquals("Alpha  Beta", request.query)
    assertEquals(2, request.limit)
    assertEquals(0, request.offsetForumTopicId)
    assertTrue(session.snapshot.loadingInitial)
    assertFalse(session.snapshot.initialized)
    assertFalse(session.snapshot.isEmpty)
  }

  @Test fun pinsPrecedeActivityOrderIncludingLongValues() {
    val session = loaded(topic(18, order = Long.MIN_VALUE).apply { isPinned = true }, topic(19, order = Long.MAX_VALUE), topic(17, order = Long.MAX_VALUE))
    assertEquals(listOf(18, 17, 19), ids(session))
    assertFalse(session.snapshot.loadingInitial)
    assertFalse(session.snapshot.stale)
  }

  @Test fun multiplePinsKeepServerOrderAcrossPagesAndRefresh() {
    val first = topic(18, order = 1).apply { isPinned = true }
    val second = topic(19, order = Long.MAX_VALUE).apply { isPinned = true }
    val session = loaded(first)
    session.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page(first, second, topic(20)))
    assertEquals(listOf(18, 19, 20), ids(session))
    session.refresh()
    backend.next<TdApi.GetForumTopics>().reply(page(second, first, topic(20)))
    assertEquals(listOf(19, 18, 20), ids(session))
  }

  @Test fun pinUpdateReconcilesServerPositionAndUnpinRestoresActivityOrder() {
    val first = topic(18, order = 1).apply { isPinned = true }
    val second = topic(19, order = 2)
    val session = loaded(first, second, topic(20, order = 3))
    store.updateTopic(update(topic(19, order = 2).apply { isPinned = true }))
    backend.advance()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(19, order = 2).apply { isPinned = true }, first, topic(20, order = 3)))
    assertEquals(listOf(19, 18, 20), ids(session))
    store.updateTopic(update(second))
    assertEquals(listOf(18, 20, 19), ids(session))
  }

  @Test fun deletingAPinDoesNotReuseAnotherPinsRankOnNextPage() {
    val session = loaded(topic(18, order = 1).apply { isPinned = true },
      topic(19, order = 2).apply { isPinned = true }, topic(20, order = 3).apply { isPinned = true })
    store.retryTopic(Key(100, 19))
    backend.next<TdApi.GetForumTopic>().reply(TdApi.Error(404, "Synthetic missing topic"))
    session.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(21, order = 100).apply { isPinned = true }))
    assertEquals(listOf(18, 20, 21), ids(session))
  }

  @Test fun searchExcludesUnrelatedPinsWithoutLosingPaginationProgress() {
    val session = open("  bEtA  ")
    backend.next<TdApi.GetForumTopics>().reply(page(topic().apply { isPinned = true }))
    assertTrue(session.snapshot.isEmpty)
    assertFalse(session.snapshot.endReached)
    assertNull(session.snapshot.error)
    session.loadMore()
    val next = backend.next<TdApi.GetForumTopics>()
    assertEquals(17, (next.request as TdApi.GetForumTopics).offsetForumTopicId)
    next.reply(page(topic(18, name = "Alpha Beta Gamma")))
    assertEquals(listOf(18), ids(session))
    session.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page())
    assertTrue(session.snapshot.endReached)
  }

  @Test fun renamedPinStopsMatchingWithoutWaitingForSearchRefresh() {
    val session = open("Alpha")
    backend.next<TdApi.GetForumTopics>().reply(page(topic().apply { isPinned = true }))
    store.updateInfo(topic(name = "Beta").info)
    assertTrue(session.snapshot.isEmpty)
    assertTrue(session.snapshot.stale)
    assertEquals("Beta", store.cachedTopic(key)!!.info.name)
  }

  @Test fun queryFilteringAndPinnedRanksAreIndependentPerList() {
    val first = topic(18, order = 1, name = "Alpha Beta").apply { isPinned = true }
    val second = topic(19, order = 9, name = "Alpha").apply { isPinned = true }
    val all = loaded(first, second)
    val search = open("Beta")
    backend.next<TdApi.GetForumTopics>().reply(page(second, first))
    assertEquals(listOf(18), ids(search))
    assertEquals(listOf(18, 19), ids(all))
    search.setQuery("")
    assertEquals(listOf(18, 19), ids(search))
  }

  @Test fun shortPageAndApproximateCountDoNotEndPagination() {
    val session = open()
    val cursor = ForumTopicStore.Cursor(55, 66L, 17)
    backend.next<TdApi.GetForumTopics>().reply(page(topic(), total = 0, cursor = cursor))
    assertFalse(session.snapshot.endReached)
    session.loadMore()
    val call = backend.next<TdApi.GetForumTopics>()
    val request = call.request as TdApi.GetForumTopics
    assertEquals(55, request.offsetDate)
    assertEquals(66L, request.offsetMessageId)
    assertEquals(17, request.offsetForumTopicId)
    assertTrue(session.snapshot.loadingMore)
    call.reply(page())
    assertTrue(session.snapshot.endReached)
    session.loadMore()
    assertEquals(2, backend.calls.size)
  }

  @Test fun duplicatesMergeOnceAcrossPages() {
    val session = loaded(topic(17), topic(17, name = "Beta"))
    assertEquals(listOf(17), ids(session))
    assertEquals("Beta", session.snapshot.topics.single().info.name)
    session.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(17, order = 200), topic(18, order = 500)))
    assertEquals(listOf(18, 17), ids(session))
    assertNull(session.snapshot.error)
  }

  @Test fun repeatedCursorStopsAndRetryStartsFromBeginning() {
    val session = loaded(topic())
    val cursor = session.snapshot.nextCursor
    session.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(18), cursor = cursor))
    assertEquals(ForumTopicStore.PAGINATION_ERROR, session.snapshot.error!!.code)
    session.loadMore()
    assertEquals(2, backend.calls.size)
    session.retry()
    assertEquals(0, (backend.next<TdApi.GetForumTopics>().request as TdApi.GetForumTopics).offsetForumTopicId)
  }

  @Test fun cursorCycleStopsEvenWithNewRows() {
    val session = loaded(topic())
    val cursor = session.snapshot.nextCursor
    session.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(18)))
    session.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(19), cursor = cursor))
    assertEquals(ForumTopicStore.PAGINATION_ERROR, session.snapshot.error!!.code)
  }

  @Test fun advancingCursorWithoutNewRowsStops() {
    val session = loaded(topic())
    session.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(), cursor = ForumTopicStore.Cursor(999, 999, 17)))
    assertEquals(ForumTopicStore.PAGINATION_ERROR, session.snapshot.error!!.code)
  }

  @Test fun malformedEmptyPageDoesNotLoop() {
    val session = open()
    backend.next<TdApi.GetForumTopics>().reply(page(cursor = ForumTopicStore.Cursor(1, 2, 3)))
    assertEquals(ForumTopicStore.PAGINATION_ERROR, session.snapshot.error!!.code)
    assertFalse(session.snapshot.loadingInitial)
  }

  @Test fun emptyInitialPageIsInitializedAndTerminal() {
    val session = open()
    backend.next<TdApi.GetForumTopics>().reply(page())
    assertTrue(session.snapshot.isEmpty)
    assertTrue(session.snapshot.endReached)
    assertFalse(session.snapshot.stale)
  }

  @Test fun initialErrorDoesNotPretendToHaveOfflineData() {
    val session = open()
    backend.next<TdApi.GetForumTopics>().reply(TdApi.Error(500, "Synthetic network failure"))
    assertFalse(session.snapshot.initialized)
    assertFalse(session.snapshot.loadingInitial)
    assertFalse(session.snapshot.isEmpty)
    backend.advance(60000)
    assertEquals(1, backend.calls.size)
    session.retry()
    backend.next<TdApi.GetForumTopics>().reply(page())
    assertNull(session.snapshot.error)
  }

  @Test fun failedRefreshKeepsWarmRows() {
    val session = loaded(topic())
    session.refresh()
    assertTrue(session.snapshot.refreshing)
    assertEquals(listOf(17), ids(session))
    backend.next<TdApi.GetForumTopics>().reply(TdApi.Error(500, "Offline"))
    assertEquals(listOf(17), ids(session))
    assertTrue(session.snapshot.stale)
    assertFalse(session.snapshot.refreshing)
  }

  @Test fun pageRetryKeepsTheFailedCursor() {
    val session = loaded(topic())
    session.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(TdApi.Error(429, "Retry later"))
    session.retry()
    val request = backend.next<TdApi.GetForumTopics>().request as TdApi.GetForumTopics
    assertEquals(17, request.offsetForumTopicId)
    backend.next<TdApi.GetForumTopics>().reply(page(topic(18)))
    assertEquals(listOf(17, 18), ids(session))
  }

  @Test fun timeoutReleasesLoadingAndLateReplyIsIgnored() {
    val session = open()
    val old = backend.next<TdApi.GetForumTopics>()
    backend.advance(ForumTopicStore.REQUEST_TIMEOUT_MS)
    assertEquals(408, session.snapshot.error!!.code)
    assertFalse(session.snapshot.loadingInitial)
    session.retry()
    old.reply(page(topic()))
    assertNull(store.cachedTopic(key))
    backend.next<TdApi.GetForumTopics>().reply(page(topic(18)))
    assertEquals(listOf(18), ids(session))
  }

  @Test fun explicitRefreshSupersedesInflightPage() {
    val session = loaded(topic())
    session.loadMore()
    val old = backend.next<TdApi.GetForumTopics>()
    session.refresh()
    old.reply(page(topic(18)))
    assertNull(store.cachedTopic(Key(100, 18)))
    backend.next<TdApi.GetForumTopics>().reply(page(topic(19)))
    assertEquals(listOf(19), ids(session))
  }

  @Test fun sharedListCoalescesInitialAndDoesNotRefreshForEachObserver() {
    val first = open()
    val second = open()
    assertEquals(1, backend.calls.size)
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    val third = open()
    assertEquals(1, backend.calls.size)
    first.close()
    second.close()
    third.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page())
    assertTrue(third.snapshot.endReached)
  }

  @Test fun searchSwitchRejectsOldReplyAndUsesIndependentMembership() {
    val session = open("Alpha")
    val old = backend.next<TdApi.GetForumTopics>()
    session.setQuery("Beta")
    old.reply(page(topic()))
    assertNull(store.cachedTopic(key))
    backend.next<TdApi.GetForumTopics>().reply(page(topic(18, name = "Beta")))
    assertEquals("Beta", session.snapshot.key.query)
    assertEquals(listOf(18), ids(session))
  }

  @Test fun crossQueryLateReplyCannotOverwriteNewerFullValue() {
    val all = open()
    val old = backend.next<TdApi.GetForumTopics>()
    val search = open("Beta")
    backend.calls.last().reply(page(topic(name = "Beta")))
    old.reply(page(topic(name = "Alpha")))
    assertEquals("Beta", all.snapshot.topics.single().info.name)
    assertEquals("Beta", search.snapshot.topics.single().info.name)
  }

  @Test fun topicKeyIncludesChatId() {
    val first = open(chat = 100)
    backend.next<TdApi.GetForumTopics>().reply(page(topic(chat = 100, name = "Alpha")))
    val second = open(chat = 101)
    backend.next<TdApi.GetForumTopics>().reply(page(topic(chat = 101, name = "Beta")))
    assertEquals("Alpha", first.snapshot.topics.single().info.name)
    assertEquals("Beta", second.snapshot.topics.single().info.name)
  }

  @Test fun closingSessionCancelsQueuedUiCallbacksAndResponse() {
    var deliveries = 0
    val session = store.openList(100, "") { deliveries++ }
    val call = backend.next<TdApi.GetForumTopics>()
    session.close()
    call.reply(page(topic()))
    backend.publish()
    assertEquals(0, deliveries)
    assertNull(store.cachedTopic(key))
  }

  @Test fun reopeningImmediatelyExposesWarmCacheWhileRefreshing() {
    val first = loaded(topic())
    first.close()
    val next = open()
    assertEquals(listOf(17), ids(next))
    assertTrue(next.snapshot.refreshing)
    assertTrue(next.snapshot.stale)
  }

  @Test fun resetClearsCacheAndInvalidatesSessionsRequestsAndCallbacks() {
    val session = loaded(topic())
    session.loadMore()
    val old = backend.next<TdApi.GetForumTopics>()
    store.reset()
    assertTrue(session.snapshot.topics.isEmpty())
    session.refresh()
    old.reply(page(topic(18)))
    backend.advance(60000)
    assertNull(store.cachedTopic(key))
    assertNull(store.cachedTopic(Key(100, 18)))
    assertEquals(2, backend.calls.size)
    assertTrue(open().snapshot.loadingInitial)
  }

  @Test fun queuedCommandsBeforeResetCannotResurrectOldSession() {
    backend.deferOwner = true
    val session = open()
    store.reset()
    backend.execute()
    session.refresh()
    backend.execute()
    assertEquals(0, backend.calls.size)
  }

  @Test fun storesAreAccountIsolated() {
    loaded(topic())
    val other = ForumTopicStore(ForumTopicTestBackend())
    assertNull(other.cachedTopic(key))
    other.reset()
    assertNotNull(store.cachedTopic(key))
  }

  @Test fun newInfoDuringPageWinsAndDoesNotMutateOlderSnapshot() {
    val session = loaded(topic())
    val snapshot = session.snapshot
    session.refresh()
    store.updateInfo(topic(name = "Renamed").info)
    backend.next<TdApi.GetForumTopics>().reply(page(topic(name = "Old response")))
    assertEquals("Renamed", session.snapshot.topics.single().info.name)
    assertEquals("Alpha", snapshot.topics.single().info.name)
    assertTrue(session.snapshot.stale)
    backend.advance()
    assertEquals(1, backend.count<TdApi.GetForumTopic>())
  }

  @Test fun fullUpdateDuringPagePreservesNullDraftCountersAndReadMarkers() {
    val original = topic().apply { draftMessage = TdApi.DraftMessage(); unreadMentionCount = 5 }
    val session = loaded(original)
    session.refresh()
    val fresh = topic().apply {
      isPinned = true
      lastReadInboxMessageId = 222
      lastReadOutboxMessageId = 333
      unreadMentionCount = 0
      unreadReactionCount = 2
      unreadPollVoteCount = 3
      notificationSettings.muteFor = 100
    }
    store.updateTopic(update(fresh))
    backend.next<TdApi.GetForumTopics>().reply(page(original))
    val merged = session.snapshot.topics.single()
    assertTrue(merged.isPinned)
    assertNull(merged.draftMessage)
    assertEquals(222L, merged.lastReadInboxMessageId)
    assertEquals(333L, merged.lastReadOutboxMessageId)
    assertEquals(0, merged.unreadMentionCount)
    assertEquals(2, merged.unreadReactionCount)
    assertEquals(3, merged.unreadPollVoteCount)
    assertEquals(100, merged.notificationSettings.muteFor)
  }

  @Test fun updatesEchoedByListRequestDoNotCausePerRowFetches() {
    val session = open()
    val value = topic().apply { unreadMentionCount = 2 }
    store.updateInfo(value.info)
    store.updateTopic(update(value))
    backend.advance()
    assertEquals(0, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopics>().reply(page(value))
    backend.advance()
    assertEquals(1, backend.calls.size)
    assertFalse(session.snapshot.stale)
  }

  @Test fun refreshRetainsRowsWithNewerEvents() {
    val session = loaded(topic(17), topic(18))
    session.refresh()
    store.updateInfo(topic(18, name = "Renamed").info)
    backend.next<TdApi.GetForumTopics>().reply(page(topic(17)))
    assertEquals(listOf(17, 18), ids(session))
  }

  @Test fun unknownUpdatesFetchFullTopicOnceAndInsertIntoLiveList() {
    val session = loaded()
    store.updateInfo(topic().info)
    store.updateTopic(update(topic().apply { unreadMentionCount = 2 }))
    backend.advance()
    assertEquals(1, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopic>().reply(topic().apply { unreadMentionCount = 2 })
    assertEquals(listOf(17), ids(session))
    assertFalse(session.snapshot.stale)
  }

  @Test fun unknownUpdatesOutsideActiveListsDoNotFetch() {
    store.updateInfo(topic().info)
    store.updateTopic(update(topic()))
    store.onMessage(topic().lastMessage!!)
    backend.advance()
    assertEquals(0, backend.calls.size)
    assertNull(store.cachedTopic(key))
  }

  @Test fun burstsOfUnseenMessagesUseOneListRefresh() {
    val session = loaded()
    repeat(20) { store.onMessage(topic(it + 17).lastMessage!!) }
    backend.advance()
    assertEquals(2, backend.count<TdApi.GetForumTopics>())
    assertEquals(0, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    assertFalse(session.snapshot.stale)
  }

  @Test fun knownMessageEventsAreThrottledAndReconcileAuthoritativeUnreadCount() {
    val session = loaded(topic().apply { unreadCount = 3 })
    repeat(20) { store.onMessage(topic().lastMessage!!) }
    assertEquals(3, session.snapshot.topics.single().unreadCount)
    backend.advance(299)
    assertEquals(0, backend.count<TdApi.GetForumTopic>())
    backend.advance(1)
    backend.next<TdApi.GetForumTopic>().reply(topic().apply { unreadCount = 7 })
    assertEquals(7, session.snapshot.topics.single().unreadCount)
    assertEquals(1, backend.count<TdApi.GetForumTopic>())
    assertFalse(session.snapshot.stale)
  }

  @Test fun genericThreadsSavedTopicsAndScheduledMessagesAreIgnored() {
    loaded(topic())
    val message = topic().lastMessage!!
    message.topicId = TdApi.MessageTopicThread(17)
    store.onMessage(message)
    message.topicId = TdApi.MessageTopicSavedMessages(17)
    store.onMessage(message)
    message.topicId = TdApi.MessageTopicForum(17)
    message.schedulingState = TdApi.MessageSchedulingStateSendAtDate(12345, 0)
    store.onMessage(message)
    backend.advance()
    assertEquals(1, backend.calls.size)
  }

  @Test fun editsOfLastMessageFetchTopicAndUnknownDeletesRefreshList() {
    val session = loaded(topic())
    store.onMessageChanged(100, longArrayOf(topic().lastMessage!!.id, 98765))
    backend.advance()
    assertEquals(2, backend.count<TdApi.GetForumTopics>())
    assertEquals(1, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopics>().reply(page())
    backend.next<TdApi.GetForumTopic>().reply(TdApi.Error(404, "Synthetic missing topic"))
    assertTrue(ids(session).isEmpty())
    assertNull(store.cachedTopic(key))
  }

  @Test fun readStateEventsAreCoalescedIntoListRefresh() {
    loaded(topic())
    repeat(20) { store.invalidateChat(100) }
    backend.advance()
    assertEquals(2, backend.calls.size)
  }

  @Test fun searchMembershipIsReconciledByServerNotGuessedFromName() {
    val session = open("Alpha")
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    store.updateInfo(topic(name = "Beta").info)
    backend.advance()
    backend.next<TdApi.GetForumTopics>().reply(page())
    assertTrue(ids(session).isEmpty())
    assertEquals("Beta", store.cachedTopic(key)!!.info.name)
  }

  @Test fun topicRequestsHaveBoundedConcurrency() {
    loaded()
    repeat(10) { store.updateInfo(topic(it + 17).info) }
    backend.advance()
    assertEquals(ForumTopicStore.MAX_TOPIC_REQUESTS, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopic>().reply(topic(17))
    backend.advance()
    assertEquals(ForumTopicStore.MAX_TOPIC_REQUESTS + 1, backend.count<TdApi.GetForumTopic>())
  }

  @Test fun fullPageMetadataChangeRefreshesOtherSearchEvenWithoutSeparateUpdate() {
    val all = loaded(topic())
    val search = open("Alpha")
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    all.refresh()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(name = "Beta")))
    assertTrue(search.snapshot.stale)
    backend.advance()
    val request = backend.next<TdApi.GetForumTopics>()
    assertEquals("Alpha", (request.request as TdApi.GetForumTopics).query)
    request.reply(page())
    assertTrue(search.snapshot.isEmpty)
    assertFalse(search.snapshot.stale)
    assertEquals("Beta", all.snapshot.topics.single().info.name)
  }

  @Test fun topicFetchTimeoutDoesNotRetryIndefinitely() {
    var error = 0
    store.observeTopic(key) { _, result -> error = result?.code ?: 0 }
    backend.advance()
    val call = backend.next<TdApi.GetForumTopic>()
    backend.advance(60000)
    backend.publish()
    assertEquals(408, error)
    assertEquals(1, backend.calls.size)
    call.reply(topic())
    assertNull(store.cachedTopic(key))
    store.retryTopic(key)
    backend.next<TdApi.GetForumTopic>().reply(topic())
    assertNotNull(store.cachedTopic(key))
  }

  @Test fun closingTopicObserverCancelsQueuedDelivery() {
    var calls = 0
    val subscription = store.observeTopic(key) { _, _ -> calls++ }
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(topic())
    subscription.close()
    backend.publish()
    assertEquals(0, calls)
  }

  @Test fun errorDoesNotDeleteCachedTopicAndExplicitRetryRecovers() {
    val session = loaded(topic())
    store.onMessage(topic().lastMessage!!)
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(TdApi.Error(500, "Offline"))
    assertEquals(listOf(17), ids(session))
    assertEquals(500, session.snapshot.error!!.code)
    backend.advance(60000)
    assertEquals(2, backend.calls.size)
    session.retry()
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    backend.advance()
    assertNull(session.snapshot.error)
    assertEquals(0, backend.calls.count { !it.answered })
  }

  @Test fun deletionTombstoneRejectsOlderPageReply() {
    val session = loaded(topic())
    session.refresh()
    val old = backend.next<TdApi.GetForumTopics>()
    store.onMessage(topic().lastMessage!!)
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(TdApi.Error(404, "Deleted"))
    old.reply(page(topic()))
    assertNull(store.cachedTopic(key))
    assertTrue(ids(session).isEmpty())
  }

  @Test fun olderTopicReplyCannotOverwriteNewerListReply() {
    val session = loaded(topic())
    store.onMessage(topic().lastMessage!!)
    backend.advance()
    val old = backend.next<TdApi.GetForumTopic>()
    session.refresh()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(name = "New")))
    old.reply(topic(name = "Old"))
    assertEquals("New", store.cachedTopic(key)!!.info.name)
  }

  @Test fun newerInfoProtectsAgainstOlderNotFound() {
    val session = loaded(topic())
    store.onMessage(topic().lastMessage!!)
    backend.advance()
    store.updateInfo(topic(name = "Renamed").info)
    backend.next<TdApi.GetForumTopic>().reply(TdApi.Error(404, "Old not found"))
    assertEquals("Renamed", session.snapshot.topics.single().info.name)
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(topic(name = "Renamed"))
    assertFalse(session.snapshot.stale)
    assertNull(session.snapshot.error)
  }

  @Test fun outdatedTopicErrorDoesNotPoisonFreshPage() {
    val session = loaded(topic())
    store.onMessage(topic().lastMessage!!)
    backend.advance()
    val old = backend.next<TdApi.GetForumTopic>()
    session.refresh()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(name = "Fresh")))
    old.reply(TdApi.Error(500, "Old failure"))
    assertNull(session.snapshot.error)
    assertFalse(session.snapshot.stale)
  }

  @Test fun successfulOtherTopicDoesNotHideUnknownTopicFailure() {
    val session = loaded()
    store.updateInfo(topic(17).info)
    store.updateInfo(topic(18).info)
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(TdApi.Error(500, "Synthetic failure"))
    backend.next<TdApi.GetForumTopic>().reply(topic(18))
    assertEquals(500, session.snapshot.error!!.code)
    assertTrue(session.snapshot.stale)
  }

  @Test fun unknownTopicQueuedDuringFailedListStillGetsAResponse() {
    var result = false
    open()
    store.observeTopic(key) { value, _ -> result = value != null }
    backend.advance()
    assertEquals(0, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopics>().reply(TdApi.Error(500, "Synthetic list failure"))
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(topic())
    backend.publish()
    assertTrue(result)
  }

  @Test fun missingTopicIsRemovedWithoutMakingWholeListFail() {
    val session = loaded(topic())
    store.onMessage(topic().lastMessage!!)
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(TdApi.Error(404, "Not Found"))
    assertTrue(ids(session).isEmpty())
    assertNull(session.snapshot.error)
    assertFalse(session.snapshot.stale)
  }

  @Test fun newerEventDuringFullFetchTriggersExactlyOneFollowup() {
    val session = loaded(topic())
    store.onMessage(topic().lastMessage!!)
    backend.advance()
    val old = backend.next<TdApi.GetForumTopic>()
    store.onMessage(topic().lastMessage!!)
    old.reply(topic().apply { unreadCount = 1 })
    assertTrue(session.snapshot.stale)
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(topic().apply { unreadCount = 2 })
    backend.advance(60000)
    assertEquals(2, backend.count<TdApi.GetForumTopic>())
    assertEquals(2, session.snapshot.topics.single().unreadCount)
    assertFalse(session.snapshot.stale)
  }

  @Test fun sameTopicObserversShareOneRequestAndLatestUiValue() {
    val names = ArrayList<String>()
    val first = store.observeTopic(key) { value, _ -> names.add(value!!.info.name) }
    val second = store.observeTopic(key) { _, _ -> }
    backend.advance()
    assertEquals(1, backend.calls.size)
    backend.next<TdApi.GetForumTopic>().reply(topic())
    store.updateInfo(topic(name = "Renamed").info)
    backend.publish()
    assertEquals(listOf("Renamed"), names)
    first.close()
    second.close()
    backend.advance()
    assertEquals(1, backend.calls.size)
  }

  @Test fun closingObsoleteSessionDoesNotCancelNewEpochRequest() {
    val old = loaded(topic())
    store.reset()
    val fresh = open()
    old.close()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(18)))
    assertEquals(listOf(18), ids(fresh))
  }

  @Test fun reconnectRefreshesOnlyActiveStaleLists() {
    val session = loaded(topic())
    store.onConnectionRestored()
    assertEquals(1, backend.calls.size)
    session.refresh()
    backend.next<TdApi.GetForumTopics>().reply(TdApi.Error(500, "Offline"))
    store.onConnectionRestored()
    assertEquals(3, backend.calls.size)
  }

  @Test fun inactiveCachesAreBoundedButActiveRowsAreRetained() {
    val limited = ForumTopicStore(backend, maxCachedTopics = 2, maxInactiveLists = 1)
    val first = limited.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(topic(17), topic(18), topic(19)))
    assertEquals(3, first.snapshot.topics.size)
    first.close()
    assertNull(limited.cachedTopic(key))
    assertEquals(0, limited.cachedSnapshot(100, "").topics.size)
    val second = limited.openList(101, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(topic(chat = 101)))
    second.close()
    val third = limited.openList(102, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(topic(chat = 102)))
    third.close()
    assertFalse(limited.cachedSnapshot(101, "").initialized)
  }

  @Test fun snapshotRowCollectionCannotBeModified() {
    val session = loaded(topic())
    try {
      (session.snapshot.topics as MutableList).clear()
      fail("Snapshot must be read-only")
    } catch (_: UnsupportedOperationException) { }
    assertEquals(listOf(17), ids(session))
  }

  @Test fun malformedResponsesReleaseLoadingWithoutPoisoningOtherChat() {
    val session = open()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(chat = 101)))
    assertEquals(502, session.snapshot.error!!.code)
    assertFalse(session.snapshot.loadingInitial)
    assertNull(store.cachedTopic(Key(101, 17)))
  }
}
