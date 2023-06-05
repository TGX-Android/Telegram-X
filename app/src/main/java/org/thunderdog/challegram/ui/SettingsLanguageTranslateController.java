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

import android.content.Context;
import android.view.View;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

public class SettingsLanguageTranslateController extends RecyclerViewController<Void> implements View.OnClickListener {

  public SettingsLanguageTranslateController(Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  private SettingsAdapter adapter;

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
        if (itemId == R.id.language) {
          TdApi.LanguagePackInfo languageInfo = (TdApi.LanguagePackInfo) item.getData();
          item.setSelected(Settings.instance().containsInNotTranslatableLanguageList(languageInfo.id));
          view.findCheckBox().setChecked(item.isSelected(), isUpdate);
          view.setData(languageInfo.name);
        } else if (itemId == R.id.btn_chatDoNotTranslateAppLang || itemId == R.id.btn_chatDoNotTranslateSelected) {
          int chatDoNotTranslateMode = Settings.instance().getChatDoNotTranslateMode();

          if (item.getId() == R.id.btn_chatDoNotTranslateAppLang) {
            item.setSelected(chatDoNotTranslateMode == Settings.DO_NOT_TRANSLATE_MODE_APP_LANG);
            view.setData(Lang.getLanguageName(Settings.instance().getLanguage().packInfo.pluralCode, ""));
          } else {
            item.setSelected(chatDoNotTranslateMode == Settings.DO_NOT_TRANSLATE_MODE_SELECTED);
            String[] languages = Settings.instance().getAllNotTranslatableLanguages();
            if (languages == null || languages.length == 0) {
              view.setData(Lang.getString(R.string.PickLanguages));
            } else if (languages.length < 4) {
              StringBuilder builder = new StringBuilder();
              for (String lang : languages) {
                if (builder.length() > 0) {
                  builder.append(", ");
                }
                builder.append(Lang.getLanguageName(lang, lang));
              }
              view.setData(builder);
            } else {
              view.setData(Lang.plural(R.string.DoNotTranslateLanguages, languages.length));
            }
          }
          view.findRadioView().setChecked(item.isSelected(), isUpdate);
        }
      }
    };
    recyclerView.setAdapter(adapter);

    int chatTranslateMode = Settings.instance().getChatTranslateMode();


    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatTranslateStylePopup, 0, R.string.ChatTranslateStyle1, R.id.btn_chatTranslateStyle, chatTranslateMode == Settings.TRANSLATE_MODE_POPUP));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatTranslateStyleInline, 0, R.string.ChatTranslateStyle2, R.id.btn_chatTranslateStyle, chatTranslateMode == Settings.TRANSLATE_MODE_INLINE));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_chatTranslateStyleNone, 0, R.string.ChatTranslateStyle3, R.id.btn_chatTranslateStyle, chatTranslateMode == Settings.TRANSLATE_MODE_NONE));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.TranslateSettingsDesc));

    if (Settings.instance().getChatTranslateMode() != Settings.TRANSLATE_MODE_NONE) {
      addDoNotTranslateItems(items);
      if (Settings.instance().getChatDoNotTranslateMode() == Settings.DO_NOT_TRANSLATE_MODE_SELECTED) {
        addLanguagesItems(items);
      }
    }

    adapter.setItems(items, true);
  }

  private void addDoNotTranslateItems (List<ListItem> items) {
    int chatDoNotTranslateMode = Settings.instance().getChatDoNotTranslateMode();
    items.add(new ListItem(ListItem.TYPE_HEADER_PADDED, 0, 0, R.string.DoNotTranslate));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2, R.id.btn_chatDoNotTranslateAppLang, 0, R.string.ApplicationLanguage, R.id.btn_chatDoNotTranslate, chatDoNotTranslateMode == Settings.DO_NOT_TRANSLATE_MODE_APP_LANG));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_RADIO_2, R.id.btn_chatDoNotTranslateSelected, 0, R.string.SelectedLanguages, R.id.btn_chatDoNotTranslate, chatDoNotTranslateMode == Settings.DO_NOT_TRANSLATE_MODE_SELECTED));
  }

  private void addLanguagesItems (List<ListItem> items) {
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    for (int a = 0; a < Lang.getSupportedLanguagesForTranslate().length; a++) {
      TdApi.LanguagePackInfo languageInfo = new TdApi.LanguagePackInfo();
      languageInfo.id = Lang.getSupportedLanguagesForTranslate()[a];
      Lang.fixLanguageCode(languageInfo.id, languageInfo);
      if (a != 0) items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_CHECKBOX, R.id.language, 0, languageInfo.nativeName, false).setData(languageInfo));
    }
  }

  @Override
  public boolean passNameToHeader () {
    return true;
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_chatTranslateStylePopup || viewId == R.id.btn_chatTranslateStyleInline || viewId == R.id.btn_chatTranslateStyleNone) {// popup
      if (adapter.processToggle(v)) {
        int valueId = adapter.getCheckIntResults().get(R.id.btn_chatTranslateStyle);
        int newMode;
        if (valueId == R.id.btn_chatTranslateStylePopup) {
          newMode = Settings.TRANSLATE_MODE_POPUP;
        } else if (valueId == R.id.btn_chatTranslateStyleInline) {
          newMode = Settings.TRANSLATE_MODE_INLINE;
        } else if (valueId == R.id.btn_chatTranslateStyleNone) {
          newMode = Settings.TRANSLATE_MODE_NONE;
        } else {
          return;
        }
        updateTranslationStyleMode(newMode);
      }
    } else if (viewId == R.id.btn_chatDoNotTranslateSelected) {
      updateDoNotTranslationStyleMode(Settings.DO_NOT_TRANSLATE_MODE_SELECTED);
    } else if (viewId == R.id.btn_chatDoNotTranslateAppLang) {
      updateDoNotTranslationStyleMode(Settings.DO_NOT_TRANSLATE_MODE_APP_LANG);
    } else if (viewId == R.id.language) {
      TdApi.LanguagePackInfo languageInfo = (TdApi.LanguagePackInfo) ((ListItem) v.getTag()).getData();
      Settings.instance().setIsNotTranslatableLanguage(languageInfo.id, !Settings.instance().containsInNotTranslatableLanguageList(languageInfo.id));
      adapter.updateValuedSettingByData(languageInfo);
      adapter.updateAllValuedSettingsById(R.id.btn_chatDoNotTranslateSelected);
    }
  }

  private void updateTranslationStyleMode (int mode) {
    int oldMode = Settings.instance().getChatTranslateMode();
    if (oldMode == mode) return;

    if (mode == Settings.TRANSLATE_MODE_POPUP) {
      adapter.setIntResult(R.id.btn_chatTranslateStyle, R.id.btn_chatTranslateStylePopup);
    } else if (mode == Settings.TRANSLATE_MODE_INLINE) {
      adapter.setIntResult(R.id.btn_chatTranslateStyle, R.id.btn_chatTranslateStyleInline);
    } else if (mode == Settings.TRANSLATE_MODE_NONE) {
      adapter.setIntResult(R.id.btn_chatTranslateStyle, R.id.btn_chatTranslateStyleNone);
    }

    Settings.instance().setChatTranslateMode(mode);

    if (mode == Settings.TRANSLATE_MODE_NONE) {
      adapter.removeRange(7, adapter.getItemCount() - 7);
    } else if (oldMode == Settings.TRANSLATE_MODE_NONE) {
      List<ListItem> items = adapter.getItems();
      addDoNotTranslateItems(items);
      if (Settings.instance().getChatDoNotTranslateMode() == Settings.DO_NOT_TRANSLATE_MODE_SELECTED) {
        addLanguagesItems(items);
      }
      adapter.notifyItemRangeInserted(7, items.size() - 7);
    }
  }

  private void updateDoNotTranslationStyleMode (int mode) {
    int oldMode = Settings.instance().getChatDoNotTranslateMode();
    if (oldMode == mode) return;

    Settings.instance().setChatDoNotTranslateMode(mode);
    adapter.updateAllValuedSettingsById(R.id.btn_chatDoNotTranslateAppLang);
    adapter.updateAllValuedSettingsById(R.id.btn_chatDoNotTranslateSelected);

    if (mode == Settings.DO_NOT_TRANSLATE_MODE_APP_LANG) {
      adapter.removeRange(12, adapter.getItemCount() - 12);
    } else {
      List<ListItem> items = adapter.getItems();
      addLanguagesItems(items);
      adapter.notifyItemRangeInserted(12, items.size() - 12);
    }
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.Translation);
  }

  @Override
  public int getId () {
    return R.id.controller_translations;
  }
}
