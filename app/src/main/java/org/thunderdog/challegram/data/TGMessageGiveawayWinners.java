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
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.text.Counter;

import me.vkryl.td.Td;

public class TGMessageGiveawayWinners extends TGMessageGiveawayBase implements TGInlineKeyboard.ClickListener {
  private final static int BLOCK_MARGIN = 18;

  private final TdApi.MessagePremiumGiveawayWinners giveawayWinners;

  public TGMessageGiveawayWinners (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessagePremiumGiveawayWinners giveawayWinners) {
    super(manager, msg);
    this.giveawayWinners = giveawayWinners;
  }

  private final RectF outlineCounterRect = new RectF();
  private final RectF backgroundCounterRect = new RectF();
  private int giftDrawableY;

  private Counter participantsCounter;
  private int participantsCounterY;

  private int contentDrawY;
  private Content content;

  @Override
  protected int onBuildContent (int maxWidth) {
    int contentHeight = Screen.dp(30);
    giftDrawableY = contentHeight;

    /* * */

    participantsCounterY = giftDrawableY + Screen.dp(73.5f);
    participantsCounter = new Counter.Builder().allBold(true).textSize(11).noBackground().textColor(ColorId.text).build();
    participantsCounter.setCount(giveawayWinners.winnerCount, false, "x" + giveawayWinners.winnerCount, false);
    backgroundCounterRect.set(
      maxWidth / 2f - participantsCounter.getWidth() / 2f - Screen.dp(8),
      participantsCounterY - Screen.dp(23 / 2f),
      maxWidth / 2f + participantsCounter.getWidth() / 2f + Screen.dp(8),
      participantsCounterY + Screen.dp(23 / 2f)
    );
    outlineCounterRect.set(backgroundCounterRect);
    outlineCounterRect.inset(-Screen.dp(3), -Screen.dp(3));


    contentHeight += Screen.dp(72); // gift drawable height
    contentHeight += Screen.dp(35); // gift drawable margin

    /* * */

    contentDrawY = contentHeight;
    content = new Content(this, maxWidth);

    content.add(Lang.boldify(Lang.getString(R.string.GiveawayWinnersSelected)));
    content.padding(Screen.dp(6));
    content.add(Lang.pluralBold(R.string.xGiveawayWinnersSelectedInfo, giveawayWinners.winnerCount));

    content.padding(Screen.dp(BLOCK_MARGIN));
    content.add(Lang.boldify(Lang.getString(R.string.GiveawayWinners)));
    content.padding(Screen.dp(6));
    content.add(new ContentBubbles(this, maxWidth)
      .setOnClickListener(this::onBubbleClick)
      .addChatIds(giveawayWinners.winnerUserIds));

    content.padding(Screen.dp(BLOCK_MARGIN));
    content.add(Lang.getString(R.string.GiveawayAllWinnersReceivedLinks));

    contentHeight += content.getHeight();

    invalidateGiveawayReceiver();
    return contentHeight;
  }

  @Override
  protected String getButtonText () {
    return Lang.getString(R.string.GiveawayLearnMore);
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
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    super.drawContent(view, c, startX, startY, maxWidth);

    c.save();
    c.translate(startX, startY);

    final int contentWidth = getContentWidth();
    final float contentCenterX = contentWidth / 2f;

    final Drawable giftIcon = view.getSparseDrawable(giveawayWinners != null ? R.drawable.baseline_party_popper_72 : R.drawable.baseline_gift_72, ColorId.NONE);
    if (giftIcon != null) {
      Drawables.draw(c, giftIcon, contentCenterX - giftIcon.getMinimumWidth() / 2f, giftDrawableY, PorterDuffPaint.get(ColorId.icon));
    }

    if (participantsCounter != null) {
      final float cor = Math.min(outlineCounterRect.width(), outlineCounterRect.height()) / 2f;
      final float cbr = Math.min(backgroundCounterRect.width(), backgroundCounterRect.height()) / 2f;
      c.drawRoundRect(outlineCounterRect, cor, cor, Paints.fillingPaint(Theme.getColor(useBubbles() ? ColorId.filling : ColorId.background)));
      c.drawRoundRect(backgroundCounterRect, cbr, cbr, Paints.fillingPaint(getCounterBackgroundColor()));
      participantsCounter.draw(c, contentCenterX, participantsCounterY, Gravity.CENTER, 1f);
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
