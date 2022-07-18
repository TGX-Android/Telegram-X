package org.thunderdog.challegram.reactions;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingParent2;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

/**
 * A {@link NestedScrollView} that implements the {@link NestedScrollingParent2} interface.
 */
public class NestedScrollView2 extends NestedScrollView implements NestedScrollingParent2 {
  private final NestedScrollingParentHelper parentHelper;

  public NestedScrollView2 (Context context) {
    super(context);
    parentHelper = new NestedScrollingParentHelper(this);
  }

  // NestedScrollingParent2 methods.

  @Override
  public boolean onStartNestedScroll (
    @NonNull View child, @NonNull View target, int axes, int type) {
    return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
  }

  @Override
  public void onNestedScrollAccepted (
    @NonNull View child, @NonNull View target, int axes, int type) {
    parentHelper.onNestedScrollAccepted(child, target, axes);
    startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, type);
  }

  @Override
  public void onNestedPreScroll (
    @NonNull View target, int dx, int dy, @Nullable int[] consumed, int type) {
    dispatchNestedPreScroll(dx, dy, consumed, null, type);
  }

  @Override
  public void onNestedScroll (
    @NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
    final int oldScrollY = getScrollY();
    scrollBy(0, dyUnconsumed);
    final int myConsumed = getScrollY() - oldScrollY;
    final int myUnconsumed = dyUnconsumed - myConsumed;
    dispatchNestedScroll(0, myConsumed, 0, myUnconsumed, null, type);
  }

  @Override
  public void onStopNestedScroll (@NonNull View target, int type) {
    parentHelper.onStopNestedScroll(target, type);
    stopNestedScroll(type);
  }

  // NestedScrollingParent methods. For the most part these methods delegate
  // to the NestedScrollingParent2 methods above, passing TYPE_TOUCH as the
  // type to maintain API compatibility.

  @Override
  public boolean onStartNestedScroll (
    @NonNull View child, @NonNull View target, int axes) {
    return onStartNestedScroll(child, target, axes, ViewCompat.TYPE_TOUCH);
  }

  @Override
  public void onNestedScrollAccepted (
    @NonNull View child, @NonNull View target, int axes) {
    onNestedScrollAccepted(child, target, axes, ViewCompat.TYPE_TOUCH);
  }

  @Override
  public void onNestedPreScroll (
    @NonNull View target, int dx, int dy, @NonNull int[] consumed) {
    onNestedPreScroll(target, dx, dy, consumed, ViewCompat.TYPE_TOUCH);
  }

  @Override
  public void onNestedScroll (
    @NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
    onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH);
  }

  @Override
  public void onStopNestedScroll (@NonNull View target) {
    onStopNestedScroll(target, ViewCompat.TYPE_TOUCH);
  }

  @Override
  public int getNestedScrollAxes () {
    return parentHelper.getNestedScrollAxes();
  }
}
