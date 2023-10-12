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
 * File created on 18/08/2023
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStickerSetInfo;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.PorterDuffPaint;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.text.Highlight;

public class TrendingPackHeaderView extends RelativeLayout {
  private final android.widget.TextView newView;
  private final NonMaterialButton button;
  private final TextView titleView;
  private final TextView subtitleView;
  private final View premiumLockIcon;
  private final Drawable lockDrawable;

  public TrendingPackHeaderView (Context context) {
    super(context);

    lockDrawable = Drawables.get(R.drawable.baseline_lock_16);

    RelativeLayout.LayoutParams params;
    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(16f));
    params.addRule(Lang.alignParent());
    if (Lang.rtl()) {
      params.leftMargin = Screen.dp(6f);
    } else {
      params.rightMargin = Screen.dp(6f);
    }
    params.topMargin = Screen.dp(3f);
    newView = new NoScrollTextView(context);
    newView.setId(R.id.btn_new);
    newView.setSingleLine(true);
    newView.setPadding(Screen.dp(4f), Screen.dp(1f), Screen.dp(4f), 0);
    newView.setTextColor(Theme.getColor(ColorId.promoContent));
    newView.setTypeface(Fonts.getRobotoBold());
    newView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f);
    newView.setText(Lang.getString(R.string.New).toUpperCase());
    newView.setLayoutParams(params);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(28f));
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(16f);
    } else {
      params.leftMargin = Screen.dp(16f);
    }
    params.topMargin = Screen.dp(5f);
    params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
    button = new NonMaterialButton(context);
    button.setId(R.id.btn_addStickerSet);
    button.setText(R.string.Add);
    button.setLayoutParams(params);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Screen.dp(28f));
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(16f);
    } else {
      params.leftMargin = Screen.dp(16f);
    }
    params.topMargin = Screen.dp(5f);
    params.addRule(Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
    params.width = params.height = Screen.dp(16);
    premiumLockIcon = new View(context) {
      @Override
      protected void dispatchDraw (Canvas canvas) {
        super.dispatchDraw(canvas);
        Drawables.draw(canvas, lockDrawable, 0, 0, PorterDuffPaint.get(ColorId.text));
      }
    };
    premiumLockIcon.setId(R.id.btn_addStickerSet);
    premiumLockIcon.setLayoutParams(params);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    if (Lang.rtl()) {
      params.leftMargin = Screen.dp(12f);
      params.addRule(RelativeLayout.LEFT_OF, R.id.btn_new);
      params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_addStickerSet);
    } else {
      params.rightMargin = Screen.dp(12f);
      params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_new);
      params.addRule(RelativeLayout.LEFT_OF, R.id.btn_addStickerSet);
    }
    titleView = new NoScrollTextView(context);
    titleView.setTypeface(Fonts.getRobotoMedium());
    titleView.setTextColor(Theme.textAccentColor());
    titleView.setGravity(Lang.gravity());
    titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
    titleView.setSingleLine(true);
    titleView.setEllipsize(TextUtils.TruncateAt.END);
    titleView.setLayoutParams(params);

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    if (Lang.rtl()) {
      params.leftMargin = Screen.dp(12f);
      params.addRule(RelativeLayout.LEFT_OF, R.id.btn_new);
      params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_addStickerSet);
    } else {
      params.rightMargin = Screen.dp(12f);
      params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_new);
      params.addRule(RelativeLayout.LEFT_OF, R.id.btn_addStickerSet);
    }

    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.addRule(Lang.alignParent());
    params.topMargin = Screen.dp(22f);
    subtitleView = new NoScrollTextView(context);
    subtitleView.setTypeface(Fonts.getRobotoRegular());
    subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
    subtitleView.setTextColor(Theme.textDecentColor());
    subtitleView.setSingleLine(true);
    subtitleView.setEllipsize(TextUtils.TruncateAt.END);
    subtitleView.setLayoutParams(params);

    addView(newView);
    addView(button);
    addView(premiumLockIcon);
    addView(titleView);
    addView(subtitleView);
  }

  public void setButtonOnClickListener (View.OnClickListener listener) {
    button.setOnClickListener(listener);
    premiumLockIcon.setOnClickListener(listener);
  }

  public void setThemeProvider (@Nullable ViewController<?> themeProvider) {
    if (themeProvider != null) {
      themeProvider.addThemeTextColorListener(newView, ColorId.promoContent);
      themeProvider.addThemeInvalidateListener(newView);
      themeProvider.addThemeInvalidateListener(this);
      themeProvider.addThemeInvalidateListener(button);
      themeProvider.addThemeInvalidateListener(premiumLockIcon);
      themeProvider.addThemeTextAccentColorListener(titleView);
      themeProvider.addThemeTextDecentColorListener(subtitleView);
      ViewSupport.setThemedBackground(newView, ColorId.promo, themeProvider).setCornerRadius(3f);
    }
  }

  public void setStickerSetInfo (TdlibDelegate context, @Nullable TGStickerSetInfo stickerSet, @Nullable String highlight, boolean isInProgress, boolean isNew) {
    setTag(stickerSet);

    boolean needLock = stickerSet != null && stickerSet.isEmoji() && !stickerSet.isInstalled() && !context.tdlib().account().isPremium();

    newView.setVisibility(!isNew ? View.GONE : View.VISIBLE);
    button.setVisibility(needLock ? GONE : VISIBLE);
    button.setInProgress(stickerSet != null && !stickerSet.isRecent() && isInProgress, false);
    button.setIsDone(stickerSet != null && stickerSet.isInstalled(), false);
    button.setTag(stickerSet);

    premiumLockIcon.setVisibility(needLock ? VISIBLE : GONE);
    premiumLockIcon.setTag(stickerSet);

    Views.setMediumText(titleView, Highlight.toSpannable(stickerSet != null ? stickerSet.getTitle() : "", highlight));
    subtitleView.setText(stickerSet != null ? Lang.plural(stickerSet.isEmoji() ? R.string.xEmoji : R.string.xStickers, stickerSet.getFullSize()) : "");

    if (Views.setAlignParent(newView, Lang.rtl())) {
      int rightMargin = Screen.dp(6f);
      int topMargin = Screen.dp(3f);
      Views.setMargins(newView, Lang.rtl() ? rightMargin : 0, topMargin, Lang.rtl() ? 0 : rightMargin, 0);
      Views.updateLayoutParams(newView);
    }

    if (Views.setAlignParent(button, Lang.rtl() ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT)) {
      int leftMargin = Screen.dp(16f);
      int topMargin = Screen.dp(5f);
      Views.setMargins(button, Lang.rtl() ? 0 : leftMargin, topMargin, Lang.rtl() ? leftMargin : 0, 0);
      Views.updateLayoutParams(button);
    }
    RelativeLayout.LayoutParams params;
    params = (RelativeLayout.LayoutParams) titleView.getLayoutParams();
    if (Lang.rtl()) {
      int leftMargin = Screen.dp(12f);
      if (params.leftMargin != leftMargin) {
        params.leftMargin = leftMargin;
        params.rightMargin = 0;
        params.addRule(RelativeLayout.LEFT_OF, R.id.btn_new);
        params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_addStickerSet);
        Views.updateLayoutParams(titleView);
      }
    } else {
      int rightMargin = Screen.dp(12f);
      if (params.rightMargin != rightMargin) {
        params.rightMargin = rightMargin;
        params.leftMargin = 0;
        params.addRule(RelativeLayout.RIGHT_OF, R.id.btn_new);
        params.addRule(RelativeLayout.LEFT_OF, R.id.btn_addStickerSet);
        Views.updateLayoutParams(titleView);
      }
    }
    Views.setTextGravity(titleView, Lang.gravity());
    if (Views.setAlignParent(subtitleView, Lang.rtl())) {
      Views.updateLayoutParams(subtitleView);
    }
  }
}
