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
 * File created on 01/09/2022, 04:22.
 */

package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.tool.Views;
import org.thunderdog.challegram.util.TextSelection;

public class TextView extends android.widget.TextView {
  private boolean scrollDisabled;
  private TextSelection selection;

  public TextView (Context context) {
    super(context);
  }

  public TextView (Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public TextView (Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Nullable
  public final TextSelection getTextSelection () {
    if (!UI.inUiThread()) {
      throw new IllegalStateException();
    }
    if (selection == null) {
      selection = new TextSelection();
    }
    if (Views.getSelection(this, selection)) {
      return selection;
    } else {
      return null;
    }
  }

  public final void setScrollDisabled (boolean scrollDisabled) {
    this.scrollDisabled = scrollDisabled;
  }

  @Override
  public boolean canScrollHorizontally (int direction) {
    if (scrollDisabled) {
      return false;
    } else {
      return super.canScrollHorizontally(direction);
    }
  }

  @Override
  public boolean canScrollVertically (int direction) {
    if (scrollDisabled) {
      return false;
    } else {
      return super.canScrollVertically(direction);
    }
  }
}
