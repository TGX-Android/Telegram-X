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
 * File created on 02/02/2016 at 18:42
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Anim;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class OverlayButtonWrap extends FrameLayoutFix implements View.OnClickListener, FactorAnimator.Target, Destroyable {
  private static final float START_SCALE = .6f;

  private Callback callback;
  private CircleButton mainButton;
  // private int rowsCount;
  private final FactorAnimator animator;

  public OverlayButtonWrap (Context context) {
    super(context);

    this.animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 200l);

    ViewUtils.setBackground(this, new OverlayBackground(this));
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
  }

  public void initWithList (final @NonNull ViewController<?> parent, int overlayColorId, int overlayIconColorId, int[] ids, int[] resources, int[] backgrounds, int[] colors, int[] items, boolean reverse) {
    if (reverse) {
      for (int i = ids.length - 1, j = 0; i >= 1; i--, j++) {
        addButton(parent, j, ids[i], resources[i], backgrounds[i], colors[i], items[i - 1]);
      }
    } else {
      for (int i = 1; i < ids.length; i++) {
        addButton(parent, i - 1, ids[i], resources[i], backgrounds[i], colors[i], items[i - 1]);
      }
    }
    addMainButton(parent, ids[0], resources[0], backgrounds[0], colors[0], overlayColorId, overlayIconColorId);
  }

  public void replaceMainButton (@IdRes int id, @DrawableRes int icon) {
    mainButton.setId(id);
    mainButton.replaceIcon(icon);
  }

  // Views

  private void addMainButton (@NonNull ViewController<?> parent, int id, int resource, @ThemeColorId int circleColorId, @ThemeColorId int iconColorId, @ThemeColorId int overlayColorId, @ThemeColorId int overlayIconColorId) {
    FrameLayoutFix.LayoutParams params;

    int padding = Screen.dp(4f);
    params = FrameLayoutFix.newParams(Screen.dp(56f) + padding * 2, Screen.dp(56f) + padding * 2, Lang.rtl() ? Gravity.LEFT | Gravity.BOTTOM : Gravity.RIGHT | Gravity.BOTTOM);
    params.rightMargin = params.leftMargin = params.bottomMargin = Screen.dp(16f) - padding;

    CircleButton button;

    button = new CircleButton(getContext());
    button.init(resource, 56f, 4f, circleColorId, iconColorId);
    button.setCrossColorId(overlayColorId, overlayIconColorId);
    button.setId(id);
    button.setOnClickListener(this);
    button.setLayoutParams(params);
    parent.addThemeInvalidateListener(button);

    addView(mainButton = button);
  }

  public void updateRtl () {
    int gravity = Lang.rtl() ? Gravity.LEFT | Gravity.BOTTOM : Gravity.RIGHT | Gravity.BOTTOM;
    int padding = Screen.dp(4f);
    int leftMargin, rightMargin;

    if (Lang.rtl()) {
      leftMargin = Screen.dp(90f) - padding;
      rightMargin = Screen.dp(26f) - padding;
    } else {
      rightMargin = Screen.dp(90f) - padding;
      leftMargin = Screen.dp(26f) - padding;
    }

    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view == null)
        continue;
      FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
      boolean changed = false;
      if (params.gravity != gravity) {
        params.gravity = gravity;
        changed = true;
      }
      if (i % 2 == 1 && (params.leftMargin != leftMargin || params.rightMargin != rightMargin)) {
        params.leftMargin = leftMargin;
        params.rightMargin = rightMargin;
        changed = true;
      }
      if (changed) {
        view.setLayoutParams(params);
      }
    }
  }

  private TextView newTextView (ViewController<?> parent) {
    int padding = Screen.dp(4f);
    TextView text = new NoScrollTextView(getContext()) {
      @Override
      public boolean onTouchEvent (MotionEvent e) {
        return !(e.getAction() == MotionEvent.ACTION_DOWN && getAlpha() == 0f) && super.onTouchEvent(e);
      }
    };
    text.setTextColor(Theme.textDecentColor());
    parent.addThemeTextDecentColorListener(text);
    RippleSupport.setRectBackground(text, 3f, 4f, R.id.theme_color_filling);
    parent.addThemeInvalidateListener(text);
    text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    text.setTypeface(Fonts.getRobotoBold());
    text.setSingleLine(true);
    text.setEllipsize(TextUtils.TruncateAt.END);
    text.setPadding(Screen.dp(8f) + padding, Screen.dp(2.5f) + padding, Screen.dp(8f) + padding, padding);
    text.setOnClickListener(this);
    return text;
  }

  private void addButton (@NonNull ViewController<?> parent, int index, int id, int resource, int background, int colorId, int stringRes) {
    FrameLayoutFix.LayoutParams params;

    final int padding = Screen.dp(4f);

    params = FrameLayoutFix.newParams(Screen.dp(40f) + padding * 2, Screen.dp(40f) + padding * 2, Lang.rtl() ? Gravity.LEFT | Gravity.BOTTOM : Gravity.RIGHT | Gravity.BOTTOM);
    params.bottomMargin = Screen.dp(96f) + Screen.dp(56f) * index - padding;
    params.rightMargin = params.leftMargin = Screen.dp(24f) - padding;

    final View button;

    CircleButton circleButton;
    circleButton = new CircleButton(getContext());
    circleButton.init(resource, 40f, 4f, background, colorId);
    button = circleButton;
    /* else {
      AvatarView avatarView = new AvatarView(getContext());
      avatarView.setUser(user);
      avatarView.setPadding(padding, padding, padding, padding);
      RippleSupport.setCircleBackground(avatarView, 40f, 4f, R.id.theme_color_filling);
      button = avatarView;
    }*/

    parent.addThemeInvalidateListener(button);
    button.setId(id);
    // button.setTag(user);
    button.setOnClickListener(this);
    button.setLayoutParams(params);

    if (easeFactor == 0f) {
      button.setEnabled(false);
      button.setScaleX(START_SCALE);
      button.setScaleY(START_SCALE);
      button.setAlpha(0f);
    }

    addView(button, index * 2);

    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(26f) + padding * 2, Lang.rtl() ? Gravity.LEFT | Gravity.BOTTOM : Gravity.RIGHT | Gravity.BOTTOM);

    params.bottomMargin = Screen.dp(103) + Screen.dp(56f) * index - padding;

    if (Lang.rtl()) {
      params.leftMargin = Screen.dp(90f) - padding;
      params.rightMargin = Screen.dp(26f) - padding;
    } else {
      params.rightMargin = Screen.dp(90f) - padding;
      params.leftMargin = Screen.dp(26f) - padding;
    }

    final TextView text = newTextView(parent);
    text.setId(id);
    // text.setTag(user);
    text.setOnClickListener(this);
    text.setText(Lang.getString(parent.bindLocaleChanger(stringRes, text, false, false)));
    /*if (user == null) {
    } else {
      text.setText(TD.getUserName(user));
    }*/
    text.setLayoutParams(params);

    if (easeFactor == 0f) {
      text.setEnabled(false);
      text.setScaleX(START_SCALE);
      text.setScaleY(START_SCALE);
      text.setAlpha(0f);
    }
    // text.setTranslationY(margin * (1f - factor));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      text.setTranslationZ(Screen.dp(2f));
    }

    addView(text, index * 2 + 1);
  }

  @Override
  public boolean onInterceptTouchEvent (MotionEvent ev) {
    return !Views.isValid(this) || super.onInterceptTouchEvent(ev);
  }

  private boolean isDestroyed;

  @Override
  public void performDestroy () {
    this.isDestroyed = true;
    for (int i = 0; i < getChildCount(); i++) {
      View view = getChildAt(i);
      if (view instanceof Destroyable) {
        ((Destroyable) view).performDestroy();
      }
    }
  }

  // Openers

  private boolean isOpen;

  public void hideIfShowing () {
    if (isOpen) {
      toggle();
    }
  }

  public void toggle () {
    if (!isOpen && (hidden || forceHidden || UI.getContext(getContext()).isNavigationBusy())) {
      return;
    }
    isOpen = !isOpen;
    if (Views.HARDWARE_LAYER_ENABLED) {
      if (isOpen && animator.getFactor() == 0f) {
        setChildrenLayerType(LAYER_TYPE_HARDWARE);
      }
    }
    animator.animateTo(isOpen ? 1f : 0f);
  }

  private void setChildrenLayerType (int layerType) {
    for (int i = 0; i < getChildCount() - 1; i++) {
      Views.setLayerType(getChildAt(i), layerType);
    }
  }

  public void close () {
    if (isOpen) {
      toggle();
    }
  }

  public void show () {
    if (mainButton != null && hidden && !forceHidden) {
      hidden = false;
      animateHideFactor(0f);
    }
  }

  private boolean hidden;
  private float hideFactor;
  private FactorAnimator hideAnimator;

  private void setHideFactor (float factor) {
    factor = Anim.anticipateRange(factor);
    if (this.hideFactor != factor) {
      this.hideFactor = factor;
      if (USE_DECELERATE) {
        float scale = .6f + .4f * (1f - factor);
        mainButton.setScaleX(scale);
        mainButton.setScaleY(scale);
        mainButton.setAlpha(1f - factor);
      } else {
        mainButton.setTranslationY((float) (Screen.dp(16f) * 2 + mainButton.getMeasuredHeight()) * factor);
      }
    }
  }

  private static final boolean USE_DECELERATE = false;

  private void animateHideFactor (float toFactor) {
    if (hideAnimator == null) {
      if (USE_DECELERATE) {
        hideAnimator = new FactorAnimator(1, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l, this.hideFactor);
      } else {
        hideAnimator = new FactorAnimator(1, this, AnimatorUtils.ANTICIPATE_OVERSHOOT_INTERPOLATOR, 440l, this.hideFactor);
      }
    }
    hideAnimator.animateTo(toFactor);
  }

  private void forceHideFactor (float factor) {
    if (hideAnimator != null) {
      hideAnimator.forceFactor(factor);
    }
    setHideFactor(factor);
  }

  public void hide () {
    if (mainButton != null && !hidden) {
      hideIfShowing();
      hidden = true;
      animateHideFactor(1f);
    }
  }

  private boolean forceHidden;

  public void forceHide () {
    if (mainButton != null && !hidden) {
      hide();
      forceHidden = true;
    } else {
      forceHidden = false;
    }
  }

  public void showIfWasHidden () {
    if (forceHidden) {
      forceHidden = false;
      show();
    }
  }

  // Animation

  private float easeFactor;

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (id == 1) {
      setHideFactor(factor);
      return;
    }

    easeFactor = factor; // isOpen ? Anim.DECELERATE_INTERPOLATOR.getInterpolation(factor) : 1f - Anim.DECELERATE_INTERPOLATOR.getInterpolation(1f - factor);
    mainButton.setRotationFactor(Lang.rtl(), easeFactor);
    HeaderView headerView = UI.getHeaderView(getContext());
    if (headerView != null) {
      headerView.setOverlayColor(calculateOverlayColor());
    }

    // Buttons

    final int count = getChildCount();

    final int rowsCount = (count - 1) / 2;

    final float stepFactor = 1f / (float) rowsCount * .8f;
    float startFactor = 0f;

    for (int index = 0; index < count - 1; index++) {
      final float maxButtonFactor = 1f - startFactor;
      final float buttonFactor;
      if (factor < startFactor) {
        buttonFactor = 0f;
      } else if (factor > startFactor + maxButtonFactor) {
        buttonFactor = 1f;
      } else {
        final float rawFactor = (factor - startFactor) / maxButtonFactor;
        // buttonFactor = isOpen ? Anim.DECELERATE_INTERPOLATOR.getInterpolation(rawFactor) : 1f - Anim.DECELERATE_INTERPOLATOR.getInterpolation(1f - rawFactor);
        buttonFactor = rawFactor;
      }

      View v = getChildAt(index);
      v.setEnabled(factor == 1f);

      if (index % 2 == 1) { // text
        v.setPivotX(Lang.rtl() ? 0f : v.getMeasuredWidth());
        startFactor += stepFactor;
        // v.setPivotY(getChildAt(index - 1).getMeasuredHeight());
      } else {
      }
      v.setPivotY(v.getMeasuredHeight());

      v.setScaleX(START_SCALE + (1f - START_SCALE) * buttonFactor);
      v.setScaleY(START_SCALE + (1f - START_SCALE) * buttonFactor);
      //noinspection Range
      v.setAlpha(buttonFactor);
    }

    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (Views.HARDWARE_LAYER_ENABLED) {
      if (finalFactor == 0f) {
        setChildrenLayerType(LAYER_TYPE_NONE);
      }
    }
  }

  // Background shit

  private int calculateOverlayColor () {
    if (easeFactor != 0f) {
      int color = ColorUtils.fromToArgb(ColorUtils.color(0, Theme.overlayColor()), Theme.overlayColor(), easeFactor);
      if (Color.alpha(color) != 0) {
        return color;
      }
    }
    return 0;
  }

  private static class OverlayBackground extends Drawable {
    private final OverlayButtonWrap wrap;

    public OverlayBackground (OverlayButtonWrap wrap) {
      this.wrap = wrap;
    }

    @Override
    public void draw (@NonNull Canvas c) {
      int color = wrap.calculateOverlayColor();
      if (color != 0) {
        c.drawColor(color);
      }
    }

    // Drawable utils

    @Override
    public void setAlpha (int alpha) { }

    @Override
    public void setColorFilter (ColorFilter colorFilter) { }

    @Override
    public int getOpacity () {
      return PixelFormat.UNKNOWN;
    }
  }

  // Touch stuff

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      close();
    }
    return Views.isValid(this) && (super.onTouchEvent(e) || animator.getFactor() != 0f);
  }

  public boolean isShowing () {
    return animator.isAnimating() || animator.getFactor() != 0f;
  }

  // Callback stuff

  @Override
  public void onClick (View v) {
    if (!hidden) {
      invokeCallback(v.getId(), v);
    }
  }

  private void invokeCallback (int id, View v) {
    if (callback != null) {
      if (callback.onOverlayButtonClick(id, v)) {
        close();
      }
    }
  }

  public void setCallback (Callback callback) {
    this.callback = callback;
  }

  public interface Callback {
    boolean onOverlayButtonClick (int id, View view);
  }
}
