/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

public interface MiniAppBridgeTransport {
  void onMessage (String message, String sourceOrigin);

  String OBJECT_NAME = "TelegramWebviewProxyMessage";
  // Retain the public Telegram proxy signature without exposing any Java object to untrusted frames.
  String SCRIPT = "(function(){if(window." + OBJECT_NAME + "){window.TelegramWebviewProxy={postEvent:function(eventType,eventData){"
    + "window." + OBJECT_NAME + ".postMessage(JSON.stringify({eventType:String(eventType),eventData:eventData}));}};}})();";
}
