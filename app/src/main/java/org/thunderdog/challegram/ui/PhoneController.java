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

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.core.Background;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.emoji.Emoji;
import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.navigation.BackHeaderButton;
import org.thunderdog.challegram.navigation.HeaderView;
import org.thunderdog.challegram.navigation.Menu;
import org.thunderdog.challegram.telegram.TGLegacyManager;
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
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.NoUnderlineClickableSpan;
import org.thunderdog.challegram.util.StringList;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;
import org.thunderdog.challegram.widget.NoScrollTextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import me.vkryl.android.AppInstallationUtil;
import me.vkryl.android.text.AcceptFilter;
import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.ArrayUtils;
import me.vkryl.core.MathUtils;
import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.IntList;
import me.vkryl.core.lambda.RunnableBool;
import tgx.td.Td;

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
      if (event == Lang.EVENT_PACK_CHANGED || (event == Lang.EVENT_STRING_CHANGED && arg1 == getCountryRes())) {
        countryView.setHint(getCountryRes());
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

  private boolean oneShotFocusSet;

  /*private void initValues () {
    if (mode != MODE_ADD_CONTACT) {
      String[] phoneNumber = TGCountry.instance().getNumber(tdlib);
      if (phoneNumber != null) {
        storedValues.put(R.id.login_code, phoneNumber[0]);
        storedValues.put(R.id.login_phone, phoneNumber[1]);

        TGCountry.Country country = currentCountry = TGCountry.instance().get(phoneNumber[0]);
        if (country != null) {
          storedValues.put(R.id.login_country, country.name);
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
        TGCountry.Country current = currentCountry = TGCountry.instance().get(countryCode);
        if (current != null) {
          storedValues.put(R.id.login_country, current.name);
        }
      }

      storedValues.put(R.id.login_phone, phoneNumber);
    }

    if (currentCountry == null && StringUtils.isEmpty(initialPhoneNumber)) {
      TGCountry.Country current = currentCountry = TGCountry.instance().getCurrent();
      if (current != null) {
        storedValues.put(R.id.login_code, current.number);
        storedValues.put(R.id.login_country, current.name);
      }
    }

    String countryText = storedValues.get(R.id.login_country, "");
    countryView.setText(countryText);

    if (mode != MODE_ADD_CONTACT || StringUtils.isEmpty(initialPhoneNumber)) {
          setLockFocusView(codeText.isEmpty() ? codeView.getEditText() : numberView.getEditText());
        }
  }*/

  @Override
  protected void onCreateView (final Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    String[] initialPhoneNumberParts = !StringUtils.isEmpty(initialPhoneNumber) ? TD.getPhoneNumberParts(initialPhoneNumber) : null;
    if (!StringUtils.isEmpty(initialPhoneNumber) && initialPhoneNumberParts == null) {
      String numeric = Strings.getNumber(initialPhoneNumber);
      initialPhoneNumberParts = new String[] {
        numeric.substring(0, 1),
        numeric.substring(1)
      };
    }
    if (initialPhoneNumberParts != null) {
      selectedCallingCode = initialPhoneNumberParts[0];
      enteredPhoneNumber = initialPhoneNumberParts[1];
    }

    int headerHeight = Screen.dp(72f);

    ((FrameLayoutFix.LayoutParams) recyclerView.getLayoutParams()).topMargin = headerHeight;

    if (mode != MODE_ADD_CONTACT) {
      setDoneIcon(R.drawable.baseline_arrow_forward_24);
    }

    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    FrameLayoutFix countryWrap = new FrameLayoutFix(context);
    countryWrap.setPadding(Screen.dp(16f), Screen.dp(12f), Screen.dp(16f), 0);
    countryWrap.setLayoutParams(FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, headerHeight, Gravity.TOP));

    countryView = new MaterialEditTextGroup(context, tdlib);
    countryView.getEditText().setFilters(new InputFilter[] {
      new EmojiFilter()
    });
    countryView.addThemeListeners(this);
    countryView.setUseTextChangeAnimations();
    countryView.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
    countryView.getEditText().setImeOptions(EditorInfo.IME_ACTION_DONE);
    countryView.setDoneListener(v -> {
      if (countryView.isEmpty()) {
        setInCountryMode(false);
        clearCountry();
        codeView.setText("");
      } else if (currentResults != null && !currentResults.isEmpty() && currentResults.get(0).getViewType() == ListItem.TYPE_COUNTRY) {
        ListItem item = currentResults.get(0);
        this.selectedCallingCode = item.getStringValue();
        setCountry((TdApi.CountryInfo) item.getData(), true, true);
      } else {
        setInCountryMode(false);
        resetCountry();
      }
      return true;
    });
    countryView.setHint(getCountryRes());
    countryView.getEditText().setId(R.id.login_country);
    countryView.getEditText().setNextFocusDownId(R.id.login_code);
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

        codeView = new MaterialEditTextGroup(context, tdlib);
        codeView.addThemeListeners(PhoneController.this);
        codeView.setLayoutParams(params);
        codeView.getEditText().setId(R.id.login_code);
        codeView.getEditText().setNextFocusDownId(R.id.login_phone);
        codeView.getEditText().setInputType(InputType.TYPE_CLASS_PHONE);
        codeView.getEditText().setFilters(new InputFilter[]{
          new CodePointCountFilter(4),
          new AcceptFilter() {
            @Override
            protected boolean accept (char c) {
              return (c >= '0' && c <= '9');
            }
          }
        });
        codeView.setFocusListener(PhoneController.this);
        codeView.setTextListener(PhoneController.this);
        numberWrap.addView(codeView);

        params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT);
        params.leftMargin = Screen.dp(89f);

        numberView = new MaterialEditTextGroup(context, tdlib);
        numberView.getEditText().setFilters(new InputFilter[] {
          new AcceptFilter() {
            @Override
            protected boolean accept (char c) {
              return (c >= '0' && c <= '9') || c == ' ' || c == '(' || c == ')' || c == '-';
            }
          }
        });
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
        if (mode == MODE_ADD_CONTACT) {
          numberView.setNextFocusDownId(R.id.edit_first_name);
        } else {
          numberView.getEditText().setImeOptions(EditorInfo.IME_ACTION_DONE);
          numberView.setDoneListener(v -> makeRequest());
        }
        numberView.setTextListener(PhoneController.this);
        numberWrap.addView(numberView);

        if (!StringUtils.isEmpty(selectedCallingCode) || !StringUtils.isEmpty(enteredPhoneNumber)) {
          codeView.setText(!StringUtils.isEmpty(selectedCallingCode) ? selectedCallingCode : "");
          numberView.setText(!StringUtils.isEmpty(enteredPhoneNumber) ? enteredPhoneNumber : "");
        }

        if (mode != MODE_ADD_CONTACT || StringUtils.isEmpty(initialPhoneNumber)) {
          setLockFocusView(StringUtils.isEmpty(selectedCallingCode) ? codeView.getEditText() : numberView.getEditText());
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
        codeView.setText(selectedCallingCode);
        MaterialEditTextGroup numberView = (MaterialEditTextGroup) group.getChildAt(2);
        numberView.setText(enteredPhoneNumber);
      }
    };
    adapter.setLockFocusOn(this, mode != MODE_ADD_CONTACT);
    if (mode == MODE_ADD_CONTACT) {
      adapter.setTextChangeListener(this);
    }
    adapter.setItems(baseItems, isAccountAdd);
    recyclerView.setAdapter(adapter);

    TGLegacyManager.instance().addEmojiListener(adapter);
    loadCountries();
  }

  private int countryRes;

  private int getCountryRes () {
    if (countryRes == 0) {
      countryRes = AppInstallationUtil.getInstallerId(context) == AppInstallationUtil.InstallerId.HUAWEI_APPGALLERY ?
        R.string.CountryCode :
        R.string.Country;
    }
    return countryRes;
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
      if (item != null && item.getData() instanceof TdApi.CountryInfo country) {
        this.selectedCallingCode = item.getStringValue();
        setCountry(country, true, true);
      }
    }
  }

  private void clearCountry () {
    setCountry(null, true, true);
  }

  private void resetCountry () {
    setCountry(selectedCountry, true, true);
  }

  private void setCountry (@Nullable TdApi.CountryInfo country, boolean dispatch, boolean selectedByUser) {
    this.selectedCountry = country;
    this.selectedCountryByUser = selectedByUser;

    CharSequence displayText;
    if (country != null) {
      String emoji = Emoji.getEmojiFlagFromCountry(country.countryCode);
      if (!StringUtils.isEmpty(emoji)) {
        displayText = new SpannableStringBuilder(Emoji.instance().replaceEmoji(emoji)).append(" ").append(country.name);
      } else {
        displayText = country.name;
      }
    } else {
      displayText = "";
    }

    countryView.setText(displayText, isFocused());
    if (dispatch) {
      setInCountryMode(false);
      Keyboard.show(numberView.getEditText());
    }
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
  public boolean performOnBackPressed (boolean fromTop, boolean commit) {
    if (inCountryMode) {
      if (commit) {
        setInCountryMode(false);
        resetCountry();
      }
      return true;
    }
    return super.performOnBackPressed(fromTop, commit);
  }

  private boolean ignorePhoneChanges;

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v) {
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
      String prevValue = selectedCallingCode;
      if (prevValue != null && StringUtils.equalsOrBothEmpty(prevValue, text)) {
        return;
      }
      selectedCallingCode = text;
      String numeric = Strings.getNumber(text);
      if (!text.equals(numeric)) {
        codeView.setText(numeric);
      } else {
        String phoneNumber = numberView.getEditText().getText().toString();
        findAndUpdateCountry(numeric, Strings.getNumber(phoneNumber), true);
        checkNumber(phoneNumber);
        updateDoneState();
      }
      showError(null);
      if (text.length() == 4 && codeView.getEditText().getSelectionEnd() == text.length()) {
        Keyboard.show(numberView.getEditText());
      }
    } else if (inputId == R.id.login_phone) {
      enteredPhoneNumber = text;
      if (!ignorePhoneChanges) {
        checkNumber(text);
        findAndUpdateCountry(selectedCallingCode, Strings.getNumber(text), false);
        updateDoneState();
      }
      showError(null);
    }
  }

  private void checkNumber (String text) {
    String number = Strings.getNumber(text);
    CharSequence code = codeView.getText();
    String expected = selectedCountry != null ? Strings.formatNumberPart(code.toString(), number) : number;
    if (!text.equals(expected)) {
      ignorePhoneChanges = true;
      Views.replaceText(numberView.getEditText().getText(), text, expected);
      ignorePhoneChanges = false;
    }
  }

  private String currentCountryCode;
  private TdApi.CountryInfo selectedCountry;
  private boolean selectedCountryByUser;
  private String selectedCallingCode = "";
  private String enteredPhoneNumber = "";
  private boolean isCountryDefault = true;

  private void setIsCountryDefault (boolean isDefault) {
    this.isCountryDefault = isDefault;
  }

  private static final String[] CANADA_AREA_CODES = {
    "204", "226", "236", "249", "250", "263",
    "306", "343", "354", "365", "367", "368", "382",
    "403", "416", "418", "426", "428", "431", "437", "438", "450", "474",
    "506", "514", "519", "548", "579", "581", "584", "587",
    "604", "613", "639", "647", "672", "683",
    "705", "709", "742", "753", "778", "780", "782",
    "807", "819", "825", "867", "873", "879",
    "902", "905", "942"
  };

  private void findAndUpdateCountry (String callingCode, String phoneNumber, boolean allowDispatch) {
    if (countries == null) {
      return;
    }

    Map<String, TdApi.CountryInfo> foundCandidates = null;

    if (!StringUtils.isEmpty(callingCode)) {
      for (TdApi.CountryInfo country : countries) {
        if (ArrayUtils.contains(country.callingCodes, callingCode)) {
          if (foundCandidates == null) {
            foundCandidates = new LinkedHashMap<>();
          }
          foundCandidates.put(country.countryCode, country);
        }
      }

    }

    if (foundCandidates == null || foundCandidates.isEmpty()) {
      setCountry(null, false, false);
      return;
    }

    TdApi.CountryInfo bestMatch = null;
    if (selectedCountry != null && foundCandidates.containsKey(selectedCountry.countryCode) && selectedCountryByUser) {
      bestMatch = selectedCountry;
    }
    if (bestMatch == null && foundCandidates.size() > 1) {
      switch (callingCode) {
        case "1": { // US (main), CA
          if (phoneNumber.length() >= 3 && ArrayUtils.contains(CANADA_AREA_CODES, phoneNumber.substring(0, 3))) {
            bestMatch = foundCandidates.get("CA");
          } else {
            bestMatch = foundCandidates.get("US");
          }
          break;
        }
        case "7": { // RU (main), KZ
          if (phoneNumber.startsWith("7")) {
            bestMatch = foundCandidates.get("KZ");
          } else {
            bestMatch = foundCandidates.get("RU");
          }
          break;
        }
        case "599": { // BQ, CW (main)
          if (phoneNumber.startsWith("3") || phoneNumber.startsWith("4") || phoneNumber.startsWith("7")) {
            bestMatch = foundCandidates.get("BQ");
          } else {
            bestMatch = foundCandidates.get("CW");
          }
          break;
        }
      }

      Set<String> currentCountryCodes = tdlib.possibleCountryCodes(currentCountryCode);
      if (bestMatch == null && !currentCountryCodes.isEmpty()) {
        for (Map.Entry<String, TdApi.CountryInfo> foundEntry : foundCandidates.entrySet()) {
          if (currentCountryCodes.contains(foundEntry.getKey())) {
            bestMatch = foundEntry.getValue();
            break;
          }
        }
      }
    }
    if (bestMatch == null) {
      bestMatch = foundCandidates.values().iterator().next();
    }
    boolean dispatch = false;
    if (bestMatch != null && !StringUtils.isEmpty(callingCode)) {
      dispatch = true;
      for (TdApi.CountryInfo country : countries) {
        for (String countryCallingCode : country.callingCodes) {
          if (countryCallingCode.startsWith(callingCode) && countryCallingCode.length() > callingCode.length()) {
            dispatch = false;
            break;
          }
        }
      }
    }
    boolean preserveSelectedByUser = false;
    if (bestMatch != null && bestMatch == selectedCountry) {
      preserveSelectedByUser = selectedCountryByUser;
    }
    setCountry(bestMatch, allowDispatch && dispatch, preserveSelectedByUser);
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
          ListItem existingItem = adapter.getItems().get(i++);
          if (
            existingItem.getData() != item.getData() ||
            existingItem.getHighlight() != item.getHighlight() ||
            !StringUtils.equalsOrBothEmpty(existingItem.getStringValue(), item.getStringValue())
          ) {
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

  private TdApi.CountryInfo[] countries;

  @Override
  public boolean needAsynchronousAnimation () {
    return countries == null || currentCountryCode == null;
  }

  private TdApi.CountryInfo findCountry (TdApi.CountryInfo[] countries, String code) {
    for (TdApi.CountryInfo country : countries) {
      if (country.countryCode.equals(code)) {
        return country;
      }
    }
    return null;
  }

  private void loadCountries () {
    Runnable check = () -> {
      if (countries != null && currentCountryCode != null) {
        Set<String> countryCodes = tdlib.possibleCountryCodes(currentCountryCode);
        AtomicReference<TdApi.CountryInfo> initialCountryRef = new AtomicReference<>();
        for (String countryCode : countryCodes) {
          TdApi.CountryInfo countryInfo = findCountry(countries, countryCode);
          if (countryInfo != null) {
            initialCountryRef.set(countryInfo);
            break;
          }
        }
        runOnUiThreadOptional(() -> {
          if (!StringUtils.isEmpty(selectedCallingCode)) {
            findAndUpdateCountry(selectedCallingCode, enteredPhoneNumber, false);
          } else {
            TdApi.CountryInfo initialCountry = initialCountryRef.get();
            if (initialCountry != null) {
              setCountry(initialCountry, false, true);
              String phoneNumberCountryCode = tdlib.account().getPhoneNumberCountryCode();
              String finalCode = !StringUtils.isEmpty(phoneNumberCountryCode) && ArrayUtils.contains(initialCountry.callingCodes, phoneNumberCountryCode) ? phoneNumberCountryCode : initialCountry.callingCodes[0];
              if (codeView != null) {
                codeView.setText(finalCode);
              } else {
                selectedCallingCode = finalCode;
              }
            }
          }
          executeScheduledAnimation();
        });
      }
    };
    tdlib.getCountries(countries -> {
      this.countries = countries;
      check.run();
    });
    tdlib.send(new TdApi.GetCountryCode(), (countryCode, error) -> {
      this.currentCountryCode = countryCode != null ? countryCode.text : "";
      check.run();
    });
  }

  private void searchCountry (@Nullable final String inputPrefix) {
    String prefix = StringUtils.isEmpty(inputPrefix) || !isCountryDefault ? inputPrefix : "";
    lastSearchPrefix = prefix;
    if (prefix == null || countries == null || countries.length == 0) {
      setItems(baseItems, false);
      return;
    }
    Background.instance().post(() -> {
      final ArrayList<ListItem> results = new ArrayList<>(countries.length);

      boolean search = !StringUtils.isEmpty(prefix);
      String numberPrefix = Strings.getNumber(prefix);

      for (TdApi.CountryInfo country : countries) {
        if (country.isHidden && !search)
          continue;

        String emoji = Emoji.getEmojiFlagFromCountry(country.countryCode);
        String emojiPrefix = !StringUtils.isEmpty(emoji) ? emoji + " " : "";

        Highlight nativeNameHighlight = Highlight.valueOf(country.name, prefix);
        boolean nativeNameMatches = Highlight.isExactWordMatch(country.name, nativeNameHighlight);
        if (!nativeNameMatches) {
          nativeNameHighlight = null;
        }
        boolean nameInEnglishMatches = Highlight.isExactWordMatch(country.englishName, Highlight.valueOf(country.englishName, prefix));

        boolean emojiMatches = search && emoji != null && emoji.equals(prefix);
        boolean countryCodeMatches = country.countryCode.equalsIgnoreCase(prefix);

        String displayText;
        if (!emojiPrefix.isEmpty()) {
          displayText = emojiPrefix + country.name;
          if (nativeNameHighlight != null) {
            nativeNameHighlight = nativeNameHighlight.offset(emojiPrefix.length());
          }
        } else {
          displayText = country.name;
        }

        for (String callingCode : country.callingCodes) {
          boolean numberMatchesPrefix = search && !numberPrefix.isEmpty() && callingCode.startsWith(numberPrefix);
          if (search && !nativeNameMatches && !nameInEnglishMatches && !numberMatchesPrefix && !emojiMatches && !countryCodeMatches) {
            continue;
          }

          ListItem item = new ListItem(ListItem.TYPE_COUNTRY, R.id.result, 0, displayText, false)
            .setData(country)
            .setStringValue(callingCode)
            .setHighlight(nativeNameHighlight);
          results.add(item);
        }
      }

      if (results.isEmpty()) {
        results.add(new ListItem(ListItem.TYPE_EMPTY, 0, 0, R.string.RegionNotFound));
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
        function = new TdApi.ImportContacts(new TdApi.ImportedContact[] {new TdApi.ImportedContact(phone, getFirstName(), getLastName(), null)});
        break;
      case MODE_CHANGE_NUMBER:
        function = new TdApi.SendPhoneNumberCode(phone, tdlib.phoneNumberAuthenticationSettings(context), new TdApi.PhoneNumberCodeTypeChange());
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

  private AlertDialog emulatorPrompt;

  private void showEmulatorPrompt () {
    if (mode != MODE_LOGIN) {
      return;
    }
    context.forceRunEmulatorChecks(detectionResult -> executeOnUiThreadOptional(() -> {
      if (detectionResult == null || !detectionResult.isEmulatorDetected()) {
        return;
      }
      if (emulatorPrompt != null && emulatorPrompt.isShowing()) {
        return;
      }
      boolean mayBeFalsePositive = detectionResult.mayBeFalsePositive();
      AlertDialog.Builder b = new AlertDialog.Builder(context, Theme.dialogTheme());
      b.setTitle(Lang.getString(R.string.EmulatorWarningTitle));
      b.setMessage(Lang.getMarkdownStringSecure(this, mayBeFalsePositive ? R.string.EmulatorWarning : R.string.EmulatorWarningStrict));
      b.setPositiveButton(Lang.getString(R.string.EmulatorWarningBtnOk), (dialog, which) -> dialog.dismiss());
      if (mayBeFalsePositive) {
        b.setNeutralButton(Lang.getString(R.string.EmulatorWarningBtnReport), (dialog, which) -> {
          try {
            Uri uri = Uri.parse(BuildConfig.REMOTE_URL);
            String title = Lang.getString(R.string.EmulatorDetectorReport_title, Build.BRAND, Build.MODEL);
            String metadata = U.getUsefulMetadata(tdlib);
            String body = Lang.getString(R.string.EmulatorDetectorReport_text,
              Build.BRAND,
              Build.MODEL,
              Build.PRODUCT,
              Build.DEVICE,
              Build.HARDWARE,
              metadata,
              detectionResult.toHumanReadableFormat()
            );
            Uri reportUri = uri
              .buildUpon()
              .appendEncodedPath("issues/new")
              .appendQueryParameter("title", title)
              .appendQueryParameter("body", body)
              .build();

            IntList ids = new IntList(3);
            StringList strings = new StringList(3);
            IntList icons = new IntList(3);
            ids.append(R.id.btn_openIn);
            strings.append(R.string.EmulatorWarningReportBtn);
            icons.append(R.drawable.baseline_github_24);

            ids.append(R.id.btn_copyLink);
            strings.append(R.string.CopyLink);
            icons.append(R.drawable.baseline_link_24);

            if (tdlib.context().hasActiveAccounts()) {
              ids.append(R.id.btn_share);
              strings.append(R.string.Share);
              icons.append(R.drawable.baseline_forward_24);
            }
            showOptions(Lang.getMarkdownStringSecure(this, R.string.EmulatorWarningReport), ids.get(), strings.get(), null, icons.get(), (optionItemView, id) -> {
              if (id == R.id.btn_openIn) {
                Intents.openUriInBrowser(reportUri);
              } else if (id == R.id.btn_copyLink) {
                UI.copyText(reportUri.toString(), R.string.CopiedLink);
              } else if (id == R.id.btn_share) {
                String text = reportUri.toString();
                ShareController c = new ShareController(context, context.currentTdlib());
                c.setArguments(new ShareController.Args(text).setExport(text));
                c.show();
              }
              return true;
            });
          } catch (Throwable t) {
            Log.e(t);
            UI.showToast("Unable to create report: " + Log.toString(t), Toast.LENGTH_SHORT);
          }
        });
      }
      b.setCancelable(false);
      emulatorPrompt = showAlert(b);
    }));
  }

  @Override
  public void onActivityResume () {
    super.onActivityResume();
    showEmulatorPrompt();
  }

  @Override
  public void destroy () {
    super.destroy();
    TGLegacyManager.instance().removeEmojiListener(adapter);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    showEmulatorPrompt();
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
