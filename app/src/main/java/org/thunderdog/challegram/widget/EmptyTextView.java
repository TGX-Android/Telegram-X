/**
 * File created on 23/02/16 at 11:58
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;

public class EmptyTextView extends NoScrollTextView {
  public EmptyTextView (Context context) {
    super(context);

    setTextColor(Theme.textDecent2Color());

    setTypeface(Fonts.getRobotoRegular());
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    setGravity(Gravity.CENTER);
  }

  private int lastSetTextColor;

  @Override
  public void setTextColor (int color) {
    this.lastSetTextColor = color;
    super.setTextColor(color);
  }

  public void setTextColorIfNeeded (int color) {
    if (this.lastSetTextColor != color) {
      this.lastSetTextColor = color;
      super.setTextColor(color);
    }
  }
}
