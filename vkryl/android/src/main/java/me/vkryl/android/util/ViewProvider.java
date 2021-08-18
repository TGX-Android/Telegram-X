package me.vkryl.android.util;

import android.graphics.Rect;
import android.view.View;

/**
 * Date: 13/02/2017
 * Author: default
 */

public interface ViewProvider extends InvalidateDelegate, LayoutDelegate {
  boolean hasAnyTargetToInvalidate ();
  void invalidate ();
  void invalidate (int left, int top, int right, int bottom);
  void invalidate (Rect dirty);
  View findAnyTarget ();
  boolean belongsToProvider (View view);
  void postInvalidate ();
  void invalidateParent ();
  void invalidateParent (int left, int top, int right, int bottom);
  void performClickSoundFeedback ();
  void requestLayout ();
  void invalidateOutline (boolean withView);
  int getMeasuredWidth ();
  int getMeasuredHeight ();
  boolean invalidateContent ();
}
