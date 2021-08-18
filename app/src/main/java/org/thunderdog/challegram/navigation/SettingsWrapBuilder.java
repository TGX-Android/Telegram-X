package org.thunderdog.challegram.navigation;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.ui.ListItem;
import org.thunderdog.challegram.widget.PopupLayout;
import org.thunderdog.challegram.widget.TimerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: 3/24/18
 * Author: default
 */
public class SettingsWrapBuilder {
  public final @IdRes
  int id;

  public @Nullable
  ArrayList<ListItem> headerItems;
  public ListItem[] rawItems;

  public @Nullable
  ViewController.SettingsIntDelegate intDelegate;
  public @Nullable
  ViewController.SettingsStringDelegate stringDelegate;


  public @Nullable
  ViewController.OnSettingItemClick onSettingItemClick;

  public @IdRes
  int sizeOptionId;
  public @StringRes
  int sizeStringRes = R.string.MaxSize;
  public int sizeValue;
  public @Nullable
  String[] sizeValues;

  public boolean needSeparators = true, needRootInsets = false;

  public String saveStr = Lang.getString(R.string.Save);
  public int saveColorId = R.id.theme_color_textNeutral;

  public String cancelStr = Lang.getString(R.string.Cancel);
  public int cancelColorId = R.id.theme_color_textNeutral;

  public boolean allowResize = true;

  public @Nullable
  PopupLayout.DismissListener dismissListener;

  public @Nullable
  SettingsWrap.OnActionButtonClick onActionButtonClick;

  public @Nullable
  CustomSettingProcessor settingProcessor;

  public boolean disableToggles;

  public SettingsWrapBuilder (int id) {
    this.id = id;
  }

  public boolean disableFooter;

  public SettingsWrapBuilder setDisableFooter (boolean disableFooter) {
    this.disableFooter = disableFooter;
    return this;
  }

  public TdlibDelegate tdlibDelegate;

  public SettingsWrapBuilder setTdlibDelegate (TdlibDelegate tdlibDelegate) {
    this.tdlibDelegate = tdlibDelegate;
    return this;
  }

  public SettingsWrapBuilder setNeedSeparators (boolean needSeparators) {
    this.needSeparators = needSeparators;
    return this;
  }

  public SettingsWrapBuilder setDisableToggles (boolean disableToggles) {
    this.disableToggles = disableToggles;
    return this;
  }

  public SettingsWrapBuilder setNeedRootInsets (boolean needRootInsets) {
    this.needRootInsets = needRootInsets;
    return this;
  }

  public SettingsWrapBuilder setSettingProcessor (@Nullable CustomSettingProcessor processor) {
    this.settingProcessor = processor;
    return this;
  }

  @Deprecated
  public SettingsWrapBuilder setHeaderItem (@Nullable ListItem headerItem) {
    if (headerItem != null) {
      if (this.headerItems == null) {
        this.headerItems = new ArrayList<>();
      } else {
        this.headerItems.clear();
      }
      this.headerItems.add(headerItem);
    }
    return this;
  }

  public SettingsWrapBuilder addHeaderItem (CharSequence text) {
    if (text != null) {
      addHeaderItem(new ListItem(ListItem.TYPE_INFO, 0, 0, text, false));
    }
    return this;
  }

  public SettingsWrapBuilder addHeaderItem (ListItem item) {
    if (item != null) {
      if (headerItems == null) {
        headerItems = new ArrayList<>();
      }
      headerItems.add(item);
    }
    return this;
  }

  public SettingsWrapBuilder setRawItems (ListItem[] rawItems) {
    this.rawItems = rawItems;
    return this;
  }

  public SettingsWrapBuilder setRawItems (List<ListItem> rawItems) {
    this.rawItems = new ListItem[rawItems.size()];
    rawItems.toArray(this.rawItems);
    return this;
  }

  public SettingsWrapBuilder setIntDelegate (@Nullable ViewController.SettingsIntDelegate intDelegate) {
    this.intDelegate = intDelegate;
    return this;
  }

  public SettingsWrapBuilder setStringDelegate (@Nullable ViewController.SettingsStringDelegate stringDelegate) {
    this.stringDelegate = stringDelegate;
    return this;
  }

  public SettingsWrapBuilder setOnSettingItemClick (@Nullable ViewController.OnSettingItemClick onSettingItemClick) {
    this.onSettingItemClick = onSettingItemClick;
    return this;
  }

  public SettingsWrapBuilder setSizeOptionId (int sizeOptionId) {
    this.sizeOptionId = sizeOptionId;
    return this;
  }

  public SettingsWrapBuilder setSizeStringRes (int sizeStringRes) {
    this.sizeStringRes = sizeStringRes;
    return this;
  }

  public SettingsWrapBuilder setSizeValue (int sizeValue) {
    this.sizeValue = sizeValue;
    return this;
  }

  public SettingsWrapBuilder setSizeValues (@Nullable String[] sizeValues) {
    this.sizeValues = sizeValues;
    return this;
  }

  public SettingsWrapBuilder setOnActionButtonClick (@Nullable SettingsWrap.OnActionButtonClick onActionButtonClick) {
    this.onActionButtonClick = onActionButtonClick;
    return this;
  }

  public SettingsWrapBuilder setSaveStr (@StringRes int stringRes) {
    return setSaveStr(Lang.getString(stringRes));
  }

  public SettingsWrapBuilder setSaveStr (String saveStr) {
    this.saveStr = saveStr;
    return this;
  }

  public SettingsWrapBuilder setCancelStr (@StringRes int stringRes) {
    return setCancelStr(Lang.getString(stringRes));
  }

  public SettingsWrapBuilder setCancelStr (String cancelStr) {
    this.cancelStr = cancelStr;
    return this;
  }

  public SettingsWrapBuilder setCancelColorId (int cancelColorId) {
    this.cancelColorId = cancelColorId;
    return this;
  }

  public SettingsWrapBuilder setSaveColorId (int saveColorId) {
    this.saveColorId = saveColorId;
    return this;
  }

  public SettingsWrapBuilder setAllowResize (boolean allowResize) {
    this.allowResize = allowResize;
    return this;
  }

  public SettingsWrapBuilder setDismissListener (@Nullable PopupLayout.DismissListener dismissListener) {
    this.dismissListener = dismissListener;
    return this;
  }

  public interface CustomSettingProcessor {
    void setValuedSetting (ListItem item, SettingView view, boolean isUpdate);
  }

  public CustomDrawerProcessor drawerProcessor;

  public interface CustomDrawerProcessor {
    void setDrawerItem (ListItem item, DrawerItemView view, TimerView timerView, boolean isUpdate);
  }

  public SettingsWrapBuilder setDrawerProcessor (CustomDrawerProcessor processor) {
    this.drawerProcessor = processor;
    return this;
  }
}
