package org.thunderdog.challegram.v;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.thunderdog.challegram.component.chat.MessageResize;
import org.thunderdog.challegram.component.chat.MessageResizeRules;
import org.thunderdog.challegram.component.chat.StickyElements;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.core.MathUtils;

public class MaxHeightScrollView extends ScrollView implements MessageResize.Messages<View> {
  public MaxHeightScrollView (Context context) {
    super(context);
  }

  private int maxHeight;
  private @Nullable MessageResize<View> resize;
  private @Nullable View placedView;
  private int placedTop, reportedScrollY;
  private boolean isPlacing, isInLayout, isTouching, hasDragged;
  private @Nullable View movedView;
  private int movedTop, measuredMovedHeight = -1;

  public void setMaxHeight (int maxHeight) {
    if (this.maxHeight != maxHeight) {
      this.maxHeight = maxHeight;
      requestLayout();
    }
  }

  private int heightLimit = Integer.MAX_VALUE;

  public int getListHeight () {
    return Math.min(maxHeight, heightLimit);
  }

  public MessageResize<View> getResize () {
    if (resize == null) {
      resize = new MessageResize<>(this);
      resize.setList(this);
    }
    return resize;
  }

  private int getVisibleHeight () {
    return maxHeight != 0 ? getListHeight() : getHeight();
  }

  private int findMovedHeight () {
    final View view = movedView;
    if (view == null || maxHeight == 0 || resize == null || !resize.isMoving(view)) {
      return -1;
    }
    final int contentTop = findContentTop(view);
    if (contentTop == Integer.MIN_VALUE || movedTop <= contentTop) {
      return -1;
    }
    return Math.max(0, getListHeight() - movedTop + contentTop);
  }

  private void requestMovedLayout () {
    // Also from a draw: a drag or a stop ends a move without a frame of it
    if (findMovedHeight() != measuredMovedHeight) {
      requestLayout();
    }
  }

  private int getContentHeight () {
    return getChildCount() > 0 ? getChildAt(0).getHeight() : 0;
  }

  private int findContentTop (View view) {
    int top = 0;
    View child = view;
    while (child != this) {
      top += child.getTop();
      final ViewParent parent = child.getParent();
      if (!(parent instanceof View)) {
        return Integer.MIN_VALUE;
      }
      child = (View) parent;
    }
    return top;
  }

  private int findContentLeft (View view) {
    int left = 0;
    for (View child = view; child != this; child = (View) child.getParent()) {
      left += child.getLeft();
    }
    return left;
  }

  private final StickyElements stickyElements = new StickyElements();
  private final int[] stickyPart = new int[2];
  private @Nullable View stickyView;

  public void clearStickyElements (View view) {
    stickyView = view;
    stickyElements.clear();
  }

  public boolean hasStuckStickyElements () {
    return stickyElements.hasStuck();
  }

  public float placeStickyElement (
    View view,
    StickyElements.Element element,
    float x,
    float minY,
    float maxY,
    float padding,
    boolean atTop
  ) {
    if (view != stickyView || !findShownPart(view, stickyPart)) {
      return atTop ? minY : maxY;
    }
    return stickyElements.stick(
      element,
      x,
      minY,
      maxY,
      padding,
      atTop,
      stickyPart[0],
      stickyPart[1]
    );
  }

  private boolean findShownPart (View view, int[] range) {
    final int contentTop = findContentTop(view);
    if (contentTop == Integer.MIN_VALUE) {
      return false;
    }
    range[0] = getScrollY() - contentTop;
    range[1] = range[0] + getHeight();
    return true;
  }

  @Override
  protected void dispatchDraw (Canvas c) {
    requestMovedLayout();
    final View view = stickyView;
    if (view == null || !findShownPart(view, stickyPart)) {
      super.dispatchDraw(c);
      return;
    }
    // Before the view draws, so that its elements change their state on this frame
    if (stickyElements.needsDrawAgain(stickyPart[0], stickyPart[1])) {
      view.invalidate();
    }
    super.dispatchDraw(c);
    stickyElements.draw(
      c,
      findContentLeft(view),
      findContentTop(view),
      stickyPart[0],
      stickyPart[1],
      view.getAlpha()
    );
  }

  @Override
  public int getMessageTop (View view) {
    final int contentTop = findContentTop(view);
    if (contentTop == Integer.MIN_VALUE) {
      return Integer.MIN_VALUE;
    }
    return contentTop - getScrollY() + getVisibleHeight() - getHeight();
  }

  @Override
  public boolean setMessageTop (View view, int top) {
    movedView = view;
    movedTop = top;
    requestMovedLayout();
    placedView = view;
    placedTop = top;
    if (!isLayoutRequested()) {
      applyPlacedTop();
    }
    return true;
  }

  private void applyPlacedTop () {
    final View view = placedView;
    placedView = null;
    final int contentTop = view != null ? findContentTop(view) : Integer.MIN_VALUE;
    if (contentTop == Integer.MIN_VALUE) {
      return;
    }
    final int scrollY = contentTop - placedTop + getVisibleHeight() - getHeight();
    final int maxScrollY = Math.max(0, getContentHeight() - getHeight());
    isPlacing = true;
    scrollTo(getScrollX(), MathUtils.clamp(scrollY, 0, maxScrollY));
    isPlacing = false;
  }

  @Override
  public boolean measureResize (View view, int finalHeight, MessageResizeRules rules) {
    final int contentTop = findContentTop(view);
    if (contentTop == Integer.MIN_VALUE || !ViewCompat.isLaidOut(this)) {
      return false;
    }
    rules.listTop = 0;
    rules.visibleBottom = rules.endBottom = getVisibleHeight();
    rules.top = getMessageTop(view);
    rules.height = view.getHeight();
    rules.finalHeight = finalHeight;
    rules.aboveHeight = contentTop;
    rules.belowHeight = getContentHeight() - contentTop - view.getHeight();
    rules.spaceAbove = view.getPaddingTop();
    rules.spaceBelow = view.getPaddingBottom();
    return true;
  }

  @Override
  public boolean findVisiblePart (View view, int[] range) {
    final int top = getMessageTop(view);
    if (top == Integer.MIN_VALUE) {
      return false;
    }
    range[0] = -top;
    range[1] = getVisibleHeight() - top;
    return true;
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    // ScrollView scrolls here to fit the new height
    isInLayout = true;
    super.onLayout(changed, l, t, r, b);
    isInLayout = false;
    applyPlacedTop();
  }

  @Override
  protected void onScrollChanged (int l, int t, int oldl, int oldt) {
    super.onScrollChanged(l, t, oldl, oldt);
    // An animated scroll reports each step twice
    final int dy = t - reportedScrollY;
    reportedScrollY = t;
    if (resize == null || isPlacing || isInLayout || dy == 0) {
      return;
    }
    if (isTouching) {
      hasDragged = true;
      resize.setListDragged(true);
    } else {
      resize.onListScrolled(dy);
    }
  }

  @Override
  public boolean dispatchTouchEvent (MotionEvent ev) {
    // Before the child, whose tap may switch a block
    switch (ev.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        isTouching = true;
        hasDragged = false;
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        isTouching = false;
        if (resize != null && !hasDragged) {
          resize.setListDragged(false);
        }
        break;
    }
    return super.dispatchTouchEvent(ev);
  }

  private boolean isScrollable;

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    heightLimit = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED ?
      Integer.MAX_VALUE : MeasureSpec.getSize(heightMeasureSpec);
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    isScrollable = maxHeight != 0 && getMeasuredHeight() > maxHeight;
    measuredMovedHeight = findMovedHeight();
    int height = isScrollable ? maxHeight : getMeasuredHeight();
    if (measuredMovedHeight != -1) {
      height = Math.min(height, measuredMovedHeight);
    }
    if (height != getMeasuredHeight()) {
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
  }

  @Override
  public boolean onTouchEvent (MotionEvent ev) {
    boolean res = super.onTouchEvent(ev) && (ev.getAction() != MotionEvent.ACTION_DOWN || (isScrollable && Views.isValid(this)));
    switch (ev.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        if (isScrollable && Views.isValid(this)) {
          ViewParent parent = getParent();
          if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
          }
        }
        break;
      }
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        ViewParent parent = getParent();
        if (parent != null) {
          parent.requestDisallowInterceptTouchEvent(false);
        }
        break;
    }
    return res;
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return (super.onInterceptTouchEvent(ev) && (ev.getAction() != MotionEvent.ACTION_DOWN || Views.isValid(this))) || !Views.isValid(this);
  }
}
