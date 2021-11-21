package me.vkryl.android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import me.vkryl.android.animator.Animated;

/**
 * Date: 11/27/17
 * Author: default
 */

public class FrameLayoutFix extends android.widget.FrameLayout implements Animated {
  public static android.widget.FrameLayout.LayoutParams newParams (int width, int height) {
    return new android.widget.FrameLayout.LayoutParams(width, height);
  }

  public static android.widget.FrameLayout.LayoutParams newParams (int width, int height, int gravity) {
    return new android.widget.FrameLayout.LayoutParams(width, height, gravity);
  }

  public static android.widget.FrameLayout.LayoutParams newParams (int width, int height, int gravity, int left, int top, int right, int bottom) {
    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(width, height, gravity);
    params.leftMargin = left;
    params.topMargin = top;
    params.rightMargin = right;
    params.bottomMargin = bottom;
    return params;
  }

  public static android.widget.FrameLayout.LayoutParams newParams (android.view.ViewGroup.LayoutParams source) {
    if (source instanceof android.widget.FrameLayout.LayoutParams) {
      return newParams((android.widget.FrameLayout.LayoutParams) source);
    } else if (source instanceof MarginLayoutParams) {
      return newParams((MarginLayoutParams) source);
    } else {
      return newParams(source.width, source.height);
    }
  }

  public static android.widget.FrameLayout.LayoutParams newParams (android.view.ViewGroup.LayoutParams source, int newGravity) {
    android.widget.FrameLayout.LayoutParams params;
    if (source instanceof android.widget.FrameLayout.LayoutParams) {
      params = newParams((android.widget.FrameLayout.LayoutParams) source);
    } else if (source instanceof MarginLayoutParams) {
      params = newParams((MarginLayoutParams) source);
    } else {
      params = newParams(source.width, source.height);
    }
    params.gravity = newGravity;
    return params;
  }

  private static android.widget.FrameLayout.LayoutParams newParams (android.widget.FrameLayout.LayoutParams source) {
    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(source.width, source.height, source.gravity);
    params.leftMargin = source.leftMargin;
    params.topMargin = source.topMargin;
    params.rightMargin = source.rightMargin;
    params.bottomMargin = source.bottomMargin;
    return params;
  }

  private static android.widget.FrameLayout.LayoutParams newParams (MarginLayoutParams source) {
    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(source.width, source.height);
    params.leftMargin = source.leftMargin;
    params.topMargin = source.topMargin;
    params.rightMargin = source.rightMargin;
    params.bottomMargin = source.bottomMargin;
    return params;
  }

  public FrameLayoutFix (@NonNull Context context) {
    super(context);
  }

  public FrameLayoutFix (@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public FrameLayoutFix (@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  private final ArrayList<View> mMatchParentChildren = new ArrayList<>();
  private boolean mMeasureAllChildren;

  @Override
  public void setMeasureAllChildren (boolean measureAll) {
    super.setMeasureAllChildren(measureAll);
    this.mMeasureAllChildren = measureAll;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    int count = getChildCount();

    final boolean measureMatchParentChildren =
      MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY ||
        MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY;
    mMatchParentChildren.clear();

    int maxHeight = 0;
    int maxWidth = 0;
    int childState = 0;

    for (int i = 0; i < count; i++) {
      final View child = getChildAt(i);
      if (child == null) {
        continue;
      }
      if (mMeasureAllChildren || child.getVisibility() != GONE) {
        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        maxWidth = Math.max(maxWidth,
          child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
        maxHeight = Math.max(maxHeight,
          child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
        childState = combineMeasuredStates(childState, child.getMeasuredState());
        if (measureMatchParentChildren) {
          if (lp.width == LayoutParams.MATCH_PARENT ||
            lp.height == LayoutParams.MATCH_PARENT) {
            mMatchParentChildren.add(child);
          }
        }
      }
    }

    final int paddingLeft = getPaddingLeft();
    final int paddingTop = getPaddingTop();
    final int paddingRight = getPaddingRight();
    final int paddingBottom = getPaddingBottom();

    // Account for padding too
    maxWidth += paddingLeft + paddingRight;
    maxHeight += paddingTop + paddingBottom;

    // Check against our minimum height and width
    maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
    maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

    // Check against our foreground's minimum height and width
    final Drawable drawable = getForeground();
    if (drawable != null) {
      maxHeight = Math.max(maxHeight, drawable.getMinimumHeight());
      maxWidth = Math.max(maxWidth, drawable.getMinimumWidth());
    }

    setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
      resolveSizeAndState(maxHeight, heightMeasureSpec,
        childState << MEASURED_HEIGHT_STATE_SHIFT));

    count = mMatchParentChildren.size();
    if (count > 1) {
      for (int i = 0; i < count; i++) {
        final View child = mMatchParentChildren.get(i);
        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();

        final int childWidthMeasureSpec;
        if (lp.width == LayoutParams.MATCH_PARENT) {
          final int width = Math.max(0, getMeasuredWidth()
            - paddingLeft - paddingRight
            - lp.leftMargin - lp.rightMargin);
          childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
            width, MeasureSpec.EXACTLY);
        } else {
          childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
            paddingLeft + paddingRight +
              lp.leftMargin + lp.rightMargin,
            lp.width);
        }

        final int childHeightMeasureSpec;
        if (lp.height == LayoutParams.MATCH_PARENT) {
          final int height = Math.max(0, getMeasuredHeight()
            - paddingTop - paddingBottom
            - lp.topMargin - lp.bottomMargin);
          childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
            height, MeasureSpec.EXACTLY);
        } else {
          childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
            paddingTop + paddingBottom +
              lp.topMargin + lp.bottomMargin,
            lp.height);
        }

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
      }
    }
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    this.pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
    try {
      super.onLayout(changed, left, top, right, bottom);
    } catch (NullPointerException e) {
      // for some reason getChildAt(i) returned null, therefore exception occurred
      e.printStackTrace();
    }
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }
}
