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

  public void addBubble (final TdApi.MessageSender senderId, int maxTextWidth) {
    bubbles.add(new BubbleView(tdlib, paint, senderId, maxTextWidth));
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

    int rs = 0;
    int rw = 0;
    int oy = Screen.dp(START_Y);
    for (int i = 0; i < length; i++) {
      final BubbleView bubble = bubbles.get(i);
      if (oy == bubble.getY()) {
        rw += bubble.getWidth();
      } else {
        rw += Screen.dp(SPACING * (i - rs - 1));
        for (int j = rs; j < i; j++) {
          BubbleView b = bubbles.get(j);
          b.setXY(b.getX() + ((width - rw) / 2), b.getY());
        }
        rw = bubble.getWidth();
        rs = i;
        oy = bubble.getY();
      }
    }
    if (rw > 0) {
      rw += Screen.dp(SPACING * (bubbles.size() - rs - 1));
      for (int j = rs; j < bubbles.size(); j++) {
        BubbleView b = bubbles.get(j);
        b.setXY(b.getX() + ((width - rw) / 2), b.getY());
      }
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
