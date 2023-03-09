/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 27/12/2016
 */
package org.thunderdog.challegram.component.inline;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.InlineResult;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.util.SelectableItemDelegate;
import org.thunderdog.challegram.widget.SimplestCheckBox;
import org.thunderdog.challegram.widget.SparseDrawableView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.lambda.Destroyable;

public class CustomResultView extends SparseDrawableView implements Destroyable, SelectableItemDelegate, FactorAnimator.Target, RemoveHelper.RemoveDelegate, DrawableProvider, InvalidateContentProvider {
  private static final int FLAG_DETACHED = 1;
  private static final int FLAG_CAUGHT = 1 << 1;
  private static final int FLAG_SELECTED = 1 << 2;

  private final ComplexReceiver receiver, textMediaReceiver;

  private int flags;
  private int selectionIndex = -1;

  public CustomResultView (Context context) {
    super(context);
    this.receiver = new ComplexReceiver(this);
    this.textMediaReceiver = new ComplexReceiver(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
    Views.setClickable(this);
    RippleSupport.setTransparentSelector(this);
  }

  public ComplexReceiver getTextMediaReceiver () {
    return textMediaReceiver;
  }

  private @Nullable InlineResult<?> result;

  @Override
  public void performDestroy () {
    setInlineResult(null);
    if (checkBox != null) {
      checkBox.destroy();
      checkBox = null;
    }
  }

  public void setInlineResult (@Nullable InlineResult<?> result) {
    final boolean isAttached = (flags & FLAG_DETACHED) == 0;
    if (isAttached && this.result != null) {
      this.result.detachFromView(this);
    }
    this.result = result;
    if (this.result != null) {
      this.result.layout(getMeasuredWidth(), receiver);
      this.result.requestFiles(receiver);
      if (isAttached) {
        this.result.attachToView(this);
      }
    } else {
      this.receiver.clear();
      this.textMediaReceiver.clear();
    }
  }

  public boolean invalidateTextMedia (InlineResult<?> result) {
    if (this.result == result && result != null) {
      this.result.requestTextMedia(textMediaReceiver);
      return true;
    }
    return false;
  }

  @Override
  public boolean invalidateContent (Object cause) {
    if (this.result == cause && cause != null) {
      this.result.requestContent(receiver, true);
      this.result.requestTextMedia(textMediaReceiver);
      return true;
    }
    return false;
  }

  public void attach () {
    final boolean isAttached = (flags & FLAG_DETACHED) == 0;
    if (!isAttached) {
      flags &= ~FLAG_DETACHED;
      receiver.attach();
      textMediaReceiver.attach();
      if (result != null) {
        result.attachToView(this);
      }
    }
  }

  public void detach () {
    final boolean isAttached = (flags & FLAG_DETACHED) == 0;
    if (isAttached) {
      flags |= FLAG_DETACHED;
      receiver.detach();
      textMediaReceiver.detach();
      if (result != null) {
        result.detachFromView(this);
      }
    }
  }

  private float lastTouchX, lastTouchY;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        lastTouchX = e.getX();
        lastTouchY = e.getY();
        boolean isCaught = selectFactor == 0f && result != null && result.onTouchEvent(this, e);
        this.flags = BitwiseUtils.setFlag(flags, FLAG_CAUGHT, isCaught);
        if (isCaught) {
          return true;
        }
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        lastTouchX = e.getX();
        lastTouchY = e.getY();
        break;
      }
    }
    return (flags & FLAG_CAUGHT) != 0 && result != null ? result.onTouchEvent(this, e) : super.onTouchEvent(e);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (result == null) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    } else {
      int width = ((View) getParent()).getMeasuredWidth();
      result.layout(width, receiver);
      setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
        MeasureSpec.makeMeasureSpec(result.getHeight(), MeasureSpec.EXACTLY));
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (result != null) {
      if (helper != null) {
        helper.save(c);
      }
      final int viewWidth = getMeasuredWidth();
      final int viewHeight = getMeasuredHeight();
      result.draw(this, c, receiver, viewWidth, viewHeight, anchorX, anchorY, selectFactor, selectionIndex, checkBox);
      if (helper != null) {
        helper.restore(c);
        helper.draw(c);
      }
    }
  }

  private float selectFactor;
  private float anchorX, anchorY;
  private SimplestCheckBox checkBox;

  private boolean setSelectionIndex (int selectionIndex) {
    if (this.selectionIndex != selectionIndex) {
      this.selectionIndex = selectionIndex;
      if (selectionIndex != -1 && checkBox == null) {
        checkBox = SimplestCheckBox.newInstance(this.selectFactor, String.valueOf(selectionIndex + 1));
      }
      return true;
    }
    return false;
  }

  @Override
  public void setIsItemSelected (boolean isSelected, int selectionIndex) {
    final boolean nowSelected = (flags & FLAG_SELECTED) != 0;
    if (nowSelected != isSelected) {
      this.flags = BitwiseUtils.setFlag(flags, FLAG_SELECTED, isSelected);
      boolean indexChanged = setSelectionIndex(selectionIndex);
      this.selectionIndex = selectionIndex;
      this.anchorX = lastTouchX;
      this.anchorY = lastTouchY;
      animateSelectFactor(isSelected ? 1f : 0f);
      if (indexChanged) {
        invalidate();
      }
    } else if (isSelected && setSelectionIndex(selectionIndex)) {
      invalidate();
    }
  }

  public void forceSelected (boolean isSelected, int selectionIndex) {
    if (animator != null) {
      animator.forceFactor(isSelected ? 1f : 0f);
    }
    this.flags = BitwiseUtils.setFlag(flags, FLAG_SELECTED, isSelected);
    boolean indexChanged = setSelectionIndex(selectionIndex);
    setSelectFactor(isSelected ? 1f : 0f);
    if (indexChanged) {
      invalidate();
    }
  }

  private void setSelectFactor (float factor) {
    if (this.selectFactor != factor) {
      this.selectFactor = factor;
      invalidate();
    }
  }

  private FactorAnimator animator;

  private void animateSelectFactor (float factor) {
    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, selectFactor);
    }
    animator.animateTo(factor);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    setSelectFactor(factor);
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  // Remove delegate

  private RemoveHelper helper;

  @Override
  public void setRemoveDx (float dx) {
    if (helper == null) {
      helper = new RemoveHelper(this, R.drawable.baseline_remove_circle_24);
    }
    helper.setDx(dx);
  }

  @Override
  public void onRemoveSwipe () {
    if (helper == null) {
      helper = new RemoveHelper(this, R.drawable.baseline_remove_circle_24);
    }
    helper.onSwipe();
  }
}
