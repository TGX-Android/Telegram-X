package org.thunderdog.challegram.navigation;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.thunderdog.challegram.ui.SettingsAdapter;
import org.thunderdog.challegram.widget.PopupLayout;

/**
 * Date: 3/24/18
 * Author: default
 */
public class SettingsWrap {
  public interface OnActionButtonClick {
    boolean onActionButtonClick (SettingsWrap wrap, View view, boolean isCancel);
  }

  public SettingsAdapter adapter;
  public RecyclerView recyclerView;
  public TextView doneButton;
  public TextView cancelButton;
  public PopupLayout window;
}
