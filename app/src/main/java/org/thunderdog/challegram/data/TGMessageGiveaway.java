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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.component.chat.MessagesManager;

public class TGMessageGiveaway extends TGMessage {
  private TdApi.MessagePremiumGiveaway premiumGiveaway;

  public TGMessageGiveaway (MessagesManager manager, TdApi.Message msg, TdApi.MessagePremiumGiveaway premiumGiveaway) {
    super(manager, msg);
    this.premiumGiveaway = premiumGiveaway;
  }

  @Override
  protected void buildContent (int maxWidth) {
    // TODO
  }
}
