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

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.sticker.TGStickerObj;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.EmbeddableStickerView;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import me.vkryl.core.StringUtils;

final class BusinessProfileController extends RecyclerViewController<Void> implements View.OnClickListener {
  private final long userId;
  private TdApi.BusinessInfo info;
  private SettingsAdapter adapter;

  BusinessProfileController (Context context, Tdlib tdlib, long userId, TdApi.BusinessInfo info) {
    super(context, tdlib);
    this.userId = userId;
    this.info = info;
  }

  static boolean hasPublicInfo (TdApi.BusinessInfo info) {
    return info != null && (info.location != null || info.openingHours != null || info.startPage != null);
  }

  @Override public int getId () { return R.id.controller_tgxPublicBusiness; }
  @Override public CharSequence getName () { return Lang.getString(R.string.TgxBusinessPublicProfile); }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recycler) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setEmbedSticker (ListItem item, int position, EmbeddableStickerView view, boolean isUpdate) {
        TdApi.Sticker sticker = (TdApi.Sticker) item.getData();
        view.setSticker(new TGStickerObj(tdlib, sticker, (String) null, sticker.fullType));
      }
    };
    recycler.setAdapter(adapter);
    render();
    tdlib.send(new TdApi.GetUserFullInfo(userId), (fresh, error) -> runOnUiThreadOptional(() -> {
      if (fresh != null) {
        info = fresh.businessInfo;
        render();
      }
    }));
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    if (!hasPublicInfo(info)) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxBusinessPublicEmpty));
    } else {
      if (info.startPage != null) {
        TdApi.BusinessStartPage page = info.startPage;
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, StringUtils.isEmpty(page.title) ?
          Lang.getString(R.string.TgxBusinessStart) : page.title));
        if (!StringUtils.isEmpty(page.message)) {
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, page.message));
        }
        if (page.sticker != null) {
          items.add(new ListItem(ListItem.TYPE_EMBED_STICKER).setData(page.sticker));
        }
      }
      if (info.location != null) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TgxBusinessLocation));
        if (!StringUtils.isEmpty(info.location.address)) {
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, info.location.address));
        }
        if (info.location.location != null) {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxBusinessMap, 0, R.string.TgxBusinessPublicMap));
        }
      }
      if (info.openingHours != null) {
        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.TgxBusinessHours));
        if (info.nextOpenIn != info.nextCloseIn) {
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0,
            info.nextCloseIn < info.nextOpenIn ? R.string.TgxBusinessOpenNow : R.string.TgxBusinessClosedNow));
        }
        TdApi.BusinessOpeningHours hours = info.localOpeningHours != null ? info.localOpeningHours : info.openingHours;
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, info.localOpeningHours != null ?
          Lang.getString(R.string.TgxBusinessYourTimezone) : hours.timeZoneId));
        String[] days = new DateFormatSymbols(Lang.getConfigurationLocale()).getWeekdays();
        for (int day = 0; day < 7; day++) {
          ArrayList<String> times = new ArrayList<>();
          for (int[] interval : hoursForDay(hours.openingHours, day)) {
            times.add(Lang.getString(R.string.TgxBusinessDateRange, formatTime(interval[0] % 1440),
              formatTime(interval[1] % 1440)));
          }
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.TgxBusinessDayHours,
            days[(day + 1) % 7 + Calendar.SUNDAY], times.isEmpty() ? Lang.getString(R.string.TgxBusinessClosed) :
              TextUtils.join(", ", times))));
        }
      }
    }
    adapter.setItems(items, false);
  }

  static ArrayList<int[]> hoursForDay (TdApi.BusinessOpeningHoursInterval[] hours, int day) {
    ArrayList<int[]> result = new ArrayList<>();
    int dayStart = day * 1440;
    int dayEnd = dayStart + 1440;
    int weekMinutes = 7 * 1440;
    for (TdApi.BusinessOpeningHoursInterval interval : hours) {
      int start = Math.max(interval.startMinute, dayStart);
      int end = Math.min(interval.endMinute, dayEnd);
      if (start < end) {
        result.add(new int[] {start, end});
      }
      start = Math.max(interval.startMinute - weekMinutes, dayStart);
      end = Math.min(interval.endMinute - weekMinutes, dayEnd);
      if (start < end) {
        result.add(new int[] {start, end});
      }
    }
    result.sort((a, b) -> Integer.compare(a[0], b[0]));
    return result;
  }

  private String formatTime (int minute) {
    java.text.DateFormat format = android.text.format.DateFormat.getTimeFormat(context());
    format.setTimeZone(TimeZone.getTimeZone("UTC"));
    return format.format(new Date(minute * 60_000L));
  }

  @Override
  public void onClick (View view) {
    if (view.getId() == R.id.btn_tgxBusinessMap && info != null && info.location != null && info.location.location != null) {
      TdApi.Location point = info.location.location;
      if (!Intents.openMap(point.latitude, point.longitude, tdlib.cache().userName(userId), info.location.address)) {
        UI.copyText(info.location.address, R.string.CopiedText);
      }
    }
  }
}
