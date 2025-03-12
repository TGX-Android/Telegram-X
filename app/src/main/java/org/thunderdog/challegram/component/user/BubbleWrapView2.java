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
import android.view.MotionEvent;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;

import java.util.ArrayList;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.lambda.RunnableData;

public class BubbleWrapView2 {
  private TextPaint paint;

  static final float START_X = 0f;
  static final float START_Y = 0f;
  static final float SPACING = 8f;

  private ArrayList<BubbleView2> bubbles;

  private final Tdlib tdlib;
  private final ViewProvider viewProvider;

  public BubbleWrapView2(Tdlib tdlib, ViewProvider viewProvider) {
    this.tdlib = tdlib;
    this.viewProvider = viewProvider;

    bubbles = new ArrayList<>(10);
    paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    paint.setStyle(Paint.Style.FILL);
    paint.setTypeface(Fonts.getRobotoMedium());
    paint.setTextSize(Screen.dp(14f));
  }

  public void addBubble (final TdApi.MessageSender senderId, int maxTextWidth) {
    BubbleView2 bubbleView2 = new BubbleView2(viewProvider, tdlib, paint, senderId, maxTextWidth);
    bubbleView2.setOnClickListener(v -> completeTouch(v, senderId));
    bubbles.add(bubbleView2);
  }

  public void requestFiles (ComplexReceiver complexReceiver) {
    for (BubbleView2 v : bubbles) {
      v.requestFile(complexReceiver);
    }
  }

  public int getCurrentHeight () {
    int length = bubbles.size();
    if (length == 0) {
      return 0;
    } else {
      BubbleView2 bubble = bubbles.get(length - 1);
      return bubble.getY() + bubble.getHeight();
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
      BubbleView2 bubble = bubbles.get(i);
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
      final BubbleView2 bubble = bubbles.get(i);
      if (oy == bubble.getY()) {
        rw += bubble.getWidth();
      } else {
        rw += Screen.dp(SPACING * (i - rs - 1));
        for (int j = rs; j < i; j++) {
          BubbleView2 b = bubbles.get(j);
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
        BubbleView2 b = bubbles.get(j);
        b.setXY(b.getX() + ((width - rw) / 2), b.getY());
      }
    }
  }

  private int lastX, lastY;

  public void draw (Canvas c, ComplexReceiver complexReceiver, int x, int y) {
    for (BubbleView2 view : bubbles) {
      view.draw(c, complexReceiver, width, lastX = x, lastY = y);
    }
  }

  public RunnableData<TdApi.MessageSender> onClickListener;

  public void setOnClickListener (RunnableData<TdApi.MessageSender> onClickListener) {
    this.onClickListener = onClickListener;
  }

  // Touch

  private int caughtIndex = -1;

  private void completeTouch (View v, TdApi.MessageSender senderId) {
    ViewUtils.onClick(v);
    if (onClickListener != null) {
      onClickListener.runWithData(senderId);
    }
  }

  public boolean onTouchEvent (View v, MotionEvent e) {
    if (bubbles.isEmpty()) {
      return false;
    }

    int x = Math.round(e.getX() - lastX);
    int y = Math.round(e.getY() - lastY);

    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      caughtIndex = findButtonByPosition(x, y);
    }

    boolean result = caughtIndex != -1 && caughtIndex >= 0 && caughtIndex < bubbles.size() && bubbles.get(caughtIndex).onTouchEvent(v, e);
    if (caughtIndex != -1 && (e.getAction() == MotionEvent.ACTION_CANCEL || e.getAction() == MotionEvent.ACTION_UP)) {
      caughtIndex = -1;
    }
    return result;
  }

  private int findButtonByPosition (int x, int y) {
    final int spacing = (int) ((float) Screen.dp(SPACING) * .5f);

    for (int i = 0; i < bubbles.size(); i++) {
      final BubbleView2 view = bubbles.get(i);

      int cx = view.getX();
      int cy = view.getY();
      int w = view.getWidth();
      int h = view.getHeight();

      if (Lang.rtl()) {
        cx = width - cx - w;
      }

      if (x >= cx - spacing && x < cx + w + spacing && y >= cy - spacing && y < cy + h + spacing) {
        return i;
      }
    }
    return -1;
  }

  public boolean performLongPress (View view) {
    boolean result = false;
    for (BubbleView2 button : bubbles) {
      if (button.performLongPress(view)) {
        result = true;
      }
    }
    return result;
  }
}
