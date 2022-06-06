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
 * File created on 21/11/2016
 */
package org.thunderdog.challegram.navigation;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.widget.NoScrollTextView;

import me.vkryl.android.widget.FrameLayoutFix;

public class SimpleHeaderView extends FrameLayoutFix implements ColorSwitchPreparator, TextChangeDelegate {
  private TextView title;

  public SimpleHeaderView (Context context) {
    super(context);

    title = newTitle(context);
    title.setTag(this);
    addView(title);
  }

  public static TextView newTitle (Context context) {
    FrameLayoutFix.LayoutParams params = FrameLayoutFix.newParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | (Lang.rtl() ? Gravity.RIGHT : Gravity.LEFT));
    params.setMargins(0, Screen.dp(15f), 0, 0);
    if (Lang.rtl()) {
      params.rightMargin = Screen.dp(68f);
    } else {
      params.leftMargin = Screen.dp(68f);
    }

    TextView title = new NoScrollTextView(context);
    title.setTypeface(Fonts.getRobotoMedium());
    title.setSingleLine();
    title.setGravity(Gravity.LEFT);
    title.setEllipsize(TextUtils.TruncateAt.END);
    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19f);
    title.setTextColor(0xffffffff);
    title.setLayoutParams(params);

    return title;
  }

  public void initWithController (ViewController<?> c) {
    this.title.setText(c.getName());
  }

  @Override
  public void setTextColor (int color) {
    title.setTextColor(color);
  }

  @Override
  public final void prepareColorChangers (int fromColor, int toColor) {
    // reserved for children
  }
}
