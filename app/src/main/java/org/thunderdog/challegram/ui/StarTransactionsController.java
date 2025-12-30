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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Controller for viewing Star transaction history.
 */
public class StarTransactionsController extends RecyclerViewController<Void> implements View.OnClickListener {

  private SettingsAdapter adapter;
  private TdApi.StarTransactions transactions;

  public StarTransactionsController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName() {
    return Lang.getString(R.string.StarTransactions);
  }

  @Override
  public int getId() {
    return R.id.controller_starTransactions;
  }

  @Override
  protected void onCreateView(Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting(ListItem item, SettingView view, boolean isUpdate) {
        // Custom rendering for transaction items if needed
      }
    };

    recyclerView.setAdapter(adapter);

    // Show loading state
    buildLoadingCells();

    // Fetch transactions
    fetchTransactions(null);
  }

  private void buildLoadingCells() {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.StarTransactions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, false);
  }

  private void fetchTransactions(String offset) {
    tdlib.send(new TdApi.GetStarTransactions(
      new TdApi.MessageSenderUser(tdlib.myUserId()),
      null, // subscriptionId
      null, // direction - null for all
      offset,
      50 // limit
    ), (result, error) -> {
      runOnUiThreadOptional(() -> {
        if (error != null) {
          showError(TD.toErrorString(error));
        } else {
          transactions = (TdApi.StarTransactions) result;
          buildCells();
        }
      });
    });
  }

  private void showError(String error) {
    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.StarTransactions));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, error));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    adapter.setItems(items, true);
  }

  private void buildCells() {
    List<ListItem> items = new ArrayList<>();

    // Balance header
    items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.StarsBalance));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    String balanceText = Lang.plural(R.string.xStars, transactions.starAmount.starCount);
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, balanceText));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // Transactions
    if (transactions.transactions != null && transactions.transactions.length > 0) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.StarTransactions));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      boolean first = true;
      for (TdApi.StarTransaction transaction : transactions.transactions) {
        if (!first) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR));
        }
        first = false;

        String title = getTransactionTitle(transaction);
        String amount = formatTransactionAmount(transaction);
        String date = Lang.dateYearShortTime(transaction.date, TimeUnit.SECONDS);
        
        ListItem item = new ListItem(
          ListItem.TYPE_INFO_MULTILINE,
          0,
          transaction.starAmount.starCount >= 0 ? R.drawable.baseline_add_24 : R.drawable.baseline_remove_circle_24,
          title,
          false
        );
        item.setString(amount + " • " + date);
        items.add(item);
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    } else {
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.NoStarTransactions));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    adapter.setItems(items, true);
  }

  private String getTransactionTitle(TdApi.StarTransaction transaction) {
    if (transaction.type == null) {
      return transaction.id;
    }

    switch (transaction.type.getConstructor()) {
      case TdApi.StarTransactionTypeFragmentDeposit.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxFragment);
      case TdApi.StarTransactionTypeAppStoreDeposit.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxAppStore);
      case TdApi.StarTransactionTypeGooglePlayDeposit.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxGooglePlay);
      case TdApi.StarTransactionTypePremiumBotDeposit.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxPremiumBot);
      case TdApi.StarTransactionTypeUserDeposit.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxUserDeposit);
      case TdApi.StarTransactionTypeGiveawayDeposit.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxGiveaway);
      case TdApi.StarTransactionTypeFragmentWithdrawal.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxWithdrawal);
      case TdApi.StarTransactionTypeTelegramAdsWithdrawal.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxAdsWithdrawal);
      case TdApi.StarTransactionTypeTelegramApiUsage.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxApiUsage);
      case TdApi.StarTransactionTypeBotPaidMediaPurchase.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxMediaPurchase);
      case TdApi.StarTransactionTypeBotPaidMediaSale.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxMediaSale);
      case TdApi.StarTransactionTypeChannelPaidMediaPurchase.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxChannelMedia);
      case TdApi.StarTransactionTypeChannelPaidMediaSale.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxChannelMediaSale);
      case TdApi.StarTransactionTypeBotInvoicePurchase.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxBotInvoice);
      case TdApi.StarTransactionTypeBotInvoiceSale.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxBotInvoiceSale);
      case TdApi.StarTransactionTypeBotSubscriptionPurchase.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxSubscription);
      case TdApi.StarTransactionTypeBotSubscriptionSale.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxSubscriptionSale);
      case TdApi.StarTransactionTypeChannelSubscriptionPurchase.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxChannelSubscription);
      case TdApi.StarTransactionTypeChannelSubscriptionSale.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxChannelSubscriptionSale);
      case TdApi.StarTransactionTypeGiftPurchase.CONSTRUCTOR:
      case TdApi.StarTransactionTypeGiftPurchaseOffer.CONSTRUCTOR:
      case TdApi.StarTransactionTypeUpgradedGiftPurchase.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxGiftPurchase);
      case TdApi.StarTransactionTypeGiftSale.CONSTRUCTOR:
      case TdApi.StarTransactionTypeUpgradedGiftSale.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxGiftSale);
      case TdApi.StarTransactionTypeGiftAuctionBid.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxGiftAuction);
      case TdApi.StarTransactionTypeGiftTransfer.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxGiftTransfer);
      case TdApi.StarTransactionTypeGiftUpgrade.CONSTRUCTOR:
      case TdApi.StarTransactionTypeGiftUpgradePurchase.CONSTRUCTOR:
      case TdApi.StarTransactionTypeGiftOriginalDetailsDrop.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxGiftUpgrade);
      case TdApi.StarTransactionTypeChannelPaidReactionSend.CONSTRUCTOR:
      case TdApi.StarTransactionTypePaidGroupCallReactionSend.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxReactionSend);
      case TdApi.StarTransactionTypeChannelPaidReactionReceive.CONSTRUCTOR:
      case TdApi.StarTransactionTypePaidGroupCallReactionReceive.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxReactionReceive);
      case TdApi.StarTransactionTypeAffiliateProgramCommission.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxAffiliateCommission);
      case TdApi.StarTransactionTypePaidMessageSend.CONSTRUCTOR:
      case TdApi.StarTransactionTypePaidGroupCallMessageSend.CONSTRUCTOR:
      case TdApi.StarTransactionTypeSuggestedPostPaymentSend.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxPaidMessage);
      case TdApi.StarTransactionTypePaidMessageReceive.CONSTRUCTOR:
      case TdApi.StarTransactionTypePaidGroupCallMessageReceive.CONSTRUCTOR:
      case TdApi.StarTransactionTypeSuggestedPostPaymentReceive.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxPaidMessageReceive);
      case TdApi.StarTransactionTypePremiumPurchase.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxPremiumPurchase);
      case TdApi.StarTransactionTypeBusinessBotTransferSend.CONSTRUCTOR:
      case TdApi.StarTransactionTypeBusinessBotTransferReceive.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxBotTransfer);
      case TdApi.StarTransactionTypePublicPostSearch.CONSTRUCTOR:
        return Lang.getString(R.string.StarTxPublicSearch);
      case TdApi.StarTransactionTypeUnsupported.CONSTRUCTOR:
      default:
        return Lang.getString(R.string.StarTxUnknown);
    }
  }

  private String formatTransactionAmount(TdApi.StarTransaction transaction) {
    long amount = transaction.starAmount.starCount;
    String prefix = amount >= 0 ? "+" : "";
    return prefix + Lang.plural(R.string.xStars, Math.abs(amount));
  }

  @Override
  public void onClick(View v) {
    // Handle clicks on transaction items if needed
  }
}
