package org.thunderdog.challegram.util.text.quotes;

import static org.thunderdog.challegram.theme.Theme.RIPPLE_COLOR;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.util.StateSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextPart;

import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;

public class QuoteBackground implements Drawable.Callback {
  public static final int QUOTE_LEFT_OFFSET = 11;
  public static final int QUOTE_RIGHT_OFFSET = 24;
  public static final int QUOTE_VERTICAL_OFFSET = 3;
  public static final int QUOTE_VERTICAL_MARGIN = 3;

  private static Drawable quoteDrawable;
  private static final RectF tmpRect = new RectF();

  private int topAddition;
  private int bottomAddition;

  public final Text parent;
  public final TextEntity entity;
  public int partStart;
  public int partEnd;

  public final RectF bounds = new RectF();
  public final @Nullable Drawable ripple;
  public final BackgroundDrawable rippleBackgroundDrawable;

  public QuoteBackground (Text text, TextEntity entity) {
    this.parent = text;
    this.entity = entity;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      rippleBackgroundDrawable = new BackgroundDrawable();


      ripple = new android.graphics.drawable.RippleDrawable(
        new ColorStateList(new int[][] {StateSet.WILD_CARD}, new int[] {RIPPLE_COLOR}),
        rippleBackgroundDrawable,
        new BackgroundDrawable()
      );
      ripple.setCallback(this);
    } else {
      ripple = null;
      rippleBackgroundDrawable = null;
    }
  }

  public void calcHeightAddition () {
    final int lineHeight = parent.getLineHeight();
    this.topAddition = TextPart.getAdditionalLinesBefore(parent.getTextPart(partStart)) * lineHeight;
    this.bottomAddition = TextPart.getAdditionalLinesAfter(parent.getTextPart(partEnd - 1)) * lineHeight;
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

    bounds.left -= Screen.dp(QUOTE_LEFT_OFFSET);
    bounds.top -= Screen.dp(QUOTE_VERTICAL_OFFSET) + topAddition;
    bounds.right += Screen.dp(QUOTE_RIGHT_OFFSET);
    bounds.bottom += Screen.dp(QUOTE_VERTICAL_OFFSET) + bottomAddition;

    /* * */

    final int colorId = parent.getQuoteTextColorId();
    final int color = Theme.getColor(colorId);

    if (ripple != null) {
      rippleBackgroundDrawable.setColor(ColorUtils.alphaColor(0.05f, color));
      ripple.setBounds((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom);
      ripple.draw(c);
    }

    Drawables.draw(c, quoteDrawable, bounds.right - Screen.dp(16), bounds.top + Screen.dp(3), PorterDuffPaint.get(colorId));

    tmpRect.set(bounds);
    tmpRect.right = bounds.left + Screen.dp(3);
    c.drawRoundRect(tmpRect, Screen.dp(1.5f), Screen.dp(1.5f), Paints.fillingPaint(color));
  }

  public boolean setPressed (int x, int y) {
    if (bounds.contains(x, y)) {
      if (ripple != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        ripple.setHotspot(x, y);
        ripple.setState(new int[] {android.R.attr.state_pressed, android.R.attr.state_enabled});
      }
      return true;
    }

    return false;
  }

  public void cancelTouch () {
    if (ripple != null) {
      ripple.setState(new int[0]);
    }
  }

  @Override
  public void invalidateDrawable (@NonNull Drawable who) {
    ViewProvider vp = parent.getViewProvider();
    if (vp != null) {
      vp.invalidate();
    }
  }

  @Override
  public void scheduleDrawable (@NonNull Drawable who, @NonNull Runnable what, long when) {

  }

  @Override
  public void unscheduleDrawable (@NonNull Drawable who, @NonNull Runnable what) {

  }

  public static class BackgroundDrawable extends Drawable {
    private static final RectF tmpRect = new RectF();
    private static final float[] TEMP_RADII = new float[8];

    private final Path path = new Path();
    private int color = 0xFFFFFFFF;

    @Override
    public void draw (@NonNull Canvas canvas) {
      canvas.drawPath(path, Paints.fillingPaint(color));
    }

    @Override
    public void setBounds (int left, int top, int right, int bottom) {
      super.setBounds(left, top, right, bottom);

      TEMP_RADII[0] = TEMP_RADII[1] = Screen.dp(1.5f);
      TEMP_RADII[2] = TEMP_RADII[3] = Screen.dp(8);
      TEMP_RADII[4] = TEMP_RADII[5] = Screen.dp(8);
      TEMP_RADII[6] = TEMP_RADII[7] = Screen.dp(1.5f);
      tmpRect.set(getBounds());

      path.reset();
      path.addRoundRect(tmpRect, TEMP_RADII, Path.Direction.CCW);
      path.close();
    }

    public void setColor (int color) {
      if (this.color != color) {
        this.color = color;
      }
    }

    @Override
    public void setAlpha (int alpha) {

    }

    @Override
    public void setColorFilter (@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity () {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
