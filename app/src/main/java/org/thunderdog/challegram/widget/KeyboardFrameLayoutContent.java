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
 * File created on 12/06/2024
 */

package org.thunderdog.challegram.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.thunderdog.challegram.tool.Keyboard;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.BitwiseUtils;

public class KeyboardFrameLayoutContent extends FrameLayout {
  public static final int FLAG_CUSTOM_HEIGHT = 1;
  public static final int FLAG_KEYBOARD_VISIBLE = 1 << 1;

  private int flags;
  private int additionalHeight;

  private KeyboardFrameLayout keyboardView;

  public final @NonNull EmojiLayout emojiLayout;
  public final @NonNull TextFormattingLayout textFormattingLayout;

  public KeyboardFrameLayoutContent (@NonNull Context context) {
    super(context);
    emojiLayout = new EmojiLayout(context);
    textFormattingLayout = new TextFormattingLayout(context);
    textFormattingLayout.setVisibility(View.GONE);

    addView(emojiLayout, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(textFormattingLayout, FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
  }

  public int getAdditionalHeight () {
    return additionalHeight;
  }

  void setKeyboardView (KeyboardFrameLayout keyboardView) {
    this.keyboardView = keyboardView;
    this.textFormattingLayout.setKeyboardView(keyboardView);
  }

  void setKeyboardVisible (boolean keyboardVisible) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_KEYBOARD_VISIBLE, keyboardVisible);
  }

  void setAllowCustomHeight (boolean allowCustomHeight) {
    this.flags = BitwiseUtils.setFlag(flags, FLAG_CUSTOM_HEIGHT, allowCustomHeight);
  }

  void setAdditionalHeight (int additionalHeight) {
    this.additionalHeight = additionalHeight;
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    if (!BitwiseUtils.hasFlag(flags, FLAG_CUSTOM_HEIGHT)) {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      return;
    }

    int height = additionalHeight;
    if (!BitwiseUtils.hasFlag(flags, FLAG_KEYBOARD_VISIBLE)) {
      height += Keyboard.getSize();
    }

    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
  }

  /* * */

  public boolean setTextFormattingLayoutVisible (boolean visible) {
    emojiLayout.optimizeForDisplayTextFormattingLayout(visible);    // todo - just hide view ?
    textFormattingLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (visible) {
      textFormattingLayout.checkButtonsActive(false);
    }

    return visible;
  }
}
