package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

/**
 * Date: 11/12/18
 * Author: default
 */
public class LineDrawModifier implements DrawModifier {
  @ThemeColorId
  private final int colorId;

  private ThemeDelegate forcedTheme;

  public LineDrawModifier (@ThemeColorId int colorId, @Nullable ThemeDelegate forcedTheme) {
    this.colorId = colorId;
    this.forcedTheme = forcedTheme;
  }

  @Override
  public void beforeDraw (View view, Canvas c) {
    RectF rectF = Paints.getRectF();

    rectF.top = Screen.dp(12f);
    rectF.bottom = view.getMeasuredHeight() - rectF.top;
    int width = Screen.dp(3f);
    int margin = Screen.dp(14f);
    if (Lang.rtl()) {
      rectF.left = view.getMeasuredWidth() - margin - width;
    } else {
      rectF.left = margin;
    }
    rectF.right = rectF.left + width;

    c.drawRoundRect(rectF, Screen.dp(1.5f), Screen.dp(1.5f), Paints.fillingPaint(forcedTheme != null ? forcedTheme.getColor(colorId) : Theme.getColor(colorId)));

    c.save();
    c.translate(Screen.dp(8f) * (Lang.rtl() ? -1 : 1), 0);
  }

  @Override
  public void afterDraw (View view, Canvas c) {
    c.restore();
  }
}
