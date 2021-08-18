/**
 * File created on 16/08/15 at 16:17
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.util;

import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.ui.ContactsController;

public interface UserPickerDelegate {
  boolean onUserPick (ContactsController context, View view, TdApi.User user); // true if no confirm required
  void onUserConfirm (ContactsController context, TdApi.User user, int option);
  default boolean allowGlobalSearch () { return false; }
  default String getUserPickTitle () { return null; }
}
