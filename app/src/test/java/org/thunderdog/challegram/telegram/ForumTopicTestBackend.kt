package org.thunderdog.challegram.telegram

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertTrue
import java.util.PriorityQueue

/** Virtual time and manually ordered replies: no Android, JNI, credentials or network. */
internal class ForumTopicTestBackend : ForumTopicStore.Backend {
  class Call(val request: TdApi.Function<*>, val callback: (TdApi.Object) -> Unit) {
    var answered = false
    fun reply(result: TdApi.Object) {
      check(!answered)
      answered = true
      callback(result)
    }
  }
  private class Task(val time: Long, val sequence: Int, val action: () -> Unit)
  val calls = ArrayList<Call>()
  private val timers = PriorityQueue<Task>(compareBy<Task> { it.time }.thenBy { it.sequence })
  private val publications = ArrayDeque<() -> Unit>()
  private val ownerTasks = ArrayDeque<() -> Unit>()
  var deferOwner = false
  var immediateResponse: ((TdApi.Function<*>) -> TdApi.Object?)? = null
  private var time = 0L
  private var sequence = 0
  var publicationCount = 0
    private set

  override fun execute(action: () -> Unit) { if (deferOwner) ownerTasks.add(action) else action() }
  override fun send(request: TdApi.Function<*>, callback: (TdApi.Object) -> Unit) {
    val call = Call(request, callback)
    calls.add(call)
    immediateResponse?.invoke(request)?.let { call.reply(it) }
  }
  override fun schedule(delayMs: Long, action: () -> Unit) { timers.add(Task(time + delayMs, sequence++, action)) }
  override fun publish(action: () -> Unit) { publicationCount++; publications.add(action) }
  fun publish() { while (publications.isNotEmpty()) publications.removeFirst()() }
  fun execute() { while (ownerTasks.isNotEmpty()) ownerTasks.removeFirst()() }
  fun advance(ms: Long = ForumTopicStore.RECONCILE_DELAY_MS) {
    val end = time + ms
    var count = 0
    while (timers.isNotEmpty() && timers.peek()!!.time <= end) {
      check(++count < 10000) { "Unbounded timer loop" }
      val task = timers.remove()
      time = task.time
      task.action()
    }
    time = end
  }
  inline fun <reified T : TdApi.Function<*>> next(): Call {
    val call = calls.firstOrNull { !it.answered && it.request is T }
    assertTrue("Missing request: ${T::class.java.simpleName}", call != null)
    return call!!
  }
  inline fun <reified T : TdApi.Function<*>> count() = calls.count { it.request is T }
}

internal fun topic(id: Int = 17, chat: Long = 100, order: Long = 1000L - id, name: String = "Alpha"): TdApi.ForumTopic = TdApi.ForumTopic().apply {
  info = TdApi.ForumTopicInfo().apply {
    chatId = chat
    forumTopicId = id
    this.name = name
    icon = TdApi.ForumTopicIcon(0x6FB9F0, 0)
    creationDate = 123
    creatorId = TdApi.MessageSenderUser(77)
    isGeneral = id == 1
  }
  this.order = order
  notificationSettings = TdApi.ChatNotificationSettings()
  lastMessage = TdApi.Message().apply {
    chatId = chat
    this.id = id.toLong() shl 20
    date = 456
    topicId = TdApi.MessageTopicForum(id)
    content = TdApi.MessageText(TdApi.FormattedText("Synthetic message", emptyArray()), null, null)
  }
}

internal fun page(vararg topics: TdApi.ForumTopic, total: Int = topics.size, cursor: ForumTopicStore.Cursor? = null): TdApi.ForumTopics {
  val last = topics.lastOrNull()
  val next = cursor ?: last?.let { ForumTopicStore.Cursor(it.lastMessage?.date ?: 123, it.lastMessage?.id ?: 0, it.info.forumTopicId) } ?: ForumTopicStore.Cursor()
  return TdApi.ForumTopics(total, arrayOf(*topics), next.date, next.messageId, next.topicId)
}

internal fun update(topic: TdApi.ForumTopic) = TdApi.UpdateForumTopic(
  topic.info.chatId, topic.info.forumTopicId, topic.isPinned, topic.lastReadInboxMessageId,
  topic.lastReadOutboxMessageId, topic.unreadMentionCount, topic.unreadReactionCount,
  topic.unreadPollVoteCount, topic.notificationSettings, topic.draftMessage
)
