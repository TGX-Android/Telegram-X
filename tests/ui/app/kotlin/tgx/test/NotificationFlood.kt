package tgx.test

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.telegram.TdlibManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "NotificationFlood"

/**
 * Starts or stops [NotificationFlood] in the app process, e.g.
 * `adb shell am broadcast -n org.example.tgx/tgx.test.NotificationFloodReceiver --ei count 3000 --ei perMinute 3000`
 * with `--ei chats 5` for several chats or `--ez textOnly false` for messages of all kinds; `--ez stop true` stops it
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
      textOnly = intent.getBooleanExtra("textOnly", true)
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
 */
class NotificationFlood(
  private val tdlib: Tdlib,
  private val count: Int,
  private val perMinute: Int,
  private val chatCount: Int,
  private val textOnly: Boolean
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

  private class Group(val id: Int, val chatId: Long, val messages: List<TdApi.Message>) {
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
    // The flood starts without notifications that TDLib gave before
    send(TdApi.UpdateActiveNotifications(arrayOf()))
    val start = SystemClock.uptimeMillis()
    var nextLog = start
    while (!isStopped && sent < count) {
      val wait = start + sent * 60_000L / perMinute - SystemClock.uptimeMillis()
      if (wait > 0) {
        Thread.sleep(wait)
      }
      notify(groups[sent % groups.size])
      sent++
      if (SystemClock.uptimeMillis() >= nextLog) {
        logProgress()
        nextLog += 1000
      }
    }
    logProgress()
    // Notifications of the last updates are still being shown
    Thread.sleep(10_000)
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
    Log.i(TAG, "sent $sent notifications in $updateCount updates, maps $maps, native heap $heap KB")
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
    for (chatId in chats.chatIds) {
      if (groups.size == chatCount) {
        break
      }
      if (!tdlib.chatNotificationsEnabled(chatId)) {
        continue
      }
      val messages = loadMessages(chatId)
      if (messages.isNotEmpty()) {
        groups.add(Group(groupId++, chatId, messages))
        // Kinds of chats tell which code shows their notifications, e.g. icons of senders of groups
        val chat = tdlib.chat(chatId)
        val type = chat?.type?.javaClass?.simpleName
        val senderCount = messages.map { it.senderId.toString() }.distinct().size
        val hasPhoto = chat?.photo != null
        Log.i(TAG, "chat ${groups.size}: $type, photo $hasPhoto, ${messages.size} messages from $senderCount senders")
      }
    }
  }

  private fun loadMessages(chatId: Long): List<TdApi.Message> {
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
        !it.isOutgoing && (!textOnly || it.content is TdApi.MessageText)
      }
      fromMessageId = history.messages.last().id
    }
    return messages
  }

  private fun execute(function: TdApi.Function<*>): TdApi.Object {
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

  private fun copyOf(message: TdApi.Message): TdApi.Message {
    // Each notification has its own message: TDLib sends a new object for each new message
    val copy = TdApi.Message()
    for (field in TdApi.Message::class.java.fields) {
      if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) {
        field.set(copy, field.get(message))
      }
    }
    return copy
  }
}
