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
 * File created on 23/01/2016 at 01:49
 */
package org.thunderdog.challegram.component.passcode;

import android.text.method.PasswordTransformationMethod;
import android.view.View;

public class InvisibleTransformationMethod extends PasswordTransformationMethod {
  private static InvisibleTransformationMethod instance;

  public static InvisibleTransformationMethod instance () {
    if (instance == null) {
      instance = new InvisibleTransformationMethod();
    }
    return instance;
  }

  @Override
  public CharSequence getTransformation (CharSequence source, View view) {
    return new InvisibleCharSequence(source);
  }

  private static class InvisibleCharSequence implements CharSequence {
    private CharSequence source;

    public InvisibleCharSequence (CharSequence source) {
      this.source = source;
    }

    @Override
    public char charAt (int index) {
      return '\0';
    }

    @Override
    public int length () {
      return source.length();
    }

    @Override
    public CharSequence subSequence (int start, int end) {
      return source.subSequence(start, end);
    }
  }
}
