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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.helper.InlineSearchContext;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.DoubleImageReceiver;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.util.InvalidateContentProvider;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class ReplyView extends FrameLayoutFix implements View.OnClickListener, Destroyable, InvalidateContentProvider {
  private final int startX;
  private final int startY;

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
      final int id = v.getId();
      if (id == R.id.btn_close) {
        callback.onCloseReply(this);
      } else if (id == R.id.btn_toggleEnlarge) {
        callback.onToggleEnlarge(this, v);
      } else if (id == R.id.btn_toggleShowAbove) {
        callback.onToggleShowAbove(this, v);
      } else if (id == R.id.btn_replace) {
        callback.onChooseNextLinkPreview(this, v);
      }
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
    if (enlargeView.getVisibility() == View.VISIBLE) {
      width -= Screen.dp(52f);
    }
    if (showAboveView.getVisibility() == View.VISIBLE) {
      width -= Screen.dp(52f);
    }
    if (replaceView.getVisibility() == View.VISIBLE) {
      width -= Screen.dp(52f);
    }
    if (lastMeasuredWidth != width) {
      lastMeasuredWidth = width;
      reply.layout(width - startX - Screen.dp(12f));
    }
    invalidate();
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

  ImageView closeView, enlargeView, showAboveView, replaceView;
  LinearLayout buttonsLayout;

  public void checkRtl () {
    if (Views.setGravity(closeView, Lang.gravity())) {
      Views.updateLayoutParams(closeView);
    }
    invalidate();
  }

  public void initWithCallback (Callback callbacK, ViewController<?> themeProvider) {
    this.callback = callbacK;
    themeProvider.addThemeInvalidateListener(this);

    FrameLayoutFix.LayoutParams params;

    params = FrameLayoutFix.newParams(Screen.dp(56f), ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.gravity();
    closeView = newButton(R.id.btn_close, R.drawable.baseline_close_24, themeProvider);
    closeView.setLayoutParams(params);
    addView(closeView);

    buttonsLayout = new LinearLayout(getContext());
    buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
    params.gravity = Lang.rtl() ? Gravity.LEFT : Gravity.RIGHT;
    buttonsLayout.setLayoutParams(params);
    addView(buttonsLayout);

    LinearLayout.LayoutParams lp;

    lp = new LinearLayout.LayoutParams(Screen.dp(52f), ViewGroup.LayoutParams.MATCH_PARENT);
    enlargeView = newButton(R.id.btn_toggleEnlarge, R.drawable.baseline_arrow_expand_24, themeProvider);
    enlargeView.setLayoutParams(lp);
    enlargeView.setVisibility(View.GONE);
    buttonsLayout.addView(enlargeView);

    lp = new LinearLayout.LayoutParams(Screen.dp(52f), ViewGroup.LayoutParams.MATCH_PARENT);
    showAboveView = newButton(R.id.btn_toggleShowAbove, R.drawable.baseline_arrow_collapse_down_24, themeProvider);
    showAboveView.setLayoutParams(lp);
    showAboveView.setVisibility(View.GONE);
    buttonsLayout.addView(showAboveView);

    lp = new LinearLayout.LayoutParams(Screen.dp(52f), ViewGroup.LayoutParams.MATCH_PARENT);
    replaceView = newButton(R.id.btn_replace, R.drawable.baseline_find_replace_24, themeProvider);
    replaceView.setLayoutParams(lp);
    replaceView.setVisibility(View.GONE);
    buttonsLayout.addView(replaceView);


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

  @Override
  public boolean onTouchEvent (MotionEvent event) {
    return reply.onTouchEvent(this, event) || super.onTouchEvent(event);
  }

  private InlineSearchContext.LinkPreview linkPreview;

  public void setWebPage (@NonNull InlineSearchContext.LinkPreview linkPreview) {
    this.linkPreview = linkPreview;
    updateVisibility();
    layoutIfNeeded();
    reply.set(linkPreview);
    invalidate();
  }

  private void updateVisibility () {
    showAboveView.setVisibility(linkPreview != null && linkPreview.isValid() ? View.VISIBLE : View.GONE);
    if (linkPreview != null && linkPreview.isValid() && linkPreview.webPage.hasLargeMedia) {
      enlargeView.setVisibility(View.VISIBLE);
      enlargeView.setImageResource(linkPreview.isBigMedia() ? R.drawable.baseline_arrow_expand_24 : R.drawable.baseline_arrow_collapse_24);
    } else {
      enlargeView.setVisibility(View.GONE);
    }
    if (linkPreview != null && (linkPreview.isValid() || (linkPreview.hasAlternatives() && !linkPreview.isNotFound()))) {
      showAboveView.setVisibility(View.VISIBLE);
      showAboveView.setImageResource(linkPreview.showAboveText() ? R.drawable.baseline_arrow_collapse_up_24 : R.drawable.baseline_arrow_collapse_down_24);
    } else {
      showAboveView.setVisibility(View.GONE);
    }
    replaceView.setVisibility(linkPreview != null && linkPreview.hasAlternatives() ? View.VISIBLE : View.GONE);
  }

  public void setReplyTo (TdApi.Message msg, @Nullable CharSequence forcedTitle) {
    this.linkPreview = null;
    updateVisibility();
    layoutIfNeeded();
    reply.set(forcedTitle, msg);
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
    void onToggleEnlarge (ReplyView view, View clickedView);
    void onToggleShowAbove (ReplyView view, View clickedView);
    void onChooseNextLinkPreview (ReplyView view, View clickedView);
  }
}
