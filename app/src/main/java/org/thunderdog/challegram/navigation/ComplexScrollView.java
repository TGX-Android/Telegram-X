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
 * File created on 10/08/2015 at 11:14
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.widget.ScrollView;

import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Size;

@Deprecated
public class ComplexScrollView extends ScrollView {
  private ComplexHeaderView headerView;
  private FloatingButton floatingButton;

  private float scrollFactor;
  private boolean factorLocked;

  public ComplexScrollView (Context context) {
    super(context);
    this.scrollFactor = 1f;
    this.factorLocked = true;
    setVerticalScrollBarEnabled(false);
  }

  public void setFactorLocked (boolean locked) {
    this.factorLocked = locked;
  }

  public void setFloatingButton (FloatingButton floatingButton) {
    this.floatingButton = floatingButton;
  }

  public void setHeaderView (ComplexHeaderView headerView) {
    this.headerView = headerView;
  }

  public float getScrollFactor () {
    return scrollFactor;
  }

  @Override
  protected void onScrollChanged (int left, int top, int oldLeft, int oldTop) {
    super.onScrollChanged(left, top, oldLeft, oldTop);
    if (headerView != null && !factorLocked) {
      float factor = 1f - (float) top / (float) Size.getMaximumHeaderSizeDifference();
      HeaderView navigationHeaderView = UI.getHeaderView();
      if (factor >= 1f) {
        scrollFactor = 1f;
        headerView.setScaleFactor(1f, 1f, 1f, true);
        if (navigationHeaderView != null) {
          navigationHeaderView.setBackgroundHeight(Size.getMaximumHeaderSizeDifference());
        }
      } else if (factor <= 0f) {
        scrollFactor = 0f;
        headerView.setScaleFactor(0f, 0f, 0f, true);
        if (navigationHeaderView != null) {
          navigationHeaderView.setBackgroundHeight(Size.getHeaderPortraitSize());
        }
      } else {
        scrollFactor = factor;
        headerView.setScaleFactor(factor, factor, factor, true);
        if (navigationHeaderView != null) {
          navigationHeaderView.setBackgroundHeight(Size.getHeaderPortraitSize() + (int) (Size.getMaximumHeaderSizeDifference() * factor));
        }
      }

      if (floatingButton != null) {
        floatingButton.setHeightFactor(scrollFactor, 0f, true);
      }
    }
  }
}
