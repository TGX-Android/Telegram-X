/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 21/10/2016
 */
package org.thunderdog.challegram.component.attach;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.CancellableResultHandler;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.td.ChatId;

public class MediaLocationFinder {
  private static MediaLocationFinder instance;

  public static MediaLocationFinder instance () {
    if (instance == null) {
      instance = new MediaLocationFinder();
    }
    return instance;
  }

  private final LocationManager manager;

  private MediaLocationFinder () {
    LocationManager manager = null;
    try {
      manager = (LocationManager) UI.getAppContext().getSystemService(Context.LOCATION_SERVICE);
    } catch (Throwable t) {
      Log.e("LocationService is unavailable", t);
    }
    this.manager = manager;
  }

  public Location getLastKnownLocation () {
    if (manager == null) {
      return null;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && UI.getAppContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      return null;
    }
    List<String> providers = manager.getProviders(true);
    for (int i = providers.size() - 1; i >= 0; i--) {
      Location location = manager.getLastKnownLocation(providers.get(i));
      if (location != null) {
        return new Location(location);
      }
    }
    return null;
  }

  public static CancellableResultHandler findNearbyPlaces (final Tdlib tdlib, final long chatId, final Location location, final @Nullable String q, final Callback callback) {
    if (callback == null) {
      throw new IllegalArgumentException();
    }

    TdApi.Location userLocation = new TdApi.Location(location.getLatitude(), location.getLongitude(), location.getAccuracy());

    CancellableResultHandler handler = new CancellableResultHandler() {
      @Override
      public void processResult (TdApi.Object object) {
        switch (object.getConstructor()) {
          case TdApi.Chat.CONSTRUCTOR: {
            TdApi.Chat chat = (TdApi.Chat) object;
            tdlib.client().send(new TdApi.GetInlineQueryResults(ChatId.toUserId(chat.id), chatId, userLocation, q, null), this);
            break;
          }
          case TdApi.InlineQueryResults.CONSTRUCTOR: {
            TdApi.InlineQueryResults results = (TdApi.InlineQueryResults) object;
            List<MediaLocationData> list = new ArrayList<>(results.results.length);
            for (TdApi.InlineQueryResult result : results.results) {
              if (result.getConstructor() == TdApi.InlineQueryResultVenue.CONSTRUCTOR) {
                list.add(new MediaLocationData(tdlib, (TdApi.InlineQueryResultVenue) result, userLocation));
              }
            }
            tdlib.ui().post(() -> callback.onNearbyPlacesLoaded(this, location, results.inlineQueryId, list, results.nextOffset));
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            tdlib.ui().post(() -> callback.onNearbyPlacesErrorLoading(this, location, (TdApi.Error) object));
            break;
          }
        }
      }
    };

    tdlib.client().send(new TdApi.SearchPublicChat(tdlib.getVenueSearchBotUsername()), handler);
    return handler;
  }

  public interface Callback {
    void onNearbyPlacesLoaded (CancellableResultHandler call, Location location, long queryId, List<MediaLocationData> result, @Nullable String nextOffset);
    void onNearbyPlacesErrorLoading (CancellableResultHandler call, Location location, TdApi.Error error);
  }
}
