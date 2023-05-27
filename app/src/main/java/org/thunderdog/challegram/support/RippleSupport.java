/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 18/05/2015 at 04:29
 */
package org.thunderdog.challegram.support;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.ColorChangeAcceptorDelegate;
import org.thunderdog.challegram.util.CustomStateListDrawable;

import java.util.ArrayList;

import me.vkryl.android.ViewUtils;

public class RippleSupport {
  public static void setSimpleWhiteBackground (@NonNull View view) {
    setSimpleWhiteBackground(view, null);
  }

  public static void setSimpleWhiteBackground (@NonNull View view, @Nullable ViewController<?> themeProvider) {
    ViewUtils.setBackground(view, Theme.fillingSelector());
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(view);
    }
  }

  public static void setSimpleWhiteBackground (@NonNull View view, @ColorId int backgroundColorId, @Nullable ViewController<?> themeProvider) {
    ViewUtils.setBackground(view, Theme.fillingSelector(backgroundColorId));
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(view);
    }
  }

  public static void setTransparentSelector (View view) {
    ViewUtils.setBackground(view, Theme.transparentSelector());
  }

  public static void setTransparentBlackSelector (View view) {
    ViewUtils.setBackground(view, Theme.transparentBlackSelector());
  }

  public static void setTransparentWhiteSelector (View view) {
    ViewUtils.setBackground(view, Theme.transparentWhiteSelector());
  }

  public static void invalidateRipple (Drawable drawable) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (drawable instanceof android.graphics.drawable.RippleDrawable) {
        android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable) drawable;
        ripple.invalidateSelf();
      }
    }
  }

  public static void setCircleBackground (View view, float size, float padding, @ColorId int colorId) {
    setCircleBackground(view, size, padding, colorId, null);
  }

  public static void changeViewColor (View view, @ColorId int fromColorId, @ColorId int toColorId, float factor) {
    if (view != null) {
      Drawable drawable = view.getBackground();
      boolean updated = false;
      if (drawable instanceof ColorChangeAcceptorDelegate) {
        ((ColorChangeAcceptorDelegate) drawable).applyColor(fromColorId, toColorId, factor);
        updated = true;
      } else if (drawable instanceof LayerDrawable) {
        LayerDrawable layerDrawable = (LayerDrawable) drawable;
        for (int i = 0; i < layerDrawable.getNumberOfLayers(); i++) {
          Drawable currentDrawable = layerDrawable.getDrawable(i);
          if (currentDrawable instanceof ColorChangeAcceptorDelegate) {
            ((ColorChangeAcceptorDelegate) currentDrawable).applyColor(fromColorId, toColorId, factor);
            updated = true;
          }
        }
      } else if (drawable instanceof CustomStateListDrawable) {
        ArrayList<Drawable> drawables = ((CustomStateListDrawable) drawable).getDrawableList();
        for (Drawable currentDrawable : drawables) {
          if (currentDrawable instanceof ColorChangeAcceptorDelegate) {
            ((ColorChangeAcceptorDelegate) currentDrawable).applyColor(fromColorId, toColorId, factor);
            updated = true;
          }
        }
      }
      if (updated) {
        view.invalidate();
      }
    }
  }

  public static int getCurrentViewColor (View view) {
    if (view != null) {
      Drawable drawable = view.getBackground();
      if (drawable instanceof ColorChangeAcceptorDelegate) {
        return ((ColorChangeAcceptorDelegate) drawable).getDrawColor();
      } else if (drawable instanceof LayerDrawable) {
        LayerDrawable layerDrawable = (LayerDrawable) drawable;
        for (int i = 0; i < layerDrawable.getNumberOfLayers(); i++) {
          Drawable currentDrawable = layerDrawable.getDrawable(i);
          if (currentDrawable instanceof ColorChangeAcceptorDelegate) {
            return ((ColorChangeAcceptorDelegate) currentDrawable).getDrawColor();
          }
        }
      } else if (drawable instanceof CustomStateListDrawable) {
        ArrayList<Drawable> drawables = ((CustomStateListDrawable) drawable).getDrawableList();
        for (Drawable currentDrawable : drawables) {
          if (currentDrawable instanceof ColorChangeAcceptorDelegate) {
            return ((ColorChangeAcceptorDelegate) currentDrawable).getDrawColor();
          }
        }
      }
    }
    return 0;
  }

  public static void setCircleBackground (View view, float size, float padding, @ColorId int colorId, @Nullable ViewController<?> themeProvider) {
    ViewUtils.setBackground(view, Theme.circleSelector(size, colorId));
    if (SimpleShapeDrawable.USE_SOFTWARE_SHADOW) {
      view.setLayerType(View.LAYER_TYPE_SOFTWARE, Views.getLayerPaint());
    }
    Views.initCircleButton(view, size, padding);
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(view);
    }
  }

  public static void setRectBackground (View view, float size, float padding, @ColorId int colorId) {
    setRectBackground(view, size, padding, colorId, null);
  }

  public static void setRectBackground (View view, float size, float padding, @ColorId int colorId, @Nullable ViewController<?> themeProvider) {
    ViewUtils.setBackground(view, Theme.rectSelector(size, padding, colorId));
    if (SimpleShapeDrawable.USE_SOFTWARE_SHADOW) {
      view.setLayerType(View.LAYER_TYPE_SOFTWARE, Views.getLayerPaint());
    }
    Views.initRectButton(view, size, padding);
    if (themeProvider != null) {
      themeProvider.addThemeInvalidateListener(view);
    }
  }
}
