package org.thunderdog.challegram.component.reactions;

import android.view.MotionEvent;

import org.thunderdog.challegram.navigation.BackListener;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;

public abstract class BottomSheet extends FrameLayoutFix implements FactorAnimator.Target, BackListener {

    public static final int HEIGHT_ANIMATOR = 1;
    public static final int REVEAL_ANIMATOR = 2;

    public interface ScrollableDelegate {
        void dispatchScrollableTouchEvent(MotionEvent e);

        int getScrollableScrollY();

        void forceScrollToTop();

        boolean isInsideScrollable(float x, float y);
    }

    public enum State {
        EXPANDED,
        COLLAPSED,
        MOVE,
        REVEALING,
        HIDING
    }

    private final BottomSheetLayout parent;
    private State currentState;
    private float offset = 0;
    private int lastMeasuredWidth;
    private int lastMeasuredHeight;
    private ScrollableDelegate scrollableDelegate;

    protected BottomSheet(BottomSheetLayout parent) {
        super(parent.getContext());
        this.parent = parent;
        this.parent.setBottomSheet(this);
    }

    public void setScrollableDelegate(ScrollableDelegate scrollableDelegate) {
        this.scrollableDelegate = scrollableDelegate;
    }

    protected void dispatchScrollableTouchEvent(MotionEvent e) {
        if (scrollableDelegate != null) {
            scrollableDelegate.dispatchScrollableTouchEvent(e);
        }
    }

    protected int getScrollableScrollY() {
        if (scrollableDelegate != null) {
            return scrollableDelegate.getScrollableScrollY();
        } else {
            return 0;
        }
    }

    protected void forceScrollToTop() {
        if (scrollableDelegate != null) {
            scrollableDelegate.forceScrollToTop();
        }
    }

    protected boolean isInsideScrollable(float x, float y) {
        return scrollableDelegate != null && scrollableDelegate.isInsideScrollable(x, y);
    }

    protected boolean canMove() {
        return true;
    }

    protected boolean canMinimizeHeight () {
        return true;
    }

    protected void onHeightChange(int height, float factor, boolean byUser) {}

    public void setCurrentState(State currentState) {
        this.currentState = currentState;
        updateTranslationOffset();
    }

    public State getCurrentState() {
        return currentState;
    }

    @Override
    public boolean onBackPressed(boolean fromTop) {
        return false;
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {

    }

    @Override
    public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
        FactorAnimator.Target.super.onFactorChangeFinished(id, finalFactor, callee);
    }

    protected boolean isExpanded() {
        return this.parent.isExpanded();
    }

    protected void collapse() {
        this.parent.collapse();
    }

    protected void expandFully() {
        this.parent.expandFully();
    }

    protected void dismiss() {
        this.parent.dismiss();
    }

    protected void setCollapsedHeight(int h) {
        this.parent.setCollapsedHeight(h);
    }

    protected void setExpandedHeight(int h) {
        this.parent.setExpandedHeight(h);
    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        if (lastMeasuredWidth != width || lastMeasuredHeight != height) {
            lastMeasuredWidth = width;
            lastMeasuredHeight = height;
            if (this.parent.getExpandedHeight() == Screen.currentHeight()) {
                setExpandedHeight(height);
                updateTranslationOffset();
            }
        }
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(offset + translationY);
    }

    private void updateTranslationOffset() {
        if (currentState == State.REVEALING || currentState == State.HIDING) {
            offset = this.parent.getMaxHeight() - this.parent.getCollapsedHeight();
        }else {
            offset = 0;
        }
    }
}
