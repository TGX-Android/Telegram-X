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
 * File created on 17/11/2016
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.attach.AvatarPickerManager;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.component.user.BubbleView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ActivityResultHandler;
import org.thunderdog.challegram.telegram.PrivacySettings;
import org.thunderdog.challegram.telegram.PrivacySettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.NoUnderlineClickableSpan;
import org.thunderdog.challegram.util.ProfilePhotoDrawModifier;
import org.thunderdog.challegram.util.UserPickerMultiDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.core.StringUtils;
import me.vkryl.core.collection.LongList;
import tgx.td.Td;

public class SettingsPrivacyKeyController extends RecyclerViewController<SettingsPrivacyKeyController.Args> implements View.OnClickListener, UserPickerMultiDelegate, PrivacySettingsListener, ActivityResultHandler,
  TdlibCache.UserDataChangeListener {
  @IntDef({
    Mode.USER_PRIVACY_SETTING,
    Mode.NEW_CHATS_PRIVACY
  })
  public @interface Mode {
    int
      USER_PRIVACY_SETTING = 0,
      NEW_CHATS_PRIVACY = 1;
  }

  public static class Args {
    public final @Mode int mode;
    public TdApi.UserPrivacySetting userPrivacySetting;

    public Args (TdApi.UserPrivacySetting userPrivacySetting) {
      this(Mode.USER_PRIVACY_SETTING);
      this.userPrivacySetting = userPrivacySetting;
    }

    private Args (@Mode int mode) {
      this.mode = mode;
    }

    public static Args newChatsPrivacy () {
      return new Args(Mode.NEW_CHATS_PRIVACY);
    }
  }

  public SettingsPrivacyKeyController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_privacyKey;
  }

  @Override
  public CharSequence getName () {
    Args args = getArgumentsStrict();
    switch (args.mode) {
      case Mode.USER_PRIVACY_SETTING:
        return Lang.getString(getName(args.userPrivacySetting, false, false));
      case Mode.NEW_CHATS_PRIVACY:
        return Lang.getString(R.string.PrivacyMessageTitle);
    }
    throw new UnsupportedOperationException();
  }

  public static int getIcon (TdApi.UserPrivacySetting privacySetting) {
    switch (privacySetting.getConstructor()) {
      case TdApi.UserPrivacySettingShowPhoneNumber.CONSTRUCTOR:
        return R.drawable.baseline_call_24;
      case TdApi.UserPrivacySettingShowBio.CONSTRUCTOR:
        return R.drawable.baseline_info_24;
      case TdApi.UserPrivacySettingShowBirthdate.CONSTRUCTOR:
        return R.drawable.baseline_cake_variant_24;
      case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
        return R.drawable.baseline_search_24;
      case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR:
        return R.drawable.baseline_visibility_24;
      case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR:
        return R.drawable.baseline_emoticon_outline_24;
      case TdApi.UserPrivacySettingShowLinkInForwardedMessages.CONSTRUCTOR:
        return R.drawable.baseline_forward_24;
      case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR:
        return R.drawable.baseline_person_add_24;
      case TdApi.UserPrivacySettingAllowCalls.CONSTRUCTOR:
        return R.drawable.baseline_phone_in_talk_24;
      case TdApi.UserPrivacySettingAllowPeerToPeerCalls.CONSTRUCTOR:
        return R.drawable.baseline_swap_horiz_24;
      case TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages.CONSTRUCTOR:
        return R.drawable.baseline_mic_24;
      case TdApi.UserPrivacySettingAutosaveGifts.CONSTRUCTOR:
        return R.drawable.baseline_gift_outline_24;
      default:
        Td.assertUserPrivacySetting_99ac9ff();
        throw Td.unsupported(privacySetting);
    }
  }

  public static int getName (TdApi.UserPrivacySetting privacyKey, boolean isException, boolean isMultiChatException) {
    switch (privacyKey.getConstructor()) {
      case TdApi.UserPrivacySettingShowPhoneNumber.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyPhoneNumber : R.string.PhoneNumber;
      case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
        return R.string.FindingByPhoneNumber;
      case TdApi.UserPrivacySettingShowBio.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyBio : R.string.UserBio;
      case TdApi.UserPrivacySettingShowBirthdate.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyBirthdate : R.string.UserBirthdate;
      case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR:
        return isException ? (isMultiChatException ? R.string.EditPrivacyChatInviteGroup : R.string.EditPrivacyChatInvite) : R.string.GroupsAndChannels;
      case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyStatus : R.string.LastSeen;
      case TdApi.UserPrivacySettingAllowCalls.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyCall : R.string.VoiceCalls;
      case TdApi.UserPrivacySettingAllowPeerToPeerCalls.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyCallP2P : R.string.PrivacyCallsP2PTitle2;
      case TdApi.UserPrivacySettingShowLinkInForwardedMessages.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyForward : R.string.PrivacyForwardLinkTitle;
      case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyPhoto : R.string.PrivacyPhotoTitle;
      case TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyVoice : R.string.PrivacyVoiceVideoTitle;
      case TdApi.UserPrivacySettingAutosaveGifts.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyGifts : R.string.PrivacyGiftsTitle;
      default:
        Td.assertUserPrivacySetting_99ac9ff();
        throw Td.unsupported(privacyKey);
    }
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    Args args = getArgumentsStrict();
    outState.putInt(keyPrefix + "mode", args.mode);
    switch (args.mode) {
      case Mode.USER_PRIVACY_SETTING:
        outState.putInt(keyPrefix + "setting", args.userPrivacySetting.getConstructor());
        break;
      case Mode.NEW_CHATS_PRIVACY:
        // Nothing to save
        break;
      default:
        throw new UnsupportedOperationException();
    }
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    @Mode int mode = in.getInt(keyPrefix + "mode", Mode.USER_PRIVACY_SETTING);
    switch (mode) {
      case Mode.USER_PRIVACY_SETTING: {
        @TdApi.UserPrivacySetting.Constructors int constructor = in.getInt(keyPrefix + "setting", 0);
        if (constructor != 0) {
          TdApi.UserPrivacySetting setting = Td.constructUserPrivacySetting(constructor);
          setArguments(new Args(setting));
          return true;
        }
        break;
      }
      case Mode.NEW_CHATS_PRIVACY: {
        setArguments(new Args(mode));
        return true;
      }
    }
    return false;
  }

  private SettingsAdapter adapter;
  private PrivacySettings privacyRules, changedPrivacyRules;
  private @Nullable TdApi.ReadDatePrivacySettings readDatePrivacySetting;

  private PrivacySettings currentRules () {
    return changedPrivacyRules != null ? changedPrivacyRules : privacyRules;
  }

  private void setPrivacyRules (TdApi.UserPrivacySettingRules rules) {
    if (this.privacyRules == null) {
      this.privacyRules = PrivacySettings.valueOf(rules);
      buildCells();
      if (!needAsynchronousAnimation()) {
        executeScheduledAnimation();
      }
    } else {
      int prevMode = currentRules().getMode();
      this.privacyRules = PrivacySettings.valueOf(rules);
      if (this.changedPrivacyRules == null) {
        int newMode = currentRules().getMode();
        int modeId;
        switch (newMode) {
          case PrivacySettings.Mode.CONTACTS:
            modeId = R.id.btn_contacts;
            break;
          case PrivacySettings.Mode.EVERYBODY:
            modeId = R.id.btn_everybody;
            break;
          case PrivacySettings.Mode.NOBODY:
            modeId = R.id.btn_nobody;
            break;
          default:
            throw new UnsupportedOperationException();
        }
        int index = adapter.indexOfViewById(modeId);
        if (index != -1) {
          adapter.processToggle(null, adapter.getItems().get(index), true);
        }
        updateRulesState(currentRules());
        adapter.updateValuedSettingById(R.id.btn_alwaysAllow);
        adapter.updateValuedSettingById(R.id.btn_neverAllow);
      } else if (this.changedPrivacyRules.equals(this.privacyRules)) {
        this.changedPrivacyRules = null;
      }
    }
  }

  private void setNewChatPrivacySettings (@NonNull TdApi.NewChatPrivacySettings newChatPrivacySettings) {
    if (newChatPrivacySettings.allowNewChatsFromUnknownUsers) {
      setPrivacyRules(new TdApi.UserPrivacySettingRules(new TdApi.UserPrivacySettingRule[] {new TdApi.UserPrivacySettingRuleAllowAll()}));
    } else {
      setPrivacyRules(new TdApi.UserPrivacySettingRules(new TdApi.UserPrivacySettingRule[] {new TdApi.UserPrivacySettingRuleAllowContacts(), new TdApi.UserPrivacySettingRuleAllowPremiumUsers()}));
    }
  }

  private boolean needNobodyOption () {
    Args args = getArgumentsStrict();
    switch (args.mode) {
      case Mode.USER_PRIVACY_SETTING: {
        //noinspection SwitchIntDef
        switch (args.userPrivacySetting.getConstructor()) {
          // case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR:
          case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
          // case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR:
            return false;
        }
        return true;
      }
      case Mode.NEW_CHATS_PRIVACY:
        return false;
    }
    throw new UnsupportedOperationException();
  }

  private boolean needExceptions () {
    Args args = getArgumentsStrict();
    switch (args.mode) {
      case Mode.USER_PRIVACY_SETTING: {
        //noinspection SwitchIntDef
        switch (args.userPrivacySetting.getConstructor()) {
          case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
            return false;
        }
        return true;
      }
      case Mode.NEW_CHATS_PRIVACY:
        return false;
    }
    throw new UnsupportedOperationException();
  }

  private boolean loadingLink;

  private void buildCells () {
    if (privacyRules == null) {
      return;
    }

    final ListItem headerItem;
    final ListItem hintItem;

    TdApi.InternalLinkType internalLinkType = null;

    final int rulesType = privacyRules.getMode();

    final Args args = getArgumentsStrict();

    switch (args.mode) {
      case Mode.USER_PRIVACY_SETTING: {
        switch (args.userPrivacySetting.getConstructor()) {
          case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanAddYouToGroupsAndChannels);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, R.string.WhoCanAddMeInfo);
            break;
          }
          case TdApi.UserPrivacySettingShowLinkInForwardedMessages.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanForwardLink);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, R.string.WhoCanForwardLinkInfo);
            break;
          }
          case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanSeePhoto);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, R.string.WhoCanSeePhotoInfo);
            break;
          }
          case TdApi.UserPrivacySettingAllowCalls.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanCallMe);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, R.string.VoiceCallPrivacyDesc);
            break;
          }
          case TdApi.UserPrivacySettingShowPhoneNumber.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanSeePhone);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, R.string.WhoCanSeePhoneInfo);
            break;
          }
          case TdApi.UserPrivacySettingShowBio.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanSeeBio);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, R.string.WhoCanSeeBioInfo);
            break;
          }
          case TdApi.UserPrivacySettingShowBirthdate.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanSeeBirthdate);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, R.string.WhoCanSeeBirthdateInfo);
            break;
          }
          case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanFindByPhone);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, rulesType == PrivacySettings.Mode.EVERYBODY ? R.string.WhoCanFindByPhoneInfoEveryone : R.string.WhoCanFindByPhoneInfoContacts);
            TdApi.User user = tdlib.myUser();
            if (user != null) {
              internalLinkType = new TdApi.InternalLinkTypeUserPhoneNumber(user.phoneNumber, "", true);
            }
            break;
          }
          case TdApi.UserPrivacySettingAllowPeerToPeerCalls.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.UseP2PWith);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, R.string.PeerToPeerInfo);
            break;
          }
          case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, R.id.btn_description, 0, R.string.LastSeenTitle);

            String str = Lang.getString(R.string.CustomHelp);
            int i = str.indexOf(':');
            if (i == -1) {
              hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CustomHelp);
            } else {
              SpannableStringBuilder b = new SpannableStringBuilder(str);
              b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoMedium(), ColorId.background_textLight), 0, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
              hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, b, false);
            }
            break;
          }
          case TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanSendVoiceVideo);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, R.string.VoiceVideoPrivacyDesc);
            break;
          }
          case TdApi.UserPrivacySettingAutosaveGifts.CONSTRUCTOR: {
            headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanDisplayGifts);
            hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, R.string.GiftsPrivacyDesc);
            break;
          }
          default: {
            Td.assertUserPrivacySetting_99ac9ff();
            throw Td.unsupported(args.userPrivacySetting);
          }
        }
        break;
      }
      case Mode.NEW_CHATS_PRIVACY: {
        headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanSendMessages);
        hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, Lang.getMarkdownString(this, R.string.NewChatsPrivacyDesc));
        break;
      }
      default: {
        throw new UnsupportedOperationException();
      }
    }

    if (!loadingLink && StringUtils.isEmpty(additionalLink) && internalLinkType != null) {
      loadingLink = true;
      tdlib.send(new TdApi.GetInternalLink(internalLinkType, true), (httpUrl, error) -> runOnUiThreadOptional(() -> {
        if (loadingLink) {
          loadingLink = false;
          if (httpUrl != null) {
            additionalLink = httpUrl.url;
            updateHints();
          }
          if (!needAsynchronousAnimation()) {
            executeScheduledAnimation();
          }
        }
      }));
    }

    ArrayList<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(headerItem);

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_everybody, 0, R.string.Everybody, null, R.id.btn_privacyRadio, rulesType == PrivacySettings.Mode.EVERYBODY));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    if (args.mode == Mode.NEW_CHATS_PRIVACY) {
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_contacts, 0, Lang.getMarkdownString(this, R.string.MyContactsAndPremium), R.id.btn_privacyRadio, rulesType == PrivacySettings.Mode.CONTACTS));
    } else {
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_contacts, 0, R.string.MyContacts, null, R.id.btn_privacyRadio, rulesType == PrivacySettings.Mode.CONTACTS));
    }
    if (needNobodyOption() || rulesType == PrivacySettings.Mode.NOBODY) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_nobody, 0, R.string.Nobody, null, R.id.btn_privacyRadio, rulesType == PrivacySettings.Mode.NOBODY));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(hintItem);
    if (needExceptions()) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AddExceptions));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      boolean needNever = privacyRules.needNeverAllow();
      if (needNever) {
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_neverAllow, 0, args.userPrivacySetting.getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR ? R.string.NeverShareWith : R.string.NeverAllow));
      }
      if (privacyRules.needAlwaysAllow()) {
        if (needNever) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_alwaysAllow, 0, args.userPrivacySetting.getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR ? R.string.AlwaysShareWith : R.string.AlwaysAllow));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CustomShareSettingsHelp));
    }

    if (needExtraToggle(privacyRules)) {
      List<ListItem> extraItems = newExtraToggleItems();
      items.addAll(extraItems);
    }

    /*if (privacyKey.getConstructor() == TdApi.UserPrivacySettingAllowCalls.CONSTRUCTOR) {
      items.add(new SettingItem(SettingItem.TYPE_HEADER, 0, 0, R.string.PrivacyCallsP2PTitle));
      items.add(new SettingItem(SettingItem.TYPE_SHADOW_TOP));

      int peerToPeerOption = tdlib.settings().peerToPeerOption();

      items.add(new SettingItem(SettingItem.TYPE_RADIO_OPTION, R.id.btn_peerToPeer_everybody, 0, R.string.Everybody, null, R.id.btn_peerToPeer, peerToPeerOption == TD.TYPE_EVERYBODY));
      items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
      items.add(new SettingItem(SettingItem.TYPE_RADIO_OPTION, R.id.btn_peerToPeer_contacts, 0, R.string.MyContacts, null, R.id.btn_peerToPeer, peerToPeerOption == TD.TYPE_CONTACTS));
      items.add(new SettingItem(SettingItem.TYPE_SEPARATOR_FULL));
      items.add(new SettingItem(SettingItem.TYPE_RADIO_OPTION, R.id.btn_peerToPeer_never, 0, R.string.Never, null, R.id.btn_peerToPeer, peerToPeerOption == TD.TYPE_NOBODY));
      items.add(new SettingItem(SettingItem.TYPE_SHADOW_BOTTOM));
      items.add(new SettingItem(SettingItem.TYPE_DESCRIPTION, 0, 0, R.string.PeerToPeerInfo));
    }*/

    adapter.setItems(items, true);
    restorePersistentScrollPosition();
  }

  private String additionalLink;

  private void updateHints () {
    Args args = getArgumentsStrict();
    if (args.mode != Mode.USER_PRIVACY_SETTING) {
      return;
    }
    @PrivacySettings.Mode int mode = currentRules().getMode();
    //noinspection SwitchIntDef
    switch (args.userPrivacySetting.getConstructor()) {
      case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:  {
        int i = adapter.indexOfViewById(R.id.btn_description);
        if (i != -1) {
          boolean changed;
          ListItem item = adapter.getItems().get(i);
          if (mode == PrivacySettings.Mode.EVERYBODY && !StringUtils.isEmpty(additionalLink)) {
            changed = item.setStringIfChanged(Lang.getString(R.string.WhoCanFindByPhoneInfoEveryoneLink, (target, argStart, argEnd, argIndex, needFakeBold) -> new NoUnderlineClickableSpan() {
              @Override
              public void onClick (@NonNull View widget) {
                tdlib.ui().showUrlOptions(SettingsPrivacyKeyController.this, additionalLink, () -> new TdlibUi.UrlOpenParameters().disableInstantView());
              }
            }, additionalLink));
          } else {
            changed = item.setStringIfChanged(mode == PrivacySettings.Mode.EVERYBODY ? R.string.WhoCanFindByPhoneInfoEveryone : R.string.WhoCanFindByPhoneInfoContacts);
          }
          if (changed) {
            adapter.notifyItemChanged(i);
          }
        }
        break;
      }
    }
  }

  private void updateRulesState (PrivacySettings newPrivacySettings) {
    int newRules = newPrivacySettings.getMode();
    if (!needExceptions()) {
      updateHints();
      return;
    }

    updateExtraToggle(newPrivacySettings);

    final boolean isShowStatus = getArgumentsStrict().userPrivacySetting.getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR;

    boolean prevHadNever = adapter.indexOfViewById(R.id.btn_neverAllow) != -1;
    boolean prevHadAlways = adapter.indexOfViewById(R.id.btn_alwaysAllow) != -1;
    boolean prevHadSeparator = prevHadAlways && prevHadNever;

    boolean nowHasNever = newPrivacySettings.needNeverAllow();
    boolean nowHasAlways = newPrivacySettings.needAlwaysAllow();
    boolean nowHasSeparator = nowHasAlways && nowHasNever;

    List<ListItem> items = adapter.getItems();
    int index = adapter.indexOfViewByType(ListItem.TYPE_VALUED_SETTING_COMPACT);

    final int alwaysString = isShowStatus ? R.string.AlwaysShareWith : R.string.AlwaysAllow;
    final int neverString = isShowStatus ? R.string.NeverShareWith : R.string.NeverAllow;

    if (nowHasSeparator == prevHadSeparator) {
      if (!nowHasSeparator) {
        if (nowHasAlways) {
          items.set(index, new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_alwaysAllow, 0, alwaysString));
        } else {
          items.set(index, new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_neverAllow, 0, neverString));
        }
        adapter.notifyItemChanged(index);
      }
    } else if (nowHasSeparator) {
      if (prevHadNever) {
        items.add(index + 1, new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_alwaysAllow, 0, alwaysString));
        items.add(index + 1, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        adapter.notifyItemRangeInserted(index + 1, 2);
      } else {
        items.add(index, new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        items.add(index, new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_neverAllow, 0, neverString));
        adapter.notifyItemRangeInserted(index, 2);
      }
    } else if (nowHasNever) {
      items.remove(index + 1);
      items.remove(index + 1);
      adapter.notifyItemRangeRemoved(index + 1, 2);
    } else {
      items.remove(index);
      items.remove(index);
      adapter.notifyItemRangeRemoved(index, 2);
    }
  }

  private boolean needExtraToggle (PrivacySettings privacyRules) {
    Args args = getArgumentsStrict();
    if (args.mode != Mode.USER_PRIVACY_SETTING) {
      return false;
    }
    switch (args.userPrivacySetting.getConstructor()) {
      case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR:
      case TdApi.UserPrivacySettingShowBirthdate.CONSTRUCTOR:
      case TdApi.UserPrivacySettingAutosaveGifts.CONSTRUCTOR:
        return true;
      case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR:
        return privacyRules.getMode() != PrivacySettings.Mode.EVERYBODY || privacyRules.getMinusUserIdCount() > 0;
      case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR:
        return privacyRules.getMode() != PrivacySettings.Mode.EVERYBODY;
    }
    return false;
  }

  private int getExtraItemCount () {
    if (getArgumentsStrict().userPrivacySetting.getConstructor() == TdApi.UserPrivacySettingShowBirthdate.CONSTRUCTOR) {
      return 3;
    } else {
      return 4;
    }
  }

  private List<ListItem> newExtraToggleItems () {
    switch (getArgumentsStrict().userPrivacySetting.getConstructor()) {
      case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR: {
        return Arrays.asList(
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_togglePermission, 0, R.string.PublicPhoto),
          new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
          new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.PublicPhotoHint)
        );
      }
      case TdApi.UserPrivacySettingShowBirthdate.CONSTRUCTOR: {
        return Arrays.asList(
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_togglePermission, 0, R.string.Birthdate),
          new ListItem(ListItem.TYPE_SHADOW_BOTTOM)
        );
      }
      case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR: {
        return Arrays.asList(
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_togglePermission, 0, R.string.HideReadTime),
          new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
          new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.HideReadTimeDesc)
        );
      }
      case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR: {
        return Arrays.asList(
          new ListItem(ListItem.TYPE_SHADOW_TOP),
          new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_togglePermission, 0, Lang.getMarkdownString(this, R.string.AllowPremiumInvite)),
          new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
          new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.AllowPremiumInviteDesc))
        );
      }
      case TdApi.UserPrivacySettingAutosaveGifts.CONSTRUCTOR: {
        if (currentRules().getMode() != PrivacySettings.Mode.EVERYBODY) {
          return Arrays.asList(
            new ListItem(ListItem.TYPE_SHADOW_TOP),
            new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_togglePermission, 0, Lang.getMarkdownString(this, R.string.AllowBotsAndMiniApps)),
            new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
            new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.AllowBotsAndMiniAppsDesc))
          );
        } else {
          return Arrays.asList(
            new ListItem(ListItem.TYPE_SHADOW_TOP),
            new ListItem(ListItem.TYPE_RADIO_SETTING, R.id.btn_togglePermission, 0, Lang.getMarkdownString(this, R.string.RestrictBotsAndMiniApps)),
            new ListItem(ListItem.TYPE_SHADOW_BOTTOM),
            new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, Lang.getMarkdownString(this, R.string.RestrictBotsAndMiniAppsDesc))
          );
        }
      }
      default:
        throw new IllegalStateException();
    }
  }

  private void updateExtraToggle (PrivacySettings newPrivacySettings) {
    if (newPrivacySettings == null) {
      // Primary privacy information isn't yet loaded.
      return;
    }
    int index = adapter.indexOfViewById(R.id.btn_togglePermission);
    boolean prevHadExtraToggle = index != -1;
    boolean nowHasExtraToggle = needExtraToggle(newPrivacySettings);
    if (prevHadExtraToggle != nowHasExtraToggle) {
      if (nowHasExtraToggle) {
        List<ListItem> extraItems = newExtraToggleItems();
        int atIndex = adapter.getItems().size();
        adapter.getItems().addAll(extraItems);
        adapter.notifyItemRangeInserted(atIndex, extraItems.size());
        loadExtraToggle();
      } else {
        adapter.removeRange(index - 1, getExtraItemCount());
      }
    } else if (nowHasExtraToggle) {
      adapter.updateValuedSettingByPosition(index);
    }
  }

  @Override
  public boolean needAsynchronousAnimation () {
    return privacyRules == null || loadingLink;
  }

  @Override
  public void onPrivacySettingRulesChanged (TdApi.UserPrivacySetting setting, TdApi.UserPrivacySettingRules rules) {
    runOnUiThreadOptional(() -> {
      Args args = getArgumentsStrict();
      if (args.mode == Mode.USER_PRIVACY_SETTING && args.userPrivacySetting.getConstructor() == setting.getConstructor()) {
        setPrivacyRules(rules);
      }
    });
  }

  @Override
  public void onNewChatPrivacySettingsChanged (TdApi.NewChatPrivacySettings settings) {
    runOnUiThreadOptional(() -> {
      Args args = getArgumentsStrict();
      if (args.mode == Mode.NEW_CHATS_PRIVACY) {
        setNewChatPrivacySettings(settings);
      }
    });
  }

  private long subscribedToUserId;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    Args args = getArgumentsStrict();
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_alwaysAllow) {
          int count = currentRules().getPlusTotalCount(tdlib);
          view.setData(count > 0 ? Lang.plural(R.string.xUsers, count) : Lang.getString(R.string.PrivacyAddUsers));
        } else if (itemId == R.id.btn_neverAllow) {
          int count = currentRules().getMinusTotalCount(tdlib);
          view.setData(count > 0 ? Lang.plural(R.string.xUsers, count) : Lang.getString(R.string.PrivacyAddUsers));
        }

        if (itemId == R.id.btn_togglePermission) {
          switch (args.userPrivacySetting.getConstructor()) {
            case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR: {
              final TdApi.UserFullInfo myUserFull = tdlib.myUserFull();
              final boolean hasAvatar = myUserFull != null && myUserFull.publicPhoto != null;

              view.setData(Lang.getString(hasAvatar ? R.string.PublicPhotoSet : R.string.PublicPhotoNoSet));
              view.setDrawModifier(new ProfilePhotoDrawModifier().requestFiles(view.getComplexReceiver(), tdlib));
              break;
            }
            case TdApi.UserPrivacySettingShowBirthdate.CONSTRUCTOR: {
              final TdApi.UserFullInfo myUserFull = tdlib.myUserFull();
              if (myUserFull != null) {
                if (myUserFull.birthdate != null) {
                  view.setName(R.string.UserBirthdate);
                  view.setData(Lang.getBirthdate(myUserFull.birthdate, true, true));
                } else {
                  view.setName(Lang.getString(R.string.ReminderSetBirthdateText));
                  view.setData(Lang.getString(R.string.ReminderSetBirthdate));
                }
              } else {
                view.setData(R.string.LoadingInformation);
              }
              break;
            }
            case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR: {
              view.setEnabledAnimated(readDatePrivacySetting != null, isUpdate);
              view.getToggler().setRadioEnabled(readDatePrivacySetting != null && !readDatePrivacySetting.showReadDate, isUpdate);
              break;
            }
            case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR: {
              view.getToggler().setRadioEnabled(currentRules().needPlusPremium(), isUpdate);
              break;
            }
            case TdApi.UserPrivacySettingAutosaveGifts.CONSTRUCTOR: {
              view.getToggler().setRadioEnabled(currentRules().needPlusOrMinusBots(), isUpdate);
              break;
            }
            default: {
              Td.assertUserPrivacySetting_99ac9ff();
              throw Td.unsupported(args.userPrivacySetting);
            }
          }
        } else {
          view.setDrawModifier(null);
        }
      }

      @Override
      protected void modifyHeaderTextView (TextView textView, int viewHeight, int paddingTop) {
        textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setPadding(textView.getPaddingLeft(), paddingTop + Screen.dp(6f), textView.getPaddingRight(), Screen.dp(6f));
        textView.setSingleLine(false);
      }
    };
    recyclerView.setAdapter(adapter);
    switch (args.mode) {
      case Mode.USER_PRIVACY_SETTING: {
        tdlib.send(new TdApi.GetUserPrivacySettingRules(args.userPrivacySetting), (rules, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showError(error);
          } else {
            setPrivacyRules(rules);
          }
        }));
        loadExtraToggle();
        break;
      }
      case Mode.NEW_CHATS_PRIVACY: {
        tdlib.send(new TdApi.GetNewChatPrivacySettings(), (newChatPrivacySettings, error) -> runOnUiThreadOptional(() -> {
          if (error != null) {
            UI.showError(error);
          } else {
            setNewChatPrivacySettings(newChatPrivacySettings);
          }
        }));
        break;
      }
      default:
        throw new UnsupportedOperationException();
    }

    subscribedToUserId = tdlib.myUserId();
    tdlib.cache().addUserDataListener(subscribedToUserId, this);
    tdlib.listeners().subscribeToPrivacyUpdates(this);
  }

  @Override
  public void onUserFullUpdated (long userId, TdApi.UserFullInfo userFull) {
    runOnUiThreadOptional(() -> {
      if (userId == tdlib.myUserId()) {
        adapter.updateValuedSettingById(R.id.btn_togglePermission);
      }
    });
  }

  private void loadExtraToggle () {
    if (getArgumentsStrict().userPrivacySetting.getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR) {
      tdlib.send(new TdApi.GetReadDatePrivacySettings(), (readDatePrivacySetting, error) -> runOnUiThreadOptional(() -> {
        if (error != null) {
          UI.showError(error);
        } else {
          this.readDatePrivacySetting = readDatePrivacySetting;
          updateExtraToggle(currentRules());
        }
      }));
    }
  }

  @Override
  public void onBlur () {
    super.onBlur();
    saveChanges();
  }

  private boolean nothingChanged () {
    return changedPrivacyRules == null || changedPrivacyRules.equals(privacyRules);
  }

  private void saveChanges () {
    if (privacyRules == null || nothingChanged()) {
      return;
    }
    Args args = getArgumentsStrict();
    switch (args.mode) {
      case Mode.USER_PRIVACY_SETTING: {
        TdApi.UserPrivacySettingRules newRules = changedPrivacyRules.toRules();
        tdlib.send(new TdApi.SetUserPrivacySettingRules(args.userPrivacySetting, newRules), tdlib.typedOkHandler());
        break;
      }
      case Mode.NEW_CHATS_PRIVACY: {
        TdApi.NewChatPrivacySettings newChatPrivacySettings = new TdApi.NewChatPrivacySettings(changedPrivacyRules.getMode() == PrivacySettings.Mode.EVERYBODY);
        tdlib.send(new TdApi.SetNewChatPrivacySettings(newChatPrivacySettings), tdlib.typedOkHandler(() ->
          tdlib.listeners().updateNewChatPrivacySettings(newChatPrivacySettings))
        );
        break;
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromPrivacyUpdates(this);
    tdlib.cache().removeUserDataListener(subscribedToUserId, this);
  }

  private int userPickMode;

  @Override
  public long[] getAlreadySelectedChatIds () {
    if (userPickMode == R.id.btn_alwaysAllow) {
      return currentRules().getAllPlusIds();
    } else if (userPickMode == R.id.btn_neverAllow) {
      return currentRules().getAllMinusIds();
    }
    return null;
  }

  private void setAllowUsers (long[] userIds, long[] chatIds) {
    changedPrivacyRules = PrivacySettings.valueOf(currentRules().allowExceptions(userIds, chatIds));
    adapter.updateValuedSettingById(R.id.btn_alwaysAllow);
    adapter.updateValuedSettingById(R.id.btn_neverAllow);
  }

  private void setNeverAllow (long[] userIds, long[] chatIds) {
    changedPrivacyRules = PrivacySettings.valueOf(currentRules().disallowExceptions(userIds, chatIds));
    adapter.updateValuedSettingById(R.id.btn_neverAllow);
    adapter.updateValuedSettingById(R.id.btn_alwaysAllow);
  }

  @Override
  public int provideMultiUserPickerHint () {
    if (userPickMode == R.id.btn_neverAllow) {
      return getArgumentsStrict().userPrivacySetting.getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR ? R.string.NeverShareWith : R.string.NeverAllow;
    } else if (userPickMode == R.id.btn_alwaysAllow) {
      return getArgumentsStrict().userPrivacySetting.getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR ? R.string.AlwaysShareWith : R.string.AlwaysAllow;
    }
    return R.string.AlwaysAllow;
  }

  @Override
  public void onAlreadyPickedChatsChanged (List<BubbleView.Entry> bubbles) {
    LongList userIds = new LongList(bubbles.size());
    LongList chatIds = new LongList(bubbles.size());
    for (BubbleView.Entry entry : bubbles) {
      if (entry.senderId == null) {
        continue;
      }
      switch (entry.senderId.getConstructor()) {
        case TdApi.MessageSenderChat.CONSTRUCTOR:
          chatIds.append(((TdApi.MessageSenderChat) entry.senderId).chatId);
          break;
        case TdApi.MessageSenderUser.CONSTRUCTOR:
          userIds.append(((TdApi.MessageSenderUser) entry.senderId).userId);
          break;
        default:
          Td.assertMessageSender_439d4c9c();
          throw Td.unsupported(entry.senderId);
      }
    }
    long[] pickedUserIds = userIds.get();
    long[] pickedChatIds = chatIds.get();
    if (userPickMode == R.id.btn_alwaysAllow) {
      setAllowUsers(pickedUserIds, pickedChatIds);
    } else if (userPickMode == R.id.btn_neverAllow) {
      setNeverAllow(pickedUserIds, pickedChatIds);
    } else {
      return;
    }
    updateExtraToggle(changedPrivacyRules);
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    final Args args = getArgumentsStrict();
    if (viewId == R.id.btn_alwaysAllow || viewId == R.id.btn_neverAllow) {
      userPickMode = v.getId();
      ContactsController c = new ContactsController(context, tdlib);
      c.setArguments(new ContactsController.Args(this).useGlobalSearch(SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_NEED_GLOBAL_SEARCH | SearchManager.FLAG_NO_BOTS | SearchManager.FLAG_NO_CHANNELS | SearchManager.FLAG_NO_SELF));
      navigateTo(c);
    } else if (viewId == R.id.btn_everybody || viewId == R.id.btn_contacts || viewId == R.id.btn_nobody) {
      ListItem item = (ListItem) v.getTag();
      if (adapter.processToggle(v)) {
        final int desiredMode;
        final int modeId = adapter.getCheckIntResults().get(item.getCheckId());
        if (modeId == R.id.btn_everybody) {
          desiredMode = PrivacySettings.Mode.EVERYBODY;
        } else if (modeId == R.id.btn_contacts) {
          desiredMode = PrivacySettings.Mode.CONTACTS;
        } else if (modeId == R.id.btn_nobody) {
          desiredMode = PrivacySettings.Mode.NOBODY;
        } else {
          return;
        }
        int prevMode = currentRules().getMode();
        boolean plusPremium, plusOrMinusBots;
        switch (args.mode) {
          case Mode.USER_PRIVACY_SETTING: {
            plusPremium = args.userPrivacySetting.getConstructor() == TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR && desiredMode == PrivacySettings.Mode.CONTACTS;
            plusOrMinusBots = args.userPrivacySetting.getConstructor() == TdApi.UserPrivacySettingAutosaveGifts.CONSTRUCTOR && desiredMode != PrivacySettings.Mode.EVERYBODY && !(prevMode != PrivacySettings.Mode.EVERYBODY && !currentRules().needPlusOrMinusBots());
            break;
          }
          case Mode.NEW_CHATS_PRIVACY: {
            plusPremium = desiredMode == PrivacySettings.Mode.CONTACTS;
            plusOrMinusBots = false;
            break;
          }
          default:
            throw new UnsupportedOperationException();
        }
        changedPrivacyRules = PrivacySettings.valueOf(currentRules().toggleGlobal(desiredMode, plusPremium, plusOrMinusBots));
        updateRulesState(changedPrivacyRules);
      }
    } else if (viewId == R.id.btn_togglePermission) {
      switch (args.userPrivacySetting.getConstructor()) {
        case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR: {
          getAvatarPickerManager().showMenuForProfile(null, true);
          break;
        }
        case TdApi.UserPrivacySettingShowBirthdate.CONSTRUCTOR: {
          tdlib.ui().openBirthdateEditor(SettingsPrivacyKeyController.this, v, TdlibUi.BirthdateOpenOrigin.PRIVACY_SETTINGS);
          break;
        }
        case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR: {
          if (readDatePrivacySetting != null) {
            readDatePrivacySetting.showReadDate = !adapter.toggleView(v);
            TdApi.ReadDatePrivacySettings newPrivacySettings = new TdApi.ReadDatePrivacySettings(readDatePrivacySetting.showReadDate);
            tdlib.send(new TdApi.SetReadDatePrivacySettings(newPrivacySettings), tdlib.typedOkHandler(() -> {
              tdlib.listeners().updateReadDatePrivacySettings(newPrivacySettings);
            }));
          }
          break;
        }
        case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR: {
          boolean plusPremium = adapter.toggleView(v);
          PrivacySettings rules = currentRules();
          changedPrivacyRules = PrivacySettings.valueOf(rules.togglePlusPremium(plusPremium));
          updateRulesState(changedPrivacyRules);
          break;
        }
        case TdApi.UserPrivacySettingAutosaveGifts.CONSTRUCTOR: {
          boolean plusOrMinusBots = adapter.toggleView(v);
          PrivacySettings rules = currentRules();
          changedPrivacyRules = PrivacySettings.valueOf(rules.togglePlusOrMinusBots(plusOrMinusBots));
          updateRulesState(changedPrivacyRules);
          break;
        }
        default: {
          throw new IllegalStateException();
        }
      }
    }
  }

  @Override
  public void onActivityResult (int requestCode, int resultCode, Intent data) {
    getAvatarPickerManager().handleActivityResult(requestCode, resultCode, data, AvatarPickerManager.MODE_PROFILE_PUBLIC, null, null);
  }

  private AvatarPickerManager avatarPickerManager;

  private AvatarPickerManager getAvatarPickerManager () {
    if (avatarPickerManager == null) {
      avatarPickerManager = new AvatarPickerManager(this);
    }
    return avatarPickerManager;
  }
}
