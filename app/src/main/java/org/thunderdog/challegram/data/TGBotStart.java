/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 16/08/2015 at 20:54
 */
package org.thunderdog.challegram.data;

public class TGBotStart { // Used only in MessagesController arguments to add a new participant
  private final long userId;
  private final String argument;
  private final boolean isGame;
  private final boolean ignoreExplicitUserInteraction;

  public TGBotStart (long userId, String argument, boolean isGame, boolean ignoreExplicitUserInteraction) {
    this.userId = userId;
    this.argument = argument;
    this.isGame = isGame;
    this.ignoreExplicitUserInteraction = ignoreExplicitUserInteraction;
  }

  public boolean isGame () {
    return isGame;
  }

  public long getUserId () {
    return userId;
  }

  public boolean ignoreExplicitUserInteraction () {
    return ignoreExplicitUserInteraction;
  }

  public boolean useDeepLinking () {
    return argument != null;
  }

  public String getArgument () {
    return argument;
  }
}
