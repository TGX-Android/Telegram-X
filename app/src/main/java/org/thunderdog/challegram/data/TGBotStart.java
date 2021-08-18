/**
 * File created on 16/08/15 at 20:54
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

public class TGBotStart { // Used only in MessagesController arguments to add a new participant
  private final int userId;
  private final String argument;
  private final boolean isGame;

  public TGBotStart (int userId, String argument, boolean isGame) {
    this.userId = userId;
    this.argument = argument;
    this.isGame = isGame;
  }

  public boolean isGame () {
    return isGame;
  }

  public int getUserId () {
    return userId;
  }

  public boolean useDeepLinking () {
    return argument != null;
  }

  public String getArgument () {
    return argument;
  }
}
