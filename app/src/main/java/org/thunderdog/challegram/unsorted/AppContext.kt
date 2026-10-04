package org.thunderdog.challegram.unsorted

import android.content.Context
import me.vkryl.android.DeviceUtils

@Suppress("StaticFieldLeak")
object AppContext {
  private val lock = Any()
  private var context: Context? = null

  @JvmStatic
  fun init(context: Context) {
    if (this.context != null) {
      return
    }
    synchronized(lock) {
      if (this.context != null) {
        return
      }
      this.context = context
    }
    initApplication()
    if (DeviceUtils.isTestLabDevice(context)) {
      // Important: Do not import.
      org.thunderdog.challegram.tool.UI.prepareTestLab()
    }
  }

  @JvmStatic
  fun get(): Context =
    context!!
}