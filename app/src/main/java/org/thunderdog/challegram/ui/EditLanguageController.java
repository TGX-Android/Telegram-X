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
 * File created on 22/08/2018
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.net.Uri;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.DoubleHeaderView;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.navigation.MoreDelegate;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.theme.ThemeColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;

public class EditLanguageController extends EditBaseController<EditLanguageController.Args> implements Menu, MoreDelegate, SettingsAdapter.TextChangeListener {
  public static class Args {
    public Delegate delegate;
    public Lang.Pack langPack;
    public Lang.PackString string;
    public List<Lang.PackString> stack;

    public Args (Delegate delegate, Lang.Pack langPack, Lang.PackString string) {
      this.delegate = delegate;
      this.langPack = langPack;
      this.string = string;
    }
  }

  public interface Delegate {
    void onLanguageStringChanged (Lang.Pack langPack, Lang.PackString string);
  }

  public EditLanguageController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_editLanguage;
  }

  private SettingsAdapter adapter;
  private TdApi.LanguagePackStringValueOrdinary value;
  private TdApi.LanguagePackStringValuePluralized pluralizedValue;

  private void buildCells () {
    Set<String> formatArgs = new HashSet<>();
    Args args = getArgumentsStrict();

    headerCell.setSubtitle(args.string.getKey());

    List<ListItem> items = new ArrayList<>();
    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_NO_HEAD));

    Lang.PackString string = args.string;

    switch (string.string.value.getConstructor()) {
      case TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR: {
        TdApi.LanguagePackStringValueOrdinary value = (TdApi.LanguagePackStringValueOrdinary) string.string.value;
        this.value = string.translated ? new TdApi.LanguagePackStringValueOrdinary(value.value) : new TdApi.LanguagePackStringValueOrdinary();
        SpannableStringBuilder originalValue = new SpannableStringBuilder();
        String originalValueStr = string.getBuiltinValue().value;
        if (!string.translated) {
          args.langPack.makeString(value.value, originalValue, true, -1);
        } else {
          args.langPack.makeString(originalValueStr, originalValue, true, -1);
        }
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, originalValue, false));
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_EDITTEXT, R.id.string, 0, R.string.LocalizationTranslation, false).setStringValue(string.translated ? value.value : null));
        findFormatArgs(formatArgs, originalValueStr);
        break;
      }
      case TdApi.LanguagePackStringValuePluralized.CONSTRUCTOR: {
        TdApi.LanguagePackStringValuePluralized plural = (TdApi.LanguagePackStringValuePluralized) string.string.value;
        TdApi.LanguagePackStringValuePluralized builtin = Lang.getBuiltinStringPluralized(string.getKey(), args.langPack.untranslatedRules.forms);
        findFormatArgs(formatArgs, builtin.zeroValue);
        findFormatArgs(formatArgs, builtin.oneValue);
        findFormatArgs(formatArgs, builtin.twoValue);
        findFormatArgs(formatArgs, builtin.fewValue);
        findFormatArgs(formatArgs, builtin.manyValue);
        findFormatArgs(formatArgs, builtin.otherValue);
        pluralizedValue = string.translated ? new TdApi.LanguagePackStringValuePluralized(plural.zeroValue, plural.oneValue, plural.twoValue, plural.fewValue, plural.manyValue, plural.otherValue) : new TdApi.LanguagePackStringValuePluralized();
        for (Lang.PluralizationForm form : args.langPack.untranslatedRules.forms) {
          String value = form.get(builtin);
          SpannableStringBuilder originalValue = new SpannableStringBuilder();
          args.langPack.makeString(value, originalValue, true, -1);
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.description, 0, originalValue, false));
        }
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        for (Lang.PluralizationForm form : args.langPack.rules.forms) {
          String value = string.translated ? form.get(plural) : null;
          items.add(new ListItem(ListItem.TYPE_EDITTEXT, getFormId(form.form), 0, getFormPlaceholder(form), false).setStringValue(value));
        }
        break;
      }
      case TdApi.LanguagePackStringValueDeleted.CONSTRUCTOR:
      default:
        throw new IllegalArgumentException(string.string.toString());
    }

    adapter.setItems(items, false);

    fillHotKeys(formatArgs);
  }

  private void fillHotKeys (Set<String> argSet) {
    hotKeysLayout.removeAllViews();
    if (!argSet.isEmpty()) {
      View.OnClickListener onClickListener = v -> {
        String hotKey = ((TextView) v).getText().toString();
        if (!StringUtils.isEmpty(hotKey) && focusedEditText != null) {
          focusedEditText.getEditText().getText().replace(focusedEditText.getEditText().getSelectionStart(), focusedEditText.getEditText().getSelectionEnd(), hotKey);
        }
      };
      String[] args = new String[argSet.size()];
      argSet.toArray(args);
      Arrays.sort(args);
      for (String string : args) {
        TextView textView = new NoScrollTextView(context);
        textView.setTypeface(Fonts.getRobotoMedium());
        textView.setTextColor(Theme.getColor(R.id.theme_color_background_textLight));
        textView.setText(string);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
        textView.setPadding(Screen.dp(12f), Screen.dp(30f), Screen.dp(12f), Screen.dp(30f));
        textView.setOnClickListener(onClickListener);
        RippleSupport.setTransparentSelector(textView);
        Views.setClickable(textView);
        addThemeTextColorListener(textView, R.id.theme_color_background_textLight);
        hotKeysLayout.addView(textView);
      }
    }
  }

  private void findFormatArgs (Set<String> formatArgs, String lookup) {
    Matcher matcher = getArgumentsStrict().langPack.getPattern().matcher(lookup);
    while (matcher.find()) {
      formatArgs.add(matcher.group());
    }
  }

  private static @IdRes int getFormId (@Lang.PluralForm int form) {
    switch (form) {
      case Lang.PluralForm.FEW:
        return R.id.pluralFew;
      case Lang.PluralForm.MANY:
        return R.id.pluralMany;
      case Lang.PluralForm.ONE:
        return R.id.pluralOne;
      case Lang.PluralForm.OTHER:
        return R.id.pluralOther;
      case Lang.PluralForm.TWO:
        return R.id.pluralTwo;
      case Lang.PluralForm.ZERO:
        return R.id.pluralZero;
    }
    throw new IllegalArgumentException("form == " + form);
  }

  private static CharSequence getFormPlaceholder (Lang.PluralizationForm form) {
    String formStr;
    switch (form.form) {
      case Lang.PluralForm.FEW:
        formStr = "few";
        break;
      case Lang.PluralForm.MANY:
        formStr = "many";
        break;
      case Lang.PluralForm.ONE:
        formStr = "one";
        break;
      case Lang.PluralForm.OTHER:
        formStr = "other";
        break;
      case Lang.PluralForm.TWO:
        formStr = "two";
        break;
      case Lang.PluralForm.ZERO:
        formStr = "zero";
        break;
      default:
        throw new IllegalArgumentException("form == " + form);
    }
    if (form.numbers.length > 0) {
      SpannableStringBuilder b = new SpannableStringBuilder(formStr);
      boolean first = true;
      for (int number : form.numbers) {
        if (first) {
          b.append(": ");
          first = false;
        } else {
          b.append(", ");
        }
        String num = String.valueOf(number);
        int startIndex = b.length();
        b.append(num);
        b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoMedium(), R.id.theme_color_background_textLight), startIndex, startIndex + num.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      return b;
    }
    return formStr;
  }

  private DoubleHeaderView headerCell;

  private void checkHotKeys () {
    String currentInput = focusedEditText != null ? focusedEditText.getText().toString() : null;
    for (int i = 0; i < hotKeysLayout.getChildCount(); i++) {
      TextView textView = (TextView) hotKeysLayout.getChildAt(i);
      String content = textView.getText().toString();
      boolean isEnabled = currentInput != null && !currentInput.contains(content);
      removeThemeListenerByTarget(textView);
      @ThemeColorId int colorId = isEnabled ? R.id.theme_color_textLink : R.id.theme_color_background_textLight;
      addThemeTextColorListener(textView, colorId);
      textView.setTextColor(Theme.getColor(colorId));
    }
  }

  @Override
  public View getCustomHeaderCell () {
    return headerCell;
  }

  private boolean exitOnSave;
  private MaterialEditTextGroup focusedEditText;
  private LinearLayout hotKeysLayout;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    Args args = getArgumentsStrict();
    Lang.Pack langPack = args.langPack;
    headerCell = new DoubleHeaderView(context);
    headerCell.setThemedTextColor(this);
    headerCell.initWithMargin(Screen.dp(49f), true);
    headerCell.setTitle(langPack.languageInfo.nativeName);
    exitOnSave = (args.string.translated || args.langPack.getUntranslatedCount() == 1) && (args.stack == null || args.stack.indexOf(args.string) == -1);
    fastAnimation = args.stack != null;

    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        String key = getArgumentsStrict().string.getKey();
        boolean numeric = key.equals("language_rtl") || key.equals("language_disable_lowercase");
        editText.getEditText().setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Views.setSingleLine(editText.getEditText(), false);
      }

      @Override
      public void onFocusChanged (MaterialEditTextGroup v, boolean isFocused) {
        super.onFocusChanged(v, isFocused);
        if (isFocused) {
          focusedEditText = v;
          checkHotKeys();
        } else if (focusedEditText == v) {
          focusedEditText = null;
          checkHotKeys();
        }
      }
    };
    adapter.setLockFocusOn(this, true);
    adapter.setTextChangeListener(this);

    FrameLayout.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
    params.rightMargin = Screen.dp(72f);
    hotKeysLayout = new LinearLayout(context);
    hotKeysLayout.setGravity(Gravity.CENTER_HORIZONTAL);
    hotKeysLayout.setOrientation(LinearLayout.HORIZONTAL);
    hotKeysLayout.setLayoutParams(params);
    contentView.addView(hotKeysLayout);

    buildCells();

    recyclerView.setAdapter(adapter);

    setDoneIcon(exitOnSave ? R.drawable.baseline_check_24 : R.drawable.baseline_arrow_forward_24);
    setDoneVisible(true);
  }

  private boolean saveString () {
    Args args = getArgumentsStrict();
    Lang.PackString string = args.string;
    String key = args.string.getKey();
    switch (args.string.string.value.getConstructor()) {
      case TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR: {
        String value = this.value.value;
        if (StringUtils.isEmpty(value)) {
          string.translated = false;
          ((TdApi.LanguagePackStringValueOrdinary) string.string.value).value = string.getBuiltinValue().value;
        } else {
          string.translated = true;
          ((TdApi.LanguagePackStringValueOrdinary) string.string.value).value = value;
        }
        if (key.equals("language_nameInEnglish")) {
          args.langPack.languageInfo.name = value;
        } else if (key.equals("language_name")) {
          args.langPack.languageInfo.nativeName = value;
        }
        break;
      }
      case TdApi.LanguagePackStringValuePluralized.CONSTRUCTOR: {
        int emptyCount = 0;
        for (Lang.PluralizationForm form : args.langPack.rules.forms) {
          if (StringUtils.isEmpty(form.get(pluralizedValue))) {
            emptyCount++;
          }
        }
        int totalCount = args.langPack.rules.forms.size();
        if (emptyCount == totalCount) {
          string.translated = false;
          copyValue((TdApi.LanguagePackStringValuePluralized) string.string.value, Lang.getBuiltinStringPluralized(string.getKey(), args.langPack.untranslatedRules.forms));
        } else if (emptyCount > 0) {
          for (Lang.PluralizationForm form : args.langPack.rules.forms) {
            if (StringUtils.isEmpty(form.get(pluralizedValue))) {
              int i = adapter.indexOfViewById(getFormId(form.form));
              if (i != -1) {
                View view = recyclerView.getLayoutManager().findViewByPosition(i);
                view = view instanceof ViewGroup ? ((ViewGroup) view).getChildAt(0) : null;
                if (view instanceof MaterialEditTextGroup) {
                  ((MaterialEditTextGroup) view).setInErrorState(true);
                }
              }
            }
          }
          return false;
        } else {
          string.translated = true;
          copyValue((TdApi.LanguagePackStringValuePluralized) string.string.value, pluralizedValue);
        }
        break;
      }
      case TdApi.LanguagePackStringValueDeleted.CONSTRUCTOR:
      default:
        throw new IllegalArgumentException(args.string.string.toString());
    }
    args.delegate.onLanguageStringChanged(args.langPack, string);
    return true;
  }

  private static void copyValue (TdApi.LanguagePackStringValuePluralized to, TdApi.LanguagePackStringValuePluralized from) {
    to.zeroValue = from.zeroValue;
    to.oneValue = from.oneValue;
    to.twoValue = from.twoValue;
    to.fewValue = from.fewValue;
    to.manyValue = from.manyValue;
    to.otherValue = from.otherValue;
  }

  @Override
  public boolean onDoneClick (View v) {
    if (saveString()) {
      if (exitOnSave) {
        onSaveCompleted();
      } else {
        navigateToNextString();
      }
    }
    return true;
  }

  private void navigateToNextString () {
    final Args args = getArgumentsStrict();
    final Args nextArgs;
    List<Lang.PackString> stack = args.stack;
    int existingIndex = stack != null ? stack.indexOf(args.string) : -1;
    if (existingIndex != -1 && existingIndex < stack.size() - 1) {
      nextArgs = new Args(args.delegate, args.langPack, args.stack.get(existingIndex + 1));
      nextArgs.stack = stack;
    } else {
      Lang.PackString nextString = args.langPack.findNextUntranslatedString(args.string);
      if (nextString == null) {
        UI.showToast("No more untranslated strings found", Toast.LENGTH_SHORT);
        return;
      }
      if (existingIndex == -1) {
        if (stack == null)
          stack = new ArrayList<>();
        stack.add(args.string);
      }
      nextArgs = new Args(args.delegate, args.langPack, nextString);
      nextArgs.stack = stack;
    }
    EditLanguageController c = new EditLanguageController(context, tdlib);
    c.setArguments(nextArgs);
    navigateTo(c);
  }

  @Override
  protected boolean swipeNavigationEnabled () {
    return !madeChanges && getArgumentsStrict().stack == null;
  }

  private boolean navigateToPreviousString () {
    Args args = getArgumentsStrict();
    List<Lang.PackString> stack = args.stack;
    if (stack != null && !stack.isEmpty()) {
      int i = stack.indexOf(args.string);
      if (i == -1) {
        i = stack.size();
      }
      if (i > 0) {
        Args nextArgs = new Args(args.delegate, args.langPack, args.stack.get(i - 1));
        nextArgs.stack = stack;
        EditLanguageController c = new EditLanguageController(context, tdlib);
        c.setArguments(nextArgs);
        navigateTo(c);
        return true;
      }
    }
    return false;
  }

  private void exit (boolean forceBack) {
    showOptions(Lang.getStringBold(R.string.LocalizationEditConfirmPrompt, getArgumentsStrict().string.getKey()), new int[] {R.id.btn_save, R.id.btn_discard, R.id.btn_cancel}, new String[] {Lang.getString(R.string.LocalizationEditConfirmSave), Lang.getString(R.string.LocalizationEditConfirmDiscard), Lang.getString(R.string.Cancel)}, new int[] {OPTION_COLOR_BLUE, OPTION_COLOR_RED, OPTION_COLOR_NORMAL}, new int[] {R.drawable.baseline_check_24, R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      switch (id) {
        case R.id.btn_save:
        case R.id.btn_discard: {
          if (id == R.id.btn_save) {
            if (!saveString()) {
              return true;
            }
          }
          if (forceBack || !navigateToPreviousString())
            navigateBack();
          break;
        }
      }
      return true;
    });
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (madeChanges) {
      exit(false);
      return true;
    }
    return navigateToPreviousString() || super.onBackPressed(fromTop);
  }

  private boolean fastAnimation;

  @Override
  protected boolean forceFastAnimation () {
    return fastAnimation;
  }

  @Override
  public void onFocus () {
    super.onFocus();
    fastAnimation = false;
    destroyStackItemByIdExcludingLast(getId());
  }

  @Override
  protected int getMenuId () {
    return R.id.menu_editLangPackString;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    switch (id) {
      case R.id.menu_editLangPackString: {
        header.addButton(menu, R.id.menu_btn_view, getHeaderIconColorId(), this, R.drawable.baseline_open_in_browser_24, Screen.dp(49f), R.drawable.bg_btn_header);
        header.addMoreButton(menu, this, getHeaderIconColorId());
        break;
      }
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    switch (id) {
      case R.id.menu_btn_view: {
        String url = TD.getLanguageKeyLink(getArgumentsStrict().string.getKey());
        if (!Intents.openInAppBrowser(context, Uri.parse(url), true)) {
          tdlib.ui().openUrl(this, url, new TdlibUi.UrlOpenParameters().disableInstantView());
        }
        break;
      }
      case R.id.menu_btn_more: {
        IntList ids = new IntList(3);
        StringList strings = new StringList(3);

        ids.append(R.id.btn_copyLink);
        strings.append(R.string.CopyLink);

        if (getArgumentsStrict().string.string.value.getConstructor() == TdApi.LanguagePackStringValueOrdinary.CONSTRUCTOR) {
          ids.append(R.id.btn_copyText);
          strings.append(R.string.LocalizationCopy);
          if (focusedEditText != null && StringUtils.isEmpty(value.value)) {
            ids.append(R.id.btn_pasteText);
            strings.append(R.string.LocalizationPaste);
          }
        }
        if (!swipeNavigationEnabled()) {
          ids.append(R.id.btn_close);
          strings.append(R.string.LocalizationExit);
        }

        showMore(ids.get(), strings.get(), 0);
        break;
      }
    }
  }

  private String getBuiltinValueOrdinary () {
    if (getArgumentsStrict().string.translated) {
      return getArgumentsStrict().string.getBuiltinValue().value;
    } else {
      return ((TdApi.LanguagePackStringValueOrdinary) getArgumentsStrict().string.string.value).value;
    }
  }

  @Override
  public void onMoreItemPressed (int id) {
    switch (id) {
      case R.id.btn_copyLink: {
        UI.copyText(TD.getLanguageKeyLink(getArgumentsStrict().string.getKey()), R.string.CopiedLink);
        break;
      }
      case R.id.btn_copyText: {
        UI.copyText(getBuiltinValueOrdinary(), R.string.CopiedText);
        break;
      }
      case R.id.btn_pasteText: {
        if (focusedEditText != null) {
          String text = getBuiltinValueOrdinary();
          if (!StringUtils.isEmpty(text)) {
            focusedEditText.getEditText().getText().replace(focusedEditText.getEditText().getSelectionStart(), focusedEditText.getEditText().getSelectionEnd(), text);
            madeChanges = true;
          }
        }
        break;
      }
      case R.id.btn_close: {
        exit(true);
        break;
      }
    }
  }

  private boolean madeChanges;

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    switch (id) {
      case R.id.string:
      case R.id.pluralZero:
      case R.id.pluralOne:
      case R.id.pluralTwo:
      case R.id.pluralFew:
      case R.id.pluralMany:
      case R.id.pluralOther:
        madeChanges = true;
        break;
    }
    switch (id) {
      case R.id.string:
        value.value = text;
        break;
      case R.id.pluralZero:
        pluralizedValue.zeroValue = text;
        break;
      case R.id.pluralOne:
        pluralizedValue.oneValue = text;
        break;
      case R.id.pluralTwo:
        pluralizedValue.twoValue = text;
        break;
      case R.id.pluralFew:
        pluralizedValue.fewValue = text;
        break;
      case R.id.pluralMany:
        pluralizedValue.manyValue = text;
        break;
      case R.id.pluralOther:
        pluralizedValue.otherValue = text;
        break;
    }
    v.setInErrorState(false);
    checkHotKeys();
  }
}
