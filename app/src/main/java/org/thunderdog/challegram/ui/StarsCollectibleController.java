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
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;

public final class StarsCollectibleController extends RecyclerViewController<Void> implements View.OnClickListener {
  private static final int[] OFFER_HOURS = {6, 12, 24, 36, 48, 72};
  private final String name;
  private final long account, recipient;
  private SettingsAdapter adapter;
  private TdApi.UpgradedGift gift;
  private Uri fragment;
  private boolean loading, busy, failed;

  public StarsCollectibleController (Context context, Tdlib tdlib, String name, long recipient) {
    super(context, tdlib);
    this.name = name;
    this.recipient = recipient;
    this.account = tdlib.myUserId();
  }

  @Override public int getId () { return R.id.controller_tgxCollectible; }
  @Override public CharSequence getName () { return Lang.getString(R.string.TgxStarsCollectibleDetails); }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recycler) {
    adapter = new SettingsAdapter(this);
    recycler.setAdapter(adapter);
    load();
  }

  private void load () {
    if (account == 0 || account != tdlib.myUserId() || busy || loading) {
      return;
    }
    loading = true;
    failed = false;
    fragment = null;
    render();
    tdlib.send(new TdApi.GetUpgradedGift(name), (fresh, error) -> runOnUiThreadOptional(() -> {
      if (account != tdlib.myUserId()) {
        return;
      }
      loading = false;
      gift = fresh;
      failed = error != null;
      render();
    }));
    tdlib.send(new TdApi.GetUpgradedGiftValueInfo(name), (value, error) -> runOnUiThreadOptional(() -> {
      if (account == tdlib.myUserId() && value != null && value.fragmentListedGiftCount > 0) {
        fragment = fragmentUri(value.fragmentUrl);
        render();
      }
    }));
  }

  static Uri fragmentUri (String url) {
    if (StringUtils.isEmpty(url)) {
      return null;
    }
    Uri uri = Uri.parse(url);
    String authority = uri.getEncodedAuthority();
    return "https".equals(uri.getScheme()) && "fragment.com".equalsIgnoreCase(authority) ? uri : null;
  }

  static long tonPrice (TdApi.UpgradedGift gift) {
    return gift != null && gift.resaleParameters != null ? gift.resaleParameters.gramCentCount : 0;
  }

  private static long userOwner (TdApi.UpgradedGift gift) {
    return gift != null && gift.ownerId instanceof TdApi.MessageSenderUser user ? user.userId : 0;
  }

  static boolean validOfferOwner (TdApi.UpgradedGift expected, TdApi.UpgradedGift fresh, long account) {
    if (expected == null || fresh == null || !fresh.canSendPurchaseOffer ||
        expected.id != fresh.id || expected.name == null || !expected.name.equals(fresh.name)) {
      return false;
    }
    if (expected.ownerId instanceof TdApi.MessageSenderUser a && fresh.ownerId instanceof TdApi.MessageSenderUser b) {
      return a.userId != 0 && a.userId != account && a.userId == b.userId;
    }
    return expected.ownerId instanceof TdApi.MessageSenderChat a && fresh.ownerId instanceof TdApi.MessageSenderChat b &&
      a.chatId != 0 && a.chatId == b.chatId;
  }

  static boolean sameTonListing (TdApi.UpgradedGift expected, TdApi.UpgradedGift fresh) {
    return fresh != null && expected.id == fresh.id && expected.name != null && expected.name.equals(fresh.name) &&
      tonPrice(expected) > 0 && tonPrice(expected) == tonPrice(fresh);
  }

  static long parseAmount (String input, boolean ton) {
    String value = input.trim();
    if (!value.matches(ton ? "[0-9]+([.,][0-9]{1,2})?" : "[0-9]+")) {
      return 0;
    }
    try {
      return ton ? new BigDecimal(value.replace(',', '.')).movePointRight(2).longValueExact() : Long.parseLong(value);
    } catch (ArithmeticException | NumberFormatException ignored) {
      return 0;
    }
  }

  public static String formatPrice (TdApi.GiftResalePrice price) {
    if (price instanceof TdApi.GiftResalePriceGram tons) {
      return Lang.getString(R.string.TgxStarsTonAmount, BigDecimal.valueOf(tons.gramCentCount, 2).stripTrailingZeros().toPlainString());
    }
    return price instanceof TdApi.GiftResalePriceStar stars ?
      Lang.getString(R.string.TgxStarsStarAmount, Lang.formatNumber(stars.starCount)) : "";
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    if (loading || busy) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.LoadingInformation));
    } else if (gift != null) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, title(gift)));
      long owner = userOwner(gift);
      if (gift.ownerId != null) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.TgxStarsGiftOwner,
          tdlib.senderName(gift.ownerId))));
      }
      if (owner != account) {
        addPurchase(items, false, StarsGiftMarketController.starPrice(gift));
        addPurchase(items, true, tonPrice(gift));
      }
      if (validOfferOwner(gift, gift, account)) {
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxGiftMakeOffer, 0, R.string.TgxStarsMakeOffer));
      }
      if (fragment != null) {
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxGiftFragment, 0, R.string.TgxStarsViewFragment));
      }
    }
    if (!busy && !loading) {
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftMarketRefresh, 0,
        failed ? R.string.RetryChatList : R.string.TgxPremiumRefreshStars));
    }
    adapter.setItems(items, false);
  }

  private void addPurchase (ArrayList<ListItem> items, boolean ton, long amount) {
    if (amount > 0) {
      items.add(new ListItem(ListItem.TYPE_SETTING, ton ? R.id.btn_tgxGiftBuyTon : R.id.btn_tgxGiftBuyStars, 0,
        Lang.getString(R.string.TgxStarsBuyForAmount, formatPrice(price(ton, amount)))));
    }
  }

  private static String title (TdApi.UpgradedGift gift) {
    return Lang.getString(R.string.TgxStarsCollectibleNumber, gift.title, Lang.formatNumber(gift.number));
  }

  static TdApi.GiftResalePrice price (boolean ton, long amount) {
    return ton ? new TdApi.GiftResalePriceGram(amount) : new TdApi.GiftResalePriceStar(amount);
  }

  @Override
  public void onClick (View view) {
    if (busy || loading || !current()) {
      return;
    }
    int id = view.getId();
    if (id == R.id.btn_giftMarketRefresh) {
      load();
    } else if (gift != null && (id == R.id.btn_tgxGiftBuyStars || id == R.id.btn_tgxGiftBuyTon)) {
      confirmPurchase(gift, id == R.id.btn_tgxGiftBuyTon);
    } else if (gift != null && id == R.id.btn_tgxGiftMakeOffer) {
      chooseCurrency(gift);
    } else if (fragment != null && id == R.id.btn_tgxGiftFragment) {
      openFragment(this, fragment.toString());
    }
  }

  static void openFragment (ViewController<?> controller, String url) {
    Uri uri = fragmentUri(url);
    if (uri != null) {
      controller.showConfirm(Lang.getString(R.string.TgxStarsFragmentConfirm), Lang.getString(R.string.Open),
        () -> Intents.openUriInBrowser(uri));
    }
  }

  private void confirmPurchase (TdApi.UpgradedGift expected, boolean ton) {
    long amount = ton ? tonPrice(expected) : StarsGiftMarketController.starPrice(expected);
    if (amount <= 0 || recipient <= 0) {
      return;
    }
    TdApi.GiftResalePrice price = price(ton, amount);
    showConfirm(Lang.getString(R.string.TgxStarsBuyCollectibleConfirm, title(expected), formatPrice(price),
      tdlib.cache().userName(recipient)), Lang.getString(R.string.TgxStarsBuyResale), () -> {
      if (!begin()) {
        return;
      }
      tdlib.send(new TdApi.GetUpgradedGift(name), (fresh, error) -> runOnUiThreadOptional(() -> {
        boolean matches = ton ? sameTonListing(expected, fresh) : StarsGiftMarketController.sameOffer(expected, fresh);
        if (!current() || error != null || !matches || userOwner(fresh) == account) {
          finish(error, false);
          return;
        }
        tdlib.send(new TdApi.SendResoldGift(name, new TdApi.MessageSenderUser(recipient), price, null, false),
          (result, purchaseError) -> runOnUiThreadOptional(() -> finish(purchaseError, result instanceof TdApi.GiftResaleResultOk)));
      }));
    });
  }

  private void chooseCurrency (TdApi.UpgradedGift expected) {
    showOptions(Lang.getString(R.string.TgxStarsMakeOffer), new int[] {1, 2, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.TgxStarsOfferStars), "TON", Lang.getString(R.string.Cancel)}, (view, id) -> {
        if (id == 1 || id == 2) {
          enterOffer(expected, id == 2);
        }
        return true;
      });
  }

  private long offerMaximum (boolean ton) {
    return ton ? tdlib.options().giftResaleGramCentCountMax : tdlib.options().giftResaleStarCountMax;
  }

  private void enterOffer (TdApi.UpgradedGift expected, boolean ton) {
    var input = openInputAlert(Lang.getString(R.string.TgxStarsMakeOffer), Lang.getString(ton ?
      R.string.TgxStarsOfferEnterTon : R.string.TgxStarsOfferEnterStars), R.string.Continue, R.string.Cancel, null, (edit, value) -> {
      long amount = parseAmount(value, ton);
      if (amount <= 0 || amount > offerMaximum(ton)) {
        return false;
      }
      tdlib.ui().post(() -> {
        if (current()) {
          chooseDuration(expected, ton, amount);
        }
      });
      return true;
    }, true);
    input.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | (ton ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
  }

  private void chooseDuration (TdApi.UpgradedGift expected, boolean ton, long amount) {
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxStarsOfferDuration));
    for (int hours : OFFER_HOURS) {
      options.item(new OptionItem(hours, Lang.getString(R.string.TgxStarsOfferHours, Lang.formatNumber(hours)), OptionColor.NORMAL, 0));
    }
    options.cancelItem();
    showOptions(options.build(), (view, hours) -> {
      for (int duration : OFFER_HOURS) {
        if (hours == duration) {
          previewOffer(expected, ton, amount, duration);
          break;
        }
      }
      return true;
    });
  }

  private void previewOffer (TdApi.UpgradedGift expected, boolean ton, long amount, int hours) {
    if (busy || !current() || !validOfferOwner(expected, expected, account)) {
      return;
    }
    getOfferFee(expected.ownerId, fee -> {
      if (busy || !current()) {
        return;
      }
      TdApi.GiftResalePrice price = price(ton, amount);
      String confirmation = Lang.getString(R.string.TgxStarsOfferConfirm, title(expected), formatPrice(price),
        tdlib.senderName(expected.ownerId), Lang.formatNumber(hours));
      if (fee > 0) {
        confirmation += "\n\n" + Lang.getString(R.string.TgxStarsOfferMessageFee, Lang.formatNumber(fee));
      }
      showConfirm(confirmation, Lang.getString(R.string.TgxStarsMakeOffer),
        () -> sendOffer(expected, ton, amount, hours, fee));
    }, error -> {
      if (current()) {
        UI.showError(error);
      }
    });
  }

  private void getOfferFee (TdApi.MessageSender owner, LongConsumer result,
                            RunnableData<TdApi.Error> failure) {
    if (owner instanceof TdApi.MessageSenderUser user) {
      tdlib.send(new TdApi.GetUserFullInfo(user.userId), (full, error) -> runOnUiThreadOptional(() -> {
        if (full != null) {
          result.accept(full.outgoingPaidMessageStarCount);
        } else {
          failure.runWithData(error);
        }
      }));
    } else if (owner instanceof TdApi.MessageSenderChat channel) {
      tdlib.send(new TdApi.GetChat(channel.chatId), (chat, error) -> runOnUiThreadOptional(() -> {
        if (chat != null && chat.type instanceof TdApi.ChatTypeSupergroup group && group.isChannel) {
          result.accept(0);
        } else {
          failure.runWithData(error != null ? error : new TdApi.Error(400, Lang.getString(R.string.TgxStarsGiftChanged)));
        }
      }));
    }
  }

  private void sendOffer (TdApi.UpgradedGift expected, boolean ton, long amount, int hours, long fee) {
    if (!begin()) {
      return;
    }
    tdlib.send(new TdApi.GetUpgradedGift(name), (fresh, error) -> runOnUiThreadOptional(() -> {
      if (!current() || error != null || !validOfferOwner(expected, fresh, account)) {
        finish(error, false);
        return;
      }
      getOfferFee(fresh.ownerId, currentFee -> {
        if (!current() || currentFee != fee || amount > offerMaximum(ton)) {
          finish(null, false);
          return;
        }
        tdlib.send(new TdApi.SendGiftPurchaseOffer(fresh.ownerId, name, price(ton, amount), hours * 3600, fee),
          (ok, sendError) -> runOnUiThreadOptional(() -> {
            if (ok != null && current()) {
              UI.showToast(R.string.TgxStarsOfferSent, Toast.LENGTH_SHORT);
              busy = false;
              load();
            } else {
              finish(sendError, false);
            }
          }));
      }, offerError -> finish(offerError, false));
    }));
  }

  public static boolean pendingOffer (TdApi.Message message, long now) {
    return message != null && !message.isOutgoing && message.forwardInfo == null && message.sendingState == null &&
      message.content instanceof TdApi.MessageUpgradedGiftPurchaseOffer offer &&
      offer.state instanceof TdApi.GiftPurchaseOfferStatePending && offer.expirationDate > now;
  }

  static boolean sameOfferPrice (TdApi.GiftResalePrice expected, TdApi.GiftResalePrice fresh) {
    if (expected instanceof TdApi.GiftResalePriceStar a && fresh instanceof TdApi.GiftResalePriceStar b) {
      return a.starCount > 0 && a.starCount == b.starCount;
    }
    return expected instanceof TdApi.GiftResalePriceGram a && fresh instanceof TdApi.GiftResalePriceGram b &&
      a.gramCentCount > 0 && a.gramCentCount == b.gramCentCount;
  }

  public static void openChatOffer (ViewController<?> screen, TdApi.Message message,
                                    TdApi.MessageUpgradedGiftPurchaseOffer offer) {
    if (screen == null || !screen.isFocused() || offer.gift == null) {
      return;
    }
    Tdlib tdlib = screen.tdlib();
    long account = tdlib.myUserId();
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxStarsOfferReview, title(offer.gift),
      formatPrice(offer.price), Lang.dateYearShortTime(offer.expirationDate, TimeUnit.SECONDS)));
    if (pendingOffer(message, tdlib.currentTime(TimeUnit.SECONDS)) && sameOfferPrice(offer.price, offer.price)) {
      options.item(new OptionItem(1, Lang.getString(R.string.TgxStarsOfferAccept), OptionColor.NORMAL, 0));
      options.item(new OptionItem(2, Lang.getString(R.string.TgxStarsOfferDecline), OptionColor.RED, 0));
    }
    options.item(new OptionItem(3, Lang.getString(R.string.TgxStarsViewCollectible), OptionColor.NORMAL, 0));
    options.cancelItem();
    screen.showOptions(options.build(), (view, id) -> {
      if (id == 3) {
        screen.context().navigation().navigateTo(new StarsCollectibleController(screen.context(), tdlib, offer.gift.name, account));
      } else if (id == 1 || id == 2) {
        boolean accept = id == 1;
        int action = accept ? R.string.TgxStarsOfferAccept : R.string.TgxStarsOfferDecline;
        screen.showConfirm(Lang.getString(accept ? R.string.TgxStarsOfferAcceptConfirm : R.string.TgxStarsOfferRejectConfirm,
          title(offer.gift), formatPrice(offer.price)), Lang.getString(action), () -> {
          if (!screen.isFocused() || account == 0 || account != tdlib.myUserId()) {
            return;
          }
          tdlib.send(new TdApi.GetMessage(message.chatId, message.id), (fresh, error) -> tdlib.ui().post(() -> {
            if (!screen.isFocused() || account != tdlib.myUserId()) {
              return;
            }
            if (error != null) {
              UI.showError(error);
            } else if (!pendingOffer(fresh, tdlib.currentTime(TimeUnit.SECONDS)) ||
                !(fresh.content instanceof TdApi.MessageUpgradedGiftPurchaseOffer current) ||
                current.gift == null || current.gift.id != offer.gift.id || current.expirationDate != offer.expirationDate ||
                !sameOfferPrice(offer.price, current.price)) {
              UI.showToast(R.string.TgxStarsOfferUnavailable, Toast.LENGTH_SHORT);
            } else {
              tdlib.send(new TdApi.ProcessGiftPurchaseOffer(fresh.id, accept), (ok, processError) -> tdlib.ui().post(() -> {
                if (screen.isFocused() && account == tdlib.myUserId()) {
                  if (processError != null) {
                    UI.showError(processError);
                  } else {
                    UI.showToast(accept ? R.string.TgxStarsOfferAcceptDone : R.string.TgxStarsOfferRejectDone, Toast.LENGTH_SHORT);
                  }
                }
              }));
            }
          }));
        });
      }
      return true;
    });
  }

  private boolean current () {
    return isFocused() && !isDestroyed() && account != 0 && account == tdlib.myUserId();
  }

  private boolean begin () {
    if (!current() || busy || loading) {
      return false;
    }
    busy = true;
    render();
    return true;
  }

  private void finish (TdApi.Error error, boolean success) {
    busy = false;
    if (current()) {
      if (error != null) {
        UI.showError(error);
      } else {
        UI.showToast(success ? R.string.TgxStarsMarketSaved : R.string.TgxStarsGiftChanged, Toast.LENGTH_SHORT);
      }
      load();
    } else if (!isDestroyed()) {
      render();
    }
  }
}
