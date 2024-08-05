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
 */
package org.thunderdog.challegram.component.chat;

import android.annotation.SuppressLint;
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
import androidx.appcompat.widget.AppCompatImageView;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;

import me.vkryl.android.widget.FrameLayoutFix;

public class TopBarView extends FrameLayoutFix {
  private final ImageView topDismissButton;
  private final LinearLayout actionsContainer;
  private LinearLayout actionsList;

  private boolean canDismiss;

  public interface DismissListener {
    void onDismissRequest (TopBarView barView);
  }

  public static class Item {
    final int id;
    final int stringRes;
    final CharSequence noticeRes;
    final int iconResId;
    final View.OnClickListener onClickListener;
    final boolean showDismissRight;

    boolean isNegative;
    boolean noDismiss;

    public Item (int id, int stringRes, CharSequence noticeRes, int iconResId, boolean showDismissRight, View.OnClickListener onClickListener) {
      this.id = id;
      this.stringRes = stringRes;
      this.noticeRes = noticeRes;
      this.iconResId = iconResId;
      this.showDismissRight = showDismissRight;
      this.onClickListener = onClickListener;
    }

    public Item (int id, int stringRes, boolean showDismissRight, int iconResId,View.OnClickListener onClickListener) {
      this(id, stringRes, null, iconResId, showDismissRight, onClickListener);
    }

    public Item (int id, int stringRes, boolean showDismissRight, View.OnClickListener onClickListener) {
      this(id, stringRes, null, 0, showDismissRight, onClickListener);
    }

    public Item (int id, int stringRes, View.OnClickListener onClickListener) {
      this(id, stringRes, null, 0, false, onClickListener);
    }

    public Item (CharSequence noticeRes, boolean showDismissRight) {
      this(0, 0, noticeRes, 0, showDismissRight, null);
    }

    public Item (CharSequence noticeRes) {
      this(0, 0, noticeRes, 0, false, null);
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
    ViewSupport.setThemedBackground(this, ColorId.filling, null);

    actionsContainer = new LinearLayout(context);
    actionsContainer.setOrientation(LinearLayout.VERTICAL);
    actionsContainer.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(40f), Lang.gravity() | Gravity.TOP));
    addView(actionsContainer);

    actionsList = new LinearLayout(context);
    actionsList.setOrientation(LinearLayout.HORIZONTAL);
    actionsList.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Lang.gravity() | Gravity.TOP));
    actionsContainer.addView(actionsList);

    topDismissButton = new AppCompatImageView(context) {
      @SuppressLint("ClickableViewAccessibility")
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
      themeProvider.addThemeFilterListener(topDismissButton, ColorId.icon);
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
    actionsList = (LinearLayout) actionsContainer.getChildAt(0);
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
      int textColorId = item.isNegative ? ColorId.textNegative : ColorId.textNeutral;

      LinearLayout buttonLayout = new LinearLayout(getContext());
      buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
      buttonLayout.setGravity(Gravity.CENTER);
      buttonLayout.setBackgroundResource(R.drawable.bg_btn_header);
      buttonLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 2f));
      Views.setClickable(buttonLayout);

      topDismissButton.setLayoutParams(FrameLayoutFix.newParams(Screen.dp(40f), ViewGroup.LayoutParams.MATCH_PARENT, (item.showDismissRight ? (Gravity.END | Gravity.CENTER_VERTICAL) : (Lang.gravity() | Gravity.TOP))));

      LinearLayout noticeItem = new LinearLayout(getContext());
      noticeItem.setOrientation(LinearLayout.HORIZONTAL);
      noticeItem.setGravity(Lang.gravity());
      noticeItem.setBackgroundResource(R.drawable.bg_btn_header);
      noticeItem.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, 2f));
      /* Rudimentary debugging
       android.util.Log.d("TopBarView", String.format("Width: %d, Height: %d", ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
       android.util.Log.d("TopBarView", String.format("Width: %d, Height: %d", noticeItem.getWidth(), noticeItem.getHeight()));
      */

      if (item.iconResId != 0) {
        ImageView iconView = new ImageView(getContext());
        iconView.setImageResource(item.iconResId);
        iconView.setColorFilter(Theme.getColor(textColorId));
        iconView.setLayoutParams(new LinearLayout.LayoutParams(Screen.dp(24f), Screen.dp(24f)));
        buttonLayout.addView(iconView);
      }

      if (item.stringRes != 0) {
        TextView buttonText = Views.newTextView(getContext(), 15f, Theme.getColor(textColorId), Gravity.CENTER, Views.TEXT_FLAG_BOLD | Views.TEXT_FLAG_HORIZONTAL_PADDING);
        buttonText.setId(item.id);

        if (themeProvider != null) {
          themeProvider.addThemeTextColorListener(buttonText, textColorId);
        }

        buttonText.setEllipsize(TextUtils.TruncateAt.END);
        buttonText.setSingleLine(true);
        buttonText.setText(Lang.getString(item.stringRes).toUpperCase());
        buttonText.setOnClickListener(item.onClickListener);
        buttonLayout.addView(buttonText);
        actionsList.addView(buttonLayout);
      }

      if (item.noticeRes != null) {
        var noticeText = Views.newTextView(getContext(), 15f, Theme.getColor(ColorId.textPlaceholder), ViewGroup.MEASURED_HEIGHT_STATE_SHIFT, Views.TEXT_FLAG_HORIZONTAL_PADDING);
        noticeText.setText(item.noticeRes);
        setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(50f)));

        noticeItem.addView(noticeText);
        actionsList.addView(noticeItem);
      }

    }
    if (items.length > 1) {
      View offsetView = new View(getContext());
      offsetView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, .75f));
      actionsList.addView(offsetView);
    }
    setCanDismiss(canDismiss);
  }
}
