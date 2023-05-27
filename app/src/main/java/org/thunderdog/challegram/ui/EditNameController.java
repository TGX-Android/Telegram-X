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
 * File created on 22/12/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.recyclerview.widget.RecyclerView;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.CharacterStyleFilter;
import org.thunderdog.challegram.widget.BetterChatView;
import org.thunderdog.challegram.widget.MaterialEditTextGroup;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.android.text.CodePointCountFilter;
import me.vkryl.android.widget.FrameLayoutFix;
import me.vkryl.core.StringUtils;
import me.vkryl.td.TdConstants;

public class EditNameController extends EditBaseController<EditNameController.Args> implements SettingsAdapter.TextChangeListener, Client.ResultHandler, TdlibCache.UserDataChangeListener, View.OnClickListener {
  public static final int MODE_SIGNUP = 0;
  public static final int MODE_RENAME_SELF = 1;
  public static final int MODE_RENAME_CONTACT = 2;
  public static final int MODE_ADD_CONTACT = 3;

  public static class Args {
    public int mode;
    public TdApi.AuthorizationStateWaitRegistration authState;
    public String formattedPhone;
    public String knownPhoneNumber;

    public Args (int mode, TdApi.AuthorizationStateWaitRegistration authState, String formattedPhone) {
      this.mode = mode;
      this.authState = authState;
      this.formattedPhone = formattedPhone;
    }

    public Args setKnownPhoneNumber (String phoneNumber) {
      this.knownPhoneNumber = phoneNumber;
      return this;
    }
  }

  public EditNameController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public boolean isUnauthorized () {
    return mode == EditNameController.MODE_SIGNUP;
  }

  public void setMode (int mode) {
    this.mode = mode;
  }

  public void setUser (TdApi.User user) {
    this.user = user;
  }

  private int mode;
  private TdApi.User user;
  private String knownPhoneNumber;

  @Override
  public void setArguments (Args args) {
    super.setArguments(args);
    this.mode = args.mode;
    this.knownPhoneNumber = args.knownPhoneNumber;
  }

  public void setKnownPhoneNumber (String phoneNumber) {
    this.knownPhoneNumber = phoneNumber;
  }

  @Override
  public int getId () {
    return R.id.controller_name;
  }

  private SettingsAdapter adapter;
  private ListItem firstName, lastName, shareMyNumber;

  @Override
  protected void onCreateView (Context context, FrameLayoutFix contentView, RecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void modifyEditText (ListItem item, ViewGroup parent, MaterialEditTextGroup editText) {
        editText.getEditText().setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setChat((TGFoundChat) item.getData());
        chatView.setEnabled(false);
      }
    };
    adapter.setLockFocusOn(this, true);
    adapter.setTextChangeListener(this);

    TdApi.User user;

    switch (mode) {
      case MODE_ADD_CONTACT:
      case MODE_RENAME_CONTACT: {
        user = this.user;
        break;
      }
      case MODE_RENAME_SELF: {
        user = tdlib.myUser();
        break;
      }
      default: {
        user = null;
        break;
      }
    }
    String firstNameValue, lastNameValue;
    if (user != null) {
      firstNameValue = user.firstName;
      lastNameValue = user.lastName;

      setDoneVisible(isGoodInput(firstNameValue, lastNameValue));
    } else {
      firstNameValue = lastNameValue = "";
      if (mode == MODE_SIGNUP && UI.inTestMode()) {
        firstNameValue = "Robot #" + tdlib.robotId();
      }
    }

    List<ListItem> items = new ArrayList<>();
    if ((mode == MODE_RENAME_CONTACT || mode == MODE_ADD_CONTACT) && user != null) {
      items.add(new ListItem(ListItem.TYPE_CHAT_BETTER)
        .setData(new TGFoundChat(tdlib, user.id)
        .setForcedSubtitle(
          !StringUtils.isEmpty(knownPhoneNumber) ?
            Strings.formatPhone(knownPhoneNumber) :
          TD.hasPhoneNumber(user) ?
            Strings.formatPhone(user.phoneNumber) :
          Lang.getString(R.string.NumberHidden))
        )
      );
    }
    items.add((firstName = new ListItem(items.isEmpty() ? ListItem.TYPE_EDITTEXT : ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_first_name, 0, R.string.login_FirstName)
      .setStringValue(firstNameValue)
      .setInputFilters(new InputFilter[] {
        new CodePointCountFilter(TdConstants.MAX_NAME_LENGTH),
        new EmojiFilter(),
        new CharacterStyleFilter()
      })));
    items.add((lastName = new ListItem(ListItem.TYPE_EDITTEXT_NO_PADDING, R.id.edit_last_name, 0, mode == MODE_RENAME_CONTACT || mode == MODE_ADD_CONTACT ? R.string.LastName : R.string.login_LastName)
      .setStringValue(lastNameValue)
      .setInputFilters(new InputFilter[] {
        new CodePointCountFilter(TdConstants.MAX_NAME_LENGTH),
        new EmojiFilter(),
        new CharacterStyleFilter()
      }).setOnEditorActionListener(new SimpleEditorActionListener(EditorInfo.IME_ACTION_DONE, this))));
    TdApi.TermsOfService termsOfService = mode == MODE_SIGNUP ? getArgumentsStrict().authState.termsOfService : null;
    if (termsOfService != null && termsOfService.minUserAge != 0) {
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.plural(R.string.AgeVerification, termsOfService.minUserAge), false));
    }
    if ((mode == MODE_RENAME_CONTACT || mode == MODE_ADD_CONTACT) && user != null) {
      if (StringUtils.isEmpty(knownPhoneNumber) && !TD.hasPhoneNumber(user)) {
        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getStringBold(R.string.NumberHiddenHint, tdlib.cache().userName(user.id)), false));
      }
      tdlib.cache().addUserDataListener(user.id, this);
      TdApi.UserFullInfo userFull = tdlib.cache().userFull(user.id);
      if (userFull != null && userFull.needPhoneNumberPrivacyException) {
        items.add(shareMyNumber = newShareItem());
      }
    }
    adapter.setItems(items, false);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    recyclerView.setAdapter(adapter);
    setDoneIcon(mode == MODE_SIGNUP ? R.drawable.baseline_arrow_forward_24 : R.drawable.baseline_check_24);
  }

  @Override
  public void onFocus () {
    super.onFocus();
    if (mode == MODE_SIGNUP) {
      ViewController<?> c = previousStackItem();
      destroyStackItemById(R.id.controller_code);
      if (UI.inTestMode()) {
        updateDoneState();
        UI.post(this::onDoneClick);
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    if (mode == MODE_ADD_CONTACT) {
      tdlib.cache().removeUserDataListener(user.id, this);
    }
  }

  private ListItem newShareItem () {
    return new ListItem(ListItem.TYPE_CHECKBOX_OPTION_REVERSE, R.id.btn_shareMyContact, 0, Lang.getStringBold(R.string.ShareMyNumber, tdlib.cache().userName(user.id)), true);
  }

  @Override
  public void onUserUpdated (TdApi.User user) { }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    tdlib.ui().post(() -> {
      if (isDestroyed() && adapter != null && mode == MODE_ADD_CONTACT && user != null && userId == user.id) {
        if (userFull.needPhoneNumberPrivacyException) {
          if (adapter.findItemById(R.id.btn_shareMyContact) == null) {
            adapter.addItem(adapter.getItemCount(), shareMyNumber = newShareItem());
          }
        } else {
          adapter.removeItemById(R.id.btn_shareMyContact);
          shareMyNumber = null;
        }
      }
    });
  }

  @Override
  public void onClick (View v) {
    if (v.getId() == R.id.btn_shareMyContact) {
      shareMyNumber.setSelected(adapter.toggleView(v));
    }
  }

  @Override
  public CharSequence getName () {
    switch (mode) {
      case MODE_RENAME_CONTACT: {
        return Lang.getString(R.string.RenameContact);
      }
      case MODE_ADD_CONTACT: {
        return Lang.getString(R.string.AddContact);
      }
      case MODE_RENAME_SELF: {
        return Lang.getString(R.string.EditName);
      }
      case MODE_SIGNUP: {
        return Lang.getString(R.string.Registration);
      }
    }
    return "";
  }

  private boolean isGoodInput (String firstName, String lastName) {
    if (!StringUtils.isEmpty(firstName))
      return true;
    if (!StringUtils.isEmpty(lastName)) {
      switch (mode) {
        case MODE_RENAME_SELF:
        case MODE_RENAME_CONTACT:
        case MODE_ADD_CONTACT:
          return true;
      }
    }
    return false;
  }

  @Override
  protected boolean onDoneClick () {
    final String firstName = this.firstName.getStringValue().trim();
    final String lastName = this.lastName.getStringValue().trim();

    if (isGoodInput(firstName, lastName)) {
      switch (mode) {
        case MODE_RENAME_SELF: {
          setDoneInProgress(true);
          tdlib.client().send(new TdApi.SetName(firstName, lastName), this);
          break;
        }
        case MODE_ADD_CONTACT:
        case MODE_RENAME_CONTACT: {
          if (user != null) {
            setDoneInProgress(true);
            TdApi.Contact contact = new TdApi.Contact(!StringUtils.isEmpty(knownPhoneNumber) ? knownPhoneNumber : user.phoneNumber, firstName, lastName, null, user.id);
            tdlib.client().send(new TdApi.AddContact(contact, shareMyNumber != null && shareMyNumber.isSelected()), this);
          }
          break;
        }
        case MODE_SIGNUP: {
          TdApi.TermsOfService termsOfService = getArgumentsStrict().authState.termsOfService;
          CharSequence text = TD.formatString(this, termsOfService.text.text, termsOfService.text.entities, null, null);
          openAlert(R.string.TermsOfService, text, Lang.getString(R.string.TermsOfServiceDone), (dialog, which) -> {
            setDoneInProgress(true);
            tdlib.client().send(new TdApi.RegisterUser(firstName, lastName), this);
          }, ALERT_NO_CANCELABLE | ALERT_HAS_LINKS);
          break;
        }
      }
    }

    return true;
  }

  @Override
  public void onResult (final TdApi.Object object) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setDoneInProgress(false);
        switch (object.getConstructor()) {
          case TdApi.Ok.CONSTRUCTOR:
          case TdApi.ImportedContacts.CONSTRUCTOR: {
            if (mode == MODE_SIGNUP) {
              hideSoftwareKeyboard();
            } else {
              onSaveCompleted();
            }
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(object);
            break;
          }
        }
      }
    });
  }

  @Override
  public void onTextChanged (int id, ListItem item, MaterialEditTextGroup v, String text) {
    if (id == R.id.edit_first_name) {
      firstName.setStringValue(text);
      updateDoneState();
    } else if (id == R.id.edit_last_name) {
      lastName.setStringValue(text);
      updateDoneState();
    }
  }

  private void updateDoneState () {
    setDoneVisible(isGoodInput(firstName.getStringValue().trim(), lastName.getStringValue().trim()));
  }
}
