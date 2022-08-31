/*
 * This file is a part of Telegram X
 * Copyright © 2014-2022 (tgx-android@pm.me)
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
import android.text.InputFilter;
import android.util.AttributeSet;

import org.thunderdog.challegram.emoji.EmojiFilter;
import org.thunderdog.challegram.emoji.EmojiUpdater;
import org.thunderdog.challegram.v.EditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.lambda.Destroyable;

public class EmojiEditText extends EditText implements Destroyable {
  private final EmojiUpdater emojiUpdater = new EmojiUpdater(this);

  public EmojiEditText (Context context) {
    super(context);
    setFilters(new InputFilter[0]);
  }

  public EmojiEditText (Context context, AttributeSet attrs) {
    super(context, attrs);
    setFilters(new InputFilter[0]);
  }

  public EmojiEditText (Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setFilters(new InputFilter[0]);
  }

  @Override
  public void setFilters (InputFilter[] filters) {
    if (filters != null) {
      for (InputFilter filter : filters) {
        if (filter instanceof EmojiFilter) {
          super.setFilters(filters);
          return;
        }
      }
    }
    List<InputFilter> filtersList = new ArrayList<>(1);
    filtersList.add(new EmojiFilter());
    if (filters != null) {
      Collections.addAll(filtersList, filters);
    }
    InputFilter[] newFilters = filtersList.toArray(new InputFilter[0]);
    super.setFilters(newFilters);
  }

  @Override
  public final void performDestroy () {
    emojiUpdater.performDestroy();
  }
}
