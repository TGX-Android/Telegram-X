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
 */
package org.thunderdog.challegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.collection.SparseArrayCompat;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.base.SettingView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.data.TD;
import org.thunderdog.challegram.data.TGFoundChat;
import org.thunderdog.challegram.telegram.ChatListener;
import org.thunderdog.challegram.telegram.PrivacySettings;
import org.thunderdog.challegram.telegram.PrivacySettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibCache;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.v.CustomRecyclerView;
import org.thunderdog.challegram.widget.BetterChatView;

import java.util.ArrayList;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.collection.LongList;
import me.vkryl.td.Td;

public class PrivacyExceptionController extends RecyclerViewController<PrivacyExceptionController.Args> implements PrivacySettingsListener, View.OnClickListener, TdlibCache.UserDataChangeListener, ChatListener {
  public static class Args {
    private final long chatId;

    public Args (long chatId) {
      this.chatId = chatId;
    }
  }

  public PrivacyExceptionController (Context context, Tdlib tdlib) {
    super(context, tdlib);
  }

  @Override
  public int getId () {
    return R.id.controller_privacyException;
  }

  @Override
  public boolean saveInstanceState (Bundle outState, String keyPrefix) {
    super.saveInstanceState(outState, keyPrefix);
    outState.putLong(keyPrefix + "chat", getArgumentsStrict().chatId);
    return true;
  }

  @Override
  public boolean restoreInstanceState (Bundle in, String keyPrefix) {
    super.restoreInstanceState(in, keyPrefix);
    long chatId = in.getLong(keyPrefix + "chat", 0);
    TdApi.Chat chat = tdlib.chatSync(chatId);
    if (chat != null) {
      setArguments(new Args(chatId));
      return true;
    }
    return false;
  }

  private SparseArrayCompat<PrivacySettings> privacyRules;
  private SparseArrayCompat<PrivacySettings> pendingRequests;

  @Override
  public long getChatId () {
    return getArgumentsStrict().chatId;
  }

  @Override
  public CharSequence getName () {
    return Lang.getString(R.string.EditPrivacy);
  }

  private SettingsAdapter adapter;
  private int remainingResponses;

  @Override
  public boolean needAsynchronousAnimation () {
    return remainingResponses > 0 || loadingGroups;
  }

  private void getPrivacySetting (TdApi.UserPrivacySetting setting) {
    remainingResponses++;
    tdlib.client().send(new TdApi.GetUserPrivacySettingRules(setting), result -> tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        if (result.getConstructor() == TdApi.UserPrivacySettingRules.CONSTRUCTOR) {
          privacyRules.put(setting.getConstructor(), PrivacySettings.valueOf((TdApi.UserPrivacySettingRules) result));
          if (adapter != null)
            adapter.updateValuedSettingByLongId(setting.getConstructor());
        }
        if (--remainingResponses == 0 && !loadingGroups)
          executeScheduledAnimation();
      }
    }));
  }

  private ListItem contactItem, visibilityTitle, actionTitle, p2pHint;

  private static final int TYPE_VISIBILITY = 0;
  private static final int TYPE_ACTIONS = 1;
  private static final int TYPE_P2P = 2;
  private static final int TYPE_INFO = 3;

  private CharSequence getTitle (int type) {
    final long chatId = getChatId();
    final boolean isBot = tdlib.isBotChat(chatId);
    final boolean isMultiChat = tdlib.isMultiChat(chatId);
    final String chatTitle = isMultiChat ? tdlib.chatTitle(chatId) : tdlib.cache().userFirstName(tdlib.chatUserId(chatId));
    int res;
    switch (type) {
      case TYPE_VISIBILITY:
        res = isMultiChat ? R.string.PrivacyVisibilityGroup : isBot ? R.string.PrivacyVisibilityBot : R.string.PrivacyVisibilityUser;
        break;
      case TYPE_ACTIONS:
        res = isMultiChat ? R.string.PrivacyActionGroup : isBot ? R.string.PrivacyActionBot : R.string.PrivacyActionUser;
        break;
      case TYPE_P2P:
        res = R.string.EditPrivacyCallP2PInfo;
        break;
      case TYPE_INFO:
        res = R.string.EditPrivacyGroupInfo;
        break;
      default:
        throw new UnsupportedOperationException();
    }
    return Lang.getStringBold(res, chatTitle);
  }

  private boolean loadingGroups;
  private LongList groupsInCommon;

  @Override
  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {
    final long chatId = getChatId();
    final long userId = tdlib.chatUserId(chatId);
    final boolean isBot = tdlib.isBotChat(chatId);
    final boolean isMultiChat = tdlib.isMultiChat(chatId);
    final boolean suggestAddContact = userId != 0 && !tdlib.isSelfUserId(userId) && !isBot;

    if (!isMultiChat && !tdlib.isSelfUserId(userId)) {
      groupsInCommon = new LongList(0);
      loadingGroups = true;
      tdlib.client().send(new TdApi.GetGroupsInCommon(userId, 0, 100), new Client.ResultHandler() {
        @Override
        public void onResult (TdApi.Object result) {
          long[] chatIds = result.getConstructor() == TdApi.Chats.CONSTRUCTOR ? ((TdApi.Chats) result).chatIds : null;
          if (chatIds != null && chatIds.length > 0) {
            groupsInCommon.appendAll(chatIds);
            if (!isDestroyed()) {
              tdlib.ui().post(() -> {
                if (!isDestroyed()) {
                  adapter.updateAllValuedSettings(item -> item.getId() == R.id.btn_privacyRule);
                }
              });
              tdlib.client().send(new TdApi.GetGroupsInCommon(userId, chatIds[chatIds.length - 1], 100), this);
            }
          } else {
            tdlib.ui().post(() -> {
              if (!isDestroyed()) {
                loadingGroups = false;
                if (remainingResponses == 0)
                  executeScheduledAnimation();
              }
            });
          }
        }
      });
    }

    privacyRules = new SparseArrayCompat<>();
    adapter = new SettingsAdapter(this) {
      @Override
      protected void setValuedSetting (ListItem item, SettingView view, boolean isUpdate) {
        final int itemId = item.getId();
        if (itemId == R.id.btn_privacyRule) {
          TdApi.UserPrivacySetting setting = (TdApi.UserPrivacySetting) item.getData();
          PrivacySettings applyingPrivacy = pendingRequests != null ? pendingRequests.get(setting.getConstructor()) : null;
          PrivacySettings privacy = applyingPrivacy != null ? applyingPrivacy : privacyRules.get(setting.getConstructor());
          if (privacy == null) {
            view.setEnabledAnimated(false, isUpdate);
            view.setData(R.string.LoadingInformation);
            view.setDataColorId(0);
            view.getToggler().setRadioEnabled(true, isUpdate);
            return;
          }
          boolean isContact = !isMultiChat && tdlib.cache().userContact(userId);
          TdApi.UserPrivacySettingRule matchingRule = isMultiChat ? privacy.firstMatchingRule(chatId, false) : privacy.firstMatchingRule(userId, isContact, groupsInCommon != null ? groupsInCommon.get() : null);
          view.setEnabledAnimated(true, isUpdate);
          boolean isActive = PrivacySettings.isAllow(matchingRule);
          view.getToggler().setRadioEnabled(isActive, isUpdate);
          if (PrivacySettings.isGeneral(matchingRule, !isMultiChat)) {
            view.setDataColorId(0);
            TdApi.UserPrivacySettingRule ancestorRule = privacy.findTopRule(isContact);
            if (PrivacySettings.isAllow(ancestorRule) == PrivacySettings.isAllow(matchingRule)) {
              matchingRule = ancestorRule;
            }
            long[] chatIds = null;
            if (matchingRule != null) {
              switch (matchingRule.getConstructor()) {
                case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
                  chatIds = ArrayUtils.intersect(((TdApi.UserPrivacySettingRuleAllowChatMembers) matchingRule).chatIds, groupsInCommon != null ? groupsInCommon.get() : null);
                  break;
                case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
                  chatIds = ArrayUtils.intersect(((TdApi.UserPrivacySettingRuleRestrictChatMembers) matchingRule).chatIds, groupsInCommon != null ? groupsInCommon.get() : null);
                  break;
              }
            }
            if (chatIds != null && chatIds.length > 0) {
              if (chatIds.length == 1) {
                view.setData(Lang.getStringBold(R.string.PrivacyDefaultChat, tdlib.chatTitle(chatIds[0])));
              } else {
                view.setData(Lang.pluralBold(R.string.PrivacyDefaultXChats, chatIds.length));
              }
            } else if (isMultiChat && !PrivacySettings.isAllow(matchingRule) && PrivacySettings.isAllow(privacy.firstMatchingRule(chatId, true))) {
              switch (setting.getConstructor()) {
                case TdApi.UserPrivacySettingShowPhoneNumber.CONSTRUCTOR:
                  view.setData(R.string.PrivacyShowNumberExceptionContacts);
                  break;
                case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR:
                  view.setData(R.string.PrivacyPhotoExceptionContacts);
                  break;
                case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR:
                  view.setData(R.string.PrivacyLastSeenExceptionContacts);
                  break;
                case TdApi.UserPrivacySettingShowLinkInForwardedMessages.CONSTRUCTOR:
                  view.setData(R.string.PrivacyForwardLinkExceptionContacts);
                  break;
                case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR:
                  view.setData(R.string.PrivacyAddToGroupsExceptionContacts);
                  break;
                case TdApi.UserPrivacySettingAllowCalls.CONSTRUCTOR:
                  view.setData(R.string.PrivacyCallsExceptionContacts);
                  break;
                case TdApi.UserPrivacySettingAllowPeerToPeerCalls.CONSTRUCTOR:
                  view.setData(R.string.PrivacyP2PExceptionContacts);
                  break;
                case TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages.CONSTRUCTOR:
                  view.setData(R.string.PrivacyVoiceVideoExceptionContacts);
                  break;
                case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
                  throw new IllegalStateException();
                default:
                  throw new UnsupportedOperationException();
              }
            } else {
              view.setData(matchingRule != null && (matchingRule.getConstructor() == TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR || matchingRule.getConstructor() == TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR) ? R.string.PrivacyDefaultContacts : R.string.PrivacyDefault);
            }
          } else {
            view.setDataColorId(ColorId.textNeutral);
            switch (setting.getConstructor()) {
              case TdApi.UserPrivacySettingShowPhoneNumber.CONSTRUCTOR:
                view.setData(isActive ? R.string.PrivacyShowNumberExceptionOn : R.string.PrivacyShowNumberExceptionOff);
                break;
              case TdApi.UserPrivacySettingShowProfilePhoto.CONSTRUCTOR:
                view.setData(isActive ? R.string.PrivacyPhotoExceptionOn : R.string.PrivacyPhotoExceptionOff);
                break;
              case TdApi.UserPrivacySettingShowStatus.CONSTRUCTOR:
                view.setData(isActive ? R.string.PrivacyLastSeenExceptionOn : R.string.PrivacyLastSeenExceptionOff);
                break;
              case TdApi.UserPrivacySettingShowLinkInForwardedMessages.CONSTRUCTOR:
                view.setData(isActive ? R.string.PrivacyForwardLinkExceptionOn : R.string.PrivacyForwardLinkExceptionOff);
                break;
              case TdApi.UserPrivacySettingAllowChatInvites.CONSTRUCTOR:
                view.setData(isActive ? R.string.PrivacyAddToGroupsExceptionOn : R.string.PrivacyAddToGroupsExceptionOff);
                break;
              case TdApi.UserPrivacySettingAllowCalls.CONSTRUCTOR:
                view.setData(isActive ? R.string.PrivacyCallsExceptionOn : R.string.PrivacyCallsExceptionOff);
                break;
              case TdApi.UserPrivacySettingAllowPeerToPeerCalls.CONSTRUCTOR:
                view.setData(isActive ? R.string.PrivacyP2PExceptionOn : R.string.PrivacyP2PExceptionOff);
                break;
              case TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages.CONSTRUCTOR:
                view.setData(isActive ? R.string.PrivacyVoiceVideoExceptionOn : R.string.PrivacyVoiceVideoExceptionOff);
                break;
              case TdApi.UserPrivacySettingAllowFindingByPhoneNumber.CONSTRUCTOR:
                throw new IllegalStateException();
              default:
                throw new UnsupportedOperationException();
            }
          }
        } else if (itemId == R.id.btn_newContact) {
          view.setName(item.getString());
          view.setTextColorId(item.getTextColorId(ColorId.text));
          view.setIcon(item.getIconResource());
          if (!isMultiChat) {
            view.setIconColorId(tdlib.cache().userContact(userId) ? ColorId.iconNegative : 0);
          }
        }
      }

      @Override
      protected void setChatData (ListItem item, int position, BetterChatView chatView) {
        chatView.setChat((TGFoundChat) item.getData());
        chatView.setEnabled(false);
      }
    };

    List<ListItem> items = new ArrayList<>();

    items.add(new ListItem(ListItem.TYPE_CHAT_BETTER).setData(new TGFoundChat(tdlib, null, chatId, false).setNoUnread()));
    if (suggestAddContact) {
      items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
      boolean isContact = tdlib.cache().userContact(userId);
      items.add(contactItem = new ListItem(ListItem.TYPE_SETTING, R.id.btn_newContact, isContact ? R.drawable.baseline_delete_24 : R.drawable.baseline_person_add_24, isContact ? R.string.DeleteContact : R.string.AddContact).setTextColorId(isContact ? ColorId.textNegative : ColorId.NONE).setBoolValue(isContact));
    }
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    if (!tdlib.isSelfUserId(userId)) {
      items.add(visibilityTitle = new ListItem(ListItem.TYPE_HEADER_MULTILINE, R.id.text_title, 0, getTitle(TYPE_VISIBILITY), false));
      items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
      TdApi.UserPrivacySetting[] privacySettings = isBot ?
        new TdApi.UserPrivacySetting[] {
          new TdApi.UserPrivacySettingShowStatus(),
          new TdApi.UserPrivacySettingShowProfilePhoto(),
          new TdApi.UserPrivacySettingShowLinkInForwardedMessages(),
        }
      :
      new TdApi.UserPrivacySetting[] {
        new TdApi.UserPrivacySettingShowStatus(),
        new TdApi.UserPrivacySettingShowProfilePhoto(),
        new TdApi.UserPrivacySettingShowPhoneNumber(),

        new TdApi.UserPrivacySettingShowLinkInForwardedMessages(),
        new TdApi.UserPrivacySettingAllowChatInvites(),
        new TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages(),
        new TdApi.UserPrivacySettingAllowCalls(),
        new TdApi.UserPrivacySettingAllowPeerToPeerCalls()
      };

      boolean first = true;
      for (TdApi.UserPrivacySetting setting : privacySettings) {
        if (first) {
          first = false;
        } else {
          if (setting.getConstructor() == TdApi.UserPrivacySettingShowLinkInForwardedMessages.CONSTRUCTOR) {
            items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
            items.add(actionTitle = new ListItem(ListItem.TYPE_HEADER_MULTILINE, R.id.text_title, 0, getTitle(TYPE_ACTIONS), false));
            items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
          } else {
            items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));
          }
        }
        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT_WITH_TOGGLER, R.id.btn_privacyRule, SettingsPrivacyKeyController.getIcon(setting), SettingsPrivacyKeyController.getName(setting, true, isMultiChat)).setData(setting).setLongId(setting.getConstructor()));
        getPrivacySetting(setting);
      }

      items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));
      if (!isBot) {
        items.add(p2pHint = new ListItem(ListItem.TYPE_DESCRIPTION, R.id.text_title, 0, getTitle(isMultiChat ? TYPE_INFO : TYPE_P2P), false));
      }
    }

    items.add(new ListItem(ListItem.TYPE_HEADER_MULTILINE, 0, 0, R.string.PrivacyOther));
    items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));
    items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_privacySettings, 0, R.string.EditPrivacyGlobal));
    items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));

    adapter.setItems(items, false);
    recyclerView.setAdapter(adapter);
    restorePersistentScrollPosition();

    if (userId != 0) {
      tdlib.cache().addUserDataListener(userId, this);
    } else {
      tdlib.listeners().subscribeToChatUpdates(chatId, this);
    }
    tdlib.listeners().subscribeToPrivacyUpdates(this);
  }

  @Override
  public void onClick (View v) {
    final int viewId = v.getId();
    if (viewId == R.id.btn_privacyRule) {
      TdApi.UserPrivacySetting setting = (TdApi.UserPrivacySetting) ((ListItem) v.getTag()).getData();
      if (Td.requiresPremiumSubscription(setting) && tdlib.ui().showPremiumAlert(this, v, TdlibUi.PremiumFeature.RESTRICT_VOICE_AND_VIDEO_MESSAGES)) {
        return;
      }
      PrivacySettings privacy = privacyRules.get(setting.getConstructor());
      if (privacy == null)
        return;
      boolean value = adapter.toggleView(v);
      long chatId = getChatId();
      boolean isMultiChat = tdlib.isMultiChat(chatId);
      long userId = tdlib.chatUserId(chatId);
      TdApi.UserPrivacySettingRules rules;
      if (isMultiChat) {
        rules = privacy.toggleChat(chatId, false, value);
      } else {
        rules = privacy.toggleUser(userId, tdlib.cache().userContact(userId), groupsInCommon != null ? groupsInCommon.get() : null, value);
      }
      if (pendingRequests == null)
        pendingRequests = new SparseArrayCompat<>();
      PrivacySettings newPrivacy = PrivacySettings.valueOf(rules);
      pendingRequests.put(setting.getConstructor(), newPrivacy);
      // privacyRules.put(setting.getConstructor(), rules);
      adapter.updateValuedSettingByLongId(setting.getConstructor());
      tdlib.client().send(new TdApi.SetUserPrivacySettingRules(setting, rules), result -> tdlib.ui().post(() -> {
        if (!isDestroyed()) {
          int i = pendingRequests.indexOfKey(setting.getConstructor());
          if (i >= 0 && pendingRequests.valueAt(i) == newPrivacy) {
            pendingRequests.removeAt(i);
            adapter.updateValuedSettingByLongId(setting.getConstructor());
          }
        }
      }));
    } else if (viewId == R.id.btn_privacySettings) {
      openGeneralSettings();
    } else if (viewId == R.id.btn_newContact) {
      long userId = tdlib.chatUserId(getChatId());
      if (userId != 0) {
        if (tdlib.cache().userContact(userId)) {
          tdlib.ui().deleteContact(this, userId);
        } else {
          tdlib.ui().addContact(this, tdlib.cache().userStrict(userId));
        }
      }
    }
  }

  @Override
  public void destroy () {
    super.destroy();
    tdlib.listeners().unsubscribeFromPrivacyUpdates(this);
    long chatId = getChatId();
    long userId = tdlib.chatUserId(chatId);
    if (userId != 0) {
      tdlib.cache().removeUserDataListener(userId, this);
    } else {
      tdlib.listeners().unsubscribeFromChatUpdates(chatId, this);
    }
  }

  @Override
  public void onPrivacySettingRulesChanged (TdApi.UserPrivacySetting setting, TdApi.UserPrivacySettingRules rules) {
    setPrivacySetting(setting, rules);
  }

  @Override
  public void onUserUpdated (TdApi.User user) {
    final boolean isContact = TD.isContact(user);
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        if (visibilityTitle != null)
          visibilityTitle.setString(getTitle(TYPE_VISIBILITY));
        if (actionTitle != null)
          actionTitle.setString(getTitle(TYPE_ACTIONS));
        if (p2pHint != null)
          p2pHint.setString(getTitle(TYPE_P2P));
        if (contactItem != null && isContact != contactItem.getBoolValue()) {
          contactItem.setBoolValue(isContact);
          contactItem.setString(isContact ? R.string.DeleteContact : R.string.AddContact);
          contactItem.setIconRes(isContact ? R.drawable.baseline_delete_24 : R.drawable.baseline_person_add_24);
          contactItem.setTextColorId(isContact ? ColorId.textNegative : ColorId.NONE);
          adapter.updateAllValuedSettings(item -> item.getId() == R.id.text_title || item.getId() == R.id.btn_newContact || item.getId() == R.id.text_title || item.getId() == R.id.btn_privacyRule);
        } else {
          adapter.updateAllValuedSettings(item -> item.getId() == R.id.text_title);
        }
      }
    });
  }

  @Override
  public void onChatTitleChanged (long chatId, String title) {
    tdlib.ui().post(() -> {
      if (visibilityTitle != null)
        visibilityTitle.setString(getTitle(TYPE_VISIBILITY));
      if (actionTitle != null)
        actionTitle.setString(getTitle(TYPE_ACTIONS));
      if (p2pHint != null)
        p2pHint.setString(getTitle(TYPE_P2P));
      adapter.updateAllValuedSettings(item -> item.getId() == R.id.text_title);
    });
  }

  private void setPrivacySetting (TdApi.UserPrivacySetting setting, TdApi.UserPrivacySettingRules rules) {
    tdlib.ui().post(() -> {
      if (!isDestroyed()) {
        this.privacyRules.put(setting.getConstructor(), PrivacySettings.valueOf(rules));
        if (adapter != null)
          adapter.updateValuedSettingByLongId(setting.getConstructor());
      }
    });
  }

  private void openGeneralSettings () {
    SettingsPrivacyController c = new SettingsPrivacyController(context, tdlib);
    c.setArguments(new SettingsPrivacyController.Args(true));
    navigateTo(c);
  }
}
