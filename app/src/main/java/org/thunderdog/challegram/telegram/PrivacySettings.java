/*
 * This file is a part of Telegram X
 * Copyright © 2014-2023 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.telegram;

import androidx.annotation.Nullable;

import org.drinkless.td.libcore.telegram.TdApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.collection.LongList;
import me.vkryl.td.ChatId;
import me.vkryl.td.Td;

public class PrivacySettings {
  public static final int MODE_NOBODY = 0;
  public static final int MODE_CONTACTS = 1;
  public static final int MODE_EVERYBODY = 2;

  private final List<TdApi.UserPrivacySettingRule> rules;
  private final int mode;
  private final long[] plusUserIds;
  private final long[] minusUserIds;
  private final long[] plusChatIds;
  private final long[] minusChatIds;

  public PrivacySettings (TdApi.UserPrivacySettingRules rules, int mode, long[] plusUserIds, long[] minusUserIds, long[] plusChatIds, long[] minusChatIds) {
    this.rules = Arrays.asList(rules.rules);
    this.mode = mode;
    this.plusUserIds = plusUserIds;
    this.minusUserIds = minusUserIds;
    this.plusChatIds = plusChatIds;
    this.minusChatIds = minusChatIds;
  }

  public boolean needNeverAllow () {
    if (mode != MODE_NOBODY)
      return true;
    /*for (TdApi.UserPrivacySettingRule rule : rules) {
      if (isGeneral(rule, false))
        break;
      if (rule.getConstructor() == TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR)
        return true;
    }*/
    return false;
  }

  public boolean needAlwaysAllow () {
    if (mode != PrivacySettings.MODE_EVERYBODY)
      return true;
    for (TdApi.UserPrivacySettingRule rule : rules) {
      if (isGeneral(rule, false))
        break;
      if (rule.getConstructor() == TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR)
        return true;
    }
    return false;
  }

  public long[] getAllPlusIds () {
    LongList list = new LongList((plusUserIds != null ? plusUserIds.length : 0) + (plusChatIds != null ? plusChatIds.length : 0));
    loop: for (TdApi.UserPrivacySettingRule rule : rules) {
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR: {
          for (long userId : ((TdApi.UserPrivacySettingRuleAllowUsers) rule).userIds) {
            list.append(ChatId.fromUserId(userId));
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR: {
          for (long chatId : ((TdApi.UserPrivacySettingRuleAllowChatMembers) rule).chatIds) {
            list.append(chatId);
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR: {
          break loop;
        }
      }
    }
    return list.get();
  }

  public long[] getAllMinusIds () {
    LongList list = new LongList((minusUserIds != null ? minusUserIds.length : 0) + (minusChatIds != null ? minusChatIds.length : 0));
    loop: for (TdApi.UserPrivacySettingRule rule : rules) {
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR: {
          for (long userId : ((TdApi.UserPrivacySettingRuleRestrictUsers) rule).userIds) {
            list.append(ChatId.fromUserId(userId));
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR: {
          for (long chatId : ((TdApi.UserPrivacySettingRuleRestrictChatMembers) rule).chatIds) {
            list.append(chatId);
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
        default: {
          if (isGeneral(rule, false)) {
            break loop;
          }
          break;
        }
      }
    }
    return list.get();
  }

  @Override
  public boolean equals (@Nullable Object obj) {
    if (obj == this)
      return true;
    if (!(obj instanceof PrivacySettings))
      return false;
    PrivacySettings a = (PrivacySettings) obj;
    if (a.rules.size() != this.rules.size())
      return false;
    for (int i = 0; i < a.rules.size(); i++) {
      TdApi.UserPrivacySettingRule r1 = a.rules.get(i);
      TdApi.UserPrivacySettingRule r2 = this.rules.get(i);
      if (!Td.equalsTo(r1, r2))
        return false;
    }
    return true;
  }

  public boolean isAllowed (boolean forContacts) {
    for (TdApi.UserPrivacySettingRule rule : rules) {
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
          if (forContacts)
            return true;
          continue;
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
          if (forContacts)
            return false;
          continue;
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
          return true;
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
          return false;
      }
    }
    return false;
  }

  public TdApi.UserPrivacySettingRules toggleGlobal (int mode) {
    switch (mode) {
      case MODE_NOBODY:
        return toggleGlobal(false, false);
      case MODE_CONTACTS:
        return toggleGlobal(true, false);
      case MODE_EVERYBODY:
        return toggleGlobal(true, true);
    }
    throw new UnsupportedOperationException("mode == " + mode);
  }

  public TdApi.UserPrivacySettingRules toggleGlobal (boolean allowContacts, boolean allowOther) {
    List<TdApi.UserPrivacySettingRule> newRules = new ArrayList<>(rules);
    for (int i = 0; i < newRules.size(); i++) {
      TdApi.UserPrivacySettingRule rule = newRules.get(i);
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
          newRules.remove(i);
          i--;
          break;
      }
    }
    if (allowContacts && allowOther) {
      newRules.add(new TdApi.UserPrivacySettingRuleAllowAll());
    } else if (allowContacts) {
      newRules.add(new TdApi.UserPrivacySettingRuleAllowContacts());
      newRules.add(new TdApi.UserPrivacySettingRuleRestrictAll());
    } else if (allowOther) {
      newRules.add(new TdApi.UserPrivacySettingRuleRestrictContacts());
      newRules.add(new TdApi.UserPrivacySettingRuleAllowAll());
    } else if (!newRules.isEmpty()) {
      newRules.add(new TdApi.UserPrivacySettingRuleRestrictAll());
    }
    return new TdApi.UserPrivacySettingRules(newRules.toArray(new TdApi.UserPrivacySettingRule[0]));
  }

  public TdApi.UserPrivacySettingRules toggleChat (long chatId, boolean needContacts, boolean value) {
    List<TdApi.UserPrivacySettingRule> newRules = new ArrayList<>(rules);
    TdApi.UserPrivacySettingRule matchingRule;
    while (isAllow(matchingRule = firstMatchingRule(newRules, chatId, needContacts)) != value) {
      if (isGeneral(matchingRule, false)) {
        int index = 0;
        if (!newRules.isEmpty()) {
          while (index != -1 && index < newRules.size()) {
            matchingRule = newRules.get(index);
            switch (matchingRule.getConstructor()) {
              case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR: {
                TdApi.UserPrivacySettingRuleAllowChatMembers allowMembers = (TdApi.UserPrivacySettingRuleAllowChatMembers) matchingRule;
                if (value) {
                  // Perfect match, just altering existing rule
                  allowMembers.chatIds = ArrayUtils.addElement(allowMembers.chatIds, chatId);
                  index = -1;
                } else {
                  // Placing ruleRestrictChatMembers after ruleAllowChatMembers otherwise
                  index++;
                  continue;
                }
                break;
              }
              case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR: {
                TdApi.UserPrivacySettingRuleRestrictChatMembers restrictMembers = (TdApi.UserPrivacySettingRuleRestrictChatMembers) matchingRule;
                if (value) {
                  // Placing ruleAllowChatMembers before any ruleRestrictChatMembers
                  // index++;
                  // continue;
                } else {
                  // Perfect match, just altering existing rule
                  restrictMembers.chatIds = ArrayUtils.addElement(restrictMembers.chatIds, chatId);
                  index = -1;
                }
                break;
              }
              case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
              case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
              case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
              case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: {
                // Placing ruleAllowChatMembers & ruleRestrictChatMembers after any ruleAllowUsers & ruleRestrictUsers
                index++;
                continue;
              }
            }
            break;
          }
        }
        if (index != -1) {
          TdApi.UserPrivacySettingRule newRule = value ? new TdApi.UserPrivacySettingRuleAllowChatMembers(new long[] {chatId}) : new TdApi.UserPrivacySettingRuleRestrictChatMembers(new long[] {chatId});
          if (index < newRules.size())
            newRules.add(index, newRule);
          else
            newRules.add(newRule);
        }
      } else {
        long[] chatIds;
        switch (matchingRule.getConstructor()) {
          case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
            chatIds = ((TdApi.UserPrivacySettingRuleRestrictChatMembers) matchingRule).chatIds;
            break;
          case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
            chatIds = ((TdApi.UserPrivacySettingRuleAllowChatMembers) matchingRule).chatIds;
            break;
          default:
            throw new UnsupportedOperationException();
        }
        int i = ArrayUtils.indexOf(chatIds, chatId);
        if (i == -1)
          throw new UnsupportedOperationException();
        if (chatIds.length > 1) {
          long[] newChatIds = ArrayUtils.removeElement(chatIds, i);
          switch (matchingRule.getConstructor()) {
            case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
              ((TdApi.UserPrivacySettingRuleRestrictChatMembers) matchingRule).chatIds = newChatIds;
              break;
            case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
              ((TdApi.UserPrivacySettingRuleAllowChatMembers) matchingRule).chatIds = newChatIds;
              break;
            default:
              throw new UnsupportedOperationException();
          }
        } else {
          newRules.remove(matchingRule);
        }
      }
    }
    return new TdApi.UserPrivacySettingRules(newRules.toArray(new TdApi.UserPrivacySettingRule[0]));
  }

  public TdApi.UserPrivacySettingRules toggleUser (long userId, boolean isContact, long[] groupsInCommon, boolean value) {
    List<TdApi.UserPrivacySettingRule> newRules = new ArrayList<>(rules);
    TdApi.UserPrivacySettingRule matchingRule;
    while (isAllow(matchingRule = firstMatchingRule(newRules, userId, isContact, groupsInCommon)) != value) {
      if (isGeneral(matchingRule, true)) {
        int index = 0;
        while (index != -1 && index < newRules.size() && !isGeneral(matchingRule = newRules.get(index), true)) {
          switch (matchingRule.getConstructor()) {
            case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR: {
              TdApi.UserPrivacySettingRuleAllowUsers allowUsers = (TdApi.UserPrivacySettingRuleAllowUsers) matchingRule;
              if (value) {
                // Perfect match, just altering existing rule
                allowUsers.userIds = ArrayUtils.addElement(allowUsers.userIds, userId);
                index = -1;
              }
              // Placing ruleRestrictUsers before any ruleAllowUsers otherwise
              break;
            }
            case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR: {
              TdApi.UserPrivacySettingRuleRestrictUsers restrictUsers = (TdApi.UserPrivacySettingRuleRestrictUsers) matchingRule;
              if (value) {
                // Placing ruleAllowUsers after any ruleRestrictUsers
                index++;
                continue;
              } else {
                // Perfect match, just altering existing rule
                restrictUsers.userIds = ArrayUtils.addElement(restrictUsers.userIds, userId);
                index = -1;
              }
              break;
            }
            default:
              throw new UnsupportedOperationException(matchingRule.toString());
          }
          break;
        }
        if (index != -1) {
          TdApi.UserPrivacySettingRule rule = value ? new TdApi.UserPrivacySettingRuleAllowUsers(new long[] {userId}) : new TdApi.UserPrivacySettingRuleRestrictUsers(new long[] {userId});
          if (index < newRules.size()) {
            newRules.add(index, rule);
          } else {
            newRules.add(rule);
          }
        }
      } else {
        long[] userIds;
        switch (matchingRule.getConstructor()) {
          case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
            userIds = ((TdApi.UserPrivacySettingRuleRestrictUsers) matchingRule).userIds;
            break;
          case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
            userIds = ((TdApi.UserPrivacySettingRuleAllowUsers) matchingRule).userIds;
            break;
          default:
            throw new UnsupportedOperationException();
        }
        int i = ArrayUtils.indexOf(userIds, userId);
        if (i == -1)
          throw new UnsupportedOperationException();
        if (userIds.length > 1) {
          long[] newUserIds = ArrayUtils.removeElement(userIds, i);
          switch (matchingRule.getConstructor()) {
            case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
              ((TdApi.UserPrivacySettingRuleRestrictUsers) matchingRule).userIds = newUserIds;
              break;
            case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
              ((TdApi.UserPrivacySettingRuleAllowUsers) matchingRule).userIds = newUserIds;
              break;
            default:
              throw new UnsupportedOperationException();
          }
        } else {
          newRules.remove(matchingRule);
        }
      }
    }
    return new TdApi.UserPrivacySettingRules(newRules.toArray(new TdApi.UserPrivacySettingRule[0]));
  }

  public TdApi.UserPrivacySettingRule firstMatchingRule (long chatId, boolean needContacts) {
    return firstMatchingRule(rules, chatId, needContacts);
  }

  public static TdApi.UserPrivacySettingRule firstMatchingRule (List<TdApi.UserPrivacySettingRule> rules, long chatId, boolean needContacts) {
    TdApi.UserPrivacySettingRule generalRule = null;
    for (TdApi.UserPrivacySettingRule rule : rules) {
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: {
          generalRule = rule;
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR: {
          return rule;
        }
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR: {
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR: {
          if (ArrayUtils.indexOf(((TdApi.UserPrivacySettingRuleAllowChatMembers) rule).chatIds, chatId) >= 0)
            return rule;
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR: {
          if (ArrayUtils.indexOf(((TdApi.UserPrivacySettingRuleRestrictChatMembers) rule).chatIds, chatId) >= 0)
            return rule;
          break;
        }
      }
    }
    return needContacts ? generalRule : null;
  }

  public TdApi.UserPrivacySettingRule findTopRule (boolean isContact) {
    for (TdApi.UserPrivacySettingRule rule : rules) {
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
          return rule;

        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: {
          if (isContact)
            return rule;
          break;
        }
      }
    }
    return null;
  }

  public TdApi.UserPrivacySettingRule firstMatchingRule (long userId, boolean isContact, long[] groupsInCommon) {
    return firstMatchingRule(rules, userId, isContact, groupsInCommon);
  }

  public static TdApi.UserPrivacySettingRule firstMatchingRule (List<TdApi.UserPrivacySettingRule> rules, long userId, boolean isContact, long[] groupsInCommon) {
    for (TdApi.UserPrivacySettingRule rule : rules) {
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: {
          if (isContact)
            return rule;
          continue;
        }
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR: {
          return rule;
        }
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR: {
          if (ArrayUtils.indexOf(((TdApi.UserPrivacySettingRuleAllowUsers) rule).userIds, userId) >= 0)
            return rule;
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR: {
          if (ArrayUtils.indexOf(((TdApi.UserPrivacySettingRuleRestrictUsers) rule).userIds, userId) >= 0)
            return rule;
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR: {
          if (ArrayUtils.findIntersection(((TdApi.UserPrivacySettingRuleAllowChatMembers) rule).chatIds, groupsInCommon) != 0)
            return rule;
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR: {
          if (ArrayUtils.findIntersection(((TdApi.UserPrivacySettingRuleRestrictChatMembers) rule).chatIds, groupsInCommon) != 0)
            return rule;
          break;
        }
      }
    }
    return null;
  }

  public static boolean isAllow (TdApi.UserPrivacySettingRule rule) {
    if (rule == null)
      return false;
    switch (rule.getConstructor()) {
      case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        return true;
      case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
        return false;
    }
    throw new UnsupportedOperationException();
  }

  public static boolean isChatMembers (TdApi.UserPrivacySettingRule rule) {
    if (rule == null)
      return false;
    switch (rule.getConstructor()) {
      case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
        return true;
    }
    return false;
  }

  public TdApi.UserPrivacySettingRules toRules () {
    return new TdApi.UserPrivacySettingRules(rules.toArray(new TdApi.UserPrivacySettingRule[0]));
  }

  public static boolean isGeneral (TdApi.UserPrivacySettingRule rule, boolean chatMembersIsGeneral) {
    if (rule == null)
      return true;
    switch (rule.getConstructor()) {
      case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
        return true;
      case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
        return false;
      case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
        return chatMembersIsGeneral;
    }
    throw new UnsupportedOperationException();
  }

  public TdApi.UserPrivacySettingRules allowExceptions (long[] userIds, long[] chatIds) {
    List<TdApi.UserPrivacySettingRule> newRules = new ArrayList<>(this.rules);
    for (int i = newRules.size() - 1; i >= 0; i--) {
      TdApi.UserPrivacySettingRule rule = newRules.get(i);
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
          newRules.remove(i);
          break;
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR: {
          TdApi.UserPrivacySettingRuleRestrictUsers restrictUsers = (TdApi.UserPrivacySettingRuleRestrictUsers) rule;
          restrictUsers.userIds = ArrayUtils.removeAll(restrictUsers.userIds, userIds);
          if (restrictUsers.userIds.length == 0) {
            newRules.remove(i);
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR: {
          TdApi.UserPrivacySettingRuleRestrictChatMembers restrictChatMembers = (TdApi.UserPrivacySettingRuleRestrictChatMembers) rule;
          restrictChatMembers.chatIds = ArrayUtils.removeAll(restrictChatMembers.chatIds, chatIds);
          if (restrictChatMembers.chatIds.length == 0) {
            newRules.remove(i);
          }
          break;
        }
      }
    }
    if (chatIds != null && chatIds.length > 0) {
      int index = 0;
      while (index < newRules.size()) {
        TdApi.UserPrivacySettingRule rule = rules.get(index);
        switch (rule.getConstructor()) {
          case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
          case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
          case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
          case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
          case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
            index++;
            continue;
          case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
          case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
          case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
            break;
        }
        break;
      }
      TdApi.UserPrivacySettingRuleAllowChatMembers newRule = new TdApi.UserPrivacySettingRuleAllowChatMembers(chatIds);
      if (index < newRules.size()) {
        newRules.add(index, newRule);
      } else {
        newRules.add(newRule);
      }
    }
    if (userIds != null && userIds.length > 0) {
      newRules.add(0, new TdApi.UserPrivacySettingRuleAllowUsers(userIds));
    }
    return new TdApi.UserPrivacySettingRules(newRules.toArray(new TdApi.UserPrivacySettingRule[0]));
  }

  public TdApi.UserPrivacySettingRules disallowExceptions (long[] userIds, long[] chatIds) {
    List<TdApi.UserPrivacySettingRule> newRules = new ArrayList<>(this.rules);
    for (int i = newRules.size() - 1; i >= 0; i--) {
      TdApi.UserPrivacySettingRule rule = newRules.get(i);
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
          newRules.remove(i);
          break;
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR: {
          TdApi.UserPrivacySettingRuleAllowUsers restrictUsers = (TdApi.UserPrivacySettingRuleAllowUsers) rule;
          restrictUsers.userIds = ArrayUtils.removeAll(restrictUsers.userIds, userIds);
          if (restrictUsers.userIds.length == 0) {
            newRules.remove(i);
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR: {
          TdApi.UserPrivacySettingRuleAllowChatMembers restrictChatMembers = (TdApi.UserPrivacySettingRuleAllowChatMembers) rule;
          restrictChatMembers.chatIds = ArrayUtils.removeAll(restrictChatMembers.chatIds, chatIds);
          if (restrictChatMembers.chatIds.length == 0) {
            newRules.remove(i);
          }
          break;
        }
      }
    }
    if (chatIds != null && chatIds.length > 0) {
      int index = 0;
      while (index < newRules.size() && !isGeneral(newRules.get(index), true)) {
        index++;
      }
      TdApi.UserPrivacySettingRuleRestrictChatMembers newRule = new TdApi.UserPrivacySettingRuleRestrictChatMembers(chatIds);
      if (index < newRules.size()) {
        newRules.add(index, newRule);
      } else {
        newRules.add(newRule);
      }
    }
    if (userIds != null && userIds.length > 0) {
      newRules.add(0, new TdApi.UserPrivacySettingRuleRestrictUsers(userIds));
    }
    return new TdApi.UserPrivacySettingRules(newRules.toArray(new TdApi.UserPrivacySettingRule[0]));
  }

  public long[] getPlusUserIds () {
    return plusUserIds;
  }

  public long[] getMinusUserIds () {
    return minusUserIds;
  }

  public int getPlusTotalCount (Tdlib tdlib) {
    return getPlusUserIdCount() + getPlusChatMemberCount(tdlib);
  }

  public int getMinusTotalCount (Tdlib tdlib) {
    return getMinusUserIdCount() + getMinusChatMemberCount(tdlib);
  }

  public int getPlusUserIdCount () {
    return plusUserIds != null ? plusUserIds.length : 0;
  }

  public int getMinusUserIdCount () {
    return minusUserIds != null ? minusUserIds.length : 0;
  }

  public int getPlusChatMemberCount (Tdlib tdlib) {
    int memberCount = 0;
    if (plusChatIds != null) {
      for (long chatId : plusChatIds) {
        memberCount += tdlib.chatMemberCount(chatId);
      }
    }
    return memberCount;
  }

  public int getMinusChatMemberCount (Tdlib tdlib) {
    int memberCount = 0;
    if (minusChatIds != null) {
      for (long chatId : minusChatIds) {
        memberCount += tdlib.chatMemberCount(chatId);
      }
    }
    return memberCount;
  }

  public int getMode () {
    return mode;
  }

  public static PrivacySettings valueOf (TdApi.UserPrivacySettingRules rules) {
    if (rules == null)
      return null;
    boolean allowContacts = false;
    boolean allowAll = false;
    boolean contactsHandled = false;
    LongList plusUserIds = null;
    LongList minusUserIds = null;
    LongList plusChatIds = null;
    LongList minusChatIds = null;
    loop : for (TdApi.UserPrivacySettingRule rule : rules.rules) {
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
          allowAll = true;
          if (!contactsHandled) {
            allowContacts = false;
          }
          break loop;
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
          allowAll = false;
          if (!contactsHandled) {
            allowContacts = false;
          }
          break loop;
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR: {
          if (!contactsHandled) {
            contactsHandled = true;
            allowContacts = true;
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: {
          if (!contactsHandled) {
            contactsHandled = true;
            allowContacts = false;
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR: {
          long[] userIds = ((TdApi.UserPrivacySettingRuleAllowUsers) rule).userIds;
          if (plusUserIds == null)
            plusUserIds = new LongList(userIds);
          else
            plusUserIds.appendAll(userIds);
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR: {
          long[] userIds = ((TdApi.UserPrivacySettingRuleRestrictUsers) rule).userIds;
          if (minusUserIds == null)
            minusUserIds = new LongList(userIds);
          else
            minusUserIds.appendAll(userIds);
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR: {
          long[] chatIds = ((TdApi.UserPrivacySettingRuleAllowChatMembers) rule).chatIds;
          if (plusChatIds == null)
            plusChatIds = new LongList(chatIds);
          else
            plusChatIds.appendAll(chatIds);
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR: {
          long[] chatIds = ((TdApi.UserPrivacySettingRuleRestrictChatMembers) rule).chatIds;
          if (minusChatIds == null)
            minusChatIds = new LongList(chatIds);
          else
            minusChatIds.appendAll(chatIds);
          break;
        }
      }
    }
    return new PrivacySettings(rules, allowAll ? MODE_EVERYBODY : allowContacts ? MODE_CONTACTS : MODE_NOBODY, plusUserIds != null ? plusUserIds.get() : null, minusUserIds != null ? minusUserIds.get() : null, plusChatIds != null ? plusChatIds.get() : null, minusChatIds != null ? minusChatIds.get() : null);
  }
}
