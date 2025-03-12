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
package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.collection.LongList;
import tgx.td.ChatId;
import tgx.td.Td;

public class PrivacySettings {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    Mode.NOBODY, Mode.CONTACTS, Mode.EVERYBODY
  })
  public @interface Mode {
    int NOBODY = 0, CONTACTS = 1, EVERYBODY = 2;
  }

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    BotsException.DEFAULT, BotsException.ALLOW, BotsException.RESTRICT
  })
  public @interface BotsException {
    int DEFAULT = 0, ALLOW = 1, RESTRICT = 2;
  }

  private final List<TdApi.UserPrivacySettingRule> rules;
  private final int mode;
  private final boolean plusPremium;
  private final int botsException;
  private final long[] plusUserIds;
  private final long[] minusUserIds;
  private final long[] plusChatIds;
  private final long[] minusChatIds;

  public PrivacySettings (TdApi.UserPrivacySettingRules rules, @Mode int mode, boolean plusPremium, @BotsException int botsException, long[] plusUserIds, long[] minusUserIds, long[] plusChatIds, long[] minusChatIds) {
    this.rules = Arrays.asList(rules.rules);
    this.mode = mode;
    this.botsException = botsException;
    this.plusPremium = plusPremium;
    this.plusUserIds = plusUserIds;
    this.minusUserIds = minusUserIds;
    this.plusChatIds = plusChatIds;
    this.minusChatIds = minusChatIds;
  }

  public boolean needNeverAllow () {
    return mode != Mode.NOBODY;
  }

  public boolean needAlwaysAllow () {
    if (mode != Mode.EVERYBODY)
      return true;
    for (TdApi.UserPrivacySettingRule rule : rules) {
      if (isGeneral(rule, false, false, false))
        break;
      if (rule.getConstructor() == TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR)
        return true;
    }
    return false;
  }

  public boolean needPlusPremium () {
    return plusPremium;
  }

  public boolean needPlusOrMinusBots () {
    return botsException != BotsException.DEFAULT;
  }

  public long[] getAllPlusIds () {
    int userIdsCount = (plusUserIds != null ? plusUserIds.length : 0);
    int chatIdsCount = (plusChatIds != null ? plusChatIds.length : 0);
    LongList list = new LongList(userIdsCount + chatIdsCount);
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
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR: {
          break;
        }
        default: {
          Td.assertUserPrivacySettingRule_58b21786();
          throw Td.unsupported(rule);
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
          Td.assertUserPrivacySettingRule_58b21786();
          if (isGeneral(rule, false, false, false)) {
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

  public boolean isAllowed (boolean forPremium, boolean forContacts, boolean forBots) {
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
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
          if (forBots)
            return true;
          continue;
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR:
          if (forBots)
            return false;
          continue;
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
          return true;
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
          return false;
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR:
          if (forPremium)
            return true;
          continue;
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
          break;
        default:
          Td.assertUserPrivacySettingRule_58b21786();
          throw Td.unsupported(rule);
      }
    }
    return false;
  }

  public TdApi.UserPrivacySettingRules toggleGlobal (int mode, boolean plusPremium, boolean plusOrMinusBots) {
    switch (mode) {
      case Mode.NOBODY:
        return toggleGlobal(plusPremium, false, false, plusOrMinusBots ? BotsException.ALLOW : BotsException.DEFAULT);
      case Mode.CONTACTS:
        return toggleGlobal(plusPremium, true, false, plusOrMinusBots ? BotsException.ALLOW : BotsException.DEFAULT);
      case Mode.EVERYBODY:
        return toggleGlobal(true, true, true, plusOrMinusBots ? BotsException.RESTRICT : BotsException.DEFAULT);
    }
    throw new UnsupportedOperationException(Integer.toString(mode));
  }

  public TdApi.UserPrivacySettingRules togglePlusOrMinusBots (boolean plusOrMinusBots) {
    return toggleGlobal(mode, plusPremium, plusOrMinusBots);
  }

  public TdApi.UserPrivacySettingRules togglePlusPremium (boolean plusPremium) {
    List<TdApi.UserPrivacySettingRule> newRules = new ArrayList<>(rules);
    int globalRuleIndex = -1;
    for (int i = 0; i < newRules.size(); i++) {
      TdApi.UserPrivacySettingRule rule = newRules.get(i);
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR: {
          newRules.remove(i);
          i--;
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: {
          if (globalRuleIndex == -1) {
            globalRuleIndex = i;
          }
          break;
        }
        default: {
          Td.assertUserPrivacySettingRule_58b21786();
          break;
        }
      }
    }
    if (plusPremium) {
      if (globalRuleIndex != -1) {
        // insert before restrictAll,allowAll
        newRules.add(globalRuleIndex, new TdApi.UserPrivacySettingRuleAllowPremiumUsers());
      } else {
        newRules.add(new TdApi.UserPrivacySettingRuleAllowPremiumUsers());
      }
    }
    return new TdApi.UserPrivacySettingRules(newRules.toArray(new TdApi.UserPrivacySettingRule[0]));
  }

  public TdApi.UserPrivacySettingRules toggleGlobal (boolean allowPremium, boolean allowContacts, boolean allowOther, @BotsException int botsException) {
    List<TdApi.UserPrivacySettingRule> newRules = new ArrayList<>(rules);
    for (int i = 0; i < newRules.size(); i++) {
      TdApi.UserPrivacySettingRule rule = newRules.get(i);
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
          newRules.remove(i);
          i--;
          break;
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
          break;
        default:
          Td.assertUserPrivacySettingRule_58b21786();
          throw Td.unsupported(rule);
      }
    }
    switch (botsException) {
      case BotsException.DEFAULT:
        break;
      case BotsException.ALLOW:
        newRules.add(new TdApi.UserPrivacySettingRuleAllowBots());
        break;
      case BotsException.RESTRICT:
        newRules.add(new TdApi.UserPrivacySettingRuleRestrictBots());
        break;
    }
    if (allowContacts && allowOther) {
      newRules.add(new TdApi.UserPrivacySettingRuleAllowAll());
    } else {
      if (allowPremium) {
        newRules.add(new TdApi.UserPrivacySettingRuleAllowPremiumUsers());
      }
      if (allowContacts) {
        newRules.add(new TdApi.UserPrivacySettingRuleAllowContacts());
        newRules.add(new TdApi.UserPrivacySettingRuleRestrictAll());
      } else if (allowOther) {
        newRules.add(new TdApi.UserPrivacySettingRuleRestrictContacts());
        newRules.add(new TdApi.UserPrivacySettingRuleAllowAll());
      } else if (!newRules.isEmpty()) {
        newRules.add(new TdApi.UserPrivacySettingRuleRestrictAll());
      }
    }
    return new TdApi.UserPrivacySettingRules(newRules.toArray(new TdApi.UserPrivacySettingRule[0]));
  }

  public TdApi.UserPrivacySettingRules toggleChat (long chatId, boolean value) {
    List<TdApi.UserPrivacySettingRule> newRules = new ArrayList<>(rules);
    TdApi.UserPrivacySettingRule matchingRule;
    while (isAllow(matchingRule = firstMatchingRule(newRules, chatId, false, false, false)) != value) {
      if (isGeneral(matchingRule,  true,true, false)) {
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
              case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
              case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
              case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR: {
                // Placing chatMembers rules after any general rules
                index++;
                continue;
              }
              case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
              case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
              case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR: {
                // Do nothing
                break;
              }
              default: {
                Td.assertUserPrivacySettingRule_58b21786();
                throw Td.unsupported(matchingRule);
              }
            }
            break;
          }
        }
        if (index != -1) {
          TdApi.UserPrivacySettingRule newRule = value ?
            new TdApi.UserPrivacySettingRuleAllowChatMembers(new long[] {chatId}) :
            new TdApi.UserPrivacySettingRuleRestrictChatMembers(new long[] {chatId});
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

  public TdApi.UserPrivacySettingRules toggleUser (long userId, boolean isPremium, boolean isContact, boolean isBot, long[] groupsInCommon, boolean value) {
    List<TdApi.UserPrivacySettingRule> newRules = new ArrayList<>(rules);
    TdApi.UserPrivacySettingRule matchingRule;
    while (isAllow(matchingRule = firstMatchingRuleForUser(newRules, userId, isPremium, isContact, isBot, groupsInCommon)) != value) {
      if (isGeneral(matchingRule, true, true, true)) {
        int index = 0;
        while (index != -1 && index < newRules.size() && !isGeneral(matchingRule = newRules.get(index), true, true, true)) {
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

  public TdApi.UserPrivacySettingRule firstMatchingRuleForChat (long chatId) {
    return firstMatchingRuleForChat(chatId, false, false, false);
  }

  public TdApi.UserPrivacySettingRule firstMatchingRuleForChat (long chatId, boolean needPremiumUsers, boolean needContacts, boolean needBots) {
    return firstMatchingRule(rules, chatId, needPremiumUsers, needContacts, needBots);
  }

  @IntDef({
    ResolvedMatch.NONE,
    ResolvedMatch.SPECIFIC,
    ResolvedMatch.CONTACTS,
    ResolvedMatch.CONTACTS_AND_PREMIUM,
    ResolvedMatch.PREMIUM
  })
  public @interface ResolvedMatch {
    int
      NONE = 0,
      SPECIFIC = 1,
      CONTACTS = 2,
      CONTACTS_AND_PREMIUM = 3,
      PREMIUM = 5;
  }

  public @ResolvedMatch int resolveMatchingAllowRulesForChat (long chatId) {
    boolean contacts = false;
    boolean premium = false;
    for (TdApi.UserPrivacySettingRule rule : rules) {
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR: {
          contacts = true;
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: {
          contacts = false;
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR: {
          premium = true;
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR: {
          return ResolvedMatch.SPECIFIC;
        }
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR: {
          if (ArrayUtils.indexOf(((TdApi.UserPrivacySettingRuleAllowChatMembers) rule).chatIds, chatId) >= 0)
            return ResolvedMatch.SPECIFIC;
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR: {
          if (ArrayUtils.indexOf(((TdApi.UserPrivacySettingRuleRestrictChatMembers) rule).chatIds, chatId) >= 0)
            return ResolvedMatch.SPECIFIC;
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR: {
          break;
        }
        default: {
          Td.assertUserPrivacySettingRule_58b21786();
          throw Td.unsupported(rule);
        }
      }
    }
    if (contacts && premium) {
      return ResolvedMatch.CONTACTS_AND_PREMIUM;
    } else if (contacts) {
      return ResolvedMatch.CONTACTS;
    } else if (premium) {
      return ResolvedMatch.PREMIUM;
    } else {
      return ResolvedMatch.NONE;
    }
  }

  public static TdApi.UserPrivacySettingRule firstMatchingRule (List<TdApi.UserPrivacySettingRule> rules, long chatId, boolean needPremiumUsers, boolean needContacts, boolean needBots) {
    TdApi.UserPrivacySettingRule generalRule = null;
    for (TdApi.UserPrivacySettingRule rule : rules) {
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: {
          if (needContacts) {
            generalRule = rule;
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR: {
          if (needBots) {
            generalRule = rule;
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR: {
          if (needPremiumUsers) {
            generalRule = rule;
          }
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
        default: {
          Td.assertUserPrivacySettingRule_58b21786();
          throw Td.unsupported(rule);
        }
      }
    }
    return generalRule;
  }

  public TdApi.UserPrivacySettingRule findTopRule (boolean isPremium, boolean isContact, boolean isBot) {
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
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR: {
          if (isBot)
            return rule;
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR: {
          if (isPremium)
            return rule;
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
          break;
        default: {
          Td.assertUserPrivacySettingRule_58b21786();
          throw Td.unsupported(rule);
        }
      }
    }
    return null;
  }

  public TdApi.UserPrivacySettingRule firstMatchingRuleForUser (long userId, boolean isPremium, boolean isContact, boolean isBot, long[] groupsInCommon) {
    return firstMatchingRuleForUser(rules, userId, isPremium, isContact, isBot, groupsInCommon);
  }

  public static TdApi.UserPrivacySettingRule firstMatchingRuleForUser (List<TdApi.UserPrivacySettingRule> rules, long userId, boolean isPremium, boolean isContact, boolean isBot, long[] groupsInCommon) {
    for (TdApi.UserPrivacySettingRule rule : rules) {
      switch (rule.getConstructor()) {
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: {
          if (isContact)
            return rule;
          continue;
        }
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR: {
          if (isBot)
            return rule;
          continue;
        }
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR: {
          return rule;
        }
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR: {
          if (isPremium)
            return rule;
          continue;
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
        default: {
          Td.assertUserPrivacySettingRule_58b21786();
          throw Td.unsupported(rule);
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
      case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR:
        return true;
      case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
        return false;
      default: {
        Td.assertUserPrivacySettingRule_58b21786();
        throw Td.unsupported(rule);
      }
    }
  }

  public TdApi.UserPrivacySettingRules toRules () {
    return new TdApi.UserPrivacySettingRules(rules.toArray(new TdApi.UserPrivacySettingRule[0]));
  }

  public static boolean isGeneral (TdApi.UserPrivacySettingRule rule, boolean premiumIsGeneral, boolean botsIsGeneral, boolean chatMembersIsGeneral) {
    if (rule == null)
      return true;
    switch (rule.getConstructor()) {
      case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
        return true;
      case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR:
        return premiumIsGeneral;
      case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR:
        return botsIsGeneral;
      case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR:
        return false;
      case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR:
      case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
        return chatMembersIsGeneral;
      default: {
        Td.assertUserPrivacySettingRule_58b21786();
        throw Td.unsupported(rule);
      }
    }
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
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR: {
          break;
        }
        default: {
          Td.assertUserPrivacySettingRule_58b21786();
          throw Td.unsupported(rule);
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
          case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR:
            index++;
            continue;
          case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
          case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
          case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR:
          case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR:
          case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR:
            break;
          default: {
            Td.assertUserPrivacySettingRule_58b21786();
            throw Td.unsupported(rule);
          }
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
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR:
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR: {
          break;
        }
        default: {
          Td.assertUserPrivacySettingRule_58b21786();
          throw Td.unsupported(rule);
        }
      }
    }
    if (chatIds != null && chatIds.length > 0) {
      int index = 0;
      while (index < newRules.size() && !isGeneral(newRules.get(index), true, true, true)) {
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

  public @Mode int getMode () {
    return mode;
  }

  public static PrivacySettings valueOf (TdApi.UserPrivacySettingRules rules) {
    if (rules == null)
      return null;
    boolean allowContacts = false;
    boolean allowAll = false;
    boolean contactsHandled = false;
    boolean premiumHandled = false;
    boolean plusPremium = false;
    @BotsException int botsException = BotsException.DEFAULT;
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
          if (!premiumHandled) {
            plusPremium = false;
          }
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
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR: {
          botsException = BotsException.ALLOW;
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR: {
          botsException = BotsException.RESTRICT;
          break;
        }
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: {
          if (!contactsHandled) {
            contactsHandled = true;
            allowContacts = false;
          }
          break;
        }
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR: {
          if (!premiumHandled) {
            premiumHandled = true;
            plusPremium = true;
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
        default: {
          Td.assertUserPrivacySettingRule_58b21786();
          throw Td.unsupported(rule);
        }
      }
    }
    return new PrivacySettings(
      rules,
      allowAll ? Mode.EVERYBODY : allowContacts ? Mode.CONTACTS : Mode.NOBODY,
      plusPremium,
      botsException,
      plusUserIds != null ? plusUserIds.get() : null,
      minusUserIds != null ? minusUserIds.get() : null,
      plusChatIds != null ? plusChatIds.get() : null,
      minusChatIds != null ? minusChatIds.get() : null
    );
  }
}
