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
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGWebPage;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.util.RateLimiter;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import me.vkryl.core.reference.ReferenceList;
import me.vkryl.td.Td;

public class LinkPreview implements Destroyable {
  public final Tdlib tdlib;
  public final String url;

  public TdApi.WebPage webPage;
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

  public LinkPreview (Tdlib tdlib, String url, @Nullable TdApi.Message existingMessage) {
    this.tdlib = tdlib;
    this.url = url;
    this.linkPreviewLoader.setDelayFirstExecution(true);
    this.needLoadWebPagePreview = true;

    TdApi.MessageText messageText = new TdApi.MessageText(new TdApi.FormattedText(url, null), null, null);
    TdApi.MessageSender messageSender = existingMessage != null ? existingMessage.senderId : new TdApi.MessageSenderUser(tdlib.myUserId());

    if (existingMessage != null && Td.isText(existingMessage.content)) {
      TdApi.MessageText existingText = (TdApi.MessageText) existingMessage.content;
      if (existingText.webPage != null) {
        boolean isCurrentWebPage =
          (existingText.linkPreviewOptions != null && !StringUtils.isEmpty(existingText.linkPreviewOptions.url) && FoundUrls.compareUrls(existingText.linkPreviewOptions.url, url)) ||
          (FoundUrls.compareUrls(existingText.webPage.url, url));
        if (isCurrentWebPage) {
          this.webPage = existingText.webPage;
          updateMessageText(messageText, this.webPage);
          this.needLoadWebPagePreview = false;
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

  private static void updateMessageText (TdApi.MessageText messageText, TdApi.WebPage webPage) {
    messageText.webPage = webPage;
    if (!Td.isEmpty(webPage.description)) {
      messageText.text = webPage.description;
    } else if (!StringUtils.isEmpty(webPage.siteName) && !StringUtils.isEmpty(webPage.title)) {
      messageText.text = new TdApi.FormattedText(webPage.title, null);
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
    tdlib.send(new TdApi.GetWebPagePreview(new TdApi.FormattedText(url, null), null), (webPage, error) -> {
      tdlib.ui().post(() -> {
        if (webPage != null) {
          this.webPage = webPage;
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
    return error == null && webPage == null;
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

    String title = Td.isEmpty(webPage.description) ? Strings.any(webPage.siteName, webPage.title) : Strings.any(webPage.title, webPage.siteName);
    if (!StringUtils.isEmpty(title)) {
      return title;
    }
    if (webPage.photo != null || (webPage.sticker != null && Math.max(webPage.sticker.width, webPage.sticker.height) > TGWebPage.STICKER_SIZE_LIMIT)) {
      return Lang.getString(R.string.Photo);
    } else if (webPage.video != null) {
      return Lang.getString(R.string.Video);
    } else if (webPage.document != null || webPage.voiceNote != null) {
      title = webPage.document != null ? webPage.document.fileName : Lang.getString(R.string.Audio);
      if (StringUtils.isEmpty(title)) {
        title = Lang.getString(R.string.File);
      }
      return title;
    } else if (webPage.audio != null) {
      return TD.getTitle(webPage.audio) + " – " + TD.getSubtitle(webPage.audio);
    } else if (webPage.sticker != null) {
      return Lang.getString(R.string.Sticker);
    }
    return Lang.getString(R.string.LinkPreview);
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
