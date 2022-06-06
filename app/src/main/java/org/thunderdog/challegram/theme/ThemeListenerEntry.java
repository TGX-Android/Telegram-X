/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 18/01/2017
 */
package org.thunderdog.challegram.theme;

import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IntDef;

import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.TextChangeDelegate;
import org.thunderdog.challegram.tool.Paints;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.BitwiseUtils;

public class ThemeListenerEntry {
  public static final int MODE_INVALIDATE = 0;
  public static final int MODE_BACKGROUND = 1;
  public static final int MODE_TEXT_COLOR = 2;
  public static final int MODE_HINT_TEXT_COLOR = 3;
  public static final int MODE_HIGHLIGHT_COLOR = 4;
  public static final int MODE_PAINT_COLOR = 5;
  public static final int MODE_FILTER = 6;
  public static final int MODE_SPECIAL_FILTER = 7;
  public static final int MODE_DOUBLE_TEXT_COLOR = 8;
  public static final int MODE_LINK_TEXT_COLOR = 9;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    MODE_INVALIDATE,
    MODE_BACKGROUND,
    MODE_TEXT_COLOR,
    MODE_HINT_TEXT_COLOR,
    MODE_LINK_TEXT_COLOR,
    MODE_HIGHLIGHT_COLOR,
    MODE_PAINT_COLOR,
    MODE_FILTER,
    // MODE_SELECTION,
    // MODE_FROM_TO,
    MODE_SPECIAL_FILTER,
    MODE_DOUBLE_TEXT_COLOR
  })
  public @interface EntryMode {}

  private final @EntryMode int mode;
  private @ThemeColorId
  int targetColor;
  private final WeakReference<Object> target;

  private static final int FLAG_NO_TEMP_UPDATES = 1;
  private static final int FLAG_SUBTITLE = 1 << 1;

  private int flags;

  private int arg1;
  private float alpha = 1.0f;
  private Object obj;

  public void setIsSubtitle (boolean isSubtitle) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SUBTITLE, isSubtitle);
  }

  public ThemeListenerEntry (@EntryMode int mode, @ThemeColorId int targetColor, Object target) {
    this.mode = mode;
    this.targetColor = targetColor;
    this.target = new WeakReference<>(target);
  }

  public ThemeListenerEntry setNoTempUpdates () {
    flags |= FLAG_NO_TEMP_UPDATES;
    return this;
  }

  public void setTargetColorId (@ThemeColorId int colorId) {
    this.targetColor = colorId;
  }

  public ThemeListenerEntry setArg1 (int arg1) {
    this.arg1 = arg1;
    return this;
  }

  public ThemeListenerEntry setAlpha (float alpha) {
    this.alpha = alpha;
    return this;
  }

  public ThemeListenerEntry setObj (Object obj) {
    this.obj = obj;
    return this;
  }

  private boolean hasTargetColorChanged () {
    return ThemeManager.instance().hasColorChanged(targetColor);
  }

  public boolean isEmpty () {
    return target.get() == null;
  }

  public boolean targetEquals (Object target) {
    return target != null && target.equals(this.target.get());
  }

  public boolean targetEquals (Object target, int mode) {
    return target != null && target.equals(this.target.get()) && this.mode == mode;
  }

  private int getTargetColor () {
    int color = Theme.getColor(targetColor);
    if ((flags & FLAG_SUBTITLE) != 0)
      color = ColorUtils.alphaColor(Theme.getSubtitleAlpha(), color);
    if (alpha != 1f)
      color = ColorUtils.alphaColor(alpha, color);
    return color;
  }

  public boolean apply (boolean isTemp) {
    Object target = this.target.get();
    if (target != null) {
      final boolean isView = target instanceof View;
      if (isTemp) {
        if ((flags & FLAG_NO_TEMP_UPDATES) != 0 || (isView && ((View) target).getParent() == null)) {
          return true;
        }
      }
      switch (mode) {
        case MODE_INVALIDATE: {
          if (target instanceof ThemeInvalidateListener) {
            ((ThemeInvalidateListener) target).onThemeInvalidate(isTemp);
          } else if (isView) {
            ((View) target).invalidate();
          }
          break;
        }
        case MODE_BACKGROUND: {
          if (hasTargetColorChanged()) {
            if (isView) {
              ((View) target).setBackgroundColor(getTargetColor());
            }
          }
          break;
        }
        case MODE_TEXT_COLOR: {
          if (hasTargetColorChanged()) {
            if (target instanceof TextView) {
              ((TextView) target).setTextColor(getTargetColor());
            } else if (target instanceof TextChangeDelegate) {
              ((TextChangeDelegate) target).setTextColor(getTargetColor());
            } else if (target instanceof BackHeaderButton) {
              ((BackHeaderButton) target).setColor(getTargetColor());
            }
          }
          break;
        }
        case MODE_HINT_TEXT_COLOR: {
          if (hasTargetColorChanged()) {
            if (target instanceof TextView) {
              ((TextView) target).setHintTextColor(getTargetColor());
            }
          }
          break;
        }
        case MODE_LINK_TEXT_COLOR: {
          if (hasTargetColorChanged()) {
            if (target instanceof TextView) {
              ((TextView) target).setLinkTextColor(getTargetColor());
            }
          }
          break;
        }
        case MODE_PAINT_COLOR: {
          if (hasTargetColorChanged()) {
            if (target instanceof Paint) {
              ((Paint) target).setColor(getTargetColor());
            }
          }
          break;
        }
        case MODE_FILTER: {
          if (hasTargetColorChanged()) {
            if (target instanceof Paint) {
              ((Paint) target).setColorFilter(Paints.getColorFilter(getTargetColor()));
            } else if (target instanceof ImageView) {
              ((ImageView) target).setColorFilter(getTargetColor());
            } else if (target instanceof Drawable) {
              ((Drawable) target).setColorFilter(Paints.getColorFilter(getTargetColor()));
            }
          }
          break;
        }
        case MODE_HIGHLIGHT_COLOR: {
          if (hasTargetColorChanged()) {
            if (target instanceof TextView) {
              ((TextView) target).setHighlightColor(getTargetColor());
            }
          }
          break;
        }
        case MODE_SPECIAL_FILTER: {
          if (hasTargetColorChanged()) {
            if (target instanceof ImageView) {
              ((ImageView) target).setColorFilter(new PorterDuffColorFilter(getTargetColor(), PorterDuff.Mode.MULTIPLY));
            } else if (target instanceof Drawable) {
              ((Drawable) target).setColorFilter(new PorterDuffColorFilter(getTargetColor(), PorterDuff.Mode.MULTIPLY));
            }
          }
          break;
        }
        case MODE_DOUBLE_TEXT_COLOR: {
          if (hasTargetColorChanged() || ThemeManager.instance().hasColorChanged(arg1)) {
            if (target instanceof ComplexHeaderView) {
              ((ComplexHeaderView) target).setTextColors(getTargetColor(), Theme.getColor(arg1));
            } else if (target instanceof DoubleHeaderView) {
              ((DoubleHeaderView) target).setTextColors(getTargetColor(), Theme.getColor(arg1));
            }
          }
          break;
        }
      }
      return true;
    }
    return false;
  }
}
