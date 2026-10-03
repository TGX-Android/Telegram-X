package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.data.ForumChatPreview
import org.thunderdog.challegram.telegram.TdlibForumTopicManager.Key
import java.io.Closeable
import java.util.Collections
import tgx.td.equalsTo

/** Account-scoped, in-memory source of truth. TDLib values exposed to consumers are read-only. */
class ForumTopicStore(
  private val backend: Backend,
  private val pageSize: Int = 50,
  private val maxCachedTopics: Int = 512,
  private val maxInactiveLists: Int = 8
) {
  interface Backend {
    fun execute(action: () -> Unit)
    fun send(request: TdApi.Function<*>, callback: (TdApi.Object) -> Unit)
    fun schedule(delayMs: Long, action: () -> Unit)
    fun publish(action: () -> Unit)
  }

  data class ListKey(@JvmField val chatId: Long, @JvmField val query: String)
  data class Cursor(@JvmField val date: Int = 0, @JvmField val messageId: Long = 0, @JvmField val topicId: Int = 0) {
    val isEmpty: Boolean get() = date == 0 && messageId == 0L && topicId == 0
  }
  class Snapshot internal constructor(
    @JvmField val key: ListKey,
    @JvmField val topics: List<TdApi.ForumTopic>,
    @JvmField val approximateTotalCount: Int,
    @JvmField val initialized: Boolean,
    @JvmField val loadingInitial: Boolean,
    @JvmField val loadingMore: Boolean,
    @JvmField val refreshing: Boolean,
    @JvmField val endReached: Boolean,
    @JvmField val stale: Boolean,
    @JvmField val error: TdApi.Error?,
    @JvmField val nextCursor: Cursor
  ) {
    val isEmpty: Boolean get() = initialized && topics.isEmpty()
  }
  fun interface ListObserver { fun onSnapshot(snapshot: Snapshot) }
  fun interface TopicObserver { fun onTopic(topic: TdApi.ForumTopic?, error: TdApi.Error?) }

  inner class ListSession internal constructor(internal var key: ListKey, internal val observer: ListObserver, internal val previewOnly: Boolean = false) : Closeable {
    private val context = epoch
    @Volatile private var disposed = false
    @Volatile private var invalidated = false
    @Volatile private var delivery = 0L
    @Volatile var snapshot: Snapshot = cachedSnapshot(key.chatId, key.query)
      private set

    fun setQuery(query: String) = onOwner {
      if (live() && key.query != normalizeQuery(query)) {
        detach(this)
        key = ListKey(key.chatId, normalizeQuery(query))
        attach(this)
      }
    }
    fun refresh() = onOwner { if (live()) refresh(state(key)) }
    fun loadMore() = onOwner { if (live()) loadMore(state(key)) }
    fun retry() = onOwner {
      if (live()) {
        val state = state(key)
        if (state.error?.code == PAGINATION_ERROR) refresh(state)
        else if (state.error != null) requestPage(state, state.retryKind)
        else {
          records.filter { it.key.chatId == key.chatId && it.value.error != null }.keys.forEach { queueTopic(it) }
          refresh(state)
        }
      }
    }
    override fun close() {
      disposed = true // Also cancels UI callbacks that are already queued.
      onOwner { detach(this); trim() }
    }
    internal fun live(): Boolean = !disposed && !invalidated && context == epoch
    internal fun deliver(value: Snapshot, terminal: Boolean = false) {
      snapshot = value
      if (terminal) invalidated = true
      val ticket = ++delivery
      backend.publish {
        if (!disposed && delivery == ticket) observer.onSnapshot(value)
      }
    }
  }

  inner class TopicSubscription internal constructor(internal val key: Key, internal val observer: TopicObserver) : Closeable {
    private val context = epoch
    @Volatile internal var closed = false
    @Volatile private var delivery = 0L
    override fun close() {
      closed = true
      onOwner { topicObservers[key]?.let { it.remove(this); if (it.isEmpty()) topicObservers.remove(key) }; trim() }
    }
    internal fun deliver(value: TdApi.ForumTopic?, error: TdApi.Error?) {
      val ticket = ++delivery
      backend.publish { if (!closed && context == epoch && delivery == ticket) observer.onTopic(value, error) }
    }
  }

  private enum class Load { INITIAL, MORE, REFRESH }
  private class PageRequest(val stamp: Long, val epoch: Long, val cursor: Cursor, val kind: Load)
  private class ListState(val key: ListKey) {
    val ids = LinkedHashSet<Key>()
    // ForumTopic.order is activity order, not pinned order. Preserve the server's pin sequence.
    val pinnedRanks = LinkedHashMap<Key, Int>()
    val sessions = LinkedHashSet<ListSession>()
    val seenCursors = HashSet<Cursor>()
    var cursor = Cursor()
    var totalCount = -1
    var initialized = false
    var endReached = false
    var stale = true
    var dirty = false
    var error: TdApi.Error? = null
    val topicErrors = LinkedHashMap<Key, TdApi.Error>()
    var retryKind = Load.INITIAL
    var request: PageRequest? = null
    var previewFresh = false
    var previewStamp = 0L
    var previewTruncated = false
    // One unresolved draft, independent of the loaded page membership in ids.
    var previewDraftKey: Key? = null
    var previewDraftDate = 0
  }
  private class Record {
    var value: TdApi.ForumTopic? = null
    var error: TdApi.Error? = null
    var fullStamp = 0L
    var infoStamp = 0L
    var updateStamp = 0L
    var dirtyStamp = 0L
    var eventStamp = 0L
    // A keyed mutation needs a point read started after its acknowledgement. A cached
    // list page or an action preflight cannot satisfy this independently tracked work.
    var mutationStamp = 0L
    var removedStamp = 0L
    var info: TdApi.ForumTopicInfo? = null
    var update: TdApi.UpdateForumTopic? = null
  }
  private class TopicRequest(val stamp: Long, val epoch: Long)

  private val records = LinkedHashMap<Key, Record>(16, .75f, true)
  private val lists = LinkedHashMap<ListKey, ListState>(16, .75f, true)
  private val topicObservers = HashMap<Key, MutableSet<TopicSubscription>>()
  private val topicRequests = HashMap<Key, TopicRequest>()
  private val pendingTopics = LinkedHashSet<Key>()
  private var sequence = 0L
  @Volatile private var epoch = 0L
  private var reconciliationScheduled = false


  init {
    require(pageSize in 1..100 && maxCachedTopics > 0 && maxInactiveLists >= 0)
  }

  private fun onOwner(action: () -> Unit) {
    val context = epoch
    backend.execute { synchronized(this) { if (context == epoch) action() } }
  }

  @Synchronized
  fun cachedTopic(key: Key): TdApi.ForumTopic? = records[key]?.value

  @Synchronized
  fun cachedSnapshot(chatId: Long, query: String): Snapshot {
    val key = ListKey(chatId, normalizeQuery(query))
    return snapshot(lists[key] ?: ListState(key))
  }

  fun openList(chatId: Long, query: String, observer: ListObserver): ListSession {
    require(chatId != 0L)
    val session = ListSession(ListKey(chatId, normalizeQuery(query)), observer)
    onOwner { if (session.live()) attach(session) }
    return session
  }

  /** Visible chat rows need only a bounded first-page projection. Fast scrolls do not start requests. */
  fun openPreview(chatId: Long, observer: ListObserver): ListSession {
    require(chatId != 0L)
    val session = ListSession(ListKey(chatId, ""), observer, previewOnly = true)
    backend.schedule(PREVIEW_ATTACH_DELAY_MS) { onOwner { if (session.live()) attach(session) } }
    return session
  }

  fun observeTopic(key: Key, observer: TopicObserver): TopicSubscription {
    val subscription = TopicSubscription(key, observer)
    onOwner {
      if (!subscription.closed) {
        topicObservers.getOrPut(key) { LinkedHashSet() }.add(subscription)
        val record = records[key]
        if (record?.value != null) subscription.deliver(record.value, null)
        if (record?.value == null || record.error != null || record.mutationStamp != 0L || record.dirtyStamp > record.fullStamp) queueTopic(key)
      }
    }
    return subscription
  }

  fun retryTopic(key: Key) = onOwner { invalidate(key); drainTopics() }

  /** Reset on both logout and client restart; callbacks from the old epoch cannot refill the cache. */
  @Synchronized
  fun reset() {
    epoch++
    reconciliationScheduled = false
    records.clear()
    pendingTopics.clear()
    topicRequests.clear()
    for (state in lists.values) {
      val empty = snapshot(ListState(state.key))
      state.sessions.forEach { it.deliver(empty, terminal = true) }
    }
    lists.clear()
    topicObservers.values.flatten().forEach { it.closed = true }
    topicObservers.clear()
  }

  private fun state(key: ListKey): ListState = lists.getOrPut(key) { ListState(key) }

  private fun attach(session: ListSession) {
    val state = state(session.key)
    val wasInactive = state.sessions.isEmpty()
    state.sessions.add(session)
    trimPreview(state)
    session.deliver(snapshot(state))
    if (!session.previewOnly && state.previewTruncated && state.request?.kind == Load.MORE) {
      refresh(state) // A pending next page cannot repair an earlier, trimmed prefix.
    } else if (state.request == null) {
      if (!state.initialized) requestPage(state, Load.INITIAL)
      else if (state.stale || !session.previewOnly && state.previewTruncated || wasInactive && (!session.previewOnly || !state.previewFresh)) refresh(state)
    }
    if (session.previewOnly) state.previewDraftKey?.let { queueTopic(it) }
    records.filter { it.key.chatId == session.key.chatId && it.value.mutationStamp != 0L && interested(it.key) }
      .keys.forEach { queueTopic(it) }
    trim()
  }

  private fun detach(session: ListSession) {
    lists[session.key]?.let { state ->
      if (state.sessions.remove(session) && state.sessions.isEmpty()) {
        if (state.request != null || !session.previewOnly) state.stale = true
        state.request = null
      }
      if (trimPreview(state)) emit(state)
    }
  }

  private fun refresh(state: ListState) {
    state.request = null // Explicit refresh supersedes an older page.
    requestPage(state, if (state.initialized) Load.REFRESH else Load.INITIAL)
  }

  private fun loadMore(state: ListState) {
    if (state.request != null || state.endReached || state.error != null) return
    requestPage(state, if (state.initialized) Load.MORE else Load.INITIAL)
  }

  private fun requestPage(state: ListState, kind: Load) {
    if (state.request != null) return
    val cursor = if (kind == Load.MORE) state.cursor else Cursor()
    val request = PageRequest(++sequence, epoch, cursor, kind)
    state.request = request
    state.error = null
    state.topicErrors.clear()
    state.dirty = false
    state.retryKind = kind
    emit(state)
    backend.send(TdApi.GetForumTopics(state.key.chatId, state.key.query, cursor.date, cursor.messageId, cursor.topicId, pageSize)) { result ->
      onOwner { finishPage(state, request, result) }
    }
    backend.schedule(REQUEST_TIMEOUT_MS) { onOwner { finishPage(state, request, TdApi.Error(408, "Forum topics request timed out")) } }
  }

  private fun finishPage(state: ListState, request: PageRequest, result: TdApi.Object) {
    if (request.epoch != epoch || state.request !== request) return
    state.request = null
    if (result is TdApi.Error) {
      state.error = result
      state.stale = true
      state.dirty = false // No automatic retry loop while offline.
      emit(state)
      if (pendingTopics.isNotEmpty()) scheduleReconciliation()
      return
    }
    if (result !is TdApi.ForumTopics || result.topics == null || result.topics.any { it?.info == null || it.info.chatId != state.key.chatId || it.info.forumTopicId <= 0 } || result.nextOffsetDate < 0 || result.nextOffsetForumTopicId < 0) {
      state.error = TdApi.Error(502, "Invalid forum topics response")
      state.stale = true
      state.dirty = false
      emit(state)
      if (pendingTopics.isNotEmpty()) scheduleReconciliation()
      return
    }
    val previousIds = state.ids.toSet()
    if (request.kind != Load.MORE) {
      // Retain updates arriving after refresh began, but replace old page membership.
      state.ids.removeAll { key -> records[key]?.let { maxOf(it.infoStamp, it.updateStamp, it.dirtyStamp, it.fullStamp) <= request.stamp } != false }
      state.seenCursors.clear()
      state.pinnedRanks.clear()
      state.previewTruncated = false
    }
    for (topic in result.topics) {
      val key = Key(topic.info.chatId, topic.info.forumTopicId)
      val previousInfo = records[key]?.value?.info
      if (merge(key, topic, request.stamp)) {
        state.ids.add(key)
        if (topic.isPinned && key !in state.pinnedRanks) state.pinnedRanks[key] = (state.pinnedRanks.values.maxOrNull() ?: -1) + 1
        if (!sameInfo(previousInfo, records[key]?.value?.info)) invalidateSearch(key.chatId, except = state)
      }
    }
    val next = Cursor(result.nextOffsetDate, result.nextOffsetMessageId, result.nextOffsetForumTopicId)
    state.totalCount = result.totalCount.coerceAtLeast(0)
    state.initialized = true
    state.previewFresh = true
    state.previewStamp = request.stamp
    backend.schedule(PREVIEW_CACHE_MS) { onOwner { if (lists[state.key] === state && state.previewStamp == request.stamp) state.previewFresh = false } }
    state.endReached = result.topics.isEmpty() && next.isEmpty
    if (!state.endReached && (next.isEmpty || next == request.cursor || !state.seenCursors.add(next) ||
        (request.kind == Load.MORE && state.ids.none { it !in previousIds }) || result.topics.isEmpty())) {
      state.error = TdApi.Error(PAGINATION_ERROR, "Forum topic pagination made no progress; refresh to retry")
    }
    if (!next.isEmpty) state.cursor = next
    lists.values.filter { it.key.chatId == state.key.chatId }.forEach { trimPreview(it); updateStaleness(it) }
    emitChat(state.key.chatId)
    trim()
    if (pendingTopics.isNotEmpty() || lists.values.any { it.dirty && it.sessions.isNotEmpty() }) scheduleReconciliation()
  }

  private fun merge(key: Key, incoming: TdApi.ForumTopic, stamp: Long): Boolean {
    val record = records.getOrPut(key) { Record() }
    if (record.removedStamp > stamp) return false
    if (record.fullStamp > stamp) return record.value != null
    var value = copy(incoming)
    if (record.infoStamp > stamp) value = copy(value, info = record.info!!)
    if (record.updateStamp > stamp) value = applyUpdate(value, record.update!!)
    record.value = value
    record.error = null
    lists.values.filter { it.key.chatId == key.chatId }.forEach { state ->
      state.topicErrors.remove(key)
      if (state.previewDraftKey == key) {
        // A page in any list may resolve the draft. Publish the full value before dropping interest.
        state.ids.add(key)
        setPreviewDraftInterest(state, null)
      }
    }
    record.fullStamp = stamp
    if (record.mutationStamp == 0L && record.eventStamp <= stamp &&
      (record.infoStamp <= stamp || sameInfo(incoming.info, record.info)) &&
      (record.updateStamp <= stamp || sameUpdate(incoming, record.update))) {
      record.dirtyStamp = minOf(record.dirtyStamp, stamp)
      pendingTopics.remove(key)
    }
    notifyTopic(key, record)
    if ((record.mutationStamp != 0L || record.dirtyStamp > stamp) && interested(key)) queueTopic(key)
    return true
  }

  fun updateInfo(info: TdApi.ForumTopicInfo) = onOwner {
    val key = Key(info.chatId, info.forumTopicId)
    if (sameInfo(records[key]?.value?.info ?: records[key]?.info, info)) return@onOwner
    invalidateSearch(info.chatId)
    if (key !in records && !interested(key)) { scheduleReconciliation(); return@onOwner }
    val record = records.getOrPut(key) { Record() }
    record.infoStamp = ++sequence
    record.info = info
    record.value = record.value?.let { copy(it, info = info) }
    record.dirtyStamp = sequence
    notifyTopic(key, record)
    invalidate(key, externalEvent = false)
  }

  fun updateTopic(update: TdApi.UpdateForumTopic) = onOwner {
    val key = Key(update.chatId, update.forumTopicId)
    val draft = update.draftMessage
    // A remote draft can arrive before either topic metadata or its list page. Keep only the
    // newest pending draft interested per visible preview, using the existing bounded hydrator.
    for (state in lists.values) {
      if (state.key.chatId != key.chatId || state.key.query.isNotEmpty()) continue
      // Clear even offscreen; resolved or cleared drafts must not leave a date threshold behind.
      if (state.previewDraftKey == key && (!ForumChatPreview.hasTextDraft(draft) || records[key]?.value != null)) {
        setPreviewDraftInterest(state, null)
      }
      if (state.sessions.any { it.previewOnly && it.live() } && records[key]?.value == null &&
          draft != null && ForumChatPreview.hasTextDraft(draft) &&
          (state.previewDraftKey == key || draft.date >= state.previewDraftDate)) {
        setPreviewDraftInterest(state, key, draft.date)
      }
    }
    if (key !in records && !interested(key)) return@onOwner
    if (sameUpdate(records[key]?.value, update)) return@onOwner
    val record = records.getOrPut(key) { Record() }
    val pinChanged = record.value?.let { it.isPinned != update.isPinned } == true
    record.updateStamp = ++sequence
    record.update = update
    record.value = record.value?.let { applyUpdate(it, update) }
    if (ForumChatPreview.hasTextDraft(update.draftMessage) && record.value != null) {
      lists.values.filter { it.key.chatId == key.chatId && it.key.query.isEmpty() && it.sessions.any { session -> session.previewOnly && session.live() } }
        .forEach { it.ids.add(key); trimPreview(it) }
    }
    // This update doesn't contain order, lastMessage or unreadCount.
    notifyTopic(key, record)
    invalidate(key, externalEvent = false)
    // A pin event has no position. Reconcile the list, including changes from another client.
    if (pinChanged) invalidateChatImpl(key.chatId)
    trim()
  }

  fun onMessage(message: TdApi.Message) = onOwner {
    val topic = message.topicId
    if (topic is TdApi.MessageTopicForum && message.schedulingState == null) {
      val key = Key(message.chatId, topic.forumTopicId)
      val known = key in records || key in topicObservers
      if (known) invalidate(key)
      if (!known || !interested(key)) invalidateChatImpl(message.chatId) // Discover a burst of unseen topics with one list refresh.
    }
  }

  fun onMessageChanged(chatId: Long, messageIds: LongArray) = onOwner {
    val ids = messageIds.toHashSet()
    val keys = records.entries.filter { it.key.chatId == chatId && it.value.value?.lastMessage?.id in ids }.map { it.key }
    keys.forEach { invalidate(it) }
    if (keys.size < ids.size) invalidateChatImpl(chatId)
  }

  fun invalidateChat(chatId: Long) = onOwner { invalidateChatImpl(chatId) }

  fun onConnectionRestored() = onOwner {
    lists.values.toList().filter { it.stale && it.request == null && it.sessions.any { session -> session.live() } }.forEach { refresh(it) }
    lists.values.filter { it.sessions.any { session -> session.previewOnly && session.live() } }
      .mapNotNull { it.previewDraftKey }.forEach { queueTopic(it) }
    topicObservers.keys.filter { records[it]?.let { record -> record.error != null || record.mutationStamp != 0L || record.dirtyStamp > record.fullStamp } != false }.forEach { queueTopic(it) }
  }

  private fun invalidateChatImpl(chatId: Long) {
    for (state in lists.values) {
      if (state.key.chatId == chatId) {
        val notify = !state.stale
        state.stale = true
        state.dirty = true
        if (notify) emit(state)
      }
    }
    scheduleReconciliation()
  }

  private fun invalidateSearch(chatId: Long, except: ListState? = null) {
    for (state in lists.values) {
      if (state !== except && state.key.chatId == chatId && state.key.query.isNotEmpty()) {
        state.stale = true
        state.dirty = true
      }
    }
  }

  private fun invalidate(key: Key, externalEvent: Boolean = true) {
    if (key !in records && !interested(key)) return
    val record = records.getOrPut(key) { Record() }
    record.dirtyStamp = ++sequence
    if (externalEvent) record.eventStamp = sequence
    for (state in lists.values) {
      if (state.key.chatId == key.chatId) {
        // Repeated message invalidations do not change rows. Publish the stale transition once;
        // metadata/counter updates must still publish their new immutable row while already stale.
        val notify = !state.stale || !externalEvent
        state.stale = true
        if (notify) emit(state)
      }
    }
    if (interested(key)) queueTopic(key)
  }

  private fun interested(key: Key): Boolean = topicObservers[key]?.any { !it.closed } == true ||
    lists.values.any { state -> state.key.chatId == key.chatId && (state.key.query.isEmpty() || key in state.ids) &&
      state.sessions.any { session -> session.live() && (!session.previewOnly || key in state.ids || state.previewDraftKey == key) } }

  private fun setPreviewDraftInterest(state: ListState, key: Key?, date: Int = 0) {
    val previous = state.previewDraftKey
    state.previewDraftKey = key
    state.previewDraftDate = if (key != null) date else 0
    // Do not cancel work still needed by a full list or an explicit topic observer.
    if (previous != null && previous != key && !interested(previous)) pendingTopics.remove(previous)
  }

  private fun queueTopic(key: Key) {
    pendingTopics.add(key)
    scheduleReconciliation()
  }

  private fun scheduleReconciliation() {
    if (reconciliationScheduled) return
    reconciliationScheduled = true
    val context = epoch
    backend.schedule(RECONCILE_DELAY_MS) {
      onOwner {
        if (context != epoch) return@onOwner
        reconciliationScheduled = false
        for (state in lists.values.toList()) {
          if (state.dirty && state.request == null && state.sessions.any { it.live() }) refresh(state)
        }
        drainTopics()
      }
    }
  }

  private fun drainTopics() {
    val iterator = pendingTopics.iterator()
    val requests = ArrayList<Pair<Key, TopicRequest>>()
    while (iterator.hasNext() && topicRequests.size < MAX_TOPIC_REQUESTS) {
      val key = iterator.next()
      if (!interested(key)) { iterator.remove(); continue }
      if (key in topicRequests) continue
      val record = records[key]
      if (record?.value != null && record.error == null && record.mutationStamp == 0L && record.dirtyStamp <= record.fullStamp) { iterator.remove(); continue }
      if (record?.value == null && (record == null || record.mutationStamp == 0L) &&
          lists.values.any { it.key.chatId == key.chatId && it.key.query.isEmpty() && it.request != null }) continue
      iterator.remove()
      val request = TopicRequest(++sequence, epoch)
      topicRequests[key] = request
      requests.add(key to request)
    }
    for ((key, request) in requests) {
      backend.send(TdApi.GetForumTopic(key.chatId, key.forumTopicId)) { result -> onOwner { finishTopic(key, request, result) } }
      backend.schedule(REQUEST_TIMEOUT_MS) { onOwner { finishTopic(key, request, TdApi.Error(408, "Forum topic request timed out")) } }
    }
  }

  private fun finishTopic(key: Key, request: TopicRequest, result: TdApi.Object) {
    if (request.epoch != epoch || topicRequests[key] !== request) return
    topicRequests.remove(key)
    val record = records.getOrPut(key) { Record() }
    val afterMutation = record.mutationStamp != 0L && request.stamp > record.mutationStamp
    if (result is TdApi.ForumTopic && result.info?.chatId == key.chatId && result.info.forumTopicId == key.forumTopicId) {
      // A superseded response has not reconciled the mutation. Keep its required read
      // queued even if a newer (but cached) page made dirtyStamp <= fullStamp.
      // TDLib can emit metadata from an overlapping older page after this read
      // started. merge() preserves those later events, so the point reply has not
      // reconciled the write unless it agrees with them (or started after them).
      // Otherwise a following cached page could consume the remaining dirty work.
      if (afterMutation && request.stamp >= maxOf(record.fullStamp, record.removedStamp) &&
          record.eventStamp <= request.stamp &&
          (record.infoStamp <= request.stamp || sameInfo(result.info, record.info)) &&
          (record.updateStamp <= request.stamp || sameUpdate(result, record.update))) record.mutationStamp = 0L
      if (merge(key, result, request.stamp)) {
        for (state in lists.values) {
          if (state.key.chatId == key.chatId && state.key.query.isEmpty() && state.sessions.any { it.live() }) state.ids.add(key)
        }
        invalidateSearch(key.chatId)
      }
      if (record.mutationStamp == 0L && record.dirtyStamp <= request.stamp) pendingTopics.remove(key)
      if (record.mutationStamp != 0L && interested(key)) queueTopic(key)
    } else {
      val error = result as? TdApi.Error ?: TdApi.Error(502, "Invalid forum topic response")
      // One failed/timed-out attempt ends the forced read. Expose the error and leave
      // retries to a new event, reconnect or explicit retry, not an automatic loop.
      if (afterMutation) record.mutationStamp = 0L
      if (afterMutation || request.stamp >= record.fullStamp) {
        record.error = error
        val removed = error.code == 404 && maxOf(record.infoStamp, record.updateStamp, record.dirtyStamp) <= request.stamp
        if (removed) {
          record.value = null
          record.fullStamp = request.stamp
          record.removedStamp = ++sequence
          lists.values.forEach { state ->
            state.ids.remove(key)
            state.pinnedRanks.remove(key)
            if (state.previewDraftKey == key) setPreviewDraftInterest(state, null)
          }
        }
        for (state in lists.values.filter { it.key.chatId == key.chatId }) {
          if (removed) state.topicErrors.remove(key)
          else if (state.key.query.isEmpty() || key in state.ids) state.topicErrors[key] = error
        }
        notifyTopic(key, record)
      }
      // A genuinely newer event still needs reconciliation; an ordinary error never retries itself.
      if ((record.mutationStamp != 0L || record.dirtyStamp > maxOf(request.stamp, record.fullStamp)) && interested(key)) queueTopic(key)
      else pendingTopics.remove(key)
    }
    lists.values.filter { it.key.chatId == key.chatId }.forEach { updateStaleness(it) }
    lists.values.filter { it.key.chatId == key.chatId }.forEach { trimPreview(it) }
    emitChat(key.chatId)
    trim()
    if (pendingTopics.isNotEmpty() || lists.values.any { it.dirty && it.sessions.isNotEmpty() }) scheduleReconciliation()
  }

  private fun notifyTopic(key: Key, record: Record) {
    topicObservers[key]?.toList()?.forEach { it.deliver(record.value, record.error) }
  }

  private fun updateStaleness(state: ListState) {
    fun belongs(key: Key) = key.chatId == state.key.chatId && (state.key.query.isEmpty() || key in state.ids)
    state.stale = !state.initialized || state.dirty || state.error != null || state.topicErrors.isNotEmpty() ||
      state.ids.any { records[it]?.let { record -> record.error != null || record.mutationStamp != 0L || record.dirtyStamp > record.fullStamp } == true } ||
      pendingTopics.any { belongs(it) } || topicRequests.keys.any { belongs(it) }
  }

  private fun snapshot(state: ListState): Snapshot {
    val topics = state.ids.mapNotNull { records[it]?.value }
      // A server page can include pinned topics unrelated to the query. Filter display rows only:
      // raw membership and the server cursor must still advance through such pages.
      .filter { state.key.query.isEmpty() || it.info.name.contains(state.key.query, ignoreCase = true) }
      .sortedWith(compareByDescending<TdApi.ForumTopic> { it.isPinned }
        .thenBy { if (it.isPinned) state.pinnedRanks[Key(it.info.chatId, it.info.forumTopicId)] ?: Int.MAX_VALUE else Int.MAX_VALUE }
        .thenByDescending { it.order }.thenBy { it.info.forumTopicId })
    return Snapshot(state.key, Collections.unmodifiableList(topics), state.totalCount, state.initialized,
      state.request?.kind == Load.INITIAL, state.request?.kind == Load.MORE, state.request?.kind == Load.REFRESH,
      state.endReached, state.stale, state.error ?: state.topicErrors.values.firstOrNull(), state.cursor)
  }

  private fun emit(state: ListState) {
    if (state.sessions.isEmpty()) return
    val value = snapshot(state)
    state.sessions.toList().forEach { if (it.live()) it.deliver(value) }
  }

  private fun emitChat(chatId: Long) = lists.values.filter { it.key.chatId == chatId }.forEach { emit(it) }

  private fun trimPreview(state: ListState): Boolean {
    if (state.sessions.isEmpty() || state.sessions.any { !it.previewOnly }) return false
    val values = state.ids.mapNotNull { records[it]?.value }
    val retained = LinkedHashSet<Key>()
    // Draft priority is independent of last-message timestamps, including much newer messages.
    ForumChatPreview.latestDraft(state.key.chatId, values)?.let { retained.add(Key(it.info.chatId, it.info.forumTopicId)) }
    for (topic in values.sortedWith(ForumChatPreview.CACHE_FIRST)) {
      if (retained.size >= MAX_PREVIEW_TOPICS) break
      retained.add(Key(topic.info.chatId, topic.info.forumTopicId))
    }
    if (retained.size < state.ids.size) state.previewTruncated = true
    val changed = state.ids.retainAll(retained)
    state.pinnedRanks.keys.retainAll(retained)
    return changed
  }

  private fun trim() {
    val inactive = lists.values.filter { it.sessions.isEmpty() }
    for (state in inactive.take((inactive.size - maxInactiveLists).coerceAtLeast(0))) lists.remove(state.key)
    if (records.size <= maxCachedTopics) return
    for (state in lists.values.filter { it.sessions.isEmpty() }) {
      lists.remove(state.key)
      evictUnused()
      if (records.size <= maxCachedTopics) return
    }
    evictUnused()
  }

  private fun evictUnused() {
    val retained = lists.values.flatMap { it.ids }.toHashSet()
    lists.values.mapNotNullTo(retained) { it.previewDraftKey }
    val iterator = records.iterator()
    while (records.size > maxCachedTopics && iterator.hasNext()) {
      val (key, record) = iterator.next()
      // Tombstones also protect any outstanding older page responses.
      val protectedRemoval = record.removedStamp != 0L && lists.values.any { it.request?.stamp?.let { stamp -> stamp < record.removedStamp } == true }
      if (key !in retained && key !in topicRequests && key !in topicObservers && !protectedRemoval) iterator.remove()
    }
  }

  /** A mutation is sent once. Timeout is an uncertain outcome, never an automatic retry. */
  internal fun perform(request: TdApi.Function<*>, chatId: Long, key: Key?, expectedConstructor: Int, changesState: Boolean, callback: (TdApi.Object?, TdApi.Error?) -> Unit) = onOwner {
    val context = epoch
    val topicRead = if (!changesState) request as? TdApi.GetForumTopic else null
    val readStamp = if (topicRead != null) ++sequence else 0L
    var finished = false
    fun complete(result: TdApi.Object) {
      if (finished || context != epoch) return
      finished = true
      val error = result as? TdApi.Error ?: if (result.constructor != expectedConstructor) TdApi.Error(502, "Unexpected forum operation response") else null
      if (error == null && changesState) {
        if (result is TdApi.ForumTopicInfo) updateInfo(result)
        if (key != null) {
          // Prevent an older full fetch from undoing an operation that just succeeded.
          if (key in records || interested(key)) {
            val record = records.getOrPut(key) { Record() }
            record.mutationStamp = ++sequence
            record.fullStamp = record.mutationStamp
          }
          invalidate(key)
        }
        invalidateChatImpl(chatId)
      }
      if (error == null && topicRead != null && result is TdApi.ForumTopic &&
          result.info?.chatId == topicRead.chatId && result.info.forumTopicId == topicRead.forumTopicId) {
        val readKey = Key(topicRead.chatId, topicRead.forumTopicId)
        // A batch can finish without a write when this read already has the desired state.
        // Reconcile that authoritative value, but never let a pre-write read undo the write.
        if (readKey in records || interested(readKey)) {
          val previousInfo = records[readKey]?.value?.info
          if (merge(readKey, result, readStamp)) {
            lists.values.filter { it.key.chatId == readKey.chatId && it.key.query.isEmpty() && it.sessions.any { session -> session.live() } }
              .forEach { it.ids.add(readKey) }
            if (!sameInfo(previousInfo, records[readKey]?.value?.info)) invalidateSearch(readKey.chatId)
          }
          lists.values.filter { it.key.chatId == readKey.chatId }.forEach { trimPreview(it); updateStaleness(it) }
          emitChat(readKey.chatId)
          trim()
          if (pendingTopics.isNotEmpty() || lists.values.any { it.dirty && it.sessions.isNotEmpty() }) scheduleReconciliation()
        }
      }
      backend.publish { if (context == epoch) callback(if (error == null) result else null, error) }
    }
    backend.send(request) { result -> onOwner { complete(result) } }
    backend.schedule(REQUEST_TIMEOUT_MS) { onOwner { complete(TdApi.Error(408, "Forum operation timed out; refresh before retrying")) } }
  }

  companion object {
    const val PREVIEW_ATTACH_DELAY_MS = 150L
    const val PREVIEW_CACHE_MS = 60000L
    const val MAX_PREVIEW_TOPICS = 16
    const val RECONCILE_DELAY_MS = 300L
    const val REQUEST_TIMEOUT_MS = 15000L
    const val MAX_TOPIC_REQUESTS = 4
    const val PAGINATION_ERROR = -1
    @JvmStatic fun normalizeQuery(query: String): String = query.trim()

    private fun sameInfo(a: TdApi.ForumTopicInfo?, b: TdApi.ForumTopicInfo?): Boolean = a === b || a != null && b != null &&
      a.chatId == b.chatId && a.forumTopicId == b.forumTopicId && a.name == b.name &&
      a.icon?.color == b.icon?.color && a.icon?.customEmojiId == b.icon?.customEmojiId &&
      a.creationDate == b.creationDate && a.creatorId.equalsTo(b.creatorId) && a.isGeneral == b.isGeneral &&
      a.isOutgoing == b.isOutgoing && a.isClosed == b.isClosed && a.isHidden == b.isHidden && a.isNameImplicit == b.isNameImplicit

    private fun sameSettings(a: TdApi.ChatNotificationSettings?, b: TdApi.ChatNotificationSettings?): Boolean = a === b || a != null && b != null &&
      a.useDefaultMuteFor == b.useDefaultMuteFor && a.muteFor == b.muteFor && a.useDefaultSound == b.useDefaultSound && a.soundId == b.soundId &&
      a.useDefaultShowPreview == b.useDefaultShowPreview && a.showPreview == b.showPreview && a.useDefaultMuteStories == b.useDefaultMuteStories &&
      a.muteStories == b.muteStories && a.useDefaultStorySound == b.useDefaultStorySound && a.storySoundId == b.storySoundId &&
      a.useDefaultShowStoryPoster == b.useDefaultShowStoryPoster && a.showStoryPoster == b.showStoryPoster &&
      a.useDefaultDisablePinnedMessageNotifications == b.useDefaultDisablePinnedMessageNotifications &&
      a.disablePinnedMessageNotifications == b.disablePinnedMessageNotifications && a.useDefaultDisableMentionNotifications == b.useDefaultDisableMentionNotifications &&
      a.disableMentionNotifications == b.disableMentionNotifications

    @OptIn(kotlin.contracts.ExperimentalContracts::class)
    private fun sameDraft(a: TdApi.DraftMessage?, b: TdApi.DraftMessage?): Boolean = a === b || a != null && b != null &&
      a.date == b.date && a.effectId == b.effectId && a.replyTo.equalsTo(b.replyTo) && a.content.equalsTo(b.content) && a.suggestedPostInfo === b.suggestedPostInfo

    private fun sameUpdate(topic: TdApi.ForumTopic?, update: TdApi.UpdateForumTopic?): Boolean = topic != null && update != null &&
      topic.isPinned == update.isPinned && topic.lastReadInboxMessageId == update.lastReadInboxMessageId && topic.lastReadOutboxMessageId == update.lastReadOutboxMessageId &&
      topic.unreadMentionCount == update.unreadMentionCount && topic.unreadReactionCount == update.unreadReactionCount && topic.unreadPollVoteCount == update.unreadPollVoteCount &&
      sameSettings(topic.notificationSettings, update.notificationSettings) && sameDraft(topic.draftMessage, update.draftMessage)

    private fun copy(topic: TdApi.ForumTopic, info: TdApi.ForumTopicInfo = topic.info) = TdApi.ForumTopic(
      info, topic.lastMessage, topic.order, topic.isPinned, topic.unreadCount,
      topic.lastReadInboxMessageId, topic.lastReadOutboxMessageId, topic.unreadMentionCount,
      topic.unreadReactionCount, topic.unreadPollVoteCount, topic.notificationSettings, topic.draftMessage
    )
    private fun applyUpdate(topic: TdApi.ForumTopic, update: TdApi.UpdateForumTopic) = TdApi.ForumTopic(
      topic.info, topic.lastMessage, topic.order, update.isPinned, topic.unreadCount,
      update.lastReadInboxMessageId, update.lastReadOutboxMessageId, update.unreadMentionCount,
      update.unreadReactionCount, update.unreadPollVoteCount, update.notificationSettings, update.draftMessage
    )
  }
}
