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
 * File created for regular gift message support
 */
package org.thunderdog.challegram.data;

import android.view.View;

import androidx.annotation.NonNull;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MessagesManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;

import tgx.td.Td;

public class TGMessageGiftRegular extends TGMessageGiveawayBase {
  private final TdApi.MessageGift gift;

  public TGMessageGiftRegular (MessagesManager manager, TdApi.Message msg, @NonNull TdApi.MessageGift gift) {
    super(manager, msg);
    this.gift = gift;
  }

  protected int onBuildContent (int maxWidth) {
    content = new Content(maxWidth - Screen.dp(CONTENT_PADDING_DP * 2));

    content.padding(Screen.dp(25));
    content.add(new ContentDrawable(R.drawable.baseline_gift_72));
    content.padding(Screen.dp(25));

    // Determine the title based on whether we're sender or receiver
    boolean isOutgoing = msg.isOutgoing;
    String title;
    if (gift.wasConverted) {
      title = Lang.getString(R.string.GiftConverted);
    } else if (gift.wasUpgraded) {
      title = Lang.getString(R.string.GiftUpgraded);
    } else if (gift.wasRefunded) {
      title = Lang.getString(R.string.GiftRefunded);
    } else if (isOutgoing) {
      title = Lang.getString(R.string.GiftSent);
    } else {
      title = Lang.getString(R.string.GiftReceived);
    }
    content.add(Lang.boldify(title), getTextColorSet(), currentViews);
    content.padding(Screen.dp(6));

    // Show sender info if available and not outgoing
    if (!isOutgoing && gift.senderId != null) {
      content.add(new ContentBubbles(this, maxWidth - Screen.dp(CONTENT_PADDING_DP * 2 + 60)).setOnClickListener(this::onBubbleClick).addChatId(Td.getSenderId(gift.senderId)));
      content.padding(Screen.dp(6));
    }

    // Show gift value
    if (gift.gift != null && gift.gift.starCount > 0) {
      String valueText = Lang.plural(R.string.xGiftValue, (int) gift.gift.starCount);
      content.add(valueText, getTextColorSet(), currentViews);
      content.padding(Screen.dp(6));
    }

    // Show gift message text if present
    if (gift.text != null && gift.text.text != null && !gift.text.text.isEmpty()) {
      content.padding(Screen.dp(BLOCK_MARGIN));
      content.add(gift.text.text, getTextColorSet(), currentViews);
    }

    // Show sell value if available
    if (!isOutgoing && gift.sellStarCount > 0 && !gift.wasConverted && !gift.wasUpgraded && !gift.wasRefunded) {
      content.padding(Screen.dp(BLOCK_MARGIN));
      String sellInfo = Lang.plural(R.string.xGiftCanBeSold, (int) gift.sellStarCount);
      content.add(sellInfo, getTextColorSet(), currentViews);
    }

    invalidateGiveawayReceiver();
    return content.getHeight();
  }

  private void onBubbleClick (TdApi.MessageSender senderId) {
    tdlib.ui().openChat(controller(), Td.getSenderId(senderId), new TdlibUi.ChatOpenParameters().keepStack().removeDuplicates().openProfileInCaseOfPrivateChat());
  }

  @Override protected String getButtonText () {
    if (gift.wasConverted || gift.wasUpgraded || gift.wasRefunded) {
      return null; // No button for converted/upgraded/refunded gifts
    }
    return Lang.getString(R.string.ViewGift);
  }

  @Override public void onClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    // Open gift details - for now just show a toast
    // Full implementation would open a gift details screen
    if (gift.receivedGiftId != null && !gift.receivedGiftId.isEmpty()) {
      // TODO: Open gift details screen using getReceivedGift
    }
  }

  @Override public boolean onLongClick (View view, TGInlineKeyboard keyboard, TGInlineKeyboard.Button button) {
    return false;
  }
}
