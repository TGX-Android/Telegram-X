package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;

import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * Date: 14/11/2016
 * Author: default
 */

public class ToastView extends NoScrollTextView {
  public ToastView (Context context) {
    super(context);

    setTypeface(Fonts.getRobotoRegular());
    setTextColor(0xffffffff);
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    setPadding(Screen.dp(10f), Screen.dp(10f), Screen.dp(10f), Screen.dp(10f));
    setGravity(Gravity.CENTER);

    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  @Override
  protected void onDraw (Canvas c) {
    RectF rectF = Paints.getRectF();
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    int radius = Screen.dp(10f);
    rectF.set(0, 0, width, height);
    c.drawRoundRect(rectF, radius, radius, Paints.fillingPaint(0xa0000000));

    super.onDraw(c);
  }
}
