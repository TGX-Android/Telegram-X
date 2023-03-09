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
 * File created on 26/04/2015 at 14:53
 */
package org.thunderdog.challegram.component.dialogs;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGChat;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.ui.ChatsController;
import org.thunderdog.challegram.widget.BaseView;
import org.thunderdog.challegram.widget.ListInfoView;
import org.thunderdog.challegram.widget.NoScrollTextView;

public class ChatsViewHolder extends RecyclerView.ViewHolder {
  public ChatsViewHolder (View itemView) {
    super(itemView);
  }

  public void setChat (TGChat chat, boolean needBackground, boolean noSeparator, boolean isSelected) {
    ((ChatView) itemView).setChat(chat);
    ((ChatView) itemView).setNeedBackground(needBackground);
    ((ChatView) itemView).setIsSelected(isSelected, false);
  }

  public void setInfo (CharSequence info) {
    if (info == null) {
      ((ListInfoView) itemView).showProgress();
    } else {
      ((ListInfoView) itemView).showInfo(info);
    }
  }

  public void setEmpty (int empty) {
    ((ListInfoView) itemView).showEmpty(Lang.getString(empty));
  }

  public static ChatsViewHolder create (Context context, Tdlib tdlib, int viewType, @Nullable ChatsController parentController, @Nullable ViewController<?> themeProvider, BaseView.ActionListProvider actionListProvider) {
    switch (viewType) {
      case ChatsAdapter.VIEW_TYPE_CHAT: {
        ChatView view = new ChatView(context, tdlib);
        view.setPreviewActionListProvider(actionListProvider);
        view.setLongPressInterceptor(parentController);
        if (parentController != null) {
          view.setAnimationsDisabled(parentController.isLaunching());
          view.setOnClickListener(parentController);
          view.setOnLongClickListener(parentController);
        } else {
          view.setEnabled(false);
          view.setOnClickListener(null);
          view.setOnLongClickListener(null);
        }
        if (themeProvider != null) {
          themeProvider.addThemeInvalidateListener(view);
        }
        return new ChatsViewHolder(view);
      }
      case ChatsAdapter.VIEW_TYPE_INFO: {
        ListInfoView view = new ListInfoView(context);
        if (themeProvider != null) {
          view.addThemeListeners(themeProvider);
        }
        return new ChatsViewHolder(view);
      }
      case ChatsAdapter.VIEW_TYPE_EMPTY: {
        TextView textView = new NoScrollTextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f);
        textView.setTypeface(Fonts.getRobotoRegular());
        textView.setPadding(Screen.dp(16f), Screen.dp(16f), Screen.dp(16f), Screen.dp(16f));
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(Theme.textDecentColor());
        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(textView, R.id.theme_color_textLight);
        }
        textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new ChatsViewHolder(textView);
      }
      default: {
        throw new IllegalArgumentException("viewType == " + viewType);
      }
    }
  }
}
