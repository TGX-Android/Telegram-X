package org.thunderdog.challegram.util;

import org.thunderdog.challegram.data.TGUser;

import java.util.List;

/**
 * Date: 14/12/2016
 * Author: default
 */

public interface UserPickerMultiDelegate {
  long[] getAlreadySelectedChatIds ();
  void onAlreadyPickedChatsChanged (List<TGUser> chats);
  int provideMultiUserPickerHint ();
}
