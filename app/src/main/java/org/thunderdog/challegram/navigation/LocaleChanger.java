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
 * File created on 14/04/2016 at 19:22
 */
package org.thunderdog.challegram.navigation;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Views;

import java.lang.ref.WeakReference;

public class LocaleChanger {
  private static final int TYPE_TEXT = 5;
  private static final int TYPE_TEXT_HINT = 6;
  private static final int TYPE_CUSTOM = 100;

  public interface CustomCallback {
    void onLocaleChange (int arg1);
    @StringRes int provideCurrentStringResource ();
  }

  private final int type;
  private final int arg1;
  private final boolean isMedium;
  private final WeakReference<Object> obj;
  private CustomCallback callback;

  public LocaleChanger (int resource, TextView textView, boolean isHint, boolean isMedium) {
    this.type = isHint ? TYPE_TEXT_HINT : TYPE_TEXT;
    this.isMedium = isMedium;
    this.arg1 = resource;
    this.obj = new WeakReference<>(textView);
  }

  public LocaleChanger (View targetView, CustomCallback callback) {
    this.type = TYPE_CUSTOM;
    this.arg1 = 0;
    this.obj = new WeakReference<>(targetView);
    this.callback = callback;
    this.isMedium = false;
  }

  public int getResource () {
    if (type == TYPE_CUSTOM) {
      return callback != null ? callback.provideCurrentStringResource() : 0;
    }
    return arg1;
  }

  public final void onLocaleChange () {
    final Object obj = this.obj.get();
    if (obj == null) {
      this.callback = null;
      return;
    }
    switch (type) {
      case TYPE_TEXT:
        if (isMedium) {
          Views.setMediumText((TextView) obj, Lang.getString(arg1));
        } else {
          ((TextView) obj).setText(Lang.getString(arg1));
        }
        break;
      case TYPE_TEXT_HINT:
        ((TextView) obj).setHint(Lang.getString(arg1));
        break;
      case TYPE_CUSTOM: {
        if (callback != null) {
          callback.onLocaleChange(arg1);
        }
        break;
      }
    }
  }
}
