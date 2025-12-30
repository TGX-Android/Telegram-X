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
import android.view.View;
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
 * Controller for Telegram Stars - buy stars and view balance.
 */
public class SettingsStarsController extends RecyclerViewController<SettingsStarsController.Args> implements View.OnClickListener {

  public static class Args {
    public final long requiredStarCount;
    public final String purpose;

    public Args() {
      this.requiredStarCount = 0;
      this.purpose = null;
    }

    public Args(long requiredStarCount, String purpose) {
      this.requiredStarCount = requiredStarCount;
      this.purpose = purpose;
    }
  }

  private SettingsAdapter adapter;
  private TdApi.StarPaymentOptions paymentOptions;
  private long starBalance = 0;

  public SettingsStarsController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName() {
    return Lang.getString(R.string.TelegramStars);
  }

  @Override
  public int getId() {
    return R.id.controller_stars;
  }

  @Override
  protected void onCreateView(Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_starOption) {
          TdApi.StarPaymentOption option = (TdApi.StarPaymentOption) item.getData();
          if (option != null) {
            String price = CurrencyUtils.buildAmount(option.currency, option.amount);
            view.setData(price);
          }
        }
      }
    };

    recyclerView.setAdapter(adapter);

    // Show loading state
    buildLoadingCells();

    // Fetch data
    fetchData();
  }

  private void buildLoadingCells() {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TelegramStars));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void fetchData() {
    // Fetch star balance
    tdlib.send(new TdApi.GetStarTransactions(new TdApi.MessageSenderUser(tdlib.myUserId()), null, null, null, 1), (result, error) -> {
      runOnUiThreadOptional(() -> {
        if (error == null && result != null) {
          TdApi.StarTransactions transactions = (TdApi.StarTransactions) result;
          starBalance = transactions.starAmount.starCount;
        }
        fetchPaymentOptions();
      });
    });
  }

  private void fetchPaymentOptions() {
    tdlib.send(new TdApi.GetStarPaymentOptions(), (result, error) -> {
      runOnUiThreadOptional(() -> {
        if (error != null) {
          showError(TD.toErrorString(error));
        } else {
          paymentOptions = (TdApi.StarPaymentOptions) result;
          buildCells();
        }
      });
    });
  }

  private void showError(String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TelegramStars));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  private void buildCells() {
    List<ListItem> items = new ArrayList<>();

    // Header and description
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.TelegramStars));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.StarsDescription));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Balance
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.StarsBalance));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    String balanceText = Lang.plural(R.string.xStars, starBalance);
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, balanceText));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Transaction history link
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_starTransactions, R.drawable.baseline_history_24, R.string.StarTransactions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Payment options
    if (paymentOptions != null && paymentOptions.options != null && paymentOptions.options.length > 0) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.BuyStars));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      boolean first = true;
      for (TdApi.StarPaymentOption option : paymentOptions.options) {
        if (option.isAdditional) continue; // Skip additional options for now
        
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;

        String title = Lang.plural(R.string.xStars, option.starCount);
        ListItem item = new ListItem(
          ListItem.TYPE_VALUED_SETTING_COMPACT,
          R.id.btn_starOption,
          R.drawable.baseline_star_24,
          title,
          false
        );
        item.setData(option);
        items.add(item);
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, true);
  }

  @Override
  public void onClick(View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_starOption) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() instanceof TdApi.StarPaymentOption) {
        TdApi.StarPaymentOption option = (TdApi.StarPaymentOption) item.getData();
        purchaseStars(option);
      }
    } else if (viewId == R.id.btn_starTransactions) {
      openTransactionHistory();
    }
  }

  private void purchaseStars(TdApi.StarPaymentOption option) {
    if (!StringUtils.isEmpty(option.storeProductId)) {
      // Store purchase - not supported in this build
      UI.showToast(R.string.PremiumStorePaymentNotAvailable, Toast.LENGTH_SHORT);
    } else {
      // Out-of-store purchase via Fragment/TON
      // This would require TelegramPaymentPurposeStars and createInvoiceLink
      UI.showToast(R.string.PremiumPaymentUnavailable, Toast.LENGTH_SHORT);
    }
  }

  private void openTransactionHistory() {
    StarTransactionsController controller = new StarTransactionsController(context(), tdlib);
    navigateTo(controller);
  }
}
