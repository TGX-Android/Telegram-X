package org.thunderdog.challegram.util.text;

/**
 * Date: 10/22/17
 * Author: default
 */
public class Letters {
  public final String text;
  public final boolean needFakeBold;

  public Letters (String str) {
    this.text = str != null ? str : "";
    this.needFakeBold = Text.needFakeBold(str);
  }
}
