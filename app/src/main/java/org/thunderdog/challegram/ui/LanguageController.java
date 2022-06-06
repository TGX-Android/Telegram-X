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
 * File created on 22/07/2018
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.ListInfoView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;

public class LanguageController extends RecyclerViewController<LanguageController.Args> implements View.OnClickListener, EditLanguageController.Delegate {
  public static class Args {
    public TdApi.LanguagePackInfo languageInfo;
    public Delegate delegate;

    public Args (TdApi.LanguagePackInfo languageInfo, Delegate delegate) {
      this.languageInfo = languageInfo;
      this.delegate = delegate;
    }
  }

  public interface Delegate {
    void onLanguageInfoChanged (TdApi.LanguagePackInfo languageInfo);
  }

  public LanguageController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_strings;
  }

  private SettingsAdapter adapter;

  @Override
  public CharSequence getName () {
    return Lang.getString(onlyUntranslated ? R.string.ToolsUntranslatedTitle : R.string.ToolsAllTitle);
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_editLangPack;
  }

  private Lang.Pack langPack;
  private boolean onlyUntranslated;
  private boolean hadChanges;

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  @Override
  public void destroy () {
    super.destroy();
    if (hadChanges) {
      tdlib.client().send(new TdApi.SetAlarm(), ignored -> tdlib.sendFakeUpdate(new TdApi.UpdateLanguagePackStrings(BuildConfig.LANGUAGE_PACK, langPack.languageInfo.id, null), false));
      hadChanges = false;
    }
  }

  @Override
  public void onLanguageStringChanged (Lang.Pack langPack, Lang.PackString string) {
    tdlib.client().send(new TdApi.SetCustomLanguagePackString(langPack.languageInfo.id, string.translated ? string.string : new TdApi.LanguagePackString(string.getKey(), Lang.STRING_DELETED())), tdlib.okHandler());
    String key = string.getKey();
    switch (key) {
      case "language_name": {
        updateTitle();
        getArgumentsStrict().delegate.onLanguageInfoChanged(langPack.languageInfo);
        break;
      }
      case "language_nameInEnglish": {
        getArgumentsStrict().delegate.onLanguageInfoChanged(langPack.languageInfo);
        break;
      }
      case Lang.INTERNAL_ID_KEY: {
        // tdlib.updateCustomLanguageCode(langPack.languageInfo.code);
        break;
      }
    }
    if (key.startsWith("language_")) {
      hadChanges = true;
    } else if (Lang.packId().equals(langPack.languageInfo.id)) {
      tdlib.sendFakeUpdate(new TdApi.UpdateLanguagePackStrings(BuildConfig.LANGUAGE_PACK, langPack.languageInfo.id, new TdApi.LanguagePackString[] {
        string.translated ? string.string : new TdApi.LanguagePackString(string.getKey(), Lang.STRING_DELETED())
      }), false);
    }
    // TODO move from old language_code to the new one, when it changes
    // TODO update language list when language_name or language_nameInEnglish updates
    // TODO overwrite warning, when language has been modified
    adapter.updateValuedSettingByData(string);
    updateSubtitle();
  }

  @Override
  protected void onLeaveSearchMode () {
    buildCells(langPack.strings, onlyUntranslated);
  }

  private static boolean startEndsSpace (String str) {
    return str != null && (str.startsWith(" ") || str.endsWith(" ") || str.startsWith("\n") || str.endsWith("\n"));
  }

  @Override
  protected void onSearchInputChanged (String query) {
    updateClearSearchButton(!query.isEmpty(), true);
    if (query.equals(" ")) {
      List<Lang.PackString> found = new ArrayList<>();
      for (Lang.PackString string : langPack.strings) {
        switch (string.string.value.getConstructor()) {
          case TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR: {
            TdApi.LanguagePackStringValueOrdinary value = (TdApi.LanguagePackStringValueOrdinary) string.string.value;
            if (startEndsSpace(value.value))
              found.add(string);
            break;
          }
          case TdApi.LanguagePackStringValuePluralized.CONSTRUCTOR: {
            TdApi.LanguagePackStringValuePluralized plural = (TdApi.LanguagePackStringValuePluralized) string.string.value;
            if (startEndsSpace(plural.zeroValue) || startEndsSpace(plural.oneValue) || startEndsSpace(plural.twoValue) || startEndsSpace(plural.fewValue) || startEndsSpace(plural.manyValue) || startEndsSpace(plural.otherValue))
              found.add(string);
            break;
          }
        }
      }
      buildCells(found, onlyUntranslated);
      return;
    }
    query = query.toLowerCase();
    if (StringUtils.isEmpty(query)) {
      buildCells(langPack.strings, onlyUntranslated);
    } else {
      List<Lang.PackString> found = new ArrayList<>();
      List<Lang.PackString> lookup = new ArrayList<>(langPack.strings);
      for (Lang.PackString string : lookup) {
        if (string.string.key.toLowerCase().startsWith(query)) {
          found.add(string);
        }
      }
      lookup.removeAll(found);
      for (Lang.PackString string : lookup) {
        switch (string.string.value.getConstructor()) {
          case TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR: {
            String value = ((TdApi.LanguagePackStringValueOrdinary) string.string.value).value.toLowerCase();
            if (value.contains(query))
              found.add(string);
            break;
          }
          case TdApi.LanguagePackStringValuePluralized.CONSTRUCTOR: {
            TdApi.LanguagePackStringValuePluralized pluralized = (TdApi.LanguagePackStringValuePluralized) string.string.value;
            if (matches(pluralized, query))
              found.add(string);
            break;
          }
        }
      }
      lookup.removeAll(found);
      for (Lang.PackString string : lookup) {
        if (string.translated) {
          switch (string.string.value.getConstructor()) {
            case TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR: {
              if (string.translated && string.getBuiltinValue().value.toLowerCase().contains(query))
                found.add(string);
              break;
            }
            case TdApi.LanguagePackStringValuePluralized.CONSTRUCTOR: {
              if (string.translated && matches(string.getBuiltinPluralized(langPack.untranslatedRules.forms), query))
                found.add(string);
              break;
            }
          }
        }
      }

      buildCells(found, onlyUntranslated);
    }
  }

  private static boolean matches (TdApi.LanguagePackStringValuePluralized pluralized, String query) {
    boolean ok = (!StringUtils.isEmpty(pluralized.zeroValue) && pluralized.zeroValue.toLowerCase().contains(query));
    ok = ok || (!StringUtils.isEmpty(pluralized.oneValue) && pluralized.oneValue.toLowerCase().contains(query));
    ok = ok || (!StringUtils.isEmpty(pluralized.twoValue) && pluralized.twoValue.toLowerCase().contains(query));
    ok = ok || (!StringUtils.isEmpty(pluralized.fewValue) && pluralized.fewValue.toLowerCase().contains(query));
    ok = ok || (!StringUtils.isEmpty(pluralized.manyValue) && pluralized.manyValue.toLowerCase().contains(query));
    ok = ok || (!StringUtils.isEmpty(pluralized.otherValue) && pluralized.otherValue.toLowerCase().contains(query));
    return ok;
  }

  @Override
  protected int getSearchMenuId () {
    return R.id.menu_clear;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_clear: {
        header.addClearButton(menu, getSearchHeaderIconColorId(), getBackButtonResource());
        break;
      }
      case R.id.menu_editLangPack: {
        header.addButton(menu, R.id.menu_btn_toggle, R.drawable.baseline_check_box_outline_blank_24, R.id.theme_color_headerIcon, this, Screen.dp(49f));
        header.addSearchButton(menu, this);
        break;
      }
      default: {
        super.fillMenuItems(id, header, menu);
        break;
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_toggle:
        if (langPack == null)
          return;
        onlyUntranslated = !onlyUntranslated;
        updateSubtitle();
        getRecyclerView().stopScroll();
        updateButton();
        if (langPack != null) {
          buildCells(langPack.strings, onlyUntranslated);
        }
        break;
      default: {
        super.onMenuItemPressed(id, view);
        break;
      }
    }
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return langPack == null;
  }

  private DoubleHeaderView headerCell;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    headerCell = new DoubleHeaderView(context);
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(49f) * 2, true);
    updateTitle();
    headerCell.setSubtitle(getArgumentsStrict().languageInfo.name);
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_string: {
            Lang.PackString string = (Lang.PackString) item.getData();
            SpannableStringBuilder b = new SpannableStringBuilder();
            langPack.makeString(string, b, false);
            view.setTextColorId(string.translated ? R.id.theme_color_text : R.id.theme_color_textNegative);
            view.setData(b);
            break;
          }
        }
      }
      @Override
      protected void setInfo (ListItem item, int position, ListInfoView infoView) {
        infoView.showInfo(Lang.pluralBold(R.string.xStrings, item.getIntValue()));
      }
    };
    recyclerView.setItemAnimator(null);
    recyclerView.setAdapter(adapter);
    Background.instance().post(() -> {
      this.langPack = Lang.getLanguagePack(getArgumentsStrict().languageInfo);
      tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          updateSubtitle();
          buildCells(langPack.strings, onlyUntranslated);
          executeScheduledAnimation();
        }
      });
    });
  }

  private void updateTitle () {
    headerCell.setTitle(getArgumentsStrict().languageInfo.nativeName);
  }

  private void updateSubtitle () {
    int untranslatedCount = langPack.getUntranslatedCount();
    int totalCount = langPack.strings.size();
    headerCell.setSubtitle(onlyUntranslated ? Lang.plural(R.string.TranslationsMissing, untranslatedCount) : Lang.getString(R.string.format_languageStatus, Lang.plural(R.string.xStrings, totalCount - untranslatedCount), (int) Math.floor((float) (totalCount - untranslatedCount) / (float) totalCount * 100f)));
  }

  private void updateButton () {
    if (headerView != null) {
      headerView.updateButton(getMenuId(), R.id.menu_btn_toggle, View.VISIBLE, onlyUntranslated ? R.drawable.baseline_indeterminate_check_box_24 : R.drawable.baseline_check_box_outline_blank_24);
    }
  }

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    updateButton();
  }

  @Override
  protected boolean useGraySearchHeader () {
    return true;
  }

  private void buildCells (List<Lang.PackString> strings, boolean onlyUntranslated) {
    int displayedStringCount = 0;
    List<ListItem> items = new ArrayList<>(strings.size() * 2);
    boolean first = true;
    int prevSection = 0;
    Lang.PackString prevString = null;
    for (Lang.PackString string : strings) {
      if (onlyUntranslated && string.translated) {
        continue;
      }
      boolean skipSeparator = false;
      int section = string.getSection();
      if (prevSection != section) {
        if (prevSection != 0) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          first = true;
        }
        prevSection = section;
      } else if (prevString != null && section == Lang.Pack.SECTION_RELATIVE_DATE) {
        String key = string.string.key;
        key = key.substring(0, key.lastIndexOf('_'));
        String prevKey = prevString.string.key;
        prevKey = prevKey.substring(0, prevKey.lastIndexOf('_'));
        if (!key.equals(prevKey)) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          skipSeparator = true;
        }
      }
      if (first) {
        items.add(new ListItem(items.isEmpty() ? ListItem.TYPE_HEADER_PADDED : ListItem.TYPE_HEADER, 0, 0, Lang.Pack.getSectionName(section), false));
        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        first = false;
      } else if (!skipSeparator) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_string, 0, string.getKey(), false).setData(string).setContentStrings(Lang.getStringResourceIdentifier(string.getKey())));
      prevString = string;
      displayedStringCount++;
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_LIST_INFO_VIEW).setIntValue(displayedStringCount));
    adapter.setItems(items, false);
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_string: {
        Lang.PackString string = (Lang.PackString) ((ListItem) v.getTag()).getData();

        if (getArgumentsStrict().languageInfo.id.startsWith("X")) {
          preventLeavingSearchMode();
          EditLanguageController c = new EditLanguageController(context, tdlib);
          c.setArguments(new EditLanguageController.Args(this, langPack, string));
          navigateTo(c);
          return;
        }

        IntList ids = new IntList(3);
        IntList icons = new IntList(3);
        StringList strings = new StringList(3);

        ids.append(R.id.btn_string);
        strings.append(R.string.ToolsOpenOnPlatform);
        icons.append(R.drawable.baseline_open_in_browser_24);

        ids.append(R.id.btn_copyLink);
        strings.append(R.string.CopyLink);
        icons.append(R.drawable.baseline_link_24);

        if (string.string.value instanceof TdApi.LanguagePackStringValueOrdinary) {
          ids.append(R.id.btn_copyText);
          strings.append(R.string.ToolsCopyString);
          icons.append(R.drawable.baseline_content_copy_24);
        }

        ids.append(R.id.btn_open);
        strings.append(R.string.ToolsShowToast);
        icons.append(R.drawable.baseline_visibility_24);

        String key = string.string.key;
        SpannableStringBuilder b = new SpannableStringBuilder(key);

        b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoItalic(), R.id.theme_color_textNeutral).setEntityType(new TdApi.TextEntityTypeItalic()), 0, key.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        showOptions(Spannable.Factory.getInstance().newSpannable(b), ids.get(), strings.get(), null, icons.get(), (itemView, id) -> {
          switch (id) {
            case R.id.btn_string: {
              Intents.openLink(TD.getLanguageKeyLink(string.string.key));
              break;
            }
            case R.id.btn_copyLink: {
              UI.copyText(TD.getLanguageKeyLink(string.string.key), R.string.CopiedLink);
              break;
            }
            case R.id.btn_copyText: {
              UI.copyText(((TdApi.LanguagePackStringValueOrdinary) string.string.value).value, R.string.CopiedText);
              break;
            }
            case R.id.btn_open: {
              switch (string.string.value.getConstructor()) {
                case TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR:
                  UI.showToast(((TdApi.LanguagePackStringValueOrdinary) string.string.value).value, Toast.LENGTH_LONG);
                  break;
                case TdApi.LanguagePackStringValuePluralized.CONSTRUCTOR: {
                  int resId = Lang.getStringResourceIdentifier(string.string.key);
                  Lang.PluralizationRules rules = string.translated ? langPack.rules : langPack.untranslatedRules;
                  for (Lang.PluralizationForm form : rules.forms) {
                    if (form.numbers != null && form.numbers.length > 0) {
                      UI.showToast(Lang.plural(resId, form.numbers[0]), Toast.LENGTH_SHORT);
                    }
                  }
                  break;
                }
                case TdApi.LanguagePackStringValueDeleted.CONSTRUCTOR:
                  break;
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

  @Override
  public void onLanguagePackEvent (int event, int arg1) {
    switch (event) {
      case Lang.EVENT_PACK_CHANGED:
        langPack.rebuild();
        break;
      case Lang.EVENT_STRING_CHANGED:
        for (Lang.PackString string : langPack.strings) {
          if (Lang.getStringResourceIdentifier(string.getKey()) == arg1) {
            string.rebuild(langPack);
            break;
          }
        }
        break;
    }
    updateSubtitle();
    super.onLanguagePackEvent(event, arg1);
  }
}
