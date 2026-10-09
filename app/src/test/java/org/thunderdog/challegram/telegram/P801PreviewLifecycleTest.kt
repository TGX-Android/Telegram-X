package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.data.ForumChatPreview

/** Synthetic P8-01 regressions; all requests and time are owned by the fake backend. */
class P801PreviewLifecycleTest {
  private val backend = ForumTopicTestBackend()
  private val store = ForumTopicStore(backend)

  private fun attachPreview(): ForumTopicStore.ListSession {
    val session = store.openPreview(100) { }
    backend.advance(ForumTopicStore.PREVIEW_ATTACH_DELAY_MS)
    backend.publish()
    return session
  }

  private fun values(first: Int, last: Int) = (first..last).map {
    topic(it).apply { lastMessage!!.date = it }
  }.toTypedArray()

  @Test fun `draft update in an uncached topic hydrates the visible preview`() {
    val preview = attachPreview()
    val recent = topic()
    backend.next<TdApi.GetForumTopics>().reply(page(recent))
    backend.publish()
    val drafted = topic(99).apply {
      draftMessage = TdApi.DraftMessage(null, 1000,
        TdApi.DraftMessageContentText(TdApi.FormattedText("Synthetic remote draft", emptyArray()), null), 0, null)
    }

    store.updateInfo(drafted.info)
    store.updateTopic(update(drafted))
    backend.advance()
    val requests = backend.calls.filter { !it.answered }
    assertTrue("An uncached draft must trigger bounded hydration for a visible preview", requests.isNotEmpty())
    requests.forEach { call ->
      when (call.request) {
        is TdApi.GetForumTopic -> call.reply(drafted)
        is TdApi.GetForumTopics -> call.reply(page(recent, drafted))
        else -> fail("Unexpected request: ${call.request.javaClass.simpleName}")
      }
    }
    backend.publish()
    assertEquals(99, ForumChatPreview.latestDraft(100, preview.snapshot.topics)?.info?.forumTopicId)
  }

  @Test fun `an existing draft survives newer messages in more than sixteen other topics`() {
    val preview = attachPreview()
    val drafted = topic(1).apply {
      lastMessage!!.date = 1
      draftMessage = TdApi.DraftMessage(null, 2,
        TdApi.DraftMessageContentText(TdApi.FormattedText("Synthetic pending draft", emptyArray()), null), 0, null)
    }
    backend.next<TdApi.GetForumTopics>().reply(page(drafted, *values(3, 30)))
    backend.publish()

    assertTrue(preview.snapshot.topics.size <= ForumTopicStore.MAX_PREVIEW_TOPICS)
    assertEquals("New message activity must not evict the draft that should replace the chat preview",
      1, ForumChatPreview.latestDraft(100, preview.snapshot.topics)?.info?.forumTopicId)
  }

  @Test fun `closing the full list publishes the trimmed snapshot to the remaining preview`() {
    val preview = attachPreview()
    backend.next<TdApi.GetForumTopics>().reply(page(*values(1, 50), total = 100))
    val full = store.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(*values(1, 50), total = 100))
    backend.publish()
    assertEquals(50, preview.snapshot.topics.size)

    full.close()
    backend.publish()
    assertEquals(ForumTopicStore.MAX_PREVIEW_TOPICS, store.cachedSnapshot(100, "").topics.size)
    assertEquals("The surviving preview must release its full-list snapshot",
      ForumTopicStore.MAX_PREVIEW_TOPICS, preview.snapshot.topics.size)
  }

  @Test fun `a preview attached to a warm full list is bounded even when refresh fails`() {
    val full = store.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(*values(1, 50), total = 100))
    full.loadMore()
    backend.next<TdApi.GetForumTopics>().reply(page(*values(51, 100), total = 100))
    full.close()

    val preview = attachPreview()
    backend.next<TdApi.GetForumTopics>().reply(TdApi.Error(500, "Synthetic offline"))
    backend.publish()
    assertTrue("Warm full-list membership must not become an unbounded active preview cache",
      store.cachedSnapshot(100, "").topics.size <= ForumTopicStore.MAX_PREVIEW_TOPICS)
    assertTrue(preview.snapshot.topics.size <= ForumTopicStore.MAX_PREVIEW_TOPICS)
  }

  @Test fun `reopening a truncated full list supersedes an in-flight next page`() {
    attachPreview()
    backend.next<TdApi.GetForumTopics>().reply(page(*values(1, 50), total = 100))
    val full = store.openList(100, "") { }
    backend.next<TdApi.GetForumTopics>().reply(page(*values(1, 50), total = 100))
    full.loadMore()
    val oldMore = backend.next<TdApi.GetForumTopics>()
    full.close()
    assertEquals(ForumTopicStore.MAX_PREVIEW_TOPICS, store.cachedSnapshot(100, "").topics.size)

    val callsBeforeReopen = backend.count<TdApi.GetForumTopics>()
    val reopened = store.openList(100, "") { }
    assertEquals("A truncated list must restart at page one even while an old next-page request exists",
      callsBeforeReopen + 1, backend.count<TdApi.GetForumTopics>())
    val restart = backend.calls.last()
    assertEquals(0, (restart.request as TdApi.GetForumTopics).offsetForumTopicId)
    restart.reply(page(*values(1, 50), total = 100))
    oldMore.reply(page(*values(51, 100), total = 100))
    backend.publish()
    assertEquals((1..50).toSet(), reopened.snapshot.topics.map { it.info.forumTopicId }.toSet())
  }
}
