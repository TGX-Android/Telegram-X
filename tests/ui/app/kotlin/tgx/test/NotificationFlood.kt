package tgx.test

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.data.TD
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.telegram.TdlibManager
import org.thunderdog.challegram.telegram.TdlibNotificationUtils
import tgx.td.fromUserId
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "NotificationFlood"

/**
 * Starts or stops [NotificationFlood] in the app process, e.g.
 * `adb shell am broadcast -n org.example.tgx/tgx.test.NotificationFloodReceiver --ei count 3000 --ei perMinute 3000`
 * with `--ei chats 5` for several chats, `--ez textOnly false` for messages of all kinds or
 * `--ez local true` for messages that TDLib adds itself, with `--ez savedMessages true` to Saved
 * Messages; `--es chatType supergroup` takes chats of one type, `--ez oneSender true` gives all
 * messages of a chat to one sender, `--ei retainedEvery 30` logs the retained native heap each 30
 * seconds, `--ez muted true` takes muted chats too; `--ez stop true` stops it
 */
class NotificationFloodReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    NotificationFlood.current?.stop()
    if (intent.getBooleanExtra("stop", false)) {
      return
    }
    NotificationFlood(
      TdlibManager.instance().current(),
      count = intent.getIntExtra("count", 100),
      perMinute = intent.getIntExtra("perMinute", 1000),
      chatCount = intent.getIntExtra("chats", 1),
      textOnly = intent.getBooleanExtra("textOnly", true),
      removeAfter = intent.getBooleanExtra("removeAfter", false),
      muteAfter = intent.getBooleanExtra("muteAfter", false),
      local = intent.getBooleanExtra("local", false),
      savedMessages = intent.getBooleanExtra("savedMessages", false),
      chatType = intent.getStringExtra("chatType"),
      oneSender = intent.getBooleanExtra("oneSender", false),
      retainedEvery = intent.getIntExtra("retainedEvery", 0),
      muted = intent.getBooleanExtra("muted", false)
    ).start()
  }
}

/**
 * Notifications of new messages that come by thousands per minute, without a second account and without
 * sending anything: incoming messages of chats of the account are loaded from their history and given to
 * the app with new ids and dates as updates of TDLib about notifications, so that the app runs the code
 * that shows a notification of a new message. Each second it logs how many notifications it sent with the
 * number of mappings and the native heap of the process.
 *
 * Like TDLib with the options that the app sets, each chat has one group of messages, and a group shows at
 * most [groupSizeMax] notifications: older ones are removed by the update that adds a new one. Delays,
 * merging of updates and limits of the number of groups of TDLib are left out.
 *
 * After the flood, [removeAfter] (`--ez removeAfter true`) removes its notifications as TDLib does
 * when they are read, and [muteAfter] (`--ez muteAfter true`) turns notifications of its chats off
 * in the app.
 *
 * With [local], TDLib itself adds the messages as local ones from their senders, so that the app
 * gets all updates of a new message through the queue of results of TDLib, and notifications of
 * them from TDLib with its delays and merging. Each second it also logs how long results of TDLib
 * wait in that queue. With [savedMessages] the messages go to Saved Messages, which has no
 * notifications, e.g. to measure the work of the app on new messages of a chat that is open.
 *
 * [chatType] takes only chats of that type: `private`, `basicGroup`, `supergroup` or `channel`.
 * With [oneSender] all messages of a chat come from the sender of its newest loaded message, e.g. a
 * flood of one user in a group. With [retainedEvery] the native heap after garbage collection is
 * logged every that many seconds, which pauses the flood for the time of the collection.
 * With [muted], chats with notifications off are taken too, e.g. a muted group, where new messages
 * update the chat list and unread counters without notifications.
 */
class NotificationFlood(
  private val tdlib: Tdlib,
  private val count: Int,
  private val perMinute: Int,
  private val chatCount: Int,
  private val textOnly: Boolean,
  private val removeAfter: Boolean,
  private val muteAfter: Boolean,
  private val local: Boolean,
  private val savedMessages: Boolean,
  private val chatType: String?,
  private val oneSender: Boolean,
  private val retainedEvery: Int,
  private val muted: Boolean
) {
  companion object {
    @Volatile
    var current: NotificationFlood? = null
      private set

    // Requests of history that may load messages from the server, like a user scrolls a chat
    private const val MAX_HISTORY_REQUESTS = 10
    private const val HISTORY_REQUEST_INTERVAL_MS = 500L
  }

  private val groupSizeMax = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 7 else 10

  @Volatile
  private var isStopped = false

  private class Group(
    val id: Int,
    val chatId: Long,
    val messages: List<TdApi.Message>,
    // Sender of all messages of the group, or null for the senders of the loaded messages
    val sender: TdApi.MessageSender?
  ) {
    var totalCount = 0
    var nextMessageId = messages.maxOf { it.id }
    // Ids of notifications that the app shows, oldest first
    val shown = ArrayDeque<Int>()
  }

  private val groups = ArrayList<Group>()
  private var nextNotificationId = 0
  private var sent = 0
  private var updateCount = 0
  private var wasCached = false
  @Volatile
  private var failedCount = 0

  // Times when markers were sent to TDLib, oldest first, and how long the last returned one waited
  private val markers = ConcurrentLinkedQueue<Long>()
  @Volatile
  private var markerDelay = 0L

  fun start() {
    current = this
    Thread({
      try {
        run()
      } catch (e: Throwable) {
        Log.e(TAG, "failed", e)
      } finally {
        if (current === this) {
          current = null
        }
      }
    }, TAG).start()
  }

  fun stop() {
    isStopped = true
  }

  private fun run() {
    loadChats()
    if (groups.isEmpty()) {
      Log.e(TAG, "no chats with incoming messages")
      return
    }
    val messageCount = groups.sumOf { it.messages.size }
    Log.i(TAG, "start: $count notifications, $perMinute per minute, ${groups.size} chats, $messageCount messages")
    // Ids grow from one flood to the next, like counters of TDLib
    val firstId = (System.currentTimeMillis() / 1000 % 1_000_000_000).toInt()
    nextNotificationId = firstId
    logRetained("before")
    if (!local) {
      // The flood starts without notifications that TDLib gave before
      send(TdApi.UpdateActiveNotifications(arrayOf()))
    }
    val start = SystemClock.uptimeMillis()
    var nextLog = start
    var nextRetained = start + retainedEvery * 1000L
    while (!isStopped && sent < count) {
      val wait = start + sent * 60_000L / perMinute - SystemClock.uptimeMillis()
      if (wait > 0) {
        Thread.sleep(wait)
      }
      if (local) {
        addLocalMessage(groups[sent % groups.size])
      } else {
        notify(groups[sent % groups.size])
      }
      sent++
      if (SystemClock.uptimeMillis() >= nextLog) {
        logProgress()
        nextLog += 1000
      }
      if (retainedEvery > 0 && SystemClock.uptimeMillis() >= nextRetained) {
        logRetained("at ${(SystemClock.uptimeMillis() - start) / 1000} s")
        nextRetained += retainedEvery * 1000L
      }
    }
    logProgress()
    // Notifications of the last updates are still being shown
    Thread.sleep(10_000)
    if (removeAfter) {
      removeNotifications()
    }
    if (muteAfter) {
      muteChats()
    }
    if (removeAfter || muteAfter) {
      Thread.sleep(2_000)
    }
    logRetained("after")
    Log.i(TAG, "done")
  }

  private fun notify(group: Group) {
    // Update of a message that has just come: the notification is added, the oldest shown one beyond
    // the size of the group is removed
    val message = copyOf(group.messages[group.totalCount % group.messages.size])
    val date = (System.currentTimeMillis() / 1000).toInt()
    group.nextMessageId += 1L shl 20
    message.id = group.nextMessageId
    message.date = date
    group.sender?.let { message.senderId = it }
    val type = TdApi.NotificationTypeNewMessage(message, true)
    val notification = TdApi.Notification(nextNotificationId++, date, false, type)
    val removed = if (group.shown.size == groupSizeMax) intArrayOf(group.shown.removeFirst()) else intArrayOf()
    group.shown.addLast(notification.id)
    group.totalCount++
    send(TdApi.UpdateNotificationGroup(
      group.id, TdApi.NotificationGroupTypeMessages(), group.chatId, group.chatId, -1,
      group.totalCount, arrayOf(notification), removed
    ))
  }

  private fun addLocalMessage(group: Group) {
    // A message that has just come from the sender of a loaded one: TDLib adds it and sends the app
    // its updates and notification
    val message = group.messages[group.totalCount % group.messages.size]
    group.totalCount++
    val text = (message.content as? TdApi.MessageText)?.text
      ?: TdApi.FormattedText("Message", arrayOf())
    val content = TdApi.InputMessageText(text, null, false)
    val sender = group.sender ?: message.senderId
    val function = TdApi.AddLocalMessage(group.chatId, sender, null, false, content)
    tdlib.client().send(function) {
      if (it is TdApi.Error && failedCount++ == 0) {
        Log.e(TAG, "AddLocalMessage failed: $it")
      }
    }
  }

  private fun send(update: TdApi.Update) {
    tdlib.sendFakeUpdate(update)
    updateCount++
  }

  private fun logRetained(moment: String) {
    // Native memory that stays after garbage collection, whose finalizers free native allocations of
    // unreachable objects, e.g. pixels of bitmaps
    repeat(2) {
      System.gc()
      System.runFinalization()
    }
    val maps = File("/proc/self/maps").useLines { it.count() }
    Log.i(TAG, "retained $moment: maps $maps, native heap ${Debug.getNativeHeapAllocatedSize() / 1024} KB")
  }

  private fun logProgress() {
    val maps = File("/proc/self/maps").useLines { it.count() }
    val heap = Debug.getNativeHeapAllocatedSize() / 1024
    // Results of TDLib wait in its queue while the thread of TDLib of the app handles earlier ones
    val now = SystemClock.uptimeMillis()
    val delay = markers.peek()?.let { now - it } ?: markerDelay
    markers.add(now)
    tdlib.client().send(TdApi.SetAlarm(0.0)) {
      markers.poll()?.let { markerDelay = SystemClock.uptimeMillis() - it }
    }
    val sentBy = if (local) "$sent local messages, $failedCount failed" else "$updateCount updates"
    val wait = "results of TDLib wait $delay ms"
    val icons = iconsOf()
    val memory = "maps $maps, native heap $heap KB"
    Log.i(TAG, "sent $sent notifications in $sentBy, $memory, $wait, $icons")
    // The system kills a cached app for binder traffic of notifications ("excessive binder traffic
    // during cached"), which ends a flood without a crash of the app
    val state = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(state)
    val isCached = state.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
    if (isCached && !wasCached) {
      Log.w(TAG, "the app is cached, the system may kill it: keep a screen of the app open")
    }
    wasCached = isCached
  }

  private fun loadChats() {
    // Chats with notifications on and incoming messages, from the top of the main chat list
    val chats = execute(TdApi.GetChats(TdApi.ChatListMain(), 100)) as TdApi.Chats
    var groupId = (System.currentTimeMillis() / 1000 % 1_000_000_000).toInt()
    if (savedMessages) {
      val chatId = tdlib.selfChatId()
      val messages = loadMessages(chatId, incomingOnly = false)
      val sender = messages.firstOrNull()?.senderId?.takeIf { oneSender }
      groups.add(Group(groupId, chatId, messages, sender))
      return
    }
    for (chatId in chats.chatIds) {
      if (groups.size == chatCount) {
        break
      }
      val isOfType = chatType == null || typeOf(tdlib.chat(chatId)) == chatType
      // TDLib adds local messages only where the account can send them
      val canAdd = !local || tdlib.canSendBasicMessage(chatId)
      if (!(muted || tdlib.chatNotificationsEnabled(chatId)) || !isOfType || !canAdd) {
        continue
      }
      val messages = loadMessages(chatId)
      if (messages.isNotEmpty()) {
        val sender = messages[0].senderId.takeIf { oneSender }
        groups.add(Group(groupId++, chatId, messages, sender))
        // Kinds of chats tell which code shows their notifications, e.g. icons of senders of groups
        val chat = tdlib.chat(chatId)
        val type = chat?.type?.javaClass?.simpleName
        val senders = messages.map { it.senderId.toString() }.distinct().size
        val senderCount = if (oneSender) 1 else senders
        val hasPhoto = chat?.photo != null
        val isMuted = !tdlib.chatNotificationsEnabled(chatId)
        val content = "${messages.size} messages from $senderCount senders"
        Log.i(TAG, "chat ${groups.size}: $type, photo $hasPhoto, muted $isMuted, $content")
      }
    }
  }

  private fun typeOf(chat: TdApi.Chat?): String? = when (val type = chat?.type) {
    is TdApi.ChatTypePrivate -> "private"
    is TdApi.ChatTypeBasicGroup -> "basicGroup"
    is TdApi.ChatTypeSupergroup -> if (type.isChannel) "channel" else "supergroup"
    is TdApi.ChatTypeSecret -> "secret"
    else -> null
  }

  private fun loadMessages(chatId: Long, incomingOnly: Boolean = true): List<TdApi.Message> {
    // Up to [count] incoming messages, from the cache first, then from the server at the pace of
    // scrolling
    val messages = ArrayList<TdApi.Message>()
    var fromMessageId = 0L
    var requests = 0
    var onlyLocal = true
    while (messages.size < count && requests < MAX_HISTORY_REQUESTS) {
      val history = execute(TdApi.GetChatHistory(chatId, fromMessageId, 0, 100, onlyLocal)) as TdApi.Messages
      if (history.messages.isEmpty()) {
        if (!onlyLocal) {
          break
        }
        onlyLocal = false
        continue
      }
      if (!onlyLocal) {
        requests++
        Thread.sleep(HISTORY_REQUEST_INTERVAL_MS)
      }
      history.messages.filterTo(messages) {
        (!incomingOnly || !it.isOutgoing) && (!textOnly || it.content is TdApi.MessageText)
      }
      fromMessageId = history.messages.last().id
    }
    return messages
  }

  private fun execute(function: TdApi.Function<*>): TdApi.Object = execute(tdlib, function)

  private fun removeNotifications() {
    // Like TDLib when messages are read: each group loses its shown notifications
    for (group in groups) {
      val removed = group.shown.toIntArray()
      group.shown.clear()
      val type = TdApi.NotificationGroupTypeMessages()
      send(TdApi.UpdateNotificationGroup(
        group.id, type, group.chatId, group.chatId, -1, 0, arrayOf(), removed
      ))
    }
  }

  private fun muteChats() {
    // Notifications of the chats are off in the app, also of mentions and pinned messages, as if
    // the user muted them, while the server keeps the settings of the account
    for (group in groups) {
      val settings = copyOf(tdlib.chatStrict(group.chatId).notificationSettings)
      settings.useDefaultMuteFor = false
      settings.muteFor = Int.MAX_VALUE
      settings.useDefaultDisableMentionNotifications = false
      settings.disableMentionNotifications = true
      settings.useDefaultDisablePinnedMessageNotifications = false
      settings.disablePinnedMessageNotifications = true
      send(TdApi.UpdateChatNotificationSettings(group.chatId, settings))
    }
  }

  private fun <T : Any> copyOf(value: T): T {
    // Each notification has its own message and each change its own settings: TDLib sends a new
    // object for each of them
    val copy = value.javaClass.getDeclaredConstructor().newInstance()
    for (field in value.javaClass.fields) {
      if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) {
        field.set(copy, field.get(value))
      }
    }
    return copy
  }
}

/**
 * Icons of notifications that the app keeps: their number, size, and how often notifications found
 * them made, e.g. `adb shell am broadcast -n org.example.tgx/tgx.test.NotificationIconsReceiver`
 * logs them with tag `NotificationFlood`
 */
class NotificationIconsReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    Log.i(TAG, iconsOf())
  }
}

fun iconsOf(): String {
  // The cache of the app is private, and only tools read it
  val icons = TdlibManager.instance().notificationIcons()
    ?: return "icons 0, 0 KB, hits 0, misses 0, reuse off"
  val field = icons.javaClass.getDeclaredField("icons").apply { isAccessible = true }
  val cache = field.get(icons) as LruCache<*, *>
  val count = cache.snapshot().size
  val size = cache.size() / 1024
  return "icons $count, $size KB, hits ${cache.hitCount()}, misses ${cache.missCount()}"
}

private fun execute(tdlib: Tdlib, function: TdApi.Function<*>): TdApi.Object {
  val latch = CountDownLatch(1)
  var result: TdApi.Object? = null
  tdlib.client().send(function) {
    result = it
    latch.countDown()
  }
  check(latch.await(30, TimeUnit.SECONDS)) { "No result of $function" }
  return result.also {
    check(it !is TdApi.Error) { "$function failed: $it" }
  }!!
}

/**
 * The icon of a call and the icon of a notification of the same private chat, made as
 * `TGCallService` and notifications make them, e.g.
 * `adb shell am broadcast -n org.example.tgx/tgx.test.CallIconReceiver` logs with tag
 * `NotificationFlood` "call icon: same true, icons 1, after the chat 0" for the first private chat
 * of the main list: whether both are one bitmap, the icons kept, and the icons kept after no
 * notification of the chat is shown anymore
 */
class CallIconReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val tdlib = TdlibManager.instance().current()
    Thread {
      val chats = execute(tdlib, TdApi.GetChats(TdApi.ChatListMain(), 100)) as TdApi.Chats
      val chat = chats.chatIds.asSequence()
        .mapNotNull { tdlib.chat(it) }
        .firstOrNull { it.type is TdApi.ChatTypePrivate && !tdlib.isSelfChat(it) }
      if (chat == null) {
        Log.i(TAG, "call icon: no private chat")
        return@Thread
      }
      val user = tdlib.cache().user((chat.type as TdApi.ChatTypePrivate).userId)!!
      // A call doesn't wait for the photo, a notification does: both find it loaded
      user.profilePhoto?.small?.let { execute(tdlib, TdApi.DownloadFile(it.id, 32, 0, 0, true)) }
      val callIcon = TdlibNotificationUtils.buildLargeIcon(tdlib, fromUserId(user.id),
        user.profilePhoto?.small, tdlib.cache().userAccentColor(user), TD.getLetters(user), false,
        true)
      val messageIcon = TdlibNotificationUtils.buildLargeIcon(tdlib, chat, true)
      val kept = iconsOf()
      TdlibManager.instance().notificationIcons()?.retainChats(tdlib.id(), emptySet())
      Log.i(TAG, "call icon: same ${callIcon === messageIcon}, $kept, after the chat ${iconsOf()}")
    }.start()
  }
}
