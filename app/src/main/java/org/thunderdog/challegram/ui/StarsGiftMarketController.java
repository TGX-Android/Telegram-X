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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Objects;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

import me.vkryl.core.StringUtils;

final class StarsGiftMarketController extends RecyclerViewController<Void> implements View.OnClickListener {
  private final long giftId, recipientId, account;
  private final TdApi.MessageSender profile;
  private final LinkedHashMap<String, TdApi.Object> entries = new LinkedHashMap<>();
  private final HashSet<String> cursors = new HashSet<>();
  private String offset = "";
  private SettingsAdapter adapter;
  private boolean loading, failed, saving;

  StarsGiftMarketController (Context context, Tdlib tdlib, long giftId, long recipientId) {
    this(context, tdlib, giftId, recipientId, null);
  }

  StarsGiftMarketController (Context context, Tdlib tdlib, TdApi.MessageSender profile) {
    this(context, tdlib, 0, tdlib.myUserId(), profile);
  }

  private StarsGiftMarketController (Context context, Tdlib tdlib, long giftId, long recipientId, TdApi.MessageSender profile) {
    super(context, tdlib);
    this.profile = profile;
    this.giftId = giftId;
    this.recipientId = recipientId;
    this.account = tdlib.myUserId();
  }

  private boolean owned () {
    return giftId == 0 && profile == null;
  }

  @Override public int getId () { return R.id.controller_giftMarket; }
  @Override public CharSequence getName () {
    return Lang.getString(profile != null ? R.string.TgxStarsPublicCollectibles :
      owned() ? R.string.TgxStarsMyCollectibles : R.string.TgxStarsResale);
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
    if (owned() || profile != null) {
      tdlib.send(new TdApi.GetReceivedGifts("", owned() ? new TdApi.MessageSenderUser(account) : profile, 0,
        profile != null, false, true, true, true, false, false, true, false, cursor, 30),
        (page, error) -> runOnUiThreadOptional(() -> {
          if (account != tdlib.myUserId()) {
            return;
          }
          if (page != null) {
            for (TdApi.ReceivedGift item : page.gifts) {
              if (!item.wasRefunded && item.gift instanceof TdApi.SentGiftUpgraded upgraded) {
                if (profile != null) {
                  entries.put(upgraded.gift.name, upgraded.gift);
                } else if (!StringUtils.isEmpty(item.receivedGiftId)) {
                  entries.put(item.receivedGiftId, item);
                }
              }
            }
          }
          finishPage(cursor, page != null ? page.nextOffset : null, error);
        }));
    } else {
      tdlib.send(new TdApi.SearchGiftsForResale(giftId, new TdApi.GiftForResaleOrderPrice(), false, false,
        new TdApi.UpgradedGiftAttributeId[0], cursor, 30), (page, error) -> runOnUiThreadOptional(() -> {
          if (account != tdlib.myUserId()) {
            return;
          }
          if (page != null) {
            for (TdApi.GiftForResale item : page.gifts) {
              if (starPrice(item.gift) > 0 || StarsCollectibleController.tonPrice(item.gift) > 0) {
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
      } else if (StarsCollectibleController.tonPrice(gift) > 0) {
        title = Lang.getString(R.string.TgxStarsCollectiblePriced, title,
          StarsCollectibleController.formatPrice(new TdApi.GiftResalePriceGram(StarsCollectibleController.tonPrice(gift))));
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
        navigateTo(new StarsCollectibleController(context, tdlib, gift.name, recipientId));
      } else if (row.getData() instanceof TdApi.ReceivedGift received) {
        showListingActions(received);
      }
    }
  }

  private void showListingActions (TdApi.ReceivedGift received) {
    TdApi.UpgradedGift gift = ((TdApi.SentGiftUpgraded) received.gift).gift;
    long stars = starPrice(gift), ton = StarsCollectibleController.tonPrice(gift);
    boolean listed = stars > 0 || ton > 0;
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxStarsCollectibleNumber,
      gift.title, Lang.formatNumber(gift.number)));
    options.item(new OptionItem(1, Lang.getString(stars > 0 ? R.string.TgxStarsEditInStars : R.string.TgxStarsListInStars), OptionColor.NORMAL, 0));
    options.item(new OptionItem(2, Lang.getString(ton > 0 && gift.resaleParameters.gramOnly ? R.string.TgxStarsEditInTon : R.string.TgxStarsListInTon), OptionColor.NORMAL, 0));
    if (listed) {
      options.item(new OptionItem(3, Lang.getString(R.string.TgxStarsUnlist), OptionColor.RED, 0));
    }
    options.cancelItem();
    showOptions(options.build(), (view, id) -> {
      if (id == 1 || id == 2) {
        enterListingPrice(received, gift, id == 2);
      } else if (id == 3) {
        showConfirm(Lang.getString(R.string.TgxStarsUnlistConfirm), Lang.getString(R.string.TgxStarsUnlist),
          () -> setListing(received, gift, null));
      }
      return true;
    });
  }

  private long minimum (boolean ton) {
    return ton ? tdlib.options().giftResaleGramCentCountMin : tdlib.options().giftResaleStarCountMin;
  }

  private long maximum (boolean ton) {
    return ton ? tdlib.options().giftResaleGramCentCountMax : tdlib.options().giftResaleStarCountMax;
  }

  private void enterListingPrice (TdApi.ReceivedGift received, TdApi.UpgradedGift gift, boolean ton) {
    long minimum = minimum(ton), maximum = maximum(ton);
    CharSequence caption = Lang.getString(R.string.TgxStarsWithdrawalRange,
      StarsCollectibleController.formatPrice(StarsCollectibleController.price(ton, minimum)),
      StarsCollectibleController.formatPrice(StarsCollectibleController.price(ton, maximum)));
    long existing = ton ? StarsCollectibleController.tonPrice(gift) : starPrice(gift);
    String initial = existing <= 0 ? null : ton ? BigDecimal.valueOf(existing, 2).stripTrailingZeros().toPlainString() :
      Long.toString(existing);
    var input = openInputAlert(Lang.getString(ton ? R.string.TgxStarsListInTon : R.string.TgxStarsListInStars),
      caption, R.string.Continue, R.string.Cancel, initial, (edit, value) -> {
        long amount = StarsCollectibleController.parseAmount(value, ton);
        if (!validListingAmount(amount, minimum, maximum)) {
          return false;
        }
        TdApi.GiftResalePrice price = StarsCollectibleController.price(ton, amount);
        tdlib.ui().post(() -> {
          if (current()) {
            showConfirm(Lang.getString(R.string.TgxStarsListingPriceConfirm, gift.title, Lang.formatNumber(gift.number),
              StarsCollectibleController.formatPrice(price)), Lang.getString(R.string.Save),
              () -> setListing(received, gift, price));
          }
        });
        return true;
      }, true);
    input.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | (ton ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
  }

  static boolean validListingAmount (long amount, long minimum, long maximum) {
    return amount > 0 && amount >= minimum && amount <= maximum;
  }

  private void setListing (TdApi.ReceivedGift received, TdApi.UpgradedGift previous, TdApi.GiftResalePrice price) {
    if (!begin()) {
      return;
    }
    tdlib.send(new TdApi.GetReceivedGift(received.receivedGiftId), (fresh, error) -> runOnUiThreadOptional(() -> {
      if (!current() || error != null || fresh == null || fresh.wasRefunded ||
          !(fresh.gift instanceof TdApi.SentGiftUpgraded upgraded) || !sameListing(previous, upgraded.gift) ||
          (price != null && fresh.nextResaleDate > tdlib.currentTime(TimeUnit.SECONDS)) ||
          (price instanceof TdApi.GiftResalePriceStar stars && !validListingAmount(stars.starCount, minimum(false), maximum(false))) ||
          (price instanceof TdApi.GiftResalePriceGram ton && !validListingAmount(ton.gramCentCount, minimum(true), maximum(true)))) {
        afterMutation(error, false);
        return;
      }
      tdlib.send(new TdApi.SetGiftResalePrice(received.receivedGiftId, price), (ok, saveError) ->
        runOnUiThreadOptional(() -> afterMutation(saveError, saveError == null)));
    }));
  }

  static boolean sameListing (TdApi.UpgradedGift first, TdApi.UpgradedGift second) {
    if (first.id != second.id || !Objects.equals(first.name, second.name)) {
      return false;
    }
    TdApi.GiftResaleParameters a = first.resaleParameters, b = second.resaleParameters;
    return a == b || (a != null && b != null && a.starCount == b.starCount && a.gramCentCount == b.gramCentCount &&
      a.gramOnly == b.gramOnly);
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
