/**
 * File created on 23/04/15 at 19:07
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;

import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class NavigationLayout extends FrameLayoutFix implements Destroyable, Screen.StatusBarHeightChangeListener {
  public NavigationLayout (Context context) {
    super(context);
    if (!Config.USE_FULLSCREEN_NAVIGATION_CONTENT) {
      setPadding(0, HeaderView.getSize(true), 0, 0);
      Screen.addStatusBarHeightListener(this);
    }
  }

  @Override
  public void onStatusBarHeightChanged (int newHeight) {
    int newPadding = HeaderView.getSize(true);
    if (getPaddingTop() != newPadding) {
      setPadding(0, newPadding, 0, 0);
    }
  }

  @Override
  public void performDestroy () {
    Screen.removeStatusBarHeightListener(this);
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
