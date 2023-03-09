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
 * File created on 18/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.method.PasswordTransformationMethod;
import android.view.KeyEvent;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class MaterialEditText extends EmojiEditText implements FactorAnimator.Target, Destroyable {
  // private static final ColorChanger goodChanger = new ColorChanger(0, 0xff18A81F);
  // private static final ColorChanger errorChanger = new ColorChanger(0xff63BAF7, 0xffED5454);
  // private static final ColorChanger globalChanger = new ColorChanger(0xffe6e6e6, 0xff63baf7);

  private MaterialEditTextGroup parent;

  public interface EnterKeyListener {
    boolean onEnterPressed (MaterialEditText v);
  }

  private int forceColorId;

  public void setForceColorId (int colorId) {
    if (this.forceColorId != colorId) {
      this.forceColorId = colorId;
      invalidate();
    }
  }

  private EnterKeyListener enterKeyListener;

  public MaterialEditText (Context context) {
    super(context);
    setBackgroundResource(R.drawable.transparent);
    setPadding(Screen.dp(1.5f), 0, Screen.dp(1.5f), 0);
    setSingleLine(true);
    setTypeface(Fonts.getRobotoRegular());
    setHighlightColor(Theme.fillingTextSelectionColor());
  }

  public void setEnterKeyListener (EnterKeyListener listener) {
    this.enterKeyListener = listener;
  }

  public void setParent (MaterialEditTextGroup parent) {
    this.parent = parent;
  }

  private float factor;

  public void setActiveFactor (float factor) {
    if (this.factor != factor) {
      this.factor = factor;
      if (parent != null) {
        parent.onInputActiveFactorChange(factor);
      }
      invalidate();
    }
  }

  @Override
  public final boolean onKeyDown(int keyCode, KeyEvent e) {
    return (keyCode == KeyEvent.KEYCODE_ENTER && enterKeyListener != null && enterKeyListener.onEnterPressed(this)) || super.onKeyDown(keyCode, e);
  }

  private static final int ACTIVE_ANIMATOR = 0;
  private boolean isActive;

  public void setIsActive (boolean isActive, boolean animated) {
    if (this.isActive != isActive) {
      this.isActive = isActive;
      if (animated) {
        animateActiveFactor(isActive ? 1f : 0f);
      }
    }
  }

  public void applyActiveFactor (float factor) {
    if ((isActive && this.factor != 1f) || (!isActive && this.factor != 0f)) {
      setActiveFactor(factor);
    }
  }

  private FactorAnimator activeAnimator;

  private void animateActiveFactor (float toFactor) {
    if (activeAnimator == null) {
      activeAnimator = new FactorAnimator(ACTIVE_ANIMATOR, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l, factor);
    } else {
      activeAnimator.forceFactor(factor);
    }
    activeAnimator.animateTo(toFactor);
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    switch (id) {
      case ACTIVE_ANIMATOR: {
        setActiveFactor(factor);
        break;
      }
    }
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  private float errorFactor;

  public void setErrorFactor (float factor) {
    if (this.errorFactor != factor) {
      this.errorFactor = factor;
      invalidate();
    }
  }

  private float goodFactor;

  public void setGoodFactor (float factor) {
    if (this.goodFactor != factor) {
      this.goodFactor = factor;
      invalidate();
    }
  }

  private boolean isPassword;

  public void setIsPassword (boolean isPassword) {
    if (this.isPassword != isPassword) {
      this.isPassword = isPassword;
      setTransformationMethod(isPassword ? PasswordTransformationMethod.getInstance() : null);
    }
  }

  private boolean lineDisabled;

  public void setLineDisabled (boolean isDisabled) {
    if (this.lineDisabled != isDisabled) {
      this.lineDisabled = isDisabled;
      invalidate();
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (!lineDisabled) {
      final int width = getMeasuredWidth();
      final int height = getMeasuredHeight();
      final int size = Screen.dp(2f);
      float factor = forceColorId != 0 ? 1f : this.factor;
      final int scaledSize = size - (int) ((float) Screen.dp(1f) * (1f - factor));
      final int scrollLeft = getScrollX();

      RectF rectF = Paints.getRectF();
      rectF.set(scrollLeft, height - scaledSize, width + scrollLeft, height);

      int color = forceColorId != 0 ? Theme.getColor(forceColorId) : ColorUtils.fromToArgb(Theme.getColor(R.id.theme_color_inputInactive), Theme.getColor(R.id.theme_color_inputActive), factor);
      if (goodFactor != 0f)
        color = ColorUtils.fromToArgb(color, Theme.getColor(R.id.theme_color_inputPositive), goodFactor);
      if (errorFactor != 0f)
        color = ColorUtils.fromToArgb(color, Theme.getColor(R.id.theme_color_inputNegative), errorFactor);
      c.drawRoundRect(rectF, scaledSize / 2, scaledSize / 2, Paints.fillingPaint(color));
    }

    super.onDraw(c);
  }
}
