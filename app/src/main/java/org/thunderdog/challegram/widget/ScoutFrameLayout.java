/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 19/08/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class ScoutFrameLayout extends FrameLayoutFix implements AttachDelegate, Destroyable {
  public ScoutFrameLayout (@NonNull Context context) {
    super(context);
  }

  public ScoutFrameLayout (@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public ScoutFrameLayout (@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void attach () {
    final int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      attachChild(getChildAt(i), true);
    }
  }

  @Override
  public void detach () {
    final int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      attachChild(getChildAt(i), false);
    }
  }

  @Override
  public void performDestroy () {
    final int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      destroyChild(getChildAt(i));
    }
  }

  private static void destroyChild (@Nullable View view) {
    if (view != null) {
      if (view instanceof Destroyable) {
        ((Destroyable) view).performDestroy();
      }
      if (view instanceof ViewGroup) {
        ViewGroup viewGroup = (ViewGroup) view;
        final int childCount = viewGroup.getChildCount();
        for (int i = 0; i < childCount; i++) {
          destroyChild(viewGroup.getChildAt(i));
        }
      }
    }
  }

  private static void attachChild (@Nullable View view, boolean attach) {
    if (view != null) {
      if (view instanceof AttachDelegate) {
        if (attach) {
          ((AttachDelegate) view).attach();
        } else {
          ((AttachDelegate) view).detach();
        }
      }
      if (view instanceof ViewGroup) {
        ViewGroup viewGroup = (ViewGroup) view;
        final int childCount = viewGroup.getChildCount();
        for (int i = 0; i < childCount; i++) {
          attachChild(viewGroup.getChildAt(i), attach);
        }
      }
    }
  }
}
