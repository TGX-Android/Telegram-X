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
 * File created on 23/04/2015 at 19:07
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Rect;

import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.RootFrameLayout;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class NavigationLayout extends FrameLayoutFix implements Destroyable, RootFrameLayout.InsetsChangeListener {
  public NavigationLayout (Context context) {
    super(context);
  }

  private RootFrameLayout rootView;

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    rootView = Views.findAncestor(this, RootFrameLayout.class, true);
    if (rootView != null) {
      rootView.addInsetsChangeListener(this);
      applyTopInset(rootView.getTopInset());
    }
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    if (rootView != null) {
      rootView.removeInsetsChangeListener(this);
      rootView = null;
    }
  }

  @Override
  public void onInsetsChanged (RootFrameLayout viewGroup, Rect effectiveInsets, Rect effectiveInsetsWithoutIme, Rect systemInsets, Rect systemInsetsWithoutIme, boolean isUpdate) {
    applyTopInset(effectiveInsets.top);
  }

  private void applyTopInset (int topInset) {
    int newSize = HeaderView.getSize(false) + topInset;
    if (newSize != getPaddingTop()) {
      setPadding(0, newSize, 0, 0);
    }
  }

  @Override
  public void performDestroy () {
    if (rootView != null) {
      rootView.removeInsetsChangeListener(this);
      rootView = null;
    }
  }

  /* optimization */

  private boolean preventLayout;
  private boolean layoutRequested;

  public void preventLayout () {
    preventLayout = true;
  }

  public void layoutIfRequested () {
    preventLayout = false;
    if (layoutRequested) {
      layoutRequested = false;
      requestLayout();
    }
  }

  public void cancelLayout () {
    preventLayout = false;
    layoutRequested = false;
  }

  public boolean isLayoutRequested () {
    return layoutRequested;
  }

  @Override
  public void requestLayout () {
    if (!preventLayout) {
      if (layoutLimit == -1) {
        super.requestLayout();
      } else if (layoutComplete < layoutLimit) {
        layoutComplete++;
        super.requestLayout();
      }
    } else {
      layoutRequested = true;
    }
  }

  private int layoutLimit = -1;
  private int layoutComplete;

  public void preventNextLayouts (int limit) {
    layoutLimit = limit;
    layoutComplete = 0;
  }

  public void completeNextLayout () {
    layoutLimit = -1;
    layoutComplete = 0;
  }

  /* optimization end */

  public void setController (NavigationController controller) {
    // NavigationController controller1 = controller;
  }
}
