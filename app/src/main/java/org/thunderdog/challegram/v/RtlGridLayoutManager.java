package org.thunderdog.challegram.v;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.GridLayoutManager;

import org.thunderdog.challegram.core.Lang;

/**
 * Date: 10/27/18
 * Author: default
 */
public class RtlGridLayoutManager extends GridLayoutManager {
  private boolean alignOnly;

  public RtlGridLayoutManager (Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  public RtlGridLayoutManager (Context context, int spanCount) {
    super(context, spanCount);
  }

  public RtlGridLayoutManager (Context context, int spanCount, int orientation, boolean reverseLayout) {
    super(context, spanCount, orientation, reverseLayout);
  }

  public RtlGridLayoutManager setAlignOnly (boolean alignOnly) {
    this.alignOnly = alignOnly;
    return this;
  }

  @Override
  protected final boolean isLayoutRTL () {
    return !alignOnly && Lang.rtl();
  }
}
