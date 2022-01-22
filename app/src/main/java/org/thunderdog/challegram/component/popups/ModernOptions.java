package org.thunderdog.challegram.component.popups;

import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.util.OptionDelegate;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.core.lambda.RunnableData;

public class ModernOptions {
  public static void showLocationAlert (BaseActivity context, @Nullable String currentInlineUsername, Runnable onCancel, Runnable onAgree) {
    String inlineUsername = "@" + currentInlineUsername;
    showLocationAlert(context, Lang.getStringBold(R.string.LocationAlertBot, inlineUsername), Lang.getStringBold(R.string.LocationAlertBotDisclaimer, inlineUsername), onCancel, onAgree);
  }

  public static void showLocationAlert (BaseActivity context, boolean isBackground, Runnable onCancel, Runnable onAgree) {
    boolean hasNoPermissions = context.checkLocationPermissions(isBackground) != PackageManager.PERMISSION_GRANTED;
    boolean shouldShowAlert;

    if (isBackground && Config.REQUEST_BACKGROUND_LOCATION) {
      shouldShowAlert = (U.shouldShowPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION) || U.shouldShowPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) && hasNoPermissions;
    } else {
      shouldShowAlert = U.shouldShowPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) || hasNoPermissions;
    }

    if (shouldShowAlert) {
      showLocationAlert(context, Lang.getString(isBackground ? R.string.LocationAlertLiveLocation : R.string.LocationAlertLocation), Lang.getString(R.string.LocationAlertLocationDisclaimer), onCancel, onAgree);
    } else {
      onAgree.run();
    }
  }

  private static void showLocationAlert (BaseActivity context, CharSequence firstLine, CharSequence secondLine, Runnable onCancel, Runnable onAgree) {
    Lang.SpanCreator firstItalicCreator = (target, argStart, argEnd, spanIndex, needFakeBold) -> spanIndex == 1 ? Lang.newItalicSpan(needFakeBold) : null;
    CharSequence desc = Lang.getString(R.string.format_doubleLines, firstItalicCreator, firstLine, secondLine);

    ViewController<?> currentController = context.navigation().getCurrentStackItem();

    if (currentController == null) {
      onAgree.run();
      return;
    }

    addBigColoredHeader(currentController.showOptions(
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
              //onCancel.run();
              currentController.tdlib().ui().openUrl(currentController, Lang.getStringSecure(R.string.url_privacyPolicy), new TdlibUi.UrlOpenParameters().forceInstantView());
              break;
            case R.id.btn_cancel:
              //onCancel.run();
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
