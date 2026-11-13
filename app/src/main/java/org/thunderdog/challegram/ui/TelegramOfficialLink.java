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

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.tool.UI;

final class TelegramOfficialLink {
  private static final String[] TELEGRAM_PACKAGES = {"org.telegram.messenger.web", "org.telegram.messenger"};

  private TelegramOfficialLink () { }

  static void open (ViewController<?> owner, TdApi.InternalLinkType destination) {
    owner.tdlib().send(new TdApi.GetInternalLink(destination, false), (link, error) ->
      owner.runOnUiThreadOptional(() -> {
        if (!owner.isFocused()) {
          return;
        }
        if (error != null) {
          UI.showError(error);
          return;
        }
        Uri uri = Uri.parse(link.url);
        if ("tg".equals(uri.getScheme())) {
          for (String packageName : TELEGRAM_PACKAGES) {
            try {
              owner.context().startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(packageName));
              return;
            } catch (ActivityNotFoundException | SecurityException ignored) { }
          }
        }
        UI.showToast(R.string.TgxOfficialTelegramRequired, Toast.LENGTH_LONG);
      }));
  }
}
