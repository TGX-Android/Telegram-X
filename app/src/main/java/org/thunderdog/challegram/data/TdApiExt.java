package org.thunderdog.challegram.data;

import org.drinkless.td.libcore.telegram.TdApi;

/**
 * Date: 9/3/17
 * Author: default
 */

public class TdApiExt {
  public static class MessageChatEvent extends TdApi.MessageContent {
    public static final int CONSTRUCTOR = 0;

    public TdApi.ChatEvent event;
    public final boolean isFull;
    public final boolean hideDate;

    public MessageChatEvent (TdApi.ChatEvent event, boolean isFull, boolean hideDate) {
      this.event = event;
      this.isFull = isFull;
      this.hideDate = hideDate;
    }

    @Override
    public int getConstructor () {
      return CONSTRUCTOR;
    }

    @Override
    public String toString () {
      return "MessageChatEvent{" +
        "event=" + event +
        ", isFull=" + isFull +
        ", noDate=" + hideDate +
        '}';
    }
  }
}
