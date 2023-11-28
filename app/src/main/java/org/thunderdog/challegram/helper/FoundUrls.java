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
 * File created on 28/11/2023
 */
package org.thunderdog.challegram.helper;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.tool.Strings;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.StringUtils;

public final class FoundUrls {
  public final @NonNull Set<String> set;
  public final @NonNull String[] urls;

  private static void findUniqueUrls (Set<String> uniqueUrls, @NonNull TdApi.FormattedText formattedText) {
    List<String> foundUrls = TD.findUrls(formattedText);
    if (foundUrls != null && !foundUrls.isEmpty()) {
      for (String url : foundUrls) {
        if (!url.matches("^[^/]+$")) {
          uniqueUrls.add(url);
        }
      }
    }
  }

  public FoundUrls () {
    this.set = Collections.emptySet();
    this.urls = new String[0];
  }

  public boolean hasUrl (@NonNull String url) {
    if (!StringUtils.isEmpty(url)) {
      if (set.contains(url)) {
        return true;
      }
      String unifiedUrl = unifyUrl(url);
      if (set.contains(unifiedUrl)) {
        return true;
      }
      for (String existingUrl : urls) {
        if (unifiedUrl.equals(unifyUrl(existingUrl))) {
          return true;
        }
      }
    }
    return false;
  }

  public int indexOfUrl (@NonNull String url) {
    int index = ArrayUtils.indexOf(urls, url);
    if (index == -1) {
      String unifiedUrl = unifyUrl(url);
      int foundIndex = 0;
      for (String existingUrl : urls) {
        String unifiedExistingUrl = unifyUrl(existingUrl);
        if (unifiedUrl.equals(unifiedExistingUrl)) {
          return foundIndex;
        }
        foundIndex++;
      }
    }
    return index;
  }

  private static FoundUrls emptyResult;

  public static FoundUrls emptyResult () {
    if (emptyResult == null) {
      emptyResult = new FoundUrls();
    }
    return emptyResult;
  }

  public FoundUrls (@NonNull TdApi.FormattedText formattedText) {
    this.set = new LinkedHashSet<>();
    findUniqueUrls(this.set, formattedText);
    this.urls = set.toArray(new String[0]);
  }

  public FoundUrls (@NonNull TdApi.MessageText messageText) {
    this.set = new LinkedHashSet<>();
    findUniqueUrls(this.set, messageText.text);
    String specificUrl =
      messageText.linkPreviewOptions != null && !messageText.linkPreviewOptions.isDisabled && !StringUtils.isEmpty(messageText.linkPreviewOptions.url) ?
        messageText.linkPreviewOptions.url :
        messageText.webPage != null ? messageText.webPage.url :
          null;
    if (!StringUtils.isEmpty(specificUrl)) {
      // Make sure there is existing url
      this.set.add(specificUrl);
    }
    this.urls = set.toArray(new String[0]);
  }

  public boolean isEmpty () {
    return set.isEmpty();
  }

  public int size () {
    return set.size();
  }

  @Override
  public boolean equals (@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof FoundUrls)) {
      return false;
    }
    FoundUrls other = (FoundUrls) obj;
    return Arrays.equals(this.urls, other.urls);
  }

  private static String unifyUrl (@NonNull String url) {
    Uri uri = Strings.forceProtocol(url, "https");
    if (uri != null) {
      String path = uri.getPath();
      if (path != null && path.matches("^/+$")) {
        uri = uri.buildUpon().path(null).build();
      }
      return uri.toString();
    }
    return url;
  }

  public static boolean compareUrls (@NonNull String a, @NonNull String b) {
    return a.equals(b) || unifyUrl(a).equals(unifyUrl(b));
  }
}
