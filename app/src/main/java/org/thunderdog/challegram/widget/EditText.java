/**
 * File created on 25/04/15 at 12:27
 * Copyright Vyacheslav Krylov, 2014
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
