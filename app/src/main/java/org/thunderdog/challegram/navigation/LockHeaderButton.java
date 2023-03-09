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
 * File created on 13/02/2016 at 12:24
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.PasscodeController;
import org.thunderdog.challegram.unsorted.Passcode;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class LockHeaderButton extends HeaderButton implements View.OnClickListener, View.OnLongClickListener, FactorAnimator.Target {
  private static final OvershootInterpolator OVERSHOOT_INTERPOLATOR = new OvershootInterpolator(3f);

  private final BoolAnimator animator = new BoolAnimator(0, this, OVERSHOOT_INTERPOLATOR, 130l);

  private Drawable lock, base;

  public LockHeaderButton (Context context) {
    super(context);

    lock = Drawables.get(getResources(), R.drawable.baseline_lock_top_24);
    base = Drawables.get(getResources(), R.drawable.baseline_lock_base_24);

    setId(R.id.menu_btn_lock);
    setButtonBackground(ThemeDeprecated.headerSelector());
    setVisibility(Passcode.instance().isEnabled() ? View.VISIBLE : View.GONE);
    setOnClickListener(this);
    setOnLongClickListener(this);
    setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(49f), ViewGroup.LayoutParams.MATCH_PARENT));
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    animator.setValue(!isVisuallyLocked(), false);
  }

  private static boolean isVisuallyLocked () {
    return Passcode.instance().isLocked() || Passcode.instance().getAutolockMode() == Passcode.AUTOLOCK_MODE_INSTANT;
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    invalidate();
  }

  @Override
  public void onClick (View v) {
    if (Passcode.instance().getAutolockMode() == Passcode.AUTOLOCK_MODE_INSTANT) {
      UI.showToast(R.string.AutoLockInstantWarn, Toast.LENGTH_SHORT);
      return;
    }
    final boolean open = !Passcode.instance().toggleLock();
    animator.setValue(open, true);
    UI.getContext(getContext()).checkPasscode(true);
  }

  @Override
  public boolean onLongClick (View v) {
    NavigationController navigation = UI.getContext(getContext()).navigation();
    ViewController<?> current = navigation != null ? navigation.getCurrentStackItem() : null;
    if (current != null) {
      PasscodeController passcode = new PasscodeController(UI.getContext(getContext()), current.tdlib());
      passcode.setPasscodeMode(PasscodeController.MODE_UNLOCK_SETUP);
      navigation.navigateTo(passcode);
      return true;
    }
    return false;
  }

  public void update () {
    final int visibility = Passcode.instance().isEnabled() ? View.VISIBLE : View.GONE;
    final boolean open = !isVisuallyLocked();
    animator.setValue(open, false);
    if (visibility != getVisibility()) {
      setVisibility(visibility);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    int cx = getMeasuredWidth() / 2;
    int cy = getMeasuredHeight() / 2;

    Paint paint = Paints.getHeaderIconPaint();
    Drawables.draw(c, lock, cx - lock.getMinimumWidth() / 2 + (int) ((float) Screen.dp(8f) * animator.getFloatValue()), cy - lock.getMinimumHeight() / 2, paint);
    Drawables.draw(c, base, cx - base.getMinimumWidth() / 2, cy - base.getMinimumHeight() / 2, paint);
  }
}
