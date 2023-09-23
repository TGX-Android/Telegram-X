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
 * File created on 06/03/2018
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.StringUtils;

public class EmojiView extends View implements ClickHelper.Delegate {
  private final ClickHelper helper;
  private final Tdlib tdlib;
  private final EmojiToneHelper toneHelper;

  public EmojiView (Context context, Tdlib tdlib, EmojiToneHelper toneHelper) {
    super(context);
    this.tdlib = tdlib;
    this.toneHelper = toneHelper;
    this.helper = new ClickHelper( this);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    //noinspection SuspiciousNameCombination
    super.onMeasure(widthMeasureSpec, widthMeasureSpec);
  }

  private boolean dispatchingEvents;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        dispatchingEvents = super.onTouchEvent(e);
        break;
      }
      default: {
        if (dispatchingEvents) {
          super.onTouchEvent(e);
        }
        break;
      }
    }
    return isEnabled() && helper.onTouchEvent(this, e);
  }

  private OnClickListener onClickListener;

  @Override
  public void setOnClickListener (@Nullable OnClickListener l) {
    this.onClickListener = l;
  }

  private String emoji;
  private String emojiTone;
  private String[] emojiOtherTones;
  private String emojiColored;

  public String getRawEmoji () {
    return emoji;
  }

  public String getEmojiColored () {
    return emojiColored;
  }

  private int colorState;

  public void setEmoji (String emoji, int colorState) {
    if (!StringUtils.equalsOrBothEmpty(this.emoji, emoji)) {
      this.emoji = emoji;
      this.colorState = colorState;
      this.emojiTone = colorState != EmojiData.STATE_NO_COLORS ? Emoji.instance().toneForEmoji(emoji) : null;
      this.emojiOtherTones = colorState == EmojiData.STATE_HAS_TWO_COLORS ? Emoji.instance().otherTonesForEmoji(emoji) : null;
      setEmojiImpl(emoji, emojiTone, emojiOtherTones);
    }
  }

  private void setEmojiImpl (String emoji, String tone, String[] otherTones) {
    String emojiColored = EmojiData.instance().colorize(emoji, tone, otherTones);
    if (!StringUtils.equalsOrBothEmpty(this.emojiColored, emojiColored)) {
      this.emojiColored = emojiColored;
      this.emojiTone = tone;
      this.emojiOtherTones = otherTones;
      setDrawable(Emoji.instance().getEmojiInfo(emojiColored));
    }
  }

  private EmojiInfo info;
  private void setDrawable (EmojiInfo info) {
    if (this.info != info) {
      this.info = info;
      invalidate();
    }
  }

  public boolean applyTone (String emoji, String tone, String[] otherTones) {
    if (StringUtils.equalsOrBothEmpty(this.emoji, emoji)) {
      setEmojiImpl(emoji, tone, otherTones);
      return true;
    }
    return false;
  }

  @Override
  protected void onDraw (Canvas c) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    if (info != null) {
      int cx = viewWidth / 2;
      int cy = viewHeight / 2;

      int size = Math.min(viewWidth, viewHeight) - Screen.dp(16f);

      Rect rect = Paints.getRect();
      rect.left = cx - size / 2;
      rect.top = cy - size / 2;
      rect.right = rect.left + size;
      rect.bottom = rect.top + size;

      Emoji.instance().draw(c, info, rect);
    }
  }

  @Override
  public boolean needClickAt (View view, float x, float y) {
    return onClickListener != null;
  }

  @Override
  public void onClickAt (View view, float x, float y) {
    if (onClickListener != null) {
      onClickListener.onClick(view);
    }
  }

  @Override
  public boolean needLongPress (float x, float y) {
    if (toneHelper != null && toneHelper.canBeShown()) {
      if (tdlib.hasPremium() || toneHelper.isInSelfChat()) {
        preliminaryLoadCustomEmoji(emoji);
        return true;
      }
      return colorState != EmojiData.STATE_NO_COLORS;
    }
    return false;
  }

  private TdlibUi.EmojiStickers emojiStickers;

  private void preliminaryLoadCustomEmoji (String emoji) {
    emojiStickers = tdlib.ui().getEmojiStickers(new TdApi.StickerTypeCustomEmoji(), emoji, false, 6, toneHelper.getCurrentChatId());
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    if (emojiStickers == null || !emojiStickers.query.equals(this.emoji)) {
      return false;
    }
    emojiStickers.getStickers(new TdlibUi.EmojiStickers.Callback() {
      @Override
      public void onStickersLoaded (TdlibUi.EmojiStickers context, @NonNull TdApi.Sticker[] installedStickers, @Nullable TdApi.Sticker[] recommendedStickers, boolean expectMoreStickers) {
        tdlib.ui().execute(() -> {
          if (emojiStickers == context) {
            int totalCount = installedStickers.length + (recommendedStickers != null ? recommendedStickers.length : 0);
            if (totalCount > 0 || colorState != EmojiData.STATE_NO_COLORS) {
              onLongClick(view, x, y, installedStickers, recommendedStickers);
            }
          }
        });
      }

      @Override
      public void onRecommendedStickersLoaded (TdlibUi.EmojiStickers context, @NonNull TdApi.Sticker[] recommendedStickers) {
        // TODO show recommended stickers
      }
    }, 300);
    return false;
  }

  private void onLongClick (View view, float x, float y, @NonNull TdApi.Sticker[] installedStickers, @Nullable TdApi.Sticker[] recommendedStickers) {
    helper.onLongPress(view, x, y);
    setInLongPress(true);
    toneHelper.openForEmoji(view, x, y, emoji, colorState, emojiTone, emojiOtherTones, installedStickers, recommendedStickers);
  }

  @Override
  public void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) {
    toneHelper.processMovement(view, e, x, y);
  }

  @Override
  public void onLongPressCancelled (View view, float x, float y) {
    emojiStickers = null;
    setInLongPress(false);
    toneHelper.hide(view);
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    if (view != this)
      throw new AssertionError();
    completeToneSelection();
    emojiStickers = null;
  }

  public void completeToneSelection () {
    String emoji = toneHelper.getSelectedEmoji();
    String selectedTone = toneHelper.getSelectedTone();
    String[] selectedOtherTones = toneHelper.getSelectedOtherTones();
    TGStickerObj selectedCustomEmoji = toneHelper.getSelectedCustomEmoji();
    boolean needApplyToAll = toneHelper.needApplyToAll();

    if (selectedCustomEmoji != null) {
      toneHelper.onCustomEmojiSelected(selectedCustomEmoji);
    } else if (needApplyToAll) {
      if (toneHelper.needForgetApplyToAll()) {
        Settings.instance().markTutorialAsComplete(Settings.TUTORIAL_EMOJI_TONE_ALL);
      }
      Emoji.instance().setDefaultTone(selectedTone);
    } else {
      Emoji.instance().setCustomTone(emoji, selectedTone, selectedOtherTones);
      if (onClickListener != null) {
        onClickListener.onClick(this);
      }
    }

    setInLongPress(false);
    toneHelper.hide(this);
  }

  private ViewParent requestedDisallowOnParent;

  private void setInLongPress (boolean inLongPress) {
    if (inLongPress) {
      setPressed(false);
      dispatchingEvents = false;
      requestedDisallowOnParent = getParent();
    }
    if (requestedDisallowOnParent != null) {
      requestedDisallowOnParent.requestDisallowInterceptTouchEvent(inLongPress);
    }
  }
}
