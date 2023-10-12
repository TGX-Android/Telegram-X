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
 * File created on 18/04/2016 at 23:18
 */
package org.thunderdog.challegram.tool;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Looper;
import android.text.TextPaint;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.collection.SparseArrayCompat;

import org.thunderdog.challegram.U;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.util.text.Letters;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.widget.ProgressComponent;

import java.lang.ref.SoftReference;

import me.vkryl.core.MathUtils;
import me.vkryl.core.util.LocalVar;

public class Paints {
  public static void reset () {
    // This should actually be inside the activity context to avoid issues like this, but for legacy reasons it's easier to simply do this for now, instead of having to refactor 1k+ references without breaking anything
    if (videoStrokePaint != null)
      videoStrokePaint.setStrokeWidth(Screen.dp(3f));
    if (smallTitlePaint != null)
      smallTitlePaint.setTextSize(Screen.dp(13f));
    if (commandPaint != null)
      commandPaint.setTextSize(Screen.dp(14f));
    if (strokeSeparatorPaint != null)
      strokeSeparatorPaint.setStrokeWidth(Screen.separatorSize());
    if (strokeBigPaint != null)
      strokeBigPaint.setStrokeWidth(Screen.dp(2f));
    if (strokeSmallPaint != null)
      strokeSmallPaint.setStrokeWidth(Screen.dp(1f));
    if (shadowFillingPaint != null) {
      float radius = Math.max(1, Screen.dpf(.5f));
      shadowFillingPaint.setShadowLayer(radius, 0f, radius, 0x5a000000);
    }
    if (boldPaint15Fake != null)
      boldPaint15Fake.setTextSize(Screen.dp(15f));
    if (boldPaint15 != null)
      boldPaint15.setTextSize(Screen.dp(15f));
    if (boldPaint14 != null)
      boldPaint14.setTextSize(Screen.dp(15f));
    if (boldPaint14Fake != null)
      boldPaint14Fake.setTextSize(Screen.dp(15f));
    if (boldPaint13Fake != null)
      boldPaint13Fake.setTextSize(Screen.dp(13f));
    if (boldPaint13 != null)
      boldPaint13.setTextSize(Screen.dp(13f));
    if (titlePaintFake != null)
      titlePaintFake.setTextSize(Screen.dp(15f));
    if (titlePaint != null)
      titlePaint.setTextSize(Screen.dp(15f));
    if (titleBigPaintFake != null)
      titleBigPaintFake.setTextSize(Screen.dp(16f));
    if (titleBigPaint != null)
      titleBigPaint.setTextSize(Screen.dp(16f));
    if (subtitlePaint != null)
      subtitlePaint.setTextSize(Screen.dp(13f));
    if (buttonOuterPaint != null)
      buttonOuterPaint.setStrokeWidth(Screen.dp(1.5f));
    if (progressOuterPaint != null)
      progressOuterPaint.setStrokeWidth(Screen.dp(1.5f));
    if (playPausePaint != null)
      playPausePaint.setPathEffect(new CornerPathEffect(Screen.dp(1.5f)));
    if (pagerTextPaintFakeBold != null)
      pagerTextPaintFakeBold.setTextSize(Screen.dp(14f));
    if (pagerTextPaint != null)
      pagerTextPaint.setTextSize(Screen.dp(14f));
    if (outerCheckPaint != null)
      outerCheckPaint.setStrokeWidth(Screen.dp(2f));
    if (emojiPaint != null)
      emojiPaint.setTextSize(Screen.dp(17f));
  }

  private static Paint filling;
  private static Paint placeholder;
  private static TextPaint letters, lettersFake;
  private static TextPaint commandPaint, smallTitlePaint;

  private static Paint videoStrokePaint;

  public static Paint videoStrokePaint () {
    if (videoStrokePaint == null) {
      videoStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      videoStrokePaint.setStrokeCap(Paint.Cap.ROUND);
      videoStrokePaint.setStyle(Paint.Style.STROKE);
      videoStrokePaint.setColor(0x9fffffff);
      videoStrokePaint.setStrokeWidth(Screen.dp(3f));
    }
    return videoStrokePaint;
  }

  public static Paint getPlaceholderPaint () {
    if (placeholder == null) {
      placeholder = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      placeholder.setStyle(Paint.Style.FILL);
      placeholder.setColor(Theme.placeholderColor());
      ThemeManager.addThemeListener(placeholder, ColorId.placeholder);
    }
    return placeholder;
  }

  public static TextPaint getSmallTitlePaint () {
    if (smallTitlePaint == null) {
      smallTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      smallTitlePaint.setColor(Theme.textAccentColor());
      smallTitlePaint.setTextSize(Screen.dp(13f));
      smallTitlePaint.setTypeface(Fonts.getRobotoRegular());
      ThemeManager.addThemeListener(smallTitlePaint, ColorId.text);
    }
    return smallTitlePaint;
  }

  private static int lastCommandColor;

  public static TextPaint getCommandPaint () {
    if (commandPaint == null) {
      commandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      commandPaint.setColor(lastCommandColor = Theme.textAccentColor());
      commandPaint.setTypeface(Fonts.getRobotoRegular());
      commandPaint.setTextSize(Screen.dp(14f));
    }
    return commandPaint;
  }

  public static TextPaint getCommandPaint (int color) {
    TextPaint paint = getCommandPaint();
    if (lastCommandColor != color) {
      paint.setColor(lastCommandColor = color);
    }
    return commandPaint;
  }

  private static int lastFillingColor;

  public static Paint fillingPaint (int color) {
    if (filling == null) {
      filling = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      filling.setStyle(Paint.Style.FILL);
      filling.setColor(lastFillingColor = color);
    } else if (lastFillingColor != color) {
      filling.setColor(lastFillingColor = color);
    }
    return filling;
  }

  private static Paint strokeSeparatorPaint, strokeBigPaint, strokeSmallPaint;
  private static int lastStrokeSeparatorColor;

  public static Paint strokeSeparatorPaint (int color) {
    if (strokeSeparatorPaint == null) {
      strokeSeparatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      strokeSeparatorPaint.setColor(lastStrokeSeparatorColor = color);
      strokeSeparatorPaint.setStyle(Paint.Style.STROKE);
      strokeSeparatorPaint.setStrokeWidth(Screen.separatorSize());
    } else if (lastStrokeSeparatorColor != color) {
      strokeSeparatorPaint.setColor(lastStrokeSeparatorColor = color);
    }
    return strokeSeparatorPaint;
  }

  public static Paint strokeBigPaint (int color) {
    if (strokeBigPaint == null) {
      strokeBigPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      strokeBigPaint.setStyle(Paint.Style.STROKE);
      strokeBigPaint.setStrokeWidth(Screen.dp(2f));
    }
    strokeBigPaint.setColor(color);
    return strokeBigPaint;
  }

  public static Paint strokeSmallPaint (int color) {
    if (strokeSmallPaint == null) {
      strokeSmallPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      strokeSmallPaint.setStyle(Paint.Style.STROKE);
      strokeSmallPaint.setStrokeWidth(Screen.dp(1f));
    }
    strokeSmallPaint.setColor(color);
    return strokeSmallPaint;
  }

  public static Paint reuseFillingPaint () {
    return filling;
  }

  private static Paint shadowFillingPaint;
  private static int lastShadowFillingColor;

  public static Paint shadowFillingPaint (int color) {
    if (shadowFillingPaint == null) {
      shadowFillingPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      shadowFillingPaint.setStyle(Paint.Style.FILL);
      float radius = Math.max(1, Screen.dpf(.5f));
      shadowFillingPaint.setShadowLayer(radius, 0f, radius, 0x5a000000);
      shadowFillingPaint.setColor(lastShadowFillingColor = color);
    } else if (lastShadowFillingColor != color) {
      shadowFillingPaint.setColor(lastShadowFillingColor = color);
    }
    return shadowFillingPaint;
  }

  private static float lastLettersDp, lastFakeLettersDp;

  private static LocalVar<SoftReference<TextPaint>> lettersLocal, lettersFakeLocal;

  private static TextPaint newWhiteMediumPaint (boolean fake) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setColor(0xffffffff);
    if (fake) {
      paint.setTypeface(Fonts.getRobotoRegular());
      paint.setFakeBoldText(true);
    } else {
      paint.setTypeface(Fonts.getRobotoMedium());
    }
    return paint;
  }

  public static TextPaint whiteMediumPaint (float dp, boolean fake, boolean measure) {
    if (measure && Looper.myLooper() != Looper.getMainLooper()) {
      TextPaint paint = null;
      synchronized (Paints.class) {
        LocalVar<SoftReference<TextPaint>> local = fake ? lettersLocal : lettersFakeLocal;
        if (local == null) {
          local = new LocalVar<>();
          if (fake) {
            lettersLocal = local;
          } else {
            lettersFakeLocal = local;
          }
        }
        SoftReference<TextPaint> reference = local.get();
        if (reference != null) {
          paint = reference.get();
        }
        if (paint == null) {
          paint = newWhiteMediumPaint(fake);
          local.set(new SoftReference<>(paint));
        }
        paint.setTextSize(Screen.dp(dp));
      }
      return paint;
    }
    if (fake) {
      if (lettersFake == null) {
        lettersFake = newWhiteMediumPaint(true);
        lettersFake.setTextSize(Screen.dp(lastFakeLettersDp = dp));
      } else if (lastFakeLettersDp != dp) {
        lettersFake.setTextSize(Screen.dp(lastFakeLettersDp = dp));
      }
      return lettersFake;
    } else {
      if (letters == null) {
        letters = newWhiteMediumPaint(false);
        letters.setTextSize(Screen.dp(lastLettersDp = dp));
      } else if (lastLettersDp != dp) {
        letters.setTextSize(Screen.dp(lastLettersDp = dp));
      }
    }
    return letters;
  }

  public static int measureLetters (Letters letters, float dp) {
    return letters != null ? (int) U.measureText(letters.text, whiteMediumPaint(dp, letters.needFakeBold, true)) : 0;
  }

  public static void drawLetters (Canvas c, Letters letters, float left, float top, float dp) {
    c.drawText(letters.text, left, top, whiteMediumPaint(dp, letters.needFakeBold, false));
  }

  public static void drawLetters (Canvas c, Letters letters, float left, float top, float dp, float alpha) {
    if (alpha == 1f) {
      c.drawText(letters.text, left, top, whiteMediumPaint(dp, letters.needFakeBold, false));
    } else if (alpha > 0f) {
      Paint paint = whiteMediumPaint(dp, letters.needFakeBold, false);
      paint.setAlpha((int) (255f * alpha));
      c.drawText(letters.text, left, top, paint);
      paint.setAlpha(255);
    }
  }

  private static TextPaint boldPaint15Fake, boldPaint15;
  private static TextPaint boldPaint13Fake, boldPaint13;
  private static TextPaint boldPaint14Fake, boldPaint14;

  public static TextPaint getBoldPaint15 (boolean fake, int color) {
    TextPaint paint = getBoldPaint15(fake);
    paint.setColor(color);
    return paint;
  }

  public static TextPaint getBoldPaint15 (boolean fake) {
    if (fake) {
      if (boldPaint15Fake == null) {
        boldPaint15Fake = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        boldPaint15Fake.setTextSize(Screen.dp(15f));
        boldPaint15Fake.setTypeface(Fonts.getRobotoRegular());
        boldPaint15Fake.setFakeBoldText(true);
      }
      return boldPaint15Fake;
    } else {
      if (boldPaint15 == null) {
        boldPaint15 = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        boldPaint15.setTextSize(Screen.dp(15f));
        boldPaint15.setTypeface(Fonts.getRobotoMedium());
      }
      return boldPaint15;
    }
  }

  public static TextPaint getBoldPaint14 (boolean fake, int color) {
    TextPaint paint = getBoldPaint14(fake);
    paint.setColor(color);
    return paint;
  }

  public static TextPaint getBoldPaint14 (boolean fake) {
    if (fake) {
      if (boldPaint14Fake == null) {
        boldPaint14Fake = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        boldPaint14Fake.setTextSize(Screen.dp(15f));
        boldPaint14Fake.setTypeface(Fonts.getRobotoRegular());
        boldPaint14Fake.setFakeBoldText(true);
      }
      return boldPaint14Fake;
    } else {
      if (boldPaint14 == null) {
        boldPaint14 = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        boldPaint14.setTextSize(Screen.dp(15f));
        boldPaint14.setTypeface(Fonts.getRobotoMedium());
      }
      return boldPaint14;
    }
  }

  public static TextPaint getBoldPaint13 (boolean fake, int color) {
    TextPaint paint = getBoldPaint13(fake);
    paint.setColor(color);
    return paint;
  }

  public static TextPaint getBoldPaint13 (boolean fake) {
    if (fake) {
      if (boldPaint13Fake == null) {
        boldPaint13Fake = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        boldPaint13Fake.setTextSize(Screen.dp(13f));
        boldPaint13Fake.setTypeface(Fonts.getRobotoRegular());
        boldPaint13Fake.setFakeBoldText(true);
      }
      return boldPaint13Fake;
    } else {
      if (boldPaint13 == null) {
        boldPaint13 = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        boldPaint13.setTextSize(Screen.dp(13f));
        boldPaint13.setTypeface(Fonts.getRobotoMedium());
      }
      return boldPaint13;
    }
  }

  private static TextPaint titlePaint, titlePaintFake, titleBigPaint, titleBigPaintFake, subtitlePaint;

  public static TextPaint getTitlePaint (boolean fake) {
    if (fake) {
      if (titlePaintFake == null) {
        titlePaintFake = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        titlePaintFake.setColor(Theme.textAccentColor());
        titlePaintFake.setTextSize(Screen.dp(15f));
        titlePaintFake.setTypeface(Fonts.getRobotoRegular());
        titlePaintFake.setFakeBoldText(true);
        ThemeManager.addThemeListener(titlePaintFake, ColorId.text);
      }
      return titlePaintFake;
    } else {
      if (titlePaint == null) {
        titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        titlePaint.setColor(Theme.textAccentColor());
        titlePaint.setTextSize(Screen.dp(15f));
        titlePaint.setTypeface(Fonts.getRobotoMedium());
        ThemeManager.addThemeListener(titlePaint, ColorId.text);
      }
      return titlePaint;
    }
  }

  public static TextPaint getTitleBigPaint () {
    return getTitleBigPaint(false);
  }

  public static TextPaint getTitleBigPaint (boolean fake) {
    if (fake) {
      if (titleBigPaintFake == null) {
        titleBigPaintFake = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        titleBigPaintFake.setColor(Theme.textAccentColor());
        titleBigPaintFake.setTextSize(Screen.dp(16f));
        titleBigPaintFake.setTypeface(Fonts.getRobotoRegular());
        titleBigPaintFake.setFakeBoldText(true);
        ThemeManager.addThemeListener(titleBigPaintFake, ColorId.text);
      }
      return titleBigPaintFake;
    } else {
      if (titleBigPaint == null) {
        titleBigPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        titleBigPaint.setColor(Theme.textAccentColor());
        titleBigPaint.setTextSize(Screen.dp(16f));
        titleBigPaint.setTypeface(Fonts.getRobotoMedium());
        ThemeManager.addThemeListener(titleBigPaint, ColorId.text);
      }
      return titleBigPaint;
    }
  }

  public static TextPaint getSubtitlePaint () {
    if (subtitlePaint == null) {
      subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      subtitlePaint.setColor(Theme.textDecentColor());
      subtitlePaint.setTextSize(Screen.dp(13f));
      subtitlePaint.setTypeface(Fonts.getRobotoRegular());
      ThemeManager.addThemeListener(subtitlePaint, ColorId.textLight);
    }
    return subtitlePaint;
  }

  public static TextStyleProvider getTitleStyleProvider () {
    return robotoStyleProvider(15);
  }

  public static TextStyleProvider getSubtitleStyleProvider () {
    return robotoStyleProvider(13);
  }

  private static Paint buttonOuterPaint;

  public static Paint getInlineButtonOuterPaint () {
    if (buttonOuterPaint == null) {
      buttonOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      buttonOuterPaint.setStrokeWidth(Screen.dp(1.5f));
      buttonOuterPaint.setStyle(Paint.Style.STROKE);
    }
    return buttonOuterPaint;
  }

  private static Paint
    unreadSeparationPaint,
    inlineIconPDPaint3,
    bubbleTimePaint,
    bubbleOverlayTimePaint;
  private static int
    bubbleTimePaintColor,
    bubbleOverlayTimePaintColor,
    unreadSeparationPaintColor,
    inlineIconPDPaintColor3;

  public static Paint createPorterDuffPaint (@Nullable Paint paint, int color) {
    if (paint == null)
      paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    paint.setColor(color);
    return paint;
  }

  private static int lastPorterDuffColor;
  private static Paint porterDuffPaint;
  private static SparseArrayCompat<PorterDuffColorFilter> porterDuffFilters;
  private static PorterDuffColorFilter lastPorterDuffObject;
  private static int lastPorterDuffObjectColor;

  public static PorterDuffColorFilter createColorFilter (int color) {
    return new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
  }

  public static PorterDuffColorFilter getColorFilter (int color) {
    if (lastPorterDuffObjectColor == color && lastPorterDuffObject != null) {
      return lastPorterDuffObject;
    }
    PorterDuffColorFilter filter;

    if (porterDuffFilters == null) {
      porterDuffFilters = new SparseArrayCompat<>();
      filter = null;
    } else {
      filter = porterDuffFilters.get(color);
    }

    if (filter == null) {
      filter = createColorFilter(color);
      if (porterDuffFilters.size() >= 20) {
        porterDuffFilters.removeAt(0);
      }
      porterDuffFilters.put(lastPorterDuffObjectColor = color, lastPorterDuffObject = filter);
    }

    return filter;
  }

  public static Paint getNotePorterDuffPaint () {
    return PorterDuffPaint.get(ColorId.playerCoverIcon);
  }

  public static Paint getHeaderPorterDuffPaint () {
    return PorterDuffPaint.get(ColorId.headerText);
  }

  public static Paint getVerifyPaint () {
    return PorterDuffPaint.get(ColorId.chatListVerify);
  }

  public static Paint getHeaderIconPaint () {
    return PorterDuffPaint.get(ColorId.headerIcon);
  }

  public static Paint getPasscodeIconPaint () {
    return PorterDuffPaint.get(ColorId.passcodeIcon);
  }

  public static Paint getBadgeTextPorterDuffPaint () {
    return PorterDuffPaint.get(ColorId.badgeText);
  }

  public static Paint getBubbleOutTimePaint () {
    return PorterDuffPaint.get(ColorId.bubbleOut_time);
  }

  public static Paint getBubbleInTimePaint () {
    return PorterDuffPaint.get(ColorId.bubbleIn_time);
  }

  public static Paint getGreenPorterDuffPaint () {
    return PorterDuffPaint.get(ColorId.textSecure);
  }

  public static Paint getIconGrayPorterDuffPaint () {
    return PorterDuffPaint.get(ColorId.icon);
  }

  public static Paint getBackgroundIconPorterDuffPaint () {
    return PorterDuffPaint.get(ColorId.background_icon);
  }

  public static Paint getIconLightPorterDuffPaint () {
    return PorterDuffPaint.get(ColorId.iconLight);
  }

  public static Paint getChatsMutePaint () {
    return PorterDuffPaint.get(ColorId.chatListMute);
  }

  public static Paint getActiveKeyboardPaint () {
    return PorterDuffPaint.get(ColorId.iconActive);
  }

  public static Paint getTicksPaint () {
    return PorterDuffPaint.get(ColorId.ticks);
  }

  public static Paint getBubbleTicksPaint () {
    return PorterDuffPaint.get(ColorId.bubbleOut_ticks);
  }

  public static Paint getTicksReadPaint () {
    return PorterDuffPaint.get(ColorId.ticksRead);
  }

  public static Paint getBubbleTicksReadPaint () {
    return PorterDuffPaint.get(ColorId.bubbleOut_ticksRead);
  }

  public static Paint getSendButtonPaint () {
    return PorterDuffPaint.get(ColorId.chatSendButton);
  }

  public static Paint getHeaderFloatIconPorterDuffPaint () {
    return PorterDuffPaint.get(ColorId.headerButtonIcon);
  }

  public static Paint getDecentPorterDuffPaint () {
    return PorterDuffPaint.get(ColorId.textLight);
  }

  public static Paint getInlineIconPorterDuffPaint (boolean isOutBubble) {
    int colorId = isOutBubble ? ColorId.bubbleOut_inlineIcon : ColorId.inlineIcon;
    return PorterDuffPaint.get(colorId);
  }

  public static Paint getUnreadSeparationPaint (int separationColor) {
    if (unreadSeparationPaint == null || unreadSeparationPaintColor != separationColor) {
      unreadSeparationPaint = createPorterDuffPaint(unreadSeparationPaint, unreadSeparationPaintColor = separationColor);
    }
    return unreadSeparationPaint;
  }

  public static Paint getBubbleOverlayTimePaint (int color) {
    if (bubbleOverlayTimePaint == null || bubbleOverlayTimePaintColor != color) {
      bubbleOverlayTimePaint = createPorterDuffPaint(bubbleOverlayTimePaint, bubbleOverlayTimePaintColor = color);
    }
    return bubbleOverlayTimePaint;
  }

  public static Paint getBubbleTimePaint (int color) {
    if (bubbleTimePaint == null || bubbleTimePaintColor != color) {
      bubbleTimePaint = createPorterDuffPaint(bubbleTimePaint, bubbleTimePaintColor = color);
    }
    return bubbleTimePaint;
  }

  public static Paint getInlineBubbleIconPaint (int color) {
    if (inlineIconPDPaint3 == null || inlineIconPDPaintColor3 != color) {
      inlineIconPDPaint3 = createPorterDuffPaint(inlineIconPDPaint3, inlineIconPDPaintColor3 = color);
    }
    return inlineIconPDPaint3;
  }

  public static Paint whitePorterDuffPaint () {
    return PorterDuffPaint.get(ColorId.white);
  }

  @Deprecated
  public static Paint getPorterDuffPaint (int color) {
    if (color == 0xffffffff) {
      return whitePorterDuffPaint();
    }

    PorterDuffColorFilter filter = getColorFilter(color);
    if (porterDuffPaint == null) {
      porterDuffPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
      porterDuffPaint.setColorFilter(filter);
      lastPorterDuffColor = color;
    } else if (lastPorterDuffColor != color) {
      lastPorterDuffColor = color;
      porterDuffPaint.setColorFilter(filter);
    }

    return porterDuffPaint;
  }

  private static int lastProgressColor;
  private static Paint progressOuterPaint;

  public static Paint getProgressOuterPaint (int color) {
    if (progressOuterPaint == null) {
      progressOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      progressOuterPaint.setStrokeWidth(Screen.dp(1.5f));
      progressOuterPaint.setStyle(Paint.Style.STROKE);
      if (ProgressComponent.USE_ROUNDED_CAP) {
        progressOuterPaint.setStrokeCap(Paint.Cap.ROUND);
      }
      progressOuterPaint.setColor(lastProgressColor = color);
    } else if (lastProgressColor != color) {
      progressOuterPaint.setColor(lastProgressColor = color);
    }
    return progressOuterPaint;
  }

  private static int lastProgressBigColor;
  private static Paint progressBigOuterPaint;
  private static float lastProgressBigStrokeWidth;

  public static Paint getProgressPaint (int color, float strokeWidth) {
    if (progressBigOuterPaint == null) {
      progressBigOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      progressBigOuterPaint.setStrokeWidth(lastProgressBigStrokeWidth = strokeWidth);
      progressBigOuterPaint.setStyle(Paint.Style.STROKE);
      if (ProgressComponent.USE_ROUNDED_CAP) {
        progressBigOuterPaint.setStrokeCap(Paint.Cap.ROUND);
      }
      progressBigOuterPaint.setColor(lastProgressBigColor = color);
    } else {
      if (lastProgressBigColor != color) {
        progressBigOuterPaint.setColor(lastProgressBigColor = color);
      }
      if (lastProgressBigStrokeWidth != strokeWidth) {
        progressBigOuterPaint.setStrokeWidth(lastProgressBigStrokeWidth = strokeWidth);
      }
    }
    return progressBigOuterPaint;
  }

  private static Paint playPausePaint;

  public static Paint getPlayPausePaint (int color, Paint.Style style) {
    if (playPausePaint == null) {
      playPausePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      playPausePaint.setPathEffect(new CornerPathEffect(Screen.dp(1.5f)));
    }
    playPausePaint.setStyle(style);
    playPausePaint.setColor(color);
    return playPausePaint;
  }

  private static RectF rectF;

  public static RectF getRectF () {
    if (rectF == null) {
      rectF = new RectF();
    }
    return rectF;
  }

  private static Path path;

  public static Path getPath () {
    if (path == null) {
      path = new Path();
    }
    return path;
  }

  private static Rect rect;

  public static Rect getRect () {
    if (rect == null) {
      rect = new Rect();
    }
    return rect;
  }

  private static LocalVar<Paint.FontMetricsInt> fontMetricsInt;

  public static Paint.FontMetricsInt getFontMetricsInt (TextPaint paint) {
    if (fontMetricsInt == null) {
      synchronized (Paints.class) {
        if (fontMetricsInt == null) {
          fontMetricsInt = new LocalVar<>();
        }
      }
    }
    Paint.FontMetricsInt fm = fontMetricsInt.get();
    if (fm == null) {
      fontMetricsInt.set(fm = new Paint.FontMetricsInt());
    }
    paint.getFontMetricsInt(fm);
    return fm;
  }

  private static TextPaint pagerTextPaint, pagerTextPaintFakeBold;

  public static TextPaint getViewPagerTextPaint (int color, boolean fakeBold) {
    if (fakeBold) {
      if (pagerTextPaintFakeBold == null) {
        pagerTextPaintFakeBold = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        pagerTextPaintFakeBold.setTypeface(Fonts.getRobotoRegular());
        pagerTextPaintFakeBold.setFakeBoldText(true);
        pagerTextPaintFakeBold.setTextSize(Screen.dp(14f));
      }
      pagerTextPaintFakeBold.setColor(color);
      return pagerTextPaintFakeBold;
    } else {
      if (pagerTextPaint == null) {
        pagerTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        pagerTextPaint.setTypeface(Fonts.getRobotoMedium());
        pagerTextPaint.setTextSize(Screen.dp(14f));
      }
      pagerTextPaint.setColor(color);
      return pagerTextPaint;
    }
  }

  private static Paint outerCheckPaint;
  private static int outerCheckPaintColor;

  public static Paint getOuterCheckPaint (int color) {
    if (outerCheckPaint == null) {
      outerCheckPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
      outerCheckPaint.setStrokeWidth(Screen.dp(2f));
      outerCheckPaint.setColor(outerCheckPaintColor = color);
      outerCheckPaint.setStyle(Paint.Style.STROKE);
    } else if (outerCheckPaintColor != color) {
      outerCheckPaint.setColor(outerCheckPaintColor = color);
    }
    return outerCheckPaint;
  }

  // Less ill-driven paints.

  public static <T extends Paint> T colorPaint (T paint, int color) {
    paint.setColor(color);
    return paint;
  }

  public static <T extends Paint> T sizePaint (T paint, float textSizeDp) {
    paint.setTextSize(Screen.dp(textSizeDp));
    return paint;
  }

  public static <T extends Paint> T alphaPaint (T paint, int alpha) {
    paint.setAlpha(alpha);
    return paint;
  }

  private static Paint erasePaint;
  public static Paint getErasePaint () {
    if (erasePaint == null) {
      synchronized (Paints.class) {
        if (erasePaint == null) {
          erasePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
          erasePaint.setColor(0);
          erasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }
      }
    }
    return erasePaint;
  }

  private static Paint xorPaint;
  public static Paint getXorPaint () {
    if (xorPaint == null) {
      synchronized (Paints.class) {
        if (xorPaint == null) {
          xorPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
          xorPaint.setColor(0);
          xorPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.XOR));
        }
      }
    }
    return xorPaint;
  }

  private static Paint srcInPaint;
  public static Paint getSrcInPaint (int color) {
    if (srcInPaint == null) {
      synchronized (Paints.class) {
        if (srcInPaint == null) {
          srcInPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
          srcInPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        }
      }
    }
    srcInPaint.setColor(color);
    return srcInPaint;
  }

  private static Paint bitmapPaint, bitmapPaint2;

  public static Paint getBitmapPaint () {
    if (bitmapPaint == null) {
      synchronized (Paints.class) {
        if (bitmapPaint == null) {
          bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
        }
      }
    }
    return bitmapPaint;
  }

  @UiThread
  public static Paint bitmapPaint () {
    return bitmapPaint(1f);
  }

  @UiThread
  public static Paint bitmapPaint (float alpha) {
    if (bitmapPaint2 == null) {
      bitmapPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    }
    bitmapPaint2.setAlpha(Math.round(255f * MathUtils.clamp(alpha)));
    return bitmapPaint2;
  }

  private static TextPaint emojiPaint;

  public static TextPaint emojiPaint () {
    if (emojiPaint == null) {
      synchronized (Paints.class) {
        if (emojiPaint == null) {
          emojiPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
          emojiPaint.setTextSize(Screen.dp(17f));
        }
      }
    }
    return emojiPaint;
  }

  // Text paints. TODO reuse it instead of other text paints

  private static LocalVar<TextPaint> regularTextPaint, mediumTextPaint, mediumTextPaintFake, boldTextPaint;

  private static TextPaint newTextPaint (Typeface typeface) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setTypeface(typeface);
    return paint;
  }

  public static TextPaint getRegularTextPaint (float textSizeDp, int color) {
    return colorPaint(getRegularTextPaint(textSizeDp), color);
  }

  public static TextPaint getMediumTextPaint (float textSizeDp, int color, boolean needFakeBold) {
    return colorPaint(getMediumTextPaint(textSizeDp, needFakeBold), color);
  }

  public static TextPaint getBoldTextPaint (float textSizeDp, int color) {
    return colorPaint(getBoldTextPaint(textSizeDp), color);
  }

  public static TextPaint getRegularTextPaint (float textSizeDp) {
    if (regularTextPaint == null) {
      synchronized (Paints.class) {
        if (regularTextPaint == null)
          regularTextPaint = new LocalVar<>();
      }
    }
    TextPaint paint = regularTextPaint.get();
    if (paint == null)
      regularTextPaint.set(paint = newTextPaint(Fonts.getRobotoRegular()));
    return sizePaint(paint, textSizeDp);
  }

  public static TextPaint getMediumTextPaint (float textSizeDp, boolean needFakeBold) {
    if (needFakeBold) {
      if (mediumTextPaintFake == null) {
        synchronized (Paints.class) {
          if (mediumTextPaintFake == null)
            mediumTextPaintFake = new LocalVar<>();
        }
      }
      TextPaint paint = mediumTextPaintFake.get();
      if (paint == null) {
        paint = newTextPaint(Fonts.getRobotoRegular());
        paint.setFakeBoldText(true);
        mediumTextPaintFake.set(paint);
      }
      return sizePaint(paint, textSizeDp);
    } else {
      if (mediumTextPaint == null) {
        synchronized (Paints.class) {
          if (mediumTextPaint == null)
            mediumTextPaint = new LocalVar<>();
        }
      }
      TextPaint paint = mediumTextPaint.get();
      if (paint == null)
        mediumTextPaint.set(paint = newTextPaint(Fonts.getRobotoMedium()));
      return sizePaint(paint, textSizeDp);
    }
  }

  public static TextPaint getBoldTextPaint (float textSizeDp) {
    if (boldTextPaint == null) {
      synchronized (Paints.class) {
        if (boldTextPaint == null)
          boldTextPaint = new LocalVar<>();
      }
    }
    TextPaint paint = boldTextPaint.get();
    if (paint == null)
      boldTextPaint.set(paint = newTextPaint(Fonts.getRobotoBold()));
    return sizePaint(paint, textSizeDp);
  }

  // Aliases

  private static TextPaint textPaint16, textPaint15;

  public static TextPaint getTextPaint16 (int color) {
    return colorPaint(getTextPaint16(), color);
  }

  public static TextPaint getTextPaint15 (int color) {
    return colorPaint(getTextPaint15(), color);
  }

  public static TextPaint getTextPaint16 () {
    if (textPaint16 == null) {
      synchronized (Paints.class) {
        if (textPaint16 == null)
          textPaint16 = newTextPaint(Fonts.getRobotoRegular());
      }
    }
    return sizePaint(textPaint16, 16f);
  }

  public static TextPaint getTextPaint15 () {
    if (textPaint15 == null) {
      synchronized (Paints.class) {
        if (textPaint15 == null)
          textPaint15 = newTextPaint(Fonts.getRobotoRegular());
      }
    }
    return sizePaint(textPaint15, 15f);
  }

  // TextStyleProvider

  private static SparseArrayCompat<TextStyleProvider> robotoProvider;
  private static TextStyleProvider titleStyleProvider, subtitleStyleProvider;

  public static TextStyleProvider robotoStyleProvider (float dp) {
    if (dp == 15) {
      if (titleStyleProvider == null) {
        titleStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(15f);
      }
      return titleStyleProvider;
    } else if (dp == 13) {
      if (subtitleStyleProvider == null) {
        subtitleStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(13f);
      }
      return subtitleStyleProvider;
    }

    if (robotoProvider == null) {
      robotoProvider = new SparseArrayCompat<>();
    }
    int key = Float.floatToIntBits(dp);
    int i = robotoProvider.indexOfKey(key);
    TextStyleProvider provider;
    if (i >= 0) {
      provider = robotoProvider.valueAt(i);
    } else {
      robotoProvider.put(key, provider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(dp));
    }
    return provider;
  }
}
