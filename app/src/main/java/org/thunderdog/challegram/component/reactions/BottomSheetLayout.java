package org.thunderdog.challegram.component.reactions;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewGroup;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;

public class BottomSheetLayout extends PopupLayout {

  @FunctionalInterface
  public interface DismissListener {
    void onDismiss();
  }

  private final HeightAnimator heightAnimator = new HeightAnimator(this);
  private int lastMeasuredWidth;
  private int lastMeasuredHeight;
  private final GestureDetector flingDetector;
  private float touchStartY;
  private boolean shouldIntercept;
  private boolean intercepting;
  private final float touchSlop;
  private float interceptStartY;
  private int currentScrollY;
  private boolean scrolling;
  private float scrollStartY;
  private int moveStartHeight;
  private boolean isMoving;
  private int contentHeight, startHeight;
  private int collapsedHeight, expandedHeight, currentHeight;
  private int lastHeightIncreaseCheck;
  private boolean isHeightIncreasing;
  private boolean lastIsExpanded;
  private boolean bottomEverMoved;
  private boolean animatingHeight;
  private boolean isRevealingAnimation = false;
  private boolean isHidingAnimation = false;
  private DismissListener dismissListener;
  private boolean pendingDismiss = false;
  private BottomSheet bottomSheet;

  public BottomSheetLayout(Context context) {
    super(context);
    initMetrics();
    this.touchSlop = Screen.getTouchSlop();
    GestureDetector.SimpleOnGestureListener flingListener = new GestureDetector.SimpleOnGestureListener() {
      @Override
      public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return handleFling(velocityY < 0);
      }
    };
    this.flingDetector = new GestureDetector(context, flingListener);
    init(true);
    setPopupHeightProvider(new PopupHeightProvider() {
      @Override
      public int getCurrentPopupHeight() {
        return currentHeight;
      }
    });
  }

  public boolean canMoveBottomSheet() {
    return bottomSheet.canMove();
  }

  public boolean ignoreStartHeightLimits() {
    return false;
  }

  public void setDismissListener(DismissListener dismissListener) {
    this.dismissListener = dismissListener;
  }

  public void setBottomSheet(BottomSheet bottomSheet) {
    this.bottomSheet = bottomSheet;
  }

  @Override
  public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
    if (bottomSheet == null) return;
    if (callee instanceof HeightAnimator) {
      setCurrentHeight(((HeightAnimator) callee).animatedValue(factor), false);
      bottomSheet.onFactorChanged(BottomSheet.HEIGHT_ANIMATOR, factor, fraction, callee);
    } else {
      super.onFactorChanged(id, factor, fraction, callee);
      bottomSheet.onFactorChanged(BottomSheet.REVEAL_ANIMATOR, factor, fraction, callee);
    }
  }

  @Override
  public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
    if (bottomSheet == null) return;
    if (callee instanceof HeightAnimator) {
      animatingHeight = false;
      if (pendingDismiss) {
        dismissListener.onDismiss();
      }
      bottomSheet.onFactorChangeFinished(BottomSheet.HEIGHT_ANIMATOR, finalFactor, callee);
    } else {
      super.onFactorChangeFinished(id, finalFactor, callee);
      bottomSheet.onFactorChangeFinished(BottomSheet.REVEAL_ANIMATOR, finalFactor, callee);
      if (isRevealingAnimation) {
        isRevealingAnimation = false;
        bottomSheet.setCurrentState(BottomSheet.State.COLLAPSED);
      }
      if (isHidingAnimation) {
        isHidingAnimation = false;
      }
    }
  }

  @Override
  public boolean onBackPressed(boolean byHeaderBackPress) {
    if (bottomSheet != null && bottomSheet.onBackPressed(byHeaderBackPress)) {
      return true;
    }
    return super.onBackPressed(byHeaderBackPress);
  }

  private final void initMetrics () {
    contentHeight = getInitialContentHeight();
    collapsedHeight = Screen.smallestSide();
    expandedHeight = getTargetHeight();
  }

  public final int getCollapsedHeight() {
    return collapsedHeight;
  }

  public int getExpandedHeight() {
    return expandedHeight;
  }

  public final int getCurrentWidth () {
    return getWidth();
  }

  public final int getCurrentHeight () {
    return currentHeight;
  }

  public final int getContentHeight () {
    return contentHeight;
  }

  protected int getInitialContentHeight () {
    return getMaxHeight();
  }

  protected final int getMaxInitialContentHeight () {
    return Screen.smallestSide();
  }

  protected int getMaxStartHeightLimit () {
    if (ignoreStartHeightLimits()) return Integer.MAX_VALUE;
    return getMaxHeight();
  }

  protected int getRecyclerHeaderOffset () {
    return 0;
  }

  protected boolean canExpandHeight () {
    return canMoveBottomSheet();
  }

  protected boolean canMinimizeHeight () {
    return bottomSheet.canMinimizeHeight();
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    int height = getMeasuredHeight();
    if (lastMeasuredWidth != width || lastMeasuredHeight != height) {
      lastMeasuredWidth = width;
      lastMeasuredHeight = height;
      if (expandedHeight == getTargetHeight()) {
        setExpandedHeight(height);
      }
    }
  }


  public void setCollapsedHeight(int collapsedHeight) {
    if (this.collapsedHeight != collapsedHeight) {
      this.collapsedHeight = this.currentHeight = collapsedHeight;
    }
  }

  public void setExpandedHeight(int expandedHeight) {
    if (this.expandedHeight != expandedHeight) {
      this.expandedHeight = expandedHeight;
    }
  }

  protected void animateCurrentHeight (int toHeight, boolean fast) {
    if (animatingHeight) {
      animatingHeight = false;
      heightAnimator.cancel();
    }

    if (currentHeight == toHeight) {
      return;
    }
    animatingHeight = true;
    final float fromHeight = this.currentHeight;
    heightAnimator.animateHeight(this.currentHeight, toHeight);
  }

  public boolean isAnimating () {
    return animatingHeight;
  }

  public boolean isRevealingAnimation() {
    return isRevealingAnimation;
  }


  protected boolean isInsideBottomSheet(float x, float y) {
    if (bottomSheet == null) return false;
    return y >= bottomSheet.getTranslationY() && y <= bottomSheet.getTranslationY() + bottomSheet.getMeasuredHeight();
  }

  public int getMaxHeight () {
    return Math.min(expandedHeight, getTargetHeight()) ;
  }

  public void onBottomSheetMovementStarted() {
    moveStartHeight = currentHeight;
    isMoving = true;
  }

  public boolean handleFling (boolean toUp) {
    if (isMoving) {
      isMoving = false;
      float expandFactor = currentHeight < collapsedHeight ? 0f : 1f - (float) (getMaxHeight() - currentHeight) / (float) (getMaxHeight() - collapsedHeight);
      if (toUp) {
        if (canExpandHeight()) {
          animateCurrentHeight(getMaxHeight(), true);
        } else {
          animateCurrentHeight(collapsedHeight, true);
        }
      } else if (expandFactor >= .2f || !canMinimizeHeight()) {
        animateCurrentHeight(collapsedHeight, false);
      }
      return true;
    }
    return false;
  }

  public void onBottomSheetMovementFinished() {
    if (isMoving) {
      isMoving = false;
      float expandFactor = currentHeight < collapsedHeight ? 0f : 1f - (float) (getMaxHeight() - currentHeight) / (float) (getMaxHeight() - collapsedHeight);
      if (expandFactor >= .35f && isHeightIncreasing) {
        animateCurrentHeight(getMaxHeight(), true);
      } else {
        animateCurrentHeight(collapsedHeight, false);
      }
    }
  }

  public boolean moveBottomSheet(float diffY) {
    int maxHeight = canExpandHeight() ? getMaxHeight() : collapsedHeight;
    int newHeight = Math.min(maxHeight, moveStartHeight - (int) diffY);

    if (newHeight < collapsedHeight && !canMinimizeHeight()) {
      return false;
    }

    if (currentHeight == newHeight) {
      return newHeight == maxHeight;
    }

    if (!bottomEverMoved && newHeight > collapsedHeight) {
      bottomEverMoved = true;
    }

    setCurrentHeight(newHeight, true);

    return newHeight == maxHeight;
  }

  protected void onScrollableTopUpdate(float top) {
    // Use to align overlay views
  }

  public void expandFully () {
    bottomSheet.setCurrentState(BottomSheet.State.EXPANDED);
    animateCurrentHeight(getMaxHeight(), false);
  }

  public boolean isExpanded () {
    return currentHeight == getMaxHeight();
  }

  public void show() {
    if (bottomSheet != null) {
      bottomSheet.setCurrentState(BottomSheet.State.REVEALING);
      isRevealingAnimation = true;
      showSimplePopupView(bottomSheet, getCollapsedHeight());
    }
  }

  @Override
  public void hideWindow(boolean animated) {
    bottomSheet.setCurrentState(BottomSheet.State.HIDING);
    isHidingAnimation = animated;
    super.hideWindow(animated);
  }

  public void collapse() {
    animateCurrentHeight(collapsedHeight, false);
  }

  public void dismiss() {
    hideWindow(true);
  }

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (isAnimating()) {
      return true;
    }

    if (!intercepting) {
      return super.onTouchEvent(e);
    }

    flingDetector.onTouchEvent(e);

    switch (e.getAction()) {
      case MotionEvent.ACTION_MOVE: {
        if (scrolling) {
          if (e.getY() <= scrollStartY) {
            dispatchBottomSheetTouchEvent(e);
            return true;
          } else {
            forceBottomSheetScrollToTop();
            scrolling = false;
          }
        }
        if (moveBottomSheet(e.getY() - interceptStartY)) {
          if (!scrolling) {
            scrolling = true;
            scrollStartY = e.getY();
          }
        }
        break;
      }

      case MotionEvent.ACTION_UP: {
        intercepting = false;
        dispatchBottomSheetTouchEvent(e);
        onBottomSheetMovementFinished();
        ((BaseActivity) getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT, false);
        return true;
      }
    }

    if (scrolling) {
      dispatchBottomSheetTouchEvent(e);
    }

    return true;
  }

  private void forceBottomSheetScrollToTop() {
    if (bottomSheet != null) bottomSheet.forceScrollToTop();
  }

  private void dispatchBottomSheetTouchEvent(MotionEvent e) {
    if (bottomSheet != null) bottomSheet.dispatchScrollableTouchEvent(e);
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        prepareIntercept(e);
        return isAnimating() || super.onInterceptTouchEvent(e);
      }
      case MotionEvent.ACTION_MOVE: {
        if (intercepting || isAnimating()) {
          return true;
        } else if (shouldIntercept) {
          float y = e.getY();
          float yDiff = y - touchStartY;
          if (Math.abs(yDiff) >= touchSlop && currentScrollY == 0) {
            intercepting = true;
            interceptStartY = y;
            onBottomSheetMovementStarted();
            ((BaseActivity) getContext()).setOrientationLockFlagEnabled(BaseActivity.ORIENTATION_FLAG_TOUCHING_MEDIA_LAYOUT, true);
            return true;
          }
        }
        break;
      }
      default: {
        break;
      }
    }
    return super.onInterceptTouchEvent(e);
  }

  private int getBottomSheetScrollY() {
    if (bottomSheet == null) return 0;
    return bottomSheet.getScrollableScrollY();
  }

  private void prepareIntercept (MotionEvent e) {
    currentScrollY = getBottomSheetScrollY();

    scrollStartY = 0;
    scrolling = false;

    intercepting = false;
    touchStartY = e.getY();
    boolean isInsideScrollable = bottomSheet != null && bottomSheet.isInsideScrollable(e.getX(), e.getY());
    shouldIntercept = !isAnimating() && canMoveBottomSheet() && isInsideBottomSheet(e.getX(), e.getY()) && isInsideScrollable;
  }

  private int getMaxStartHeight () {
    if (ignoreStartHeightLimits()) return Integer.MAX_VALUE;
    return Math.min(getContentHeight(), getMaxStartHeightLimit());
  }

  private void updateBottomSheetTop() {
    int currentHeight = getMeasuredHeight();
    updateBottomSheetTop(currentHeight == 0 ? getMaxHeight() : Math.min(currentHeight, getMaxHeight()));
  }

  private static int getTargetHeight () {
    return Screen.currentHeight();
  }

  private void updateBottomSheetTop(int height) {
    if (bottomSheet != null) {
      float top = height - currentHeight;
      if (!isRevealingAnimation) {
        bottomSheet.setTranslationY(top);
      }
    }
  }

  private void setCurrentHeight (int height, boolean byUser) {
    if (this.currentHeight != height) {
      this.currentHeight = height;
      float factor = 1f * (this.currentHeight - this.collapsedHeight) / (getMaxHeight() - this.collapsedHeight);
      this.bottomSheet.onHeightChange(this.currentHeight, factor, byUser);

      final int maxHeight = getMaxHeight();

      lastIsExpanded = height == maxHeight;

      if (Math.abs(height - lastHeightIncreaseCheck) >= Screen.getTouchSlop()) {
        isHeightIncreasing = height > lastHeightIncreaseCheck;
        lastHeightIncreaseCheck = height;
      }

      if (currentHeight == expandedHeight) {
        bottomSheet.setCurrentState(BottomSheet.State.EXPANDED);
      } else if (currentHeight == collapsedHeight) {
        bottomSheet.setCurrentState(BottomSheet.State.COLLAPSED);
      } else {
        bottomSheet.setCurrentState(BottomSheet.State.MOVE);
      }

      updateBottomSheetTop();
    }
  }

  private static class HeightAnimator extends FactorAnimator {
    private static final int HEIGHT_ANIMATOR = 1;
    private static final long HEIGHT_ANIM_DURATION = 300;
    public static final float FACTOR_EXPAND = 1f;
    public static final float FACTOR_COLLAPSE = 0f;
    private int from, to;

    public HeightAnimator(Target target) {
      super(HEIGHT_ANIMATOR, target, AnimatorUtils.ACCELERATE_DECELERATE_INTERPOLATOR, HEIGHT_ANIM_DURATION);
    }

    public boolean isIncreasingHeight() {
      return to > from;
    }

    public void animateHeight(int from, int to) {
      this.from = from;
      this.to = to;
      final float heightDiff = to - from;
      final float toFactor = heightDiff > 0 ? FACTOR_EXPAND : FACTOR_COLLAPSE;
      forceFactor(FACTOR_EXPAND - toFactor);
      animateTo(toFactor);
    }

    public int animatedValue(float factor) {
      final float heightDiff = to - from;
      final float f = heightDiff > 0 ? factor : 1 - factor;
      return Math.round(from + (to - from) * f);
    }
  }
}