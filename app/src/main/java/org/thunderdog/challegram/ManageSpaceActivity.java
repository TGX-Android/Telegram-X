/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 17/07/2017
 */
package org.thunderdog.challegram;

import android.os.Bundle;

import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.ui.SettingsCacheController;

public class ManageSpaceActivity extends BaseActivity {
  @Override
  protected boolean needDrawer () {
    return false;
  }

  @Override
  public void onCreate (Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    navigation.initController(new SettingsCacheController(this, TdlibManager.instance().current()));
  }
}
