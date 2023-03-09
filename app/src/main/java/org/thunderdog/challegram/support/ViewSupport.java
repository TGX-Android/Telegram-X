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
 * File created on 18/05/2015 at 04:27
 */
package org.thunderdog.challegram.support;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.FillingDrawable;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import java.lang.reflect.Field;

import me.vkryl.android.ViewUtils;
import me.vkryl.core.ColorUtils;

public class ViewSupport {
  public static void setThemedBackground (View view, @ThemeColorId int colorId) {
    setThemedBackground(view, colorId, null);
    // view.setBackgroundColor(0);
  }

  public static FillingDrawable setThemedBackground (View view, @ThemeColorId int colorId, @Nullable ViewController<?> themeProvider) {
    FillingDrawable result = null;
    if (view != null) {
      Drawable existingBackground = view.getBackground();
      if (existingBackground instanceof FillingDrawable) {
        result = (FillingDrawable) existingBackground;
        result.setColorId(colorId);
      } else {
        ViewUtils.setBackground(view, result = new FillingDrawable(colorId));
      }
      if (themeProvider != null) {
        themeProvider.addThemeInvalidateListener(view);
      }
    }
    return result;
  }

  public static int clipPath (Canvas c, Path path) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && path != null) {
      int result = c.save();
      try {
        c.clipPath(path);
        return result;
      } catch (Throwable ignored) {
        c.restoreToCount(result);
        return Integer.MIN_VALUE;
      }
    }
    return Integer.MIN_VALUE;
  }

  public static void restoreClipPath (Canvas c, int restoreToCount) {
    if (restoreToCount != Integer.MIN_VALUE) {
      c.restoreToCount(restoreToCount);
    }
  }

  public static void showDatePicker (final DatePickerDialog dialog) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      dialog.setOnShowListener(ignored -> {
        final DatePicker datePicker = dialog.getDatePicker();
        try {
          Field mDatePickerField;
          mDatePickerField = DatePickerDialog.class.getDeclaredField("mDatePicker");
          mDatePickerField.setAccessible(true);
          final DatePicker mDatePicker = (DatePicker) mDatePickerField.get(dialog);

          int viewId = Resources.getSystem().getIdentifier("day_picker_selector_layout", "id", "android");
          if (viewId == 0) {
            viewId = Resources.getSystem().getIdentifier("date_picker_header", "id", "android");
          }
          ThemeDelegate theme = ThemeManager.instance().currentTheme(false);
          final View header = mDatePicker.findViewById(viewId);
          if (header != null) {
            final int bgColor = ColorUtils.compositeColor(theme.getColor(R.id.theme_color_headerBackground), theme.getColor(R.id.theme_color_headerPickerBackground));
            final int textColor = ColorUtils.compositeColor(theme.getColor(R.id.theme_color_headerText), theme.getColor(R.id.theme_color_headerPickerText));
            header.setBackgroundColor(bgColor);
            viewId = Resources.getSystem().getIdentifier("date_picker_header_year", "id", "android");
            if (viewId != 0) {
              View view = header.findViewById(viewId);
              if (view instanceof TextView)
                ((TextView) view).setTextColor(textColor);
            }
            viewId = Resources.getSystem().getIdentifier("date_picker_header_date", "id", "android");
            if (viewId != 0) {
              View view = header.findViewById(viewId);
              if (view instanceof TextView)
                ((TextView) view).setTextColor(textColor);
            }
          }

        } catch (Throwable t) {
          Log.i(t);
        }
        try {
          View view = Views.tryFindAndroidView(dialog.getContext(), dialog, "date_picker_day_picker");
          if (view != null && view.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
            params.gravity = Gravity.CENTER_HORIZONTAL;
            view.setLayoutParams(params);
          }
        } catch (Throwable t) {
          Log.i(t);
        }
      });
    }
    dialog.setButton(DialogInterface.BUTTON_POSITIVE, Lang.getString(R.string.OK), dialog);
    dialog.setButton(DialogInterface.BUTTON_NEGATIVE, Lang.getString(R.string.Cancel), dialog);
    dialog.show();
    BaseActivity.modifyAlert(dialog.getContext(), dialog, null);
  }

  public static void showTimePicker (final TimePickerDialog dialog) {
    dialog.setButton(DialogInterface.BUTTON_POSITIVE, Lang.getString(R.string.OK), dialog);
    dialog.setButton(DialogInterface.BUTTON_NEGATIVE, Lang.getString(R.string.Cancel), dialog);

    dialog.show();
    // Nothing to do?
    BaseActivity.modifyAlert(dialog.getContext(), dialog, null);
  }

  public static Drawable getDrawable (final Context context, final @DrawableRes int resource) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return context.getDrawable(resource);
    } else {
      //noinspection deprecation
      return context.getResources().getDrawable(resource);
    }
  }

  public static Drawable getDrawableFilter (final Context context, final @DrawableRes int resource, final ColorFilter filter) {
    Drawable drawable = getDrawable(context, resource);
    drawable.setColorFilter(filter);
    return drawable;
  }

  public static void setHigherElevation(View higher, View lower, boolean needElevation) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      if (needElevation) {
        higher.setElevation(lower.getElevation() + 1);
      }
      higher.setTranslationZ(lower.getTranslationZ() + 1);
    }
  }

  public static void setHigherElevation (View lower) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      lower.setTranslationZ(Screen.dp(4f));
    }
  }
}
