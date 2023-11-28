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
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.helper.FoundUrls;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.ui.MessagesController;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.ViewUtils;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

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
      }
    }
  }

  ImageView closeView;

  public void checkRtl () {
    if (Views.setGravity(closeView, Lang.gravity())) {
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
      }

      @Override
      public void onMessageClick (PinnedMessagesBar view, TdApi.Message message, @Nullable TdApi.FormattedText quote) {
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

  public void showWebPage (@NonNull MessagesController.MessageInputContext context, int selectedUrlIndex) {
    FoundUrls foundUrls = context.getFoundUrls();
    List<PinnedMessagesBar.Entry> entryList = new ArrayList<>();
    for (String url : foundUrls.urls) {
      entryList.add(new PinnedMessagesBar.Entry(tdlib, context, url));
    }
    pinnedMessagesBar.setStaticMessageList(entryList, selectedUrlIndex);
  }

  public void setReplyTo (TdApi.Message msg, @Nullable TdApi.FormattedText quote) {
    pinnedMessagesBar.setMessage(tdlib, msg, quote);
  }

  public void setEditingMessage (TdApi.Message msg) {
    pinnedMessagesBar.setMessage(tdlib, msg);
  }

  public void reset () {
    performDestroy();
  }

  @Override
  public void performDestroy () {
    pinnedMessagesBar.performDestroy();
  }

  public void completeDestroy () {
    pinnedMessagesBar.completeDestroy();
  }

  public interface Callback {
    void onDismissReplyBar (ReplyBarView view);
    void onMessageHighlightRequested (ReplyBarView view, TdApi.Message message, @Nullable TdApi.FormattedText quote);
    void onSelectLinkPreviewUrl (ReplyBarView view, MessagesController.MessageInputContext messageContext, String url);
  }
}
