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
 * File created on 05/12/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.drinkless.td.libcore.telegram.TdApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ThemeDeprecated;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Size;
import org.thunderdog.challegram.v.WebViewProxy;

public class WebPaymentMethodController extends WebkitController<WebPaymentMethodController.Args> implements Menu {
  public static class Args {
    public final String paymentProcessor, url;
    public final PaymentFormController.NewPaymentMethodCallback callback;

    public Args (String paymentProcessor, String url, PaymentFormController.NewPaymentMethodCallback callback) {
      this.paymentProcessor = paymentProcessor;
      this.url = url;
      this.callback = callback;
    }
  }

  private HeaderButton toggleSaveButton;
  private boolean shouldSavePaymentMethod = true;

  public WebPaymentMethodController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_newPaymentMethodWeb;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_newPaymentMethodWeb) {
      toggleSaveButton = header.genButton(R.id.menu_btn_savePaymentMethod, R.drawable.hansbohm_content_save_minus_24, getHeaderIconColorId(), this, Screen.dp(52f), ThemeDeprecated.headerSelector(), header);
      header.addButton(menu, toggleSaveButton);
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_savePaymentMethod) {
      shouldSavePaymentMethod = !shouldSavePaymentMethod;
      toggleSaveButton.setImageResource(shouldSavePaymentMethod ? R.drawable.hansbohm_content_save_minus_24 : R.drawable.baseline_save_24);
      UI.showCustomToast(shouldSavePaymentMethod ? R.string.PaymentFormMethodToastSaved : R.string.PaymentFormMethodToastNotSaved, Toast.LENGTH_LONG, 0);
    }
  }

  @SuppressLint("AddJavascriptInterface")
  @Override
  protected void onCreateWebView (DoubleHeaderView headerCell, WebView webView) {
    if (getArguments() != null) {
      headerCell.setTitle(R.string.PaymentFormNewMethodCard);
      headerCell.setSubtitle(getArguments().paymentProcessor);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      webView.addJavascriptInterface(new WebViewProxy(new WebViewProxy.ClientCallback() {
        @Override
        public void submitPaymentForm (String jsonData) {
          try {
            JSONObject obj = new JSONObject(jsonData);
            getArgumentsStrict().callback.onNewMethodCreated(
              new TdApi.InputCredentialsNew(
                obj.getJSONObject("credentials").toString(), shouldSavePaymentMethod
              ), obj.getString("title")
            );
          } catch (JSONException e) {
            UI.showToast(R.string.PaymentFormMethodError, Toast.LENGTH_LONG);
            e.printStackTrace();
          }

          navigateBack();
        }
      }), "TelegramWebviewProxy");
    }

    if (getArguments() != null) {
      webView.loadUrl(getArguments().url);
    }
  }

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return y < Size.getHeaderPortraitSize() || x <= Screen.dp(15f);
  }
}
