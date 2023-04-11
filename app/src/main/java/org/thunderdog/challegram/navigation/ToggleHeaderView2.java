package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;

public class ToggleHeaderView2 extends View {
  private final ReplaceAnimator<TrimmedText> titleR = new ReplaceAnimator<>((r) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
  private final ReplaceAnimator<TrimmedText> subtitleR = new ReplaceAnimator<>((r) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
  private final Drawable arrowDrawable;

  private int textPadding;
  private int textTop;

  private float triangleTop;

  private int textColor;

  public ToggleHeaderView2(Context context) {
    super(context);
    textColor = Theme.headerTextColor();
    arrowDrawable = Drawables.get(R.drawable.baseline_keyboard_arrow_down_20);
  }

  public void setTitle (String title, boolean animated) {
    titleR.replace(new TrimmedText(title, Paints.getMediumTextPaint(18f, textColor, false)), animated);
    triangleTop = Screen.dp(4f + 8.5f);
    textTop = Screen.dp(18f + 8.5f);
    textPadding = Screen.dp(10f);
    trimTexts();
    requestLayout();
    invalidate();
  }

  public void setSubtitle (String subtitle, boolean animated) {
    subtitleR.replace(new TrimmedText(subtitle, Paints.getRegularTextPaint(14f, Theme.getColor(R.id.theme_color_textLight))), animated);
  }

  private void trimTexts () {
    int avail = getMeasuredWidth() - textPadding - Screen.dp(12f);
    for (ListAnimator.Entry<TrimmedText> entry: titleR) {
      entry.item.measure(avail);
    }
    for (ListAnimator.Entry<TrimmedText> entry: subtitleR) {
      entry.item.measure(avail);
    }
  }

  public void setTextColor (int color) {
    if (this.textColor != color) {
      this.textColor  = color;
      invalidate();
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    trimTexts();
  }

  private float getTitleWidth () {
    float width = 0f;
    for (ListAnimator.Entry<TrimmedText> entry: titleR) {
      width += entry.item.getWidth() * entry.getVisibility();
    }

    return width;
  }

  @Override
  protected void onDraw (Canvas c) {
    for (ListAnimator.Entry<TrimmedText> entry: titleR) {
      final int offset2 = (int) ((!entry.isAffectingList() ?
        ((entry.getVisibility() - 1f) * Screen.dp(18)):
        ((1f - entry.getVisibility()) * Screen.dp(18))));
      entry.item.draw(c, getPaddingLeft(), textTop + offset2, entry.getVisibility());
    }
    for (ListAnimator.Entry<TrimmedText> entry: subtitleR) {
      final int offset2 = (int) ((!entry.isAffectingList() ?
        ((entry.getVisibility() - 1f) * Screen.dp(14)):
        ((1f - entry.getVisibility()) * Screen.dp(14))));
      entry.item.draw(c, getPaddingLeft(), textTop + Screen.dp(18) + offset2, entry.getVisibility());
    }

    Drawables.draw(c, arrowDrawable, getTitleWidth() + Screen.dp(2), triangleTop, Paints.getPorterDuffPaint(Theme.getColor(R.id.theme_color_icon)));
  }


  private static class TrimmedText {
    private final TextPaint paint;
    private final String text;
    private String textTrimmed;
    private float textTrimmedWidth;
    private final float textWidth;

    public TrimmedText (String text, TextPaint paint) {
      this.paint = paint;
      this.text = text;
      this.textWidth = U.measureText(text, paint);
    }

    public void measure (int width) {
      if (textWidth <= width) {
        textTrimmed = null;
        textTrimmedWidth = 0;
      } else {
        textTrimmed = TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString();
        textTrimmedWidth = U.measureText(textTrimmed, paint);
      }
    }

    public float getWidth () {
      return textTrimmed != null ? textTrimmedWidth: textWidth;
    }

    public void draw (Canvas canvas, int x, int y, float alpha) {
      paint.setAlpha((int) (alpha * 255));
      canvas.drawText(textTrimmed != null ? textTrimmed : text, x, y, paint);
    }
  }
}
