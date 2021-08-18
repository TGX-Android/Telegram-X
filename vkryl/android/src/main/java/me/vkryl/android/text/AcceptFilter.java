package me.vkryl.android.text;

import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

public abstract class AcceptFilter implements InputFilter {
  public interface Listener {
    void onCharRemoved (AcceptFilter filter, CharSequence source, int start, int end, int index, char c);
  }

  private Listener listener;

  public AcceptFilter setListener (Listener listener) {
    this.listener = listener;
    return this;
  }

  protected abstract boolean accept (char c);

  @Override
  public final CharSequence filter (CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
    boolean keepOriginal = true;
    StringBuilder sb = null;
    SpannableStringBuilder spb = null;
    boolean spanned = source instanceof Spanned;
    int lastGoodIndex = -1;
    for (int i = start; i < end; i++) {
      char c = source.charAt(i);
      if (!accept(c)) {
        if (listener != null)
          listener.onCharRemoved(this, source, start, end, i, c);
        if (keepOriginal) {
          if (spanned) {
            spb = new SpannableStringBuilder(source, start, i);
            TextUtils.copySpans((Spanned) source, start, i, null, spb, 0);
          } else {
            sb = new StringBuilder(end - i).append(source, start, i);
          }
          keepOriginal = false;
          lastGoodIndex = -1;
        } else if (lastGoodIndex != -1) {
          if (spanned) {
            spb.append(source, lastGoodIndex, i);
            TextUtils.copySpans((Spanned) source, lastGoodIndex, i, null, spb, 0);
          } else {
            sb.append(source, lastGoodIndex, i);
          }
          lastGoodIndex = -1;
        }
      } else if (lastGoodIndex == -1) {
        lastGoodIndex = i;
      }
    }
    if (keepOriginal) {
      return null;
    }
    if (lastGoodIndex != -1) {
      if (spanned) {
        spb.append(source, lastGoodIndex, end);
        TextUtils.copySpans((Spanned) source, lastGoodIndex, end, null, spb, 0);
      } else {
        sb.append(source, lastGoodIndex, end);
      }
    }
    return spanned ? spb : sb;
  }
}
