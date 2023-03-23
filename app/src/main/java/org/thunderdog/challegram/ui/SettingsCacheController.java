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

import android.content.Context;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGStorageStats;
import org.thunderdog.challegram.data.TGStorageStatsFast;
import org.thunderdog.challegram.loader.ImageLoader;
import org.thunderdog.challegram.mediaview.paint.PaintState;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.telegram.TGLegacyManager;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.DoubleTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.FileUtils;
import me.vkryl.core.collection.SparseLongArray;

public class SettingsCacheController extends RecyclerViewController<SettingsDataController> implements View.OnClickListener, Client.ResultHandler, SettingsDataController.StorageStatsFastCallback, MoreDelegate, TGLegacyManager.EmojiLoadListener {
  public SettingsCacheController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_storageSettings;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.StorageUsage);
  }

  private SettingsAdapter adapter;

  private int headerSize;

  private boolean isCleaningUp;
  private long[] busyChatIds;
  private TGStorageStats.Entry busyEntry;

  private @Nullable TGStorageStatsFast fastStats;

  private long keepMedia;

  private void setKeepMedia (long keepMediaInSeconds, boolean needRequest) {
    if (this.keepMedia != keepMediaInSeconds && !isDestroyed()) {
      this.keepMedia = keepMediaInSeconds;
      adapter.updateValuedSettingById(R.id.btn_keepMedia);
      if (needRequest) {
        tdlib.client().send(new TdApi.SetOption("storage_max_time_from_last_access", new TdApi.OptionValueInteger(keepMediaInSeconds)), tdlib.okHandler());
        tdlib.client().send(new TdApi.SetOption("use_storage_optimizer", new TdApi.OptionValueBoolean(keepMediaInSeconds != 0)), tdlib.okHandler());
      }
    }
  }

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  private boolean isBusyGlobally;

  private void setIsBusyGlobally (boolean isBusyGlobally) {
    if (this.isBusyGlobally != isBusyGlobally) {
      this.isBusyGlobally = isBusyGlobally;
      adapter.updateValuedSettingById(R.id.btn_localDatabase);
      adapter.updateValuedSettingById(R.id.btn_clearCache);
    }
  }

  private View headerCell;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    TGLegacyManager.instance().addEmojiListener(this);
    if (tdlib.context().isMultiUser()) {
      DoubleHeaderView headerCell = new DoubleHeaderView(context);
      headerCell.setThemedTextColor(this);
      headerCell.initWithMargin(Screen.dp(49f), true);
      headerCell.setTitle(getName());
      headerCell.setSubtitle(tdlib.account().getName());
      this.headerCell = headerCell;
    }
    SettingsDataController parent = getArguments();
    if (parent != null) {
      this.fastStats = parent.getStorageStats();
    }
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_keepMedia: {
            view.setData(keepMedia == 0 ? Lang.getString(R.string.KeepMediaForever) : Lang.getDuration((int) keepMedia, 0, 0, false));
            break;
          }
          case R.id.btn_storagePath: {
            TdApi.SetTdlibParameters parametersRequest = tdlib.clientParameters();
            view.setData(parametersRequest != null ? parametersRequest.filesDirectory : "Unavailable");
            view.setEnabled(parametersRequest != null && item.getBoolValue());
            break;
          }
          case R.id.btn_localDatabase:
            view.setEnabledAnimated(fastStats != null, isUpdate);
            view.setData(fastStats != null ? Strings.buildSize(fastStats.getDatabaseSize(stats != null ? stats.getDatabaseAddSize() : 0)) : Lang.getString(R.string.Calculating));
            break;
          case R.id.btn_settings:
            view.setEnabled(false);
            view.setData(fastStats != null ? Lang.getString(R.string.format_approx, Strings.buildSize(fastStats.getSettingsSize())) : Lang.getString(R.string.Calculating));
            break;
          case R.id.btn_lottie:
            view.setEnabledAnimated(fastStats != null, isUpdate);
            view.setData(fastStats != null ? Strings.buildSize(fastStats.getLottieSize()) : Lang.getString(R.string.Calculating));
            break;
          case R.id.btn_languageSettings:
            view.setEnabled(false);
            view.setData(fastStats != null ? Strings.buildSize(fastStats.getLanguagePackDatabaseSize()) : Lang.getString(R.string.Calculating));
            break;
          case R.id.btn_junk:
            view.setEnabledAnimated(fastStats != null, isUpdate);
            view.setData(fastStats != null ? Strings.buildSize(fastStats.getJunkSize()) : Lang.getString(R.string.Calculating));
            break;
          case R.id.btn_paint:
            view.setEnabledAnimated(fastStats != null, isUpdate);
            view.setData(fastStats != null ? Strings.buildSize(fastStats.getPaintsSize()) : Lang.getString(R.string.Calculating));
            break;
          case R.id.btn_camera:
            view.setEnabledAnimated(fastStats != null && fastStats.getPrivateCameraMediaSize() > 0, isUpdate);
            view.setData(fastStats != null ? Strings.buildSize(fastStats.getPrivateCameraMediaSize()) : Lang.getString(R.string.Calculating));
            break;
          case R.id.btn_emoji:
            view.setEnabledAnimated(fastStats != null && fastStats.getEmojiUnusedSize() > 0, isUpdate);
            view.setData(fastStats != null ? Strings.buildSize(fastStats.getEmojiSize()) : Lang.getString(R.string.Calculating));
            break;
          case R.id.btn_logsSize:
            view.setEnabledAnimated(fastStats != null, isUpdate);
            view.setData(fastStats != null ? Strings.buildSize(fastStats.getLogsSize()) : Lang.getString(R.string.Calculating));
            break;

          case R.id.btn_otherChats:
          case R.id.btn_otherFiles: {
            if (stats != null) {
              boolean isChat = item.getId() == R.id.btn_otherChats;
              final TGStorageStats.Entry entry = isChat ? stats.getOtherChatsEntry() : stats.getOtherFilesEntry();
              if (isUpdate) {
                view.setEnabledAnimated(!entry.isEmpty() && !isCleaningUp);
              } else {
                view.setEnabled(!entry.isEmpty() && !isCleaningUp);
              }
              if (isCleaningUp && ((isChat && busyChatIds != null && busyChatIds.length == 1 && busyChatIds[0] == 0)) || (!isChat && busyEntry == stats.getOtherFilesEntry())) {
                view.setData(R.string.CleaningUp);
              } else {
                view.setData(Strings.buildSize(entry.getSize()));
              }
            } else {
              view.setData(R.string.Calculating);
            }
            break;
          }
          case R.id.btn_clearCache: {
            if (stats != null) {
              if (isUpdate) {
                view.setEnabledAnimated(!stats.isFilesEmpty() && !isCleaningUp);
              } else {
                view.setEnabled(!stats.isFilesEmpty() && !isCleaningUp);
              }
              if (isCleaningUp) {
                view.setData(R.string.CleaningUp);
              } else {
                view.setData(stats.getFilesSize());
              }
            } else {
              view.setData(R.string.Calculating);
            }
            break;
          }
        }
      }

      @Override
      protected void setDoubleText (ListItem item, int position, DoubleTextView textView, boolean isUpdate) {
        TGStorageStats.Entry entry = (TGStorageStats.Entry) item.getData();
        if (entry != null) {
          textView.setText(entry.getTitle(), isCleaningUp && ArrayUtils.contains(busyChatIds, item.getLongId()) ? Lang.getString(R.string.CleaningUp) : Strings.buildSize(entry.getSize()));
          textView.setTitleColorId(entry.isSecret() ? R.id.theme_color_textSecure : entry.isSelfChat() ? R.id.theme_color_textNeutral : R.id.theme_color_text);
          textView.setChatAvatar(tdlib, entry.getId());
        }
      }
    };

    ArrayList<ListItem> items = new ArrayList<>();

    /*if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      TdApi.TdlibParameters parameters = tdlib.clientParameters();
      if (parameters != null) {
        File[] files = UI.getContext().getExternalFilesDirs(null);
        boolean changeAvailable = files != null && files.length > 1;
        items.add(new SettingItem(SettingItem.TYPE_VALUED_SETTING, R.id.btn_storagePath, 0, R.string.Location).setBoolValue(changeAvailable));
        items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
      }
    }*/

    items.add(new ListItem(VIEW_TYPE, R.id.btn_keepMedia, 0, R.string.KeepMedia));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Strings.replaceBoldTokens(Lang.getString(R.string.KeepMediaInfo), R.id.theme_color_background_textLight), false));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(VIEW_TYPE, R.id.btn_settings, 0, R.string.SettingsAndThemes));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(VIEW_TYPE, R.id.btn_languageSettings, 0, R.string.LanguageDatabase));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    if (needCameraEntry()) {
      items.add(new ListItem(VIEW_TYPE, R.id.btn_camera, 0, R.string.InAppCameraCache));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    if (needEmojiEntry()) {
      items.add(new ListItem(VIEW_TYPE, R.id.btn_emoji, 0, R.string.EmojiSets));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    if (needLottieEntry()) {
      items.add(new ListItem(VIEW_TYPE, R.id.btn_lottie, 0, R.string.AnimatedStickers));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    if (needLogEntry()) {
      items.add(new ListItem(VIEW_TYPE, R.id.btn_logsSize, 0, R.string.LogFiles));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    if (needPaintsEntry()) {
      items.add(new ListItem(VIEW_TYPE, R.id.btn_paint, 0, R.string.Paints));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    if (needJunkEntry()) {
      items.add(new ListItem(VIEW_TYPE, R.id.btn_junk, 0, R.string.JunkFiles));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    }
    items.add(new ListItem(VIEW_TYPE, R.id.btn_localDatabase, 0, R.string.LocalDatabase));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(VIEW_TYPE, R.id.btn_clearCache, 0, R.string.MediaAndFiles));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    headerSize = items.size();
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_clearCacheHint, 0, R.string.ClearCacheHint));
    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);

    if (fastStats == null) {
      if (parent != null) {
        parent.setStorageStatsCallback(this);
      } else {
        tdlib.client().send(new TdApi.GetStorageStatisticsFast(), this);
      }
    }

    TGLegacyManager.instance().addEmojiListener(adapter);
    tdlib.client().send(new TdApi.GetOption("storage_max_time_from_last_access"), result -> {
      switch (result.getConstructor()) {
        case TdApi.OptionValueInteger.CONSTRUCTOR: {
          final long value = ((TdApi.OptionValueInteger) result).value;
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              setKeepMedia(value, false);
            }
          });
          break;
        }
      }
    });
    getStats(true);
    tdlib.ui().postDelayed(this::executeScheduledAnimation, 500);
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return this.stats == null;
  }

  private void updateFastStatsEntries () {
    adapter.updateValuedSettingById(R.id.btn_localDatabase);
    adapter.updateValuedSettingById(R.id.btn_settings);
    adapter.updateValuedSettingById(R.id.btn_languageSettings);
    checkCameraEntry();
    checkEmojiEntry();
    checkLottieEntry();
    checkLogEntry();
    checkPaintEntry();
    checkJunkEntry();
  }

  private void setStorageStatsFast (TGStorageStatsFast stats) {
    if (!isDestroyed()) {
      this.fastStats = stats;
      if (adapter != null) {
        updateFastStatsEntries();
      }
    }
  }

  @Override
  public void onStorageStatsFastLoaded (TGStorageStatsFast stats) {
    setStorageStatsFast(stats);
  }

  private boolean shortPolling;

  private boolean needShowAllChats;

  private void showAllChats () {
    if (!needShowAllChats) {
      needShowAllChats = true;
      getStats(false);
    }
  }

  private int getChatIdsLimit (boolean firstRequest) {
    return firstRequest ? 0 : needShowAllChats ? 1000 : CHAT_ID_LIMIT;
  }

  @Override
  protected void onFocusStateChanged () {
    super.onFocusStateChanged();
    checkShortPolling();
  }

  private static final long SHORT_POLLING_TIME = 2500;

  private Runnable shortPollingActor;

  private void checkShortPolling () {
    setShortPolling(isFocused() && tdlib != null && Settings.instance().hasLogsEnabled());
  }

  private void setShortPolling (boolean shortPolling) {
    if (this.shortPolling != shortPolling) {
      this.shortPolling = shortPolling;
      if (shortPolling) {
        shortPollingActor = new Runnable() {
          @Override
          public void run () {
            if (shortPollingActor == this) {
              if (!isDestroyed() && isFocused()) {
                tdlib.client().send(new TdApi.GetStorageStatisticsFast(), SettingsCacheController.this);
              }
              if (SettingsCacheController.this.shortPolling) {
                tdlib.ui().postDelayed(this, SHORT_POLLING_TIME);
              }
            }
          }
        };
        shortPollingActor.run();
      } else {
        tdlib.ui().removeCallbacks(shortPollingActor);
        shortPollingActor = null;
      }
    }
  }

  public void onDataErased () {
    setIsBusyGlobally(false);
    if (navigationController != null) {
      SettingsDataController dataController = new SettingsDataController(context, tdlib);
      setArguments(dataController);
      dataController.getValue();
      navigationController.insertController(dataController, 0);
      SettingsController c = new SettingsController(context, tdlib);
      navigationController.insertController(c, 0);
      c.getValue();
    }
    tdlib.client().send(new TdApi.GetStorageStatisticsFast(), this);
    getStats(false);
  }

  private void getStats (boolean firstRequest) {
    tdlib.client().send(new TdApi.GetStorageStatistics(getChatIdsLimit(firstRequest)), result -> {
      processStorageStats(result, firstRequest);
    });
  }

  private void reloadFastStats () {
    if (!isDestroyed()) {
      tdlib.client().send(new TdApi.GetStorageStatisticsFast(), this);
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_localDatabase: {
        openFeatureUnavailable(R.string.LocalDatabaseExcuse);
        break;
      }
      case R.id.btn_showOtherChats: {
        showAllChats();
        break;
      }
      case R.id.btn_paint: {
        if (fastStats != null) {
          showOptions(Lang.getString(R.string.PaintsInfo), new int[] {R.id.btn_deleteFile, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ClearX, Strings.buildSize(fastStats.getPaintsSize())), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_deleteFile) {
              Background.instance().post(() -> {
                FileUtils.delete(PaintState.getPaintsDir(), true);
                reloadFastStats();
              });
            }
            return true;
          });
        }
        break;
      }
      case R.id.btn_junk: {
        if (fastStats != null) {
          showOptions(Lang.getString(R.string.JunkFilesInfo), new int[] {R.id.btn_deleteFile, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ClearX, Strings.buildSize(fastStats.getJunkSize())), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_deleteFile) {
              if (!fastStats.deleteJunk())
                Log.w("Failed to delete some junk");
              reloadFastStats();
            }
            return true;
          });
        }
        break;
      }
      case R.id.btn_camera: {
        if (fastStats != null) {
          showOptions(Lang.getString(R.string.InAppCameraCacheDeleteConfirm), new int[] {R.id.btn_deleteFile, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ClearX, Strings.buildSize(fastStats.getPrivateCameraMediaSize())), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_deleteFile) {
              if (!fastStats.deletePrivateCameraMedia())
                Log.w("Failed to delete some emoji sets");
              reloadFastStats();
            }
            return true;
          });
        }
        break;
      }
      case R.id.btn_emoji: {
        if (fastStats != null) {
          showOptions(Lang.getString(R.string.EmojiSetsInfo), new int[] {R.id.btn_deleteFile, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ClearX, Strings.buildSize(fastStats.getEmojiUnusedSize())), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_deleteFile) {
              if (!fastStats.deleteEmoji())
                Log.w("Failed to delete some emoji sets");
              reloadFastStats();
            }
            return true;
          });
        }
        break;
      }
      case R.id.btn_lottie: {
        if (fastStats != null) {
          showOptions(Lang.getString(R.string.AnimatedStickersInfo), new int[] {R.id.btn_deleteFile, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ClearX, Strings.buildSize(fastStats.getLottieSize())), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_deleteFile) {
              if (!fastStats.deleteLottieFiles())
                Log.w("Failed to delete some emoji sets");
              reloadFastStats();
            }
            return true;
          });
        }
        break;
      }
      case R.id.btn_logsSize: {
        if (fastStats == null)
          return;
        if (Settings.instance().hasLogsEnabled()) {
          showSettings(new SettingsWrapBuilder(R.id.btn_logsSize).addHeaderItem(Lang.getString(R.string.AppLogsClear)).setSaveColorId(R.id.theme_color_textNegative).setSaveStr(Lang.getString(R.string.ClearX, Strings.buildSize(fastStats.getLogsSize()))).setRawItems(new ListItem[] {new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_tdlib_resetLogSettings, 0, R.string.AppLogsDisable, false)}).setIntDelegate((id, result) -> {
            boolean reset = result.get(R.id.btn_tdlib_resetLogSettings) == R.id.btn_tdlib_resetLogSettings;
            if (reset) {
              Settings.instance().disableAllLogs();
            }
            Settings.instance().deleteAllLogs(true, this::reloadFastStats);
          }));
        } else {
          showOptions(Lang.getString(R.string.AppLogsClear), new int[] {R.id.btn_deleteFile, R.id.btn_cancel}, new String[] {Lang.getString(R.string.ClearX, Strings.buildSize(fastStats.getLogsSize())), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
            if (id == R.id.btn_deleteFile) {
              Settings.instance().deleteAllLogs(true, this::reloadFastStats);
            }
            return true;
          });
        }
        break;
      }
      case R.id.btn_keepMedia: {
        showOptions(new int[] {R.id.btn_keepMedia_3days, R.id.btn_keepMedia_1week, R.id.btn_keepMedia_1month, R.id.btn_keepMedia_forever}, new String[] {Lang.plural(R.string.xDays, 3), Lang.plural(R.string.xWeeks, 1), Lang.plural(R.string.xMonths, 1), Lang.getString(R.string.KeepMediaForever)}, (itemView, id) -> {
          switch (id) {
            case R.id.btn_keepMedia_3days: {
              setKeepMedia(60 * 60 * 24 * 3, true);
              break;
            }
            case R.id.btn_keepMedia_1week: {
              setKeepMedia(60 * 60 * 24 * 7, true);
              break;
            }
            case R.id.btn_keepMedia_1month: {
              setKeepMedia(60 * 60 * 24 * 30, true);
              break;
            }
            case R.id.btn_keepMedia_forever: {
              setKeepMedia(0, true);
              break;
            }
          }
          return true;
        });
        break;
      }
      case R.id.btn_clearCache:
      case R.id.btn_otherChats:
      case R.id.btn_otherFiles: {
        if (isBusyGlobally)
          return;

        if (stats != null && !isCleaningUp) {
          final int id = v.getId();
          TGStorageStats.Entry entry = id == R.id.btn_clearCache ? stats.getTotalFilesEntry() : id == R.id.btn_otherChats ? stats.getOtherChatsEntry() : stats.getOtherFilesEntry();
          showClearSettings(R.id.btn_otherChats, entry);
        }
        break;
      }
      case R.id.chat: {
        if (isBusyGlobally)
          return;

        ListItem item = (ListItem) v.getTag();
        if (item != null && !isCleaningUp) {
          TGStorageStats.Entry entry = (TGStorageStats.Entry) item.getData();
          if (entry != null) {
            showClearSettings(R.id.chat, entry);
          }
        }
        break;
      }
    }
  }

  private void showClearSettings (int id, final TGStorageStats.Entry entry) {
    ArrayList<ListItem> items = new ArrayList<>();

    SparseLongArray sizes = entry.getSizes();
    SparseIntArray counts = entry.getCounts();

    long selectedSize = 0l;

    final int count = sizes.size();
    for (int i = 0; i < count; i++) {
      int entryKey = sizes.keyAt(i);
      long entrySize = sizes.valueAt(i);
      int entryCount = counts.keyAt(i) == entryKey ? counts.valueAt(i) : counts.get(entryKey);

      if (entrySize == 0) {
        continue;
      }

      final int entryKeyId;
      final String entryName;
      switch (entryKey) {
        case TGStorageStats.FILE_TYPE_ANIMATIONS: {
          entryKeyId = R.id.btn_gifs;
          entryName = Lang.plural(R.string.xGIFs, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.GIFs);
          break;
        }
        case TGStorageStats.FILE_TYPE_DOCUMENTS: {
          entryKeyId = R.id.btn_files;
          entryName = Lang.plural(R.string.xFiles, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.Files);
          break;
        }
        case TGStorageStats.FILE_TYPE_MUSIC: {
          entryKeyId = R.id.btn_music;
          entryName = Lang.plural(R.string.xMusicFiles, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.Music);
          break;
        }
        case TGStorageStats.FILE_TYPE_PHOTOS: {
          entryKeyId = R.id.btn_photos;
          entryName = Lang.plural(R.string.xPhotos, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.Photos);
          break;
        }
        case TGStorageStats.FILE_TYPE_VIDEOS: {
          entryKeyId = R.id.btn_video;
          entryName = Lang.plural(R.string.xVideos, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.Videos);
          break;
        }
        case TGStorageStats.FILE_TYPE_PROFILE_PHOTOS: {
          entryKeyId = R.id.btn_profilePhotos;
          entryName = Lang.plural(R.string.xProfilePhotos, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.ProfilePhotos);
          break;
        }
        case TGStorageStats.FILE_TYPE_STICKERS: {
          entryKeyId = R.id.btn_stickers;
          entryName = Lang.plural(R.string.xStickers, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.Stickers);
          break;
        }
        case TGStorageStats.FILE_TYPE_VOICE: {
          entryKeyId = R.id.btn_voice;
          entryName = Lang.plural(R.string.xVoiceMessages, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.VoiceMessages);
          break;
        }
        case TGStorageStats.FILE_TYPE_VIDEO_MESSAGE: {
          entryKeyId = R.id.btn_videoNote;
          entryName = Lang.plural(R.string.xVideoMessages, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.VideoMessages);
          break;
        }
        case TGStorageStats.FILE_TYPE_SECRET: {
          entryKeyId = R.id.btn_secretFiles;
          entryName = Lang.getString(R.string.SecretFiles);
          break;
        }
        case TGStorageStats.FILE_TYPE_THUMBNAILS: {
          entryKeyId = R.id.btn_thumbnails;
          entryName = Lang.plural(R.string.xThumbnails, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.Thumbnails);
          break;
        }
        case TGStorageStats.FILE_TYPE_OTHER: {
          entryKeyId = R.id.btn_other;
          entryName = Lang.getString(R.string.Other);
          break;
        }
        case TGStorageStats.FILE_TYPE_WALLPAPER: {
          entryKeyId = R.id.btn_wallpaper;
          entryName = Lang.plural(R.string.xWallpapers, entryCount); // entryCount != 0 ?  : Lang.getString(R.string.Wallpapers);
          break;
        }
        default: {
          continue;
        }
      }

      boolean isSelected = !TGStorageStats.isShouldKeepType(entryKey);

      if (isSelected) {
        selectedSize += entrySize;
      }

      ListItem item = new ListItem(ListItem.TYPE_CHECKBOX_OPTION_DOUBLE_LINE, entryKeyId, 0, entryName, isSelected);
      item.setStringValue(Strings.buildSize(entrySize));
      item.setIntValue(entryKey);
      item.setLongId(entrySize);
      items.add(item);
    }

    Collections.sort(items, (o1, o2) -> {
      boolean s1 = o1.isSelected();
      boolean s2 = o2.isSelected();
      long b1 = o1.getLongId();
      long b2 = o2.getLongId();
      int k1 = o1.getIntValue();
      int k2 = o2.getIntValue();
      return s1 != s2 ? (s1 ? -1 : 1) : b1 != b2 ? (b1 < b2 ? 1 : -1) : (k1 < k2 ? -1 : k1 > k2 ? 1 : 0);
    });

    ListItem headerItem;
    if (entry.getId() != 0) {
      headerItem = new ListItem(ListItem.TYPE_INFO, 0, 0, entry.isSecret() ? Lang.lowercase(Lang.getString(R.string.ChatTitleSecretChat, entry.getTitle())) : entry.getTitle(), false);
    } else {
      headerItem = null;
    }

    ListItem[] array = new ListItem[items.size()];
    items.toArray(array);

    showSettings(new SettingsWrapBuilder(id).addHeaderItem(headerItem).setRawItems(array).setIntDelegate((id1, result) -> optimizeStorage(result, entry)).setOnSettingItemClick((view, settingsId, item, doneButton, settingsAdapter) -> {
      String size = Strings.buildSize(measureTotal(settingsAdapter.getCheckIntResults(), entry), false);
      doneButton.setText(Lang.getString(R.string.ClearX, size).toUpperCase());
    }).setSaveStr(Lang.getString(R.string.ClearX, Strings.buildSize(selectedSize, false)))
      .setSaveColorId(R.id.theme_color_textNegative).setAllowResize(count >= 5));
  }

  private void optimizeStorage (SparseIntArray result, TGStorageStats.Entry entry) {
    if (isCleaningUp || result.size() == 0) {
      return;
    }

    ArrayList<TdApi.FileType> fileTypes = new ArrayList<>();

    final int count = result.size();
    for (int i = 0; i < count; i++) {
      int id = result.keyAt(i);
      if (result.valueAt(i) != 0) {
        int key = convertIdToKey(id);
        if (key != -1) {
          switch (key) {
            case TGStorageStats.FILE_TYPE_ANIMATIONS:
              fileTypes.add(new TdApi.FileTypeAnimation());
              break;
            case TGStorageStats.FILE_TYPE_DOCUMENTS:
              fileTypes.add(new TdApi.FileTypeDocument());
              break;
            case TGStorageStats.FILE_TYPE_MUSIC:
              fileTypes.add(new TdApi.FileTypeAudio());
              break;
            case TGStorageStats.FILE_TYPE_PHOTOS:
              fileTypes.add(new TdApi.FileTypePhoto());
              break;
            case TGStorageStats.FILE_TYPE_VIDEOS:
              fileTypes.add(new TdApi.FileTypeVideo());
              break;
            case TGStorageStats.FILE_TYPE_PROFILE_PHOTOS:
              fileTypes.add(new TdApi.FileTypeProfilePhoto());
              break;
            case TGStorageStats.FILE_TYPE_STICKERS:
              fileTypes.add(new TdApi.FileTypeSticker());
              break;
            case TGStorageStats.FILE_TYPE_VOICE:
              fileTypes.add(new TdApi.FileTypeVoiceNote());
              break;
            case TGStorageStats.FILE_TYPE_VIDEO_MESSAGE:
              fileTypes.add(new TdApi.FileTypeVideoNote());
              break;
            case TGStorageStats.FILE_TYPE_SECRET:
              fileTypes.add(new TdApi.FileTypeSecret());
              break;
            case TGStorageStats.FILE_TYPE_THUMBNAILS:
              fileTypes.add(new TdApi.FileTypeThumbnail());
              break;
            case TGStorageStats.FILE_TYPE_WALLPAPER:
              fileTypes.add(new TdApi.FileTypeWallpaper());
              break;
            case TGStorageStats.FILE_TYPE_OTHER:
              fileTypes.add(new TdApi.FileTypeUnknown());
              break;
            default: {
              throw new IllegalArgumentException("key == " + key);
            }
          }
        }
      }
    }

    TdApi.FileType[] fileTypesRaw = new TdApi.FileType[fileTypes.size()];
    fileTypes.toArray(fileTypesRaw);

    long[] chatIds = entry.getTargetChatIds();
    long[] excludeChatIds = entry.getExcludeChatIds();

    if (setIsBusy(true, chatIds, entry)) {
      getRecyclerView().setItemAnimator(null);
      ImageLoader.instance().clear(tdlib.id(), false);
      needShowAllChats = false;
      tdlib.client().send(new TdApi.OptimizeStorage(0, 0, 0, 0, fileTypesRaw, chatIds, excludeChatIds, false, 0), stats -> {
        processStorageStats(stats, true);
      });
    }
  }

  private static final int VIEW_TYPE = ListItem.TYPE_VALUED_SETTING_COMPACT;

  private boolean needLogEntry () {
    return Settings.instance().hasLogsEnabled() || (fastStats != null && fastStats.getLogsSize() > 0);
  }

  private void checkLogEntry () {
    checkEntry(R.id.btn_logsSize, R.string.LogFiles, needLogEntry());
    checkShortPolling();
  }

  private boolean needEmojiEntry () {
    return fastStats != null && fastStats.getEmojiSize() > 0;
  }

  private void checkEmojiEntry () {
    checkEntry(R.id.btn_emoji, R.string.EmojiSets, needEmojiEntry());
  }

  private boolean needCameraEntry () {
    return fastStats != null && fastStats.getPrivateCameraMediaSize() > 0;
  }

  private void checkCameraEntry () {
    checkEntry(R.id.btn_camera, R.string.InAppCameraCache, needCameraEntry());
  }

  private boolean needPaintsEntry () {
    return fastStats != null && fastStats.getPaintsSize() > 0;
  }

  private void checkPaintEntry () {
    checkEntry(R.id.btn_paint, R.string.Paints, needPaintsEntry());
  }

  private boolean needLottieEntry () {
    return fastStats != null && fastStats.getLottieSize() > 0;
  }

  private void checkLottieEntry () {
    checkEntry(R.id.btn_lottie, R.string.AnimatedStickers, needLottieEntry());
  }

  private boolean needJunkEntry () {
    return fastStats != null && fastStats.getJunkSize() > 0;
  }

  private void checkJunkEntry () {
    checkEntry(R.id.btn_junk, R.string.JunkFiles, needJunkEntry());
  }

  private void checkEntry (int viewId, int viewString, boolean needEntry) {
    int index = adapter.indexOfViewById(viewId);
    boolean hadEntry = index != -1;
    if (hadEntry != needEntry) {
      if (needEntry) {
        index = adapter.indexOfViewById(R.id.btn_localDatabase);
        if (index == -1)
          throw new AssertionError();
        adapter.getItems().add(index, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.getItems().add(index, new ListItem(VIEW_TYPE, viewId, 0, viewString));
        adapter.notifyItemRangeInserted(index, 2);
        headerSize += 2;
      } else {
        adapter.removeRange(index, 2);
        headerSize -= 2;
      }
    } else if (needEntry) {
      adapter.updateValuedSettingByPosition(index);
    }
  }

  private static final int CHAT_ID_LIMIT = 15;

  private boolean setIsBusy (boolean isCleaningUp, long[] busyChatIds, TGStorageStats.Entry busyEntry) {
    if (this.isCleaningUp != isCleaningUp) {
      this.isCleaningUp = isCleaningUp;

      adapter.updateValuedSettingById(R.id.btn_localDatabase);
      adapter.updateValuedSettingById(R.id.btn_clearCache);
      adapter.updateValuedSettingById(R.id.btn_logsSize);

      long[] prevBusyChatIds = this.busyChatIds;
      this.busyChatIds = busyChatIds;
      this.busyEntry = busyEntry;

      if (prevBusyChatIds != null) {
        for (long busyChatId : prevBusyChatIds) {
          if (busyChatId != 0) {
            adapter.updateValuedSettingByLongId(busyChatId);
          } else {
            adapter.updateValuedSettingById(R.id.btn_otherChats);
          }
        }
      }
      if (busyChatIds != null) {
        for (long busyChatId : busyChatIds) {
          if (busyChatId != 0) {
            adapter.updateValuedSettingByLongId(busyChatId);
          } else {
            adapter.updateValuedSettingById(R.id.btn_otherChats);
          }
        }
      }

      return true;
    }
    return false;
  }

  private static int convertIdToKey (@IdRes int id) {
    switch (id) {
      case R.id.btn_gifs: {
        return TGStorageStats.FILE_TYPE_ANIMATIONS;
      }
      case R.id.btn_files: {
        return TGStorageStats.FILE_TYPE_DOCUMENTS;
      }
      case R.id.btn_music: {
        return TGStorageStats.FILE_TYPE_MUSIC;
      }
      case R.id.btn_photos: {
        return TGStorageStats.FILE_TYPE_PHOTOS;
      }
      case R.id.btn_video: {
        return TGStorageStats.FILE_TYPE_VIDEOS;
      }
      case R.id.btn_profilePhotos: {
        return TGStorageStats.FILE_TYPE_PROFILE_PHOTOS;
      }
      case R.id.btn_thumbnails: {
        return TGStorageStats.FILE_TYPE_THUMBNAILS;
      }
      case R.id.btn_stickers: {
        return TGStorageStats.FILE_TYPE_STICKERS;
      }
      case R.id.btn_voice: {
        return TGStorageStats.FILE_TYPE_VOICE;
      }
      case R.id.btn_videoNote: {
        return TGStorageStats.FILE_TYPE_VIDEO_MESSAGE;
      }
      case R.id.btn_secretFiles: {
        return TGStorageStats.FILE_TYPE_SECRET;
      }
      case R.id.btn_other: {
        return TGStorageStats.FILE_TYPE_OTHER;
      }
      case R.id.btn_wallpaper: {
        return TGStorageStats.FILE_TYPE_WALLPAPER;
      }
    }
    return -1;
  }

  private static long measureTotal (SparseIntArray results, TGStorageStats.Entry entry) {
    long totalSize = 0;
    final int size = results.size();
    for (int i = 0; i < size; i++) {
      int id = results.keyAt(i);
      int key = convertIdToKey(id);
      if (key != -1 && results.valueAt(i) != 0) {
        long entrySize = entry.getSizes().get(key, -1);
        if (entrySize != -1) {
          totalSize += entrySize;
        }
      }
    }
    return totalSize;
  }

  @Override
  protected int getMenuId () {
    return tdlib != null ? R.id.menu_more : 0;
  }

  private boolean canEraseAllData () {
    return stats != null && !isBusyGlobally && !isCleaningUp;
  }

  @Override
  protected void openMoreMenu () {
    if (canEraseAllData()) {
      showMore(new int[]{R.id.btn_resetLocalData}, new String[] {Lang.getString(R.string.EraseDatabase)}, 0);
    } else {
      UI.showToast(R.string.EraseDatabaseWait, Toast.LENGTH_SHORT);
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    switch (id) {
      case R.id.btn_resetLocalData: {
        if (canEraseAllData()) {
          tdlib.ui().eraseLocalData(this, true, new TdlibUi.EraseCallback() {
            @Override
            public void onPrepareEraseData() {
              navigationController().getStack().destroyAllExceptLast();
              setIsBusyGlobally(true);
              setArguments(null);
            }

            @Override
            public void onEraseDataCompleted() {
              MainController c = new MainController(context, tdlib);
              onDataErased();
              c.getValue();
              navigationController().insertController(c, 0);
            }
          });
        } else {
          UI.showToast(R.string.EraseDatabaseWait, Toast.LENGTH_SHORT);
        }
        break;
      }
    }
  }

  private TGStorageStats stats;

  private void setStorageStats (TGStorageStats stats, boolean firstRequest) {
    TGStorageStats oldStats = this.stats;

    setIsBusy(false, null, null);

    this.stats = stats;
    executeScheduledAnimation();
    adapter.updateValuedSettingById(R.id.btn_clearCache);
    adapter.updateValuedSettingById(R.id.btn_localDatabase);

    if (firstRequest) {
      getStats(false);
      int i = adapter.indexOfViewById(R.id.btn_clearCacheHint);
      if (i != -1) {
        if (adapter.getItem(i).setStringIfChanged(R.string.ClearCacheHint2)) {
          adapter.notifyItemChanged(i);
        }
      } else {
        adapter.addItem(adapter.getItemCount(), new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_clearCacheHint, 0, R.string.ClearCacheHint2));
      }
    } else {
      adapter.removeItemById(R.id.btn_clearCacheHint);
    }

    final ArrayList<TGStorageStats.Entry> chatsList = stats.getChatList();
    final List<ListItem> items = adapter.getItems();

    boolean hasRemovedSomeItems = false;
    final int oldItemCount = items.size();

    if (oldStats != null && headerSize < items.size()) {
      for (int i = oldItemCount - 1; i >= headerSize; i--) {
        items.remove(i);
        hasRemovedSomeItems = true;
      }
    }

    if (chatsList.isEmpty()) {
      if (hasRemovedSomeItems) {
        adapter.notifyItemRangeRemoved(headerSize, oldItemCount - headerSize);
      }
      return;
    }

    final TGStorageStats.Entry otherChats = stats.getOtherChatsEntry();
    final TGStorageStats.Entry otherFiles = stats.getOtherFilesEntry();

    boolean first = true;

    int shownChatCount = 0;
    for (TGStorageStats.Entry entry : chatsList) {
      if (entry.isEmpty()) {
        continue;
      }
      if (first) {
        items.add(new ListItem(ListItem.TYPE_HEADER, R.id.chatsStorageUsageList, 0, R.string.Chats));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        first = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR));
      }
      items.add(new ListItem(ListItem.TYPE_DOUBLE_TEXTVIEW_ROUNDED, R.id.chat, 0, 0).setLongId(entry.getId()).setData(entry));
      shownChatCount++;
    }
    if (!otherChats.isEmpty()) {
      if (first) {
        items.add(new ListItem(ListItem.TYPE_HEADER, R.id.chatsStorageUsageList, 0, R.string.Chats));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        first = false;
      } else {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(VIEW_TYPE, R.id.btn_otherChats, 0, chatsList.isEmpty() ? R.string.Chats : R.string.OtherChats));
      if (!needShowAllChats && shownChatCount == CHAT_ID_LIMIT) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_showOtherChats, 0, R.string.ShowOtherChats));
      }
    }

    if (!first) {
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    }

    /*if (!otherFiles.isEmpty()) {
      items.add(new SettingItem(SettingItem.TYPE_SHADOW_TOP));
      items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
      items.add(new SettingItem(SettingItem.TYPE_VALUED_SETTING, R.id.btn_otherFiles, 0, R.string.OtherFiles));
      items.add(new SettingItem(SettingItem.TYPE_SHADOW_BOTTOM));
    }*/

    if (oldStats != null) {
      final int newItemCount = items.size();
      if (oldItemCount == newItemCount) {
        adapter.notifyItemRangeChanged(headerSize, newItemCount - headerSize);
      } else {
        adapter.notifyItemRangeChanged(headerSize, Math.min(newItemCount - headerSize, oldItemCount - headerSize));
        if (newItemCount > oldItemCount) {
          adapter.notifyItemRangeInserted(oldItemCount, newItemCount - oldItemCount);
        } else {
          adapter.notifyItemRangeRemoved(newItemCount, oldItemCount - newItemCount);
        }
      }
    } else {
      adapter.notifyItemRangeInserted(headerSize, items.size() - headerSize);
    }
  }

  private void processStorageStats (TdApi.Object object, boolean firstRequest) {
    if (object.getConstructor() != TdApi.StorageStatistics.CONSTRUCTOR) {
      this.onResult(object);
      return;
    }
    final TGStorageStats stats = new TGStorageStats(tdlib, (TdApi.StorageStatistics) object);
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        boolean wasCleaning = isCleaningUp;
        setStorageStats(stats, firstRequest);
        if (wasCleaning) {
          tdlib.client().send(new TdApi.GetStorageStatisticsFast(), SettingsCacheController.this);
        }
      }
    });
  }

  @Override
  public void onResult (TdApi.Object object) {
    switch (object.getConstructor()) {
      case TdApi.StorageStatistics.CONSTRUCTOR: {
        processStorageStats(object, false);
        break;
      }
      case TdApi.StorageStatisticsFast.CONSTRUCTOR: {
        final TGStorageStatsFast stats = new TGStorageStatsFast((TdApi.StorageStatisticsFast) object, fastStats);
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            SettingsDataController parent = getArguments();
            if (parent != null) {
              parent.setStorageStats(stats);
            }
            setStorageStatsFast(stats);
          }
        });
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        UI.showError(object);
        break;
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    TGLegacyManager.instance().removeEmojiListener(this);
    TGLegacyManager.instance().removeEmojiListener(adapter);
    // TODO TDLib cancel calculation
  }

  @Override
  public void onEmojiUpdated (boolean isPackSwitch) {
    if (headerCell != null) {
      headerCell.invalidate();
    }
  }
}
