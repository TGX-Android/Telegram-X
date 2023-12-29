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
 * File created on 08/02/2016 at 10:19
 */
package org.thunderdog.challegram.component.user;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

public class BubbleWrapView2 {
  private TextPaint paint;

  static final float START_X = 0f;
  static final float START_Y = 0f;
  static final float SPACING = 8f;

  private ArrayList<BubbleView> bubbles;

  private final Tdlib tdlib;

  public BubbleWrapView2(Tdlib tdlib) {
    this.tdlib = tdlib;

    bubbles = new ArrayList<>(10);
    paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    paint.setStyle(Paint.Style.FILL);
    paint.setTypeface(Fonts.getRobotoRegular());
    paint.setTextSize(Screen.dp(14f));
  }

  public void addBubble (final TdApi.MessageSender senderId, ComplexReceiver complexReceiver) {
    int defaultWidth = Screen.dp(100f);
    int maxWidth = (int) ((float) (Screen.smallestSide() - Screen.dp(60f)) * .5f) - Screen.dp(SPACING) - Screen.dp(44f);

    final int maxTextWidth;
    if (maxWidth < defaultWidth) {
      maxTextWidth = defaultWidth;
    } else if (maxWidth > Screen.dp(200f)) {
      maxTextWidth = Screen.dp(200f);
    } else {
      maxTextWidth = maxWidth;
    }

    BubbleView view = new BubbleView(tdlib, paint, senderId, maxTextWidth);

    if (bubbles.size() == 0) {
      view.setXY(Screen.dp(START_X), Screen.dp(START_Y));
    } else {
      BubbleView prev = bubbles.get(bubbles.size() - 1);
      float add = Screen.dp(SPACING);
      float cx = prev.getX() + prev.getWidth() + add;
      float cy = prev.getY();
      if (cx + view.getWidth() > width - add) {
        cx = Screen.dp(START_X);
        cy = cy + prev.getHeight() + add;
      }
      view.setXY((int) cx, (int) cy);
    }

    view.requestFile(complexReceiver);
    bubbles.add(view);
  }

  public void requestFiles (ComplexReceiver complexReceiver) {
    for (BubbleView v : bubbles) {
      v.requestFile(complexReceiver);
    }
  }

  public int getCurrentHeight () {
    int length = bubbles.size();
    if (length == 0) {
      return 0;
    } else {
      BubbleView bubble = null;
      while (length != 0 && (bubble = bubbles.get(length - 1)).isHiding()) {
        length--;
      }
      return bubble.isHiding() ? 0 : bubble.getY() + bubble.getHeight();
    }
  }

  private int width;

  public void buildLayout (int width) {
    this.width = width;
    int length = bubbles.size();

    if (length == 0) {
      return;
    }

    float add = Screen.dp(SPACING);
    float maxX = (width == 0 ? Screen.currentWidth() - Screen.dp(60f) : width) - add;
    float startX = Screen.dp(START_X);
    float cx = startX;
    float cy = Screen.dp(START_Y);

    for (int i = 0; i < length; i++) {
      BubbleView bubble = bubbles.get(i);
      if (bubble.isHiding()) {
        continue;
      }
      if (cx + bubble.getWidth() > maxX) {
        cx = startX;
        cy = cy + bubble.getHeight() + add;
      }
      bubble.setXY((int) cx, (int) cy);
      cx = cx + bubble.getWidth() + add;
    }
  }

  public void draw (Canvas c, ComplexReceiver complexReceiver, int x, int y) {
    c.save();
    c.translate(x, y);
    for (BubbleView view : bubbles) {
      view.draw(c, complexReceiver, width);
    }
    c.restore();
  }
}
