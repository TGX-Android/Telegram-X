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
 * File created on 22/10/2016
 */
package org.thunderdog.challegram.component.attach;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageFile;
import org.thunderdog.challegram.loader.ImageFileRemote;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Screen;

import me.vkryl.core.StringUtils;

public class MediaLocationData {
  private final TdApi.Venue venue;
  private final int distance;
  private final ImageFileRemote icon;
  private String inlineQueryResultId;

  public MediaLocationData (Tdlib tdlib, TdApi.InlineQueryResultVenue venue, @Nullable TdApi.Location userLocation) {
    this(tdlib, venue.venue, userLocation);
    this.inlineQueryResultId = venue.id;
  }

  public MediaLocationData (Tdlib tdlib, TdApi.Venue venue, @Nullable TdApi.Location userLocation) {
    this.venue = venue;
    if (userLocation != null) {
      distance = (int) U.distanceBetween(venue.location.latitude, venue.location.longitude, userLocation.latitude, userLocation.longitude);
    } else {
      distance = 0;
    }

    // Icon
    String iconUrl = TD.getIconUrl(venue);
    if (iconUrl != null) {
      icon = new ImageFileRemote(tdlib, iconUrl, new TdApi.FileTypeThumbnail());
      icon.setSize(Screen.dp(40f));
      icon.setScaleType(ImageFile.FIT_CENTER);
    } else {
      icon = null;
    }
  }

  @Override
  public boolean equals (Object obj) {
    return obj != null && obj instanceof MediaLocationData && StringUtils.equalsOrBothEmpty(((MediaLocationData) obj).venue.id, this.venue.id) && StringUtils.equalsOrBothEmpty(((MediaLocationData) obj).venue.provider, this.venue.provider);
  }

  public String getId () {
    return venue.id;
  }

  public String getInlineQueryResultId () {
    return inlineQueryResultId;
  }

  public double getLatitude () {
    return venue.location.latitude;
  }

  public double getLongitude () {
    return venue.location.longitude;
  }

  public int getDistance () {
    return distance;
  }

  public String getTitle () {
    return venue.title;
  }

  public String getAddress () {
    return venue.address;
  }

  public ImageFileRemote getIconImage () {
    return icon;
  }

  public TdApi.InputMessageVenue convertToInputMessage () {
    return new TdApi.InputMessageVenue(venue);
  }
}
