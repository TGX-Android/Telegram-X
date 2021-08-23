/**
 * File created on 23/04/15 at 17:48
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Size;

public class NavigationGestureController implements GestureDetector.OnGestureListener {
  private GestureDetector gestureDetector;
  private NavigationController navigation;
  private @Nullable DrawerController drawer;

  private boolean isScrolling;

  private float startX, startY;
  private int lastScrollX, lastScrollY;

  private boolean listenSlidingBack, slidingVertical, slidingBack, slidingDrawer;
  private boolean /*blocked,*/ abortUp;
  //private boolean listenMenuSlide;

  private void clear (boolean releaseInput) {
    startX = 0.0f;
    startY = 0.0f;
    listenSlidingBack = false;
    slidingVertical = false;
    // blocked = false;
    slidingBack = false;
    slidingDrawer = false;
    lastScrollX = -1;
    lastScrollY = -1;

    if (releaseInput && !navigation.isAnimating()) {
      setInputBlocked(false);
    }
  }

  private boolean inputBlocked;

  private void setInputBlocked (boolean isBlocked) {
    if (this.inputBlocked != isBlocked) {
      this.inputBlocked = isBlocked;
      ((BaseActivity) navigation.getContext()).setIsKeyboardBlocked(isBlocked);
      // UI.getContext(navigation.getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_NAVIGATION, isBlocked);
    }
  }

  public NavigationGestureController (Context context, NavigationController navigation, DrawerController drawer) {
    this.gestureDetector = new GestureDetector(context, this);
    this.navigation = navigation;
    this.drawer = drawer;
    clear(false);
  }

  public void onScrollStateChanged (int state) {
    /*boolean isScrolling = state == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL;
    if (this.isScrolling != isScrolling) {
      // this.isScrolling = isScrolling;
    }*/
  }

  public boolean onTouchEvent (MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_UP: {
        gestureDetector.onTouchEvent(event);
        return onUp(event);
      }
      case MotionEvent.ACTION_MOVE: {
        gestureDetector.onTouchEvent(event);
        return onDrag(event);
      }
      case MotionEvent.ACTION_DOWN: {
        gestureDetector.onTouchEvent(event);
        return true;
      }
      default: {
        return gestureDetector.onTouchEvent(event);
      }
    }
  }

  public boolean onDown (MotionEvent event) {
    if (navigation.isDestroyed() || navigation.isAnimating())
      return false;

    startX = event.getX(0);
    startY = event.getY(0);

    if (startY <= 0f) //HeaderView.getSize()
      return false;

    ViewController<?> c = navigation.getCurrentStackItem();

    if (c == null || c.isIntercepted())
      return false;

    listenSlidingBack = ((drawer != null && drawer.isVisible()) || (!isScrolling && canSlideBack(c, startX, startY)));
    slidingVertical = listenSlidingBack && c.usePopupMode();

    return listenSlidingBack;
  }

  private boolean canSlideBack () {
    int stackSize = navigation.getStackSize();
    ViewController<?> c = navigation.getCurrentStackItem();
    return stackSize > 0 && c != null && c.swipeNavigationEnabled() && !c.forceFadeMode() && !c.inSelectMode() && !c.inCustomMode() && !(stackSize == 1 && c.inSearchMode());
  }

  private boolean canSlideBack (ViewController<?> c, float x, float y) {
    int stackSize = navigation.getStackSize();
    return stackSize > 0 && c != null && c.swipeNavigationEnabled() && !c.forceFadeMode() && !c.inSelectMode() && !c.inCustomMode() && !(stackSize == 1 && c.inSearchMode()) && c.canSlideBackFrom(navigation, x, y);
  }

  public void onCancel () {
    abortUp = false;
    if (slidingBack) {
      navigation.forceClosePreview();
      navigation.setIsAnimating(false);
    } else if (slidingDrawer && drawer != null) {
      drawer.forceDrop();
    }
    clear(true);
  }

  public boolean onUp (MotionEvent event) {
    if (abortUp) {
      clear(true);
      return abortUp = false;
    }

    if (listenSlidingBack && drawer != null && drawer.isVisible()) {
      boolean ok;
      if (Lang.rtl()) {
        ok = event.getX() < navigation.get().getMeasuredWidth() - drawer.getWidth() + drawer.getShadowWidth();
      } else {
        ok = event.getX() >= drawer.getWidth();
      }
      if (ok) {
        drawer.close(0f, null);
      }
    } else if (slidingBack) {
      boolean close;

      if (slidingVertical) {
        close = lastScrollY < (float) navigation.get().getMeasuredHeight() * Size.NAVIGATION_DROP_FACTOR;
      } else {
        close = lastScrollX < (float) navigation.get().getMeasuredWidth() * Size.NAVIGATION_DROP_FACTOR;
      }

      if (close) {
        navigation.closePreview(0f);
      } else {
        navigation.applyPreview(0f);
      }
    } else if (drawer != null && slidingDrawer) {
      drawer.drop();
    }

    clear(true);

    return false;
  }

  public boolean onFling (MotionEvent event, MotionEvent event2, float velocityX, float velocityY) {
    float velocity = slidingVertical ? velocityY : velocityX;
    float abs = Math.abs(velocity);

    if (abs > Screen.dp(250, 1f)) {
      abortUp = true;

      if (slidingBack) {
        if ((velocity < 0 && (!Lang.rtl() || slidingVertical)) || (velocity >= 0 && Lang.rtl() && !slidingVertical)) {
          navigation.closePreview(abs);
        } else {
          navigation.applyPreview(abs);
        }
      } else if (slidingDrawer && drawer != null) {
        if ((velocityX < 0 && !Lang.rtl()) || (velocityX >= 0 && Lang.rtl())) {
          drawer.close(abs, null);
        } else {
          drawer.open(abs);
        }
      }

      clear(false);
    } else {
      abortUp = false;
    }

    return abortUp;
  }

  public boolean onScroll (MotionEvent event, MotionEvent event2, float velocityX, float velocityY) {
    return slidingBack || slidingDrawer;
  }

  public boolean onDrag (MotionEvent event) {
    lastScrollX = (int) Math.floor(event.getX(0) - startX);
    lastScrollY = slidingVertical ? (int) Math.floor(Math.max(0f, event.getY(0) - startY)) : 0;

    if (slidingBack) {
      navigation.translatePreview(slidingVertical ? lastScrollY : lastScrollX);
      return slidingBack;
    } else if (slidingDrawer && drawer != null) {
      if (drawer.isVisible()) {
        lastScrollX = (int) (event.getX(0) - startX);
      }
      drawer.translate(lastScrollX);
      return false;
    }

    if (listenSlidingBack) {
      float diffX = event.getX(0) - startX;
      float diffY = event.getY(0) - startY;

      if (drawer != null && drawer.isVisible() && (Math.abs(diffX) >= Screen.getTouchSlop() /*|| Math.abs(diffY) > Size.TOUCH_SLOP_Y*/)) {
        startX += diffX;

        if (!canSlideBack()) {
          listenSlidingBack = false;
          return false;
        } listenSlidingBack = false;

        if (!drawer.prepare()) {
          listenSlidingBack = false;
          return false;
        }

        slidingDrawer = true;
      } else {
        boolean approve;

        if (slidingVertical) {
          approve = diffY >= Screen.getTouchSlopBig() && Math.abs(diffX) <= Screen.getTouchSlopY();
        } else if (Lang.rtl()) {
          approve = diffX <= -Screen.getTouchSlopBig() && Math.abs(diffY) <= Screen.getTouchSlopY();
        } else {
          approve = diffX >= Screen.getTouchSlopBig() && Math.abs(diffY) <= Screen.getTouchSlopY();
        }

        if (!approve) {
          return false;
        }

        if (!canSlideBack()) {
          listenSlidingBack = false;
          return false;
        }

        startX += diffX;
        startY += diffY;

        listenSlidingBack = false;
        slidingBack = navigation.getStackSize() > 1;

        setInputBlocked(true);

        if (slidingBack) {
          if (!navigation.openPreview(event.getY())) {
            slidingBack = false;
            listenSlidingBack = false;
            return false;
          }
        } else {
          if (drawer == null || !drawer.prepare()) {
            listenSlidingBack = false;
            return false;
          }
          slidingDrawer = true;
        }

        onDrag(event);

        return true;
      }
    }

    return false;
  }

  public boolean isDispatching () {
    return slidingBack || slidingDrawer;
  }

  public boolean onSingleTapUp (MotionEvent event) {
    return false;
  }

  public void onShowPress (MotionEvent event) { }
  public void onLongPress (MotionEvent event) { }

}
