package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileLocal;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.TGBackground;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeDelegate;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.DrawAlgorithms;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.DrawableProvider;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.ColorUtils;
import me.vkryl.td.TdConstants;

public class WallpaperComponent extends BaseComponent implements ClickHelper.Delegate {
  private final TGMessage context;
  private final DrawAlgorithms.GradientCache gradientCache = new DrawAlgorithms.GradientCache();
  private final RectF placeholderRect = new RectF();
  private final ClickHelper clickHelper = new ClickHelper(this);
  private final String fullUrl;

  private Path placeholderPath;
  private int lastMaxWidth, lastRadius;

  private TdApi.Background background;
  private TGBackground tgBackground;

  private ImageFile imageFileMinithumbnail;
  private ImageFile imageFilePrimary;
  private ImageReceiver imageReceiver;
  private DoubleImageReceiver imageReceiverMini;

  public WallpaperComponent (@NonNull TGMessage context, @NonNull String fullUrl, @NonNull String wallpaperUrl) {
    this.context = context;
    this.fullUrl = fullUrl;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      this.placeholderPath = new Path();
    }

    context.tdlib.client().send(new TdApi.SearchBackground(wallpaperUrl), result -> {
      if (result.getConstructor() == TdApi.Background.CONSTRUCTOR) {
        background = (TdApi.Background) result;
      }

      context.tdlib.ui().post(() -> {
        if (background != null) {
          tgBackground = new TGBackground(context.tdlib, background);
        }

        updateBackground();

        if (viewProvider != null) {
          viewProvider.invalidate();
        }
      });
    });
  }

  private void updateBackground () {
    if (background.document != null) {
      if (background.document.minithumbnail != null) {
        imageFileMinithumbnail = new ImageFileLocal(background.document.minithumbnail);
        imageFileMinithumbnail.setScaleType(ImageFile.CENTER_CROP);
        imageFileMinithumbnail.setDecodeSquare(true);
        imageFileMinithumbnail.setSize(getHeight());
      } else {
        imageFileMinithumbnail = null;
      }

      if (background.document.document != null) {
        boolean isPattern = background.document.mimeType.equals(TdConstants.BACKGROUND_PATTERN_MIME_TYPE);
        imageFilePrimary = new ImageFile(context.tdlib, background.document.document);
        imageFilePrimary.setScaleType(ImageFile.CENTER_CROP);
        imageFilePrimary.setNoBlur();
        imageFilePrimary.setDecodeSquare(true);
        imageFilePrimary.setSize(isPattern ? getHeight() * 2 : getHeight());
        if (isPattern) {
          imageFilePrimary.setIsVector();
        }
      } else {
        imageFilePrimary = null;
      }

      if (imageReceiverMini != null) {
        requestPreview(imageReceiverMini);
      }

      if (imageReceiver != null) {
        requestContent(imageReceiver);
      }
    } else {
      imageFileMinithumbnail = null;
      imageFilePrimary = null;
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

    if (imageFilePrimary != null) {
      preview.setPaintAlpha(alpha + preview.getAlpha());
      receiver.setPaintAlpha(alpha + receiver.getAlpha());
      DrawAlgorithms.drawReceiver(c, preview, receiver, true, true, startX, startY, right, bottom);
      receiver.restorePaintAlpha();
      preview.restorePaintAlpha();
    }

    if (clipped) {
      ViewSupport.restoreClipPath(c, saveCount);
      TGMessage.drawCornerFixes(c, context, 1f, startX, startY, right, bottom, radius, radius, radius, radius);
    }
  }

  private int getWallpaperBackground (ThemeDelegate theme) {
    return ColorUtils.compositeColor(theme.getColor(R.id.theme_color_background), theme.getColor(R.id.theme_color_bubble_chatBackground));
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
      receiver.setColorFilter(wallpaper.getPatternColor());
      receiver.setPaintAlpha(alpha * wallpaper.getPatternIntensity());
    } else {
      receiver.disableColorFilter();
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
    this.imageReceiverMini = receiver;
    if (imageFileMinithumbnail != null) {
      receiver.requestFile(null, imageFileMinithumbnail);
    } else {
      receiver.clear();
    }
  }

  @Override
  public void requestContent (ImageReceiver receiver) {
    this.imageReceiver = receiver;
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
    context.tdlib().ui().openUrl(context.controller(), fullUrl, new TdlibUi.UrlOpenParameters().disableInstantView());
  }
}
