package org.thunderdog.challegram.data

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.telegram.topic

class ForumChatPreviewTest {
  private fun recent(id: Int, date: Int, chat: Long = 100) = topic(id, chat).apply { lastMessage!!.date = date }
  private fun draft(text: String, date: Int) = TdApi.DraftMessage(null, date, TdApi.DraftMessageContentText(TdApi.FormattedText(text, emptyArray()), null), 0, null)

  @Test fun `latest topic draft is independent of last message and hidden drafts`() {
    val old = recent(1, 50).apply { draftMessage = draft("Older draft", 60) }
    val current = recent(2, 20).apply { draftMessage = draft("Current draft", 80) }
    val hidden = recent(3, 100).apply { info.isHidden = true; draftMessage = draft("Hidden", 110) }
    val empty = recent(4, 90).apply { draftMessage = draft("", 120) }
    val topics = listOf(old, hidden, current, empty)
    assertSame(current, ForumChatPreview.latestDraft(100, topics))
    assertEquals(2, ForumChatPreview.select(100, 2, topics, current, false).first().id)
    assertEquals(listOf(4, 1, 2), ForumChatPreview.select(100, null, topics, null, false).map { it.id })
  }

  @Test fun `clearing topic draft does not fabricate fallback draft`() {
    val value = recent(2, 50).apply { draftMessage = draft("Draft", 90) }
    assertSame(value, ForumChatPreview.latestDraft(100, listOf(value)))
    value.draftMessage = null
    assertNull(ForumChatPreview.latestDraft(100, listOf(value)))
  }

  @Test fun `draft participates in bounded cache priority but not message activity ordering`() {
    val oldDraft = recent(1, 10).apply { draftMessage = draft("Draft", 200) }
    val message = recent(2, 100)
    assertEquals(listOf(oldDraft, message), listOf(message, oldDraft).sortedWith(ForumChatPreview.CACHE_FIRST))
    assertEquals(listOf(message, oldDraft), listOf(oldDraft, message).sortedWith(ForumChatPreview.RECENT_FIRST))
  }

  @Test fun `current message topic leads independently of pin and store order`() {
    val oldPin = recent(1, 10).apply { isPinned = true }
    val newest = recent(2, 50)
    val displayed = recent(3, 40)
    val source = listOf(oldPin, newest, displayed)
    assertEquals(listOf(3, 2, 1), ForumChatPreview.select(100, displayed.lastMessage, source, null, false).map { it.id })
    assertEquals(listOf(1, 2, 3), source.map { it.info.forumTopicId })
  }

  @Test fun `unknown leading metadata reserves topic without mislabeling message`() {
    val shown = recent(8, 50)
    val result = ForumChatPreview.select(100, shown.lastMessage, listOf(recent(9, 40)), null, false)
    assertEquals(8, result[0].id)
    assertEquals("", result[0].name)
    assertEquals(9, result[1].id)
  }

  @Test fun `hydrated leader is unique even outside first page`() {
    val leader = recent(9, 100).apply { info.name = "Resolved" }
    val result = ForumChatPreview.select(100, leader.lastMessage, listOf(recent(2, 90), recent(9, 80)), leader, false)
    assertEquals(listOf(9, 2), result.map { it.id })
    assertEquals("Resolved", result[0].name)
  }

  @Test fun `deleted leader is not reintroduced as placeholder or cached candidate`() {
    val leader = recent(9, 50)
    assertEquals(listOf(3), ForumChatPreview.select(100, leader.lastMessage, listOf(leader, recent(3, 40)), null, true).map { it.id })
  }

  @Test fun `hidden general and empty or scheduled topics are not active previews`() {
    val hidden = recent(1, 100).apply { info.isHidden = true }
    val empty = topic(2).apply { lastMessage = null }
    val scheduled = recent(3, 99).apply { lastMessage!!.schedulingState = TdApi.MessageSchedulingStateSendAtDate() }
    val visible = recent(4, 98)
    assertEquals(listOf(4), ForumChatPreview.select(100, hidden.lastMessage, listOf(hidden, empty, scheduled, visible), hidden, false).map { it.id })
  }

  @Test fun `foreign chats and other topic constructors do not leak into summary`() {
    val foreign = recent(8, 100, 200)
    val wrongType = recent(7, 90).apply { lastMessage!!.topicId = TdApi.MessageTopicSavedMessages(123) }
    assertEquals(listOf(4), ForumChatPreview.select(100, foreign.lastMessage, listOf(foreign, wrongType, recent(4, 50)), foreign, false).map { it.id })
    assertEquals(0, ForumChatPreview.topicId(100, wrongType.lastMessage))
  }

  @Test fun `bounded unique projection sorts equal timestamps by message id`() {
    val values = (1..20).map { recent(it, 100) }
    assertEquals(listOf(20, 19, 18, 17), ForumChatPreview.select(100, null, values + values, null, false).map { it.id })
  }

  @Test fun `rename and icon updates change immutable projection`() {
    val value = recent(2, 50)
    val before = ForumChatPreview.select(100, value.lastMessage, listOf(value), null, false)
    value.info.name = "Renamed"
    value.info.icon = TdApi.ForumTopicIcon(0x8EEE98, 12345)
    val after = ForumChatPreview.select(100, value.lastMessage, listOf(value), null, false)
    assertNotEquals(before, after)
    assertEquals("Alpha", before[0].name)
    assertEquals(12345L, after[0].customEmojiId)
  }

  @Test fun `irrelevant counters do not change labels or reload emoji`() {
    val value = recent(2, 50)
    val before = ForumChatPreview.select(100, value.lastMessage, listOf(value), null, false)
    value.unreadCount = 88
    value.isPinned = true
    assertEquals(before, ForumChatPreview.select(100, value.lastMessage, listOf(value), null, false))
  }

  @Test fun `empty forum has no fabricated active topic`() {
    assertTrue(ForumChatPreview.select(100, null, emptyList(), null, false).isEmpty())
  }

  @Test fun `network error differs from unavailable chat`() {
    assertFalse(ForumChatPreview.inaccessible(TdApi.Error(408, "Timeout")))
    assertFalse(ForumChatPreview.inaccessible(null))
    assertTrue(ForumChatPreview.inaccessible(TdApi.Error(400, "CHANNEL_PRIVATE")))
    assertTrue(ForumChatPreview.inaccessible(TdApi.Error(404, "Not found")))
  }
}
