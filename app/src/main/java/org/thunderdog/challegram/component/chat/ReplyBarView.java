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
 * File created on 21/02/2016 at 21:08
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.MediaToReplacePickerManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.helper.FoundUrls;
import org.thunderdog.challegram.helper.LinkPreview;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.widget.LinkPreviewToggleView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.Td;
import tgx.td.data.MessageWithProperties;

public class ReplyBarView extends FrameLayoutFix implements View.OnClickListener, Destroyable {
  protected final Tdlib tdlib;
  private Callback callback;

  public ReplyBarView (Context context, Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
  }

  @Override
  public void onClick (View v) {
    if (callback != null) {
      final int id = v.getId();
      if (id == R.id.btn_close) {
        callback.onDismissReplyBar(this);
      } else if (id == R.id.btn_replace) {
        callback.onMessageMediaReplaceRequested(this, getMessage());
      } else if (id == R.id.btn_edit) {
        callback.onMessageMediaEditRequested(this, getMessage());
      }
    }
  }

  private TdApi.Message getMessage () {
    return displayedMessage != null ? displayedMessage.message : null;
  }

  ImageView closeView;
  ImageView replaceMediaView;
  ImageView editMediaView;
  LinkPreviewToggleView linkPreviewToggleView;

  public void checkRtl () {
    if (Views.setGravity(closeView, Lang.gravity())) {
      Views.updateLayoutParams(closeView);
    }
    if (Views.setGravity(closeView, Lang.reverseGravity())) {
      Views.updateLayoutParams(closeView);
    }
    invalidate();
  }

  private PinnedMessagesBar pinnedMessagesBar;

  public void initWithCallback (Callback callbacK, ViewController<?> themeProvider) {
    this.callback = callbacK;
    themeProvider.addThemeInvalidateListener(this);

    FrameLayoutFix.LayoutParams params;

    pinnedMessagesBar = new PinnedMessagesBar(getContext(), false);
    pinnedMessagesBar.setPadding(Screen.dp(49.5f), 0, 0, 0);
    pinnedMessagesBar.setCollapseButtonVisible(false);
    pinnedMessagesBar.setIgnoreAlbums(true);
    pinnedMessagesBar.setMessageListener(new PinnedMessagesBar.MessageListener() {
      @Override
      public void onCreateMessagePreview (PinnedMessagesBar view, MessagePreviewView previewView) {
        ViewUtils.setBackground(previewView, null);
        // previewView.setLinePadding(4f);
      }

      @Override
      public void onMessageDisplayed (PinnedMessagesBar view, MessagePreviewView previewView, TdApi.Message message) {
        previewView.clearPreviewChat();
      }

      @Override
      public void onSelectLinkPreviewUrl (PinnedMessagesBar view, MessagesController.MessageInputContext messageContext, String url) {
        if (callback != null) {
          callback.onSelectLinkPreviewUrl(ReplyBarView.this, messageContext, url);
        }
        updateLinkPreviewSettings(true);
      }

      @Override
      public boolean onToggleLargeMedia (PinnedMessagesBar view, MessagePreviewView previewView, MessagesController.MessageInputContext messageContext, LinkPreview linkPreview) {
        if (callback != null && callback.onRequestToggleLargeMedia(ReplyBarView.this, previewView, messageContext, linkPreview)) {
          updateLinkPreviewSettings(true);
          return true;
        }
        return false;
      }

      @Override
      public void onMessageClick (PinnedMessagesBar view, TdApi.Message message, @Nullable TdApi.InputTextQuote quote) {
        if (callback != null) {
          callback.onMessageHighlightRequested(ReplyBarView.this, message, quote);
        }
      }
    });
    pinnedMessagesBar.setAnimationsDisabled(animationsDisabled);
    pinnedMessagesBar.initialize(themeProvider);
    addView(pinnedMessagesBar);

    params = FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.gravity();
    closeView = newButton(R.id.btn_close, R.drawable.baseline_close_24, themeProvider);
    closeView.setLayoutParams(params);
    addView(closeView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.reverseGravity();
    replaceMediaView = newButton(R.id.btn_replace, R.drawable.dot_baseline_image_replace_24, themeProvider);
    replaceMediaView.setLayoutParams(params);
    replaceMediaView.setVisibility(View.GONE);
    addView(replaceMediaView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.reverseGravity();
    params.rightMargin = Screen.dp(46);
    editMediaView = newButton(R.id.btn_edit, R.drawable.baseline_brush_24, themeProvider);
    editMediaView.setLayoutParams(params);
    editMediaView.setVisibility(View.GONE);
    addView(editMediaView);

    params = FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.reverseGravity();
    linkPreviewToggleView = new LinkPreviewToggleView(getContext());
    linkPreviewToggleView.setLayoutParams(params);
    linkPreviewToggleView.setVisibility(View.GONE);
    linkPreviewToggleView.addThemeListeners(themeProvider);
    linkPreviewToggleView.setOnClickListener(v -> {
      if (callback != null && inputContext != null && callback.onRequestToggleShowAbove(ReplyBarView.this, v, inputContext)) {
        updateLinkPreviewSettings(true);
      }
    });
    Views.setClickable(linkPreviewToggleView);
    linkPreviewToggleView.setBackgroundResource(R.drawable.bg_btn_header);
    addView(linkPreviewToggleView);
  }

  private boolean animationsDisabled;

  public void setAnimationsDisabled (boolean animationsDisabled) {
    if (this.animationsDisabled != animationsDisabled) {
      this.animationsDisabled = animationsDisabled;
      if (pinnedMessagesBar != null) {
        pinnedMessagesBar.setAnimationsDisabled(animationsDisabled);
      }
    }
  }

  public boolean areAnimationsDisabled () {
    return animationsDisabled;
  }

  private ImageView newButton (@IdRes int idRes, @DrawableRes int iconRes, ViewController<?> themeProvider) {
    ImageView btn = new ImageView(getContext());
    btn.setId(idRes);
    btn.setImageResource(iconRes);
    btn.setColorFilter(Theme.iconColor());
    themeProvider.addThemeFilterListener(btn, ColorId.icon);
    btn.setScaleType(ImageView.ScaleType.CENTER);
    btn.setOnClickListener(this);
    Views.setClickable(btn);
    btn.setBackgroundResource(R.drawable.bg_btn_header);
    return btn;
  }

  private void updateLinkPreviewSettings (boolean animated) {
    LinkPreview linkPreview = inputContext != null ? inputContext.getSelectedLinkPreview() : null;
    setPendingLinkPreview(linkPreview != null && linkPreview.isLoading() ? linkPreview : null);
    if (linkPreview != null) {
      TdApi.LinkPreviewOptions options = inputContext.takeOutputLinkPreviewOptions(false);
      @LinkPreviewToggleView.MediaVisibility int mediaState;
      if (!linkPreview.hasMedia()) {
        mediaState = LinkPreviewToggleView.MediaVisibility.NONE;
      } else {
        mediaState = linkPreview.getOutputShowLargeMedia() ? LinkPreviewToggleView.MediaVisibility.LARGE : LinkPreviewToggleView.MediaVisibility.SMALL;
      }
      linkPreviewToggleView.setMediaVisibility(mediaState, animated);
      linkPreviewToggleView.setShowAboveText(options.showAboveText, animated);
    }
  }

  private void setLinkPreviewToggleVisible (boolean isVisible) {
    if (isVisible != (linkPreviewToggleView.getVisibility() == View.VISIBLE)) {
      linkPreviewToggleView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
      checkPinnedMessagesBarPadding();
    }
  }

  private void setMediaEditToggleVisible (boolean canReplace, boolean canEdit) {
    boolean needUpdate = false;
    if (canEdit != (editMediaView.getVisibility() == View.VISIBLE)) {
      editMediaView.setVisibility(canEdit ? View.VISIBLE : View.GONE);
      needUpdate = true;
    }
    if (canReplace != (replaceMediaView.getVisibility() == View.VISIBLE)) {
      replaceMediaView.setVisibility(canReplace ? View.VISIBLE : View.GONE);
      needUpdate = true;
    }
    if (needUpdate) {
      checkPinnedMessagesBarPadding();
    }
  }

  private MessagesController.MessageInputContext inputContext;
  private LinkPreview pendingLinkPreview;

  private final RunnableData<LinkPreview> onLinkPreviewLoadListener = linkPreview -> {
    if (linkPreview == pendingLinkPreview) {
      updateLinkPreviewSettings(true);
    }
  };

  private void setPendingLinkPreview (LinkPreview linkPreview) {
    if (this.pendingLinkPreview != linkPreview) {
      if (this.pendingLinkPreview != null) {
        this.pendingLinkPreview.removeLoadCallback(onLinkPreviewLoadListener);
      }
      this.pendingLinkPreview = linkPreview;
      if (linkPreview != null) {
        linkPreview.addLoadCallback(onLinkPreviewLoadListener);
      }
    }
  }

  private void setMessageInputContext (MessagesController.MessageInputContext context) {
    if (this.inputContext != context) {
      if (this.inputContext != null) {
        setPendingLinkPreview(null);
      }
      this.inputContext = context;
      if (context != null) {
        updateLinkPreviewSettings(false);
      } else {
        setPendingLinkPreview(null);
      }
    }
  }

  public void showWebPage (@NonNull MessagesController.MessageInputContext context, int selectedUrlIndex) {
    FoundUrls foundUrls = context.getFoundUrls();
    List<PinnedMessagesBar.Entry> entryList = new ArrayList<>();
    for (String url : foundUrls.urls) {
      entryList.add(new PinnedMessagesBar.Entry(tdlib, context, url));
    }
    pinnedMessagesBar.setStaticMessageList(entryList, selectedUrlIndex);
    setLinkPreviewToggleVisible(true);
    setMediaEditToggleVisible(false, false);
    setMessageInputContext(context);
  }

  private MessageWithProperties displayedMessage;

  public void setReplyTo (MessageWithProperties msg, @Nullable TdApi.InputTextQuote quote) {
    displayedMessage = msg;
    pinnedMessagesBar.setMessage(tdlib, msg != null ? msg.message : null, quote);
    setLinkPreviewToggleVisible(false);
    setMediaEditToggleVisible(false, false);
    setMessageInputContext(null);
  }

  public void setEditingMessage (MessageWithProperties msg, @Nullable MediaToReplacePickerManager.LocalPickedFile localPickedFile) {
    final boolean hasReplacedImage = localPickedFile != null && localPickedFile.imageGalleryFile != null;

    final boolean canReplace = localPickedFile != null || tdlib.canEditMedia(msg, false);
    final boolean canEdit = canReplace && (localPickedFile == null && tdlib.canEditMedia(msg, true) || hasReplacedImage);

    if (msg.message.mediaAlbumId != 0) {
      final boolean usePhotoIcon = Td.isPhoto(msg.message.content) || hasReplacedImage
        || (msg.message.content != null && msg.message.content.getConstructor() == TdApi.MessageVideo.CONSTRUCTOR);

      replaceMediaView.setImageResource(usePhotoIcon ? R.drawable.dot_baseline_image_replace_24 : R.drawable.dot_baseline_file_replace_24);
    } else {
      replaceMediaView.setImageResource(R.drawable.dot_baseline_file_media_replace_24);
    }

    displayedMessage = msg;
    pinnedMessagesBar.setMessage(tdlib, msg.message, null, localPickedFile);
    setLinkPreviewToggleVisible(false);
    setMediaEditToggleVisible(canReplace, canEdit && TD.isFileLoaded(msg.message));
    setMessageInputContext(null);
  }

  public void reset () {
    performDestroy();
  }

  @Override
  public void performDestroy () {
    pinnedMessagesBar.performDestroy();
    setMessageInputContext(null);
  }

  public void completeDestroy () {
    pinnedMessagesBar.completeDestroy();
  }

  public LinkPreviewToggleView getLinkPreviewToggleView () {
    return linkPreviewToggleView;
  }

  public interface Callback {
    void onDismissReplyBar (ReplyBarView view);
    void onMessageMediaReplaceRequested (ReplyBarView view, TdApi.Message message);
    void onMessageMediaEditRequested (ReplyBarView view, TdApi.Message message);
    void onMessageHighlightRequested (ReplyBarView view, TdApi.Message message, @Nullable TdApi.InputTextQuote quote);
    void onSelectLinkPreviewUrl (ReplyBarView view, MessagesController.MessageInputContext messageContext, String url);
    boolean onRequestToggleShowAbove (ReplyBarView view, View buttonView, MessagesController.MessageInputContext messageContext);
    boolean onRequestToggleLargeMedia (ReplyBarView view, View buttonView, MessagesController.MessageInputContext messageContext, LinkPreview linkPreview);
  }

  private void checkPinnedMessagesBarPadding () {
    final int buttonsCount = (Views.isValid(linkPreviewToggleView) ? 1 : 0)
      + (Views.isValid(editMediaView) ? 1 : 0)
      + (Views.isValid(replaceMediaView) ? 1 : 0);
    pinnedMessagesBar.setPadding(Screen.dp(49.5f), 0, buttonsCount > 0 ? Screen.dp(buttonsCount * 48f + 1.5f) : 0, 0);
  }
}
