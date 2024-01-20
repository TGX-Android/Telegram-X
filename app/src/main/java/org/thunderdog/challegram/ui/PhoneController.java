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
 * File created on 21/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.SparseArrayCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibAccount;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.PorterDuffColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Intents;
import org.thunderdog.challegram.tool.Keyboard;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.TGCountry;
import org.thunderdog.challegram.tool.TGPhoneFormat;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.NoUnderlineClickableSpan;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.td.Td;

public class PhoneController extends EditBaseController<Void> implements SettingsAdapter.TextChangeListener, MaterialEditTextGroup.FocusListener, MaterialEditTextGroup.TextChangeListener, View.OnClickListener, Menu {

  public static final int MODE_LOGIN = 0;
  public static final int MODE_CHANGE_NUMBER = 1;
  public static final int MODE_ADD_CONTACT = 2;

  public PhoneController (Context context, Tdlib tdlib) {
    super(context, tdlib);
    if (tdlib == null)
      throw new IllegalArgumentException();
  }

  @Override
  public int getId () {
    return R.id.controller_phone;
  }

  @Override
  public CharSequence getName () {
    String result;
    switch (mode) {
      case MODE_ADD_CONTACT:
        result = Lang.getString(R.string.AddContact);
        break;
      case MODE_CHANGE_NUMBER:
        result = Lang.getString(R.string.NewNumber);
        break;
      case MODE_LOGIN:
        result = Lang.getString(isAccountAdd ? R.string.AddAccount : R.string.YourPhone);
        break;
      default:
        throw new IllegalArgumentException("mode == " + mode);
    }
    return Lang.getDebugString(result, tdlib != null && tdlib.account().isDebug());
  }

  @Override
  protected int getMenuId () {
    return mode == MODE_LOGIN && !isAccountAdd ? R.id.menu_login : 0;
  }

  @Override
  public void fillMenuItems (int id, HeaderView header, LinearLayout menu) {
    if (id == R.id.menu_login) {
      header.addButton(menu, R.id.btn_proxy, R.drawable.baseline_security_24, ColorId.headerIcon, this, Screen.dp(48f));
      header.addButton(menu, R.id.btn_languageSettings, R.drawable.baseline_language_24, ColorId.headerIcon, this, Screen.dp(48f));
    }
  }

  @Override
  public void onMenuItemPressed (int id, View view) {
    if (id == R.id.btn_proxy) {
      tdlib.ui().openProxySettings(this, true);
    } else if (id == R.id.btn_languageSettings) {
      navigateTo(new SettingsLanguageController(context, tdlib));
    }
  }

  @Override
  public void handleLanguagePackEvent (int event, int arg1) {
    if (adapter != null) {
      switch (event) {
        case Lang.EVENT_PACK_CHANGED:
        case Lang.EVENT_DIRECTION_CHANGED:
          adapter.notifyAllStringsChanged();
          break;
        case Lang.EVENT_STRING_CHANGED:
          adapter.notifyStringChanged(arg1);
          break;
        case Lang.EVENT_DATE_FORMAT_CHANGED:
          // Nothing to change
          break;
      }
    }
    if (countryView != null) {
      if (event == Lang.EVENT_PACK_CHANGED || (event == Lang.EVENT_STRING_CHANGED && arg1 == R.string.Country)) {
        countryView.setHint(R.string.Country);
      }
    }
    if (numberView != null) {
      if (event == Lang.EVENT_PACK_CHANGED || (event == Lang.EVENT_STRING_CHANGED && arg1 == getNumberHint())) {
        numberView.setHint(getNumberHint());
      }
    }
  }

  @Override
  protected int getBackButton () {
    return (mode == MODE_LOGIN && isAccountAdd) || mode == MODE_ADD_CONTACT || mode == MODE_CHANGE_NUMBER || (!Config.REMOVE_INTRO && stackSize() > 0 && stackItemAt(0) instanceof IntroController) ? BackHeaderButton.TYPE_BACK : BackHeaderButton.TYPE_NONE;
  }

  private int mode;
  private boolean isAccountAdd;

  public boolean isAccountAdd () {
    return isAccountAdd;
  }

  public void setIsAccountAdd (boolean isAdd) {
    if (isAdd && mode != MODE_LOGIN) {
      throw new IllegalStateException();
    }
    this.isAccountAdd = isAdd;
  }

  @Override
  public boolean isUnauthorized () {
    return mode == MODE_LOGIN;
  }

  public PhoneController setMode (int mode) {
    this.mode = mode;
    return this;
  }

  private String initialPhoneNumber, initialFirstName, initialLastName;

  public void setInitialData (String phoneNumber, String firstName, String lastName) {
    this.initialPhoneNumber = phoneNumber;
    this.initialFirstName = firstName;
    this.initialLastName = lastName;
  }

  private SettingsAdapter adapter;
  private ArrayList<ListItem> baseItems;

  private MaterialEditTextGroup countryView, codeView, numberView;
  private ListItem hintItem;
  private ListItem firstNameItem, lastNameItem;

  private SparseArrayCompat<String> storedValues;

  private boolean oneShotFocusSet;

  @Override
  protected void onCreateView (final Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    int headerHeight = Screen.dp(72f);

    ((FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams()).topMargin = headerHeight;

    if (mode != MODE_ADD_CONTACT) {
      setDoneIcon(R.drawable.baseline_arrow_forward_24);
    }

    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    storedValues = new SparseArrayCompat<>(3);

    if (mode != MODE_ADD_CONTACT) {
      String[] phoneNumber = TGCountry.instance().getNumber(tdlib);
      if (phoneNumber != null) {
        storedValues.put(R.id.login_code, phoneNumber[0]);
        storedValues.put(R.id.login_phone, phoneNumber[1]);

        String[] country = currentCountry = TGCountry.instance().get(phoneNumber[0]);
        if (country != null) {
          storedValues.put(R.id.login_country, country[2]);
        }
      }
    } else if (!StringUtils.isEmpty(initialPhoneNumber)) {
      String countryCode = null;
      String phoneNumber = initialPhoneNumber;
      String numbers = Strings.getNumber(initialPhoneNumber);
      if (countryCode == null) {
        String formattedPhone = Strings.formatPhone(numbers);
        int i = formattedPhone.indexOf(' ');
        if (i != -1) {
          countryCode = formattedPhone.substring(1, i);
          phoneNumber = formattedPhone.substring(i + 1);
        }
      }
      if (countryCode == null) {
        int i = Strings.indexOfNumber(initialPhoneNumber);
        if (i != -1) {
          int codeSize = 0;
          int numbersLen = numbers.length();
          int len = initialPhoneNumber.length();
          for (int j = i; j < len; ) {
            int codePoint = initialPhoneNumber.codePointAt(j);
            int size = Character.charCount(codePoint);
            if (size != 1 || !StringUtils.isNumeric((char) codePoint)) {
              break;
            }
            codeSize += size;
            countryCode = initialPhoneNumber.substring(0, codeSize);
            if (codeSize == 4) {
              break;
            }
            if (TGPhoneFormat.getExpectedNumberLength(countryCode) == numbersLen) {
              break;
            }
            j += size;
          }
          phoneNumber = initialPhoneNumber.substring(codeSize);
        }
      }

      if (!StringUtils.isEmpty(countryCode)) {
        storedValues.put(R.id.login_code, countryCode);
        String[] current = currentCountry = TGCountry.instance().get(countryCode);
        if (current != null) {
          storedValues.put(R.id.login_country, current[2]);
        }
      }

      storedValues.put(R.id.login_phone, phoneNumber);
    }

    if (currentCountry == null && StringUtils.isEmpty(initialPhoneNumber)) {
      String[] current = currentCountry = TGCountry.instance().getCurrent();
      if (current != null) {
        storedValues.put(R.id.login_code, current[0]);
        storedValues.put(R.id.login_country, current[2]);
      }
    }

    FrameLayoutFix countryWrap = new FrameLayoutFix(context);
    countryWrap.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), 0);
    countryWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerHeight, Gravity.TOP));

    String countryText = storedValues.get(R.id.login_country, "");
    countryView = new MaterialEditTextGroup(context);
    countryView.addThemeListeners(this);
    countryView.setUseTextChangeAnimations();
    countryView.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
    countryView.getEditText().setImeOptions(EditorInfo.IME_ACTION_DONE);
    countryView.setDoneListener(v -> {
      if (countryView.isEmpty()) {
        setInCountryMode(false);
        updateCountry("", true);
        codeView.setText("");
      } else if (currentResults != null && !currentResults.isEmpty() && currentResults.get(0).getViewType() == ListItem.TYPE_COUNTRY) {
        ListItem item = currentResults.get(0);
        setCountry(item);
      } else {
        setInCountryMode(false);
        updateCountry(storedValues.get(R.id.login_code, ""), true);
      }
      return true;
    });
    countryView.setHint(R.string.Country);
    countryView.getEditText().setId(R.id.login_country);
    countryView.getEditText().setNextFocusDownId(R.id.login_code);
    countryView.setText(countryText);
    countryView.setTextListener(this);
    countryView.setFocusListener(this);
    countryWrap.addView(countryView);

    contentView.addView(countryWrap);

    this.baseItems = new ArrayList<>(3);
    this.baseItems.add(new ListItem(ListItem.TYPE_CUSTOM_SINGLE));
    if (mode == MODE_ADD_CONTACT) {
      this.baseItems.add(firstNameItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE, R.id.edit_first_name, 0, R.string.login_FirstName).setStringValue(initialFirstName));
      this.baseItems.add(lastNameItem = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING_REUSABLE, R.id.edit_last_name, 0, R.string.login_LastName).setStringValue(initialLastName));
    }
    hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, mode == MODE_ADD_CONTACT ? 0 : mode == MODE_CHANGE_NUMBER ? R.string.ChangePhoneHelp : R.string.login_SmsHint).setTextColorId(ColorId.textLight);
    if (mode != MODE_ADD_CONTACT) {
      this.baseItems.add(hintItem);
    }

    if (isAccountAdd) {
      // this.baseItems.add(new SettingItem(SettingItem.TYPE_CHECKBOX_OPTION_REVERSE, R.id.btn_syncContacts, 0, R.string.SyncContacts, R.id.btn_syncContacts, true));
    }

    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrollStateChanged (RecyclerView recyclerView, int newState) {
        if (newState != RecyclerView.SCROLL_STATE_IDLE && getLockFocusView() == countryView.getEditText()) {
          Keyboard.hide(countryView.getEditText());
        }
      }
    });

    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        editText.getEditText().setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        editText.setDoneListener(item.getId() == R.id.edit_last_name ? ignoredV -> makeRequest() : null);
        if (!oneShotFocusSet && mode == MODE_ADD_CONTACT && StringUtils.isEmpty(initialPhoneNumber) && item.getId() == R.id.edit_first_name) {
          setLockFocusView(editText.getEditText());
          oneShotFocusSet = true;
        }
      }

      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        if (item.getId() == R.id.btn_syncContacts) {
          // Do smth?
        }
      }

      @Override
      protected SettingHolder initCustom (ViewGroup parent) {
        final FrameLayoutFix numberWrap = new FrameLayoutFix(context);
        numberWrap.setPadding(Screen.dp(16f), Screen.dp(6f), Screen.dp(16f), 0);
        numberWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, Screen.dp(76f)));

        FrameLayoutFix.LayoutParams params;

        params = FrameLayoutFix.newParams(Screen.dp(18f), Screen.dp(40f));
        params.topMargin = Screen.dp(20f);
        TextView plusView = new NoScrollTextView(context);
        plusView.setText("+");
        plusView.setTextColor(Theme.textAccentColor());
        addThemeTextAccentColorListener(plusView);
        plusView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        plusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
        plusView.setLayoutParams(params);
        numberWrap.addView(plusView);

        params = FrameLayoutFix.newParams(Screen.dp(50f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT);
        params.leftMargin = Screen.dp(18f);

        String codeText = storedValues.get(R.id.login_code, "");

        codeView = new MaterialEditTextGroup(context);
        codeView.addThemeListeners(PhoneController.this);
        codeView.setLayoutParams(params);
        codeView.getEditText().setId(R.id.login_code);
        codeView.getEditText().setNextFocusDownId(R.id.login_phone);
        codeView.getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
        codeView.getEditText().setFilters(new InputFilter[]{
          new InputFilter.LengthFilter(4)
        });
        codeView.setFocusListener(PhoneController.this);
        codeView.setText(codeText);
        codeView.setTextListener(PhoneController.this);
        numberWrap.addView(codeView);

        params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT);
        params.leftMargin = Screen.dp(89f);

        String numberText = storedValues.get(R.id.login_country, "");
        numberView = new MaterialEditTextGroup(context);
        numberView.addThemeListeners(PhoneController.this);
        numberView.getEditText().setBackspaceListener((v, editable, selectionStart, selectionEnd) -> {
          if (editable.length() == 0) {
            Keyboard.show(codeView.getEditText());
            return true;
          }
          if (selectionStart < 0 || selectionEnd < 0) {
            return false;
          }
          int selectedCount = selectionEnd - selectionStart;
          if (selectedCount != 0) {
            return false;
          }
          if (selectionStart == 0) {
            Keyboard.show(codeView.getEditText());
            return true;
          }
          int foundNumber = -1;
          int checkIndex = selectionStart;
          int numBefore = 0;
          while (--checkIndex >= 0) {
            if (StringUtils.isNumeric(editable.charAt(checkIndex))) {
              if (foundNumber == -1) {
                foundNumber = checkIndex;
              } else {
                numBefore++;
              }
            }
          }
          if (foundNumber == -1) {
            Keyboard.show(codeView.getEditText());
            return true;
          }
          if (numBefore == 0) {
            editable.delete(0, selectionStart);
            return true;
          }
          if (currentCountry == null) {
            return false;
          }

          ignorePhoneChanges = true;

          editable.delete(foundNumber, selectionStart);

          String text = editable.toString();
          CharSequence code = codeView.getText();
          String number = Strings.getNumber(text);
          String expected = Strings.formatNumberPart(code.toString(), number);

          if (!text.equals(expected)) {
            Views.replaceText(editable, text, expected);
            int bestIndex = -1;
            int remain = numBefore;
            int len = editable.length();
            for (int i = 0; i < len; i++) {
              if (StringUtils.isNumeric(editable.charAt(i))) {
                if (--remain == 0) {
                  bestIndex = i + 1;
                  break;
                }
              }
            }
            if (bestIndex != -1) {
              int selectionIndex = bestIndex;
              tdlib.ui().post(() -> {
                v.setSelection(selectionIndex);
              });
            }
          }


          ignorePhoneChanges = false;



          return true;
        });
        numberView.setHint(getNumberHint());
        numberView.setLayoutParams(params);
        numberView.getEditText().setId(R.id.login_phone);
        numberView.getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
        numberView.setFocusListener(PhoneController.this);
        numberView.setText(numberText);
        if (mode == MODE_ADD_CONTACT) {
          numberView.setNextFocusDownId(R.id.edit_first_name);
        } else {
          numberView.getEditText().setImeOptions(EditorInfo.IME_ACTION_DONE);
          numberView.setDoneListener(v -> makeRequest());
        }
        numberView.setTextListener(PhoneController.this);
        numberWrap.addView(numberView);

        if (mode != MODE_ADD_CONTACT || StringUtils.isEmpty(initialPhoneNumber)) {
          setLockFocusView(codeText.isEmpty() ? codeView.getEditText() : numberView.getEditText());
        }

        if (UI.inTestMode() && isFocused()) {
          makeTestRequest();
        }

        return new SettingHolder(numberWrap);
      }

      @Override
      protected void setCustom (ListItem item, SettingHolder holder, int position) {
        ViewGroup group = (ViewGroup) holder.itemView;
        MaterialEditTextGroup codeView = (MaterialEditTextGroup) group.getChildAt(1);
        codeView.setText(storedValues.get(R.id.login_code, ""));
        MaterialEditTextGroup numberView = (MaterialEditTextGroup) group.getChildAt(2);
        numberView.setText(storedValues.get(R.id.login_phone, ""));
      }
    };
    adapter.setLockFocusOn(this, true);
    if (mode == MODE_ADD_CONTACT) {
      adapter.setTextChangeListener(this);
    }
    adapter.setItems(baseItems, isAccountAdd);
    recyclerView.setAdapter(adapter);
  }

  private @StringRes int getNumberHint () {
    return mode == MODE_CHANGE_NUMBER ? R.string.NewPhone : R.string.login_PhoneNumber;
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_syncContacts) {
      adapter.toggleView(v);
    } else if (viewId == R.id.result) {
      ListItem item = (ListItem) v.getTag();
      if (item != null && item.getData() != null) {
        setCountry(item);
      }
    }
  }

  private void setCountry (ListItem item) {
    String country = item.getString().toString();
    String code = ((String) item.getData()).substring(1);
    storedValues.put(R.id.login_code, code);
    countryView.setText(country, true);
    setInCountryMode(false);
    Keyboard.show(numberView.getEditText());
    UI.post(() -> setIsCountryDefault(true));
  }

  @Override
  public void onFocusChanged (MaterialEditTextGroup v, boolean isFocused) {
    if (isFocused) {
      setLockFocusView(v.getEditText());
      if (v.getEditText().getId() == R.id.login_country) {
        setInCountryMode(true);
        v.post(() -> Views.selectAll(v.getEditText()));
        // v.postDelayed(() -> Views.selectAll(v.getEditText()), 10);
      }
    }
  }

  @Override
  public boolean onBackPressed (boolean fromTop) {
    if (inCountryMode) {
      setInCountryMode(false);
      updateCountry(storedValues.get(R.id.login_code, ""), isCountryDefault);
      return true;
    }
    return false;
  }

  private boolean ignorePhoneChanges;

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    if (id == R.id.edit_first_name) {
      updateDoneState();
    }
  }

  @Override
  public void onTextChanged (MaterialEditTextGroup v, CharSequence charSequence) {
    String text = charSequence.toString();
    final int inputId = v.getEditText().getId();
    if (inputId == R.id.login_country) {
      if (inCountryMode) {
        setIsCountryDefault(false);
      }
      searchCountry(text.trim().toLowerCase());
    } else if (inputId == R.id.login_code) {
      String prevValue = storedValues.get(R.id.login_code);
      if (prevValue != null && StringUtils.equalsOrBothEmpty(prevValue, text)) {
        return;
      }
      storedValues.put(v.getEditText().getId(), text);
      String numeric = Strings.getNumber(text);
      if (!text.equals(numeric)) {
        codeView.setText(numeric);
      } else {
        updateCountry(numeric, true);
        checkNumber(numberView.getEditText().getText().toString());
        updateDoneState();
      }
      showError(null);
      if (text.length() == 4 && codeView.getEditText().getSelectionEnd() == text.length()) {
        Keyboard.show(numberView.getEditText());
      }
    } else if (inputId == R.id.login_phone) {
      storedValues.put(v.getEditText().getId(), text);
      if (!ignorePhoneChanges) {
        checkNumber(text);
        updateDoneState();
      }
      showError(null);
    }
  }

  private void checkNumber (String text) {
    String number = Strings.getNumber(text);
    CharSequence code = codeView.getText();
    String expected = currentCountry != null ? Strings.formatNumberPart(code.toString(), number) : number;
    if (!text.equals(expected)) {
      ignorePhoneChanges = true;
      Views.replaceText(numberView.getEditText().getText(), text, expected);
      ignorePhoneChanges = false;
    }
  }

  private String[] currentCountry;
  private boolean isCountryDefault = true;

  private void setIsCountryDefault (boolean isDefault) {
    this.isCountryDefault = isDefault;
  }

  private void updateCountry (String code, boolean isDefault) {
    String[] country = currentCountry = TGCountry.instance().get(code);
    setIsCountryDefault(isDefault);
    countryView.setText(country != null ? country[2] : null, true);
  }

  private boolean hasValidNumber () {
    CharSequence code = codeView.getText();
    CharSequence number = numberView.getText();
    return code.length() > 0 && number.length() > 0;
  }

  private boolean hasValidFirstName () {
    String firstName = getFirstName();
    return !StringUtils.isEmpty(firstName) && firstName.trim().length() > 0;
  }

  private void updateDoneState () {
    setDoneVisible(hasValidNumber() && !inCountryMode && (mode != MODE_ADD_CONTACT || hasValidFirstName()));
  }

  private String lastSearchPrefix;

  private boolean areResults;
  private ArrayList<ListItem> currentResults;

  private void setItems (ArrayList<ListItem> items, boolean results) {
    boolean changedState = this.areResults != results;
    this.areResults = results;
    currentResults = results ? items : null;
    if (changedState) {
      recyclerView.setItemAnimator(itemAnimator);
      adapter.setItems(items, false);
      if (results) {
        UI.post(() -> {
          if (inCountryMode) {
            recyclerView.setItemAnimator(null);
          }
        }, 360l);
      }
    } else if (results) {
      if (items.get(0).getViewType() == ListItem.TYPE_EMPTY && adapter.getItems().get(0).getViewType() == ListItem.TYPE_EMPTY) {
        return;
      }
      if (items.size() == adapter.getItems().size()) {
        int i = 0;
        boolean ok = true;
        for (ListItem item : items) {
          if (adapter.getItems().get(i++).getData() != item.getData()) {
            ok = false;
            break;
          }
        }
        if (ok) {
          return;
        }
      }
      adapter.replaceItems(items);
      ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(0, 0);
    }
  }

  private void searchCountry (@Nullable final String inputPrefix) {
    String prefix = StringUtils.isEmpty(inputPrefix) || !isCountryDefault ? inputPrefix : "";
    lastSearchPrefix = prefix;
    if (prefix == null) {
      setItems(baseItems, false);
      return;
    }
    Background.instance().post(() -> {
      String[][] countries = TGCountry.instance().getAll();
      String number = Strings.getNumber(prefix);
      // ArrayList<String[]> result = new ArrayList<>(countries.length);
      int[] level = new int[1];
      final ArrayList<ListItem> results = new ArrayList<>(countries.length + 1);
      final Comparator<ListItem> comparator = (o1, o2) -> {
        int level1 = o1.getSliderValue();
        int level2 = o2.getSliderValue();

        if (level1 != level2) {
          return level1 < level2 ? -1 : 1;
        }

        String c1 = o1.getString().toString();
        String c2 = o2.getString().toString();

        int cmp = c1.compareTo(c2);

        return cmp != 0 ? cmp : ((String) o1.getData()).compareTo((String) o2.getData());
      };

      for (String[] country : countries) {
        String name = country[2].toLowerCase();
        if (!number.isEmpty() && country[0].startsWith(number)) {
          level[0] = -1;
        } else if (!Strings.anyWordStartsWith(name, prefix, level)) {
          String clean = Strings.clean(name);
          if (StringUtils.equalsOrBothEmpty(name, clean) || !Strings.anyWordStartsWith(clean, prefix, level)) {
            continue;
          }
        }
        ListItem item = new ListItem(ListItem.TYPE_COUNTRY, R.id.result, 0, country[2], false).setData("+" + country[0]).setSliderInfo(null, level[0]);
        int i = Collections.binarySearch(results, item, comparator);
        if (i < 0) {
          results.add(-(++i), item);
        }
      }

      if (results.isEmpty()) {
        results.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.RegionNotFound));
      } else {
        //results.add(new SettingItem(SettingItem.TYPE_EMPTY_OFFSET_SMALL));
        /*for (String[] country : result) {
          results.add();
        }*/
      }

      runOnUiThreadOptional(() -> {
        if (prefix.equals(lastSearchPrefix) && inCountryMode) {
          setItems(results, true);
        }
      });
    });
  }

  private boolean inCountryMode;

  private void setInCountryMode (final boolean inCountryMode) {
    if (this.inCountryMode != inCountryMode) {
      this.inCountryMode = inCountryMode;
      countryView.getEditText().setNextFocusDownId(inCountryMode ? View.NO_ID : R.id.login_code);
      if (inCountryMode) {
        updateDoneState();
      } else {
        Keyboard.show(codeView.isEmpty() ? codeView.getEditText() : numberView.getEditText());
        UI.post(() -> {
          if (!PhoneController.this.inCountryMode) {
            if (isFocused()) {
              Keyboard.show(codeView.isEmpty() ? codeView.getEditText() : numberView.getEditText());
            }
            updateDoneState();
          }
        }, 360l);
      }
      searchCountry(inCountryMode ? countryView.getText().toString().trim().toLowerCase() : null);
    }
  }

  private void updateHint (boolean useOffsetLeft, CharSequence text, boolean isError) {
    int offsetLeft = useOffsetLeft ? Screen.dp(89f) : 0;
    @PorterDuffColorId int textColorId = isError ? ColorId.textNegative : ColorId.textLight;
    if (offsetLeft != hintItem.getTextPaddingLeft() || hintItem.getTextColorId(ColorId.background_textLight) != textColorId || !StringUtils.equalsOrBothEmpty(hintItem.getString(), text)) {
      hintItem.setTextPaddingLeft(offsetLeft);
      hintItem.setTextColorId(textColorId);
      hintItem.setString(text);
      if (!inCountryMode) {
        adapter.updateItem(baseItems.indexOf(hintItem));
      }
      numberView.setInErrorState(isError);
    }
  }

  private void showError (int res) {
    showError(Lang.getString(res));
  }

  private void showError (@Nullable CharSequence res) {
    updateHint(res != null, res != null ? res : mode == MODE_ADD_CONTACT ? null : Lang.getString(mode == MODE_CHANGE_NUMBER ? R.string.ChangePhoneHelp : R.string.login_SmsHint), res != null);
  }

  // Logic stuff

  @Override
  protected boolean onDoneClick () {
    return makeRequest();
  }

  @Override
  protected void onProgressStateChanged (boolean inProgress) {
    countryView.setBlockedText(inProgress ? countryView.getText().toString() : null);
    numberView.setBlockedText(inProgress ? numberView.getText().toString() : null);
    codeView.setBlockedText(inProgress ? codeView.getText().toString() : null);
    if (mode != MODE_ADD_CONTACT) {
      setStackLocked(inProgress);
    }
  }

  @Override
  public void hideSoftwareKeyboard () {
    super.hideSoftwareKeyboard();
    Keyboard.hideList(numberView.getEditText(), codeView.getEditText(), countryView.getEditText());
  }

  private String getFirstName () {
    return firstNameItem != null ? firstNameItem.getStringValue() : null;
  }

  private String getLastName () {
    return lastNameItem != null ? lastNameItem.getStringValue() : null;
  }

  private String getPhoneNumber () {
    return "+" + Strings.getNumber(codeView.getText().toString()) + Strings.getNumber(numberView.getText().toString());
  }

  private boolean makeRequest () {
    if (isInProgress() || inCountryMode) {
      return false;
    }
    if (!hasValidNumber()) {
      showError(R.string.login_InvalidPhone);
      return true;
    }
    final String phoneCode = Strings.getNumber(codeView.getText().toString());
    final String phoneNumber = Strings.getNumber(numberView.getText().toString());
    final String tdlibNumber = phoneCode + phoneNumber;

    if (isAccountAdd) {
      int existingAccountId = tdlib.context().hasAccountWithPhoneNumber(tdlibNumber, tdlib.account().isDebug());
      if (existingAccountId != TdlibAccount.NO_ID) {
        tdlib.context().changePreferredAccountId(existingAccountId, TdlibManager.SWITCH_REASON_EXISTING_NUMBER, success -> {
          if (!success) {
            onSaveCompleted();
          }
        });
        return true;
      }
      /*boolean syncContacts = adapter.getCheckIntResults().get(R.id.btn_syncContacts) == R.id.btn_syncContacts;
      tdlib.contacts().forceEnable(syncContacts);*/
    }

    showError(null);
    setInProgress(true);

    final String phone = "+" + tdlibNumber;

    TdApi.Function<?> function;

    switch (mode) {
      case MODE_ADD_CONTACT:
        function = new TdApi.ImportContacts(new TdApi.Contact[] {new TdApi.Contact(phone, getFirstName(), getLastName(), null, 0)});
        break;
      case MODE_CHANGE_NUMBER:
        function = new TdApi.ChangePhoneNumber(phone, tdlib.phoneNumberAuthenticationSettings(context));
        break;
      case MODE_LOGIN:
        function = new TdApi.SetAuthenticationPhoneNumber(phone, tdlib.phoneNumberAuthenticationSettings(context));
        tdlib.setAuthPhoneNumber(phoneCode, phoneNumber);
        break;
      default:
        throw new IllegalArgumentException("mode == " + mode);
    }
    RunnableBool act = (tokenVerified) -> tdlib.awaitReadyOrWaitingForData(() -> {
      tdlib.client().send(function, object -> runOnUiThreadOptional(() -> {
        setInProgress(false);
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR: {
            break;
          }
          case TdApi.AuthenticationCodeInfo.CONSTRUCTOR: {
            PasswordController passwordController = new PasswordController(context, tdlib);
            passwordController.setArguments(new PasswordController.Args(PasswordController.MODE_CODE_CHANGE, (TdApi.AuthenticationCodeInfo) object, Strings.formatPhone(phone)));
            navigateTo(passwordController);
            break;
          }
          case TdApi.ImportedContacts.CONSTRUCTOR: {
            if (mode == MODE_ADD_CONTACT) {
              final TdApi.ImportedContacts contacts = (TdApi.ImportedContacts) object;
              final long[] userIds = contacts.userIds;
              runOnUiThreadOptional(() -> {
                setInProgress(false);
                if (userIds.length == 1) {
                  if (userIds[0] == 0) {
                    suggestInvitingUser(userIds[0], contacts.importerCount[0]);
                  } else {
                    UI.showToast(R.string.ContactAdded, Toast.LENGTH_SHORT);
                    if (StringUtils.isEmpty(initialPhoneNumber)) {
                      tdlib.ui().openPrivateChat(PhoneController.this, userIds[0], null);
                    } else {
                      navigateBack();
                    }
                  }
                }
              });
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            HelpInfo help = handleRichError(phone, (TdApi.Error) object);
            if (help != null) {
              CharSequence message = Lang.getMarkdownString(this, help.info, TD.toErrorString(help.error));
              if (message instanceof Spannable) {
                CustomTypefaceSpan[] spans = ((Spannable) message).getSpans(0, message.length(), CustomTypefaceSpan.class);
                for (CustomTypefaceSpan span : spans) {
                  if (span.getTextEntityType() != null && Td.isItalic(span.getTextEntityType())) {
                    span.setTypeface(null);
                    span.setColorId(ColorId.textLink);
                    span.setTextEntityType(new TdApi.TextEntityTypeEmailAddress());
                    int start = ((Spannable) message).getSpanStart(span);
                    int end = ((Spannable) message).getSpanEnd(span);
                    ((Spannable) message).setSpan(new NoUnderlineClickableSpan() {
                      @Override
                      public void onClick (@NonNull View widget) {
                        Intents.sendEmail(help.email, help.subject, help.text);
                      }
                    }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                  }
                }
              }
              showError(message);
            } else {
              showError(TD.toErrorString(object));
            }
            break;
          }
        }
      }));
    });
    if (mode == MODE_LOGIN) {
      tdlib.context().checkDeviceToken(0, tokenVerified -> {
        tdlib.checkConnectionParams();
        act.runWithBool(tokenVerified);
      });
    } else {
      act.runWithBool(false);
    }

    return true;
  }

  private static class HelpInfo {
    @StringRes
    public final int info;

    public final TdApi.Error error;

    public final String email;
    public final String subject, text;

    public HelpInfo (int infoRes, TdApi.Error error, String email, String subject, String text) {
      this.info = infoRes;
      this.error = error;
      this.email = email;
      this.subject = subject;
      this.text = text;
    }
  }

  @Nullable
  private HelpInfo handleRichError (String phoneNumber, TdApi.Error error) {
    if (StringUtils.isEmpty(error.message))
      return null;
    int res;
    String email, title, message;
    switch (error.message) {
      case "PHONE_NUMBER_INVALID":
        res = R.string.login_PHONE_NUMBER_INVALID;
        email = Lang.getStringSecure(R.string.email_LoginHelp);
        title = Lang.getString(R.string.email_InvalidNumber_subject, phoneNumber);
        message = Lang.getString(R.string.email_InvalidNumber_text, phoneNumber) + "\n\n" + tdlib.emailMetadata();
        break;
      case "PHONE_NUMBER_BANNED":
        res = R.string.login_PHONE_NUMBER_BANNED;
        email = Lang.getStringSecure(R.string.email_LoginHelp);
        title = Lang.getString(R.string.email_BannedNumber_subject, phoneNumber);
        message = Lang.getString(R.string.email_BannedNumber_text, phoneNumber) + "\n\n" + tdlib.emailMetadata();
        break;
      default: {
        if (mode != MODE_LOGIN)
          return null;
        res = R.string.login_error;
        email = Lang.getStringSecure(R.string.email_SmsHelp);
        title = Lang.getString(R.string.email_LoginError_subject, error.message);
        message = Lang.getString(R.string.email_LoginError_text, phoneNumber, TD.toErrorString(error)) + "\n\n" + tdlib.emailMetadata();
        break;
      }
    }
    return new HelpInfo(res, error, email, title, message);
  }

  private void suggestInvitingUser (long userId, int importerCount) {
    CharSequence msg;
    if (importerCount > 1) {
      msg = Lang.plural(R.string.SuggestInvitingUserCommon, importerCount, Lang.boldCreator(), getFirstName());
    } else {
      msg = Lang.getStringBold(R.string.SuggestInvitingUser, getFirstName());
    }
    showOptions(msg, new int[] {R.id.btn_invite, R.id.btn_cancel}, new String[] {Lang.getString(R.string.Invite), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.BLUE, OptionColor.NORMAL}, new int[] {R.drawable.baseline_person_add_24, R.drawable.baseline_cancel_24}, (itemView, id) -> {
      if (id == R.id.btn_invite) {
        tdlib.cache().getInviteText(text -> {
          Intents.sendSms(getPhoneNumber(), text.text);
        });
      }
      return true;
    });
  }

  // Controller stuff

  private boolean oneShot;

  public void onAuthorizationReady () {
    if (UI.inTestMode()) {
      makeRequest();
    }
  }

  private void makeTestRequest () {
    if (codeView != null && numberView != null) {
      final String phoneCode = "99";
      final StringBuilder b = new StringBuilder(4).append("966").append(Config.ROBOT_DC_ID);
      if (UI.TEST_MODE == UI.TEST_MODE_USER) {
        for (int i = 0; i < 4; i++) {
          b.append(MathUtils.random(0, 9));
        }
      } else {
        b.append("73").append(Config.ROBOT_ID_PREFIX + MathUtils.random(1, Config.MAX_ROBOT_ID));
      }
      final String phoneNumber = b.toString();
      codeView.setText(phoneCode);
      numberView.setText(phoneNumber);
      makeRequest();
    }
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (mode == MODE_LOGIN && isAccountAdd) {
      context.runEmulatorChecks(true);
    }
    if (oneShot) {
      return;
    }
    oneShot = true;
    switch (mode) {
      case MODE_CHANGE_NUMBER: {
        destroyStackItemById(R.id.controller_editPhone);
        break;
      }
      case MODE_LOGIN: {
        if (Config.REMOVE_INTRO) {
          destroyStackItemById(R.id.controller_intro);
        }
        if (UI.inTestMode()) {
          makeTestRequest();
        }
        break;
      }
    }
  }
}
