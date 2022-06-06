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
 *
 * File created on 25/02/2016 at 15:56
 */
package org.thunderdog.challegram.component.preview;

import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.StringRes;
import androidx.collection.SparseArrayCompat;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BaseActivity;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.EmbeddedService;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.OptionsLayout;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ThemeListenerList;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.widget.PopupLayout;

import me.vkryl.android.widget.FrameLayoutFix;

public abstract class PreviewLayout extends FrameLayoutFix implements View.OnClickListener, PopupLayout.ShowListener, PopupLayout.DismissListener {
  protected EmbeddedService nativeEmbed;
  protected int footerHeight;
  protected final ViewController<?> parent;

  private final ThemeListenerList themeListeners = new ThemeListenerList();

  public PreviewLayout (Context context, final ViewController<?> parent) {
    super(context);
    this.parent = parent;

    addFooterItem(R.id.btn_share, R.string.Share, R.drawable.baseline_forward_24);
    addFooterItem(R.id.btn_openLink, R.string.OpenInExternalApp,  R.drawable.baseline_open_in_browser_24);

    setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

    UI.getContext(context).addGlobalThemeListeners(themeListeners);
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_openLink: {
        popupLayout.hideWindow(true);
        UI.openUrl(nativeEmbed.viewUrl);
        break;
      }
      case R.id.btn_share: {
        popupLayout.hideWindow(true);
        TD.shareLink(parent, nativeEmbed.viewUrl);
        break;
      }
    }
  }

  protected void setFooterVisibility (int visibility) {
    int i = 0;
    View view;
    while (i < getChildCount() && (view = getChildAt(i)) instanceof TextView) {
      view.setVisibility(visibility);
      i++;
    }
  }

  protected void addFooterItem (int id, @StringRes int stringRes, int icon) {
    FrameLayoutFix.LayoutParams params;
    params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(54f), Gravity.BOTTOM);
    params.bottomMargin = footerHeight;
    footerHeight += params.height;

    TextView item;
    item = OptionsLayout.genOptionView(getContext(), id, Lang.getString(stringRes), ViewController.OPTION_COLOR_NORMAL, icon, this, themeListeners, null);
    RippleSupport.setSimpleWhiteBackground(item);
    themeListeners.addThemeInvalidateListener(item);
    item.setLayoutParams(params);
    addView(item);
  }

  @CallSuper
  public boolean setPreview (EmbeddedService nativeEmbed) {
    this.nativeEmbed = nativeEmbed;
    return buildLayout();
  }

  protected PopupLayout popupLayout;

  private void show () {
    popupLayout = new PopupLayout(getContext());
    popupLayout.setIgnoreBottom(true);
    popupLayout.setHideKeyboard();
    popupLayout.setNeedRootInsets();
    popupLayout.setOverlayStatusBar(true);
    popupLayout.setShowListener(this);
    popupLayout.setDismissListener(this);
    popupLayout.showSimplePopupView(this, getPreviewHeight());
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(computeHeight(MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.EXACTLY));
  }

  protected abstract int computeHeight (int currentWidth);
  protected abstract int getPreviewHeight ();
  protected abstract boolean buildLayout ();
  protected abstract boolean onPrepareNextPreview (String pageUrl);
  protected abstract void onDestroyPopupInternal ();
  public abstract void forceClose (boolean animated);

  @Override
  public final void onPopupDismiss (PopupLayout popup) {
    onDestroyPopupInternal();
    UI.getContext(getContext()).removeGlobalThemeListeners(themeListeners);
  }

  public static boolean show (ViewController<?> parent, TdApi.WebPage webPage, boolean needConfirmation) {
    EmbeddedService service = EmbeddedService.parse(webPage);
    return show(parent, service, needConfirmation);
  }

  public static boolean show (ViewController<?> parent, String webPageUrl, boolean needConfirmation) {
    EmbeddedService service = EmbeddedService.parseUrl(webPageUrl);
    return show(parent, service, needConfirmation);
  }

  public static boolean show (ViewController<?> parent, EmbeddedService service, boolean needConfirmation) {
    if (service != null) {
      BaseActivity context = parent.context();

      SparseArrayCompat<PopupLayout> popups = context.getForgottenWindows();
      for (int i = 0; i < popups.size(); i++) {
        PopupLayout popupLayout = popups.valueAt(i);
        if (popupLayout.getBoundView() instanceof PreviewLayout && ((PreviewLayout) popupLayout.getBoundView()).onPrepareNextPreview(service.viewUrl)) {
          return true;
        }
      }
      context.closeOtherPips();

      if (service.type == EmbeddedService.TYPE_UNKNOWN && needConfirmation) {
        Uri viewUri = Uri.parse(service.viewUrl);

        parent.showOptions(Lang.getStringBold(
          R.string.OpenPreviewInSecretChatWarning,
          Strings.unwrapWww(viewUri.getHost()),
          service.embedUrl
        ), new int[]{
          R.id.btn_openLink,
          R.id.btn_useInAppBrowser,
          R.id.btn_cancel
        }, new String[]{
          Lang.getString(R.string.OpenPreviewInSecretChatActionOpen),
          Lang.getString(R.string.OpenPreviewInSecretChatActionBrowser),
          Lang.getString(R.string.Cancel),
        }, null, new int[]{
          R.drawable.baseline_play_circle_filled_24_white,
          R.drawable.baseline_open_in_browser_24,
          R.drawable.baseline_cancel_24
        }, (optionItemView, id) -> {
          switch (id) {
            case R.id.btn_openLink:
              show(parent, service, false);
              break;
            case R.id.btn_useInAppBrowser:
              UI.openUrl(service.viewUrl);
              break;
          }

          return true;
        });

        return true;
      }

      PreviewLayout popup = null;
      switch (service.type) {
        case EmbeddedService.TYPE_YOUTUBE:
          if (YouTube.isYoutubeAppInstalled()) {
            popup = new YouTubePreviewLayout(context, parent);
          } else {
            popup = new WebViewPreviewLayout(context, parent);
          }

          break;
        case EmbeddedService.TYPE_SOUNDCLOUD:
        case EmbeddedService.TYPE_DAILYMOTION:
        case EmbeddedService.TYPE_VIMEO:
        case EmbeddedService.TYPE_UNKNOWN:
        case EmbeddedService.TYPE_CUSTOM_EMBED:
          popup = new WebViewPreviewLayout(context, parent);
          break;
      }

      if (popup != null && popup.setPreview(service)) {
        if (!parent.tdlib().context().calls().promptActiveCall()) {
          popup.show();
        }
        return true;
      }
    }
    return false;
  }
}
