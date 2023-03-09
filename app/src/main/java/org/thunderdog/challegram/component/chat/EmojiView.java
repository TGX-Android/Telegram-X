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

import androidx.annotation.Nullable;

import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiInfo;
import org.thunderdog.challegram.tool.EmojiData;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.util.ClickHelper;
import me.vkryl.core.StringUtils;

public class EmojiView extends View implements ClickHelper.Delegate {
  private final ClickHelper helper;
  private final EmojiToneHelper toneHelper;

  public EmojiView (Context context, EmojiToneHelper toneHelper) {
    super(context);
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
    return colorState != EmojiData.STATE_NO_COLORS && toneHelper != null && toneHelper.canBeShown();
  }

  @Override
  public boolean onLongPressRequestedAt (View view, float x, float y) {
    if (colorState != EmojiData.STATE_NO_COLORS) {
      UI.forceVibrate(view, false);
      setInLongPress(true);
      return toneHelper.openForEmoji(view, x, y, emoji, colorState, emojiTone, emojiOtherTones);
    }
    return false;
  }

  @Override
  public void onLongPressMove (View view, MotionEvent e, float x, float y, float startX, float startY) {
    toneHelper.processMovement(view, e, x, y);
  }

  @Override
  public void onLongPressCancelled (View view, float x, float y) {
    setInLongPress(false);
    toneHelper.hide(view);
  }

  @Override
  public void onLongPressFinish (View view, float x, float y) {
    if (view != this)
      throw new AssertionError();
    completeToneSelection();
  }

  public void completeToneSelection () {
    String emoji = toneHelper.getSelectedEmoji();
    String selectedTone = toneHelper.getSelectedTone();
    String[] selectedOtherTones = toneHelper.getSelectedOtherTones();
    boolean needApplyToAll = toneHelper.needApplyToAll();

    if (needApplyToAll) {
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
