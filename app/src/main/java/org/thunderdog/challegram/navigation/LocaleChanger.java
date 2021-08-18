/**
 * File created on 14/04/16 at 19:22
 * Copyright Vyacheslav Krylov, 2014
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
