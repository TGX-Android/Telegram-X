/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.telegram.Tdlib;
import me.vkryl.core.lambda.RunnableInt;

/** The bot profile is the single place for revoking Mini App permissions. */
public final class MiniAppBotPermissions {
  private MiniAppBotPermissions () { }
  public static String scope (Tdlib tdlib, long bot) { return (tdlib.account().isDebug() ? "test-" : "production-") + tdlib.myUserId() + "-" + bot; }
  private static SharedPreferences preferences (Context context, Tdlib tdlib, long bot, boolean biometric) {
    return context.getSharedPreferences("miniapp-" + (biometric ? "biometry-" : "location-") + scope(tdlib, bot), Context.MODE_PRIVATE);
  }
  public static boolean requested (Context context, Tdlib tdlib, long bot, boolean biometric) { return preferences(context, tdlib, bot, biometric).getBoolean("requested", false); }
  public static boolean granted (Context context, Tdlib tdlib, long bot, boolean biometric) {
    return preferences(context, tdlib, bot, biometric).getBoolean("granted", false) && (biometric || hasLocation(context));
  }
  private static boolean hasLocation (Context context) {
    return androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
      androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }
  public static void toggle (BaseActivity context, Tdlib tdlib, long bot, boolean biometric, Runnable done) {
    SharedPreferences prefs = preferences(context, tdlib, bot, biometric);
    boolean allow = !granted(context, tdlib, bot, biometric);
    Runnable save = () -> {
      SharedPreferences.Editor edit = prefs.edit().putBoolean("requested", true).putBoolean("granted", allow && (biometric || hasLocation(context)));
      if (!allow && !biometric) {
        // Revoking bot location also revokes all HTML origin grants previously given to that bot.
        for (String key : prefs.getAll().keySet()) if (key.startsWith("html:")) edit.remove(key);
      }
      edit.apply(); done.run();
    };
    if (!biometric && allow && context.permissions().requestPermissions((RunnableInt) ignored -> save.run(), Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) return;
    save.run();
  }
}
