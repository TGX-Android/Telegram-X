/**
 * File created on 29/08/15 at 21:49
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesAdapter;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.td.ChatId;

public class TGMessageBotInfo extends TGMessage {
  private TextWrapper titleWrapper;
  private TextWrapper textWrapper;

  private Drawable icon;

  public TGMessageBotInfo (MessagesManager context, long chatId, TdApi.FormattedText description) {
    super(context, TD.newFakeMessage(chatId, context.controller().tdlib().sender(chatId), new TdApi.MessageText(description, null)));

    if (!tdlib().isRepliesChat(ChatId.fromUserId(getSender().getUserId()))) {
      String text = Lang.getString(R.string.WhatThisBotCanDo);
      this.titleWrapper = new TextWrapper(text, getTextStyleProvider(), getTextColorSet(), null).addTextFlags(Text.FLAG_ALL_BOLD);
      this.titleWrapper.setViewProvider(currentViews);
    }

    this.textWrapper = new TextWrapper(description.text, getTextStyleProvider(), getTextColorSet(), TextEntity.valueOf(tdlib, description, null)).setClickCallback(clickCallback());
    this.textWrapper.setViewProvider(currentViews);

    icon = Drawables.get(context.controller().context().getResources(), R.drawable.baseline_help_24);
  }

  @Override
  protected boolean headerDisabled () {
    return true;
  }

  @Override
  protected boolean useLargeHeight () {
    return true;
  }

  @Override
  public boolean needRefreshViewCount () {
    return false;
  }

  @Override
  public boolean canMarkAsViewed () {
    return false;
  }

  @Override
  protected void buildContent (int maxWidth) {
    int maxTextWidth = width - xPaddingRight - xContentLeft;
    if (titleWrapper != null) {
      titleWrapper.prepare(maxTextWidth);
    }
    textWrapper.prepare(maxTextWidth);
  }

  private MessagesAdapter boundAdapter;

  public void setBoundAdapter (MessagesAdapter adapter) {
    this.boundAdapter = adapter;
  }

  @Override
  protected int getContentHeight () {
    return textWrapper.getHeight() + (titleWrapper != null ? titleWrapper.getHeight() + Screen.dp(3f) : 0);
  }

  @Override
  protected int getContentWidth () {
    return Math.max(titleWrapper != null ? titleWrapper.getWidth() : 0, textWrapper.getWidth());
  }

  @Override
  protected boolean centerBubble () {
    return true;
  }

  private int cachedHeight;

  public int getCachedHeight () {
    return cachedHeight;
  }

  @Override
  public int getHeight () {
    int totalHeight = getContentHeight() + Screen.dp(6f) + xAvatarRadius * 2 + xPaddingBottom - xContentOffset - xPaddingTop + xHeaderPadding;

    final View view = findCurrentView();

    if (view == null) {
      return cachedHeight = totalHeight;
    }

    ViewParent parent = view.getParent();

    if (parent == null) {
      return cachedHeight = totalHeight;
    }

    int height = ((View) parent).getMeasuredHeight();

    if (height == 0 || totalHeight >= height) {
      return cachedHeight = totalHeight;
    }

    int padding = (int) ((float) (height - totalHeight - xPaddingBottom - xHeaderPadding - Screen.dp(6f)) * .5f);

    if (boundAdapter != null) {
      int messageCount = boundAdapter.getMessageCount();
      for (int i = 0; i < messageCount; i++) {
        TGMessage msg = boundAdapter.getMessage(i);
        if (msg != null && msg.getMessage().id != 0) {
          padding -= msg.getHeight();
          if (padding <= 0) {
            return cachedHeight = totalHeight;
          }
        }
      }
    }

    return cachedHeight = totalHeight + padding;
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    int paddingTop = Screen.dp(6f);
    if (!useBubbles()) {
      Drawables.draw(c, icon, xAvatarCenterX - icon.getMinimumWidth() / 2, xHeaderPadding + paddingTop, Paints.getIconGrayPorterDuffPaint());
    }

    if (useBubbles()) {
      startX = getActualLeftContentEdge() + getBubbleContentPadding();
    } else {
      startY = xHeaderPadding + paddingTop + Screen.dp(3f);
    }

    if (titleWrapper != null) {
      titleWrapper.draw(c, startX, startY, null, 1f);
      startY += titleWrapper.getHeight() + Screen.dp(3f);
    }
    textWrapper.draw(c, startX, startY, null, 1f);
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    return super.onTouchEvent(view, e) ||
      (textWrapper.onTouchEvent(view, e));
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    return textWrapper.performLongPress(view) || res;
  }
}
