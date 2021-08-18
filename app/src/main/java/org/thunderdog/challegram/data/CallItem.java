package org.thunderdog.challegram.data;

import androidx.annotation.DrawableRes;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.DateUtils;

/**
 * Date: 6/3/17
 * Author: default
 */

public class CallItem {
  private final ArrayList<TdApi.Message> messages;
  private final int userId;

  public CallItem (Tdlib tdlib, TdApi.Message message) {
    this.messages = new ArrayList<>();
    this.messages.add(message);
    this.userId = tdlib.calleeUserId(message);
  }

  public int getUserId () {
    return userId;
  }

  public long getChatId () {
    return messages.get(0).chatId;
  }

  public long getLatestMessageId () {
    return messages.get(0).id;
  }

  public long[] getMessageIds () {
    long[] messageIds = new long[messages.size()];
    int i = 0;
    for (TdApi.Message message : messages) {
      messageIds[i++] = message.id;
    }
    return messageIds;
  }

  public int getRevokeCount () {
    int count = 0;
    for (TdApi.Message message : messages) {
      if (message.canBeDeletedForAllUsers) {
        count++;
      }
    }
    return count;
  }

  public String getTime () {
    return Lang.time(messages.get(0).date, TimeUnit.SECONDS);
  }

  public TdApi.Message lastMessage () {
    return messages.get(messages.size() - 1);
  }

  public @DrawableRes int getSubtitleIcon () {
    return getSubtitleIcon((TdApi.MessageCall) lastMessage().content, isOutgoing());
  }

  public static @DrawableRes int getSubtitleIcon (TdApi.MessageCall call, boolean isOutgoing) {
    return isOutgoing ? R.drawable.baseline_call_made_18 : isMissed(call) ? R.drawable.baseline_call_missed_18 : R.drawable.baseline_call_received_18;
  }

  public @ThemeColorId
  int getSubtitleIconColorId () {
    return getSubtitleIconColorId((TdApi.MessageCall) lastMessage().content);
  }

  public static @ThemeColorId int getSubtitleIconColorId (TdApi.MessageCall call) {
    return isMissedOrCancelled(call) ? R.id.theme_color_iconNegative : R.id.theme_color_iconPositive;
  }

  public int getDate () {
    return messages.get(0).date;
  }

  public boolean isOutgoing () {
    return lastMessage().isOutgoing;
  }

  public boolean isEmpty () {
    return messages.isEmpty();
  }

  public boolean removeMessage (long chatId, long messageId) {
    int i = 0;
    for (TdApi.Message message : messages) {
      if (message.chatId == chatId && message.id == messageId) {
        messages.remove(i);
        return true;
      }
      i++;
    }
    return false;
  }

  public boolean isMissed () {
    return isMissed((TdApi.MessageCall) (messages.get(messages.size() - 1)).content);
  }

  public static boolean isMissed (TdApi.MessageCall call) {
    return call.discardReason.getConstructor() == TdApi.CallDiscardReasonMissed.CONSTRUCTOR;
  }

  public boolean isCancelled () {
    return isCancelled((TdApi.MessageCall) (messages.get(messages.size() - 1)).content);
  }

  public static boolean isCancelled (TdApi.MessageCall call) {
    return call.discardReason.getConstructor() == TdApi.CallDiscardReasonDeclined.CONSTRUCTOR;
  }

  public boolean isMissedOrCancelled () {
    return isMissedOrCancelled((TdApi.MessageCall) (messages.get(messages.size() - 1)).content);
  }

  public static boolean isMissedOrCancelled (TdApi.MessageCall call) {
    return call.discardReason.getConstructor() == TdApi.CallDiscardReasonMissed.CONSTRUCTOR ||
      call.discardReason.getConstructor() == TdApi.CallDiscardReasonDeclined.CONSTRUCTOR;
  }

  public String getSubtitle () {
    StringBuilder b = new StringBuilder();
    if (messages.size() > 1) {
      Collections.sort(messages, (o1, o2) -> Integer.compare(((TdApi.MessageCall) o1.content).discardReason.getConstructor(), ((TdApi.MessageCall) o2.content).discardReason.getConstructor()));
    }

    int count = 0;
    TdApi.Message lastMessage = null;
    for (TdApi.Message msg : messages) {
      if (lastMessage == null) {
        lastMessage = msg;
      } else if (((TdApi.MessageCall) msg.content).discardReason.getConstructor() != ((TdApi.MessageCall) lastMessage.content).discardReason.getConstructor()) {

        if (b.length() > 0) {
          b.append(", ");
        }

        TdApi.MessageCall call = (TdApi.MessageCall) lastMessage.content;
        b.append(Lang.getString(TD.getCallName(call, lastMessage.isOutgoing, false)));

        if (count != 1) {
          b.append(" (");
          b.append(Strings.buildCounter(count));
          b.append(")");
        } else if (call.duration != 0) {
          b.append(Lang.getCallDuration(call.duration));
        }

        count = 0;
        lastMessage = msg;
      }

      count++;
    }

    if (count > 0) {
      if (b.length() > 0) {
        b.append(", ");
      }

      TdApi.MessageCall call = (TdApi.MessageCall) lastMessage.content;
      b.append(Lang.getString(TD.getCallName(call, lastMessage.isOutgoing, false)));

      if (count != 1) {
        b.append(" (");
        b.append(Strings.buildCounter(count));
        b.append(")");
      } else if (call.duration != 0) {
        b.append(" (");
        if (call.duration >= 60) {
          b.append(Lang.plural(R.string.xMin, Math.round((float) call.duration / 60f)));
        } else {
          b.append(Lang.plural(R.string.xSec, call.duration));
        }
        b.append(")");
      }
    }

    return b.toString();
  }

  public static String getSubtitle (TdApi.Message msg, boolean isFull, int messageCount) {
    TdApi.MessageCall call = (TdApi.MessageCall) msg.content;

    StringBuilder b;
    if (isFull) {
      b = new StringBuilder();
    } else {
      String callName = Lang.getString(TD.getCallName(call, msg.isOutgoing, false));
      b = new StringBuilder(callName);
    }
    if (messageCount != 1) {
      b.append(" (");
      b.append(Strings.buildCounter(messageCount));
      b.append(")");
    } else if (call.duration != 0) {
      if (!isFull) {
        b.append(" (");
      }
      if (call.duration >= 60) {
        b.append(Lang.plural(isFull ? R.string.xMinutes : R.string.xMin, Math.round((float) call.duration / 60f)));
      } else {
        b.append(Lang.plural(isFull ? R.string.xSeconds : R.string.xSec, call.duration));
      }
      if (!isFull) {
        b.append(")");
      }
    }
    return b.toString();
  }

  public boolean mergeWith (CallItem item) {
    TdApi.Message message = item.lastMessage();
    TdApi.Message firstMessage = messages.get(messages.size() - 1);
    if (message.chatId == firstMessage.chatId && DateUtils.isSameDay(message.date, firstMessage.date) && message.isOutgoing == firstMessage.isOutgoing && firstMessage.date - message.date <= 60 * 60) {
      TdApi.MessageCall call = (TdApi.MessageCall) message.content;
      TdApi.MessageCall lastCall = (TdApi.MessageCall) firstMessage.content;

      if (call.duration == lastCall.duration && call.duration == 0 && (call.discardReason.getConstructor() == lastCall.discardReason.getConstructor() || (TD.isCallMissedOrCancelled(call.discardReason) && TD.isCallMissedOrCancelled(lastCall.discardReason)))) {
        messages.add(message);
        return true;
      }
    }
    return false;
  }
}
