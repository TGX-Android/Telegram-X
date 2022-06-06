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
 * File created on 14/11/2018
 */
package org.thunderdog.challegram.ui;

import android.content.ClipboardManager;
import android.content.Context;
import android.util.SparseIntArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.SparseArrayCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.navigation.ViewPagerController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorState;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.theme.ThemeInfo;
import org.thunderdog.challegram.theme.ThemeManager;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.CircleButton;
import org.thunderdog.challegram.widget.ViewPager;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;

public class ThemeController extends ViewPagerController<ThemeController.Args> implements Menu, MoreDelegate, ClipboardManager.OnPrimaryClipChangedListener {
  public static class Args {
    private final ThemeInfo theme;
    private Runnable after;
    private SettingsThemeController parent;

    public Args (ThemeInfo theme, Runnable after, @Nullable SettingsThemeController parent) {
      this.theme = theme;
      this.after = after;
      this.parent = parent;
    }
  }

  private final int[] themeSections = {
    R.id.theme_category_main,
    R.id.theme_category_content,
    R.id.theme_category_navigation,
    R.id.theme_category_controls,
    R.id.theme_category_colors,
    R.id.theme_category_chat,
    R.id.theme_category_bubbles,
    R.id.theme_category_iv,
    R.id.theme_category_other,
    R.id.theme_category_internal,
  };

  @Override
  protected int getMenuId () {
    return R.id.menu_theme;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_theme:
        header.addSearchButton(menu, this);
        header.addMoreButton(menu, this);
        break;
    }
  }

  public void highlightColor (@ThemeColorId int colorId) {
    SparseArrayCompat<ViewController<?>> controllers = getAllCachedControllers();
    if (controllers == null)
      return;
    int position = getCurrentPagerItemPosition();
    if (position > 0) {
      ViewController<?> c = controllers.get(position);
      if (c instanceof ThemeListController && ((ThemeListController) c).highlightColor(colorId)) {
        return;
      }
    }
    for (int i = controllers.size() - 1; i >= 0; i--) {
      ViewController<?> c = controllers.valueAt(i);
      if (c instanceof ThemeListController && ((ThemeListController) c).highlightColor(colorId)) {
        setCurrentPagerPosition(controllers.keyAt(i), false);
        return;
      }
    }
  }

  private String currentQuery;
  private void searchColors (String query) {
    if (!StringUtils.isEmpty(query)) {
      query = query.trim().toLowerCase();
    }
    if (StringUtils.isEmpty(query)) {
      query = null;
    }
    if (currentQuery == null && query == null)
      return;
    if (query == null || !query.equals(currentQuery)) {
      currentQuery = query;
      SparseArrayCompat<ViewController<?>> controllers = getAllCachedControllers();
      if (controllers != null) {
        int searchCount = controllers.size();
        SparseIntArray result = new SparseIntArray(searchCount);
        for (int i = 0; i < searchCount; i++) {
          int index = controllers.keyAt(i);
          ViewController<?> c = controllers.valueAt(i);
          if (c instanceof ThemeListController) {
            ((ThemeListController) c).searchColors(query, count -> {
              result.put(index, count);
              if (result.size() == searchCount) {
                int currentPosition = getCurrentPagerItemPosition();
                if (result.get(currentPosition) == 0) {
                  for (int j = 0; j < searchCount; j++) {
                    if (result.valueAt(j) > 0) {
                      setCurrentPagerPosition(currentPosition = result.keyAt(j), true);
                      break;
                    }
                  }
                }
              }
            });
          }
        }
      }
    }
  }

  /*@Override
  public boolean disableHeaderTransformation () {
    return true;
  }*/

  @Override
  protected void onSearchInputChanged (String query) {
    super.onSearchInputChanged(query);
    searchColors(query);
  }

  @Override
  protected boolean useDropPlayer () {
    return false;
  }

  @Override
  public boolean allowThemeChanges () {
    return false;
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_search: {
        Args args = getArgumentsStrict();
        ThemeListController c = new ThemeListController(context, tdlib);
        c.setArguments(new ThemeListController.Args(args.theme, 0).setLookupParent(this));
        navigateTo(c);
        break;
      }
      case R.id.menu_btn_more: {
        int size = openOverlayOnClose ? 4 : 7;

        IntList ids = new IntList(size);
        StringList strings = new StringList(size);

        if (!openOverlayOnClose) {
          ids.append(R.id.btn_edit);
          strings.append(R.string.ThemeEditName);

          ids.append(R.id.btn_wallpaper);
          strings.append(R.string.Wallpaper);
        }

        ids.append(R.id.btn_showAdvanced);
        strings.append(R.string.ThemeAdvanced);

        ids.append(R.id.btn_color);
        strings.append(R.string.ThemeColorFormat);

        ids.append(R.id.btn_share);
        strings.append(Settings.instance().canEditAuthor(ThemeManager.resolveCustomThemeId(getArgumentsStrict().theme.getId())) ? R.string.ThemeExport : R.string.Share);

        if (!openOverlayOnClose) {
          ids.append(R.id.btn_delete);
          strings.append(R.string.ThemeRemove);
        }

        ids.append(R.id.btn_close);
        strings.append(openOverlayOnClose ? R.string.ThemeClose : R.string.ThemeMinimize);

        showMore(ids.get(), strings.get(), 0);
        break;
      }
    }
  }

  @Override
  public void setLockFocusView (View view, boolean showAlways) {
    super.setLockFocusView(view, showAlways);
    ViewController<?> c = getCachedControllerForPosition(getCurrentPagerItemPosition());
    if (c instanceof ThemeListController) {
      ((ThemeListController) c).setDisableSettling(view != null);
    }
  }

  @Override
  public void onThemeColorsChanged (boolean areTemp, ColorState state) {
    super.onThemeColorsChanged(areTemp, state);
    if (!areTemp && state != null && ThemeListController.isMainColor(state.getColorId())) {
      int currentItem = getCurrentPagerItemPosition();
      SparseArrayCompat<ViewController<?>> controllers = getAllCachedControllers();
      if (controllers != null) {
        for (int i = 0; i < controllers.size(); i++) {
          if (currentItem != controllers.keyAt(i)) {
            ViewController<?> c = controllers.valueAt(i);
            if (c instanceof ThemeListController) {
              ((ThemeListController) c).updateColorValue(state.getColorId(), true);
            }
          }
        }
      }
    }
  }

  public void closeOtherEditors (ThemeListController callee, @ThemeColorId int colorId) {
    SparseArrayCompat<ViewController<?>> controllers = getAllCachedControllers();
    if (controllers != null) {
      for (int i = 0; i < controllers.size(); i++) {
        ViewController<?> c = controllers.valueAt(i);
        if (c != callee && c instanceof ThemeListController) {
          ((ThemeListController) c).forceClosePicker(colorId);
        }
      }
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    switch (id) {
      case R.id.btn_edit: {
        openInputAlert(Lang.getString(R.string.ThemeEditName), Lang.getString(R.string.ThemeName), R.string.Save, R.string.Cancel, getArgumentsStrict().theme.getName(), (v, value) -> {
          value = value.trim();
          if (value.isEmpty())
            return false;
          String currentName = getArgumentsStrict().theme.getName();
          if (currentName.equals(value))
            return true;
          Args args = getArgumentsStrict();
          args.theme.setName(value);
          changeName(value);
          Settings.instance().setCustomThemeName(ThemeManager.resolveCustomThemeId(args.theme.getId()), value);
          if (args.parent != null && !args.parent.isDestroyed()) {
            args.parent.updateTheme(args.theme);
          }
          return true;
        }, true);
        break;
      }
      case R.id.btn_wallpaper: {
        openInputAlert(Lang.getString(R.string.ThemeEditWallpaper), Lang.getString(R.string.ThemeWallpaper), R.string.Save, R.string.Cancel, getArgumentsStrict().theme.getWallpaperLink(tdlib), (v, value) -> {
          value = tdlib.getWallpaperData(value.trim());
          String currentWallpaper = getArgumentsStrict().theme.getWallpaper();
          if (StringUtils.equalsOrBothEmpty(currentWallpaper, value))
            return true;
          Args args = getArgumentsStrict();
          args.theme.setWallpaper(value);
          Settings.instance().setCustomThemeWallpaper(ThemeManager.resolveCustomThemeId(args.theme.getId()), value);
          if (args.parent != null && !args.parent.isDestroyed()) {
            args.parent.updateTheme(args.theme);
          }
          tdlib.wallpaper().notifyDefaultWallpaperChanged(args.theme.getId());
          return true;
        }, true);
        break;
      }
      case R.id.btn_showAdvanced: {
        ThemeListController c = new ThemeListController(context, tdlib);
        c.setArguments(new ThemeListController.Args(getArgumentsStrict().theme, R.id.theme_category_settings));
        navigateTo(c);
        break;
      }
      case R.id.btn_close: {
        openOverlayOnClose = !openOverlayOnClose;
        getArgumentsStrict().parent = null;
        navigateBack();
        break;
      }
      case R.id.btn_color: {
        showOptions(new int[] {R.id.btn_colorFormatHex, R.id.btn_colorFormatRgb, R.id.btn_colorFormatHsl}, new String[] {Lang.getString(R.string.ColorTypeHex), Lang.getString(R.string.ColorTypeRGBA), Lang.getString(R.string.ColorTypeHSLA)}, (itemView, optionId) -> {
          int value;
          switch (optionId) {
            case R.id.btn_colorFormatHex:
              value = Settings.COLOR_FORMAT_HEX;
              break;
            case R.id.btn_colorFormatRgb:
              value = Settings.COLOR_FORMAT_RGB;
              break;
            case R.id.btn_colorFormatHsl:
              value = Settings.COLOR_FORMAT_HSL;
              break;
            default:
              return false;
          }
          if (Settings.instance().setColorFormat(value)) {
            SparseArrayCompat<ViewController<?>> array = getAllCachedControllers();
            if (array != null && array.size() > 0) {
              for (int i = 0; i < array.size(); i++) {
                ViewController<?> c = array.valueAt(i);
                if (c instanceof RecyclerViewController) {
                  ((RecyclerViewController<?>) c).getRecyclerView().getAdapter().notifyDataSetChanged();
                }
              }
            }
          }
          return true;
        });
        break;
      }
      case R.id.btn_share: {
        ThemeInfo theme = getArgumentsStrict().theme;
        tdlib.ui().exportTheme(this, theme, !theme.hasParent(), false);
        break;
      }
      case R.id.btn_delete: {
        tdlib.ui().showDeleteThemeConfirm(this, getArgumentsStrict().theme, () -> {
          navigateBack();
          if (getArgumentsStrict().parent != null && !getArgumentsStrict().parent.isDestroyed()) {
            getArgumentsStrict().parent.deleteTheme(getArgumentsStrict().theme, false);
          }
        });
        break;
      }
    }
  }

  @StringRes
  private static int getSectionName (@IdRes int sectionId) {
    switch (sectionId) {
      case R.id.theme_category_main:
        return R.string.ThemeCategoryAccent;
      case R.id.theme_category_content:
        return R.string.ThemeCategoryContent;
      case R.id.theme_category_navigation:
        return R.string.ThemeCategoryNavigation;
      case R.id.theme_category_controls:
        return R.string.ThemeCategoryControls;
      case R.id.theme_category_chat:
        return R.string.ThemeCategoryChats;
      case R.id.theme_category_bubbles:
        return R.string.ThemeCategoryBubbles;
      case R.id.theme_category_colors:
        return R.string.ThemeCategoryColors;
      case R.id.theme_category_iv:
        return R.string.ThemeCategoryIV;
      case R.id.theme_category_other:
        return R.string.ThemeCategoryOther;
      case R.id.theme_category_internal:
        return R.string.ThemeCategoryInternal;
    }
    throw Theme.newError(sectionId, "sectionId");
  }

  public ThemeController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_theme;
  }

  @Override
  protected int getPagerItemCount () {
    return themeSections.length;
  }

  private boolean clipEventsRegistered;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, ViewPager pager) {
    pager.setOffscreenPageLimit(1);

    try {
      ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
      if (clipboard != null) {
        clipboard.addPrimaryClipChangedListener(this);
        clipEventsRegistered = true;
      }
    } catch (Throwable ignored) {}

    context().closeThumbnails(ThemeController.class);
  }

  @Override
  public View onCreateThumbnailView (Context context) {
    CircleButton btn = new CircleButton(context);
    int size = Screen.dp(52f) + Screen.dp(12f * 2f);
    btn.setLayoutParams(FrameLayoutFix.newParams(size, size, Settings.instance().getMinimizedThemeLocation()));
    btn.init(R.drawable.baseline_palette_24, 52f, 12f, R.id.theme_color_circleButtonTheme, R.id.theme_color_circleButtonThemeIcon);
    btn.setOnClickListener(v -> {
      context().closeThumbnail(ThemeController.this);
      context().navigation().navigateTo(ThemeController.this);
    });
    return btn;
  }

  public boolean isDetached () {
    return openOverlayOnClose;
  }

  private boolean openOverlayOnClose;

  @Override
  public void destroy () {
    if (openOverlayOnClose) {
      context().openThumbnail(this);
      return;
    }
    super.destroy();
    if (clipEventsRegistered) {
      try {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
          clipboard.removePrimaryClipChangedListener(this);
          clipEventsRegistered = false;
        }
      } catch (Throwable ignored) {}
    }
    context().checkNightMode();
  }

  @Override
  public void onPrimaryClipChanged () {
    SparseArrayCompat<ViewController<?>> array = getAllCachedControllers();
    if (array != null) {
      for (int i = array.size() - 1; i >= 0; i--) {
        ViewController<?> c = array.valueAt(i);
        if (c instanceof ClipboardManager.OnPrimaryClipChangedListener) {
          ((ClipboardManager.OnPrimaryClipChangedListener) c).onPrimaryClipChanged();
        }
      }
    }
  }

  @Override
  protected int getDrawerReplacementColorId () {
    return R.id.theme_color_background;
  }

  @Override
  protected ViewController<?> onCreatePagerItemForPosition (Context context, int position) {
    ThemeListController c = new ThemeListController(context, tdlib);
    c.setArguments(new ThemeListController.Args(getArgumentsStrict().theme, themeSections[position]));
    if (currentQuery != null)
      c.searchColors(currentQuery, null);
    return c;
  }

  @Override
  protected String[] getPagerSections () {
    String[] names = new String[themeSections.length];
    int i = 0;
    for (int sectionId : themeSections) {
      names[i] = Lang.getString(getSectionName(sectionId)).toUpperCase();
      i++;
    }
    return names;
  }

  @Override
  protected int getTitleStyle () {
    return TITLE_STYLE_COMPACT_BIG;
  }

  @Override
  public CharSequence getName () {
    return getArgumentsStrict().theme.getName();
  }

  @Override
  protected int getBackButton () {
    return BackHeaderButton.TYPE_BACK;
  }

  @Override
  public void onFocus () {
    super.onFocus();

    ThemeController.Args args = getArgumentsStrict();
    if (args.after != null) {
      args.after.run();
      args.after = null;
    }
    ThemeManager.instance().changeGlobalTheme(tdlib, args.theme.getTheme(), true, null);

    getViewPager().setOffscreenPageLimit(getPagerItemCount());

    UI.setSoftInputMode(context(), WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    if (isFocused()) {
      UI.setSoftInputMode(context(), WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    UI.setSoftInputMode(context(), Config.DEFAULT_WINDOW_PARAMS);
  }
}
