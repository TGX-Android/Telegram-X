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
 * File created on 21/10/2026
 */
package org.thunderdog.challegram.telegram;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

final class TdlibWebProxy {
  private final Client client;

  TdlibWebProxy (Client client, Runnable onResume) {
    this.client = client;
  }

  void open (TdApi.WebProxyCarrier carrier) {
    client.send(
      new TdApi.ReportWebProxyCarrierError(
        carrier.id,
        new TdApi.WebProxyCarrierErrorUnsupported()
      ),
      ignored -> { }
    );
  }

  void data (int carrierId, byte[] data) { }
  void close (int carrierId) { }
  void destroy () { }
}
