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
 * File created on 21/11/2016
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.loader.AvatarReceiver;
import org.thunderdog.challegram.loader.ComplexReceiver;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.loader.Receiver;
import org.thunderdog.challegram.loader.gif.GifReceiver;
import org.thunderdog.challegram.navigation.RtlCheckListener;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.core.lambda.Destroyable;

public class DoubleTextView extends RelativeLayout implements RtlCheckListener, Destroyable {
  private final TextView titleView, subtitleView;
  private final ComplexReceiver receiver;
  private @Nullable NonMaterialButton button;

  private boolean ignoreStartOffset;
  private int currentStartOffset;

  @Override
  public void checkRtl () {
    if (titleView.getGravity() != Lang.gravity())
      titleView.setGravity(Lang.gravity());
    if (subtitleView.getGravity() != Lang.gravity())
      subtitleView.setGravity(Lang.gravity());
    int leftMargin = Screen.dp(72f) - (ignoreStartOffset ? currentStartOffset / 2 : 0);
    int rightMargin = Screen.dp(16f);
    updateLayoutParams(titleView, leftMargin, rightMargin, Screen.dp(15f));
    updateLayoutParams(subtitleView, leftMargin, rightMargin, Screen.dp(38f));
  }

  private static void updateLayoutParams (View view, int leftMargin, int rightMargin, int topMargin) {
    if (Views.setMargins(view, Lang.rtl() ? rightMargin : leftMargin, topMargin, Lang.rtl() ? leftMargin : rightMargin, 0)) {
      RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();
      Views.removeRule(params, Lang.rtl() ? RelativeLayout.LEFT_OF : RelativeLayout.RIGHT_OF);
      params.addRule(Lang.rtl() ? RelativeLayout.RIGHT_OF : RelativeLayout.LEFT_OF, R.id.btn_double);
      Views.updateLayoutParams(view);
    }
  }

  public void ignoreStartOffset (boolean value) {
    this.ignoreStartOffset = value;
    checkRtl();
    invalidate();
  }

  public DoubleTextView (Context context) {
    super(context);

    int viewHeight = Screen.dp(72f);
    setPadding(0, Math.max(1, Screen.dp(.5f)), 0, 0);
    setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewHeight));

    RelativeLayout.LayoutParams params;

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(15f);
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(72f);
      params.leftMargin = Screen.dp(16f);
      params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_double);
    } else {
      params.leftMargin = Screen.dp(72f);
      params.rightMargin = Screen.dp(16f);
      params.addRule(RelativeLayout.LEFT_OF, R.id.btn_double);
    }

    titleView = new EmojiTextView(context);
    titleView.setScrollDisabled(true);
    titleView.setId(R.id.text_title);
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    titleView.setTypeface(Fonts.getRobotoMedium());
    titleView.setTextColor(Theme.textAccentColor());
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    titleView.setSingleLine(true);
    titleView.setLayoutParams(params);
    titleView.setGravity(Lang.gravity());

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = Screen.dp(38f);
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(72f);
      params.leftMargin = Screen.dp(16f);
      params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_double);
    } else {
      params.leftMargin = Screen.dp(72f);
      params.rightMargin = Screen.dp(16f);
      params.addRule(RelativeLayout.LEFT_OF, R.id.btn_double);
    }

    subtitleView = new EmojiTextView(context);
    subtitleView.setScrollDisabled(true);
    subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
    subtitleView.setTextColor(Theme.textDecentColor());
    subtitleView.setTypeface(Fonts.getRobotoRegular());
    subtitleView.setEllipsize(TextUtils.TruncateAt.END);
    subtitleView.setSingleLine(true);
    subtitleView.setGravity(Lang.gravity());
    subtitleView.setLayoutParams(params);

    int imageSize = viewHeight - Screen.dp(12f) * 2;
    int offset = currentStartOffset = viewHeight / 2 - imageSize / 2;

    this.receiver = new ComplexReceiver(this);

    addView(titleView);
    addView(subtitleView);
    setWillNotDraw(false);
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    layoutReceiver(receiver.getImageReceiver(0));
    layoutReceiver(receiver.getGifReceiver(0));
    layoutReceiver(receiver.getAvatarReceiver(0));
  }

  private void layoutReceiver (Receiver receiver) {
    int viewHeight = Screen.dp(72f);
    int imageSize = viewHeight - Screen.dp(12f) * 2;
    int offset = currentStartOffset = viewHeight / 2 - imageSize / 2;
    int startOffset = ignoreStartOffset ? offset / 2 : offset;
    if (Lang.rtl()) {
      int x = getMeasuredWidth() - startOffset - imageSize;
      receiver.setBounds(x, offset, x + imageSize, offset + imageSize);
    } else {
      receiver.setBounds(startOffset, offset, startOffset + imageSize, offset + imageSize);
    }
  }

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeTextAccentColorListener(titleView);
      themeProvider.addThemeTextDecentColorListener(subtitleView);
      themeProvider.addThemeInvalidateListener(this);
      themeProvider.addThemeInvalidateListener(button);
    }
  }

  private boolean isRounded;

  public void setIsRounded (boolean isRounded) {
    if (this.isRounded != isRounded) {
      this.isRounded = isRounded;
      receiver.getAvatarReceiver(0).setFullScreen(!isRounded, false);
    }
  }

  private void checkButton () {
    if (button == null) {
      RelativeLayout.LayoutParams params;
      params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(28f));
      params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
      params.addRule(RelativeLayout.CENTER_VERTICAL);
      params.rightMargin = params.leftMargin = Screen.dp(19f);

      button = new NonMaterialButton(getContext());
      button.setId(R.id.btn_double);
      button.setLayoutParams(params);

      addView(button);
    }
  }

  public void setButton (@StringRes int string, View.OnClickListener onClickListener) {
    checkButton();
    button.setText(string);
    button.setOnClickListener(onClickListener);
  }

  public void setIcon (@DrawableRes int icon, View.OnClickListener onClickListener) {
    checkButton();
    button.setIcon(icon);
    button.setOnClickListener(onClickListener);
    button.setPadding(Screen.dp(6f), 0, Screen.dp(6f), 0);
  }

  public @Nullable NonMaterialButton getButton () {
    return button;
  }

  @Override
  public void performDestroy () {
    receiver.performDestroy();
  }

  public void attach () {
    receiver.attach();
  }

  public void detach () {
    receiver.detach();
  }

  private @Nullable TGStickerSetInfo stickerSetInfo;
  private @Nullable Path stickerSetContour;
  private boolean useAvatarReceiver;

  public void setStickerSet (@NonNull TGStickerSetInfo stickerSet) {
    needPlaceholder = false;
    titleView.setText(stickerSet.getTitle());
    subtitleView.setText(Lang.plural(stickerSet.isMasks() ? R.string.xMasks : R.string.xStickers, stickerSet.getSize()));
    receiver.getImageReceiver(0).requestFile(stickerSet.getPreviewImage());
    receiver.getGifReceiver(0).requestFile(stickerSet.getPreviewAnimation());
    receiver.getAvatarReceiver(0).clear();
    stickerSetContour = stickerSet.getPreviewContour(Screen.dp(72f) - Screen.dp(12f) * 2);
    stickerSetInfo = stickerSet;
  }

  private boolean needPlaceholder;

  public void setText (CharSequence title, CharSequence subtitle) {
    titleView.setText(title);
    subtitleView.setText(subtitle);
  }

  public void setTitleColorId (@ThemeColorId int colorId) {
    titleView.setTextColor(Theme.getColor(colorId));
  }

  public void setUserAvatar (Tdlib tdlib, long userId) {
    useAvatarReceiver = true;
    receiver.getAvatarReceiver(0).requestUser(tdlib, userId, AvatarReceiver.Options.SHOW_ONLINE);
    receiver.getGifReceiver(0).clear();
    receiver.getImageReceiver(0).clear();
  }

  public void setSenderAvatar (Tdlib tdlib, TdApi.MessageSender sender) {
    useAvatarReceiver = true;
    receiver.getAvatarReceiver(0).requestMessageSender(tdlib, sender, AvatarReceiver.Options.SHOW_ONLINE);
    receiver.getGifReceiver(0).clear();
    receiver.getImageReceiver(0).clear();
  }

  public void setChatAvatar (Tdlib tdlib, long chatId) {
    useAvatarReceiver = true;
    receiver.getAvatarReceiver(0).requestChat(tdlib, chatId, AvatarReceiver.Options.SHOW_ONLINE);
    receiver.getGifReceiver(0).clear();
    receiver.getImageReceiver(0).clear();
  }

  @Override
  protected void onDraw (Canvas c) {
    if (useAvatarReceiver) {
      AvatarReceiver avatarReceiver = receiver.getAvatarReceiver(0);
      if (avatarReceiver.needPlaceholder()) {
        avatarReceiver.drawPlaceholder(c);
      }
      avatarReceiver.draw(c);
    } else if (stickerSetInfo != null && stickerSetInfo.isAnimated()) {
      GifReceiver gifReceiver = receiver.getGifReceiver(0);
      if (gifReceiver.needPlaceholder()) {
        gifReceiver.drawPlaceholderContour(c, stickerSetContour);
      }
      gifReceiver.draw(c);
    } else {
      ImageReceiver imageReceiver = receiver.getImageReceiver(0);
      if (imageReceiver.needPlaceholder()) {
        if (stickerSetContour != null) {
          imageReceiver.drawPlaceholderContour(c, stickerSetContour);
        } else if (needPlaceholder) {
          imageReceiver.drawPlaceholderRounded(c, imageReceiver.getWidth() / 2f);
        }
      }
      imageReceiver.draw(c);
    }
    if (stickerSetInfo != null && stickerSetInfo.needSeparatorOnTop()) {
      int height = Math.max(1, Screen.dp(.5f));
      int offset = Screen.dp(72f);
      if (Lang.rtl()) {
        int viewWidth = getMeasuredWidth();
        c.drawRect(viewWidth - offset, 0, viewWidth, height, Paints.fillingPaint(Theme.fillingColor()));
        c.drawRect(0, 0, viewWidth - offset, height, Paints.fillingPaint(Theme.separatorColor()));
      } else {
        c.drawRect(0, 0, offset, height, Paints.fillingPaint(Theme.fillingColor()));
        c.drawRect(offset, 0, getMeasuredWidth(), height, Paints.fillingPaint(Theme.separatorColor()));
      }
    }
  }
}
