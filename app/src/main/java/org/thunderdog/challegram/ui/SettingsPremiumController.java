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
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.CurrencyUtils;
import me.vkryl.core.StringUtils;

/**
 * Controller for Telegram Premium subscription management.
 * Displays Premium features and handles payment flow.
 */
public class SettingsPremiumController extends RecyclerViewController<Void> implements View.OnClickListener {

  private SettingsAdapter adapter;
  private TdApi.PremiumState premiumState;

  public SettingsPremiumController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName() {
    return Lang.getString(R.string.TelegramPremium);
  }

  @Override
  public int getId() {
    return R.id.controller_premium;
  }

  @Override
  protected void onCreateView(Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyDescription(ListItem item, TextView textView) {
        if (item.getId() == R.id.btn_premiumDescription) {
          textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
      }

      @Override
      protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_premiumOption) {
          TdApi.PremiumStatePaymentOption option = (TdApi.PremiumStatePaymentOption) item.getData();
          if (option != null && option.paymentOption != null) {
            String price = formatPrice(option.paymentOption.currency, option.paymentOption.amount);
            String duration = Lang.plural(R.string.xMonths, option.paymentOption.monthCount);
            if (option.paymentOption.discountPercentage > 0) {
              view.setData(Lang.getString(R.string.format_premiumOptionDiscount, price, duration, option.paymentOption.discountPercentage));
            } else {
              view.setData(Lang.getString(R.string.format_premiumOption, price, duration));
            }
          }
        }
      }
    };

    recyclerView.setAdapter(adapter);

    // Show loading state
    buildLoadingCells();

    // Fetch Premium state
    fetchPremiumState();
  }

  private void buildLoadingCells() {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TelegramPremium));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void fetchPremiumState() {
    tdlib.send(new TdApi.GetPremiumState(), (result, error) -> {
      runOnUiThreadOptional(() -> {
        if (error != null) {
          showError(TD.toErrorString(error));
        } else {
          premiumState = (TdApi.PremiumState) result;
          buildCells();
        }
      });
    });
  }

  private void showError(String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TelegramPremium));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  private void buildCells() {
    List<ListItem> items = new ArrayList<>();

    // Header
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TelegramPremium));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

    // Premium status - format with clickable entities
    if (premiumState.state != null && !StringUtils.isEmpty(premiumState.state.text)) {
      CharSequence formattedText = TD.formatString(this, premiumState.state.text, premiumState.state.entities, null, null);
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_premiumDescription, 0, formattedText));
    } else {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.PremiumDescription));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Payment options
    if (premiumState.paymentOptions != null && premiumState.paymentOptions.length > 0) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PremiumSubscriptionOptions));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      boolean first = true;
      for (TdApi.PremiumStatePaymentOption option : premiumState.paymentOptions) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;

        String title = Lang.plural(R.string.xMonths, option.paymentOption.monthCount);
        if (option.isCurrent) {
          title = title + " " + Lang.getString(R.string.PremiumCurrent);
        }

        ListItem item = new ListItem(
          ListItem.TYPE_VALUED_SETTING_COMPACT,
          R.id.btn_premiumOption,
          R.drawable.baseline_premium_star_24,
          title,
          false
        );
        item.setData(option);
        items.add(item);
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.PremiumNoOptions));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, true);
  }

  @Override
  public void onClick(View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_premiumOption) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.PremiumStatePaymentOption) {
        TdApi.PremiumStatePaymentOption option = (TdApi.PremiumStatePaymentOption) item.getData();
        if (option.isCurrent) {
          UI.showToast(R.string.PremiumAlreadySubscribed, Toast.LENGTH_SHORT);
          return;
        }
        startPaymentFlow(option);
      }
    }
  }

  private void startPaymentFlow(TdApi.PremiumStatePaymentOption option) {
    TdApi.PremiumPaymentOption paymentOption = option.paymentOption;

    // Check if we have a payment link (for non-store payments)
    if (paymentOption.paymentLink != null) {
      processPaymentLink(paymentOption.paymentLink, paymentOption.monthCount);
    } else if (!StringUtils.isEmpty(paymentOption.storeProductId)) {
      // Fall back to store payment
      UI.showToast(R.string.PremiumStorePaymentNotAvailable, Toast.LENGTH_SHORT);
    } else {
      UI.showToast(R.string.PremiumPaymentUnavailable, Toast.LENGTH_SHORT);
    }
  }

  private void processPaymentLink(TdApi.InternalLinkType paymentLink, int monthCount) {
    if (paymentLink instanceof TdApi.InternalLinkTypeInvoice) {
      TdApi.InternalLinkTypeInvoice invoice = (TdApi.InternalLinkTypeInvoice) paymentLink;
      openPaymentForm(invoice.invoiceName, monthCount);
    } else {
      // Handle other link types or open via TdlibUi
      tdlib.ui().openInternalLinkType(this, null, paymentLink, null, null);
    }
  }

  private void openPaymentForm(String invoiceName, int monthCount) {
    // Show loading
    UI.showToast(R.string.LoadingPaymentForm, Toast.LENGTH_SHORT);

    TdApi.InputInvoiceName inputInvoice = new TdApi.InputInvoiceName(invoiceName);

    tdlib.send(new TdApi.GetPaymentForm(inputInvoice, null), (result, error) -> {
      runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showToast(TD.toErrorString(error), Toast.LENGTH_SHORT);
        } else {
          TdApi.PaymentForm paymentForm = (TdApi.PaymentForm) result;
          handlePaymentForm(paymentForm, inputInvoice, monthCount);
        }
      });
    });
  }

  private void handlePaymentForm(TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice, int monthCount) {
    if (paymentForm.type instanceof TdApi.PaymentFormTypeRegular) {
      // Open payment form controller
      PaymentFormController controller = new PaymentFormController(context(), tdlib);
      controller.setArguments(new PaymentFormController.Args(paymentForm, inputInvoice, monthCount));
      navigateTo(controller);
    } else if (paymentForm.type instanceof TdApi.PaymentFormTypeStars) {
      TdApi.PaymentFormTypeStars starsType = (TdApi.PaymentFormTypeStars) paymentForm.type;
      showStarsPaymentConfirmation(paymentForm, inputInvoice, starsType.starCount);
    } else {
      UI.showToast(R.string.PaymentUnknownType, Toast.LENGTH_SHORT);
    }
  }

  private String formatPrice(String currency, long amount) {
    return CurrencyUtils.buildAmount(currency, amount);
  }

  private void showStarsPaymentConfirmation(TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice, long starCount) {
    String message = Lang.getString(R.string.StarsPayConfirmMessage, starCount);
    showOptions(
      message,
      new int[] { R.id.btn_done, R.id.btn_cancel },
      new String[] { Lang.getString(R.string.StarsPayConfirm, starCount), Lang.getString(R.string.Cancel) },
      new int[] { OptionColor.BLUE, OptionColor.NORMAL },
      new int[] { R.drawable.baseline_star_24, R.drawable.baseline_cancel_24 },
      (view, optionId) -> {
        if (optionId == R.id.btn_done) {
          sendStarsPayment(paymentForm, inputInvoice);
        }
        return true;
      }
    );
  }

  private void sendStarsPayment(TdApi.PaymentForm paymentForm, TdApi.InputInvoice inputInvoice) {
    UI.showToast(R.string.PaymentProcessing, Toast.LENGTH_SHORT);
    tdlib.send(new TdApi.SendPaymentForm(inputInvoice, paymentForm.id, "", "", null, 0), (result, error) -> {
      runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showToast(Lang.getString(R.string.StarsPaymentFailed, TD.toErrorString(error)), Toast.LENGTH_SHORT);
        } else {
          UI.showToast(R.string.StarsPaymentSuccess, Toast.LENGTH_SHORT);
        }
      });
    });
  }
}

