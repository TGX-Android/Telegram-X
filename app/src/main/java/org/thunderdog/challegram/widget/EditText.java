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
 * File created on 25/04/2015 at 12:27
 */
package org.thunderdog.challegram.widget;

import android.content.Context;
import android.util.TypedValue;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Fonts;
import org.thunderdog.challegram.v.EditTextBase;

public class EditText extends EditTextBase {
  public EditText (Context context) {
    super(context);
    setTextColor(Theme.textAccentColor());
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f);
    setHintTextColor(Theme.textPlaceholderColor());
    setTypeface(Fonts.getRobotoRegular());
    setBackgroundResource(R.drawable.bg_edittext);
  }
}
