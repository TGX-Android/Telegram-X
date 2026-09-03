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
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.ViewSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.widget.CustomTextView;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.lambda.Destroyable;

public class TopBarView extends FrameLayoutFix implements Destroyable {
  private static final float ACTIONS_HEIGHT_DP = 46f;
  private static final float DISMISS_SIZE_DP = 40f;
  private static final float CONTENT_HORIZONTAL_PADDING_DP = 16f;
  private static final float SEPARATOR_HEIGHT_DP = 1f;
  private static final float SEPARATOR_HORIZONTAL_MARGIN_DP = 12f;
  private static final float ACTION_ICON_SIZE_DP = 24f;
  private static final float ACTION_ICON_TEXT_SPACING_DP = 8f;
  private static final float ACTION_TEXT_HORIZONTAL_PADDING_DP = 4f;
  private static final float NOTICE_DISMISS_PADDING_DP = 48f;
  private static final float NOTICE_TOP_PADDING_DP = 10f;
  private static final float NOTICE_BOTTOM_PADDING_DP = 8f;
  private static final float LEGACY_NOTICE_END_PADDING_DP = 26f;
  private static final float LEGACY_NOTICE_VERTICAL_PADDING_DP = 8f;
  private static final float ACTION_TEXT_SIZE_SP = 15f;
  private static final float NOTICE_TEXT_SIZE_SP = 14f;

  private final ImageView topDismissButton;
  private final LinearLayout actionsContainer;
  private final LinearLayout actionsList;
  private final @Nullable Tdlib tdlib;
  private final boolean useNewLayout;

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

    public Item (int id, int stringRes, CharSequence noticeRes, int iconResId,
                 boolean showDismissRight, View.OnClickListener onClickListener) {
      this.id = id;
      this.stringRes = stringRes;
      this.noticeRes = noticeRes;
      this.iconResId = iconResId;
      this.showDismissRight = showDismissRight;
      this.onClickListener = onClickListener;
    }

    public Item (int id, int stringRes, boolean showDismissRight, int iconResId,
                 View.OnClickListener onClickListener) {
      this(id, stringRes, null, iconResId, showDismissRight, onClickListener);
    }

    public Item (int id, int stringRes, boolean showDismissRight,
                 View.OnClickListener onClickListener) {
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
    this(context, null);
  }

  public TopBarView (@NonNull Context context, @Nullable Tdlib tdlib) {
    super(context);
    this.tdlib = tdlib;
    this.useNewLayout = Config.ENABLE_NEW_CHAT_ACTION_BAR && tdlib != null;

    setLayoutParams(FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      useNewLayout ? ViewGroup.LayoutParams.WRAP_CONTENT : Screen.dp(36f)
    ));
    ViewSupport.setThemedBackground(this, ColorId.filling, null);

    actionsContainer = new LinearLayout(context);
    actionsContainer.setOrientation(LinearLayout.VERTICAL);
    actionsContainer.setLayoutParams(FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      Lang.gravity() | Gravity.TOP
    ));
    addView(actionsContainer);

    actionsList = new LinearLayout(context);
    actionsList.setOrientation(LinearLayout.HORIZONTAL);
    actionsList.setLayoutParams(FrameLayoutFix.newParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      useNewLayout ? Screen.dp(ACTIONS_HEIGHT_DP) : ViewGroup.LayoutParams.MATCH_PARENT,
      Lang.gravity() | Gravity.TOP
    ));
    actionsContainer.addView(actionsList);

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

  public int getVisualHeight () {
    return Math.max(getMeasuredHeight(), Screen.dp(ACTIONS_HEIGHT_DP));
  }

  public void setItems (Item... items) {
    clearItems();

    int actionCount = 0;
    boolean canDismiss = false;
    boolean showDismissRight = false;
    boolean hasNotice = false;
    for (Item item : items) {
      if (item.stringRes != 0) {
        actionCount++;
      }
      canDismiss |= !item.noDismiss;
      showDismissRight |= item.showDismissRight;
      hasNotice |= item.noticeRes != null;
    }

    boolean dismissInNotice = useNewLayout && showDismissRight && actionCount == 0;
    int actionsHeight = Screen.dp(ACTIONS_HEIGHT_DP);
    actionsList.getLayoutParams().height = useNewLayout ?
      actionsHeight : ViewGroup.LayoutParams.MATCH_PARENT;
    int dismissSpace = useNewLayout && showDismissRight && actionCount > 0 ?
      Screen.dp(DISMISS_SIZE_DP) : 0;
    int startPadding = useNewLayout ? Screen.dp(CONTENT_HORIZONTAL_PADDING_DP) : 0;
    int endPadding = dismissSpace != 0 ? dismissSpace : startPadding;
    actionsList.setPadding(Lang.rtl() ? endPadding : startPadding, 0,
      Lang.rtl() ? startPadding : endPadding, 0);

    topDismissButton.setLayoutParams(FrameLayoutFix.newParams(
      Screen.dp(DISMISS_SIZE_DP),
      useNewLayout && actionCount > 0 ? actionsHeight :
        ViewGroup.LayoutParams.MATCH_PARENT,
      (showDismissRight ? Lang.reverseGravity() : Lang.gravity()) |
        (useNewLayout && actionCount > 0 ? Gravity.TOP : Gravity.CENTER_VERTICAL)
    ));

    if (actionCount > 0 || (!useNewLayout && hasNotice)) {
      actionsContainer.addView(actionsList);
    }
    if (useNewLayout && actionCount > 0 && hasNotice) {
      View separator = new View(getContext());
      LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(SEPARATOR_HEIGHT_DP)
      );
      separatorParams.leftMargin = Screen.dp(SEPARATOR_HORIZONTAL_MARGIN_DP);
      separatorParams.rightMargin = Screen.dp(SEPARATOR_HORIZONTAL_MARGIN_DP);
      separator.setLayoutParams(separatorParams);
      ViewSupport.setThemedBackground(separator, ColorId.separator, themeProvider);
      actionsContainer.addView(separator);
    }

    if (!useNewLayout && items.length > 1) {
      View offsetView = new View(getContext());
      offsetView.setLayoutParams(new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.MATCH_PARENT, .75f
      ));
      actionsList.addView(offsetView);
    }

    for (Item item : items) {
      int textColorId = item.isNegative ? ColorId.textNegative : ColorId.textNeutral;

      if (item.stringRes != 0) {
        boolean showText = !useNewLayout || actionCount <= 3;
        LinearLayout buttonLayout = new LinearLayout(getContext());
        buttonLayout.setId(item.id);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);
        buttonLayout.setBackgroundResource(R.drawable.bg_btn_header);
        buttonLayout.setOnClickListener(item.onClickListener);
        buttonLayout.setContentDescription(Lang.getString(item.stringRes));
        buttonLayout.setLayoutParams(new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
          2f
        ));
        Views.setClickable(buttonLayout);

        ImageView iconView = null;
        if (item.iconResId != 0) {
          iconView = new ImageView(getContext());
          iconView.setImageResource(item.iconResId);
          iconView.setColorFilter(Theme.getColor(textColorId));
          LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            Screen.dp(ACTION_ICON_SIZE_DP), Screen.dp(ACTION_ICON_SIZE_DP)
          );
          if (showText) {
            if (Lang.rtl()) {
              iconParams.leftMargin = Screen.dp(ACTION_ICON_TEXT_SPACING_DP);
            } else {
              iconParams.rightMargin = Screen.dp(ACTION_ICON_TEXT_SPACING_DP);
            }
          }
          iconView.setLayoutParams(iconParams);
          if (themeProvider != null) {
            themeProvider.addThemeFilterListener(iconView, textColorId);
          }
        }

        TextView buttonText = null;
        if (showText) {
          buttonText = Views.newTextView(
            getContext(), ACTION_TEXT_SIZE_SP, Theme.getColor(textColorId),
            useNewLayout && item.iconResId != 0 ?
              Lang.gravity() | Gravity.CENTER_VERTICAL : Gravity.CENTER,
            Views.TEXT_FLAG_BOLD | Views.TEXT_FLAG_HORIZONTAL_PADDING
          );

          if (themeProvider != null) {
            themeProvider.addThemeTextColorListener(buttonText, textColorId);
          }

          buttonText.setEllipsize(TextUtils.TruncateAt.END);
          buttonText.setSingleLine(true);
          buttonText.setPadding(
            Screen.dp(ACTION_TEXT_HORIZONTAL_PADDING_DP), 0,
            Screen.dp(ACTION_TEXT_HORIZONTAL_PADDING_DP), 0
          );
          Views.setMediumText(buttonText, Lang.uppercase(Lang.getString(item.stringRes)));
        }

        if (Lang.rtl()) {
          if (buttonText != null) {
            buttonLayout.addView(buttonText);
          }
          if (iconView != null) {
            buttonLayout.addView(iconView);
          }
        } else {
          if (iconView != null) {
            buttonLayout.addView(iconView);
          }
          if (buttonText != null) {
            buttonLayout.addView(buttonText);
          }
        }
        actionsList.addView(buttonLayout);
      }

      if (item.noticeRes != null) {
        if (useNewLayout) {
          CustomTextView noticeText = new CustomTextView(getContext(), tdlib);
          noticeText.setTextColorId(ColorId.textLight);
          noticeText.setTextSize(NOTICE_TEXT_SIZE_SP);
          noticeText.setText(item.noticeRes, null, false);
          noticeText.setContentDescription(item.noticeRes);
          noticeText.setPadding(
            Screen.dp(dismissInNotice && Lang.rtl() ?
              NOTICE_DISMISS_PADDING_DP : CONTENT_HORIZONTAL_PADDING_DP),
            Screen.dp(NOTICE_TOP_PADDING_DP),
            Screen.dp(dismissInNotice && !Lang.rtl() ?
              NOTICE_DISMISS_PADDING_DP : CONTENT_HORIZONTAL_PADDING_DP),
            Screen.dp(NOTICE_BOTTOM_PADDING_DP)
          );
          noticeText.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
          ));
          if (themeProvider != null) {
            themeProvider.addThemeInvalidateListener(noticeText);
          }
          actionsContainer.addView(noticeText);
        } else {
          LinearLayout noticeItem = new LinearLayout(getContext());
          noticeItem.setGravity(Gravity.CENTER);
          noticeItem.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 2f
          ));

          TextView noticeText = Views.newTextView(
            getContext(), ACTION_TEXT_SIZE_SP, Theme.getColor(ColorId.textPlaceholder),
            Lang.gravity(), Views.TEXT_FLAG_HORIZONTAL_PADDING
          );
          noticeText.setText(item.noticeRes);
          noticeText.setPadding(Screen.dp(CONTENT_HORIZONTAL_PADDING_DP),
            Screen.dp(LEGACY_NOTICE_VERTICAL_PADDING_DP),
            Screen.dp(LEGACY_NOTICE_END_PADDING_DP),
            Screen.dp(LEGACY_NOTICE_VERTICAL_PADDING_DP));
          noticeText.setSingleLine(false);
          noticeItem.addView(noticeText);
          actionsList.addView(noticeItem);
        }
      }

    }

    if (!useNewLayout && items.length > 1) {
      View offsetView = new View(getContext());
      offsetView.setLayoutParams(new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.MATCH_PARENT, .75f
      ));
      actionsList.addView(offsetView);
    }
    setCanDismiss(canDismiss);
  }

  private void clearItems () {
    clearThemeListeners(actionsContainer);
    clearThemeListeners(actionsList);
    for (int i = 0; i < actionsContainer.getChildCount(); i++) {
      View child = actionsContainer.getChildAt(i);
      if (child instanceof Destroyable) {
        ((Destroyable) child).performDestroy();
      }
    }
    actionsContainer.removeAllViews();
    actionsList.removeAllViews();
  }

  private void clearThemeListeners (View view) {
    if (themeProvider == null) {
      return;
    }
    themeProvider.removeThemeListenerByTarget(view);
    if (view instanceof ViewGroup) {
      ViewGroup viewGroup = (ViewGroup) view;
      for (int i = 0; i < viewGroup.getChildCount(); i++) {
        clearThemeListeners(viewGroup.getChildAt(i));
      }
    }
  }

  @Override
  public void performDestroy () {
    clearItems();
  }
}
