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
 * File created on 16/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.collection.SparseArrayCompat;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.navigation.SettingsWrapBuilder;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.PrivacySettings;
import org.thunderdog.challegram.telegram.PrivacySettingsListener;
import org.thunderdog.challegram.telegram.SessionListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibContactManager;
import org.thunderdog.challegram.telegram.TdlibManager;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Passcode;
import org.thunderdog.challegram.unsorted.Settings;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.StringUtils;
import tgx.td.Td;

public class SettingsPrivacyController extends RecyclerViewController<SettingsPrivacyController.Args> implements View.OnClickListener, ViewController.SettingsIntDelegate, TdlibCache.UserDataChangeListener, TdlibContactManager.StatusChangeListener, PrivacySettingsListener, SessionListener, ChatListener {
  public static class Args {
    private final boolean onlyPrivacy;

    public Args (boolean onlyPrivacy) {
      this.onlyPrivacy = onlyPrivacy;
    }
  }

  private boolean isOnlyPrivacy () {
    return getArguments() != null && getArgumentsStrict().onlyPrivacy;
  }

  public SettingsPrivacyController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(isOnlyPrivacy() ? R.string.Privacy : R.string.PrivacySettings);
  }

  @Override
  public int getId () {
    return R.id.controller_privacySettings;
  }

  private SettingsAdapter adapter;
  private ListItem secretInfo;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      public void setValuedSetting (ListItem item, SettingView v, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_syncContacts) {
          v.getToggler().setRadioEnabled(tdlib.contacts().isSyncEnabled(), isUpdate);
        } else if (itemId == R.id.btn_mapProvider) {
          v.setData(getMapProviderName(false));
        } else if (itemId == R.id.btn_mapProviderCloud) {
          v.setData(getMapProviderName(true));
        } else if (itemId == R.id.btn_suggestContacts) {
          v.getToggler().setRadioEnabled(!tdlib.areTopChatsDisabled(), isUpdate);
        } else if (itemId == R.id.btn_incognitoMode) {
          v.getToggler().setRadioEnabled(Settings.instance().needsIncognitoMode(), isUpdate);
        } else if (itemId == R.id.btn_blockedSenders) {
          v.setData(getBlockedSendersCount());
        } else if (itemId == R.id.btn_newChatsPrivacy) {
          v.setData(buildNewChatsPrivacy());
        } else if (itemId == R.id.btn_privacyRule) {
          v.setData(buildPrivacy(((TdApi.UserPrivacySetting) item.getData()).getConstructor()));
        } else if (itemId == R.id.btn_sessions) {
          v.setData(getAuthorizationsCount());
        } else if (itemId == R.id.btn_passcode) {
          v.setData(Passcode.instance().getModeName());
        } else if (itemId == R.id.btn_accountTTL) {
          v.setData(getAccountTTLIn());
        } else if (itemId == R.id.btn_hideSecretChats) {
          v.getToggler().setRadioEnabled(Settings.instance().needHideSecretChats(), isUpdate);
        } else if (itemId == R.id.btn_2fa) {
          if (isUpdate) {
            v.setEnabledAnimated(passwordState != null);
          } else {
            v.setEnabled(passwordState != null);
          }
          v.setData(getPasswordState());
        } else if (itemId == R.id.btn_loginEmail) {
          v.setEnabledAnimated(passwordState != null && !StringUtils.isEmpty(passwordState.loginEmailAddressPattern), isUpdate);
          if (passwordState != null) {
            v.setData(passwordState.loginEmailAddressPattern);
          } else {
            v.setData(R.string.LoadingInformation);
          }
        } else if (itemId == R.id.btn_secretLinkPreviews) {
          v.getToggler().setRadioEnabled(Settings.instance().needSecretLinkPreviews(), isUpdate);
        }
      }
    };

    final List<ListItem> items = new ArrayList<>();

    if (!isOnlyPrivacy()) {
      items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.SecurityTitle));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_sessions, R.drawable.baseline_devices_other_24, R.string.SessionsTitle));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_passcode, R.drawable.baseline_lock_24, R.string.PasscodeTitle));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_2fa, R.drawable.mrgrigri_baseline_textbox_password_24, R.string.TwoStepVerification));
      if (passwordState != null && !StringUtils.isEmpty(passwordState.loginEmailAddressPattern)) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_loginEmail, R.drawable.baseline_alternate_email_24, R.string.LoginEmail));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PrivacyTitle));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_blockedSenders, R.drawable.baseline_remove_circle_24, R.string.BlockedSenders));
    }

    TdApi.UserPrivacySetting[] privacySettings = new TdApi.UserPrivacySetting[] {
      new TdApi.UserPrivacySettingShowStatus(),
      new TdApi.UserPrivacySettingShowProfilePhoto(),
      new TdApi.UserPrivacySettingShowBirthdate(),
      new TdApi.UserPrivacySettingShowBio(),
      new TdApi.UserPrivacySettingShowPhoneNumber(),
      new TdApi.UserPrivacySettingAllowFindingByPhoneNumber(),
      new TdApi.UserPrivacySettingShowLinkInForwardedMessages(),
      new TdApi.UserPrivacySettingAllowChatInvites(),
      new TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages(),
      new TdApi.UserPrivacySettingAllowCalls(),
      new TdApi.UserPrivacySettingAllowPeerToPeerCalls()
    };
    for (TdApi.UserPrivacySetting privacySetting : privacySettings) {
      if (!items.isEmpty()) {
        if (privacySetting.getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          /*items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));*/
          // TODO visualise how other see me
        } else if (privacySetting.getConstructor() == TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR) {
          items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
          items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.EditPrivacyHint));
          items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
        } else {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
      }
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_privacyRule, SettingsPrivacyKeyController.getIcon(privacySetting), SettingsPrivacyKeyController.getName(privacySetting, false, false)).setData(privacySetting).setLongId(privacySetting.getConstructor()));
      getPrivacy(privacySetting);
      if (privacySetting.getConstructor() == TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages.CONSTRUCTOR) {
        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_newChatsPrivacy, R.drawable.baseline_chat_bubble_24, R.string.PrivacyMessagePreview));
        tdlib.send(new TdApi.GetNewChatPrivacySettings(), (newChatPrivacySettings, error) -> runOnUiThreadOptional(() -> {
          if (newChatPrivacySettings != null) {
            setNewChatsPrivacy(newChatPrivacySettings);
          }
        }));
      }
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
    items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.PeerToPeerInfo));

    if (!isOnlyPrivacy()) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.Contacts));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_suggestContacts, 0, R.string.SuggestContacts));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_resetContacts, 0, R.string.SyncContactsDelete));
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_syncContacts, 0, R.string.SyncContacts));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_syncContactsInfo, 0, getSyncContactsInfoRes()));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PrivacyBots));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_clearPaymentAndShipping, 0, R.string.PrivacyPaymentsClear, false));
    /*items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
    items.add(new SettingItem(SettingItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_loggedWebsites, 0, R.string.WebSessionsTitle));*/
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      // items.add(new SettingItem(SettingItem.TYPE_DESCRIPTION, 0, 0, R.string.PrivacyBotsInfo));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.SecretChats));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_secretLinkPreviews, 0, R.string.SecretWebPage));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.SecretWebPageInfo));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_hideSecretChats, 0, R.string.HideSecret));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(secretInfo = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Settings.instance().needHideSecretChats() ? R.string.HideSecretOn : R.string.HideSecretOff));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_incognitoMode, 0, R.string.IncognitoKeyboard));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.IncognitoKeyboardInfo));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_mapProvider, 0, R.string.MapPreviewProvider));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.MapPreviewProviderInfo));

      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.PrivacyAdvanced));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_clearAllDrafts, 0, R.string.PrivacyDeleteCloudDrafts));
    /*items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
    items.add(new SettingItem(SettingItem.TYPE_VALUED_SETTING, R.id.btn_mapProviderCloud, 0, R.string.MapPreviewProviderCloud));*/
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_accountTTL, 0, R.string.DeleteAccountIfAwayFor2));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.DeleteAccountHelp));

      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_deleteAccount, 0, R.string.DeleteMyAccount).setTextColorId(ColorId.textNegative));
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.DeleteMyAccountInfo));
    }


    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);

    Log.ensureReturnType(TdApi.GetBlockedMessageSenders.class, TdApi.MessageSenders.class);
    tdlib.send(new TdApi.GetBlockedMessageSenders(new TdApi.BlockListMain(), 0, 1), (blockedSenders) -> runOnUiThreadOptional(() ->
      setBlockedSenders(blockedSenders)
    ), UI::showError);
    fetchSessions();
    tdlib.send(new TdApi.GetPasswordState(), (passwordState) -> runOnUiThreadOptional(() ->
      setPasswordState(passwordState, true)
    ), UI::showError);
    tdlib.send(new TdApi.GetAccountTtl(), (accountTtl) -> runOnUiThreadOptional(() ->
      setAccountTTL(accountTtl)
    ), UI::showError);
    tdlib.send(new TdApi.GetConnectedWebsites(), (websites) -> runOnUiThreadOptional(() ->
      setWebsites(websites)
    ), UI::showError);

    tdlib.cache().putGlobalUserDataListener(this);
    tdlib.contacts().addStatusListener(this);

    tdlib.listeners().subscribeForGlobalUpdates(this);
  }

  @Override
  protected boolean needPersistentScrollPosition () {
    return true;
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putBoolean(keyPrefix + "only", getArguments() != null && getArguments().onlyPrivacy);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    setArguments(new Args(in.getBoolean(keyPrefix + "only", false)));
    return true;
  }

  private int getSyncContactsInfoRes () {
    return tdlib.contacts().isSyncEnabled() ? R.string.SyncContactsInfoOn : R.string.SyncContactsInfoOff;
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.cache().deleteGlobalUserDataListener(this);
    tdlib.contacts().removeStatusListener(this);
    tdlib.listeners().unsubscribeFromGlobalUpdates(this);
  }

  @Override
  public void onPrivacySettingRulesChanged (TdApi.UserPrivacySetting setting, TdApi.UserPrivacySettingRules rules) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        setPrivacyRules(setting.getConstructor(), rules);
      }
    });
  }

  @Override
  public void onNewChatPrivacySettingsChanged (TdApi.NewChatPrivacySettings newChatPrivacySettings) {
    runOnUiThreadOptional(() -> {
      setNewChatsPrivacy(newChatPrivacySettings);
    });
  }

  @Override
  public void onContactSyncEnabled (Tdlib tdlib, boolean isEnabled) {
    adapter.updateValuedSettingById(R.id.btn_syncContacts);
    int i = adapter.indexOfViewByIdReverse(R.id.btn_syncContactsInfo);
    if (i != -1) {
      adapter.getItems().get(i).setString(getSyncContactsInfoRes());
      adapter.updateValuedSettingByPosition(i);
    }
  }

  private void getPrivacy (final TdApi.UserPrivacySetting setting) {
    tdlib.send(new TdApi.GetUserPrivacySettingRules(setting), (rules, error) -> runOnUiThreadOptional(() -> {
      if (error != null) {
        UI.showError(error);
      } else {
        setPrivacyRules(setting.getConstructor(), rules);
      }
    }));
  }

  private int lastClickedButton;

  @Override
  public void onPrepareToShow () {
    super.onPrepareToShow();
    if (lastClickedButton != 0) {
      adapter.updateValuedSettingById(lastClickedButton);
      lastClickedButton = 0;
    }
  }

  @Override
  public void onClick (View v) {
    final int id = v.getId();
    if (id == R.id.btn_syncContacts) {
      if (tdlib.contacts().isSyncEnabled()) {
        tdlib.contacts().disableSync();
      } else {
        tdlib.contacts().enableSync(context);
      }
    } else if (id == R.id.btn_mapProvider || id == R.id.btn_mapProviderCloud) {
      tdlib.ui().showMapProviderSettings(this, id == R.id.btn_mapProviderCloud ? TdlibUi.MAP_PROVIDER_MODE_CLOUD : TdlibUi.MAP_PROVIDER_MODE_SECRET, () -> {
        if (!isDestroyed()) {
          adapter.updateValuedSettingById(id);
        }
      });
    } else if (id == R.id.btn_secretLinkPreviews) {
      Settings.instance().setUseSecretLinkPreviews(adapter.toggleView(v));
    } else if (id == R.id.btn_incognitoMode) {
      Settings.instance().setIncognitoMode(adapter.toggleView(v) ? Settings.INCOGNITO_CHAT_SECRET : 0);
    } else if (id == R.id.btn_hideSecretChats) {
      boolean hide = adapter.toggleView(v);
      boolean updated = Settings.instance().setNeedHideSecretChats(hide);
      if (secretInfo != null) {
        secretInfo.setString(hide ? R.string.HideSecretOn : R.string.HideSecretOff);
        adapter.updateValuedSetting(secretInfo);
      }
      if (updated) {
        TdlibManager.instance().onUpdateSecretChatNotifications();
      }
    } else if (id == R.id.btn_clearPaymentAndShipping) {
      showSettings(new SettingsWrapBuilder(R.id.btn_clearPaymentAndShipping).setSaveStr(R.string.Clear).setRawItems(new ListItem[] {
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_clearShipping, 0, R.string.PrivacyClearShipping, R.id.btn_clearShipping, true),
        new ListItem(ListItem.TYPE_CHECKBOX_OPTION, R.id.btn_clearPayment, 0, R.string.PrivacyClearPayment, R.id.btn_clearPayment, true)
      }).setSaveColorId(ColorId.textNegative).addHeaderItem(Lang.getString(R.string.PrivacyPaymentsClearAlert)).setIntDelegate((settingsId, result) -> {
        boolean clearShipping = result.get(R.id.btn_clearShipping) == R.id.btn_clearShipping;
        boolean clearPayment = result.get(R.id.btn_clearPayment) == R.id.btn_clearPayment;
        if (!clearPayment || !clearShipping) {
          if (clearShipping)
            tdlib.client().send(new TdApi.DeleteSavedOrderInfo(), tdlib.doneHandler());
          if (clearPayment)
            tdlib.client().send(new TdApi.DeleteSavedCredentials(), tdlib.doneHandler());
        } else {
          int[] numResults = new int[1];
          Client.ResultHandler handler = object -> {
            switch (object.getConstructor()) {
              case TdApi.Ok.CONSTRUCTOR: {
                if (++numResults[0] == 2) {
                  UI.showToast(R.string.Done, Toast.LENGTH_SHORT);
                }
                break;
              }
              case TdApi.Error.CONSTRUCTOR:
                UI.showError(object);
                break;
            }
          };
          tdlib.client().send(new TdApi.DeleteSavedOrderInfo(), handler);
          tdlib.client().send(new TdApi.DeleteSavedCredentials(), handler);
        }
      }).setOnActionButtonClick((wrap, view, isCancel) -> {
        if (!isCancel) {
          SparseIntArray result = wrap.adapter.getCheckIntResults();
          boolean clearShipping = result.get(R.id.btn_clearShipping) == R.id.btn_clearShipping;
          boolean clearPayment = result.get(R.id.btn_clearPayment) == R.id.btn_clearPayment;
          return !clearPayment && !clearShipping;
        }
        return false;
      }));
    } else if (id == R.id.btn_suggestContacts) {
      boolean value = ((SettingView) v).getToggler().isEnabled();
      if (value) {
        showOptions(Lang.getString(R.string.SuggestContactsAlert), new int[] {R.id.btn_suggestContacts, R.id.btn_cancel}, new String[] {Lang.getString(R.string.SuggestContactsDone), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, resultId) -> {
          if (resultId == R.id.btn_suggestContacts) {
            tdlib.setDisableTopChats(true);
            adapter.updateValuedSettingById(R.id.btn_suggestContacts);
          }
          return true;
        });
      } else {
        tdlib.setDisableTopChats(!((SettingView) v).getToggler().toggle(true));
      }
    } else if (id == R.id.btn_resetContacts) {
      showOptions(Lang.getString(R.string.SyncContactsDeleteInfo), new int[] {R.id.btn_resetContacts, R.id.btn_cancel}, new String[] {Lang.getString(R.string.SyncContactsDeleteButton), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, resultId) -> {
        if (resultId == R.id.btn_resetContacts) {
          tdlib.contacts().deleteContacts();
        }
        return true;
      });
    } else if (id == R.id.btn_blockedSenders) {
      SettingsBlockedController c = new SettingsBlockedController(context, tdlib);
      c.setArguments(new TdApi.BlockListMain());
      navigateTo(c);
    } else if (id == R.id.btn_newChatsPrivacy) {
      if (!tdlib.canSetNewChatPrivacySettings() && tdlib.ui().showPremiumAlert(this, v, TdlibUi.PremiumFeature.NEW_CHATS_PRIVACY)) {
        return;
      }
      SettingsPrivacyKeyController c = new SettingsPrivacyKeyController(context, tdlib);
      c.setArguments(SettingsPrivacyKeyController.Args.newChatsPrivacy());
      navigateTo(c);
    } else if (id == R.id.btn_privacyRule) {
      TdApi.UserPrivacySetting setting = (TdApi.UserPrivacySetting) ((ListItem) v.getTag()).getData();
      if (Td.requiresPremiumSubscription(setting) && tdlib.ui().showPremiumAlert(this, v, TdlibUi.PremiumFeature.RESTRICT_VOICE_AND_VIDEO_MESSAGES)) {
        return;
      }
      SettingsPrivacyKeyController c = new SettingsPrivacyKeyController(context, tdlib);
      c.setArguments(new SettingsPrivacyKeyController.Args(setting));
      navigateTo(c);
    } else if (id == R.id.btn_sessions) {
      lastClickedButton = id;
      SettingsSessionsController sessions = new SettingsSessionsController(context, tdlib);
      SettingsWebsitesController websites = new SettingsWebsitesController(context, tdlib);
      websites.setArguments(this);

      SimpleViewPagerController c = new SimpleViewPagerController(context, tdlib, new ViewController[] {sessions, websites}, new String[] {Lang.getString(R.string.Devices).toUpperCase(), Lang.getString(R.string.Websites).toUpperCase()}, false);
      navigateTo(c);
    } else if (id == R.id.btn_passcode) {
      lastClickedButton = id;
      if (Passcode.instance().isEnabled()) {
        PasscodeController passcode = new PasscodeController(context, tdlib);
        passcode.setPasscodeMode(PasscodeController.MODE_UNLOCK_SETUP);
        navigateTo(passcode);
      } else {
        navigateTo(new PasscodeSetupController(context, tdlib));
      }
    } else if (id == R.id.btn_2fa) {
      if (passwordState != null) {
        if (!passwordState.hasPassword) {
          Settings2FAController controller = new Settings2FAController(context, tdlib);
          controller.setArguments(new Settings2FAController.Args(this, null, null));
          navigateTo(controller);
        } else {
          PasswordController controller = new PasswordController(context, tdlib);
          controller.setArguments(new PasswordController.Args(PasswordController.MODE_UNLOCK_EDIT, passwordState));
          navigateTo(controller);
        }
      }
    } else if (id == R.id.btn_loginEmail) {
      if (passwordState != null && !StringUtils.isEmpty(passwordState.loginEmailAddressPattern)) {
        showOptions(new Options.Builder()
          .title(passwordState.loginEmailAddressPattern) // TODO(spoiler): replace `*` with the spoiler effect
          .info(Lang.getMarkdownString(this, R.string.ChangeEmailPromptText))
          .item(new OptionItem.Builder().id(R.id.btn_changeEmail).name(R.string.ChangeEmailPromptButton).icon(R.drawable.baseline_edit_24).build())
          .cancelItem()
          .build(), (optionView, optionId) -> {
            if (optionId == R.id.btn_changeEmail) {
              PasswordController controller = new PasswordController(context, tdlib);
              controller.setArguments(new PasswordController.Args(PasswordController.MODE_LOGIN_EMAIL_CHANGE, passwordState));
              navigateTo(controller);
            }
            return true;
          }
        );
      }
    } else if (id == R.id.btn_accountTTL) {
      int days = accountTtl != null ? accountTtl.days : 0;
      int months = days / 30;
      int years = months / 12;
      showSettings(id, new ListItem[] {
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_1month, 0, Lang.pluralBold(R.string.xMonths, 1), R.id.btn_accountTTL, months == 1),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_3months, 0, Lang.pluralBold(R.string.xMonths, 3), R.id.btn_accountTTL, months == 3),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_6month, 0, Lang.pluralBold(R.string.xMonths, 6), R.id.btn_accountTTL, months == 6),
        new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_1year, 0, Lang.pluralBold(R.string.xYears, 1), R.id.btn_accountTTL, years == 1)
      }, this);
    } else if (id == R.id.btn_deleteAccount) {
      tdlib.ui().permanentlyDeleteAccount(this, true);
    } else if (id == R.id.btn_clearAllDrafts) {
      showOptions(Lang.getString(R.string.AreYouSureClearDrafts), new int[] {R.id.btn_clearAllDrafts, R.id.btn_cancel}, new String[] {Lang.getString(R.string.PrivacyDeleteCloudDrafts), Lang.getString(R.string.Cancel)}, new int[] {OptionColor.RED, OptionColor.NORMAL}, new int[] {R.drawable.baseline_delete_forever_24, R.drawable.baseline_cancel_24}, (itemView, actionId) -> {
        if (actionId == R.id.btn_clearAllDrafts) {
          tdlib.client().send(new TdApi.ClearAllDraftMessages(true), tdlib.doneHandler());
        }
        return true;
      });
    }
  }

  @Override
  public void onApplySettings (@IdRes int id, SparseIntArray result) {
    if (id == R.id.btn_accountTTL) {
      if (result.size() == 1) {
        int resultDaysTTL;
        int resultId = result.valueAt(0);
        if (resultId == R.id.btn_1month) {
          resultDaysTTL = 31;
        } else if (resultId == R.id.btn_3months) {
          resultDaysTTL = 91;
        } else if (resultId == R.id.btn_6month) {
          resultDaysTTL = 181;
        } else if (resultId == R.id.btn_1year) {
          resultDaysTTL = 366;
        } else {
          resultDaysTTL = 0;
        }
        if (resultDaysTTL >= 30 && (accountTtl == null || accountTtl.days != resultDaysTTL)) {
          accountTtl = new TdApi.AccountTtl(resultDaysTTL);
          tdlib.client().send(new TdApi.SetAccountTtl(accountTtl), tdlib.okHandler());
          adapter.updateValuedSettingById(R.id.btn_accountTTL);
        }
      }
    }
  }

  // Blocked users

  private int blockedSendersCount = -1;

  private CharSequence getBlockedSendersCount () {
    return blockedSendersCount == -1 ? Lang.getString(R.string.LoadingInformation) : blockedSendersCount > 0 ? Lang.pluralBold(R.string.xSenders, blockedSendersCount) : Lang.getString(R.string.BlockedNone);
  }

  private String getMapProviderName (boolean cloud) {
    switch (Settings.instance().getMapProviderType(cloud)) {
      case Settings.MAP_PROVIDER_TELEGRAM:
        return Lang.getString(R.string.MapPreviewProviderTelegram);
      case Settings.MAP_PROVIDER_NONE:
        return Lang.getString(R.string.MapPreviewProviderNone);
      case Settings.MAP_PROVIDER_GOOGLE:
        return Lang.getString(R.string.MapPreviewProviderGoogle);
      default:
        return Lang.getString(R.string.MapPreviewProviderUnset);
    }
  }

  @Override
  public void onUserUpdated (TdApi.User user) { }

  public void diffBlockList (int delta) {
    if (this.blockedSendersCount != -1) {
      this.blockedSendersCount += delta;
      adapter.updateValuedSettingById(R.id.btn_blockedSenders);
    }
  }

  @Override
  public void onChatBlockListChanged (long chatId, @Nullable TdApi.BlockList blockList) {
    runOnUiThread(() -> {
      if (!isDestroyed()) {
        tdlib.send(new TdApi.GetBlockedMessageSenders(new TdApi.BlockListMain(), 0, 1), blockedSenders -> runOnUiThreadOptional(() ->
          setBlockedSenders(blockedSenders)
        ), null /*ignored*/);
      }
    }, 350l);
  }

  // Sessions

  private Tdlib.SessionsInfo sessions;

  private void fetchSessions () {
    tdlib.getSessions(true, sessionsInfo -> {
      if (sessionsInfo != null) {
        runOnUiThreadOptional(() -> {
          setSessions(sessionsInfo);
        });
      }
    });
  }

  private void setSessions (Tdlib.SessionsInfo sessions) {
    this.sessions = sessions;
    adapter.updateValuedSettingById(R.id.btn_sessions);
  }

  @Override
  public void onSessionListChanged (Tdlib tdlib, boolean isWeakGuess) {
    fetchSessions();
  }

  public Tdlib.SessionsInfo getSessions () {
    return sessions;
  }

  private CharSequence getAuthorizationsCount () {
    if (sessions == null) {
      return Lang.getString(R.string.LoadingInformation);
    }
    CharSequence sessionsStr = Lang.pluralBold(R.string.xSessions, sessions.activeSessionCount);
    if (websites == null || websites.websites.length == 0) {
      return sessionsStr;
    } else {
      return Lang.getCharSequence(R.string.format_sessionsAndWebsites, sessionsStr, Lang.pluralBold(R.string.xWebsites, websites.websites.length));
    }
  }

  // website authorizations

  public interface WebsitesLoadListener {
    void onWebsitesLoaded (TdApi.ConnectedWebsites websites);
  }

  private @Nullable WebsitesLoadListener websitesLoadListener;
  private TdApi.ConnectedWebsites websites;

  public void setWebsitesLoadListener (@Nullable WebsitesLoadListener websitesLoadListener) {
    this.websitesLoadListener = websitesLoadListener;
  }

  private void setWebsites (TdApi.ConnectedWebsites websites) {
    this.websites = websites;
    adapter.updateValuedSettingById(R.id.btn_sessions);
    if (websitesLoadListener != null) {
      websitesLoadListener.onWebsitesLoaded(websites);
    }
  }

  public void updateWebsites (ArrayList<TdApi.ConnectedWebsite> websites) {
    if (isDestroyed()) {
      return;
    }
    this.websites.websites = new TdApi.ConnectedWebsite[websites.size()];
    websites.toArray(this.websites.websites);
    adapter.updateValuedSettingById(R.id.btn_sessions);
  }

  public TdApi.ConnectedWebsites getWebsites () {
    return websites;
  }

  // Password state

  public interface PasswordStateLoadListener {
    void onPasswordStateLoaded (TdApi.PasswordState state);
  }

  private @Nullable PasswordStateLoadListener passwordListener;
  private TdApi.PasswordState passwordState;

  @Override
  public boolean needAsynchronousAnimation () {
    return !isOnlyPrivacy() && passwordState == null;
  }

  private void setPasswordState (TdApi.PasswordState passwordState, boolean needCallback) {
    this.passwordState = passwordState;
    int passwordIndex = adapter.indexOfViewById(R.id.btn_2fa);
    if (passwordIndex != -1) {
      adapter.updateValuedSettingByPosition(passwordIndex);
    }
    int loginEmailIndex = adapter.indexOfViewById(R.id.btn_loginEmail);
    boolean hadLoginEmail = (loginEmailIndex != -1);
    boolean hasLoginEmail = (passwordState != null && !StringUtils.isEmpty(passwordState.loginEmailAddressPattern));
    if (hadLoginEmail != hasLoginEmail) {
      if (hasLoginEmail) {
        adapter.addItems(passwordIndex + 1,
          new ListItem(ListItem.TYPE_SEPARATOR_FULL),
          new ListItem(ListItem.TYPE_VALUED_SETTING, R.id.btn_loginEmail, R.drawable.baseline_alternate_email_24, R.string.LoginEmail)
        );
      } else {
        adapter.removeRange(loginEmailIndex - 1, 2);
      }
    } else if (hasLoginEmail) {
      adapter.updateValuedSettingByPosition(loginEmailIndex);
    }
    if (needCallback && passwordListener != null) {
      passwordListener.onPasswordStateLoaded(passwordState);
    }
    executeScheduledAnimation();
  }

  public void reloadPasswordState () {
    setPasswordState(null, false);
    executeOnUiThreadOptional(() -> tdlib.send(new TdApi.GetPasswordState(), this::updatePasswordState, error -> executeOnUiThreadOptional(this::executeScheduledAnimation)));
  }

  public void updatePasswordState (TdApi.PasswordState passwordState) {
    executeOnUiThreadOptional(() ->
      setPasswordState(passwordState, false)
    );
  }


  public void setPasswordListener (@Nullable PasswordStateLoadListener listener) {
    this.passwordListener = listener;
  }

  private String getPasswordState () {
    return Lang.getString(passwordState != null ? passwordState.hasPassword ? R.string.PasswordEnabled : passwordState.recoveryEmailAddressCodeInfo != null ? R.string.AwaitingEmailConfirmation : R.string.PasswordDisabled : R.string.LoadingInformation);
  }


  public TdApi.PasswordState getCurrentPasswordState () {
    return passwordState;
  }

  // Account TTL

  private TdApi.AccountTtl accountTtl;

  private void setAccountTTL (TdApi.AccountTtl accountTTL) {
    this.accountTtl = accountTTL;
    adapter.updateValuedSettingById(R.id.btn_accountTTL);
  }

  public CharSequence getAccountTTLIn () {
    if (accountTtl != null) {
      CharSequence duration;
      int days = accountTtl.days;
      if (days < 30) {
        duration = Lang.pluralBold(R.string.DeleteAccountIfAwayForDays, days);
      } else {
        days /= 30;
        if (days < 12) {
          duration = Lang.pluralBold(R.string.DeleteAccountIfAwayForMonths, days);
        } else {
          days /= 12;
          duration = Lang.pluralBold(R.string.DeleteAccountIfAwayForYears, days);
        }
      }
      return duration;
    }
    return Lang.getString(R.string.LoadingInformation);
  }

  // Privacy rules

  private static String buildPrivacy (Tdlib tdlib, TdApi.UserPrivacySettingRules rules, @TdApi.UserPrivacySetting.Constructors int privacyKey) {
    PrivacySettings privacy = PrivacySettings.valueOf(rules);
    if (privacy == null)
      return Lang.getString(R.string.LoadingInformation);
    return TD.getPrivacyRulesString(tdlib, privacyKey, privacy);
  }

  public TdApi.UserPrivacySettingRules getPrivacyRules (int privacyKey) {
    return privacyRules.get(privacyKey);
  }

  private final SparseArrayCompat<TdApi.UserPrivacySettingRules> privacyRules = new SparseArrayCompat<>();

  private String buildPrivacy (@TdApi.UserPrivacySetting.Constructors int privacyKey) {
    return buildPrivacy(tdlib, privacyRules.get(privacyKey), privacyKey);
  }

  private void setPrivacyRules (int privacyKey, TdApi.UserPrivacySettingRules rules) {
    privacyRules.put(privacyKey, rules);
    if (adapter != null)
      adapter.updateValuedSettingByLongId(privacyKey);
  }

  private TdApi.NewChatPrivacySettings newChatPrivacySettings;

  private CharSequence buildNewChatsPrivacy () {
    if (newChatPrivacySettings != null) {
      return Lang.getMarkdownString(this, newChatPrivacySettings.allowNewChatsFromUnknownUsers ? R.string.PrivacyMessageEverybody : R.string.PrivacyMessageContactsPremium);
    } else {
      return Lang.getString(R.string.LoadingInformation);
    }
  }

  private void setNewChatsPrivacy (TdApi.NewChatPrivacySettings newChatPrivacySettings) {
    this.newChatPrivacySettings = newChatPrivacySettings;
    adapter.updateValuedSettingById(R.id.btn_newChatsPrivacy);
  }

  private void setBlockedSenders (TdApi.MessageSenders senders) {
    if (this.blockedSendersCount != senders.totalCount) {
      this.blockedSendersCount = senders.totalCount;
      adapter.updateValuedSettingById(R.id.btn_blockedSenders);
    }
  }
}
