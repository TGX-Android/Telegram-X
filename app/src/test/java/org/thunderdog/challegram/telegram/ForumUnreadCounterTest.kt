package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class ForumUnreadCounterTest {
  private val backend = ForumTopicTestBackend()
  private val counter = ForumUnreadCounter(backend)
  private fun attach() = backend.advance(ForumUnreadCounter.ATTACH_DELAY_MS + ForumUnreadCounter.RECONCILE_DELAY_MS)
  private fun reply(vararg topics: TdApi.ForumTopic) {
    backend.next<TdApi.GetForumTopics>().reply(page(*topics))
    backend.advance(0)
    backend.publish()
  }
  private fun unread(id: Int = 17, chat: Long = 100, messages: Int = 25) = topic(id, chat).apply { unreadCount = messages }
  private fun load(vararg topics: TdApi.ForumTopic) { reply(*topics); if (topics.isNotEmpty()) reply() }

  @Test fun `counts topics not messages and publishes only after last page`() {
    val seen = ArrayList<Int>()
    counter.observe(100) { seen.add(it) }; attach()
    reply(unread(messages = 25))
    assertEquals(ForumUnreadCounter.UNKNOWN, counter.cachedCount(100))
    assertEquals(listOf(ForumUnreadCounter.UNKNOWN), seen)
    reply(unread(99, messages = 5), topic(50))
    assertEquals(ForumUnreadCounter.UNKNOWN, counter.cachedCount(100))
    reply()
    assertEquals(listOf(ForumUnreadCounter.UNKNOWN, 2), seen)
  }

  @Test fun `unread topic outside preview and first page is included`() {
    counter.observe(100) { }; attach()
    reply(*(1..100).map { topic(it) }.toTypedArray())
    reply(*(101..120).map { if (it == 120) unread(it) else topic(it) }.toTypedArray())
    reply()
    assertEquals(1, counter.cachedCount(100))
    assertTrue(backend.calls.all { (it.request as TdApi.GetForumTopics).query.isEmpty() })
  }

  @Test fun `list and rail share one scan and both receive remote read`() {
    val list = ArrayList<Int>(); val rail = ArrayList<Int>()
    counter.observe(100) { list.add(it) }; counter.observe(100) { rail.add(it) }; attach()
    assertEquals(1, backend.calls.size)
    val topic = unread()
    load(topic)
    counter.onTopicRead(100, 17, topic.lastMessage!!.id)
    backend.publish()
    assertEquals(0, list.last()); assertEquals(list, rail)
    backend.advance()
    load(topic.apply { unreadCount = 0; lastReadInboxMessageId = lastMessage!!.id })
    assertEquals(0, counter.cachedCount(100))
  }

  @Test fun `partial read does not clear remaining unread topic`() {
    counter.observe(100) { }; attach(); load(unread(), unread(30))
    counter.onTopicRead(100, 17, topic().lastMessage!!.id)
    assertEquals(1, counter.cachedCount(100))
    counter.onTopicRead(100, 30, 1)
    assertEquals(1, counter.cachedCount(100))
    backend.advance()
    load(topic(), unread(30).apply { lastReadInboxMessageId = 1 })
    assertEquals(1, counter.cachedCount(100))
  }

  @Test fun `old in-flight refresh cannot resurrect remotely read badge`() {
    counter.observe(100) { }; attach(); load(unread())
    counter.invalidate(100); backend.advance()
    reply(unread())
    counter.onTopicRead(100, 17, topic().lastMessage!!.id)
    reply() // The scan began before the remote event. It cannot publish its old total.
    backend.advance()
    load(topic().apply { lastReadInboxMessageId = lastMessage!!.id })
    assertEquals(0, counter.cachedCount(100))
  }

  @Test fun `initial metadata announcements do not restart pagination`() {
    counter.observe(100) { }; attach()
    counter.onTopicRead(100, 17, topic().lastMessage!!.id)
    load(unread()) // Older page is protected by the more recent read watermark.
    assertEquals(0, counter.cachedCount(100))
    backend.advance()
    assertEquals(2, backend.calls.size)
  }

  @Test fun `same read watermark from repeated metadata causes no request storm`() {
    counter.observe(100) { }; attach(); load(unread().apply { lastReadInboxMessageId = 10 })
    repeat(1000) { counter.onTopicRead(100, 17, 10) }
    backend.advance()
    assertEquals(2, backend.calls.size)
  }

  @Test fun `new message invalidation prevents optimistic zero from older last message`() {
    counter.observe(100) { }; attach(); load(unread())
    counter.invalidate(100)
    counter.onTopicRead(100, 17, topic().lastMessage!!.id)
    assertEquals(1, counter.cachedCount(100))
    backend.advance()
    load(unread().apply { lastReadInboxMessageId = lastMessage!!.id; lastMessage!!.id += 1L shl 20 })
    assertEquals(1, counter.cachedCount(100))
  }

  @Test fun `error after a partial page keeps previous complete count and does not spin`() {
    counter.observe(100) { }; attach(); load(unread())
    counter.invalidate(100); backend.advance(); reply(topic())
    backend.next<TdApi.GetForumTopics>().reply(TdApi.Error(500, "Offline"))
    val requests = backend.calls.size
    backend.advance(180000)
    assertEquals(requests, backend.calls.size)
    assertEquals(1, counter.cachedCount(100))
    counter.refreshVisible(); backend.advance(); load(topic())
    assertEquals(0, counter.cachedCount(100))
  }

  @Test fun `timeout and late response do not clear unknown count`() {
    counter.observe(100) { }; attach()
    val request = backend.next<TdApi.GetForumTopics>()
    backend.advance(180000)
    request.reply(page())
    assertEquals(1, backend.calls.size)
    assertEquals(ForumUnreadCounter.UNKNOWN, counter.cachedCount(100))
  }

  @Test fun `duplicate topics are deduplicated across progressing pages`() {
    counter.observe(100) { }; attach(); reply(unread(), topic(20)); reply(unread(), unread(30)); reply()
    assertEquals(2, counter.cachedCount(100))
  }

  @Test fun `non-progressing cursor does not manufacture a complete zero`() {
    counter.observe(100) { }; attach(); reply(topic()); reply(topic())
    backend.advance(180000)
    assertEquals(2, backend.calls.size)
    assertEquals(ForumUnreadCounter.UNKNOWN, counter.cachedCount(100))
  }

  @Test fun `foreign chat and malformed page never produce a total`() {
    counter.observe(100) { }; attach(); reply(topic(chat = 200))
    assertEquals(ForumUnreadCounter.UNKNOWN, counter.cachedCount(100))
    assertEquals(1, backend.calls.size)
  }

  @Test fun `general hidden closed and muted topics all contribute`() {
    counter.observe(100) { }; attach()
    load(unread(1).apply { info.isHidden = true }, unread(2).apply { info.isClosed = true },
      unread(3).apply { notificationSettings.muteFor = Int.MAX_VALUE })
    assertEquals(3, counter.cachedCount(100))
  }

  @Test fun `manual unread dot is preserved independently of complete topic count`() {
    assertEquals(0, ForumUnreadCounter.badgeCount(0, 25, false))
    assertEquals(2, ForumUnreadCounter.badgeCount(2, 25, false))
    assertEquals(Tdlib.CHAT_MARKED_AS_UNREAD, ForumUnreadCounter.badgeCount(0, 25, true))
    assertEquals(Tdlib.CHAT_MARKED_AS_UNREAD, ForumUnreadCounter.badgeCount(ForumUnreadCounter.UNKNOWN, 25, false))
    assertEquals(0, ForumUnreadCounter.badgeCount(ForumUnreadCounter.UNKNOWN, 0, false))
  }

  @Test fun `fast scrolling performs no requests`() {
    repeat(100) { counter.observe(100L + it) { fail("Detached observer") }.close() }
    attach()
    assertTrue(backend.calls.isEmpty())
  }

  @Test fun `detached subscription receives no queued callback or late page`() {
    val subscription = counter.observe(100) { fail("Detached observer") }; attach()
    subscription.close(); load()
    backend.publish()
    assertEquals(ForumUnreadCounter.UNKNOWN, counter.cachedCount(100))
  }

  @Test fun `reset isolates sessions and ignores old account callbacks`() {
    counter.observe(100) { fail("Old epoch") }; attach()
    val old = backend.next<TdApi.GetForumTopics>()
    counter.reset()
    counter.observe(100) { }; attach()
    old.reply(page(unread()))
    load(topic())
    assertEquals(0, counter.cachedCount(100))
  }

  @Test fun `separate accounts do not share badge values`() {
    val other = ForumUnreadCounter(backend)
    counter.observe(100) { }; other.observe(100) { }; attach()
    backend.next<TdApi.GetForumTopics>().reply(page())
    load(unread())
    assertEquals(0, counter.cachedCount(100)); assertEquals(1, other.cachedCount(100))
  }

  @Test fun `unknown root read events are reconciled while visible`() {
    counter.observe(100) { }; attach(); load(unread())
    backend.advance(ForumUnreadCounter.REFRESH_MS + ForumUnreadCounter.RECONCILE_DELAY_MS)
    load(topic()) // No UpdateForumTopic was delivered for this read.
    assertEquals(0, counter.cachedCount(100))
  }

  @Test fun `background stops pagination and foreground refreshes from first page`() {
    counter.observe(100) { }; attach()
    counter.setEnabled(false)
    reply(unread())
    backend.advance(180000)
    assertEquals(1, backend.calls.size)
    counter.setEnabled(true); backend.advance()
    assertEquals(0, (backend.next<TdApi.GetForumTopics>().request as TdApi.GetForumTopics).offsetForumTopicId)
    load(topic())
    assertEquals(0, counter.cachedCount(100))
  }

  @Test fun `one account has at most two pending page requests`() {
    repeat(20) { counter.observe(100L + it) { } }; attach()
    assertEquals(2, backend.calls.count { !it.answered })
    backend.next<TdApi.GetForumTopics>().reply(page())
    backend.advance(0)
    assertEquals(2, backend.calls.count { !it.answered })
  }

  @Test fun `message bursts coalesce instead of one request per event`() {
    counter.observe(100) { }; attach(); load(topic())
    repeat(1000) { counter.invalidate(100) }; backend.advance()
    assertEquals(3, backend.calls.size)
    load(unread())
    assertEquals(1, counter.cachedCount(100))
  }
}
