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
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGCountry;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Counter;

import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import me.vkryl.td.Td;

public class TGMessageGiveaway extends TGMessageGiveawayBase implements TGInlineKeyboard.ClickListener {
  private final static int BLOCK_MARGIN = 18;

  private final TdApi.MessagePremiumGiveaway premiumGiveaway;

  public TGMessageGiveaway (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessagePremiumGiveaway premiumGiveaway) {
    super(manager, msg);
    this.premiumGiveaway = premiumGiveaway;
  }

  private final RectF outlineCounterRect = new RectF();
  private final RectF backgroundCounterRect = new RectF();

  private Counter participantsCounter;
  private int participantsCounterY;

  @Override protected int onBuildContent (int maxWidth) {
    content = new Content(maxWidth - Screen.dp(CONTENT_PADDING_DP * 2));

    content.padding(Screen.dp(30));
    content.add(new ContentDrawable(R.drawable.baseline_gift_72));
    participantsCounterY = content.getHeight() + Screen.dp(1.5f);
    content.padding(Screen.dp(35));

    content.add(Lang.boldify(Lang.getString(R.string.GiveawayPrizes)), getTextColorSet(), currentViews);
    content.padding(Screen.dp(6));
    if (!StringUtils.isEmpty(premiumGiveaway.parameters.prizeDescription)) {
      content.add(Lang.getCharSequence(R.string.GiveawayPrizesAdditional, Lang.boldify(Integer.toString(premiumGiveaway.winnerCount)), premiumGiveaway.parameters.prizeDescription), getTextColorSet(), currentViews);
      content.padding(Screen.dp(6));
      content.add(Lang.getString(R.string.GiveawayPrizesWith), () -> Theme.getColor(ColorId.textLight), currentViews);
      content.padding(Screen.dp(6));
    }
    CharSequence text = Lang.plural(
      R.string.xGiveawayPrizePremium,
      premiumGiveaway.winnerCount,
      (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 0 ? Lang.newBoldSpan(needFakeBold) : null,
      Lang.pluralBold(
        R.string.xGiveawayPrizePremiumMonths, premiumGiveaway.monthCount
      )
    );
    text = Strings.replaceBoldTokens(text);
    content.add(text, getTextColorSet(), currentViews);

    content.padding(Screen.dp(BLOCK_MARGIN));
    content.add(Lang.boldify(Lang.getString(R.string.GiveawayParticipants)), getTextColorSet(), currentViews);
    content.padding(Screen.dp(6));
    content.add(Lang.getString(premiumGiveaway.parameters.onlyNewMembers ? R.string.GiveawayParticipantsNew : R.string.GiveawayParticipantsAll), getTextColorSet(), currentViews);
    content.padding(Screen.dp(6));
    content.add(new ContentBubbles(this, maxWidth - Screen.dp(CONTENT_PADDING_DP * 2 + 60)).setOnClickListener(this::onBubbleClick).addChatId(premiumGiveaway.parameters.boostedChatId).addChatIds(premiumGiveaway.parameters.additionalChatIds));

    if (premiumGiveaway.parameters.countryCodes.length > 0) {
      StringBuilder sb = new StringBuilder();
      for (String countryCode : premiumGiveaway.parameters.countryCodes) {
        if (sb.length() > 0) {
          sb.append(Lang.getConcatSeparator());
        }
        String[] info = TGCountry.instance().find(countryCode);
        sb.append(info != null ? info[2] : countryCode);
      }
      content.padding(Screen.dp(6));
      content.add(Lang.getString(R.string.GiveawayCountries, sb), getTextColorSet(), currentViews);
    }

    content.padding(Screen.dp(BLOCK_MARGIN));
    content.add(Lang.boldify(Lang.getString(R.string.GiveawayWinnersSelectionDateHeader)), getTextColorSet(), currentViews);
    content.padding(Screen.dp(6));
    content.add(Lang.getString(R.string.GiveawayWinnersSelectionDate, Lang.dateYearFull(premiumGiveaway.parameters.winnersSelectionDate, TimeUnit.SECONDS), Lang.time(premiumGiveaway.parameters.winnersSelectionDate, TimeUnit.SECONDS)), getTextColorSet(), currentViews);

    /* * */

    participantsCounter = new Counter.Builder().allBold(true).textSize(11).noBackground().textColor(ColorId.badgeText).build();
    participantsCounter.setCount(premiumGiveaway.winnerCount, false, "x" + premiumGiveaway.winnerCount, false);
    backgroundCounterRect.set(maxWidth / 2f - participantsCounter.getWidth() / 2f - Screen.dp(8), participantsCounterY - Screen.dp(23 / 2f), maxWidth / 2f + participantsCounter.getWidth() / 2f + Screen.dp(8), participantsCounterY + Screen.dp(23 / 2f));
    outlineCounterRect.set(backgroundCounterRect);
    outlineCounterRect.inset(-Screen.dp(3), -Screen.dp(3));

    invalidateGiveawayReceiver();
    UI.execute(this::loadPremiumGiveawayInfo);
    return content.getHeight();
  }

  @Override
  protected void onBuildButton (int maxWidth) {
    final boolean isParticipating = TD.isParticipating(premiumGiveawayInfo);
    rippleButton.setCustom(isParticipating ? R.drawable.baseline_check_18 : 0, Lang.getString(isParticipating ? R.string.GiveawayParticipating : R.string.GiveawayLearnMore), maxWidth, false, this);
    rippleButton.firstButton().setCustomIconReverse(true);
    rippleButton.firstButton().setCustomColorId(isParticipating ? ColorId.iconPositive : ColorId.NONE);
  }

  private void onBubbleClick (TdApi.MessageSender senderId) {
    tdlib.ui().openChat(controller(), Td.getSenderId(senderId), new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates().openProfileInCaseOfPrivateChat().openProfileInCaseOfDuplicateChat());
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

  private boolean byClick;

  @Override public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    loadPremiumGiveawayInfo();
    byClick = true;
  }

  @Override protected void onPremiumGiveawayInfoLoaded (TdApi.PremiumGiveawayInfo result, @Nullable TdApi.Error error) {
    super.onPremiumGiveawayInfoLoaded(result, error);
    this.onBuildButton(getContentWidth());

    if (byClick && error != null) {
      UI.showError(error);
      return;
    }
    if (byClick && !isDestroyed()) {
      showPremiumGiveawayInfoPopup(premiumGiveaway.winnerCount, premiumGiveaway.monthCount, premiumGiveaway.parameters.boostedChatId, premiumGiveaway.parameters.additionalChatIds.length, premiumGiveaway.parameters.additionalChatIds, premiumGiveaway.parameters.winnersSelectionDate, premiumGiveaway.parameters.prizeDescription);
    }
    byClick = false;
  }

  @Override public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return false;
  }
}
