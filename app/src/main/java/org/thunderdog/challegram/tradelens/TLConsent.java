/*
 * This file is a part of TradeLens Connect, an unofficial Telegram client derived from Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.tradelens;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * First-use notice for the TradeLens mini app. Telegram's guidelines ask clients to tell the user, before a bot's web app
 * opens for the first time, that it is a third-party service and what it receives. Asked once, then remembered.
 */
public final class TLConsent {
  private static final String KEY = "tradelens_miniapp_consent";
  private static final String TEXT = "TradeLens is a separate service, run by TradeLens and not by Telegram. "
    + "To recognise you, Telegram shares your name, username and Telegram ID with it, exactly as when you open any bot's web app. "
    + "Your messages, contacts and Telegram password are never shared.\n\nContinue to TradeLens?";

  private TLConsent () { }

  public static boolean accepted () {
    return Settings.instance().getBoolean(KEY, false);
  }

  public static void ask (ViewController<?> controller, Runnable onAccept) {
    controller.showConfirm(TEXT, "Continue", () -> {
      Settings.instance().putBoolean(KEY, true);
      onAccept.run();
    });
  }
}
