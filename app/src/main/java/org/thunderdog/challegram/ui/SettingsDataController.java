/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 16/11/2016
 */
package org.thunderdog.challegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGNetworkStats;
import org.thunderdog.challegram.data.TGStorageStatsFast;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.GlobalConnectionListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.voip.VoIPController;

import java.util.List;

import me.vkryl.core.ArrayUtils;

public class SettingsDataController extends RecyclerViewController<SettingsDataController.Args> implements View.OnClickListener, ViewController.SettingsIntDelegate,
  GlobalConnectionListener, Client.ResultHandler, Settings.ProxyChangeListener {
  public static class Args {
    public int mode;
    public Object data;

    public Args (int mode) {
      this.mode = mode;
    }

    public Args setData (Object data) {
      this.data = data;
      return this;
    }
  }

  public SettingsDataController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args.mode;
    switch (mode) {
      case MODE_STATISTICS: {
        networkStats = (TGNetworkStats) args.data;
        break;
      }
    }
  }

  private int mode;

  private static final int MODE_NONE = 0;
  private static final int MODE_STATISTICS = 1;

  @Override
  public int getId () {
    return R.id.controller_chatSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(mode == MODE_STATISTICS ? R.string.NetworkUsage : R.string.DataSettings);
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putInt(keyPrefix + "mode", mode);
    if (mode == MODE_NONE) {
      outState.putBoolean(keyPrefix + "advanced", adapter.indexOfViewById(R.id.btn_showAdvanced) == -1);
    }
    return true;
  }

  private boolean forceOpenAdvanced;

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    int mode = in.getInt(keyPrefix + "mode", MODE_NONE);
    forceOpenAdvanced = mode == MODE_NONE && in.getBoolean(keyPrefix + "advanced", false);
    if (mode != MODE_NONE) {
      setArguments(new Args(mode));
    }
    return true;
  }

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  private SettingsAdapter adapter;

  private View headerCell;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @SuppressLint("InflateParams")
  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    this.adapter = new SettingsAdapter(this) {
      @Override
      public void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_dataSaver) {
          final boolean isEnabled = !tdlib.files().isDataSaverEventuallyEnabled();
          if (isUpdate) {
            view.setEnabledAnimated(isEnabled);
          } else {
            view.setEnabled(isEnabled);
          }
          view.getToggler().setRadioEnabled(tdlib.files().isDataSaverActive(), isUpdate);
        } else if (itemId == R.id.btn_dataSaverForce) {
          final boolean overMobile = tdlib.files().isDataSaverEnabledOverMobile();
          final boolean overRoaming = tdlib.files().isDataSaverEnabledOverRoaming();
          final int resource = overMobile && overRoaming ? R.string.WhenUsingMobileDataOrRoaming : overMobile ? R.string.WhenUsingMobileData : overRoaming ? R.string.WhenUsingRoaming : R.string.Never;
          view.setData(resource);

          final boolean isEnabled = tdlib.files().isDataSaverEventuallyEnabled() || !tdlib.files().isDataSaverAlwaysEnabled();
          if (isUpdate) {
            view.setEnabledAnimated(isEnabled);
          } else {
            view.setEnabled(isEnabled);
          }
        } else if (itemId == R.id.btn_proxy) {
          int proxyId = Settings.instance().getEffectiveProxyId();
          if (proxyId != Settings.PROXY_ID_NONE) {
            view.setData(Settings.instance().getProxyName(proxyId));
          } else {
            view.setData(Settings.instance().getAvailableProxyCount() == 0 ? R.string.ProxySetup : R.string.ProxyDisabled);
          }
        } else if (itemId == R.id.btn_inPrivateChats) {
          view.setData(tdlib.files().getDownloadInPrivateChatsList());
        } else if (itemId == R.id.btn_inGroupChats) {
          view.setData(tdlib.files().getDownloadInGroupChatsList());
        } else if (itemId == R.id.btn_inChannelChats) {
          view.setData(tdlib.files().getDownloadInChannelChatsList());
        } else if (itemId == R.id.btn_mediaWiFiLimits) {
          view.setData(tdlib.files().getDownloadLimitOverWiFiString());
        } else if (itemId == R.id.btn_mediaMobileLimits) {
          view.setData(tdlib.files().getDownloadLimitOverMobileString());
        } else if (itemId == R.id.btn_mediaRoamingLimits) {
          view.setData(tdlib.files().getDownloadLimitOverRoamingString());
          // Voice
        } else if (itemId == R.id.btn_lessDataForCalls) {
          switch (tdlib.files().getVoipDataSavingOption()) {
            case VoIPController.DATA_SAVING_ALWAYS:
              view.setData(R.string.UseLessDataAlways);
              break;
            case VoIPController.DATA_SAVING_MOBILE:
              view.setData(R.string.OnMobileNetwork);
              break;
            case VoIPController.DATA_SAVING_ROAMING:
              view.setData(R.string.OnRoaming);
              break;
            case VoIPController.DATA_SAVING_NEVER:
            default:
              view.setData(R.string.Never);
              break;
          }
          // Storage usage
        } else if (itemId == R.id.btn_storageUsage) {
          view.setData(storageStats != null ? storageStats.isEmpty() ? Lang.getString(R.string.StorageUsageHint) : storageStats.getTotalSizeEntry() : Lang.getString(R.string.Calculating));
          // Data usage
        } else if (itemId == R.id.btn_dataUsageTotal) {
          view.setData(networkStats != null ? networkStats.getTotalEntry() : Lang.getString(R.string.Calculating));
        } else if (itemId == R.id.btn_dataUsageMobile) {
          view.setData(networkStats != null ? networkStats.getMobileEntry() : Lang.getString(R.string.Calculating));
        } else if (itemId == R.id.btn_dataUsageRoaming) {
          view.setData(networkStats != null ? networkStats.getRoamingEntry() : Lang.getString(R.string.Calculating));
        } else if (itemId == R.id.btn_dataUsageWiFi) {
          view.setData(networkStats != null ? networkStats.getWiFiEntry() : Lang.getString(R.string.Calculating));
        } else if (itemId == R.id.btn_resetNetworkStats) {
          view.setData(networkStats != null ? networkStats.getDateEntry() : Lang.getString(R.string.LoadingInformation));
        }
      }
    };

    final ListItem[] rawItems;

    if (mode == MODE_STATISTICS) {
      if (tdlib.context().isMultiUser()) {
        DoubleHeaderView headerCell = new DoubleHeaderView(context);
        headerCell.setThemedTextColor(this);
        headerCell.initWithMargin(0, true);
        headerCell.setTitle(getName());
        headerCell.setSubtitle(tdlib.account().getName());
        this.headerCell = headerCell;
      }
      rawItems = new ListItem[] {
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_dataUsageMobile, R.drawable.baseline_signal_cellular_alt_24, R.string.MobileUsage),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_dataUsageWiFi, R.drawable.baseline_wifi_24, R.string.WiFiUsage),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_dataUsageRoaming, R.drawable.baseline_public_24, R.string.RoamingUsage),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_resetNetworkStats, 0, R.string.ResetStatistics).setTextColorId(ColorId.textNegative),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
      };
    } else {
      rawItems = new ListItem[] {
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_storageUsage, R.drawable.baseline_data_usage_24, R.string.StorageUsage),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_dataUsageTotal, R.drawable.baseline_import_export_24, R.string.NetworkUsage),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_dataSaver, 0, R.string.DataSaver),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_dataSaverForce, 0, R.string.TurnOnAutomatically),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
        new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.DataSaverDesc),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Connection),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_lessDataForCalls, 0, R.string.VoipUseLessData),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_proxy, 0, R.string.Proxy),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),

        new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AutomaticMediaDownload),
        new ListItem(ListItem.TYPE_SHADOW_TOP),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inPrivateChats, 0, R.string.InPrivateChats),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inGroupChats, 0, R.string.InGroups),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_inChannelChats, 0, R.string.InChannels),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_SETTING, R.id.btn_showAdvanced, 0, R.string.Advanced),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
      };
    }
    this.adapter.setItems(rawItems, false);
    if (forceOpenAdvanced) {
      List<ListItem> items = adapter.getItems();
      int index = adapter.indexOfViewById(R.id.btn_showAdvanced);
      if (index != -1) {
        items.remove(index);
        items.remove(index);

        ListItem[] advancedItems = newAdvancedItems();

        ArrayUtils.ensureCapacity(items, items.size() + advancedItems.length);
        int i = index;
        for (ListItem item : advancedItems) {
          items.add(i++, item);
        }
      }
    }
    recyclerView.setAdapter(adapter);

    switch (mode) {
      case MODE_NONE: {
        tdlib.client().send(new TdApi.GetStorageStatisticsFast(), this);
        tdlib.client().send(new TdApi.GetNetworkStatistics(), this);

        TdlibManager.instance().global().addConnectionListener(this);
        Settings.instance().addProxyListener(this);
        break;
      }
      case MODE_STATISTICS: {
        if (networkStats == null) {
          tdlib.client().send(new TdApi.GetNetworkStatistics(), this);
        }
        break;
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (mode == MODE_NONE) {
      TdlibManager.instance().global().removeConnectionListener(this);
      Settings.instance().removeProxyListener(this);
    }
  }

  private TGStorageStatsFast storageStats;

  public interface StorageStatsFastCallback {
    void onStorageStatsFastLoaded (TGStorageStatsFast stats);
  }

  private StorageStatsFastCallback statsCallback;

  public void setStorageStats (final TGStorageStatsFast stats) {
    if (!isDestroyed()) {
      this.storageStats = stats;
      adapter.updateValuedSettingById(R.id.btn_storageUsage);
      if (statsCallback != null) {
        statsCallback.onStorageStatsFastLoaded(stats);
        statsCallback = null;
      }
    }
  }

  public @Nullable TGStorageStatsFast getStorageStats () {
    return storageStats;
  }

  public void setStorageStatsCallback (StorageStatsFastCallback callback) {
    this.statsCallback = callback;
  }

  private TGNetworkStats networkStats;

  private void setNetworkStats (TGNetworkStats stats) {
    this.networkStats = stats;

    if (mode == MODE_STATISTICS) {
      adapter.updateValuedSettingById(R.id.btn_dataUsageWiFi);
      adapter.updateValuedSettingById(R.id.btn_dataUsageMobile);
      adapter.updateValuedSettingById(R.id.btn_dataUsageRoaming);
      adapter.updateValuedSettingById(R.id.btn_resetNetworkStats);

      ViewController<?> c = previousStackItem();
      if (c != null) {
        ((SettingsDataController) c).setNetworkStats(stats);
      }
    } else {
      adapter.updateValuedSettingById(R.id.btn_dataUsageTotal);
    }
  }

  @Override
  public void onProxyConfigurationChanged (int proxyId, @Nullable TdApi.InternalLinkTypeProxy proxy, String description, boolean isCurrent, boolean isNewAdd) {
    if (isCurrent) {
      adapter.updateValuedSettingById(R.id.btn_proxy);
    }
  }

  @Override
  public void onProxyAvailabilityChanged (boolean isAvailable) { }

  @Override
  public void onProxyAdded (Settings.Proxy proxy, boolean isCurrent) { }

  @Override
  public void onResult (final TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.StorageStatisticsFast.CONSTRUCTOR: {
        final TGStorageStatsFast stats = new TGStorageStatsFast((TdApi.StorageStatisticsFast) object, null);
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            setStorageStats(stats);
          }
        });
        break;
      }
      case TdApi.NetworkStatistics.CONSTRUCTOR: {
        final TGNetworkStats stats = new TGNetworkStats((TdApi.NetworkStatistics) object);
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            setNetworkStats(stats);
          }
        });
        break;
      }
      /*case TdApi.ProxyEmpty.CONSTRUCTOR:
      case TdApi.ProxySocks5.CONSTRUCTOR: {
        tdlib.ui().post(new Runnable() {
          @Override
          public void run () {
            if (!isDestroyed()) {
              setProxy((TdApi.Proxy) object);
            }
          }
        });
        break;
      }*/
      case TdApi.Error.CONSTRUCTOR: {
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            if (storageStats == null) {
              setStorageStats(new TGStorageStatsFast(null, null));
            } else {
              UI.showError(object);
            }
          }
        });
        break;
      }
    }
  }

  @Override
  public void onConnectionStateChanged (Tdlib tdlib, int newState, boolean isCurrent) { }

  @Override
  public void onConnectionTypeChanged (int oldConnectionType, int connectionType) {
    if (!isDestroyed()) {
      adapter.updateValuedSettingById(R.id.btn_dataSaver);
      adapter.updateValuedSettingById(R.id.btn_dataSaverForce);
    }
  }

  @Override
  public void onSystemDataSaverStateChanged (boolean isEnabled) {
    if (!isDestroyed()) {
      adapter.updateValuedSettingById(R.id.btn_dataSaver);
      adapter.updateValuedSettingById(R.id.btn_dataSaverForce);
    }
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    final boolean toggleResult = adapter.toggleView(v);

    if (id == R.id.btn_resetNetworkStats) {
      showOptions(Lang.getString(R.string.ResetStatsHint), new int[] {R.id.btn_delete, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Reset), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, optionId) -> {
        if (optionId == R.id.btn_delete) {
          tdlib.client().send(new TdApi.ResetNetworkStatistics(), object -> {
            switch (object.getConstructor()) {
              case TdApi.Ok.CONSTRUCTOR: {
                tdlib.client().send(new TdApi.GetNetworkStatistics(), SettingsDataController.this);
                break;
              }
              case TdApi.Error.CONSTRUCTOR: {
                UI.showError(object);
                break;
              }
            }
          });
        }
        return true;
      });
    } else if (id == R.id.btn_storageUsage) {
      SettingsCacheController cacheController = new SettingsCacheController(context, tdlib);
      cacheController.setArguments(this);
      navigateTo(cacheController);
    } else if (id == R.id.btn_dataUsageTotal) {
      if (networkStats == null) {
        return;
      }
      SettingsDataController c = new SettingsDataController(context, tdlib);
      c.setArguments(new Args(MODE_STATISTICS).setData(networkStats));
      navigateTo(c);
    } else if (id == R.id.btn_dataUsageMobile || id == R.id.btn_dataUsageWiFi || id == R.id.btn_dataUsageRoaming) {
      if (networkStats == null) {
        return;
      }

      int type;
      if (id == R.id.btn_dataUsageMobile) {
        type = TGNetworkStats.TYPE_MOBILE;
      } else if (id == R.id.btn_dataUsageRoaming) {
        type = TGNetworkStats.TYPE_ROAMING;
      } else if (id == R.id.btn_dataUsageWiFi) {
        type = TGNetworkStats.TYPE_WIFI;
      } else {
        return;
      }

      SettingsNetworkStatsController c = new SettingsNetworkStatsController(context, tdlib);
      c.setArguments(new SettingsNetworkStatsController.Args(type, networkStats));
      navigateTo(c);
    } else if (id == R.id.btn_lessDataForCalls) {
      showSettings(new SettingsWrapBuilder(id).addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, R.string.UseLessDataForCallsDesc)).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_disabled, 0, R.string.Never, id, tdlib.files().getVoipDataSavingOption() == VoIPController.DATA_SAVING_NEVER),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_roaming, 0, R.string.OnRoaming, id, tdlib.files().getVoipDataSavingOption() == VoIPController.DATA_SAVING_ROAMING),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_mobile, 0, R.string.OnMobileNetwork, id, tdlib.files().getVoipDataSavingOption() == VoIPController.DATA_SAVING_MOBILE),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_always, 0, R.string.UseLessDataAlways, id, tdlib.files().getVoipDataSavingOption() == VoIPController.DATA_SAVING_ALWAYS)
      }).setIntDelegate(this));
    } else if (id == R.id.btn_proxy) {
      tdlib.ui().openProxySettings(this, true);
    } else if (id == R.id.btn_dataSaver) {
      if (tdlib.files().setDataSaverEnabled(toggleResult)) {
        adapter.updateValuedSettingById(R.id.btn_dataSaverForce);
      }
    } else if (id == R.id.btn_dataSaverForce) {
      showSettings(id, new ListItem[] {
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_forceMobile, 0, R.string.WhenUsingMobileData, tdlib.files().isDataSaverEnabledOverMobile()),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_forceRoaming, 0, R.string.WhenUsingRoaming, tdlib.files().isDataSaverEnabledOverRoaming())
      }, this);
    } else if (id == R.id.btn_inPrivateChats || id == R.id.btn_inGroupChats || id == R.id.btn_inChannelChats || id == R.id.btn_mediaMobileLimits || id == R.id.btn_mediaWiFiLimits || id == R.id.btn_mediaRoamingLimits) {
      int flags;
      int sizeOption = 0;
      int size = 0;
      if (id == R.id.btn_inPrivateChats) {
        flags = tdlib.files().getDownloadInPrivateChats();
      } else if (id == R.id.btn_inGroupChats) {
        flags = tdlib.files().getDownloadInGroupChats();
      } else if (id == R.id.btn_inChannelChats) {
        flags = tdlib.files().getDownloadInChannelChats();
      } else if (id == R.id.btn_mediaMobileLimits) {
        flags = tdlib.files().getExcludeOverMobile();
        size = tdlib.files().getDownloadLimitOverMobile();
        sizeOption = R.id.btn_size;
      } else if (id == R.id.btn_mediaWiFiLimits) {
        flags = tdlib.files().getExcludeOverWiFi();
        size = tdlib.files().getDownloadLimitOverWifi();
        sizeOption = R.id.btn_size;
      } else if (id == R.id.btn_mediaRoamingLimits) {
        flags = tdlib.files().getExcludeOverRoaming();
        size = tdlib.files().getDownloadLimitOverRoaming();
        sizeOption = R.id.btn_size;
      } else {
        throw new RuntimeException();
      }

      int currentValue = 0;
      String[] sizeOptions;
      if (sizeOption != 0) {
        sizeOptions = new String[TdlibFilesManager.DOWNLOAD_LIMIT_OPTIONS.length];
        for (int i = 0; i < sizeOptions.length; i++) {
          sizeOptions[i] = tdlib.files().getDownloadLimitString(TdlibFilesManager.DOWNLOAD_LIMIT_OPTIONS[i]);
          if (TdlibFilesManager.DOWNLOAD_LIMIT_OPTIONS[i] == size) {
            currentValue = i;
          }
        }
      } else {
        sizeOptions = null;
      }

      showSettings(new SettingsWrapBuilder(id).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_photos, 0, sizeOption != 0 ? R.string.NoPhotos : R.string.Photos, (flags & TdlibFilesManager.DOWNLOAD_FLAG_PHOTO) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_voice, 0, sizeOption != 0 ? R.string.NoVoiceMessages : R.string.VoiceMessages, (flags & TdlibFilesManager.DOWNLOAD_FLAG_VOICE) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_videoNote, 0, sizeOption != 0 ? R.string.NoVideoMessages : R.string.VideoMessages, (flags & TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_video, 0, sizeOption != 0 ? R.string.NoVideos : R.string.Videos, (flags & TdlibFilesManager.DOWNLOAD_FLAG_VIDEO) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_file, 0, sizeOption != 0 ? R.string.NoFiles : R.string.Files, (flags & TdlibFilesManager.DOWNLOAD_FLAG_FILE) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_music, 0, sizeOption != 0 ? R.string.NoMusic : R.string.Music, (flags & TdlibFilesManager.DOWNLOAD_FLAG_MUSIC) != 0),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_gif, 0, sizeOption != 0 ? R.string.NoGIFs : R.string.GIFs, (flags & TdlibFilesManager.DOWNLOAD_FLAG_GIF) != 0),
      }).setIntDelegate(this).setSizeOptionId(sizeOption).setSizeValue(currentValue).setSizeValues(sizeOptions).setAllowResize(false));
    } else if (id == R.id.btn_cacheSettings) {
      navigateTo(new SettingsCacheController(context, tdlib));
    } else if (id == R.id.btn_showAdvanced) {
      final int index = adapter.indexOfViewById(R.id.btn_showAdvanced);

      if (index != -1) {
        List<ListItem> items = adapter.getItems();

        items.remove(index);
        items.remove(index);

        ListItem[] advancedItems = newAdvancedItems();

        ArrayUtils.ensureCapacity(items, items.size() + advancedItems.length);
        int i = index;
        for (ListItem item : advancedItems) {
          items.add(i++, item);
        }

        adapter.notifyItemRangeRemoved(index, 2);
        adapter.notifyItemRangeInserted(index, advancedItems.length);
      }
    }
  }

  private ListItem[] newAdvancedItems () {
    return new ListItem[] {
      new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_mediaMobileLimits, 0, R.string.RestrictOverMobile),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_mediaWiFiLimits, 0, R.string.RestrictOnWiFi),
        new ListItem(ListItem.TYPE_SEPARATOR_FULL),
        new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_mediaRoamingLimits, 0, R.string.RestrictOnRoaming),
        new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
      // new SettingItem(SettingItem.TYPE_DESCRIPTION, 0, 0, R.string.SizeLimitDesc)
    };
  }

  @Override
  public void onApplySettings (@IdRes int id, SparseIntArray result) {
    if (id == R.id.btn_dataSaverForce) {
      final boolean forceMobile = result.get(R.id.btn_forceMobile) != 0;
      final boolean forceRoaming = result.get(R.id.btn_forceRoaming) != 0;

      if (tdlib.files().setDataSaverForcedOptions(forceMobile, forceRoaming)) {
        adapter.updateValuedSettingById(R.id.btn_dataSaver);
        adapter.updateValuedSettingById(id);
      }
    } else if (id == R.id.btn_lessDataForCalls) {
      final int res = result.get(R.id.btn_lessDataForCalls);
      final int option =
        res == R.id.btn_always ? VoIPController.DATA_SAVING_ALWAYS :
          res == R.id.btn_mobile ? VoIPController.DATA_SAVING_MOBILE :
            res == R.id.btn_roaming ? VoIPController.DATA_SAVING_ROAMING :
              VoIPController.DATA_SAVING_NEVER;

      if (tdlib.files().setVoipDataSavingOption(option)) {
        adapter.updateValuedSettingById(R.id.btn_lessDataForCalls);
      }
    } else if (id == R.id.btn_inPrivateChats || id == R.id.btn_inGroupChats || id == R.id.btn_inChannelChats || id == R.id.btn_mediaMobileLimits || id == R.id.btn_mediaWiFiLimits || id == R.id.btn_mediaRoamingLimits) {
      int size = 0;
      int flags = 0;
      final int itemCount = result.size();
      for (int i = 0; i < itemCount; i++) {
        int key = result.keyAt(i);
        int value = result.valueAt(i);
        if (key == R.id.btn_size) {
          size = TdlibFilesManager.DOWNLOAD_LIMIT_OPTIONS[value];
        } else if (key == R.id.btn_photos) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_PHOTO;
        } else if (key == R.id.btn_voice) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_VOICE;
        } else if (key == R.id.btn_videoNote) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE;
        } else if (key == R.id.btn_video) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_VIDEO;
        } else if (key == R.id.btn_file) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_FILE;
        } else if (key == R.id.btn_music) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_MUSIC;
        } else if (key == R.id.btn_gif) {
          flags |= TdlibFilesManager.DOWNLOAD_FLAG_GIF;
        }
      }

      boolean changed = false;
      if (id == R.id.btn_inPrivateChats) {
        changed = tdlib.files().setDownloadInPrivateChats(flags);
      } else if (id == R.id.btn_inGroupChats) {
        changed = tdlib.files().setDownloadInGroupChats(flags);
      } else if (id == R.id.btn_inChannelChats) {
        changed = tdlib.files().setDownloadInChannelChats(flags);
      } else if (id == R.id.btn_mediaMobileLimits) {
        changed = tdlib.files().setLimitsOverMobile(flags, size);
      } else if (id == R.id.btn_mediaWiFiLimits) {
        changed = tdlib.files().setLimitsOverWiFi(flags, size);
      } else if (id == R.id.btn_mediaRoamingLimits) {
        changed = tdlib.files().setLimitsOverRoaming(flags, size);
      }

      if (changed) {
        adapter.updateValuedSettingById(id);
      }
    }
  }
}
