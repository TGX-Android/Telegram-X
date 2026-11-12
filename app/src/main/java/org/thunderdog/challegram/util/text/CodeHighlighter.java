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
 * File created on 26/10/2026
 */
package org.thunderdog.challegram.util.text;

import android.content.ComponentCallbacks2;
import android.util.LruCache;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.N;
import org.thunderdog.challegram.core.BaseThread;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.tool.UI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.vkryl.core.lambda.RunnableData;

/**
 * Finds tokens of code in background: they change only colors, so layout doesn't wait for them
 */
public final class CodeHighlighter {
  private CodeHighlighter () { }

  // Same as in syntax.cpp
  static final int STYLE_COLOR_MASK = 0xff;
  static final int STYLE_BOLD = 1 << 8;
  static final int STYLE_ITALIC = 1 << 9;

  // In the order of COLORED_TYPES of syntax.cpp
  static final int[] COLOR_IDS = {
    0,
    ColorId.syntaxComment,
    ColorId.syntaxBlockComment,
    ColorId.syntaxProlog,
    ColorId.syntaxDoctype,
    ColorId.syntaxCdata,
    ColorId.syntaxPunctuation,
    ColorId.syntaxProperty,
    ColorId.syntaxTag,
    ColorId.syntaxBoolean,
    ColorId.syntaxNumber,
    ColorId.syntaxConstant,
    ColorId.syntaxSymbol,
    ColorId.syntaxDeleted,
    ColorId.syntaxSelector,
    ColorId.syntaxAttrName,
    ColorId.syntaxString,
    ColorId.syntaxChar,
    ColorId.syntaxBuiltin,
    ColorId.syntaxInserted,
    ColorId.syntaxOperator,
    ColorId.syntaxEntity,
    ColorId.syntaxUrl,
    ColorId.syntaxAtrule,
    ColorId.syntaxAttrValue,
    ColorId.syntaxKeyword,
    ColorId.syntaxFunction,
    ColorId.syntaxClassName,
    ColorId.syntaxAnnotation
  };

  private static final int[] NO_TOKENS = new int[0];

  // About 2 MB, so that a block gets its colors at once at its next layout
  private static final LruCache<String, int[]> results = new LruCache<String, int[]>(512 * 1024) {
    @Override
    protected int sizeOf (String key, int[] value) {
      return key.length() / 2 + value.length + 1;
    }
  };
  private static final Map<String, List<RunnableData<int[]>>> pending = new HashMap<>();
  private static final BaseThread thread = new BaseThread("CodeHighlighter");

  private static String keyOf (String code, String language) {
    return language + '\n' + code;
  }

  public static void trimMemory (int level) {
    if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
      level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
      level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
      results.evictAll();
    }
  }

  @Nullable
  static int[] getTokens (String code, String language) {
    // Start, end and style of each token, empty when the code has none, null when not found yet
    return results.get(keyOf(code, language));
  }

  static void findTokens (String code, String language, RunnableData<int[]> onFound) {
    // onFound gets the tokens on the main thread, as the cache may drop them before
    final String key = keyOf(code, language);
    synchronized (pending) {
      final List<RunnableData<int[]>> callbacks = pending.get(key);
      if (callbacks != null) {
        callbacks.add(onFound);
        return;
      }
      final List<RunnableData<int[]>> newCallbacks = new ArrayList<>();
      newCallbacks.add(onFound);
      pending.put(key, newCallbacks);
    }
    thread.post(() -> {
      final String foundLanguage =
        language.isEmpty() ? CodeLanguageDetector.detect(code) : language;
      final int[] ranges = foundLanguage != null ? N.highlightCode(code, foundLanguage) : null;
      final int[] tokens = ranges != null ? ranges : NO_TOKENS;
      results.put(key, tokens);
      final List<RunnableData<int[]>> callbacks;
      synchronized (pending) {
        callbacks = pending.remove(key);
      }
      UI.post(() -> {
        for (RunnableData<int[]> callback : callbacks) {
          callback.runWithData(tokens);
        }
      });
    }, 0);
  }

  public static void runAfterPending (Runnable runnable) {
    // After tokens requested before reach their texts, e.g. so that tests draw code with colors
    thread.post(() -> UI.post(runnable), 0);
  }
}
