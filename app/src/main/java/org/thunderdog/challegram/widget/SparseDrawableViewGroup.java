package org.thunderdog.challegram.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewGroup;

import androidx.collection.SparseArrayCompat;

import org.thunderdog.challegram.util.DrawableProvider;

/**
 * Date: 6/4/18
 * Author: default
 */
public abstract class SparseDrawableViewGroup extends ViewGroup implements DrawableProvider {
  public SparseDrawableViewGroup (Context context) {
    super(context);
  }

  public SparseDrawableViewGroup (Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SparseDrawableViewGroup (Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public SparseArrayCompat<Drawable> getSparseDrawableHolder () {
    return null;
  }

  @Override
  public Resources getSparseDrawableResources () {
    return null;
  }
}
