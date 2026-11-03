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
 * File created on 03/11/2026
 */
package org.thunderdog.challegram.telegram;

import android.os.Build;
import android.webkit.ServiceWorkerClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import androidx.annotation.RequiresApi;
import androidx.webkit.Profile;

@RequiresApi(Build.VERSION_CODES.N)
final class TdlibWebProxyServiceWorker {
  private TdlibWebProxyServiceWorker () { }

  static void install (Profile profile) {
    profile.getServiceWorkerController().setServiceWorkerClient(new ServiceWorkerClient() {
      @Override
      public WebResourceResponse shouldInterceptRequest (WebResourceRequest request) {
        return TdlibWebProxyClient.blockedResponse();
      }
    });
  }
}
