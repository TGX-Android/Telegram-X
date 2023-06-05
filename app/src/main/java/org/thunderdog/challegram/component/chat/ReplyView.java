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
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGWebPage;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.Destroyable;
import me.vkryl.td.Td;

public class ReplyView extends FrameLayoutFix implements View.OnClickListener, Destroyable, InvalidateContentProvider {
  private int startX;
  private int startY;

  private final DoubleImageReceiver receiver;
  private final ComplexReceiver textMediaReceiver;

  private ReplyComponent reply;
  private Callback callback;

  public ReplyView (Context context, Tdlib tdlib) {
    super(context);

    setWillNotDraw(false);

    startX = Screen.dp(60f);
    startY = Screen.dp(7f);

    receiver = new DoubleImageReceiver(this, 0);
    textMediaReceiver = new ComplexReceiver(this, Config.MAX_ANIMATED_EMOJI_REFRESH_RATE);

    reply = new ReplyComponent(tdlib);
    reply.setCurrentView(this);
  }

  public ComplexReceiver getTextMediaReceiver () {
    return textMediaReceiver;
  }

  @Override
  public void onClick (View v) {
    if (callback != null) {
      callback.onCloseReply(this);
    }
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    layoutIfNeeded();
  }

  private int lastMeasuredWidth;

  private void layoutIfNeeded () {
    int width = getMeasuredWidth();
    if (lastMeasuredWidth != width) {
      lastMeasuredWidth = width;
      reply.layout(width - startX - Screen.dp(12f));
    }
  }

  @Override
  public boolean invalidateContent (Object cause) {
    if (reply == cause) {
      reply.requestPreview(receiver, textMediaReceiver);
      return true;
    }
    return false;
  }

  @Override
  protected void onDraw (Canvas c) {
    reply.draw(c, startX, startY, getMeasuredWidth() - startX, reply.width(false), receiver, textMediaReceiver, Lang.rtl());
  }

  ImageView closeView;

  public void checkRtl () {
    if (Views.setGravity(closeView, Lang.gravity())) {
      Views.updateLayoutParams(closeView);
    }
    invalidate();
  }

  public void initWithCallback (Callback callbacK, ViewController<?> themeProvider) {
    this.callback = callbacK;

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.gravity();

    closeView = new ImageView(getContext());
    closeView.setImageResource(R.drawable.baseline_close_24);
    closeView.setColorFilter(Theme.iconColor());
    themeProvider.addThemeFilterListener(closeView, ColorId.icon);
    closeView.setScaleType(ImageView.ScaleType.CENTER);
    closeView.setLayoutParams(params);
    closeView.setOnClickListener(this);
    Views.setClickable(closeView);
    closeView.setBackgroundResource(R.drawable.bg_btn_header);

    addView(closeView);
    themeProvider.addThemeInvalidateListener(this);
  }

  public void setReplyTo (TdApi.Message msg, @Nullable CharSequence forcedTitle) {
    layoutIfNeeded();
    reply.set(forcedTitle, msg);
    invalidate();
  }

  public void setPinnedMessage (TdApi.Message msg) {
    layoutIfNeeded();
    reply.set(Lang.getString(R.string.PinnedMessage), msg,true);
    invalidate();
  }

  public ReplyComponent getReply () {
    return reply;
  }

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return reply.onTouchEvent(this, event) || super.onTouchEvent(event);
  }

  public void setWebPage (String link, TdApi.WebPage page) {
    layoutIfNeeded();
    if (page == null) {
      reply.set(Lang.getString(R.string.GettingLinkInfo), new TD.ContentPreview(link, false), null, null);
    } else {
      String title = Strings.any(page.title, page.siteName);
      if (StringUtils.isEmpty(title)) {
        if (page.photo != null || (page.sticker != null && Math.max(page.sticker.width, page.sticker.height) > TGWebPage.STICKER_SIZE_LIMIT)) {
          title = Lang.getString(R.string.Photo);
        } else if (page.video != null) {
          title = Lang.getString(R.string.Video);
        } else if (page.document != null || page.voiceNote != null) {
          title = page.document != null ? page.document.fileName : Lang.getString(R.string.Audio);
          if (StringUtils.isEmpty(title)) {
            title = Lang.getString(R.string.File);
          }
        } else if (page.audio != null) {
          title = TD.getTitle(page.audio) + " – " + TD.getSubtitle(page.audio);
        } else if (page.sticker != null) {
          title = Lang.getString(R.string.Sticker);
        } else {
          title = Lang.getString(R.string.LinkPreview);
        }
      }
      String desc = !Td.isEmpty(page.description) ? page.description.text : page.displayUrl;
      reply.set(title, new TD.ContentPreview(desc, false), page.photo != null ? page.photo.minithumbnail : null, TD.getWebPagePreviewImage(page));
    }
    invalidate();
  }

  public void clear () {
    receiver.clear();
    textMediaReceiver.clear();
  }

  @Override
  public void performDestroy () {
    receiver.destroy();
    textMediaReceiver.performDestroy();
    reply.performDestroy();
  }

  public interface Callback {
    void onCloseReply (ReplyView view);
  }
}
