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
 * File created on 05/09/2022, 19:12.
 */

package org.thunderdog.challegram.util;

import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.vkryl.core.lambda.Destroyable;

public final class DestroySpansWatcher implements TextWatcher {
  private final List<Destroyable> spansToDestroy = new ArrayList<>();

  @Override
  public void beforeTextChanged (CharSequence s, int start, int count, int after) {
    if (s instanceof Spanned && start < s.length()) {
      Destroyable[] spans = ((Spanned) s).getSpans(start, Math.min(start + count, s.length()), Destroyable.class);
      if (spans != null) {
        Collections.addAll(spansToDestroy, spans);
      }
    }
  }

  @Override
  public void onTextChanged (CharSequence s, int start, int before, int count) { }

  @Override
  public void afterTextChanged (Editable s) {
    for (Destroyable destroyable : spansToDestroy) {
      destroyable.performDestroy();
    }
    spansToDestroy.clear();
  }
}
