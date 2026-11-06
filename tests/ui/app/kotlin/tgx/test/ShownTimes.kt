package tgx.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.view.ViewGroup
import org.drinkless.tdlib.TdApi
import org.thunderdog.challegram.component.chat.MessageView
import org.thunderdog.challegram.component.dialogs.ChatView
import org.thunderdog.challegram.core.Lang
import org.thunderdog.challegram.data.TGChat
import org.thunderdog.challegram.data.TGMessage
import org.thunderdog.challegram.telegram.DateChangeListener
import org.thunderdog.challegram.telegram.TdlibManager
import org.thunderdog.challegram.tool.UI
import org.thunderdog.challegram.util.text.FormattedText
import org.thunderdog.challegram.widget.BetterChatView
import java.util.concurrent.TimeUnit

private const val TAG = "ShownTimes"

/**
 * Whether times on the screen are those that the app would make now, e.g. after a change of the
 * time zone, the locale or 12 or 24 hours:
 * `adb shell am broadcast -n org.example.tgx/tgx.test.ShownTimesReceiver` logs with tag
 * `ShownTimes` the numbers of chats of the list and messages of the open chat seen on the screen
 * and of those whose time is old, and of changes of the time format that the app heard since the
 * first broadcast, as `chats 9, old 0, messages 12, old 0, changes 0`, then each old one as
 * `old <shown> instead of <now>`. With `--es action merges` it logs each message of the open chat
 * on the screen as `message <id> top <y> merge <flags> <index> date <date above it or null>`; with
 * `--es action shown` it also logs each time on the screen as `shown <time>`, and each row of
 * search as `found <time of a found message> | <status of a found chat>`; `--es action word` logs
 * `word <word>` of a text message of the last 6 hours, the last message of a chat on the screen
 */
class ShownTimesReceiver : BroadcastReceiver() {
  companion object {
    private var changes = 0
    private var counter: DateChangeListener? = null
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.getStringExtra("action") == "merges") {
      UI.post {
        UI.getUiContext()?.window?.decorView?.let { logMerges(it) }
      }
      return
    }
    if (intent.getStringExtra("action") == "word") {
      UI.post {
        UI.getUiContext()?.window?.decorView?.let { Log.i(TAG, "word ${wordOfToday(it) ?: ""}") }
      }
      return
    }
    UI.post {
      if (counter == null) {
        counter = object : DateChangeListener {
          override fun onTimeFormatChanged() {
            changes++
          }
        }.also { TdlibManager.instance().dateManager().addListener(it) }
      }
      val chats = mutableListOf<Pair<String, String>>()
      val messages = mutableListOf<Pair<String, String>>()
      UI.getUiContext()?.window?.decorView?.let { collect(it, chats, messages) }
      val oldChats = chats.filter { it.first != it.second }
      val oldMessages = messages.filter { it.first != it.second }
      Log.i(
        TAG,
        "chats ${chats.size}, old ${oldChats.size}, " +
          "messages ${messages.size}, old ${oldMessages.size}, changes $changes"
      )
      for ((shown, now) in oldChats + oldMessages) {
        Log.i(TAG, "old $shown instead of $now")
      }
      if (intent.getStringExtra("action") == "shown") {
        for ((shown, _) in chats + messages) {
          Log.i(TAG, "shown $shown")
        }
        UI.getUiContext()?.window?.decorView?.let { logFound(it) }
      }
    }
  }

  private fun collect(
    view: View, chats: MutableList<Pair<String, String>>,
    messages: MutableList<Pair<String, String>>
  ) {
    if (view is ChatView) {
      view.chat?.let { chat -> timesOf(chat)?.let { chats += it } }
      return
    }
    if (view is MessageView) {
      view.message?.let { messages += timesOf(it) }
      return
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        collect(view.getChildAt(i), chats, messages)
      }
    }
  }

  private fun logFound(view: View) {
    // Rows of search: the time of a found message and the status of a found chat
    if (view is BetterChatView) {
      val time = BetterChatView::class.java.getDeclaredField("time").apply { isAccessible = true }
      val subtitle = BetterChatView::class.java.getDeclaredField("subtitle")
        .apply { isAccessible = true }
      val status = (subtitle.get(view) as? FormattedText)?.text ?: ""
      Log.i(TAG, "found ${time.get(view) ?: ""} | $status")
      return
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        logFound(view.getChildAt(i))
      }
    }
  }

  private fun logMerges(view: View) {
    if (view is MessageView) {
      val message = view.message ?: return
      val location = IntArray(2).also { view.getLocationOnScreen(it) }
      val date = TGMessage::class.java.getDeclaredField("date").apply { isAccessible = true }
      Log.i(
        TAG,
        "message ${message.id} top ${location[1]} merge ${message.mergeFlags} " +
          "${message.mergeIndex} date ${if (message.hasDate()) date.get(message) else null}"
      )
      return
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        logMerges(view.getChildAt(i))
      }
    }
  }

  private fun wordOfToday(view: View): String? {
    // A word of 4 letters or more of a text message of the last 6 hours, the last message of a
    // chat on the screen, so that a search finds a message that shows a time
    if (view is ChatView) {
      val chat = view.chat ?: return null
      val tdChat = TGChat::class.java.getDeclaredField("chat").apply { isAccessible = true }
        .get(chat) as? TdApi.Chat ?: return null
      val message = tdChat.lastMessage ?: return null
      val text = (message.content as? TdApi.MessageText)?.text?.text ?: return null
      val today = System.currentTimeMillis() / 1000 - message.date < 6 * 60 * 60
      return if (today) Regex("\\p{L}{4,}").find(text)?.value else null
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        wordOfToday(view.getChildAt(i))?.let { return it }
      }
    }
    return null
  }

  private fun timesOf(chat: TGChat): Pair<String, String>? {
    // Archives, drafts and sponsors show other times than their last message
    val chatField = TGChat::class.java.getDeclaredField("chat").apply { isAccessible = true }
    val tdChat = chatField.get(chat) as? TdApi.Chat ?: return null
    val message = tdChat.lastMessage
    if (chat.isArchive || tdChat.draftMessage != null || message == null) {
      return null
    }
    val timeField = TGChat::class.java.getDeclaredField("time").apply { isAccessible = true }
    val shown = timeField.get(chat) as? String ?: ""
    return shown to Lang.timeOrDateShort(message.date.toLong(), TimeUnit.SECONDS)
  }

  private fun timesOf(message: TGMessage): Pair<String, String> {
    val timeField = TGMessage::class.java.getDeclaredField("time").apply { isAccessible = true }
    val genTime = TGMessage::class.java.getDeclaredMethod("genTime").apply { isAccessible = true }
    return (timeField.get(message) as? String ?: "") to (genTime.invoke(message) as String)
  }
}
