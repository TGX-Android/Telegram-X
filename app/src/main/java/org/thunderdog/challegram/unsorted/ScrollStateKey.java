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
package org.thunderdog.challegram.unsorted;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tgx.td.Td;

/** Existing on-disk format for normal-history positions; special modes do not persist positions. */
@me.vkryl.annotation.MadeByLLM(value = "gpt-6-astra", id = "forum-topics", ref = "PR #1095")
final class ScrollStateKey {
  static final String PREFIX = "scroll_chat";
  private static final Pattern FIELD =
    Pattern.compile(
      "^(_(?:message|chat|aliases|stack|offset|read|top))(?:_(?:thread|forum|direct|saved)-?\\d+)?$"
    );

  private ScrollStateKey () {
  }

  static String key (
    @Nullable String field,
    int accountId,
    long chatId,
    @Nullable TdApi.MessageTopic topicId
  ) {
    return (accountId != 0 ? accountId + "_" : "") + PREFIX + chatId +
      (field != null ? field : "") + (topicId != null ? "_" + Td.cacheKey(topicId) : "");
  }

  @Nullable
  static String field (String key, int accountId, long chatId) {
    String prefix = key(null, accountId, chatId, null);
    if (!key.startsWith(prefix)) {
      return null;
    }
    Matcher matcher = FIELD.matcher(key.substring(prefix.length()));
    return matcher.matches() ? matcher.group(1) : null;
  }

  @Nullable
  static String field (
    String key,
    int accountId,
    long chatId,
    @Nullable TdApi.MessageTopic topicId
  ) {
    String field = field(key, accountId, chatId);
    return field != null && key.equals(key(field, accountId, chatId, topicId)) ? field : null;
  }
}