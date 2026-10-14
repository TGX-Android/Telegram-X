package org.thunderdog.challegram.telegram;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.ForumChatPreview;
import org.thunderdog.challegram.telegram.TdlibForumTopicManager.Key;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tgx.td.Td;

/** Account-scoped, in-memory source of truth. TDLib values exposed to consumers are read-only. */
@me.vkryl.annotation.MadeByLLM(models = "gpt-6-astra", ref = "PR #1095")
public final class ForumTopicStore {
  public interface Backend {
    void execute (@NonNull Runnable action);
    void send (@NonNull TdApi.Function<?> request, @NonNull Client.ResultHandler callback);
    void schedule (long delayMs, @NonNull Runnable action);
    void publish (@NonNull Runnable action);
  }

  public static final class ListKey {
    public final long chatId;
    public final String query;

    public ListKey (long chatId, String query) {
      this.chatId = chatId;
      this.query = query;
    }

    public long component1 () { return chatId; }
    public String component2 () { return query; }
    public ListKey copy (long chatId, String query) { return new ListKey(chatId, query); }

    @Override
    public boolean equals (Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof ListKey)) return false;
      ListKey other = (ListKey) obj;
      return chatId == other.chatId && query.equals(other.query);
    }

    @Override
    public int hashCode () {
      return (int) (chatId ^ (chatId >>> 32)) * 31 + query.hashCode();
    }

    @Override
    public String toString () {
      return "ListKey(chatId=" + chatId + ", query=" + query + ")";
    }
  }

  public static final class Cursor {
    public final int date;
    public final long messageId;
    public final int topicId;

    public Cursor () { this(0, 0, 0); }
    public Cursor (int date) { this(date, 0, 0); }
    public Cursor (int date, long messageId) { this(date, messageId, 0); }

    public Cursor (int date, long messageId, int topicId) {
      this.date = date;
      this.messageId = messageId;
      this.topicId = topicId;
    }

    public boolean isEmpty () { return date == 0 && messageId == 0 && topicId == 0; }
    public int component1 () { return date; }
    public long component2 () { return messageId; }
    public int component3 () { return topicId; }
    public Cursor copy (int date, long messageId, int topicId) { return new Cursor(date, messageId, topicId); }

    @Override
    public boolean equals (Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Cursor)) return false;
      Cursor other = (Cursor) obj;
      return date == other.date && messageId == other.messageId && topicId == other.topicId;
    }

    @Override
    public int hashCode () {
      return (date * 31 + (int) (messageId ^ (messageId >>> 32))) * 31 + topicId;
    }

    @Override
    public String toString () {
      return "Cursor(date=" + date + ", messageId=" + messageId + ", topicId=" + topicId + ")";
    }
  }

  public static final class Snapshot {
    public final ListKey key;
    public final List<TdApi.ForumTopic> topics;
    public final int approximateTotalCount;
    public final boolean initialized;
    public final boolean loadingInitial;
    public final boolean loadingMore;
    public final boolean refreshing;
    public final boolean endReached;
    public final boolean stale;
    public final @Nullable TdApi.Error error;
    public final Cursor nextCursor;

    public Snapshot (ListKey key, List<TdApi.ForumTopic> topics, int approximateTotalCount, boolean initialized,
                     boolean loadingInitial, boolean loadingMore, boolean refreshing, boolean endReached,
                     boolean stale, @Nullable TdApi.Error error, Cursor nextCursor) {
      this.key = key;
      this.topics = topics;
      this.approximateTotalCount = approximateTotalCount;
      this.initialized = initialized;
      this.loadingInitial = loadingInitial;
      this.loadingMore = loadingMore;
      this.refreshing = refreshing;
      this.endReached = endReached;
      this.stale = stale;
      this.error = error;
      this.nextCursor = nextCursor;
    }

    public boolean isEmpty () {
      return initialized && topics.isEmpty();
    }
  }

  public interface ListObserver {
    void onSnapshot (@NonNull Snapshot snapshot);
  }

  public interface TopicObserver {
    void onTopic (@Nullable TdApi.ForumTopic topic, @Nullable TdApi.Error error);
  }

  public final class ListSession implements Closeable {
    private ListKey key;
    private final ListObserver observer;
    private final boolean previewOnly;
    private final long context = epoch;
    private volatile boolean disposed;
    private volatile boolean invalidated;
    private volatile long delivery;
    private volatile Snapshot snapshot;

    ListSession (ListKey key, ListObserver observer, boolean previewOnly) {
      this.key = key;
      this.observer = observer;
      this.previewOnly = previewOnly;
      snapshot = cachedSnapshot(key.chatId, key.query);
    }

    public @NonNull Snapshot getSnapshot () {
      return snapshot;
    }

    public void setQuery (String query) {
      onOwner(() -> {
        if (live() && !key.query.equals(normalizeQuery(query))) {
          detach(this);
          key = new ListKey(key.chatId, normalizeQuery(query));
          attach(this);
        }
      });
    }

    public void refresh () {
      onOwner(() -> { if (live()) ForumTopicStore.this.refresh(state(key)); });
    }

    public void loadMore () {
      onOwner(() -> { if (live()) ForumTopicStore.this.loadMore(state(key)); });
    }

    public void retry () {
      onOwner(() -> {
        if (live()) {
          ListState state = state(key);
          if (state.error != null && state.error.code == PAGINATION_ERROR) ForumTopicStore.this.refresh(state);
          else if (state.error != null) requestPage(state, state.retryKind);
          else {
            List<Key> failed = new ArrayList<>();
            for (Map.Entry<Key, Record> entry : records.entrySet()) {
              if (entry.getKey().chatId == key.chatId && entry.getValue().error != null) failed.add(entry.getKey());
            }
            for (Key failedKey : failed) queueTopic(failedKey);
            ForumTopicStore.this.refresh(state);
          }
        }
      });
    }

    @Override
    public void close () {
      disposed = true; // Also cancels UI callbacks that are already queued.
      onOwner(() -> { detach(this); trim(); });
    }

    boolean live () {
      return !disposed && !invalidated && context == epoch;
    }

    void deliver (Snapshot value) {
      deliver(value, false);
    }

    void deliver (Snapshot value, boolean terminal) {
      snapshot = value;
      if (terminal) invalidated = true;
      long ticket = ++delivery;
      backend.publish(() -> {
        if (!disposed && delivery == ticket) observer.onSnapshot(value);
      });
    }
  }

  public final class TopicSubscription implements Closeable {
    private final Key key;
    private final TopicObserver observer;
    private final long context = epoch;
    private volatile boolean closed;
    private volatile long delivery;

    TopicSubscription (Key key, TopicObserver observer) {
      this.key = key;
      this.observer = observer;
    }

    @Override
    public void close () {
      closed = true;
      onOwner(() -> {
        Set<TopicSubscription> observers = topicObservers.get(key);
        if (observers != null) {
          observers.remove(this);
          if (observers.isEmpty()) topicObservers.remove(key);
        }
        trim();
      });
    }

    void deliver (@Nullable TdApi.ForumTopic value, @Nullable TdApi.Error error) {
      long ticket = ++delivery;
      backend.publish(() -> {
        if (!closed && context == epoch && delivery == ticket) observer.onTopic(value, error);
      });
    }
  }

  private enum Load { INITIAL, MORE, REFRESH }

  private static final class PageRequest {
    final long stamp;
    final long epoch;
    final Cursor cursor;
    final Load kind;

    PageRequest (long stamp, long epoch, Cursor cursor, Load kind) {
      this.stamp = stamp;
      this.epoch = epoch;
      this.cursor = cursor;
      this.kind = kind;
    }
  }

  private static final class ListState {
    final ListKey key;
    final LinkedHashSet<Key> ids = new LinkedHashSet<>();
    // ForumTopic.order is activity order, not pinned order. Preserve the server's pin sequence.
    final LinkedHashMap<Key, Integer> pinnedRanks = new LinkedHashMap<>();
    final LinkedHashSet<ListSession> sessions = new LinkedHashSet<>();
    final HashSet<Cursor> seenCursors = new HashSet<>();
    int overlapPages;
    Cursor cursor = new Cursor();
    int totalCount = -1;
    boolean initialized;
    boolean endReached;
    boolean stale = true;
    boolean dirty;
    TdApi.Error error;
    final LinkedHashMap<Key, TdApi.Error> topicErrors = new LinkedHashMap<>();
    Load retryKind = Load.INITIAL;
    PageRequest request;
    boolean previewFresh;
    long previewStamp;
    boolean previewTruncated;
    // One unresolved draft, independent of the loaded page membership in ids.
    Key previewDraftKey;
    int previewDraftDate;

    ListState (ListKey key) {
      this.key = key;
    }
  }

  private static final class Record {
    TdApi.ForumTopic value;
    TdApi.Error error;
    long fullStamp;
    long infoStamp;
    long updateStamp;
    long dirtyStamp;
    long eventStamp;
    // A keyed mutation needs a point read started after its acknowledgement. A cached
    // list page or an action preflight cannot satisfy this independently tracked work.
    long mutationStamp;
    long removedStamp;
    TdApi.ForumTopicInfo info;
    TdApi.UpdateForumTopic update;
  }

  private static final class TopicRequest {
    final long stamp;
    final long epoch;

    TopicRequest (long stamp, long epoch) {
      this.stamp = stamp;
      this.epoch = epoch;
    }
  }

  private final Backend backend;
  private final int pageSize;
  private final int maxCachedTopics;
  private final int maxInactiveLists;
  private final LinkedHashMap<Key, Record> records = new LinkedHashMap<>(16, .75f, true);
  private final LinkedHashMap<ListKey, ListState> lists = new LinkedHashMap<>(16, .75f, true);
  private final HashMap<Key, Set<TopicSubscription>> topicObservers = new HashMap<>();
  private final HashMap<Key, TopicRequest> topicRequests = new HashMap<>();
  private final LinkedHashSet<Key> pendingTopics = new LinkedHashSet<>();
  private long sequence;
  private volatile long epoch;
  private boolean reconciliationScheduled;

  public ForumTopicStore (Backend backend) { this(backend, 50, 512, 8); }
  public ForumTopicStore (Backend backend, int pageSize) { this(backend, pageSize, 512, 8); }
  public ForumTopicStore (Backend backend, int pageSize, int maxCachedTopics) { this(backend, pageSize, maxCachedTopics, 8); }

  public ForumTopicStore (Backend backend, int pageSize, int maxCachedTopics, int maxInactiveLists) {
    if (pageSize < 1 || pageSize > 100 || maxCachedTopics <= 0 || maxInactiveLists < 0) throw new IllegalArgumentException();
    this.backend = backend;
    this.pageSize = pageSize;
    this.maxCachedTopics = maxCachedTopics;
    this.maxInactiveLists = maxInactiveLists;
  }

  private void onOwner (Runnable action) {
    long context = epoch;
    backend.execute(() -> {
      synchronized (this) {
        if (context == epoch) action.run();
      }
    });
  }

  public synchronized @Nullable TdApi.ForumTopic cachedTopic (Key key) {
    Record record = records.get(key);
    return record != null ? record.value : null;
  }

  public synchronized @NonNull Snapshot cachedSnapshot (long chatId, String query) {
    ListKey key = new ListKey(chatId, normalizeQuery(query));
    ListState state = lists.get(key);
    return snapshot(state != null ? state : new ListState(key));
  }

  public @NonNull ListSession openList (long chatId, String query, ListObserver observer) {
    if (chatId == 0) throw new IllegalArgumentException();
    ListSession session = new ListSession(new ListKey(chatId, normalizeQuery(query)), observer, false);
    onOwner(() -> { if (session.live()) attach(session); });
    return session;
  }

  /** Visible chat rows need only a bounded first-page projection. Fast scrolls do not start requests. */
  public @NonNull ListSession openPreview (long chatId, ListObserver observer) {
    if (chatId == 0) throw new IllegalArgumentException();
    ListSession session = new ListSession(new ListKey(chatId, ""), observer, true);
    backend.schedule(PREVIEW_ATTACH_DELAY_MS, () -> onOwner(() -> { if (session.live()) attach(session); }));
    return session;
  }

  public @NonNull TopicSubscription observeTopic (Key key, TopicObserver observer) {
    TopicSubscription subscription = new TopicSubscription(key, observer);
    onOwner(() -> {
      if (!subscription.closed) {
        Set<TopicSubscription> observers = topicObservers.get(key);
        if (observers == null) {
          observers = new LinkedHashSet<>();
          topicObservers.put(key, observers);
        }
        observers.add(subscription);
        Record record = records.get(key);
        if (record != null && record.value != null) subscription.deliver(record.value, null);
        if (record == null || record.value == null || record.error != null || record.mutationStamp != 0 || record.dirtyStamp > record.fullStamp) queueTopic(key);
      }
    });
    return subscription;
  }

  public void retryTopic (Key key) {
    onOwner(() -> { invalidate(key); drainTopics(); });
  }

  /** Reset on both logout and client restart; callbacks from the old epoch cannot refill the cache. */
  public synchronized void reset () {
    epoch++;
    reconciliationScheduled = false;
    records.clear();
    pendingTopics.clear();
    topicRequests.clear();
    for (ListState state : lists.values()) {
      Snapshot empty = snapshot(new ListState(state.key));
      for (ListSession session : state.sessions) session.deliver(empty, true);
    }
    lists.clear();
    for (Set<TopicSubscription> observers : topicObservers.values()) {
      for (TopicSubscription subscription : observers) subscription.closed = true;
    }
    topicObservers.clear();
  }

  private ListState state (ListKey key) {
    ListState state = lists.get(key);
    if (state == null) {
      state = new ListState(key);
      lists.put(key, state);
    }
    return state;
  }

  private Record record (Key key) {
    Record record = records.get(key);
    if (record == null) {
      record = new Record();
      records.put(key, record);
    }
    return record;
  }

  private List<ListState> chatLists (long chatId) {
    List<ListState> result = new ArrayList<>();
    for (ListState state : lists.values()) if (state.key.chatId == chatId) result.add(state);
    return result;
  }

  private static boolean hasLiveSession (ListState state) {
    for (ListSession session : state.sessions) if (session.live()) return true;
    return false;
  }

  private static boolean hasLivePreview (ListState state) {
    for (ListSession session : state.sessions) if (session.previewOnly && session.live()) return true;
    return false;
  }

  private void attach (ListSession session) {
    ListState state = state(session.key);
    boolean wasInactive = state.sessions.isEmpty();
    state.sessions.add(session);
    trimPreview(state);
    session.deliver(snapshot(state));
    if (!session.previewOnly && state.previewTruncated && state.request != null && state.request.kind == Load.MORE) {
      refresh(state); // A pending next page cannot repair an earlier, trimmed prefix.
    } else if (state.request == null) {
      if (!state.initialized) requestPage(state, Load.INITIAL);
      else if (state.stale || !session.previewOnly && state.previewTruncated || wasInactive && (!session.previewOnly || !state.previewFresh)) refresh(state);
    }
    if (session.previewOnly && state.previewDraftKey != null) queueTopic(state.previewDraftKey);
    List<Key> mutations = new ArrayList<>();
    for (Map.Entry<Key, Record> entry : records.entrySet()) {
      if (entry.getKey().chatId == session.key.chatId && entry.getValue().mutationStamp != 0 && interested(entry.getKey())) mutations.add(entry.getKey());
    }
    for (Key key : mutations) queueTopic(key);
    trim();
  }

  private void detach (ListSession session) {
    ListState state = lists.get(session.key);
    if (state != null) {
      if (state.sessions.remove(session) && state.sessions.isEmpty()) {
        if (state.request != null || !session.previewOnly) state.stale = true;
        state.request = null;
      }
      if (trimPreview(state)) emit(state);
    }
  }

  private void refresh (ListState state) {
    state.request = null; // Explicit refresh supersedes an older page.
    requestPage(state, state.initialized ? Load.REFRESH : Load.INITIAL);
  }

  private void loadMore (ListState state) {
    if (state.request != null || state.endReached || state.error != null) return;
    requestPage(state, state.initialized ? Load.MORE : Load.INITIAL);
  }

  private void requestPage (ListState state, Load kind) {
    if (state.request != null) return;
    Cursor cursor = kind == Load.MORE ? state.cursor : new Cursor();
    PageRequest request = new PageRequest(++sequence, epoch, cursor, kind);
    state.request = request;
    state.error = null;
    state.topicErrors.clear();
    state.dirty = false;
    state.retryKind = kind;
    emit(state);
    backend.send(new TdApi.GetForumTopics(state.key.chatId, state.key.query, cursor.date, cursor.messageId, cursor.topicId, pageSize),
      result -> onOwner(() -> finishPage(state, request, result)));
    backend.schedule(REQUEST_TIMEOUT_MS, () -> onOwner(() -> finishPage(state, request, new TdApi.Error(408, "Forum topics request timed out"))));
  }

  private void finishPage (ListState state, PageRequest request, TdApi.Object response) {
    if (request.epoch != epoch || state.request != request) return;
    state.request = null;
    if (response instanceof TdApi.Error) {
      state.error = (TdApi.Error) response;
      state.stale = true;
      state.dirty = false; // No automatic retry loop while offline.
      emit(state);
      if (!pendingTopics.isEmpty()) scheduleReconciliation();
      return;
    }
    TdApi.ForumTopics result = response instanceof TdApi.ForumTopics ? (TdApi.ForumTopics) response : null;
    boolean invalid = result == null || result.topics == null || result.nextOffsetDate < 0 || result.nextOffsetForumTopicId < 0;
    if (!invalid) {
      for (TdApi.ForumTopic topic : result.topics) {
        if (topic == null || topic.info == null || topic.info.chatId != state.key.chatId || topic.info.forumTopicId <= 0) {
          invalid = true;
          break;
        }
      }
    }
    if (invalid) {
      state.error = new TdApi.Error(502, "Invalid forum topics response");
      state.stale = true;
      state.dirty = false;
      emit(state);
      if (!pendingTopics.isEmpty()) scheduleReconciliation();
      return;
    }
    Set<Key> previousIds = new LinkedHashSet<>(state.ids);
    if (request.kind != Load.MORE) {
      // Retain updates arriving after refresh began, but replace old page membership.
      Iterator<Key> iterator = state.ids.iterator();
      while (iterator.hasNext()) {
        Record record = records.get(iterator.next());
        if (record == null || Math.max(Math.max(record.infoStamp, record.updateStamp), Math.max(record.dirtyStamp, record.fullStamp)) <= request.stamp) iterator.remove();
      }
      state.seenCursors.clear();
      state.overlapPages = 0;
      state.pinnedRanks.clear();
      state.previewTruncated = false;
    }
    for (TdApi.ForumTopic topic : result.topics) {
      Key key = new Key(topic.info.chatId, topic.info.forumTopicId);
      TdApi.ForumTopic previous = cachedTopic(key);
      TdApi.ForumTopicInfo previousInfo = previous != null ? previous.info : null;
      if (merge(key, topic, request.stamp)) {
        state.ids.add(key);
        if (topic.isPinned && !state.pinnedRanks.containsKey(key)) {
          int maxRank = -1;
          for (int rank : state.pinnedRanks.values()) maxRank = Math.max(maxRank, rank);
          state.pinnedRanks.put(key, maxRank + 1);
        }
        TdApi.ForumTopic value = cachedTopic(key);
        if (!sameInfo(previousInfo, value != null ? value.info : null)) invalidateSearch(key.chatId, state);
      }
    }
    Cursor next = new Cursor(result.nextOffsetDate, result.nextOffsetMessageId, result.nextOffsetForumTopicId);
    state.totalCount = Math.max(0, result.totalCount);
    state.initialized = true;
    state.previewFresh = true;
    state.previewStamp = request.stamp;
    backend.schedule(PREVIEW_CACHE_MS, () -> onOwner(() -> {
      if (lists.get(state.key) == state && state.previewStamp == request.stamp) state.previewFresh = false;
    }));
    state.endReached = result.topics.length == 0 && next.isEmpty();
    // TDLib may return an overlapping page (notably hidden General) before EOF.
    // Cursor progress, not the number of newly inserted rows, allows the next page.
    // Bound consecutive overlap to keep a changing, broken cursor from looping forever.
    state.overlapPages = request.kind == Load.MORE && result.topics.length != 0 && previousIds.containsAll(state.ids) ? state.overlapPages + 1 : 0;
    if (!state.endReached && (next.isEmpty() || next.equals(request.cursor) || !state.seenCursors.add(next) ||
        state.overlapPages > MAX_OVERLAP_PAGES || result.topics.length == 0)) {
      state.error = new TdApi.Error(PAGINATION_ERROR, "Forum topic pagination made no progress; refresh to retry");
    }
    if (!next.isEmpty()) state.cursor = next;
    for (ListState other : chatLists(state.key.chatId)) {
      trimPreview(other);
      updateStaleness(other);
    }
    emitChat(state.key.chatId);
    trim();
    if (needsReconciliation()) scheduleReconciliation();
  }

  private boolean needsReconciliation () {
    if (!pendingTopics.isEmpty()) return true;
    for (ListState state : lists.values()) if (state.dirty && !state.sessions.isEmpty()) return true;
    return false;
  }

  private boolean merge (Key key, TdApi.ForumTopic incoming, long stamp) {
    Record record = record(key);
    if (record.removedStamp > stamp) return false;
    if (record.fullStamp > stamp) return record.value != null;
    TdApi.ForumTopic value = copy(incoming, incoming.info);
    if (record.infoStamp > stamp) value = copy(value, record.info);
    if (record.updateStamp > stamp) value = applyUpdate(value, record.update);
    record.value = value;
    record.error = null;
    for (ListState state : chatLists(key.chatId)) {
      state.topicErrors.remove(key);
      if (key.equals(state.previewDraftKey)) {
        // A page in any list may resolve the draft. Publish the full value before dropping interest.
        state.ids.add(key);
        setPreviewDraftInterest(state, null);
      }
    }
    record.fullStamp = stamp;
    if (record.mutationStamp == 0 && record.eventStamp <= stamp &&
        (record.infoStamp <= stamp || sameInfo(incoming.info, record.info)) &&
        (record.updateStamp <= stamp || sameUpdate(incoming, record.update))) {
      record.dirtyStamp = Math.min(record.dirtyStamp, stamp);
      pendingTopics.remove(key);
    }
    notifyTopic(key, record);
    if ((record.mutationStamp != 0 || record.dirtyStamp > stamp) && interested(key)) queueTopic(key);
    return true;
  }

  public void updateInfo (TdApi.ForumTopicInfo info) {
    onOwner(() -> {
      Key key = new Key(info.chatId, info.forumTopicId);
      Record previous = records.get(key);
      TdApi.ForumTopicInfo previousInfo = previous != null && previous.value != null ? previous.value.info : null;
      if (previousInfo == null) {
        previous = records.get(key);
        previousInfo = previous != null ? previous.info : null;
      }
      if (sameInfo(previousInfo, info)) return;
      invalidateSearch(info.chatId);
      if (!records.containsKey(key) && !interested(key)) {
        scheduleReconciliation();
        return;
      }
      Record record = record(key);
      record.infoStamp = ++sequence;
      record.info = info;
      if (record.value != null) record.value = copy(record.value, info);
      record.dirtyStamp = sequence;
      notifyTopic(key, record);
      invalidate(key, false);
    });
  }

  public void updateTopic (TdApi.UpdateForumTopic update) {
    onOwner(() -> {
      Key key = new Key(update.chatId, update.forumTopicId);
      TdApi.DraftMessage draft = update.draftMessage;
      // A remote draft can arrive before either topic metadata or its list page. Keep only the
      // newest pending draft interested per visible preview, using the existing bounded hydrator.
      for (ListState state : lists.values()) {
        if (state.key.chatId != key.chatId || !state.key.query.isEmpty()) continue;
        // Clear even offscreen; resolved or cleared drafts must not leave a date threshold behind.
        if (key.equals(state.previewDraftKey) && (!ForumChatPreview.hasTextDraft(draft) || cachedTopic(key) != null)) {
          setPreviewDraftInterest(state, null);
        }
        if (hasLivePreview(state) && cachedTopic(key) == null && draft != null && ForumChatPreview.hasTextDraft(draft) &&
            (key.equals(state.previewDraftKey) || draft.date >= state.previewDraftDate)) {
          setPreviewDraftInterest(state, key, draft.date);
        }
      }
      if (!records.containsKey(key) && !interested(key)) return;
      if (sameUpdate(cachedTopic(key), update)) return;
      Record record = record(key);
      boolean pinChanged = record.value != null && record.value.isPinned != update.isPinned;
      record.updateStamp = ++sequence;
      record.update = update;
      if (record.value != null) record.value = applyUpdate(record.value, update);
      if (ForumChatPreview.hasTextDraft(update.draftMessage) && record.value != null) {
        List<ListState> previews = new ArrayList<>();
        for (ListState state : lists.values()) {
          if (state.key.chatId == key.chatId && state.key.query.isEmpty() && hasLivePreview(state)) previews.add(state);
        }
        for (ListState state : previews) {
          state.ids.add(key);
          trimPreview(state);
        }
      }
      // This update doesn't contain order, lastMessage or unreadCount.
      notifyTopic(key, record);
      invalidate(key, false);
      // A pin event has no position. Reconcile the list, including changes from another client.
      if (pinChanged) invalidateChatImpl(key.chatId);
      trim();
    });
  }

  public void onMessage (TdApi.Message message) {
    onOwner(() -> {
      if (message.topicId instanceof TdApi.MessageTopicForum && message.schedulingState == null) {
        Key key = new Key(message.chatId, ((TdApi.MessageTopicForum) message.topicId).forumTopicId);
        boolean known = records.containsKey(key) || topicObservers.containsKey(key);
        if (known) invalidate(key);
        if (!known || !interested(key)) invalidateChatImpl(message.chatId); // Discover a burst of unseen topics with one list refresh.
      }
    });
  }

  public void onMessageChanged (long chatId, long[] messageIds) {
    onOwner(() -> {
      Set<Long> ids = new HashSet<>();
      for (long id : messageIds) ids.add(id);
      List<Key> keys = new ArrayList<>();
      for (Map.Entry<Key, Record> entry : records.entrySet()) {
        TdApi.ForumTopic topic = entry.getValue().value;
        if (entry.getKey().chatId == chatId && topic != null && topic.lastMessage != null && ids.contains(topic.lastMessage.id)) keys.add(entry.getKey());
      }
      for (Key key : keys) invalidate(key);
      if (keys.size() < ids.size()) invalidateChatImpl(chatId);
    });
  }

  public void invalidateChat (long chatId) {
    onOwner(() -> invalidateChatImpl(chatId));
  }

  public void onConnectionRestored () {
    onOwner(() -> {
      List<ListState> stale = new ArrayList<>();
      for (ListState state : new ArrayList<>(lists.values())) {
        if (state.stale && state.request == null && hasLiveSession(state)) stale.add(state);
      }
      for (ListState state : stale) refresh(state);
      List<Key> drafts = new ArrayList<>();
      for (ListState state : lists.values()) {
        if (hasLivePreview(state) && state.previewDraftKey != null) drafts.add(state.previewDraftKey);
      }
      for (Key key : drafts) queueTopic(key);
      List<Key> staleTopics = new ArrayList<>();
      for (Key key : topicObservers.keySet()) {
        Record record = records.get(key);
        if (record == null || record.error != null || record.mutationStamp != 0 || record.dirtyStamp > record.fullStamp) staleTopics.add(key);
      }
      for (Key key : staleTopics) queueTopic(key);
    });
  }

  private void invalidateChatImpl (long chatId) {
    for (ListState state : lists.values()) {
      if (state.key.chatId == chatId) {
        boolean notify = !state.stale;
        state.stale = true;
        state.dirty = true;
        if (notify) emit(state);
      }
    }
    scheduleReconciliation();
  }

  private void invalidateSearch (long chatId) {
    invalidateSearch(chatId, null);
  }

  private void invalidateSearch (long chatId, @Nullable ListState except) {
    for (ListState state : lists.values()) {
      if (state != except && state.key.chatId == chatId && !state.key.query.isEmpty()) {
        state.stale = true;
        state.dirty = true;
      }
    }
  }

  private void invalidate (Key key) {
    invalidate(key, true);
  }

  private void invalidate (Key key, boolean externalEvent) {
    if (!records.containsKey(key) && !interested(key)) return;
    Record record = record(key);
    record.dirtyStamp = ++sequence;
    if (externalEvent) record.eventStamp = sequence;
    for (ListState state : lists.values()) {
      if (state.key.chatId == key.chatId) {
        // Repeated message invalidations do not change rows. Publish the stale transition once;
        // metadata/counter updates must still publish their new immutable row while already stale.
        boolean notify = !state.stale || !externalEvent;
        state.stale = true;
        if (notify) emit(state);
      }
    }
    if (interested(key)) queueTopic(key);
  }

  private boolean interested (Key key) {
    Set<TopicSubscription> observers = topicObservers.get(key);
    if (observers != null) {
      for (TopicSubscription subscription : observers) if (!subscription.closed) return true;
    }
    for (ListState state : lists.values()) {
      if (state.key.chatId == key.chatId && (state.key.query.isEmpty() || state.ids.contains(key))) {
        for (ListSession session : state.sessions) {
          if (session.live() && (!session.previewOnly || state.ids.contains(key) || key.equals(state.previewDraftKey))) return true;
        }
      }
    }
    return false;
  }

  private void setPreviewDraftInterest (ListState state, @Nullable Key key) {
    setPreviewDraftInterest(state, key, 0);
  }

  private void setPreviewDraftInterest (ListState state, @Nullable Key key, int date) {
    Key previous = state.previewDraftKey;
    state.previewDraftKey = key;
    state.previewDraftDate = key != null ? date : 0;
    // Do not cancel work still needed by a full list or an explicit topic observer.
    if (previous != null && !previous.equals(key) && !interested(previous)) pendingTopics.remove(previous);
  }

  private void queueTopic (Key key) {
    pendingTopics.add(key);
    scheduleReconciliation();
  }

  private void scheduleReconciliation () {
    if (reconciliationScheduled) return;
    reconciliationScheduled = true;
    long context = epoch;
    backend.schedule(RECONCILE_DELAY_MS, () -> onOwner(() -> {
      if (context != epoch) return;
      reconciliationScheduled = false;
      for (ListState state : new ArrayList<>(lists.values())) {
        if (state.dirty && state.request == null && hasLiveSession(state)) refresh(state);
      }
      drainTopics();
    }));
  }

  private void drainTopics () {
    Iterator<Key> iterator = pendingTopics.iterator();
    Map<Key, TopicRequest> requests = new LinkedHashMap<>();
    while (iterator.hasNext() && topicRequests.size() < MAX_TOPIC_REQUESTS) {
      Key key = iterator.next();
      if (!interested(key)) {
        iterator.remove();
        continue;
      }
      if (topicRequests.containsKey(key)) continue;
      Record record = records.get(key);
      if (record != null && record.value != null && record.error == null && record.mutationStamp == 0 && record.dirtyStamp <= record.fullStamp) {
        iterator.remove();
        continue;
      }
      if ((record == null || record.value == null) && (record == null || record.mutationStamp == 0)) {
        boolean pendingList = false;
        for (ListState state : lists.values()) {
          if (state.key.chatId == key.chatId && state.key.query.isEmpty() && state.request != null) {
            pendingList = true;
            break;
          }
        }
        if (pendingList) continue;
      }
      iterator.remove();
      TopicRequest request = new TopicRequest(++sequence, epoch);
      topicRequests.put(key, request);
      requests.put(key, request);
    }
    for (Map.Entry<Key, TopicRequest> entry : requests.entrySet()) {
      Key key = entry.getKey();
      TopicRequest request = entry.getValue();
      backend.send(new TdApi.GetForumTopic(key.chatId, key.forumTopicId), result -> onOwner(() -> finishTopic(key, request, result)));
      backend.schedule(REQUEST_TIMEOUT_MS, () -> onOwner(() -> finishTopic(key, request, new TdApi.Error(408, "Forum topic request timed out"))));
    }
  }

  private void finishTopic (Key key, TopicRequest request, TdApi.Object result) {
    if (request.epoch != epoch || topicRequests.get(key) != request) return;
    topicRequests.remove(key);
    Record record = record(key);
    boolean afterMutation = record.mutationStamp != 0 && request.stamp > record.mutationStamp;
    TdApi.ForumTopic topic = result instanceof TdApi.ForumTopic ? (TdApi.ForumTopic) result : null;
    if (topic != null && topic.info != null && topic.info.chatId == key.chatId && topic.info.forumTopicId == key.forumTopicId) {
      // A superseded response has not reconciled the mutation. Keep its required read
      // queued even if a newer (but cached) page made dirtyStamp <= fullStamp.
      // TDLib can emit metadata from an overlapping older page after this read
      // started. merge() preserves those later events, so the point reply has not
      // reconciled the write unless it agrees with them (or started after them).
      // Otherwise a following cached page could consume the remaining dirty work.
      if (afterMutation && request.stamp >= Math.max(record.fullStamp, record.removedStamp) &&
          record.eventStamp <= request.stamp &&
          (record.infoStamp <= request.stamp || sameInfo(topic.info, record.info)) &&
          (record.updateStamp <= request.stamp || sameUpdate(topic, record.update))) record.mutationStamp = 0;
      if (merge(key, topic, request.stamp)) {
        for (ListState state : lists.values()) {
          if (state.key.chatId == key.chatId && state.key.query.isEmpty() && hasLiveSession(state)) state.ids.add(key);
        }
        invalidateSearch(key.chatId);
      }
      if (record.mutationStamp == 0 && record.dirtyStamp <= request.stamp) pendingTopics.remove(key);
      if (record.mutationStamp != 0 && interested(key)) queueTopic(key);
    } else {
      TdApi.Error error = result instanceof TdApi.Error ? (TdApi.Error) result : new TdApi.Error(502, "Invalid forum topic response");
      // One failed/timed-out attempt ends the forced read. Expose the error and leave
      // retries to a new event, reconnect or explicit retry, not an automatic loop.
      if (afterMutation) record.mutationStamp = 0;
      if (afterMutation || request.stamp >= record.fullStamp) {
        record.error = error;
        boolean removed = error.code == 404 && Math.max(Math.max(record.infoStamp, record.updateStamp), record.dirtyStamp) <= request.stamp;
        if (removed) {
          record.value = null;
          record.fullStamp = request.stamp;
          record.removedStamp = ++sequence;
          for (ListState state : lists.values()) {
            state.ids.remove(key);
            state.pinnedRanks.remove(key);
            if (key.equals(state.previewDraftKey)) setPreviewDraftInterest(state, null);
          }
        }
        for (ListState state : chatLists(key.chatId)) {
          if (removed) state.topicErrors.remove(key);
          else if (state.key.query.isEmpty() || state.ids.contains(key)) state.topicErrors.put(key, error);
        }
        notifyTopic(key, record);
      }
      // A genuinely newer event still needs reconciliation; an ordinary error never retries itself.
      if ((record.mutationStamp != 0 || record.dirtyStamp > Math.max(request.stamp, record.fullStamp)) && interested(key)) queueTopic(key);
      else pendingTopics.remove(key);
    }
    for (ListState state : chatLists(key.chatId)) updateStaleness(state);
    for (ListState state : chatLists(key.chatId)) trimPreview(state);
    emitChat(key.chatId);
    trim();
    if (needsReconciliation()) scheduleReconciliation();
  }

  private void notifyTopic (Key key, Record record) {
    Set<TopicSubscription> observers = topicObservers.get(key);
    if (observers != null) {
      for (TopicSubscription subscription : new ArrayList<>(observers)) subscription.deliver(record.value, record.error);
    }
  }

  private static boolean belongs (ListState state, Key key) {
    return key.chatId == state.key.chatId && (state.key.query.isEmpty() || state.ids.contains(key));
  }

  private void updateStaleness (ListState state) {
    boolean stale = !state.initialized || state.dirty || state.error != null || !state.topicErrors.isEmpty();
    if (!stale) {
      for (Key key : state.ids) {
        Record record = records.get(key);
        if (record != null && (record.error != null || record.mutationStamp != 0 || record.dirtyStamp > record.fullStamp)) {
          stale = true;
          break;
        }
      }
    }
    if (!stale) {
      for (Key key : pendingTopics) {
        if (belongs(state, key)) {
          stale = true;
          break;
        }
      }
    }
    if (!stale) {
      for (Key key : topicRequests.keySet()) {
        if (belongs(state, key)) {
          stale = true;
          break;
        }
      }
    }
    state.stale = stale;
  }

  private Snapshot snapshot (ListState state) {
    List<TdApi.ForumTopic> topics = new ArrayList<>();
    // Resolve all raw members before filtering, preserving the access-ordered cache behavior.
    for (Key key : state.ids) {
      TdApi.ForumTopic value = cachedTopic(key);
      if (value != null) topics.add(value);
    }
    // A server page can include pinned topics unrelated to the query. Filter display rows only:
    // raw membership and the server cursor must still advance through such pages.
    if (!state.key.query.isEmpty()) {
      Iterator<TdApi.ForumTopic> iterator = topics.iterator();
      while (iterator.hasNext()) {
        if (!containsIgnoreCase(iterator.next().info.name, state.key.query)) iterator.remove();
      }
    }
    topics.sort((a, b) -> {
      int result = Boolean.compare(b.isPinned, a.isPinned);
      if (result == 0) result = Integer.compare(pinRank(state, a), pinRank(state, b));
      if (result == 0) result = Long.compare(b.order, a.order);
      if (result == 0) result = Integer.compare(a.info.forumTopicId, b.info.forumTopicId);
      return result;
    });
    TdApi.Error error = state.error;
    if (error == null && !state.topicErrors.isEmpty()) error = state.topicErrors.values().iterator().next();
    return new Snapshot(state.key, Collections.unmodifiableList(topics), state.totalCount, state.initialized,
      state.request != null && state.request.kind == Load.INITIAL, state.request != null && state.request.kind == Load.MORE,
      state.request != null && state.request.kind == Load.REFRESH, state.endReached, state.stale, error, state.cursor);
  }

  private static int pinRank (ListState state, TdApi.ForumTopic topic) {
    if (!topic.isPinned) return Integer.MAX_VALUE;
    Integer rank = state.pinnedRanks.get(new Key(topic.info.chatId, topic.info.forumTopicId));
    return rank != null ? rank : Integer.MAX_VALUE;
  }

  private static boolean containsIgnoreCase (String value, String query) {
    for (int i = 0; i <= value.length() - query.length(); i++) {
      if (value.regionMatches(true, i, query, 0, query.length())) return true;
    }
    return false;
  }

  private void emit (ListState state) {
    if (state.sessions.isEmpty()) return;
    Snapshot value = snapshot(state);
    for (ListSession session : new ArrayList<>(state.sessions)) if (session.live()) session.deliver(value);
  }

  private void emitChat (long chatId) {
    for (ListState state : chatLists(chatId)) emit(state);
  }

  private boolean trimPreview (ListState state) {
    if (state.sessions.isEmpty()) return false;
    for (ListSession session : state.sessions) if (!session.previewOnly) return false;
    List<TdApi.ForumTopic> values = new ArrayList<>();
    for (Key key : state.ids) {
      TdApi.ForumTopic value = cachedTopic(key);
      if (value != null) values.add(value);
    }
    LinkedHashSet<Key> retained = new LinkedHashSet<>();
    // Draft priority is independent of last-message timestamps, including much newer messages.
    TdApi.ForumTopic draft = ForumChatPreview.latestDraft(state.key.chatId, values);
    if (draft != null) retained.add(new Key(draft.info.chatId, draft.info.forumTopicId));
    values.sort(ForumChatPreview.CACHE_FIRST);
    for (TdApi.ForumTopic topic : values) {
      if (retained.size() >= MAX_PREVIEW_TOPICS) break;
      retained.add(new Key(topic.info.chatId, topic.info.forumTopicId));
    }
    if (retained.size() < state.ids.size()) state.previewTruncated = true;
    boolean changed = state.ids.retainAll(retained);
    state.pinnedRanks.keySet().retainAll(retained);
    return changed;
  }

  private List<ListState> inactiveLists () {
    List<ListState> inactive = new ArrayList<>();
    for (ListState state : lists.values()) if (state.sessions.isEmpty()) inactive.add(state);
    return inactive;
  }

  private void trim () {
    List<ListState> inactive = inactiveLists();
    int removeCount = Math.max(0, inactive.size() - maxInactiveLists);
    for (int i = 0; i < removeCount; i++) lists.remove(inactive.get(i).key);
    if (records.size() <= maxCachedTopics) return;
    for (ListState state : inactiveLists()) {
      lists.remove(state.key);
      evictUnused();
      if (records.size() <= maxCachedTopics) return;
    }
    evictUnused();
  }

  private void evictUnused () {
    Set<Key> retained = new HashSet<>();
    for (ListState state : lists.values()) retained.addAll(state.ids);
    for (ListState state : lists.values()) if (state.previewDraftKey != null) retained.add(state.previewDraftKey);
    Iterator<Map.Entry<Key, Record>> iterator = records.entrySet().iterator();
    while (records.size() > maxCachedTopics && iterator.hasNext()) {
      Map.Entry<Key, Record> entry = iterator.next();
      Key key = entry.getKey();
      Record record = entry.getValue();
      // Tombstones also protect any outstanding older page responses.
      boolean protectedRemoval = false;
      if (record.removedStamp != 0) {
        for (ListState state : lists.values()) {
          if (state.request != null && state.request.stamp < record.removedStamp) {
            protectedRemoval = true;
            break;
          }
        }
      }
      if (!retained.contains(key) && !topicRequests.containsKey(key) && !topicObservers.containsKey(key) && !protectedRemoval) iterator.remove();
    }
  }

  interface OperationCallback {
    void onResult (@Nullable TdApi.Object value, @Nullable TdApi.Error error);
  }

  /** A mutation is sent once. Timeout is an uncertain outcome, never an automatic retry. */
  void perform (TdApi.Function<?> request, long chatId, @Nullable Key key, int expectedConstructor, boolean changesState, OperationCallback callback) {
    onOwner(() -> {
      long context = epoch;
      TdApi.GetForumTopic topicRead = !changesState && request instanceof TdApi.GetForumTopic ? (TdApi.GetForumTopic) request : null;
      long readStamp = topicRead != null ? ++sequence : 0;
      class Completion {
        boolean finished;

        void complete (TdApi.Object result) {
          if (finished || context != epoch) return;
          finished = true;
          TdApi.Error error = result instanceof TdApi.Error ? (TdApi.Error) result :
            result.getConstructor() != expectedConstructor ? new TdApi.Error(502, "Unexpected forum operation response") : null;
          if (error == null && changesState) {
            if (result instanceof TdApi.ForumTopicInfo) updateInfo((TdApi.ForumTopicInfo) result);
            if (key != null) {
              // Prevent an older full fetch from undoing an operation that just succeeded.
              if (records.containsKey(key) || interested(key)) {
                Record record = record(key);
                record.mutationStamp = ++sequence;
                record.fullStamp = record.mutationStamp;
              }
              invalidate(key);
            }
            invalidateChatImpl(chatId);
          }
          TdApi.ForumTopic topic = result instanceof TdApi.ForumTopic ? (TdApi.ForumTopic) result : null;
          if (error == null && topicRead != null && topic != null && topic.info != null &&
              topic.info.chatId == topicRead.chatId && topic.info.forumTopicId == topicRead.forumTopicId) {
            Key readKey = new Key(topicRead.chatId, topicRead.forumTopicId);
            // A batch can finish without a write when this read already has the desired state.
            // Reconcile that authoritative value, but never let a pre-write read undo the write.
            if (records.containsKey(readKey) || interested(readKey)) {
              TdApi.ForumTopic previous = cachedTopic(readKey);
              TdApi.ForumTopicInfo previousInfo = previous != null ? previous.info : null;
              if (merge(readKey, topic, readStamp)) {
                List<ListState> active = new ArrayList<>();
                for (ListState state : lists.values()) {
                  if (state.key.chatId == readKey.chatId && state.key.query.isEmpty() && hasLiveSession(state)) active.add(state);
                }
                for (ListState state : active) state.ids.add(readKey);
                TdApi.ForumTopic value = cachedTopic(readKey);
                if (!sameInfo(previousInfo, value != null ? value.info : null)) invalidateSearch(readKey.chatId);
              }
              for (ListState state : chatLists(readKey.chatId)) {
                trimPreview(state);
                updateStaleness(state);
              }
              emitChat(readKey.chatId);
              trim();
              if (needsReconciliation()) scheduleReconciliation();
            }
          }
          backend.publish(() -> {
            if (context == epoch) callback.onResult(error == null ? result : null, error);
          });
        }
      }
      Completion completion = new Completion();
      backend.send(request, result -> onOwner(() -> completion.complete(result)));
      backend.schedule(REQUEST_TIMEOUT_MS, () -> onOwner(() -> completion.complete(new TdApi.Error(408, "Forum operation timed out; refresh before retrying"))));
    });
  }

  public static final long PREVIEW_ATTACH_DELAY_MS = 150L;
  public static final long PREVIEW_CACHE_MS = 60000L;
  public static final int MAX_PREVIEW_TOPICS = 16;
  public static final long RECONCILE_DELAY_MS = 300L;
  public static final long REQUEST_TIMEOUT_MS = 15000L;
  public static final int MAX_TOPIC_REQUESTS = 4;
  public static final int PAGINATION_ERROR = -1;
  public static final int MAX_OVERLAP_PAGES = 8;

  public static String normalizeQuery (String query) {
    // Kotlin trim() includes Unicode space characters which String.trim() does not.
    int start = 0;
    int end = query.length();
    while (start < end && isWhitespace(query.charAt(start))) start++;
    while (end > start && isWhitespace(query.charAt(end - 1))) end--;
    return query.substring(start, end);
  }

  private static boolean isWhitespace (char value) {
    return Character.isWhitespace(value) || Character.isSpaceChar(value);
  }

  private static boolean sameInfo (@Nullable TdApi.ForumTopicInfo a, @Nullable TdApi.ForumTopicInfo b) {
    return a == b || a != null && b != null &&
      a.chatId == b.chatId && a.forumTopicId == b.forumTopicId && (a.name == b.name || a.name != null && a.name.equals(b.name)) &&
      (a.icon == b.icon || a.icon != null && b.icon != null && a.icon.color == b.icon.color && a.icon.customEmojiId == b.icon.customEmojiId) &&
      a.creationDate == b.creationDate && Td.equalsTo(a.creatorId, b.creatorId) && a.isGeneral == b.isGeneral &&
      a.isOutgoing == b.isOutgoing && a.isClosed == b.isClosed && a.isHidden == b.isHidden && a.isNameImplicit == b.isNameImplicit;
  }

  private static boolean sameSettings (@Nullable TdApi.ChatNotificationSettings a, @Nullable TdApi.ChatNotificationSettings b) {
    return a == b || a != null && b != null &&
      a.useDefaultMuteFor == b.useDefaultMuteFor && a.muteFor == b.muteFor && a.useDefaultSound == b.useDefaultSound && a.soundId == b.soundId &&
      a.useDefaultShowPreview == b.useDefaultShowPreview && a.showPreview == b.showPreview && a.useDefaultMuteStories == b.useDefaultMuteStories &&
      a.muteStories == b.muteStories && a.useDefaultStorySound == b.useDefaultStorySound && a.storySoundId == b.storySoundId &&
      a.useDefaultShowStoryPoster == b.useDefaultShowStoryPoster && a.showStoryPoster == b.showStoryPoster &&
      a.useDefaultDisablePinnedMessageNotifications == b.useDefaultDisablePinnedMessageNotifications &&
      a.disablePinnedMessageNotifications == b.disablePinnedMessageNotifications && a.useDefaultDisableMentionNotifications == b.useDefaultDisableMentionNotifications &&
      a.disableMentionNotifications == b.disableMentionNotifications;
  }

  private static boolean sameDraft (@Nullable TdApi.DraftMessage a, @Nullable TdApi.DraftMessage b) {
    return a == b || a != null && b != null && a.date == b.date && a.effectId == b.effectId &&
      Td.equalsTo(a.replyTo, b.replyTo) && Td.equalsTo(a.content, b.content) && a.suggestedPostInfo == b.suggestedPostInfo;
  }

  private static boolean sameUpdate (@Nullable TdApi.ForumTopic topic, @Nullable TdApi.UpdateForumTopic update) {
    return topic != null && update != null &&
      topic.isPinned == update.isPinned && topic.lastReadInboxMessageId == update.lastReadInboxMessageId && topic.lastReadOutboxMessageId == update.lastReadOutboxMessageId &&
      topic.unreadMentionCount == update.unreadMentionCount && topic.unreadReactionCount == update.unreadReactionCount && topic.unreadPollVoteCount == update.unreadPollVoteCount &&
      sameSettings(topic.notificationSettings, update.notificationSettings) && sameDraft(topic.draftMessage, update.draftMessage);
  }

  private static TdApi.ForumTopic copy (TdApi.ForumTopic topic, TdApi.ForumTopicInfo info) {
    return new TdApi.ForumTopic(info, topic.lastMessage, topic.order, topic.isPinned, topic.unreadCount,
      topic.lastReadInboxMessageId, topic.lastReadOutboxMessageId, topic.unreadMentionCount,
      topic.unreadReactionCount, topic.unreadPollVoteCount, topic.notificationSettings, topic.draftMessage);
  }

  private static TdApi.ForumTopic applyUpdate (TdApi.ForumTopic topic, TdApi.UpdateForumTopic update) {
    return new TdApi.ForumTopic(topic.info, topic.lastMessage, topic.order, update.isPinned, topic.unreadCount,
      update.lastReadInboxMessageId, update.lastReadOutboxMessageId, update.unreadMentionCount,
      update.unreadReactionCount, update.unreadPollVoteCount, update.notificationSettings, update.draftMessage);
  }
}
