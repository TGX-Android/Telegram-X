package tgx.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.thunderdog.challegram.telegram.TdlibManager
import org.thunderdog.challegram.theme.ThemeManager
import org.thunderdog.challegram.unsorted.Settings

/**
 * Sets the theme and the chat style of the app for UI tests, without screens of settings, e.g.
 * `adb shell am broadcast -n org.example.tgx/tgx.test.AppearanceReceiver --ei theme 10`
 * with `--ez bubbles false` for the plain chat style, where the theme is an id of `ThemeId`
 */
class AppearanceReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val tdlib = TdlibManager.instance().current()
    if (intent.hasExtra("theme")) {
      // Like choosing a theme in settings, which asks to turn automatic night mode off
      Settings.instance().setAutoNightMode(Settings.NIGHT_MODE_NONE)
      val theme = ThemeManager.instance().getTheme(intent.getIntExtra("theme", 0))
      ThemeManager.instance().changeGlobalTheme(tdlib, theme, false, null)
    }
    if (intent.hasExtra("bubbles")) {
      val bubbles = intent.getBooleanExtra("bubbles", true)
      tdlib.settings().setChatStyle(
        if (bubbles) ThemeManager.CHAT_STYLE_BUBBLES else ThemeManager.CHAT_STYLE_MODERN
      )
    }
  }
}
