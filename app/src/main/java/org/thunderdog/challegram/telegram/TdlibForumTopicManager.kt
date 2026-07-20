package org.thunderdog.challegram.telegram

import me.vkryl.core.reference.ReferenceMap
import org.drinkless.tdlib.TdApi.*

class TdlibForumTopicManager(
  tdlib: Tdlib
) : TdlibDataManager<TdlibForumTopicManager.Key, ForumTopic, TdlibForumTopicManager.Entry>(tdlib) {
  data class Key(
    @JvmField val chatId: Long,
    @JvmField val forumTopicId: Int
  )
  class Entry : AbstractEntry<Key, ForumTopic> {
    @JvmField val forumTopicId: Int

    constructor(key: Key, value: ForumTopic?, error: Error?) : super(key, value, error) {
      this.forumTopicId = key.forumTopicId
    }
  }

  interface Watcher : TdlibDataManager.Watcher<Key, ForumTopic, Entry> {
    fun onForumTopicLoaded (context: TdlibForumTopicManager, entry: Entry)

    override fun onEntryLoaded(context: TdlibDataManager<Key, ForumTopic, Entry>, entry: Entry) {
      onForumTopicLoaded(context as TdlibForumTopicManager, entry)
    }
  }

  override fun newEntry(key: Key, value: ForumTopic?, error: Error?): Entry =
    Entry(key, value, error)

  override fun requestData(contextId: Int, keysToRequest: Collection<Key>) {
    for (key in keysToRequest) {
      tdlib.send(GetForumTopic(key.chatId, key.forumTopicId)) { forumTopic, error ->
        if (!isCancelled(contextId)) {
          if (error != null) {
            processError(contextId, key, error)
          } else {
            processData(contextId, key, forumTopic)
          }
        }
      }
    }
  }

  interface Observer {
    fun onTopicFound(key: Key, topic: ForumTopic, inPlace: Boolean)
    fun onTopicUpdated(key: Key, update: UpdateForumTopic)
    fun onTopicInfoUpdated(key: Key, topicInfo: ForumTopicInfo)
  }

  @TdlibThread
  fun updateForumTopic(update: UpdateForumTopic) {
    val key = Key(
      update.chatId,
      update.forumTopicId
    )
    val isUpdated = replaceExisting(key) { prevValue, _ ->
      if (prevValue != null) {
        ForumTopic(
          prevValue.info,
          prevValue.lastMessage,
          prevValue.order,
          update.isPinned,
          prevValue.unreadCount,
          update.lastReadInboxMessageId,
          update.lastReadOutboxMessageId,
          update.unreadMentionCount,
          update.unreadReactionCount,
          update.unreadPollVoteCount,
          update.notificationSettings,
          update.draftMessage
        );
      } else {
        null
      }
    }
    if (isUpdated) {
      observers.iterate(key) { observer ->
        observer.onTopicUpdated(key, update)
      }
    }
  }

  @TdlibThread
  fun updateForumTopicInfo(update: UpdateForumTopicInfo) {
    val key = Key(
      update.info.chatId,
      update.info.forumTopicId
    )
    val isUpdated = replaceExisting(key) { prevValue, _ ->
      if (prevValue != null) {
        ForumTopic(
          update.info,
          prevValue.lastMessage,
          prevValue.order,
          prevValue.isPinned,
          prevValue.unreadCount,
          prevValue.lastReadInboxMessageId,
          prevValue.lastReadOutboxMessageId,
          prevValue.unreadMentionCount,
          prevValue.unreadReactionCount,
          prevValue.unreadPollVoteCount,
          prevValue.notificationSettings,
          prevValue.draftMessage
        )
      } else {
        null
      }
    }
    if (isUpdated) {
      observers.iterate(key) { observer ->
        observer.onTopicInfoUpdated(key, update.info)
      }
    }
  }

  private val observers = ReferenceMap<Key, Observer>(true)

  fun findAndObserve(key: Key, observer: Observer): Entry? {
    observers.add(key, observer)
    return findOrRequest(key) { entry, inPlace ->
      if (!entry.isNotFound) {
        observer.onTopicFound(key, entry.value!!, inPlace)
      }
    }
  }

  fun stopObserving(key: Key, observer: Observer) {
    observers.remove(key, observer)
  }
}