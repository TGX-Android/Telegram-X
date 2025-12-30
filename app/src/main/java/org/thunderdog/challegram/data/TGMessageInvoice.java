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
 * File created on 11/02/2017
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.ui.PaymentFormController;
import org.thunderdog.challegram.util.text.TextColorSets;
import org.thunderdog.challegram.util.text.TextWrapper;

import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;

public class TGMessageInvoice extends TGMessage implements TGInlineKeyboard.ClickListener {

  private final TdApi.MessageInvoice invoice;
  private MediaWrapper mediaWrapper;

  private TextWrapper titleWrapper;
  private TextWrapper descriptionWrapper;
  private TextWrapper priceWrapper;

  private TGInlineKeyboard keyboard;

  private int contentWidth;
  private int contentHeight;

  public TGMessageInvoice (MessagesManager context, TdApi.Message msg, TdApi.MessageInvoice invoice) {
    super(context, msg);
    this.invoice = invoice;
    if (invoice.productInfo != null && invoice.productInfo.photo != null) {
      setPhoto(invoice.productInfo.photo);
    }
  }

  @Override
  public void autoDownloadContent (TdApi.ChatType type) {
    if (mediaWrapper != null) {
      mediaWrapper.getFileProgress().downloadAutomatically(type);
    }
  }

  private void setPhoto (@Nullable TdApi.Photo photo) {
    if (mediaWrapper != null) {
      mediaWrapper.destroy();
    }
    if (photo != null) {
      mediaWrapper = new MediaWrapper(context(), tdlib, photo, msg.chatId, msg.id, this, false);
      mediaWrapper.setViewProvider(currentViews);
    } else {
      mediaWrapper = null;
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
  protected void onMessageContainerDestroyed () {
    setPhoto(null);
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
  protected void buildContent (int maxWidth) {
    contentWidth = maxWidth;
    contentHeight = 0;

    int textMaxWidth = maxWidth - Screen.dp(24f);

    // Build title from productInfo
    if (invoice.productInfo != null && !StringUtils.isEmpty(invoice.productInfo.title)) {
      titleWrapper = new TextWrapper(invoice.productInfo.title, getTextStyleProvider(), TextColorSets.Regular.NORMAL);
      titleWrapper.prepare(textMaxWidth);
      contentHeight += titleWrapper.getHeight() + Screen.dp(8f);
    }

    // Build description from productInfo
    if (invoice.productInfo != null && invoice.productInfo.description != null && !StringUtils.isEmpty(invoice.productInfo.description.text)) {
      descriptionWrapper = new TextWrapper(invoice.productInfo.description.text, getTextStyleProvider(), TextColorSets.Regular.NORMAL);
      descriptionWrapper.prepare(textMaxWidth);
      contentHeight += descriptionWrapper.getHeight() + Screen.dp(8f);
    }

    // Build price
    String priceText = CurrencyUtils.buildAmount(invoice.currency, invoice.totalAmount);
    if (invoice.isTest) {
      priceText = Lang.getString(R.string.PaymentTestPrefix, priceText);
    }
    priceWrapper = new TextWrapper(priceText, getTextStyleProvider(), TextColorSets.Regular.NORMAL);
    priceWrapper.prepare(textMaxWidth);
    contentHeight += priceWrapper.getHeight() + Screen.dp(16f);

    // Build photo
    if (mediaWrapper != null) {
      mediaWrapper.buildContent(maxWidth, maxWidth);
      contentHeight += mediaWrapper.getCellHeight() + Screen.dp(12f);
    }

    // Build button
    String buttonText;
    if (invoice.receiptMessageId != 0) {
      buttonText = Lang.getString(R.string.ViewReceipt);
    } else {
      buttonText = Lang.getString(R.string.PaymentPay, priceText);
    }

    if (keyboard == null) {
      keyboard = new TGInlineKeyboard(this, false);
      keyboard.setViewProvider(currentViews);
    }
    keyboard.setCustom(0, buttonText, maxWidth, false, this);
    contentHeight += TGInlineKeyboard.getButtonHeight() + Screen.dp(8f);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth, Receiver preview, Receiver receiver) {
    int y = startY;

    // Draw photo first
    if (mediaWrapper != null) {
      mediaWrapper.draw(view, c, startX, y, preview, receiver, 1f);
      y += mediaWrapper.getCellHeight() + Screen.dp(12f);
    }

    int textX = startX + Screen.dp(12f);

    // Draw title
    if (titleWrapper != null) {
      titleWrapper.draw(c, textX, y, null, 1f);
      y += titleWrapper.getHeight() + Screen.dp(8f);
    }

    // Draw description
    if (descriptionWrapper != null) {
      descriptionWrapper.draw(c, textX, y, null, 1f);
      y += descriptionWrapper.getHeight() + Screen.dp(8f);
    }

    // Draw price
    if (priceWrapper != null) {
      priceWrapper.draw(c, textX, y, null, 1f);
      y += priceWrapper.getHeight() + Screen.dp(16f);
    }

    // Draw keyboard
    if (keyboard != null) {
      keyboard.draw(view, c, startX, y);
    }
  }

  @Override
  protected int getContentWidth () {
    return contentWidth;
  }

  @Override
  protected int getContentHeight () {
    return contentHeight;
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (keyboard != null && keyboard.onTouchEvent(view, e)) {
      return true;
    }
    return super.onTouchEvent(view, e);
  }

  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    if (invoice.receiptMessageId != 0) {
      // View receipt - TODO: implement receipt viewing
      UI.showToast(R.string.ViewReceipt, Toast.LENGTH_SHORT);
    } else {
      // Open payment form
      TdApi.InputInvoiceMessage inputInvoice = new TdApi.InputInvoiceMessage(msg.chatId, msg.id);
      UI.showToast(R.string.LoadingPaymentForm, Toast.LENGTH_SHORT);
      tdlib.send(new TdApi.GetPaymentForm(inputInvoice, null), (result, error) -> {
        UI.post(() -> {
          if (error != null) {
            UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
          } else {
            TdApi.PaymentForm paymentForm = (TdApi.PaymentForm) result;
            if (paymentForm.type instanceof TdApi.PaymentFormTypeRegular) {
              PaymentFormController formController = new PaymentFormController(controller().context(), tdlib);
              formController.setArguments(new PaymentFormController.Args(paymentForm, inputInvoice, 0));
              controller().navigateTo(formController);
            } else if (paymentForm.type instanceof TdApi.PaymentFormTypeStars) {
              TdApi.PaymentFormTypeStars starsType = (TdApi.PaymentFormTypeStars) paymentForm.type;
              showStarsPaymentConfirmation(paymentForm, inputInvoice, starsType.starCount);
            } else {
              UI.showToast(R.string.PaymentUnsupportedType, Toast.LENGTH_SHORT);
            }
          }
        });
      });
    }
  }

  private void showStarsPaymentConfirmation(TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice, long starCount) {
    String message = Lang.getString(R.string.StarsPayConfirmMessage, starCount);
    controller().showOptions(
      message,
      new int[] { R.id.btn_done, R.id.btn_cancel },
      new String[] { Lang.getString(R.string.StarsPayConfirm, starCount), Lang.getString(R.string.Cancel) },
      new int[] { ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL },
      new int[] { R.drawable.baseline_star_24, R.drawable.baseline_cancel_24 },
      (view, optionId) -> {
        if (optionId == R.id.btn_done) {
          sendStarsPayment(paymentForm, inputInvoice);
        }
        return true;
      }
    );
  }

  private void sendStarsPayment(TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice) {
    UI.showToast(R.string.PaymentProcessing, Toast.LENGTH_SHORT);
    tdlib.send(new TdApi.SendPaymentForm(inputInvoice, paymentForm.id, "", "", null, 0), (result, error) -> {
      UI.post(() -> {
        if (error != null) {
          UI.showToast(Lang.getString(R.string.StarsPaymentFailed, TD.toErrorString(error)), Toast.LENGTH_SHORT);
        } else {
          UI.showToast(R.string.StarsPaymentSuccess, Toast.LENGTH_SHORT);
        }
      });
    });
  }
}
