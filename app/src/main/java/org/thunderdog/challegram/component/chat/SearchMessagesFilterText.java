package org.thunderdog.challegram.component.chat;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * TODO stub !Delete later!
 * Wait changes from tdlib api
 * Returns only text messages.
 */
public class SearchMessagesFilterText extends TdApi.SearchMessagesFilter {

  /**
   * Returns all found messages, no filter is applied.
   */
  public SearchMessagesFilterText () {
  }

  /**
   * Identifier uniquely determining type of the object.
   */
  public static final int CONSTRUCTOR = -869395659;

  /**
   * @return this.CONSTRUCTOR
   */
  @Override
  public int getConstructor () {
    return CONSTRUCTOR;
  }
}
