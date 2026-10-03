package org.thunderdog.challegram.component.chat

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.telegram.topic

class ForumTopicListDiffTest {
  private class Changes : ListUpdateCallback {
    var changed = 0; var inserted = 0; var removed = 0; var moved = 0
    override fun onChanged(position: Int, count: Int, payload: Any?) { changed += count }
    override fun onInserted(position: Int, count: Int) { inserted += count }
    override fun onRemoved(position: Int, count: Int) { removed += count }
    override fun onMoved(fromPosition: Int, toPosition: Int) { moved++ }
  }

  @Test fun oneChangedRowInThousandDoesNotRebindOtherRows() {
    val before = (1..1000).map { topic(it) }
    val after = before.toMutableList().apply { this[499] = topic(500, name = "Synthetic renamed") }
    val changes = Changes()
    val start = System.nanoTime()
    DiffUtil.calculateDiff(ForumTopicListDiff(before, after)).dispatchUpdatesTo(changes)
    println("FORUM_DIFF rows=1000 changedRows=${changes.changed - 1} elapsedMicros=${(System.nanoTime() - start) / 1000}")
    assertEquals(2, changes.changed) // One topic plus the status footer.
    assertEquals(0, changes.inserted + changes.removed + changes.moved)
  }

  @Test fun loadingOnlyChangeUpdatesFooterNotRows() {
    val rows = (1..100).map { topic(it) }
    val changes = Changes()
    DiffUtil.calculateDiff(ForumTopicListDiff(rows, rows)).dispatchUpdatesTo(changes)
    assertEquals(1, changes.changed)
    assertEquals(0, changes.inserted + changes.removed + changes.moved)
  }

  @Test fun reorderingDoesNotRebindUnchangedRows() {
    val rows = (1..3).map { topic(it) }
    val changes = Changes()
    DiffUtil.calculateDiff(ForumTopicListDiff(rows, listOf(rows[2], rows[0], rows[1]))).dispatchUpdatesTo(changes)
    assertEquals(1, changes.changed)
    assertTrue(changes.moved > 0)
    assertEquals(0, changes.inserted + changes.removed)
  }

  @Test fun deletionKeepsFooterIdentity() {
    val rows = (1..3).map { topic(it) }
    val changes = Changes()
    DiffUtil.calculateDiff(ForumTopicListDiff(rows, rows.drop(1))).dispatchUpdatesTo(changes)
    assertEquals(1, changes.removed)
    assertEquals(0, changes.inserted)
    assertEquals(1, changes.changed)
  }

  @Test fun sameNumericIdInDifferentChatsIsNotSameRow() {
    val diff = ForumTopicListDiff(listOf(topic(chat = 100)), listOf(topic(chat = 200)))
    assertFalse(diff.areItemsTheSame(0, 0))
    assertTrue(diff.areItemsTheSame(1, 1))
  }

  @Test fun changedContentAndFooterReuseTheirHolderWithoutCrossfade() {
    val diff = ForumTopicListDiff(listOf(topic()), listOf(topic(name = "Synthetic updated")))
    assertSame(ForumTopicListDiff.CONTENT_PAYLOAD, diff.getChangePayload(0, 0))
    assertSame(ForumTopicListDiff.CONTENT_PAYLOAD, diff.getChangePayload(1, 1))
    assertNotSame(ForumTopicListDiff.SELECTION_PAYLOAD, diff.getChangePayload(0, 0))
  }
}
