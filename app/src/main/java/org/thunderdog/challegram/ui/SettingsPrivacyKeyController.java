package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.component.dialogs.SearchManager;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TGUser;
import org.thunderdog.challegram.telegram.PrivacySettings;
import org.thunderdog.challegram.telegram.PrivacySettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.util.CustomTypefaceSpan;
import org.thunderdog.challegram.util.UserPickerMultiDelegate;
import org.thunderdog.challegram.v.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.collection.IntList;
import me.vkryl.core.collection.LongList;
import me.vkryl.td.ChatId;

/**
 * Date: 17/11/2016
 * Author: default
 */

public class SettingsPrivacyKeyController extends RecyclerViewController<TdApi.UserPrivacySetting> implements View.OnClickListener, UserPickerMultiDelegate, PrivacySettingsListener {

  public SettingsPrivacyKeyController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_privacyKey;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(getName(getArgumentsStrict(), false, false));
  }

  public static int getIcon (TdApi.UserPrivacySetting privacySetting) {
    switch (privacySetting.getConstructor()) {
      case TdApi.UserPrivacySettingShowPhoneNumber.CONSTRUCTOR:
        return R.drawable.baseline_call_24;
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
    }
    return 0;
  }

  public static int getName (TdApi.UserPrivacySetting privacyKey, boolean isException, boolean isMultiChatException) {
    switch (privacyKey.getConstructor()) {
      case TdApi.UserPrivacySettingShowPhoneNumber.CONSTRUCTOR:
        return isException ? R.string.EditPrivacyPhoneNumber : R.string.PhoneNumber;
      case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
        return R.string.FindingByPhoneNumber;
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
    }
    throw new IllegalStateException("privacyKey == " + privacyKey);
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putInt(keyPrefix + "setting", getArgumentsStrict().getConstructor());
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    @TdApi.UserPrivacySetting.Constructors int constructor = in.getInt(keyPrefix + "setting", 0);
    TdApi.UserPrivacySetting setting = null;
    switch (constructor) {
      case TdApi.UserPrivacySettingAllowCalls.CONSTRUCTOR:
        setting = new TdApi.UserPrivacySettingAllowCalls();
        break;
      case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR:
        setting = new TdApi.UserPrivacySettingAllowChatInvites();
        break;
      case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
        setting = new TdApi.UserPrivacySettingAllowFindingByPhoneNumber();
        break;
      case TdApi.UserPrivacySettingAllowPeerToPeerCalls.CONSTRUCTOR:
        setting = new TdApi.UserPrivacySettingAllowPeerToPeerCalls();
        break;
      case TdApi.UserPrivacySettingShowLinkInForwardedMessages.CONSTRUCTOR:
        setting = new TdApi.UserPrivacySettingShowLinkInForwardedMessages();
        break;
      case TdApi.UserPrivacySettingShowPhoneNumber.CONSTRUCTOR:
        setting = new TdApi.UserPrivacySettingShowPhoneNumber();
        break;
      case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR:
        setting = new TdApi.UserPrivacySettingShowProfilePhoto();
        break;
      case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR:
        setting = new TdApi.UserPrivacySettingShowStatus();
        break;
    }
    if (setting != null) {
      setArguments(setting);
      return true;
    }
    return false;
  }

  private SettingsAdapter adapter;
  private PrivacySettings privacyRules, changedPrivacyRules;

  private PrivacySettings currentRules () {
    return changedPrivacyRules != null ? changedPrivacyRules : privacyRules;
  }

  private void setPrivacyRules (TdApi.UserPrivacySettingRules rules) {
    if (this.privacyRules == null) {
      this.privacyRules = PrivacySettings.valueOf(rules);
      buildCells();
      executeScheduledAnimation();
    } else {
      int prevMode = currentRules().getMode();
      this.privacyRules = PrivacySettings.valueOf(rules);
      if (this.changedPrivacyRules == null) {
        int newMode = currentRules().getMode();
        int modeId;
        switch (newMode) {
          case PrivacySettings.MODE_CONTACTS:
            modeId = R.id.btn_contacts;
            break;
          case PrivacySettings.MODE_EVERYBODY:
            modeId = R.id.btn_everybody;
            break;
          case PrivacySettings.MODE_NOBODY:
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

  private boolean needNobodyOption () {
    switch (getArgumentsStrict().getConstructor()) {
      case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR:
      case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
      // case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR:
        return false;
    }
    return true;
  }

  private boolean needExceptions () {
    switch (getArgumentsStrict().getConstructor()) {
      case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
        return false;
    }
    return true;
  }

  private void buildCells () {
    if (privacyRules == null) {
      return;
    }

    final ListItem headerItem;
    final ListItem hintItem;

    final int rulesType = privacyRules.getMode();

    switch (getArgumentsStrict().getConstructor()) {
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
      case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR: {
        headerItem = new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.WhoCanFindByPhone);
        hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.btn_description, 0, rulesType == PrivacySettings.MODE_EVERYBODY ? R.string.WhoCanFindByPhoneInfoEveryone : R.string.WhoCanFindByPhoneInfoContacts);
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
          b.setSpan(new CustomTypefaceSpan(Fonts.getRobotoMedium(), R.id.theme_color_background_textLight), 0, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          hintItem = new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, b, false);
        }
        break;
      }
      default: {
        throw new IllegalStateException("privacyKey == " + getArgumentsStrict());
      }
    }

    ArrayList<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_EMPTY_OFFSET_SMALL));
    items.add(headerItem);

    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_everybody, 0, R.string.Everybody, null, R.id.btn_privacyRadio, rulesType == PrivacySettings.MODE_EVERYBODY));
    items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
    items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_contacts, 0, R.string.MyContacts, null, R.id.btn_privacyRadio, rulesType == PrivacySettings.MODE_CONTACTS));
    if (needNobodyOption() || rulesType == PrivacySettings.MODE_NOBODY) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      items.add(new ListItem(ListItem.TYPE_RADIO_OPTION, R.id.btn_nobody, 0, R.string.Nobody, null, R.id.btn_privacyRadio, rulesType == PrivacySettings.MODE_NOBODY));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    items.add(hintItem);
    if (needExceptions()) {
      items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, R.string.AddExceptions));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));

      boolean needNever = privacyRules.needNeverAllow();
      if (needNever) {
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_neverAllow, 0, getArgumentsStrict().getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR ? R.string.NeverShareWith : R.string.NeverAllow));
      }
      if (privacyRules.needAlwaysAllow()) {
        if (needNever) {
          items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
        }
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_alwaysAllow, 0, getArgumentsStrict().getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR ? R.string.AlwaysShareWith : R.string.AlwaysAllow));
      }
      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, R.string.CustomShareSettingsHelp));
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

  private void updateRulesState (PrivacySettings newPrivacySettings) {
    int newRules = newPrivacySettings.getMode();
    if (!needExceptions()) {
      switch (getArgumentsStrict().getConstructor()) {
        case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:  {
          int i = adapter.indexOfViewById(R.id.btn_description);
          if (i != -1 && adapter.getItems().get(i).setStringIfChanged(newRules == PrivacySettings.MODE_EVERYBODY ? R.string.WhoCanFindByPhoneInfoEveryone : R.string.WhoCanFindByPhoneInfoContacts)) {
            adapter.notifyItemChanged(i);
          }
          break;
        }
      }
      return;
    }

    boolean prevHadNever = adapter.indexOfViewById(R.id.btn_neverAllow) != -1;
    boolean prevHadAlways = adapter.indexOfViewById(R.id.btn_alwaysAllow) != -1;
    boolean prevHadSeparator = prevHadAlways && prevHadNever;

    boolean nowHasNever = newPrivacySettings.needNeverAllow();
    boolean nowHasAlways = newPrivacySettings.needAlwaysAllow();
    boolean nowHasSeparator = nowHasAlways && nowHasNever;

    List<ListItem> items = adapter.getItems();
    int index = adapter.indexOfViewByType(ListItem.TYPE_VALUED_SETTING_COMPACT);

    final int alwaysString = getArgumentsStrict().getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR ? R.string.AlwaysShareWith : R.string.AlwaysAllow;
    final int neverString = getArgumentsStrict().getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR ? R.string.NeverShareWith : R.string.NeverAllow;

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

  @Override
  public boolean needAsynchronousAnimation () {
    return privacyRules == null;
  }

  @Override
  public void onPrivacySettingRulesChanged (TdApi.UserPrivacySetting setting, TdApi.UserPrivacySettingRules rules) {
    tdlib.ui().post(() -> {
      if (!isDestroyed() && getArgumentsStrict().getConstructor() == setting.getConstructor()) {
        setPrivacyRules(rules);
      }
    });
  }

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        switch (item.getId()) {
          case R.id.btn_alwaysAllow: {
            int count = currentRules().getPlusTotalCount(tdlib);
            view.setData(count > 0 ? Lang.plural(R.string.xUsers, count) : Lang.getString(R.string.PrivacyAddUsers));
            break;
          }
          case R.id.btn_neverAllow: {
            int count = currentRules().getMinusTotalCount(tdlib);
            view.setData(count > 0 ? Lang.plural(R.string.xUsers, count) : Lang.getString(R.string.PrivacyAddUsers));
            break;
          }
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
    tdlib.client().send(new TdApi.GetUserPrivacySettingRules(getArgumentsStrict()), result -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        switch (result.getConstructor()) {
          case TdApi.UserPrivacySettingRules.CONSTRUCTOR: {
            setPrivacyRules((TdApi.UserPrivacySettingRules) result);
            break;
          }
          case TdApi.Error.CONSTRUCTOR: {
            UI.showError(result);
            break;
          }
        }
      }
    }));
    tdlib.listeners().subscribeToPrivacyUpdates(this);
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
    TdApi.UserPrivacySettingRules newRules = changedPrivacyRules.toRules();

    tdlib.client().send(new TdApi.SetUserPrivacySettingRules(getArgumentsStrict(), newRules), tdlib.okHandler());
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromPrivacyUpdates(this);
  }

  private int userPickMode;

  @Override
  public long[] getAlreadySelectedChatIds () {
    switch (userPickMode) {
      case R.id.btn_alwaysAllow: {
        return currentRules().getAllPlusIds();
      }
      case R.id.btn_neverAllow: {
        return currentRules().getAllMinusIds();
      }
    }
    return null;
  }

  private void setAllowUsers (int[] userIds, long[] chatIds) {
    changedPrivacyRules = PrivacySettings.valueOf(currentRules().allowExceptions(userIds, chatIds));
    adapter.updateValuedSettingById(R.id.btn_alwaysAllow);
    adapter.updateValuedSettingById(R.id.btn_neverAllow);
  }

  private void setNeverAllow (int[] userIds, long[] chatIds) {
    changedPrivacyRules = PrivacySettings.valueOf(currentRules().disallowExceptions(userIds, chatIds));
    adapter.updateValuedSettingById(R.id.btn_neverAllow);
    adapter.updateValuedSettingById(R.id.btn_alwaysAllow);
  }

  @Override
  public int provideMultiUserPickerHint () {
    switch (userPickMode) {
      case R.id.btn_neverAllow: {
        return getArgumentsStrict().getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR ? R.string.NeverShareWith : R.string.NeverAllow;
      }
      case R.id.btn_alwaysAllow: {
        return getArgumentsStrict().getConstructor() == TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR ? R.string.AlwaysShareWith : R.string.AlwaysAllow;
      }
    }
    return R.string.AlwaysAllow;
  }

  @Override
  public void onAlreadyPickedChatsChanged (List<TGUser> users) {
    IntList userIds = new IntList(users.size());
    LongList chatIds = new LongList(users.size());
    for (TGUser user : users) {
      long chatId = user.getChatId();
      if (ChatId.isPrivate(chatId)) {
        userIds.append(ChatId.toUserId(chatId));
      } else {
        chatIds.append(chatId);
      }
    }
    switch (userPickMode) {
      case R.id.btn_alwaysAllow: {
        setAllowUsers(userIds.get(), chatIds.get());
        break;
      }
      case R.id.btn_neverAllow: {
        setNeverAllow(userIds.get(), chatIds.get());
        break;
      }
    }
  }

  @Override
  public void onClick (View v) {
    switch (v.getId()) {
      case R.id.btn_alwaysAllow:
      case R.id.btn_neverAllow: {
        userPickMode = v.getId();
        ContactsController c = new ContactsController(context, tdlib);
        c.setArguments(new ContactsController.Args(this).useGlobalSearch(SearchManager.FLAG_NEED_TOP_CHATS | SearchManager.FLAG_NEED_GLOBAL_SEARCH | SearchManager.FLAG_NO_BOTS | SearchManager.FLAG_NO_CHANNELS | SearchManager.FLAG_NO_SELF));
        navigateTo(c);
        break;
      }

      case R.id.btn_everybody:
      case R.id.btn_contacts:
      case R.id.btn_nobody: {
        ListItem item = (ListItem) v.getTag();
        if (adapter.processToggle(v)) {
          final int desiredMode;
          switch (adapter.getCheckIntResults().get(item.getCheckId())) {
            case R.id.btn_everybody:
              desiredMode = PrivacySettings.MODE_EVERYBODY;
              break;
            case R.id.btn_contacts:
              desiredMode = PrivacySettings.MODE_CONTACTS;
              break;
            case R.id.btn_nobody:
              desiredMode = PrivacySettings.MODE_NOBODY;
              break;
            default:
              return;
          }
          int prevMode = currentRules().getMode();
          changedPrivacyRules = PrivacySettings.valueOf(currentRules().toggleGlobal(desiredMode));
          updateRulesState(changedPrivacyRules);
        }
        break;
      }
    }
  }
}
