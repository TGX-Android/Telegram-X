package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.widget.FrameLayoutFix;

public class BottomInsetFrameLayout extends FrameLayoutFix implements RootFrameLayout.MarginModifier {
  public BottomInsetFrameLayout (@NonNull Context context) {
    super(context);
  }

  @Override
  public void onApplyMarginInsets (View child, LayoutParams params, Rect legacyInsets, Rect insets, Rect insetsWithoutIme) {
    setBottomInset(insetsWithoutIme.bottom);
  }

  private int bottomInset;

  public void setBottomInset (int bottomInset) {
    if (this.bottomInset != bottomInset) {
      this.bottomInset = bottomInset;
      Views.setPaddingBottom(this, bottomInset);
      setWillNotDraw(bottomInset == 0);
    }
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    if (getPaddingBottom() > 0) {
      c.drawRect(0, getMeasuredHeight() - getPaddingBottom(), getMeasuredWidth(), getMeasuredHeight(), Paints.fillingPaint(Theme.fillingColor()));
    }
  }
}
