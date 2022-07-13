/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 01/04/2017
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.palette.graphics.Palette;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.theme.ChatStyleChangeListener;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeChangeListener;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.theme.ThemeProperty;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;

public class WallpaperView extends View implements ThemeChangeListener, ChatStyleChangeListener, FactorAnimator.Target, TdlibCache.MyUserDataChangeListener, Destroyable {
  private final MessagesManager manager;
  private final Tdlib tdlib;

  private DoubleImageReceiver receiver;
  private DoubleImageReceiver preview;

  private @Nullable
  TGBackground wallpaper, previewWallpaper;

  private boolean inSetupMode, inSelfBlurMode, selfBlurValue;

  public WallpaperView (Context context, MessagesManager manager, Tdlib tdlib) {
    super(context);
    this.manager = manager;
    this.tdlib = tdlib;
    this.receiver = new DoubleImageReceiver(this, 0);
  }

  public void initWithSetupMode (boolean inSetupMode) {
    this.inSetupMode = inSetupMode;
    preview = new DoubleImageReceiver(this, 0);
    setWallpaper(tdlib.settings().getWallpaper(Theme.getWallpaperIdentifier()), false);
    ThemeManager.instance().addThemeListener(this);
    ThemeManager.instance().addChatStyleListener(this);
    tdlib.cache().addMyUserListener(this);
  }

  public void initWithCustomWallpaper (TGBackground wallpaper) {
    this.inSetupMode = true;
    preview = new DoubleImageReceiver(this, 0);
    setWallpaper(wallpaper, false);
  }

  public void setSelfBlur (boolean value) {
    inSelfBlurMode = true;
    selfBlurValue = value;
    ThemeManager.instance().addChatStyleListener(this);
  }

  @Override
  public void onMyUserUpdated (final TdApi.User myUser) {
    UI.post(() -> {
      if (myUser != null && scheduledWallpaper != null) {
        setWallpaper(scheduledWallpaper, scheduledWallpaperAnimated);
      }
    });
  }

  private TGBackground scheduledWallpaper;
  private boolean scheduledWallpaperAnimated;

  private void setWallpaper (TGBackground wallpaper, boolean animated) {
    if (wallpaper != null && wallpaper.isEmpty()) {
      wallpaper = null;
    }
    animated = animated && manager.controller().isFocused();
    if (tdlib.myUserId() == 0) {
      scheduledWallpaper = wallpaper;
      scheduledWallpaperAnimated = animated;
      return;
    }
    if (this.wallpaper != wallpaper) {
      if (animated) {
        if (TGBackground.compare(this.wallpaper, wallpaper, false)) {
          return;
        }
        this.previewWallpaper = wallpaper;
        preview.setAnimationDisabled(inSetupMode);
        requestFiles(preview, wallpaper);
        animateChange();
      } else {
        this.wallpaper = wallpaper;
        requestFiles(receiver, wallpaper);
        if (manager.useBubbles() && manager.getAdapter() != null) {
          manager.getAdapter().invalidateAllMessages();
        }
      }
    }
  }

  private void requestFiles (DoubleImageReceiver receiver, TGBackground wallpaper) {
    if (wallpaper != null) {
      wallpaper.requestFiles(receiver, !wallpaper.isPattern());
    } else {
      receiver.requestFile(null, null);
    }
  }

  @Override
  public boolean needsTempUpdates () {
    return false;
  }

  @Override
  public void onThemeChanged (ThemeDelegate oldTheme, ThemeDelegate newTheme) {
    int newUsageIdentifier = Theme.getWallpaperIdentifier(newTheme);
    if (Theme.getWallpaperIdentifier(oldTheme) != newUsageIdentifier) {
      setWallpaper(tdlib.settings().getWallpaper(newUsageIdentifier), true);
    }
  }

  @Override
  public void onThemePropertyChanged (int themeId, @ThemeProperty int propertyId, float value, boolean isDefault) {
    switch (propertyId) {
      case ThemeProperty.WALLPAPER_USAGE_ID:
        setWallpaper(tdlib.settings().getWallpaper(Theme.getWallpaperIdentifier(themeId)), true);
        break;
    }
  }

  @Override
  public void onThemeAutoNightModeChanged (int autoNightMode) { }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    requestFiles(receiver, this.wallpaper);
  }

  @Override
  public void onChatStyleChanged (Tdlib tdlib, int newChatStyle) {
    if (this.tdlib == tdlib) {
      requestFiles(receiver, this.wallpaper);
      manager.controller().updateShadowColor();
    }
  }

  @Override
  public void onChatWallpaperChanged (Tdlib tdlib, TGBackground wallpaper, int usageIdentifier) {
    if (this.tdlib == tdlib && usageIdentifier == Theme.getWallpaperIdentifier()) {
      if (inSelfBlurMode) {
        wallpaper = TGBackground.newBlurredWallpaper(tdlib, wallpaper, selfBlurValue);
      }

      setWallpaper(wallpaper, true);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    layoutReceivers();
  }

  private void layoutReceivers () {
    int width = getMeasuredWidth();
    int height = Math.max(Screen.currentActualHeight(), getMeasuredHeight());
    receiver.setBounds(0, 0, width, height);
    if (preview != null) {
      preview.setBounds(0, 0, width, height);
    }
  }

  private float factor;
  private FactorAnimator animator;

  private void animateChange () {
    if (animator == null) {
      animator = new FactorAnimator(0, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);
    } else if (animator.getFactor() == 1f) {
      animator.forceFactor(factor = 0f);
    }
    animator.animateTo(1f);
  }

  private boolean isAnimatingChanges () {
    return animator != null && animator.isAnimating();
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    if (this.factor != factor) {
      this.factor = factor;
      if (manager.controller().inWallpaperMode() && manager.useBubbles() && manager.getAdapter() != null) {
        manager.getAdapter().invalidateAllMessages();
      }
      invalidate();
    }
  }

  public float getBackgroundTransparency () {
    if (!manager.useBubbles())
      return 0f;
    if (animator != null && animator.isAnimating()) {
      if (previewWallpaper == null) {
        return factor;
      } else if (wallpaper == null) {
        return 1f - factor;
      } else {
        return 0f;
      }
    }
    return wallpaper != null ? 0f : 1f;
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) {
    if (finalFactor == 1f) {
      wallpaper = previewWallpaper;
      previewWallpaper = null;

      DoubleImageReceiver tempReceiver = receiver;
      receiver = preview;
      receiver.setAnimationDisabled(false);
      preview = tempReceiver;

      factor = 0f;

      preview.clear();

      if (manager.useBubbles() && manager.getAdapter() != null) {
        manager.getAdapter().invalidateAllMessages();
      }
      invalidate();
    }
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
    if (preview != null) {
      preview.destroy();
    }
    tdlib.cache().removeMyUserListener(this);
    ThemeManager.instance().removeThemeListener(this);
    ThemeManager.instance().removeChatStyleListener(this);
  }

  private static int getWallpaperBackground (ThemeDelegate theme) {
    return ColorUtils.compositeColor(theme.getColor(R.id.theme_color_background), theme.getColor(R.id.theme_color_bubble_chatBackground));
  }

  private final DrawAlgorithms.GradientCache gradientCache = new DrawAlgorithms.GradientCache();

  private static void drawWallpaper (TGBackground wallpaper, Canvas c, DrawAlgorithms.GradientCache gradientCache, ThemeDelegate theme, DoubleImageReceiver receiver, float alpha) {
    final int defaultColor = getWallpaperBackground(theme);
    if (wallpaper == null || wallpaper.isEmpty()) {
      c.drawColor(ColorUtils.alphaColor(alpha, defaultColor));
    } else if (wallpaper.isFillSolid()) {
      c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
    } else if (wallpaper.isFillGradient()) {
      DrawAlgorithms.drawGradient(c, gradientCache, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), wallpaper.getTopColor(), wallpaper.getBottomColor(), wallpaper.getRotationAngle(), alpha);
    } else if (wallpaper.isFillFreeformGradient()) {
      c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
      DrawAlgorithms.drawMulticolorGradient(c, gradientCache, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), wallpaper.getFreeformColors(), alpha);
    } else if (wallpaper.isPattern()) {
      if (wallpaper.isPatternBackgroundGradient()) {
        DrawAlgorithms.drawGradient(c, gradientCache, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), wallpaper.getTopColor(), wallpaper.getBottomColor(), wallpaper.getRotationAngle(), alpha);
      } else if (wallpaper.isPatternBackgroundFreeformGradient()) {
        c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
        DrawAlgorithms.drawMulticolorGradient(c, gradientCache, receiver.getLeft(), receiver.getTop(), receiver.getRight(), receiver.getBottom(), wallpaper.getFreeformColors(), alpha);
      } else {
        c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
      }
      receiver.getReceiver().setColorFilter(wallpaper.getPatternColor());
      alpha *= wallpaper.getPatternIntensity();
      if (alpha != 1f)
        receiver.setPaintAlpha(alpha);
      receiver.getReceiver().draw(c);
      if (alpha != 1f)
        receiver.restorePaintAlpha();
    } else {
      if (receiver.needPlaceholder()) {
        c.drawColor(ColorUtils.alphaColor(alpha, defaultColor));
      }
      receiver.disableColorFilter();
      if (alpha != 1f) {
        receiver.setPaintAlpha(alpha);
      }
      receiver.draw(c);
      if (alpha != 1f) {
        receiver.restorePaintAlpha();
      }
    }
  }

  public int getDefaultOverlayColor (int defaultColor, boolean text) {
    if (manager.useBubbles()) {
      if (!isAnimatingChanges() || factor == 0f) {
        return getDefaultOverlayColor(wallpaper, receiver, defaultColor, text);
      } else if (factor == 1f) {
        return getDefaultOverlayColor(previewWallpaper, preview, defaultColor, text);
      } else {
        return ColorUtils.fromToArgb(getDefaultOverlayColor(wallpaper, receiver, defaultColor, text), getDefaultOverlayColor(previewWallpaper, preview, defaultColor, text), factor);
      }
    }
    return defaultColor;
  }

  private static int getDefaultOverlayColor (TGBackground background, DoubleImageReceiver receiver, int defaultColor, boolean text) {
    if (background == null || background.isEmpty())
      return defaultColor;
    int previewColor = getColor(background, receiver.getPreview().getCurrentFile(), defaultColor, text);
    int targetColor = getColor(background, receiver.getImageReceiver().getCurrentFile(), defaultColor, text);
    return ColorUtils.fromToArgb(previewColor, targetColor, receiver.getFullLoadFactor());
  }

  private static int getColor (TGBackground background, ImageFile imageFile, int defaultColor, boolean text) {
    if (text)
      return defaultColor;
    int legacyOverlayColor = TGBackground.getLegacyOverlayColor(background.getLegacyWallpaperId(), 0);
    if (legacyOverlayColor != 0)
      return legacyOverlayColor;
    if (background.isPattern())
      return ColorUtils.color((int) ((float) OVERLAY_ALPHA * (.7f + .3f * background.getPatternIntensity())), background.getPatternColor()); // (int) (255f * (.4f + .2f * background.getPatternIntensity()))
    if (background.isFill())
      return ColorUtils.color(OVERLAY_ALPHA, background.getSolidOverlayColor());
    Palette.Swatch swatch = imageFile != null ? imageFile.getPaletteSwatch() : null;
    if (swatch == null)
      return defaultColor;
    return text ? ColorUtils.color(255, swatch.getTitleTextColor()) : ColorUtils.color(OVERLAY_ALPHA, swatch.getRgb());
  }

  public static final int OVERLAY_ALPHA = 0x70;

  @Override
  protected void onDraw (Canvas c) {
    if (manager.useBubbles()) {
      layoutReceivers();
      if (!isAnimatingChanges()) {
        drawWallpaper(wallpaper, c, gradientCache, ThemeManager.instance().currentTheme(), receiver, 1f);
      } else if (factor == 0f) {
        drawWallpaper(wallpaper, c, gradientCache, ThemeManager.instance().previousTheme(), receiver, 1f);
      } else if (factor == 1f) {
        drawWallpaper(previewWallpaper, c, gradientCache, ThemeManager.instance().appliedTheme(), preview, 1f);
      } else {
        drawWallpaper(wallpaper, c, gradientCache, ThemeManager.instance().previousTheme(), receiver, 1f);
        drawWallpaper(previewWallpaper, c, gradientCache, ThemeManager.instance().appliedTheme(), preview, factor);
      }
    } else {
      c.drawColor(Theme.getColor(R.id.theme_color_chatBackground));
    }
  }
}
