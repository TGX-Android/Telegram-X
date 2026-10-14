package org.thunderdog.challegram.telegram

import me.vkryl.core.reference.ReferenceMap
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.TdApi.*
import org.thunderdog.challegram.tool.UI

/** TDLib/UI-thread adapter for the account's single forum topic store. */
class TdlibForumTopicManager(private val tdlib: Tdlib) : CleanupStartupDelegate, MessageListener, ChatListener, ConnectionListener, UI.StateListener {
  data class Key(
    @JvmField val chatId: Long,
    @JvmField val forumTopicId: Int
  )
  class Entry : TdlibDataManager.AbstractEntry<Key, ForumTopic> {
    @JvmField val forumTopicId: Int

    constructor(key: Key, value: ForumTopic?, error: Error?) : super(key, value, error) {
      this.forumTopicId = key.forumTopicId
    }
  }

  private val backend = object : ForumTopicStore.Backend {
    override fun execute(action: () -> Unit) = tdlib.executeOnTdlibThread(action)
    override fun send(request: TdApi.Function<*>, callback: (TdApi.Object) -> Unit) = tdlib.client().send(request, Client.ResultHandler { callback(it) })
    override fun schedule(delayMs: Long, action: () -> Unit) = tdlib.runOnTdlibThread(action, delayMs / 1000.0, false)
    override fun publish(action: () -> Unit) = tdlib.runOnUiThread(action)
  }
  private val store = ForumTopicStore(backend)
  private val unread = ForumUnreadCounter(backend, UI.getUiState() == UI.State.RESUMED)
  @JvmField val actions = ForumTopicActions(store)
  private val observers = ReferenceMap<Key, Observer>(true)
  private val subscriptions = HashMap<Key, ForumTopicStore.TopicSubscription>()

  init {
    tdlib.listeners().addCleanupListener(this)
    tdlib.listeners().subscribeForGlobalUpdates(this)
    UI.addStateListener(this)
  }

  fun openList(chatId: Long, query: String, observer: ForumTopicStore.ListObserver) = store.openList(chatId, query, observer)
  fun openPreview(chatId: Long, observer: ForumTopicStore.ListObserver) = store.openPreview(chatId, observer)
  fun observeTopic(key: Key, observer: ForumTopicStore.TopicObserver) = store.observeTopic(key, observer)
  fun cachedList(chatId: Long, query: String) = store.cachedSnapshot(chatId, query)
  fun find(key: Key): Entry? = store.cachedTopic(key)?.let { Entry(key, it, null) }
  fun retryTopic(key: Key) = store.retryTopic(key)
  fun observeUnread(chatId: Long, observer: ForumUnreadCounter.Observer) = unread.observe(chatId, observer)
  fun unreadCount(chat: Chat): Int = if (tdlib.isForum(chat.id)) {
    ForumUnreadCounter.badgeCount(unread.cachedCount(chat.id), chat.unreadCount, chat.isMarkedAsUnread)
  } else if (chat.unreadCount > 0) chat.unreadCount else if (chat.isMarkedAsUnread) Tdlib.CHAT_MARKED_AS_UNREAD else 0

  // Existing message consumers keep their weak subscriptions; list consumers own closeable sessions.
  // Updates now deliver the merged full value through onTopicFound as well.
  interface Observer {
    fun onTopicFound(key: Key, topic: ForumTopic, inPlace: Boolean)
    fun onTopicUpdated(key: Key, update: UpdateForumTopic)
    fun onTopicInfoUpdated(key: Key, topicInfo: ForumTopicInfo)
  }

  @TdlibThread
  fun updateForumTopic(update: UpdateForumTopic) {
    store.updateTopic(update)
    unread.onTopicRead(update.chatId, update.forumTopicId, update.lastReadInboxMessageId)
  }

  @TdlibThread
  fun updateForumTopicInfo(update: UpdateForumTopicInfo) = store.updateInfo(update.info)

  fun findAndObserve(key: Key, observer: Observer): Entry? {
    synchronized(subscriptions) {
      pruneObservers()
      observers.add(key, observer)
      if (key !in subscriptions) {
        subscriptions[key] = store.observeTopic(key) { topic, _ ->
          if (topic != null) observers.iterate(key) { it.onTopicFound(key, topic, false) }
          synchronized(subscriptions) { pruneObservers() }
        }
      }
    }
    return find(key)?.also { observer.onTopicFound(key, it.value!!, true) }
  }

  fun stopObserving(key: Key, observer: Observer) {
    synchronized(subscriptions) {
      observers.remove(key, observer)
      pruneObservers()
    }
  }

  private fun pruneObservers() {
    val iterator = subscriptions.iterator()
    while (iterator.hasNext()) {
      val (key, subscription) = iterator.next()
      if (!observers.has(key)) {
        subscription.close()
        iterator.remove()
      }
    }
  }

  private fun clear() {
    synchronized(subscriptions) {
      store.reset()
      unread.reset()
      subscriptions.clear()
      observers.clear()
    }
  }
  override fun onPerformUserCleanup() = clear()
  override fun onPerformRestart() = clear()

  private fun messageChanged(message: Message) {
    store.onMessage(message)
    if (message.topicId is MessageTopicForum && message.schedulingState == null) unread.invalidate(message.chatId)
  }
  override fun onNewMessage(message: Message) = messageChanged(message)
  override fun onMessageSendSucceeded(message: Message, oldMessageId: Long) = messageChanged(message)
  override fun onMessageSendFailed(message: Message, oldMessageId: Long, error: Error) = messageChanged(message)
  override fun onMessageContentChanged(chatId: Long, messageId: Long, newContent: MessageContent) = changed(chatId, messageId)
  override fun onMessageEphemeralContentChanged(chatId: Long, messageId: Long, newEphemeralContent: EphemeralMessageContent?) = changed(chatId, messageId)
  override fun onMessageEdited(chatId: Long, messageId: Long, editDate: Int, replyMarkup: ReplyMarkup?) = changed(chatId, messageId)
  override fun onMessagePinned(chatId: Long, messageId: Long, isPinned: Boolean) = changed(chatId, messageId)
  override fun onMessageMentionRead(chatId: Long, messageId: Long) = changed(chatId, messageId)
  override fun onMessageUnreadReactionsChanged(chatId: Long, messageId: Long, unreadReactions: Array<UnreadReaction>?, unreadReactionCount: Int) = changed(chatId, messageId)
  override fun onMessageUnreadPollVotesChanged(chatId: Long, messageId: Long, hasUnreadPollVote: Boolean, unreadPollVoteCount: Int) = changed(chatId, messageId)
  override fun onMessagesDeleted(chatId: Long, messageIds: LongArray) {
    store.onMessageChanged(chatId, messageIds)
    unread.invalidate(chatId)
  }
  private fun changed(chatId: Long, messageId: Long) = store.onMessageChanged(chatId, longArrayOf(messageId))

  override fun onChatReadInbox(chatId: Long, lastReadInboxMessageId: Long, unreadCount: Int, availabilityChanged: Boolean) {
    store.invalidateChat(chatId)
    unread.invalidate(chatId)
  }
  override fun onChatReadOutbox(chatId: Long, lastReadOutboxMessageId: Long) = store.invalidateChat(chatId)
  override fun onChatUnreadMentionCount(chatId: Long, unreadMentionCount: Int, availabilityChanged: Boolean) = store.invalidateChat(chatId)
  override fun onChatUnreadReactionCount(chatId: Long, unreadReactionCount: Int, availabilityChanged: Boolean) = store.invalidateChat(chatId)
  override fun onChatUnreadPollVoteCount(chatId: Long, unreadMentionCount: Int, availabilityChanged: Boolean) = store.invalidateChat(chatId)

  override fun onConnectionStateChanged(newState: Int, oldState: Int) {
    if (newState == ConnectionState.CONNECTED && oldState != newState) {
      store.onConnectionRestored()
      unread.refreshVisible()
    }
  }

  override fun onUiStateChanged(newState: Int) = unread.setEnabled(newState == UI.State.RESUMED)
}
