package org.thunderdog.challegram.util;

import android.text.TextPaint;
import android.text.style.ClickableSpan;

import androidx.annotation.NonNull;

public abstract class NoUnderlineClickableSpan extends ClickableSpan {
  public NoUnderlineClickableSpan () { }

  @Override
  public void updateDrawState (@NonNull TextPaint ds) {
    boolean isUnderlineText = ds.isUnderlineText();
    super.updateDrawState(ds);
    ds.setUnderlineText(isUnderlineText);
  }
}
