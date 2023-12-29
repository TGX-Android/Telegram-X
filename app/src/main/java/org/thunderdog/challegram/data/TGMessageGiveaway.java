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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.user.BubbleWrapView;
import org.thunderdog.challegram.component.user.BubbleWrapView2;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextStyleProvider;
import org.thunderdog.challegram.util.text.TextWrapper;

import java.util.concurrent.TimeUnit;

public class TGMessageGiveaway extends TGMessage implements TGInlineKeyboard.ClickListener {
  private final static int BLOCK_MARGIN = 18;

  private final TdApi.MessagePremiumGiveaway giveawayContent;

  public TGMessageGiveaway (MessagesManager manager, TdApi.Message msg, TdApi.MessagePremiumGiveaway giveawayContent) {
    super(manager, msg);
    this.giveawayContent = giveawayContent;
    this.participantsCounter = new Counter.Builder()
      .allBold(true)
      .textSize(11)
      .noBackground()
      .textColor(ColorId.text)
      .build();

    participantsCounter.setCount(giveawayContent.winnerCount, false, "x" + giveawayContent.winnerCount, false);
  }

  private TGInlineKeyboard rippleButton;
  private final Counter participantsCounter;
  private final RectF outlineCounterRect = new RectF();
  private final RectF backgroundCounterRect = new RectF();


  private int giftDrawableY;
  private int usersCounterY;

  private Block giveawayPrizeBlock;
  private int giveawayPrizeBlockY;

  private Block giveawayParticipantsBlock;
  private int giveawayParticipantsBlockY;

  private BubbleWrapView2 giveawayParticipantsBubbles;
  private int giveawayParticipantsBubblesY;

  private Block giveawayFinishTimeBlock;
  private int giveawayFinishTimeBlockY;

  private int rippleButtonY;
  private int contentHeight = 0;


  @Override
  protected void buildContent (int maxWidth) {
    contentHeight = Screen.dp(30);
    giftDrawableY = contentHeight;
    usersCounterY = giftDrawableY + Screen.dp(73.5f);

    backgroundCounterRect.set(
      maxWidth / 2f - participantsCounter.getWidth() / 2f - Screen.dp(8),
      usersCounterY - Screen.dp(23 / 2f),
      maxWidth / 2f + participantsCounter.getWidth() / 2f + Screen.dp(8),
      usersCounterY + Screen.dp(23 / 2f)
    );
    outlineCounterRect.set(backgroundCounterRect);
    outlineCounterRect.inset(-Screen.dp(3), -Screen.dp(3));
    contentHeight += Screen.dp(72); // gift drawable height
    contentHeight += Screen.dp(35); // gift drawable margin



    /* * */

    giveawayPrizeBlockY = contentHeight;
    giveawayPrizeBlock = new Block(this,
      Lang.boldify(Lang.getString(R.string.GiveawayPrizes)),
      Lang.pluralBold(R.string.xGiveawayPrizePremiumInfo, giveawayContent.winnerCount, giveawayContent.monthCount));
    giveawayPrizeBlock.build(maxWidth);
    contentHeight += giveawayPrizeBlock.getHeight();
    contentHeight += Screen.dp(BLOCK_MARGIN);

    /* * */

    giveawayParticipantsBlockY = contentHeight;
    giveawayParticipantsBlock = new Block(this,
      Lang.boldify(Lang.getString(R.string.GiveawayParticipants)),
      Lang.getString(giveawayContent.parameters.onlyNewMembers ?
        R.string.GiveawayParticipantsNew : R.string.GiveawayParticipantsAll));
    giveawayParticipantsBlock.build(maxWidth);
    contentHeight += giveawayParticipantsBlock.getHeight();
    contentHeight += Screen.dp(6);

    giveawayParticipantsBubblesY = contentHeight;
    giveawayParticipantsBubbles = new BubbleWrapView2(tdlib);
    giveawayParticipantsBubbles.addBubble(new TdApi.MessageSenderChat(giveawayContent.parameters.boostedChatId), null);
    giveawayParticipantsBubbles.buildLayout(maxWidth);
    contentHeight += giveawayParticipantsBubbles.getCurrentHeight();
    contentHeight += Screen.dp(BLOCK_MARGIN);

    /* * */

    giveawayFinishTimeBlockY = contentHeight;
    giveawayFinishTimeBlock = new Block(this,
      Lang.boldify(Lang.getString(R.string.GiveawayWinnersSelectionDateHeader)),
      Lang.getString(R.string.GiveawayWinnersSelectionDate,
        Lang.dateYearFull(giveawayContent.parameters.winnersSelectionDate, TimeUnit.SECONDS),
        Lang.time(giveawayContent.parameters.winnersSelectionDate, TimeUnit.SECONDS))
    );
    giveawayFinishTimeBlock.build(maxWidth);
    contentHeight += giveawayFinishTimeBlock.getHeight();
    contentHeight += Screen.dp(BLOCK_MARGIN);

    /* * */



    rippleButtonY = contentHeight;
    rippleButton = new TGInlineKeyboard(this, false);
    rippleButton.setCustom(0, Lang.getString(R.string.GiveawayLearnMore), maxWidth, false, this);
    rippleButton.setViewProvider(currentViews);

    contentHeight += TGInlineKeyboard.getButtonHeight();
    // contentHeight += Screen.dp(BLOCK_MARGIN);

    invalidateGiveawayReceiver();
  }

  @Override
  public void requestGiveawayAvatars (ComplexReceiver complexReceiver, boolean isUpdate) {
    giveawayParticipantsBubbles.requestFiles(complexReceiver);
  }

  @Override
  protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    c.save();
    c.translate(startX, startY);

    final int contentWidth = getContentWidth();
    final float contentCenterX = contentWidth / 2f;

    final Drawable giftIcon = view.getSparseDrawable(R.drawable.baseline_gift_72, ColorId.NONE);
    if (giftIcon != null) {
      Drawables.draw(c, giftIcon, contentCenterX - giftIcon.getMinimumWidth() / 2f, giftDrawableY, PorterDuffPaint.get(ColorId.icon));
    }

    final float cor = Math.min(outlineCounterRect.width(), outlineCounterRect.height()) / 2f;
    final float cbr = Math.min(backgroundCounterRect.width(), backgroundCounterRect.height()) / 2f;

    c.drawRoundRect(outlineCounterRect, cor, cor, Paints.fillingPaint(Theme.getColor(useBubbles() ? ColorId.filling : ColorId.background)));
    c.drawRoundRect(backgroundCounterRect, cbr, cbr, Paints.fillingPaint(getCounterBackgroundColor()));

    participantsCounter.draw(c, contentCenterX, usersCounterY, Gravity.CENTER, 1f);

    giveawayPrizeBlock.draw(c, 0, giveawayPrizeBlockY);
    giveawayParticipantsBlock.draw(c, 0, giveawayParticipantsBlockY);
    giveawayParticipantsBubbles.draw(c, view.getGiveawayAvatarsReceiver(), 0, giveawayParticipantsBubblesY);
    giveawayFinishTimeBlock.draw(c, 0, giveawayFinishTimeBlockY);

    c.restore();



    if (rippleButton != null) {
      rippleButton.draw(view, c, startX, startY + rippleButtonY);
    }
  }

  public int getCounterBackgroundColor () {
    if (!useBubbles()) {
      return Theme.getColor(ColorId.fillingPositive); //, ColorId.fillingActive);
    } else if (isOutgoing()) {
      return Theme.getColor(ColorId.bubbleOut_fillingPositive); //, ColorId.bubbleOut_fillingActive);
    } else {
      return Theme.getColor(ColorId.bubbleIn_fillingPositive); //, ColorId.bubbleIn_fillingActive);
    }
  }

  @Override
  protected int getContentHeight () {
    return contentHeight;
  }

  @Override
  public boolean onTouchEvent (MessageView view, MotionEvent e) {
    if (rippleButton != null && rippleButton.onTouchEvent(view, e)) {
      return true;
    }
    return super.onTouchEvent(view, e);
  }

  @Override
  public boolean performLongPress (View view, float x, float y) {
    boolean res = super.performLongPress(view, x, y);
    return (rippleButton != null && rippleButton.performLongPress(view)) || res;
  }

  @Override
  public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {

  }

  @Override
  public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return TGInlineKeyboard.ClickListener.super.onLongClick(view, keyboard, button);
  }








  private static class Block {
    private final TextWrapper[] texts;

    public Block (TGMessageGiveaway messageGiveaway, CharSequence ...texts) {
      this.texts = new TextWrapper[texts.length];
      for (int a = 0; a < texts.length; a++) {
        this.texts[a] = genTextWrapper(messageGiveaway, texts[a]);
      }
    }

    public void build (int maxWidth) {
      for (TextWrapper text : texts) {
        text.prepare(maxWidth);
      }
    }

    public int getHeight () {
      int height = Screen.dp(6 * (texts.length - 1));
      for (TextWrapper text : texts) {
        height += text.getHeight();
      }
      return height;
    }

    public void draw (Canvas c, int x, int y) {
      for (TextWrapper text : texts) {
        text.draw(c, x, y);
        y += text.getHeight() + Screen.dp(6);
      }
    }
  }

  private static TextWrapper genTextWrapper (TGMessageGiveaway message, CharSequence text) {
    return new TextWrapper(message.tdlib(), TD.toFormattedText(text, false), getGiveawayTextStyleProvider(), message.getTextColorSet(), null, null)
      .setTextFlagEnabled(Text.FLAG_ALIGN_CENTER, true)
      .setViewProvider(message.currentViews);
  }

  private static TextStyleProvider giveawayStyleProvider;

  private static TextStyleProvider getGiveawayTextStyleProvider () {
    if (giveawayStyleProvider == null) {
      giveawayStyleProvider = new TextStyleProvider(Fonts.newRobotoStorage()).setTextSize(15f).setAllowSp(true);
      Settings.instance().addChatFontSizeChangeListener(giveawayStyleProvider);
    }
    return giveawayStyleProvider;
  }
}
