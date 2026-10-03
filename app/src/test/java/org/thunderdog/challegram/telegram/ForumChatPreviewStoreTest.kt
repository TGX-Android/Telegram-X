package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class ForumChatPreviewStoreTest {
  private val backend = ForumTopicTestBackend()
  private val store = ForumTopicStore(backend)
  private fun attach() { backend.advance(ForumTopicStore.PREVIEW_ATTACH_DELAY_MS); backend.publish() }
  private fun reply() { backend.next<TdApi.GetForumTopics>().reply(page(topic())); backend.publish() }

  @Test fun `rapid scroll starts no network request or callback after detach`() {
    var count = 0
    repeat(100) { store.openPreview(100L + it) { count++ }.close() }
    attach()
    assertTrue(backend.calls.isEmpty())
    assertEquals(0, count)
  }

  @Test fun `two visible representations share first page and do not paginate`() {
    store.openPreview(100) { }
    store.openPreview(100) { }
    attach()
    assertEquals(1, backend.count<TdApi.GetForumTopics>())
    reply()
    backend.advance(1000)
    assertEquals(1, backend.count<TdApi.GetForumTopics>())
    assertEquals(0, backend.count<TdApi.GetForumTopic>())
  }

  @Test fun `quick reattach uses clean bounded cache without refresh storm`() {
    val session = store.openPreview(100) { }
    attach(); reply(); session.close()
    repeat(10) { val next = store.openPreview(100) { }; attach(); next.close() }
    assertEquals(1, backend.count<TdApi.GetForumTopics>())
  }

  @Test fun `expired preview is refreshed on next appearance`() {
    val session = store.openPreview(100) { }
    attach(); reply(); session.close()
    backend.advance(ForumTopicStore.PREVIEW_CACHE_MS + 1)
    store.openPreview(100) { }; attach()
    assertEquals(2, backend.count<TdApi.GetForumTopics>())
  }

  @Test fun `offscreen late reply cannot refill abandoned row`() {
    var last: ForumTopicStore.Snapshot? = null
    val session = store.openPreview(100) { last = it }
    attach()
    session.close()
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    backend.publish()
    assertFalse(last!!.initialized)
    assertTrue(store.cachedSnapshot(100, "").topics.isEmpty())
  }

  @Test fun `preview is bounded and full list refresh cannot skip trimmed rows`() {
    store.openPreview(100) { }; attach()
    val values = (1..50).map { topic(it).apply { lastMessage!!.date = it } }.toTypedArray()
    backend.next<TdApi.GetForumTopics>().reply(page(*values, total = 500))
    backend.publish()
    assertEquals(ForumTopicStore.MAX_PREVIEW_TOPICS, store.cachedSnapshot(100, "").topics.size)
    store.openList(100, "") { }
    val request = backend.next<TdApi.GetForumTopics>()
    assertEquals(0, (request.request as TdApi.GetForumTopics).offsetForumTopicId)
    request.reply(page(*values, total = 500)); backend.publish()
    assertEquals(50, store.cachedSnapshot(100, "").topics.size)
  }

  @Test fun `unseen topic burst coalesces to one refresh`() {
    store.openPreview(100) { }; attach(); reply()
    repeat(1000) { store.onMessage(topic(99).lastMessage!!) }
    backend.advance()
    assertEquals(2, backend.count<TdApi.GetForumTopics>())
    assertEquals(0, backend.count<TdApi.GetForumTopic>())
  }

  @Test fun `known topic burst uses bounded reconciliation`() {
    store.openPreview(100) { }; attach(); reply()
    repeat(1000) { store.onMessage(topic().lastMessage!!) }
    backend.advance()
    assertEquals(1, backend.count<TdApi.GetForumTopic>())
    assertEquals(1, backend.count<TdApi.GetForumTopics>())
  }

  @Test fun `offline does not spin and reconnect refreshes visible projection`() {
    store.openPreview(100) { }; attach()
    backend.next<TdApi.GetForumTopics>().reply(TdApi.Error(500, "Offline"))
    backend.advance(30000)
    assertEquals(1, backend.count<TdApi.GetForumTopics>())
    store.onConnectionRestored()
    assertEquals(2, backend.count<TdApi.GetForumTopics>())
  }

  @Test fun `cleanup cancels delayed attach and pending deliveries`() {
    var deliveries = 0
    store.openPreview(100) { deliveries++ }
    store.reset(); attach()
    assertTrue(backend.calls.isEmpty())
    assertEquals(0, deliveries)
  }

  @Test fun `accounts own independent summary records`() {
    val other = ForumTopicStore(backend)
    store.openPreview(100) { }; other.openPreview(100) { }; attach()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(name = "Account A")))
    backend.next<TdApi.GetForumTopics>().reply(page(topic(name = "Account B")))
    assertEquals("Account A", store.cachedSnapshot(100, "").topics.single().info.name)
    assertEquals("Account B", other.cachedSnapshot(100, "").topics.single().info.name)
  }

  @Test fun `draft in cached older topic enters visible bounded summary`() {
    store.openPreview(100) { }; attach()
    val values = (1..50).map { topic(it).apply { lastMessage!!.date = it } }.toTypedArray()
    backend.next<TdApi.GetForumTopics>().reply(page(*values))
    assertFalse(store.cachedSnapshot(100, "").topics.any { it.info.forumTopicId == 1 })
    val old = values.first().apply {
      draftMessage = TdApi.DraftMessage(null, 1000, TdApi.DraftMessageContentText(TdApi.FormattedText("Synthetic draft", emptyArray()), null), 0, null)
    }
    store.updateTopic(update(old)); backend.publish()
    assertEquals(ForumTopicStore.MAX_PREVIEW_TOPICS, store.cachedSnapshot(100, "").topics.size)
    assertTrue(store.cachedSnapshot(100, "").topics.any { it.info.forumTopicId == 1 && it.draftMessage != null })
  }

  @Test fun `rename and deletion refresh visible labels from same store`() {
    store.openPreview(100) { }; attach(); reply()
    store.updateInfo(topic(name = "Renamed").info)
    backend.publish()
    assertEquals("Renamed", store.cachedSnapshot(100, "").topics.single().info.name)
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(TdApi.Error(404, "Deleted"))
    backend.publish()
    assertTrue(store.cachedSnapshot(100, "").topics.isEmpty())
  }

  @Test fun `detached leading metadata hydration is cancelled with preview`() {
    val list = store.openPreview(100) { }
    val leader = store.observeTopic(TdlibForumTopicManager.Key(100, 17)) { _, _ -> }
    list.close(); leader.close()
    backend.advance(1000)
    assertTrue(backend.calls.isEmpty())
  }
}
