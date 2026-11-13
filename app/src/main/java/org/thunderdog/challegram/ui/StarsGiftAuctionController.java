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
import java.util.concurrent.TimeUnit;

import me.vkryl.core.lambda.RunnableData;
import tgx.td.Td;

public final class StarsGiftAuctionController extends RecyclerViewController<Void> implements View.OnClickListener {
  private final String auctionId;
  private final long recipientId, account;
  private SettingsAdapter adapter;
  private TdApi.GiftAuctionState state;
  private TdApi.GiftAuctionAcquiredGift[] acquired;
  private boolean loading, failed, bidding, checkingAcquired;
  private long openedGiftId;
  private int stateRevision;
  private final RunnableData<TdApi.GiftAuctionState> auctionListener = latest -> runOnUiThreadOptional(() -> {
    if (current() && latest != null && latest.gift != null && latest.gift.id == openedGiftId) {
      stateRevision++;
      updateState(latest);
      render();
    }
  });

  public StarsGiftAuctionController (Context context, Tdlib tdlib, String auctionId, long recipientId) {
    super(context, tdlib);
    this.auctionId = auctionId;
    this.account = tdlib.myUserId();
    this.recipientId = recipientId != 0 ? recipientId : account;
  }

  @Override public int getId () { return R.id.controller_giftAuction; }
  @Override public CharSequence getName () { return Lang.getString(R.string.TgxStarsAuction); }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recycler) {
    adapter = new SettingsAdapter(this);
    recycler.setAdapter(adapter);
    tdlib.addGiftAuctionListener(auctionListener);
    render();
  }

  @Override
  public void onFocus () {
    super.onFocus();
    refresh();
  }

  @Override
  public void onBlur () {
    closeAuction();
    super.onBlur();
  }

  @Override
  public void destroy () {
    tdlib.removeGiftAuctionListener(auctionListener);
    closeAuction();
    super.destroy();
  }

  private boolean current () {
    return isFocused() && account != 0 && account == tdlib.myUserId();
  }

  private void closeAuction () {
    if (openedGiftId != 0) {
      tdlib.send(new TdApi.CloseGiftAuction(openedGiftId), (ok, error) -> { });
      openedGiftId = 0;
    }
  }

  private void refresh () {
    if (loading || bidding || !current()) {
      return;
    }
    loading = true;
    failed = false;
    if (state == null) {
      render();
    }
    int requestedRevision = stateRevision;
    tdlib.send(new TdApi.GetGiftAuctionState(auctionId), (fresh, error) -> runOnUiThreadOptional(() -> {
      loading = false;
      if (!current()) {
        return;
      }
      failed = error != null;
      if (fresh != null && requestedRevision == stateRevision) {
        updateState(fresh);
      }
      render();
    }));
  }

  private void updateState (TdApi.GiftAuctionState fresh) {
    state = fresh;
    if (fresh.state instanceof TdApi.AuctionStateFinished) {
      closeAuction();
    } else if (openedGiftId == 0 && fresh.gift != null && fresh.state instanceof TdApi.AuctionStateActive) {
      long openedId = fresh.gift.id;
      openedGiftId = openedId;
      tdlib.send(new TdApi.OpenGiftAuction(openedId), (ok, error) -> runOnUiThreadOptional(() -> {
        if (error != null && openedGiftId == openedId) {
          openedGiftId = 0;
          failed = true;
          render();
        }
      }));
    }
  }

  static long currentBid (TdApi.AuctionStateActive active) {
    return active.userBid != null && !active.userBid.wasReturned ? active.userBid.starCount : 0;
  }

  static long minimum (TdApi.AuctionStateActive active) {
    long current = currentBid(active);
    long next = active.userBid != null ? active.userBid.nextBidStarCount : 0;
    return Math.max(active.minBid, Math.max(next, current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1));
  }

  static boolean canBid (TdApi.AuctionStateActive active, long now, long total) {
    return active.startDate <= now && active.endDate > now && active.currentRoundNumber > 0 &&
      active.currentRoundEndDate > now && (active.userBid == null || !active.userBid.wasReturned) &&
      total > currentBid(active) && total >= minimum(active);
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    if (state != null && state.gift != null) {
      items.add(new ListItem(ListItem.TYPE_HEADER_MULTILINE, 0, 0,
        Lang.getString(R.string.TgxStarsGiftNumber, Long.toString(state.gift.id))));
      if (state.state instanceof TdApi.AuctionStateActive active) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.TgxStarsAuctionRound,
          Lang.formatNumber(active.currentRoundNumber), Lang.formatNumber(active.totalRoundCount),
          Lang.dateYearShortTime(active.currentRoundEndDate, TimeUnit.SECONDS))));
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.TgxStarsAuctionMinimum,
          Lang.formatNumber(minimum(active)))));
        if (active.userBid != null) {
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(
            active.userBid.wasReturned ? R.string.TgxStarsAuctionReturned : R.string.TgxStarsAuctionYourBid,
            Lang.formatNumber(active.userBid.starCount))));
        }
        if (!bidding && !failed && (!state.gift.isPremium || tdlib.hasPremium()) &&
            canBid(active, tdlib.currentTime(TimeUnit.SECONDS), minimum(active))) {
          items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_giftAuctionBid, 0,
            currentBid(active) > 0 ? R.string.TgxStarsAuctionRaise : R.string.TgxStarsAuctionPlace));
        }
      } else if (state.state instanceof TdApi.AuctionStateFinished finished) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.TgxStarsAuctionFinished,
          Lang.formatNumber(finished.averagePrice))));
        if (StarsCollectibleController.fragmentUri(finished.fragmentUrl) != null && finished.fragmentListedItemCount > 0) {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_tgxGiftFragment, 0, R.string.TgxStarsViewFragment));
        }
        if (finished.telegramListedItemCount > 0) {
          items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftAuctionResale, 0, R.string.TgxStarsResale));
        }
      }
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_giftAuctionAcquired, 0, R.string.TgxStarsAuctionWon));
    }
    if (acquired != null) {
      if (acquired.length == 0) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxStarsAuctionNoWins));
      }
      for (TdApi.GiftAuctionAcquiredGift gift : acquired) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.TgxStarsAuctionWin,
          Lang.formatNumber(gift.uniqueGiftNumber), Lang.formatNumber(gift.starCount), Lang.formatNumber(gift.auctionRoundNumber))));
      }
    }
    items.add(new ListItem(bidding || loading ? ListItem.TYPE_DESCRIPTION : ListItem.TYPE_SETTING,
      R.id.btn_giftAuctionRefresh, 0, bidding || loading ? R.string.LoadingInformation :
        failed ? R.string.RetryChatList : R.string.TgxPremiumRefreshStars));
    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View view) {
    if (view.getId() == R.id.btn_giftAuctionRefresh) {
      refresh();
    } else if (view.getId() == R.id.btn_giftAuctionBid && state != null &&
        state.state instanceof TdApi.AuctionStateActive active && !bidding) {
      enterBid(state.gift.id, active);
    } else if (view.getId() == R.id.btn_tgxGiftFragment && state != null &&
        state.state instanceof TdApi.AuctionStateFinished finished) {
      StarsCollectibleController.openFragment(this, finished.fragmentUrl);
    } else if (view.getId() == R.id.btn_giftAuctionResale && state != null) {
      navigateTo(new StarsGiftMarketController(context(), tdlib, state.gift.id, recipientId));
    } else if (view.getId() == R.id.btn_giftAuctionAcquired && state != null && !checkingAcquired) {
      checkingAcquired = true;
      tdlib.send(new TdApi.GetGiftAuctionAcquiredGifts(state.gift.id), (result, error) -> runOnUiThreadOptional(() -> {
        checkingAcquired = false;
        if (!current()) {
          return;
        }
        if (error != null) {
          UI.showError(error);
        } else {
          acquired = result.gifts;
          render();
        }
      }));
    }
  }

  private void enterBid (long giftId, TdApi.AuctionStateActive expected) {
    long prior = currentBid(expected);
    long min = minimum(expected);
    var field = openInputAlert(Lang.getString(R.string.TgxStarsAuction), Lang.getString(R.string.TgxStarsAuctionMinimum,
      Lang.formatNumber(min)), R.string.Continue, R.string.Cancel, Long.toString(min), (edit, text) -> {
        long total;
        try {
          total = Long.parseLong(text.trim());
        } catch (NumberFormatException error) {
          return false;
        }
        if (!canBid(expected, tdlib.currentTime(TimeUnit.SECONDS), total)) {
          return false;
        }
        tdlib.ui().post(() -> {
          if (current()) {
            if (prior == 0) {
              chooseBidDetails(giftId, expected, total, "", false);
            } else {
              confirmBid(giftId, expected, total, "", false);
            }
          }
        });
        return true;
      }, true);
    field.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
  }

  private void chooseBidDetails (long giftId, TdApi.AuctionStateActive expected, long total, String note, boolean privateBid) {
    Options.Builder options = new Options.Builder().info(Lang.getString(R.string.TgxStarsAuctionBidDetails));
    options.item(new OptionItem(1, Lang.getString(privateBid ? R.string.TgxStarsAuctionBidPublic :
      R.string.TgxStarsAuctionBidPrivate), OptionColor.NORMAL, 0));
    if (tdlib.options().giftTextLengthMax > 0) {
      options.item(new OptionItem(2, Lang.getString(R.string.TgxStarsAuctionBidNote), OptionColor.NORMAL, 0));
    }
    options.item(new OptionItem(3, Lang.getString(R.string.Continue), OptionColor.NORMAL, 0));
    options.cancelItem();
    showOptions(options.build(), (view, id) -> {
      if (id == 1) {
        tdlib.ui().post(() -> {
          if (current()) {
            chooseBidDetails(giftId, expected, total, note, !privateBid);
          }
        });
      } else if (id == 2) {
        openInputAlert(Lang.getString(R.string.TgxStarsAuctionBidNote), null, R.string.Done, R.string.Cancel,
          note, (edit, value) -> {
            if (value.codePointCount(0, value.length()) > tdlib.options().giftTextLengthMax) {
              return false;
            }
            tdlib.ui().post(() -> {
              if (current()) {
                chooseBidDetails(giftId, expected, total, value.trim(), privateBid);
              }
            });
            return true;
          }, true);
      } else if (id == 3) {
        confirmBid(giftId, expected, total, note, privateBid);
      }
      return true;
    });
  }

  private void confirmBid (long giftId, TdApi.AuctionStateActive expected, long total, String note, boolean privateBid) {
    long prior = currentBid(expected);
    String receiver = prior > 0 && expected.userBid.ownerId != null ? tdlib.senderName(expected.userBid.ownerId) :
      tdlib.cache().userName(recipientId);
    String message = Lang.getString(R.string.TgxStarsAuctionBidConfirm, Lang.formatNumber(total),
      Lang.formatNumber(total - prior), receiver);
    if (prior == 0) {
      message += "\n\n" + Lang.getString(R.string.TgxStarsAuctionBidPrivacyConfirm,
        Lang.getString(privateBid ? R.string.TgxStarsAuctionBidPrivate : R.string.TgxStarsAuctionBidPublic));
      if (!note.isEmpty()) {
        message += "\n" + Lang.getString(R.string.TgxStarsAuctionBidNoteConfirm, note);
      }
    }
    showConfirm(message, Lang.getString(R.string.Confirm), () -> sendBid(giftId, expected, total, note, privateBid));
  }

  private void sendBid (long giftId, TdApi.AuctionStateActive expected, long total, String note, boolean privateBid) {
    if (!current() || bidding) {
      return;
    }
    bidding = true;
    render();
    tdlib.send(new TdApi.GetGiftAuctionState(auctionId), (fresh, error) -> runOnUiThreadOptional(() -> {
      if (!current() || error != null || fresh.gift.id != giftId ||
          (fresh.gift.isPremium && !tdlib.hasPremium()) || !(fresh.state instanceof TdApi.AuctionStateActive active) ||
          active.currentRoundNumber != expected.currentRoundNumber || currentBid(expected) != currentBid(active) ||
          (currentBid(active) > 0 && (active.userBid.bidDate != expected.userBid.bidDate ||
            !Td.equalsTo(active.userBid.ownerId, expected.userBid.ownerId))) ||
          !canBid(active, tdlib.currentTime(TimeUnit.SECONDS), total) ||
          (!note.isEmpty() && note.codePointCount(0, note.length()) > tdlib.options().giftTextLengthMax)) {
        finish(error, false);
        return;
      }
      TdApi.Function<TdApi.Ok> request = currentBid(active) > 0 ? new TdApi.IncreaseGiftAuctionBid(giftId, total) :
        new TdApi.PlaceGiftAuctionBid(giftId, total, recipientId, note.isEmpty() ? null :
          new TdApi.FormattedText(note, new TdApi.TextEntity[0]), privateBid);
      tdlib.send(request, (ok, bidError) -> runOnUiThreadOptional(() -> finish(bidError, bidError == null)));
    }));
  }

  private void finish (TdApi.Error error, boolean success) {
    bidding = false;
    if (current()) {
      if (error != null) {
        UI.showError(error);
      } else {
        UI.showToast(success ? R.string.TgxStarsAuctionBidSaved : R.string.TgxStarsAuctionChanged, Toast.LENGTH_SHORT);
      }
      refresh();
    } else {
      render();
    }
  }
}
