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
import org.thunderdog.challegram.charts.Chart;
import org.thunderdog.challegram.charts.data.ChartDataUtil;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

public final class StarsRevenueController extends RecyclerViewController<Void> implements View.OnClickListener {
  private final TdApi.MessageSender owner;
  private final boolean mayWithdraw;
  private SettingsAdapter adapter;
  private TdApi.StarRevenueStatus status;
  private Chart chart;
  private boolean loading, failed, withdrawing;
  private int generation;

  public StarsRevenueController (Context context, Tdlib tdlib, TdApi.MessageSender owner, boolean mayWithdraw) {
    super(context, tdlib);
    this.owner = owner;
    this.mayWithdraw = mayWithdraw;
  }

  @Override
  public int getId () { return R.id.controller_starsRevenue; }

  @Override
  public CharSequence getName () { return Lang.getString(R.string.TgxStarsRevenue); }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recycler) {
    adapter = new SettingsAdapter(this);
    recycler.setAdapter(adapter);
    refresh();
  }

  private void refresh () {
    if (loading || withdrawing) {
      return;
    }
    int request = ++generation;
    loading = true;
    failed = false;
    render();
    tdlib.send(new TdApi.GetStarRevenueStatistics(owner, Theme.isDark()), (stats, error) -> runOnUiThreadOptional(() -> {
      loading = false;
      failed = error != null;
      if (stats != null) {
        status = stats.status;
        chart = null;
        if (stats.revenueByDayGraph != null) {
          long chatId = owner instanceof TdApi.MessageSenderChat chat ? chat.chatId : 0;
          Chart candidate = new Chart(R.id.stats_starsRevenue, tdlib, chatId, R.string.TgxStarsRevenueByDay,
            ChartDataUtil.TYPE_LINEAR, stats.revenueByDayGraph, 0);
          if (candidate.isAsync() && chatId != 0) {
            candidate.load(hasData -> {
              if (hasData && !isDestroyed() && request == generation) {
                chart = candidate;
                render();
              }
            });
          } else if (!candidate.isAsync() && !candidate.isError() && candidate.getBaseData() != null) {
            chart = candidate;
          }
        }
      }
      render();
    }));
  }

  static long maximum (TdApi.StarRevenueStatus status, long max) {
    return status != null && status.availableAmount != null ? Math.min(max, status.availableAmount.starCount) : 0;
  }

  static boolean canWithdraw (TdApi.StarRevenueStatus status, long count, long min, long max) {
    return status != null && status.withdrawalEnabled && status.nextWithdrawalIn <= 0 && count > 0 &&
      count >= min && count <= maximum(status, max);
  }

  private void render () {
    ArrayList<ListItem> items = new ArrayList<>();
    if (status != null) {
      addAmount(items, R.string.TgxStarsRevenueTotal, status.totalAmount);
      addAmount(items, R.string.TgxStarsRevenueCurrent, status.currentAmount);
      addAmount(items, R.string.TgxStarsRevenueAvailable, status.availableAmount);
      if (hasWithdrawalAccess() && !failed && canWithdraw(status, tdlib.options().starWithdrawalCountMin,
          tdlib.options().starWithdrawalCountMin, tdlib.options().starWithdrawalCountMax)) {
        items.add(new ListItem(ListItem.TYPE_BUTTON, R.id.btn_starsWithdraw, 0, R.string.TgxStarsWithdraw));
      } else if (hasWithdrawalAccess() && status.nextWithdrawalIn > 0) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0,
          Lang.getString(R.string.TgxStarsWithdrawalDelay, Lang.getDuration(status.nextWithdrawalIn))));
      } else if (hasWithdrawalAccess()) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(R.string.TgxStarsWithdrawalUnavailable,
          Lang.formatNumber(tdlib.options().starWithdrawalCountMin))));
      }
    }
    if (chart != null) {
      items.add(new ListItem(ListItem.TYPE_CHART_HEADER).setData(chart));
      items.add(new ListItem(chart.getViewType(), chart.getId()).setData(chart));
    }
    items.add(new ListItem(loading || withdrawing ? ListItem.TYPE_DESCRIPTION : ListItem.TYPE_SETTING,
      R.id.btn_starsRevenueReload, 0, loading || withdrawing ? R.string.LoadingInformation :
        failed ? R.string.RetryChatList : R.string.TgxPremiumRefreshStars));
    if (failed) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TgxStarsRevenueError));
    }
    adapter.setItems(items, false);
  }

  private void addAmount (ArrayList<ListItem> items, int label, TdApi.StarAmount amount) {
    if (amount != null) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getString(label,
        StarsHistory.amount(amount, Lang.dateFormatLocale(), false))));
    }
  }

  @Override
  public void onClick (View view) {
    if (view.getId() == R.id.btn_starsWithdraw && hasWithdrawalAccess() && !loading && !withdrawing && !failed) {
      enterAmount();
    } else if (view.getId() == R.id.btn_starsRevenueReload) {
      refresh();
    }
  }

  private void enterAmount () {
    long low = tdlib.options().starWithdrawalCountMin, high = maximum(status, tdlib.options().starWithdrawalCountMax);
    CharSequence caption = Lang.getString(R.string.TgxStarsWithdrawalRange, Lang.formatNumber(low), Lang.formatNumber(high));
    var input = openInputAlert(Lang.getString(R.string.TgxStarsWithdraw), caption, R.string.Continue, R.string.Cancel, null,
      (edit, value) -> {
        long count;
        try {
          count = Long.parseLong(value.trim());
        } catch (NumberFormatException error) {
          return false;
        }
        if (!canWithdraw(status, count, low, high)) {
          return false;
        }
        tdlib.ui().post(() -> {
          if (isFocused()) {
            showConfirm(Lang.getString(R.string.TgxStarsWithdrawalConfirm, Lang.formatNumber(count)),
              Lang.getString(R.string.Continue), () -> recheckAndPrompt(count));
          }
        });
        return true;
      }, true);
    input.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
  }

  private void recheckAndPrompt (long count) {
    long userId = tdlib.myUserId();
    if (!hasWithdrawalAccess() || withdrawing || !isFocused() || userId == 0) {
      return;
    }
    withdrawing = true;
    render();
    tdlib.send(new TdApi.GetStarRevenueStatistics(owner, Theme.isDark()), (fresh, error) -> runOnUiThreadOptional(() -> {
      if (!isFocused() || userId != tdlib.myUserId() || error != null) {
        endRequest(error);
        return;
      }
      status = fresh.status;
      if (!canWithdraw(status, count, tdlib.options().starWithdrawalCountMin, tdlib.options().starWithdrawalCountMax)) {
        UI.showToast(R.string.TgxStarsWithdrawalChanged, Toast.LENGTH_LONG);
        endRequest(null);
        return;
      }
      tdlib.send(new TdApi.GetPasswordState(), (passwordState, passwordError) -> runOnUiThreadOptional(() -> {
        endRequest(passwordError);
        if (passwordError != null || !isFocused() || userId != tdlib.myUserId()) {
          return;
        }
        if (!passwordState.hasPassword) {
          UI.showToast(R.string.TgxStarsWithdrawalNeedsPassword, Toast.LENGTH_LONG);
          return;
        }
        PasswordController controller = new PasswordController(context(), tdlib);
        controller.setArguments(new PasswordController.Args(PasswordController.MODE_CUSTOM_CONFIRM, passwordState)
          .setConfirmDelegate(new PasswordController.CustomConfirmDelegate() {
            @Override public boolean needNext () { return true; }
            @Override public void onPasswordConfirmed (ViewController<?> origin, String password) {
              openWithdrawal(origin, userId, count, password);
            }
          }));
        navigateTo(controller);
      }));
    }));
  }

  private void openWithdrawal (ViewController<?> origin, long userId, long count, String password) {
    if (!hasWithdrawalAccess() || withdrawing || !origin.isFocused() || origin.tdlib() != tdlib || userId != tdlib.myUserId()) {
      return;
    }
    withdrawing = true;
    tdlib.send(new TdApi.GetStarWithdrawalUrl(owner, count, password), (result, error) -> tdlib.ui().post(() -> {
      withdrawing = false;
      if (isDestroyed() || !origin.isFocused() || userId != tdlib.myUserId()) {
        return;
      }
      if (error != null) {
        UI.showError(error);
      } else {
        Uri url = Uri.parse(result.url);
        if ("https".equals(url.getScheme()) && "fragment.com".equalsIgnoreCase(url.getHost()) &&
            url.getUserInfo() == null && url.getPort() == -1) {
          origin.navigateBack();
          Intents.openUriInBrowser(url);
        } else {
          UI.showToast(R.string.TgxStarsUnexpectedWithdrawalUrl, Toast.LENGTH_LONG);
        }
      }
    }));
  }

  private boolean hasWithdrawalAccess () {
    if (!mayWithdraw) {
      return false;
    }
    if (owner instanceof TdApi.MessageSenderChat target) {
      TdApi.ChatMemberStatus member = tdlib.chatStatus(target.chatId);
      return member != null && TD.isCreator(member);
    }
    TdApi.MessageSenderUser target = (TdApi.MessageSenderUser) owner;
    if (tdlib.isSelfUserId(target.userId)) {
      return true;
    }
    TdApi.User bot = tdlib.cache().user(target.userId);
    return bot != null && bot.type instanceof TdApi.UserTypeBot type && type.canBeEdited;
  }

  private void endRequest (TdApi.Error error) {
    withdrawing = false;
    if (error != null && isFocused()) {
      UI.showError(error);
    }
    render();
  }
}
