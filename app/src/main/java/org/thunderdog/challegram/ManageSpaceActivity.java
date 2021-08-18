package org.thunderdog.challegram;

import android.os.Bundle;

import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.ui.SettingsCacheController;

/**
 * Date: 7/17/17
 * Author: default
 */

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
