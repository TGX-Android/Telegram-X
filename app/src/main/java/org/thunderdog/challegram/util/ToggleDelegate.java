/**
 * File created on 09/08/15 at 15:50
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.util;

public interface ToggleDelegate {
  void onToggle (int section);
  String[] getToggleSections ();
}
