/*
 * This file is a part of Telegram X.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.thunderdog.challegram.miniapps;

/** All sizes are in physical pixels; the web viewport is measured after native chrome is laid out. */
public final class MiniAppViewport {
  private MiniAppViewport () { }

  public static int sheetHeight (int windowHeight, int topInset, int bottomInset, boolean expanded, boolean fullscreen) {
    int available = Math.max(0, windowHeight - bottomInset - (fullscreen ? 0 : topInset));
    return fullscreen || expanded ? available : Math.round(available * .6f);
  }
}
