/**
 * File created on 08/08/15 at 16:42
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.component.passcode;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

public class PinButtonView extends View {
  // private static Paint bigPaint;
  // private static Paint smallPaint;

  private int number;
  private Drawable icon;

  private static final float TEXT_SIZE_BIG = 34f;
  private static final float TEXT_SIZE_SMALL = 11f;

  public PinButtonView (Context context) {
    super(context);
    Views.setClickable(this);
  }

  public void setHasFeedback (boolean has) {
    setBackgroundResource(has ? R.drawable.bg_btn_pin : R.drawable.transparent);
  }

  public void setNumber (int number) {
    this.number = number;
  }

  public String getNumber () {
    return String.valueOf(number);
  }

  @SuppressWarnings(value = "SpellCheckingInspection")
  public String getCodes () {
    switch (number) {
      case 0:
        return "+";
      case 1:
        return "";
      case 2:
        return "ABC";
      case 3:
        return "DEF";
      case 4:
        return "GHI";
      case 5:
        return "JKL";
      case 6:
        return "MNO";
      case 7:
        return "PQRS";
      case 8:
        return "TUV";
      case 9:
        return "WXYZ";
      default:
        return null;
    }
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    if (changed) {
      buildLayout();
    }
  }

  private float smallLeft, smallTop;
  private float bigLeft, bigTop;

  private void buildLayout () {
    float totalWidth = getMeasuredWidth();
    float totalHeight = getMeasuredHeight();

    float centerX = totalWidth * .5f;
    float centerY = totalHeight * .5f;

    if (number == -1) {
      if (icon == null) {
        icon = Drawables.get(getResources(), R.drawable.baseline_backspace_24);
      }

      bigLeft = centerX - icon.getMinimumWidth() * .5f;
      bigTop = centerY - icon.getMinimumHeight() * .5f - Screen.dp(10f);

      return;
    }

    smallLeft = centerX - U.measureText(getCodes(), Paints.getRegularTextPaint(TEXT_SIZE_SMALL)) * .5f;
    bigLeft = centerX - U.measureText(getNumber(), Paints.getRegularTextPaint(TEXT_SIZE_BIG)) * .5f;

    float offset = 22f;

    bigTop = centerY - Screen.dp(20f - offset);
    smallTop = centerY - Screen.dp(-offset);
  }

  @Override
  protected void onDraw (Canvas c) {
    if (number == -1) {
      Drawables.draw(c, icon, bigLeft, bigTop, Paints.getPorterDuffPaint(Theme.getColor(R.id.theme_color_passcodeText)));
    } else {
      c.drawText(getNumber(), bigLeft, bigTop, Paints.getRegularTextPaint(TEXT_SIZE_BIG, Theme.getColor(R.id.theme_color_passcodeText)));
      c.drawText(getCodes(), smallLeft, smallTop, Paints.getRegularTextPaint(TEXT_SIZE_SMALL, Theme.passcodeSubtitleColor()));
    }
  }
}
