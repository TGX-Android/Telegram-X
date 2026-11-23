package org.thunderdog.challegram.telegram

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.util.LruCache
import org.thunderdog.challegram.theme.ColorState
import org.thunderdog.challegram.theme.ThemeChangeListener
import org.thunderdog.challegram.theme.ThemeDelegate
import org.thunderdog.challegram.theme.ThemeManager
import org.thunderdog.challegram.tool.UI

/** One cache for all accounts: an icon stays while a shown notification uses it */
class NotificationIcons(maxSize: Int) : ThemeChangeListener {
  private val icons = object : LruCache<String, Bitmap>(maxSize) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }

  private val usedIcons = HashMap<String, MutableSet<String>>()
  private var isListeningTheme = false

  private fun ownerOf(accountId: Int, chatId: Long): String = "$accountId:$chatId"

  @Synchronized
  fun get(accountId: Int, chatId: Long, key: String): Bitmap? {
    val icon = icons.get("$accountId:$key") ?: return null
    usedIcons.getOrPut(ownerOf(accountId, chatId)) { HashSet() }.add(key)
    return icon
  }

  @Synchronized
  fun put(accountId: Int, chatId: Long, key: String, icon: Bitmap): Bitmap {
    if (!isListeningTheme) {
      isListeningTheme = true
      UI.post { ThemeManager.instance().addThemeListener(this) }
    }
    val copy = icon.copy(Bitmap.Config.ARGB_8888, false) ?: return icon
    icon.recycle()
    icons.put("$accountId:$key", copy)
    usedIcons.getOrPut(ownerOf(accountId, chatId)) { HashSet() }.add(key)
    return copy
  }

  @Synchronized
  fun resetUsedIcons(accountId: Int, chatId: Long) {
    usedIcons.remove(ownerOf(accountId, chatId))
  }

  @Synchronized
  fun retainChats(accountId: Int, chatIds: Set<Long>) {
    val prefix = "$accountId:"
    usedIcons.keys.removeAll {
      it.startsWith(prefix) && it.substring(prefix.length).toLong() !in chatIds
    }
    val used = HashSet<String>()
    for ((owner, keys) in usedIcons) {
      if (owner.startsWith(prefix)) {
        used.addAll(keys)
      }
    }
    for (key in icons.snapshot().keys) {
      if (key.startsWith(prefix) && key.substring(prefix.length) !in used) {
        icons.remove(key)
      }
    }
  }

  fun releaseAccount(accountId: Int) {
    retainChats(accountId, emptySet())
  }

  fun clear() {
    icons.evictAll()
  }

  fun trimMemory(level: Int) {
    if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
      level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
      level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
    ) {
      clear()
    }
  }

  override fun needsTempUpdates(): Boolean = false

  override fun onThemeColorsChanged(areTemp: Boolean, state: ColorState?) {
    if (!areTemp) {
      clear()
    }
  }

  override fun onThemeChanged(fromTheme: ThemeDelegate, toTheme: ThemeDelegate) {
    clear()
  }
}
