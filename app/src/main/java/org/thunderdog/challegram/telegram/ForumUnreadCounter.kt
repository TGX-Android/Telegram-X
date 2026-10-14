package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import java.io.Closeable

/** Account-scoped badge projection, not a message cache. Only complete unfiltered walks may
 * replace the total. In particular, the sixteen-topic chat preview is never a count source. */
class ForumUnreadCounter(private val backend: ForumTopicStore.Backend, initiallyEnabled: Boolean = true) {
  fun interface Observer { fun onUnreadChanged(unreadTopics: Int) }

  inner class Subscription internal constructor(val chatId: Long, private val observer: Observer) : Closeable {
    private val context = epoch
    @Volatile private var closed = false
    internal fun live() = !closed && context == epoch
    internal fun deliver(count: Int) = backend.publish { if (live()) observer.onUnreadChanged(count) }
    override fun close() {
      closed = true
      owner {
        states[chatId]?.let {
          it.observers.remove(this)
          if (!it.visible()) { it.scan = null; queue.remove(it) }
        }
        trim()
      }
    }
  }

  private data class ReadState(val lastMessage: Long, val inbox: Long, val unread: Boolean) {
    fun readThrough(id: Long) = copy(inbox = maxOf(inbox, id), unread = unread && !(lastMessage > 0 && id >= lastMessage))
  }
  private class Scan(val revision: Long) {
    val topics = HashMap<Int, ReadState>()
    val cursors = HashSet<ForumTopicStore.Cursor>()
    var overlapPages = 0
    var cursor = ForumTopicStore.Cursor()
  }
  private inner class State(val chatId: Long) {
    val observers = LinkedHashSet<Subscription>()
    var topics: Map<Int, ReadState>? = null
    // High-water marks also protect a late page from a newer remote read event.
    val reads = HashMap<Int, Long>()
    var count = UNKNOWN
    var revision = 0L
    var fresh = false
    var expiry = 0L
    var scan: Scan? = null
    var pending = false
    fun visible() = observers.any { it.live() }
  }

  private val states = LinkedHashMap<Long, State>(16, .75f, true)
  private val queue = LinkedHashSet<State>()
  @Volatile private var epoch = 0L
  private var enabled = initiallyEnabled
  private var inFlight = 0
  private var drainScheduled = false

  private fun owner(action: () -> Unit) {
    val context = epoch
    backend.execute { synchronized(this) { if (context == epoch) action() } }
  }

  @Synchronized fun cachedCount(chatId: Long): Int = states[chatId]?.count ?: UNKNOWN

  fun observe(chatId: Long, observer: Observer): Subscription {
    require(chatId != 0L)
    val subscription = Subscription(chatId, observer)
    backend.schedule(ATTACH_DELAY_MS) {
      owner {
        if (subscription.live()) {
          val state = states.getOrPut(chatId) { State(chatId) }
          state.observers.add(subscription)
          subscription.deliver(state.count)
          if (!state.fresh) enqueue(state)
          trim()
        }
      }
    }
    return subscription
  }

  /** Reconcile new/deleted messages and explicit chat-read events without marking anything read. */
  fun invalidate(chatId: Long) = owner {
    states[chatId]?.let {
      it.revision++
      it.fresh = false
      enqueue(it)
    }
  }

  fun onTopicRead(chatId: Long, topicId: Int, inbox: Long) = owner {
    if (inbox <= 0) return@owner
    val state = states[chatId] ?: return@owner
    val previous = state.reads[topicId] ?: state.topics?.get(topicId)?.inbox ?: state.scan?.topics?.get(topicId)?.inbox
    if (previous != null && inbox <= previous) return@owner
    state.reads[topicId] = inbox
    state.topics?.get(topicId)?.takeIf { state.fresh }?.let { value ->
      state.topics = state.topics!! + (topicId to value.readThrough(inbox))
      publishCount(state)
    }
    // GetForumTopics itself can announce an as-yet unknown topic before delivering its page.
    // Do not invalidate that initial scan for these announcements; merge the read watermark.
    if (previous != null || state.scan == null) {
      state.revision++
      state.fresh = false
      enqueue(state)
    }
  }

  fun refreshVisible() = owner {
    for (state in states.values.toList()) {
      state.fresh = false
      state.revision++
      enqueue(state)
    }
  }

  /** No pagination/polling while the application is in the background. */
  fun setEnabled(value: Boolean) = owner {
    if (enabled != value) {
      enabled = value
      if (!value) {
        queue.clear()
        states.values.forEach { it.scan = null; it.fresh = false }
      } else {
        states.values.toList().forEach { enqueue(it) }
      }
    }
  }

  @Synchronized fun reset() {
    epoch++
    states.clear()
    queue.clear()
    inFlight = 0
    drainScheduled = false
  }

  private fun enqueue(state: State) {
    if (!enabled || !state.visible()) return
    queue.add(state)
    scheduleDrain(RECONCILE_DELAY_MS)
  }

  private fun scheduleDrain(delay: Long) {
    if (drainScheduled) return
    drainScheduled = true
    val context = epoch
    backend.schedule(delay) {
      owner {
        if (context != epoch) return@owner
        drainScheduled = false
        drain()
      }
    }
  }

  private fun drain() {
    if (!enabled) return
    // One page per chat, at most two requests per account, round-robin between visible chats.
    for (state in queue.toList()) {
      if (inFlight >= MAX_REQUESTS) break
      if (!state.visible()) { queue.remove(state); continue }
      if (state.pending) continue
      queue.remove(state)
      val scan = state.scan ?: Scan(state.revision).also { state.scan = it }
      val cursor = scan.cursor
      val context = epoch
      state.pending = true
      inFlight++
      var finished = false
      fun complete(result: TdApi.Object) = owner {
        if (finished || context != epoch) return@owner
        finished = true
        inFlight--
        state.pending = false
        if (states[state.chatId] === state && state.scan === scan && state.visible() && enabled) {
          finishPage(state, scan, cursor, result)
        }
        if (queue.isNotEmpty()) scheduleDrain(0)
      }
      backend.send(TdApi.GetForumTopics(state.chatId, "", cursor.date, cursor.messageId, cursor.topicId, PAGE_SIZE), ::complete)
      backend.schedule(REQUEST_TIMEOUT_MS) { complete(TdApi.Error(408, "Forum badge request timed out")) }
    }
  }

  private fun fail(state: State) {
    state.scan = null
    state.fresh = false
    state.expiry++
    queue.remove(state)
    // Keep the previous complete total, never publish a partial zero. No automatic error loop;
    // reconnect, reattach, foreground or a new event can retry.
  }

  private fun finishPage(state: State, scan: Scan, cursor: ForumTopicStore.Cursor, result: TdApi.Object) {
    if (result !is TdApi.ForumTopics || result.topics == null ||
      result.topics.any { it?.info == null || it.info.chatId != state.chatId || it.info.forumTopicId <= 0 } ||
      result.nextOffsetDate < 0 || result.nextOffsetForumTopicId < 0) { fail(state); return }
    val next = ForumTopicStore.Cursor(result.nextOffsetDate, result.nextOffsetMessageId, result.nextOffsetForumTopicId)
    if (result.topics.isEmpty() && next.isEmpty) {
      state.scan = null
      if (scan.revision != state.revision) { enqueue(state); return }
      state.topics = scan.topics
      state.reads.keys.retainAll(scan.topics.keys)
      state.fresh = true
      publishCount(state)
      queue.remove(state)
      val expiry = ++state.expiry
      val context = epoch
      // TDLib can omit read events for roots absent from its message cache. While visible,
      // periodically reconcile with authoritative pages; never rely on such an event alone.
      backend.schedule(REFRESH_MS) {
        owner {
          if (context == epoch && states[state.chatId] === state && state.expiry == expiry) {
            state.fresh = false
            enqueue(state)
          }
        }
      }
      return
    }
    val oldSize = scan.topics.size
    for (topic in result.topics) {
      var value = ReadState(topic.lastMessage?.id ?: 0, topic.lastReadInboxMessageId, topic.unreadCount > 0)
      state.reads[topic.info.forumTopicId]?.let { if (it > value.inbox) value = value.readThrough(it) }
      scan.topics[topic.info.forumTopicId] = value
    }
    // Hidden General and other overlaps can advance the cursor without adding a topic.
    // Keep walking to authoritative EOF, with the same bounded overlap as the list.
    scan.overlapPages = if (scan.topics.size == oldSize) scan.overlapPages + 1 else 0
    if (result.topics.isEmpty() || next.isEmpty || next == cursor || !scan.cursors.add(next) ||
      scan.overlapPages > ForumTopicStore.MAX_OVERLAP_PAGES || scan.topics.size > MAX_TOPICS) { fail(state); return }
    scan.cursor = next
    queue.add(state)
  }

  private fun publishCount(state: State) {
    val count = state.topics?.values?.count { it.unread } ?: UNKNOWN
    if (state.count != count) {
      state.count = count
      state.observers.toList().forEach { if (it.live()) it.deliver(count) }
    }
  }

  private fun trim() {
    val inactive = states.values.filter { !it.visible() && !it.pending }
    inactive.take((inactive.size - MAX_INACTIVE_CHATS).coerceAtLeast(0)).forEach { states.remove(it.chatId) }
  }

  companion object {
    const val UNKNOWN = -1
    const val ATTACH_DELAY_MS = 150L
    const val RECONCILE_DELAY_MS = 300L
    const val REFRESH_MS = 60000L
    const val REQUEST_TIMEOUT_MS = 15000L
    const val PAGE_SIZE = 100
    const val MAX_REQUESTS = 2
    const val MAX_TOPICS = 10000
    const val MAX_INACTIVE_CHATS = 32

    /** Unknown is an unread dot, not a made-up topic count or a prematurely cleared badge. */
    @JvmStatic fun badgeCount(unreadTopics: Int, unreadMessages: Int, markedUnread: Boolean): Int = when {
      unreadTopics > 0 -> unreadTopics
      markedUnread || unreadTopics == UNKNOWN && unreadMessages > 0 -> Tdlib.CHAT_MARKED_AS_UNREAD
      else -> 0
    }
  }
}
