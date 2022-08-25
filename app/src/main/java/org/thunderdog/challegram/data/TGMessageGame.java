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
 * File created on 04/11/2016
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class TGMessageGame extends TGMessage implements MediaWrapper.OnClickListener {
  private final TdApi.Game game;

  private @Nullable TextWrapper title;

  private TdApi.FormattedText currentText;
  private @Nullable TextWrapper text;

  public TGMessageGame (MessagesManager context, TdApi.Message msg, TdApi.Game game) {
    super(context, msg);
    this.game = game;
    setText();
  }

  private int getGameMaxWidth () {
    return getSmallestMaxContentWidth() - getImagePaddingLeft();
  }

  @Override
  protected int getBubbleContentPadding () {
    return xBubblePadding + xBubblePaddingSmall;
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().downloadAutomatically(type);
    }
  }

  @Override
  protected void onMessageIdChanged (long oldMessageId, long newMessageId, boolean success) {
    if (mediaWrapper != null) {
      mediaWrapper.updateMessageId(oldMessageId, newMessageId, success);
    }
  }

  @Override
  protected void onMessageAttachedToView (@NonNull MessageView view, boolean attached) {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().notifyInvalidateTargetsChanged();
    }
  }

  @Override
  protected void buildContent (int contentMaxWidth) {
    int maxWidth = getGameMaxWidth();
    int maxHeight = maxWidth * 2;

    if (title != null) {
      title.prepare(maxWidth);
    }

    if (text != null) {
      text.prepare(maxWidth);
    }

    buildImageOrGif(maxWidth, maxHeight);
  }

  @Override
  public void requestSingleTextMedia (ComplexReceiver textMediaReceiver, int displayMediaKey) {
    if (text != null) {
      text.requestSingleMedia(textMediaReceiver, displayMediaKey);
    } else {
      textMediaReceiver.clearReceivers(displayMediaKey);
    }
  }

  @Override
  public void requestTextMedia (ComplexReceiver textMediaReceiver) {
    if (text != null) {
      text.requestMedia(textMediaReceiver);
    } else {
      textMediaReceiver.clear();
    }
  }

  private boolean setText () {
    final boolean titleChanged = !StringUtils.isEmpty(game.title) && (text == null || !text.getText().equals(game.title));
    if (titleChanged) {
      title = new TextWrapper(game.title, getTextStyleProvider(), getChatAuthorColorSet(), null).addTextFlags(Text.FLAG_ALL_BOLD).setClickCallback(clickCallback());
    }

    final TdApi.FormattedText text;
    if (!Td.isEmpty(game.text)) {
      text = game.text;
    } else {
      text = new TdApi.FormattedText(game.description, Text.findEntities(game.description, Text.ENTITY_FLAGS_EXTERNAL));
    }

    boolean descriptionChanged = false;
    if (Td.isEmpty(text)) {
      if (currentText != null) {
        this.text = null;
        currentText = null;
        descriptionChanged = true;
      }
    } else if (currentText == null || !Td.equalsTo(currentText, text)) {
      descriptionChanged = true;
      currentText = text;
      this.text = new TextWrapper(text.text, getTextStyleProvider(), getTextColorSet(), TextEntity.valueOf(tdlib, text, openParameters())).setClickCallback(clickCallback());
    }

    if (descriptionChanged) {
      invalidateTextMediaReceiver();
    }

    return titleChanged || descriptionChanged;
  }

  @Override
  protected boolean updateMessageContent (TdApi.Message message, TdApi.MessageContent newContent, boolean isBottomMessage) {
    TdApi.MessageGame messageGame = (TdApi.MessageGame) newContent;
    TdApi.Game newGame = messageGame.game;
    game.text = newGame.text;
    game.title = newGame.title;
    game.description = newGame.description;
    if (setText()) {
      rebuildContent();
      return true;
    }
    return false;
  }

  private MediaWrapper mediaWrapper;

  private void buildImageOrGif (int maxWidth, int maxHeight) {
    if (mediaWrapper == null) {
      if (game.animation != null) {
        mediaWrapper = new MediaWrapper(context(), tdlib, game.animation, msg.chatId, msg.id, this, false);
      } else {
        mediaWrapper = new MediaWrapper(context(), tdlib, game.photo, msg.chatId, msg.id, this, false);
      }
      mediaWrapper.setViewProvider(currentViews);
      // mediaWrapper.setNeedRound(true, true);
      mediaWrapper.setOnClickListener(this);
    }

    int photoWidth = mediaWrapper.getContentWidth();
    int photoHeight = mediaWrapper.getContentHeight();

    float ratio = Math.min((float) maxWidth / (float) photoWidth, (float) maxHeight / (float) photoHeight);
    int cellWidth = (int) (ratio * photoWidth);
    int cellHeight = (int) (ratio * photoHeight);

    mediaWrapper.buildContent(cellWidth, cellHeight);
  }

  @Override
  public boolean needGifReceiver () {
    return mediaWrapper.getTargetGifFile() != null;
  }

  @Override
  protected int getContentWidth () {
    return getImagePaddingLeft() + Math.max(text != null ? text.getWidth() : 0, mediaWrapper.getCellWidth());
  }

  private int textHeight () {
    int textHeight = 0;
    if (title != null) {
      textHeight += getTextPaddingTop() + title.getHeight();
    }
    if (text != null) {
      textHeight += (textHeight != 0 ? Screen.dp(4f) : getTextPaddingTop()) + text.getHeight();
    }
    if (textHeight > 0) {
      textHeight += getTextPaddingBottom();
    }
    return textHeight;
  }

  @Override
  protected int getContentHeight () {
    return mediaWrapper.getCellHeight() + textHeight();
  }

  @Override
  public boolean needImageReceiver () {
    return true;
  }

  @Override
  public int getImageContentRadius (boolean isPreview) {
    return Screen.dp(Theme.getImageRadius());
  }

  private int getGameImageLeft () {
    return getContentX() + getImagePaddingLeft();
  }

  private int getGameImageTop () {
    return getContentY() + textHeight();
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    mediaWrapper.requestPreview(receiver);
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    mediaWrapper.requestImage(receiver);
  }

  @Override
  public void requestGif (GifReceiver receiver) {
    mediaWrapper.requestGif(receiver);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    mediaWrapper.draw(view, c, getGameImageLeft(), getGameImageTop(), preview, receiver, 1f);
    drawGameContent(view, c, startX, startY, maxWidth);
  }

  private static int getTextPaddingBottom () {
    return Screen.dp(7f);
  }

  private int getTextPaddingTop () {
    return text != null || title != null ? Screen.dp(2f) : 0;
  }

  private static int getImagePaddingLeft () {
    return Screen.dp(12f);
  }

  private void drawGameContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    RectF rectF = Paints.getRectF();
    int lineWidth = Screen.dp(3f);
    rectF.set(startX, startY, startX + lineWidth, startY + getContentHeight());
    c.drawRoundRect(rectF, lineWidth / 2, lineWidth / 2, Paints.fillingPaint(getVerticalLineColor()));

    final int left = startX + getImagePaddingLeft();
    int textY = 0;

    if (title != null) {
      textY += getTextPaddingTop();
      title.draw(c, left, startY + textY, null, 1f);
      textY += title.getHeight();
    }

    if (text != null) {
      textY += textY != 0 ? Screen.dp(4f) : getTextPaddingTop();
      text.draw(c, left, startY + textY, null, 1f, view.getTextMediaReceiver());
    }
  }

  @Override
  protected void onMessageContainerDestroyed () {
    if (text != null) {
      text.performDestroy();
    }
  }

  @Override
  public boolean onClick (View view, MediaWrapper wrapper) {
    if (inlineKeyboard != null && !inlineKeyboard.isEmpty()) {
      inlineKeyboard.openGame(view);
      return true;
    }
    return false;
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    return mediaWrapper.performLongPress(view) || res;
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (super.onTouchEvent(view, e) ||
      mediaWrapper.onTouchEvent(view, e))
      return true;
    int textY;
    if (title != null)
      textY = getTextPaddingTop() + title.getHeight() + Screen.dp(4f);
    else
      textY = getTextPaddingTop();
    return text != null && text.onTouchEvent(view, e);
  }
}
