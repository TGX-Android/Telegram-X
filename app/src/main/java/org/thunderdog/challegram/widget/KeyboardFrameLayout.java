/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 12/06/2024
 */

package org.thunderdog.challegram.widget;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.Keyboard;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.lambda.RunnableFloat;

public class KeyboardFrameLayout extends FrameLayoutFix implements ViewTreeObserver.OnPreDrawListener, FactorAnimator.Target {

  private static final int FLAG_VISIBLE = 1;
  private static final int FLAG_HIDE_BY_DETACH_VIEW = 1 << 1;
  private int flags;

  public final @NonNull KeyboardFrameLayoutContent contentView;
  private ViewGroup parentViewForAttachedMode;
  private ViewGroup parentViewForDetachedMode;


    public KeyboardFrameLayout (@NonNull Context context) {
    super(context);

    contentView = new KeyboardFrameLayoutContent(context);
    contentView.setKeyboardView(this);
    addContentViewToSelf();
  }

  public void setParentView (ViewGroup parentViewForAttachedMode, ViewGroup parentViewForDetachedMode, ViewGroup parentViewForFreezeRender) {
    this.parentViewForAttachedMode = parentViewForAttachedMode;
    this.parentViewForDetachedMode = parentViewForDetachedMode;

    parentViewForFreezeRender.getViewTreeObserver().addOnPreDrawListener(this);
  }

  public void useHideByDetachView () {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_HIDE_BY_DETACH_VIEW, true);
  }

  public void rebuildLayout () {
    // Nothing to do?
  }

  public void setVisible (boolean visible) {
    final boolean oldVisible = BitwiseUtils.hasFlag(flags, FLAG_VISIBLE);
    if (oldVisible != visible) {
      flags = BitwiseUtils.setFlag(flags, FLAG_VISIBLE, visible);
      setVisibleImpl(visible);
      requestLayout();
    }
  }

  private void setVisibleImpl (boolean visible) {
    if (BitwiseUtils.hasFlag(flags, FLAG_HIDE_BY_DETACH_VIEW)) {
      if (visible) {
        final ViewParent parent = getParent();
        if (parent != null) {
          ((ViewGroup) parent).removeView(this);
        }
        parentViewForAttachedMode.addView(this);
      } else {
        parentViewForAttachedMode.removeView(this);
        parentViewForAttachedMode.requestLayout();
      }
    } else {
      setVisibility(visible ? VISIBLE : GONE);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getKeyboardHeight(), MeasureSpec.EXACTLY));
  }

  private static final int STATE_NONE = 0;
  private static final int STATE_AWAITING_SHOW = 1;
  private static final int STATE_AWAITING_HIDE = 2;

  private int keyboardState;

  public void showKeyboard (android.widget.EditText input) {
    keyboardState = STATE_AWAITING_SHOW;
    Keyboard.show(input);
  }

  public void hideKeyboard (android.widget.EditText input) {
    keyboardState = STATE_AWAITING_HIDE;
    Keyboard.hide(input);
  }

  public void onKeyboardStateChanged (boolean visible) {
    if (keyboardState == STATE_AWAITING_SHOW && visible) {
      framesDropped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? 45 : 55;
    } else if (keyboardState == STATE_AWAITING_HIDE && !visible) {
      keyboardState = STATE_NONE;
    }
    contentView.setKeyboardVisible(visible);
    contentView.requestLayout();
  }

  private int framesDropped;

  @Override
  public boolean onPreDraw () {
    if (keyboardState == STATE_AWAITING_SHOW || keyboardState == STATE_AWAITING_HIDE) {
      if (++framesDropped >= 60) {
        framesDropped = 0;
        keyboardState = STATE_NONE;
        return true;
      }
      return false;
    }

    return true;
  }



  /* * */

  private static final int ANIMATOR_ADDITIONAL_HEIGHT = 0;

  private final FactorAnimator offset = new FactorAnimator(ANIMATOR_ADDITIONAL_HEIGHT, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 1000L);
  private Runnable afterAnimationFinish;

  private RunnableFloat updateTranslationListener;

  public void setUpdateTranslationListener (RunnableFloat updateTranslationListener) {
    this.updateTranslationListener = updateTranslationListener;
  }

  public void requestAdditionalHeight (int height, @Nullable Runnable doAfter) {
    setContentAdditionalHeight(height, false);
    afterAnimationFinish = doAfter;
    offset.animateTo(height);
  }

  private void removeContentView () {
    final ViewParent parent = contentView.getParent();
    if (parent != null) {
      ((ViewGroup) parent).removeView(contentView);
    }
  }

  private void addContentViewToSelf () {
    removeContentView();

    contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(contentView);
  }

  private int extraBottomInset, extraBottomInsetWithoutIme;

  public void setExtraBottomInset (int extraBottomInset, int extraBottomInsetWithoutIme) {
    if (this.extraBottomInset != extraBottomInset || this.extraBottomInsetWithoutIme != extraBottomInsetWithoutIme) {
      this.extraBottomInset = extraBottomInset;
      this.extraBottomInsetWithoutIme = extraBottomInsetWithoutIme;
      ViewGroup.LayoutParams layoutParams = contentView.getLayoutParams();
      if (layoutParams.height != ViewGroup.LayoutParams.MATCH_PARENT) {
        layoutParams.height = getKeyboardHeight();
        contentView.setLayoutParams(layoutParams);
      }
      contentView.emojiLayout.setExtraBottomInset(extraBottomInset, extraBottomInsetWithoutIme);
      requestLayout();
    }
  }

  private int getKeyboardHeight () {
    return Keyboard.getSize() + extraBottomInset;
  }

  private void addContentViewToParent () {
    removeContentView();

    if (parentViewForDetachedMode instanceof RelativeLayout) {
      RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getKeyboardHeight());
      params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
      contentView.setLayoutParams(params);
    } else {
      contentView.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM));
    }

    parentViewForDetachedMode.addView(contentView);
  }

  private void setContentAdditionalHeight (int additionalHeight, boolean isFinal) {
    final int oldAdditionalHeight = contentView.getAdditionalHeight();

    if (additionalHeight != oldAdditionalHeight || isFinal) {
      if (additionalHeight > oldAdditionalHeight || isFinal) {
        contentView.setAdditionalHeight(additionalHeight);
        if (additionalHeight > 0) {
          addContentViewToParent();
          contentView.setAllowCustomHeight(true);
        } else {
          if (isFinal) {
            contentView.setAllowCustomHeight(false);
            addContentViewToSelf();
          }
        }
        contentView.requestLayout();
      }
      checkContentTranslationY();
    }
  }

  private void checkContentTranslationY () {
    final float offset = getLayoutTranslationOffset();
    contentView.setTranslationY(contentView.getAdditionalHeight() - offset);
    if (updateTranslationListener != null) {
      updateTranslationListener.runWithFloat(-offset);
    }
  }

  public float getLayoutTranslationOffset () {
    return offset.getFactor();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == ANIMATOR_ADDITIONAL_HEIGHT) {
      checkContentTranslationY();
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (id == ANIMATOR_ADDITIONAL_HEIGHT) {
      int height = (int) finalFactor;
      setContentAdditionalHeight(height, true);
      if (afterAnimationFinish != null) {
        afterAnimationFinish.run();
      }
    }
  }
}
