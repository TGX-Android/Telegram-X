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
 * File created on 09/08/2015 at 16:28
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Letters;

import me.vkryl.core.StringUtils;

public class ToggleHeaderView extends View {
  // private TextPaint paint;

  private Letters text;
  private String textTrimmed;
  private float textTrimmedWidth;
  private float textWidth;
  private int textPadding;
  private int textTop;

  private TriangleView triangle;
  private float triangleTop;

  private int textColor;

  public ToggleHeaderView (Context context) {
    super(context);
    this.textColor = Theme.headerTextColor();
    triangle = new TriangleView();
  }

  public float getTriangleCenterX () {
    return triangle.getCenterX();
  }

  private TextPaint getPaint (boolean needFakeBold, boolean willDraw) {
    if (willDraw) {
      return Paints.getMediumTextPaint(19f, textColor, needFakeBold);
    } else {
      return Paints.getMediumTextPaint(19f, needFakeBold);
    }
  }

  public void setText (CharSequence sequence) {
    this.text = !StringUtils.isEmpty(sequence) ? new Letters(sequence.toString()) : null;
    this.textWidth = text != null ? U.measureText(text.text, getPaint(text.needFakeBold, false)) : 0;
    this.triangleTop = Screen.dp(12f);
    this.textTop = Screen.dp(20f);
    this.textPadding = Screen.dp(10f);
    trimText();
    requestLayout();
    invalidate();
  }

  private void trimText () {
    int avail = getMeasuredWidth() - textPadding - Screen.dp(12f);
    if (text == null || getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT || textWidth <= avail) {
      textTrimmed = null;
      textTrimmedWidth = 0;
    } else {
      TextPaint paint = getPaint(text.needFakeBold, false);
      textTrimmed = TextUtils.ellipsize(text.text, paint, avail, TextUtils.TruncateAt.END).toString();
      textTrimmedWidth = U.measureText(textTrimmed, paint);
    }
  }

  public void setTextColor (int color) {
    if (this.textColor != color) {
      this.textColor  = color;
      invalidate();
    }
  }

  public void setTriangleColor (int color) {
    triangle.setColor(color);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (getLayoutParams().width == ViewGroup.LayoutParams.WRAP_CONTENT) {
      setMeasuredDimension((int) (textWidth + triangle.getWidth() + textPadding), getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    } else {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      trimText();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    float textWidth = (textTrimmed != null ? textTrimmedWidth : this.textWidth);
    if (Lang.rtl()) {
      int viewWidth = getMeasuredWidth();
      if (text != null)
        c.drawText(textTrimmed != null ? textTrimmed : text.text, viewWidth - textWidth, textTop, getPaint(text.needFakeBold, true));
      c.save();
      c.translate(viewWidth - textWidth - textPadding - triangle.getWidth(), triangleTop);
      triangle.draw(c);
      c.restore();
    } else {
      if (text != null)
        c.drawText(textTrimmed != null ? textTrimmed : text.text, 0, textTop, getPaint(text.needFakeBold, true));
      c.save();
      c.translate(textWidth + textPadding, triangleTop);
      triangle.draw(c);
      c.restore();
    }
  }
}
