package org.thunderdog.challegram.ui;

import android.content.Context;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;

import java.util.List;

import me.vkryl.core.lambda.RunnableData;

/**
 * Date: 2019-10-20
 * Author: default
 */
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
