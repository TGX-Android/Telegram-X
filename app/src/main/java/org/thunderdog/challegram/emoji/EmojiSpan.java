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
 * File created on 04/05/2019
 */
package org.thunderdog.challegram.emoji;

import android.graphics.Canvas;
import android.text.Layout;
import android.view.View;

import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.util.text.TextReplacementSpan;

public interface EmojiSpan extends TextReplacementSpan {
  boolean isCustomEmoji ();
  default long getCustomEmojiId () {
    return 0;
  }
  default EmojiInfo getBuiltInEmojiInfo () {
    return null;
  }
  default boolean belongsToSurface (CustomEmojiSurfaceProvider customEmojiSurfaceProvider) {
    return false;
  }
  default boolean forceDisableAnimations () {
    return false;
  }
  default void requestCustomEmoji (ComplexReceiver receiver, int mediaKey) {
    receiver.clearReceivers(mediaKey);
  }
  EmojiSpan toBuiltInEmojiSpan ();

  default boolean needRefresh () {
    return false;
  }

  default void onOverlayDraw (Canvas c, View view, Layout layout) { }
}
