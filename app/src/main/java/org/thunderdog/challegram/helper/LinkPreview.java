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

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.chat.MediaPreview;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.RateLimiter;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceList;
import tgx.td.Td;
import tgx.td.TdExt;

public class LinkPreview implements Destroyable {
  public final Tdlib tdlib;
  public final String url;

  public TdApi.LinkPreview linkPreview;
  public TdApi.Error error;

  private final TdApi.Message fakeMessage;

  private final RateLimiter linkPreviewLoader = new RateLimiter(this::loadLinkPreview, 400L, null);
  private boolean needLoadWebPagePreview, isDestroyed;
  private final List<RunnableData<LinkPreview>> staticLoadCallbacks = new ArrayList<>();

  private final ReferenceList<RunnableData<LinkPreview>> loadCallbacks = new ReferenceList<>(false, true, (list, isFull) -> {
    if (isFull) {
      if (needLoadWebPagePreview) {
        linkPreviewLoader.run();
      }
    } else {
      linkPreviewLoader.cancelIfScheduled();
    }
  });

  private boolean forceSmallMedia, forceLargeMedia;

  public LinkPreview (Tdlib tdlib, String url, @Nullable TdApi.Message existingMessage) {
    this.tdlib = tdlib;
    this.url = url;
    this.linkPreviewLoader.setDelayFirstExecution(true);
    this.needLoadWebPagePreview = true;

    TdApi.MessageText messageText = new TdApi.MessageText(new TdApi.FormattedText(url, null), null, null);
    TdApi.MessageSender messageSender = existingMessage != null ? existingMessage.senderId : new TdApi.MessageSenderUser(tdlib.myUserId());

    if (existingMessage != null && Td.isText(existingMessage.content)) {
      TdApi.MessageText existingText = (TdApi.MessageText) existingMessage.content;
      if (existingText.linkPreview != null) {
        TdApi.LinkPreviewOptions existingOptions = existingText.linkPreviewOptions;
        boolean isCurrentWebPage =
          (existingText.linkPreviewOptions != null && !StringUtils.isEmpty(existingText.linkPreviewOptions.url) && FoundUrls.compareUrls(existingText.linkPreviewOptions.url, url)) ||
          (FoundUrls.compareUrls(existingText.linkPreview.url, url));
        if (isCurrentWebPage) {
          this.linkPreview = existingText.linkPreview;
          updateMessageText(messageText, this.linkPreview);
          this.needLoadWebPagePreview = false;
          this.forceSmallMedia = existingOptions != null && existingOptions.forceSmallMedia;
          this.forceLargeMedia = existingOptions != null && existingOptions.forceLargeMedia;
        }
      }
    }
    this.fakeMessage = TD.newFakeMessage(0, messageSender, messageText);
  }

  public void addLoadCallback (RunnableData<LinkPreview> loadCallback) {
    this.staticLoadCallbacks.add(loadCallback);
  }

  public void removeLoadCallback (RunnableData<LinkPreview> loadCallback) {
    this.staticLoadCallbacks.remove(loadCallback);
  }

  private static void updateMessageText (TdApi.MessageText messageText, TdApi.LinkPreview linkPreview) {
    messageText.linkPreview = linkPreview;
    if (!Td.isEmpty(linkPreview.description)) {
      messageText.text = linkPreview.description;
    } else if (!StringUtils.isEmpty(linkPreview.siteName) && !StringUtils.isEmpty(linkPreview.title)) {
      messageText.text = new TdApi.FormattedText(linkPreview.title, null);
    }
  }

  public void addReference (RunnableData<LinkPreview> callback) {
    loadCallbacks.add(callback);
  }

  public void removeReference (RunnableData<LinkPreview> callback) {
    loadCallbacks.remove(callback);
  }

  @Override
  public void performDestroy () {
    isDestroyed = true;
    needLoadWebPagePreview = false;
    linkPreviewLoader.cancelIfScheduled();
    loadCallbacks.clear();
  }

  private void loadLinkPreview () {
    needLoadWebPagePreview = false;
    tdlib.send(new TdApi.GetLinkPreview(new TdApi.FormattedText(url, null), null), (webPage, error) -> {
      tdlib.ui().post(() -> {
        if (webPage != null) {
          this.linkPreview = webPage;
          updateMessageText((TdApi.MessageText) fakeMessage.content, webPage);
        } else {
          this.error = error;
        }
        notifyLinkPreviewLoaded();
      });
    });
  }

  public boolean isNotFound () {
    return error != null;
  }

  public boolean isLoading () {
    return error == null && linkPreview == null;
  }

  public boolean hasMedia () {
    return linkPreview != null && MediaPreview.hasMedia(linkPreview);
  }

  public boolean forceSmallMedia () {
    return forceSmallMedia;
  }

  public boolean forceLargeMedia () {
    return forceLargeMedia;
  }

  public boolean toggleLargeMedia () {
    boolean showLargeMedia = getOutputShowLargeMedia();
    if (hasMedia() && linkPreview.hasLargeMedia) {
      forceLargeMedia = !showLargeMedia;
      forceSmallMedia = showLargeMedia;
      return getOutputShowLargeMedia() != showLargeMedia;
    }
    return false;
  }

  public boolean getOutputShowLargeMedia () {
    if (hasMedia()) {
      if (linkPreview.hasLargeMedia) {
        if (forceLargeMedia) {
          return true;
        }
        if (forceSmallMedia) {
          return false;
        }
      }
      return linkPreview.showLargeMedia;
    }
    return false;
  }

  public TdApi.Message getFakeMessage () {
    return fakeMessage;
  }

  public String getForcedTitle () {
    if (isLoading()) {
      return Lang.getString(R.string.GettingLinkInfo);
    } else if (isNotFound()) {
      return Lang.getString(R.string.NoLinkInfo);
    }

    String title = Td.isEmpty(linkPreview.description) ? Strings.any(linkPreview.siteName, linkPreview.title) : Strings.any(linkPreview.title, linkPreview.siteName);
    if (!StringUtils.isEmpty(title)) {
      return title;
    }
    return TdExt.getRepresentationTitle(linkPreview);
  }

  private void notifyLinkPreviewLoaded () {
    if (isDestroyed) {
      return;
    }
    for (RunnableData<LinkPreview> callback : loadCallbacks) {
      callback.runWithData(this);
    }
    for (int index = staticLoadCallbacks.size() - 1; index >= 0; index--) {
      staticLoadCallbacks.get(index).runWithData(this);
    }
  }
}
