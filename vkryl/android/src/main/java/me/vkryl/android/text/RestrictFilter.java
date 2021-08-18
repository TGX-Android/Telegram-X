package me.vkryl.android.text;

import me.vkryl.core.ArrayUtils;

public class RestrictFilter extends AcceptFilter {
  private final char[] restrictedChars;

  public RestrictFilter (char[] restrictedChars) {
    this.restrictedChars = restrictedChars;
  }

  @Override
  protected boolean accept (char c) {
    return !ArrayUtils.contains(restrictedChars, c);
  }
}
