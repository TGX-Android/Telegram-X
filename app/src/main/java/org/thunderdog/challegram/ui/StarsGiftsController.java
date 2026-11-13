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
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public final class StarsGiftsController extends RecyclerViewController<Void> implements View.OnClickListener {
  private final long recipientId;
  private SettingsAdapter adapter;
  private TdApi.AvailableGift[] gifts;
  private boolean loading, failed, sending;

  public StarsGiftsController (Context context, Tdlib tdlib, long recipientId) {
    super(context, tdlib);
    this.recipientId = recipientId;
  }

  @Override
  public int getId () { return R.id.controller_starsGifts; }

  @Override
  public CharSequence getName () { return Lang.getString(R.string.TgxStarsGiftCatalog); }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recycler) {
    adapter = new SettingsAdapter(this);
    recycler.setAdapter(adapter);
    load();
  }

  static boolean canOffer (TdApi.Gift gift, boolean premium, long now) {
    return gift != null && gift.id != 0 && gift.starCount > 0 && gift.auctionInfo == null &&
      (!gift.isPremium || premium) && gift.nextSendDate <= now &&
      (gift.userLimits == null || gift.userLimits.remainingCount > 0) &&
      (gift.overallLimits == null || gift.overallLimits.remainingCount > 0);
  }

  private void load () {
    if (loading || sending) {
      return;
    }
    loading = true;
    failed = false;
    render();
    tdlib.send(new TdApi.GetAvailableGifts(), (result, error) -> runOnUiThreadOptional(() -> {
      loading = false;
      failed = error != null;
      if (result != null) {
        gifts = result.gifts;
      }
      render();
    }));
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_HEADER_MULTILINE, 0, 0,
      Lang.getString(R.string.TgxStarsGiftFor, tdlib.cache().userName(recipientId))));
    if (gifts != null) {
      int count = 0;
      for (TdApi.AvailableGift entry : gifts) {
        if (canOffer(entry.gift, tdlib.hasPremium(), tdlib.currentTime(TimeUnit.SECONDS))) {
          String emoji = entry.gift.sticker != null ? entry.gift.sticker.emoji : "";
          String title = entry.title != null && !entry.title.isEmpty() ? entry.title :
            Lang.getString(R.string.TgxStarsGiftNumber, Long.toString(entry.gift.id));
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_starsGiftItem, R.drawable.baseline_stars_24,
            Lang.getString(R.string.TgxStarsGiftItem, (emoji + " " + title).trim(), Lang.formatNumber(entry.gift.starCount)))
            .setData(entry.gift));
          count++;
        }
      }
      if (count == 0 && !loading && !failed) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxStarsGiftEmpty));
      }
    }
    items.add(new ListItem(loading || sending ? ListItem.TYPE_DESCRIPTION : ListItem.TYPE_SETTING,
      R.id.btn_starsGiftReload, 0, loading || sending ? R.string.LoadingInformation :
        failed ? R.string.RetryChatList : R.string.TgxPremiumRefreshStars));
    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View view) {
    if (sending || loading) {
      return;
    }
    if (view.getId() == R.id.btn_starsGiftReload) {
      load();
    } else if (view.getTag() instanceof ListItem item && item.getData() instanceof TdApi.Gift gift) {
      compose(gift);
    }
  }

  private void compose (TdApi.Gift gift) {
    openInputAlert(Lang.getString(R.string.TgxStarsGiftMessage), Lang.getString(R.string.TgxStarsGiftMessage),
      R.string.Continue, R.string.Cancel, null, (input, value) -> {
        if (value.codePointCount(0, value.length()) > tdlib.options().giftTextLengthMax) {
          UI.showToast(R.string.TgxStarsGiftMessageLimit, Toast.LENGTH_SHORT);
          return false;
        }
        tdlib.ui().post(() -> {
          if (isFocused()) {
            choosePrivacy(gift, value);
          }
        });
        return true;
      }, true);
  }

  private void choosePrivacy (TdApi.Gift gift, String message) {
    showOptions(new int[] {R.id.btn_starsGiftPublic, R.id.btn_starsGiftPrivate, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.TgxStarsGiftPublic), Lang.getString(R.string.TgxStarsGiftPrivate),
        Lang.getString(R.string.Cancel)}, (view, id) -> {
          if (id == R.id.btn_starsGiftPublic || id == R.id.btn_starsGiftPrivate) {
            boolean privateGift = id == R.id.btn_starsGiftPrivate;
            CharSequence confirmation = Lang.getString(R.string.TgxStarsGiftConfirm, Lang.formatNumber(gift.starCount),
              tdlib.cache().userName(recipientId), Lang.getString(privateGift ? R.string.TgxStarsGiftPrivate : R.string.TgxStarsGiftPublic));
            showConfirm(confirmation, Lang.getString(R.string.TgxStarsSendGift), () -> send(gift, message, privateGift));
          }
          return true;
        });
  }

  private boolean isCurrentAccount (long userId) {
    return isFocused() && userId != 0 && userId == tdlib.myUserId() && recipientId != userId;
  }

  private void send (TdApi.Gift expected, String message, boolean privateGift) {
    final long userId = tdlib.myUserId();
    if (sending || loading || !isCurrentAccount(userId)) {
      return;
    }
    sending = true;
    render();
    tdlib.send(new TdApi.GetAvailableGifts(), (catalog, error) -> runOnUiThreadOptional(() -> {
      if (!isCurrentAccount(userId) || error != null) {
        finish(error, false);
        return;
      }
      TdApi.Gift selected = null;
      for (TdApi.AvailableGift item : catalog.gifts) {
        if (item.gift.id == expected.id) {
          selected = item.gift;
          break;
        }
      }
      if (!canOffer(selected, tdlib.hasPremium(), tdlib.currentTime(TimeUnit.SECONDS)) ||
          selected.starCount != expected.starCount) {
        finish(null, false);
        return;
      }
      tdlib.send(new TdApi.GetUserFullInfo(recipientId), (recipient, recipientError) -> runOnUiThreadOptional(() -> {
        if (!isCurrentAccount(userId) || recipientError != null) {
          finish(recipientError, false);
        } else if (recipient.outgoingPaidMessageStarCount > 0 && !message.isEmpty()) {
          sending = false;
          UI.showToast(R.string.TgxStarsGiftPaidRecipient, Toast.LENGTH_LONG);
          render();
        } else if (message.codePointCount(0, message.length()) > tdlib.options().giftTextLengthMax) {
          sending = false;
          UI.showToast(R.string.TgxStarsGiftMessageLimit, Toast.LENGTH_SHORT);
          render();
        } else {
          TdApi.FormattedText text = new TdApi.FormattedText(message, new TdApi.TextEntity[0]);
          tdlib.send(new TdApi.SendGift(expected.id, new TdApi.MessageSenderUser(recipientId), text, privateGift, false),
            (ok, sendError) -> runOnUiThreadOptional(() -> finish(sendError, sendError == null)));
        }
      }));
    }));
  }

  private void finish (TdApi.Error error, boolean sent) {
    sending = false;
    if (isFocused()) {
      if (error != null) {
        UI.showError(error);
      } else {
        UI.showToast(sent ? R.string.TgxStarsGiftSent : R.string.TgxStarsGiftChanged, Toast.LENGTH_SHORT);
      }
    }
    load();
  }
}
