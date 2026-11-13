/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
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
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.SenderPickerDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;

public final class StarsController extends RecyclerViewController<Void> implements View.OnClickListener {
  private enum Page { ALL, INCOMING, OUTGOING, SUBSCRIPTIONS, TOP_UP }

  private final long requiredBalance;
  private final String purpose;
  private final Page initialPage;
  private Page page;
  private SettingsAdapter adapter;
  private TdApi.StarAmount balance;
  private int balanceRevision;
  private final RunnableData<TdApi.StarAmount> balanceListener = amount -> runOnUiThreadOptional(() -> {
    balanceRevision++;
    balance = amount;
    render();
  });
  private TdApi.StarPaymentOption[] topUps;
  private final LinkedHashMap<String, TdApi.StarTransaction> history = new LinkedHashMap<>();
  private final LinkedHashMap<String, TdApi.StarSubscription> subscriptions = new LinkedHashMap<>();
  private final HashSet<String> completedOffsets = new HashSet<>();
  private String offset;
  private boolean loading, failed, editing;
  private int generation;

  public StarsController (Context context, Tdlib tdlib, boolean topUp, long requiredBalance, String purpose) {
    super(context, tdlib);
    this.initialPage = topUp ? Page.TOP_UP : Page.ALL;
    this.requiredBalance = requiredBalance;
    this.purpose = purpose;
  }

  @Override
  public int getId () { return R.id.controller_tgxStars; }

  @Override
  public CharSequence getName () { return Lang.getString(R.string.TgxStarsTitle); }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getData() instanceof TdApi.StarTransaction transaction) {
          String amount = StarsHistory.amount(transaction.starAmount, Lang.dateFormatLocale(), true);
          view.setData(Lang.getString(R.string.TgxStarsDatedAmount, amount, date(transaction.date)));
        } else if (item.getData() instanceof TdApi.StarSubscription subscription) {
          String price = Lang.getString(R.string.TgxStarsRecurring, Lang.formatNumber(subscription.pricing.starCount),
            Lang.getDuration(subscription.pricing.period));
          view.setData(Lang.getString(R.string.TgxStarsSubscriptionSummary, price, Lang.getString(subscriptionStatus(subscription))));
        } else if (item.getData() instanceof TdApi.StarPaymentOption option) {
          view.setData(topupPrice(option));
        }
      }
    };
    recyclerView.setAdapter(adapter);
    balance = tdlib.ownedStarAmount();
    tdlib.addStarAmountListener(balanceListener);
    showPage(initialPage);
    if (initialPage == Page.TOP_UP && balance == null && tdlib.myUserId() != 0) {
      final int requestGeneration = generation;
      final int requestBalanceRevision = balanceRevision;
      tdlib.send(new TdApi.GetStarTransactions(new TdApi.MessageSenderUser(tdlib.myUserId()), "", null, "", 1),
        (result, error) -> runOnUiThreadOptional(() -> {
          if (generation == requestGeneration && balanceRevision == requestBalanceRevision && result != null) {
            balance = result.starAmount;
            render();
          }
        }));
    }
  }

  @Override
  public void destroy () {
    tdlib.removeStarAmountListener(balanceListener);
    super.destroy();
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (adapter != null && isFocused() && !editing && page != null) {
      showPage(page);
    }
  }

  private void showPage (Page selected) {
    page = selected;
    generation++;
    history.clear();
    subscriptions.clear();
    completedOffsets.clear();
    topUps = null;
    offset = "";
    loading = failed = false;
    loadPage();
  }

  private void loadPage () {
    if (loading || offset == null || tdlib.myUserId() == 0) {
      render();
      return;
    }
    loading = true;
    failed = false;
    render();
    final int requestGeneration = generation;
    final String requested = offset;
    final int requestBalanceRevision = balanceRevision;
    if (page == Page.TOP_UP) {
      tdlib.send(new TdApi.GetStarPaymentOptions(), (result, error) -> runOnUiThreadOptional(() -> {
        if (requestGeneration == generation) {
          if (result != null) {
            topUps = result.options;
          }
          complete(requested, "", error);
        }
      }));
    } else if (page == Page.SUBSCRIPTIONS) {
      tdlib.send(new TdApi.GetStarSubscriptions(false, requested), (result, error) -> runOnUiThreadOptional(() -> {
        if (requestGeneration == generation) {
          if (result != null) {
            if (requested.isEmpty() && requestBalanceRevision == balanceRevision) {
              balance = result.starAmount;
            }
            for (TdApi.StarSubscription subscription : result.subscriptions) {
              if (subscription.pricing != null) {
                subscriptions.put(subscription.id, subscription);
              }
            }
          }
          complete(requested, result != null ? result.nextOffset : null, error);
        }
      }));
    } else {
      TdApi.TransactionDirection direction = page == Page.INCOMING ? new TdApi.TransactionDirectionIncoming() :
        page == Page.OUTGOING ? new TdApi.TransactionDirectionOutgoing() : null;
      tdlib.send(new TdApi.GetStarTransactions(new TdApi.MessageSenderUser(tdlib.myUserId()), "", direction, requested, 30),
        (result, error) -> runOnUiThreadOptional(() -> {
          if (requestGeneration == generation) {
            if (result != null) {
              if (requested.isEmpty() && requestBalanceRevision == balanceRevision) {
                balance = result.starAmount;
              }
              for (TdApi.StarTransaction transaction : result.transactions) {
                if (transaction.starAmount != null) {
                  history.put(StarsHistory.transactionKey(transaction), transaction);
                }
              }
            }
            complete(requested, result != null ? result.nextOffset : null, error);
          }
        }));
    }
  }

  private void complete (String requested, String next, TdApi.Error error) {
    loading = false;
    failed = error != null;
    if (!failed) {
      completedOffsets.add(requested);
      offset = !StringUtils.isEmpty(next) && !completedOffsets.contains(next) ? next : null;
    }
    render();
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    CharSequence summary = balance == null ? Lang.getString(R.string.TgxStarsTitle) :
      Lang.getString(R.string.TgxStarsBalance, StarsHistory.amount(balance, Lang.dateFormatLocale(), false));
    items.add(new ListItem(ListItem.TYPE_ICONIZED_EMPTY, 0, R.drawable.baseline_stars_24, summary));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxStarsView, 0,
      Lang.getString(R.string.TgxStarsChooseView, Lang.getString(pageLabel(page)))));
    switch (page) {
      case TOP_UP -> renderTopUps(items);
      case SUBSCRIPTIONS -> {
        for (TdApi.StarSubscription subscription : subscriptions.values()) {
          String title = tdlib.chat(subscription.chatId) != null ? tdlib.chatTitle(subscription.chatId) :
            Lang.getString(R.string.TgxStarsUnknownChat, Long.toString(subscription.chatId));
          items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgxStarsRow, 0, title).setData(subscription));
        }
      }
      default -> {
        for (TdApi.StarTransaction transaction : history.values()) {
          items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_tgxStarsRow, 0, transactionTitle(transaction))
            .setData(transaction));
        }
      }
    }
    if (loading || editing) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    } else if (failed) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxStarsMore, 0, R.string.RetryChatList));
    } else if (offset != null && page != Page.TOP_UP) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxStarsMore, 0, R.string.TgxStarsMore));
    } else if (page == Page.SUBSCRIPTIONS ? subscriptions.isEmpty() : page != Page.TOP_UP && history.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxStarsEmpty));
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxStarsRefresh, 0, R.string.TgxPremiumRefreshStars));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_starsGifts, 0, R.string.TgxStarsGiftCatalog));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_starsRevenue, 0, R.string.TgxStarsRevenue));
    adapter.setItems(items, false);
  }

  private void renderTopUps (ArrayList<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxStarsTopUpInfo));
    if (requiredBalance > 0) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.TgxStarsRequired, Lang.formatNumber(requiredBalance))));
    }
    if (topUps != null) {
      for (TdApi.StarPaymentOption option : topUps) {
        if (option.starCount > 0 && option.amount > 0 && PremiumCatalog.currencyExponent(option.currency) >= 0) {
          items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, 0, 0,
            Lang.getString(R.string.TgxStarsPack, Lang.formatNumber(option.starCount))).setData(option));
        }
      }
    }
    items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_tgxStarsOfficial, 0, R.string.TgxPremiumViewOptions));
  }

  private String topupPrice (TdApi.StarPaymentOption option) {
    return PremiumCatalog.formatAmount(new TdApi.PremiumPaymentOption(option.currency, option.amount, 0, 1, "", null),
      Lang.dateFormatLocale());
  }

  private static int pageLabel (Page page) {
    return switch (page) {
      case ALL -> R.string.TgxStarsAll;
      case INCOMING -> R.string.TgxStarsIncoming;
      case OUTGOING -> R.string.TgxStarsOutgoing;
      case SUBSCRIPTIONS -> R.string.TgxStarsSubscriptions;
      case TOP_UP -> R.string.TgxStarsTopUp;
    };
  }

  @Override
  public void onClick (View view) {
    int id = view.getId();
    if (editing) {
      return;
    }
    if (id == R.id.btn_tgxStarsView) {
      Options.Builder options = new Options.Builder();
      for (Page tab : Page.values()) {
        options.item(new OptionItem(tab.ordinal() + 1, Lang.getString(pageLabel(tab)), OptionColor.NORMAL,
          tab == page ? R.drawable.baseline_check_24 : 0));
      }
      showOptions(options.build(), (button, selected) -> {
        showPage(Page.values()[selected - 1]);
        return true;
      });
    } else if (id == R.id.btn_starsGifts) {
      pickGiftRecipient();
    } else if (id == R.id.btn_starsRevenue) {
      chooseRevenueOwner();
    } else if (id == R.id.btn_tgxStarsRefresh) {
      showPage(page);
    } else if (id == R.id.btn_tgxStarsMore) {
      loadPage();
    } else if (id == R.id.btn_tgxStarsOfficial) {
      confirmTopUp(requiredBalance);
    } else if (id == R.id.btn_tgxStarsRow && view.getTag() instanceof ListItem item) {
      if (item.getData() instanceof TdApi.StarTransaction transaction) {
        showTransaction(transaction);
      } else if (item.getData() instanceof TdApi.StarSubscription subscription) {
        showSubscription(subscription);
      }
    }
  }

  public void pickGiftRecipient () {
    ContactsController picker = new ContactsController(context(), tdlib);
    picker.setArguments(new ContactsController.Args(new SenderPickerDelegate() {
      @Override
      public boolean allowGlobalSearch () { return true; }

      @Override
      public String getUserPickTitle () { return Lang.getString(R.string.TgxStarsGiftRecipient); }

      @Override
      public boolean onSenderPick (ContactsController origin, View view, TdApi.MessageSender senderId) {
        if (senderId instanceof TdApi.MessageSenderUser user && user.userId != tdlib.myUserId() &&
            tdlib.cache().user(user.userId) != null &&
            tdlib.cache().user(user.userId).type instanceof TdApi.UserTypeRegular) {
          origin.navigateTo(new StarsGiftsController(context(), tdlib, user.userId));
        } else {
          UI.showToast(R.string.TgxStarsGiftRecipientError, Toast.LENGTH_SHORT);
        }
        return false;
      }
    }));
    navigateTo(picker);
  }

  public void openPersonalRevenue () {
    long userId = tdlib.myUserId();
    if (userId != 0) {
      navigateTo(new StarsRevenueController(context(), tdlib, new TdApi.MessageSenderUser(userId), true));
    }
  }

  private void chooseRevenueOwner () {
    showOptions(new int[] {R.id.btn_starsPersonalRevenue, R.id.btn_starsBotRevenue, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.TgxStarsPersonalRevenue), Lang.getString(R.string.TgxStarsBotRevenue),
        Lang.getString(R.string.Cancel)}, (view, id) -> {
          if (id == R.id.btn_starsPersonalRevenue) {
            openPersonalRevenue();
          } else if (id == R.id.btn_starsBotRevenue) {
            loadRevenueBots();
          }
          return true;
        });
  }

  private void loadRevenueBots () {
    tdlib.send(new TdApi.GetOwnedBots(), (result, error) -> runOnUiThreadOptional(() -> {
      if (!isFocused()) {
        return;
      }
      if (error != null) {
        UI.showError(error);
        return;
      }
      if (result.userIds.length == 0) {
        UI.showToast(R.string.TgxStarsNoOwnedBots, Toast.LENGTH_SHORT);
        return;
      }
      Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxStarsBotRevenue));
      for (int i = 0; i < result.userIds.length; i++) {
        options.item(new OptionItem(i + 1, tdlib.cache().userName(result.userIds[i]), OptionColor.NORMAL, 0));
      }
      options.cancelItem();
      showOptions(options.build(), (view, id) -> {
        if (id > 0 && id <= result.userIds.length) {
          long bot = result.userIds[id - 1];
          navigateTo(new StarsRevenueController(context(), tdlib, new TdApi.MessageSenderUser(bot), true));
        }
        return true;
      });
    }));
  }

  private void confirmTopUp (long minimumBalance) {
    showConfirm(Lang.getString(R.string.TgxStarsTopUpInfo), Lang.getString(R.string.Continue), () ->
      TelegramOfficialLink.open(this, minimumBalance > 0
        ? new TdApi.InternalLinkTypeStarPurchase(minimumBalance, purpose)
        : new TdApi.InternalLinkTypeSettings(new TdApi.SettingsSectionMyStars("top-up"))));
  }

  private static String date (int seconds) {
    return Lang.dateYearShortTime(seconds, TimeUnit.SECONDS);
  }

  private static String transactionTitle (TdApi.StarTransaction transaction) {
    if (transaction.isRefund) {
      return Lang.getString(R.string.TgxStarsRefund);
    }
    if (transaction.type == null) {
      return Lang.getString(R.string.TgxStarsTransaction);
    }
    int label = switch (transaction.type.getConstructor()) {
      case TdApi.StarTransactionTypePremiumBotDeposit.CONSTRUCTOR,
           TdApi.StarTransactionTypeAppStoreDeposit.CONSTRUCTOR,
           TdApi.StarTransactionTypeGooglePlayDeposit.CONSTRUCTOR,
           TdApi.StarTransactionTypeFragmentDeposit.CONSTRUCTOR,
           TdApi.StarTransactionTypeUserDeposit.CONSTRUCTOR,
           TdApi.StarTransactionTypeGiveawayDeposit.CONSTRUCTOR -> R.string.TgxStarsDeposit;
      case TdApi.StarTransactionTypeFragmentWithdrawal.CONSTRUCTOR,
           TdApi.StarTransactionTypeTelegramAdsWithdrawal.CONSTRUCTOR -> R.string.TgxStarsWithdrawal;
      case TdApi.StarTransactionTypeBotPaidMediaPurchase.CONSTRUCTOR,
           TdApi.StarTransactionTypeBotPaidMediaSale.CONSTRUCTOR,
           TdApi.StarTransactionTypeChannelPaidMediaPurchase.CONSTRUCTOR,
           TdApi.StarTransactionTypeChannelPaidMediaSale.CONSTRUCTOR -> R.string.TgxStarsPaidMedia;
      case TdApi.StarTransactionTypeBotSubscriptionPurchase.CONSTRUCTOR,
           TdApi.StarTransactionTypeBotSubscriptionSale.CONSTRUCTOR,
           TdApi.StarTransactionTypeChannelSubscriptionPurchase.CONSTRUCTOR,
           TdApi.StarTransactionTypeChannelSubscriptionSale.CONSTRUCTOR -> R.string.TgxStarsSubscriptionTransaction;
      case TdApi.StarTransactionTypeGiftAuctionBid.CONSTRUCTOR,
           TdApi.StarTransactionTypeGiftPurchase.CONSTRUCTOR,
           TdApi.StarTransactionTypeGiftPurchaseOffer.CONSTRUCTOR,
           TdApi.StarTransactionTypeGiftTransfer.CONSTRUCTOR,
           TdApi.StarTransactionTypeGiftOriginalDetailsDrop.CONSTRUCTOR,
           TdApi.StarTransactionTypeGiftSale.CONSTRUCTOR,
           TdApi.StarTransactionTypeGiftUpgrade.CONSTRUCTOR,
           TdApi.StarTransactionTypeGiftUpgradePurchase.CONSTRUCTOR,
           TdApi.StarTransactionTypeUpgradedGiftPurchase.CONSTRUCTOR,
           TdApi.StarTransactionTypeUpgradedGiftSale.CONSTRUCTOR -> R.string.TgxStarsGift;
      case TdApi.StarTransactionTypeChannelPaidReactionSend.CONSTRUCTOR,
           TdApi.StarTransactionTypeChannelPaidReactionReceive.CONSTRUCTOR,
           TdApi.StarTransactionTypePaidGroupCallReactionSend.CONSTRUCTOR,
           TdApi.StarTransactionTypePaidGroupCallReactionReceive.CONSTRUCTOR -> R.string.TgxStarsPaidReaction;
      case TdApi.StarTransactionTypePaidMessageSend.CONSTRUCTOR,
           TdApi.StarTransactionTypePaidMessageReceive.CONSTRUCTOR,
           TdApi.StarTransactionTypePaidGroupCallMessageSend.CONSTRUCTOR,
           TdApi.StarTransactionTypePaidGroupCallMessageReceive.CONSTRUCTOR -> R.string.TgxStarsPaidMessage;
      case TdApi.StarTransactionTypePremiumPurchase.CONSTRUCTOR -> R.string.TgxPremiumTitle;
      default -> R.string.TgxStarsTransaction;
    };
    return Lang.getString(label);
  }

  private void showTransaction (TdApi.StarTransaction transaction) {
    showOptions(transactionTitle(transaction) + "\n" +
      Lang.getString(R.string.TgxStarsDatedAmount, StarsHistory.amount(transaction.starAmount, Lang.dateFormatLocale(), true),
        date(transaction.date)),
      new int[] {R.id.btn_tgxStarsCopy, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.TgxStarsCopyId), Lang.getString(R.string.Cancel)},
      (view, id) -> {
        if (id == R.id.btn_tgxStarsCopy) {
          UI.copyText(transaction.id, R.string.CopiedText);
        }
        return true;
      });
  }

  private int subscriptionStatus (TdApi.StarSubscription subscription) {
    if (subscription.expirationDate <= tdlib.currentTime(TimeUnit.SECONDS)) {
      return R.string.TgxStarsExpired;
    }
    return subscription.isCanceled ? R.string.TgxStarsCanceled :
      subscription.isExpiring ? R.string.TgxStarsExpiring : R.string.TgxStarsRenewing;
  }

  private void showSubscription (TdApi.StarSubscription subscription) {
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxStarsSubscriptionDate,
      Lang.getString(subscriptionStatus(subscription)), date(subscription.expirationDate)));
    options.item(new OptionItem(R.id.btn_tgxStarsOpenChat, Lang.getString(R.string.OpenChat), OptionColor.NORMAL, 0));
    int action = subscription.isCanceled ? R.string.TgxStarsEnableRenewal : R.string.TgxStarsCancelRenewal;
    if (subscription.expirationDate > tdlib.currentTime(TimeUnit.SECONDS)) {
      options.item(new OptionItem(R.id.btn_tgxStarsChangeRenewal, Lang.getString(action), OptionColor.NORMAL, 0));
    }
    options.cancelItem();
    showOptions(options.build(), (view, id) -> {
      if (id == R.id.btn_tgxStarsOpenChat) {
        tdlib.ui().openChat(this, subscription.chatId, null);
      } else if (id == R.id.btn_tgxStarsChangeRenewal) {
        showConfirm(Lang.getString(subscription.isCanceled ? R.string.TgxStarsEnableConfirm : R.string.TgxStarsCancelConfirm),
          Lang.getString(action), () -> startRenewalChange(subscription));
      }
      return true;
    });
  }

  private void startRenewalChange (TdApi.StarSubscription expected) {
    if (editing || page != Page.SUBSCRIPTIONS || !isFocused()) {
      return;
    }
    editing = true;
    render();
    verifySubscription(expected, "", new HashSet<>());
  }

  private void verifySubscription (TdApi.StarSubscription expected, String cursor, HashSet<String> visited) {
    if (!visited.add(cursor)) {
      finishRenewal(null, false);
      return;
    }
    tdlib.send(new TdApi.GetStarSubscriptions(false, cursor), (result, error) -> runOnUiThreadOptional(() -> {
      if (!isFocused() || error != null) {
        finishRenewal(error, false);
        return;
      }
      for (TdApi.StarSubscription fresh : result.subscriptions) {
        if (expected.id.equals(fresh.id)) {
          if (StarsHistory.canEdit(expected, fresh, tdlib.currentTime(TimeUnit.SECONDS))) {
            tdlib.send(new TdApi.EditStarSubscription(fresh.id, !fresh.isCanceled),
              (ok, editError) -> runOnUiThreadOptional(() -> finishRenewal(editError, editError == null)));
          } else {
            finishRenewal(null, false);
          }
          return;
        }
      }
      if (StringUtils.isEmpty(result.nextOffset)) {
        finishRenewal(null, false);
      } else {
        verifySubscription(expected, result.nextOffset, visited);
      }
    }));
  }

  private void finishRenewal (TdApi.Error error, boolean success) {
    editing = false;
    if (isFocused()) {
      if (error != null) {
        UI.showError(error);
      } else if (!success) {
        UI.showToast(R.string.TgxStarsSubscriptionChanged, Toast.LENGTH_SHORT);
      }
    }
    showPage(Page.SUBSCRIPTIONS);
  }
}
