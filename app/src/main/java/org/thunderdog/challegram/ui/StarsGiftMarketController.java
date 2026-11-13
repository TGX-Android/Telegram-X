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
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;

final class StarsGiftMarketController extends RecyclerViewController<Void> implements View.OnClickListener {
  private final long giftId, recipientId, account;
  private final LinkedHashMap<String, TdApi.Object> entries = new LinkedHashMap<>();
  private final HashSet<String> cursors = new HashSet<>();
  private String offset = "";
  private SettingsAdapter adapter;
  private boolean loading, failed, saving;

  StarsGiftMarketController (Context context, Tdlib tdlib, long giftId, long recipientId) {
    super(context, tdlib);
    this.giftId = giftId;
    this.recipientId = recipientId;
    this.account = tdlib.myUserId();
  }

  private boolean owned () {
    return giftId == 0;
  }

  @Override public int getId () { return R.id.controller_giftMarket; }
  @Override public CharSequence getName () {
    return Lang.getString(owned() ? R.string.TgxStarsMyCollectibles : R.string.TgxStarsResale);
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recycler) {
    adapter = new SettingsAdapter(this);
    recycler.setAdapter(adapter);
    load();
  }

  private void load () {
    if (loading || saving || offset == null || account == 0 || account != tdlib.myUserId()) {
      return;
    }
    final String cursor = offset;
    loading = true;
    failed = false;
    render();
    if (owned()) {
      tdlib.send(new TdApi.GetReceivedGifts("", new TdApi.MessageSenderUser(account), 0,
        false, false, true, true, true, false, false, true, false, cursor, 30),
        (page, error) -> runOnUiThreadOptional(() -> {
          if (account != tdlib.myUserId()) {
            return;
          }
          if (page != null) {
            for (TdApi.ReceivedGift item : page.gifts) {
              if (!item.wasRefunded && item.receivedGiftId != null && !item.receivedGiftId.isEmpty() &&
                  item.gift instanceof TdApi.SentGiftUpgraded) {
                entries.put(item.receivedGiftId, item);
              }
            }
          }
          finishPage(cursor, page != null ? page.nextOffset : null, error);
        }));
    } else {
      tdlib.send(new TdApi.SearchGiftsForResale(giftId, new TdApi.GiftForResaleOrderPrice(), false, true,
        new TdApi.UpgradedGiftAttributeId[0], cursor, 30), (page, error) -> runOnUiThreadOptional(() -> {
          if (account != tdlib.myUserId()) {
            return;
          }
          if (page != null) {
            for (TdApi.GiftForResale item : page.gifts) {
              if (starPrice(item.gift) > 0) {
                entries.put(item.gift.name, item.gift);
              }
            }
          }
          finishPage(cursor, page != null ? page.nextOffset : null, error);
        }));
    }
  }

  private void finishPage (String cursor, String next, TdApi.Error error) {
    loading = false;
    failed = error != null;
    if (!failed) {
      cursors.add(cursor);
      offset = !StringUtils.isEmpty(next) && !cursors.contains(next) ? next : null;
    }
    render();
  }

  private void refresh () {
    if (loading || saving) {
      return;
    }
    entries.clear();
    cursors.clear();
    offset = "";
    load();
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    for (TdApi.Object entry : entries.values()) {
      TdApi.UpgradedGift gift = entry instanceof TdApi.ReceivedGift received ?
        ((TdApi.SentGiftUpgraded) received.gift).gift : (TdApi.UpgradedGift) entry;
      String title = Lang.getString(R.string.TgxStarsCollectibleNumber, gift.title, Lang.formatNumber(gift.number));
      long price = starPrice(gift);
      if (price > 0) {
        title = Lang.getString(R.string.TgxStarsGiftItem, title, Lang.formatNumber(price));
      }
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftMarketItem, R.drawable.baseline_stars_24, title).setData(entry));
    }
    if (loading || saving) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    } else if (failed || offset != null) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftMarketMore, 0,
        failed ? R.string.RetryChatList : R.string.TgxStarsMore));
    } else if (entries.isEmpty()) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxStarsGiftEmpty));
    }
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftMarketRefresh, 0, R.string.TgxPremiumRefreshStars));
    adapter.setItems(items, false);
  }

  static long starPrice (TdApi.UpgradedGift gift) {
    return gift != null && gift.resaleParameters != null && !gift.resaleParameters.gramOnly
      ? gift.resaleParameters.starCount : 0;
  }

  static boolean sameOffer (TdApi.UpgradedGift expected, TdApi.UpgradedGift fresh) {
    return fresh != null && expected.id == fresh.id && expected.name != null && expected.name.equals(fresh.name) && starPrice(expected) > 0 &&
      starPrice(expected) == starPrice(fresh);
  }

  @Override
  public void onClick (View view) {
    if (loading || saving) {
      return;
    }
    if (view.getId() == R.id.btn_giftMarketMore) {
      load();
    } else if (view.getId() == R.id.btn_giftMarketRefresh) {
      refresh();
    } else if (view.getId() == R.id.btn_giftMarketItem && view.getTag() instanceof ListItem row) {
      if (row.getData() instanceof TdApi.UpgradedGift gift) {
        confirmPurchase(gift);
      } else if (row.getData() instanceof TdApi.ReceivedGift received) {
        showListingActions(received);
      }
    }
  }

  private void confirmPurchase (TdApi.UpgradedGift expected) {
    long price = starPrice(expected);
    if (price <= 0) {
      return;
    }
    showConfirm(Lang.getString(R.string.TgxStarsBuyResaleConfirm, expected.title, Lang.formatNumber(expected.number),
      Lang.formatNumber(price), tdlib.cache().userName(recipientId)), Lang.getString(R.string.TgxStarsBuyResale), () -> {
        if (!begin()) {
          return;
        }
        tdlib.send(new TdApi.GetUpgradedGift(expected.name), (fresh, error) -> runOnUiThreadOptional(() -> {
          if (!current() || error != null || !sameOffer(expected, fresh) ||
              (fresh.ownerId instanceof TdApi.MessageSenderUser user && user.userId == account)) {
            afterMutation(error, false);
            return;
          }
          tdlib.send(new TdApi.SendResoldGift(fresh.name, new TdApi.MessageSenderUser(recipientId),
            new TdApi.GiftResalePriceStar(price), null, false), (result, purchaseError) -> runOnUiThreadOptional(() ->
            afterMutation(purchaseError, result instanceof TdApi.GiftResaleResultOk)));
        }));
      });
  }

  private void showListingActions (TdApi.ReceivedGift received) {
    TdApi.UpgradedGift gift = ((TdApi.SentGiftUpgraded) received.gift).gift;
    boolean listed = starPrice(gift) > 0;
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxStarsCollectibleNumber,
      gift.title, Lang.formatNumber(gift.number)));
    if (gift.resaleParameters == null || !gift.resaleParameters.gramOnly) {
      options.item(new OptionItem(1, Lang.getString(R.string.TgxStarsListForSale), OptionColor.NORMAL, 0));
    }
    if (listed) {
      options.item(new OptionItem(2, Lang.getString(R.string.TgxStarsUnlist), OptionColor.RED, 0));
    }
    options.cancelItem();
    showOptions(options.build(), (view, id) -> {
      if (id == 1) {
        enterListingPrice(received, gift);
      } else if (id == 2) {
        showConfirm(Lang.getString(R.string.TgxStarsUnlistConfirm), Lang.getString(R.string.TgxStarsUnlist),
          () -> setListing(received, gift, 0));
      }
      return true;
    });
  }

  private void enterListingPrice (TdApi.ReceivedGift received, TdApi.UpgradedGift gift) {
    long minimum = tdlib.options().giftResaleStarCountMin;
    long maximum = tdlib.options().giftResaleStarCountMax;
    CharSequence caption = Lang.getString(R.string.TgxStarsWithdrawalRange, Lang.formatNumber(minimum), Lang.formatNumber(maximum));
    long existing = starPrice(gift);
    var input = openInputAlert(Lang.getString(R.string.TgxStarsListForSale), caption, R.string.Continue, R.string.Cancel,
      existing > 0 ? Long.toString(existing) : null, (edit, value) -> {
        long amount;
        try {
          amount = Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
          return false;
        }
        if (amount < minimum || amount > maximum || amount <= 0) {
          return false;
        }
        tdlib.ui().post(() -> {
          if (current()) {
            showConfirm(Lang.getString(R.string.TgxStarsListingConfirm, gift.title, Lang.formatNumber(gift.number),
              Lang.formatNumber(amount)), Lang.getString(R.string.Save), () -> setListing(received, gift, amount));
          }
        });
        return true;
      }, true);
    input.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
  }

  private void setListing (TdApi.ReceivedGift received, TdApi.UpgradedGift previous, long amount) {
    if (!begin()) {
      return;
    }
    tdlib.send(new TdApi.GetReceivedGift(received.receivedGiftId), (fresh, error) -> runOnUiThreadOptional(() -> {
      if (!current() || error != null || fresh.wasRefunded || !(fresh.gift instanceof TdApi.SentGiftUpgraded upgraded) ||
          upgraded.gift.id != previous.id || !sameListing(previous, upgraded.gift) ||
          (amount > 0 && (fresh.nextResaleDate > tdlib.currentTime(TimeUnit.SECONDS) ||
            amount < tdlib.options().giftResaleStarCountMin || amount > tdlib.options().giftResaleStarCountMax))) {
        afterMutation(error, false);
        return;
      }
      tdlib.send(new TdApi.SetGiftResalePrice(received.receivedGiftId,
        amount == 0 ? null : new TdApi.GiftResalePriceStar(amount)), (ok, saveError) ->
        runOnUiThreadOptional(() -> afterMutation(saveError, saveError == null)));
    }));
  }

  static boolean sameListing (TdApi.UpgradedGift first, TdApi.UpgradedGift second) {
    TdApi.GiftResaleParameters a = first.resaleParameters, b = second.resaleParameters;
    return a == b ? a == null || !a.gramOnly : (a != null && b != null && a.starCount == b.starCount && a.gramCentCount == b.gramCentCount &&
      a.gramOnly == b.gramOnly && !b.gramOnly);
  }

  private boolean current () {
    return isFocused() && account != 0 && account == tdlib.myUserId();
  }

  private boolean begin () {
    if (!current() || saving || loading) {
      return false;
    }
    saving = true;
    render();
    return true;
  }

  private void afterMutation (TdApi.Error error, boolean success) {
    saving = false;
    if (current()) {
      if (error != null) {
        UI.showError(error);
      } else {
        UI.showToast(success ? R.string.TgxStarsMarketSaved : R.string.TgxStarsGiftChanged, Toast.LENGTH_SHORT);
      }
      refresh();
    } else {
      render();
    }
  }
}
