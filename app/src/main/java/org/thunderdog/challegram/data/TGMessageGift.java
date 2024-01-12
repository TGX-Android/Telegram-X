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

import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.component.popups.ModernActionedLayout;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.td.Td;

public class TGMessageGift extends TGMessageGiveawayBase {
  private final TdApi.MessagePremiumGiftCode premiumGiftCode;

  public TGMessageGift (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessagePremiumGiftCode premiumGiftCode) {
    super(manager, msg);
    this.premiumGiftCode = premiumGiftCode;
  }

  protected int onBuildContent (int maxWidth) {
    final boolean isUnclaimed = premiumGiftCode.isUnclaimed;

    content = new Content(maxWidth - Screen.dp(CONTENT_PADDING_DP * 2));

    content.padding(Screen.dp(25));
    content.add(new ContentDrawable(R.drawable.baseline_gift_72));
    content.padding(Screen.dp(25));

    content.add(Lang.boldify(Lang.getString(isUnclaimed ? R.string.GiveawayUnclaimedPrize : R.string.GiveawayCongratulations)), getTextColorSet(), currentViews);
    content.padding(Screen.dp(6));
    content.add(Lang.getString(isUnclaimed ? R.string.GiveawayYouGetUnclaimedPrize : (premiumGiftCode.isFromGiveaway ? R.string.GiveawayYouWon : R.string.GiveawayYouGetGift)), getTextColorSet(), currentViews);
    if (premiumGiftCode.creatorId != null) {
      content.padding(Screen.dp(6));
      content.add(new ContentBubbles(this, maxWidth - Screen.dp(CONTENT_PADDING_DP * 2 + 60)).setOnClickListener(this::onBubbleClick).addChatId(Td.getSenderId(premiumGiftCode.creatorId)));
    }

    content.padding(Screen.dp(BLOCK_MARGIN));
    content.add(Strings.buildMarkdown(this, Lang.plural(isUnclaimed ? R.string.xGiveawayUnclaimedPrizeReceivedInfo : R.string.xGiveawayPrizeReceivedInfo, premiumGiftCode.monthCount)), getTextColorSet(), currentViews);

    invalidateGiveawayReceiver();
    return content.getHeight();
  }

  private void onBubbleClick (TdApi.MessageSender senderId) {
    tdlib.ui().openChat(controller(), Td.getSenderId(senderId), new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates().openProfileInCaseOfPrivateChat());
  }

  @Override protected String getButtonText () {
    return Lang.getString(R.string.OpenGiftLink);
  }

  private boolean loading;

  @Override public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    if (loading) {
      return;
    }

    loading = true;
    tdlib.send(new TdApi.CheckPremiumGiftCode(premiumGiftCode.code), (info, error) -> UI.post(() -> {
      loading = false;
      if (error != null) {
        UI.showError(error);
      } else {
        ModernActionedLayout.showGiftCode(context().navigation().getCurrentStackItem(), premiumGiftCode.code, premiumGiftCode, info);
      }
    }));
  }

  @Override public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return false;
  }
}
