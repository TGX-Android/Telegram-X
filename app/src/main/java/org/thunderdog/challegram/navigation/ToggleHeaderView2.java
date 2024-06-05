package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;

public class ToggleHeaderView2 extends View {
  private final ReplaceAnimator<TrimmedText> titleR = new ReplaceAnimator<>((r) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
  private final ReplaceAnimator<TrimmedText> subtitleR = new ReplaceAnimator<>((r) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);
  private final Drawable arrowDrawable;

  @Dimension(unit = Dimension.DP)
  private float textPaddingDp = 10f;
  @Dimension(unit = Dimension.DP)
  private float textTopDp = 8.5f;
  @Dimension(unit = Dimension.DP)
  private float triangleTopDp = 8.5f;

  private ColorSet colorSet = ColorSet.DEFAULT;

  public interface ColorSet {
    ColorSet DEFAULT = new ColorSet() {
    };

    @ColorInt default int titleColor () {
      return Theme.getColor(ColorId.text);
    }

    @ColorInt default int subtitleColor () {
      return Theme.getColor(ColorId.textLight);
    }

    @PorterDuffColorId default int iconColorId () {
      return ColorId.icon;
    }
  }

  public ToggleHeaderView2 (Context context) {
    super(context);
    arrowDrawable = Drawables.get(R.drawable.baseline_keyboard_arrow_down_20);
  }

  public void setTitle (String title, boolean animated) {
    titleR.replace(new TrimmedText(title), animated);
    trimTexts();
    invalidate();
  }

  public void setSubtitle (String subtitle, boolean animated) {
    subtitleR.replace(new TrimmedText(subtitle), animated);
    trimTexts();
    invalidate();
  }

  public void setTextTop (@Dimension(unit = Dimension.DP) float textTop) {
    textTopDp = textTop;
  }

  public void setTriangleTop (@Dimension(unit = Dimension.DP) float triangleTop) {
    triangleTopDp = triangleTop;
  }

  public void setTextPadding (@Dimension(unit = Dimension.DP) float textPadding) {
    textPaddingDp = textPadding;
  }

  public void setColorSet (ColorSet colorSet) {
    this.colorSet = colorSet;
  }

  private void trimTexts () {
    int avail = getMeasuredWidth() - Screen.dp(textPaddingDp) - Screen.dp(12f);
    for (ListAnimator.Entry<TrimmedText> entry : titleR) {
      entry.item.measure(avail, Paints.getMediumTextPaint(18f, colorSet.titleColor(), false));
    }
    for (ListAnimator.Entry<TrimmedText> entry : subtitleR) {
      entry.item.measure(avail, Paints.getRegularTextPaint(14f, colorSet.subtitleColor()));
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    trimTexts();
  }

  public float getTitleWidth () {
    float width = 0f;
    for (ListAnimator.Entry<TrimmedText> entry : titleR) {
      width += entry.item.getWidth() * entry.getVisibility();
    }

    return width;
  }

  @Override
  protected void onDraw (@NonNull Canvas c) {
    final int textTop = Screen.dp(textTopDp) + Screen.dp(17);
    final int triangleTop = Screen.dp(triangleTopDp) + Screen.dp(3);
    for (ListAnimator.Entry<TrimmedText> entry : titleR) {
      final int offset2 = (int) ((!entry.isAffectingList() ?
        ((entry.getVisibility() - 1f) * Screen.dp(18)) :
        ((1f - entry.getVisibility()) * Screen.dp(18))));
      entry.item.draw(c, getPaddingLeft(), getPaddingTop() + textTop + offset2, entry.getVisibility(), Paints.getMediumTextPaint(18f, colorSet.titleColor(), false));
    }
    for (ListAnimator.Entry<TrimmedText> entry : subtitleR) {
      final int offset2 = (int) ((!entry.isAffectingList() ?
        ((entry.getVisibility() - 1f) * Screen.dp(14)) :
        ((1f - entry.getVisibility()) * Screen.dp(14))));
      entry.item.draw(c, getPaddingLeft(), getPaddingTop() + textTop + Screen.dp(19) + offset2, entry.getVisibility(), Paints.getRegularTextPaint(14f, colorSet.subtitleColor()));
    }

    Drawables.draw(c, arrowDrawable, getTitleWidth() + Screen.dp(2), getPaddingTop() + triangleTop, PorterDuffPaint.get(colorSet.iconColorId()));
  }


  private static class TrimmedText {
    private final String text;
    private String textTrimmed;
    private float textTrimmedWidth;
    private float textWidth;

    public TrimmedText (String text) {
      this.text = text;
    }

    public void measure (int width, TextPaint paint) {
      textWidth = U.measureText(text, paint);
      if (textWidth <= width) {
        textTrimmed = null;
        textTrimmedWidth = 0;
      } else {
        textTrimmed = TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString();
        textTrimmedWidth = U.measureText(textTrimmed, paint);
      }
    }

    public float getWidth () {
      return textTrimmed != null ? textTrimmedWidth : textWidth;
    }

    public void draw (Canvas canvas, int x, int y, float alpha, TextPaint paint) {
      paint.setAlpha((int) (alpha * 255));
      canvas.drawText(textTrimmed != null ? textTrimmed : text, x, y, paint);
    }
  }
}
