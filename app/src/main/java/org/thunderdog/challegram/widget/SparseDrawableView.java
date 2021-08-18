package org.thunderdog.challegram.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import org.thunderdog.challegram.util.DrawableProvider;

/**
 * Date: 6/4/18
 * Author: default
 */
public class SparseDrawableView extends View implements DrawableProvider {
  public SparseDrawableView (Context context) {
    super(context);
  }

  public SparseDrawableView (Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public SparseDrawableView (Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  // Sparse drawable function
  private SparseArrayCompat<Drawable> sparseDrawables;
  @Override
  public final SparseArrayCompat<Drawable> getSparseDrawableHolder () { return (sparseDrawables != null ? sparseDrawables : (sparseDrawables = new SparseArrayCompat<>())); }
  @Override
  public final Resources getSparseDrawableResources () { return getResources(); }
}
