/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.widget.FrameLayoutFix;

public class TopBarView extends FrameLayoutFix {
  private final ImageView topDismissButton;
  private final LinearLayout actionsList;

  private boolean canDismiss;

  public interface DismissListener {
    void onDismissRequest (TopBarView barView);
  }

  public static class Item {
    final int id;
    final int stringRes;
    final View.OnClickListener onClickListener;

    boolean isNegative;
    boolean noDismiss;

    public Item (int id, int stringRes, View.OnClickListener onClickListener) {
      this.id = id;
      this.stringRes = stringRes;
      this.onClickListener = onClickListener;
    }

    public Item setIsNegative () {
      this.isNegative = true;
      return this;
    }

    public Item setNoDismiss () {
      this.noDismiss = true;
      return this;
    }
  }

  private DismissListener dismissListener;

  public TopBarView (@NonNull Context context) {
    super(context);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(36f)));
    ViewSupport.setThemedBackground(this, R.id.theme_color_filling, null);

    actionsList = new LinearLayout(context);
    actionsList.setOrientation(LinearLayout.HORIZONTAL);
    actionsList.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Lang.gravity() | Gravity.TOP));
    addView(actionsList);

    topDismissButton = new ImageView(context) {
      @Override
      public boolean onTouchEvent (MotionEvent event) {
        return Views.isValid(this) && super.onTouchEvent(event);
      }
    };
    topDismissButton.setOnClickListener(view -> {
      if (dismissListener != null) {
        dismissListener.onDismissRequest(this);
      }
    });
    topDismissButton.setScaleType(ImageView.ScaleType.CENTER);
    topDismissButton.setColorFilter(Theme.iconColor());
    topDismissButton.setImageResource(R.drawable.baseline_close_18);
    topDismissButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(40f), ViewGroup.LayoutParams.MATCH_PARENT, Lang.gravity() | Gravity.TOP));
    topDismissButton.setBackgroundResource(R.drawable.bg_btn_header);
    Views.setClickable(topDismissButton);
    topDismissButton.setVisibility(View.INVISIBLE);
    addView(topDismissButton);
  }

  public void setDismissListener (DismissListener dismissListener) {
    this.dismissListener = dismissListener;
  }

  private @Nullable ViewController<?> themeProvider;

  public void addThemeListeners (@Nullable ViewController<?> themeProvider) {
    this.themeProvider = themeProvider;
    if (themeProvider != null) {
      themeProvider.addThemeFilterListener(topDismissButton, R.id.theme_color_icon);
      themeProvider.addThemeInvalidateListener(this);
    }
  }

  public void setCanDismiss (boolean canDismiss) {
    if (this.canDismiss != canDismiss) {
      this.canDismiss = canDismiss;
      topDismissButton.setVisibility(canDismiss ? View.VISIBLE : View.GONE);
    }
  }

  public void setItems (Item... items) {
    for (int i = 0; i < actionsList.getChildCount(); i++) {
      View view = actionsList.getChildAt(i);
      if (view != null && themeProvider != null) {
        themeProvider.removeThemeListenerByTarget(view);
      }
    }
    actionsList.removeAllViews();
    if (items.length > 1) {
      View offsetView = new View(getContext());
      offsetView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .75f));
      actionsList.addView(offsetView);
    }
    boolean canDismiss = false;
    for (Item item : items) {
      if (!item.noDismiss) {
        canDismiss = true;
      }
      int textColorId = item.isNegative ? R.id.theme_color_textNegative : R.id.theme_color_textNeutral;
      TextView button = Views.newTextView(getContext(), 15f, Theme.getColor(textColorId), Gravity.CENTER, Views.TEXT_FLAG_BOLD | Views.TEXT_FLAG_HORIZONTAL_PADDING);
      button.setId(item.id);
      if (themeProvider != null) {
        themeProvider.addThemeTextColorListener(button, textColorId);
      }
      button.setEllipsize(TextUtils.TruncateAt.END);
      button.setSingleLine(true);
      button.setBackgroundResource(R.drawable.bg_btn_header);
      button.setOnClickListener(item.onClickListener);
      Views.setMediumText(button, Lang.getString(item.stringRes).toUpperCase());
      Views.setClickable(button);
      button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 2f));
      actionsList.addView(button);
    }
    if (items.length > 1) {
      View offsetView = new View(getContext());
      offsetView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .75f));
      actionsList.addView(offsetView);
    }
    setCanDismiss(canDismiss);
  }
}
