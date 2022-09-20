/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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
import android.graphics.Paint;
import android.text.Layout;
import android.view.View;

import org.thunderdog.challegram.loader.ComplexReceiver;

public interface EmojiSpan {
  int getRawSize (Paint paint);

  boolean isCustomEmoji ();
  default long getCustomEmojiId () {
    return 0;
  }
  default boolean belongsToSurface (CustomEmojiSurfaceProvider customEmojiSurfaceProvider) {
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
