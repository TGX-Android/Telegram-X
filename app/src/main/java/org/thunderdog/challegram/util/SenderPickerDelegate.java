/**
 * File created on 16/08/15 at 16:17
 * Copyright Vyacheslav Krylov, 2014
 */
package org.thunderdog.challegram.util;

import android.view.View;

import org.drinkless.td.libcore.telegram.TdApi;
import org.thunderdog.challegram.ui.ContactsController;

public interface SenderPickerDelegate {
  boolean onSenderPick (ContactsController context, View view, TdApi.MessageSender senderId); // true if no confirm required
  default void onSenderConfirm (ContactsController context, TdApi.MessageSender senderId, int option) { } // called if onSenderPick returned false
  default boolean allowGlobalSearch () { return false; }
  default String getUserPickTitle () { return null; }
}
