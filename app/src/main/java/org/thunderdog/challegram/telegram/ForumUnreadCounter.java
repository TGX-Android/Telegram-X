package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.TdApi;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Account-scoped badge projection, not a message cache. Only complete unfiltered walks may
 * replace the total. In particular, the sixteen-topic chat preview is never a count source. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
public final class ForumUnreadCounter {
  public interface Observer {
    void onUnreadChanged (int unreadTopics);
  }

  public final class Subscription implements Closeable {
    private final long chatId;
    private final Observer observer;
    private final long context = epoch;
    private volatile boolean closed;

    Subscription (long chatId, Observer observer) {
      this.chatId = chatId;
      this.observer = observer;
    }

    public long getChatId () {
      return chatId;
    }

    boolean live () {
      return !closed && context == epoch;
    }

    void deliver (int count) {
      backend.publish(() -> {
        if (live()) {
          observer.onUnreadChanged(count);
        }
      });
    }

    @Override
    public void close () {
      closed = true;
      owner(() -> {
        State state = states.get(chatId);
        if (state != null) {
          state.observers.remove(this);
          if (!state.visible()) {
            state.scan = null;
            queue.remove(state);
          }
        }
        trim();
      });
    }
  }

  private static final class ReadState {
    final long lastMessage;
    final long inbox;
    final boolean unread;

    ReadState (long lastMessage, long inbox, boolean unread) {
      this.lastMessage = lastMessage;
      this.inbox = inbox;
      this.unread = unread;
    }

    ReadState readThrough (long id) {
      return new ReadState(lastMessage, Math.max(inbox, id), unread && !(lastMessage > 0 && id >= lastMessage));
    }
  }

  private static final class Scan {
    final long revision;
    final HashMap<Integer, ReadState> topics = new HashMap<>();
    final HashSet<ForumTopicStore.Cursor> cursors = new HashSet<>();
    int overlapPages;
    ForumTopicStore.Cursor cursor = new ForumTopicStore.Cursor();

    Scan (long revision) {
      this.revision = revision;
    }
  }

  private final class State {
    final long chatId;
    final LinkedHashSet<Subscription> observers = new LinkedHashSet<>();
    Map<Integer, ReadState> topics;
    // High-water marks also protect a late page from a newer remote read event.
    final HashMap<Integer, Long> reads = new HashMap<>();
    int count = UNKNOWN;
    long revision;
    boolean fresh;
    long expiry;
    Scan scan;
    boolean pending;

    State (long chatId) {
      this.chatId = chatId;
    }

    boolean visible () {
      for (Subscription subscription : observers) {
        if (subscription.live()) {
          return true;
        }
      }
      return false;
    }
  }

  private final ForumTopicStore.Backend backend;
  private final LinkedHashMap<Long, State> states = new LinkedHashMap<>(16, .75f, true);
  private final LinkedHashSet<State> queue = new LinkedHashSet<>();
  private volatile long epoch;
  private boolean enabled;
  private int inFlight;
  private boolean drainScheduled;

  public ForumUnreadCounter (ForumTopicStore.Backend backend) {
    this(backend, true);
  }

  public ForumUnreadCounter (ForumTopicStore.Backend backend, boolean initiallyEnabled) {
    this.backend = backend;
    this.enabled = initiallyEnabled;
  }

  private void owner (Runnable action) {
    long context = epoch;
    backend.execute(() -> {
      synchronized (this) {
        if (context == epoch) {
          action.run();
        }
      }
    });
  }

  public synchronized int cachedCount (long chatId) {
    State state = states.get(chatId);
    return state != null ? state.count : UNKNOWN;
  }

  public Subscription observe (long chatId, Observer observer) {
    if (chatId == 0) {
      throw new IllegalArgumentException();
    }
    Subscription subscription = new Subscription(chatId, observer);
    backend.schedule(ATTACH_DELAY_MS, () -> owner(() -> {
      if (subscription.live()) {
        State state = states.get(chatId);
        if (state == null) {
          state = new State(chatId);
          states.put(chatId, state);
        }
        state.observers.add(subscription);
        subscription.deliver(state.count);
        if (!state.fresh) {
          enqueue(state);
        }
        trim();
      }
    }));
    return subscription;
  }

  /** Reconcile new/deleted messages and explicit chat-read events without marking anything read. */
  public void invalidate (long chatId) {
    owner(() -> {
      State state = states.get(chatId);
      if (state != null) {
        state.revision++;
        state.fresh = false;
        enqueue(state);
      }
    });
  }

  public void onTopicRead (long chatId, int topicId, long inbox) {
    owner(() -> {
      if (inbox <= 0) {
        return;
      }
      State state = states.get(chatId);
      if (state == null) {
        return;
      }
      Long previous = state.reads.get(topicId);
      if (previous == null && state.topics != null) {
        ReadState value = state.topics.get(topicId);
        if (value != null) {
          previous = value.inbox;
        }
      }
      if (previous == null && state.scan != null) {
        ReadState value = state.scan.topics.get(topicId);
        if (value != null) {
          previous = value.inbox;
        }
      }
      if (previous != null && inbox <= previous) {
        return;
      }
      state.reads.put(topicId, inbox);
      ReadState value = state.topics != null ? state.topics.get(topicId) : null;
      if (value != null && state.fresh) {
        state.topics = new LinkedHashMap<>(state.topics);
        state.topics.put(topicId, value.readThrough(inbox));
        publishCount(state);
      }
      // GetForumTopics itself can announce an as-yet unknown topic before delivering its page.
      // Do not invalidate that initial scan for these announcements; merge the read watermark.
      if (previous != null || state.scan == null) {
        state.revision++;
        state.fresh = false;
        enqueue(state);
      }
    });
  }

  public void refreshVisible () {
    owner(() -> {
      for (State state : new ArrayList<>(states.values())) {
        state.fresh = false;
        state.revision++;
        enqueue(state);
      }
    });
  }

  /** No pagination/polling while the application is in the background. */
  public void setEnabled (boolean value) {
    owner(() -> {
      if (enabled != value) {
        enabled = value;
        if (!value) {
          queue.clear();
          for (State state : states.values()) {
            state.scan = null;
            state.fresh = false;
          }
        } else {
          for (State state : new ArrayList<>(states.values())) {
            enqueue(state);
          }
        }
      }
    });
  }

  public synchronized void reset () {
    epoch++;
    states.clear();
    queue.clear();
    inFlight = 0;
    drainScheduled = false;
  }

  private void enqueue (State state) {
    if (!enabled || !state.visible()) {
      return;
    }
    queue.add(state);
    scheduleDrain(RECONCILE_DELAY_MS);
  }

  private void scheduleDrain (long delay) {
    if (drainScheduled) {
      return;
    }
    drainScheduled = true;
    long context = epoch;
    backend.schedule(delay, () -> owner(() -> {
      if (context != epoch) {
        return;
      }
      drainScheduled = false;
      drain();
    }));
  }

  private void drain () {
    if (!enabled) {
      return;
    }
    // One page per chat, at most two requests per account, round-robin between visible chats.
    for (State state : new ArrayList<>(queue)) {
      if (inFlight >= MAX_REQUESTS) {
        break;
      }
      if (!state.visible()) {
        queue.remove(state);
        continue;
      }
      if (state.pending) {
        continue;
      }
      queue.remove(state);
      if (state.scan == null) {
        state.scan = new Scan(state.revision);
      }
      Scan scan = state.scan;
      ForumTopicStore.Cursor cursor = scan.cursor;
      long context = epoch;
      state.pending = true;
      inFlight++;
      class Completion {
        boolean finished;

        void complete (TdApi.Object result) {
          owner(() -> {
            if (finished || context != epoch) {
              return;
            }
            finished = true;
            inFlight--;
            state.pending = false;
            if (states.get(state.chatId) == state && state.scan == scan && state.visible() && enabled) {
              finishPage(state, scan, cursor, result);
            }
            if (!queue.isEmpty()) {
              scheduleDrain(0);
            }
          });
        }
      }
      Completion completion = new Completion();
      backend.send(new TdApi.GetForumTopics(state.chatId, "", cursor.date, cursor.messageId, cursor.topicId, PAGE_SIZE), completion::complete);
      backend.schedule(REQUEST_TIMEOUT_MS, () -> completion.complete(new TdApi.Error(408, "Forum badge request timed out")));
    }
  }

  private void fail (State state) {
    state.scan = null;
    state.fresh = false;
    state.expiry++;
    queue.remove(state);
    // Keep the previous complete total, never publish a partial zero. No automatic error loop;
    // reconnect, reattach, foreground or a new event can retry.
  }

  private void finishPage (State state, Scan scan, ForumTopicStore.Cursor cursor, TdApi.Object response) {
    if (!(response instanceof TdApi.ForumTopics)) {
      fail(state);
      return;
    }
    TdApi.ForumTopics result = (TdApi.ForumTopics) response;
    if (result.topics == null || result.nextOffsetDate < 0 || result.nextOffsetForumTopicId < 0) {
      fail(state);
      return;
    }
    for (TdApi.ForumTopic topic : result.topics) {
      if (topic == null || topic.info == null || topic.info.chatId != state.chatId || topic.info.forumTopicId <= 0) {
        fail(state);
        return;
      }
    }
    ForumTopicStore.Cursor next = new ForumTopicStore.Cursor(result.nextOffsetDate, result.nextOffsetMessageId, result.nextOffsetForumTopicId);
    if (result.topics.length == 0 && next.isEmpty()) {
      state.scan = null;
      if (scan.revision != state.revision) {
        enqueue(state);
        return;
      }
      state.topics = scan.topics;
      state.reads.keySet().retainAll(scan.topics.keySet());
      state.fresh = true;
      publishCount(state);
      queue.remove(state);
      long expiry = ++state.expiry;
      long context = epoch;
      // TDLib can omit read events for roots absent from its message cache. While visible,
      // periodically reconcile with authoritative pages; never rely on such an event alone.
      backend.schedule(REFRESH_MS, () -> owner(() -> {
        if (context == epoch && states.get(state.chatId) == state && state.expiry == expiry) {
          state.fresh = false;
          enqueue(state);
        }
      }));
      return;
    }
    int oldSize = scan.topics.size();
    for (TdApi.ForumTopic topic : result.topics) {
      ReadState value = new ReadState(topic.lastMessage != null ? topic.lastMessage.id : 0, topic.lastReadInboxMessageId, topic.unreadCount > 0);
      Long read = state.reads.get(topic.info.forumTopicId);
      if (read != null && read > value.inbox) {
        value = value.readThrough(read);
      }
      scan.topics.put(topic.info.forumTopicId, value);
    }
    // Hidden General and other overlaps can advance the cursor without adding a topic.
    // Keep walking to authoritative EOF, with the same bounded overlap as the list.
    scan.overlapPages = scan.topics.size() == oldSize ? scan.overlapPages + 1 : 0;
    if (result.topics.length == 0 || next.isEmpty() || next.equals(cursor) || !scan.cursors.add(next) ||
        scan.overlapPages > ForumTopicStore.MAX_OVERLAP_PAGES || scan.topics.size() > MAX_TOPICS) {
      fail(state);
      return;
    }
    scan.cursor = next;
    queue.add(state);
  }

  private void publishCount (State state) {
    int count = UNKNOWN;
    if (state.topics != null) {
      count = 0;
      for (ReadState value : state.topics.values()) {
        if (value.unread) {
          count++;
        }
      }
    }
    if (state.count != count) {
      state.count = count;
      for (Subscription subscription : new ArrayList<>(state.observers)) {
        if (subscription.live()) {
          subscription.deliver(count);
        }
      }
    }
  }

  private void trim () {
    List<State> inactive = new ArrayList<>();
    for (State state : states.values()) {
      if (!state.visible() && !state.pending) {
        inactive.add(state);
      }
    }
    int removeCount = Math.max(0, inactive.size() - MAX_INACTIVE_CHATS);
    for (int i = 0; i < removeCount; i++) {
      states.remove(inactive.get(i).chatId);
    }
  }

  public static final int UNKNOWN = -1;
  public static final long ATTACH_DELAY_MS = 150L;
  public static final long RECONCILE_DELAY_MS = 300L;
  public static final long REFRESH_MS = 60000L;
  public static final long REQUEST_TIMEOUT_MS = 15000L;
  public static final int PAGE_SIZE = 100;
  public static final int MAX_REQUESTS = 2;
  public static final int MAX_TOPICS = 10000;
  public static final int MAX_INACTIVE_CHATS = 32;

  /** Unknown is an unread dot, not a made-up topic count or a prematurely cleared badge. */
  public static int badgeCount (int unreadTopics, int unreadMessages, boolean markedUnread) {
    if (unreadTopics > 0) {
      return unreadTopics;
    }
    if (markedUnread || unreadTopics == UNKNOWN && unreadMessages > 0) {
      return Tdlib.CHAT_MARKED_AS_UNREAD;
    }
    return 0;
  }
}
