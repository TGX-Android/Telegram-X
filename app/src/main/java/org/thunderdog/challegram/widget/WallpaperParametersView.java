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
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ChatStyleChangeListener;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.lambda.Destroyable;

public class WallpaperParametersView extends View implements ClickHelper.Delegate, Destroyable, ChatStyleChangeListener {
  private WallpaperParametersListener listener;
  private boolean isInitialBlur;

  private final Paint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
  private final RectF blurRect = new RectF();
  private final ClickHelper helper = new ClickHelper(this);

  private final BoolAnimator isBlurEnabled = new BoolAnimator(0, (id, factor, fraction, callee) -> {
    if (listener != null) listener.onBlurValueAnimated(isInitialBlur ? 1f - factor : factor);
    invalidate();
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

  private final BoolAnimator isViewShown = new BoolAnimator(1, (id, factor, fraction, callee) -> {
    if (listener != null) listener.onParametersViewScaleChanged(factor);
  }, AnimatorUtils.DECELERATE_INTERPOLATOR, 180L);

  public WallpaperParametersView (Context context) {
    super(context);
    this.textPaint.setColor(Theme.textAccentColor());
    this.textPaint.setTypeface(Fonts.getRobotoRegular());
    this.textPaint.setTextSize(Screen.sp(14f));
    this.isViewShown.setValue(true, false);
    ThemeManager.instance().addChatStyleListener(this);
    setWillNotDraw(false);
  }

  public void initWith (TdApi.Background background, @Nullable WallpaperParametersListener listener) {
    this.isBlurEnabled.setValue(((TdApi.BackgroundTypeWallpaper) background.type).isBlurred, false);
    this.isInitialBlur = this.isBlurEnabled.getValue();
    this.listener = listener;
  }

  public void initWith (@Nullable TGBackground background, @Nullable WallpaperParametersListener listener) {
    this.isBlurEnabled.setValue(background != null && background.isBlurred(), false);
    this.isInitialBlur = this.isBlurEnabled.getValue();
    this.listener = listener;
  }

  public void updateBackground (TGBackground background) {
    this.isBlurEnabled.setValue(background.isBlurred(), true);
    this.isInitialBlur = this.isBlurEnabled.getValue();
  }

  public void setParametersAvailability (boolean value, boolean animate) {
    this.isViewShown.setValue(value, animate);
  }

  public boolean isBlurred () {
    return isBlurEnabled.getValue();
  }

  @Override
  protected void onDraw (Canvas c) {
    drawButton(c, getWidth() / 2, getHeight() / 2, blurRect, Lang.getString(R.string.ChatBackgroundBlur), isBlurEnabled);
  }

  private void drawButton (Canvas c, int centerX, int centerY, RectF buttonRect, String text, BoolAnimator selectAnimator) {
    float textWidth = this.textPaint.measureText(text);
    float checkboxScale = .75f;
    float checkboxSize = (SimplestCheckBox.size() * checkboxScale);
    float offset = (textWidth / 2) - checkboxSize;
    int checkboxX = centerX - (int) (checkboxSize) / 2 - Screen.dp(8f) + (int) (Screen.dp(2f) * checkboxScale) - (int) offset;
    int checkboxY = centerY - (int) (Screen.dp(2f) * checkboxScale);

    buttonRect.top = checkboxY - checkboxSize;
    buttonRect.bottom = checkboxY + checkboxSize;
    buttonRect.left = checkboxX - checkboxSize;
    buttonRect.right = centerX + textWidth + (int) (checkboxSize / 1.5) - offset;
    c.drawRoundRect(buttonRect, Screen.dp(16f), Screen.dp(16f), Paints.fillingPaint(Theme.getColor(R.id.theme_color_previewBackground)));

    c.drawText(text, centerX - offset, centerY + Screen.sp(4f), textPaint);

    c.save();
    c.scale(checkboxScale, checkboxScale, checkboxX, centerY);
    c.drawCircle(checkboxX, checkboxY, checkboxSize / 2, Paints.getProgressPaint(Theme.getColor(R.id.theme_color_text), Screen.dp(2f)));
    SimplestCheckBox.draw(c, checkboxX, checkboxY, selectAnimator.getFloatValue(), null);
    c.restore();
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return helper.onTouchEvent(this, event);
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return blurRect.contains(x, y);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (blurRect.contains(x, y)) {
      isBlurEnabled.toggleValue(true);
      if (listener != null) listener.onBlurValueChanged(isBlurred());
    }
  }

  @Override
  public void performDestroy () {
    ThemeManager.instance().removeChatStyleListener(this);
  }

  @Override
  public void onChatStyleChanged (Tdlib tdlib, int newChatStyle) {

  }

  @Override
  public void onChatWallpaperChanged (Tdlib tdlib, @Nullable TGBackground wallpaper, int usageIdentifier) {
    setParametersAvailability(wallpaper != null && wallpaper.isWallpaper(), true);
  }

  public interface WallpaperParametersListener {
    default void onBlurValueAnimated (float factor) {}
    default void onBlurValueChanged (boolean newValue) {}
    default void onParametersViewScaleChanged (float factor) {}
  }
}
