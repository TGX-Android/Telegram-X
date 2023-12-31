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
 * File created on 06/11/2023
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;

import me.vkryl.td.Td;

public class TGMessageGift extends TGMessageGiveawayBase {
  private final TdApi.MessagePremiumGiftCode msgContent;

  public TGMessageGift(MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessagePremiumGiftCode premiumGiftCode) {
    super(manager, msg);
    this.msgContent = premiumGiftCode;
  }

  private int giftDrawableY;
  private int contentDrawY;
  private Content content;

  protected int onBuildContent (int maxWidth) {
    int contentHeight = Screen.dp(30);
    giftDrawableY = contentHeight;

    /* * */

    contentHeight += Screen.dp(72); // gift drawable height
    contentHeight += Screen.dp(25); // gift drawable margin

    /* * */

    contentDrawY = contentHeight;
    content = new Content(this, maxWidth);

    if (msgContent != null) {
      content.add(Lang.boldify(Lang.getString(R.string.GiveawayCongratulations)));
      content.padding(Screen.dp(6));
      content.add(Lang.getString(msgContent.isFromGiveaway ? R.string.GiveawayYouWon : R.string.GiveawayYouGetGift));
      if (msgContent.creatorId != null) {
        content.padding(Screen.dp(6));
        content.add(new ContentBubbles(this, maxWidth)
          .setOnClickListener(this::onBubbleClick)
          .addChatId(Td.getSenderId(msgContent.creatorId)));
      }

      content.padding(Screen.dp(BLOCK_MARGIN));
      content.add(Strings.buildMarkdown(this, Lang.plural(R.string.xGiveawayPrizeReceivedInfo, msgContent.monthCount)));
    }

    contentHeight += content.getHeight();

    invalidateGiveawayReceiver();
    return contentHeight;
  }


  private void onBubbleClick (TdApi.MessageSender senderId) {
    tdlib.ui().openChat(controller(), Td.getSenderId(senderId), new TdlibUi.ChatOpenParameters()
      .keepStack().removeDuplicates().openProfileInCaseOfPrivateChat());
  }

  @Override
  public void requestGiveawayAvatars (ComplexReceiver complexReceiver, boolean isUpdate) {
    content.requestFiles(complexReceiver);
  }

  @Override
  protected String getButtonText () {
    return Lang.getString(R.string.OpenGiftLink);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    super.drawContent(view, c, startX, startY, maxWidth);

    c.save();
    c.translate(startX, startY);

    final int contentWidth = getContentWidth();
    final float contentCenterX = contentWidth / 2f;

    final Drawable giftIcon = view.getSparseDrawable(R.drawable.baseline_gift_72, ColorId.NONE);
    if (giftIcon != null) {
      Drawables.draw(c, giftIcon, contentCenterX - giftIcon.getMinimumWidth() / 2f, giftDrawableY, PorterDuffPaint.get(ColorId.icon));
    }

    c.restore();

    content.draw(c, view, startX, startY + contentDrawY);
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (content != null && content.onTouchEvent(view, e)) {
      return true;
    }
    return super.onTouchEvent(view, e);
  }



  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {

  }

  @Override
  public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return false;
  }
}
