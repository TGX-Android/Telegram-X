/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 26/02/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Bundle;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGNetworkStats;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;

public class SettingsNetworkStatsController extends RecyclerViewController<SettingsNetworkStatsController.Args> {
  public static class Args {
    private final int type;
    private final TGNetworkStats stats;

    public Args (int type, TGNetworkStats stats) {
      this.type = type;
      this.stats = stats;
    }
  }

  public SettingsNetworkStatsController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return getArgumentsStrict().stats == null;
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putInt(keyPrefix + "type", getArgumentsStrict().type);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    setArguments(new Args(in.getInt(keyPrefix + "type", TGNetworkStats.TYPE_MOBILE), null));
    return true;
  }

  @Override
  public CharSequence getName () {
    switch (getArgumentsStrict().type) {
      case TGNetworkStats.TYPE_ROAMING: {
        return Lang.getString(R.string.RoamingUsage);
      }
      case TGNetworkStats.TYPE_WIFI: {
        return Lang.getString(R.string.WiFiUsage);
      }
      case TGNetworkStats.TYPE_MOBILE:
      default: {
        return Lang.getString(R.string.MobileUsage);
      }
    }
  }

  @Override
  public int getId () {
    return R.id.controller_networkStats;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    SettingsAdapter adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setData(item.getStringValue());
        view.setIgnoreEnabled(true);
        view.setEnabled(false);
      }
    };
    ArrayList<ListItem> items = new ArrayList<>();
    Args args = getArgumentsStrict();
    if (args.stats != null) {
      args.stats.buildEntries(items, args.type);
    } else {
      tdlib.client().send(new TdApi.GetNetworkStatistics(), result -> runOnUiThreadOptional(() -> {
        switch (result.getConstructor()) {
          case TdApi.NetworkStatistics.CONSTRUCTOR: {
            TGNetworkStats stats = new TGNetworkStats((TdApi.NetworkStatistics) result);
            setArguments(new Args(args.type, stats));
            stats.buildEntries(items, args.type);
            adapter.setItems(items, false);
            restorePersistentScrollPosition();
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(result);
            break;
          }
        }
        executeScheduledAnimation();
      }));
    }
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
  }
}
