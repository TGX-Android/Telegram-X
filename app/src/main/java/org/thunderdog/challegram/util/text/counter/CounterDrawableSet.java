package org.thunderdog.challegram.util.text.counter;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.TextColorSet;

import java.util.HashMap;

import me.vkryl.core.ColorUtils;

public abstract class CounterDrawableSet implements Counter.CustomTextPartBuilder {
  private final HashMap<String, CharDrawable> set = new HashMap<>();

  public abstract CharDrawable onCreateTextDrawableImpl (String text);

  @Override
  public final CounterTextPart onCreateTextDrawable (String text) {
    CharDrawable part = set.get(text);

    if (part == null) {
      part = onCreateTextDrawableImpl(text);
      set.put(text, part);
    }

    return part;
  }

  public static class CharDrawable implements CounterTextPart {
    public final Drawable drawable;
    public final String text;
    private int gap;

    public CharDrawable setGap (int gap) {
      this.gap = gap;
      return this;
    }

    public CharDrawable (Drawable drawable, String text) {
      this.drawable = drawable;
      this.text = text;
    }

    @Override
    public void draw (Canvas c, int startX, int endX, int endXBottomPadding, int startY, @Nullable TextColorSet defaultTheme, float alpha) {
      Drawables.draw(c, drawable, startX + gap, startY, defaultTheme != null ?
        Paints.getPorterDuffPaint(ColorUtils.alphaColor(alpha, defaultTheme.defaultTextColor())) :
        PorterDuffPaint.get(ColorId.text, alpha)
      );
    }

    @Override
    public int getWidth () {
      return drawable.getMinimumWidth() + gap;
    }

    @Override
    public int getHeight () {
      return drawable.getMinimumHeight();
    }

    @Override
    public String getText () {
      return text;
    }
  }
}
