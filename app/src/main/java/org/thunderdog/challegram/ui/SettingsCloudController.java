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
 * File created on 20/10/2019
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.os.CancellationSignal;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.loader.ImageReceiver;
import org.thunderdog.challegram.navigation.NavigationController;
import org.thunderdog.challegram.telegram.FileUpdateListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.DrawModifier;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.RadioView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.lambda.RunnableData;
import me.vkryl.td.Td;

public abstract class SettingsCloudController<T extends Settings.CloudSetting> extends RecyclerViewController<SettingsCloudController.Args<T>> implements View.OnClickListener, FileUpdateListener, TdlibFilesManager.FileListener {
  private final long tutorialFlag;
  private final @StringRes int tutorialStringRes, currentStringRes, builtinStringRes, installedStringRes, updateStringRes, installingStringRes;

  public SettingsCloudController (Context context, Tdlib tdlib, long tutorialFlag, int tutorialStringRes, int currentStringRes, int builtinStringRes, int installedStringRes, int updateStringRes, int installingStringRes) {
    super(context, tdlib);
    this.tutorialFlag = tutorialFlag;
    this.tutorialStringRes = tutorialStringRes;
    this.currentStringRes = currentStringRes;
    this.builtinStringRes = builtinStringRes;
    this.installedStringRes = installedStringRes;
    this.updateStringRes = updateStringRes;
    this.installingStringRes = installingStringRes;
  }

  public static class Args <T extends Settings.CloudSetting> {
    T applySetting;
    SettingsThemeController parentController;
    SettingsStickersAndEmojiController parentControllerStickers;

    public Args (T applySetting) {
      this.applySetting = applySetting;
    }

    public Args (SettingsThemeController parentController) {
      this.parentController = parentController;
    }

    public Args (SettingsStickersAndEmojiController parentController) {
      this.parentControllerStickers = parentController;
    }
  }

  protected final SettingsThemeController getThemeController () {
    return getArguments() != null ? getArguments().parentController : null;
  }

  protected final SettingsStickersAndEmojiController getStickersAndEmojiController () {
    return getArguments() != null ? getArguments().parentControllerStickers : null;
  }

  protected abstract T getCurrentSetting ();
  protected abstract void getSettings (RunnableData<List<T>> callback);
  protected abstract void applySetting (T setting);

  private SettingsAdapter adapter;
  private List<T> settings;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this, this, this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        view.setDrawModifier(item.getDrawModifier());
        final int itemId = item.getId();
        if (itemId == R.id.btn_settings) {
          T setting = (T) item.getData();
          T currentSetting = getCurrentSetting();
          boolean isCurrent = setting.equals(currentSetting);
          boolean isPending = installingSetting != null && installingSetting.equals(setting);
          if (isCurrent) {
            view.setData(currentStringRes);
          } else if (isPending) {
            view.setData(Lang.getDownloadStatus(isInstalling ? null : setting.getFile(), installingStringRes, false));
          } else {
            int installState = setting.getInstallState(true);
            boolean isInstalled = installState == Settings.CloudSetting.STATE_INSTALLED;
            view.setData(Lang.getDownloadStatus(isInstalled ? null : setting.getFile(), setting.isBuiltIn() ? builtinStringRes : installState == Settings.CloudSetting.STATE_UPDATE_NEEDED ? updateStringRes : installedStringRes, !isInstalled));
          }
          boolean isEffective = installingSetting == null ? isCurrent : isPending;
          RadioView radioView = view.findRadioView();
          if (!isUpdate || isEffective) {
            radioView.setActive(isCurrent, isUpdate);
          }
          radioView.setChecked(isEffective, isUpdate);
          view.setDataColorId(isCurrent && isEffective ? ColorId.textNeutral : 0);

          view.getReceiver().requestFile(setting.getPreviewFile());
        }
      }
    };
    getSettings(settings -> tdlib.ui().post(() -> {
      if (isDestroyed())
        return;
      this.settings = settings;
      buildCells();
      T applySetting = getArguments() != null ? getArguments().applySetting : null;
      if (applySetting != null) {
        boolean found = false;
        for (T newSetting : settings) {
          if (newSetting.equals(applySetting)) {
            selectSetting(newSetting);
            found = true;
            break;
          }
        }
        if (!found) {
          UI.showToast(R.string.EmojiUpdateUnavailable, Toast.LENGTH_SHORT);
        }
      }
    }));
    recyclerView.setAdapter(adapter);
  }

  private void buildCells () {
    if (settings == null)
      return;
    List<ListItem> items = new ArrayList<>();

    for (T setting : settings) {
      if (!items.isEmpty())
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO, R.id.btn_settings, 0, setting.getDisplayName(), false).setData(setting).setDrawModifier(new DrawModifier() {
        @Override
        public void afterDraw (View view, Canvas c) {
          ImageReceiver receiver = ((SettingView) view).getReceiver();
          int right = Screen.dp(18f);
          int size = Screen.dp(64f) - Screen.dp(12f) * 2;
          receiver.setBounds(view.getMeasuredWidth() - right - size, view.getMeasuredHeight() / 2 - size / 2, view.getMeasuredWidth() - right, view.getMeasuredHeight() / 2 + size / 2);
          receiver.draw(c);
        }
      }));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    // TODO add custom settings

    adapter.setItems(items, false);
    executeScheduledAnimation();
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return settings == null;
  }

  private boolean isInstalling;
  @Nullable
  private T installingSetting;

  @Override
  public boolean canSlideBackFrom (NavigationController navigationController, float x, float y) {
    return installingSetting == null;
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (installingSetting != null) {
      showUnsavedChangesPromptBeforeLeaving(() -> selectSetting(getCurrentSetting()));
      return true;
    }
    return super.onBackPressed(fromTop);
  }

  private void updateSetting (T setting) {
    int index = 0;
    for (ListItem item : adapter.getItems()) {
      if (item.getId() == R.id.btn_settings && item.getData() != null && item.getData().equals(setting)) {
        adapter.updateValuedSettingByPosition(index);
        return;
      }
      index++;
    }
  }

  protected final void selectSetting (T setting) {
    T currentSetting = getCurrentSetting();
    T prevSelectedSetting = installingSetting != null ? installingSetting : currentSetting;

    if (installingSetting != null) {
      if (installingSetting.equals(setting))
        return;
      tdlib.files().removeCloudReference(installingSetting.getFile(), this);
      installingSetting = null;
    }

    if (!currentSetting.equals(setting)) {
      downloadAndInstall(setting);
    }
    updateSetting(prevSelectedSetting);
    updateSetting(setting);
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_settings) {
      T setting = (T) ((ListItem) v.getTag()).getData();
      if (tutorialFlag != 0 && tutorialStringRes != 0 && Settings.instance().needTutorial(tutorialFlag)) {
        showWarning(Lang.getMarkdownString(this, tutorialStringRes), success -> {
          if (success) {
            Settings.instance().markTutorialAsComplete(tutorialFlag);
            selectSetting(setting);
          }
        });
      } else {
        selectSetting(setting);
      }
    }
  }

  @Override
  public void onUpdateFile (TdApi.UpdateFile updateFile) {
    updateFile(updateFile.file, true);
  }

  @Override
  public void onFileLoadProgress (TdApi.File file) {
    updateFile(file, false);
  }

  @Override
  public void onFileLoadStateChanged (Tdlib tdlib, int fileId, int state, @Nullable TdApi.File downloadedFile) {
    if (downloadedFile != null) {
      updateFile(downloadedFile, false);
    }
  }

  private T findSetting (int fileId) {
    for (ListItem item : adapter.getItems()) {
      if (item.getId() == R.id.btn_settings && item.getData() != null) {
        T setting = (T) item.getData();
        TdApi.File file = setting.getFile();
        if (file != null && file.id == fileId) {
          return setting;
        }
      }
    }
    return null;
  }

  private void updateFile (TdApi.File file, boolean allowSubscribe) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        T setting = findSetting(file.id);
        if (setting == null)
          return;
        Td.copyTo(file, setting.getFile());
        if (TD.isFileLoaded(file)) {
          tdlib.files().unsubscribe(file.id, this);
          tdlib.files().removeCloudReference(setting.getFile(), this);
          if (installingSetting != null && installingSetting.getFile() != null && installingSetting.getFile().id == file.id) {
            install(installingSetting);
          }
        } else if (allowSubscribe) {
          tdlib.files().subscribe(file, this);
        }
        updateSetting(setting);
      }
    });
  }

  private CancellationSignal installationSignal;

  private void downloadAndInstall (T setting) {
    installingSetting = setting;
    isInstalling = false;
    if (installationSignal != null) {
      installationSignal.cancel();
      installationSignal = null;
    }
    if (setting.isBuiltIn() || setting.getFile() == null || setting.isInstalled()) {
      install(setting);
    } else {
      CancellationSignal signal = new CancellationSignal();
      installationSignal = signal;
      TdApi.File file = setting.getFile();
      tdlib.files().isFileLoadedAndExists(file, isLoadedAndExists -> {
        if (!signal.isCanceled()) {
          if (isLoadedAndExists) {
            runOnUiThreadOptional(() -> {
              if (!signal.isCanceled()) {
                install(setting);
              }
            });
          } else {
            tdlib.files().addCloudReference(file, this, false);
          }
        }
      });
    }
  }

  private void install (T setting) {
    isInstalling = true;

    if (setting.isInstalled()) {
      installingSetting = null;
      applySetting(setting);
    } else {
      setting.install(success -> tdlib.ui().post(() -> {
        if (!isDestroyed() && installingSetting != null && installingSetting.equals(setting)) {
          if (success) {
            T prevPack = getCurrentSetting();
            installingSetting = null;
            applySetting(setting);
            updateSetting(prevPack);
            updateSetting(setting);
          } else {
            selectSetting(getCurrentSetting());
          }
        }
      }));
    }
  }
}
