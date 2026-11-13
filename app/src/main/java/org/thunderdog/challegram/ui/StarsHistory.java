/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.ui;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import org.drinkless.tdlib.TdApi;

final class StarsHistory {
  private StarsHistory () { }

  static String amount (TdApi.StarAmount amount, Locale locale, boolean signed) {
    BigDecimal total = BigDecimal.valueOf(amount.starCount).add(BigDecimal.valueOf(amount.nanostarCount, 9));
    NumberFormat format = NumberFormat.getNumberInstance(locale);
    format.setMaximumFractionDigits(9);
    return (signed && total.signum() > 0 ? "+" : "") + format.format(total);
  }

  static String transactionKey (TdApi.StarTransaction transaction) {
    return (transaction.isRefund ? "refund:" : "original:") + transaction.id;
  }

  static boolean canEdit (TdApi.StarSubscription shown, TdApi.StarSubscription fresh, long now) {
    return shown.id.equals(fresh.id) && shown.isCanceled == fresh.isCanceled &&
      shown.chatId == fresh.chatId && shown.expirationDate == fresh.expirationDate &&
      fresh.expirationDate > now && shown.pricing != null && fresh.pricing != null &&
      shown.pricing.starCount == fresh.pricing.starCount && shown.pricing.period == fresh.pricing.period;
  }
}
