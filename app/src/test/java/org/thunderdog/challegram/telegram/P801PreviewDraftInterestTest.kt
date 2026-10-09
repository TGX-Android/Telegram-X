package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.data.ForumChatPreview

/** Synthetic edge cases for the P8-01 preview-draft interest and session handoff fixes. */
class P801PreviewDraftInterestTest {
  private val backend = ForumTopicTestBackend()
  private val store = ForumTopicStore(backend)

  private fun preview(): ForumTopicStore.ListSession {
    val session = store.openPreview(100) { }
    backend.advance(ForumTopicStore.PREVIEW_ATTACH_DELAY_MS)
    backend.publish()
    return session
  }

  private fun loadedPreview(): ForumTopicStore.ListSession {
    val session = preview()
    backend.next<TdApi.GetForumTopics>().reply(page(topic()))
    backend.publish()
    return session
  }

  private fun drafted(id: Int, date: Int) = topic(id).apply {
    draftMessage = TdApi.DraftMessage(null, date,
      TdApi.DraftMessageContentText(TdApi.FormattedText("Synthetic draft", emptyArray()), null), 0, null)
  }

  @Test fun `uncached draft burst hydrates the newest candidate without fetching obsolete candidates`() {
    val session = loadedPreview()
    (100..199).forEach { store.updateTopic(update(drafted(it, it))) }
    backend.advance()

    val requests = backend.calls.mapNotNull { it.request as? TdApi.GetForumTopic }
    assertEquals("Only the newest pending draft should retain preview-only hydration interest",
      listOf(199), requests.map { it.forumTopicId })
    backend.next<TdApi.GetForumTopic>().reply(drafted(199, 199))
    backend.publish()
    assertEquals(199, ForumChatPreview.latestDraft(100, session.snapshot.topics)?.info?.forumTopicId)
  }

  @Test fun `pending draft hydration survives a newer first page that omits its topic`() {
    val session = loadedPreview()
    val draft = drafted(99, 100)
    store.updateTopic(update(draft))
    store.invalidateChat(100)
    backend.advance()
    assertEquals(0, backend.count<TdApi.GetForumTopic>()) // Wait for the coalesced first page.
    backend.next<TdApi.GetForumTopics>().reply(page(topic(), total = 100))
    backend.advance()

    assertEquals("A first page is not proof that the pending draft's topic no longer exists",
      1, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopic>().reply(draft)
    backend.publish()
    assertEquals(99, ForumChatPreview.latestDraft(100, session.snapshot.topics)?.info?.forumTopicId)
  }

  @Test fun `clearing a resolved draft offscreen releases its pending date threshold`() {
    val firstSession = loadedPreview()
    val newest = drafted(98, 1000)
    store.updateTopic(update(newest))
    backend.advance()
    backend.next<TdApi.GetForumTopic>().reply(newest)
    backend.publish()
    firstSession.close()

    store.updateTopic(update(topic(98))) // Clear the old draft while no preview is visible.
    val currentSession = preview()
    backend.next<TdApi.GetForumTopics>().reply(page(topic(), total = 100))
    backend.publish()
    assertNull(ForumChatPreview.latestDraft(100, currentSession.snapshot.topics))

    val olderRemainingDraft = drafted(99, 900)
    val requestsBefore = backend.count<TdApi.GetForumTopic>()
    store.updateTopic(update(olderRemainingDraft))
    backend.advance()
    assertEquals("A cleared draft must not suppress an older remaining draft reported later",
      requestsBefore + 1, backend.count<TdApi.GetForumTopic>())
    backend.next<TdApi.GetForumTopic>().reply(olderRemainingDraft)
    backend.publish()
    assertEquals(99, ForumChatPreview.latestDraft(100, currentSession.snapshot.topics)?.info?.forumTopicId)
  }

  @Test fun `full subscribers share a prefix repairing refresh already in flight`() {
    preview()
    val topics = (1..50).map { topic(it) }.toTypedArray()
    backend.next<TdApi.GetForumTopics>().reply(page(*topics, total = 100))
    val first = store.openList(100, "") { }
    val repair = backend.next<TdApi.GetForumTopics>()
    assertEquals(0, (repair.request as TdApi.GetForumTopics).offsetForumTopicId)

    val countBeforeSecond = backend.count<TdApi.GetForumTopics>()
    val second = store.openList(100, "") { }
    assertEquals("An existing first-page refresh already repairs the truncated prefix",
      countBeforeSecond, backend.count<TdApi.GetForumTopics>())
    repair.reply(page(*topics, total = 100))
    backend.publish()
    assertEquals(50, first.snapshot.topics.size)
    assertEquals(50, second.snapshot.topics.size)
  }
}
