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
 * File created on 28/03/2023
 */
package org.thunderdog.challegram.voip;

import androidx.annotation.Keep;

import org.drinkless.td.libcore.telegram.TdApi;

@Keep
public class Socks5Proxy {
  public final String host;
  public final int port;

  public final String username;
  public final String password;

  public Socks5Proxy (String host, int port, String username, String password) {
    this.host = host;
    this.port = port;
    this.username = username;
    this.password = password;
  }

  public Socks5Proxy (TdApi.InternalLinkTypeProxy proxy) {
    if (proxy.type.getConstructor() != TdApi.ProxyTypeSocks5.CONSTRUCTOR)
      throw new IllegalArgumentException(proxy.type.toString());
    TdApi.ProxyTypeSocks5 socks5 = (TdApi.ProxyTypeSocks5) proxy.type;
    this.host = proxy.server;
    this.port = proxy.port;
    this.username = socks5.username;
    this.password = socks5.password;
  }
}
