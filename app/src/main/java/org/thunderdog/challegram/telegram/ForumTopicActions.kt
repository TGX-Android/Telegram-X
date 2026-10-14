package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.data.ForumTopicPolicy
import org.thunderdog.challegram.data.ForumTopicSelection
import org.thunderdog.challegram.data.ForumTopicSelection.Action
import org.thunderdog.challegram.telegram.TdlibForumTopicManager.Key

/** Server-authoritative operations. Permission checks and confirmation dialogs belong to the UI. */
class ForumTopicActions internal constructor(private val store: ForumTopicStore) {
  fun interface Callback<T : TdApi.Object> { fun onResult(value: T?, error: TdApi.Error?) }

  private fun <T : TdApi.Object> send(request: TdApi.Function<T>, chatId: Long, key: Key?, constructor: Int, callback: Callback<T>, changesState: Boolean = true) {
    store.perform(request, chatId, key, constructor, changesState) { value, error ->
      @Suppress("UNCHECKED_CAST")
      callback.onResult(value as T?, error)
    }
  }

  fun create(chatId: Long, name: String, isNameImplicit: Boolean, icon: TdApi.ForumTopicIcon, callback: Callback<TdApi.ForumTopicInfo>) =
    send(TdApi.CreateForumTopic(chatId, name, isNameImplicit, icon), chatId, null, TdApi.ForumTopicInfo.CONSTRUCTOR, callback)

  fun edit(key: Key, name: String, editIconCustomEmoji: Boolean, customEmojiId: Long, callback: Callback<TdApi.Ok>) =
    send(TdApi.EditForumTopic(key.chatId, key.forumTopicId, name, editIconCustomEmoji, customEmojiId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun setClosed(key: Key, closed: Boolean, callback: Callback<TdApi.Ok>) =
    send(TdApi.ToggleForumTopicIsClosed(key.chatId, key.forumTopicId, closed), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun setGeneralHidden(chatId: Long, hidden: Boolean, callback: Callback<TdApi.Ok>) =
    send(TdApi.ToggleGeneralForumTopicIsHidden(chatId, hidden), chatId, Key(chatId, 1), TdApi.Ok.CONSTRUCTOR, callback)

  fun setPinned(key: Key, pinned: Boolean, callback: Callback<TdApi.Ok>) =
    send(TdApi.ToggleForumTopicIsPinned(key.chatId, key.forumTopicId, pinned), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun setPinnedOrder(chatId: Long, topicIds: IntArray, callback: Callback<TdApi.Ok>) =
    send(TdApi.SetPinnedForumTopics(chatId, topicIds.copyOf()), chatId, null, TdApi.Ok.CONSTRUCTOR, callback)

  // TDLib decides whether the topic disappears or only its history is cleared (notably General).
  fun delete(key: Key, callback: Callback<TdApi.Ok>) =
    send(TdApi.DeleteForumTopic(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun setNotifications(key: Key, settings: TdApi.ChatNotificationSettings, callback: Callback<TdApi.Ok>) =
    send(TdApi.SetForumTopicNotificationSettings(key.chatId, key.forumTopicId, settings), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun readAllMentions(key: Key, callback: Callback<TdApi.Ok>) =
    send(TdApi.ReadAllForumTopicMentions(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun readAllReactions(key: Key, callback: Callback<TdApi.Ok>) =
    send(TdApi.ReadAllForumTopicReactions(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun readAllPollVotes(key: Key, callback: Callback<TdApi.Ok>) =
    send(TdApi.ReadAllForumTopicPollVotes(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun unpinAllMessages(key: Key, callback: Callback<TdApi.Ok>) =
    send(TdApi.UnpinAllForumTopicMessages(key.chatId, key.forumTopicId), key.chatId, key, TdApi.Ok.CONSTRUCTOR, callback)

  fun getLink(key: Key, callback: Callback<TdApi.MessageLink>) =
    send(TdApi.GetForumTopicLink(key.chatId, key.forumTopicId), key.chatId, key, TdApi.MessageLink.CONSTRUCTOR, callback, changesState = false)

  fun setViewAsTopics(chatId: Long, viewAsTopics: Boolean, callback: Callback<TdApi.Ok>) =
    send(TdApi.ToggleChatViewAsTopics(chatId, viewAsTopics), chatId, null, TdApi.Ok.CONSTRUCTOR, callback)

  class BatchTarget @JvmOverloads constructor(@JvmField val topicId: Int, @JvmField val name: String, @JvmField val retrying: Boolean = false)
  class BatchOutcome(@JvmField val target: BatchTarget, @JvmField val error: TdApi.Error?, @JvmField val uncertain: Boolean = false) {
    val successful: Boolean get() = error == null
    val retryable: Boolean get() = error != null && !uncertain
  }
  class BatchResult(@JvmField val action: Action, outcomes: List<BatchOutcome>) {
    @JvmField val outcomes: List<BatchOutcome> = outcomes.toList()
    fun failedTargets(): List<BatchTarget> = outcomes.filter { it.retryable }.map { BatchTarget(it.target.topicId, it.target.name, true) }
  }
  interface BatchCallback {
    fun onProgress(completed: Int, total: Int)
    fun onComplete(result: BatchResult)
  }

  private var activeBatch: Batch? = null

  /** One account-wide runner, at most 100 targets, one request in flight. Never auto-retries a write. */
  fun startBatch(chatId: Long, selfUserId: Long, action: Action, targets: List<BatchTarget>, callback: BatchCallback): Batch? {
    if (activeBatch != null) return null
    val batch = Batch(chatId, selfUserId, action, targets.toList(), callback)
    activeBatch = batch
    batch.start()
    return batch
  }

  inner class Batch internal constructor(
    private val chatId: Long, private val selfUserId: Long, private val action: Action,
    private val targets: List<BatchTarget>, private val callback: BatchCallback
  ) {
    private var cancelled = false
    private var finished = false
    private var status: TdApi.ChatMemberStatus? = null
    private var groupMuted = false
    private var pinLimit = 0
    private val preflightErrors = HashMap<Int, TdApi.Error>()
    private val alreadyDeleted = HashSet<Int>()
    private val outcomes = ArrayList<BatchOutcome>()

    /** Stops unsent operations. An already sent request cannot be recalled. */
    fun cancel() {
      cancelled = true
      if (activeBatch === this) activeBatch = null
    }
    private fun live() = !cancelled && !finished
    private fun unavailable() = TdApi.Error(BATCH_UNAVAILABLE, "Topic action is no longer available")
    private fun failAll(error: TdApi.Error) {
      if (!live()) return
      targets.drop(outcomes.size).forEach { outcomes.add(BatchOutcome(it, error)) }
      finish()
    }
    private fun finish() {
      if (!live()) return
      finished = true
      if (activeBatch === this) activeBatch = null
      callback.onComplete(BatchResult(action, outcomes))
    }
    private fun <T : TdApi.Object> read(request: TdApi.Function<T>, constructor: Int, block: (T?, TdApi.Error?) -> Unit) {
      if (!live()) return
      send(request, chatId, null, constructor, Callback { value, error -> if (live()) block(value, error) }, changesState = false)
    }
    private fun rights(done: (TdApi.Error?) -> Unit) {
      read(TdApi.GetChatMember(chatId, TdApi.MessageSenderUser(selfUserId)), TdApi.ChatMember.CONSTRUCTOR) { member, error ->
        status = member?.status
        done(error ?: if (!ForumTopicPolicy.isMember(status)) unavailable() else null)
      }
    }
    private fun muteDefault(done: (TdApi.Error?) -> Unit) {
      if (action != Action.MUTE && action != Action.UNMUTE) { done(null); return }
      read(TdApi.GetChat(chatId), TdApi.Chat.CONSTRUCTOR) { chat, error ->
        if (error != null) { done(error); return@read }
        val settings = chat?.notificationSettings
        if (settings == null) { done(unavailable()); return@read }
        if (!settings.useDefaultMuteFor) { groupMuted = settings.muteFor > 0; done(null) }
        else read(TdApi.GetScopeNotificationSettings(TdApi.NotificationSettingsScopeGroupChats()), TdApi.ScopeNotificationSettings.CONSTRUCTOR) { scope, failure ->
          groupMuted = (scope?.muteFor ?: 0) > 0
          done(failure)
        }
      }
    }
    internal fun start() {
      if (chatId == 0L || selfUserId == 0L || targets.isEmpty() || targets.size > ForumTopicSelection.MAX_SELECTED ||
          targets.any { it.topicId <= 0 } || targets.map { it.topicId }.toSet().size != targets.size ||
          action == Action.CLEAR_GENERAL && targets.size != 1) { failAll(unavailable()); return }
      callback.onProgress(0, targets.size)
      rights { error ->
        if (error != null) failAll(error)
        else muteDefault { failure -> if (failure != null) failAll(failure) else preflight(0) }
      }
    }
    private fun preflight(index: Int) {
      if (!live()) return
      if (index == targets.size) {
        if (preflightErrors.isNotEmpty()) {
          targets.forEach { outcomes.add(BatchOutcome(it, preflightErrors[it.topicId] ?: TdApi.Error(BATCH_PREFLIGHT_ABORTED, "Not sent because another selected topic failed preflight"))) }
          finish()
          return
        }
        if (action == Action.PIN || action == Action.UNPIN) checkPins(targets.map { it.topicId }) { error ->
          if (error != null) failAll(error) else next()
        } else next()
        return
      }
      val target = targets[index]
      read(TdApi.GetForumTopic(chatId, target.topicId), TdApi.ForumTopic.CONSTRUCTOR) { topic, error ->
        if (target.retrying && action == Action.DELETE && error?.code == 404) {
          alreadyDeleted.add(target.topicId)
        } else if (error != null) {
          preflightErrors[target.topicId] = error
        } else if (topic == null || topic.info.chatId != chatId || topic.info.forumTopicId != target.topicId ||
            !ForumTopicSelection.allowed(action, topic, status) || !target.retrying && !ForumTopicSelection.applicable(action, topic, groupMuted)) {
          preflightErrors[target.topicId] = unavailable()
        }
        preflight(index + 1)
      }
    }
    /** Full server prefix, not a cached search result; cursor- and page-bounded. */
    private fun checkPins(remaining: List<Int>, done: (TdApi.Error?) -> Unit) {
      read(TdApi.GetOption("pinned_forum_topic_count_max"), TdApi.OptionValueInteger.CONSTRUCTOR) { option, error ->
        if (error != null) { done(error); return@read }
        pinLimit = (option as? TdApi.OptionValueInteger)?.value?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0
        val pinned = HashSet<Int>()
        val cursors = HashSet<ForumTopicStore.Cursor>()
        fun page(cursor: ForumTopicStore.Cursor, pages: Int) {
          if (pages >= MAX_PIN_PAGES || !cursors.add(cursor)) { done(TdApi.Error(BATCH_PIN_PREFIX, "Unable to verify the full pinned topic order")); return }
          read(TdApi.GetForumTopics(chatId, "", cursor.date, cursor.messageId, cursor.topicId, 100), TdApi.ForumTopics.CONSTRUCTOR) prefixRead@ { result, failure ->
            if (failure != null || result == null) { done(failure ?: unavailable()); return@prefixRead }
            var reachedUnpinned = false
            for (topic in result.topics) {
              if (topic.info.chatId != chatId) { done(unavailable()); return@prefixRead }
              if (topic.isPinned && !reachedUnpinned) pinned.add(topic.info.forumTopicId) else reachedUnpinned = true
            }
            val next = ForumTopicStore.Cursor(result.nextOffsetDate, result.nextOffsetMessageId, result.nextOffsetForumTopicId)
            if (reachedUnpinned || next.isEmpty) {
              val needed = remaining.count { it !in pinned }
              done(if (action == Action.PIN && pinned.size.toLong() + needed > pinLimit) TdApi.Error(BATCH_PIN_LIMIT, "Pinned topic limit exceeded") else null)
            } else page(next, pages + 1)
          }
        }
        page(ForumTopicStore.Cursor(), 0)
      }
    }
    private fun next() {
      if (!live()) return
      if (outcomes.size == targets.size) { finish(); return }
      val target = targets[outcomes.size]
      if (target.topicId in alreadyDeleted) { record(target, null); return }
      // Permissions and states can change during a long batch; refresh immediately before each write.
      rights { error ->
        if (error != null) { record(target, error); return@rights }
        muteDefault { failure ->
          if (failure != null) { record(target, failure); return@muteDefault }
          read(TdApi.GetForumTopic(chatId, target.topicId), TdApi.ForumTopic.CONSTRUCTOR) { topic, problem ->
            if (problem != null) { record(target, problem); return@read }
            if (topic == null || topic.info.chatId != chatId || topic.info.forumTopicId != target.topicId || !ForumTopicSelection.allowed(action, topic, status)) {
              record(target, unavailable()); return@read
            }
            // Another client may already have applied this exact non-destructive direction.
            if (!ForumTopicSelection.applicable(action, topic, groupMuted)) { record(target, null); return@read }
            if (action == Action.PIN || action == Action.UNPIN) checkPins(targets.drop(outcomes.size).map { it.topicId }) { pinError ->
              if (pinError != null) failAll(pinError) else apply(target, topic)
            } else apply(target, topic)
          }
        }
      }
    }
    private fun apply(target: BatchTarget, topic: TdApi.ForumTopic) {
      if (!live()) return
      val key = Key(chatId, target.topicId)
      val done = Callback<TdApi.Ok> { _, error ->
        record(target, error, action.isDestructive && error != null && (error.code == 408 || error.code >= 500 || error.code == 0))
      }
      when (action) {
        Action.PIN, Action.UNPIN -> setPinned(key, action == Action.PIN, done)
        Action.MUTE, Action.UNMUTE -> setNotifications(key, ForumTopicPolicy.notifications(topic.notificationSettings, false, if (action == Action.MUTE) Int.MAX_VALUE else 0), done)
        Action.CLOSE, Action.OPEN -> setClosed(key, action == Action.CLOSE, done)
        Action.DELETE, Action.CLEAR_GENERAL -> delete(key, done)
        Action.READ_MENTIONS -> readAllMentions(key, done)
        Action.READ_REACTIONS -> readAllReactions(key, done)
        Action.READ_POLL_VOTES -> readAllPollVotes(key, done)
      }
    }
    private fun record(target: BatchTarget, error: TdApi.Error?, uncertain: Boolean = false) {
      if (!live()) return
      outcomes.add(BatchOutcome(target, error, uncertain))
      callback.onProgress(outcomes.size, targets.size)
      next()
    }
  }

  companion object {
    const val BATCH_UNAVAILABLE = -8101
    const val BATCH_PIN_LIMIT = -8102
    const val BATCH_PIN_PREFIX = -8103
    const val BATCH_PREFLIGHT_ABORTED = -8104
    private const val MAX_PIN_PAGES = 32
  }
}
