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

import androidx.annotation.NonNull;

import org.thunderdog.challegram.emoji.EmojiUpdater;
import org.thunderdog.challegram.v.EditText;

import me.vkryl.core.lambda.Destroyable;

public class EmojiEditText extends EditText implements Destroyable {
  private EmojiUpdater emojiUpdater;

  public EmojiEditText (Context context) {
    super(context);
    init();
  }

  public EmojiEditText (Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public EmojiEditText (Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init () {
    setFilters(new InputFilter[0]);
  }

  @Override
  public final void setFilters (@NonNull InputFilter[] filters) {
    if (emojiUpdater == null)
      emojiUpdater = new EmojiUpdater(this);
    super.setFilters(EmojiTextView.newFilters(filters, emojiUpdater));
  }

  @Override
  public final void performDestroy () {
    emojiUpdater.performDestroy();
  }
}
