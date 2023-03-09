/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.component.popups;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.util.Permissions;
import org.thunderdog.challegram.widget.PopupLayout;

public class ModernOptions {
  public static void showLocationAlert (ViewController<?> context, @Nullable String currentInlineUsername, Runnable onCancel, Runnable onAgree) {
    String inlineUsername = "@" + currentInlineUsername;
    showLocationAlert(context, Lang.getStringBold(R.string.LocationAlertBot, inlineUsername), Lang.getStringBold(R.string.LocationAlertBotDisclaimer, inlineUsername), onCancel, onAgree);
  }

  public static void showLocationAlert (ViewController<?> context, boolean isBackground, Runnable onCancel, Runnable onAgree) {
    Permissions permissions = context.context().permissions();
    boolean hasPermissions = permissions.canAccessLocation() && (!isBackground || permissions.canAccessLocationInBackground());
    if (hasPermissions) {
      onAgree.run();
      return;
    }
    // FIXME: should it actually check for permission rationale?
    if (
      (isBackground && permissions.shouldShowBackgroundLocationRationale()) ||
      permissions.shouldShowAccessLocationRationale()) {
      showLocationAlert(context, Lang.getString(isBackground ? R.string.LocationAlertLiveLocation : R.string.LocationAlertLocation), Lang.getString(R.string.LocationAlertLocationDisclaimer), isBackground ? onCancel : () -> {}, onAgree);
    } else {
      onAgree.run();
    }
  }

  private static void showLocationAlert (ViewController<?> context, CharSequence firstLine, CharSequence secondLine, Runnable onCancel, Runnable onAgree) {
    if (!context.isFocused()) {
      context.addOneShotFocusListener(() -> {
        showLocationAlert(context, firstLine, secondLine, onCancel, onAgree);
      });
      return;
    }
    Lang.SpanCreator firstItalicCreator = (target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 1 ? Lang.newItalicSpan(needFakeBold) : null;
    CharSequence desc = Lang.getString(R.string.format_doubleLines, firstItalicCreator, firstLine, secondLine);

    addBigColoredHeader(context.showOptions(
      desc,
      new int[]{R.id.btn_done, R.id.btn_privacyPolicy, R.id.btn_cancel},
      new String[]{Lang.getString(R.string.Continue), Lang.getString(R.string.PrivacyPolicy), Lang.getString(R.string.Cancel)},
      new int[]{ViewController.OPTION_COLOR_BLUE, ViewController.OPTION_COLOR_NORMAL, ViewController.OPTION_COLOR_NORMAL},
      new int[]{R.drawable.baseline_check_circle_24, R.drawable.baseline_policy_24, R.drawable.baseline_cancel_24},
      new OptionDelegate() {
        @Override
        public boolean disableCancelOnTouchdown () {
          return true;
        }

        @Override
        public boolean onOptionItemPressed (View optionItemView, int id) {
          switch (optionItemView.getId()) {
            case R.id.btn_done:
              onAgree.run();
              break;
            case R.id.btn_privacyPolicy:
              onCancel.run();
              context.tdlib().ui().openUrl(context, Lang.getStringSecure(R.string.url_privacyPolicy), new TdlibUi.UrlOpenParameters().forceInstantView());
              break;
            case R.id.btn_cancel:
              onCancel.run();
              break;
          }

          return true;
        }
      }
    ), R.drawable.baseline_location_on_48);
  }

  private static void addBigColoredHeader (PopupLayout layout, @DrawableRes int icon) {
    if (!(layout.getChildAt(1) instanceof OptionsLayout)) {
      return;
    }

    OptionsLayout optionsLayout = (OptionsLayout) layout.getChildAt(1);

    ImageView coloredHeader = new ImageView(layout.getContext());
    coloredHeader.setBackgroundColor(Theme.getColor(R.id.theme_color_fillingPositive));
    coloredHeader.setImageResource(icon);
    coloredHeader.setScaleType(ImageView.ScaleType.CENTER);
    coloredHeader.setColorFilter(Paints.getColorFilter(Theme.getColor(R.id.theme_color_fillingPositiveContent)));
    coloredHeader.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Screen.dp(132f)));

    optionsLayout.addView(coloredHeader, 1);
  }
}
