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
 * File created on 08/07/2018
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.ComplexHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.TextChangeDelegate;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.widget.ViewPager;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;

public class HashtagChatController extends ViewPagerController<HashtagChatController.Arguments> {
  public static class Arguments {
    public TdApi.ChatList chatList;
    public long chatId;
    public String searchQuery;
    public TdApi.MessageSender searchSender;
    public boolean hideGlobal;
    public boolean isChannel;

    public Arguments (TdApi.ChatList chatList, long chatId, String searchQuery, TdApi.MessageSender searchSender, boolean isChannel) {
      this.chatList = chatList;
      this.chatId = chatId;
      this.searchQuery = searchQuery;
      this.searchSender = searchSender;
      this.isChannel = isChannel;
    }

    public Arguments setHideGlobal (boolean hideGlobal) {
      this.hideGlobal = hideGlobal;
      return this;
    }
  }

  public HashtagChatController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public long getChatId () {
    return getArguments() != null ? getArguments().chatId : 0;
  }

  @Override
  protected int getPagerItemCount () {
    return StringUtils.isEmpty(getArgumentsStrict().searchQuery) ? 1 : 2;
  }

  @Override
  public View getViewForApplyingOffsets () {
    ViewController<?> c = getCachedControllerForPosition(0);
    return c != null ? c.getViewForApplyingOffsets() : null;
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell; // getPreparedControllerForPosition(0).getCustomHeaderCell();
  }

  private static class CustomHeaderCell extends FrameLayoutFix implements TextChangeDelegate {
    public CustomHeaderCell (@NonNull Context context) {
      super(context);
    }

    @Override
    public void setTextColor (int color) {
      ((ComplexHeaderView) getChildAt(0)).setTextColor(color);
      TextView textView = (TextView) getChildAt(1);
      if (textView != null) {
        textView.setTextColor(color);
      }
      ImageView imageView = (ImageView) getChildAt(2);
      if (imageView != null) {
        imageView.setColorFilter(color);
      }
    }
  }

  private CustomHeaderCell headerCell;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    headerCell = new CustomHeaderCell(context);
    headerCell.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Size.getHeaderPortraitSize()));

    ComplexHeaderView headerView = (ComplexHeaderView) getPreparedControllerForPosition(0).getCustomHeaderCell();
    headerView.setPhotoOpenDisabled(true);
    headerCell.addView(headerView);
    if (getPagerItemCount() > 1) {
      headerView.setNeedArrow(true);
      TextView textView = context().navigation().getHeaderView().genTextTitle(context);
      ViewGroup.MarginLayoutParams params = ((FrameLayout.LayoutParams) textView.getLayoutParams());
      params.topMargin -= HeaderView.getTopOffset();
      params.leftMargin = Screen.dp(68f) + Screen.dp(16f);
      textView.setText(getArgumentsStrict().searchQuery);
      textView.setAlpha(0f);
      addThemeTextAccentColorListener(textView);
      headerCell.addView(textView);

      ImageView imageView = new ImageView(context);
      imageView.setScaleType(ImageView.ScaleType.CENTER);
      imageView.setImageResource(R.drawable.round_keyboard_arrow_left_24);
      imageView.setColorFilter(Theme.textAccentColor());
      imageView.setAlpha(.15f);
      addThemeFilterListener(imageView, R.id.theme_color_text);
      imageView.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(24f), Size.getHeaderPortraitSize(), Gravity.LEFT, Screen.dp(68f) - Screen.dp(12f), 0, 0, 0));
      headerCell.addView(imageView);
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    int count = getPagerItemCount();
    for (int i = 0; i < count; i++) {
      getPreparedControllerForPosition(i).onFocus();
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    int count = getPagerItemCount();
    for (int i = 0; i < count; i++) {
      getPreparedControllerForPosition(i).onBlur();
    }
  }

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
    Arguments args = getArgumentsStrict();
    switch (position) {
      case 0: {
        MessagesController c = new MessagesController(context(), tdlib);
        c.setArguments(new MessagesController.Arguments(args.chatList, tdlib.chatStrict(args.chatId), args.searchQuery, args.searchSender, null));
        return c;
      }
      case 1:
        HashtagController c = new HashtagController(context(), tdlib) {
          @Override
          protected int getChatMessagesSearchTitle () {
            return R.string.GlobalSearch;
          }
        };
        c.setArguments(args.searchQuery);
        return c;
    }
    return null;
  }

  @Override
  public void onPageScrolled (int position, int actualPosition, float positionOffset, int positionOffsetPixels) {
    // FIXME RTL
    float totalFactor = (float) actualPosition + positionOffset;
    View v1 = headerCell.getChildAt(0);
    View v2 = headerCell.getChildAt(1);
    float distance = (float) get().getMeasuredWidth() * .14f /*HeaderView.TRANSLATION_FACTOR*/;
    v1.setTranslationX(totalFactor == 1f ? 0 : -distance * totalFactor);
    v1.setAlpha(1f - totalFactor);
    if (v2 != null) {
      float x = distance * (1f - totalFactor);
      v2.setTranslationX(x);
      v2.setAlpha(totalFactor);
      View v3 = headerCell.getChildAt(2);
      v3.setTranslationX(x);
      v3.setAlpha(.15f * totalFactor);
    }
  }

  @Override
  protected String[] getPagerSections () {
    return null;
  }

  @Override
  public int getId () {
    return R.id.controller_hashtagPreview;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  protected int getHeaderColorId () {
    return R.id.theme_color_filling;
  }

  @Override
  protected int getHeaderIconColorId () {
    return R.id.theme_color_headerLightIcon;
  }

  @Override
  protected int getHeaderTextColorId () {
    return R.id.theme_color_text;
  }

}
