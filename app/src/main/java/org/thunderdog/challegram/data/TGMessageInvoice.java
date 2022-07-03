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
 * File created on 11/02/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.core.CurrencyUtils;

public class TGMessageInvoice extends TGMessage {
  private static final float MIN_RATIO = .5f;
  private static final float MAX_RATIO = 1.5f;
  private static float TEXT_MARGIN = 10f;
  private static float TEXT_SMALL_MARGIN = 2f;

  private @Nullable MediaWrapper mediaWrapper;
  private TextWrapper titleWrapper;
  private TextWrapper descriptionWrapper;
  private TextWrapper invoiceInfoWrapper;

  public TGMessageInvoice (MessagesManager context, TdApi.Message msg, TdApi.MessageInvoice invoice) {
    super(context, msg);
    setPhoto(invoice.photo);
    setText(invoice);
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().downloadAutomatically(type);
    }
  }

  @Override
  public boolean onMessageClick (MessageView v, MessagesController c) {
    return super.onMessageClick(v, c); // open form by click?
  }

  private void setPhoto (@Nullable TdApi.Photo photo) {
    if (mediaWrapper != null) {
      mediaWrapper.destroy();
    }
    if (photo != null) {
      mediaWrapper = new MediaWrapper(context(), tdlib, photo, msg.chatId, msg.id, this, false);
    } else {
      mediaWrapper = null;
    }
  }

  private void setText (TdApi.MessageInvoice invoice) {
    titleWrapper = new TextWrapper(invoice.title, Paints.robotoStyleProvider(14f), getLinkColorSet(), null).setTextFlagEnabled(Text.FLAG_ALL_BOLD, true);
    descriptionWrapper = new TextWrapper(invoice.description, Paints.robotoStyleProvider(14f), getTextColorSet(), null);
    invoiceInfoWrapper = buildInvoiceInfoWrapper(invoice);
  }

  private TextWrapper buildInvoiceInfoWrapper (TdApi.MessageInvoice invoice) {
    CharSequence text = Lang.getStringBold(invoice.isTest ? R.string.InvoiceFmtTest : R.string.InvoiceFmt, CurrencyUtils.buildAmount(invoice.currency, invoice.totalAmount));
    TdApi.TextEntity[] entities = TD.toEntities(text, false);
    return new TextWrapper(text.toString(), Paints.robotoStyleProvider(mediaWrapper != null ? 13f : 14f), getTextColorSet(), TextEntity.valueOf(tdlib, text.toString(), entities, null));
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
  protected void onMessageContainerDestroyed () {
    setPhoto(null);
  }

  @Override
  public boolean needImageReceiver () {
    return mediaWrapper != null;
  }

  @Override
  public void requestPreview (DoubleImageReceiver receiver) {
    if (mediaWrapper != null) {
      mediaWrapper.requestPreview(receiver);
    }
  }

  @Override
  public void requestImage (ImageReceiver receiver) {
    if (mediaWrapper != null) {
      mediaWrapper.requestImage(receiver);
    }
  }

  @Override
  protected void buildContent (int origMaxWidth) {
    if (mediaWrapper != null) {
      int maxWidth = getSmallestMaxContentWidth() - getImagePaddingLeft();
      int maxHeight = maxWidth * 2;

      int photoWidth = mediaWrapper.getContentWidth();
      int photoHeight = mediaWrapper.getContentHeight();

      float ratio = Math.min((float) maxWidth / (float) photoWidth, (float) maxHeight / (float) photoHeight);
      int cellWidth = (int) (ratio * photoWidth);
      int cellHeight = (int) (ratio * photoHeight);

      mediaWrapper.buildContent(cellWidth, cellHeight);
    }

    int maxTextWidth = (useBubbles() && mediaWrapper != null) ? mediaWrapper.getCellWidth() - xBubblePadding * 2 : getRealContentMaxWidth();
    titleWrapper.prepare(maxTextWidth);
    descriptionWrapper.prepare(maxTextWidth);
    invoiceInfoWrapper.prepare(maxTextWidth);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    int alterStartY = startY;

    if (mediaWrapper != null) {
      mediaWrapper.draw(view, c, startX, startY, preview, receiver, 1f);
      alterStartY += mediaWrapper.getCellHeight() + Screen.dp(TEXT_MARGIN);
    }

    drawText(view, c, startX, alterStartY, startY, maxWidth);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    drawText(view, c, startX, startY, startY, maxWidth);
  }

  private void drawText (MessageView view, Canvas c, int startX, int startY, int origStartY, int maxWidth) {
    titleWrapper.draw(c, startX, startY);
    startY += titleWrapper.getHeight() + Screen.dp(TEXT_SMALL_MARGIN);

    descriptionWrapper.draw(c, startX, startY);
    startY += descriptionWrapper.getHeight();

    if (mediaWrapper != null) {
      float inset = 4f;
      float margin = 4f;

      RectF boundRect = Paints.getRectF();
      boundRect.set(startX + Screen.dp(margin), origStartY + Screen.dp(margin), startX + Screen.dp(margin) + invoiceInfoWrapper.getWidth() + Screen.dp(inset * 4), origStartY + Screen.dp(margin) + invoiceInfoWrapper.getHeight() + Screen.dp(inset * 2));
      c.drawRoundRect(boundRect, Screen.dp(margin), Screen.dp(margin), Paints.fillingPaint(0x80000000));
      invoiceInfoWrapper.draw(c, startX + Screen.dp(margin) + Screen.dp(inset * 2), origStartY + Screen.dp(margin) + Screen.dp(inset));
    } else {
      invoiceInfoWrapper.draw(c, startX, startY);
    }
  }

  @Override
  protected int getContentWidth () {
    if (mediaWrapper != null) {
      return mediaWrapper.getCellWidth();
    }

    return Math.max(titleWrapper.getWidth(), descriptionWrapper.getWidth());
  }

  @Override
  protected int getContentHeight () {
    return (mediaWrapper != null ? mediaWrapper.getCellHeight() : (Screen.dp(TEXT_SMALL_MARGIN))) + Screen.dp(TEXT_MARGIN) + titleWrapper.getHeight() + Screen.dp(TEXT_SMALL_MARGIN) + descriptionWrapper.getHeight();
  }

  private static int getImagePaddingLeft () {
    return Screen.dp(12f);
  }
}
