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
 * File created on 20/10/2019
 */
package org.thunderdog.challegram.ui;

import android.content.Context;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.List;

import me.vkryl.core.lambda.RunnableData;

public class SettingsCloudIconController extends SettingsCloudController<Settings.IconPack> {
  public SettingsCloudIconController (Context context, Tdlib tdlib) {
    super(context, tdlib, 0, 0, R.string.IconsCurrent, R.string.IconsBuiltIn, R.string.IconsLoaded, R.string.IconsUpdate, R.string.IconsInstalling);
  }

  @Override
  public int getId () {
    return R.id.controller_iconSets;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Icons);
  }

  @Override
  protected Settings.IconPack getCurrentSetting () {
    return Settings.instance().getIconPack();
  }

  @Override
  protected void getSettings (RunnableData<List<Settings.IconPack>> callback) {
    tdlib.getIconPacks(callback);
  }

  @Override
  protected void applySetting (Settings.IconPack setting) {
    // TODO Drawables.instance().changeIconPack(setting);
    if (getThemeController() != null) {
      getThemeController().updateSelectedIconPack();
    }
  }
}
