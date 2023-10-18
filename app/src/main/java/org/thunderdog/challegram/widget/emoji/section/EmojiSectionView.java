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
 * File created on 18/08/2023
 */
package org.thunderdog.challegram.widget.emoji.section;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.thunderdog.challegram.tool.Screen;

public class EmojiSectionView extends View {
  private int forceWidth = -1;

  public EmojiSectionView (Context context) {
    super(context);
  }

  private EmojiSection section;

  public void setSection (EmojiSection section) {
    if (this.section != null) {
      this.section.setCurrentView(null);
    }
    this.section = section;
    if (section != null) {
      section.setCurrentView(this);
    }
  }

  public EmojiSection getSection () {
    return section;
  }

  public void setForceWidth (int width) {
    forceWidth = width;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int itemWidth = forceWidth > 0 ? forceWidth : Screen.dp(44);
    setMeasuredDimension(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
  }

  @Override
  protected void onDraw (Canvas c) {
    if (section != null) {
      section.draw(c, getMeasuredWidth() / 2, getMeasuredHeight() / 2);
    }
  }
}
