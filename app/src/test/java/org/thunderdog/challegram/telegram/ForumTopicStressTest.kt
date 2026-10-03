package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.telegram.TdlibForumTopicManager.Key

/** Deterministic load/epoch regressions. All chat/topic/message data is synthetic. */
class ForumTopicStressTest {
  private val backend = ForumTopicTestBackend()
  private val store = ForumTopicStore(backend)

  private fun load(count: Int): ForumTopicStore.ListSession {
    val session = store.openList(100, "") { }
    (1..count).chunked(50).forEachIndexed { index, ids ->
      if (index > 0) session.loadMore()
      backend.next<TdApi.GetForumTopics>().reply(page(*ids.map { topic(it) }.toTypedArray(), total = count))
    }
    session.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page(total = count))
    backend.publish()
    assertEquals(count, session.snapshot.topics.size)
    return session
  }

  @Test fun largeKnownTopicBurstHasBoundedUiWorkAndNoListReload() {
    val session = load(1000)
    val requests = backend.count<TdApi.GetForumTopics>()
    val publications = backend.publicationCount
    val message = topic(17).lastMessage!!
    val start = System.nanoTime()
    repeat(10000) { store.onMessage(message) }
    val elapsedMs = (System.nanoTime() - start) / 1_000_000
    val queued = backend.publicationCount - publications
    println("FORUM_BURST known topics=1000 events=10000 elapsedMs=$elapsedMs uiPublications=$queued")
    assertTrue(session.snapshot.stale)
    backend.advance()
    assertEquals(requests, backend.count<TdApi.GetForumTopics>())
    assertEquals(1, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopic>().reply(topic(17).apply { unreadCount = 10000 })
    backend.publish()
    assertFalse(session.snapshot.stale)
    assertEquals(10000, session.snapshot.topics.first { it.info.forumTopicId == 17 }.unreadCount)
    assertTrue("A burst must not sort/publish the unchanged list for every message: $queued", queued <= 1)
  }

  @Test fun unknownTopicBurstDiscoversWithOneRefresh() {
    val session = load(1000)
    val requests = backend.count<TdApi.GetForumTopics>()
    val publications = backend.publicationCount
    repeat(5000) { store.onMessage(topic(2000 + it).lastMessage!!) }
    val queued = backend.publicationCount - publications
    println("FORUM_BURST unknown topics=1000 events=5000 uiPublications=$queued")
    backend.advance()
    assertEquals(requests + 1, backend.count<TdApi.GetForumTopics>())
    assertEquals(0, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopics>().reply(page(topic(2000)))
    backend.publish()
    assertEquals(listOf(2000), session.snapshot.topics.map { it.info.forumTopicId })
    assertTrue("Discovery bursts must coalesce the stale indicator: $queued", queued <= 1)
  }

  @Test fun distinctTopicBurstHasBoundedConcurrencyAndNoPageReload() {
    val session = load(500)
    val pages = backend.count<TdApi.GetForumTopics>()
    repeat(5) { (1..100).forEach { id -> store.onMessage(topic(id).lastMessage!!) } }
    var maximum = 0
    repeat(25) {
      backend.advance()
      val pending = backend.calls.filter { !it.answered && it.request is TdApi.GetForumTopic }
      maximum = maxOf(maximum, pending.size)
      pending.forEach { call -> call.reply(topic((call.request as TdApi.GetForumTopic).forumTopicId)) }
    }
    backend.publish()
    assertEquals(ForumTopicStore.MAX_TOPIC_REQUESTS, maximum)
    assertEquals(100, backend.count<TdApi.GetForumTopic>())
    assertEquals(pages, backend.count<TdApi.GetForumTopics>())
    assertEquals(500, session.snapshot.topics.size)
    assertFalse(session.snapshot.stale)
  }

  @Test fun metadataAndCountersStillPublishWhileAlreadyStale() {
    val session = load(50)
    store.onMessage(topic(17).lastMessage!!)
    store.updateInfo(topic(17, name = "Synthetic renamed").info)
    store.updateTopic(update(topic(18).apply { unreadMentionCount = 8 }))
    backend.publish()
    assertEquals("Synthetic renamed", session.snapshot.topics.first { it.info.forumTopicId == 17 }.info.name)
    assertEquals(8, session.snapshot.topics.first { it.info.forumTopicId == 18 }.unreadMentionCount)
  }

  @Test fun hundredsOfObserversShareFetchAndAllClosedObserversStaySilent() {
    var deliveries = 0
    val subscriptions = (1..500).map { store.observeTopic(Key(100, 17)) { _, _ -> deliveries++ } }
    backend.advance()
    assertEquals(1, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopic>().reply(topic())
    subscriptions.forEach { it.close() }
    backend.publish()
    store.onMessage(topic().lastMessage!!)
    backend.advance()
    assertEquals(0, deliveries)
    assertEquals(1, backend.count<TdApi.GetForumTopic>())
  }

  @Test fun repeatedAccountResetsRejectAllOldRequestsAndQueuedResults() {
    var oldDeliveries = 0
    repeat(50) {
      val session = store.openList(100, "") { if (it.topics.isNotEmpty()) oldDeliveries++ }
      val old = backend.next<TdApi.GetForumTopics>()
      store.reset()
      session.close()
      old.reply(page(topic()))
      backend.publish()
    }
    assertEquals(0, oldDeliveries)
    assertNull(store.cachedTopic(Key(100, 17)))
    val fresh = load(100)
    assertEquals(100, fresh.snapshot.topics.size)
  }
}
