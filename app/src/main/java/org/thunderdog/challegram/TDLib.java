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
 */
package org.thunderdog.challegram;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TDLib {
  private static String format (String format, Object... formatArgs) {
    if (formatArgs != null && formatArgs.length > 0) {
      try {
        return String.format(Locale.US, format, formatArgs);
      } catch (IllegalFormatException e) {
        return "Failed: String.format(\"" + format.replace("\"", "\\\"") + "\", " + Arrays.toString(formatArgs) + ")";
      }
    } else {
      return format;
    }
  }

  private static void log (int verbosityLevel, String format, Object... formatArgs) {
    try {
      Client.execute(new TdApi.AddLogMessage(verbosityLevel, format(format, formatArgs)));
    } catch (Client.ExecutionException ignored) { }
  }

  public static void e (String format, Object... formatArgs) {
    log(1, format, formatArgs);
  }

  public static void w (String format, Object... formatArgs) {
    log(2, format, formatArgs);
  }

  public static void i (String format, Object... formatArgs) {
    log(3, format, formatArgs);
  }

  public static void d (String format, Object... formatArgs) {
    log(4, format, formatArgs);
  }

  public static void v (String format, Object... formatArgs) {
    log(5, format, formatArgs);
  }

  private static void logModule (@Nullable String module, String format, Object... formatArgs) {
    log(Settings.instance().getTdlibLogSettings().getVerbosity(module), format, formatArgs);
  }

  private static final Set<Long> trackingStates = new HashSet<>();
  private static final Map<Long, String> lastMessage = new HashMap<>();

  public static String trackPushState (long pushId, boolean needTrack) {
    if (needTrack) {
      trackingStates.add(pushId);
      return null;
    } else {
      trackingStates.remove(pushId);
      return lastMessage.remove(pushId);
    }
  }

  public static String lastPushState (long pushId) {
    return lastMessage.get(pushId);
  }

  public static final class Tag {
    public static void safetyNet (String format, Object... formatArgs) {
      i("[safetynet]: %s", format(format, formatArgs));
    }

    public static void td_init (String format, Object... formatArgs) {
      logModule("td_init", format, formatArgs);
    }

    public static void notifications (String format, Object... formatArgs) {
      notifications(0, TdlibAccount.NO_ID, format, formatArgs);
    }

    public static void notifications (long pushId, int accountId, String format, Object... formatArgs) {
      String message = format(format, formatArgs);
      StringBuilder prefix = new StringBuilder("[fcm");
      if (pushId != 0) {
        prefix.append(":").append(pushId);
        if (trackingStates.contains(pushId)) {
          lastMessage.put(pushId, message);
        }
      }
      if (accountId != TdlibAccount.NO_ID) {
        prefix.append(",account:").append(accountId);
      }
      prefix.append("]: ");
      logModule("notifications", prefix + message);
    }
  }
}
