/**
 * File created on 23/01/16 at 01:49
 * Copyright Vyacheslav Krylov, 2014
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
