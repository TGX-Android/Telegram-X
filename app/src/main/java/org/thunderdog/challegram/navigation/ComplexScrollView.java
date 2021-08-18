/**
 * File created on 10/08/15 at 11:14
 * Copyright Vyacheslav Krylov, 2014
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
