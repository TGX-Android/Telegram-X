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
package org.thunderdog.challegram.data;

import org.drinkless.tdlib.TdApi;

/** Map only profile tabs whose contents already have a shared-media renderer. */
public final class ProfileTabUtils {
  private ProfileTabUtils () { }

  public static boolean isSupported (TdApi.ProfileTab tab) {
    return tab instanceof TdApi.ProfileTabMedia || tab instanceof TdApi.ProfileTabFiles ||
      tab instanceof TdApi.ProfileTabLinks || tab instanceof TdApi.ProfileTabMusic ||
      tab instanceof TdApi.ProfileTabVoice || tab instanceof TdApi.ProfileTabGifs;
  }

  public static boolean matches (TdApi.ProfileTab tab, int filter) {
    if (tab instanceof TdApi.ProfileTabMedia) {
      return filter == TdApi.SearchMessagesFilterPhotoAndVideo.CONSTRUCTOR ||
        filter == TdApi.SearchMessagesFilterPhoto.CONSTRUCTOR || filter == TdApi.SearchMessagesFilterVideo.CONSTRUCTOR;
    } else if (tab instanceof TdApi.ProfileTabFiles) {
      return filter == TdApi.SearchMessagesFilterDocument.CONSTRUCTOR;
    } else if (tab instanceof TdApi.ProfileTabLinks) {
      return filter == TdApi.SearchMessagesFilterUrl.CONSTRUCTOR;
    } else if (tab instanceof TdApi.ProfileTabMusic) {
      return filter == TdApi.SearchMessagesFilterAudio.CONSTRUCTOR;
    } else if (tab instanceof TdApi.ProfileTabVoice) {
      return filter == TdApi.SearchMessagesFilterVoiceNote.CONSTRUCTOR || filter == TdApi.SearchMessagesFilterVideoNote.CONSTRUCTOR;
    } else if (tab instanceof TdApi.ProfileTabGifs) {
      return filter == TdApi.SearchMessagesFilterAnimation.CONSTRUCTOR;
    }
    return false;
  }

  public static int findIndex (TdApi.ProfileTab tab, int[] filters) {
    for (int i = 0; i < filters.length; i++) {
      if (matches(tab, filters[i])) {
        return i;
      }
    }
    return -1;
  }
}
