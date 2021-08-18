package me.vkryl.android.util;

import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import me.vkryl.android.ViewUtils;

/**
 * Date: 14/02/2017
 * Author: default
 */

public class SingleViewProvider implements ViewProvider {
  private @Nullable View view;

  public SingleViewProvider (@Nullable View view) {
    this.view = view;
  }

  public void setView (@Nullable View view) {
    this.view = view;
  }

  @Override
  public @Nullable View findAnyTarget () {
    return view;
  }

  @Override
  public boolean belongsToProvider (View view) {
    return findAnyTarget() == view;
  }

  @Override
  public boolean hasAnyTargetToInvalidate () {
    return findAnyTarget() != null;
  }

  @Override
  public void invalidate () {
    View view = findAnyTarget();
    if (view != null) {
      view.invalidate();
    }
  }

  @Override
  public void invalidateParent () {
    View view = findAnyTarget();
    if (view != null) {
      ViewParent parent = view.getParent();
      if (parent != null) {
        ((View) parent).invalidate();
      }
    }
  }

  @Override
  public void invalidateParent (int left, int top, int right, int bottom) {
    View view = findAnyTarget();
    if (view != null) {
      ViewParent parent = view.getParent();
      if (parent != null) {
        ((View) parent).invalidate(left, top, right, bottom);
      }
    }
  }

  @Override
  public void invalidate (int left, int top, int right, int bottom) {
    View view = findAnyTarget();
    if (view != null) {
      view.invalidate(left, top, right, bottom);
    }
  }

  @Override
  public void invalidate (Rect dirty) {
    View view = findAnyTarget();
    if (view != null) {
      view.invalidate(dirty);
    }
  }

  @Override
  public void invalidateOutline (boolean withInvalidate) {
    View view = findAnyTarget();
    if (view != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        view.invalidateOutline();
      }
      if (withInvalidate) {
        view.invalidate();
      }
    }
  }

  @Override
  public void postInvalidate () {
    View view = findAnyTarget();
    if (view != null) {
      view.postInvalidate();
    }
  }

  @Override
  public void performClickSoundFeedback () {
    ViewUtils.onClick(findAnyTarget());
  }

  @Override
  public void requestLayout () {
    View view = findAnyTarget();
    if (view != null) {
      view.requestLayout();
    }
  }

  @Override
  public int getMeasuredWidth () {
    View view = findAnyTarget();
    return view != null ? view.getMeasuredWidth() : 0;
  }

  @Override
  public int getMeasuredHeight () {
    View view = findAnyTarget();
    return view != null ? view.getMeasuredHeight() : 0;
  }

  @Override
  public boolean invalidateContent () {
    return false;
  }
}
