package org.thunderdog.challegram.telegram;

import org.thunderdog.challegram.BaseActivity;

/**
 * Date: 2/24/18
 * Author: default
 */

public class TdlibContext implements TdlibDelegate {
  private final BaseActivity context;
  private final Tdlib tdlib;

  public TdlibContext (BaseActivity context, Tdlib tdlib) {
    this.context = context;
    this.tdlib = tdlib;
  }

  @Override
  public BaseActivity context () {
    return context;
  }

  @Override
  public Tdlib tdlib () {
    return tdlib;
  }
}
