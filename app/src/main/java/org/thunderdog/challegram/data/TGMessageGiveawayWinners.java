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
 * File created on 04/01/2024
 */
package org.thunderdog.challegram.data;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessageView;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Counter;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.Text;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;

import me.vkryl.core.StringUtils;
import me.vkryl.td.MessageId;
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

  private Counter participantsCounter;
  private int participantsCounterY;

  @Override protected int onBuildContent (int maxWidth) {
    content = new Content(maxWidth - Screen.dp(CONTENT_PADDING_DP * 2));

    content.padding(Screen.dp(30));
    content.add(new ContentDrawable(R.drawable.baseline_party_popper_72));
    participantsCounterY = content.getHeight() + Screen.dp(1.5f);
    content.padding(Screen.dp(35));


    CharSequence text = Lang.pluralBold(R.string.xGiveawayWinnersSelectedText, giveawayWinners.winnerCount);
    int startIndex = StringUtils.indexOf(text, "**");
    if (startIndex != -1) {
      int endIndex = StringUtils.indexOf(text, "**", startIndex + 2);
      if (endIndex != -1) {
        SpannableStringBuilder b = new SpannableStringBuilder(text);

        String linkText = b.subSequence(startIndex + 2, endIndex).toString();
        TextEntityCustom custom = new TextEntityCustom(controller(), tdlib, linkText, 0, linkText.length(), 0, openParameters());
        custom.setCustomColorSet(getLinkColorSet());
        custom.setOnClickListener(new ClickableSpan() {
          @Override public void onClick (@NonNull View widget) {
            tdlib.ui().openMessage(controller(), giveawayWinners.boostedChatId, new MessageId(giveawayWinners.boostedChatId, giveawayWinners.giveawayMessageId), openParameters());
          }
        });
        FormattedText linkArgument = new FormattedText(linkText, new TextEntity[] {custom});

        b.delete(endIndex, endIndex + 2);
        b.delete(startIndex, startIndex + 2);
        endIndex -= 2;
        b.setSpan(linkArgument, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text = b;
      }
    }

    FormattedText formattedText = FormattedText.valueOf(text, tdlib, openParameters());
    Text.Builder b = new Text.Builder(formattedText, maxWidth - Screen.dp(CONTENT_PADDING_DP * 2), getGiveawayTextStyleProvider(), getTextColorSet(), null).viewProvider(currentViews).textFlags(Text.FLAG_ALIGN_CENTER);

    content.add(Lang.boldify(Lang.getString(R.string.GiveawayWinnersSelected)), getTextColorSet(), currentViews);
    content.padding(Screen.dp(6));
    content.add(new ContentText(b.build()));

    content.padding(Screen.dp(BLOCK_MARGIN));
    content.add(Lang.boldify(Lang.getString(R.string.GiveawayWinners)), getTextColorSet(), currentViews);
    content.padding(Screen.dp(6));
    content.add(new ContentBubbles(this, maxWidth - Screen.dp(CONTENT_PADDING_DP * 2 + 60)).setOnClickListener(this::onBubbleClick).addChatIds(giveawayWinners.winnerUserIds));

    content.padding(Screen.dp(BLOCK_MARGIN));
    content.add(Lang.getString(R.string.GiveawayAllWinnersReceivedLinks), getTextColorSet(), currentViews);

    /* * */

    participantsCounter = new Counter.Builder().allBold(true).textSize(11).noBackground().textColor(ColorId.badgeText).build();
    participantsCounter.setCount(giveawayWinners.winnerCount, false, "x" + giveawayWinners.winnerCount, false);
    backgroundCounterRect.set(maxWidth / 2f - participantsCounter.getWidth() / 2f - Screen.dp(8), participantsCounterY - Screen.dp(23 / 2f), maxWidth / 2f + participantsCounter.getWidth() / 2f + Screen.dp(8), participantsCounterY + Screen.dp(23 / 2f));
    outlineCounterRect.set(backgroundCounterRect);
    outlineCounterRect.inset(-Screen.dp(3), -Screen.dp(3));

    invalidateGiveawayReceiver();
    return content.getHeight();
  }

  @Override protected String getButtonText () {
    return Lang.getString(R.string.GiveawayLearnMore);
  }

  private void onBubbleClick (TdApi.MessageSender senderId) {
    final long userId = Td.getSenderUserId(senderId);
    if (userId != 0) {
      tdlib.ui().openPrivateProfile(controller(), userId, openParameters());
      return;
    }

    tdlib.ui().openChat(controller(), Td.getSenderId(senderId), new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates().openProfileInCaseOfPrivateChat());
  }

  @Override protected void drawContent (MessageView view, Canvas c, int startX, int startY, int maxWidth) {
    super.drawContent(view, c, startX, startY, maxWidth);

    final int saveCount = Views.save(c);
    c.translate(startX, startY);

    final int contentWidth = getContentWidth();
    final float contentCenterX = contentWidth / 2f;

    if (participantsCounter != null) {
      final float cor = Math.min(outlineCounterRect.width(), outlineCounterRect.height()) / 2f;
      final float cbr = Math.min(backgroundCounterRect.width(), backgroundCounterRect.height()) / 2f;
      c.drawRoundRect(outlineCounterRect, cor, cor, Paints.fillingPaint(Theme.getColor(ColorId.filling)));
      c.drawRoundRect(backgroundCounterRect, cbr, cbr, Paints.fillingPaint(Theme.getColor(ColorId.badge)));
      participantsCounter.draw(c, contentCenterX, participantsCounterY, Gravity.CENTER, 1f);
    }

    Views.restore(c, saveCount);
  }

  @Override public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    loadPremiumGiveawayInfo();
  }

  @Override protected void onPremiumGiveawayInfoLoaded (TdApi.PremiumGiveawayInfo result, @Nullable TdApi.Error error) {
    super.onPremiumGiveawayInfoLoaded(result, error);
    if (error != null) {
      UI.showError(error);
      return;
    }
    if (!isDestroyed()) {
      showPremiumGiveawayInfoPopup(giveawayWinners.winnerCount, giveawayWinners.monthCount, giveawayWinners.boostedChatId, giveawayWinners.additionalChatCount, null, giveawayWinners.actualWinnersSelectionDate, giveawayWinners.prizeDescription);
    }
  }

  @Override public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return false;
  }
}
