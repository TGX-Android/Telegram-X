package org.thunderdog.challegram.data;

/**
 * Date: 09/11/2016
 * Author: default
 */

public class TGSwitchInline {
  private final String username;
  private final String query;

  public TGSwitchInline (String username, String query) {
    this.username = username;
    this.query = query;
  }

  public String getUsername () {
    return username;
  }

  public String getQuery () {
    return query;
  }

  @Override
  public String toString () {
    return "@" + username + " " + query;
  }
}
