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
 * File created on 01/08/2018
 */
package org.thunderdog.challegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.user.RemoveHelper;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.vkryl.android.text.AcceptFilter;
import me.vkryl.android.text.RestrictFilter;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.td.Td;

public class SettingsLanguageController extends RecyclerViewController<Void> implements View.OnClickListener, LanguageController.Delegate, View.OnLongClickListener {
  public SettingsLanguageController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Language);
  }

  @Override
  public int getId () {
    return R.id.controller_language;
  }

  private SettingsAdapter adapter;
  private boolean isLoading;

  @Override
  public boolean needAsynchronousAnimation () {
    return isLoading;
  }

  @Override
  public long getAsynchronousAnimationTimeout (boolean fastAnimation) {
    return 300l;
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_help;
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.menu_btn_help) {
      openHelp();
    }
  }

  private void openHelp () {
    // best code design patterns (c) 2018
    AlertDialog[] finalDialog = new AlertDialog[1];

    AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
    b.setTitle(Lang.getString(R.string.TranslationMoreTitle));
    b.setMessage(Strings.buildMarkdown(this, Lang.getString(R.string.TranslationMoreText), (v, span, clickedText) -> {
      if (finalDialog[0] != null) {
        try {
          finalDialog[0].dismiss();
        } catch (Throwable ignored) { }
      }
      return false;
    }));
    b.setPositiveButton(Lang.getString(R.string.TranslationMoreDone), (dialog, which) -> dialog.dismiss());
    b.setNeutralButton(Lang.getString(R.string.TranslationMoreCreate), (dialog, which) -> {
      dialog.dismiss();
      tdlib.ui().postDelayed(() -> {
        if (!isDestroyed()) {
          createNewLanguage();
        }
      }, 200);
    });
    finalDialog[0] = modifyAlert(showAlert(b), ALERT_HAS_LINKS);
  }

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_chatTranslateStyle) {
          int chatTranslateMode = Settings.instance().getChatTranslateMode();
          switch (chatTranslateMode) {
            case Settings.TRANSLATE_MODE_POPUP:
              view.setData(Lang.getString(R.string.ChatTranslateStyle1));
              break;
            case Settings.TRANSLATE_MODE_INLINE:
              view.setData(Lang.getString(R.string.ChatTranslateStyle2));
              break;
            case Settings.TRANSLATE_MODE_NONE:
              view.setData(Lang.getString(R.string.ChatTranslateStyleDisabled));
              break;
          }
        } else if (itemId == R.id.language) {
          TdApi.LanguagePackInfo languageInfo = (TdApi.LanguagePackInfo) item.getData();
          view.setInProgress(applyingLanguageInfo == languageInfo, isUpdate);
          view.findRadioView().setChecked(languageInfo.id.equals(Lang.packId()), isUpdate);
          view.forcePadding(Screen.dp(73), view.getForcedPaddingRight());
          if (Td.isBeta(languageInfo)) {
            view.setName(rawName(languageInfo.nativeName));
            String data = rawName(languageInfo.name);
            int percent = (int) Math.floor((float) languageInfo.translatedStringCount / (float) languageInfo.totalStringCount * 100f);
            if (Td.isInstalled(languageInfo) || percent == 100) {
              view.setData(data);
            } else {
              view.setData(Lang.getString(R.string.format_languageStatus, data, percent));
            }
          } else {
            if (item.setStringIfChanged(languageInfo.nativeName)) {
              view.setName(languageInfo.nativeName);
            }
            view.setData(languageInfo.name);
          }
        }
      }
    };
    adapter.setOnLongClickListener(this);
    recyclerView.setAdapter(adapter);
    isLoading = true;
    tdlib.client().send(new TdApi.GetLocalizationTargetInfo(true), localResult -> {

      TdApi.LocalizationTargetInfo localLanguages = localResult.getConstructor() == TdApi.LocalizationTargetInfo.CONSTRUCTOR ? (TdApi.LocalizationTargetInfo) localResult : null;
      if (localLanguages != null && localLanguages.languagePacks.length > 0) {
        Td.sort(localLanguages.languagePacks, Lang.packId());
        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            processLanguagePackResult(localLanguages);
          }
        });
      }

      tdlib.client().send(new TdApi.GetLocalizationTargetInfo(false), cloudResult -> {
        List<TdApi.LanguagePackInfo> cloudLanguages;
        if (localLanguages != null && localLanguages.languagePacks.length > 0) {
          cloudLanguages = new ArrayList<>();
          if (cloudResult.getConstructor() == TdApi.LocalizationTargetInfo.CONSTRUCTOR) {
            TdApi.LanguagePackInfo[] cloudInfos = ((TdApi.LocalizationTargetInfo) cloudResult).languagePacks;
            Td.sort(cloudInfos, Lang.packId());
            Collections.addAll(cloudLanguages, cloudInfos);
            for (int i = cloudLanguages.size() - 1; i >= 0; i--) {
              TdApi.LanguagePackInfo cloudLanguage = cloudLanguages.get(i);
              for (TdApi.LanguagePackInfo localLanguage : localLanguages.languagePacks) {
                if (localLanguage.id.equals(cloudLanguage.id)) {
                  cloudLanguages.remove(i);
                  break;
                }
              }
            }
          }
        } else {
          cloudLanguages = null;
        }

        tdlib.ui().post(() -> {
          if (!isDestroyed()) {
            isLoading = false;
            if (cloudLanguages == null || !cloudLanguages.isEmpty())
              processLanguagePackResult(cloudResult);
          }
        });
      });
    });
    RemoveHelper.attach(recyclerView, new RemoveHelper.Callback() {
      @Override
      public boolean canRemove (RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int position) {
        ListItem item = (ListItem) viewHolder.itemView.getTag();
        if (item == null || item.getId() != R.id.language)
          return false;
        TdApi.LanguagePackInfo languageInfo = (TdApi.LanguagePackInfo) item.getData();
        return languageInfo != null && languageInfo.id.startsWith("X");
      }

      @Override
      public void onRemove (RecyclerView.ViewHolder viewHolder) {
        ListItem item = (ListItem) viewHolder.itemView.getTag();
        if (item != null)
          showRemoveLanguagePrompt(item);
      }
    });
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    adapter.updateAllValuedSettingsById(R.id.btn_chatTranslateStyle);
  }

  private int headerItemsOffset = 0;

  private void processLanguagePackResult (TdApi.Object result) {
    headerItemsOffset = 0;

    switch (result.getConstructor()) {
      case TdApi.LocalizationTargetInfo.CONSTRUCTOR: {
        TdApi.LocalizationTargetInfo languagePack = (TdApi.LocalizationTargetInfo) result;
        List<ListItem> items = new ArrayList<>(languagePack.languagePacks.length * 2);

        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_chatTranslateStyle, R.drawable.baseline_translate_24, R.string.Translation));
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        //items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TranslateSettingsDesc));
        headerItemsOffset = 2;

        boolean first = true;
        TdApi.LanguagePackInfo prevLanguageInfo = null;
        for (TdApi.LanguagePackInfo languageInfo : languagePack.languagePacks) {
          if (prevLanguageInfo != null && (Td.isInstalled(prevLanguageInfo) != Td.isInstalled(languageInfo) || (!Td.isInstalled(prevLanguageInfo) && Td.isBeta(prevLanguageInfo) != Td.isBeta(languageInfo)))) {
            first = true;
          }
          if (first) {
            boolean isEmpty = items.size() <= 3;
            int sectionName = getSectionName(languageInfo);
            if (!isEmpty)
              items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
            items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, sectionName));
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
            first = false;
          } else {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          }
          items.add(newSettingItem(languageInfo));
          prevLanguageInfo = languageInfo;
        }
        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
        adapter.setItems(items, false);
        break;
      }
      case TdApi.Error.CONSTRUCTOR: {
        adapter.setItems(new ListItem[]{
          new ListItem(ListItem.TYPE_EMPTY, 0, 0, TD.toErrorString(result), false)
        }, false);
        break;
      }
    }
    executeScheduledAnimation();
  }

  private static ListItem newSettingItem (TdApi.LanguagePackInfo languageInfo) {
    return new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO, R.id.language, 0, languageInfo.nativeName, false).setData(languageInfo);
  }

  private void showRemoveLanguagePrompt (ListItem item) {
    TdApi.LanguagePackInfo languageInfo = (TdApi.LanguagePackInfo) item.getData();
    if (languageInfo == null || languageInfo.isOfficial)
      return;
    boolean isCustom = Td.isLocal(languageInfo);
    showOptions(Lang.getStringBold(isCustom ? R.string.DeleteLanguageConfirm : R.string.LanguageDeleteConfirm, languageInfo.nativeName, languageInfo.name, TD.getLink(languageInfo)), new int[]{R.id.btn_delete, R.id.btn_cancel}, new String[]{Lang.getString(isCustom ? R.string.RemoveLanguage : R.string.LanguageDelete), Lang.getString(R.string.Cancel)}, new int[]{OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[]{R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_delete) {
        removeLanguage(item, languageInfo);
      }
      return true;
    });
  }

  @Override
  public void onLanguageInfoChanged (TdApi.LanguagePackInfo languageInfo) {
    adapter.updateValuedSettingByData(languageInfo);
  }

  private void removeLanguage (ListItem item, TdApi.LanguagePackInfo languageInfo) {
    Client.ResultHandler after = result -> {
      switch (result.getConstructor()) {
        case TdApi.Ok.CONSTRUCTOR:
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              int i = adapter.indexOfView(item);
              if (i != -1) {
                ListItem prev = adapter.getItems().get(i - 1);
                ListItem next = adapter.getItems().get(i + 1);
                if (prev.getViewType() == ListItem.TYPE_SEPARATOR_FULL) {
                  adapter.removeRange(i - 1, 2);
                } else if (next.getViewType() == ListItem.TYPE_SEPARATOR_FULL) {
                  adapter.removeRange(i, 2);
                } else {
                  adapter.removeRange(i - 2, 4);
                }
              }
            }
          });
          break;
        case TdApi.Error.CONSTRUCTOR:
          UI.showError(result);
          break;
      }
    };
    if (languageInfo.id.equals(Lang.packId())) {
      TdApi.LanguagePackInfo defaultLanguage = !StringUtils.isEmpty(languageInfo.baseLanguagePackId) ? findLanguage(languageInfo.baseLanguagePackId) : null;
      if (defaultLanguage == null)
        defaultLanguage = findLanguage(Lang.getBuiltinLanguagePackId());
      if (defaultLanguage != null)
        changeLanguage(defaultLanguage, false, false, () -> tdlib.client().send(new TdApi.DeleteLanguagePack(languageInfo.id), after));
    } else {
      tdlib.client().send(new TdApi.DeleteLanguagePack(languageInfo.id), after);
    }
  }

  private TdApi.LanguagePackInfo findLanguage (@Nullable String code) {
    for (ListItem item : adapter.getItems()) {
      if (item.getId() == R.id.language) {
        TdApi.LanguagePackInfo info = (TdApi.LanguagePackInfo) item.getData();
        if (code == null || info.id.equals(code)) {
          return info;
        }
      }
    }
    return null;
  }

  @Override
  public boolean passNameToHeader () {
    return true;
  }

  private static @StringRes int getSectionName (TdApi.LanguagePackInfo info) {
    if (Td.isInstalled(info)) {
      return R.string.LanguageSectionInstalled;
    } else if (Td.isBeta(info)) {
      return R.string.LanguageSectionRaw;
    } else {
      return R.string.Language;
    }
  }

  private static String rawName (String name) {
    return name.endsWith(" (raw)") ? name.substring(0, name.length() - " (raw)".length()) : name.endsWith(" (beta)") ? name.substring(0, name.length() - " (beta)".length()) : name;
  }

  private void openLanguage (TdApi.LanguagePackInfo languageInfo) {
    Runnable act = () -> {
      LanguageController c = new LanguageController(context, tdlib);
      c.setArguments(new LanguageController.Args(languageInfo, this));
      navigateTo(c);
    };
    if (!Lang.packId().equals(languageInfo.id)) {
      tdlib.syncLanguage(languageInfo, success -> {
        if (success) {
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              act.run();
            }
          });
        }
      });
    } else {
      act.run();
    }
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_chatTranslateStyle) {
      showTranslateOptions();
    } else if (viewId == R.id.language) {
      TdApi.LanguagePackInfo languageInfo = (TdApi.LanguagePackInfo) ((ListItem) v.getTag()).getData();
      String code = Lang.packId();
      if (code.equals(languageInfo.id)) {
        if (Td.isBeta(languageInfo) || Td.isInstalled(languageInfo) || hasAccessToRawLanguages()) {
          showLanguageOptions((ListItem) v.getTag());
        } else {
          navigateBack();
        }
      } else {
        changeLanguage(languageInfo, !Td.isInstalled(languageInfo) && !Td.isBeta(languageInfo) && !code.startsWith("X") && !code.endsWith("-raw"), true, null);
      }
    }
  }

  private void addInstalledLanguage (TdApi.LanguagePackInfo languageInfo) {
    TdApi.LanguagePackInfo firstInfo = findLanguage(null);
    if (firstInfo == null) {
      return;
    }
    ListItem item = newSettingItem(languageInfo);
    if (Td.isInstalled(firstInfo)) {
      adapter.getItems().add(2 + headerItemsOffset, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      adapter.getItems().add(2 + headerItemsOffset, item);
      adapter.notifyItemRangeInserted(2 + headerItemsOffset, 2);
    } else {
      adapter.getItems().add(headerItemsOffset, new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      adapter.getItems().add(headerItemsOffset, item);
      adapter.getItems().add(headerItemsOffset, new ListItem(ListItem.TYPE_SHADOW_TOP));
      adapter.getItems().add(headerItemsOffset, new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.LanguageSectionInstalled));
      adapter.notifyItemRangeInserted(headerItemsOffset, 4);
      ((LinearLayoutManager) getRecyclerView().getLayoutManager()).scrollToPositionWithOffset(0, 0);
    }
  }

  private void createNewLanguage () {
    MaterialEditTextGroup group = openInputAlert(Lang.getString(R.string.LocalizationCreateTitle), Strings.setSpanColorId(Strings.buildMarkdown(this, Lang.getString(R.string.ToolsLocalePlaceholder), null), ColorId.text), R.string.LocalizationCreateDone, R.string.Cancel, null, (inputView, languageCode) -> {
      if (!languageCode.matches("[A-Za-z\\-]*"))
        return false;
      TdApi.LanguagePackInfo newLanguageInfo = new TdApi.LanguagePackInfo("X" + languageCode + "X-android-x-local", null, "Unknown (" + languageCode + ")", "Unknown", Lang.cleanLanguageCode(languageCode), false, false, false, true, 0, 0, 0, null);
      TdApi.LanguagePackInfo existingLanguage = findLanguage(newLanguageInfo.id);
      if (existingLanguage != null)
        return false;
      List<TdApi.LanguagePackString> languagePackStrings = new ArrayList<>(3);
      languagePackStrings.add(new TdApi.LanguagePackString(Lang.INTERNAL_ID_KEY, new TdApi.LanguagePackStringValueOrdinary(languageCode)));

      String cleanLanguageCode = newLanguageInfo.id;
      if (cleanLanguageCode.startsWith("X")) {
        int i = cleanLanguageCode.indexOf('X', 1);
        cleanLanguageCode = cleanLanguageCode.substring(1, i != -1 ? i : cleanLanguageCode.length());
      }
      if (Lang.fixLanguageCode(cleanLanguageCode, newLanguageInfo)) {
        languagePackStrings.add(new TdApi.LanguagePackString("language_nameInEnglish", new TdApi.LanguagePackStringValueOrdinary(newLanguageInfo.name)));
        languagePackStrings.add(new TdApi.LanguagePackString("language_name", new TdApi.LanguagePackStringValueOrdinary(newLanguageInfo.nativeName)));
        int i = cleanLanguageCode.indexOf('-');
        if (i != -1) {
          languagePackStrings.add(new TdApi.LanguagePackString("language_dateFormatLocale", new TdApi.LanguagePackStringValueOrdinary(cleanLanguageCode)));
        }
      }
      newLanguageInfo.localStringCount = languagePackStrings.size();

      TdApi.LanguagePackString[] array = new TdApi.LanguagePackString[languagePackStrings.size()];
      languagePackStrings.toArray(array);
      tdlib.client().send(new TdApi.SetCustomLanguagePack(newLanguageInfo, array), result -> {
        switch (result.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR:
            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                addInstalledLanguage(newLanguageInfo);
                // openLanguage(newLanguageInfo);
              }
            });
            break;
          case TdApi.Error.CONSTRUCTOR:
            UI.showError(result);
            break;
        }
      });
      return true;
    }, true);
    if (group != null) {
      group.getEditText().setFilters(new InputFilter[] {
        new AcceptFilter() {
          @Override
          protected boolean accept (char c) {
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-';
          }
        },
        new InputFilter.LengthFilter(64 - "XX-android-x-local".length())
      });
    }
  }

  private boolean showLanguageOptions (ListItem item) {
    TdApi.LanguagePackInfo languageInfo = (TdApi.LanguagePackInfo) item.getData();

    IntList ids = null;
    IntList icons = null;
    IntList colors = null;
    StringList strings = null;
    CharSequence info = null;

    if (Lang.isBuiltinLanguage(languageInfo.id)) {
      ids = new IntList(3);
      strings = new StringList(3);
      icons = new IntList(3);

      if (hasAccessToRawLanguages()) {
        ids.append(R.id.btn_view);
        icons.append(R.drawable.baseline_visibility_24);
        strings.append(R.string.LocalizationView);
      }

      ids.append(R.id.btn_shareLink);
      icons.append(R.drawable.baseline_forward_24);
      strings.append(R.string.Share);

      ids.append(R.id.btn_help);
      strings.append(R.string.TranslationMoreTitle);
      icons.append(R.drawable.baseline_help_outline_24);

      ids.append(R.id.btn_new);
      strings.append(R.string.LocalizationCreateTitle);
      icons.append(R.drawable.baseline_create_24);
    } else if (Td.isBeta(languageInfo) || Td.isLocal(languageInfo) || Td.isInstalled(languageInfo) || hasAccessToRawLanguages()) {
      int size = Td.isLocal(languageInfo) ? 3 : 2;

      info = languageInfo.nativeName + " / " + languageInfo.name;

      ids = new IntList(size);
      icons = new IntList(size);
      colors = new IntList(size);
      strings = new StringList(size);
      /* TODO?

      ids.append(R.id.btn_help);
      icons.append(R.drawable.baseline_help_outline_24);
      strings.append(R.string.LocalizationFindTypos);
      colors.append(OPTION_COLOR_NORMAL);*/
      if (Td.isLocal(languageInfo)) {
        ids.append(R.id.btn_view);
        icons.append(R.drawable.baseline_edit_24);
        strings.append(R.string.LocalizationEdit);
        colors.append(OPTION_COLOR_NORMAL);
      } else if (languageInfo.id.equals(Lang.packId())) {
        ids.append(R.id.btn_view);
        icons.append(R.drawable.baseline_visibility_24);
        strings.append(R.string.LocalizationView);
        colors.append(OPTION_COLOR_NORMAL);
      }
      ids.append(R.id.btn_shareLink);
      icons.append(R.drawable.baseline_forward_24);
      strings.append(R.string.Share);
      colors.append(OPTION_COLOR_NORMAL);

      ids.append(R.id.btn_share);
      icons.append(R.drawable.baseline_code_24);
      strings.append(R.string.LocalisationShare);
      colors.append(OPTION_COLOR_NORMAL);

      if (Td.isInstalled(languageInfo)) {
        ids.append(R.id.btn_delete);
        icons.append(R.drawable.baseline_delete_forever_24);
        strings.append(Td.isLocal(languageInfo) ? R.string.RemoveLanguage : R.string.LanguageDelete);
        colors.append(OPTION_COLOR_RED);
      }
    } else if (!Td.isLocal(languageInfo)) {
      ids = new IntList(1);
      icons = new IntList(1);
      strings = new StringList(1);

      ids.append(R.id.btn_shareLink);
      icons.append(R.drawable.baseline_forward_24);
      strings.append(R.string.Share);
    }
    if (ids != null) {
      showOptions(info, ids.get(), strings.get(), colors != null ? colors.get() : null, icons.get(), (itemView, id) -> {
        if (id == R.id.btn_new) {
          createNewLanguage();
        } else if (id == R.id.btn_help) {
          openHelp();
        } else if (id == R.id.btn_share) {
          if (Td.isLocal(languageInfo) || languageInfo.id.equals(Lang.packId())) {
            exportLanguage(languageInfo);
          } else {
            tdlib.syncLanguage(languageInfo, success -> {
              if (success) {
                tdlib.ui().post(() -> exportLanguage(languageInfo));
              }
            });
          }
        } else if (id == R.id.btn_shareLink) {
          tdlib.ui().shareLanguageUrl(this, languageInfo);
        } else if (id == R.id.btn_view) {
          openLanguage(languageInfo);
        } else if (id == R.id.btn_delete) {
          showRemoveLanguagePrompt(item);
        }
        return true;
      });
      return true;
    }

    return false;
  }

  private boolean hasAccessToRawLanguages () {
    if (adapter == null)
      return false;
    List<ListItem> items = adapter.getItems();
    for (int i = items.size() - 1; i >= 0; i--) {
      ListItem item = items.get(i);
      if (item.getId() == R.id.language) {
        TdApi.LanguagePackInfo languageInfo = (TdApi.LanguagePackInfo) item.getData();
        if (Td.isBeta(languageInfo)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean onLongClick (View v) {
    if (v.getId() != R.id.language)
      return false;
    TdApi.LanguagePackInfo languageInfo = (TdApi.LanguagePackInfo) ((ListItem) v.getTag()).getData();
    return (!Td.isBeta(languageInfo) || languageInfo.id.equals(Lang.packId()) || hasAccessToRawLanguages()) && showLanguageOptions((ListItem) v.getTag());
  }

  private void exportLanguage (TdApi.LanguagePackInfo languageInfo) {
    tdlib.client().send(new TdApi.GetLanguagePackStrings(languageInfo.id, null), result -> {
      switch (result.getConstructor()) {
        case TdApi.LanguagePackStrings.CONSTRUCTOR: {
          if (((TdApi.LanguagePackStrings) result).strings.length == 0) {
            UI.showToast(R.string.LanguageEmpty, Toast.LENGTH_SHORT);
            return;
          }
          int[] requiredStrings = Lang.getRequiredKeys();
          for (int resId : requiredStrings) {
            if (Lang.queryTdlibStringValue(Lang.getResourceEntryName(resId), languageInfo.id) == null) {
              UI.showToast(R.string.InvalidLocalisation, Toast.LENGTH_SHORT);
              return;
            }
          }
          tdlib.ui().post(() -> {
            if (!isDestroyed()) {
              String fileNamePrefix = BuildConfig.LANGUAGE_PACK + "_" + Lang.cleanLanguageCode(languageInfo.id);
              MaterialEditTextGroup group = openInputAlert(Lang.getString(R.string.FileName), Strings.setSpanColorId(Strings.buildMarkdown(this, "**" + Lang.getString(R.string.LocalizationFileNamePlaceholder) + "**.xml", null), ColorId.text), R.string.Share, R.string.Cancel, fileNamePrefix + "_" + Lang.getTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS).replace('/', '.'), (inputView, result1) -> {
                if (result1.indexOf('/') != -1 && StringUtils.isEmpty(result1.trim()))
                  return false;

                String name = result1.endsWith(".xml") ? result1 : result1 + ".xml";
                String fileName = StringUtils.secureFileName(name);
                String conversion = "language_export_" + System.currentTimeMillis() + "_" + languageInfo.id;
                String appName = BuildConfig.PROJECT_NAME;
                String channelUrl = "https://t.me/tgx_android";

                TdApi.FormattedText caption = TD.newText(Lang.getString(R.string.ToolsExportText, (target, argStart, argEnd, argIndex, needFakeBold) -> argIndex == 2 ? TD.newSpan(new TdApi.TextEntityTypeTextUrl(channelUrl)) : null, languageInfo.nativeName, languageInfo.name, appName));
                TdApi.InputMessageContent content = new TdApi.InputMessageDocument(new TdApi.InputFileGenerated(fileName, conversion, 0), null, false, caption);
                ShareController c = new ShareController(context, tdlib);
                c.setArguments(new ShareController.Args(content));
                Keyboard.hide(inputView.getEditText());
                tdlib.ui().postDelayed(c::show, 200);

                return true;
              }, true);
              if (group != null) {
                group.getEditText().setFilters(new InputFilter[]{ new RestrictFilter(new char[] {'/'})});
              }
            }
          });
          break;
        }
        case TdApi.Error.CONSTRUCTOR: {
          UI.showError(result);
          break;
        }
      }
    });
  }

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    if (Lang.hasDirectionChanged(event, arg1)) {
      super.onLanguagePackEvent(event, arg1);
    } else if (event == Lang.EVENT_PACK_CHANGED || event == Lang.EVENT_STRING_CHANGED) {
      setName(getName());
      adapter.updateAllValuedSettings(item -> item.getViewType() == ListItem.TYPE_HEADER || item.getViewType() == ListItem.TYPE_HEADER_PADDED);
    }
  }

  private TdApi.LanguagePackInfo applyingLanguageInfo;

  private void changeLanguage (TdApi.LanguagePackInfo languageInfo, boolean navigateBack, boolean needPrompt, @Nullable Runnable after) {
    if (applyingLanguageInfo == languageInfo)
      return;
    TdApi.LanguagePackInfo oldLanguageInfo = this.applyingLanguageInfo;
    this.applyingLanguageInfo = languageInfo;
    if (oldLanguageInfo != null) {
      adapter.updateValuedSettingByData(oldLanguageInfo);
    }
    TdApi.LanguagePackInfo previousLanguage = findLanguage(Lang.packId());
    if (languageInfo != null) {
      adapter.updateValuedSettingByData(languageInfo);
      tdlib.applyLanguage(languageInfo, success -> {
        if (!isDestroyed()) {
          applyingLanguageInfo = null;
          adapter.updateValuedSettingByData(languageInfo);
          if (success) {
            if (after != null) {
              after.run();
            }
            if (navigateBack) {
              navigateBack();
            } else {
              adapter.updateValuedSettingByData(languageInfo);
              if (previousLanguage != null)
                adapter.updateValuedSettingByData(previousLanguage);
              if (needPrompt) {
                UI.showToast(R.string.LocalisationApplied, Toast.LENGTH_SHORT);
              }
            }
          }
        }
      }, true);
    }
  }

  private void showTranslateOptions () {
    navigateTo(new SettingsLanguageTranslateController(context, tdlib));

    /*int chatTranslateMode = Settings.instance().getChatTranslateMode();
    showSettings(new SettingsWrapBuilder(R.id.btn_chatTranslateStyle).setRawItems(new ListItem[]{
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatTranslateStyle1, 0, R.string.ChatTranslateStyle1, R.id.btn_chatTranslateStyle, chatTranslateMode == Settings.TRANSLATE_MODE_POPUP),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatTranslateStyle2, 0, R.string.ChatTranslateStyle2, R.id.btn_chatTranslateStyle, chatTranslateMode == Settings.TRANSLATE_MODE_INLINE),
      new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatTranslateStyle3, 0, R.string.ChatTranslateStyle3, R.id.btn_chatTranslateStyle, chatTranslateMode == Settings.TRANSLATE_MODE_NONE),
    }).setIntDelegate((id, result) -> {
      int chatTranslateMode1 = Settings.instance().getChatTranslateMode();
      int chatTranslateStyleResult = result.get(R.id.btn_chatTranslateStyle);
      switch (chatTranslateStyleResult) {
        case R.id.btn_chatTranslateStyle1:
          chatTranslateMode1 = Settings.TRANSLATE_MODE_POPUP;
          break;
        case R.id.btn_chatTranslateStyle2:
          chatTranslateMode1 = Settings.TRANSLATE_MODE_INLINE;
          break;
        case R.id.btn_chatTranslateStyle3:
          chatTranslateMode1 = Settings.TRANSLATE_MODE_NONE;
          break;
      }
      Settings.instance().setChatTranslateMode(chatTranslateMode1);
      adapter.updateValuedSettingById(R.id.btn_chatTranslateStyle);
    }).setAllowResize(false));*/
  }
}
