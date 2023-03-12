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
 * File created on 31/08/2022, 22:07.
 */

package org.thunderdog.challegram.widget;

import android.content.Context;
import android.os.Build;
import android.text.InputFilter;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.emoji.EmojiInputConnection;
import org.thunderdog.challegram.emoji.EmojiUpdater;
import org.thunderdog.challegram.util.DestroySpansWatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.lambda.Destroyable;

public class EmojiTextView extends TextView implements Destroyable {
  private EmojiUpdater emojiUpdater;

  public EmojiTextView (Context context) {
    super(context);
    init();
  }

  public EmojiTextView (Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public EmojiTextView (Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init () {
    addTextChangedListener(new DestroySpansWatcher());
    setFilters(new InputFilter[0]);
  }

  @Override
  public final void setFilters (@NonNull InputFilter[] filters) {
    if (emojiUpdater == null)
      emojiUpdater = new EmojiUpdater(this);
    super.setFilters(newFilters(filters, emojiUpdater));
  }

  @Override
  @CallSuper
  public void performDestroy () {
    emojiUpdater.performDestroy();
  }

  @Override
  public final InputConnection onCreateInputConnection (EditorInfo editorInfo) {
    InputConnection ic = createInputConnection(editorInfo);
    if (ic != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !(ic instanceof EmojiInputConnection)) {
      return new EmojiInputConnection(this, ic);
    } else {
      return ic;
    }
  }

  protected InputConnection createInputConnection (EditorInfo editorInfo) {
    return super.onCreateInputConnection(editorInfo);
  }

  static InputFilter[] newFilters (@NonNull InputFilter[] filters, InputFilter emojiUpdater) {
    int emojiFilterIndex = -1;
    int emojiUpdaterIndex = -1;
    for (int i = 0; i < filters.length; i++) {
      InputFilter filter = filters[i];
      if (filter instanceof EmojiFilter) {
        emojiFilterIndex = i;
      } else if (filter == emojiUpdater) {
        emojiUpdaterIndex = i;
      } else {
        continue;
      }
      if (emojiFilterIndex != -1 && emojiUpdaterIndex != -1) {
        return filters;
      }
    }
    List<InputFilter> filtersList = new ArrayList<>(
      (emojiFilterIndex == -1 ? 1 : 0) +
        (emojiUpdaterIndex == -1 ? 1 : 0) +
        filters.length
    );
    if (emojiFilterIndex == -1) {
      filtersList.add(new EmojiFilter());
      if (emojiUpdaterIndex == -1) {
        filtersList.add(emojiUpdater);
      }
      Collections.addAll(filtersList, filters);
    } else {
      Collections.addAll(filtersList, filters);
      filtersList.add(emojiFilterIndex + 1, emojiUpdater);
    }
    return filtersList.toArray(new InputFilter[0]);
  }
}
