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
 * File created on 31/05/2023
 */
package org.thunderdog.challegram.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.gif.GifFile;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextMedia;

import me.vkryl.core.ColorUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class EmojiStatusHelper implements Destroyable {
  private final @Nullable Tdlib tdlib;
  public final ComplexReceiver emojiStatusReceiver;
  private final View parentView;

  private @Nullable EmojiStatusDrawable emojiStatusDrawable;
  private @Nullable Text.ClickListener clickListenerToSet;

  private @Nullable String sharedUsageId;

  public EmojiStatusHelper (@Nullable Tdlib tdlib, View v, @Nullable String sharedUsageId) {
    this.tdlib = tdlib;
    this.parentView = v;
    this.sharedUsageId = sharedUsageId;
    emojiStatusReceiver = new ComplexReceiver(v, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);
  }

  public void setSharedUsageId (@Nullable String sharedUsageId) {
    this.sharedUsageId = sharedUsageId;
  }

  public void setClickListener (@Nullable Text.ClickListener clickListenerToSet) {
    this.clickListenerToSet = clickListenerToSet;
  }

  public void invalidateEmojiStatusReceiver (@Nullable TextMedia specificMedia) {
    invalidateEmojiStatusReceiver(emojiStatusDrawable != null ? emojiStatusDrawable.emojiStatus : null, specificMedia);
  }

  public void invalidateEmojiStatusReceiver (Text text, @Nullable TextMedia specificMedia) {
    if (text != null) {
      text.requestMedia(emojiStatusReceiver, 0, 1);
    }
    if (parentView != null) {
      parentView.invalidate();
    }
  }

  public void updateEmoji (TdlibAccount account, TextColorSet textColorSet) {
    updateEmoji(account, textColorSet, R.drawable.baseline_premium_star_16, 15);
  }

  public void updateEmoji (TdlibAccount account, TextColorSet textColorSet, @DrawableRes int defaultStarIconId, int textSize) {
    TdApi.User user = account.getUser();
    if (user != null) {
      updateEmoji(account.tdlib(), user, textColorSet, defaultStarIconId, textSize);
    } else {
      TdApi.Sticker cachedSticker = account.getEmojiStatusSticker();
      if (cachedSticker != null || account.isPremium()) {
        updateEmojiWithoutTdlib(account.isPremium(), cachedSticker, textColorSet, defaultStarIconId, textSize);
      } else {
        clear();
      }
    }
  }

  public void updateEmojiWithoutTdlib (boolean isPremium, @Nullable TdApi.Sticker sticker, TextColorSet textColorSet, int defaultStarIconId, int textSize) {
    emojiStatusDrawable = new EmojiStatusDrawable(parentView, sharedUsageId, isPremium, sticker, clickListenerToSet, textColorSet, defaultStarIconId, textSize);
    emojiStatusDrawable.ignoreDraw = ignoreDraw;
    invalidateEmojiStatusReceiver(null, null);
  }

  public void updateEmoji (@Nullable TdApi.User user, TextColorSet textColorSet) {
    updateEmoji(tdlib, user, textColorSet);
  }

  public void updateEmoji (Tdlib tdlib, @Nullable TdApi.User user, TextColorSet textColorSet) {
    updateEmoji(tdlib, user, textColorSet, R.drawable.baseline_premium_star_16, 15);
  }

  public boolean onTouchEvent (View v, MotionEvent ev) {
    return emojiStatusDrawable != null && emojiStatusDrawable.onTouchEvent(v, ev);
  }

  public void updateEmoji (Tdlib tdlib, @Nullable TdApi.User user, TextColorSet textColorSet, int defaultStarIconId, int textSize) {
    if (user == null || !user.isPremium) {
      clear();
      return;
    }
    emojiStatusDrawable = new EmojiStatusDrawable(sharedUsageId, tdlib, user, clickListenerToSet, textColorSet, this::invalidateEmojiStatusReceiver, defaultStarIconId, textSize);
    emojiStatusDrawable.ignoreDraw = ignoreDraw;
    invalidateEmojiStatusReceiver(emojiStatusDrawable.emojiStatus, null);
  }

  public int getLastDrawX () {
    return emojiStatusDrawable != null ? emojiStatusDrawable.lastDrawX : 0;
  }

  public int getLastDrawY () {
    return emojiStatusDrawable != null ? emojiStatusDrawable.lastDrawY : 0;
  }

  public void attach () {
    emojiStatusReceiver.attach();
  }

  public void detach () {
    emojiStatusReceiver.detach();
  }

  @Override
  public void performDestroy () {
    clear();
  }

  public boolean needDrawEmojiStatus () {
    return emojiStatusDrawable != null && emojiStatusDrawable.needDrawEmojiStatus;
  }

  public int getWidth () {
    return emojiStatusDrawable != null ? emojiStatusDrawable.getWidth() : 0;
  }

  public int getWidth (int offset) {
    return emojiStatusDrawable != null ? emojiStatusDrawable.getWidth(offset) : 0;
  }

  public void draw (Canvas c, int startX, int startY) {
    draw(c, startX, startY, 1f);
  }

  public void draw (Canvas c, int startX, int startY, float alpha) {
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.draw(c, startX, startY, alpha, emojiStatusReceiver);
    }
  }

  public void draw (Canvas c, int startX, int startY, float alpha, float scale) {
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.draw(c, startX, startY, alpha, scale, emojiStatusReceiver);
    }
  }

  public interface EmojiStatusReceiverInvalidateDelegate {
    void invalidateEmojiStatusReceiver (Text text, @Nullable TextMedia specificMedia);
  }

  public static int emojiSizeToTextSize (int emojiSize) {
    return emojiSize - 3;
  }

  public static int textSizeToEmojiSize (int textSize) {
    return textSize + 3;
  }

  private boolean ignoreDraw;

  public void clear () {
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.performDestroy();
      emojiStatusDrawable = null;
    }
    emojiStatusReceiver.performDestroy();
  }

  public void setIgnoreDraw (boolean ignoreDraw) {
    this.ignoreDraw = ignoreDraw;
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.ignoreDraw = ignoreDraw;
    }
  }

  public void onAppear () {
    if (emojiStatusDrawable != null) {
      emojiStatusDrawable.onAppear();
    }
  }

  public static EmojiStatusDrawable makeDrawable (String sharedUsageId, Tdlib tdlib, @Nullable TdApi.User user, TextColorSet textColorSet, Text.TextMediaListener textMediaListener) {
    return new EmojiStatusDrawable(sharedUsageId, tdlib, user, null, textColorSet, textMediaListener, R.drawable.baseline_premium_star_16, 15);
  }

  public static class EmojiStatusDrawable implements Destroyable {
    private final @Nullable Text emojiStatus;
    private final @Nullable Drawable starDrawable;
    private final @Nullable TextColorSet textColorSet;
    private final @Nullable Text.ClickListener clickListener;
    private final boolean needDrawEmojiStatus;
    private final Text.TextMediaListener textMediaListener;
    private final int textSize;
    private final @Nullable ImageReceiver preview;
    private final @Nullable ImageReceiver imageReceiver;
    private final @Nullable GifReceiver gifReceiver;
    private final boolean needRepainting;
    private int lastDrawX, lastDrawY;
    private float lastDrawScale = 1f;
    private boolean ignoreDraw;

    private EmojiStatusDrawable (@Nullable String sharedUsageId, Tdlib tdlib, @Nullable TdApi.User user, @Nullable Text.ClickListener clickListener, @Nullable TextColorSet textColorSet, Text.TextMediaListener textMediaListener, int defaultStarIconId, int textSize) {
      this.emojiStatus = makeText(sharedUsageId, tdlib, user, clickListener, textColorSet, textMediaListener, textSize);
      this.needDrawEmojiStatus = user != null && user.isPremium;
      this.textSize = textSize;
      this.textColorSet = textColorSet;
      this.textMediaListener = textMediaListener;
      this.clickListener = clickListener;
      this.starDrawable = emojiStatus == null && needDrawEmojiStatus ? Drawables.get(defaultStarIconId) : null;
      this.preview = null;
      this.imageReceiver = null;
      this.gifReceiver = null;
      this.needRepainting = false;
    }

    private EmojiStatusDrawable (View v, @Nullable String sharedUsageId, boolean isPremium, @Nullable TdApi.Sticker sticker, @Nullable Text.ClickListener clickListener, @Nullable TextColorSet textColorSet, int defaultStarIconId, int textSize) {
      this.emojiStatus = null;
      this.needDrawEmojiStatus = isPremium;
      this.textSize = textSize;
      this.textColorSet = textColorSet;
      this.textMediaListener = null;
      this.clickListener = clickListener;
      this.starDrawable = needDrawEmojiStatus && (sticker == null || !TD.isFileLoaded(sticker.sticker)) ? Drawables.get(defaultStarIconId) : null;
      this.needRepainting = TD.needRepainting(sticker);

      if (sticker != null && TD.isFileLoaded(sticker.sticker)) {
        this.imageReceiver = new ImageReceiver(v, 0);
        this.gifReceiver = new GifReceiver(v);
        this.preview = new ImageReceiver(v, 0);
        if (Td.isAnimated(sticker.format)) {
          GifFile gifFile = new GifFile(null, sticker);
          gifFile.setScaleType(GifFile.FIT_CENTER);
          gifFile.setOptimizationMode(GifFile.OptimizationMode.EMOJI);
          gifFile.setRepeatCount(2);
          gifFile.setPlayOnceId(sharedUsageId);
          gifReceiver.requestFile(gifFile);
        } else {
          ImageFile imageFile = new ImageFile(null, sticker.sticker);
          imageFile.setScaleType(GifFile.FIT_CENTER);
          imageReceiver.requestFile(imageFile);
        }
        if (sticker.thumbnail != null && TD.isFileLoaded(sticker.thumbnail.file)) {
          preview.requestFile(TD.toImageFile(null, sticker.thumbnail));
        }
      } else {
        this.preview = null;
        this.imageReceiver = null;
        this.gifReceiver = null;
      }
    }

    @Override
    public void performDestroy () {
      if (imageReceiver != null) {
        imageReceiver.destroy();
      }
      if (preview != null) {
        preview.destroy();
      }
      if (gifReceiver != null) {
        gifReceiver.destroy();
      }
    }

    public void setIgnoreDraw (boolean ignoreDraw) {
      this.ignoreDraw = ignoreDraw;
    }

    public void invalidateTextMedia () {
      if (emojiStatus != null) {
        textMediaListener.onInvalidateTextMedia(emojiStatus, null);
      }
    }

    public int getWidth () {
      if (imageReceiver != null) {
        return Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize));
      } else if (emojiStatus != null) {
        return emojiStatus.getWidth();
      } else if (needDrawEmojiStatus) {
        return Screen.dp(18);
      }
      return 0;
    }

    public int getWidth (int offset) {
      if (imageReceiver != null) {
        return Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)) + offset;
      } else if (emojiStatus != null) {
        return emojiStatus.getWidth() + offset;
      } else if (needDrawEmojiStatus) {
        return Screen.dp(18) + offset;
      }
      return 0;
    }

    public void requestMedia (ComplexReceiver complexReceiver) {
      if (emojiStatus != null) {
        emojiStatus.requestMedia(complexReceiver, 0, 1);
      }
    }

    boolean isCapture;

    public boolean onTouchEvent (View v, MotionEvent e) {
      if (emojiStatus != null) {
        return emojiStatus.onTouchEvent(v, e);
      }

      int width = starDrawable != null ? starDrawable.getMinimumWidth() :
        imageReceiver != null ? imageReceiver.getWidth() : 0;
      int height = starDrawable != null ? starDrawable.getMinimumHeight() :
        imageReceiver != null ? imageReceiver.getHeight() : 0;

      if (clickListener == null || width == 0 || height == 0) return false;

      int touchX = (int) e.getX();
      int touchY = (int) e.getY();

      boolean isInside = (lastDrawX <= touchX) && (lastDrawY <= touchY) &&
        (touchX <= lastDrawX + width) &&
        (touchY <= lastDrawY + height);

      switch (e.getAction()) {
        case MotionEvent.ACTION_DOWN: {
          return isCapture = isInside;
        }
        case MotionEvent.ACTION_CANCEL: {
          boolean r = isCapture;
          isCapture = false;
          return r;
        }
        case MotionEvent.ACTION_MOVE: {
          if (isCapture) {
            if (Math.max(Math.abs(touchX - e.getX()), Math.abs(touchY - e.getY())) > Screen.getTouchSlop()) {
              isCapture = false;
            }
            return true;
          }
          return false;
        }
        case MotionEvent.ACTION_UP: {
          if (isCapture) {
            clickListener.onClick(null, null, null, null);
            isCapture = false;
            return true;
          }
          break;
        }
      }
      return isCapture;
    }

    public void draw (Canvas c, int startX, int startY, float alpha, ComplexReceiver emojiStatusReceiver) {
      draw(c, startX, startY, alpha, 1f, emojiStatusReceiver);
    }

    public void draw (Canvas c, int startX, int startY, float alpha, float scale, ComplexReceiver emojiStatusReceiver) {
      if (ignoreDraw) return;

      final boolean isScaled = scale != 1f;
      int restoreToCount = -1;
      if (isScaled) {
        restoreToCount = Views.save(c);
        c.scale(scale, scale, startX, startY);
      }
      lastDrawX = startX;
      lastDrawY = startY;
      lastDrawScale = scale;
      if (imageReceiver != null && gifReceiver != null && preview != null) {
        int repaintRestoreToCount = -1;
        if (needRepainting) {
          repaintRestoreToCount = c.saveLayerAlpha(startX, startY, startX + Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)), startY + Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)), 255, Canvas.ALL_SAVE_FLAG);
        }

        imageReceiver.setBounds(startX, startY, startX + Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)), startY + Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)));
        preview.setBounds(startX, startY, startX + Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)), startY + Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)));
        gifReceiver.setBounds(startX, startY, startX + Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)), startY + Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)));
        if (!gifReceiver.isEmpty()) {
          if (gifReceiver.needPlaceholder()) {
            preview.draw(c);
          }
          gifReceiver.draw(c);
        } else if (!imageReceiver.isEmpty()) {
          if (imageReceiver.needPlaceholder()) {
            preview.draw(c);
          }
          imageReceiver.draw(c);
        }
        if (needRepainting) {
          if (textColorSet != null) {
            c.drawRect(startX, startY, startX + Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)), startY + Screen.dp(EmojiStatusHelper.textSizeToEmojiSize(textSize)), Paints.getSrcInPaint(textColorSet.emojiStatusColor()));
          }
          Views.restore(c, repaintRestoreToCount);
        }
      } else if (emojiStatus != null) {
        emojiStatus.draw(c, startX, startY, null, alpha, emojiStatusReceiver);
      } else if (starDrawable != null && textColorSet != null) {
        Paint p = Paints.getPorterDuffPaint(ColorUtils.alphaColor(alpha, textColorSet.emojiStatusColor()));
        Drawables.draw(c, starDrawable, startX, startY + (Screen.dp(textSize + 2) - starDrawable.getMinimumHeight()) / 2f, p);
      }
      if (isScaled) {
        Views.restore(c, restoreToCount);
      }
    }

    public void onAppear () {
      if (emojiStatus != null) {
        TextMedia part = emojiStatus.getTextMediaFromLastPart();
        if (part != null) {
          part.rebuild();
        }
      }
    }
  }



  private static @Nullable Text makeText (@Nullable String sharedUsageId, Tdlib tdlib, @Nullable TdApi.User user, Text.ClickListener clickListener, TextColorSet textColorSet, Text.TextMediaListener textMediaListener, int textSize) {
    TdApi.FormattedText text = makeEmojiText(user);
    if (text == null) return null;

    Text result = new Text.Builder(tdlib, text, null, Screen.dp(1000), Paints.robotoStyleProvider(textSize), textColorSet, textMediaListener)
      .singleLine()
      .onClick(clickListener)
      .build();

    TextMedia part = result.getTextMediaFromLastPart();
    if (part != null) {
      part.setIsEmojiStatus(sharedUsageId);
    }

    return result;
  }

  private static @Nullable TdApi.FormattedText makeEmojiText (@Nullable TdApi.User user) {
    if (user == null || user.emojiStatus == null) return null;

    long emojiStatusId = user.emojiStatus.customEmojiId;
    TdApi.TextEntity emoji = new TdApi.TextEntity(0, 1, new TdApi.TextEntityTypeCustomEmoji(emojiStatusId));
    return new TdApi.FormattedText("*", new TdApi.TextEntity[] {emoji});
  }
}
