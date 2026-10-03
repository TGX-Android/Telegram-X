package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import org.thunderdog.challegram.telegram.TdlibForumTopicManager.Key

/** Synthetic replies only, including a cached page delivered inside Backend.send. */
class ForumTopicMutationReconciliationTest {
  private class Fixture(initiallyClosed: Boolean = false) {
    val backend = ForumTopicTestBackend()
    val store = ForumTopicStore(backend)
    val actions = ForumTopicActions(store)
    val session = store.openList(100, "") { }
    val server = linkedMapOf(17 to initiallyClosed, 18 to initiallyClosed)

    init { backend.next<TdApi.GetForumTopics>().reply(page(value(17), value(18))) }

    fun value(id: Int, closed: Boolean = server.getValue(id)) = topic(id).apply {
      info.isClosed = closed
      lastMessage!!.content = TdApi.MessageForumTopicIsClosedToggled(closed)
    }

    fun write(id: Int, closed: Boolean) {
      actions.setClosed(Key(100, id), closed) { _, error -> assertNull(error) }
      server[id] = closed
      store.updateInfo(value(id).info)
      store.onMessage(value(id).lastMessage!!)
      backend.next<TdApi.ToggleForumTopicIsClosed>().reply(TdApi.Ok())
      backend.publish()
    }

    fun synchronousStalePage(id: Int) {
      backend.immediateResponse = { request ->
        if (request is TdApi.GetForumTopics) {
          val stale = value(id, !server.getValue(id)).apply {
            // The message is current while the page metadata is cached, as in the QA trace.
            lastMessage!!.content = TdApi.MessageForumTopicIsClosedToggled(server.getValue(id))
          }
          store.updateInfo(stale.info)
          store.updateTopic(update(stale))
          page(*server.keys.map { if (it == id) stale else value(it) }.toTypedArray())
        } else null
      }
    }

    fun point(id: Int): ForumTopicTestBackend.Call {
      val call = backend.next<TdApi.GetForumTopic>()
      assertEquals(id, (call.request as TdApi.GetForumTopic).forumTopicId)
      return call
    }
  }

  @Test fun synchronousCachedPageCannotConsumeEitherSuccessfulWriteInBothDirections() {
    for (closed in listOf(true, false)) {
      val f = Fixture(initiallyClosed = !closed)
      for (id in listOf(17, 18)) {
        val reads = f.backend.count<TdApi.GetForumTopic>()
        f.synchronousStalePage(id)
        f.write(id, closed)
        f.backend.advance()
        assertEquals("A page must not satisfy the post-write point read", reads + 1, f.backend.count<TdApi.GetForumTopic>())
        assertTrue(f.session.snapshot.stale)
        f.point(id).reply(f.value(id))
        assertEquals(closed, f.store.cachedTopic(Key(100, id))!!.info.isClosed)
        assertFalse(f.session.snapshot.stale)
      }
      f.backend.advance(60000)
      assertEquals(2, f.backend.count<TdApi.ToggleForumTopicIsClosed>())
      assertEquals(2, f.backend.count<TdApi.GetForumTopic>())
      assertTrue(f.session.snapshot.topics.all { it.info.isClosed == closed })
    }
  }

  @Test fun preflightAfterSuccessCannotSatisfyTheRequiredPointRead() {
    val f = Fixture()
    f.write(17, true)
    f.store.perform(TdApi.GetForumTopic(100, 17), 100, null, TdApi.ForumTopic.CONSTRUCTOR, false) { _, error -> assertNull(error) }
    f.point(17).reply(f.value(17))
    f.synchronousStalePage(17)
    f.backend.advance()
    assertEquals(2, f.backend.count<TdApi.GetForumTopic>())
    f.point(17).reply(f.value(17))
    assertTrue(f.store.cachedTopic(Key(100, 17))!!.info.isClosed)
    assertFalse(f.session.snapshot.stale)
  }

  @Test fun pointReadStartedBeforeTheLatestWriteCannotConsumeItsRequirement() {
    val f = Fixture()
    f.synchronousStalePage(17)
    f.write(17, true)
    f.backend.advance()
    val old = f.point(17)
    f.write(17, false)
    old.reply(f.value(17, true))
    f.backend.advance()
    assertEquals(2, f.backend.count<TdApi.GetForumTopic>())
    f.point(17).reply(f.value(17))
    assertFalse(f.store.cachedTopic(Key(100, 17))!!.info.isClosed)
    assertFalse(f.session.snapshot.stale)
    f.backend.advance(60000)
    assertEquals(2, f.backend.count<TdApi.GetForumTopic>())
  }

  @Test fun pageSupersedingAnInFlightPointReadDoesNotSatisfyTheMutation() {
    val f = Fixture()
    f.synchronousStalePage(17)
    f.write(17, true)
    f.backend.advance()
    val old = f.point(17)
    f.session.refresh() // A newer request stamp, but still cached old metadata.
    old.reply(f.value(17))
    f.backend.advance()
    assertEquals(2, f.backend.count<TdApi.GetForumTopic>())
    f.point(17).reply(f.value(17))
    assertTrue(f.store.cachedTopic(Key(100, 17))!!.info.isClosed)
    assertFalse(f.session.snapshot.stale)
    f.backend.advance(60000)
    assertEquals(2, f.backend.count<TdApi.GetForumTopic>())
  }

  @Test fun conflictingInfoAfterPointStartCannotLetNewerPageConsumeMutation() {
    val f = Fixture(initiallyClosed = true)
    f.write(17, false)
    f.backend.advance()
    val point = f.point(17)
    // An overlapping page emits old metadata after the post-write point read starts.
    f.store.updateInfo(f.value(17, true).info)
    point.reply(f.value(17)) // Fresh OPEN reply must not consume the write's barrier.

    f.session.refresh() // A later page can agree with that old metadata.
    val stale = f.value(17, true).apply {
      lastMessage!!.content = TdApi.MessageForumTopicIsClosedToggled(false)
    }
    f.backend.calls.last { it.request is TdApi.GetForumTopics }.reply(page(stale, f.value(18)))
    f.backend.advance()
    assertEquals("Conflicting later info still requires a point read after the newer page", 2, f.backend.count<TdApi.GetForumTopic>())
    f.point(17).reply(f.value(17))
    f.backend.advance(60000)
    assertFalse(f.store.cachedTopic(Key(100, 17))!!.info.isClosed)
    assertFalse((f.store.cachedTopic(Key(100, 17))!!.lastMessage!!.content as TdApi.MessageForumTopicIsClosedToggled).isClosed)
    assertFalse(f.session.snapshot.stale)
    assertNull(f.session.snapshot.error)
    assertEquals(2, f.backend.count<TdApi.GetForumTopic>())
  }

  @Test fun postWriteReadErrorOrTimeoutDoesNotAutomaticallyRetry() {
    for (timeout in listOf(false, true)) {
      val f = Fixture()
      f.synchronousStalePage(17)
      f.write(17, true)
      f.backend.advance()
      val call = f.point(17)
      if (timeout) f.backend.advance(ForumTopicStore.REQUEST_TIMEOUT_MS)
      else call.reply(TdApi.Error(500, "Synthetic failure"))
      assertEquals(if (timeout) 408 else 500, f.session.snapshot.error!!.code)
      f.backend.advance(60000)
      assertEquals(1, f.backend.count<TdApi.GetForumTopic>())
      assertEquals(1, f.backend.count<TdApi.ToggleForumTopicIsClosed>())
      f.backend.immediateResponse = null
      f.store.retryTopic(Key(100, 17))
      val retry = f.backend.calls.last { it.request is TdApi.GetForumTopic }
      retry.reply(f.value(17))
      assertTrue(f.store.cachedTopic(Key(100, 17))!!.info.isClosed)
      assertNull(f.session.snapshot.error)
      assertFalse(f.session.snapshot.stale)
      if (timeout) call.reply(f.value(17, false)) // The timed-out response must remain obsolete.
      assertTrue(f.store.cachedTopic(Key(100, 17))!!.info.isClosed)
    }
  }
}
