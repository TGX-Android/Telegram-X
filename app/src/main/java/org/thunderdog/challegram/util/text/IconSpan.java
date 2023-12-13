package org.thunderdog.challegram.util.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.DynamicDrawableSpan;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.PorterDuffPaint;

public class IconSpan extends DynamicDrawableSpan implements TextReplacementSpan {

  private final Drawable drawable;
  private final @PorterDuffColorId int iconColorId;

  public IconSpan (@DrawableRes int iconRes, @PorterDuffColorId int iconColorId) {
    super(DynamicDrawableSpan.ALIGN_BOTTOM);
    this.drawable = Drawables.get(iconRes);
    this.drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    this.iconColorId = iconColorId;
  }

  @Override
  public Drawable getDrawable () {
    return drawable;
  }

  @Override
  public void draw (@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
    drawable.setColorFilter(PorterDuffPaint.get(iconColorId).getColorFilter());
    super.draw(canvas, text, start, end, x, top, y, bottom, paint);
  }

  @Override
  public int getSize (@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
    if (fm != null) {
      paint.getFontMetricsInt(fm);
    }
    return getRawSize(paint);
  }

  @Override
  public int getRawSize (Paint paint) {
    return drawable.getIntrinsicWidth();
  }
}
