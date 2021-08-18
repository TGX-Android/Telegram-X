package org.thunderdog.challegram.telegram;

import androidx.annotation.IntDef;

import org.thunderdog.challegram.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Date: 2019-06-29
 * Author: default
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({
  R.id.right_readMessages,
  R.id.right_sendMessages,
  R.id.right_sendMedia,
  R.id.right_sendStickersAndGifs,
  R.id.right_sendPolls,
  R.id.right_embedLinks,
  R.id.right_changeChatInfo,
  R.id.right_editMessages,
  R.id.right_deleteMessages,
  R.id.right_banUsers,
  R.id.right_inviteUsers,
  R.id.right_pinMessages,
  R.id.right_addNewAdmins
})
public @interface RightId {}
