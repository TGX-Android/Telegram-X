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
 * File created on 07/11/2017
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class SettingsLogFilesController extends RecyclerViewController<SettingsLogFilesController.Arguments> implements
  View.OnClickListener, Log.OutputListener {
  public static class Arguments {
    Log.LogFiles currentFiles;
    public Arguments (Log.LogFiles currentFiles) {
      this.currentFiles = currentFiles;
    }
  }

  public SettingsLogFilesController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_logs;
  }

  @Override
  public CharSequence getName () {
    return "Application Logs";
  }

  private SettingsAdapter adapter;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_file: {
            File file = (File) item.getData();
            view.setData(Lang.getFileTimestamp(file.lastModified(), TimeUnit.MILLISECONDS, file.length()));
            break;
          }
        }
      }
    };
    if (getArguments() != null && getArguments().currentFiles != null && !getArguments().currentFiles.isEmpty()) {
      setFiles(getArguments().currentFiles);
    } else {
      buildCells();
      getFiles();
    }
    recyclerView.setAdapter(adapter);
    Log.addOutputListener(this);
  }

  @Override
  public void destroy () {
    super.destroy();
    Log.removeOutputListener(this);
  }

  private void getFiles () {
    Log.getLogFiles(result -> {
      if (!isDestroyed()) {
        UI.post(() -> {
          if (!isDestroyed()) {
            setFiles(result);
          }
        });
      }
    });
  }

  @Override
  public void onLogOutput (int tag, int level, String message, @Nullable Throwable t) {
    UI.post(() -> {
      if (!isDestroyed()) {
        if (adapter.getItems() != null) {
          int position = 0;
          for (ListItem item : adapter.getItems()) {
            if (item.getId() == R.id.btn_file) {
              adapter.updateValuedSettingByPosition(position);
            }
            position++;
          }
        }
        if (files == null || files.isEmpty()) {
          getFiles();
        }
      }
    });
  }

  @Override
  public void onLogFilesAltered () { }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_file: {
        final ListItem item = (ListItem) v.getTag();
        final File file = (File) item.getData();
        showOptions(file.getName() + " (" + Strings.buildSize(file.length()) + ")", new int[]{R.id.btn_open, R.id.btn_share, R.id.btn_delete}, new String[]{"View", "Share", "Delete"}, new int[]{OPTION_COLOR_NORMAL, OPTION_COLOR_NORMAL, OPTION_COLOR_RED}, new int[]{R.drawable.baseline_visibility_24, R.drawable.baseline_forward_24, R.drawable.baseline_delete_24}, (itemView, id) -> {
          switch (id) {
            case R.id.btn_open: {
              TextController c = new TextController(context, tdlib);
              c.setArguments(TextController.Arguments.fromFile(file.getName(), file.getPath(), "text/plain"));
              navigateTo(c);
              break;
            }
            case R.id.btn_share: {
              ShareController c = new ShareController(context, tdlib);
              c.setArguments(new ShareController.Args(file, "text/plain"));
              c.show();
              break;
            }
            case R.id.btn_delete: {
              final long size = file.length();
              final boolean isCrash = file.getName().startsWith(Log.CRASH_PREFIX);
              if (Log.deleteFile(file)) {
                UI.showToast("OK. Freed " + Strings.buildSize(size), Toast.LENGTH_SHORT);
                removeFile(file, size, isCrash);
              } else {
                UI.showToast("Failed", Toast.LENGTH_SHORT);
              }
              break;
            }
          }
          return true;
        });
        break;
      }
    }
  }

  private void removeFile (File file, long size, boolean isCrash) {
    if (files == null) {
      return;
    }
    int i = files.files.indexOf(file);
    if (i != -1) {
      files.totalSize -= size;
      if (isCrash) {
        files.crashesCount--;
      } else {
        files.logsCount--;
      }
      removeFileByPosition(i);
    }
  }

  private void removeFileByPosition (int position) {
    files.files.remove(position);
    if (files.files.isEmpty()) {
      buildCells();
    } else if (position == 0) {
      adapter.getItems().remove(0);
      adapter.getItems().remove(0);
      adapter.notifyItemRangeRemoved(0, 2);
    } else if (position == files.files.size()) { // TODO check removal of the last log in the list
      final int count = adapter.getItems().size();
      adapter.getItems().remove(count - 2);
      adapter.getItems().remove(count - 3);
      adapter.notifyItemRangeRemoved(count - 3, 2);
    } else {
      adapter.getItems().remove(position * 2 + 1);
      adapter.getItems().remove(position * 2);
      adapter.notifyItemRangeRemoved(position * 2, 2);
    }
  }

  private Log.LogFiles files;
  private boolean filesReceived;

  private void setFiles (Log.LogFiles files) {
    this.files = files;
    this.filesReceived = true;
    buildCells();
  }

  private void buildCells () {
    ArrayList<ListItem> items = new ArrayList<>();
    if (filesReceived) {
      if (files == null || files.isEmpty()) {
        items.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, "Application Logs are empty", false));
      } else {
        boolean first = true;
        for (File file : files.files) {
          if (first) {
            first = false;
          } else {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          }
          items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_file, 0, file.getName(), false).setData(file));
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      }
    }
    adapter.setItems(items, false);
  }
}
