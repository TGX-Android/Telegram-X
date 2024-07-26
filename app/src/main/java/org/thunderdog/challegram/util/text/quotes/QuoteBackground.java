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
 * File created on 12/06/2024
 */

package org.thunderdog.challegram.util.text.quotes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.RippleDrawable;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextPart;

import me.vkryl.android.util.ViewProvider;

public class QuoteBackground {
  public static final int QUOTE_LEFT_PADDING = 11;
  public static final int QUOTE_RIGHT_PADDING = 27;
  public static final int QUOTE_VERTICAL_PADDING = 2;
  public static final int QUOTE_VERTICAL_MARGIN = 8;

  private static Drawable quoteDrawable;
  private static final RectF tmpRect = new RectF();

  private int topAddition;
  private int bottomAddition;

  public final Text parent;
  public final TextEntity entity;
  public int partStart;
  public int partEnd;

  public final RectF bounds = new RectF();
  private final RippleDrawable ripple;

  public QuoteBackground (Text text, TextEntity entity) {
    this.parent = text;
    this.entity = entity;

    this.ripple = new RippleDrawable();
  }

  public void setViewProvider (@Nullable ViewProvider viewProvider) {
    ripple.setViewProvider(viewProvider);
  }

  public void performLongPress (View view) {
    ripple.performLongPress(view);
  }

  public void performCancelTouch () {
    ripple.performCancelTouch();
  }

  public boolean contains (int x, int y) {
    return ripple.bounds.contains(x, y);
  }

  public boolean onTouchEvent (View view, MotionEvent e) {
    return ripple.onTouchEvent(view, e, (int) (e.getX() - ripple.bounds.left), (int) (e.getY() - ripple.bounds.top));
  }



  public void calcHeightAddition () {
    final int lineHeight = parent.getLineHeight();
    this.topAddition = TextPart.getAdditionalLinesBefore(parent.getTextPart(partStart)) * lineHeight;

    final int count = TextPart.getAdditionalLinesAfter(parent.getTextPart(partEnd - 1));
    this.bottomAddition = (count == 1 ? 0 : count) * lineHeight;
  }

  public void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY) {
    if (quoteDrawable == null) {
      quoteDrawable = Drawables.get(R.drawable.baseline_format_quote_close_16);
    }

    for (int a = partStart; a < partEnd; a++) {
      final TextPart part = parent.getTextPart(a);
      final TextPaint paint = parent.getTextPaint(part.getEntity());
      final Paint.FontMetricsInt fm = parent.getFontMetrics(paint.getTextSize());
      final int height = (part.getHeight() == -1 ? fm.descent - fm.ascent : part.getHeight());

      final int x = part.makeX(startX, endX, endXBottomPadding);
      final int y = part.getY() + startY;

      if (a == partStart) {
        bounds.set(x, y, x + (int) part.getWidth(), y + height);
      } else {
        bounds.union(x, y, x + (int) part.getWidth(), y + height);
      }
    }

    bounds.left -= Screen.dp(QUOTE_LEFT_PADDING);
    bounds.top -= Screen.dp(QUOTE_VERTICAL_PADDING) + topAddition;
    bounds.right += Screen.dp(QUOTE_RIGHT_PADDING);
    bounds.bottom += Screen.dp(QUOTE_VERTICAL_PADDING) + bottomAddition;

    /* * */

    final int textColorId = parent.getQuoteTextColorId();
    final int lineColorId = parent.getQuoteLineColorId();
    final int lineColor = Theme.getColor(lineColorId);

    ripple.setBounds((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom);
    ripple.setRadius(Screen.dp(1.5f), Screen.dp(8f), Screen.dp(8f), Screen.dp(1.5f));
    ripple.setMainColorId(lineColorId, 0.25f);
    ripple.draw(c);

    Drawables.draw(c, quoteDrawable, bounds.right - Screen.dp(19), bounds.top + Screen.dp(3.5f), PorterDuffPaint.get(lineColorId));

    tmpRect.set(bounds);
    tmpRect.right = bounds.left + Screen.dp(3);
    c.drawRoundRect(tmpRect, Screen.dp(1.5f), Screen.dp(1.5f), Paints.fillingPaint(lineColor));
  }
}
