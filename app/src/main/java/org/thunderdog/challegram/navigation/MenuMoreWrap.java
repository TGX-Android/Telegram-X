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
 * File created on 16/05/2015 at 16:02
 */
package org.thunderdog.challegram.navigation;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.HapticMenuHelper;
import org.thunderdog.challegram.widget.NoScrollTextView;
import org.thunderdog.challegram.widget.SimplestCheckBox;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.Animated;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class MenuMoreWrap extends LinearLayout implements Animated {
  public static final int ITEM_HEIGHT = 48;
  public static final int PADDING = 8;

  public static final float START_SCALE = .56f;

  public static final long REVEAL_DURATION = 258l;
  public static final Interpolator REVEAL_INTERPOLATOR = AnimatorUtils.DECELERATE_INTERPOLATOR;

  public static final int ANCHOR_MODE_RIGHT = 0;
  public static final int ANCHOR_MODE_HEADER = 1;
  public static final int ANCHOR_MODE_CENTER = 2;

  // private int currentWidth;
  private int anchorMode;

  private @Nullable ThemeListenerList themeListeners;
  private @Nullable ThemeDelegate forcedTheme;
  private final ComplexReceiver complexAvatarReceiver;

  public MenuMoreWrap (Context context) {
    super(context);
    setWillNotDraw(false);
    complexAvatarReceiver = new ComplexReceiver(this);
    factorAnimator.forceFactor(-1);
  }

  public void updateDirection () {
    if (Views.setGravity(this, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)))
      Views.updateLayoutParams(this);
  }


  public void init (@Nullable ThemeListenerList themeProvider, ThemeDelegate forcedTheme) {
    this.themeListeners = themeProvider;
    this.forcedTheme = forcedTheme;

    setMinimumWidth(Screen.dp(196f));
    Drawable drawable;
    if (forcedTheme != null) {
      drawable = ViewSupport.getDrawableFilter(getContext(), R.drawable.bg_popup_fixed, new PorterDuffColorFilter(forcedTheme.getColor(ColorId.overlayFilling), PorterDuff.Mode.MULTIPLY));
    } else {
      drawable = ViewSupport.getDrawableFilter(getContext(), R.drawable.bg_popup_fixed, new PorterDuffColorFilter(Theme.headerFloatBackgroundColor(), PorterDuff.Mode.MULTIPLY));
    }
    ViewUtils.setBackground(this, drawable);

    if (themeProvider != null && forcedTheme == null) {
      themeProvider.addThemeSpecialFilterListener(drawable, ColorId.overlayFilling);
      themeProvider.addThemeInvalidateListener(this);
    }

    setOrientation(VERTICAL);
    setLayerType(LAYER_TYPE_HARDWARE, Views.getLayerPaint());
    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT)));
  }

  public void setRightNumber (int number) {
    setTranslationX(-Screen.dp(49f) * number);
  }

  public void setAnchorMode (int anchorMode) {
    if (this.anchorMode != anchorMode) {
      this.anchorMode = anchorMode;
      FrameLayoutFix.LayoutParams params = (FrameLayoutFix.LayoutParams) getLayoutParams();
      switch (anchorMode) {
        case ANCHOR_MODE_RIGHT: {
          params.gravity = Gravity.TOP | (Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT);
          break;
        }
        case ANCHOR_MODE_CENTER: {
          params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
          break;
        }
        case ANCHOR_MODE_HEADER: {
          params.gravity = Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT);
          setTranslationX(Lang.rtl() ? -Screen.dp(46f) : Screen.dp(46f));
          break;
        }
      }
    }
  }

  public int getAnchorMode () {
    return anchorMode;
  }

  public void updateItem (int index, int id, CharSequence title, int icon, OnClickListener listener, @Nullable ThemeListenerList themeProvider) {
    TextView menuItem = (TextView) getChildAt(index);
    menuItem.setId(id);
    menuItem.setOnClickListener(listener);
    menuItem.setText(title);
    Drawable drawable = icon != 0 ? Drawables.get(getResources(), icon) : null;
    menuItem.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    menuItem.setVisibility(View.VISIBLE);
    if (drawable != null) {
      drawable.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.icon)));
      if (themeProvider != null) {
        themeProvider.addThemeFilterListener(drawable, ColorId.icon);
      }
      if (Drawables.needMirror(icon)) {
        // TODO
      }
      if (Lang.rtl()) {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
      } else {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
      }
    } else {
      menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
    }

    menuItem.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    menuItem.setTag(menuItem.getMeasuredWidth());
  }

  public View addItem (@Nullable Tdlib tdlib, HapticMenuHelper.MenuItem menuItem, OnClickListener listener) {
    if (!menuItem.isCheckbox && ((StringUtils.isEmpty(menuItem.subtitle) && menuItem.messageSenderId == null) || tdlib == null)) {
      return addItem(menuItem.id, menuItem.title, menuItem.iconResId, menuItem.icon, listener);
    }

    final int maxWidth = Screen.dp(250);
    final int textRightOffset = Screen.dp(menuItem.isLocked ? 41 : 17);
    final Drawable finalIcon = menuItem.iconResId != 0 ? Drawables.get(getResources(), menuItem.iconResId) : menuItem.icon;
    final AvatarReceiver receiver = (menuItem.messageSenderId != null && menuItem.iconResId == 0) ?
      complexAvatarReceiver.getAvatarReceiver(Td.getSenderId(menuItem.messageSenderId)) : null;
    if (receiver != null) {
      receiver.requestMessageSender(tdlib, menuItem.messageSenderId, AvatarReceiver.Options.NONE);
    }

    FrameLayout.LayoutParams lp = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ITEM_HEIGHT), Gravity.LEFT);
    FrameLayout frameLayout = new FrameLayoutFix(getContext()) {
      @Override
      protected void onDraw (Canvas canvas) {
        super.onDraw(canvas);
        if (receiver != null) {
          receiver.draw(canvas);
        }

        if (menuItem.isLocked) {
          Drawable icon = Drawables.get(getResources(), R.drawable.baseline_lock_16);
          if (icon != null) {
            float x = getMeasuredWidth() - Screen.dp(17 + 16);
            float y = (getMeasuredHeight() - icon.getMinimumHeight()) / 2f;
            Drawables.draw(canvas, icon, x, y, Paints.getPorterDuffPaint(Theme.getColor(ColorId.text)));
          }
        }

        if (menuItem.isCheckbox) {
          int fillingColor, checkColor, outlineColor;
          if (forcedTheme != null) {
            fillingColor = forcedTheme.getColor(ColorId.checkActive);
            checkColor = forcedTheme.getColor(ColorId.checkContent);
            outlineColor = forcedTheme.getColor(ColorId.icon);
          } else {
            fillingColor = Theme.checkFillingColor();
            checkColor = Theme.checkCheckColor();
            outlineColor = Theme.getColor(ColorId.icon);
          }

          // TODO move into SimplestCheckbox
          int checkBoxCenterX = Screen.dp(29f);
          int progressCy = getMeasuredHeight() / 2;
          int progressRadius = Screen.dp(9f);
          RectF rectF = Paints.getRectF();
          progressRadius -= Screen.dp(1f);
          rectF.set(checkBoxCenterX - progressRadius, progressCy - progressRadius, checkBoxCenterX + progressRadius, progressCy + progressRadius);
          int outlineRadius = Screen.dp(3f);
          canvas.drawRoundRect(rectF, outlineRadius, outlineRadius, Paints.getProgressPaint(outlineColor, Screen.dp(1f)));

          float selectionFactor = menuItem.isCheckboxSelectedAnimated != null ?
            menuItem.isCheckboxSelectedAnimated.getFloatValue() :
            menuItem.isCheckboxSelected ? 1f :
            0f;

          SimplestCheckBox.draw(
            canvas,
            Screen.dp(29f), getMeasuredHeight() / 2,
            selectionFactor, null, null,
            fillingColor, checkColor,
            false, 1f
          );
        } else if (finalIcon != null) {
          canvas.save();
          canvas.translate(Screen.dp(29) - finalIcon.getMinimumWidth() / 2f, (getMeasuredHeight() - finalIcon.getMinimumHeight()) / 2f);
          if (menuItem.iconResId == 0) {
            finalIcon.draw(canvas);
          } else {
            Drawables.draw(canvas, finalIcon, 0, 0, PorterDuffPaint.get(ColorId.icon));
          }
          canvas.restore();
        }
      }
    };

    frameLayout.setLayoutParams(lp);
    frameLayout.setId(menuItem.id);
    if (menuItem.isCheckbox) {
      menuItem.isCheckboxSelectedAnimated = new BoolAnimator(frameLayout, AnimatorUtils.DECELERATE_INTERPOLATOR, 165l, menuItem.isCheckboxSelected);
      frameLayout.setOnClickListener(v -> {
        menuItem.isCheckboxSelected = menuItem.isCheckboxSelectedAnimated.toggleValue(true);
        listener.onClick(v);
      });
    } else {
      frameLayout.setOnClickListener(listener);
    }
    frameLayout.setWillNotDraw(false);
    if (receiver != null) {
      receiver.setBounds(Screen.dp(19), Screen.dp(ITEM_HEIGHT / 2f - 10), Screen.dp(39), Screen.dp(ITEM_HEIGHT / 2f + 10));
      receiver.setUpdateListener(r -> frameLayout.invalidate());
    }

    TextView titleTextItem = new NoScrollTextView(getContext());
    titleTextItem.setTypeface(Fonts.getRobotoRegular());
    titleTextItem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    titleTextItem.setText(menuItem.title);
    titleTextItem.setSingleLine(true);
    titleTextItem.setEllipsize(TextUtils.TruncateAt.END);
    titleTextItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ITEM_HEIGHT)));
    if (!StringUtils.isEmpty(menuItem.subtitle)) {
      titleTextItem.setGravity(Gravity.TOP | Lang.gravity());
      titleTextItem.setPadding(Screen.dp(59f), Screen.dp(5f), textRightOffset, 0);
    } else {
      titleTextItem.setGravity(Gravity.CENTER_HORIZONTAL | Lang.gravity());
      titleTextItem.setPadding(Screen.dp(59f), 0, textRightOffset, 0);
      titleTextItem.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Lang.gravity()));
    }
    titleTextItem.setMaxWidth(maxWidth);

    TextView subtitleTextItem;
    if (!StringUtils.isEmpty(menuItem.subtitle)) {
      subtitleTextItem = new NoScrollTextView(getContext());
      subtitleTextItem.setTypeface(Fonts.getRobotoRegular());
      subtitleTextItem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
      subtitleTextItem.setText(menuItem.subtitle);
      subtitleTextItem.setGravity(Gravity.BOTTOM | Lang.gravity());
      subtitleTextItem.setSingleLine(true);
      subtitleTextItem.setEllipsize(TextUtils.TruncateAt.END);
      subtitleTextItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ITEM_HEIGHT)));
      subtitleTextItem.setPadding(Screen.dp(59f), 0, textRightOffset, Screen.dp(8f));
      subtitleTextItem.setMaxWidth(maxWidth);

      if (forcedTheme != null) {
        subtitleTextItem.setTextColor(forcedTheme.getColor(ColorId.textLight));
      } else {
        subtitleTextItem.setTextColor(Theme.getColor(ColorId.textLight));
        if (themeListeners != null) {
          themeListeners.addThemeTextDecentColorListener(subtitleTextItem);
        }
      }
    } else {
      subtitleTextItem = null;
    }

    if (forcedTheme != null) {
      titleTextItem.setTextColor(forcedTheme.getColor(ColorId.text));
    } else {
      titleTextItem.setTextColor(Theme.textAccentColor());
      if (themeListeners != null) {
        themeListeners.addThemeTextAccentColorListener(titleTextItem);
      }
    }

    if (themeListeners != null) {
      themeListeners.addThemeInvalidateListener(frameLayout);
    }

    frameLayout.addView(titleTextItem);
    if (subtitleTextItem != null) {
      frameLayout.addView(subtitleTextItem);
    }

    Views.setClickable(frameLayout);
    RippleSupport.setTransparentSelector(frameLayout);
    addView(frameLayout);
    frameLayout.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    frameLayout.setTag(frameLayout.getMeasuredWidth());
    return frameLayout;
  }

  public TextView addItem (int id, CharSequence title, int iconRes, Drawable icon, OnClickListener listener) {
    TextView menuItem = new NoScrollTextView(getContext());
    menuItem.setId(id);
    menuItem.setTypeface(Fonts.getRobotoRegular());
    menuItem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    if (forcedTheme != null) {
      menuItem.setTextColor(forcedTheme.getColor(ColorId.text));
    } else {
      menuItem.setTextColor(Theme.textAccentColor());
      if (themeListeners != null) {
        themeListeners.addThemeTextAccentColorListener(menuItem);
      }
    }
    menuItem.setText(title);
    menuItem.setGravity(Gravity.CENTER_VERTICAL | Lang.gravity());
    menuItem.setSingleLine(true);
    menuItem.setEllipsize(TextUtils.TruncateAt.END);
    menuItem.setOnClickListener(listener);
    menuItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(ITEM_HEIGHT)));
    menuItem.setPadding(Screen.dp(17f), 0, Screen.dp(17f), 0);
    menuItem.setCompoundDrawablePadding(Screen.dp(18f));
    icon = iconRes != 0 ? Drawables.get(getResources(), iconRes) : icon;
    if (icon != null) {
      if (forcedTheme != null) {
        icon.setColorFilter(Paints.getColorFilter(forcedTheme.getColor(ColorId.icon)));
      } else {
        icon.setColorFilter(Paints.getColorFilter(Theme.getColor(ColorId.icon)));
        if (themeListeners != null) {
          themeListeners.addThemeFilterListener(icon, ColorId.icon);
        }
      }
      if (Drawables.needMirror(iconRes)) {
        // TODO
      }
      if (Lang.rtl()) {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(null, null, icon, null);
      } else {
        menuItem.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
      }
    }
    Views.setClickable(menuItem);
    RippleSupport.setTransparentSelector(menuItem);
    addView(menuItem);
    menuItem.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    menuItem.setTag(menuItem.getMeasuredWidth());
    return menuItem;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(MeasureSpec.makeMeasureSpec(getItemsWidth(), MeasureSpec.EXACTLY), heightMeasureSpec);
  }

  public int getItemsWidth () {
    int padding = Screen.dp(PADDING);
    int childCount = getChildCount();
    int maxWidth = 0;
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE && v.getTag() instanceof Integer) {
        maxWidth = Math.max(maxWidth, (Integer) v.getTag());
      }
    }
    return Math.max(getMinimumWidth(), maxWidth + padding + padding);
  }

  private boolean shouldPivotBottom;

  public void setShouldPivotBottom (boolean shouldPivotBottom) {
    this.shouldPivotBottom = shouldPivotBottom;
  }

  public boolean shouldPivotBottom () {
    return shouldPivotBottom;
  }

  public int getItemsHeight () {
    int itemHeight = Screen.dp(ITEM_HEIGHT);
    int padding = Screen.dp(PADDING);
    int total = 0;
    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++) {
      View v = getChildAt(i);
      if (v != null && v.getVisibility() != View.GONE) {
        total += itemHeight;
      }
    }
    return total + padding + padding;
  }

  public float getRevealRadius () {
    return (float) Math.hypot(getItemsWidth(), getItemsHeight());
  }

  public void scaleIn (Animator.AnimatorListener listener) {
    Views.animate(this, 1f, 1f, 1f, 135l, 10l, AnimatorUtils.DECELERATE_INTERPOLATOR, listener);
  }

  public void scaleOut (Animator.AnimatorListener listener) {
    Views.animate(this, START_SCALE, START_SCALE, 0f, 120l, 0l, AnimatorUtils.ACCELERATE_INTERPOLATOR, listener);
  }

  private Runnable pendingAction;

  @Override
  public void runOnceViewBecomesReady (View view, Runnable action) {
    pendingAction = action;
  }

  @Override
  protected void onLayout (boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    if (pendingAction != null) {
      pendingAction.run();
      pendingAction = null;
    }
  }

  private FactorAnimator factorAnimator = new FactorAnimator(0, (a, b, c, d) -> invalidate(), AnimatorUtils.DECELERATE_INTERPOLATOR, 250L);
  private int lastSelectedIndex = -1;

  public void processMoveEvent (View v, float x, float y, float startX, float startY) {
    int[] location = Views.getLocationOnScreen(v);
    int sourceX = location[0] + (int) x;
    int sourceY = location[1] + (int) y;
    location = Views.getLocationOnScreen(this);
    int innerX = sourceX - location[0];
    int innerY = sourceY - location[1];

    int index = Math.floorDiv(innerY - Screen.dp(PADDING), Screen.dp(ITEM_HEIGHT));
    setSelectedIndex(index == MathUtils.clamp(index, 0, getChildCount() - 1) ? index : -1);

    // Log.i("HAPTIC INNER", String.format("INDEX %d", index));
  }

  public void onApply () {
    if (lastSelectedIndex != -1) {
      View v = getChildAt(lastSelectedIndex);
      if (v != null) v.callOnClick();
    }
  }

  private void setSelectedIndex (int index) {
    if (index == lastSelectedIndex) return;
    if (lastSelectedIndex == -1 || index == -1) {
      factorAnimator.forceFactor(index);
    } else {
      factorAnimator.animateTo(index);
    }
    lastSelectedIndex = index;
    invalidate();
  }

  private int bubbleTailX = -1;

  public void setBubbleTailX (int bubbleTailX) {
    this.bubbleTailX = bubbleTailX;
    invalidate();
  }

  @Override
  protected void onDraw (Canvas canvas) {
    if (bubbleTailX > 0) {
      float cx = bubbleTailX;
      float cy = getMeasuredHeight() - Screen.dp(PADDING);
      canvas.save();
      canvas.rotate(45f, cx, cy);
      canvas.drawRect(
        cx - Screen.dp(4.5f),
        cy - Screen.dp(4.5f),
        cx + Screen.dp(4.5f),
        cy + Screen.dp(4.5f),
        Paints.fillingPaint(Theme.fillingColor())
      );
      canvas.restore();
    }

    if (lastSelectedIndex != -1) {
      int childCount = getChildCount();
      for (int i = 0; i < childCount; i++) {
        final float alpha = MathUtils.clamp(1f - Math.abs(factorAnimator.getFactor() - i));
        if (alpha > 0f) {
          canvas.drawRect(Screen.dp(PADDING), Screen.dp(PADDING + ITEM_HEIGHT * i), getMeasuredWidth() - Screen.dp(PADDING), Screen.dp(PADDING + ITEM_HEIGHT * (i + 1)), Paints.fillingPaint(ColorUtils.alphaColor(alpha * 0.05f, Theme.getColor(ColorId.text))));
        }
      }
    }


    super.onDraw(canvas);
  }

  @Override
  protected void onAttachedToWindow () {
    super.onAttachedToWindow();
    complexAvatarReceiver.attach();
  }

  @Override
  protected void onDetachedFromWindow () {
    super.onDetachedFromWindow();
    complexAvatarReceiver.detach();
  }
}