/**
 * File created on 25/04/15 at 07:58
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.navigation;

import android.view.View;
import android.widget.LinearLayout;

public interface Menu {
  void fillMenuItems (int id, HeaderView header, LinearLayout menu);
  void onMenuItemPressed (int id, View view);
}
