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
 * File created on 18/08/2017
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.DoubleTextWrapper;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.EmojiStatusHelper;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextMedia;

import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.core.lambda.Destroyable;

public class SmallChatView extends BaseView implements AttachDelegate, TooltipOverlayView.LocationProvider, InvalidateContentProvider, Destroyable, EmojiStatusHelper.EmojiStatusReceiverInvalidateDelegate {
  private final AvatarReceiver avatarReceiver;
  private final EmojiStatusHelper emojiStatusHelper;

  private DoubleTextWrapper chat;

  public SmallChatView (Context context, Tdlib tdlib) {
    super(context, tdlib);

    int viewHeight = Screen.dp(62f);
    this.avatarReceiver = new AvatarReceiver(this);
    this.emojiStatusHelper = new EmojiStatusHelper(tdlib, this, null);
    layoutReceiver();
    setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewHeight));
  }

  private void layoutReceiver () {
    int viewHeight = Screen.dp(62f);
    int radius = Screen.dp(50f) / 2;
    int left = Screen.dp(11f);
    int right = Screen.dp(11f) + radius * 2;
    int viewWidth = getMeasuredWidth();
    if (viewWidth == 0)
      return;
    if (Lang.rtl()) {
      this.avatarReceiver.setBounds(viewWidth - right, viewHeight / 2 - radius, viewWidth - left, viewHeight / 2 + radius);
    } else {
      this.avatarReceiver.setBounds(left, viewHeight / 2 - radius, right, viewHeight / 2 + radius);
    }
  }

  @Override
  public void attach () {
    avatarReceiver.attach();
    emojiStatusHelper.attach();
  }

  @Override
  public void detach () {
    avatarReceiver.detach();
    emojiStatusHelper.detach();
  }

  @Override
  public void performDestroy () {
    emojiStatusHelper.performDestroy();
    setChat(null);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    final int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    if (width > 0 && chat != null) {
      chat.layout(width);
    }
    layoutReceiver();
  }

  public void setChat (DoubleTextWrapper chat) {
    if (this.chat == chat) {
      return;
    }

    if (this.chat != null) {
      this.chat.getViewProvider().detachFromView(this);
    }

    this.chat = chat;
    if (chat != null) {
      setPreviewChatId(null, chat.getChatId(), null);
    } else {
      clearPreviewChat();
    }

    if (chat != null) {
      final int currentWidth = getMeasuredWidth();
      if (currentWidth != 0) {
        chat.layout(currentWidth);
      }
      chat.getViewProvider().attachToView(this);
    }

    invalidateEmojiStatusReceiver(null, null);
    requestFile();
    invalidate();
    if (chat != null) {
      chat.onAttachToView();
    }
  }

  private void requestFile () {
    if (chat != null) {
      avatarReceiver.requestMessageSender(tdlib, chat.getSenderId(), AvatarReceiver.Options.NONE);
    } else {
      avatarReceiver.clear();
    }
  }

  @Override
  public boolean invalidateContent (Object cause) {
    if (this.chat == cause) {
      requestFile();
      return true;
    }
    return false;
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    if (chat != null) {
      chat.getTargetBounds(targetView, outRect);
    }
  }

  @Override
  protected void onDraw (Canvas c) {
    if (chat == null) {
      return;
    }

    layoutReceiver();
    if (avatarReceiver.needPlaceholder()) {
      avatarReceiver.drawPlaceholder(c);
    }
    avatarReceiver.draw(c);

    chat.draw(this, avatarReceiver, c, emojiStatusHelper.emojiStatusReceiver);

    if (checkboxVisible) {
      c.save();
      final float lineSize = Screen.dp(2f);
      float cx = getWidth() - Screen.dp(26);
      float cy = getHeight() / 2f;
      float r2 = Screen.dp(10f);
      c.drawCircle(cx, cy, r2, Paints.fillingPaint(Theme.radioFillingColor()));

      float x1 = cx - Screen.dp(2);
      float y1 = cy + Screen.dp(5f);
      float w2 = Screen.dp(11);// * checkedFactor.getFloatValue();
      float h1 = Screen.dp(5.5f);// * checkedFactor.getFloatValue();

      c.rotate(-45f, x1, y1);
      c.drawRect(x1, y1 - h1, x1 + lineSize, y1, Paints.fillingPaint(Theme.radioCheckColor()));
      c.drawRect(x1, y1 - lineSize, x1 + w2, y1, Paints.fillingPaint(Theme.radioCheckColor()));
      c.restore();
    }
  }

  private boolean checkboxVisible = false;

  public TdApi.MessageSender getSenderId () {
    if (chat == null) return null;
    return chat.getSenderId();
  }

  public void setCheckboxIconVisible (boolean checkboxVisible) {
    if (chat == null) return;
    this.checkboxVisible = checkboxVisible;
    chat.setAdminSignVisible(!checkboxVisible, true);
    invalidate();
  }

  @Override
  public void invalidateEmojiStatusReceiver (Text text, @Nullable TextMedia specificMedia) {
    if (emojiStatusHelper != null && chat != null) {
      chat.requestEmojiStatusReceiver(emojiStatusHelper.emojiStatusReceiver);
    }
  }
}
