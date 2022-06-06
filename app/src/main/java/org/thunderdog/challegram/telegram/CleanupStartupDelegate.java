/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 23/02/2018
 */
package org.thunderdog.challegram.telegram;

public interface CleanupStartupDelegate {
  /**
   * Called when component has been initialized and authorization became ready
   * */
  void onPerformStartup (boolean isAfterRestart);

  /**
   * Called when component should reset any user-related settings
   */
  void onPerformUserCleanup ();

  /**
   * Called when TDLib client instance has been restarted
   */
  void onPerformRestart ();
}
