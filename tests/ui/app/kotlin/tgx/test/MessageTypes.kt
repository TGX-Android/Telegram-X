package tgx.test

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Picture
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.telegram.Tdlib
import org.thunderdog.challegram.telegram.TdlibManager
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.unsorted.Settings
import java.io.File
import java.util.Calendar
import java.util.concurrent.CountDownLatch

private const val TAG = "MessageTypes"
private const val TIMEOUT_MS = 30_000L

// Media of samples by their keys in ContentKit, found in messages of the account by these filters
private val MEDIA_FILTERS = mapOf(
  "photo" to TdApi.SearchMessagesFilterPhoto(),
  "video" to TdApi.SearchMessagesFilterVideo(),
  "animation" to TdApi.SearchMessagesFilterAnimation(),
  "audio" to TdApi.SearchMessagesFilterAudio(),
  "document" to TdApi.SearchMessagesFilterDocument(),
  "voiceNote" to TdApi.SearchMessagesFilterVoiceNote(),
  "videoNote" to TdApi.SearchMessagesFilterVideoNote()
)

/**
 * Samples of all types of messages in made-up chats of [FakeChats], made once for an account: the
 * made-up users and chats that they need, and real media of the account that they show. Messages
 * have the same date on every run, 10:00 of the day, so that images of them can be compared
 */
class MessageTypes private constructor(private val tdlib: Tdlib, media: Map<String, String>) {
  companion object {
    private val instances = HashMap<Int, MessageTypes>()

    fun of(tdlib: Tdlib, media: Map<String, String>): MessageTypes = synchronized(instances) {
      instances.getOrPut(tdlib.id()) { MessageTypes(tdlib, media) }
    }

    fun current(tdlib: Tdlib): MessageTypes? {
      // Samples made by the action types in this process, null when the app started again since
      return synchronized(instances) { instances[tdlib.id()] }
    }
  }

  private val fake = FakeChats.of(tdlib)
  private val kit: ContentKit
  val samples: List<ContentSample>
  val pushes: List<PushSample>

  // Where each medium of the kit comes from, e.g. "photo" to "<chat id>:<message id>"
  val mediaSources = LinkedHashMap<String, String>()

  init {
    val anna = fake.addUser("Anna", "Lee", 2)
    val ben = fake.addUser("Ben", "Ross", 4)
    val clara = fake.addUser("Clara", "Diaz", 1)
    val members = listOf(anna, ben, clara)
    val chatIds = ChatKind.entries.associateWith { newChat(it, members) }
    val found = HashMap<String, TdApi.Object>()
    for ((key, filter) in MEDIA_FILTERS) {
      val message = media[key]?.let { messageOf(it) } ?: searchMessage(filter)
      val content = message?.content ?: continue
      found[key] = when (content) {
        is TdApi.MessagePhoto -> content.photo
        is TdApi.MessageVideo -> content.video
        is TdApi.MessageAnimation -> content.animation
        is TdApi.MessageAudio -> content.audio
        is TdApi.MessageDocument -> content.document
        is TdApi.MessageVoiceNote -> content.voiceNote
        is TdApi.MessageVideoNote -> content.videoNote
        else -> continue
      }
      mediaSources[key] = "${message.chatId}:${message.id}"
    }
    // Stickers by the name of their set, the first sticker of the first installed set by default
    val setName = media["sticker"] ?: firstInstalledSetName()
    val set = setName?.let {
      runCatching { execute(TdApi.SearchStickerSet(it, false)) }.getOrNull()
    }
    set?.stickers?.firstOrNull()?.let {
      found["sticker"] = it
      mediaSources["sticker"] = set.name
    }
    // Files that images show, loaded before the first image, so that no image catches a medium
    // while it loads
    for (medium in found.values) {
      val files = when (medium) {
        is TdApi.Photo -> medium.sizes.map { it.photo }
        is TdApi.Video -> listOfNotNull(medium.thumbnail?.file)
        is TdApi.Animation -> listOfNotNull(medium.thumbnail?.file)
        is TdApi.Audio -> listOfNotNull(medium.albumCoverThumbnail?.file)
        is TdApi.Document -> listOfNotNull(medium.thumbnail?.file)
        is TdApi.VideoNote -> listOfNotNull(medium.thumbnail?.file)
        is TdApi.Sticker -> listOf(medium.sticker)
        else -> listOf()
      }
      for (file in files) {
        runCatching { execute(TdApi.DownloadFile(file.id, 32, 0, 0, true)) }
      }
    }
    val today = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 10)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
    }
    val now = (today.timeInMillis / 1000).toInt()
    kit = ContentKit(tdlib.myUserId(), members, chatIds, found, now)
    samples = contentSamples(kit)
    pushes = pushSamples(kit)
  }

  fun add(index: Int, outgoing: Boolean, kind: ChatKind? = null): TdApi.Message {
    // The sample as the only message of a new made-up chat of its kind, so that it looks the same
    // whichever samples came before it, e.g. with the date above it
    val sample = samples[index]
    val chatId = newChat(kind ?: sample.chats.first(), kit.users)
    return fake.addMessage(chatId, sample.content, outgoing, date = kit.now).also {
      lastMessage = it
    }
  }

  private fun newChat(kind: ChatKind, members: List<TdApi.User>): Long = when (kind) {
    // The same title, users and colors in each chat of a kind; a private chat needs its own user
    ChatKind.PRIVATE -> fake.addPrivateChat(fake.addUser("Anna", "Lee", 2)).id
    ChatKind.SECRET -> fake.addSecretChat(members[1]).id
    ChatKind.BASIC_GROUP -> fake.addBasicGroup("Weekend trip", members, 1).id
    ChatKind.SUPERGROUP -> fake.addSupergroup("Book club", members, accentColorId = 3).id
    ChatKind.CHANNEL -> fake.addSupergroup("Daily news", listOf(), true, accentColorId = 5).id
  }

  private var lastMessage: TdApi.Message? = null
  private val shownNotifications = HashMap<Long, Int>()
  private var nextNotificationId = 1

  fun notifyLast(): Boolean {
    // A notification of the last added message, as TDLib sends it. Only incoming messages have
    // notifications
    val message = lastMessage?.takeIf { !it.isOutgoing } ?: return false
    show(message.chatId, TdApi.NotificationTypeNewMessage(message, true))
    return true
  }

  private var lastPush: Pair<Int, Long>? = null

  fun push(index: Int): Boolean {
    // A notification of a message that comes by Firebase before TDLib loads it
    val sample = pushes[index]
    val kind = sample.chats.first()
    val chatId = kit.chatIds.getValue(kind)
    // The sender of the message that replaces it
    val sender = fake.incomingSender(chatId)
    val senderName = when (sender) {
      is TdApi.MessageSenderUser -> fake.name(fake.user(sender.userId)!!)
      else -> fake.chat(chatId)!!.title
    }
    val type = TdApi.NotificationTypeNewPushMessage(
      1L shl 20, sender, senderName, false, sample.content
    )
    lastPush = index to chatId
    show(chatId, type)
    return true
  }

  fun replacePush(): Boolean {
    // The notification of the last push with the message of its sample instead, as when TDLib
    // loads the message: the same notification with another type
    val (index, chatId) = lastPush ?: return false
    val name = pushes[index].message ?: return false
    val sampleIndex = samples.indexOfFirst { it.name == name }.takeIf { it >= 0 }
      ?: samples.indexOfFirst { it.name.startsWith("$name, ") }.takeIf { it >= 0 }
      ?: return false
    val kind = ChatKind.entries.first { kit.chatIds[it] == chatId }
    val message = add(sampleIndex, false, kind)
    val notificationId = shownNotifications[chatId] ?: return false
    val type = TdApi.NotificationTypeNewMessage(message, true)
    val notification = TdApi.Notification(notificationId, message.date, false, type)
    tdlib.sendFakeUpdate(TdApi.UpdateNotification(notificationGroupId(chatId), notification))
    return true
  }

  private fun show(chatId: Long, type: TdApi.NotificationType) {
    // The notification instead of the one shown before in the same chat
    val notification = TdApi.Notification(nextNotificationId++, kit.now, false, type)
    val removed = listOfNotNull(shownNotifications.put(chatId, notification.id))
    tdlib.sendFakeUpdate(
      TdApi.UpdateNotificationGroup(
        notificationGroupId(chatId), TdApi.NotificationGroupTypeMessages(), chatId, chatId, -1, 1,
        arrayOf(notification), removed.toIntArray()
      )
    )
  }

  fun removeNotifications() {
    for ((chatId, notificationId) in shownNotifications) {
      tdlib.sendFakeUpdate(
        TdApi.UpdateNotificationGroup(
          notificationGroupId(chatId), TdApi.NotificationGroupTypeMessages(), chatId, chatId, -1,
          0, arrayOf(), intArrayOf(notificationId)
        )
      )
    }
    shownNotifications.clear()
  }

  // A group of notifications for each made-up chat, far from ids of groups of TDLib
  private val notificationGroupIds = HashMap<Long, Int>()

  private fun notificationGroupId(chatId: Long): Int = notificationGroupIds.getOrPut(chatId) {
    Int.MAX_VALUE - notificationGroupIds.size
  }

  private fun messageOf(source: String): TdApi.Message? {
    val (chatId, messageId) = source.split(":").map { it.toLong() }
    return runCatching { execute(TdApi.GetMessage(chatId, messageId)) }.getOrNull()
  }

  private fun searchMessage(filter: TdApi.SearchMessagesFilter): TdApi.Message? {
    // The oldest of the last 100 messages of the account with the filter, which changes less
    // often than the newest one
    val found = execute(
      TdApi.SearchMessages(TdApi.ChatListMain(), "", "", 100, filter, null, 0, 0)
    )
    return found.messages.lastOrNull()
  }

  private fun firstInstalledSetName(): String? {
    val sets = execute(TdApi.GetInstalledStickerSets(TdApi.StickerTypeRegular())).sets
    return sets.firstOrNull()?.name
  }

  private fun <T : TdApi.Object> execute(function: TdApi.Function<T>): T =
    tdlib.clientExecuteT(function, TIMEOUT_MS, true)
}

/**
 * Samples of all types of messages driven by broadcasts of tests, e.g.
 * `adb shell am broadcast -n org.example.tgx/tgx.test.MessageTypesReceiver --es action types`:
 * `types` makes the samples and logs the media they show as `media <key> <source>`, each sample as
 * `sample <index> <directions> <kinds> <name>`, e.g. `sample 3 in,out private,secret
 * MessagePhoto`, each sample of notifications by Firebase as `push <index> <kinds> <name>`, and
 * then `types <count of samples> <count of pushes>`; `--es media photo=<chat id>:<message
 * id>,sticker=<name of a set>` takes these media instead of found ones. `type` with `--ei index
 * <index>` and
 * `--ez outgoing <true or false>` adds the sample to the made-up chat of its first kind, or of
 * `--es kind <kind>`, and logs `type <index> <chat id>`; FakeChatReceiver opens the chat.
 * `notify` shows the notification of the last added sample when it's incoming, saves its image
 * in external files of the app, removes it and logs `notified <path of the image>`, or
 * `notified none` without a notification. `push` with `--ei index <index>` shows the notification
 * of a push, then replaces it with the message of its sample, as when TDLib loads it, and logs
 * `pushed <index> <image of the push> <image after the message>`, `none` for a missing one
 */
class MessageTypesReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val tdlib = TdlibManager.instance().current()
    when (intent.getStringExtra("action")) {
      "types" -> {
        val media = intent.getStringExtra("media").orEmpty().split(",")
          .filter { it.contains("=") }
          .associate { it.substringBefore("=") to it.substringAfter("=") }
        // Animations stop, so that an image of a run is the same as of another: stickers and
        // animated emoji play once, custom emoji and GIFs don't play
        Settings.instance().setNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_STICKERS_LOOP, true)
        Settings.instance().setNewSetting(Settings.SETTING_FLAG_NO_ANIMATED_EMOJI, true)
        Settings.instance().setAutoplayGIFs(false)
        // Searches of media wait for TDLib, longer than a receiver may run
        Thread {
          try {
            val types = MessageTypes.of(tdlib, media)
            for ((key, source) in types.mediaSources) {
              Log.i(TAG, "media $key $source")
            }
            for ((index, sample) in types.samples.withIndex()) {
              val directions = listOfNotNull(
                "in".takeIf { sample.incoming }, "out".takeIf { sample.outgoing }
              )
              val kinds = sample.chats.map { it.name.lowercase() }
              Log.i(
                TAG,
                "sample $index ${directions.joinToString(",")} ${kinds.joinToString(",")} " +
                  sample.name
              )
            }
            for ((index, push) in types.pushes.withIndex()) {
              val kinds = push.chats.map { it.name.lowercase() }
              Log.i(TAG, "push $index ${kinds.joinToString(",")} ${push.name}")
            }
            Log.i(TAG, "types ${types.samples.size} ${types.pushes.size}")
          } catch (e: Throwable) {
            Log.e(TAG, "failed", e)
          }
        }.start()
      }
      "type" -> {
        val types = MessageTypes.current(tdlib) ?: return noSamples()
        val index = intent.getIntExtra("index", 0)
        val kind = intent.getStringExtra("kind")?.let { ChatKind.valueOf(it.uppercase()) }
        val message = types.add(index, intent.getBooleanExtra("outgoing", false), kind)
        Log.i(TAG, "type $index ${message.chatId}")
      }
      "notify" -> {
        val types = MessageTypes.current(tdlib) ?: return noSamples()
        // The app builds notifications on its own thread
        Thread {
          val image = notificationImage(context, "notification") { types.notifyLast() }
          types.removeNotifications()
          Log.i(TAG, "notified ${image ?: "none"}")
        }.start()
      }
      "push" -> {
        val types = MessageTypes.current(tdlib) ?: return noSamples()
        val index = intent.getIntExtra("index", 0)
        Thread {
          val pushImage = notificationImage(context, "push") { types.push(index) }
          val messageImage = notificationImage(context, "message") { types.replacePush() }
          types.removeNotifications()
          Log.i(TAG, "pushed $index ${pushImage ?: "none"} ${messageImage ?: "none"}")
        }.start()
      }
    }
  }

  private fun noSamples() {
    // E.g. after a crash, when the system started the process again: the tool runs types first
    Log.w(TAG, "no samples, the action types makes them")
  }

  private fun notificationImage(context: Context, name: String, show: () -> Boolean): String? {
    // The image of the notification that show() makes, in external files of the app, or null
    // without one. Notifications are read back from NotificationManager and drawn by a Picture,
    // which draws their hardware bitmaps since Android 9
    val since = System.currentTimeMillis()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !show()) {
      return null
    }
    val posted = waitForNotification(context, since) ?: return null
    val file = File(context.getExternalFilesDir(null), "types/$name.png")
    val latch = CountDownLatch(1)
    var isSaved = false
    // An error of the tool is logged, so that only errors of the app crash it
    UI.post {
      try {
        // Messages of the template lay out only in a window, so the view stays a moment in the
        // window of the app, under its screen
        val view = notificationView(context, posted.notification)
        val window = UI.getUiContext()!!.window.decorView as ViewGroup
        window.addView(view, 0, ViewGroup.LayoutParams(view.measuredWidth, WRAP_CONTENT))
        UI.post({
          try {
            saveImage(view, file)
            isSaved = true
          } catch (e: Exception) {
            Log.e(TAG, "no image of the notification", e)
          } finally {
            window.removeView(view)
            latch.countDown()
          }
        }, 500)
      } catch (e: Exception) {
        Log.e(TAG, "no view of the notification", e)
        latch.countDown()
      }
    }
    latch.await()
    return if (isSaved) file.path else null
  }

  private fun waitForNotification(context: Context, since: Long): StatusBarNotification? {
    val manager = context.getSystemService(NotificationManager::class.java)
    repeat(50) {
      val posted = manager.activeNotifications.filter {
        it.postTime >= since && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
      }
      posted.maxByOrNull { it.postTime }?.let { return it }
      Thread.sleep(200)
    }
    return null
  }

  private fun notificationView(context: Context, notification: Notification): View {
    // The expanded notification made in the app from the views of the template of the system, as
    // the shade makes it, without opening the shade, where notifications of other apps are.
    // Colors of the system theme, light or dark as the phone is, and no time, which changes
    val system = context.createConfigurationContext(Resources.getSystem().configuration)
    val themed = ContextThemeWrapper(system, android.R.style.Theme_DeviceDefault_DayNight)
    val builder = Notification.Builder.recoverBuilder(themed, notification).setShowWhen(false)
    val views = builder.createBigContentView() ?: builder.createContentView()
    val view = views.apply(themed, FrameLayout(themed))
    val background = themed.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
    view.setBackgroundColor(background.getColor(0, 0))
    background.recycle()
    val metrics = context.resources.displayMetrics
    val width = metrics.widthPixels - (32 * metrics.density).toInt()
    view.measure(
      View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    return view
  }

  private fun saveImage(view: View, file: File) {
    // Icons of notifications are hardware bitmaps, which a Canvas of a bitmap can't draw
    val picture = Picture()
    view.draw(picture.beginRecording(view.width, view.height))
    picture.endRecording()
    val bitmap = Bitmap.createBitmap(picture).copy(Bitmap.Config.ARGB_8888, false)
    file.parentFile!!.mkdirs()
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
}
