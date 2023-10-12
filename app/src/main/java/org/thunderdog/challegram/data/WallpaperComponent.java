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
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.TGMimeType;
import org.thunderdog.challegram.util.DrawableProvider;
import org.thunderdog.challegram.widget.FileProgressComponent;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.android.util.ViewProvider;
import me.vkryl.core.ColorUtils;
import me.vkryl.td.TdConstants;

public class WallpaperComponent extends BaseComponent implements ClickHelper.Delegate {
  private final TGMessage context;
  private final DrawAlgorithms.GradientCache gradientCache = new DrawAlgorithms.GradientCache();
  private final RectF placeholderRect = new RectF();
  private final Paint placeholderPaint = new Paint();
  private final ClickHelper clickHelper = new ClickHelper(this);
  private final String fullUrl;

  private Path placeholderPath;
  private int lastMaxWidth, lastRadius;

  private TdApi.Background background;
  private TGBackground tgBackground;
  private TdApi.File backgroundFile;
  private int backgroundFileState = -1;

  private ImageFile imageFileMinithumbnail;
  private ImageFile imageFilePrimary;

  private final FileProgressComponent progress;

  public WallpaperComponent (@NonNull TGMessage context, @NonNull TdApi.WebPage webPage, @NonNull String wallpaperUrl) {
    this.context = context;
    this.fullUrl = webPage.url;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      this.placeholderPath = new Path();
    }

    updateBackground(webPage.document);

    this.progress = new FileProgressComponent(context.context(), context.tdlib(), TdlibFilesManager.DOWNLOAD_FLAG_FILE, webPage.document != null && TGMimeType.isImageMimeType(webPage.document.mimeType), context.getChatId(), context.getId());
    this.progress.setBackgroundColorProvider(context);
    this.progress.setSimpleListener(new FileProgressComponent.SimpleListener() {
      @Override
      public boolean onClick (FileProgressComponent context, View view, TdApi.File file, long messageId) {
        openBackgroundPreview();
        return true;
      }

      @Override
      public void onStateChanged (TdApi.File file, int state) {
        backgroundFileState = state;
        context.postInvalidate();
      }

      @Override
      public void onProgress (TdApi.File file, float progress) {
        context.postInvalidate();
      }
    });
    this.progress.setBackgroundColor(0x44000000);
    this.progress.setFile(webPage.document != null ? webPage.document.document : null, context.getMessage());
    if (viewProvider != null) {
      this.progress.setViewProvider(viewProvider);
    }

    context.tdlib.client().send(new TdApi.SearchBackground(wallpaperUrl), result -> {
      if (result.getConstructor() == TdApi.Background.CONSTRUCTOR) {
        background = (TdApi.Background) result;
      }

      context.tdlib.ui().post(() -> {
        if (background != null) {
          tgBackground = new TGBackground(context.tdlib, background);
        }

        if (viewProvider != null) {
          viewProvider.invalidate();
        }
      });
    });
  }

  private void updateBackground (TdApi.Document document) {
    if (document != null) {
      if (document.minithumbnail != null) {
        imageFileMinithumbnail = new ImageFileLocal(document.minithumbnail);
        imageFileMinithumbnail.setScaleType(ImageFile.CENTER_CROP);
        imageFileMinithumbnail.setDecodeSquare(true);
        imageFileMinithumbnail.setSize(getHeight());
      } else {
        imageFileMinithumbnail = null;
      }

      if (document.document != null) {
        backgroundFile = document.document;
        boolean isPattern = document.mimeType.equals(TdConstants.BACKGROUND_PATTERN_MIME_TYPE);
        imageFilePrimary = new ImageFile(context.tdlib, document.document);
        imageFilePrimary.setScaleType(ImageFile.CENTER_CROP);
        imageFilePrimary.setNoBlur();
        imageFilePrimary.setDecodeSquare(true);
        imageFilePrimary.setSize(isPattern ? getHeight() * 2 : getHeight());
        if (isPattern) {
          imageFilePrimary.setIsVector();
        }
      } else {
        imageFilePrimary = null;
        backgroundFile = null;
      }
    } else {
      imageFileMinithumbnail = null;
      imageFilePrimary = null;
      backgroundFile = null;
    }
  }

  private void layoutPath (int startX, int startY, int radius) {
    lastRadius = radius;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && placeholderPath != null) {
      placeholderPath.reset();
      RectF rectF = Paints.getRectF();
      rectF.set(startX, startY, startX + getWidth(), startY + getHeight());
      DrawAlgorithms.buildPath(placeholderPath, rectF, radius, radius, radius, radius);
    }
  }

  @Override
  public <T extends View & DrawableProvider> void draw (T view, Canvas c, int startX, int startY, Receiver preview, Receiver receiver, int backgroundColor, int contentReplaceColor, float alpha, float checkFactor) {
    int radius = getRadius();
    int right = startX + getWidth();
    int bottom = startY + getHeight();

    placeholderPaint.setColor(Theme.getColor(ColorId.placeholder));
    placeholderRect.set(startX, startY, right, bottom);

    final boolean clipped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && placeholderPath != null;
    final int saveCount;
    
    if (clipped) {
      if (lastRadius != radius) {
        layoutPath(startX, startY, radius);
      }

      saveCount = ViewSupport.clipPath(c, placeholderPath);
    } else {
      saveCount = Integer.MIN_VALUE;
    }

    if (tgBackground != null) {
      drawBackground(c, tgBackground, startX, startY, right, bottom, alpha, receiver);
    }

    if (backgroundFileState != -1 && backgroundFileState != TdlibFilesManager.STATE_DOWNLOADED_OR_UPLOADED) {
      preview.setPaintAlpha(alpha + preview.getAlpha());
      preview.setBounds(startX, startY, right, bottom);
      preview.draw(c);
      preview.restorePaintAlpha();
    } else if (imageFilePrimary != null) {
      preview.setPaintAlpha(alpha + preview.getAlpha());
      receiver.setPaintAlpha(alpha + receiver.getAlpha());
      DrawAlgorithms.drawReceiver(c, preview, receiver, true, true, startX, startY, right, bottom);
      receiver.restorePaintAlpha();
      preview.restorePaintAlpha();
    } else {
      c.drawRoundRect(placeholderRect, radius, radius, placeholderPaint);
    }

    progress.setRequestedAlpha(alpha);
    progress.setBounds(startX, startY, right, bottom);
    progress.draw(view, c);

    if (clipped) {
      ViewSupport.restoreClipPath(c, saveCount);
      TGMessage.drawCornerFixes(c, context, 1f, startX, startY, right, bottom, radius, radius, radius, radius);
    }
  }

  private int getWallpaperBackground (ThemeDelegate theme) {
    return ColorUtils.compositeColor(theme.getColor(ColorId.background), theme.getColor(ColorId.bubble_chatBackground));
  }

  private void drawBackground (Canvas c, TGBackground wallpaper, int startX, int startY, int endX, int endY, float alpha, Receiver receiver) {
    final int defaultColor = getWallpaperBackground(ThemeManager.instance().currentTheme());
    if (wallpaper == null || wallpaper.isEmpty()) {
      c.drawColor(ColorUtils.alphaColor(alpha, defaultColor));
    } else if (wallpaper.isFillSolid()) {
      c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
    } else if (wallpaper.isFillGradient()) {
      DrawAlgorithms.drawGradient(c, gradientCache, startX, startY, endX, endY, wallpaper.getTopColor(), wallpaper.getBottomColor(), wallpaper.getRotationAngle(), alpha);
    } else if (wallpaper.isFillFreeformGradient()) {
      c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
      DrawAlgorithms.drawMulticolorGradient(c, gradientCache, startX, startY, endX, endY, wallpaper.getFreeformColors(), alpha);
    } else if (wallpaper.isPattern()) {
      if (wallpaper.isPatternBackgroundGradient()) {
        DrawAlgorithms.drawGradient(c, gradientCache, startX, startY, endX, endY, wallpaper.getTopColor(), wallpaper.getBottomColor(), wallpaper.getRotationAngle(), alpha);
      } else if (wallpaper.isPatternBackgroundFreeformGradient()) {
        c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
        DrawAlgorithms.drawMulticolorGradient(c, gradientCache, startX, startY, endX, endY, wallpaper.getFreeformColors(), alpha);
      } else {
        c.drawColor(ColorUtils.alphaColor(alpha, wallpaper.getBackgroundColor(defaultColor)));
      }
      receiver.setPorterDuffColorFilter(wallpaper.getPatternColor());
      receiver.setPaintAlpha(alpha * wallpaper.getPatternIntensity());
    } else {
      receiver.disablePorterDuffColorFilter();
      if (alpha != 1f) {
        receiver.setPaintAlpha(alpha);
      }
    }
  }

  @Override
  public void buildLayout (int maxWidth) {
    lastMaxWidth = maxWidth;
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    if (imageFileMinithumbnail != null) {
      receiver.requestFile(null, imageFileMinithumbnail);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestContent (ImageReceiver receiver) {
    if (imageFilePrimary != null) {
      receiver.requestFile(imageFilePrimary);
    } else {
      receiver.requestFile(null);
    }
  }

  @Override
  public int getHeight () {
    return Screen.dp(200f);
  }

  @Override
  public int getWidth () {
    return lastMaxWidth;
  }

  @Override
  public int getContentRadius (int defaultValue) {
    return defaultValue;
  }

  @Override
  public boolean onTouchEvent (View view, MotionEvent event) {
    if (progress.onTouchEvent(view, event)) {
      return true;
    }

    return clickHelper.onTouchEvent(view, event);
  }

  private int getRadius () {
    return Screen.dp(Theme.getBubbleMergeRadius());
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return placeholderRect.contains(x, y);
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    openBackgroundPreview();
  }

  private void openBackgroundPreview() {
    context.tdlib().ui().openUrl(context.controller(), fullUrl, new TdlibUi.UrlOpenParameters().disableInstantView());
  }

  @Nullable
  @Override
  public TdApi.File getFile () {
    return backgroundFile;
  }

  @Override
  public void setViewProvider (@Nullable ViewProvider provider) {
    super.setViewProvider(provider);
    progress.setViewProvider(provider);
  }

  @Nullable
  @Override
  public FileProgressComponent getFileProgress () {
    return progress;
  }

  @Override
  public void performDestroy () {
    progress.performDestroy();
  }
}
